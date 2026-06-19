<?php

/* Hand-written overlay — not generated. */

declare(strict_types=1);

namespace MyGrainExchange\Mgx\Overlay;

use MyGrainExchange\Mgx\Api\BidsApi;
use MyGrainExchange\Mgx\Api\CashBidsApi;
use MyGrainExchange\Mgx\Api\InventoryApi;
use MyGrainExchange\Mgx\Api\MarketApi;
use MyGrainExchange\Mgx\Api\TeamsApi;
use MyGrainExchange\Mgx\Api\TradesApi;
use MyGrainExchange\Mgx\Api\WebhooksApi;
use MyGrainExchange\Mgx\ApiException;
use MyGrainExchange\Mgx\Configuration;

/**
 * The MGX Enterprise API client. Wraps the generated resource APIs with OAuth2
 * token handling, auto-pagination, idempotency keys, and typed errors.
 *
 * Resource namespaces are exposed via accessor methods that mirror the other
 * MGX SDKs: {@see inventory()}, {@see market()}, {@see bids()}, {@see trades()},
 * {@see teams()}, {@see cashBids()}, {@see webhooks()}.
 */
class MgxClient
{
    public const DEFAULT_BASE_URL = 'https://api.mygrainexchange.com/v1';

    private InventoryResource $inventory;
    private MarketResource $market;
    private BidsResource $bids;
    private TradesResource $trades;
    private TeamsResource $teams;
    private CashBidsResource $cashBids;
    private WebhooksResource $webhooks;

    /**
     * @param string|null       $clientId     OAuth2 client id (client-credentials or auth-code app).
     * @param string|null       $clientSecret OAuth2 client secret.
     * @param list<string>      $scopes       Scopes to request on a client-credentials grant.
     * @param string|null       $accessToken  A pre-obtained access token (e.g. from Login-with-MGX).
     * @param string|null       $refreshToken A refresh token to renew an auth-code access token.
     * @param string            $baseUrl      API base URL. Defaults to production.
     * @param string|null       $tokenUrl     Override the token endpoint (defaults to `<host>/oauth/token`).
     */
    public function __construct(
        ?string $clientId = null,
        ?string $clientSecret = null,
        array $scopes = [],
        ?string $accessToken = null,
        ?string $refreshToken = null,
        string $baseUrl = self::DEFAULT_BASE_URL,
        ?string $tokenUrl = null,
    ) {
        $tokens = new TokenManager(
            [
                'clientId' => $clientId,
                'clientSecret' => $clientSecret,
                'scopes' => $scopes,
                'accessToken' => $accessToken,
                'refreshToken' => $refreshToken,
                'tokenUrl' => $tokenUrl,
            ],
            $baseUrl,
        );

        // GOTCHA: the generated Api classes build the header as
        //   'Authorization: Bearer ' . $config->getAccessToken()
        // i.e. they add the "Bearer " prefix themselves. So the Configuration must
        // return the RAW token (not "Bearer <token>"), or the header would become
        // "Bearer Bearer <token>". TokenAwareConfiguration resolves the token lazily
        // on each getAccessToken() call so refreshes are picked up per request.
        $config = new TokenAwareConfiguration($tokens);
        $config->setHost(rtrim($baseUrl, '/'));

        $this->inventory = new InventoryResource(new InventoryApi(null, $config), new BidsApi(null, $config));
        $this->market = new MarketResource(new MarketApi(null, $config));
        $this->bids = new BidsResource(new BidsApi(null, $config));
        $this->trades = new TradesResource(new TradesApi(null, $config));
        $this->teams = new TeamsResource(new TeamsApi(null, $config));
        $this->cashBids = new CashBidsResource(new CashBidsApi(null, $config));
        $this->webhooks = new WebhooksResource(new WebhooksApi(null, $config));
    }

