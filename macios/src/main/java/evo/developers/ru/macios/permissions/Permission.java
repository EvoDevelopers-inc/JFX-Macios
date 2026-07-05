package evo.developers.ru.macios.permissions;

/**
 * A macOS privacy-protected resource that can be checked / requested via
 * {@link PermissionsManager}. {@link #settingsAnchor} points at the matching pane in
 * System Settings &rarr; Privacy &amp; Security.
 */
public enum Permission {

    /** Notification delivery (also a prerequisite for showing push alerts). */
    NOTIFICATIONS("com.apple.preference.notifications"),
    /** Camera capture (AVFoundation). */
    CAMERA("com.apple.preference.security?Privacy_Camera"),
    /** Microphone capture (AVFoundation). */
    MICROPHONE("com.apple.preference.security?Privacy_Microphone"),
    /** Screen recording / screenshots (CoreGraphics). */
    SCREEN_RECORDING("com.apple.preference.security?Privacy_ScreenCapture"),
    /** Control the computer / read other apps' UI (Accessibility, AX API). */
    ACCESSIBILITY("com.apple.preference.security?Privacy_Accessibility"),
    /** Global keyboard/mouse monitoring (Input Monitoring). */
    INPUT_MONITORING("com.apple.preference.security?Privacy_ListenEvent"),
    /** Contacts (Contacts framework). */
    CONTACTS("com.apple.preference.security?Privacy_Contacts"),
    /** Calendar events (EventKit). */
    CALENDAR("com.apple.preference.security?Privacy_Calendars"),
    /** Reminders (EventKit). */
    REMINDERS("com.apple.preference.security?Privacy_Reminders"),
    /** Photo library (Photos framework). */
    PHOTOS("com.apple.preference.security?Privacy_Photos");

    private final String settingsAnchor;

    Permission(String settingsAnchor) {
        this.settingsAnchor = settingsAnchor;
    }

    /** The {@code x-apple.systempreferences:} URL fragment for this permission's pane. */
    public String settingsUrl() {
        return "x-apple.systempreferences:" + settingsAnchor;
    }
}
