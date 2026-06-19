/** Unit test for webhook signature verification (no network). */
import assert from 'node:assert'
import { createHmac } from 'node:crypto'
import { MgxClient, verifyWebhook, MgxSignatureError } from '../dist/overlay/index.js'

const secret = 'whsec_test'
const body = JSON.stringify({ id: 'evt_6Yh2', type: 'trade.created', created_at: '2026-06-18T00:00:00Z', data: { id: 'trd_1' } })
const t = Math.floor(Date.now() / 1000)
const sign = (ts, b, s) => `t=${ts},v1=${createHmac('sha256', s).update(`${ts}.${b}`).digest('hex')}`

let failures = 0
const check = (name, fn) => {
  try { fn(); console.log(`  ok  ${name}`) } catch (e) { failures++; console.error(`  FAIL ${name}: ${e.message}`) }
}

check('valid signature returns the typed event', () => {
  const evt = verifyWebhook(body, sign(t, body, secret), secret)
  assert.equal(evt.type, 'trade.created')
  assert.equal(evt.id, 'evt_6Yh2')
})
check('client.webhooks.verify works', () => {
  const mgx = new MgxClient({ accessToken: 'x' })
  const evt = mgx.webhooks.verify(body, sign(t, body, secret), secret)
  assert.equal(evt.type, 'trade.created')
})
check('tampered body is rejected', () => {
  assert.throws(() => verifyWebhook(body + ' ', sign(t, body, secret), secret), MgxSignatureError)
})
check('wrong secret is rejected', () => {
  assert.throws(() => verifyWebhook(body, sign(t, body, secret), 'whsec_wrong'), MgxSignatureError)
})
check('stale timestamp is rejected', () => {
  assert.throws(() => verifyWebhook(body, sign(t - 9999, body, secret), secret), MgxSignatureError)
})
check('malformed header is rejected', () => {
  assert.throws(() => verifyWebhook(body, 'garbage', secret), MgxSignatureError)
})

console.log(failures === 0 ? '\nAll webhook-verify checks passed.' : `\n${failures} failed.`)
process.exit(failures === 0 ? 0 : 1)
