<?php

/* Hand-written overlay — not generated. */

declare(strict_types=1);

namespace MyGrainExchange\Mgx\Overlay;

/**
 * Lazily yields every item across pages, following the envelope's `next` link
 * until it is null. Each page is fetched on demand, so iterating a large result
 * set never buffers the whole collection.
 *
 * The page object is any generated `*Page` model exposing `getItems()` and
 * `getNext()` (e.g. {@see \MyGrainExchange\Mgx\Model\InventoryPage}).
 *
 * @template T
 * @implements \IteratorAggregate<int, T>
 */
class Paginator implements \IteratorAggregate
{
    /**
     * @param callable(int): object $fetchPage Fetches one page given an offset.
     */
    public function __construct(private $fetchPage)
    {
    }

    /**
     * @return \Generator<int, T>
     */
    public function getIterator(): \Generator
    {
        $offset = 0;
        while (true) {
            $page = ($this->fetchPage)($offset);
            $items = [];
            if (is_object($page) && method_exists($page, 'getItems')) {
                $items = $page->getItems() ?? [];
            }

            foreach ($items as $item) {
                yield $item;
            }

            $next = null;
            if (is_object($page) && method_exists($page, 'getNext')) {
                $next = $page->getNext();
            }

            if (empty($next) || count($items) === 0) {
                return;
            }
            $offset += count($items);
        }
    }

    /**
     * Eagerly collect every item into an array. Convenience for small result sets.
     *
     * @return list<T>
     */
    public function toArray(): array
    {
        return iterator_to_array($this->getIterator(), false);
    }
}
