/* Hand-written overlay — not generated. */
package com.mygrainexchange.mgx.overlay;

import com.mygrainexchange.mgx.ApiClient;
import com.mygrainexchange.mgx.ApiException;
import com.mygrainexchange.mgx.api.BidsApi;
import com.mygrainexchange.mgx.api.CashBidsApi;
import com.mygrainexchange.mgx.api.InventoryApi;
import com.mygrainexchange.mgx.api.MarketApi;
import com.mygrainexchange.mgx.api.TeamsApi;
import com.mygrainexchange.mgx.api.TradesApi;
import com.mygrainexchange.mgx.api.WebhooksApi;
import com.mygrainexchange.mgx.model.Bid;
import com.mygrainexchange.mgx.model.BidPage;
import com.mygrainexchange.mgx.model.CashBid;
import com.mygrainexchange.mgx.model.CashBidOffer;
import com.mygrainexchange.mgx.model.CashBidOfferPage;
import com.mygrainexchange.mgx.model.CashBidPage;
import com.mygrainexchange.mgx.model.Inventory;
import com.mygrainexchange.mgx.model.InventoryPage;
import com.mygrainexchange.mgx.model.InventoryFilters;
import com.mygrainexchange.mgx.model.MarketCommodity;
import com.mygrainexchange.mgx.model.MarketPrice;
import com.mygrainexchange.mgx.model.PlaceBid;
import com.mygrainexchange.mgx.model.PriceHistory;
import com.mygrainexchange.mgx.model.StoreCashBid;
import com.mygrainexchange.mgx.model.Team;
import com.mygrainexchange.mgx.model.Trade;
import com.mygrainexchange.mgx.model.TradePage;
import com.mygrainexchange.mgx.model.UpdateCashBid;
import com.mygrainexchange.mgx.model.Webhook;
import com.mygrainexchange.mgx.model.WebhookCreated;
import com.mygrainexchange.mgx.model.WebhookDelivery;
import com.mygrainexchange.mgx.model.WebhookDeliveryPage;
import com.mygrainexchange.mgx.model.WebhookEvent;
import com.mygrainexchange.mgx.model.WebhookResource;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The MGX Enterprise API client. Wraps the generated resource APIs with OAuth2
 * token handling, auto-pagination, idempotency keys, and typed errors.
 *
 * <pre>{@code
 * MgxClient mgx = MgxClient.builder()
 *     .clientId("...")
 *     .clientSecret("...")
 *     .scopes("inventory.read", "bids.write")
 *     .build();
 *
 * for (Inventory lot : mgx.inventory().list(new InventoryListFilters().commodity("wheat"))) {
 *     System.out.println(lot.getId());
 * }
 * }</pre>
 */
public final class MgxClient {

    public static final String DEFAULT_BASE_URL = "https://api.mygrainexchange.com/v1";

    /** OAuth2 + endpoint configuration shared with {@link TokenManager}. */
    public static final class AuthOptions {
        public String clientId;
        public String clientSecret;
        public List<String> scopes;
        public String accessToken;
        public String refreshToken;
        public String tokenUrl;
        public String baseUrl;
    }

    private final InventoryApi inventoryApi;
    private final MarketApi marketApi;
    private final BidsApi bidsApi;
    private final TradesApi tradesApi;
    private final TeamsApi teamsApi;
    private final CashBidsApi cashBidsApi;
    private final WebhooksApi webhooksApi;

    private final Inventory$ inventory = new Inventory$();
    private final Market$ market = new Market$();
    private final Bids$ bids = new Bids$();
    private final Trades$ trades = new Trades$();
    private final Teams$ teams = new Teams$();
    private final CashBids$ cashBids = new CashBids$();
    private final Webhooks$ webhooks = new Webhooks$();

    public MgxClient(AuthOptions options) {
        String baseUrl = options.baseUrl != null ? options.baseUrl : DEFAULT_BASE_URL;

        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(baseUrl);

        // Use a dedicated, un-intercepted OkHttp client for the token endpoint so
        // token requests never carry (or trigger fetching of) an Authorization header.
        final OkHttpClient tokenHttp = apiClient.getHttpClient().newBuilder().build();
        final TokenManager tokens = new TokenManager(options, baseUrl, tokenHttp);

        // The generated OAuth auth prepends "Bearer " itself when an access token is set,
        // but it only holds a static string. To get correct per-request refresh we own
        // the header via an interceptor and leave the generated auth's token unset.
        Interceptor authInterceptor = new Interceptor() {
            @Override
            public Response intercept(Interceptor.Chain chain) throws IOException {
                Request original = chain.request();
                Request authed = original.newBuilder()
                        .header("Authorization", "Bearer " + tokens.getAccessToken())
                        .build();
                return chain.proceed(authed);
            }
        };
        apiClient.setHttpClient(
                apiClient.getHttpClient().newBuilder().addInterceptor(authInterceptor).build());

        this.inventoryApi = new InventoryApi(apiClient);
        this.marketApi = new MarketApi(apiClient);
        this.bidsApi = new BidsApi(apiClient);
        this.tradesApi = new TradesApi(apiClient);
        this.teamsApi = new TeamsApi(apiClient);
        this.cashBidsApi = new CashBidsApi(apiClient);
        this.webhooksApi = new WebhooksApi(apiClient);
    }

