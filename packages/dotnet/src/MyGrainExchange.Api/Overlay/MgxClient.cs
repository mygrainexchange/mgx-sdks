/* Hand-written overlay — not generated. */
using System;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using MyGrainExchange.Api.Api;
using MyGrainExchange.Api.Client;
using MyGrainExchange.Api.Model;

namespace MyGrainExchange.Api.Overlay
{
    /// <summary>Construction options for <see cref="MgxClient"/>.</summary>
    public sealed class MgxClientOptions : AuthOptions
    {
        /// <summary>API base URL. Defaults to production; set to your Herd URL for local dev.</summary>
        public string BaseUrl { get; set; }
    }

    /// <summary>
    /// Query filters for <see cref="MgxClient.InventoryResource.ListAsync"/>. Mirrors the
    /// query parameters the csharp generator flattened onto <c>InventoryListAsync</c>.
    /// All fields are optional; <c>offset</c> is managed by pagination.
    /// </summary>
    public sealed class InventoryListFilters
    {
        public string Commodity { get; set; }
        public string Grade { get; set; }
        public decimal? MinQuantity { get; set; }
        public decimal? MaxQuantity { get; set; }
        public string Province { get; set; }
        public string Near { get; set; }
        public int? CropYear { get; set; }
        public bool? IsOrganic { get; set; }
        public decimal? MinProtein { get; set; }
        public string Sort { get; set; }
        public int? Limit { get; set; }
    }

    /// <summary>
    /// The MGX Enterprise API client. Wraps the generated resource APIs with OAuth2
    /// token handling, auto-pagination, idempotency keys, and typed errors.
    /// </summary>
    public sealed class MgxClient
    {
        public const string DefaultBaseUrl = "https://api.mygrainexchange.com/v1";

        private readonly Configuration _config;
        private readonly TokenManager _tokens;

        private readonly InventoryApi _inventoryApi;
        private readonly MarketApi _marketApi;
        private readonly BidsApi _bidsApi;
        private readonly TradesApi _tradesApi;
        private readonly TeamsApi _teamsApi;
        private readonly CashBidsApi _cashBidsApi;
        private readonly WebhooksApi _webhooksApi;

        public InventoryResource Inventory { get; }
        public MarketResource Market { get; }
        public BidsResource Bids { get; }
        public TradesResource Trades { get; }
        public TeamsResource Teams { get; }
        public CashBidsResource CashBids { get; }
        public WebhooksResource Webhooks { get; }

        public MgxClient(MgxClientOptions options)
        {
            if (options == null) throw new ArgumentNullException(nameof(options));

            var baseUrl = string.IsNullOrEmpty(options.BaseUrl) ? DefaultBaseUrl : options.BaseUrl;
            _tokens = new TokenManager(options, baseUrl);
            _config = new Configuration { BasePath = baseUrl };

            _inventoryApi = new InventoryApi(_config);
            _marketApi = new MarketApi(_config);
            _bidsApi = new BidsApi(_config);
            _tradesApi = new TradesApi(_config);
            _teamsApi = new TeamsApi(_config);
            _cashBidsApi = new CashBidsApi(_config);
            _webhooksApi = new WebhooksApi(_config);

            Inventory = new InventoryResource(this);
            Market = new MarketResource(this);
            Bids = new BidsResource(this);
            Trades = new TradesResource(this);
            Teams = new TeamsResource(this);
            CashBids = new CashBidsResource(this);
            Webhooks = new WebhooksResource(this);
        }

        /// <summary>
        /// Refreshes <see cref="Configuration.AccessToken"/> from the <see cref="TokenManager"/>
        /// before each call. The generated client prepends "Bearer " to AccessToken when building
        /// the Authorization header, so we assign the RAW token here (NOT "Bearer &lt;token&gt;").
        /// </summary>
        private async Task EnsureAuthAsync(CancellationToken cancellationToken)
        {
            _config.AccessToken = await _tokens.GetAccessTokenAsync(cancellationToken).ConfigureAwait(false);
        }

        /// <summary>Runs an API call, mapping the generated <see cref="ApiException"/> to <see cref="MgxApiError"/>.</summary>
        private async Task<T> CallAsync<T>(Func<Task<T>> op, CancellationToken cancellationToken)
        {
            await EnsureAuthAsync(cancellationToken).ConfigureAwait(false);
            try
            {
                return await op().ConfigureAwait(false);
            }
            catch (ApiException ex)
            {
                throw MgxApiError.FromApiException(ex);
            }
        }

        private static string NewIdempotencyKey() => Guid.NewGuid().ToString();

        // ---- Resource: Inventory -------------------------------------------------

        public sealed class InventoryResource
        {
            private readonly MgxClient _c;
            internal InventoryResource(MgxClient c) => _c = c;

