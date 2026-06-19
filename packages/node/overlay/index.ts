/* Hand-written overlay — the package's public entry point. */
export { MgxClient, type MgxClientOptions } from './client'
export { MgxApiError, type MgxFieldError } from './errors'
export { type AuthOptions } from './auth'
export { paginate, collect, type PageLike } from './pagination'
export { verifyWebhook, MgxSignatureError, type VerifyOptions } from './webhooks'

// Re-export every generated model + type so consumers import them from one place.
export * from '../src/models'
