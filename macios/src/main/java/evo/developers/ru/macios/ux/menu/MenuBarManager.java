package evo.developers.ru.macios.ux.menu;

import com.sun.jna.Pointer;
import evo.developers.ru.macios.base.Manager;
import evo.developers.ru.macios.core.ObjCRuntime;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static evo.developers.ru.macios.core.ObjC.*;
import static evo.developers.ru.macios.core.ObjCRuntime.*;

/**
 * Installs a custom application menu bar via {@code [NSApp setMainMenu:]}.
 *
 * <p>The first (application) menu is added automatically with the standard
 * About / Hide / Quit items. Additional top-level menus are described with
 * {@link #addMenu(String, Consumer)} and materialised on the JavaFX thread by
 * {@link #apply()}.</p>
 *
 * <p>JavaFX (Glass) reinstalls its own default menu bar every time the app is
 * reactivated, which would wipe our custom menu. To keep it sticky we retain the
 * built {@code NSMenu} and re-apply it on {@code NSApplicationDidBecomeActiveNotification}.</p>
 *
 * <pre>{@code
 * helper.getMenuBar()
 *     .appName("Demo")
 *     .addMenu("File", m -> m
 *         .item("New", () -> ...)
 *         .item("Open\u2026", "o", () -> ...)
 *         .separator()
 *         .appItem("Close", "performClose:", "w"))
 *     .addMenu("Help", m -> m.item("About Demo", () -> ...))
 *     .apply();
 * }</pre>
 */
public class MenuBarManager extends Manager {

    private static final String OBSERVER_CLASS = "JFXMenuBarObserver";

    /** The retained menu that should be re-applied whenever the app becomes active. */
    private static volatile Pointer activeMainMenu;
    private static Pointer observer;
    private static ObjCRuntime.VoidObjMethod activateCallback;
    private static boolean observerInstalled;

    private final List<Map.Entry<String, Consumer<NativeMenu>>> menus = new ArrayList<>();
    private String appName = "App";
    private boolean includeAppMenu = true;

    public MenuBarManager(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
    }

    /** Name shown in the application (first) menu and its About/Hide/Quit items. */
    public MenuBarManager appName(String name) {
        if (name != null) this.appName = name;
        return this;
    }

    /** Whether to prepend the standard application menu (default {@code true}). */
    public MenuBarManager withAppMenu(boolean include) {
        this.includeAppMenu = include;
        return this;
    }

    /** Registers a top-level menu; {@code builder} fills it when {@link #apply()} runs. */
    public MenuBarManager addMenu(String title, Consumer<NativeMenu> builder) {
        menus.add(new AbstractMap.SimpleEntry<>(title, builder));
        return this;
    }

    /** Builds all menus and installs them as the app's main menu (on the JavaFX thread). */
    public void apply() {
        Platform.runLater(() -> {
            try {
                Pointer menu = build();

                Pointer previous = activeMainMenu;
                activeMainMenu = menu;
                setMainMenu(menu);
                installActivationObserver();

                if (isValid(previous) && !previous.equals(menu)) {
                    msg(previous, sel("release"));
                }
            } catch (Throwable t) {
                System.out.println("MenuBar apply error: " + t.getMessage());
            }
        });
    }

    private Pointer build() {
        NativeMenu main = new NativeMenu("MainMenu");

        if (includeAppMenu) {
            NativeMenu app = new NativeMenu(appName);
            app.appItem("About " + appName, "orderFrontStandardAboutPanel:", "");
            app.separator();
            app.appItem("Hide " + appName, "hide:", "h");
            app.appItem("Hide Others", "hideOtherApplications:", "");
            app.appItem("Show All", "unhideAllApplications:", "");
            app.separator();
            app.appItem("Quit " + appName, "terminate:", "q");
            main.submenu(appName, app);
        }

        for (Map.Entry<String, Consumer<NativeMenu>> spec : menus) {
            NativeMenu m = new NativeMenu(spec.getKey());
            spec.getValue().accept(m);
            main.submenu(spec.getKey(), m);
        }

        Pointer handle = main.handle();
        msg(handle, sel("retain"));
        return handle;
    }

    private static void setMainMenu(Pointer menu) {
        Pointer nsApp = msg(cls("NSApplication"), sel("sharedApplication"));
        msg(nsApp, sel("setMainMenu:"), menu);
    }

    /**
     * Registers a one-time observer that re-applies {@link #activeMainMenu} every time the
     * application becomes active, undoing JavaFX's default menu-bar reinstall.
     */
    private static synchronized void installActivationObserver() {
        if (observerInstalled) return;

        if (!isRegistered(OBSERVER_CLASS)) {
            Pointer klass = allocateClass(OBSERVER_CLASS, "NSObject");
            activateCallback = (self, cmd, note) -> {
                Pointer m = activeMainMenu;
                if (isValid(m)) {
                    // Re-apply after Glass has finished restoring its own menu on this pulse.
                    Platform.runLater(() -> setMainMenu(m));
                }
            };
            addMethod(klass, "onActivate:", activateCallback, "v@:@");
            registerClass(klass);
        }

        observer = newInstance(cls(OBSERVER_CLASS));
        msg(observer, sel("retain"));

        Pointer center = msg(cls("NSNotificationCenter"), sel("defaultCenter"));
        msg(center, sel("addObserver:selector:name:object:"),
                observer, sel("onActivate:"),
                nsString("NSApplicationDidBecomeActiveNotification"), Pointer.NULL);

        observerInstalled = true;
    }
}
