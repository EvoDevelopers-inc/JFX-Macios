package evo.developers.ru.macios.security;

import com.sun.jna.Pointer;
import evo.developers.ru.macios.base.Manager;
import javafx.stage.Stage;

import static evo.developers.ru.macios.core.ObjC.*;

/**
 * Controls whether the window can be captured by screenshots and screen recording,
 * via {@code NSWindow.sharingType}.
 */
public class WindowSecurity extends Manager {

    public WindowSecurity(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
    }

    public enum WindowSharingType {
        NONE(0),
        READ_ONLY(1),
        READ_WRITE(2);

        final long value;
        WindowSharingType(long value) { this.value = value; }
    }

    /** When {@code true}, the window becomes invisible to screenshots and screen recording. */
    public void setSecure(boolean secure) {
        setSharingType(secure ? WindowSharingType.NONE : WindowSharingType.READ_ONLY);
    }

    public boolean isSecure() {
        Pointer window = window();
        if (window == null) return false;
        return msgLong(window, sel("sharingType")) == WindowSharingType.NONE.value;
    }

    public void setSharingType(WindowSharingType type) {
        Pointer window = window();
        if (window == null) return;
        msg(window, sel("setSharingType:"), type.value);
    }
}