    // ---- builder ----------------------------------------------------------

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final AuthOptions opts = new AuthOptions();

        public Builder clientId(String v) {
            opts.clientId = v;
            return this;
        }

        public Builder clientSecret(String v) {
            opts.clientSecret = v;
            return this;
        }

        public Builder scopes(String... v) {
            opts.scopes = java.util.Arrays.asList(v);
            return this;
        }

        public Builder scopes(List<String> v) {
            opts.scopes = v;
            return this;
        }

        public Builder accessToken(String v) {
            opts.accessToken = v;
            return this;
        }

        public Builder refreshToken(String v) {
            opts.refreshToken = v;
            return this;
        }

        public Builder tokenUrl(String v) {
            opts.tokenUrl = v;
            return this;
        }

        public Builder baseUrl(String v) {
            opts.baseUrl = v;
            return this;
        }

        public MgxClient build() {
            return new MgxClient(opts);
        }
    }

    // ---- internal helpers -------------------------------------------------

    private static String idempotencyKey(String supplied) {
        return supplied != null ? supplied : UUID.randomUUID().toString();
    }

    @FunctionalInterface
    private interface Call<R> {
        R run() throws ApiException;
    }

    /** Runs a generated call, mapping {@link ApiException} to {@link MgxApiError}. */
    private static <R> R unwrap(Call<R> call) {
        try {
            return call.run();
        } catch (ApiException e) {
            throw MgxApiError.from(e);
        }
    }

    // ---- resource accessors ----------------------------------------------

    /** Browse and filter anonymized inventory. */
    public Inventory$ inventory() {
        return inventory;
    }

    /** Market commodities, prices, and history. */
    public Market$ market() {
        return market;
    }

    /** Read the authenticated team's own bids and act on counter-offers. */
    public Bids$ bids() {
        return bids;
    }

    /** Read the authenticated team's own trades. */
    public Trades$ trades() {
        return trades;
    }

    /** Read the teams the authenticated user belongs to. */
    public Teams$ teams() {
        return teams;
    }

    /** Manage the elevator's own cash bids and the offers received on them. */
    public CashBids$ cashBids() {
        return cashBids;
    }

    /** Subscribe to event notifications and inspect deliveries. */
    public Webhooks$ webhooks() {
        return webhooks;
    }

    // ---- resource wrappers ------------------------------------------------

    /** Inventory resource. */
    public final class Inventory$ {
        public Pagination<Inventory> list() {
            return list(new InventoryListFilters());
        }

        public Pagination<Inventory> list(InventoryListFilters f) {
            final InventoryListFilters filters = f != null ? f : new InventoryListFilters();
            return new Pagination<>(offset -> {
                InventoryPage page = inventoryApi.inventoryList(
                        filters.commodity, filters.grade, filters.minQuantity, filters.maxQuantity,
                        filters.province, filters.near, filters.cropYear, filters.isOrganic,
                        filters.minProtein, filters.sort, filters.limit, offset, null);
                return new Pagination.PageLike<Inventory>() {
                    public List<Inventory> getItems() {
                        return page.getItems();
                    }

                    public String getNext() {
                        return page.getNext();
                    }
                };
            });
        }

        public Inventory get(String id) {
            return unwrap(() -> inventoryApi.inventoryGet(id, null)).getData();
        }

        public InventoryFilters filters() {
            return unwrap(inventoryApi::inventoryFilters);
        }

        public Bid placeBid(String id, PlaceBid body) {
            return placeBid(id, body, null);
        }

        public Bid placeBid(String id, PlaceBid body, String idempotencyKey) {
            return unwrap(() -> bidsApi.bidPlace(id, body, idempotencyKey(idempotencyKey))).getData();
        }
    }

    /** Market resource. */
    public final class Market$ {
        public List<MarketCommodity> commodities() {
            List<MarketCommodity> items = unwrap(() -> marketApi.marketCommodities(null)).getItems();
            return items != null ? items : Collections.emptyList();
        }

        public List<MarketPrice> prices() {
            return prices(null, null);
        }

        public List<MarketPrice> prices(String commodity, LocalDate date) {
            List<MarketPrice> items = unwrap(() -> marketApi.marketPrices(commodity, date, null)).getItems();
            return items != null ? items : Collections.emptyList();
        }

        public PriceHistory history(String commodity) {
            return history(commodity, null, null, null);
        }

        public PriceHistory history(String commodity, LocalDate from, LocalDate to, String interval) {
            return unwrap(() -> marketApi.marketHistory(commodity, from, to, interval, null));
        }
    }

    /** Bids resource. */
    public final class Bids$ {
        public Pagination<Bid> list() {
            return list(null);
        }

        public Pagination<Bid> list(String status) {
            return new Pagination<>(offset -> {
                BidPage page = bidsApi.bidList(status, null, offset, null);
                return new Pagination.PageLike<Bid>() {
                    public List<Bid> getItems() {
                        return page.getItems();
                    }

                    public String getNext() {
                        return page.getNext();
                    }
                };
            });
        }

        public Bid get(String id) {
            return unwrap(() -> bidsApi.bidGet(id, null)).getData();
        }

        public Trade accept(String id) {
            return unwrap(() -> bidsApi.bidAccept(id)).getData();
        }

        public Bid reject(String id) {
            return unwrap(() -> bidsApi.bidReject(id)).getData();
        }

        public Bid counter(String id, PlaceBid body) {
            return unwrap(() -> bidsApi.bidCounter(id, body)).getData();
        }
    }

    /** Trades resource. */
    public final class Trades$ {
        public Pagination<Trade> list() {
            return list(new TradeListFilters());
        }

        public Pagination<Trade> list(TradeListFilters f) {
            final TradeListFilters filters = f != null ? f : new TradeListFilters();
            return new Pagination<>(offset -> {
                TradePage page = tradesApi.tradeList(
                        filters.status, filters.commodity, filters.from, filters.to, filters.limit, offset, null);
                return new Pagination.PageLike<Trade>() {
                    public List<Trade> getItems() {
                        return page.getItems();
                    }

                    public String getNext() {
                        return page.getNext();
                    }
                };
            });
        }

        public Trade get(String id) {
            return unwrap(() -> tradesApi.tradeGet(id, null)).getData();
        }
    }

    /** Teams resource. */
    public final class Teams$ {
        public List<Team> list() {
            List<Team> items = unwrap(teamsApi::teamList).getItems();
            return items != null ? items : Collections.emptyList();
        }

        public Team get(String id) {
            return unwrap(() -> teamsApi.teamGet(id)).getData();
        }
    }

    /** Cash bids resource. */
    public final class CashBids$ {
        public Pagination<CashBid> list() {
            return list(new CashBidListFilters());
        }

        public Pagination<CashBid> list(CashBidListFilters f) {
            final CashBidListFilters filters = f != null ? f : new CashBidListFilters();
            return new Pagination<>(offset -> {
                CashBidPage page = cashBidsApi.cashBidList(filters.isActive, filters.commodity, filters.limit, offset, null);
                return new Pagination.PageLike<CashBid>() {
                    public List<CashBid> getItems() {
                        return page.getItems();
                    }

                    public String getNext() {
                        return page.getNext();
                    }
                };
            });
        }

        public CashBid create(StoreCashBid body) {
            return unwrap(() -> cashBidsApi.cashBidCreate(body)).getData();
        }

        public CashBid update(String id, UpdateCashBid body) {
            return unwrap(() -> cashBidsApi.cashBidUpdate(id, body)).getData();
        }

        public Pagination<CashBidOffer> offers(String cashBidId) {
            return offers(cashBidId, null);
        }

        public Pagination<CashBidOffer> offers(String cashBidId, String status) {
            return new Pagination<>(offset -> {
                CashBidOfferPage page = cashBidsApi.cashBidOffers(cashBidId, status, null, offset);
                return new Pagination.PageLike<CashBidOffer>() {
                    public List<CashBidOffer> getItems() {
                        return page.getItems();
                    }

                    public String getNext() {
                        return page.getNext();
                    }
                };
            });
        }

        public Trade acceptOffer(String offerId) {
            return unwrap(() -> cashBidsApi.cashBidOfferAccept(offerId)).getData();
        }

        public CashBidOffer rejectOffer(String offerId) {
            return unwrap(() -> cashBidsApi.cashBidOfferReject(offerId)).getData();
        }
    }

    /** Webhooks resource. */
    public final class Webhooks$ {
        public List<WebhookResource> list() {
            List<WebhookResource> items = unwrap(webhooksApi::webhookList).getItems();
            return items != null ? items : Collections.emptyList();
        }

        public WebhookCreated create(Webhook body) {
            return unwrap(() -> webhooksApi.webhookCreate(body)).getData();
        }

        public void delete(String id) {
            unwrap(() -> webhooksApi.webhookDelete(id));
        }

        public Pagination<WebhookDelivery> deliveries(String webhookId) {
            return new Pagination<>(offset -> {
                WebhookDeliveryPage page = webhooksApi.webhookDeliveries(webhookId, null, offset);
                return new Pagination.PageLike<WebhookDelivery>() {
                    public List<WebhookDelivery> getItems() {
                        return page.getItems();
                    }

                    public String getNext() {
                        return page.getNext();
                    }
                };
            });
        }

        /**
         * Verify an inbound webhook's signature and return the typed event. Pass the
         * raw request body, the {@code MGX-Signature} header, and the subscription
         * secret. Uses the default tolerance (300 seconds) and the system clock.
         *
         * @throws WebhookVerifier.MgxSignatureError on a bad signature or stale timestamp
         */
        public WebhookEvent verify(String rawBody, String signatureHeader, String secret) {
            return WebhookVerifier.verify(rawBody, signatureHeader, secret);
        }

        /**
         * Verify an inbound webhook's signature and return the typed event, with an
         * explicit tolerance window and optional clock override.
         *
         * @param toleranceSeconds max age of the signed timestamp, in seconds
         * @param now              override for "now" (unix seconds); pass {@code null} for the system clock
         * @throws WebhookVerifier.MgxSignatureError on a bad signature or stale timestamp
         */
        public WebhookEvent verify(
                String rawBody, String signatureHeader, String secret, int toleranceSeconds, Long now) {
            return WebhookVerifier.verify(rawBody, signatureHeader, secret, toleranceSeconds, now);
        }
    }

    // ---- filter holders (overlay-only request params) ---------------------

    /** Fluent filters for {@link Inventory$#list(InventoryListFilters)}. */
    public static final class InventoryListFilters {
        String commodity;
        String grade;
        BigDecimal minQuantity;
        BigDecimal maxQuantity;
        String province;
        String near;
        Integer cropYear;
        Boolean isOrganic;
        BigDecimal minProtein;
        String sort;
        Integer limit;

        public InventoryListFilters commodity(String v) { this.commodity = v; return this; }
        public InventoryListFilters grade(String v) { this.grade = v; return this; }
        public InventoryListFilters minQuantity(BigDecimal v) { this.minQuantity = v; return this; }
        public InventoryListFilters maxQuantity(BigDecimal v) { this.maxQuantity = v; return this; }
        public InventoryListFilters province(String v) { this.province = v; return this; }
        public InventoryListFilters near(String v) { this.near = v; return this; }
        public InventoryListFilters cropYear(Integer v) { this.cropYear = v; return this; }
        public InventoryListFilters isOrganic(Boolean v) { this.isOrganic = v; return this; }
        public InventoryListFilters minProtein(BigDecimal v) { this.minProtein = v; return this; }
        public InventoryListFilters sort(String v) { this.sort = v; return this; }
        public InventoryListFilters limit(Integer v) { this.limit = v; return this; }
    }

    /** Fluent filters for {@link Trades$#list(TradeListFilters)}. */
    public static final class TradeListFilters {
        String status;
        String commodity;
        LocalDate from;
        LocalDate to;
        Integer limit;

        public TradeListFilters status(String v) { this.status = v; return this; }
        public TradeListFilters commodity(String v) { this.commodity = v; return this; }
        public TradeListFilters from(LocalDate v) { this.from = v; return this; }
        public TradeListFilters to(LocalDate v) { this.to = v; return this; }
        public TradeListFilters limit(Integer v) { this.limit = v; return this; }
    }

    /** Fluent filters for {@link CashBids$#list(CashBidListFilters)}. */
    public static final class CashBidListFilters {
        Boolean isActive;
        String commodity;
        Integer limit;

        public CashBidListFilters isActive(Boolean v) { this.isActive = v; return this; }
        public CashBidListFilters commodity(String v) { this.commodity = v; return this; }
        public CashBidListFilters limit(Integer v) { this.limit = v; return this; }
    }
}
