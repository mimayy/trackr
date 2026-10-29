/**
 * Shapes returned by the Trackr API.
 *
 * These mirror the backend DTOs. They are hand-written rather than generated so
 * the front end stays buildable on its own, and are kept deliberately narrow -
 * only fields the UI actually uses.
 */

export type Role = 'EMPLOYEE' | 'SUPERVISOR' | 'ADMIN'
export type AccountStatus = 'PENDING_ACTIVATION' | 'ACTIVE' | 'DISABLED'
export type CredentialStatus = 'ACTIVE' | 'REVOKED'
export type Branch = 'MANILA' | 'CEBU'
export type WorkCategory = 'OFFICE_BASED' | 'FIELD_BASED'
export type LocationType = 'OFFICE' | 'WAREHOUSE' | 'FIELD'
export type CaptureMode = 'ONLINE' | 'OFFLINE'
export type SyncStatus = 'PENDING' | 'SYNCING' | 'SYNCED' | 'FAILED' | 'FLAGGED'
export type Disposition = 'ACCEPTED' | 'FLAGGED'
export type ReviewStatus = 'NONE' | 'REQUIRES_REVIEW' | 'REVIEWED_OK' | 'REVIEWED_ADJUSTED'
export type FlagSeverity = 'INFO' | 'WARNING' | 'CRITICAL'
export type FlagCategory =
  | 'ATTENDANCE' | 'LOCATION' | 'AUTHENTICATION' | 'OFFLINE_SYNC' | 'WORKFLOW'
export type Destination = 'OFFICE' | 'HOME' | 'ANOTHER_FIELD_LOCATION'

export type EventType =
  | 'TIME_IN'
  | 'OFFICE_DEPARTURE'
  | 'FIELD_ARRIVAL'
  | 'FIELD_DEPARTURE'
  | 'OFFICE_ARRIVAL'
  | 'TIME_OUT'

export type WorkflowState =
  | 'NOT_STARTED'
  | 'AT_OFFICE'
  | 'EN_ROUTE_TO_FIELD'
  | 'AT_FIELD'
  | 'EN_ROUTE_TO_OFFICE'
  | 'EN_ROUTE_HOME'
  | 'DAY_ENDED'

export type AttendanceStatus =
  | 'ON_TIME' | 'LATE' | 'UNDERTIME' | 'OVERTIME'
  | 'ABSENT' | 'INCOMPLETE' | 'REST_DAY' | 'HOLIDAY'

export interface SessionUser {
  userId: string
  email: string
  displayName: string
  firstName: string
  role: Role
  accountStatus: AccountStatus
  employeeId?: string
  employeeNo?: string
  branch?: Branch
  workCategory?: WorkCategory
  supervisorName?: string
  scheduleName?: string
  homeOfficeName?: string
  deviceRegistered: boolean
  deviceLabel?: string
}

export interface SessionResponse {
  accessToken: string
  refreshToken: string
  expiresInSeconds: number
  user: SessionUser
}

export interface ActivationDetails {
  firstName: string
  fullName: string
  employeeNo?: string
  email: string
  branch?: Branch
  workCategory?: WorkCategory
  supervisorName?: string
  scheduleName?: string
  expiresAt: string
}

export interface InvitationResponse {
  activationUrl: string
  expiresAt: string
  emailSent: boolean
  emailNote?: string
}

export interface LocationView {
  id: string
  name: string
  type: LocationType
  address?: string
  latitude?: number
  longitude?: number
  geofenceRadiusM?: number
}

export interface FlagView {
  category: FlagCategory
  code: string
  severity: FlagSeverity
  label: string
  detail?: string
}

export interface EmployeeEventView {
  id: string
  eventType: EventType
  eventLabel: string
  eventTime: string
  businessDate: string
  locationName?: string
  destination?: Destination
  destinationName?: string
  locationVerified: boolean
  fingerprintVerified: boolean
  hasPhoto: boolean
  photoId?: string
  syncStatus: SyncStatus
  captureMode: CaptureMode
  needsReview: boolean
  latitude?: number
  longitude?: number
  siteName?: string
}

