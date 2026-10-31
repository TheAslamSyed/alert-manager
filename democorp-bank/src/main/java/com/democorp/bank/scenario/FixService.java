package com.democorp.bank.scenario;

import com.democorp.bank.ingest.IngestClient;
import com.democorp.bank.telemetry.BankLog;
import org.springframework.stereotype.Service;

import java.util.Map;

import static com.democorp.bank.scenario.FaultState.*;

/**
 * Applies a fix the way an operator would. A fix only clears the fault it really addresses;
 * the wrong fix has no effect, so the War Room's "verify" step can tell whether it worked.
 */
@Service
public class FixService {

    private final FaultState state;
    private final IngestClient ingest;

    public FixService(FaultState state, IngestClient ingest) {
        this.state = state;
        this.ingest = ingest;
    }

    public synchronized FixResult apply(FixAction action, String requestedBy) {
        Scenario active = state.active().orElse(null);
        boolean resolves = active != null && active.fixes().contains(action);
        BankLog.info("democorp", "Admin action executed", "action", action, "requested_by", requestedBy,
                "resolves_active_fault", resolves);

        if (!resolves) {
            return new FixResult(action, false, "No effect: " + describe(action) + " does not address the current state.",
                    state.describe());
        }
        switch (action) {
            case ROLLBACK_DEPLOYMENT -> ingest.deploy(PAYMENT, state.version(PAYMENT), "2.2.0",
                    "Rollback approved in War Room", requestedBy);
            case REVERT_CONFIG -> {
                ingest.configChange(PAYMENT, CFG_IDEMPOTENCY, "false", "true", requestedBy);
                ingest.configChange(PAYMENT, CFG_MAX_ATTEMPTS, "3", "1", requestedBy);
            }
            case REVERT_FRAUD_RULE -> ingest.configChange(PAYMENT, CFG_FRAUD_THRESHOLD, "0.35", "0.85", requestedBy);
            case FAILOVER_PAYMENT_ROUTE -> state.setCardRoute("backup");
            default -> { /* behavioural fault only */ }
        }
        state.resolve();
        if (action == FixAction.FAILOVER_PAYMENT_ROUTE) {
            state.setCardRoute("backup");
        }
        return new FixResult(action, true, describe(action) + " applied.", state.describe());
    }

    /** Command-pattern dry run: says what would happen without changing anything. */
    public FixResult dryRun(FixAction action) {
        Scenario active = state.active().orElse(null);
        boolean resolves = active != null && active.fixes().contains(action);
        String msg = describe(action) + (resolves ? " would address the current fault." : " would not change the current fault.");
        return new FixResult(action, resolves, msg, state.describe());
    }

    private String describe(FixAction action) {
        return switch (action) {
            case ROLLBACK_DEPLOYMENT -> "Roll back payment-service to 2.2.0";
            case FAILOVER_PAYMENT_ROUTE -> "Switch card traffic to the backup processor";
            case ROTATE_CERTIFICATE -> "Install the renewed card-network certificate";
            case ENABLE_THROTTLING -> "Queue and throttle card authorizations";
            case REVERT_CONFIG -> "Restore payment retry config (idempotency on, 1 attempt)";
            case REFUND_DUPLICATES -> "Refund duplicate debits";
            case REVERT_FRAUD_RULE -> "Restore fraud threshold to 0.85";
            case MOVE_MESSAGE_TO_DLQ -> "Move poison ledger message to the dead-letter queue";
            case RESTART_CACHE -> "Restart the account cache";
            case ENABLE_STEP_UP_AUTH -> "Require step-up verification on login";
            case BLOCK_IP_RANGE -> "Block attacking IP ranges";
            case REVOKE_API_TOKEN -> "Revoke partner API token tok_8f2c";
        };
    }

    public record FixResult(FixAction action, boolean effective, String message, Map<String, Object> state) {
    }
}
