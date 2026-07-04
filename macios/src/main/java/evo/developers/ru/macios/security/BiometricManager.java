package evo.developers.ru.macios.security;

import com.sun.jna.Pointer;
import evo.developers.ru.macios.base.Manager;
import javafx.stage.Stage;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static evo.developers.ru.macios.core.ObjC.*;
import static evo.developers.ru.macios.core.ObjCRuntime.*;

/**
 * Touch ID / biometric authentication via {@code LAContext} (LocalAuthentication).
 *
 * <p>Note: requires a Mac with Touch ID (or a paired Apple Watch for some policies),
 * and the app should ideally run from a signed {@code .app} bundle for the prompt to
 * behave correctly.</p>
 */
public class BiometricManager extends Manager {

    /** LAPolicyDeviceOwnerAuthenticationWithBiometrics — Touch ID only. */
    public static final long POLICY_BIOMETRICS = 1;
    /** LAPolicyDeviceOwnerAuthentication — Touch ID or password fallback. */
    public static final long POLICY_BIOMETRICS_OR_PASSWORD = 2;

    static {
        loadFramework("/System/Library/Frameworks/LocalAuthentication.framework/LocalAuthentication");
    }

    public BiometricManager(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
    }

    public interface AuthCallback {
        void onResult(boolean success, String error);
    }

    /** @return whether biometric authentication is currently available. */
    public static boolean isBiometricsAvailable() {
        return canEvaluate(POLICY_BIOMETRICS);
    }

    public static boolean canEvaluate(long policy) {
        try {
            Pointer ctx = newContext();
            if (!isValid(ctx)) return false;
            return msgLong(ctx, sel("canEvaluatePolicy:error:"), policy, Pointer.NULL) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Asynchronous authentication; the callback fires when the user responds. */
    public void authenticate(String reason, AuthCallback callback) {
        authenticate(POLICY_BIOMETRICS_OR_PASSWORD, reason, callback);
    }

    public void authenticate(long policy, String reason, AuthCallback callback) {
        try {
            Pointer ctx = newContext();
            if (!isValid(ctx)) {
                if (callback != null) callback.onResult(false, "LAContext unavailable");
                return;
            }
            msg(ctx, sel("retain")); // keep alive until the reply block runs

            Pointer block = createBoolErrorBlock((blk, success, error) -> {
                String errorText = null;
                if (success == 0 && isValid(error)) {
                    errorText = fromNSString(msg(error, sel("localizedDescription")));
                }
                try {
                    if (callback != null) callback.onResult(success != 0, errorText);
                } finally {
                    msg(ctx, sel("release"));
                }
            });

            msg(ctx, sel("evaluatePolicy:localizedReason:reply:"), policy, nsString(reason), block);
        } catch (Throwable t) {
            if (callback != null) callback.onResult(false, t.getMessage());
        }
    }

    /**
     * Blocking authentication. Do NOT call on the JavaFX thread — the prompt needs the
     * main thread to keep running. Returns {@code true} on success.
     */
    public boolean authenticateBlocking(String reason, long timeoutSeconds) {
        final AtomicBoolean result = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);
        authenticate(reason, (ok, err) -> {
            result.set(ok);
            latch.countDown();
        });
        try {
            latch.await(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        return result.get();
    }

    private static Pointer newContext() {
        return msg(msg(cls("LAContext"), sel("alloc")), sel("init"));
    }
}
