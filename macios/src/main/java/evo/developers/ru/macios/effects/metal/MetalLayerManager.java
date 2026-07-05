package evo.developers.ru.macios.effects.metal;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import evo.developers.ru.macios.base.Manager;
import evo.developers.ru.macios.core.ObjC;
import evo.developers.ru.macios.core.ObjCRuntime;
import javafx.animation.AnimationTimer;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.stage.Stage;

import java.util.Arrays;
import java.util.List;

import static evo.developers.ru.macios.core.ObjC.*;

/**
 * Renders a real Metal ({@code CAMetalLayer}) surface in a borderless child {@code NSWindow}
 * overlaid on a JavaFX node. Ships with an animated clear-color render loop; override
 * {@link #render(Pointer, Pointer, double)} to drive your own Metal pipeline.
 *
 * <p>Uses the same anchor/clip/positioning approach as the Liquid Glass overlay: the surface
 * tracks the node's on-screen bounds, is clipped to the window content area (and an optional
 * clip node), and ignores mouse events so clicks pass through.</p>
 *
 * <pre>{@code
 * helper.getMetal().attach(anchorRegion, scrollPane);
 * // ...
 * helper.getMetal().detach();
 * }</pre>
 */
public class MetalLayerManager extends Manager {

    private static final long BACKING_BUFFERED = 2;
    private static final long ORDER_ABOVE = 1;
    private static final long PIXEL_FORMAT_BGRA8UNORM = 80;
    private static final long LOAD_ACTION_CLEAR = 2;
    private static final long STORE_ACTION_STORE = 1;

    private static Function createSystemDefaultDevice;
    private static volatile boolean metalLoaded;

    private Pointer overlayWindow;
    private Pointer metalLayer;
    private Pointer device;
    private Pointer commandQueue;
    private Node anchor;
    private Node clip;
    private AnimationTimer timer;
    private boolean hidden;
    private double scale = 2.0;
    private long startNanos;

    public MetalLayerManager(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
    }

    /** @return {@code true} if a Metal-capable GPU/device is available. */
    public boolean isMetalSupported() {
        return ensureDevice() != null;
    }

    /** Attaches the Metal surface so it tracks {@code node}'s on-screen bounds. */
    public void attach(Node node) {
        attach(node, null);
    }

    /** Attaches tracking {@code node}, additionally clipped to {@code clipNode}'s bounds. */
    public void attach(Node node, Node clipNode) {
        runFx(() -> {
            try {
                Pointer parent = window();
                if (parent == null || node == null) return;
                if (ensureDevice() == null) {
                    System.out.println("Metal: no system default device available");
                    return;
                }
                detachInternal();
                createWindow(parent);
                this.anchor = node;
                this.clip = clipNode;
                this.startNanos = System.nanoTime();
                syncFrame(true);
                startLoop();
            } catch (Throwable t) {
                System.out.println("Metal attach error: " + t.getMessage());
            }
        });
    }

    public void detach() {
        runFx(this::detachInternal);
    }

    // ---- Rendering ----------------------------------------------------------

    /**
     * Renders one frame. Default: an animated clear color (hue cycling). Override for a custom
     * pipeline.
     *
     * @param drawable the {@code CAMetalDrawable} for this frame
     * @param texture  the drawable's color texture
     * @param seconds  elapsed seconds since attach
     */
    protected void render(Pointer drawable, Pointer texture, double seconds) {
        float[] rgb = hsvToRgb((float) ((seconds * 0.12) % 1.0), 0.65f, 0.95f);
        MTLClearColor color = new MTLClearColor(rgb[0], rgb[1], rgb[2], 0.9);

        Pointer desc = msg(cls("MTLRenderPassDescriptor"), sel("renderPassDescriptor"));
        Pointer attachments = msg(desc, sel("colorAttachments"));
        Pointer att0 = msg(attachments, sel("objectAtIndexedSubscript:"), 0L);
        msg(att0, sel("setTexture:"), texture);
        msg(att0, sel("setLoadAction:"), LOAD_ACTION_CLEAR);
        msg(att0, sel("setStoreAction:"), STORE_ACTION_STORE);
        msg(att0, sel("setClearColor:"), color);

        Pointer commandBuffer = msg(commandQueue, sel("commandBuffer"));
        Pointer encoder = msg(commandBuffer, sel("renderCommandEncoderWithDescriptor:"), desc);
        msg(encoder, sel("endEncoding"));
        msg(commandBuffer, sel("presentDrawable:"), drawable);
        msg(commandBuffer, sel("commit"));
    }

