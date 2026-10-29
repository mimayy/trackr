import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { MapView } from '../../components/MapView'
import { Alert, LoadingPanel, Modal, Pill, Spinner } from '../../components/ui'
import { ApiError, api } from '../../lib/api'
import type { Branch, LocationRow, LocationType } from '../../lib/types'

const TYPE_LABEL: Record<LocationType, string> = {
  OFFICE: 'Offices',
  WAREHOUSE: 'Warehouses',
  FIELD: 'Field locations',
}

/**
 * Offices, warehouses and field locations, each with the geofence used to
 * verify arrivals.
 *
 * The map in the editor is not decoration: a radius typed into a box is hard to
 * judge, and seeing the circle over the actual site is how a mistake gets
 * caught before an employee is refused at the gate.
 */
export function LocationsPage() {
  const [rows, setRows] = useState<LocationRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [editing, setEditing] = useState<LocationRow | 'new' | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setRows(await api.get<LocationRow[]>('/api/admin/locations'))
      setError(null)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'We could not load locations.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const grouped: Array<[LocationType, LocationRow[]]> = (['OFFICE', 'WAREHOUSE', 'FIELD'] as LocationType[])
    .map((type) => [type, rows.filter((row) => row.type === type)])

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Locations</h1>
          <p className="page-subtitle">
            Each location's geofence decides whether an arrival can be verified there.
          </p>
        </div>
        <button type="button" className="btn btn-primary" onClick={() => setEditing('new')}>
          + Add location
        </button>
      </div>

      {notice && <Alert tone="success">{notice}</Alert>}
      {error && <Alert tone="error">{error}</Alert>}
      {loading && <LoadingPanel />}

      {!loading && (
        <div className="stack-lg">
          {rows.length > 0 && (
            <div className="map-panel">
              <MapView
                height="100%"
                pins={rows.map((row) => ({
                  id: row.id,
                  latitude: row.latitude,
                  longitude: row.longitude,
                  title: row.name,
                  subtitle: row.address,
                  detail: `Geofence radius: ${row.geofenceRadiusM} m`,
                  tone: row.type === 'OFFICE' ? 'brand' : row.type === 'WAREHOUSE' ? 'warning' : 'success',
                }))}
                geofences={rows.map((row) => ({
                  id: row.id,
                  latitude: row.latitude,
                  longitude: row.longitude,
                  radiusMeters: row.geofenceRadiusM,
                  name: row.name,
                }))}
              />
            </div>
          )}

          {grouped.map(([type, list]) => (
            <section key={type}>
              <h2 style={{ marginBottom: 'var(--space-3)' }}>{TYPE_LABEL[type]}</h2>
              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Name</th>
                      <th>Branch</th>
                      <th>Address</th>
                      <th>Coordinates</th>
                      <th>Geofence</th>
                      <th>Status</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {list.map((row) => (
                      <tr key={row.id}>
                        <td className="strong">{row.name}</td>
                        <td>{row.branch ? (row.branch === 'MANILA' ? 'Manila' : 'Cebu') : '—'}</td>
                        <td>{row.address ?? <span className="muted">—</span>}</td>
                        <td className="tiny muted">
                          {row.latitude.toFixed(5)}, {row.longitude.toFixed(5)}
                        </td>
                        <td>{row.geofenceRadiusM} m</td>
                        <td>
                          {row.active ? <Pill tone="success">Active</Pill> : <Pill tone="neutral">Inactive</Pill>}
                        </td>
                        <td>
                          <button type="button" className="btn btn-secondary btn-sm" onClick={() => setEditing(row)}>
                            Edit
                          </button>
                        </td>
                      </tr>
                    ))}
                    {list.length === 0 && (
                      <tr>
                        <td colSpan={7} className="table-empty">None configured.</td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </section>
          ))}
        </div>
      )}

      {editing && (
        <LocationEditor
          location={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={(saved) => {
            setEditing(null)
            setNotice(`${saved.name} saved.`)
            void load()
          }}
        />
      )}
    </div>
  )
}

function LocationEditor({
  location,
  onClose,
  onSaved,
}: {
  location: LocationRow | null
  onClose: () => void
  onSaved: (saved: LocationRow) => void
}) {
  const [name, setName] = useState(location?.name ?? '')
  const [type, setType] = useState<LocationType>(location?.type ?? 'WAREHOUSE')
  const [branch, setBranch] = useState<Branch | ''>(location?.branch ?? '')
  const [address, setAddress] = useState(location?.address ?? '')
  const [latitude, setLatitude] = useState(String(location?.latitude ?? '14.5547'))
  const [longitude, setLongitude] = useState(String(location?.longitude ?? '121.0244'))
  const [radius, setRadius] = useState(String(location?.geofenceRadiusM ?? 100))
  const [active, setActive] = useState(location?.active ?? true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const lat = Number(latitude)
  const lon = Number(longitude)
  const radiusNumber = Number(radius)
  const previewable = Number.isFinite(lat) && Number.isFinite(lon) && Number.isFinite(radiusNumber)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)

    const payload = {
      name: name.trim(),
      type,
      branch: branch || undefined,
      address: address.trim() || undefined,
      latitude: lat,
      longitude: lon,
      geofenceRadiusM: radiusNumber,
      active,
    }

    try {
      const saved = location
        ? await api.patch<LocationRow>(`/api/admin/locations/${location.id}`, payload)
        : await api.post<LocationRow>('/api/admin/locations', payload)
      onSaved(saved)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'That location could not be saved.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={location ? `Edit ${location.name}` : 'Add location'} onClose={onClose} width={620}>
      <form onSubmit={handleSubmit} className="stack">
        {error && <Alert tone="error">{error}</Alert>}

        <div className="field">
          <label className="label" htmlFor="l-name">Name</label>
          <input id="l-name" className="input" required maxLength={160} value={name} onChange={(e) => setName(e.target.value)} />
        </div>

        <div className="grid-form">
          <div className="field">
            <label className="label" htmlFor="l-type">Type</label>
            <select id="l-type" className="select" value={type} onChange={(e) => setType(e.target.value as LocationType)}>
              <option value="OFFICE">Office</option>
              <option value="WAREHOUSE">Warehouse</option>
              <option value="FIELD">Field location</option>
            </select>
          </div>
          <div className="field">
            <label className="label" htmlFor="l-branch">Branch</label>
            <select id="l-branch" className="select" value={branch} onChange={(e) => setBranch(e.target.value as Branch | '')}>
              <option value="">Not branch specific</option>
              <option value="MANILA">Manila</option>
              <option value="CEBU">Cebu</option>
            </select>
          </div>
        </div>

        <div className="field">
          <label className="label" htmlFor="l-address">Address</label>
          <input id="l-address" className="input" maxLength={400} value={address} onChange={(e) => setAddress(e.target.value)} />
        </div>

        <div className="grid-form">
          <div className="field">
            <label className="label" htmlFor="l-lat">Latitude</label>
            <input id="l-lat" className="input" required inputMode="decimal" value={latitude} onChange={(e) => setLatitude(e.target.value)} />
          </div>
          <div className="field">
            <label className="label" htmlFor="l-lon">Longitude</label>
            <input id="l-lon" className="input" required inputMode="decimal" value={longitude} onChange={(e) => setLongitude(e.target.value)} />
          </div>
          <div className="field">
            <label className="label" htmlFor="l-radius">Geofence radius (metres)</label>
            <input
              id="l-radius"
              className="input"
              type="number"
              min={10}
              max={5000}
              required
              value={radius}
              onChange={(e) => setRadius(e.target.value)}
            />
            <span className="field-hint">
              An employee is verified as "here" within this distance. 100–150 m suits most sites.
            </span>
          </div>
        </div>

        {previewable && (
          <div style={{ height: 240, borderRadius: 'var(--radius-md)', overflow: 'hidden', border: '1px solid var(--border)' }}>
            <MapView
              height="100%"
              zoom={16}
              center={[lat, lon]}
              pins={[{ id: 'preview', latitude: lat, longitude: lon, title: name || 'New location', tone: 'brand' }]}
              geofences={[{ id: 'preview', latitude: lat, longitude: lon, radiusMeters: radiusNumber, name: name || 'New location' }]}
            />
          </div>
        )}

        <label className="checkbox-row">
          <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
          <span>Active — employees can record arrivals here</span>
        </label>

        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <button type="button" className="btn btn-secondary" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? <Spinner light label="Saving…" /> : 'SAVE LOCATION'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
