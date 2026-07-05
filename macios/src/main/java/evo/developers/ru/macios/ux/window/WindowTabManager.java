package evo.developers.ru.macios.ux.window;

import com.sun.jna.Pointer;
import evo.developers.ru.macios.base.Manager;
import evo.developers.ru.macios.core.WindowManager;
import javafx.stage.Stage;

import static evo.developers.ru.macios.core.ObjC.*;

/**
 * Native macOS window tabs ({@code NSWindow} tabbing).
 *
 * <p>Give windows the same {@link #setTabbingIdentifier(String) tabbing identifier} and a
 * {@link TabbingMode#PREFERRED preferred} mode so the system groups them into tabs, or
 * merge them explicitly with {@link #addTab(Stage)}.</p>
 *
 * <pre>{@code
 * helper.getWindowTab().setTabbingIdentifier("editor");
 * helper.getWindowTab().setTabbingMode(WindowTabManager.TabbingMode.PREFERRED);
 * helper.getWindowTab().addTab(otherStage);   // pull another window in as a tab
 * helper.getWindowTab().toggleTabBar();
 * }</pre>
 */
public class WindowTabManager extends Manager {

    /** {@code NSWindowTabbingMode}. */
    public enum TabbingMode {
        AUTOMATIC(0),
        PREFERRED(1),
        DISALLOWED(2);

        final long value;
        TabbingMode(long value) { this.value = value; }
    }

    /** {@code NSWindowAbove} ordering for {@code addTabbedWindow:ordered:}. */
    private static final long ORDER_ABOVE = 1;

    public WindowTabManager(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
    }

    /** Windows sharing an identifier can be tabbed together. */
    public void setTabbingIdentifier(String identifier) {
        runFx(() -> {
            Pointer window = window();
            if (window != null) msg(window, sel("setTabbingIdentifier:"), nsString(identifier));
        });
    }

    public void setTabbingMode(TabbingMode mode) {
        runFx(() -> {
            Pointer window = window();
            if (window != null) msg(window, sel("setTabbingMode:"), mode.value);
        });
    }

    /** Adds another stage's window as a tab of this manager's window. */
    public void addTab(Stage other) {
        if (other == null) return;
        runFx(() -> {
            Pointer window = window();
            Pointer otherWindow = WindowManager.getCurrentWindow(other.getTitle());
            if (window != null && isValid(otherWindow)) {
                msg(window, sel("addTabbedWindow:ordered:"), otherWindow, ORDER_ABOVE);
            }
        });
    }

    /** Merges all mergeable windows into this window's tab group. */
    public void mergeAllWindows() {
        runFx(() -> {
            Pointer window = window();
            if (window != null) msg(window, sel("mergeAllWindows:"), Pointer.NULL);
        });
    }

    /** Moves the current tab out into its own window. */
    public void moveTabToNewWindow() {
        runFx(() -> {
            Pointer window = window();
            if (window != null) msg(window, sel("moveTabToNewWindow:"), Pointer.NULL);
        });
    }

    public void selectNextTab() {
        runFx(() -> {
            Pointer window = window();
            if (window != null) msg(window, sel("selectNextTab:"), Pointer.NULL);
        });
    }

    public void selectPreviousTab() {
        runFx(() -> {
            Pointer window = window();
            if (window != null) msg(window, sel("selectPreviousTab:"), Pointer.NULL);
        });
    }

    /** Shows/hides the tab bar. */
    public void toggleTabBar() {
        runFx(() -> {
            Pointer window = window();
            if (window != null) msg(window, sel("toggleTabBar:"), Pointer.NULL);
        });
    }

    /** Shows/hides the tab overview (Exposé-like grid of tabs). */
    public void toggleTabOverview() {
        runFx(() -> {
            Pointer window = window();
            if (window != null && msgLong(window, sel("respondsToSelector:"), sel("toggleTabOverview:")) != 0) {
                msg(window, sel("toggleTabOverview:"), Pointer.NULL);
            }
        });
    }

    /** @return the number of windows in this window's tab group (1 if untabbed). */
    public int getTabCount() {
        Pointer window = window();
        if (window == null) return 0;
        Pointer tabbed = msg(window, sel("tabbedWindows"));
        if (!isValid(tabbed)) return 1;
        return (int) msgLong(tabbed, sel("count"));
    }
}
