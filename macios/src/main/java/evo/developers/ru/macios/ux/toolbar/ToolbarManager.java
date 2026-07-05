package evo.developers.ru.macios.ux.toolbar;

import com.sun.jna.Pointer;
import evo.developers.ru.macios.base.Manager;
import evo.developers.ru.macios.core.ObjCRuntime;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static evo.developers.ru.macios.core.ObjC.*;
import static evo.developers.ru.macios.core.ObjCRuntime.*;

/**
 * A native {@code NSToolbar} in the window title bar.
 *
 * <p>Items are described with {@link #addButton}, materialised through a runtime
 * {@code NSToolbarDelegate}, and their clicks are routed to Java {@link Runnable}s on the
 * JavaFX thread.</p>
 *
 * <pre>{@code
 * helper.getToolbar()
 *     .addButton("new", "New", "plus", () -> ...)
 *     .addButton("share", "Share", "square.and.arrow.up", () -> ...)
 *     .apply();
 * }</pre>
 */
public class ToolbarManager extends Manager {

    private static final String DELEGATE_CLASS = "JFXToolbarDelegate";

    private static Pointer delegateClass;
    private static ObjCRuntime.ObjObjMethod defaultItemsCb;
    private static ObjCRuntime.ObjObjMethod allowedItemsCb;
    private static ObjCRuntime.ObjObjObjBoolMethod itemForIdCb;
    private static ObjCRuntime.VoidObjMethod actionCb;
    private static final Map<Long, ToolbarManager> INSTANCES = new ConcurrentHashMap<>();

    private static final class Item {
        final String id;
        final String label;
        final String symbol;
        final Runnable action;
        final long tag;
        Item(String id, String label, String symbol, Runnable action, long tag) {
            this.id = id; this.label = label; this.symbol = symbol; this.action = action; this.tag = tag;
        }
    }

    private final List<Item> items = new ArrayList<>();
    private final Map<String, Item> byId = new ConcurrentHashMap<>();
    private final Map<Long, Runnable> actions = new ConcurrentHashMap<>();
    private long nextTag = 1;

    private Pointer delegate;
    private Pointer toolbar;

    public ToolbarManager(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
    }

    /**
     * Registers a toolbar button.
     *
     * @param id        unique item identifier
     * @param label     text shown under the icon
     * @param sfSymbol  SF Symbol name for the icon (macOS 11+), or {@code null}
     * @param action    runnable executed on the JavaFX thread when clicked
     */
    public ToolbarManager addButton(String id, String label, String sfSymbol, Runnable action) {
        Item item = new Item(id, label, sfSymbol, action, nextTag++);
        items.add(item);
        byId.put(id, item);
        actions.put(item.tag, action);
        return this;
    }

    /** Builds and installs the toolbar on this manager's window (on the JavaFX thread). */
    public void apply() {
        runFx(() -> {
            try {
                Pointer window = window();
                if (window == null) return;

                ensureClass();
                delegate = newInstance(delegateClass);
                INSTANCES.put(Pointer.nativeValue(delegate), this);

                toolbar = msg(msg(cls("NSToolbar"), sel("alloc")),
                        sel("initWithIdentifier:"), nsString("JFXToolbar"));
                msg(toolbar, sel("setDelegate:"), delegate);
                msg(toolbar, sel("setAllowsUserCustomization:"), 0L);
                msg(toolbar, sel("retain"));

                // Unified toolbar style looks best with a transparent titlebar (macOS 11+).
                if (msgLong(window, sel("respondsToSelector:"), sel("setToolbarStyle:")) != 0) {
                    msg(window, sel("setToolbarStyle:"), 3L); // NSWindowToolbarStyleUnified
                }
                msg(window, sel("setToolbar:"), toolbar);
            } catch (Throwable t) {
                System.out.println("Toolbar apply error: " + t.getMessage());
            }
        });
    }

    /** Removes the toolbar from the window. */
    public void remove() {
        runFx(() -> {
            Pointer window = window();
            if (window != null) msg(window, sel("setToolbar:"), Pointer.NULL);
            if (isValid(delegate)) INSTANCES.remove(Pointer.nativeValue(delegate));
            toolbar = null;
        });
    }

    private static synchronized void ensureClass() {
        if (delegateClass != null) return;
        if (isRegistered(DELEGATE_CLASS)) {
            delegateClass = cls(DELEGATE_CLASS);
            return;
        }
        Pointer c = allocateClass(DELEGATE_CLASS, "NSObject");

        defaultItemsCb = (self, cmd, tb) -> identifiers(self);
        allowedItemsCb = (self, cmd, tb) -> identifiers(self);
        itemForIdCb = (self, cmd, tb, identifier, flag) -> buildItem(self, identifier);
        actionCb = (self, cmd, sender) -> {
            try {
                long tag = msgLong(sender, sel("tag"));
                ToolbarManager mgr = INSTANCES.get(Pointer.nativeValue(self));
                if (mgr != null) {
                    Runnable r = mgr.actions.get(tag);
                    if (r != null) Platform.runLater(r);
                }
            } catch (Throwable ignored) { }
        };

        addMethod(c, "toolbarDefaultItemIdentifiers:", defaultItemsCb, "@@:@");
        addMethod(c, "toolbarAllowedItemIdentifiers:", allowedItemsCb, "@@:@");
        addMethod(c, "toolbar:itemForItemIdentifier:willBeInsertedIntoToolbar:", itemForIdCb, "@@:@@c");
        addMethod(c, "onAction:", actionCb, "v@:@");
        registerClass(c);
        delegateClass = c;
    }

    /** Builds an {@code NSArray<NSString*>} of this delegate's item identifiers. */
    private static Pointer identifiers(Pointer self) {
        Pointer array = msg(msg(cls("NSMutableArray"), sel("alloc")), sel("init"));
        ToolbarManager mgr = INSTANCES.get(Pointer.nativeValue(self));
        if (mgr != null) {
            for (Item item : mgr.items) {
                msg(array, sel("addObject:"), nsString(item.id));
            }
        }
        return msg(array, sel("autorelease"));
    }

    private static Pointer buildItem(Pointer self, Pointer identifier) {
        ToolbarManager mgr = INSTANCES.get(Pointer.nativeValue(self));
        if (mgr == null) return Pointer.NULL;
        String id = fromNSString(identifier);
        Item item = mgr.byId.get(id);
        if (item == null) return Pointer.NULL;

        Pointer nsItem = msg(msg(cls("NSToolbarItem"), sel("alloc")),
                sel("initWithItemIdentifier:"), identifier);
        msg(nsItem, sel("setLabel:"), nsString(item.label));
        msg(nsItem, sel("setPaletteLabel:"), nsString(item.label));

        if (item.symbol != null) {
            Pointer image = msg(cls("NSImage"), sel("imageWithSystemSymbolName:accessibilityDescription:"),
                    nsString(item.symbol), Pointer.NULL);
            if (isValid(image)) msg(nsItem, sel("setImage:"), image);
        }

        msg(nsItem, sel("setTarget:"), self);
        msg(nsItem, sel("setAction:"), sel("onAction:"));
        msg(nsItem, sel("setTag:"), item.tag);
        return msg(nsItem, sel("autorelease"));
    }
}
