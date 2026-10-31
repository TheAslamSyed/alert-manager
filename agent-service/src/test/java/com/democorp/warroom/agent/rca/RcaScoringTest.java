package com.democorp.warroom.agent.rca;

import com.democorp.warroom.agent.core.InvestigationContext;
import com.democorp.warroom.agent.llm.LlmClient;
import com.democorp.warroom.common.contracts.IncidentSnapshot;
import com.democorp.warroom.common.contracts.IncidentStatus;
import com.democorp.warroom.common.contracts.Investigation.Evidence;
import com.democorp.warroom.common.contracts.Investigation.RcaResult;
import com.democorp.warroom.common.contracts.Severity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** RCA without the LLM (rules fallback): the right cause must still win. */
class RcaScoringTest {

    private final LlmClient llm = mock(LlmClient.class);
    private final RcaAgent rca = new RcaAgent(new CauseCatalog(), llm, null);
    private final IncidentSnapshot incident = new IncidentSnapshot("INC-1", "High error rate on payment-service",
            "payment-service", List.of("payment-service", "banking-api"), Severity.SEV1, IncidentStatus.INVESTIGATING,
            Instant.now(), Instant.now(), List.of(), List.of(), null, null);
    private final InvestigationContext ctx = new InvestigationContext("INV-1", incident, Instant.now().minusSeconds(600));

    RcaScoringTest() {
        when(llm.askJson(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
    }

    @Test
    void badReleaseWithPoolExhaustionRanksFirst() {
        List<Evidence> evidence = List.of(
                ev("CHANGE", "recent_deploy:payment-service:2.3.0"),
                ev("PROMETHEUS", "error_rate_high:payment-service"),
                ev("PROMETHEUS", "db_pool_saturated:payment-service"),
                ev("LOKI", "log_pool_exhausted:payment-service"),
                ev("SECURITY", "no_attack_signals"));
        RcaResult result = rca.analyze(ctx, evidence, new ArrayList<>());

        assertThat(result.method()).isEqualTo("RULES");
        assertThat(result.hypotheses().get(0).category()).isEqualTo("BAD_RELEASE");
        assertThat(result.hypotheses().get(0).title()).contains("v2.3.0").contains("payment-service");
        assertThat(result.hypotheses().get(0).evidenceIds()).isNotEmpty();
        assertThat(result.hypotheses().get(0).confidence())
                .isGreaterThan(result.hypotheses().get(1).confidence());
    }

    @Test
    void batchJobWithoutRealErrorsIsAFalseAlarm() {
        List<Evidence> evidence = List.of(
                ev("PROMETHEUS", "latency_high:account-service"),
                ev("LOKI", "log_batch_job:account-service"),
                ev("SECURITY", "no_attack_signals"));
        assertThat(rca.analyze(ctx, evidence, new ArrayList<>()).hypotheses().get(0).category()).isEqualTo("FALSE_ALARM");
    }

    @Test
    void batchJobDuringRealOutageIsNotCalledAFalseAlarm() {
        List<Evidence> evidence = List.of(
                ev("PROMETHEUS", "cache_down:account-service"),
                ev("LOKI", "log_batch_job:account-service"),
                ev("LOKI", "log_cache_down:account-service"));
        RcaResult result = rca.analyze(ctx, evidence, new ArrayList<>());
        assertThat(result.hypotheses()).extracting("category").doesNotContain("FALSE_ALARM");
        assertThat(result.hypotheses().get(0).category()).isEqualTo("CACHE_FAILURE");
    }

    private Evidence ev(String source, String tag) {
        return ctx.evidence("test", source, tag, tag, "q", List.of(tag));
    }
}
