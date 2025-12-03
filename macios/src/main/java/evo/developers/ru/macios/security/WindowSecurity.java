package evo.developers.ru.macios.security;


import com.sun.jna.Pointer;
import evo.developers.ru.macios.base.Manager;
import evo.developers.ru.macios.core.ObjC;
import evo.developers.ru.macios.core.WindowManager;
import javafx.stage.Stage;

import static evo.developers.ru.macios.core.ObjC.msg;
import static evo.developers.ru.macios.core.ObjC.sel;

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
    
    public void setSecure(boolean secure) {
        setWindowSharingType(secure ? WindowSharingType.NONE : WindowSharingType.READ_ONLY);
    }
    
    public boolean isSecure() {
        Pointer window = WindowManager.getCurrentWindow("");
        if (window == null) return false;
        long sharingType = ObjC.msgLong(window, sel("sharingType"));
        return sharingType == WindowSharingType.NONE.value;
    }

    public static void setWindowSharingType(WindowSharingType type) {
        try {
            Pointer window = WindowManager.getCurrentWindow("");
            if (window == null) return;

            msg(window, sel("setSharingType:"), type.value);
            System.out.println("WindowBlur: Window sharing set to " + type.name());

        } catch (Exception e) {
            System.out.println("WindowBlur setWindowSharingType error: " + e.getMessage());
        }
    }
}