package com.democorp.warroom.remediation.api;

import com.democorp.warroom.remediation.catalog.ActionCatalog;
import com.democorp.warroom.remediation.catalog.ActionCatalog.ActionSpec;
import com.democorp.warroom.remediation.domain.RemediationAction;
import com.democorp.warroom.remediation.domain.RemediationAction.ApprovalRuleException;
import com.democorp.warroom.remediation.domain.RemediationPlan;
import com.democorp.warroom.remediation.service.RemediationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/remediations")
@Tag(name = "Remediation", description = "Proposed fixes and the human approval workflow (FR-06, FR-08)")
public class RemediationController {

    private final RemediationService remediation;
    private final ActionCatalog catalog;

    public RemediationController(RemediationService remediation, ActionCatalog catalog) {
        this.remediation = remediation;
        this.catalog = catalog;
    }

    @GetMapping
    public List<RemediationAction> list(@RequestParam(required = false) String incidentId,
                                        @RequestParam(required = false) RemediationAction.Status status) {
        return remediation.list(incidentId, status);
    }

    @GetMapping("/{id}")
    public RemediationAction get(@PathVariable String id) {
        return remediation.find(id);
    }

    @GetMapping("/plans/{incidentId}")
    @Operation(summary = "Root cause, preventive actions and rationale for an incident")
    public RemediationPlan plan(@PathVariable String incidentId) {
        return remediation.plan(incidentId);
    }

    @GetMapping("/catalog")
    @Operation(summary = "The allowlist: every action that can ever run, its risk and approvals needed")
    public List<ActionSpec> catalog() {
        return catalog.all();
    }

    @PostMapping("/{id}/dry-run")
    @PreAuthorize("hasAnyRole('RESPONDER', 'APPROVER')")
    public Map<String, String> dryRun(@PathVariable String id) {
        return Map.of("preview", remediation.dryRun(id));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('RESPONDER', 'APPROVER')")
    @Operation(summary = "Send the agent's proposal for approval")
    public RemediationAction submit(@PathVariable String id, Authentication user) {
        return remediation.submit(id, user.getName());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('APPROVER')")
    @Operation(summary = "Approve (not your own; admins cannot; HIGH risk needs two approvers)")
    public RemediationAction approve(@PathVariable String id, @RequestBody(required = false) Decision body,
                                     Authentication user) {
        return remediation.approve(id, user.getName(), roles(user), body == null ? null : body.reason());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('RESPONDER', 'APPROVER')")
    public RemediationAction reject(@PathVariable String id, @RequestBody(required = false) Decision body,
                                    Authentication user) {
        return remediation.reject(id, user.getName(), body == null ? null : body.reason());
    }

    private static Set<String> roles(Authentication user) {
        return user.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .map(a -> a.replaceFirst("^ROLE_", "")).collect(Collectors.toSet());
    }

    @ExceptionHandler(ApprovalRuleException.class)
    ProblemDetail rule(ApprovalRuleException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    public record Decision(String reason) {
    }
}
