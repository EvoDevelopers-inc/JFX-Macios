package evo.developers.ru.macios.core;

import com.sun.jna.Pointer;
import evo.developers.ru.macios.base.Manager;
import javafx.stage.Stage;

import static evo.developers.ru.macios.core.ObjC.*;

public class WindowManager extends Manager {
    public WindowManager(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
    }

    public static class StyleMask {
        public static final long TITLED = 1 << 0;
        public static final long CLOSABLE = 1 << 1;
        public static final long MINIATURIZABLE = 1 << 2;
        public static final long RESIZABLE = 1 << 3;
        public static final long FULL_SIZE_CONTENT_VIEW = 1 << 15;
    }

    public static Pointer getCurrentWindow(String windowName) {
        return getFirstWindow(); //TODO Get pointer window by name
    }


    private static Pointer getFirstWindow() {
        Pointer app = msg(cls("NSApplication"), sel("sharedApplication"));
        Pointer windows = msg(app, sel("windows"));
        long count = msgLong(windows, sel("count"));
        if (count == 0) return null;
        return msg(windows, sel("objectAtIndex:"), 0L);
    }

    public static void closeWindow() {
        Pointer window = getFirstWindow();
        if (window != null) msg(window, sel("close"));
    }

    public static void minimizeWindow() {
        Pointer window = getFirstWindow();
        if (window != null) msg(window, sel("miniaturize:"), Pointer.NULL);
    }

    public static void zoomWindow() {
        Pointer window = getFirstWindow();
        if (window != null) msg(window, sel("zoom:"), Pointer.NULL);
    }

}
