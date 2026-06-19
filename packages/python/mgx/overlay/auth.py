"""Hand-written overlay -- not generated.

OAuth2 token acquisition, caching, and refresh. Uses only the standard library
(``urllib``) so the overlay adds no dependencies beyond the generated client's.
"""
from __future__ import annotations

import json
import time
import urllib.parse
import urllib.request
from typing import Dict, List, Optional


class TokenManager:
    """Acquires, caches, and refreshes OAuth2 access tokens.

    - ``client_credentials`` when ``client_id`` + ``client_secret`` are given;
    - ``refresh_token`` when a ``refresh_token`` is given (user-context tokens);
    - otherwise uses the static ``access_token`` as-is.

    Tokens are cached with their expiry and renewed automatically.
    """

    def __init__(
        self,
        base_url: str,
        client_id: Optional[str] = None,
        client_secret: Optional[str] = None,
        scopes: Optional[List[str]] = None,
        access_token: Optional[str] = None,
        refresh_token: Optional[str] = None,
        token_url: Optional[str] = None,
    ) -> None:
        self.base_url = base_url
        self.client_id = client_id
        self.client_secret = client_secret
        self.scopes = scopes or []
        self._token = access_token
        self._refresh_token = refresh_token
        self._token_url = token_url
        # A caller-supplied token with no refresh path is treated as long-lived.
        self._expires_at = float("inf") if (access_token and not refresh_token) else 0.0

    @property
    def token_url(self) -> str:
        if self._token_url:
            return self._token_url
        base = self.base_url.rstrip("/")
        if base.endswith("/v1"):
            base = base[: -len("/v1")]
        return base + "/oauth/token"

    def get_access_token(self) -> str:
        """Return a valid bearer token, refreshing or acquiring one as needed."""
        if self._token and time.time() < self._expires_at:
            return self._token

        if self._refresh_token:
            return self._grant(
                {
                    "grant_type": "refresh_token",
                    "refresh_token": self._refresh_token,
                    "client_id": self.client_id or "",
                    "client_secret": self.client_secret or "",
                }
            )

        if self.client_id and self.client_secret:
            return self._grant(
                {
                    "grant_type": "client_credentials",
                    "client_id": self.client_id,
                    "client_secret": self.client_secret,
                    "scope": " ".join(self.scopes),
                }
            )

        if self._token:
            return self._token

        raise RuntimeError(
            "MGX SDK: no credentials configured. Provide client_id + "
            "client_secret, or an access_token."
        )

    def _grant(self, body: Dict[str, str]) -> str:
        data = urllib.parse.urlencode(body).encode("utf-8")
        req = urllib.request.Request(
            self.token_url,
            data=data,
            method="POST",
            headers={
                "Content-Type": "application/x-www-form-urlencoded",
                "Accept": "application/json",
            },
        )
        try:
            with urllib.request.urlopen(req) as resp:
                payload = json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:  # type: ignore[attr-defined]
            raise RuntimeError(
                f"MGX SDK: OAuth2 token request failed ({exc.code})."
            ) from exc

        self._token = payload["access_token"]
        expires_in = payload.get("expires_in", 3600)
        self._expires_at = time.time() + (expires_in - 60)
        if payload.get("refresh_token"):
            self._refresh_token = payload["refresh_token"]
        return self._token