export interface StaffEventView {
  id: string
  employeeId: string
  employeeNo: string
  employeeName: string
  branch: Branch
  workCategory: WorkCategory
  eventType: EventType
  eventLabel: string
  eventTime: string
  deviceTimestamp?: string
  syncTimestamp?: string
  receivedAt: string
  businessDate: string
  location?: LocationView
  latitude?: number
  longitude?: number
  gpsAccuracyM?: number
  distanceToLocationM?: number
  withinGeofence?: boolean
  destination?: Destination
  destinationName?: string
  biometricVerified: boolean
  deviceLabel?: string
  hasPhoto: boolean
  photoId?: string
  captureMode: CaptureMode
  syncStatus: SyncStatus
  disposition: Disposition
  reviewStatus: ReviewStatus
  reviewNotes?: string
  siteName?: string
  flags: FlagView[]
}

export interface ActionOption {
  eventType: EventType
  label: string
  helper: string
  requiresLocation: boolean
  requiresSiteName: boolean
}

export interface WorkflowStatus {
  state: WorkflowState
  statusLabel: string
  statusDetail: string
  workdayStartedAt?: string
  currentLocationName?: string
  allowedActions: ActionOption[]
  nextAction?: ActionOption
  requiresDestination: boolean
  fingerprintAvailable: boolean
  photoSupported: boolean
  /** Warehouse names this employee has used before, newest first. */
  recentSiteNames: string[]
  todaysEvents: EmployeeEventView[]
}

export interface AttendanceDayView {
  date: string
  scheduleName?: string
  scheduledStart?: string
  scheduledEnd?: string
  timeIn?: string
  timeOut?: string
  hoursWorked: number
  hoursWorkedLabel: string
  unpaidBreakMinutes: number
  lateMinutes: number
  undertimeMinutes: number
  overtimeMinutes: number
  restDay: boolean
  holiday: boolean
  holidayName?: string
  complete: boolean
  statuses: AttendanceStatus[]
}

export interface LatestLocationView {
  employeeId: string
  employeeNo: string
  employeeName: string
  branch: Branch
  workCategory: WorkCategory
  accountStatus: AccountStatus
  lastEventType?: EventType
  lastEventLabel?: string
  lastEventTime?: string
  locationName?: string
  latitude?: number
  longitude?: number
  workflowState: WorkflowState
  statusLabel: string
}

export interface EmployeeRow {
  id: string
  employeeNo: string
  fullName: string
  email: string
  contactNumber?: string
  branch: Branch
  workCategory: WorkCategory
  supervisorName?: string
  supervisorId?: string
  scheduleName?: string
  scheduleId?: string
  accountStatus: AccountStatus
  deviceStatus?: CredentialStatus
  deviceLabel?: string
  latestLocationName?: string
  latestEventTime?: string
  latestEventType?: EventType
  latestEventLabel?: string
}

export interface DeviceSecurityView {
  credentialId: string
  deviceLabel?: string
  status: CredentialStatus
  algorithm: string
  registeredAt: string
  lastUsedAt?: string
  revokedAt?: string
  revokedReason?: string
}

export interface EmployeeDetail {
  summary: EmployeeRow
  homeOfficeName?: string
  hiredOn?: string
  createdAt: string
  lastLoginAt?: string
  device?: DeviceSecurityView
  invitationPending: boolean
  invitationExpiresAt?: string
}

export interface SupervisorRow {
  id: string
  fullName: string
  email: string
  contactNumber?: string
  branch: Branch
  scopeNote?: string
  accountStatus: AccountStatus
  employeeCount: number
  active: boolean
}

export interface LocationRow {
  id: string
  name: string
  type: LocationType
  branch?: Branch
  address?: string
  latitude: number
  longitude: number
  geofenceRadiusM: number
  active: boolean
}

