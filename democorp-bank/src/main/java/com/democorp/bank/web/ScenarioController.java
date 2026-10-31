package com.democorp.bank.web;

import com.democorp.bank.scenario.FaultState;
import com.democorp.bank.scenario.FixAction;
import com.democorp.bank.scenario.Scenario;
import com.democorp.bank.scenario.ScenarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/scenarios")
@Tag(name = "Scenarios", description = "Start and stop DemoCorp practice incidents")
public class ScenarioController {

    private final ScenarioService scenarios;
    private final FaultState state;

    public ScenarioController(ScenarioService scenarios, FaultState state) {
        this.scenarios = scenarios;
        this.state = state;
    }

    @GetMapping
    @Operation(summary = "List practice incidents with their known root cause (ground truth)")
    public List<ScenarioView> list() {
        return Arrays.stream(Scenario.values()).map(ScenarioView::of).toList();
    }

    @GetMapping("/active")
    public ResponseEntity<Map<String, Object>> active() {
        return state.active()
                .map(s -> ResponseEntity.ok(Map.<String, Object>of("code", s.code(), "startedAt", state.startedAt())))
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/{code}/start")
    @Operation(summary = "Start a practice incident, e.g. FIN-01")
    public ResponseEntity<Map<String, Object>> start(@PathVariable String code) {
        Scenario scenario = Scenario.byCode(code);
        Instant startedAt = scenarios.start(scenario);
        return ResponseEntity.accepted().body(Map.of("code", scenario.code(), "startedAt", startedAt));
    }

    @PostMapping("/stop")
    @Operation(summary = "Stop the running practice incident (bank goes back to healthy)")
    public ResponseEntity<Void> stop() {
        scenarios.stop();
        return ResponseEntity.noContent().build();
    }

    public record ScenarioView(String code, String title, String primaryService, String rootCause,
                               List<String> rootCauseKeywords, Set<FixAction> fixes, boolean falseAlarm) {
        static ScenarioView of(Scenario s) {
            return new ScenarioView(s.code(), s.title(), s.primaryService(), s.rootCause(),
                    s.rootCauseKeywords(), s.fixes(), s.falseAlarm());
        }
    }
}
