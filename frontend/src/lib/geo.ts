/**
 * Getting the employee's position for a work event.
 *
 * Location is read only at the moment an event is recorded - never in the
 * background, never on a timer. That is the whole basis of the product's
 * privacy promise, so there is deliberately no "watch position" helper here.
 */

export interface Position {
  latitude: number
  longitude: number
  accuracy: number
  capturedAt: string
}

export class LocationError extends Error {
  readonly code: 'DENIED' | 'UNAVAILABLE' | 'TIMEOUT' | 'UNSUPPORTED'

  constructor(code: LocationError['code'], message: string) {
    super(message)
    this.name = 'LocationError'
    this.code = code
  }
}

/**
 * Reads the current position.
 *
 * Every failure is translated into something the employee can act on - "turn on
 * Location Services", not "PERMISSION_DENIED".
 */
export function getCurrentPosition(timeoutMs = 20_000): Promise<Position> {
  return new Promise((resolve, reject) => {
    if (!('geolocation' in navigator)) {
      reject(
        new LocationError(
          'UNSUPPORTED',
          "This phone's browser can't share your location. Please ask your administrator for help.",
        ),
      )
      return
    }

    navigator.geolocation.getCurrentPosition(
      (position) =>
        resolve({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          // Some browsers omit accuracy; treat that as "unknown but usable"
          // rather than refusing to record the event at all.
          accuracy: Number.isFinite(position.coords.accuracy) ? position.coords.accuracy : 50,
          capturedAt: new Date(position.timestamp).toISOString(),
        }),
      (error) => {
        switch (error.code) {
          case error.PERMISSION_DENIED:
            reject(
              new LocationError(
                'DENIED',
                'Trackr needs permission to use your location. Please turn on Location Services for this app and try again.',
              ),
            )
            break
          case error.POSITION_UNAVAILABLE:
            reject(
              new LocationError(
                'UNAVAILABLE',
                "We can't find your location right now. Please move to an open area and try again.",
              ),
            )
            break
          default:
            reject(
              new LocationError(
                'TIMEOUT',
                'Finding your location is taking too long. Please move to an open area and try again.',
              ),
            )
        }
      },
      {
        enableHighAccuracy: true,
        timeout: timeoutMs,
        // Never reuse a stale fix for a work event; it must be read now.
        maximumAge: 0,
      },
    )
  })
}

/** Great-circle distance in metres, used for the "how far away" hints. */
export function distanceMeters(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number,
): number {
  const earthRadius = 6_371_008.8
  const toRad = (value: number) => (value * Math.PI) / 180

  const dLat = toRad(lat2 - lat1)
  const dLon = toRad(lon2 - lon1)
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2

  return 2 * earthRadius * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

export function describeAccuracy(accuracy: number): string {
  if (accuracy <= 15) return 'Very good'
  if (accuracy <= 35) return 'Good'
  if (accuracy <= 75) return 'Fair'
  return 'Poor'
}