export interface ScheduleRow {
  id: string
  name: string
  startTime: string
  endTime: string
  workingDays: string[]
  breakStart?: string
  breakEnd?: string
  breakPaid: boolean
  timezone: string
  active: boolean
  scheduledHours: number
  employeeCount: number
}

export interface HolidayRow {
  id: string
  date: string
  name: string
  branch?: Branch
  paid: boolean
}

export interface DashboardResponse {
  date: string
  totalEmployees: number
  present: number
  late: number
  absent: number
  inField: number
  atOffice: number
  finished: number
  pendingActivation: number
  requiresReview: number
  recentActivity: StaffEventView[]
  latestLocations: LatestLocationView[]
}

export interface ExceptionRow {
  eventId: string
  employeeId: string
  employeeName: string
  employeeNo: string
  eventType: EventType
  eventLabel: string
  eventTime: string
  businessDate: string
  locationName?: string
  captureMode: CaptureMode
  reviewStatus: ReviewStatus
  flags: FlagView[]
  highestSeverity: FlagSeverity
}

export interface AuditRow {
  id: string
  occurredAt: string
  actorLabel: string
  actorRole: string
  action: string
  targetType?: string
  targetId?: string
  targetLabel?: string
  result: 'SUCCESS' | 'FAILURE' | 'FLAGGED' | 'DENIED'
  detail?: string
  ipAddress?: string
}

export interface SettingRow {
  key: string
  value: string
  description?: string
  updatedAt: string
  updatedByName?: string
}

export interface AdjustmentRow {
  id: string
  eventId: string
  field: string
  originalValue?: string
  adjustedValue?: string
  reason: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  requestedByName?: string
  requestedAt: string
  decidedByName?: string
  decidedAt?: string
  decisionNote?: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

/* ---- Reports -------------------------------------------------------------- */

export interface ReportMeta {
  reportType: string
  from: string
  to: string
  rowCount: number
  generatedAt: string
}

export interface AttendanceReportRow {
  employeeId: string
  employeeNo: string
  employeeName: string
  branch: Branch
  workCategory: WorkCategory
  date: string
  scheduleName?: string
  scheduledStart?: string
  scheduledEnd?: string
  timeIn?: string
  timeOut?: string
  hoursWorked: number
  hoursWorkedLabel: string
  lateMinutes: number
  undertimeMinutes: number
  overtimeMinutes: number
  statuses: AttendanceStatus[]
}

export interface AttendanceReport {
  meta: ReportMeta
  rows: AttendanceReportRow[]
  totals: {
    totalHoursWorked: number
    totalLateMinutes: number
    totalUndertimeMinutes: number
    totalOvertimeMinutes: number
    daysPresent: number
    daysAbsent: number
    daysIncomplete: number
  }
}

export interface LateReportRow {
  employeeId: string
  employeeNo: string
  employeeName: string
  branch: Branch
  date: string
  scheduledStart?: string
  actualTimeIn?: string
  localTimeIn?: string
  minutesLate: number
}

export interface LateReport {
  meta: ReportMeta
  rows: LateReportRow[]
}

export interface FieldActivityRow {
  employeeId: string
  employeeNo: string
  employeeName: string
  siteName?: string
  date: string
  arrivalTime?: string
  departureTime?: string
  minutesOnSite?: number
  destination?: string
}

export interface FieldActivityReport {
  meta: ReportMeta
  rows: FieldActivityRow[]
}

export interface OfficeActivityRow {
  employeeId: string
  employeeNo: string
  employeeName: string
  date: string
  officeDeparture?: string
  officeArrival?: string
  minutesAway?: number
  destination?: string
}

export interface OfficeActivityReport {
  meta: ReportMeta
  rows: OfficeActivityRow[]
}

export interface EmployeeActivityReport {
  meta: ReportMeta
  employeeId: string
  employeeName: string
  employeeNo: string
  events: StaffEventView[]
}