            /// <summary>Browse and filter anonymized inventory, following pagination lazily.</summary>
            public IAsyncEnumerable<Inventory> ListAsync(InventoryListFilters filters = null, CancellationToken cancellationToken = default)
            {
                var f = filters ?? new InventoryListFilters();
                return Pagination.PaginateAsync(
                    (offset, ct) => _c.CallAsync(() => _c._inventoryApi.InventoryListAsync(
                        f.Commodity, f.Grade, f.MinQuantity, f.MaxQuantity, f.Province, f.Near,
                        f.CropYear, f.IsOrganic, f.MinProtein, f.Sort, f.Limit, offset, ct), ct),
                    page => (IReadOnlyList<Inventory>)page.Items,
                    page => page.Next,
                    cancellationToken);
            }

            public Task<Inventory> GetAsync(string id, CancellationToken cancellationToken = default) =>
                _c.CallAsync(async () => (await _c._inventoryApi.InventoryGetAsync(id, cancellationToken)).Data, cancellationToken);

            /// <summary>Returns the available filter values (commodities, grades, provinces, crop years).</summary>
            public Task<InventoryFilters> FiltersAsync(CancellationToken cancellationToken = default) =>
                _c.CallAsync(() => _c._inventoryApi.InventoryFiltersAsync(cancellationToken), cancellationToken);

            public Task<Bid> PlaceBidAsync(string id, PlaceBid body, string idempotencyKey = null, CancellationToken cancellationToken = default) =>
                _c.CallAsync(async () => (await _c._bidsApi.BidPlaceAsync(
                    id, body, idempotencyKey ?? NewIdempotencyKey(), cancellationToken)).Data, cancellationToken);
        }

        // ---- Resource: Market ----------------------------------------------------

        public sealed class MarketResource
        {
            private readonly MgxClient _c;
            internal MarketResource(MgxClient c) => _c = c;

            public Task<List<MarketCommodity>> CommoditiesAsync(CancellationToken cancellationToken = default) =>
                _c.CallAsync(async () => (await _c._marketApi.MarketCommoditiesAsync(cancellationToken)).Items ?? new List<MarketCommodity>(), cancellationToken);

            public Task<List<MarketPrice>> PricesAsync(string commodity = null, DateOnly? date = null, CancellationToken cancellationToken = default) =>
                _c.CallAsync(async () => (await _c._marketApi.MarketPricesAsync(commodity, date, cancellationToken)).Items ?? new List<MarketPrice>(), cancellationToken);

            public Task<PriceHistory> HistoryAsync(string commodity, DateOnly? from = null, DateOnly? to = null, string interval = null, CancellationToken cancellationToken = default) =>
                _c.CallAsync(() => _c._marketApi.MarketHistoryAsync(commodity, from, to, interval, cancellationToken), cancellationToken);
        }

        // ---- Resource: Bids ------------------------------------------------------

        public sealed class BidsResource
        {
            private readonly MgxClient _c;
            internal BidsResource(MgxClient c) => _c = c;

            public IAsyncEnumerable<Bid> ListAsync(string status = null, int? limit = null, CancellationToken cancellationToken = default) =>
                Pagination.PaginateAsync(
                    (offset, ct) => _c.CallAsync(() => _c._bidsApi.BidListAsync(status, limit, offset, ct), ct),
                    page => (IReadOnlyList<Bid>)page.Items,
                    page => page.Next,
                    cancellationToken);

            public Task<Bid> GetAsync(string id, CancellationToken cancellationToken = default) =>
                _c.CallAsync(async () => (await _c._bidsApi.BidGetAsync(id, cancellationToken)).Data, cancellationToken);

            public Task<Trade> AcceptAsync(string id, CancellationToken cancellationToken = default) =>
                _c.CallAsync(async () => (await _c._bidsApi.BidAcceptAsync(id, cancellationToken)).Data, cancellationToken);

            public Task<Bid> RejectAsync(string id, CancellationToken cancellationToken = default) =>
                _c.CallAsync(async () => (await _c._bidsApi.BidRejectAsync(id, cancellationToken)).Data, cancellationToken);

            public Task<Bid> CounterAsync(string id, PlaceBid body, CancellationToken cancellationToken = default) =>
                _c.CallAsync(async () => (await _c._bidsApi.BidCounterAsync(id, body, cancellationToken)).Data, cancellationToken);
        }

        // ---- Resource: Trades ----------------------------------------------------

        public sealed class TradesResource
        {
            private readonly MgxClient _c;
            internal TradesResource(MgxClient c) => _c = c;

            public IAsyncEnumerable<Trade> ListAsync(string status = null, string commodity = null, DateOnly? from = null, DateOnly? to = null, int? limit = null, CancellationToken cancellationToken = default) =>
                Pagination.PaginateAsync(
                    (offset, ct) => _c.CallAsync(() => _c._tradesApi.TradeListAsync(status, commodity, from, to, limit, offset, ct), ct),
                    page => (IReadOnlyList<Trade>)page.Items,
                    page => page.Next,
                    cancellationToken);

            public Task<Trade> GetAsync(string id, CancellationToken cancellationToken = default) =>
                _c.CallAsync(async () => (await _c._tradesApi.TradeGetAsync(id, cancellationToken)).Data, cancellationToken);
        }

