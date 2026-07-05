package evo.developers.ru.macios.effects;

import com.sun.jna.Pointer;
import evo.developers.ru.macios.base.Manager;
import evo.developers.ru.macios.core.ObjC;
import javafx.animation.AnimationTimer;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.stage.Stage;

import static evo.developers.ru.macios.core.ObjC.*;

/**
 * Real macOS 26 "Liquid Glass" ({@code NSGlassEffectView}) rendered as a borderless child
 * {@code NSWindow} overlaid on top of a JavaFX node, so it genuinely refracts what's behind
 * it (unlike a fake in-scene blur). Falls back to {@code NSVisualEffectView} on macOS &lt; 26.
 *
 * <p>The overlay ignores mouse events, so clicks pass through to the JavaFX content beneath.
 * Attach it to an (empty) placeholder node whose on-screen bounds define the glass region.</p>
 *
 * <pre>{@code
 * helper.getLiquidGlass().attach(glassPlaceholderRegion);
 * // ...
 * helper.getLiquidGlass().detach();
 * }</pre>
 */
public class LiquidGlassManager extends Manager {

    /** {@code NSBackingStoreBuffered}. */
    private static final long BACKING_BUFFERED = 2;
    /** {@code NSWindowAbove}. */
    private static final long ORDER_ABOVE = 1;
    /** {@code NSVisualEffectBlendingModeBehindWindow}. */
    private static final long BLENDING_BEHIND_WINDOW = 0;
    /** {@code NSVisualEffectStateActive}. */
    private static final long STATE_ACTIVE = 1;

    private Pointer glassWindow;
    private Pointer glassContainer;
    private Pointer glassView;
    private Node attachedNode;
    private Node clipNode;
    private AnimationTimer syncTimer;
    private boolean usingNativeGlass;
    private boolean hidden;

    private double cornerRadius = 18;
    private Material fallbackMaterial = Material.UNDER_WINDOW;

    private boolean lightAppearance = true;
    private boolean hasTint = false;
    private double tintR, tintG, tintB, tintA;

    public LiquidGlassManager(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
    }

    /** @return {@code true} if the OS provides {@code NSGlassEffectView} (macOS 26+). */
    public boolean isNativeGlassSupported() {
        return isValid(cls("NSGlassEffectView"));
    }

    /** @return {@code true} if the real glass view is in use (vs. the blur fallback). */
    public boolean isUsingNativeGlass() {
        return usingNativeGlass;
    }

    public LiquidGlassManager setCornerRadius(double radius) {
        this.cornerRadius = radius;
        return this;
    }

    /** Material used only for the {@code NSVisualEffectView} fallback. */
    public LiquidGlassManager setFallbackMaterial(Material material) {
        if (material != null) this.fallbackMaterial = material;
        return this;
    }

    /**
     * Forces a light ({@code Aqua}) appearance on the glass so it reads as bright/frosty even
     * when the app runs in dark mode. Defaults to {@code true}. Set {@code false} to inherit the
     * system appearance. Applied on the next {@link #attach(Node)}.
     */
    public LiquidGlassManager setLightAppearance(boolean light) {
        this.lightAppearance = light;
        return this;
    }

    /**
     * Sets a translucent tint blended into the glass (all components 0..1). A low alpha
     * (e.g. {@code 0.10}) keeps it glassy. Applied on the next {@link #attach(Node)}.
     */
    public LiquidGlassManager setTint(double r, double g, double b, double a) {
        this.hasTint = true;
        this.tintR = r; this.tintG = g; this.tintB = b; this.tintA = a;
        return this;
    }

    /** Attaches the glass overlay so it tracks {@code node}'s on-screen bounds. */
    public void attach(Node node) {
        attach(node, null);
    }

