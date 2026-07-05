package evo.developers.ru.macios.ux.web;

import com.sun.jna.Pointer;
import evo.developers.ru.macios.base.Manager;
import evo.developers.ru.macios.core.ObjC;
import evo.developers.ru.macios.core.ObjCRuntime;
import javafx.animation.AnimationTimer;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.stage.Stage;

import static evo.developers.ru.macios.core.ObjC.*;

/**
 * Hosts a real Apple {@code WKWebView} in a borderless child {@code NSWindow} overlaid on a
 * JavaFX node. Built by analogy with {@link evo.developers.ru.macios.effects.metal.MetalLayerManager}:
 * the web view tracks the node's on-screen bounds, is clipped to the window content area (and an
 * optional clip node), and follows scrolling/resizing via an {@link AnimationTimer}.
 *
 * <p>Unlike the Metal overlay, the web view is <b>interactive</b> — it receives mouse events
 * (clicks, scrolling) and keyboard input: it is hosted in a borderless {@code NSWindow} subclass
 * whose {@code canBecomeKeyWindow} returns YES, so page inputs (text fields, etc.) are focusable.
 * When scrolled, the view is clipped (not resized): the window covers only the visible region
 * while the web view keeps its full anchor size, offset inside a clipping container.</p>
 *
 * <pre>{@code
 * WebViewManager web = helper.getWebView();
 * web.attach(anchorRegion, scrollPane); // clip optional
 * web.loadUrl("https://www.apple.com");
 * // ...
 * web.detach();
 * }</pre>
 */
public class WebViewManager extends Manager {

    private static final long BACKING_BUFFERED = 2;
    private static final long ORDER_ABOVE = 1;
    /** {@code NSWindowStyleMaskBorderless} = 0; we still want a key-capable window for input. */
    private static final String KEYABLE_WINDOW_CLASS = "JFXKeyableWindow";

    private static volatile boolean webKitLoaded;
    private static volatile boolean keyableClassReady;

    private Pointer overlayWindow;
    private Pointer container;
    private Pointer webView;
    private Node anchor;
    private Node clip;
    private AnimationTimer timer;
    private boolean hidden;

    private String pendingUrl;
    private String pendingHtml;

    public WebViewManager(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
    }

    /** @return {@code true} if {@code WKWebView} is available on this system. */
    public boolean isSupported() {
        ensureWebKit();
        return isValid(cls("WKWebView"));
    }

    /** Attaches the web view so it tracks {@code node}'s on-screen bounds. */
    public void attach(Node node) {
        attach(node, null);
    }

    /** Attaches tracking {@code node}, additionally clipped to {@code clipNode}'s bounds. */
    public void attach(Node node, Node clipNode) {
        runFx(() -> {
            try {
                Pointer parent = window();
                if (parent == null || node == null) return;
                if (!isSupported()) {
                    System.out.println("WebView: WKWebView not available");
                    return;
                }
                detachInternal();
                createWindow(parent);
                this.anchor = node;
                this.clip = clipNode;
                syncFrame(true);
                startLoop();

                if (pendingUrl != null) { loadUrlInternal(pendingUrl); pendingUrl = null; }
                else if (pendingHtml != null) { loadHtmlInternal(pendingHtml); pendingHtml = null; }
            } catch (Throwable t) {
                System.out.println("WebView attach error: " + t.getMessage());
            }
        });
    }

    public void detach() {
        runFx(this::detachInternal);
    }

    // ---- Navigation ----------------------------------------------------------

    /** Loads a remote URL (attach first, or it is applied on the next attach). */
    public void loadUrl(String url) {
        runFx(() -> {
            if (isValid(webView)) loadUrlInternal(url);
            else { pendingUrl = url; pendingHtml = null; }
        });
    }

    /** Loads an HTML string directly. */
    public void loadHtml(String html) {
        runFx(() -> {
            if (isValid(webView)) loadHtmlInternal(html);
            else { pendingHtml = html; pendingUrl = null; }
        });
    }

    public void reload() {
        runFx(() -> { if (isValid(webView)) msg(webView, sel("reload")); });
    }

    public void goBack() {
        runFx(() -> { if (isValid(webView)) msg(webView, sel("goBack")); });
    }

    public void goForward() {
        runFx(() -> { if (isValid(webView)) msg(webView, sel("goForward")); });
    }

    private void loadUrlInternal(String url) {
        Pointer nsUrl = msg(cls("NSURL"), sel("URLWithString:"), nsString(url));
        if (!isValid(nsUrl)) { System.out.println("WebView: invalid URL " + url); return; }
        Pointer request = msg(cls("NSURLRequest"), sel("requestWithURL:"), nsUrl);
        msg(webView, sel("loadRequest:"), request);
    }

    private void loadHtmlInternal(String html) {
        msg(webView, sel("loadHTMLString:baseURL:"), nsString(html), Pointer.NULL);
    }

    // ---- Native window / view -------------------------------------------------

