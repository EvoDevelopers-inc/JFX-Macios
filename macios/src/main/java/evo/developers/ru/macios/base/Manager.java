package evo.developers.ru.macios.base;

import com.sun.jna.Pointer;
import evo.developers.ru.macios.core.WindowManager;
import javafx.application.Platform;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;

/**
 * Base class for every feature manager.
 *
 * <p>Each manager is bound to a {@link Stage} and resolves its native {@code NSWindow}
 * lazily via {@link #window()}, so the public API never needs a window-title argument —
 * callers just use {@code helper.getX().doThing()}.</p>
 */
abstract public class Manager {

    @Getter
    private final Stage stage;

    @Getter
    @Setter
    private Pointer pointerWindow;

    public Manager(Stage stage, Pointer pointerWindow) {
        this.stage = stage;
        this.pointerWindow = pointerWindow;
    }

    /** Resolves the native {@code NSWindow} for this manager's stage (or {@code null}). */
    protected Pointer window() {
        if (stage == null) return null;
        return WindowManager.getCurrentWindow(stage.getTitle());
    }

    /** Runs {@code r} on the JavaFX Application Thread (immediately if already on it). */
    protected static void runFx(Runnable r) {
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }
}
