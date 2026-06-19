# Changelog

All notable changes to `mygrainexchange/mgx-php` are documented here. This SDK's
semver tracks the **MGX Enterprise API**, not the OpenAPI spec revision.

## 1.0.0

Initial release. Targets MGX Enterprise API `v1`.

- `MgxClient` entry point with OAuth2 client-credentials and refresh-token grants,
  token caching, and a static `accessToken` fallback.
- Resource namespaces: `inventory`, `market`, `bids`, `trades`, `teams`,
  `cashBids`, `webhooks`.
- Auto-pagination via `Paginator` over the `{ items, limit, offset, total, next }`
  envelope; `list()` methods return a lazy iterator.
- Automatic `Idempotency-Key` on `inventory()->placeBid()` and `cashBids()->create()`.
- Typed `MgxApiError` mapping the `{ error: { status, code, message, errors[] } }`
  envelope, exposing `status`, `code`, `message`, and `fieldErrors`.
