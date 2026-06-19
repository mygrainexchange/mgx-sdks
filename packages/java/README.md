# MGX Java SDK

Official Java client for the [MGX Enterprise API](https://api.mygrainexchange.com). Browse
anonymized inventory, place bids, read your team's trades and bids, manage cash bids,
subscribe to webhooks, and read market prices.

The generated HTTP client (models + raw operations) is wrapped by a hand-written
ergonomic overlay (`com.mygrainexchange.mgx.overlay`) that adds OAuth2 token handling,
auto-pagination, idempotency keys, and a typed error.

## Install

**Maven**

```xml
<dependency>
  <groupId>com.mygrainexchange</groupId>
  <artifactId>mgx-sdk</artifactId>
  <version>1.0.0</version>
</dependency>
```

**Gradle**

```groovy
implementation 'com.mygrainexchange:mgx-sdk:1.0.0'
```

Requires Java 8+. Built on OkHttp + Gson.

## Quickstart

```java
import com.mygrainexchange.mgx.overlay.MgxClient;
import com.mygrainexchange.mgx.overlay.MgxApiError;
import com.mygrainexchange.mgx.model.Inventory;

MgxClient mgx = MgxClient.builder()
    .clientId(System.getenv("MGX_CLIENT_ID"))
    .clientSecret(System.getenv("MGX_CLIENT_SECRET"))
    .scopes("inventory.read", "market.read")
    // .baseUrl("https://dashboard.mgx.test/v1") // for local development
    .build();

// Auto-paginates across the { items, next } envelope.
for (Inventory lot : mgx.inventory().list(
        new MgxClient.InventoryListFilters().commodity("wheat"))) {
    System.out.println(lot.getId());
}
```

See [`examples/Quickstart.java`](examples/Quickstart.java) for a fuller walkthrough.

## Authentication

The client acquires, caches, and auto-refreshes an OAuth2 access token against
`<host>/oauth/token` and attaches `Authorization: Bearer <token>` to every request.

| Grant | When | Configure |
| --- | --- | --- |
| **client-credentials** | Server-to-server, read scopes | `.clientId(...).clientSecret(...).scopes(...)` |
| **refresh_token** | Renew a user-context token | `.refreshToken(...)` (+ `clientId`/`clientSecret`) |
| **static token** | A pre-obtained access token | `.accessToken(...)` |

Override the token endpoint with `.tokenUrl(...)` and the API host with `.baseUrl(...)`
(default `https://api.mygrainexchange.com/v1`).

## Features

- **`MgxClient`** — single entry point with a fluent builder.
- **Resource accessors** — `inventory()`, `market()`, `bids()`, `trades()`, `teams()`,
  `cashBids()`, `webhooks()`. Methods unwrap the `data`/`items` envelopes for you.
- **Pagination** — `list()`/`offers()`/`deliveries()` return a lazy `Pagination<T>`
  (`Iterable<T>`, plus `.stream()` and `.toList()`) that transparently follows `next`.
- **Idempotency** — `inventory().placeBid(...)` auto-generates an `Idempotency-Key`
  (a random UUID) when you do not pass one.
- **Typed errors** — every failure throws `MgxApiError` exposing `getStatus()`,
  `getCode()`, `getMessage()`, and `getFieldErrors()`, parsed from the API's
  `{ error: { status, code, message, errors[] } }` envelope.

## Resource reference

| Resource | Methods |
| --- | --- |
| `inventory()` | `list(filters)`, `get(id)`, `filters()`, `placeBid(id, body[, idempotencyKey])` |
| `market()` | `commodities()`, `prices([commodity, date])`, `history(commodity[, from, to, interval])` |
| `bids()` | `list([status])`, `get(id)`, `accept(id)`, `reject(id)`, `counter(id, body)` |
| `trades()` | `list(filters)`, `get(id)` |
| `teams()` | `list()`, `get(id)` |
| `cashBids()` | `list(filters)`, `create(body)`, `update(id, body)`, `offers(id[, status])`, `acceptOffer(offerId)`, `rejectOffer(offerId)` |
| `webhooks()` | `list()`, `create(body)`, `delete(id)`, `deliveries(id)` |

## License

See [LICENSE](LICENSE).