    private void createWindow(Pointer parent) {
        ObjC.CGRect rect = new ObjC.CGRect(0, 0, 10, 10);
        Pointer windowClass = ensureKeyableWindowClass();
        Pointer w = msg(msg(windowClass, sel("alloc")),
                sel("initWithContentRect:styleMask:backing:defer:"),
                rect, 0L, BACKING_BUFFERED, 0L);
        msg(w, sel("setOpaque:"), 0L);
        msg(w, sel("setBackgroundColor:"), msg(cls("NSColor"), sel("clearColor")));
        msg(w, sel("setHasShadow:"), 0L);

        // Clipping container: the window backing (sized to the visible rect) clips the web view,
        // which keeps its full anchor size and is only offset — so scrolling clips, not resizes.
        container = msg(msg(cls("NSView"), sel("alloc")), sel("initWithFrame:"), rect);
        msg(container, sel("setWantsLayer:"), 1L);
        Pointer clayer = msg(container, sel("layer"));
        if (isValid(clayer)) msg(clayer, sel("setMasksToBounds:"), 1L);

        Pointer config = msg(msg(cls("WKWebViewConfiguration"), sel("alloc")), sel("init"));
        webView = msg(msg(cls("WKWebView"), sel("alloc")),
                sel("initWithFrame:configuration:"), rect, config);
        msg(config, sel("release"));

        msg(container, sel("addSubview:"), webView);
        msg(w, sel("setContentView:"), container);
        msg(parent, sel("addChildWindow:ordered:"), w, ORDER_ABOVE);
        msg(w, sel("retain"));
        overlayWindow = w;
    }

    /** Borderless {@code NSWindow} subclass whose {@code canBecomeKeyWindow} returns YES, so the
     *  hosted web view can receive keyboard input (text fields, etc.). */
    private static synchronized Pointer ensureKeyableWindowClass() {
        Pointer existing = cls(KEYABLE_WINDOW_CLASS);
        if (keyableClassReady && isValid(existing)) return existing;
        if (isValid(existing)) { keyableClassReady = true; return existing; }

        Pointer klass = ObjCRuntime.allocateClass(KEYABLE_WINDOW_CLASS, "NSWindow");
        ObjCRuntime.BoolMethod yes = (self, cmd) -> (byte) 1;
        ObjCRuntime.addMethod(klass, "canBecomeKeyWindow", yes, "c@:");
        ObjCRuntime.addMethod(klass, "canBecomeMainWindow", yes, "c@:");
        ObjCRuntime.registerClass(klass);
        keyableClassReady = true;
        return cls(KEYABLE_WINDOW_CLASS);
    }

    // ---- Positioning / clipping ----------------------------------------------

    private double[] lastRect;

    private void syncFrame(boolean force) {
        if (!isValid(overlayWindow) || anchor == null) return;
        Pointer parent = window();
        if (parent == null) return;
        Pointer contentView = msg(parent, sel("contentView"));
        if (!isValid(contentView)) return;

        double[] full = screenRect(anchor, parent, contentView);
        if (full == null) { hide(); return; }

        ObjC.CGRect content = msgRect(parent, sel("contentRectForFrameRect:"),
                msgRect(parent, sel("frame")));
        double[] vis = intersect(full, new double[]{ content.x, content.y, content.width, content.height });
        if (vis != null && clip != null) vis = intersect(vis, screenRect(clip, parent, contentView));
        if (vis == null) { hide(); return; }

        double vx = vis[0], vy = vis[1], vw = vis[2], vh = vis[3];
        double fw = full[2], fh = full[3];
        double offX = full[0] - vx, offY = full[1] - vy;
        show();

        if (force || lastRect == null
                || lastRect[0] != vx || lastRect[1] != vy || lastRect[2] != vw || lastRect[3] != vh
                || lastRect[4] != offX || lastRect[5] != offY || lastRect[6] != fw || lastRect[7] != fh) {
            lastRect = new double[]{ vx, vy, vw, vh, offX, offY, fw, fh };
            // Window covers only the visible region; the web view keeps its full size and is
            // offset inside the container, so the overflow is clipped (not squeezed).
            msg(overlayWindow, sel("setFrame:display:"), new ObjC.CGRect(vx, vy, vw, vh), 1L);
            if (isValid(webView)) {
                msg(webView, sel("setFrame:"), new ObjC.CGRect(offX, offY, fw, fh));
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
            }
        };
        timer.start();
    }

    private void stopLoop() {
        if (timer != null) { timer.stop(); timer = null; }
    }

    private void detachInternal() {
        stopLoop();
        if (isValid(webView)) {
            msg(webView, sel("stopLoading"));
        }
        if (isValid(overlayWindow)) {
            Pointer parent = window();
            if (parent != null) msg(parent, sel("removeChildWindow:"), overlayWindow);
            msg(overlayWindow, sel("orderOut:"), Pointer.NULL);
            msg(overlayWindow, sel("release"));
        }
        overlayWindow = null;
        container = null;
        webView = null;
        anchor = null;
        clip = null;
        lastRect = null;
        hidden = false;
    }

    // ---- Framework loading ----------------------------------------------------

    private static synchronized void ensureWebKit() {
        if (webKitLoaded) return;
        ObjCRuntime.loadFramework("/System/Library/Frameworks/WebKit.framework/WebKit");
        webKitLoaded = true;
    }
}
