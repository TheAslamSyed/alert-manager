package com.democorp.warroom.common.contracts;

import java.util.EnumSet;
import java.util.Set;

/** Incident lifecycle (State pattern): each status lists the statuses it may move to. */
public enum IncidentStatus {
    OPEN,
    INVESTIGATING,
    IDENTIFIED,
    MITIGATING,
    MITIGATED,
    RESOLVED,
    CLOSED_FALSE_POSITIVE;

    public Set<IncidentStatus> next() {
        return switch (this) {
            case OPEN -> EnumSet.of(INVESTIGATING, CLOSED_FALSE_POSITIVE);
            case INVESTIGATING -> EnumSet.of(IDENTIFIED, CLOSED_FALSE_POSITIVE);
            case IDENTIFIED -> EnumSet.of(MITIGATING, INVESTIGATING, RESOLVED, CLOSED_FALSE_POSITIVE);
            case MITIGATING -> EnumSet.of(MITIGATED, IDENTIFIED);
            case MITIGATED -> EnumSet.of(RESOLVED, INVESTIGATING);
            case RESOLVED, CLOSED_FALSE_POSITIVE -> EnumSet.noneOf(IncidentStatus.class);
        };
    }

    public boolean canMoveTo(IncidentStatus target) {
        return next().contains(target);
    }

    public boolean isClosed() {
        return this == RESOLVED || this == CLOSED_FALSE_POSITIVE;
    }
}
