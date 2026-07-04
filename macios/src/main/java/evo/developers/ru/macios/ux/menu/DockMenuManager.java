package evo.developers.ru.macios.ux.menu;

import com.sun.jna.Pointer;
import evo.developers.ru.macios.base.Manager;
import evo.developers.ru.macios.core.ObjCRuntime;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.util.function.Consumer;

import static evo.developers.ru.macios.core.ObjC.*;
import static evo.developers.ru.macios.core.ObjCRuntime.*;

/**
 * Provides the menu shown when the user right-clicks the app's Dock icon.
 *
 * <p>AppKit asks the application delegate for this menu via
 * {@code -(NSMenu *)applicationDockMenu:(NSApplication *)sender}. JavaFX owns the
 * delegate, so we inject that method into the delegate's class at runtime (once)
 * and have it return our menu.</p>
 *
 * <pre>{@code
 * helper.getDockMenu().setMenu(m -> m
 *     .item("New Window", () -> ...)
 *     .separator()
 *     .item("Say Hello", () -> ...));
 * }</pre>
 */
public class DockMenuManager extends Manager {

    /** Retained menu returned to AppKit; read by the injected delegate method. */
    private static volatile Pointer dockMenu;
    private static ObjCRuntime.ObjObjMethod callback;
    private static boolean injected;

    public DockMenuManager(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
    }

    /** Builds and installs the Dock menu (on the JavaFX thread). */
    public void setMenu(Consumer<NativeMenu> builder) {
        Platform.runLater(() -> {
            try {
                NativeMenu menu = new NativeMenu("DockMenu");
                builder.accept(menu);
                Pointer handle = menu.handle();
                msg(handle, sel("retain"));
                dockMenu = handle;
                inject();
            } catch (Throwable t) {
                System.out.println("DockMenu setMenu error: " + t.getMessage());
            }
        });
    }

    /** Removes the custom Dock menu (the injected method then returns nil). */
    public void clear() {
        dockMenu = null;
    }

    private static synchronized void inject() {
        if (injected) return;
        Pointer app = msg(cls("NSApplication"), sel("sharedApplication"));
        Pointer delegate = msg(app, sel("delegate"));
        if (!isValid(delegate)) {
            System.out.println("DockMenu: no application delegate yet, cannot install");
            return;
        }
        Pointer delegateClass = classOf(delegate);
        callback = (self, cmd, sender) -> {
            Pointer m = dockMenu;
            return m != null ? m : Pointer.NULL;
        };
        addMethod(delegateClass, "applicationDockMenu:", callback, "@@:@");
        injected = true;
    }
}
