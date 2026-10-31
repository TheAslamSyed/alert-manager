package com.democorp.warroom.incident.correlation;

import com.democorp.warroom.common.contracts.SignalPayload;
import com.democorp.warroom.incident.correlation.CorrelationRule.Match;
import com.democorp.warroom.incident.domain.Incident;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Scores a signal against every open incident and picks the best match above the threshold. */
@Component
public class CorrelationEngine {

    private final List<CorrelationRule> rules;
    private final CorrelationProperties props;

    public CorrelationEngine(List<CorrelationRule> rules, CorrelationProperties props) {
        this.rules = rules;
        this.props = props;
    }

    public Optional<Decision> bestMatch(SignalPayload signal, List<Incident> candidates) {
        Decision best = null;
        for (Incident incident : candidates) {
            double score = 0;
            List<String> reasons = new ArrayList<>();
            for (CorrelationRule rule : rules) {
                Optional<Match> match = rule.evaluate(signal, incident);
                if (match.isPresent()) {
                    score += match.get().score();
                    reasons.add(match.get().reason());
                }
            }
            if (score >= props.attachThreshold() && (best == null || score > best.score())) {
                best = new Decision(incident, score, String.join(", ", reasons));
            }
        }
        return Optional.ofNullable(best);
    }

    public record Decision(Incident incident, double score, String reason) {
    }
}