        // ---- Resource: Teams -----------------------------------------------------

        public sealed class TeamsResource
        {
            private readonly MgxClient _c;
            internal TeamsResource(MgxClient c) => _c = c;

            public Task<List<Team>> ListAsync(CancellationToken cancellationToken = default) =>
                _c.CallAsync(async () => (await _c._teamsApi.TeamListAsync(cancellationToken)).Items ?? new List<Team>(), cancellationToken);

            public Task<Team> GetAsync(string id, CancellationToken cancellationToken = default) =>
                _c.CallAsync(async () => (await _c._teamsApi.TeamGetAsync(id, cancellationToken)).Data, cancellationToken);
        }

        // ---- Resource: CashBids --------------------------------------------------

        public sealed class CashBidsResource
        {
            private readonly MgxClient _c;
            internal CashBidsResource(MgxClient c) => _c = c;

            public IAsyncEnumerable<CashBid> ListAsync(bool? isActive = null, string commodity = null, int? limit = null, CancellationToken cancellationToken = default) =>
                Pagination.PaginateAsync(
                    (offset, ct) => _c.CallAsync(() => _c._cashBidsApi.CashBidListAsync(isActive, commodity, limit, offset, ct), ct),
                    page => (IReadOnlyList<CashBid>)page.Items,
                    page => page.Next,
                    cancellationToken);

            public Task<CashBid> CreateAsync(StoreCashBid body, CancellationToken cancellationToken = default) =>
                _c.CallAsync(async () => (await _c._cashBidsApi.CashBidCreateAsync(body, cancellationToken)).Data, cancellationToken);

            public Task<CashBid> UpdateAsync(string id, UpdateCashBid body, CancellationToken cancellationToken = default) =>
                _c.CallAsync(async () => (await _c._cashBidsApi.CashBidUpdateAsync(id, body, cancellationToken)).Data, cancellationToken);

            public IAsyncEnumerable<CashBidOffer> OffersAsync(string cashBidId, string status = null, int? limit = null, CancellationToken cancellationToken = default) =>
                Pagination.PaginateAsync(
                    (offset, ct) => _c.CallAsync(() => _c._cashBidsApi.CashBidOffersAsync(cashBidId, status, limit, offset, ct), ct),
                    page => (IReadOnlyList<CashBidOffer>)page.Items,
                    page => page.Next,
                    cancellationToken);

            public Task<Trade> AcceptOfferAsync(string offerId, CancellationToken cancellationToken = default) =>
                _c.CallAsync(async () => (await _c._cashBidsApi.CashBidOfferAcceptAsync(offerId, cancellationToken)).Data, cancellationToken);

            public Task<CashBidOffer> RejectOfferAsync(string offerId, CancellationToken cancellationToken = default) =>
                _c.CallAsync(async () => (await _c._cashBidsApi.CashBidOfferRejectAsync(offerId, cancellationToken)).Data, cancellationToken);
        }

        // ---- Resource: Webhooks --------------------------------------------------

        public sealed class WebhooksResource
        {
            private readonly MgxClient _c;
            internal WebhooksResource(MgxClient c) => _c = c;

            public Task<List<WebhookResource>> ListAsync(CancellationToken cancellationToken = default) =>
                _c.CallAsync(async () => (await _c._webhooksApi.WebhookListAsync(cancellationToken)).Items ?? new List<WebhookResource>(), cancellationToken);

            public Task<WebhookCreated> CreateAsync(Webhook body, CancellationToken cancellationToken = default) =>
                _c.CallAsync(async () => (await _c._webhooksApi.WebhookCreateAsync(body, cancellationToken)).Data, cancellationToken);

            public Task DeleteAsync(string id, CancellationToken cancellationToken = default) =>
                _c.CallAsync(async () => { await _c._webhooksApi.WebhookDeleteAsync(id, cancellationToken); return true; }, cancellationToken);

            public IAsyncEnumerable<WebhookDelivery> DeliveriesAsync(string webhookId, int? limit = null, CancellationToken cancellationToken = default) =>
                Pagination.PaginateAsync(
                    (offset, ct) => _c.CallAsync(() => _c._webhooksApi.WebhookDeliveriesAsync(webhookId, limit, offset, ct), ct),
                    page => (IReadOnlyList<WebhookDelivery>)page.Items,
                    page => page.Next,
                    cancellationToken);

            /// <summary>
            /// Verify an inbound webhook's signature and return the typed event. Pass the
            /// raw request body, the <c>MGX-Signature</c> header, and the subscription secret.
            /// Throws <see cref="MgxSignatureError"/> on a bad signature or stale timestamp.
            /// </summary>
            public WebhookEvent Verify(string rawBody, string signatureHeader, string secret,
                int toleranceSeconds = WebhookVerifier.DefaultToleranceSeconds, long? now = null) =>
                WebhookVerifier.Verify(rawBody, signatureHeader, secret, toleranceSeconds, now);
        }
    }
}
