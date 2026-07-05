package evo.developers.ru.macios.ui;

import com.sun.jna.Pointer;
import evo.developers.ru.macios.base.Manager;
import javafx.stage.Stage;

import static evo.developers.ru.macios.core.ObjC.*;

/**
 * Controls the native window title bar: transparent / hidden title bars with native
 * traffic lights, and native window dragging.
 */
public class TitleBarManager extends Manager {

    private static final long STYLE_FULL_SIZE_CONTENT_VIEW = 1 << 15;

    /** Standard window buttons for {@link #standardWindowButton}. */
    public static final int BUTTON_CLOSE = 0;
    public static final int BUTTON_MINIATURIZE = 1;
    public static final int BUTTON_ZOOM = 2;

    public TitleBarManager(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
    }

    /** Enables dragging the window by its background. */
    public void enableWindowDrag() {
        Pointer window = window();
        if (window == null) return;
        msg(window, sel("setMovable:"), 1L);
        msg(window, sel("setMovableByWindowBackground:"), 1L);
    }

    /**
     * Starts a native window drag (double-click zoom, edge snapping, smooth animation).
     * Call this from a JavaFX {@code setOnMousePressed} handler on your custom header.
     */
    public void startWindowDrag() {
        Pointer window = window();
        if (window == null) return;
        Pointer nsApp = msg(cls("NSApplication"), sel("sharedApplication"));
        Pointer currentEvent = msg(nsApp, sel("currentEvent"));
        if (isValid(currentEvent)) {
            msg(window, sel("performWindowDragWithEvent:"), currentEvent);
        }
    }

    /**
     * Full-size content view with a transparent title bar. Native traffic lights stay
     * visible and functional; the title text is hidden.
     */
    public void setupTransparentTitlebar() {
        Pointer window = window();
        if (window == null) return;

        long mask = msgLong(window, sel("styleMask")) | STYLE_FULL_SIZE_CONTENT_VIEW;
        msg(window, sel("setStyleMask:"), mask);
        msg(window, sel("setTitlebarAppearsTransparent:"), 1L);
        msg(window, sel("setTitleVisibility:"), 1L);
        msg(window, sel("setMovable:"), 1L);
        msg(window, sel("setMovableByWindowBackground:"), 1L);

        Pointer contentView = msg(window, sel("contentView"));
        if (isValid(contentView)) msg(contentView, sel("setWantsLayer:"), 1L);
    }

    /**
     * Full-size content view with the traffic-light buttons hidden.
     *
     * @param titleTextVisibility {@code 0} shows the title, {@code 1} hides it
     */
    public void setupNoTitlebar(long titleTextVisibility) {
        Pointer window = window();
        if (window == null) return;

        long mask = msgLong(window, sel("styleMask")) | STYLE_FULL_SIZE_CONTENT_VIEW;
        msg(window, sel("setStyleMask:"), mask);
        msg(window, sel("setTitlebarAppearsTransparent:"), 1L);
        msg(window, sel("setTitleVisibility:"), titleTextVisibility);
        msg(window, sel("setMovable:"), 1L);
        msg(window, sel("setMovableByWindowBackground:"), 1L);

        hideWindowButton(window, BUTTON_CLOSE);
        hideWindowButton(window, BUTTON_MINIATURIZE);
        hideWindowButton(window, BUTTON_ZOOM);
    }

    private static void hideWindowButton(Pointer window, int type) {
        Pointer button = msg(window, sel("standardWindowButton:"), (long) type);
        if (isValid(button)) msg(button, sel("setHidden:"), 1L);
    }
}
