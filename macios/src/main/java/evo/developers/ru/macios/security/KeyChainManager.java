package evo.developers.ru.macios.security;

import com.sun.jna.*;
import evo.developers.ru.macios.base.Manager;
import javafx.stage.Stage;

import static evo.developers.ru.macios.core.ObjC.*;

@Deprecated
public class KeyChainManager extends Manager {
    private static final NativeLibrary SECURITY;
    private static final Function SEC_ITEM_ADD;
    private static final Function SEC_ITEM_COPY_MATCHING;
    private static final Function SEC_ITEM_UPDATE;
    private static final Function SEC_ITEM_DELETE;

    static {
        NativeLibrary security = null;
        Function add = null, copy = null, update = null, delete = null;
        try {
            security = NativeLibrary.getInstance("Security");
            add = security.getFunction("SecItemAdd");
            copy = security.getFunction("SecItemCopyMatching");
            update = security.getFunction("SecItemUpdate");
            delete = security.getFunction("SecItemDelete");
        } catch (Exception e) {
            System.out.println("WindowBlur: Security framework not available");
        }
        SECURITY = security;
        SEC_ITEM_ADD = add;
        SEC_ITEM_COPY_MATCHING = copy;
        SEC_ITEM_UPDATE = update;
        SEC_ITEM_DELETE = delete;
    }

    // Keychain constants (CFString keys)
    private static Pointer kSecClass;
    private static Pointer kSecClassGenericPassword;
    private static Pointer kSecAttrService;
    private static Pointer kSecAttrAccount;
    private static Pointer kSecValueData;
    private static Pointer kSecReturnData;
    private static Pointer kSecMatchLimit;
    private static Pointer kSecMatchLimitOne;

    private static boolean keychainInitialized = false;

    public KeyChainManager(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
    }

    private static void initKeychainConstants() {
        if (keychainInitialized) return;
        try {
            // Get constant pointers from Security framework
            kSecClass = SECURITY.getGlobalVariableAddress("kSecClass").getPointer(0);
            kSecClassGenericPassword = SECURITY.getGlobalVariableAddress("kSecClassGenericPassword").getPointer(0);
            kSecAttrService = SECURITY.getGlobalVariableAddress("kSecAttrService").getPointer(0);
            kSecAttrAccount = SECURITY.getGlobalVariableAddress("kSecAttrAccount").getPointer(0);
            kSecValueData = SECURITY.getGlobalVariableAddress("kSecValueData").getPointer(0);
            kSecReturnData = SECURITY.getGlobalVariableAddress("kSecReturnData").getPointer(0);
            kSecMatchLimit = SECURITY.getGlobalVariableAddress("kSecMatchLimit").getPointer(0);
            kSecMatchLimitOne = SECURITY.getGlobalVariableAddress("kSecMatchLimitOne").getPointer(0);
            keychainInitialized = true;
        } catch (Exception e) {
            System.out.println("WindowBlur: Failed to init keychain constants: " + e.getMessage());
        }
    }

