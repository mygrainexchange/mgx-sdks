"""Hand-written overlay -- not generated.

Lazy offset-pagination over the API's ``{ items, next }`` envelope.
"""
from __future__ import annotations

from typing import Any, Callable, Iterator


def paginate(fetch_page: Callable[[int], Any]) -> Iterator[Any]:
    """Yield every item across pages, following the envelope's ``next`` link.

    ``fetch_page(offset)`` must return a page object exposing ``.items`` (a list)
    and ``.next`` (a falsy value once there are no more pages). Pages are fetched
    on demand, so iterating a large result set never buffers the whole collection.
    The offset advances by the number of items returned on each page.
    """
    offset = 0
    while True:
        page = fetch_page(offset)
        items = getattr(page, "items", None) or []
        for item in items:
            yield item
        next_link = getattr(page, "next", None)
        if not next_link or len(items) == 0:
            return
        offset += len(items)
