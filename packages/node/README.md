# @mygrainexchange/sdk

Official TypeScript/Node client for the [MGX Enterprise API](https://developers.mygrainexchange.com).

```bash
npm install @mygrainexchange/sdk
```

## Quickstart

```ts
import { MgxClient, MgxApiError } from '@mygrainexchange/sdk'

const mgx = new MgxClient({
  clientId: process.env.MGX_CLIENT_ID!,
  clientSecret: process.env.MGX_CLIENT_SECRET!,
  scopes: ['inventory.read', 'market.read'],
  // baseUrl: 'https://dashboard.mgx.test/v1', // for local development
})

// Auto-paginates the { items, limit, offset, next } envelope.
for await (const lot of mgx.inventory.list({ commodity: 'wheat', minQuantity: 50 })) {
  console.log(lot.id, lot.quantityMt, lot.askingPrice?.amount)
}
```

## Authentication

- **Client credentials** (read-only data) — pass `clientId` + `clientSecret` + `scopes`; the SDK acquires and refreshes the token for you.
- **Login with MGX** (user-context: bids, trades, teams, cash bids, webhooks) — complete the authorization-code + PKCE flow, then pass the resulting `accessToken` (and optionally `refreshToken`) to `MgxClient`.

## Features

- **Resource namespaces** — `inventory`, `market`, `bids`, `trades`, `teams`, `cashBids`, `webhooks`.
- **Auto-pagination** — every `list()` returns an async iterator that follows `next`.
- **Idempotency** — `inventory.placeBid()` and `cashBids.create()` send an `Idempotency-Key` automatically.
- **Typed errors** — non-2xx responses throw `MgxApiError` with `status`, `code`, `message`, and `fieldErrors`.
- **Webhook verification** — `mgx.webhooks.verify(rawBody, signatureHeader, secret)` validates the `MGX-Signature` HMAC (constant-time, replay-protected) and returns a typed `WebhookEvent`; throws `MgxSignatureError` on a bad signature.

```ts
// In your webhook endpoint (pass the RAW request body):
const event = mgx.webhooks.verify(rawBody, req.headers['mgx-signature'], process.env.MGX_WEBHOOK_SECRET!)
if (event.type === 'trade.created') { /* ... */ }
```

```ts
try {
  const bid = await mgx.inventory.placeBid('inv_3Kd9aZ', {
    quantityMt: 50,
    price: { amount: 312.5 },
    delivery: { from: '2026-08-01', to: '2026-09-30' },
  })
} catch (e) {
  if (e instanceof MgxApiError) console.error(e.status, e.code, e.message)
}
```

## Generated code

The `src/` client is generated from the OpenAPI spec; the ergonomic layer lives in
`overlay/`. Do not hand-edit `src/` — change the API spec and regenerate. See the
[mgx-sdks](https://github.com/mygrainexchange/mgx-sdks) repo.

## License

MIT
