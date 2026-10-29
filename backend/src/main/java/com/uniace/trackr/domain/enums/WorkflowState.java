package com.uniace.trackr.domain.enums;

/**
 * Where the employee currently is in their workday, derived on the server from
 * the events already recorded today. The employee UI only ever offers the
 * actions this state allows.
 */
public enum WorkflowState {
    NOT_STARTED,
    AT_OFFICE,
    EN_ROUTE_TO_FIELD,
    AT_FIELD,
    EN_ROUTE_TO_OFFICE,
    EN_ROUTE_HOME,
    DAY_ENDED
}
