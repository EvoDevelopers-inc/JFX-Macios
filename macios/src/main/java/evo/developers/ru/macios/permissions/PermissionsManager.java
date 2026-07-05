package evo.developers.ru.macios.permissions;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import evo.developers.ru.macios.base.Manager;
import evo.developers.ru.macios.core.ObjCRuntime;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.util.function.Consumer;

import static evo.developers.ru.macios.core.ObjC.*;

/**
 * Checks and requests macOS privacy permissions (TCC), and opens the matching
 * System Settings pane. Requesting a permission triggers the native OS prompt the first
 * time; afterwards the choice is remembered and only {@link #openSettings(Permission)} can
 * take the user to change it.
 *
 * <pre>{@code
 * var perms = helper.getPermissions();
 * if (perms.status(Permission.CAMERA) != PermissionStatus.GRANTED) {
 *     perms.request(Permission.CAMERA, granted -> System.out.println("camera: " + granted));
 * }
 * // Denied? Send the user to the exact settings pane:
 * perms.openSettings(Permission.SCREEN_RECORDING);
 * }</pre>
 *
 * <p>Note: many prompts (camera, mic, notifications, push) only appear for a properly
 * code-signed {@code .app} bundle with the relevant {@code Info.plist} usage strings /
 * entitlements. Unsigned dev launches may silently fail — the API degrades gracefully.</p>
 */
public class PermissionsManager extends Manager {

    private static final String AV_VIDEO = "vide"; // AVMediaTypeVideo
    private static final String AV_AUDIO = "soun"; // AVMediaTypeAudio

    private static Function cgPreflightScreenCapture;
    private static Function cgRequestScreenCapture;
    private static Function axIsProcessTrusted;
    private static volatile boolean frameworksLoaded;

    public PermissionsManager(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
    }

    // ---- Status -------------------------------------------------------------

    /** @return the current authorization status ({@link PermissionStatus#UNKNOWN} if async-only). */
    public PermissionStatus status(Permission permission) {
        ensureFrameworks();
        try {
            switch (permission) {
                case CAMERA:
                    return mapTcc(msgLong(cls("AVCaptureDevice"),
                            sel("authorizationStatusForMediaType:"), nsString(AV_VIDEO)));
                case MICROPHONE:
                    return mapTcc(msgLong(cls("AVCaptureDevice"),
                            sel("authorizationStatusForMediaType:"), nsString(AV_AUDIO)));
                case CONTACTS:
                    return mapTcc(msgLong(cls("CNContactStore"),
                            sel("authorizationStatusForEntityType:"), 0L));
                case CALENDAR:
                    return mapTcc(msgLong(cls("EKEventStore"),
                            sel("authorizationStatusForEntityType:"), 0L));
                case REMINDERS:
                    return mapTcc(msgLong(cls("EKEventStore"),
                            sel("authorizationStatusForEntityType:"), 1L));
                case PHOTOS:
                    return mapTcc(msgLong(cls("PHPhotoLibrary"), sel("authorizationStatus")));
                case SCREEN_RECORDING:
                    return preflightScreenCapture() ? PermissionStatus.GRANTED : PermissionStatus.DENIED;
                case ACCESSIBILITY:
                    return isProcessTrusted() ? PermissionStatus.GRANTED : PermissionStatus.NOT_DETERMINED;
                case INPUT_MONITORING:
                case NOTIFICATIONS:
                default:
                    return PermissionStatus.UNKNOWN;
            }
        } catch (Throwable t) {
            System.out.println("Permissions.status(" + permission + ") error: " + t.getMessage());
            return PermissionStatus.UNKNOWN;
        }
    }

    // ---- Request ------------------------------------------------------------

