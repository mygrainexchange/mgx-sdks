/**
 * MGX Java SDK — webhook signature verification test (no network).
 *
 * Mirrors the Node SDK's test/webhook.verify.mjs. This is a standalone runner
 * (a plain {@code main}) rather than a JUnit case, so it can be compiled and run
 * against just the built jar without a test framework on the classpath.
 *
 * Compile and run (after building the jar, e.g. `mvn -q -DskipTests package`):
 *   javac -cp target/classes test/WebhookVerifyTest.java -d /tmp/mgx-test
 *   java  -cp target/classes:/tmp/mgx-test WebhookVerifyTest
 *
 * Exits 0 when all checks pass, 1 otherwise.
 */
import com.mygrainexchange.mgx.model.WebhookEvent;
import com.mygrainexchange.mgx.overlay.MgxClient;
import com.mygrainexchange.mgx.overlay.WebhookVerifier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

public class WebhookVerifyTest {

    private static int failures = 0;

    public static void main(String[] args) {
        final String secret = "whsec_test";
        final String body =
                "{\"id\":\"evt_6Yh2\",\"type\":\"trade.created\","
                + "\"created_at\":\"2026-06-18T00:00:00Z\",\"data\":{\"id\":\"trd_1\"}}";
        final long t = System.currentTimeMillis() / 1000L;

        check("valid signature returns the typed event", new Runnable() {
            public void run() {
                WebhookEvent evt = WebhookVerifier.verify(body, sign(t, body, secret), secret);
                assertEquals("trade.created", evt.getType().getValue());
                assertEquals("evt_6Yh2", evt.getId());
            }
        });

        check("client.webhooks().verify works", new Runnable() {
            public void run() {
                MgxClient mgx = MgxClient.builder().accessToken("x").build();
                WebhookEvent evt = mgx.webhooks().verify(body, sign(t, body, secret), secret);
                assertEquals("trade.created", evt.getType().getValue());
            }
        });

        check("tampered body is rejected", new Runnable() {
            public void run() {
                assertThrows(body + " ", sign(t, body, secret), secret);
            }
        });

        check("wrong secret is rejected", new Runnable() {
            public void run() {
                assertThrows(body, sign(t, body, secret), "whsec_wrong");
            }
        });

        check("stale timestamp is rejected", new Runnable() {
            public void run() {
                assertThrows(body, sign(t - 9999, body, secret), secret);
            }
        });

        check("malformed header is rejected", new Runnable() {
            public void run() {
                assertThrows(body, "garbage", secret);
            }
        });

        System.out.println(failures == 0
                ? "\nAll webhook-verify checks passed."
                : "\n" + failures + " failed.");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---- helpers ----------------------------------------------------------

    private static void check(String name, Runnable fn) {
        try {
            fn.run();
            System.out.println("  ok  " + name);
        } catch (Throwable e) {
            failures++;
            System.err.println("  FAIL " + name + ": " + e.getMessage());
        }
    }

    /** Builds a valid `t=...,v1=...` header for the given timestamp/body/secret. */
    private static String sign(long ts, String b, String s) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(s.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((ts + "." + b).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                int unsigned = value & 0xff;
                if (unsigned < 0x10) {
                    hex.append('0');
                }
                hex.append(Integer.toHexString(unsigned));
            }
            return "t=" + ts + ",v1=" + hex;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Asserts that verify(...) throws MgxSignatureError for the given inputs. */
    private static void assertThrows(String rawBody, String signatureHeader, String secret) {
        try {
            WebhookVerifier.verify(rawBody, signatureHeader, secret);
        } catch (WebhookVerifier.MgxSignatureError expected) {
            return;
        }
        throw new AssertionError("expected MgxSignatureError but none was thrown");
    }

    private static void assertEquals(String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }
}
