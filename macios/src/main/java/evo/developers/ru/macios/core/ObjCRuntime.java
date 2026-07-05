package evo.developers.ru.macios.core;

import com.sun.jna.Callback;
import com.sun.jna.CallbackReference;
import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

import static evo.developers.ru.macios.core.ObjC.*;

/**
 * Extends the minimal {@link ObjC} bridge with the pieces needed for callbacks:
 * defining Objective-C classes at runtime (for target/action and delegates) and
 * building Objective-C blocks (for APIs like {@code LAContext}'s reply block).
 */
public class ObjCRuntime {

    private static final NativeLibrary OBJC = NativeLibrary.getInstance("objc");
    private static final Function ALLOC_CLASS = OBJC.getFunction("objc_allocateClassPair");
    private static final Function REGISTER_CLASS = OBJC.getFunction("objc_registerClassPair");
    private static final Function ADD_METHOD = OBJC.getFunction("class_addMethod");
    private static final Function OBJECT_GET_CLASS = OBJC.getFunction("object_getClass");

    /** Strong references so JNA callbacks / native memory are never garbage collected. */
    private static final List<Object> KEEP_ALIVE = Collections.synchronizedList(new ArrayList<>());

    private static Pointer globalBlockIsa;

    // ---- Objective-C method signatures (JNA calling convention: id self, SEL _cmd, ...) ----

    /** {@code -(void)sel:(id)arg} — type encoding {@code "v@:@"}. */
    public interface VoidObjMethod extends Callback {
        void invoke(Pointer self, Pointer cmd, Pointer arg);
    }

    /** {@code -(NSInteger)sel:(id)arg} — type encoding {@code "q@:@"} (e.g. remote command handlers). */
    public interface LongObjMethod extends Callback {
        long invoke(Pointer self, Pointer cmd, Pointer arg);
    }

    /** {@code -(BOOL)sel} — type encoding {@code "c@:"} (e.g. {@code canBecomeKeyWindow}). */
    public interface BoolMethod extends Callback {
        byte invoke(Pointer self, Pointer cmd);
    }

    /** {@code -(void)sel:(id)a b:(id)c} — type encoding {@code "v@:@@"}. */
    public interface VoidObjObjMethod extends Callback {
        void invoke(Pointer self, Pointer cmd, Pointer a, Pointer b);
    }

    /** {@code -(BOOL)sel:(id)a b:(id)c} — type encoding {@code "c@:@@"}. */
    public interface BoolObjObjMethod extends Callback {
        byte invoke(Pointer self, Pointer cmd, Pointer a, Pointer b);
    }

    /** {@code -(id)sel:(id)arg} — type encoding {@code "@@:@"} (e.g. {@code applicationDockMenu:}). */
    public interface ObjObjMethod extends Callback {
        Pointer invoke(Pointer self, Pointer cmd, Pointer arg);
    }

    /**
     * {@code -(id)sel:(id)a b:(id)c d:(BOOL)e} — type encoding {@code "@@:@@c"}
     * (e.g. {@code toolbar:itemForItemIdentifier:willBeInsertedIntoToolbar:}).
     */
    public interface ObjObjObjBoolMethod extends Callback {
        Pointer invoke(Pointer self, Pointer cmd, Pointer a, Pointer b, byte c);
    }

    /**
     * {@code -(BOOL)sel:(id)a b:(id)c d:(id)e} — type encoding {@code "c@:@@@"}
     * (e.g. {@code application:continueUserActivity:restorationHandler:}).
     */
    public interface BoolObjObjObjMethod extends Callback {
        byte invoke(Pointer self, Pointer cmd, Pointer a, Pointer b, Pointer c);
    }

    /** The invoke function of a {@code void(^)(BOOL, NSError*)} block: first arg is the block itself. */
    public interface BoolErrorBlock extends Callback {
        void invoke(Pointer block, byte success, Pointer error);
    }

