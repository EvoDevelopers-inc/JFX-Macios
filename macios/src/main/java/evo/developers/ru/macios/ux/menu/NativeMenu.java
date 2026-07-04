package evo.developers.ru.macios.ux.menu;

import com.sun.jna.Pointer;
import evo.developers.ru.macios.core.ObjCRuntime;
import javafx.application.Platform;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static evo.developers.ru.macios.core.ObjC.*;
import static evo.developers.ru.macios.core.ObjCRuntime.*;

/**
 * Fluent builder around {@code NSMenu} / {@code NSMenuItem}. Clicks on items created
 * with a {@link Runnable} are routed to Java (on the JavaFX thread) through a single
 * Objective-C target class created at runtime.
 *
 * <p>Shared by {@code MenuBarManager} (app menu bar) and {@code DockMenuManager}.</p>
 */
public class NativeMenu {

    private static final String TARGET_CLASS = "JFXMenuTarget";
    private static Pointer targetClass;
    private static ObjCRuntime.VoidObjMethod actionCallback;
    private static final Map<Long, NativeMenu> INSTANCES = new ConcurrentHashMap<>();

    private final Pointer menu;
    private final Pointer target;
    private final Map<Long, Runnable> actions = new ConcurrentHashMap<>();
    private long nextTag = 1;

    public NativeMenu(String title) {
        ensureClass();
        this.menu = msg(msg(cls("NSMenu"), sel("alloc")), sel("initWithTitle:"),
                nsString(title == null ? "" : title));
        // Items with a submenu need autoenable off, otherwise custom items may look disabled.
        msg(this.menu, sel("setAutoenablesItems:"), 0L);
        this.target = newInstance(targetClass);
        INSTANCES.put(Pointer.nativeValue(this.target), this);
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
                NativeMenu owner = INSTANCES.get(Pointer.nativeValue(self));
                if (owner != null) {
                    Runnable r = owner.actions.get(tag);
                    if (r != null) Platform.runLater(r);
                }
            } catch (Throwable ignored) { }
        };
        addMethod(c, "onAction:", actionCallback, "v@:@");
        registerClass(c);
        targetClass = c;
    }

    /** Adds a clickable item that runs {@code action} on the JavaFX thread. */
    public NativeMenu item(String title, Runnable action) {
        return item(title, "", action);
    }

    public NativeMenu item(String title, String keyEquivalent, Runnable action) {
        long tag = nextTag++;
        actions.put(tag, action);
        Pointer item = msg(msg(cls("NSMenuItem"), sel("alloc")),
                sel("initWithTitle:action:keyEquivalent:"),
                nsString(title), sel("onAction:"), nsString(keyEquivalent == null ? "" : keyEquivalent));
        msg(item, sel("setTarget:"), target);
        msg(item, sel("setTag:"), tag);
        msg(menu, sel("addItem:"), item);
        return this;
    }

    /** Adds an item wired to a standard selector on {@code NSApp} (e.g. {@code terminate:}). */
    public NativeMenu appItem(String title, String selectorName, String keyEquivalent) {
        Pointer item = msg(msg(cls("NSMenuItem"), sel("alloc")),
                sel("initWithTitle:action:keyEquivalent:"),
                nsString(title), sel(selectorName), nsString(keyEquivalent == null ? "" : keyEquivalent));
        msg(item, sel("setTarget:"), msg(cls("NSApplication"), sel("sharedApplication")));
        msg(menu, sel("addItem:"), item);
        return this;
    }

    public NativeMenu separator() {
        msg(menu, sel("addItem:"), msg(cls("NSMenuItem"), sel("separatorItem")));
        return this;
    }

    /** Adds {@code submenu} as a nested submenu under a titled item. */
    public NativeMenu submenu(String title, NativeMenu submenu) {
        Pointer item = msg(msg(cls("NSMenuItem"), sel("alloc")),
                sel("initWithTitle:action:keyEquivalent:"), nsString(title), Pointer.NULL, nsString(""));
        msg(item, sel("setSubmenu:"), submenu.handle());
        msg(menu, sel("addItem:"), item);
        return this;
    }

    /** The underlying {@code NSMenu} pointer. */
    public Pointer handle() {
        return menu;
    }
}
