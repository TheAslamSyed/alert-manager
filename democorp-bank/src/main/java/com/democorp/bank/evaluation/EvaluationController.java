package com.democorp.bank.evaluation;

import com.democorp.bank.evaluation.EvaluationRunner.Result;
import com.democorp.bank.evaluation.EvaluationRunner.Run;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/evaluation")
@Tag(name = "Evaluation", description = "Practice-incident evaluation and MTTR report")
public class EvaluationController {

    private final EvaluationRunner runner;

    public EvaluationController(EvaluationRunner runner) {
        this.runner = runner;
    }

    @PostMapping("/run")
    @Operation(summary = "Run practice incidents (all, or the codes given) and build the report. Takes minutes.")
    public ResponseEntity<Map<String, Object>> run(@RequestBody(required = false) RunRequest request) {
        Run run = runner.start(EvaluationRunner.parse(request == null ? null : request.scenarios()));
        return ResponseEntity.accepted().body(Map.of("startedAt", run.startedAt, "scenarios",
                run.scenarios.stream().map(s -> s.code()).toList()));
    }

    @GetMapping("/report")
    @Operation(summary = "Latest evaluation: summary and per-scenario results")
    public ResponseEntity<Map<String, Object>> report() {
        Run run = runner.current();
        if (run == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(summary(run));
    }

    @GetMapping(value = "/report.md", produces = MediaType.TEXT_MARKDOWN_VALUE)
    public String markdown() {
        Run run = runner.current();
        if (run == null) {
            return "No evaluation has been run yet.";
        }
        Map<String, Object> s = summary(run);
        StringBuilder md = new StringBuilder("# War Room evaluation report\n\n")
                .append("Started ").append(run.startedAt).append(" · status ").append(run.status).append("\n\n")
                .append("| Metric | Value |\n|---|---|\n");
        ((Map<?, ?>) s.get("summary")).forEach((k, v) -> md.append("| ").append(k).append(" | ").append(v).append(" |\n"));
        md.append("\n| Scenario | Expected | Top cause | Top-1 | Top-3 | Confidence | Diagnosis (s) | Fix proposed | "
                + "Correct fix | Manual MTTR (min) | Agent MTTR (min) |\n|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (Result r : run.results) {
            md.append("| ").append(r.code()).append(' ').append(r.title()).append(" | ").append(r.expected())
                    .append(" | ").append(r.topCause()).append(" | ").append(r.top1() ? "yes" : "no")
                    .append(" | ").append(r.top3() ? "yes" : "no").append(" | ").append(r.confidence())
                    .append(" | ").append(r.diagnoseSeconds()).append(" | ").append(r.proposedAction())
                    .append(" | ").append(r.correctFix() ? "yes" : "no").append(" | ").append(r.manualMttrMinutes())
                    .append(" | ").append(r.agentMttrMinutes()).append(" |\n");
        }
        md.append("\nAgent MTTR = time to diagnosis + ").append("assumed human approval and fix time (see config). ")
                .append("Manual MTTR = time-to-resolve of the most similar past incident in the knowledge base.\n");
        return md.toString();
    }

    private Map<String, Object> summary(Run run) {
        List<Result> rs = run.results;
        long n = rs.size();
        long top1 = rs.stream().filter(Result::top1).count();
        long top3 = rs.stream().filter(Result::top3).count();
        long fixes = rs.stream().filter(Result::correctFix).count();
        double manual = rs.stream().mapToDouble(Result::manualMttrMinutes).average().orElse(0);
        double agent = rs.stream().filter(r -> r.agentMttrMinutes() != null).mapToDouble(Result::agentMttrMinutes)
                .average().orElse(0);
        double diagnose = rs.stream().filter(r -> r.diagnoseSeconds() != null).mapToDouble(Result::diagnoseSeconds)
                .average().orElse(0);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("scenarios", n + " of " + run.scenarios.size());
        summary.put("top1Accuracy", n == 0 ? 0 : Math.round(100.0 * top1 / n) + "%");
        summary.put("top3Accuracy", n == 0 ? 0 : Math.round(100.0 * top3 / n) + "%");
        summary.put("correctFixProposed", n == 0 ? 0 : Math.round(100.0 * fixes / n) + "%");
        summary.put("avgTimeToDiagnosisSeconds", Math.round(diagnose));
        summary.put("avgManualMttrMinutes", Math.round(manual * 10) / 10.0);
        summary.put("avgAgentMttrMinutes", Math.round(agent * 10) / 10.0);
        summary.put("mttrReduction", manual == 0 ? "n/a" : Math.round(100 * (1 - agent / manual)) + "%");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", run.status);
        out.put("active", run.active);
        out.put("startedAt", run.startedAt);
        out.put("finishedAt", run.finishedAt);
        out.put("summary", summary);
        out.put("results", rs);
        return out;
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail busy(IllegalStateException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail bad(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    public record RunRequest(List<String> scenarios) {
    }
}
