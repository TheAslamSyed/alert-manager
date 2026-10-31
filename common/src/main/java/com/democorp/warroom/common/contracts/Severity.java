package com.democorp.warroom.common.contracts;

import java.util.Locale;

/** Incident severity. SEV1 is the most serious. */
public enum Severity {
    SEV1, SEV2, SEV3, SEV4;

    /** Maps common alert label values (critical, high, warning, info) to a severity. */
    public static Severity fromLabel(String label) {
        if (label == null) {
            return SEV3;
        }
        return switch (label.toLowerCase(Locale.ROOT)) {
            case "critical", "sev1", "p1" -> SEV1;
            case "high", "major", "sev2", "p2" -> SEV2;
            case "warning", "medium", "sev3", "p3" -> SEV3;
            default -> SEV4;
        };
    }

    public Severity moreSevere(Severity other) {
        return other == null || this.ordinal() <= other.ordinal() ? this : other;
    }
}
