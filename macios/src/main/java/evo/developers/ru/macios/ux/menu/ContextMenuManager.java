package evo.developers.ru.macios.ux.menu;

import com.sun.jna.Pointer;
import evo.developers.ru.macios.base.Manager;
import evo.developers.ru.macios.core.ObjC;
import javafx.stage.Stage;

import java.util.function.Consumer;

import static evo.developers.ru.macios.core.ObjC.*;

/**
 * Shows a native {@code NSMenu} as a context menu at the current mouse location.
 *
 * <p>JavaFX renders into a single native view, so per-node native menus aren't possible;
 * instead, call {@link #show(Consumer)} from a node's {@code setOnContextMenuRequested}
 * handler — you decide which node triggers it, and the menu pops up natively.</p>
 *
 * <pre>{@code
 * node.setOnContextMenuRequested(e -> helper.getContextMenu().show(m -> m
 *     .item("Copy", () -> ...)
 *     .item("Paste", () -> ...)
 *     .separator()
 *     .item("Delete", () -> ...)));
 * }</pre>
 */
public class ContextMenuManager extends Manager {

    public ContextMenuManager(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
    }

    public void show(Consumer<NativeMenu> builder) {
        runFx(() -> {
            try {
                NativeMenu menu = new NativeMenu("Context");
                builder.accept(menu);

                // With inView == nil, the location is interpreted in screen coordinates,
                // which is exactly what -[NSEvent mouseLocation] returns.
                ObjC.CGPoint location = msgPoint(cls("NSEvent"), sel("mouseLocation"));
                msg(menu.handle(), sel("popUpMenuPositioningItem:atLocation:inView:"),
                        Pointer.NULL, location, Pointer.NULL);
            } catch (Throwable t) {
                System.out.println("ContextMenu show error: " + t.getMessage());
            }
        });
    }
}
