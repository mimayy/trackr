import { Navigate, Route, Routes } from 'react-router-dom'
import type { ReactNode } from 'react'
import { ConsoleShell } from './components/ConsoleShell'
import { EmployeeShell } from './components/EmployeeShell'
import { LoadingPanel } from './components/ui'
import { homePathFor, useAuth } from './lib/auth'
import type { Role } from './lib/types'

import { ActivatePage } from './pages/ActivatePage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'

import { ActivitiesPage } from './pages/employee/ActivitiesPage'
import { ActivityDetailPage } from './pages/employee/ActivityDetailPage'
import { EmployeeHomePage } from './pages/employee/EmployeeHomePage'
import { HelpPage } from './pages/employee/HelpPage'
import { HistoryPage } from './pages/employee/HistoryPage'
import { MyMapPage } from './pages/employee/MyMapPage'
import { ProfilePage } from './pages/employee/ProfilePage'
import { RecordEventPage } from './pages/employee/RecordEventPage'
import { SyncStatusPage } from './pages/employee/SyncStatusPage'

import { ConsoleDashboardPage } from './pages/console/ConsoleDashboardPage'
import { ConsoleEmployeeDetailPage } from './pages/console/ConsoleEmployeeDetailPage'
import { ConsoleEmployeesPage } from './pages/console/ConsoleEmployeesPage'
import { ExceptionsPage } from './pages/console/ExceptionsPage'
import { MonitoringPage } from './pages/console/MonitoringPage'
import { ReportsPage } from './pages/console/ReportsPage'

import { AdminEmployeeFormPage } from './pages/admin/AdminEmployeeFormPage'
import { AuditLogsPage } from './pages/admin/AuditLogsPage'
import { LocationsPage } from './pages/admin/LocationsPage'
import { SchedulesPage } from './pages/admin/SchedulesPage'
import { SettingsPage } from './pages/admin/SettingsPage'
import { SupervisorsPage } from './pages/admin/SupervisorsPage'

/**
 * Routing, and the first half of access control.
 *
 * Hiding a route is a convenience, never the control: every endpoint behind
 * these screens re-checks the caller's role and scope on the server.
 */
export function App() {
  const { user, loading } = useAuth()

  if (loading) {
    return (
      <div style={{ padding: 'var(--space-8) var(--space-4)', maxWidth: 480, margin: '0 auto' }}>
        <LoadingPanel label="Opening Trackr…" />
      </div>
    )
  }

  return (
    <Routes>
      {/* ---- Public ---- */}
      <Route
        path="/login"
        element={user ? <Navigate to={homePathFor(user)} replace /> : <LoginPage />}
      />
      <Route path="/activate" element={<ActivatePage />} />

      {/* ---- Employee ---- */}
      <Route
        element={
          <RequireRole roles={['EMPLOYEE', 'SUPERVISOR', 'ADMIN']}>
            <EmployeeShell />
          </RequireRole>
        }
      >
        <Route path="/home" element={<EmployeeHomePage />} />
        <Route path="/activities" element={<ActivitiesPage />} />
        <Route path="/activities/:id" element={<ActivityDetailPage />} />
        <Route path="/history" element={<HistoryPage />} />
        <Route path="/my-map" element={<MyMapPage />} />
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="/sync" element={<SyncStatusPage />} />
        <Route path="/help" element={<HelpPage />} />
      </Route>

      {/* Recording is full screen: one task, no navigation to wander into. */}
      <Route
        path="/record/:eventType"
        element={
          <RequireRole roles={['EMPLOYEE', 'SUPERVISOR', 'ADMIN']}>
            <RecordEventPage />
          </RequireRole>
        }
      />

      {/* ---- Supervisor console ---- */}
      <Route
        element={
          <RequireRole roles={['SUPERVISOR', 'ADMIN']}>
            <ConsoleShell role="SUPERVISOR" />
          </RequireRole>
        }
      >
        <Route path="/supervisor" element={<ConsoleDashboardPage />} />
        <Route path="/supervisor/employees" element={<ConsoleEmployeesPage />} />
        <Route path="/supervisor/employees/:id" element={<ConsoleEmployeeDetailPage />} />
        <Route path="/supervisor/monitoring" element={<MonitoringPage />} />
        <Route path="/supervisor/exceptions" element={<ExceptionsPage />} />
        <Route path="/supervisor/reports" element={<ReportsPage />} />
      </Route>

      {/* ---- Administrator console ---- */}
      <Route
        element={
          <RequireRole roles={['ADMIN']}>
            <ConsoleShell role="ADMIN" />
          </RequireRole>
        }
      >
        <Route path="/admin" element={<ConsoleDashboardPage />} />
        <Route path="/admin/employees" element={<ConsoleEmployeesPage />} />
        <Route path="/admin/employees/new" element={<AdminEmployeeFormPage />} />
        <Route path="/admin/employees/:id" element={<ConsoleEmployeeDetailPage />} />
        <Route path="/admin/employees/:id/edit" element={<AdminEmployeeFormPage />} />
        <Route path="/admin/supervisors" element={<SupervisorsPage />} />
        <Route path="/admin/locations" element={<LocationsPage />} />
        <Route path="/admin/schedules" element={<SchedulesPage />} />
        <Route path="/admin/monitoring" element={<MonitoringPage />} />
        <Route path="/admin/exceptions" element={<ExceptionsPage />} />
        <Route path="/admin/reports" element={<ReportsPage />} />
        <Route path="/admin/audit-logs" element={<AuditLogsPage />} />
        <Route path="/admin/settings" element={<SettingsPage />} />
      </Route>

      <Route path="/" element={<Navigate to={user ? homePathFor(user) : '/login'} replace />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

function RequireRole({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { user } = useAuth()

  if (!user) {
    return <Navigate to="/login" replace />
  }
  if (!roles.includes(user.role)) {
    return <Navigate to={homePathFor(user)} replace />
  }
  return <>{children}</>
}
