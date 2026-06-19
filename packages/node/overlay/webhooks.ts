/* Hand-written overlay — not generated. Inbound webhook verification. */
import { createHmac, timingSafeEqual } from 'node:crypto'
import type { WebhookEvent } from '../src'

/** Thrown when an inbound webhook signature cannot be verified. */
export class MgxSignatureError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'MgxSignatureError'
  }
}

export interface VerifyOptions {
  /** Max age of the signed timestamp, in seconds. Default 300 (5 minutes). */
  toleranceSeconds?: number
  /** Override "now" (unix seconds) — for testing. */
  now?: number
}

const DEFAULT_TOLERANCE_SECONDS = 300

function parseSignatureHeader(header: string): { t: number; v1: string } {
  const parts: Record<string, string> = {}
  for (const segment of header.split(',')) {
    const idx = segment.indexOf('=')
    if (idx === -1) continue
    parts[segment.slice(0, idx).trim()] = segment.slice(idx + 1).trim()
  }
  const t = Number(parts.t)
  if (!parts.t || Number.isNaN(t) || !parts.v1) {
    throw new MgxSignatureError('Malformed MGX-Signature header.')
  }
  return { t, v1: parts.v1 }
}

/**
 * Verifies an inbound MGX webhook and returns the typed event.
 *
 * The signature header is `MGX-Signature: t=<unix>,v1=<hex hmac>`. The HMAC is
 * SHA-256 over `"{t}.{rawBody}"` keyed with the subscription's signing secret.
 * Pass the EXACT raw request body (not a re-serialized object). Throws
 * {@link MgxSignatureError} on a bad signature or a stale timestamp.
 */
export function verifyWebhook(
  rawBody: string,
  signatureHeader: string,
  secret: string,
  opts: VerifyOptions = {},
): WebhookEvent {
  const { t, v1 } = parseSignatureHeader(signatureHeader)
  const tolerance = opts.toleranceSeconds ?? DEFAULT_TOLERANCE_SECONDS
  const now = opts.now ?? Math.floor(Date.now() / 1000)
  if (Math.abs(now - t) > tolerance) {
    throw new MgxSignatureError('Webhook timestamp is outside the tolerance window.')
  }
  const expected = createHmac('sha256', secret).update(`${t}.${rawBody}`).digest('hex')
  const a = Buffer.from(expected, 'utf8')
  const b = Buffer.from(v1, 'utf8')
  if (a.length !== b.length || !timingSafeEqual(a, b)) {
    throw new MgxSignatureError('Webhook signature does not match.')
  }
  return JSON.parse(rawBody) as WebhookEvent
}