    /** Requests {@code permission}; {@code onResult} is called on the JavaFX thread with the outcome. */
    public void request(Permission permission, Consumer<Boolean> onResult) {
        ensureFrameworks();
        runFx(() -> {
            try {
                // These prompts hard-crash the process (uncaught NSException) if the app has no
                // bundle identifier / no matching Info.plist usage string — e.g. an unsigned dev
                // launch from the JDK. Fall back to opening System Settings instead.
                if (requiresBundle(permission) && !isAppBundle()) {
                    System.out.println("Permissions: " + permission + " requires a signed .app "
                            + "bundle with the matching Info.plist usage string; opening settings.");
                    openSettings(permission);
                    deliver(onResult, false);
                    return;
                }
                switch (permission) {
                    case CAMERA:      requestAV(AV_VIDEO, onResult); break;
                    case MICROPHONE:  requestAV(AV_AUDIO, onResult); break;
                    case CONTACTS:    requestContacts(onResult); break;
                    case CALENDAR:    requestEvent(0L, "requestFullAccessToEventsWithCompletion:", onResult); break;
                    case REMINDERS:   requestEvent(1L, "requestFullAccessToRemindersWithCompletion:", onResult); break;
                    case PHOTOS:      requestPhotos(onResult); break;
                    case NOTIFICATIONS: requestNotifications(onResult); break;
                    case SCREEN_RECORDING: deliver(onResult, requestScreenCapture()); break;
                    case ACCESSIBILITY:
                        openSettings(permission);
                        deliver(onResult, isProcessTrusted());
                        break;
                    case INPUT_MONITORING:
                    default:
                        openSettings(permission);
                        deliver(onResult, false);
                }
            } catch (Throwable t) {
                System.out.println("Permissions.request(" + permission + ") error: " + t.getMessage());
                deliver(onResult, false);
            }
        });
    }

    /** Opens the System Settings pane for {@code permission}. */
    public void openSettings(Permission permission) {
        try {
            Pointer url = msg(cls("NSURL"), sel("URLWithString:"), nsString(permission.settingsUrl()));
            Pointer workspace = msg(cls("NSWorkspace"), sel("sharedWorkspace"));
            if (isValid(url)) msg(workspace, sel("openURL:"), url);
        } catch (Throwable t) {
            System.out.println("Permissions.openSettings error: " + t.getMessage());
        }
    }

    // ---- Per-permission request helpers -------------------------------------

    private void requestAV(String mediaType, Consumer<Boolean> onResult) {
        ObjCRuntime.BoolBlock cb = (block, granted) -> deliver(onResult, granted != 0);
        Pointer blk = ObjCRuntime.createGlobalBlock(cb);
        msg(cls("AVCaptureDevice"), sel("requestAccessForMediaType:completionHandler:"),
                nsString(mediaType), blk);
    }

    private void requestContacts(Consumer<Boolean> onResult) {
        Pointer store = msg(msg(cls("CNContactStore"), sel("alloc")), sel("init"));
        ObjCRuntime.BoolErrorBlock cb = (block, granted, error) -> deliver(onResult, granted != 0);
        Pointer blk = ObjCRuntime.createGlobalBlock(cb);
        msg(store, sel("requestAccessForEntityType:completionHandler:"), 0L, blk);
    }

    private void requestEvent(long entityType, String fullAccessSelector, Consumer<Boolean> onResult) {
        Pointer store = msg(msg(cls("EKEventStore"), sel("alloc")), sel("init"));
        ObjCRuntime.BoolErrorBlock cb = (block, granted, error) -> deliver(onResult, granted != 0);
        Pointer blk = ObjCRuntime.createGlobalBlock(cb);
        // macOS 14+ split access into full/write-only; fall back to the legacy API otherwise.
        if (msgLong(store, sel("respondsToSelector:"), sel(fullAccessSelector)) != 0) {
            msg(store, sel(fullAccessSelector), blk);
        } else {
            msg(store, sel("requestAccessToEntityType:completion:"), entityType, blk);
        }
    }

    private void requestPhotos(Consumer<Boolean> onResult) {
        ObjCRuntime.LongBlock cb = (block, status) -> deliver(onResult, status >= 3);
        Pointer blk = ObjCRuntime.createGlobalBlock(cb);
        msg(cls("PHPhotoLibrary"), sel("requestAuthorization:"), blk);
    }