    private void renderFrame() {
        if (!isValid(metalLayer) || !isValid(commandQueue)) return;
        Pointer drawable = msg(metalLayer, sel("nextDrawable"));
        if (!isValid(drawable)) return;
        Pointer texture = msg(drawable, sel("texture"));
        if (!isValid(texture)) return;
        double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
        render(drawable, texture, seconds);
    }

    // ---- Native window / layer ----------------------------------------------

    private void createWindow(Pointer parent) {
        ObjC.CGRect rect = new ObjC.CGRect(0, 0, 10, 10);
        Pointer w = msg(msg(cls("NSWindow"), sel("alloc")),
                sel("initWithContentRect:styleMask:backing:defer:"),
                rect, 0L, BACKING_BUFFERED, 0L);
        msg(w, sel("setOpaque:"), 0L);
        msg(w, sel("setBackgroundColor:"), msg(cls("NSColor"), sel("clearColor")));
        msg(w, sel("setHasShadow:"), 0L);
        msg(w, sel("setIgnoresMouseEvents:"), 1L);

        scale = Math.max(1.0, msgDouble(parent, sel("backingScaleFactor")));

        metalLayer = msg(msg(cls("CAMetalLayer"), sel("alloc")), sel("init"));
        msg(metalLayer, sel("setDevice:"), device);
        msg(metalLayer, sel("setPixelFormat:"), PIXEL_FORMAT_BGRA8UNORM);
        msg(metalLayer, sel("setFramebufferOnly:"), 1L);
        msg(metalLayer, sel("setOpaque:"), 0L);
        msg(metalLayer, sel("setContentsScale:"), scale);

        Pointer view = msg(msg(cls("NSView"), sel("alloc")), sel("initWithFrame:"), rect);
        msg(view, sel("setLayer:"), metalLayer);
        msg(view, sel("setWantsLayer:"), 1L);

        msg(w, sel("setContentView:"), view);
        msg(parent, sel("addChildWindow:ordered:"), w, ORDER_ABOVE);
        msg(w, sel("retain"));
        overlayWindow = w;

        commandQueue = msg(device, sel("newCommandQueue"));
    }

    // ---- Positioning / clipping ---------------------------------------------

    private double[] lastRect;

    private void syncFrame(boolean force) {
        if (!isValid(overlayWindow) || anchor == null) return;
        Pointer parent = window();
        if (parent == null) return;
        Pointer contentView = msg(parent, sel("contentView"));
        if (!isValid(contentView)) return;

        double[] rect = screenRect(anchor, parent, contentView);
        if (rect == null) { hide(); return; }

        ObjC.CGRect content = msgRect(parent, sel("contentRectForFrameRect:"),
                msgRect(parent, sel("frame")));
        rect = intersect(rect, new double[]{ content.x, content.y, content.width, content.height });
        if (rect != null && clip != null) rect = intersect(rect, screenRect(clip, parent, contentView));
        if (rect == null) { hide(); return; }

        double x = rect[0], y = rect[1], w = rect[2], h = rect[3];
        show();

        if (force || lastRect == null
                || lastRect[0] != x || lastRect[1] != y || lastRect[2] != w || lastRect[3] != h) {
            lastRect = new double[]{ x, y, w, h };
            msg(overlayWindow, sel("setFrame:display:"), new ObjC.CGRect(x, y, w, h), 1L);
            if (isValid(metalLayer)) {
                msg(metalLayer, sel("setDrawableSize:"), new CGSize(w * scale, h * scale));
            }
        }
    }

    private double[] screenRect(Node node, Pointer parent, Pointer contentView) {
        if (node == null) return null;
        Bounds b = node.localToScene(node.getBoundsInLocal());
        if (b == null || b.getWidth() <= 0 || b.getHeight() <= 0) return null;
        double w = b.getWidth();
        double h = b.getHeight();
        ObjC.CGPoint viewPt = new ObjC.CGPoint(b.getMinX(), b.getMinY() + h);
        ObjC.CGPoint winPt = msgPoint(contentView, sel("convertPoint:toView:"), viewPt, Pointer.NULL);
        ObjC.CGPoint screenPt = msgPoint(parent, sel("convertPointToScreen:"), winPt);
        return new double[]{ screenPt.x, screenPt.y, w, h };
    }

