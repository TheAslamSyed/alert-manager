package com.democorp.warroom.common.contracts;

import java.util.List;

/** Payloads published by remediation-service on {@code remediation.v1}. */
public final class Remediation {

    private Remediation() {
    }

    /**
     * @param approvals usernames that approved (HIGH risk needs two different people)
     * @param signature HMAC over the approved command; the executor refuses anything unsigned
     */
    public record Decision(String actionId, String incidentId, String action, String target, String risk,
                           String decision, String decidedBy, String reason, List<String> approvals,
                           String signature) {
    }

    /** outcome: SUCCEEDED, FAILED_VERIFICATION, NO_EFFECT, ERROR. */
    public record Result(String actionId, String incidentId, String action, String target, String outcome,
                         boolean verified, String message, String verification) {
    }
}
