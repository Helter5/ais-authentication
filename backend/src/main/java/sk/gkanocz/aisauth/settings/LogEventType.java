package sk.gkanocz.aisauth.settings;

public enum LogEventType {

    WARN_ISSUED("/warn add", "New warning issued"),
    WARN_REMOVED("/warn remove", "Warning removed"),
    WARNS_CLEARED("/warn clearall", "All warnings cleared"),
    WARN_THRESHOLD_ACTION("Warn threshold", "Automatic action when a warn threshold is reached"),
    HACKED_ACCOUNT_TRAP_TRIGGERED("Hacked Account Trap", "Trap triggered (ban)"),
    WIPE_INACTIVE_USER_REMOVED("Wipe", "Inactive user removed"),
    WIPE_RECAP("Wipe", "Recap report after a wipe run"),
    SEMESTER_RECAP("Semester Switch", "Recap report after a semester switch"),
    VERIFY_REQUESTED("/verify", "Verification email sent"),
    CODE_CONFIRMED("/code", "Verification completed"),
    MANUAL_VERIFY_PERFORMED("/manualverify", "User manually verified"),
    VERIFIED_ROLE_ADDED_WITHOUT_VERIFY("/verify", "Verified role added to a member without going through /verify or /manualverify"),
    VERIFIED_USER_REMOVED("/verify", "User removed from the Users Directory (left/kicked/banned, or Verified role removed)");

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
