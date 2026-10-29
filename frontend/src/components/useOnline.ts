import { useEffect, useState } from 'react'
import { listQueued, onQueueChange, type QueuedEvent } from '../lib/offline'

/** Tracks connectivity so the UI can say plainly whether it is online. */
export function useOnline(): boolean {
  const [online, setOnline] = useState(() =>
    typeof navigator === 'undefined' ? true : navigator.onLine,
  )

  useEffect(() => {
    const goOnline = () => setOnline(true)
    const goOffline = () => setOnline(false)

    window.addEventListener('online', goOnline)
    window.addEventListener('offline', goOffline)
    return () => {
      window.removeEventListener('online', goOnline)
      window.removeEventListener('offline', goOffline)
    }
  }, [])

  return online
}

/** The offline queue, kept in step with whatever changes it. */
export function useQueue(): QueuedEvent[] {
  const [queue, setQueue] = useState<QueuedEvent[]>([])

  useEffect(() => {
    let active = true

    const load = () => {
      void listQueued().then((items) => {
        if (active) {
          setQueue(items)
        }
      })
    }

    load()
    const unsubscribe = onQueueChange(load)
    return () => {
      active = false
      unsubscribe()
    }
  }, [])

  return queue
}
