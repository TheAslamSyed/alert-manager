package com.democorp.warroom.common.events;

/** Event type names, grouped by topic. */
public final class EventTypes {

    // signals.v1
    public static final String SIGNAL_RECEIVED = "SignalReceived";

    // incident.v1
    public static final String INCIDENT_OPENED = "IncidentOpened";
    public static final String INCIDENT_UPDATED = "IncidentUpdated";
    public static final String INCIDENT_STATUS_CHANGED = "IncidentStatusChanged";
    public static final String COMMS_MESSAGE_SENT = "CommsMessageSent";

    // investigation.v1
    public static final String INVESTIGATION_STARTED = "InvestigationStarted";
    public static final String AGENT_FINDING = "AgentFinding";
    public static final String RCA_COMPLETED = "RcaCompleted";
    public static final String REMEDIATION_PROPOSED = "RemediationProposed";
    public static final String COMMS_DRAFTED = "CommsDrafted";
    public static final String INVESTIGATION_COMPLETED = "InvestigationCompleted";

    // remediation.v1
    public static final String ACTION_APPROVED = "ActionApproved";
    public static final String ACTION_REJECTED = "ActionRejected";
    public static final String ACTION_EXECUTED = "ActionExecuted";
    public static final String ACTION_FAILED = "ActionFailed";

    private EventTypes() {
    }
}
