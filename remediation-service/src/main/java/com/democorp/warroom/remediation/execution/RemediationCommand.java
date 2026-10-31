package com.democorp.warroom.remediation.execution;

/**
 * Command pattern: every fix is run the same way —
 * preconditions → dry run → execute → verify, and undo when verification fails.
 */
public interface RemediationCommand {

    void checkPreconditions();

    String dryRun();

    Outcome execute();

    Verification verify();

    String undo();

    record Outcome(boolean effective, String message) {
    }

    record Verification(boolean passed, String detail) {
    }
}
