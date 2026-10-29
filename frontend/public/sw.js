/*
 * Trackr@Uni-Ace service worker.
 *
 * Its one job is to make the app *open* with no connection, so an employee
 * standing in a warehouse basement can still tap TIME IN. The activity itself
 * is queued in IndexedDB by the app, not here - a service worker cache is the
 * wrong place for something that must never be lost.
 *
 * API requests are deliberately never cached. Stale attendance data would be
 * worse than no data: it could show someone as timed in when they are not.
 */

const CACHE = 'trackr-shell-v1'
const SHELL = ['/', '/index.html', '/manifest.webmanifest', '/icons/icon-192.png']

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(CACHE)
      .then((cache) => cache.addAll(SHELL))
      // A missing asset must not stop the worker installing.
      .catch(() => undefined)
      .then(() => self.skipWaiting()),
  )
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== CACHE).map((key) => caches.delete(key))))
      .then(() => self.clients.claim()),
  )
})

self.addEventListener('fetch', (event) => {
  const request = event.request

  if (request.method !== 'GET') {
    return
  }

  const url = new URL(request.url)

  // Never cache the API, and never serve a cached answer for it.
  if (url.pathname.startsWith('/api/')) {
    return
  }

  // Map tiles come from a third party; let the browser handle them normally.
  if (url.origin !== self.location.origin) {
    return
  }

  // Navigations: try the network, fall back to the cached shell so the app
  // still opens offline.
  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request).catch(() =>
        caches.match('/index.html').then((cached) => cached ?? Response.error()),
      ),
    )
    return
  }

  // Static assets: serve from cache when we have them, and refresh in the
  // background so a new deploy is picked up on the next visit.
  event.respondWith(
    caches.match(request).then((cached) => {
      const network = fetch(request)
        .then((response) => {
          if (response.ok) {
            const copy = response.clone()
            void caches.open(CACHE).then((cache) => cache.put(request, copy))
          }
          return response
        })
        .catch(() => cached ?? Response.error())

      return cached ?? network
    }),
  )
})
