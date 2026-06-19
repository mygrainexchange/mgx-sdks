"""Hand-written overlay -- not generated.

The ergonomic ``MgxClient``. Wraps the generated resource APIs with OAuth2 token
handling, auto-pagination, idempotency keys, and typed errors. Responses are
unwrapped (``.data`` / ``.items``) so callers work with plain models and lists.
"""
from __future__ import annotations

import uuid
from typing import Any, Iterator, List, Optional

from mgx.api import (
    BidsApi,
    CashBidsApi,
    InventoryApi,
    MarketApi,
    TeamsApi,
    TradesApi,
    WebhooksApi,
)
from mgx.api_client import ApiClient
from mgx.configuration import Configuration
from mgx.models import (
    PlaceBid,
    StoreCashBid,
    UpdateCashBid,
    Webhook,
)

from .auth import TokenManager
from .errors import to_mgx_error
from mgx.exceptions import ApiException
from .pagination import paginate
from .webhooks import verify_webhook

DEFAULT_BASE_URL = "https://api.mygrainexchange.com/v1"


def _idempotency_key() -> str:
    return str(uuid.uuid4())


class _TokenConfiguration(Configuration):
    """Configuration whose ``access_token`` is resolved lazily per request.

    The generated ``Configuration.auth_settings()`` reads ``access_token`` and
    prepends ``"Bearer "`` itself, so we expose a *raw* token here (never prefixed)
    and let the generator build the final ``Authorization: Bearer <token>`` header.
    """

    def __init__(self, tokens: TokenManager, host: str) -> None:
        super().__init__(host=host)
        self._tokens = tokens

    @property
    def access_token(self) -> Optional[str]:  # type: ignore[override]
        return self._tokens.get_access_token()

    @access_token.setter
    def access_token(self, value: Optional[str]) -> None:
        # The base __init__ assigns access_token; ignore it -- the TokenManager owns it.
        # (It runs before _tokens is set, so we cannot delegate yet.)
        pass


def _call(fn: Any, *args: Any, **kwargs: Any) -> Any:
    """Invoke a generated method, converting ApiException -> MgxApiError."""
    try:
        return fn(*args, **kwargs)
    except ApiException as exc:
        raise to_mgx_error(exc) from exc


class _Inventory:
    def __init__(self, client: "MgxClient") -> None:
        self._inventory = client._inventory_api
        self._bids = client._bids_api

    def list(self, **filters: Any) -> Iterator[Any]:
        filters.pop("offset", None)
        return paginate(lambda offset: _call(self._inventory.inventory_list, offset=offset, **filters))

    def get(self, id: str) -> Any:
        return _call(self._inventory.inventory_get, id).data

    def filters(self) -> Any:
        return _call(self._inventory.inventory_filters)

    def place_bid(self, id: str, idempotency_key: Optional[str] = None, **body: Any) -> Any:
        return _call(
            self._bids.bid_place,
            id,
            PlaceBid.from_dict(body),
            idempotency_key=idempotency_key or _idempotency_key(),
        ).data


class _Market:
    def __init__(self, client: "MgxClient") -> None:
        self._market = client._market_api

    def commodities(self) -> List[Any]:
        return _call(self._market.market_commodities).items or []

    def prices(self, commodity: Optional[str] = None, date: Any = None) -> List[Any]:
        return _call(self._market.market_prices, commodity=commodity, var_date=date).items or []

    def history(self, commodity: str, **params: Any) -> Any:
        # Spec param `from` becomes `var_from` in the generated client; accept `from_`/`from`.
        if "from" in params:
            params["var_from"] = params.pop("from")
        if "from_" in params:
            params["var_from"] = params.pop("from_")
        return _call(self._market.market_history, commodity, **params)


class _Bids:
    def __init__(self, client: "MgxClient") -> None:
        self._bids = client._bids_api

    def list(self, **params: Any) -> Iterator[Any]:
        params.pop("offset", None)
        return paginate(lambda offset: _call(self._bids.bid_list, offset=offset, **params))

    def get(self, id: str) -> Any:
        return _call(self._bids.bid_get, id).data

    def accept(self, id: str) -> Any:
        return _call(self._bids.bid_accept, id).data

    def reject(self, id: str) -> Any:
        return _call(self._bids.bid_reject, id).data

    def counter(self, id: str, **body: Any) -> Any:
        return _call(self._bids.bid_counter, id, PlaceBid.from_dict(body)).data


