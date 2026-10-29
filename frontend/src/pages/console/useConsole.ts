import { useLocation } from 'react-router-dom'
import { useAuth } from '../../lib/auth'

/**
 * Supervisors and administrators share most screens.
 *
 * The shared read endpoints live under {@code /api/supervisor} and accept both
 * roles - the server narrows the results to the caller's own scope, so an
 * administrator sees the company and a supervisor sees their team from exactly
 * the same call. Administrator-only actions use {@code /api/admin}.
 */
export function useConsole() {
  const { user } = useAuth()
  const location = useLocation()

  const isAdminArea = location.pathname.startsWith('/admin')
  const isAdmin = user?.role === 'ADMIN'

  return {
    /** Endpoint prefix for data both roles may read. */
    api: '/api/supervisor',
    /** Endpoint prefix for administrator-only actions. */
    adminApi: '/api/admin',
    /** Route prefix, so links stay inside the console the user is in. */
    base: isAdminArea ? '/admin' : '/supervisor',
    isAdmin,
    isAdminArea,
    /** True when this user may create, edit or configure, not just view. */
    canManage: isAdmin && isAdminArea,
  }
}
