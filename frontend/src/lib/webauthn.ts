/**
 * Fingerprint verification through the phone's own security hardware.
 *
 * What actually happens, in plain terms: the phone asks for a fingerprint,
 * unlocks a private key it created for Trackr, and signs a one-time challenge
 * from the server. The fingerprint itself never leaves the phone and Trackr
 * never sees it.
 *
 * What this cannot do: verify anything while offline. Verification needs the
 * server's challenge and the server's check of the signature. An event captured
 * with no connection therefore records that the phone's own check passed and is
 * verified properly when it syncs - and the UI says exactly that rather than
 * implying more.
 */

import { api } from './api'

/**
 * How long to wait for the fingerprint prompt before carrying on without it.
 * Short enough that nobody is stranded, long enough for an unhurried tap.
 */
const FINGERPRINT_TIMEOUT_MS = 25_000

export interface RegistrationOptions {
  rp: { id: string; name: string }
  user: { id: string; name: string; displayName: string }
  challenge: string
  pubKeyCredParams: Array<{ type: string; alg: number }>
  timeout: number
  attestation: string
  authenticatorSelection: Record<string, unknown>
  excludeCredentials: Array<{ type: string; id: string }>
}

export interface AuthenticationOptions {
  challenge: string
  rpId: string
  timeout: number
  userVerification: string
  allowCredentials: Array<{ type: string; id: string }>
}

export interface DeviceAssertion {
  credentialId: string
  clientDataJSON: string
  authenticatorData: string
  signature: string
}

/** Whether this browser can do platform (fingerprint) authentication at all. */
export async function isFingerprintSupported(): Promise<boolean> {
  if (typeof window === 'undefined' || !window.PublicKeyCredential) {
    return false
  }
  if (!window.isSecureContext) {
    // WebAuthn requires HTTPS (or localhost). Without it there is no point
    // offering the option and then failing.
    return false
  }
  try {
    return await window.PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable()
  } catch {
    return false
  }
}

/* -------------------------------------------------------------------------- */
/* base64url helpers                                                          */
/* -------------------------------------------------------------------------- */

export function base64UrlToBytes(value: string): Uint8Array {
  const padded = value.replace(/-/g, '+').replace(/_/g, '/')
  const withPadding = padded + '='.repeat((4 - (padded.length % 4)) % 4)
  const binary = atob(withPadding)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes
}

export function bytesToBase64Url(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer)
  let binary = ''
  for (const byte of bytes) {
    binary += String.fromCharCode(byte)
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

/* -------------------------------------------------------------------------- */
/* Registration                                                               */
/* -------------------------------------------------------------------------- */

/** Names the device something the employee will recognise in a list. */
export function guessDeviceLabel(): string {
  const agent = navigator.userAgent
  if (/iPhone/i.test(agent)) return 'iPhone'
  if (/iPad/i.test(agent)) return 'iPad'
  if (/Android/i.test(agent)) return 'Android phone'
  if (/Windows/i.test(agent)) return 'Windows computer'
  if (/Macintosh/i.test(agent)) return 'Mac'
  return 'Registered device'
}

export async function registerFingerprint(): Promise<{ deviceLabel: string }> {
  const options = await api.post<RegistrationOptions>('/api/me/webauthn/register/start')

  const credential = (await navigator.credentials.create({
    publicKey: {
      rp: { id: options.rp.id, name: options.rp.name },
      user: {
        id: base64UrlToBytes(options.user.id),
        name: options.user.name,
        displayName: options.user.displayName,
      },
      challenge: base64UrlToBytes(options.challenge),
      pubKeyCredParams: options.pubKeyCredParams.map((param) => ({
        type: 'public-key' as const,
        alg: param.alg,
      })),
      timeout: options.timeout,
      attestation: 'none',
      authenticatorSelection: options.authenticatorSelection,
      excludeCredentials: options.excludeCredentials.map((entry) => ({
        type: 'public-key' as const,
        id: base64UrlToBytes(entry.id),
      })),
    },
  })) as PublicKeyCredential | null

  if (!credential) {
    throw new Error('Fingerprint setup was cancelled. Please try again.')
  }

  const response = credential.response as AuthenticatorAttestationResponse

  return api.post<{ registered: boolean; deviceLabel: string }>('/api/me/webauthn/register/finish', {
    id: credential.id,
    type: credential.type,
    deviceLabel: guessDeviceLabel(),
    response: {
      clientDataJSON: bytesToBase64Url(response.clientDataJSON),
      attestationObject: bytesToBase64Url(response.attestationObject),
    },
  })
}

/* -------------------------------------------------------------------------- */
/* Verification for a work event                                              */
/* -------------------------------------------------------------------------- */

/**
 * Asks for a fingerprint and returns the assertion to attach to a work event.
 *
 * Returns null when a fingerprint could not be obtained - no registered device,
 * no connection to get a challenge, or the person cancelled. The caller records
 * the event anyway and it is stored as not fingerprint-verified, which is
 * honest and still useful.
 */
export async function verifyFingerprint(): Promise<DeviceAssertion | null> {
  if (!(await isFingerprintSupported())) {
    return null
  }

  let options: AuthenticationOptions
  try {
    options = await api.post<AuthenticationOptions>('/api/me/webauthn/verify/start')
  } catch {
    // No challenge available (offline, or no device registered). The event is
    // still recorded; it simply is not fingerprint-verified.
    return null
  }

  try {
    // WebAuthn's own timeout is generous (two minutes) and the browser gives no
    // progress in the meantime. An employee who ignores the prompt, or whose
    // sensor does not respond, would be left staring at "Waiting for your
    // fingerprint" with nothing to do. Give up sooner and record the activity
    // unverified instead - which is the honest outcome and keeps them moving.
    const controller = new AbortController()
    const giveUp = setTimeout(() => controller.abort(), FINGERPRINT_TIMEOUT_MS)

    const assertion = (await navigator.credentials.get({
      signal: controller.signal,
      publicKey: {
        challenge: base64UrlToBytes(options.challenge),
        rpId: options.rpId,
        timeout: options.timeout,
        userVerification: 'required',
        allowCredentials: options.allowCredentials.map((entry) => ({
          type: 'public-key' as const,
          id: base64UrlToBytes(entry.id),
        })),
      },
    }).finally(() => clearTimeout(giveUp))) as PublicKeyCredential | null

    if (!assertion) {
      return null
    }

    const response = assertion.response as AuthenticatorAssertionResponse
    return {
      credentialId: assertion.id,
      clientDataJSON: bytesToBase64Url(response.clientDataJSON),
      authenticatorData: bytesToBase64Url(response.authenticatorData),
      signature: bytesToBase64Url(response.signature),
    }
  } catch {
    // Cancelled, timed out, or the fingerprint was not recognised.
    return null
  }
}
