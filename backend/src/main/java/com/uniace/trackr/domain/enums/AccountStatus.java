package com.uniace.trackr.domain.enums;

/**
 * Lifecycle of a login account. Note that "revoked" is deliberately NOT an
 * account status - revocation applies to device credentials only.
 */
public enum AccountStatus {
    PENDING_ACTIVATION,
    ACTIVE,
    DISABLED
}
