package com.democorp.warroom.agent.llm;

import com.democorp.warroom.agent.AgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The only way agents talk to the LLM.
 * <ul>
 *   <li>Time limit per call ({@code warroom.agents.llm-timeout}, 180 s by default: CPU-only Ollama is slow).</li>
 *   <li>Resilience4j circuit breaker: after repeated failures it opens for 60 s and callers get the
 *       rule-based fallback immediately instead of waiting.</li>
 *   <li>Returns empty instead of throwing, so every caller must have a fallback.</li>
 * </ul>
 * The model gets read-only text in and JSON out; it has no tools that can change anything.
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final ChatClient chat;
    private final AgentProperties props;
    private final ObjectMapper mapper;
    private final MeterRegistry meters;
    private final ExecutorService executor;
    private final String model;
    private final CircuitBreaker breaker = CircuitBreaker.of("ollama", CircuitBreakerConfig.custom()
            .slidingWindowSize(4)
            .minimumNumberOfCalls(2)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .build());

    public LlmClient(ChatClient.Builder builder, AgentProperties props, ObjectMapper mapper, MeterRegistry meters,
                     ExecutorService agentExecutor,
                     @Value("${spring.ai.ollama.chat.options.model:unknown}") String model) {
        this.chat = builder.build();
        this.props = props;
        this.mapper = mapper;
        this.meters = meters;
        this.executor = agentExecutor;
        this.model = model;
    }

    public Optional<JsonNode> askJson(String purpose, String system, String user) {
        if (!props.llmEnabled()) {
            return Optional.empty();
        }
        Timer.Sample sample = Timer.start(meters);
        String text = null;
        String outcome = "ok";
        try {
            text = breaker.executeCallable(() -> CompletableFuture
                    .supplyAsync(() -> chat.prompt().system(system).user(user).call().content(), executor)
                    .get(props.llmTimeout().toMillis(), TimeUnit.MILLISECONDS));
        } catch (CallNotPermittedException e) {
            outcome = "circuit_open";
            log.warn("LLM circuit is open; {} uses its fallback", purpose);
        } catch (Exception e) {
            outcome = "error";
            log.warn("LLM call for {} failed or timed out after {} s, using fallback: {}", purpose,
                    props.llmTimeout().toSeconds(), e.toString());
        }
        sample.stop(meters.timer("warroom.llm.call", "purpose", purpose, "outcome", outcome));
        if (text == null) {
            return Optional.empty();
        }
        try {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start < 0 || end <= start) {
                log.warn("LLM answer for {} had no JSON object", purpose);
                return Optional.empty();
            }
            return Optional.of(mapper.readTree(text.substring(start, end + 1)));
        } catch (Exception e) {
            log.warn("LLM answer for {} was not valid JSON: {}", purpose, e.getMessage());
            return Optional.empty();
        }
    }

    public String model() {
        return model;
    }
}
