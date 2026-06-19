"""Unit test for webhook signature verification (no network).

Mirrors the TypeScript test at packages/node/test/webhook.verify.mjs.
Run with::

    python test/webhook_verify_test.py
"""
import hashlib
import hmac
import json
import sys
import time

from mgx.overlay import MgxClient, MgxSignatureError, verify_webhook

SECRET = "whsec_test"
BODY = json.dumps(
    {
        "id": "evt_6Yh2",
        "type": "trade.created",
        "created_at": "2026-06-18T00:00:00Z",
        "data": {"id": "trd_1"},
    }
)
T = int(time.time())


def sign(ts: int, body: str, secret: str) -> str:
    digest = hmac.new(secret.encode(), f"{ts}.{body}".encode(), hashlib.sha256).hexdigest()
    return f"t={ts},v1={digest}"


failures = 0


def check(name, fn):
    global failures
    try:
        fn()
        print(f"  ok  {name}")
    except Exception as e:  # noqa: BLE001
        failures += 1
        print(f"  FAIL {name}: {e}")


def expect_raises(fn):
    try:
        fn()
    except MgxSignatureError:
        return
    raise AssertionError("expected MgxSignatureError, none raised")


def t_valid():
    evt = verify_webhook(BODY, sign(T, BODY, SECRET), SECRET)
    assert evt.type == "trade.created", evt.type
    assert evt.id == "evt_6Yh2", evt.id


def t_client_verify():
    mgx = MgxClient(access_token="x")
    evt = mgx.webhooks.verify(BODY, sign(T, BODY, SECRET), SECRET)
    assert evt.type == "trade.created", evt.type


def t_tampered():
    expect_raises(lambda: verify_webhook(BODY + " ", sign(T, BODY, SECRET), SECRET))


def t_wrong_secret():
    expect_raises(lambda: verify_webhook(BODY, sign(T, BODY, SECRET), "whsec_wrong"))


def t_stale():
    expect_raises(lambda: verify_webhook(BODY, sign(T - 9999, BODY, SECRET), SECRET))


def t_malformed():
    expect_raises(lambda: verify_webhook(BODY, "garbage", SECRET))


check("valid signature returns the typed event", t_valid)
check("client.webhooks.verify works", t_client_verify)
check("tampered body is rejected", t_tampered)
check("wrong secret is rejected", t_wrong_secret)
check("stale timestamp is rejected", t_stale)
check("malformed header is rejected", t_malformed)

print("\nAll webhook-verify checks passed." if failures == 0 else f"\n{failures} failed.")
sys.exit(0 if failures == 0 else 1)