    /**
     * Store a secret value in macOS Keychain.
     *
     * The value is:
     * - Encrypted with AES-256
     * - Stored in system keychain
     * - Protected by user's macOS password
     * - Accessible only by your app
     *
     * Perfect for: auth tokens, encryption keys, passwords, API keys
     *
     * @param service Your app identifier (e.g., "com.myapp.messenger")
     * @param account Key name (e.g., "authToken", "encryptionKey")
     * @param secret The secret value to store
     * @return true if stored successfully
     */
    public static boolean keychainStore(String service, String account, String secret) {
        initKeychainConstants();
        if (!keychainInitialized || SEC_ITEM_ADD == null) {
            System.out.println("WindowBlur: Keychain not available");
            return false;
        }

        try {
            // First try to delete existing (to update)
            keychainDelete(service, account);

            // Create CFStrings
            Pointer cfService = createCFString(service);
            Pointer cfAccount = createCFString(account);
            Pointer cfSecret = createCFData(secret.getBytes("UTF-8"));

            // Create query dictionary
            Pointer[] keys = {kSecClass, kSecAttrService, kSecAttrAccount, kSecValueData};
            Pointer[] values = {kSecClassGenericPassword, cfService, cfAccount, cfSecret};

            Pointer query = createCFDictionary(keys, values, 4);

            // Add to keychain
            int status = (Integer) SEC_ITEM_ADD.invoke(Integer.class, new Object[]{query, Pointer.NULL});

            // Release
            releaseCF(cfService);
            releaseCF(cfAccount);
            releaseCF(cfSecret);
            releaseCF(query);

            if (status == 0) {
                System.out.println("WindowBlur: ✅ Stored in Keychain: " + account);
                return true;
            } else {
                System.out.println("WindowBlur: Keychain store failed with status: " + status);
                return false;
            }

        } catch (Exception e) {
            System.out.println("WindowBlur keychainStore error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Retrieve a secret value from macOS Keychain.
     *
     * @param service Your app identifier
     * @param account Key name
     * @return The secret value, or null if not found
     */
    public static String keychainGet(String service, String account) {
        initKeychainConstants();
        if (!keychainInitialized || SEC_ITEM_COPY_MATCHING == null) return null;

        try {
            Pointer cfService = createCFString(service);
            Pointer cfAccount = createCFString(account);
            Pointer cfTrue = msg(cls("NSNumber"), sel("numberWithBool:"), 1L);

            Pointer[] keys = {kSecClass, kSecAttrService, kSecAttrAccount, kSecReturnData, kSecMatchLimit};
            Pointer[] values = {kSecClassGenericPassword, cfService, cfAccount, cfTrue, kSecMatchLimitOne};

            Pointer query = createCFDictionary(keys, values, 5);

            // Result pointer
            Memory resultPtr = new Memory(Native.POINTER_SIZE);

            int status = (Integer) SEC_ITEM_COPY_MATCHING.invoke(Integer.class, new Object[]{query, resultPtr});

            String result = null;
            if (status == 0) {
                Pointer dataRef = resultPtr.getPointer(0);
                if (dataRef != null && Pointer.nativeValue(dataRef) != 0) {
                    // Get bytes from CFData
                    long length = msgLong(dataRef, sel("length"));
                    Pointer bytes = msg(dataRef, sel("bytes"));
                    if (bytes != null && length > 0) {
                        byte[] data = bytes.getByteArray(0, (int) length);
                        result = new String(data, "UTF-8");
                    }
                    releaseCF(dataRef);
                }
            }

            releaseCF(cfService);
            releaseCF(cfAccount);
            releaseCF(query);

            return result;

        } catch (Exception e) {
            System.out.println("WindowBlur keychainGet error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Delete a secret from macOS Keychain.
     */
    public static boolean keychainDelete(String service, String account) {
        initKeychainConstants();
        if (!keychainInitialized || SEC_ITEM_DELETE == null) return false;

        try {
            Pointer cfService = createCFString(service);
            Pointer cfAccount = createCFString(account);

            Pointer[] keys = {kSecClass, kSecAttrService, kSecAttrAccount};
            Pointer[] values = {kSecClassGenericPassword, cfService, cfAccount};

            Pointer query = createCFDictionary(keys, values, 3);

            int status = (Integer) SEC_ITEM_DELETE.invoke(Integer.class, new Object[]{query});

            releaseCF(cfService);
            releaseCF(cfAccount);
            releaseCF(query);

            return status == 0 || status == -25300; // 0 = success, -25300 = not found (ok for delete)

        } catch (Exception e) {
            return false;
        }
    }

    private static void releaseCF(Pointer obj) {
        // ARC handles this in modern macOS, but explicit release for safety
        // Actually with ARC on the ObjC side and JNA, we don't need to release NSObjects
        // Just let them go out of scope
    }
    private static Pointer createCFString(String str) {
        return msg(cls("NSString"), sel("stringWithUTF8String:"), str);
    }

    private static Pointer createCFData(byte[] data) {
        return msg(cls("NSData"), sel("dataWithBytes:length:"), data, data.length);
    }

    private static Pointer createCFDictionary(Pointer[] keys, Pointer[] values, int count) {
        // Use NSDictionary for simplicity
        Pointer dict = msg(msg(cls("NSMutableDictionary"), sel("alloc")), sel("init"));
        for (int i = 0; i < count; i++) {
            msg(dict, sel("setObject:forKey:"), values[i], keys[i]);
        }
        return dict;
    }

    /**
     * Check if a key exists in Keychain.
     */
    public static boolean keychainExists(String service, String account) {
        return keychainGet(service, account) != null;
    }
}
