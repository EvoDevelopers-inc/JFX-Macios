package evo.developers.ru.macios.ux.push;

import com.sun.jna.Pointer;
import evo.developers.ru.macios.base.Manager;
import evo.developers.ru.macios.core.ObjCRuntime;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.util.function.Consumer;

import static evo.developers.ru.macios.core.ObjC.*;
import static evo.developers.ru.macios.core.ObjCRuntime.*;

/**
 * Registers the app with Apple Push Notification service (APNs) and surfaces the resulting
 * device token, failures, and incoming remote notifications back to Java.
 *
 * <p>APNs delivery only works for a properly code-signed {@code .app} bundle that carries the
 * {@code aps-environment} entitlement and (for user-visible alerts) notification authorization.
 * Request notification permission first — {@link #register()} does this for you — otherwise the
 * OS will withhold alerts even after a token is issued. In an unsigned dev launch registration
 * typically calls {@link #onError} with a {@code no valid "aps-environment"} error.</p>
 *
 * <pre>{@code
 * helper.getPush()
 *     .onToken(hex -> System.out.println("APNs token: " + hex))
 *     .onError(err -> System.out.println("APNs error: " + err))
 *     .onRemoteNotification(payload -> System.out.println("push: " + payload))
 *     .register();
 * }</pre>
 */
public class PushNotificationManager extends Manager {

    private static volatile Consumer<String> onToken;
    private static volatile Consumer<String> onError;
    private static volatile Consumer<String> onRemote;

    private static ObjCRuntime.VoidObjObjMethod tokenCb;
    private static ObjCRuntime.VoidObjObjMethod errorCb;
    private static ObjCRuntime.VoidObjObjMethod remoteCb;
    private static boolean injected;

    public PushNotificationManager(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
    }

    /** Called on the JavaFX thread with the hex device token once APNs registration succeeds. */
    public PushNotificationManager onToken(Consumer<String> handler) {
        onToken = handler;
        return this;
    }

    /** Called on the JavaFX thread with a description if registration fails. */
    public PushNotificationManager onError(Consumer<String> handler) {
        onError = handler;
        return this;
    }

    /** Called on the JavaFX thread with the payload description of an incoming remote notification. */
    public PushNotificationManager onRemoteNotification(Consumer<String> handler) {
        onRemote = handler;
        return this;
    }

    /** Requests notification authorization, then registers for remote (APNs) notifications. */
    public void register() {
        runFx(() -> {
            try {
                if (!isAppBundle()) {
                    fail("APNs requires a real signed .app bundle — the current process is "
                            + "running from the JDK, not an .app.");
                    return;
                }
                inject();
                requestNotificationAuth();
                Pointer app = msg(cls("NSApplication"), sel("sharedApplication"));
                msg(app, sel("registerForRemoteNotifications"));
            } catch (Throwable t) {
                fail("register error: " + t.getMessage());
            }
        });
    }

    /** Unregisters from remote notifications. */
    public void unregister() {
        runFx(() -> {
            try {
                Pointer app = msg(cls("NSApplication"), sel("sharedApplication"));
                msg(app, sel("unregisterForRemoteNotifications"));
            } catch (Throwable ignored) { }
        });
    }

    private void requestNotificationAuth() {
        try {
            if (!isAppBundle()) return; // UNUserNotificationCenter hard-crashes outside a real .app
            ObjCRuntime.loadFramework(
                    "/System/Library/Frameworks/UserNotifications.framework/UserNotifications");
            Pointer center = msg(cls("UNUserNotificationCenter"), sel("currentNotificationCenter"));
            if (!isValid(center)) return;
            long options = 1 | 2 | 4; // badge | sound | alert
            ObjCRuntime.BoolErrorBlock cb = (block, granted, error) -> { };
            Pointer blk = ObjCRuntime.createGlobalBlock(cb);
            msg(center, sel("requestAuthorizationWithOptions:completionHandler:"), options, blk);
        } catch (Throwable t) {
            System.out.println("Push: notification auth error: " + t.getMessage());
        }
    }

    private static synchronized void inject() {
        if (injected) return;
        Pointer app = msg(cls("NSApplication"), sel("sharedApplication"));
        Pointer delegate = msg(app, sel("delegate"));
        if (!isValid(delegate)) {
            System.out.println("Push: no application delegate yet, cannot install APNs handlers");
            return;
        }
        Pointer delegateClass = classOf(delegate);

        tokenCb = (self, cmd, application, deviceToken) -> {
            String hex = toHex(deviceToken);
            Consumer<String> h = onToken;
            if (h != null) Platform.runLater(() -> h.accept(hex));
        };
        errorCb = (self, cmd, application, error) -> {
            String desc = isValid(error) ? fromNSString(msg(error, sel("localizedDescription"))) : "unknown";
            fail(desc);
        };
        remoteCb = (self, cmd, application, userInfo) -> {
            String desc = isValid(userInfo) ? fromNSString(msg(userInfo, sel("description"))) : null;
            Consumer<String> h = onRemote;
            if (h != null) Platform.runLater(() -> h.accept(desc));
        };

        addMethod(delegateClass, "application:didRegisterForRemoteNotificationsWithDeviceToken:",
                tokenCb, "v@:@@");
        addMethod(delegateClass, "application:didFailToRegisterForRemoteNotificationsWithError:",
                errorCb, "v@:@@");
        addMethod(delegateClass, "application:didReceiveRemoteNotification:",
                remoteCb, "v@:@@");
        injected = true;
    }

    private static String toHex(Pointer nsData) {
        if (!isValid(nsData)) return null;
        long len = msgLong(nsData, sel("length"));
        if (len <= 0) return "";
        Pointer bytes = msg(nsData, sel("bytes"));
        if (!isValid(bytes)) return "";
        byte[] raw = bytes.getByteArray(0, (int) len);
        StringBuilder sb = new StringBuilder((int) len * 2);
        for (byte b : raw) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    private static void fail(String message) {
        Consumer<String> h = onError;
        if (h != null) Platform.runLater(() -> h.accept(message));
        else System.out.println("Push error: " + message);
    }

    /** @return {@code true} only if the current process runs from a real {@code .app} bundle. */
    private static boolean isAppBundle() {
        try {
            Pointer bundle = msg(cls("NSBundle"), sel("mainBundle"));
            if (!isValid(bundle)) return false;
            String id = fromNSString(msg(bundle, sel("bundleIdentifier")));
            String path = fromNSString(msg(bundle, sel("bundlePath")));
            return id != null && path != null && path.endsWith(".app");
        } catch (Throwable t) {
            return false;
        }
    }
}
