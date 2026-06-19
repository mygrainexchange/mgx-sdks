# mygrainexchange/mgx-php

Official PHP client for the [MGX Enterprise API](https://developers.mygrainexchange.com).

```bash
composer require mygrainexchange/mgx-php
```

Requires PHP 8.1+.

## Quickstart

```php
use MyGrainExchange\Mgx\Overlay\MgxClient;
use MyGrainExchange\Mgx\Overlay\MgxApiError;

$mgx = new MgxClient(
    clientId: getenv('MGX_CLIENT_ID'),
    clientSecret: getenv('MGX_CLIENT_SECRET'),
    scopes: ['inventory.read', 'market.read'],
    // baseUrl: 'https://dashboard.mgx.test/v1', // for local development
);

// Auto-paginates the { items, limit, offset, next } envelope.
foreach ($mgx->inventory()->list(['commodity' => 'wheat', 'minQuantity' => 50]) as $lot) {
    echo $lot->getId(), ' ', $lot->getQuantityMt(), ' ', $lot->getAskingPrice()?->getAmount(), PHP_EOL;
}
```

You can also construct with an options array:

```php
$mgx = MgxClient::create([
    'clientId' => getenv('MGX_CLIENT_ID'),
    'clientSecret' => getenv('MGX_CLIENT_SECRET'),
    'scopes' => ['inventory.read', 'market.read'],
]);
```

## Authentication

- **Client credentials** (read-only data) — pass `clientId` + `clientSecret` + `scopes`; the SDK acquires, caches, and refreshes the token for you.
- **Login with MGX** (user-context: bids, trades, teams, cash bids, webhooks) — complete the authorization-code + PKCE flow, then pass the resulting `accessToken` (and optionally `refreshToken`) to `MgxClient`.

## Features

- **Resource namespaces** — `inventory()`, `market()`, `bids()`, `trades()`, `teams()`, `cashBids()`, `webhooks()`.
- **Auto-pagination** — every `list()` returns a `Paginator` you can `foreach` over; it follows `next` until the result set is exhausted (`->toArray()` collects eagerly).
- **Idempotency** — `inventory()->placeBid()` and `cashBids()->create()` send an `Idempotency-Key` automatically (override with the `$idempotencyKey` argument).
- **Typed errors** — non-2xx responses throw `MgxApiError` with `status()`, `code()`, `getMessage()`, and `fieldErrors()`.

```php
try {
    $bid = $mgx->inventory()->placeBid('inv_3Kd9aZ', [
        'quantity_mt' => 50,
        'price' => ['amount' => 312.5],
        'delivery' => ['from' => '2026-08-01', 'to' => '2026-09-30'],
    ]);
} catch (MgxApiError $e) {
    error_log("{$e->status()} {$e->code()} {$e->getMessage()}");
}
```

## Generated code

The `lib/` client is generated from the OpenAPI spec; the ergonomic layer lives in
`overlay/` (namespace `MyGrainExchange\Mgx\Overlay`). Do not hand-edit `lib/` —
change the API spec and regenerate. See the
[mgx-sdks](https://github.com/mygrainexchange/mgx-sdks) repo.

## License

MIT
