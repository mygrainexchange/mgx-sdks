# Changelog

## 1.0.0

- Initial release. Generated from MGX Enterprise API OpenAPI `1.0.0`.
- Resource namespaces: inventory, market, bids (incl. accept/reject/counter),
  trades, teams, cash bids (incl. offers), webhooks.
- Overlay: OAuth2 (client-credentials + authorization-code/refresh), auto-
  pagination, automatic idempotency keys, typed `MgxApiError`.
- Inbound webhook verification: `webhooks.verify()` (HMAC-SHA256 timestamped
  signature, constant-time, replay-protected) returning a typed `WebhookEvent`.
