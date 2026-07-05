package evo.developers.ru.macios.security;

import com.sun.jna.*;
import evo.developers.ru.macios.base.Manager;
import javafx.stage.Stage;

import java.nio.charset.StandardCharsets;

import static evo.developers.ru.macios.core.ObjC.*;

/**
 * Secure storage backed by the macOS Keychain ({@code Security.framework} {@code SecItem*}
 * APIs) for generic passwords — auth tokens, API keys, encryption keys, etc.
 *
 * <p>Values are stored encrypted in the login keychain and protected by the user's macOS
 * account. The API is static (the keychain is process-wide, not window-scoped); instance
 * methods are provided for facade consistency via {@code helper.getKeychain()}.</p>
 */
public class KeyChainManager extends Manager {

    /** {@code errSecItemNotFound}. */
    private static final int ERR_ITEM_NOT_FOUND = -25300;

    private static final Function SEC_ITEM_ADD;
    private static final Function SEC_ITEM_COPY_MATCHING;
    private static final Function SEC_ITEM_DELETE;
    private static final Function CF_RELEASE;

    static {
        Function add = null, copy = null, delete = null, release = null;
        try {
            NativeLibrary security = NativeLibrary.getInstance("Security");
            add = security.getFunction("SecItemAdd");
            copy = security.getFunction("SecItemCopyMatching");
            delete = security.getFunction("SecItemDelete");
            release = NativeLibrary.getInstance("CoreFoundation").getFunction("CFRelease");
        } catch (Throwable t) {
            System.out.println("Keychain: Security framework not available: " + t.getMessage());
        }
        SEC_ITEM_ADD = add;
        SEC_ITEM_COPY_MATCHING = copy;
        SEC_ITEM_DELETE = delete;
        CF_RELEASE = release;
    }

    // CoreFoundation / Security constant keys (CFStringRef / CFBooleanRef globals).
    private static Pointer kSecClass;
    private static Pointer kSecClassGenericPassword;
    private static Pointer kSecAttrService;
    private static Pointer kSecAttrAccount;
    private static Pointer kSecValueData;
    private static Pointer kSecReturnData;
    private static Pointer kSecMatchLimit;
    private static Pointer kSecMatchLimitOne;
    private static Pointer kCFBooleanTrue;

    private static boolean initialized;
    private static boolean initFailed;

    public KeyChainManager(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
    }

    private static synchronized boolean ensureConstants() {
        if (initialized) return true;
        if (initFailed || SEC_ITEM_ADD == null) return false;
        try {
            NativeLibrary security = NativeLibrary.getInstance("Security");
            NativeLibrary cf = NativeLibrary.getInstance("CoreFoundation");

            kSecClass = deref(security, "kSecClass");
            kSecClassGenericPassword = deref(security, "kSecClassGenericPassword");
            kSecAttrService = deref(security, "kSecAttrService");
            kSecAttrAccount = deref(security, "kSecAttrAccount");
            kSecValueData = deref(security, "kSecValueData");
            kSecReturnData = deref(security, "kSecReturnData");
            kSecMatchLimit = deref(security, "kSecMatchLimit");
            kSecMatchLimitOne = deref(security, "kSecMatchLimitOne");
            kCFBooleanTrue = deref(cf, "kCFBooleanTrue");

            initialized = true;
            return true;
        } catch (Throwable t) {
            System.out.println("Keychain: failed to init constants: " + t.getMessage());
            initFailed = true;
            return false;
        }
    }

    private static Pointer deref(NativeLibrary lib, String symbol) {
        return lib.getGlobalVariableAddress(symbol).getPointer(0);
    }

    // ---- Static API ----

