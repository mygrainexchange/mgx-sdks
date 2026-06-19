// MGX .NET SDK — inbound webhook verification example. This file is illustrative;
// it is not compiled into the package (examples/ is excluded from the project).
//
// MGX signs every webhook delivery with the subscription's signing secret. Always
// verify the signature against the EXACT raw request body before trusting the event.
using System;
using MyGrainExchange.Api.Model;
using MyGrainExchange.Api.Overlay;

internal static class WebhookHandler
{
    // In a real handler these come from the inbound HTTP request and your config.
    // e.g. in ASP.NET Core: read the raw body with a buffered stream, and the header
    // with Request.Headers["MGX-Signature"].
    private static void HandleDelivery(string rawBody, string signatureHeader)
    {
        var secret = Environment.GetEnvironmentVariable("MGX_WEBHOOK_SECRET");

        WebhookEvent evt;
        try
        {
            evt = WebhookVerifier.Verify(rawBody, signatureHeader, secret);
            // Equivalent via the client wrapper:
            //   var mgx = new MgxClient(new MgxClientOptions { /* ... */ });
            //   evt = mgx.Webhooks.Verify(rawBody, signatureHeader, secret);
        }
        catch (MgxSignatureError e)
        {
            // Reject: do NOT process. Return 400 to the sender.
            Console.Error.WriteLine($"rejected webhook: {e.Message}");
            return;
        }

        // The signature is valid — branch on the typed event.
        switch (evt.Type)
        {
            case WebhookEventType.BidAccepted:
                Console.WriteLine($"bid accepted: {evt.Id}");
                break;
            case WebhookEventType.BidRejected:
                Console.WriteLine($"bid rejected: {evt.Id}");
                break;
            case WebhookEventType.BidCountered:
                Console.WriteLine($"bid countered: {evt.Id}");
                break;
            case WebhookEventType.TradeCreated:
                Console.WriteLine($"trade created: {evt.Id} at {evt.CreatedAt:o}");
                break;
            case WebhookEventType.TradeSettled:
                Console.WriteLine($"trade settled: {evt.Id}");
                break;
            case WebhookEventType.CashbidOfferReceived:
                Console.WriteLine($"cash bid offer received: {evt.Id}");
                break;
            case WebhookEventType.InventoryMatched:
                Console.WriteLine($"inventory matched: {evt.Id}");
                break;
            default:
                Console.WriteLine($"unhandled event type {evt.Type}: {evt.Id}");
                break;
        }

        // evt.Data carries the resource for this event (a Trade, CashBidOffer, etc.).
    }
}
