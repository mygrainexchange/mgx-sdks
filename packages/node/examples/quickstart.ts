/**
 * MGX Node SDK — quickstart. Run: `npm run build && node dist-examples` or see
 * the developer portal. These snippets are embedded in the docs.
 */
import { MgxClient, MgxApiError } from '@mygrainexchange/sdk'

const mgx = new MgxClient({
  clientId: process.env.MGX_CLIENT_ID!,
  clientSecret: process.env.MGX_CLIENT_SECRET!,
  scopes: ['inventory.read', 'market.read'],
  // baseUrl: 'https://dashboard.mgx.test/v1', // for local development
})

async function main() {
  // Browse anonymized inventory — auto-paginates across the items/next envelope.
  for await (const lot of mgx.inventory.list({ commodity: 'wheat', minQuantity: 50 })) {
    console.log(lot.id, lot.quantityMt, lot.askingPrice?.amount)
  }

  // Current market prices.
  const prices = await mgx.market.prices({ commodity: 'wheat,canola' })
  console.log(prices.map((p) => `${p.commodity?.slug}: ${p.price?.amount}`))

  // Place a bid (requires a user-context token with bids.write). The SDK adds
  // an Idempotency-Key automatically.
  try {
    const bid = await mgx.inventory.placeBid('inv_3Kd9aZ', {
      quantityMt: 50,
      price: { amount: 312.5 },
      delivery: { from: new Date('2026-08-01'), to: new Date('2026-09-30') },
    })
    console.log('placed', bid?.id, bid?.status)
  } catch (e) {
    if (e instanceof MgxApiError) {
      console.error(e.status, e.code, e.message, e.fieldErrors)
    } else {
      throw e
    }
  }
}

main()