    /**
     * Convenience named-args factory mirroring the TS `new MgxClient({ ... })`
     * options object. Accepts the same keys as the constructor parameters.
     *
     * @param array{
     *   clientId?: string|null,
     *   clientSecret?: string|null,
     *   scopes?: list<string>,
     *   accessToken?: string|null,
     *   refreshToken?: string|null,
     *   baseUrl?: string,
     *   tokenUrl?: string|null
     * } $options
     */
    public static function create(array $options): self
    {
        return new self(
            clientId: $options['clientId'] ?? null,
            clientSecret: $options['clientSecret'] ?? null,
            scopes: $options['scopes'] ?? [],
            accessToken: $options['accessToken'] ?? null,
            refreshToken: $options['refreshToken'] ?? null,
            baseUrl: $options['baseUrl'] ?? self::DEFAULT_BASE_URL,
            tokenUrl: $options['tokenUrl'] ?? null,
        );
    }

    /** Browse and filter anonymized inventory. */
    public function inventory(): InventoryResource
    {
        return $this->inventory;
    }

    /** Market commodities, prices, and history. */
    public function market(): MarketResource
    {
        return $this->market;
    }

    /** Read the authenticated team's own bids and act on counter-offers. */
    public function bids(): BidsResource
    {
        return $this->bids;
    }

    /** Read the authenticated team's own trades. */
    public function trades(): TradesResource
    {
        return $this->trades;
    }

    /** Read the teams the authenticated user belongs to. */
    public function teams(): TeamsResource
    {
        return $this->teams;
    }

    /** Manage the elevator's own cash bids and the offers received on them. */
    public function cashBids(): CashBidsResource
    {
        return $this->cashBids;
    }

    /** Subscribe to event notifications and inspect deliveries. */
    public function webhooks(): WebhooksResource
    {
        return $this->webhooks;
    }
}

/**
 * A {@see Configuration} whose access token is resolved lazily from a
 * {@see TokenManager} on every read, so each request uses a fresh token.
 *
 * @internal
 */
final class TokenAwareConfiguration extends Configuration
{
    public function __construct(private readonly TokenManager $tokens)
    {
        parent::__construct();
    }

    public function getAccessToken()
    {
        // Returns the RAW token; the generated client adds the "Bearer " prefix.
        return $this->tokens->accessToken();
    }
}

/**
 * Shared helpers for resource wrappers.
 *
 * @internal
 */
trait ResourceHelpers
{
    /**
     * Run a generated API call, translating its ApiException into MgxApiError.
     *
     * @template R
     * @param callable(): R $call
     * @return R
     */
    private function call(callable $call)
    {
        try {
            return $call();
        } catch (ApiException $e) {
            throw MgxApiError::fromApiException($e);
        }
    }

    /** Generate a RFC 4122 version-4 UUID for idempotency keys. */
    private static function uuidV4(): string
    {
        $data = random_bytes(16);
        $data[6] = chr((ord($data[6]) & 0x0f) | 0x40);
        $data[8] = chr((ord($data[8]) & 0x3f) | 0x80);

        return vsprintf('%s%s-%s-%s-%s-%s%s%s', str_split(bin2hex($data), 4));
    }
}

/** @internal */
final class InventoryResource
{
    use ResourceHelpers;

    public function __construct(private readonly InventoryApi $api, private readonly BidsApi $bidsApi)
    {
    }

    /**
     * Auto-paginating list of anonymized inventory lots.
     *
     * @param array<string, mixed> $filters commodity, grade, minQuantity, maxQuantity,
     *                                       province, near, cropYear, isOrganic, minProtein,
     *                                       sort, limit
     * @return Paginator<\MyGrainExchange\Mgx\Model\Inventory>
     */
    public function list(array $filters = []): Paginator
    {
        return new Paginator(fn (int $offset) => $this->call(fn () => $this->api->inventoryList(
            $filters['commodity'] ?? null,
            $filters['grade'] ?? null,
            $filters['minQuantity'] ?? null,
            $filters['maxQuantity'] ?? null,
            $filters['province'] ?? null,
            $filters['near'] ?? null,
            $filters['cropYear'] ?? null,
            $filters['isOrganic'] ?? null,
            $filters['minProtein'] ?? null,
            $filters['sort'] ?? null,
            $filters['limit'] ?? 20,
            $offset,
        )));
    }

    public function get(string $id): ?\MyGrainExchange\Mgx\Model\Inventory
    {
        return $this->call(fn () => $this->api->inventoryGet($id))->getData();
    }

    public function filters(): \MyGrainExchange\Mgx\Model\InventoryFilters
    {
        return $this->call(fn () => $this->api->inventoryFilters());
    }