class _Trades:
    def __init__(self, client: "MgxClient") -> None:
        self._trades = client._trades_api

    def list(self, **params: Any) -> Iterator[Any]:
        params.pop("offset", None)
        return paginate(lambda offset: _call(self._trades.trade_list, offset=offset, **params))

    def get(self, id: str) -> Any:
        return _call(self._trades.trade_get, id).data


class _Teams:
    def __init__(self, client: "MgxClient") -> None:
        self._teams = client._teams_api

    def list(self) -> List[Any]:
        return _call(self._teams.team_list).items or []

    def get(self, id: str) -> Any:
        return _call(self._teams.team_get, id).data


class _CashBids:
    def __init__(self, client: "MgxClient") -> None:
        self._cash = client._cash_bids_api

    def list(self, **params: Any) -> Iterator[Any]:
        params.pop("offset", None)
        return paginate(lambda offset: _call(self._cash.cash_bid_list, offset=offset, **params))

    def create(self, **body: Any) -> Any:
        return _call(self._cash.cash_bid_create, StoreCashBid.from_dict(body)).data

    def update(self, id: str, **body: Any) -> Any:
        return _call(self._cash.cash_bid_update, id, UpdateCashBid.from_dict(body)).data

    def offers(self, cash_bid_id: str, **params: Any) -> Iterator[Any]:
        params.pop("offset", None)
        return paginate(lambda offset: _call(self._cash.cash_bid_offers, cash_bid_id, offset=offset, **params))

    def accept_offer(self, offer_id: str) -> Any:
        return _call(self._cash.cash_bid_offer_accept, offer_id).data

    def reject_offer(self, offer_id: str) -> Any:
        return _call(self._cash.cash_bid_offer_reject, offer_id).data


class _Webhooks:
    def __init__(self, client: "MgxClient") -> None:
        self._webhooks = client._webhooks_api

    def list(self) -> List[Any]:
        return _call(self._webhooks.webhook_list).items or []

    def create(self, **body: Any) -> Any:
        return _call(self._webhooks.webhook_create, Webhook.from_dict(body)).data

    def delete(self, id: str) -> None:
        _call(self._webhooks.webhook_delete, id)

    def deliveries(self, webhook_id: str, **params: Any) -> Iterator[Any]:
        params.pop("offset", None)
        return paginate(lambda offset: _call(self._webhooks.webhook_deliveries, webhook_id, offset=offset, **params))

    def verify(self, raw_body: str, signature_header: str, secret: str, **opts: Any) -> Any:
        """Verify an inbound webhook's signature and return the typed event.

        Pass the raw request body, the ``MGX-Signature`` header, and the
        subscription's signing secret. Accepts ``tolerance_seconds`` and ``now``
        keyword options. Raises ``MgxSignatureError`` on failure.
        """
        return verify_webhook(raw_body, signature_header, secret, **opts)


class MgxClient:
    """The MGX Enterprise API client.

    Example::

        from mgx.overlay import MgxClient

        mgx = MgxClient(client_id="...", client_secret="...", scopes=["inventory:read"])
        for lot in mgx.inventory.list(commodity="wheat"):
            print(lot.id)
    """

    def __init__(
        self,
        client_id: Optional[str] = None,
        client_secret: Optional[str] = None,
        scopes: Optional[List[str]] = None,
        access_token: Optional[str] = None,
        refresh_token: Optional[str] = None,
        base_url: Optional[str] = None,
        token_url: Optional[str] = None,
    ) -> None:
        base = base_url or DEFAULT_BASE_URL
        tokens = TokenManager(
            base_url=base,
            client_id=client_id,
            client_secret=client_secret,
            scopes=scopes,
            access_token=access_token,
            refresh_token=refresh_token,
            token_url=token_url,
        )
        config = _TokenConfiguration(tokens, host=base)
        api_client = ApiClient(config)

        self._inventory_api = InventoryApi(api_client)
        self._market_api = MarketApi(api_client)
        self._bids_api = BidsApi(api_client)
        self._trades_api = TradesApi(api_client)
        self._teams_api = TeamsApi(api_client)
        self._cash_bids_api = CashBidsApi(api_client)
        self._webhooks_api = WebhooksApi(api_client)

        self.inventory = _Inventory(self)
        self.market = _Market(self)
        self.bids = _Bids(self)
        self.trades = _Trades(self)
        self.teams = _Teams(self)
        self.cash_bids = _CashBids(self)
        self.webhooks = _Webhooks(self)
