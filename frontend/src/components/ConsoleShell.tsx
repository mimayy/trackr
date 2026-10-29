import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../lib/auth'

interface NavItem {
  to: string
  label: string
  icon: string
  end?: boolean
}

const ADMIN_NAV: NavItem[] = [
  { to: '/admin', label: 'Dashboard', icon: '▦', end: true },
  { to: '/admin/employees', label: 'Employees', icon: '👥' },
  { to: '/admin/supervisors', label: 'Supervisors', icon: '🧭' },
  { to: '/admin/locations', label: 'Locations', icon: '📍' },
  { to: '/admin/schedules', label: 'Schedules', icon: '🕗' },
  { to: '/admin/monitoring', label: 'Monitoring', icon: '🗺️' },
  { to: '/admin/exceptions', label: 'Exceptions', icon: '⚠' },
  { to: '/admin/reports', label: 'Reports', icon: '📊' },
  { to: '/admin/audit-logs', label: 'Audit Logs', icon: '🧾' },
  { to: '/admin/settings', label: 'Settings', icon: '⚙' },
]

const SUPERVISOR_NAV: NavItem[] = [
  { to: '/supervisor', label: 'Dashboard', icon: '▦', end: true },
  { to: '/supervisor/employees', label: 'Employees', icon: '👥' },
  { to: '/supervisor/monitoring', label: 'Monitoring', icon: '🗺️' },
  { to: '/supervisor/exceptions', label: 'Exceptions', icon: '⚠' },
  { to: '/supervisor/reports', label: 'Reports', icon: '📊' },
]

/**
 * The supervisor and administrator console: sidebar, dense tables, filters.
 *
 * It collapses into a drawer on a narrow screen so it stays usable on a tablet,
 * but it is not the employee interface shrunk - the two are separate on
 * purpose, because the jobs are separate.
 */
export function ConsoleShell({ role }: { role: 'ADMIN' | 'SUPERVISOR' }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [drawerOpen, setDrawerOpen] = useState(false)

  const nav = role === 'ADMIN' ? ADMIN_NAV : SUPERVISOR_NAV

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="console-shell">
      {drawerOpen && <div className="console-scrim" onClick={() => setDrawerOpen(false)} />}

      <aside className={`console-sidebar${drawerOpen ? ' open' : ''}`}>
        <div className="brand">
          Trackr@Uni-Ace
          <span className="brand-role">{role === 'ADMIN' ? 'Administrator' : 'Supervisor'}</span>
        </div>

        <nav className="console-nav" aria-label="Main">
          {nav.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              onClick={() => setDrawerOpen(false)}
              className={({ isActive }) => (isActive ? 'active' : '')}
            >
              <span className="nav-icon" aria-hidden="true">{item.icon}</span>
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="console-sidebar-footer">
          <div className="strong">{user?.displayName}</div>
          <div className="tiny" style={{ opacity: 0.7, marginBottom: 12 }}>{user?.email}</div>
          <button type="button" className="btn btn-secondary btn-sm btn-block" onClick={handleLogout}>
            Sign out
          </button>
        </div>
      </aside>

      <div className="console-body">
        <header className="console-header">
          <button
            type="button"
            className="btn btn-secondary btn-sm console-menu-button"
            onClick={() => setDrawerOpen((open) => !open)}
            aria-label="Toggle navigation"
          >
            ☰
          </button>
          <div className="row" style={{ marginLeft: 'auto' }}>
            <span className="small muted">
              {role === 'ADMIN' ? 'Administrator' : 'Supervisor'}: {user?.displayName}
            </span>
          </div>
        </header>

        <main className="console-main">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
