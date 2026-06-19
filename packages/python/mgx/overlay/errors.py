"""Hand-written overlay -- not generated.

Typed errors mapped from the API's ``{ "error": { status, code, message, errors[] } }``
envelope.
"""
from __future__ import annotations

import json
from typing import Any, Callable, Dict, List, Optional, TypeVar

from mgx.exceptions import ApiException

T = TypeVar("T")


class MgxFieldError(dict):
    """A single field-level validation error (``{"field": ..., "message": ...}``)."""

    @property
    def field(self) -> Optional[str]:
        return self.get("field")

    @property
    def message(self) -> Optional[str]:
        return self.get("message")


class MgxApiError(Exception):
    """Typed error raised for any non-2xx API response.

    Attributes:
        status: HTTP status code.
        code: machine-readable error code from the API envelope.
        message: human-readable message.
        field_errors: list of per-field validation errors (may be empty).
    """

    def __init__(
        self,
        status: int,
        code: str,
        message: str,
        field_errors: Optional[List[MgxFieldError]] = None,
    ) -> None:
        super().__init__(message)
        self.status = status
        self.code = code
        self.message = message
        self.field_errors: List[MgxFieldError] = field_errors or []

    def __str__(self) -> str:
        return f"MgxApiError({self.status} {self.code}): {self.message}"


def _parse_body(exc: ApiException) -> Dict[str, Any]:
    """Best-effort decode of the JSON error body off a generated ApiException."""
    raw = exc.data if exc.data is not None else exc.body
    if raw is None:
        return {}
    if isinstance(raw, (bytes, bytearray)):
        try:
            raw = raw.decode("utf-8")
        except Exception:
            return {}
    if isinstance(raw, str):
        try:
            return json.loads(raw)
        except Exception:
            return {}
    if isinstance(raw, dict):
        return raw
    return {}


def to_mgx_error(exc: ApiException) -> MgxApiError:
    """Convert a generated :class:`ApiException` into a typed :class:`MgxApiError`."""
    status = exc.status or 0
    code = "error"
    message = exc.reason or "Request failed"
    field_errors: List[MgxFieldError] = []

    body = _parse_body(exc)
    error = body.get("error") if isinstance(body, dict) else None
    if isinstance(error, dict):
        status = error.get("status", status) or status
        code = error.get("code", code) or code
        message = error.get("message", message) or message
        errors = error.get("errors") or []
        if isinstance(errors, list):
            field_errors = [MgxFieldError(e) for e in errors if isinstance(e, dict)]

    return MgxApiError(int(status), str(code), str(message), field_errors)


def wrap_errors(fn: Callable[..., T]) -> Callable[..., T]:
    """Decorate a call so any generated ApiException surfaces as an MgxApiError."""

    def _wrapped(*args: Any, **kwargs: Any) -> T:
        try:
            return fn(*args, **kwargs)
        except ApiException as exc:
            raise to_mgx_error(exc) from exc

    return _wrapped
