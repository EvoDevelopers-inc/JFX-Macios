package evo.developers.ru.macios.ux.alert;

import com.sun.jna.Pointer;
import evo.developers.ru.macios.base.Manager;
import evo.developers.ru.macios.core.ObjCRuntime;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.util.concurrent.CountDownLatch;

import static evo.developers.ru.macios.core.ObjC.*;
import static evo.developers.ru.macios.core.ObjCRuntime.newInstance;

/**
 * Native {@code NSAlert} dialogs, shown either as a window-attached sheet (non-blocking,
 * preferred) or as an application-modal dialog.
 *
 * <p>Button results are reported as a zero-based index into the {@code buttons} you passed
 * (the first button is the default / rightmost).</p>
 *
 * <pre>{@code
 * helper.getAlert().showSheet("Delete file?", "This cannot be undone.",
 *     AlertManager.Style.WARNING, new String[]{ "Delete", "Cancel" },
 *     index -> { if (index == 0) delete(); });
 * }</pre>
 */
public class AlertManager extends Manager {

    /** {@code NSAlertFirstButtonReturn}. */
    private static final long FIRST_BUTTON_RETURN = 1000;

    public enum Style {
        WARNING(0),
        INFORMATIONAL(1),
        CRITICAL(2);

        final long value;
        Style(long value) { this.value = value; }
    }

    public interface ResultCallback {
        /** @param buttonIndex zero-based index of the pressed button, or {@code -1} on error */
        void onResult(int buttonIndex);
    }

    public AlertManager(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
    }

    /** Shows a sheet attached to this manager's window; {@code callback} runs on the JavaFX thread. */
    public void showSheet(String title, String message, Style style, String[] buttons, ResultCallback callback) {
        runFx(() -> {
            try {
                Pointer window = window();
                Pointer alert = build(title, message, style, buttons);
                if (window == null) {
                    // No window to attach to — fall back to a modal dialog.
                    long resp = msgLong(alert, sel("runModal"));
                    if (callback != null) callback.onResult((int) (resp - FIRST_BUTTON_RETURN));
                    return;
                }

                Pointer block = ObjCRuntime.createGlobalBlock(
                        (ObjCRuntime.LongBlock) (blk, response) -> {
                            int index = (int) (response - FIRST_BUTTON_RETURN);
                            if (callback != null) Platform.runLater(() -> callback.onResult(index));
                        });

                msg(alert, sel("beginSheetModalForWindow:completionHandler:"), window, block);
            } catch (Throwable t) {
                System.out.println("Alert showSheet error: " + t.getMessage());
                if (callback != null) callback.onResult(-1);
            }
        });
    }

    /**
     * Shows an application-modal alert and blocks until the user responds.
     *
     * @return the zero-based index of the pressed button, or {@code -1} on error
     */
    public int showModal(String title, String message, Style style, String... buttons) {
        if (Platform.isFxApplicationThread()) {
            return runModal(title, message, style, buttons);
        }
        final int[] result = { -1 };
        final CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result[0] = runModal(title, message, style, buttons);
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result[0];
    }

    private int runModal(String title, String message, Style style, String[] buttons) {
        try {
            Pointer alert = build(title, message, style, buttons);
            long resp = msgLong(alert, sel("runModal"));
            return (int) (resp - FIRST_BUTTON_RETURN);
        } catch (Throwable t) {
            System.out.println("Alert showModal error: " + t.getMessage());
            return -1;
        }
    }

    private Pointer build(String title, String message, Style style, String[] buttons) {
        Pointer alert = newInstance(cls("NSAlert"));
        msg(alert, sel("setMessageText:"), nsString(title == null ? "" : title));
        msg(alert, sel("setInformativeText:"), nsString(message == null ? "" : message));
        msg(alert, sel("setAlertStyle:"), style.value);

        if (buttons == null || buttons.length == 0) {
            msg(alert, sel("addButtonWithTitle:"), nsString("OK"));
        } else {
            for (String b : buttons) {
                msg(alert, sel("addButtonWithTitle:"), nsString(b));
            }
        }
        return alert;
    }
}
