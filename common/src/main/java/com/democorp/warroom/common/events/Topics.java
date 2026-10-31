package com.democorp.warroom.common.events;

import java.util.List;

/** Kafka topic names. Every topic has a matching dead-letter topic ({@code <name>.DLT}). */
public final class Topics {

    public static final String SIGNALS = "signals.v1";
    public static final String INCIDENT = "incident.v1";
    public static final String INVESTIGATION = "investigation.v1";
    public static final String REMEDIATION = "remediation.v1";

    public static final List<String> ALL = List.of(SIGNALS, INCIDENT, INVESTIGATION, REMEDIATION);

    public static final String DLT_SUFFIX = ".DLT";

    private Topics() {
    }

    public static String deadLetter(String topic) {
        return topic + DLT_SUFFIX;
    }
}
