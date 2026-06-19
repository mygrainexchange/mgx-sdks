# MyGrainExchange.Api

Official .NET client for the [MGX Enterprise API](https://developers.mygrainexchange.com). Targets `net8.0`.

```bash
dotnet add package MyGrainExchange.Api
```

## Quickstart

```csharp
using MyGrainExchange.Api.Model;
using MyGrainExchange.Api.Overlay;

var mgx = new MgxClient(new MgxClientOptions
{
    ClientId = Environment.GetEnvironmentVariable("MGX_CLIENT_ID"),
    ClientSecret = Environment.GetEnvironmentVariable("MGX_CLIENT_SECRET"),
    Scopes = new[] { "inventory.read", "market.read" },
    // BaseUrl = "https://dashboard.mgx.test/v1", // for local development
});

// Auto-paginates the { items, limit, offset, next } envelope.
await foreach (var lot in mgx.Inventory.ListAsync(new InventoryListFilters
{
    Commodity = "wheat",
    MinQuantity = 50,
}))
{
    Console.WriteLine($"{lot.Id} {lot.QuantityMt} {lot.AskingPrice?.Amount}");
}
```

## Authentication

- **Client credentials** (read-only data) — set `ClientId` + `ClientSecret` + `Scopes`; the SDK acquires and refreshes the token for you.
- **Login with MGX** (user-context: bids, trades, teams, cash bids, webhooks) — complete the authorization-code + PKCE flow, then pass the resulting `AccessToken` (and optionally `RefreshToken`) to `MgxClient`.

The token endpoint defaults to `<host>/oauth/token`, derived from `BaseUrl` (override with `TokenUrl`).

## Features

- **Resource namespaces** — `Inventory`, `Market`, `Bids`, `Trades`, `Teams`, `CashBids`, `Webhooks`.
- **Auto-pagination** — every `ListAsync(...)` / `OffersAsync(...)` / `DeliveriesAsync(...)` returns an `IAsyncEnumerable<T>` that lazily follows `next`. Use `await foreach`, or `.ToListAsync()` for small sets.
- **Idempotency** — `Inventory.PlaceBidAsync(...)` sends an `Idempotency-Key` automatically (override via the `idempotencyKey` argument).
- **Typed errors** — non-2xx responses throw `MgxApiError` with `Status`, `Code`, `Message`, and `FieldErrors`.

```csharp
try
{
    var bid = await mgx.Inventory.PlaceBidAsync("inv_3Kd9aZ", new PlaceBid(
        price: new Money(amount: 312.5m),
        quantityMt: 50,
        delivery: new DateWindow(new DateOnly(2026, 8, 1), new DateOnly(2026, 9, 30))));
}
catch (MgxApiError e)
{
    Console.Error.WriteLine($"{e.Status} {e.Code} {e.Message}");
    foreach (var fe in e.FieldErrors)
        Console.Error.WriteLine($"  {fe.Field}: {fe.Message}");
}
```

## Generated code

The `src/MyGrainExchange.Api/{Api,Model,Client}` code is generated from the OpenAPI
spec; the ergonomic layer lives in `src/MyGrainExchange.Api/Overlay/` (it ships in the
same assembly so it's protected from regeneration via `.openapi-generator-ignore`). Do
not hand-edit the generated code — change the API spec and regenerate. See the
[mgx-sdks](https://github.com/mygrainexchange/mgx-sdks) repo.

## License

MIT