    /**
     * Place a bid on an inventory lot. Auto-generates an Idempotency-Key when one
     * is not supplied.
     *
     * @param array<string, mixed>|\MyGrainExchange\Mgx\Model\PlaceBid $body
     */
    public function placeBid(string $id, $body, ?string $idempotencyKey = null): ?\MyGrainExchange\Mgx\Model\Bid
    {
        $key = $idempotencyKey ?? self::uuidV4();

        return $this->call(fn () => $this->bidsApi->bidPlace($id, $body, $key))->getData();
    }
}

/** @internal */
final class MarketResource
{
    use ResourceHelpers;

    public function __construct(private readonly MarketApi $api)
    {
    }

    /** @return list<\MyGrainExchange\Mgx\Model\MarketCommodity> */
    public function commodities(): array
    {
        return $this->call(fn () => $this->api->marketCommodities())->getItems() ?? [];
    }

    /**
     * @param array<string, mixed> $params commodity, date
     * @return list<\MyGrainExchange\Mgx\Model\MarketPrice>
     */
    public function prices(array $params = []): array
    {
        return $this->call(fn () => $this->api->marketPrices(
            $params['commodity'] ?? null,
            $params['date'] ?? null,
        ))->getItems() ?? [];
    }

    /**
     * @param array<string, mixed> $params from, to, interval
     */
    public function history(string $commodity, array $params = []): \MyGrainExchange\Mgx\Model\PriceHistory
    {
        return $this->call(fn () => $this->api->marketHistory(
            $commodity,
            $params['from'] ?? null,
            $params['to'] ?? null,
            $params['interval'] ?? 'day',
        ));
    }
}

/** @internal */
final class BidsResource
{
    use ResourceHelpers;

    public function __construct(private readonly BidsApi $api)
    {
    }

    /**
     * @param array<string, mixed> $params status, limit
     * @return Paginator<\MyGrainExchange\Mgx\Model\Bid>
     */
    public function list(array $params = []): Paginator
    {
        return new Paginator(fn (int $offset) => $this->call(fn () => $this->api->bidList(
            $params['status'] ?? null,
            $params['limit'] ?? 20,
            $offset,
        )));
    }

    public function get(string $id): ?\MyGrainExchange\Mgx\Model\Bid
    {
        return $this->call(fn () => $this->api->bidGet($id))->getData();
    }

    public function accept(string $id): ?\MyGrainExchange\Mgx\Model\Trade
    {
        return $this->call(fn () => $this->api->bidAccept($id))->getData();
    }

    public function reject(string $id): ?\MyGrainExchange\Mgx\Model\Bid
    {
        return $this->call(fn () => $this->api->bidReject($id))->getData();
    }

    /**
     * @param array<string, mixed>|\MyGrainExchange\Mgx\Model\PlaceBid $body
     */
    public function counter(string $id, $body): ?\MyGrainExchange\Mgx\Model\Bid
    {
        return $this->call(fn () => $this->api->bidCounter($id, $body))->getData();
    }
}

/** @internal */
final class TradesResource
{
    use ResourceHelpers;

    public function __construct(private readonly TradesApi $api)
    {
    }

    /**
     * @param array<string, mixed> $params status, commodity, from, to, limit
     * @return Paginator<\MyGrainExchange\Mgx\Model\Trade>
     */
    public function list(array $params = []): Paginator
    {
        return new Paginator(fn (int $offset) => $this->call(fn () => $this->api->tradeList(
            $params['status'] ?? null,
            $params['commodity'] ?? null,
            $params['from'] ?? null,
            $params['to'] ?? null,
            $params['limit'] ?? 20,
            $offset,
        )));
    }

    public function get(string $id): ?\MyGrainExchange\Mgx\Model\Trade
    {
        return $this->call(fn () => $this->api->tradeGet($id))->getData();
    }
}

/** @internal */
final class TeamsResource
{
    use ResourceHelpers;

    public function __construct(private readonly TeamsApi $api)
    {
    }

    /** @return list<\MyGrainExchange\Mgx\Model\Team> */
    public function list(): array
    {
        return $this->call(fn () => $this->api->teamList())->getItems() ?? [];
    }

