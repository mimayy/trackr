/**
 * The offline queue.
 *
 * When there is no connection, a work event is written to IndexedDB and shown
 * as "waiting to sync". Nothing is ever discarded: the queue survives reloads
 * and app restarts, and every entry is retried until the server accepts it or
 * explicitly refuses it.
 *
 * Two design points worth stating:
 *
 *   * The event id is generated here, on the device. That is what makes the
 *     upload idempotent - retrying a batch after a dropped connection cannot
 *     create a second Time In.
 *   * The queue holds only what is needed to synchronise one event. No personal
 *     data beyond the event itself is kept on the phone.
 */

import { api, ApiError } from './api'
import type { Destination, EventType } from './types'

const DB_NAME = 'trackr-offline'
const DB_VERSION = 1
const STORE = 'pending-events'

export type QueueState = 'PENDING' | 'SYNCING' | 'FAILED'

export interface QueuedEvent {
  eventId: string
  eventType: EventType
  eventLabel: string
  eventTime: string
  deviceTimestamp: string
  latitude?: number
  longitude?: number
  gpsAccuracyM?: number
  siteName?: string
  destination?: Destination
  destinationLocationId?: string
  photoId?: string
  deviceAssertion?: unknown
  integrityHash?: string
  clientAppVersion: string
  locationName?: string
  fingerprintVerified: boolean
  state: QueueState
  attempts: number
  lastError?: string
  queuedAt: string
}

/* -------------------------------------------------------------------------- */
/* IndexedDB plumbing                                                         */
/* -------------------------------------------------------------------------- */

let dbPromise: Promise<IDBDatabase> | null = null

function openDb(): Promise<IDBDatabase> {
  if (dbPromise) {
    return dbPromise
  }

  dbPromise = new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') {
      reject(new Error('This browser cannot store activities offline.'))
      return
    }

    const request = indexedDB.open(DB_NAME, DB_VERSION)

    request.onupgradeneeded = () => {
      const db = request.result
      if (!db.objectStoreNames.contains(STORE)) {
        db.createObjectStore(STORE, { keyPath: 'eventId' })
      }
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error ?? new Error('Could not open offline storage'))
  })

  return dbPromise
}

async function withStore<T>(
  mode: IDBTransactionMode,
  action: (store: IDBObjectStore) => IDBRequest<T>,
): Promise<T> {
  const db = await openDb()
  return new Promise<T>((resolve, reject) => {
    const transaction = db.transaction(STORE, mode)
    const request = action(transaction.objectStore(STORE))
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error ?? new Error('Offline storage failed'))
  })
}

/* -------------------------------------------------------------------------- */
/* Queue operations                                                           */
/* -------------------------------------------------------------------------- */

export async function enqueue(event: QueuedEvent): Promise<void> {
  await withStore('readwrite', (store) => store.put(event))
  notify()
}

export async function listQueued(): Promise<QueuedEvent[]> {
  try {
    const all = await withStore<QueuedEvent[]>('readonly', (store) => store.getAll() as IDBRequest<QueuedEvent[]>)
    return all.sort((a, b) => a.eventTime.localeCompare(b.eventTime))
  } catch {
    return []
  }
}

export async function queuedCount(): Promise<number> {
  return (await listQueued()).length
}

async function remove(eventId: string): Promise<void> {
  await withStore('readwrite', (store) => store.delete(eventId))
}

async function update(event: QueuedEvent): Promise<void> {
  await withStore('readwrite', (store) => store.put(event))
}

/** Clears an entry the server has permanently refused. */
export async function discard(eventId: string): Promise<void> {
  await remove(eventId)
  notify()
}

/* -------------------------------------------------------------------------- */
/* Change notification                                                        */
/* -------------------------------------------------------------------------- */

type Listener = () => void
const listeners = new Set<Listener>()

export function onQueueChange(listener: Listener): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

function notify(): void {
  listeners.forEach((listener) => listener())
}

/* -------------------------------------------------------------------------- */
/* Synchronisation                                                            */
/* -------------------------------------------------------------------------- */

