package com.democorp.warroom.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "warroom.agents")
public record AgentProperties(String prometheusUrl, String lokiUrl, String knowledgeUrl, Duration agentTimeout,
                              Duration llmTimeout, boolean llmEnabled, Duration lookback) {
}
