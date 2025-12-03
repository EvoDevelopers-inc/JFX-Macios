package evo.developers.ru.macios.ui;

import com.sun.jna.Pointer;
import evo.developers.ru.macios.base.Manager;
import evo.developers.ru.macios.core.WindowManager;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import static evo.developers.ru.macios.core.ObjC.*;
import static evo.developers.ru.macios.core.ObjC.msg;
import static evo.developers.ru.macios.core.ObjC.msgLong;
import static evo.developers.ru.macios.core.ObjC.sel;

public class TitleBarManager extends Manager {

    public TitleBarManager(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
    }

    /**
     * Enable dragging window by its background.
     */
    public static void enableWindowDrag() {
        try {
            Pointer window = WindowManager.getCurrentWindow("");
            if (window == null) return;

            msg(window, sel("setMovable:"), 1L);
            msg(window, sel("setMovableByWindowBackground:"), 1L);
        } catch (Exception e) {
            System.out.println("WindowBlur error: " + e.getMessage());
        }
    }

    /**
     * Start native window drag. Call this from JavaFX mousePressed event.
     * This gives you native macOS window dragging with:
     * - Double-click to zoom
     * - Window snapping (drag to top/sides)
     * - Smooth animation
     */
    public static void startWindowDrag() {
        try {
            Pointer window = WindowManager.getCurrentWindow("");
            if (window == null) return;

            // Get current event from NSApp
            Pointer nsApp = msg(cls("NSApplication"), sel("sharedApplication"));
            Pointer currentEvent = msg(nsApp, sel("currentEvent"));

            if (currentEvent != null && Pointer.nativeValue(currentEvent) != 0) {
                // Perform native window drag with current event
                msg(window, sel("performWindowDragWithEvent:"), currentEvent);
            }
        } catch (Exception e) {
            System.out.println("WindowBlur startWindowDrag error: " + e.getMessage());
        }
    }

    private static final long NSWindowStyleMaskTitled = 1 << 0;
    private static final long NSWindowStyleMaskClosable = 1 << 1;
    private static final long NSWindowStyleMaskMiniaturizable = 1 << 2;
    private static final long NSWindowStyleMaskResizable = 1 << 3;
    private static final long NSWindowStyleMaskFullSizeContentView = 1 << 15;

    /**
     * Setup window for custom titlebar with native behavior.
     * Keeps native traffic lights visible and functional.
     * Native drag and window snapping all work.
     */
    public static void setupTransparentTitlebar() {
        try {
            Pointer window = WindowManager.getCurrentWindow("");
            if (window == null) return;

            // Get current style mask and add FullSizeContentView
            long currentMask = msgLong(window, sel("styleMask"));
            long newMask = currentMask | NSWindowStyleMaskFullSizeContentView;
            msg(window, sel("setStyleMask:"), newMask);

            // Make titlebar transparent but keep it functional
            msg(window, sel("setTitlebarAppearsTransparent:"), 1L);

            // Hide title text
            msg(window, sel("setTitleVisibility:"), 1L);

            // Enable native window dragging
            msg(window, sel("setMovable:"), 1L);
            msg(window, sel("setMovableByWindowBackground:"), 1L);

            // Make contentView also allow dragging
            Pointer contentView = msg(window, sel("contentView"));
            if (contentView != null && Pointer.nativeValue(contentView) != 0) {
                // Register for mouse events
                msg(contentView, sel("setWantsLayer:"), 1L);
            }

            System.out.println("WindowBlur: Transparent titlebar with native behavior");
        } catch (Exception e) {
            System.out.println("WindowBlur error: " + e.getMessage());
        }
    }
}