    /**
     * Attaches the glass overlay tracking {@code node}, clipped to {@code clip}'s on-screen
     * bounds (e.g. a {@code ScrollPane}'s viewport) so it never spills outside a scroll region.
     * The overlay is always additionally clipped to the window's content area.
     */
    public void attach(Node node, Node clip) {
        runFx(() -> {
            try {
                Pointer parent = window();
                if (parent == null || node == null) return;
                detachInternal();

                createGlassWindow(parent);
                attachedNode = node;
                clipNode = clip;
                syncFrame(true);
                startSync();
            } catch (Throwable t) {
                System.out.println("LiquidGlass attach error: " + t.getMessage());
            }
        });
    }

    /** Removes the glass overlay. */
    public void detach() {
        runFx(this::detachInternal);
    }

    private void createGlassWindow(Pointer parent) {
        ObjC.CGRect rect = new ObjC.CGRect(0, 0, 10, 10);
        Pointer w = msg(msg(cls("NSWindow"), sel("alloc")),
                sel("initWithContentRect:styleMask:backing:defer:"),
                rect, 0L, BACKING_BUFFERED, 0L);

        msg(w, sel("setOpaque:"), 0L);
        msg(w, sel("setBackgroundColor:"), msg(cls("NSColor"), sel("clearColor")));
        msg(w, sel("setHasShadow:"), 0L);
        msg(w, sel("setIgnoresMouseEvents:"), 1L);

        if (lightAppearance) {
            Pointer appearance = msg(cls("NSAppearance"), sel("appearanceNamed:"),
                    nsString("NSAppearanceNameAqua"));
            if (isValid(appearance)) msg(w, sel("setAppearance:"), appearance);
        }

        Pointer glassClass = cls("NSGlassEffectView");
        if (isValid(glassClass)) {
            glassView = msg(msg(glassClass, sel("alloc")), sel("init"));
            usingNativeGlass = true;
            if (msgLong(glassView, sel("respondsToSelector:"), sel("setCornerRadius:")) != 0) {
                msg(glassView, sel("setCornerRadius:"), cornerRadius);
            }
            if (hasTint && msgLong(glassView, sel("respondsToSelector:"), sel("setTintColor:")) != 0) {
                msg(glassView, sel("setTintColor:"), tintColor());
            }
        } else {
            glassClass = cls("NSVisualEffectView");
            glassView = msg(msg(glassClass, sel("alloc")), sel("init"));
            usingNativeGlass = false;
            msg(glassView, sel("setBlendingMode:"), BLENDING_BEHIND_WINDOW);
            msg(glassView, sel("setState:"), STATE_ACTIVE);
            msg(glassView, sel("setMaterial:"), fallbackMaterial.value);
            msg(glassView, sel("setWantsLayer:"), 1L);
            Pointer layer = msg(glassView, sel("layer"));
            if (isValid(layer)) {
                msg(layer, sel("setCornerRadius:"), cornerRadius);
                msg(layer, sel("setMasksToBounds:"), 1L);
                if (hasTint) msg(layer, sel("setBackgroundColor:"), msg(tintColor(), sel("CGColor")));
            }
        }

        // Clipping container: the window backing (sized to the visible rect) clips the glass view,
        // which keeps its full anchor size and is only offset — so scrolling clips, not resizes.
        glassContainer = msg(msg(cls("NSView"), sel("alloc")), sel("initWithFrame:"), rect);
        msg(glassContainer, sel("setWantsLayer:"), 1L);
        Pointer clayer = msg(glassContainer, sel("layer"));
        if (isValid(clayer)) msg(clayer, sel("setMasksToBounds:"), 1L);

        msg(glassContainer, sel("addSubview:"), glassView);
        msg(w, sel("setContentView:"), glassContainer);
        msg(parent, sel("addChildWindow:ordered:"), w, ORDER_ABOVE);
        msg(w, sel("retain"));
        glassWindow = w;
    }

    private Pointer tintColor() {
        return msg(cls("NSColor"), sel("colorWithSRGBRed:green:blue:alpha:"),
                tintR, tintG, tintB, tintA);
    }

    private double[] lastRect;

