package evo.developers.ru.macios.core;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

public class ObjC {
    private static final NativeLibrary OBJC = NativeLibrary.getInstance("objc");
    private static final Function MSG_SEND = OBJC.getFunction("objc_msgSend");
    private static final Function GET_CLASS = OBJC.getFunction("objc_getClass");
    private static final Function SEL_REG = OBJC.getFunction("sel_registerName");

    public static Pointer sel(String name) {
        return (Pointer) SEL_REG.invoke(Pointer.class, new Object[]{name});
    }

    public static Pointer cls(String name) {
        return (Pointer) GET_CLASS.invoke(Pointer.class, new Object[]{name});
    }

    public static Pointer msg(Pointer receiver, Pointer selector, Object... args) {
        Object[] all = new Object[2 + args.length];
        all[0] = receiver;
        all[1] = selector;
        System.arraycopy(args, 0, all, 2, args.length);
        return (Pointer) MSG_SEND.invoke(Pointer.class, all);
    }

    public static long msgLong(Pointer receiver, Pointer selector, Object... args) {
        Object[] all = new Object[2 + args.length];
        all[0] = receiver;
        all[1] = selector;
        System.arraycopy(args, 0, all, 2, args.length);
        Object result = MSG_SEND.invoke(Long.class, all);
        return result != null ? (Long) result : 0L;
    }

    /** Create an {@code NSString} from a Java string. */
    public static Pointer nsString(String value) {
        if (value == null) return Pointer.NULL;
        return msg(cls("NSString"), sel("stringWithUTF8String:"), value);
    }

    /** Read a Java string from an {@code NSString} pointer (or {@code null}). */
    public static String fromNSString(Pointer nsString) {
        if (nsString == null || Pointer.nativeValue(nsString) == 0) return null;
        Pointer utf8 = msg(nsString, sel("UTF8String"));
        if (utf8 == null || Pointer.nativeValue(utf8) == 0) return null;
        return utf8.getString(0);
    }

    /** @return {@code true} if the given pointer is non-null and non-zero. */
    public static boolean isValid(Pointer p) {
        return p != null && Pointer.nativeValue(p) != 0;
    }
}
