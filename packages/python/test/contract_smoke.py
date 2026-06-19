"""Gate-2 contract smoke test (mirrors the Node contract.smoke.mjs).

Expects a Prism mock of the spec at MGX_MOCK_URL (default 127.0.0.1:4013).
Exercises each overlay namespace and asserts spec-shaped responses decode cleanly.

    node ../../MGX/tooling/sdk/contract/serve-mock.mjs --port 4013 &
    MGX_MOCK_URL=http://127.0.0.1:4013 python test/contract_smoke.py
"""
import os
import sys

from mgx.overlay import MgxClient, MgxApiError

base_url = os.environ.get("MGX_MOCK_URL", "http://127.0.0.1:4013")
mgx = MgxClient(base_url=base_url, access_token="mock")

failures = 0


def check(name, fn):
    global failures
    try:
        fn()
        print(f"  ok  {name}")
    except Exception as e:
        failures += 1
        detail = f"{e.status} {e.code}" if isinstance(e, MgxApiError) else str(e)
        print(f"  FAIL {name}: {detail}")


def _inventory_list():
    count = 0
    for lot in mgx.inventory.list(commodity="wheat"):
        assert isinstance(lot.id, str)
        count += 1
        if count >= 1:
            break


def _inventory_get():
    lot = mgx.inventory.get("inv_3Kd9aZ")
    assert lot is not None


def _inventory_filters():
    mgx.inventory.filters()


def _market_commodities():
    c = mgx.market.commodities()
    assert isinstance(c, list)


def _market_prices():
    p = mgx.market.prices(commodity="wheat")
    assert isinstance(p, list)


def _market_history():
    mgx.market.history("wheat", interval="day")


def _teams_list():
    t = mgx.teams.list()
    assert isinstance(t, list)


check("inventory.list yields items", _inventory_list)
check("inventory.get returns a listing", _inventory_get)
check("inventory.filters returns facets", _inventory_filters)
check("market.commodities returns array", _market_commodities)
check("market.prices returns array", _market_prices)
check("market.history returns series", _market_history)
check("teams.list returns array", _teams_list)

print("\nAll contract checks passed." if failures == 0 else f"\n{failures} check(s) failed.")
sys.exit(0 if failures == 0 else 1)
