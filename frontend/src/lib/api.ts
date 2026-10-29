/**
 * The single door to the Trackr API.
 *
 * Responsibilities kept in one place:
 *   * attaches the access token;
 *   * refreshes it once, transparently, when it has expired;
 *   * turns every failure into an ApiError carrying a message written for the
 *     person reading it, never an HTTP status code.
 */

const BASE_URL: string = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/+$/, '') ?? ''

const ACCESS_TOKEN_KEY = 'trackr.accessToken'
const REFRESH_TOKEN_KEY = 'trackr.refreshToken'

export class ApiError extends Error {
  readonly code: string
  readonly status: number
  readonly fields?: Record<string, string>
  readonly details?: Record<string, unknown>

  constructor(
    message: string,
    code: string,
    status: number,
    fields?: Record<string, string>,
    details?: Record<string, unknown>,
  ) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.fields = fields
    this.details = details
  }

  /** True when the request never reached the server. */
  get isOffline(): boolean {
    return this.code === 'NETWORK_UNAVAILABLE'
  }
}

/* -------------------------------------------------------------------------- */
/* Token storage                                                              */
/* -------------------------------------------------------------------------- */

export const tokens = {
  access(): string | null {
    return safeRead(ACCESS_TOKEN_KEY)
  },
  refresh(): string | null {
    return safeRead(REFRESH_TOKEN_KEY)
  },
  set(accessToken: string, refreshToken: string): void {
    safeWrite(ACCESS_TOKEN_KEY, accessToken)
    safeWrite(REFRESH_TOKEN_KEY, refreshToken)
  },
  clear(): void {
    safeRemove(ACCESS_TOKEN_KEY)
    safeRemove(REFRESH_TOKEN_KEY)
  },
}

function safeRead(key: string): string | null {
  try {
    return window.localStorage.getItem(key)
  } catch {
    // Private browsing or blocked site data. The session simply will not
    // survive a reload, which is acceptable.
    return null
  }
}

function safeWrite(key: string, value: string): void {
  try {
    window.localStorage.setItem(key, value)
  } catch {
    /* ignore */
  }
}

function safeRemove(key: string): void {
  try {
    window.localStorage.removeItem(key)
  } catch {
    /* ignore */
  }
}

/* -------------------------------------------------------------------------- */
/* Session expiry callback                                                    */
/* -------------------------------------------------------------------------- */

let onSessionLost: (() => void) | null = null

export function setSessionLostHandler(handler: (() => void) | null): void {
  onSessionLost = handler
}

/* -------------------------------------------------------------------------- */
/* Requests                                                                   */
/* -------------------------------------------------------------------------- */

interface RequestOptions {
  method?: string
  body?: unknown
  /** Skips the Authorization header. Used for login and activation. */
  anonymous?: boolean
  /** Returns the raw Response instead of parsed JSON (used for images). */
  raw?: boolean
  signal?: AbortSignal
  formData?: FormData
}

let refreshInFlight: Promise<boolean> | null = null

async function refreshAccessToken(): Promise<boolean> {
  // Several requests can fail at once when a token expires. They share one
  // refresh rather than each firing their own and invalidating the others.
  if (refreshInFlight) {
    return refreshInFlight
  }

  const refreshToken = tokens.refresh()
  if (!refreshToken) {
    return false
  }

  refreshInFlight = (async () => {
    try {
      const response = await fetch(`${BASE_URL}/api/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      })
      if (!response.ok) {
        return false
      }
      const session = (await response.json()) as { accessToken: string; refreshToken: string }
      tokens.set(session.accessToken, session.refreshToken)
      return true
    } catch {
      return false
    } finally {
      refreshInFlight = null
    }
  })()

  return refreshInFlight
}

async function send<T>(path: string, options: RequestOptions = {}, isRetry = false): Promise<T> {
  const headers: Record<string, string> = {}

  if (!options.formData && options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (!options.anonymous) {
    const token = tokens.access()
    if (token) {
      headers.Authorization = `Bearer ${token}`
    }
  }

  let response: Response
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method: options.method ?? 'GET',
      headers,
      body: options.formData ?? (options.body === undefined ? undefined : JSON.stringify(options.body)),
      signal: options.signal,
    })
  } catch {
    throw new ApiError(
      "We can't reach Trackr right now. Check your connection and try again.",
      'NETWORK_UNAVAILABLE',
      0,
    )
  }

  if (response.status === 401 && !options.anonymous && !isRetry) {
    const refreshed = await refreshAccessToken()
    if (refreshed) {
      return send<T>(path, options, true)
    }
    tokens.clear()
    onSessionLost?.()
    throw new ApiError(
      'We could not verify your session. Please log in again.',
      'UNAUTHENTICATED',
      401,
    )
  }

  if (!response.ok) {
    throw await toApiError(response)
  }

  if (options.raw) {
    return response as unknown as T
  }
  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

async function toApiError(response: Response): Promise<ApiError> {
  let payload: Record<string, unknown> = {}
  try {
    payload = (await response.json()) as Record<string, unknown>
  } catch {
    /* A response with no JSON body still deserves a readable message. */
  }

  const message =
    typeof payload.message === 'string'
      ? payload.message
      : 'Something went wrong. Please try again in a moment.'
  const code = typeof payload.code === 'string' ? payload.code : 'ERROR'

  return new ApiError(
    message,
    code,
    response.status,
    payload.fields as Record<string, string> | undefined,
    payload.details as Record<string, unknown> | undefined,
  )
}

/* -------------------------------------------------------------------------- */
/* Public surface                                                             */
/* -------------------------------------------------------------------------- */

export const api = {
  get: <T>(path: string, signal?: AbortSignal) => send<T>(path, { signal }),
  post: <T>(path: string, body?: unknown) => send<T>(path, { method: 'POST', body }),
  patch: <T>(path: string, body?: unknown) => send<T>(path, { method: 'PATCH', body }),
  del: <T>(path: string) => send<T>(path, { method: 'DELETE' }),

  anonymousGet: <T>(path: string) => send<T>(path, { anonymous: true }),
  anonymousPost: <T>(path: string, body?: unknown) =>
    send<T>(path, { method: 'POST', body, anonymous: true }),

  upload: <T>(path: string, formData: FormData) => send<T>(path, { method: 'POST', formData }),

  /** Fetches an access-controlled image and returns an object URL. */
  async blobUrl(path: string): Promise<string> {
    const response = await send<Response>(path, { raw: true })
    return URL.createObjectURL(await response.blob())
  },

  baseUrl: BASE_URL,
}

export function query(params: Record<string, string | number | boolean | undefined | null>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value))
    }
  }
  const encoded = search.toString()
  return encoded ? `?${encoded}` : ''
}
