package com.democorp.bank.scenario;

/** The fixes DemoCorp's admin endpoints support. The War Room's executor can only call these. */
public enum FixAction {
    ROLLBACK_DEPLOYMENT,
    FAILOVER_PAYMENT_ROUTE,
    ROTATE_CERTIFICATE,
    ENABLE_THROTTLING,
    REVERT_CONFIG,
    REFUND_DUPLICATES,
    REVERT_FRAUD_RULE,
    MOVE_MESSAGE_TO_DLQ,
    RESTART_CACHE,
    ENABLE_STEP_UP_AUTH,
    BLOCK_IP_RANGE,
    REVOKE_API_TOKEN
}
