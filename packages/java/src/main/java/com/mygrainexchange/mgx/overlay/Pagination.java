/* Hand-written overlay — not generated. */
package com.mygrainexchange.mgx.overlay;

import com.mygrainexchange.mgx.ApiException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Lazily iterates every item across pages, following the envelope's {@code next}
 * link until it is null. Each page is fetched on demand, so iterating a large
 * result set never buffers the whole collection.
 *
 * @param <T> the item type
 */
public final class Pagination<T> implements Iterable<T> {

    /** Minimal view of the offset-pagination envelope ({@code items} + {@code next}). */
    public interface PageLike<T> {
        List<T> getItems();

        String getNext();
    }

    /** Fetches one page given a zero-based offset. */
    public interface PageFetcher<T> {
        PageLike<T> fetch(int offset) throws ApiException;
    }

    private final PageFetcher<T> fetcher;

    public Pagination(PageFetcher<T> fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private int offset = 0;
            private Iterator<T> current = java.util.Collections.<T>emptyList().iterator();
            private boolean exhausted = false;

            private void advance() {
                while (!current.hasNext() && !exhausted) {
                    PageLike<T> page;
                    try {
                        page = fetcher.fetch(offset);
                    } catch (ApiException e) {
                        throw MgxApiError.from(e);
                    }
                    List<T> items = page.getItems() != null ? page.getItems() : java.util.Collections.<T>emptyList();
                    current = items.iterator();
                    if (page.getNext() == null || items.isEmpty()) {
                        exhausted = true;
                    } else {
                        offset += items.size();
                    }
                }
            }

            @Override
            public boolean hasNext() {
                advance();
                return current.hasNext();
            }

            @Override
            public T next() {
                advance();
                if (!current.hasNext()) {
                    throw new NoSuchElementException();
                }
                return current.next();
            }
        };
    }

    /** A sequential {@link Stream} over the same lazy iteration. */
    public Stream<T> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    /** Eagerly collects every item into a list. Convenience for small result sets. */
    public List<T> toList() {
        List<T> out = new ArrayList<>();
        for (T item : this) {
            out.add(item);
        }
        return out;
    }
}
