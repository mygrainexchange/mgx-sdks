# Changelog

All notable changes to the MGX Java SDK are documented here. This SDK's semantic
version tracks the **MGX Enterprise API**, not the OpenAPI spec revision.

## 1.0.0

Initial release.

- `MgxClient` entry point with a fluent builder (`clientId`, `clientSecret`, `scopes`,
  `accessToken`, `refreshToken`, `tokenUrl`, `baseUrl`).
- OAuth2 authentication against `<host>/oauth/token`: client-credentials and
  refresh-token grants, plus static access tokens. Tokens are cached and auto-refreshed,
  and `Authorization: Bearer <token>` is attached to every request.
- Resource accessors: `inventory()`, `market()`, `bids()`, `trades()`, `teams()`,
  `cashBids()`, `webhooks()`.
- Lazy `Pagination<T>` (`Iterable`, `.stream()`, `.toList()`) that follows the
  `{ items, next }` envelope to exhaustion.
- Automatic `Idempotency-Key` generation for `inventory().placeBid(...)`.
- Typed `MgxApiError` (`status`, `code`, `message`, `fieldErrors`) parsed from the
  `{ error: { status, code, message, errors[] } }` envelope.
