package com.democorp.warroom.agent.core;

import com.democorp.warroom.common.contracts.Investigation.Evidence;

import java.util.List;

/**
 * A phase-1 specialist. It only reads (PromQL, LogQL) and returns evidence; it can never change anything.
 * Runs in parallel with the other specialists, each under its own time limit.
 */
public interface EvidenceAgent {

    String name();

    Output investigate(InvestigationContext ctx);

    record Output(String summary, List<Evidence> evidence) {
    }
}
