package com.democorp.bank;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param incidentUrl        incident-service (internal API, used by the evaluation runner)
 * @param knowledgeUrl       knowledge-service (historical MTTR baseline for the report)
 * @param evalSettleSeconds  wait between scenarios so the previous alerts clear
 * @param evalTimeoutSeconds how long to wait for an RCA per scenario
 * @param evalApprovalMinutes assumed human approval time added to the agents' MTTR
 * @param evalFixMinutes     assumed fix-and-verify time added to the agents' MTTR
 */
@ConfigurationProperties(prefix = "democorp")
public record DemocorpProperties(
        String ingestUrl,
        String ingestApiKey,
        String adminKey,
        boolean trafficEnabled,
        boolean directAlerts,
        int ticketDelaySeconds,
        String incidentUrl,
        String knowledgeUrl,
        int evalSettleSeconds,
        int evalTimeoutSeconds,
        double evalApprovalMinutes,
        double evalFixMinutes) {
}
