import { fileURLToPath, URL } from 'node:url'
import react from '@vitejs/plugin-react'
// vitest/config re-exports Vite's defineConfig with the `test` block typed,
// so one config file covers both the build and the tests.
import { defineConfig } from 'vitest/config'
import type { ProxyOptions } from 'vite'

/** Where the dev server should forward /api to. */
const API_TARGET = process.env.API_PROXY_TARGET ?? 'http://127.0.0.1:8080'

/**
 * Forward a path to the API as if the API had served it itself.
 *
 * `changeOrigin` rewrites the Host header but deliberately leaves Origin alone,
 * and Origin is the one the API's CORS filter reads. Over a tunnel the browser
 * sends `Origin: https://<something>.trycloudflare.com`, which the API has
 * never heard of, so a perfectly ordinary sign-in comes back 403 "Invalid CORS
 * request".
 *
 * Dropping the header is the honest description of what is happening: the
 * browser is talking to one origin, and this hop is the server fetching from
 * itself. Spring treats a request with no Origin as a plain, non-CORS request.
 * The API's allowlist is left strict, which is where it matters - anything
 * reaching it from outside this proxy is still checked.
 */
function apiProxy(): ProxyOptions {
  return {
    target: API_TARGET,
    changeOrigin: true,
    configure: (proxy) => {
      proxy.on('proxyReq', (proxyReq) => {
        proxyReq.removeHeader('origin')
      })
    },
  }
}

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    // Listening on all interfaces is what makes it possible to open the app on
    // a real phone over the local network, or through an HTTPS tunnel - the
    // only ways to exercise the fingerprint and GPS flows properly.
    host: true,
    proxy: {
      // The API is served from the same origin as the app in development.
      //
      // This is not just convenience. It means a phone only needs ONE tunnel to
      // reach the whole system, and it removes CORS from the picture entirely -
      // the browser only ever talks to Vite, which forwards to the API itself.
      '/api': apiProxy(),
      '/actuator': apiProxy(),
    },
  },
  preview: {
    port: 5173,
    host: true,
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks: {
          // Maps are needed on a few screens only. Splitting Leaflet out keeps
          // the employee's first load small on a phone.
          maps: ['leaflet', 'react-leaflet'],
          router: ['react-router-dom'],
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