    public function get(string $id): ?\MyGrainExchange\Mgx\Model\Team
    {
        return $this->call(fn () => $this->api->teamGet($id))->getData();
    }
}

/** @internal */
final class CashBidsResource
{
    use ResourceHelpers;

    public function __construct(private readonly CashBidsApi $api)
    {
    }

    /**
     * @param array<string, mixed> $params isActive, commodity, limit
     * @return Paginator<\MyGrainExchange\Mgx\Model\CashBid>
     */
    public function list(array $params = []): Paginator
    {
        return new Paginator(fn (int $offset) => $this->call(fn () => $this->api->cashBidList(
            $params['isActive'] ?? null,
            $params['commodity'] ?? null,
            $params['limit'] ?? 20,
            $offset,
        )));
    }

    /**
     * @param array<string, mixed>|\MyGrainExchange\Mgx\Model\StoreCashBid $body
     */
    public function create($body): ?\MyGrainExchange\Mgx\Model\CashBid
    {
        return $this->call(fn () => $this->api->cashBidCreate($body))->getData();
    }

    /**
     * @param array<string, mixed>|\MyGrainExchange\Mgx\Model\UpdateCashBid $body
     */
    public function update(string $id, $body): ?\MyGrainExchange\Mgx\Model\CashBid
    {
        return $this->call(fn () => $this->api->cashBidUpdate($id, $body))->getData();
    }

    /**
     * @param array<string, mixed> $params status, limit
     * @return Paginator<\MyGrainExchange\Mgx\Model\CashBidOffer>
     */
    public function offers(string $cashBidId, array $params = []): Paginator
    {
        return new Paginator(fn (int $offset) => $this->call(fn () => $this->api->cashBidOffers(
            $cashBidId,
            $params['status'] ?? null,
            $params['limit'] ?? 20,
            $offset,
        )));
    }

    public function acceptOffer(string $offerId): ?\MyGrainExchange\Mgx\Model\Trade
    {
        return $this->call(fn () => $this->api->cashBidOfferAccept($offerId))->getData();
    }

    public function rejectOffer(string $offerId): ?\MyGrainExchange\Mgx\Model\CashBidOffer
    {
        return $this->call(fn () => $this->api->cashBidOfferReject($offerId))->getData();
    }
}

/** @internal */
final class WebhooksResource
{
    use ResourceHelpers;

    public function __construct(private readonly WebhooksApi $api)
    {
    }

    /** @return list<\MyGrainExchange\Mgx\Model\WebhookResource> */
    public function list(): array
    {
        return $this->call(fn () => $this->api->webhookList())->getItems() ?? [];
    }

    /**
     * @param array<string, mixed>|\MyGrainExchange\Mgx\Model\Webhook $body
     */
    public function create($body): ?\MyGrainExchange\Mgx\Model\WebhookCreated
    {
        return $this->call(fn () => $this->api->webhookCreate($body))->getData();
    }

    public function delete(string $id): void
    {
        $this->call(fn () => $this->api->webhookDelete($id));
    }

    /**
     * @param array<string, mixed> $params limit
     * @return Paginator<\MyGrainExchange\Mgx\Model\WebhookDelivery>
     */
    public function deliveries(string $webhookId, array $params = []): Paginator
    {
        return new Paginator(fn (int $offset) => $this->call(fn () => $this->api->webhookDeliveries(
            $webhookId,
            $params['limit'] ?? 20,
            $offset,
        )));
    }

    /**
     * Verify an inbound webhook's signature and return the typed event.
     *
     * Pass the EXACT raw request body (not a re-serialized object) and the value
     * of the `MGX-Signature` header. Throws {@see MgxSignatureError} on a bad
     * signature or a stale timestamp.
     *
     * @param int|null $now Override "now" (unix seconds) — for testing.
     *
     * @throws MgxSignatureError
     */
    public function verify(
        string $rawBody,
        string $signatureHeader,
        string $secret,
        int $toleranceSeconds = DEFAULT_WEBHOOK_TOLERANCE_SECONDS,
        ?int $now = null,
    ): \MyGrainExchange\Mgx\Model\WebhookEvent {
        return verifyWebhook($rawBody, $signatureHeader, $secret, $toleranceSeconds, $now);
    }
}
