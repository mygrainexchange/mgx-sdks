"""MGX Python SDK -- quickstart.

Mirrors the TypeScript quickstart: browse inventory, read market prices, and
place a bid with typed error handling. Run with your credentials in the env::

    MGX_CLIENT_ID=... MGX_CLIENT_SECRET=... python examples/quickstart.py
"""
import os

from mgx.overlay import MgxClient, MgxApiError

mgx = MgxClient(
    client_id=os.environ.get("MGX_CLIENT_ID"),
    client_secret=os.environ.get("MGX_CLIENT_SECRET"),
    scopes=["inventory.read", "market.read"],
    # base_url="https://dashboard.mgx.test/v1",  # for local development
)


def main() -> None:
    # Browse anonymized inventory -- auto-paginates across the items/next envelope.
    for lot in mgx.inventory.list(commodity="wheat", min_quantity=50):
        asking = lot.asking_price.amount if lot.asking_price else None
        print(lot.id, lot.quantity_mt, asking)

    # Current market prices.
    prices = mgx.market.prices(commodity="wheat,canola")
    print([f"{p.commodity.slug}: {p.price.amount}" for p in prices if p.commodity and p.price])

    # Place a bid (requires a user-context token with bids.write). The SDK adds
    # an Idempotency-Key automatically.
    try:
        bid = mgx.inventory.place_bid(
            "inv_3Kd9aZ",
            quantity_mt=50,
            price={"amount": 312.5},
            delivery={"from": "2026-08-01", "to": "2026-09-30"},
        )
        print("placed", bid.id, bid.status)
    except MgxApiError as e:
        print(e.status, e.code, e.message, e.field_errors)


if __name__ == "__main__":
    main()
