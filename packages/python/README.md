# mgx

Official Python client for the [MGX Enterprise API](https://developers.mygrainexchange.com).

```bash
pip install mgx
```

## Quickstart

The ergonomic client lives in `mgx.overlay`:

```python
import os
from mgx.overlay import MgxClient, MgxApiError

mgx = MgxClient(
    client_id=os.environ["MGX_CLIENT_ID"],
    client_secret=os.environ["MGX_CLIENT_SECRET"],
    scopes=["inventory.read", "market.read"],
    # base_url="https://dashboard.mgx.test/v1",  # for local development
)

# Auto-paginates the { items, limit, offset, next } envelope.
for lot in mgx.inventory.list(commodity="wheat", min_quantity=50):
    print(lot.id, lot.quantity_mt, lot.asking_price.amount if lot.asking_price else None)
```

## Authentication

- **Client credentials** (read-only data) — pass `client_id` + `client_secret` + `scopes`; the SDK acquires and refreshes the token for you.
- **Login with MGX** (user-context: bids, trades, teams, cash bids, webhooks) — complete the authorization-code + PKCE flow, then pass the resulting `access_token` (and optionally `refresh_token`) to `MgxClient`.

## Features

- **Resource namespaces** — `inventory`, `market`, `bids`, `trades`, `teams`, `cash_bids`, `webhooks`.
- **Auto-pagination** — every `list()` returns a generator that follows `next` and yields items lazily.
- **Idempotency** — `inventory.place_bid()` sends an `Idempotency-Key` automatically (pass `idempotency_key=` to override).
- **Typed errors** — non-2xx responses raise `MgxApiError` with `status`, `code`, `message`, and `field_errors`.

```python
try:
    bid = mgx.inventory.place_bid(
        "inv_3Kd9aZ",
        quantity_mt=50,
        price={"amount": 312.5},
        delivery={"from": "2026-08-01", "to": "2026-09-30"},
    )
except MgxApiError as e:
    print(e.status, e.code, e.message, e.field_errors)
```

## Generated code

The `mgx/` client (everything except `mgx/overlay/`) is generated from the OpenAPI
spec; the ergonomic layer lives in `mgx/overlay/`. Do not hand-edit the generated
modules — change the API spec and regenerate. See the
[mgx-sdks](https://github.com/mygrainexchange/mgx-sdks) repo.

## License

MIT