    private void requestNotifications(Consumer<Boolean> onResult) {
        // UNUserNotificationCenter throws an uncaught NSException (which crashes the whole
        // process) when the running executable has no bundle identifier — i.e. an unsigned
        // dev launch straight from the JDK. Guard against it.
        if (!isAppBundle()) {
            System.out.println("Permissions: notifications require a real .app bundle "
                    + "(the current process is not running from an .app).");
            deliver(onResult, false);
            return;
        }
        Pointer center = msg(cls("UNUserNotificationCenter"), sel("currentNotificationCenter"));
        if (!isValid(center)) { deliver(onResult, false); return; }
        long options = 1 | 2 | 4; // badge | sound | alert
        ObjCRuntime.BoolErrorBlock cb = (block, granted, error) -> deliver(onResult, granted != 0);
        Pointer blk = ObjCRuntime.createGlobalBlock(cb);
        msg(center, sel("requestAuthorizationWithOptions:completionHandler:"), options, blk);
    }

    // ---- C functions --------------------------------------------------------

    private boolean preflightScreenCapture() {
        ensureFrameworks();
        if (cgPreflightScreenCapture == null) return false;
        Object r = cgPreflightScreenCapture.invoke(Boolean.class, new Object[]{});
        return Boolean.TRUE.equals(r);
    }

    private boolean requestScreenCapture() {
        ensureFrameworks();
        if (cgRequestScreenCapture == null) { openSettings(Permission.SCREEN_RECORDING); return false; }
        Object r = cgRequestScreenCapture.invoke(Boolean.class, new Object[]{});
        return Boolean.TRUE.equals(r);
    }

    private boolean isProcessTrusted() {
        ensureFrameworks();
        if (axIsProcessTrusted == null) return false;
        Object r = axIsProcessTrusted.invoke(Boolean.class, new Object[]{});
        return Boolean.TRUE.equals(r);
    }

    // ---- Helpers ------------------------------------------------------------

    private static PermissionStatus mapTcc(long status) {
        switch ((int) status) {
            case 0:  return PermissionStatus.NOT_DETERMINED;
            case 1:  return PermissionStatus.RESTRICTED;
            case 2:  return PermissionStatus.DENIED;
            default: return PermissionStatus.GRANTED; // 3 authorized, 4 limited/fullAccess, 5 writeOnly
        }
    }

    private static void deliver(Consumer<Boolean> onResult, boolean value) {
        if (onResult != null) Platform.runLater(() -> onResult.accept(value));
    }

    /** Permissions whose request prompt requires a bundle + Info.plist usage string. */
    private static boolean requiresBundle(Permission permission) {
        switch (permission) {
            case CAMERA:
            case MICROPHONE:
            case CONTACTS:
            case CALENDAR:
            case REMINDERS:
            case PHOTOS:
            case NOTIFICATIONS:
                return true;
            default:
                return false;
        }
    }

    /**
     * @return {@code true} only if the current process runs from a real {@code .app} bundle with
     *         an identifier. A bare JDK launch has a non-nil {@code bundleIdentifier} but its
     *         path is not an {@code .app}, and {@code UNUserNotificationCenter} (and other TCC
     *         prompts) hard-crash there — so require the {@code .app} path too.
     */
    static boolean isAppBundle() {
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

    private static synchronized void ensureFrameworks() {
        if (frameworksLoaded) return;
        ObjCRuntime.loadFramework("/System/Library/Frameworks/AVFoundation.framework/AVFoundation");
        ObjCRuntime.loadFramework("/System/Library/Frameworks/Contacts.framework/Contacts");
        ObjCRuntime.loadFramework("/System/Library/Frameworks/EventKit.framework/EventKit");
        ObjCRuntime.loadFramework("/System/Library/Frameworks/Photos.framework/Photos");
        ObjCRuntime.loadFramework("/System/Library/Frameworks/UserNotifications.framework/UserNotifications");
        try {
            NativeLibrary cg = NativeLibrary.getInstance(
                    "/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics");
            cgPreflightScreenCapture = safeFn(cg, "CGPreflightScreenCaptureAccess");
            cgRequestScreenCapture = safeFn(cg, "CGRequestScreenCaptureAccess");
        } catch (Throwable ignored) { }
        try {
            NativeLibrary ax = NativeLibrary.getInstance(
                    "/System/Library/Frameworks/ApplicationServices.framework/ApplicationServices");
            axIsProcessTrusted = safeFn(ax, "AXIsProcessTrusted");
        } catch (Throwable ignored) { }
        frameworksLoaded = true;
    }

    private static Function safeFn(NativeLibrary lib, String name) {
        try {
            return lib.getFunction(name);
        } catch (Throwable t) {
            return null;
        }
    }
}
