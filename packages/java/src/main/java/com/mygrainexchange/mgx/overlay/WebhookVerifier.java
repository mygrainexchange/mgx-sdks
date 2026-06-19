/* Hand-written overlay — not generated. Inbound webhook verification. */
package com.mygrainexchange.mgx.overlay;

import com.mygrainexchange.mgx.JSON;
import com.mygrainexchange.mgx.model.WebhookEvent;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

/**
 * Verifies inbound MGX webhook signatures and returns the typed event.
 *
 * <p>The signature header is {@code MGX-Signature: t=<unix>,v1=<hex hmac>}. The HMAC
 * is SHA-256 over {@code "{t}.{rawBody}"} keyed with the subscription's signing
 * secret. Pass the EXACT raw request body (not a re-serialized object). Throws
 * {@link MgxSignatureError} on a bad signature or a stale timestamp.
 *
 * <pre>{@code
 * WebhookEvent event = WebhookVerifier.verify(rawBody, request.getHeader("MGX-Signature"), secret);
 * }</pre>
 */
public final class WebhookVerifier {

    /** Max age of the signed timestamp, in seconds. Default 300 (5 minutes). */
    public static final int DEFAULT_TOLERANCE_SECONDS = 300;

    private WebhookVerifier() {
    }

    /** Thrown when an inbound webhook signature cannot be verified. */
    public static final class MgxSignatureError extends RuntimeException {
        public MgxSignatureError(String message) {
            super(message);
        }
    }

    /** Parsed {@code MGX-Signature} header fields. */
    private static final class ParsedSignature {
        final long t;
        final String v1;

        ParsedSignature(long t, String v1) {
            this.t = t;
            this.v1 = v1;
        }
    }

    /**
     * Verifies an inbound MGX webhook and returns the typed event, using the default
     * tolerance (300 seconds) and the current system time.
     *
     * @param rawBody         the exact raw request body
     * @param signatureHeader the value of the {@code MGX-Signature} header
     * @param secret          the subscription's signing secret
     * @return the deserialized {@link WebhookEvent}
     * @throws MgxSignatureError on a bad signature, a stale timestamp, or a malformed header
     */
    public static WebhookEvent verify(String rawBody, String signatureHeader, String secret) {
        return verify(rawBody, signatureHeader, secret, DEFAULT_TOLERANCE_SECONDS, null);
    }

    /**
     * Verifies an inbound MGX webhook and returns the typed event.
     *
     * @param rawBody          the exact raw request body
     * @param signatureHeader  the value of the {@code MGX-Signature} header
     * @param secret           the subscription's signing secret
     * @param toleranceSeconds max age of the signed timestamp, in seconds
     * @param now              override for "now" (unix seconds); pass {@code null} to use the system clock
     * @return the deserialized {@link WebhookEvent}
     * @throws MgxSignatureError on a bad signature, a stale timestamp, or a malformed header
     */
    public static WebhookEvent verify(
            String rawBody,
            String signatureHeader,
            String secret,
            int toleranceSeconds,
            Long now) {
        ParsedSignature parsed = parseSignatureHeader(signatureHeader);
        long currentTime = now != null ? now : System.currentTimeMillis() / 1000L;
        if (Math.abs(currentTime - parsed.t) > toleranceSeconds) {
            throw new MgxSignatureError("Webhook timestamp is outside the tolerance window.");
        }

        String expected = hmacSha256Hex(parsed.t + "." + rawBody, secret);
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = parsed.v1.getBytes(StandardCharsets.UTF_8);
        // Constant-time comparison; MessageDigest.isEqual already short-circuits on
        // length but does not leak content timing for equal-length inputs.
        if (a.length != b.length || !MessageDigest.isEqual(a, b)) {
            throw new MgxSignatureError("Webhook signature does not match.");
        }

        try {
            return deserializeEvent(rawBody);
        } catch (RuntimeException e) {
            throw new MgxSignatureError("Webhook body is not a valid JSON event.");
        }
    }

    private static WebhookEvent deserializeEvent(String rawBody) {
        // Mirrors the generated WebhookEvent.fromJson(...) deserialization path.
        return JSON.getGson().fromJson(rawBody, WebhookEvent.class);
    }

    private static ParsedSignature parseSignatureHeader(String header) {
        if (header == null) {
            throw new MgxSignatureError("Malformed MGX-Signature header.");
        }
        String tValue = null;
        String v1Value = null;
        for (String segment : header.split(",")) {
            int idx = segment.indexOf('=');
            if (idx == -1) {
                continue;
            }
            String key = segment.substring(0, idx).trim();
            String value = segment.substring(idx + 1).trim();
            if ("t".equals(key)) {
                tValue = value;
            } else if ("v1".equals(key)) {
                v1Value = value;
            }
        }
        if (tValue == null || tValue.isEmpty() || v1Value == null || v1Value.isEmpty()) {
            throw new MgxSignatureError("Malformed MGX-Signature header.");
        }
        long t;
        try {
            t = Long.parseLong(tValue);
        } catch (NumberFormatException e) {
            throw new MgxSignatureError("Malformed MGX-Signature header.");
        }
        return new ParsedSignature(t, v1Value);
    }

    private static String hmacSha256Hex(String message, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                int unsigned = value & 0xff;
                if (unsigned < 0x10) {
                    hex.append('0');
                }
                hex.append(Integer.toHexString(unsigned));
            }
            return hex.toString();
        } catch (GeneralSecurityException e) {
            throw new MgxSignatureError("Unable to compute HMAC-SHA256 signature.");
        }
    }
}