    /** The invoke function of a {@code void(^)(NSInteger)} block: first arg is the block itself. */
    public interface LongBlock extends Callback {
        void invoke(Pointer block, long value);
    }

    /** The invoke function of a {@code void(^)(BOOL)} block: first arg is the block itself. */
    public interface BoolBlock extends Callback {
        void invoke(Pointer block, byte value);
    }

    private ObjCRuntime() { }

    /** Loads a framework by dylib path so its classes/constants become available to the runtime. */
    public static void loadFramework(String path) {
        try {
            NativeLibrary.getInstance(path);
        } catch (Throwable t) {
            System.out.println("ObjCRuntime: failed to load framework " + path + ": " + t.getMessage());
        }
    }

    /** @return {@code true} if a class with this name is already registered. */
    public static boolean isRegistered(String className) {
        return isValid(cls(className));
    }

    /**
     * Allocates a new class pair (not yet registered). Add methods, then call
     * {@link #registerClass(Pointer)}. Returns the existing class if already registered.
     */
    public static Pointer allocateClass(String className, String superclassName) {
        Pointer existing = cls(className);
        if (isValid(existing)) return existing;
        Pointer superclass = cls(superclassName);
        return (Pointer) ALLOC_CLASS.invoke(Pointer.class, new Object[]{ superclass, className, 0L });
    }

    /**
     * Adds a method implemented by a JNA callback.
     *
     * @param types Objective-C type encoding, e.g. {@code "v@:@"} for {@code -(void)foo:(id)x}.
     */
    public static void addMethod(Pointer klass, String selectorName, Callback impl, String types) {
        KEEP_ALIVE.add(impl);
        ADD_METHOD.invoke(Boolean.class, new Object[]{ klass, sel(selectorName), impl, types });
    }

    public static void registerClass(Pointer klass) {
        REGISTER_CLASS.invoke(Void.class, new Object[]{ klass });
    }

    /** {@code [[klass alloc] init]}. */
    public static Pointer newInstance(Pointer klass) {
        return msg(msg(klass, sel("alloc")), sel("init"));
    }

    /** Returns the class of an Objective-C instance ({@code object_getClass}). */
    public static Pointer classOf(Pointer instance) {
        return (Pointer) OBJECT_GET_CLASS.invoke(Pointer.class, new Object[]{ instance });
    }

    /**
     * Builds a global Objective-C block wrapping {@code impl}. The block captures nothing,
     * so a global block (whose copy/dispose are no-ops) is safe and never freed.
     */
    public static Pointer createBoolErrorBlock(BoolErrorBlock impl) {
        return createGlobalBlock(impl);
    }

    /**
     * Builds a global Objective-C block wrapping any callback whose first parameter is the
     * block itself. The block captures nothing, so a global block (whose copy/dispose are
     * no-ops) is safe and never freed.
     */
    public static Pointer createGlobalBlock(Callback impl) {
        KEEP_ALIVE.add(impl);
        if (globalBlockIsa == null) {
            globalBlockIsa = NativeLibrary.getInstance("System").getGlobalVariableAddress("_NSConcreteGlobalBlock");
        }
        Pointer invoke = CallbackReference.getFunctionPointer(impl);

        // struct Block_descriptor { unsigned long reserved; unsigned long size; }
        Memory descriptor = new Memory(16);
        descriptor.setLong(0, 0L);
        descriptor.setLong(8, 32L); // sizeof(Block_literal)

        // struct Block_literal { void *isa; int flags; int reserved; void *invoke; void *descriptor; }
        Memory block = new Memory(32);
        block.setPointer(0, globalBlockIsa);
        block.setInt(8, 1 << 28); // BLOCK_IS_GLOBAL
        block.setInt(12, 0);
        block.setPointer(16, invoke);
        block.setPointer(24, descriptor);

        KEEP_ALIVE.add(descriptor);
        KEEP_ALIVE.add(block);
        return block;
    }
}
