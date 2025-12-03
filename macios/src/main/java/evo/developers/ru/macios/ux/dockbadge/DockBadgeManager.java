package evo.developers.ru.macios.ux.dockbadge;

import com.sun.jna.Pointer;
import evo.developers.ru.macios.base.Manager;
import javafx.stage.Stage;

import static evo.developers.ru.macios.core.ObjC.*;

public class DockBadgeManager extends Manager {

    public DockBadgeManager(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
    }

    public static void setDockBadge(int count) {
        setDockBadge(count > 0 ? String.valueOf(count) : null);
    }

    public static void clearDockBadge() {
        setDockBadge((String) null);
    }

    /**
     * Set badge on Dock icon (unread count).
     * Pass null or empty string to clear.
     */
    public static void setDockBadge(String badge) {
        try {
            Pointer app = msg(cls("NSApplication"), sel("sharedApplication"));
            Pointer dockTile = msg(app, sel("dockTile"));

            Pointer badgeStr = (badge == null || badge.isEmpty())
                    ? Pointer.NULL
                    : msg(cls("NSString"), sel("stringWithUTF8String:"), badge);

            msg(dockTile, sel("setBadgeLabel:"), badgeStr);

        } catch (Exception e) {
            System.out.println("WindowBlur setDockBadge error: " + e.getMessage());
        }
    }

}
