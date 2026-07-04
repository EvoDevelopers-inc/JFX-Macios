package evo.developers.ru.macios.ux.tray;

import com.sun.jna.Pointer;
import evo.developers.ru.macios.base.Manager;
import evo.developers.ru.macios.core.ObjCRuntime;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static evo.developers.ru.macios.core.ObjC.*;
import static evo.developers.ru.macios.core.ObjCRuntime.*;

/**
 * A macOS menu-bar (tray) status item backed by {@code NSStatusBar}, with a native
 * {@code NSMenu}. Menu-item clicks are routed back to Java {@link Runnable}s through a
 * small Objective-C target class created at runtime.
 */
public class StatusBarManager extends Manager {

    private static final String TARGET_CLASS = "JFXStatusBarTarget";
    private static final double NS_VARIABLE_STATUS_ITEM_LENGTH = -1.0;

    private static Pointer targetClass;
    private static ObjCRuntime.VoidObjMethod actionCallback;
    /** Maps a target instance pointer -> owning manager, so the C callback can dispatch. */
    private static final Map<Long, StatusBarManager> INSTANCES = new ConcurrentHashMap<>();

    private Pointer statusItem;
    private Pointer menu;
    private Pointer targetInstance;
    private final Map<Long, Runnable> actions = new ConcurrentHashMap<>();
    private long nextTag = 1;

    public StatusBarManager(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
    }

    private static synchronized void ensureClass() {
        if (targetClass != null) return;
        if (isRegistered(TARGET_CLASS)) {
            targetClass = cls(TARGET_CLASS);
            return;
        }
        Pointer c = allocateClass(TARGET_CLASS, "NSObject");
        actionCallback = (self, cmd, sender) -> {
            try {
                long tag = msgLong(sender, sel("tag"));
                StatusBarManager mgr = INSTANCES.get(Pointer.nativeValue(self));
                if (mgr != null) {
                    Runnable r = mgr.actions.get(tag);
                    if (r != null) Platform.runLater(r);
                }
            } catch (Throwable ignored) { }
        };
        addMethod(c, "onAction:", actionCallback, "v@:@");
        registerClass(c);
        targetClass = c;
    }

    /** Creates and shows the status-bar item with a text title. */
    public void show(String title) {
        Platform.runLater(() -> {
            try {
                ensureClass();
                Pointer bar = msg(cls("NSStatusBar"), sel("systemStatusBar"));
                statusItem = msg(bar, sel("statusItemWithLength:"), NS_VARIABLE_STATUS_ITEM_LENGTH);
                msg(statusItem, sel("retain"));

                Pointer button = msg(statusItem, sel("button"));
                if (isValid(button) && title != null) {
                    msg(button, sel("setTitle:"), nsString(title));
                }

                targetInstance = newInstance(targetClass);
                INSTANCES.put(Pointer.nativeValue(targetInstance), this);

                menu = msg(msg(cls("NSMenu"), sel("alloc")), sel("init"));
                msg(statusItem, sel("setMenu:"), menu);
            } catch (Throwable t) {
                System.out.println("StatusBar show error: " + t.getMessage());
            }
        });
    }

    /** Optionally set an SF Symbol icon on the status button (macOS 11+). */
    public void setSymbolIcon(String sfSymbolName) {
        Platform.runLater(() -> {
            if (!isValid(statusItem)) return;
            Pointer button = msg(statusItem, sel("button"));
            if (!isValid(button)) return;
            Pointer image = msg(cls("NSImage"), sel("imageWithSystemSymbolName:accessibilityDescription:"),
                    nsString(sfSymbolName), Pointer.NULL);
            if (isValid(image)) msg(button, sel("setImage:"), image);
        });
    }

    public void setTitle(String title) {
        Platform.runLater(() -> {
            if (!isValid(statusItem)) return;
            Pointer button = msg(statusItem, sel("button"));
            if (isValid(button)) msg(button, sel("setTitle:"), nsString(title));
        });
    }

    /** Adds a clickable menu item that runs {@code action} (on the JavaFX thread). */
    public void addItem(String title, Runnable action) {
        Platform.runLater(() -> {
            if (!isValid(menu)) return;
            long tag = nextTag++;
            actions.put(tag, action);
            Pointer item = msg(msg(cls("NSMenuItem"), sel("alloc")),
                    sel("initWithTitle:action:keyEquivalent:"), nsString(title), sel("onAction:"), nsString(""));
            msg(item, sel("setTarget:"), targetInstance);
            msg(item, sel("setTag:"), tag);
            msg(menu, sel("addItem:"), item);
        });
    }

    public void addSeparator() {
        Platform.runLater(() -> {
            if (isValid(menu)) msg(menu, sel("addItem:"), msg(cls("NSMenuItem"), sel("separatorItem")));
        });
    }

    /** Adds a standard Quit item wired to {@code NSApp terminate:}. */
    public void addQuitItem(String title) {
        Platform.runLater(() -> {
            if (!isValid(menu)) return;
            Pointer item = msg(msg(cls("NSMenuItem"), sel("alloc")),
                    sel("initWithTitle:action:keyEquivalent:"), nsString(title), sel("terminate:"), nsString("q"));
            msg(item, sel("setTarget:"), msg(cls("NSApplication"), sel("sharedApplication")));
            msg(menu, sel("addItem:"), item);
        });
    }

    /** Removes the status item from the menu bar. */
    public void remove() {
        Platform.runLater(() -> {
            try {
                if (!isValid(statusItem)) return;
                Pointer bar = msg(cls("NSStatusBar"), sel("systemStatusBar"));
                msg(bar, sel("removeStatusItem:"), statusItem);
                if (isValid(targetInstance)) INSTANCES.remove(Pointer.nativeValue(targetInstance));
                statusItem = null;
                menu = null;
                actions.clear();
            } catch (Throwable t) {
                System.out.println("StatusBar remove error: " + t.getMessage());
            }
        });
    }
}
