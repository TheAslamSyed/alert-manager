package com.democorp.warroom.agent.comms;

import com.democorp.warroom.common.contracts.Investigation.CommsDraft;

import java.util.List;

/**
 * Template Method pattern: every audience's message has the same skeleton
 * (what happened → impact → what we are doing → next update), each audience fills it in its own words.
 * Used when the LLM is unavailable, and for quick status updates.
 */
public final class DraftTemplates {

    private DraftTemplates() {
    }

    /** Facts every template may use. */
    public record Facts(String incidentId, String title, String services, String severity, String status,
                        String rootCause, String confidence, String action, String evidence) {
    }

    public abstract static class DraftTemplate {

        public final CommsDraft render(Facts f) {
            String body = String.join("\n", whatHappened(f), impact(f), whatWeAreDoing(f), nextUpdate(f));
            return new CommsDraft(audience(), channel(), subject(f), body);
        }

        protected abstract String audience();

        protected abstract String channel();

        protected abstract String subject(Facts f);

        protected abstract String whatHappened(Facts f);

        protected abstract String impact(Facts f);

        protected abstract String whatWeAreDoing(Facts f);

        protected String nextUpdate(Facts f) {
            return "RESOLVED".equals(f.status()) ? "This is the final update." : "Next update within 30 minutes.";
        }
    }

    static final class Executive extends DraftTemplate {
        protected String audience() { return "EXECUTIVE"; }
        protected String channel() { return "Email · leadership list"; }
        protected String subject(Facts f) { return f.severity() + " " + f.incidentId() + ": " + f.title() + " (" + label(f.status()) + ")"; }
        protected String whatHappened(Facts f) { return f.title() + "."; }
        protected String impact(Facts f) { return "Affected services: " + f.services() + ". No customer data is affected."; }
        protected String whatWeAreDoing(Facts f) {
            return f.rootCause() == null ? "The team is investigating."
                    : "Cause: " + f.rootCause() + ". " + (f.action() == null ? "" : "Fix: " + f.action() + ".");
        }
    }

    static final class Engineering extends DraftTemplate {
        protected String audience() { return "ENGINEERING"; }
        protected String channel() { return "Chat · #" + "incidents"; }
        protected String subject(Facts f) {
            return f.incidentId() + " · " + label(f.status()) + (f.rootCause() == null ? "" : " · RCA: " + f.rootCause()
                    + " (" + f.confidence() + ")");
        }
        protected String whatHappened(Facts f) { return f.title() + " on " + f.services() + "."; }
        protected String impact(Facts f) { return f.evidence() == null ? "" : "Evidence: " + f.evidence(); }
        protected String whatWeAreDoing(Facts f) {
            return f.action() == null ? "No automatic fix proposed yet." : "Proposed: " + f.action() + " (awaiting approval).";
        }
    }

    static final class Customer extends DraftTemplate {
        protected String audience() { return "CUSTOMER"; }
        protected String channel() { return "Status page"; }
        protected String subject(Facts f) {
            return "RESOLVED".equals(f.status()) || "MITIGATED".equals(f.status())
                    ? "Service restored" : "Some payments may be affected";
        }
        protected String whatHappened(Facts f) {
            return "RESOLVED".equals(f.status()) || "MITIGATED".equals(f.status())
                    ? "The issue affecting some services has been fixed."
                    : "Some customers may have trouble with payments or the app right now.";
        }
        protected String impact(Facts f) { return "Your money and data are safe."; }
        protected String whatWeAreDoing(Facts f) {
            return "RESOLVED".equals(f.status()) ? "Thank you for your patience."
                    : "We know the cause and are fixing it. If something failed, please try again in a few minutes.";
        }
    }

    public static List<CommsDraft> all(Facts f) {
        return List.of(new Executive().render(f), new Engineering().render(f), new Customer().render(f));
    }

    static String label(String status) {
        String s = status == null ? "" : status.replace('_', ' ').toLowerCase();
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
