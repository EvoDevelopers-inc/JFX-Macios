package evo.developers.ru.macios.ui;

import com.sun.jna.Pointer;
import evo.developers.ru.macios.base.Manager;
import javafx.stage.Stage;

import static evo.developers.ru.macios.core.ObjC.*;

public class ThemeManager extends Manager {
    public ThemeManager(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
    }

    public interface AppearanceListener {
        void onAppearanceChanged(boolean isDark);
    }


    private static java.util.List<AppearanceListener> appearanceListeners = new java.util.ArrayList<>();
    private static java.util.Timer appearanceTimer = null;
    private static boolean lastKnownDarkMode = false;
    private static boolean appearanceListenerActive = false;

    /**
     * Start listening for system appearance (dark/light mode) changes.
     * Uses polling at specified interval.
     *
     * @param listener Callback when appearance changes
     * @param checkIntervalMs How often to check (milliseconds), recommended: 500-1000
     */
    public static void startAppearanceListener(AppearanceListener listener, long checkIntervalMs) {
        if (listener != null) {
            appearanceListeners.add(listener);
        }

        if (appearanceListenerActive) {
            return; // Already running
        }

        lastKnownDarkMode = isDarkMode();
        appearanceListenerActive = true;

        appearanceTimer = new java.util.Timer("AppearanceListener", true);
        appearanceTimer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                try {
                    boolean currentDark = isDarkMode();
                    if (currentDark != lastKnownDarkMode) {
                        lastKnownDarkMode = currentDark;
                        // Notify all listeners
                        for (AppearanceListener l : appearanceListeners) {
                            try {
                                l.onAppearanceChanged(currentDark);
                            } catch (Exception e) {
                                // Ignore listener errors
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignore polling errors
                }
            }
        }, checkIntervalMs, checkIntervalMs);

        System.out.println("WindowBlur: Appearance listener started (interval: " + checkIntervalMs + "ms)");
    }

    /**
     * Start appearance listener with default 500ms interval.
     */
    public static void startAppearanceListener(AppearanceListener listener) {
        startAppearanceListener(listener, 500);
    }

    /**
     * Add another listener (if listener is already started).
     */
    public static void addAppearanceListener(AppearanceListener listener) {
        if (listener != null && !appearanceListeners.contains(listener)) {
            appearanceListeners.add(listener);
        }
    }

    /**
     * Remove a listener.
     */
    public static void removeAppearanceListener(AppearanceListener listener) {
        appearanceListeners.remove(listener);
    }

    /**
     * Stop the appearance listener.
     */
    public static void stopAppearanceListener() {
        if (appearanceTimer != null) {
            appearanceTimer.cancel();
            appearanceTimer = null;
        }
        appearanceListenerActive = false;
        appearanceListeners.clear();
        System.out.println("WindowBlur: Appearance listener stopped");
    }

    /**
     * Check if appearance listener is active.
     */
    public static boolean isAppearanceListenerActive() {
        return appearanceListenerActive;
    }

    /**
     * Get current system appearance.
     */
    public enum Appearance {
        LIGHT, DARK
    }

    /**
     * Get current appearance as enum.
     */
    public static Appearance getAppearance() {
        return isDarkMode() ? Appearance.DARK : Appearance.LIGHT;
    }

    public static boolean isDarkMode() {
        try {
            Pointer defaults = msg(cls("NSUserDefaults"), sel("standardUserDefaults"));
            Pointer key = msg(cls("NSString"), sel("stringWithUTF8String:"), "AppleInterfaceStyle");
            Pointer value = msg(defaults, sel("stringForKey:"), key);

            if (value != null && Pointer.nativeValue(value) != 0) {
                Pointer utf8 = msg(value, sel("UTF8String"));
                if (utf8 != null && Pointer.nativeValue(utf8) != 0) {
                    return "Dark".equals(utf8.getString(0));
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }


    /**
     * Get accent color name from system preferences.
     * Returns: "Blue", "Purple", "Pink", "Red", "Orange", "Yellow", "Green", "Graphite", or null
     */
    public static String getAccentColor() {
        try {
            Pointer defaults = msg(cls("NSUserDefaults"), sel("standardUserDefaults"));
            Pointer key = msg(cls("NSString"), sel("stringWithUTF8String:"), "AppleAccentColor");
            long colorValue = msgLong(defaults, sel("integerForKey:"), key);

            // macOS accent color values
            switch ((int) colorValue) {
                case -1: return "Graphite";
                case 0: return "Red";
                case 1: return "Orange";
                case 2: return "Yellow";
                case 3: return "Green";
                case 4: return "Blue";
                case 5: return "Purple";
                case 6: return "Pink";
                default: return "Blue"; // Default
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get highlight color (selection color) from system.
     */
    public static String getHighlightColor() {
        try {
            Pointer defaults = msg(cls("NSUserDefaults"), sel("standardUserDefaults"));
            Pointer key = msg(cls("NSString"), sel("stringWithUTF8String:"), "AppleHighlightColor");
            Pointer value = msg(defaults, sel("stringForKey:"), key);

            if (value != null && Pointer.nativeValue(value) != 0) {
                Pointer utf8 = msg(value, sel("UTF8String"));
                if (utf8 != null) {
                    return utf8.getString(0);
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
