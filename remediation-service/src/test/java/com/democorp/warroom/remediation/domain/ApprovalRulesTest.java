package com.democorp.warroom.remediation.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalRulesTest {

    private static final Set<String> APPROVER = Set.of("APPROVER", "RESPONDER");

    private RemediationAction action(String risk, int approvals) {
        RemediationAction a = new RemediationAction("ACT-1", "INC-1", "INV-1", "ROLLBACK_DEPLOYMENT",
                "payment-service", risk, approvals, "Roll back", "reason", "Remediation planner");
        a.submit("asha");
        return a;
    }

    @Test
    void mediumRiskNeedsOneApproverWhoIsNotTheSubmitter() {
        RemediationAction a = action("MEDIUM", 1);
        assertThatThrownBy(() -> a.approve("asha", APPROVER, "self"))
                .hasMessageContaining("cannot approve an action you sent");
        assertThat(a.approve("priya", APPROVER, "evidence is clear")).isTrue();
        assertThat(a.getStatus()).isEqualTo(RemediationAction.Status.APPROVED);
    }

    @Test
    void highRiskNeedsTwoDifferentApprovers() {
        RemediationAction a = action("HIGH", 2);
        assertThat(a.approve("priya", APPROVER, "ok")).isFalse();
        assertThatThrownBy(() -> a.approve("priya", APPROVER, "again")).hasMessageContaining("different second approver");
        assertThat(a.approve("meera", APPROVER, "ok")).isTrue();
    }

    @Test
    void adminsAndRespondersCannotApprove() {
        RemediationAction a = action("MEDIUM", 1);
        assertThatThrownBy(() -> a.approve("omar", Set.of("ADMIN", "APPROVER"), "x")).hasMessageContaining("Admins");
        assertThatThrownBy(() -> a.approve("bob", Set.of("RESPONDER"), "x")).hasMessageContaining("Only an APPROVER");
    }

    @Test
    void cannotRunWithoutApproval() {
        RemediationAction a = action("MEDIUM", 1);
        assertThatThrownBy(a::startExecuting).hasMessageContaining("Only an approved action");
    }
}
