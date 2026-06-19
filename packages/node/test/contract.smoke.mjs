/**
 * Gate-2 contract smoke test. Boots nothing itself — expects a Prism mock of the
 * spec at MGX_MOCK_URL (default http://127.0.0.1:4010). Exercises each overlay
 * namespace against the mock and asserts spec-shaped responses decode cleanly.
 *
 *   node ../../../MGX/tooling/sdk/contract/serve-mock.mjs &   # in another shell
 *   npm run build && node test/contract.smoke.mjs
 */
import assert from 'node:assert'
import { MgxClient, MgxApiError } from '../dist/overlay/index.js'

const baseUrl = process.env.MGX_MOCK_URL ?? 'http://127.0.0.1:4010'
const mgx = new MgxClient({ baseUrl, accessToken: 'mock-token' })

let failures = 0
async function check(name, fn) {
  try {
    await fn()
    console.log(`  ok  ${name}`)
  } catch (e) {
    failures++
    console.error(`  FAIL ${name}: ${e instanceof MgxApiError ? `${e.status} ${e.code}` : e.message}`)
  }
}

await check('inventory.list yields items', async () => {
  let count = 0
  for await (const lot of mgx.inventory.list({ commodity: 'wheat' })) {
    assert.ok(typeof lot.id === 'string')
    if (++count >= 1) break
  }
})
await check('inventory.get returns a listing', async () => {
  const lot = await mgx.inventory.get('inv_3Kd9aZ')
  assert.ok(lot)
})
await check('inventory.filters returns facets', async () => {
  await mgx.inventory.filters()
})
await check('market.commodities returns array', async () => {
  const c = await mgx.market.commodities()
  assert.ok(Array.isArray(c))
})
await check('market.prices returns array', async () => {
  const p = await mgx.market.prices({ commodity: 'wheat' })
  assert.ok(Array.isArray(p))
})
await check('market.history returns series', async () => {
  await mgx.market.history('wheat', { interval: 'day' })
})
await check('teams.list returns array', async () => {
  const t = await mgx.teams.list()
  assert.ok(Array.isArray(t))
})
await check('bids.counter posts a counter', async () => {
  await mgx.bids.counter('bid_7Qp2', {
    quantityMt: 50,
    price: { amount: 300 },
    delivery: { from: new Date('2026-08-01'), to: new Date('2026-09-30') },
  })
})
await check('cashBids.offers yields offers', async () => {
  let n = 0
  for await (const o of mgx.cashBids.offers('cb_9Xa1')) {
    assert.ok(typeof o.id === 'string')
    if (++n >= 1) break
  }
})
await check('webhooks.list returns array', async () => {
  const w = await mgx.webhooks.list()
  assert.ok(Array.isArray(w))
})
await check('webhooks.create returns subscription', async () => {
  const w = await mgx.webhooks.create({ url: 'https://example.com/hook', events: ['trade.created'] })
  assert.ok(w)
})

console.log(failures === 0 ? '\nAll contract checks passed.' : `\n${failures} check(s) failed.`)
process.exit(failures === 0 ? 0 : 1)
