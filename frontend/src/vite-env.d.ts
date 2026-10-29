/// <reference types="vite/client" />

/**
 * Build-time configuration.
 *
 * Vite inlines these into the bundle, so they are visible to anyone who opens
 * the page. Nothing secret belongs here - API keys for anything that matters
 * stay on the server.
 */
interface ImportMetaEnv {
  /** Base URL of the Trackr API, e.g. https://trackr-uni-ace-api.onrender.com */
  readonly VITE_API_BASE_URL?: string
  /** Map tile template. Defaults to OpenStreetMap, which needs no key. */
  readonly VITE_MAP_TILE_URL?: string
  readonly VITE_MAP_ATTRIBUTION?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
