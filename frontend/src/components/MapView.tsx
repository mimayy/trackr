import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useEffect } from 'react'
import { Circle, MapContainer, Marker, Popup, TileLayer, useMap } from 'react-leaflet'
import type { ReactNode } from 'react'

const TILE_URL =
  (import.meta.env.VITE_MAP_TILE_URL as string | undefined) ??
  'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'

const ATTRIBUTION =
  (import.meta.env.VITE_MAP_ATTRIBUTION as string | undefined) ??
  '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'

export interface MapPin {
  id: string
  latitude: number
  longitude: number
  title: string
  /** Shown under the title; kept short. */
  subtitle?: string
  detail?: ReactNode
  tone?: 'brand' | 'success' | 'warning' | 'danger' | 'neutral'
}

export interface MapGeofence {
  id: string
  latitude: number
  longitude: number
  radiusMeters: number
  name: string
}

const TONE_COLOR: Record<NonNullable<MapPin['tone']>, string> = {
  brand: '#0f5c8c',
  success: '#22803f',
  warning: '#b96208',
  danger: '#c22a2a',
  neutral: '#6f7a87',
}

/**
 * Leaflet's default marker images are loaded from a CDN path that does not
 * survive bundling, so markers are drawn as inline SVG instead. That also lets
 * a pin carry its own colour, which is how event types stay distinguishable.
 *
 * @param label a sequence number, or null for a plain dot. A number on a pin
 *              reads as a count, so it is only ever shown where the order
 *              genuinely means something.
 */
function pinIcon(tone: NonNullable<MapPin['tone']>, label: string | null): L.DivIcon {
  const color = TONE_COLOR[tone]
  const face = label
    ? `<span style="
         position:absolute;top:5px;left:0;width:30px;text-align:center;
         font:700 11px/19px 'Segoe UI',system-ui,sans-serif;color:${color};">
         ${label}
       </span>`
    : `<span style="
         position:absolute;top:10px;left:11px;width:8px;height:8px;
         border-radius:50%;background:${color};"></span>`

  return L.divIcon({
    className: 'trackr-pin',
    html: `
      <div style="
        width:30px;height:40px;position:relative;transform:translate(-50%,-100%);
        filter:drop-shadow(0 2px 3px rgba(0,0,0,.35));">
        <svg viewBox="0 0 30 40" width="30" height="40" aria-hidden="true">
          <path d="M15 0C6.7 0 0 6.7 0 15c0 10.5 13.2 24 13.8 24.6a1.7 1.7 0 0 0 2.4 0C16.8 39 30 25.5 30 15 30 6.7 23.3 0 15 0z" fill="${color}"/>
          <circle cx="15" cy="14.5" r="10" fill="#fff"/>
        </svg>
        ${face}
      </div>`,
    iconSize: [30, 40],
    iconAnchor: [0, 0],
  })
}

/** Keeps every pin in view whenever the set of pins changes. */
function FitBounds({ pins }: { pins: MapPin[] }) {
  const map = useMap()

  useEffect(() => {
    if (pins.length === 0) {
      return
    }
    if (pins.length === 1) {
      map.setView([pins[0].latitude, pins[0].longitude], 16)
      return
    }
    map.fitBounds(
      L.latLngBounds(pins.map((pin) => [pin.latitude, pin.longitude] as [number, number])),
      { padding: [40, 40], maxZoom: 16 },
    )
  }, [map, pins])

  return null
}

export function MapView({
  pins,
  geofences = [],
  height = 400,
  center = [14.5547, 121.0244],
  zoom = 11,
  numbered = false,
}: {
  pins: MapPin[]
  geofences?: MapGeofence[]
  height?: number | string
  center?: [number, number]
  zoom?: number
  /**
   * Number the pins in order. Only true where the order is meaningful - one
   * employee's day, read top to bottom. On a map of several people it would
   * read as a count of something, so it stays off.
   */
  numbered?: boolean
}) {
  return (
    <div style={{ height, width: '100%' }}>
      <MapContainer center={center} zoom={zoom} scrollWheelZoom style={{ height: '100%', width: '100%' }}>
        <TileLayer url={TILE_URL} attribution={ATTRIBUTION} maxZoom={19} />

        {geofences.map((fence) => (
          <Circle
            key={fence.id}
            center={[fence.latitude, fence.longitude]}
            radius={fence.radiusMeters}
            pathOptions={{ color: '#0f5c8c', weight: 1, fillColor: '#0f5c8c', fillOpacity: 0.08 }}
          />
        ))}

        {pins.map((pin, index) => (
          <Marker
            key={pin.id}
            position={[pin.latitude, pin.longitude]}
            icon={pinIcon(pin.tone ?? 'brand', numbered ? String(index + 1) : null)}
          >
            <Popup>
              <div style={{ minWidth: 170 }}>
                <div style={{ fontWeight: 700, marginBottom: 2 }}>{pin.title}</div>
                {pin.subtitle && (
                  <div style={{ color: '#55606d', fontSize: 13 }}>{pin.subtitle}</div>
                )}
                {pin.detail && <div style={{ marginTop: 8, fontSize: 13 }}>{pin.detail}</div>}
              </div>
            </Popup>
          </Marker>
        ))}

        <FitBounds pins={pins} />
      </MapContainer>
    </div>
  )
}
