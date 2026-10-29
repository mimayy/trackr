import { Link } from 'react-router-dom'
import { homePathFor, useAuth } from '../lib/auth'

export function NotFoundPage() {
  const { user } = useAuth()

  return (
    <div
      style={{
        minHeight: '100dvh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 'var(--space-5)',
      }}
    >
      <div className="card center stack" style={{ maxWidth: 420 }}>
        <div style={{ fontSize: '2.5rem' }} aria-hidden="true">🧭</div>
        <h1>That page isn't here</h1>
        <p className="muted">The link may be old, or something was mistyped.</p>
        <Link className="btn btn-primary btn-hero" to={user ? homePathFor(user) : '/login'}>
          {user ? 'GO TO HOME' : 'GO TO SIGN IN'}
        </Link>
      </div>
    </div>
  )
}
