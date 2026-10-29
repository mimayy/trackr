import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

// jsdom has no crypto.randomUUID in every version, and the offline queue relies
// on it to generate event ids.
if (!globalThis.crypto?.randomUUID) {
  Object.defineProperty(globalThis, 'crypto', {
    value: {
      ...globalThis.crypto,
      randomUUID: () => '00000000-0000-4000-8000-' + Math.random().toString(16).slice(2, 14).padEnd(12, '0'),
    },
    configurable: true,
  })
}

// Nothing in the tests should reach the network.
if (!('onLine' in navigator)) {
  Object.defineProperty(navigator, 'onLine', { value: true, configurable: true })
}
