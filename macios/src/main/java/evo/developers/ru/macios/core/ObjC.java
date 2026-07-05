package evo.developers.ru.macios.core;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;

public class ObjC {
    private static final NativeLibrary OBJC = NativeLibrary.getInstance("objc");
    private static final Function MSG_SEND = OBJC.getFunction("objc_msgSend");
    private static final Function GET_CLASS = OBJC.getFunction("objc_getClass");
    private static final Function SEL_REG = OBJC.getFunction("sel_registerName");

    /** {@code CGPoint} / {@code NSPoint} (two 64-bit floats). */
    public static class CGPoint extends Structure implements Structure.ByValue {
        public double x;
        public double y;

        public CGPoint() { }
        public CGPoint(double x, double y) { this.x = x; this.y = y; }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("x", "y");
        }
    }

    /** {@code CGRect} / {@code NSRect} (origin.x, origin.y, size.width, size.height). */
    public static class CGRect extends Structure implements Structure.ByValue {
        public double x;
        public double y;
        public double width;
        public double height;

        public CGRect() { }
        public CGRect(double x, double y, double width, double height) {
            this.x = x; this.y = y; this.width = width; this.height = height;
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("x", "y", "width", "height");
        }
    }

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

    /** Sends a message whose return type is a {@code double} ({@code CGFloat}). */
    public static double msgDouble(Pointer receiver, Pointer selector, Object... args) {
        Object[] all = new Object[2 + args.length];
        all[0] = receiver;
        all[1] = selector;
        System.arraycopy(args, 0, all, 2, args.length);
        Object result = MSG_SEND.invoke(Double.class, all);
        return result != null ? (Double) result : 0.0;
    }

    /** Sends a message whose return type is a {@code CGPoint} passed/returned by value. */
    public static CGPoint msgPoint(Pointer receiver, Pointer selector, Object... args) {
        Object[] all = new Object[2 + args.length];
        all[0] = receiver;
        all[1] = selector;
        System.arraycopy(args, 0, all, 2, args.length);
        return (CGPoint) MSG_SEND.invoke(CGPoint.class, all);
    }

    /** Sends a message whose return type is a {@code CGRect} passed/returned by value. */
    public static CGRect msgRect(Pointer receiver, Pointer selector, Object... args) {
        Object[] all = new Object[2 + args.length];
        all[0] = receiver;
        all[1] = selector;
        System.arraycopy(args, 0, all, 2, args.length);
        return (CGRect) MSG_SEND.invoke(CGRect.class, all);
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
