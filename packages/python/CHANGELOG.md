# Changelog

All notable changes to the MGX Python SDK are documented here.

## 1.0.0

Initial release.

- Generated client for the MGX Enterprise API (OpenAPI 1.0.0).
- Ergonomic overlay at `mgx.overlay`:
  - `MgxClient` with `inventory`, `market`, `bids`, `trades`, `teams`, `cash_bids`, and `webhooks` namespaces.
  - OAuth2 `TokenManager` — client-credentials, refresh-token, and static-token auth with caching and automatic refresh.
  - Auto-pagination generators that follow the `{ items, next }` envelope.
  - Automatic `Idempotency-Key` on `inventory.place_bid()`.
  - Typed `MgxApiError` (`status`, `code`, `message`, `field_errors`) mapped from the API error envelope.
