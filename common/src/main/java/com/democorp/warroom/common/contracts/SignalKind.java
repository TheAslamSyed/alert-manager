package com.democorp.warroom.common.contracts;

/** What kind of signal came in. */
public enum SignalKind {
    /** Metric alert from Alertmanager. Can open an incident. */
    ALERT,
    /** Log-based alert (Loki rule). Can open an incident. */
    LOG_ALERT,
    /** A release went out. Context only. */
    DEPLOY,
    /** A configuration value changed. Context only. */
    CONFIG_CHANGE,
    /** A customer or support ticket. Context only. */
    TICKET;

    /** Only alerts open incidents; the rest are attached as context. */
    public boolean opensIncident() {
        return this == ALERT || this == LOG_ALERT;
    }
}
