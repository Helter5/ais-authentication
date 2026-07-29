package sk.gkanocz.aisauth.settings;

public enum LogEventType {

    WARN_ISSUED("/warn", "New warning issued"),
    WARN_REMOVED("/removewarn", "Warning removed"),
    WARNS_CLEARED("/clearwarns", "All warnings cleared"),
    WARN_THRESHOLD_ACTION("Warn threshold", "Automatic action when a warn threshold is reached"),
    HACKED_ACCOUNT_TRAP_TRIGGERED("Hacked Account Trap", "Trap triggered (timeout/kick/ban)"),
    TICKET_TRANSCRIPT_SAVED("Ticket transcript", "Incident ticket transcript saved"),
    WIPE_INACTIVE_USER_REMOVED("Wipe", "Inactive user removed"),
    WIPE_RECAP("Wipe", "Recap report after a wipe run"),
    SEMESTER_RECAP("Semester Switch", "Recap report after a semester switch");

    private final String label;
    private final String description;

    LogEventType(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }
}
