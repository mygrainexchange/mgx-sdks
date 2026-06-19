/**
 * MGX Java SDK — inbound webhook verification. This snippet is embedded in the
 * developer docs.
 *
 * Compile against the built mgx-sdk jar (and its okhttp-gson runtime deps), e.g.
 *   mvn -q -DskipTests package
 * then run with the dependency classpath on the command line.
 *
 * In a real HTTP handler, read the EXACT raw request body (do not re-serialize a
 * parsed object) and the {@code MGX-Signature} header, then verify before trusting
 * the payload.
 */
import com.mygrainexchange.mgx.model.WebhookEvent;
import com.mygrainexchange.mgx.overlay.WebhookVerifier;

import java.util.Map;

public class WebhookHandler {

    public static void main(String[] args) {
        // The subscription's signing secret (returned once when the webhook is created).
        String secret = System.getenv("MGX_WEBHOOK_SECRET");

        // These two values come straight from the inbound HTTP request.
        // e.g. String rawBody = readRequestBody(request);
        //      String signatureHeader = request.getHeader("MGX-Signature");
        String rawBody =
                "{\"id\":\"evt_6Yh2\",\"type\":\"trade.created\","
                + "\"created_at\":\"2026-06-18T00:00:00Z\",\"data\":{\"id\":\"trd_1\"}}";
        String signatureHeader = "t=1750204800,v1=<hex hmac from MGX>";

        try {
            // Returns the typed, verified event. Throws on bad signature / stale timestamp.
            WebhookEvent event = WebhookVerifier.verify(rawBody, signatureHeader, secret);

            System.out.println("verified " + event.getId() + " " + event.getType());

            // Dispatch on the event type.
            if (event.getType() != null && "trade.created".equals(event.getType().getValue())) {
                Map<String, Object> data = event.getData();
                System.out.println("trade " + (data != null ? data.get("id") : null));
            }

            // Acknowledge with a 2xx so MGX stops retrying this delivery.
        } catch (WebhookVerifier.MgxSignatureError e) {
            // Reject with a 4xx — do NOT process an unverified payload.
            System.err.println("rejected webhook: " + e.getMessage());
        }
    }
}