    private void syncFrame(boolean force) {
        if (!isValid(glassWindow) || attachedNode == null) return;
        Pointer parent = window();
        if (parent == null) return;
        Pointer contentView = msg(parent, sel("contentView"));
        if (!isValid(contentView)) return;

        double[] full = screenRect(attachedNode, parent, contentView);
        if (full == null) { hide(); return; }

        // Always clip to the window's content area so the overlay never spills over the titlebar
        // or outside the window (e.g. when the node scrolls out of view).
        ObjC.CGRect content = msgRect(parent, sel("contentRectForFrameRect:"),
                msgRect(parent, sel("frame")));
        double[] vis = intersect(full, new double[]{ content.x, content.y, content.width, content.height });

        // Optional caller-provided clip region (e.g. a ScrollPane viewport).
        if (vis != null && clipNode != null) {
            vis = intersect(vis, screenRect(clipNode, parent, contentView));
        }

        if (vis == null) { hide(); return; }

        double vx = vis[0], vy = vis[1], vw = vis[2], vh = vis[3];
        double fw = full[2], fh = full[3];
        double offX = full[0] - vx, offY = full[1] - vy;
        show();

        if (!force && lastRect != null
                && lastRect[0] == vx && lastRect[1] == vy && lastRect[2] == vw && lastRect[3] == vh
                && lastRect[4] == offX && lastRect[5] == offY && lastRect[6] == fw && lastRect[7] == fh) {
            return;
        }
        lastRect = new double[]{ vx, vy, vw, vh, offX, offY, fw, fh };

        // Window covers only the visible region; the glass view keeps its full size and is offset
        // inside the container, so the overflow (and rounded corners) are clipped, not squeezed.
        msg(glassWindow, sel("setFrame:display:"), new ObjC.CGRect(vx, vy, vw, vh), 1L);
        if (isValid(glassView)) {
            msg(glassView, sel("setFrame:"), new ObjC.CGRect(offX, offY, fw, fh));
        }
    }

    /**
     * @return the on-screen (AppKit, bottom-left origin) rect of {@code node} as
     *         {@code {x, y, w, h}}, or {@code null} if it has no positive size.
     */
    private double[] screenRect(Node node, Pointer parent, Pointer contentView) {
        if (node == null) return null;
        Bounds b = node.localToScene(node.getBoundsInLocal());
        if (b == null || b.getWidth() <= 0 || b.getHeight() <= 0) return null;

        double w = b.getWidth();
        double h = b.getHeight();
        // The JavaFX scene fills the (flipped) content view, so a scene point maps 1:1 onto it.
        // Bottom-left corner of the node in flipped content-view coords -> window base -> screen.
        ObjC.CGPoint viewPt = new ObjC.CGPoint(b.getMinX(), b.getMinY() + h);
        ObjC.CGPoint winPt = msgPoint(contentView, sel("convertPoint:toView:"), viewPt, Pointer.NULL);
        ObjC.CGPoint screenPt = msgPoint(parent, sel("convertPointToScreen:"), winPt);
        return new double[]{ screenPt.x, screenPt.y, w, h };
    }

    /** Intersects two {@code {x, y, w, h}} rects; returns {@code null} if they don't overlap. */
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
        if (hidden && isValid(glassWindow)) {
            msg(glassWindow, sel("setAlphaValue:"), 1.0);
            hidden = false;
        }
    }

    private void hide() {
        if (!hidden && isValid(glassWindow)) {
            msg(glassWindow, sel("setAlphaValue:"), 0.0);
            hidden = true;
        }
    }

    private void startSync() {
        stopSync();
        syncTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                syncFrame(false);
            }
        };
        syncTimer.start();
    }

    private void stopSync() {
        if (syncTimer != null) {
            syncTimer.stop();
            syncTimer = null;
        }
    }

    private void detachInternal() {
        stopSync();
        if (isValid(glassWindow)) {
            Pointer parent = window();
            if (parent != null) msg(parent, sel("removeChildWindow:"), glassWindow);
            msg(glassWindow, sel("orderOut:"), Pointer.NULL);
            msg(glassWindow, sel("release"));
        }
        glassWindow = null;
        glassContainer = null;
        glassView = null;
        attachedNode = null;
        clipNode = null;
        lastRect = null;
        hidden = false;
    }
}
