"""MGX SDK ergonomic overlay (hand-written -- survives client regeneration).

Import the client from here::

    from mgx.overlay import MgxClient, MgxApiError
"""
from .auth import TokenManager
from .client import MgxClient
from .errors import MgxApiError, MgxFieldError
from .webhooks import MgxSignatureError, verify_webhook

__all__ = [
    "MgxClient",
    "MgxApiError",
    "MgxFieldError",
    "TokenManager",
    "verify_webhook",
    "MgxSignatureError",
]
