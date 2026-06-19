/* Hand-written overlay — not generated. */

export interface AuthOptions {
  /** OAuth2 client id (client-credentials or authorization-code app). */
  clientId?: string
  /** OAuth2 client secret. */
  clientSecret?: string
  /** Scopes to request on a client-credentials grant. */
  scopes?: string[]
  /** A pre-obtained access token (e.g. from a Login-with-MGX authorization-code flow). */
  accessToken?: string
  /** A refresh token to renew an authorization-code access token. */
  refreshToken?: string
  /** Override the token endpoint. Defaults to `<host>/oauth/token`. */
  tokenUrl?: string
}

interface TokenResponse {
  access_token: string
  expires_in?: number
  refresh_token?: string
}

/**
 * Acquires, caches, and refreshes OAuth2 access tokens.
 *
 * - client-credentials when `clientId` + `clientSecret` are given (read scopes);
 * - refresh_token when a `refreshToken` is given (user-context tokens);
 * - otherwise uses the static `accessToken` as-is.
 */
export class TokenManager {
  private token?: string
  private refreshToken?: string
  private expiresAt = 0

  constructor(
    private readonly opts: AuthOptions,
    private readonly baseUrl: string,
  ) {
    this.token = opts.accessToken
    this.refreshToken = opts.refreshToken
    // A caller-supplied token with no refresh path is treated as long-lived.
    if (opts.accessToken && !opts.refreshToken) {
      this.expiresAt = Number.POSITIVE_INFINITY
    }
  }

  private get tokenUrl(): string {
    if (this.opts.tokenUrl) {
      return this.opts.tokenUrl
    }
    return this.baseUrl.replace(/\/v1\/?$/, '') + '/oauth/token'
  }

  async getAccessToken(): Promise<string> {
    if (this.token && Date.now() < this.expiresAt) {
      return this.token
    }
    if (this.refreshToken) {
      return this.grant({
        grant_type: 'refresh_token',
        refresh_token: this.refreshToken,
        client_id: this.opts.clientId ?? '',
        client_secret: this.opts.clientSecret ?? '',
      })
    }
    if (this.opts.clientId && this.opts.clientSecret) {
      return this.grant({
        grant_type: 'client_credentials',
        client_id: this.opts.clientId,
        client_secret: this.opts.clientSecret,
        scope: (this.opts.scopes ?? []).join(' '),
      })
    }
    if (this.token) {
      return this.token
    }
    throw new Error(
      'MGX SDK: no credentials configured. Provide clientId + clientSecret, or an accessToken.',
    )
  }

  private async grant(body: Record<string, string>): Promise<string> {
    const res = await fetch(this.tokenUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        Accept: 'application/json',
      },
      body: new URLSearchParams(body).toString(),
    })
    if (!res.ok) {
      throw new Error(`MGX SDK: OAuth2 token request failed (${res.status}).`)
    }
    const data = (await res.json()) as TokenResponse
    this.token = data.access_token
    this.expiresAt = Date.now() + ((data.expires_in ?? 3600) - 60) * 1000
    if (data.refresh_token) {
      this.refreshToken = data.refresh_token
    }
    return this.token
  }
}
