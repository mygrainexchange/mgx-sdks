"""MGX Python SDK -- inbound webhook handler.

Verify the signature of an inbound webhook before trusting it, then branch on
the event type. Pass the EXACT raw request body (bytes/str as received) -- not a
re-serialized object -- so the HMAC matches the dashboard sender.

This snippet uses a generic request; in Flask/FastAPI read ``request.get_data()``
(Flask) or ``await request.body()`` (FastAPI) for the raw body, and the
``MGX-Signature`` header for the signature.
"""
import os

from mgx.overlay import MgxSignatureError, verify_webhook

WEBHOOK_SECRET = os.environ.get("MGX_WEBHOOK_SECRET", "whsec_...")


def handle(raw_body: str, signature_header: str) -> None:
    try:
        event = verify_webhook(raw_body, signature_header, WEBHOOK_SECRET)
    except MgxSignatureError as exc:
        # Reject the delivery with a 400 -- do NOT act on an unverified payload.
        print("rejected:", exc)
        return

    if event.type == "trade.created":
        print("new trade", event.data)
    elif event.type == "cashbid.offer_received":
        print("offer received", event.data)
    else:
        print("unhandled event", event.type)


if __name__ == "__main__":
    # Example values -- replace with the real request body and header.
    handle('{"id":"evt_1","type":"trade.created","data":{}}', "t=0,v1=deadbeef")
