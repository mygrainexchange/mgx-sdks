/* Hand-written overlay — not generated. */

/** Minimal shape of the API's offset-pagination envelope. */
export interface PageLike<T> {
  items?: T[]
  next?: string | null
}

/**
 * Lazily yields every item across pages, following the envelope's `next` link
 * until it is null. Each page is fetched on demand, so `for await` over a large
 * result set never buffers the whole collection.
 */
export async function* paginate<T>(
  fetchPage: (offset: number) => Promise<PageLike<T>>,
): AsyncGenerator<T, void, unknown> {
  let offset = 0
  for (;;) {
    const page = await fetchPage(offset)
    const items = page.items ?? []
    for (const item of items) {
      yield item
    }
    if (!page.next || items.length === 0) {
      return
    }
    offset += items.length
  }
}

/** Collects an async iterator into an array. Convenience for small result sets. */
export async function collect<T>(iter: AsyncIterable<T>): Promise<T[]> {
  const out: T[] = []
  for await (const item of iter) {
    out.push(item)
  }
  return out
}
