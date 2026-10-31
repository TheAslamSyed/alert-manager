package com.democorp.bank.scenario;

import com.democorp.bank.DemocorpProperties;
import com.democorp.bank.ingest.IngestClient;
import com.democorp.bank.telemetry.BankLog;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

import static com.democorp.bank.scenario.FaultState.*;

/** Starts and stops practice incidents, and sends the "context" signals a real bank would produce. */
@Service
public class ScenarioService {

    private final FaultState state;
    private final IngestClient ingest;
    private final TaskScheduler scheduler;
    private final DemocorpProperties props;

    public ScenarioService(FaultState state, IngestClient ingest, TaskScheduler scheduler, DemocorpProperties props) {
        this.state = state;
        this.ingest = ingest;
        this.scheduler = scheduler;
        this.props = props;
    }

    public synchronized Instant start(Scenario scenario) {
        state.start(scenario);
        BankLog.info("democorp", "Scenario started", "scenario", scenario.code());
        switch (scenario) {
            case FIN_01 -> {
                ingest.deploy(PAYMENT, "2.2.0", "2.3.0", "Refactor transfer DB access (PAY-971)", "ci-pipeline");
                ticketLater(PAYMENT, "Card transfer keeps failing", "I tried to send money three times and it fails.");
            }
            case FIN_02 -> ticketLater(PAYMENT, "Card payment stuck on processing",
                    "My card payment spins for a long time and then fails.");
            case FIN_03 -> ticketLater(PAYMENT, "Card payments fail instantly",
                    "Every card payment fails straight away.");
            case FIN_04 -> ticketLater(PAYMENT, "Payment rejected, try again later",
                    "Payday salary transfer was rejected with 'try again later'.");
            case FIN_05 -> {
                ingest.configChange(PAYMENT, CFG_IDEMPOTENCY, "true", "false", "rahul (PAY-990)");
                ingest.configChange(PAYMENT, CFG_MAX_ATTEMPTS, "1", "3", "rahul (PAY-990)");
                ticketLater(PAYMENT, "I was charged twice", "The same payment appears twice on my statement.");
            }
            case FIN_06 -> {
                ingest.configChange(PAYMENT, CFG_FRAUD_THRESHOLD, "0.85", "0.35", "fraud-ops (FRD-311)");
                ticketLater(PAYMENT, "Card declined but I have money", "My card keeps getting declined at shops.");
            }
            case FIN_07 -> ticketLater(ACCOUNT, "Transfer done but balance not updated",
                    "Money left my account but the balance did not change.");
            case FIN_08 -> ticketLater(ACCOUNT, "App very slow to show balance",
                    "The balance screen takes a long time to load.");
            case FIN_11 -> ticketLater(BANKING, "My account got locked",
                    "I got a message my account is locked after failed logins, but it wasn't me.");
            case FIN_13 -> {
                BankLog.info(ACCOUNT, "Nightly interest batch started", "job", "interest-accrual");
                scheduler.schedule(() -> {
                    if (state.is(Scenario.FIN_13)) {
                        BankLog.info(ACCOUNT, "Nightly interest batch finished", "job", "interest-accrual");
                        state.resolve();
                    }
                }, Instant.now().plus(Duration.ofSeconds(45)));
            }
            default -> { /* FIN-12: no context signal, only telemetry */ }
        }
        if (props.directAlerts()) {
            scheduler.schedule(() -> {
                if (state.is(scenario)) {
                    ingest.alert(scenario.primaryService(), "Direct" + scenario.name(), "critical",
                            scenario.title() + " on " + scenario.primaryService());
                }
            }, Instant.now().plus(Duration.ofSeconds(8)));
        }
        return state.startedAt();
    }

    public synchronized void stop() {
        state.active().ifPresent(s -> BankLog.info("democorp", "Scenario stopped", "scenario", s.code()));
        state.resolve();
    }

    private void ticketLater(String service, String subject, String description) {
        Scenario expected = state.active().orElse(null);
        scheduler.schedule(() -> {
            if (expected != null && state.is(expected)) {
                ingest.ticket(service, subject, description);
            }
        }, Instant.now().plus(Duration.ofSeconds(props.ticketDelaySeconds())));
    }
}
