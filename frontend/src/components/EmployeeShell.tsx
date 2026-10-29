import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import { useOnline, useQueue } from './useOnline'

const NAV = [
  { to: '/home', label: 'Home', icon: '🏠' },
  { to: '/activities', label: 'Activities', icon: '📋' },
  { to: '/history', label: 'History', icon: '🗓️' },
  { to: '/profile', label: 'Profile', icon: '👤' },
  { to: '/help', label: 'Help', icon: '❓' },
]

/**
 * The employee interface: a phone screen with a header, one column of content
 * and five large navigation targets. No nested menus, no tabs within tabs.
 *
 * The offline state is shown at the top of every screen rather than hidden in a
 * corner - knowing whether an activity has reached the office is the single
 * thing employees most need reassurance about.
 */
export function EmployeeShell() {
  const { user } = useAuth()
  const online = useOnline()
  const queue = useQueue()

  return (
    <div className="employee-shell">
      <header className="employee-header">
        <span className="brand">Trackr@Uni-Ace</span>
        <span className="small" style={{ opacity: 0.9 }}>
          {user?.employeeNo}
        </span>
      </header>

      {!online && (
        <div
          role="status"
          style={{
            background: 'var(--warning-100)',
            color: 'var(--warning-700)',
            borderBottom: '1px solid #f2d0a3',
            padding: 'var(--space-3) var(--space-4)',
            fontWeight: 600,
            textAlign: 'center',
          }}
        >
          ⚠ You're offline. Your work activity can still be recorded.
        </div>
      )}

      {online && queue.length > 0 && (
        <div
          role="status"
          style={{
            background: 'var(--info-100)',
            color: 'var(--info-700)',
            borderBottom: '1px solid #b7d0ee',
            padding: 'var(--space-3) var(--space-4)',
            fontWeight: 600,
            textAlign: 'center',
          }}
        >
          Sending {queue.length} saved {queue.length === 1 ? 'activity' : 'activities'}…
        </div>
      )}

      <main className="employee-main">
        <Outlet />
      </main>

      <nav className="employee-nav" aria-label="Main">
        {NAV.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) => (isActive ? 'active' : '')}
          >
            {/* Icons always carry a word beside them - an icon alone is a
                guessing game for someone who does not use apps every day. */}
            <span className="nav-icon" aria-hidden="true">{item.icon}</span>
            <span>{item.label}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