export interface SyncSummary {
  accepted: number
  flagged: number
  duplicates: number
  failed: number
  remaining: number
}

interface SyncResultItem {
  eventId: string
  status: 'ACCEPTED' | 'FLAGGED' | 'DUPLICATE' | 'FAILED'
  message: string
}

interface SyncResponse {
  accepted: number
  flagged: number
  duplicates: number
  failed: number
  results: SyncResultItem[]
}

let syncing = false

/**
 * Uploads everything waiting.
 *
 * Accepted, flagged and duplicate events all leave the queue - the server has
 * them. A failure the server describes (an impossible sequence, say) also
 * leaves the queue with its reason kept for display, because retrying it
 * forever would never succeed. A transport failure leaves the event queued.
 */
export async function syncNow(): Promise<SyncSummary | null> {
  if (syncing || !navigator.onLine) {
    return null
  }

  const pending = await listQueued()
  if (pending.length === 0) {
    return null
  }

  syncing = true
  try {
    for (const event of pending) {
      await update({ ...event, state: 'SYNCING' })
    }
    notify()

    const response = await api.post<SyncResponse>('/api/me/sync/work-events', {
      events: pending.map(toRequest),
    })

    for (const result of response.results) {
      const queued = pending.find((event) => event.eventId === result.eventId)
      if (!queued) {
        continue
      }

      if (result.status === 'FAILED') {
        // The server understood it and will not take it. Keep it visible with
        // the reason instead of retrying in a loop.
        await update({
          ...queued,
          state: 'FAILED',
          attempts: queued.attempts + 1,
          lastError: result.message,
        })
      } else {
        await remove(result.eventId)
      }
    }

    notify()
    return {
      accepted: response.accepted,
      flagged: response.flagged,
      duplicates: response.duplicates,
      failed: response.failed,
      remaining: (await listQueued()).length,
    }
  } catch (error) {
    // Could not reach the server. Everything stays queued for the next attempt.
    const message =
      error instanceof ApiError && error.isOffline
        ? 'Waiting for a connection.'
        : 'Could not upload yet. It will be tried again.'

    for (const event of pending) {
      await update({
        ...event,
        state: 'PENDING',
        attempts: event.attempts + 1,
        lastError: message,
      })
    }
    notify()
    return null
  } finally {
    syncing = false
  }
}

function toRequest(event: QueuedEvent) {
  return {
    eventId: event.eventId,
    eventType: event.eventType,
    eventTime: event.eventTime,
    deviceTimestamp: event.deviceTimestamp,
    latitude: event.latitude,
    longitude: event.longitude,
    gpsAccuracyM: event.gpsAccuracyM,
    siteName: event.siteName,
    destination: event.destination,
    destinationLocationId: event.destinationLocationId,
    photoId: event.photoId,
    deviceAssertion: event.deviceAssertion,
    captureMode: 'OFFLINE' as const,
    syncTimestamp: new Date().toISOString(),
    integrityHash: event.integrityHash,
    clientAppVersion: event.clientAppVersion,
  }
}

/**
 * A checksum over the event as it was captured.
 *
 * It cannot prove the contents are truthful - the browser belongs to the person
 * using it - but a mismatch when the event arrives is a strong signal that the
 * stored record was altered after capture, and the server flags it.
 */
export async function computeIntegrityHash(payload: Record<string, unknown>): Promise<string | undefined> {
  try {
    if (!globalThis.crypto?.subtle) {
      return undefined
    }
    const canonical = JSON.stringify(payload, Object.keys(payload).sort())
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(canonical))
    return Array.from(new Uint8Array(digest))
      .map((byte) => byte.toString(16).padStart(2, '0'))
      .join('')
  } catch {
    return undefined
  }
}

/** Retries automatically whenever the connection comes back. */
export function startAutoSync(): () => void {
  const attempt = () => {
    void syncNow()
  }

  window.addEventListener('online', attempt)
  const timer = window.setInterval(attempt, 60_000)
  attempt()

  return () => {
    window.removeEventListener('online', attempt)
    window.clearInterval(timer)
  }
}
