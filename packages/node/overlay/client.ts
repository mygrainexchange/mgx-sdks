/* Hand-written overlay — not generated. */
import { Configuration } from '../src/runtime'
import {
  InventoryApi,
  MarketApi,
  BidsApi,
  TradesApi,
  TeamsApi,
  CashBidsApi,
  WebhooksApi,
} from '../src/apis'
import type {
  Inventory,
  InventoryFilters,
  InventoryListRequest,
  Bid,
  BidListRequest,
  PlaceBid,
  Trade,
  TradeListRequest,
  Team,
  MarketCommodity,
  MarketPrice,
  MarketPricesRequest,
  MarketHistoryRequest,
  PriceHistory,
  CashBid,
  CashBidListRequest,
  CashBidOffer,
  CashBidOffersRequest,
  StoreCashBid,
  UpdateCashBid,
  Webhook,
  WebhookResource,
  WebhookCreated,
  WebhookDelivery,
  WebhookDeliveriesRequest,
} from '../src'
import { TokenManager, type AuthOptions } from './auth'
import { errorMiddleware } from './errors'
import { paginate } from './pagination'
import { verifyWebhook, type VerifyOptions } from './webhooks'
import type { WebhookEvent } from '../src'

const DEFAULT_BASE_URL = 'https://api.mygrainexchange.com/v1'

export interface MgxClientOptions extends AuthOptions {
  /** API base URL. Defaults to production; set to your Herd URL for local dev. */
  baseUrl?: string
}

function idempotencyKey(): string {
  const c: any = (globalThis as any).crypto
  if (c?.randomUUID) {
    return c.randomUUID()
  }
  return 'idem_' + Date.now().toString(36) + Math.random().toString(36).slice(2)
}

/**
 * The MGX Enterprise API client. Wraps the generated resource APIs with OAuth2
 * token handling, auto-pagination, idempotency keys, and typed errors.
 */
export class MgxClient {
  private readonly inventoryApi: InventoryApi
  private readonly marketApi: MarketApi
  private readonly bidsApi: BidsApi
  private readonly tradesApi: TradesApi
  private readonly teamsApi: TeamsApi
  private readonly cashBidsApi: CashBidsApi
  private readonly webhooksApi: WebhooksApi

  constructor(options: MgxClientOptions) {
    const baseUrl = options.baseUrl ?? DEFAULT_BASE_URL
    const tokens = new TokenManager(options, baseUrl)
    const config = new Configuration({
      basePath: baseUrl,
      // This generator assigns the Authorization header verbatim from accessToken,
      // so the overlay supplies the full "Bearer <token>" credential.
      accessToken: async () => `Bearer ${await tokens.getAccessToken()}`,
      middleware: [errorMiddleware],
    })
    this.inventoryApi = new InventoryApi(config)
    this.marketApi = new MarketApi(config)
    this.bidsApi = new BidsApi(config)
    this.tradesApi = new TradesApi(config)
    this.teamsApi = new TeamsApi(config)
    this.cashBidsApi = new CashBidsApi(config)
    this.webhooksApi = new WebhooksApi(config)
  }

  /** Browse and filter anonymized inventory. */
  readonly inventory = {
    list: (params: Omit<InventoryListRequest, 'offset'> = {}): AsyncGenerator<Inventory> =>
      paginate((offset) => this.inventoryApi.inventoryList({ ...params, offset })),
    get: async (id: string): Promise<Inventory | undefined> =>
      (await this.inventoryApi.inventoryGet({ id })).data,
    filters: (): Promise<InventoryFilters> => this.inventoryApi.inventoryFilters(),
    placeBid: async (
      id: string,
      body: PlaceBid,
      opts: { idempotencyKey?: string } = {},
    ): Promise<Bid | undefined> =>
      (
        await this.bidsApi.bidPlace({
          id,
          placeBid: body,
          idempotencyKey: opts.idempotencyKey ?? idempotencyKey(),
        })
      ).data,
  }

