package com.democorp.bank.web;

import com.democorp.bank.DemocorpProperties;
import com.democorp.bank.scenario.FaultState;
import com.democorp.bank.scenario.FixAction;
import com.democorp.bank.scenario.FixService;
import com.democorp.bank.scenario.FixService.FixResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/** Operator "fix" endpoints. Only the War Room's remediation executor holds the admin key. */
@RestController
@RequestMapping("/admin")
@Tag(name = "Admin", description = "Fix endpoints used by the remediation executor")
public class AdminController {

    private final FixService fixes;
    private final FaultState state;
    private final byte[] adminKey;

    public AdminController(FixService fixes, FaultState state, DemocorpProperties props) {
        this.fixes = fixes;
        this.state = state;
        this.adminKey = props.adminKey().getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/state")
    public Map<String, Object> state(@RequestHeader(value = "X-Admin-Key", required = false) String key) {
        authorize(key);
        return state.describe();
    }

    @PostMapping("/actions/{action}/dry-run")
    @Operation(summary = "Describe what a fix would change, without changing anything")
    public FixResult dryRun(@PathVariable FixAction action,
                            @RequestHeader(value = "X-Admin-Key", required = false) String key) {
        authorize(key);
        return fixes.dryRun(action);
    }

    @PostMapping("/actions/{action}")
    @Operation(summary = "Apply a fix")
    public FixResult apply(@PathVariable FixAction action,
                           @RequestBody(required = false) ActionRequest request,
                           @RequestHeader(value = "X-Admin-Key", required = false) String key) {
        authorize(key);
        String by = request == null || request.requestedBy() == null ? "unknown" : request.requestedBy();
        return fixes.apply(action, by);
    }

    private void authorize(String key) {
        if (key == null || !MessageDigest.isEqual(adminKey, key.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or wrong X-Admin-Key");
        }
    }

    public record ActionRequest(String requestedBy, String reason) {
    }
}