    /**
     * Stores (or replaces) a secret for the given service/account.
     *
     * @param service app identifier, e.g. {@code "com.myapp.messenger"}
     * @param account key name, e.g. {@code "authToken"}
     * @param secret  the value to store
     * @return {@code true} on success
     */
    public static boolean keychainStore(String service, String account, String secret) {
        if (!ensureConstants()) return false;
        try {
            // Replace any existing item first (SecItemAdd fails with errSecDuplicateItem otherwise).
            keychainDelete(service, account);

            Pointer query = dictionary(
                    new Pointer[]{ kSecClass, kSecAttrService, kSecAttrAccount, kSecValueData },
                    new Pointer[]{ kSecClassGenericPassword, nsString(service), nsString(account),
                            data(secret.getBytes(StandardCharsets.UTF_8)) });

            int status = (Integer) SEC_ITEM_ADD.invoke(Integer.class, new Object[]{ query, Pointer.NULL });
            if (status != 0) {
                System.out.println("Keychain: store failed, status=" + status);
            }
            return status == 0;
        } catch (Throwable t) {
            System.out.println("Keychain store error: " + t.getMessage());
            return false;
        }
    }

    /** @return the stored secret for the service/account, or {@code null} if not found. */
    public static String keychainGet(String service, String account) {
        if (!ensureConstants()) return null;
        try {
            Pointer query = dictionary(
                    new Pointer[]{ kSecClass, kSecAttrService, kSecAttrAccount, kSecReturnData, kSecMatchLimit },
                    new Pointer[]{ kSecClassGenericPassword, nsString(service), nsString(account),
                            kCFBooleanTrue, kSecMatchLimitOne });

            Memory resultRef = new Memory(Native.POINTER_SIZE);
            int status = (Integer) SEC_ITEM_COPY_MATCHING.invoke(Integer.class, new Object[]{ query, resultRef });
            if (status != 0) return null;

            Pointer dataRef = resultRef.getPointer(0);
            if (!isValid(dataRef)) return null;

            String result = null;
            long length = msgLong(dataRef, sel("length"));
            Pointer bytes = msg(dataRef, sel("bytes"));
            if (isValid(bytes) && length > 0) {
                result = new String(bytes.getByteArray(0, (int) length), StandardCharsets.UTF_8);
            }
            // SecItemCopyMatching returns a +1 CFData (the "Copy" rule) — release it.
            if (CF_RELEASE != null) CF_RELEASE.invoke(Void.class, new Object[]{ dataRef });
            return result;
        } catch (Throwable t) {
            System.out.println("Keychain get error: " + t.getMessage());
            return null;
        }
    }

    /** Deletes the secret for the service/account. */
    public static boolean keychainDelete(String service, String account) {
        if (!ensureConstants()) return false;
        try {
            Pointer query = dictionary(
                    new Pointer[]{ kSecClass, kSecAttrService, kSecAttrAccount },
                    new Pointer[]{ kSecClassGenericPassword, nsString(service), nsString(account) });

            int status = (Integer) SEC_ITEM_DELETE.invoke(Integer.class, new Object[]{ query });
            return status == 0 || status == ERR_ITEM_NOT_FOUND;
        } catch (Throwable t) {
            System.out.println("Keychain delete error: " + t.getMessage());
            return false;
        }
    }

    public static boolean keychainExists(String service, String account) {
        return keychainGet(service, account) != null;
    }

    // ---- Instance API (facade) ----

    public boolean store(String service, String account, String secret) {
        return keychainStore(service, account, secret);
    }

    public String get(String service, String account) {
        return keychainGet(service, account);
    }

    public boolean delete(String service, String account) {
        return keychainDelete(service, account);
    }

    public boolean exists(String service, String account) {
        return keychainExists(service, account);
    }

    // ---- Helpers (NSObjects are toll-free bridged to their CF counterparts) ----

    /** Autoreleased {@code NSMutableDictionary} used as a {@code CFDictionaryRef} query. */
    private static Pointer dictionary(Pointer[] keys, Pointer[] values) {
        Pointer dict = msg(cls("NSMutableDictionary"), sel("dictionary"));
        for (int i = 0; i < keys.length; i++) {
            msg(dict, sel("setObject:forKey:"), values[i], keys[i]);
        }
        return dict;
    }

    private static Pointer data(byte[] bytes) {
        return msg(cls("NSData"), sel("dataWithBytes:length:"), bytes, (long) bytes.length);
    }
}
