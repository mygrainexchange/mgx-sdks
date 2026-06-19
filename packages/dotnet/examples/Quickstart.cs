// MGX .NET SDK — quickstart. These snippets mirror the cross-SDK quickstart and
// are embedded in the developer portal. This file is illustrative; it is not
// compiled into the package (examples/ is excluded from the project).
using System;
using System.Threading.Tasks;
using MyGrainExchange.Api.Model;
using MyGrainExchange.Api.Overlay;

internal static class Quickstart
{
    private static async Task Main()
    {
        var mgx = new MgxClient(new MgxClientOptions
        {
            ClientId = Environment.GetEnvironmentVariable("MGX_CLIENT_ID"),
            ClientSecret = Environment.GetEnvironmentVariable("MGX_CLIENT_SECRET"),
            Scopes = new[] { "inventory.read", "market.read" },
            // BaseUrl = "https://dashboard.mgx.test/v1", // for local development
        });

        // Browse anonymized inventory — auto-paginates across the items/next envelope.
        await foreach (var lot in mgx.Inventory.ListAsync(new InventoryListFilters
        {
            Commodity = "wheat",
            MinQuantity = 50,
        }))
        {
            Console.WriteLine($"{lot.Id} {lot.QuantityMt} {lot.AskingPrice?.Amount}");
        }

        // Current market prices.
        var prices = await mgx.Market.PricesAsync(commodity: "wheat,canola");
        foreach (var p in prices)
            Console.WriteLine($"{p.Commodity?.Slug}: {p.Price?.Amount}");

        // Place a bid (requires a user-context token with bids.write). The SDK adds
        // an Idempotency-Key automatically.
        try
        {
            var bid = await mgx.Inventory.PlaceBidAsync("inv_3Kd9aZ", new PlaceBid(
                price: new Money(amount: 312.5m),
                quantityMt: 50,
                delivery: new DateWindow(
                    from: new DateOnly(2026, 8, 1),
                    to: new DateOnly(2026, 9, 30))));
            Console.WriteLine($"placed {bid?.Id} {bid?.Status}");
        }
        catch (MgxApiError e)
        {
            Console.Error.WriteLine($"{e.Status} {e.Code} {e.Message}");
            foreach (var fe in e.FieldErrors)
                Console.Error.WriteLine($"  {fe.Field}: {fe.Message}");
        }
    }
}
