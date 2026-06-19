"""Hand-written overlay -- not generated. Inbound webhook verification.

Verify the signature of an inbound MGX webhook before trusting its payload.
The dashboard sender signs with the same scheme implemented here.
"""
from __future__ import annotations

import hashlib
import hmac
import time
from typing import Optional

from mgx.models.webhook_event import WebhookEvent

DEFAULT_TOLERANCE_SECONDS = 300


class MgxSignatureError(Exception):
    """Raised when an inbound webhook signature cannot be verified."""


def _parse_signature_header(header: str) -> tuple[int, str]:
    """Parse ``t=<unix>,v1=<hex hmac>`` into ``(t, v1)``."""
    parts: dict[str, str] = {}
    for segment in header.split(","):
        key, sep, value = segment.partition("=")
        if not sep:
            continue
        parts[key.strip()] = value.strip()
    raw_t = parts.get("t")
    v1 = parts.get("v1")
    if not raw_t or not v1:
        raise MgxSignatureError("Malformed MGX-Signature header.")
    try:
        t = int(raw_t)
    except ValueError as exc:
        raise MgxSignatureError("Malformed MGX-Signature header.") from exc
    return t, v1


def verify_webhook(
    raw_body: str,
    signature_header: str,
    secret: str,
    *,
    tolerance_seconds: int = DEFAULT_TOLERANCE_SECONDS,
    now: Optional[int] = None,
) -> WebhookEvent:
    """Verify an inbound MGX webhook and return the typed event.

    The signature header is ``MGX-Signature: t=<unix>,v1=<hex hmac>``. The HMAC is
    SHA-256 over ``"{t}.{raw_body}"`` keyed with the subscription's signing secret.
    Pass the EXACT raw request body (not a re-serialized object). Raises
    :class:`MgxSignatureError` on a bad signature or a stale timestamp.
    """
    t, v1 = _parse_signature_header(signature_header)
    current = int(time.time()) if now is None else now
    if abs(current - t) > tolerance_seconds:
        raise MgxSignatureError("Webhook timestamp is outside the tolerance window.")
    expected = hmac.new(
        secret.encode(), f"{t}.{raw_body}".encode(), hashlib.sha256
    ).hexdigest()
    if not hmac.compare_digest(expected, v1):
        raise MgxSignatureError("Webhook signature does not match.")
    event = WebhookEvent.from_json(raw_body)
    if event is None:
        raise MgxSignatureError("Webhook body could not be parsed.")
    return event