  /** Market commodities, prices, and history. */
  readonly market = {
    commodities: async (): Promise<MarketCommodity[]> =>
      (await this.marketApi.marketCommodities()).items ?? [],
    prices: async (params: MarketPricesRequest = {}): Promise<MarketPrice[]> =>
      (await this.marketApi.marketPrices(params)).items ?? [],
    history: (commodity: string, params: Omit<MarketHistoryRequest, 'commodity'> = {}): Promise<PriceHistory> =>
      this.marketApi.marketHistory({ commodity, ...params }),
  }

  /** Read the authenticated team's own bids and act on counter-offers. */
  readonly bids = {
    list: (params: Omit<BidListRequest, 'offset'> = {}): AsyncGenerator<Bid> =>
      paginate((offset) => this.bidsApi.bidList({ ...params, offset })),
    get: async (id: string): Promise<Bid | undefined> =>
      (await this.bidsApi.bidGet({ id })).data,
    accept: async (id: string): Promise<Trade | undefined> =>
      (await this.bidsApi.bidAccept({ id })).data,
    reject: async (id: string): Promise<Bid | undefined> =>
      (await this.bidsApi.bidReject({ id })).data,
    counter: async (id: string, body: PlaceBid): Promise<Bid | undefined> =>
      (await this.bidsApi.bidCounter({ id, placeBid: body })).data,
  }

  /** Read the authenticated team's own trades. */
  readonly trades = {
    list: (params: Omit<TradeListRequest, 'offset'> = {}): AsyncGenerator<Trade> =>
      paginate((offset) => this.tradesApi.tradeList({ ...params, offset })),
    get: async (id: string): Promise<Trade | undefined> =>
      (await this.tradesApi.tradeGet({ id })).data,
  }

  /** Read the teams the authenticated user belongs to. */
  readonly teams = {
    list: async (): Promise<Team[]> => (await this.teamsApi.teamList()).items ?? [],
    get: async (id: string): Promise<Team | undefined> =>
      (await this.teamsApi.teamGet({ id })).data,
  }

  /** Manage the elevator's own cash bids and the offers received on them. */
  readonly cashBids = {
    list: (params: Omit<CashBidListRequest, 'offset'> = {}): AsyncGenerator<CashBid> =>
      paginate((offset) => this.cashBidsApi.cashBidList({ ...params, offset })),
    create: async (body: StoreCashBid): Promise<CashBid | undefined> =>
      (await this.cashBidsApi.cashBidCreate({ storeCashBid: body })).data,
    update: async (id: string, body: UpdateCashBid): Promise<CashBid | undefined> =>
      (await this.cashBidsApi.cashBidUpdate({ id, updateCashBid: body })).data,
    offers: (cashBidId: string, params: Omit<CashBidOffersRequest, 'id' | 'offset'> = {}): AsyncGenerator<CashBidOffer> =>
      paginate((offset) => this.cashBidsApi.cashBidOffers({ id: cashBidId, ...params, offset })),
    acceptOffer: async (offerId: string): Promise<Trade | undefined> =>
      (await this.cashBidsApi.cashBidOfferAccept({ id: offerId })).data,
    rejectOffer: async (offerId: string): Promise<CashBidOffer | undefined> =>
      (await this.cashBidsApi.cashBidOfferReject({ id: offerId })).data,
  }

  /** Subscribe to event notifications and inspect deliveries. */
  readonly webhooks = {
    list: async (): Promise<WebhookResource[]> => (await this.webhooksApi.webhookList()).items ?? [],
    create: async (body: Webhook): Promise<WebhookCreated | undefined> =>
      (await this.webhooksApi.webhookCreate({ webhook: body })).data,
    delete: async (id: string): Promise<void> => {
      await this.webhooksApi.webhookDelete({ id })
    },
    deliveries: (webhookId: string, params: Omit<WebhookDeliveriesRequest, 'id' | 'offset'> = {}): AsyncGenerator<WebhookDelivery> =>
      paginate((offset) => this.webhooksApi.webhookDeliveries({ id: webhookId, ...params, offset })),
    /**
     * Verify an inbound webhook's signature and return the typed event. Pass the
     * raw request body, the `MGX-Signature` header, and the subscription secret.
     */
    verify: (rawBody: string, signatureHeader: string, secret: string, opts?: VerifyOptions): WebhookEvent =>
      verifyWebhook(rawBody, signatureHeader, secret, opts),
  }
}
