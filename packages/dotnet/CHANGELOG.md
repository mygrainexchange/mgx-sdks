# Changelog

## 1.0.0

- Initial release. Generated from MGX Enterprise API OpenAPI `1.0.0`.
- Resource namespaces: Inventory, Market, Bids (incl. Accept/Reject/Counter),
  Trades, Teams, CashBids (incl. Offers), Webhooks.
- Overlay: OAuth2 (client-credentials + authorization-code/refresh), auto-
  pagination via `IAsyncEnumerable<T>`, automatic idempotency keys, typed
  `MgxApiError`.