    private static double[] intersect(double[] a, double[] b) {
        if (a == null || b == null) return null;
        double x1 = Math.max(a[0], b[0]);
        double y1 = Math.max(a[1], b[1]);
        double x2 = Math.min(a[0] + a[2], b[0] + b[2]);
        double y2 = Math.min(a[1] + a[3], b[1] + b[3]);
        if (x2 <= x1 || y2 <= y1) return null;
        return new double[]{ x1, y1, x2 - x1, y2 - y1 };
    }

    private void show() {
        if (hidden && isValid(overlayWindow)) {
            msg(overlayWindow, sel("setAlphaValue:"), 1.0);
            hidden = false;
        }
    }

    private void hide() {
        if (!hidden && isValid(overlayWindow)) {
            msg(overlayWindow, sel("setAlphaValue:"), 0.0);
            hidden = true;
        }
    }

    private void startLoop() {
        stopLoop();
        timer = new AnimationTimer() {
            @Override public void handle(long now) {
                syncFrame(false);
                renderFrame();
            }
        };
        timer.start();
    }

    private void stopLoop() {
        if (timer != null) { timer.stop(); timer = null; }
    }

    private void detachInternal() {
        stopLoop();
        if (isValid(overlayWindow)) {
            Pointer parent = window();
            if (parent != null) msg(parent, sel("removeChildWindow:"), overlayWindow);
            msg(overlayWindow, sel("orderOut:"), Pointer.NULL);
            msg(overlayWindow, sel("release"));
        }
        overlayWindow = null;
        metalLayer = null;
        commandQueue = null;
        anchor = null;
        clip = null;
        lastRect = null;
        hidden = false;
    }

    // ---- Metal device ---------------------------------------------------------

    private Pointer ensureDevice() {
        if (isValid(device)) return device;
        ensureMetal();
        if (createSystemDefaultDevice == null) return null;
        try {
            Object d = createSystemDefaultDevice.invoke(Pointer.class, new Object[]{});
            device = (Pointer) d;
            return isValid(device) ? device : null;
        } catch (Throwable t) {
            System.out.println("Metal: MTLCreateSystemDefaultDevice failed: " + t.getMessage());
            return null;
        }
    }

    private static synchronized void ensureMetal() {
        if (metalLoaded) return;
        ObjCRuntime.loadFramework("/System/Library/Frameworks/QuartzCore.framework/QuartzCore");
        try {
            NativeLibrary metal = NativeLibrary.getInstance(
                    "/System/Library/Frameworks/Metal.framework/Metal");
            createSystemDefaultDevice = metal.getFunction("MTLCreateSystemDefaultDevice");
        } catch (Throwable t) {
            System.out.println("Metal: failed to load Metal.framework: " + t.getMessage());
        }
        metalLoaded = true;
    }

    private static float[] hsvToRgb(float h, float s, float v) {
        int i = (int) (h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);
        switch (i % 6) {
            case 0:  return new float[]{ v, t, p };
            case 1:  return new float[]{ q, v, p };
            case 2:  return new float[]{ p, v, t };
            case 3:  return new float[]{ p, q, v };
            case 4:  return new float[]{ t, p, v };
            default: return new float[]{ v, p, q };
        }
    }

    // ---- Value structs --------------------------------------------------------

    /** {@code MTLClearColor} (red, green, blue, alpha as doubles). */
    public static class MTLClearColor extends Structure implements Structure.ByValue {
        public double red, green, blue, alpha;
        public MTLClearColor() { }
        public MTLClearColor(double r, double g, double b, double a) {
            red = r; green = g; blue = b; alpha = a;
        }
        @Override protected List<String> getFieldOrder() {
            return Arrays.asList("red", "green", "blue", "alpha");
        }
    }

    /** {@code CGSize} (width, height as doubles). */
    public static class CGSize extends Structure implements Structure.ByValue {
        public double width, height;
        public CGSize() { }
        public CGSize(double w, double h) { width = w; height = h; }
        @Override protected List<String> getFieldOrder() {
            return Arrays.asList("width", "height");
        }
    }
}
