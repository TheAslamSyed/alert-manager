package com.democorp.warroom.audit;

import com.democorp.warroom.common.events.Topics;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audit")
@Tag(name = "Audit", description = "Tamper-evident record of every event, finding and decision (FR-09)")
public class AuditController {

    private final AuditLog audit;

    public AuditController(AuditLog audit) {
        this.audit = audit;
    }

    /** One consumer for all topics, one at a time, so the chain stays in a single order. */
    @KafkaListener(id = "audit-all", concurrency = "1",
            topics = {Topics.SIGNALS, Topics.INCIDENT, Topics.INVESTIGATION, Topics.REMEDIATION})
    public void record(String json, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        audit.append(topic, json);
    }

    @GetMapping
    @Operation(summary = "Audit records (all, newest first; or one incident, oldest first)")
    public List<Map<String, Object>> records(@RequestParam(required = false) String incidentId,
                                             @RequestParam(defaultValue = "200") int limit) {
        return audit.records(incidentId, limit);
    }

    @GetMapping("/verify")
    @Operation(summary = "Recompute the hash chain and report whether any record was changed")
    public AuditLog.Verification verify() {
        return audit.verify();
    }
}
