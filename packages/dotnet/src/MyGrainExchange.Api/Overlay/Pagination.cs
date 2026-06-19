/* Hand-written overlay — not generated. */
using System;
using System.Collections.Generic;
using System.Runtime.CompilerServices;
using System.Threading;
using System.Threading.Tasks;

namespace MyGrainExchange.Api.Overlay
{
    /// <summary>Minimal view of the API's offset-pagination envelope.</summary>
    public interface IPageLike<T>
    {
        IReadOnlyList<T> PageItems { get; }
        string PageNext { get; }
    }

    internal static class Pagination
    {
        /// <summary>
        /// Lazily yields every item across pages, following the envelope's <c>next</c> link
        /// until it is null. Each page is fetched on demand, so iterating a large result set
        /// never buffers the whole collection.
        /// </summary>
        public static async IAsyncEnumerable<T> PaginateAsync<TPage, T>(
            Func<int, CancellationToken, Task<TPage>> fetchPage,
            Func<TPage, IReadOnlyList<T>> getItems,
            Func<TPage, string> getNext,
            [EnumeratorCancellation] CancellationToken cancellationToken = default)
        {
            var offset = 0;
            while (true)
            {
                var page = await fetchPage(offset, cancellationToken).ConfigureAwait(false);
                var items = getItems(page) ?? Array.Empty<T>();
                foreach (var item in items)
                    yield return item;

                if (string.IsNullOrEmpty(getNext(page)) || items.Count == 0)
                    yield break;

                offset += items.Count;
            }
        }
    }

    /// <summary>Convenience extensions over the paginated streams returned by the client.</summary>
    public static class AsyncEnumerableExtensions
    {
        /// <summary>Collects an async stream into a list. Use only for small result sets.</summary>
        public static async Task<List<T>> ToListAsync<T>(
            this IAsyncEnumerable<T> source, CancellationToken cancellationToken = default)
        {
            var list = new List<T>();
            await foreach (var item in source.WithCancellation(cancellationToken).ConfigureAwait(false))
                list.Add(item);
            return list;
        }
    }
}
