/* Hand-written overlay — not generated. */
import type { Middleware, ResponseContext } from '../src/runtime'

export interface MgxFieldError {
  field: string
  message: string
}

/** Typed error mapped from the API's `{ error: { status, code, message, errors[] } }` envelope. */
export class MgxApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: MgxFieldError[]

  constructor(status: number, code: string, message: string, fieldErrors: MgxFieldError[] = []) {
    super(message)
    this.name = 'MgxApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

/** Runtime middleware that turns any non-2xx response into a typed {@link MgxApiError}. */
export const errorMiddleware: Middleware = {
  async post(context: ResponseContext): Promise<Response | void> {
    const { response } = context
    if (response.ok) {
      return response
    }
    let status = response.status
    let code = 'error'
    let message = response.statusText || 'Request failed'
    let fieldErrors: MgxFieldError[] = []
    try {
      const body: any = await response.clone().json()
      if (body && body.error) {
        status = body.error.status ?? status
        code = body.error.code ?? code
        message = body.error.message ?? message
        fieldErrors = body.error.errors ?? []
      }
    } catch {
      /* non-JSON error body — keep the HTTP status text */
    }
    throw new MgxApiError(status, code, message, fieldErrors)
  },
}
