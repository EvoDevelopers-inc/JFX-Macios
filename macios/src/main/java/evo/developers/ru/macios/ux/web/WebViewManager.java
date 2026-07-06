package evo.developers.ru.macios.ux.web;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import evo.developers.ru.macios.base.Manager;
import evo.developers.ru.macios.core.ObjC;
import evo.developers.ru.macios.core.ObjCRuntime;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.stage.Stage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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

    /** Runtime {@code WKNavigationDelegate} subclass name (built once, shared by all instances). */
    private static final String NAV_DELEGATE_CLASS = "JFXWebNavigationDelegate";

    private static volatile boolean webKitLoaded;
    private static volatile boolean keyableClassReady;

    private static volatile boolean cgLoaded;
    private static Function cgPathCreateMutable, cgPathMove, cgPathLine, cgPathArc, cgPathClose, cgPathRelease;

    private static Pointer navDelegateClass;
    private static ObjCRuntime.VoidObjObjMethod didStartCb;
    private static ObjCRuntime.VoidObjObjMethod didCommitCb;
    private static ObjCRuntime.VoidObjObjMethod didFinishCb;
    private static ObjCRuntime.VoidObjObjObjMethod didFailCb;
    private static ObjCRuntime.VoidObjObjObjMethod didFailProvisionalCb;
    /** Maps a native delegate pointer back to its owning manager. */
    private static final Map<Long, WebViewManager> NAV_INSTANCES = new ConcurrentHashMap<>();

    private Pointer overlayWindow;
    private Pointer container;
    private Pointer webView;
    private Pointer navDelegate;
    private Node anchor;
    private Node clip;
    private AnimationTimer timer;
    private boolean hidden;

    private String pendingUrl;
    private String pendingHtml;

    private volatile String currentUrl;
    private volatile String currentTitle;

    private final List<NavigationListener> navListeners = new CopyOnWriteArrayList<>();

    private double cornerRadius = 12;

    /** Per-corner mode: independent radii (screen-space top-left, top-right, bottom-right, bottom-left). */
    private boolean perCorner;
    private double radTL, radTR, radBR, radBL;
    private double lastMaskW = -1, lastMaskH = -1;

    /** Receives page-load lifecycle events (delivered on the JavaFX thread). */
    public interface NavigationListener {
        /** A main-frame navigation started for {@code url}. */
        default void onStarted(String url) { }
        /** The page content of {@code url} began arriving (committed). */
        default void onCommitted(String url) { }
        /** The main-frame navigation to {@code url} finished loading. */
        default void onFinished(String url) { }
        /** A navigation failed; {@code error} is the localized description (may be {@code null}). */
        default void onFailed(String url, String error) { }
    }

    /** Receives the result of {@link #evaluateJavaScript(String, JsCallback)} (on the JavaFX thread). */
    public interface JsCallback {
        /**
         * @param result stringified JavaScript result (may be {@code null})
         * @param error  localized error description, or {@code null} on success
         */
        void onResult(String result, String error);
    }

    public WebViewManager(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
    }

    /** @return {@code true} if {@code WKWebView} is available on this system. */
    public boolean isSupported() {
        ensureWebKit();
        return isValid(cls("WKWebView"));
    }

    /**
     * Sets a uniform corner radius (continuous "squircle" curve) on all four corners. Applied
     * immediately if attached, otherwise on the next {@link #attach(Node)}. {@code 0} = square.
     */
    public WebViewManager setCornerRadius(double radius) {
        this.cornerRadius = radius;
        this.perCorner = false;
        runFx(() -> { if (isValid(webView)) applyRounding(); });
        return this;
    }

    /**
     * Sets an independent radius for each corner (screen-space orientation). Backed by a
     * {@code CAShapeLayer} mask, so the corners can differ. Applied immediately if attached,
     * otherwise on the next {@link #attach(Node)}.
     */
    public WebViewManager setCornerRadii(double topLeft, double topRight, double bottomRight, double bottomLeft) {
        this.radTL = topLeft; this.radTR = topRight; this.radBR = bottomRight; this.radBL = bottomLeft;
        this.perCorner = true;
        runFx(() -> { if (isValid(webView)) applyRounding(); });
        return this;
    }

    private Pointer ensureLayer() {
        Pointer layer = msg(webView, sel("layer"));
        if (!isValid(layer)) {
            msg(webView, sel("setWantsLayer:"), 1L);
            layer = msg(webView, sel("layer"));
        }
        return isValid(layer) ? layer : null;
    }

    private void applyRounding() {
        Pointer layer = ensureLayer();
        if (layer == null) return;
        if (perCorner) {
            msg(layer, sel("setCornerRadius:"), 0.0);
            msg(layer, sel("setMasksToBounds:"), 1L);
            lastMaskW = -1; lastMaskH = -1;
            if (lastRect != null) rebuildMask(lastRect[6], lastRect[7]); // full width/height
        } else {
            msg(layer, sel("setMask:"), Pointer.NULL);
            msg(layer, sel("setCornerRadius:"), cornerRadius);
            if (msgLong(layer, sel("respondsToSelector:"), sel("setCornerCurve:")) != 0) {
                msg(layer, sel("setCornerCurve:"), nsString("continuous"));
            }
            msg(layer, sel("setMasksToBounds:"), 1L);
        }
    }

    /** Rebuilds the {@code CAShapeLayer} mask for the current view size (per-corner mode). */
    private void rebuildMask(double w, double h) {
        if (!isValid(webView) || w <= 0 || h <= 0) return;
        Pointer layer = ensureLayer();
        if (layer == null) return;
        ensureCG();
        if (cgPathCreateMutable == null) return;

        // Map screen-space corners to geometric corners of the (possibly flipped) layer.
        boolean flipped = msgLong(webView, sel("isFlipped")) != 0;
        double c00, cW0, cWH, c0H; // corners at (0,0), (w,0), (w,h), (0,h)
        if (flipped) { c00 = radTL; cW0 = radTR; cWH = radBR; c0H = radBL; }
        else         { c00 = radBL; cW0 = radBR; cWH = radTR; c0H = radTL; }

        Pointer path = buildRoundedPath(w, h, c00, cW0, cWH, c0H);
        Pointer shape = msg(msg(cls("CAShapeLayer"), sel("alloc")), sel("init"));
        msg(shape, sel("setFrame:"), new ObjC.CGRect(0, 0, w, h));
        msg(shape, sel("setPath:"), path);
        msg(layer, sel("setMask:"), shape);
        msg(shape, sel("release"));
        cgPathRelease.invoke(Void.class, new Object[]{ path });
        lastMaskW = w; lastMaskH = h;
    }

    private static Pointer buildRoundedPath(double w, double h,
                                            double r00, double rW0, double rWH, double r0H) {
        double max = Math.min(w, h) / 2.0;
        r00 = clamp(r00, max); rW0 = clamp(rW0, max); rWH = clamp(rWH, max); r0H = clamp(r0H, max);
        Pointer p = (Pointer) cgPathCreateMutable.invoke(Pointer.class, new Object[]{});
        cgMove(p, 0, r00);
        cgLine(p, 0, h - r0H);
        cgArc(p, 0, h, r0H, h, r0H);          // corner (0, h)
        cgLine(p, w - rWH, h);
        cgArc(p, w, h, w, h - rWH, rWH);       // corner (w, h)
        cgLine(p, w, rW0);
        cgArc(p, w, 0, w - rW0, 0, rW0);       // corner (w, 0)
        cgLine(p, r00, 0);
        cgArc(p, 0, 0, 0, r00, r00);           // corner (0, 0)
        cgPathClose.invoke(Void.class, new Object[]{ p });
        return p;
    }

    private static double clamp(double r, double max) {
        return Math.max(0, Math.min(r, max));
    }

    private static void cgMove(Pointer p, double x, double y) {
        cgPathMove.invoke(Void.class, new Object[]{ p, Pointer.NULL, x, y });
    }

    private static void cgLine(Pointer p, double x, double y) {
        cgPathLine.invoke(Void.class, new Object[]{ p, Pointer.NULL, x, y });
    }

    private static void cgArc(Pointer p, double x1, double y1, double x2, double y2, double r) {
        cgPathArc.invoke(Void.class, new Object[]{ p, Pointer.NULL, x1, y1, x2, y2, r });
    }

    private static synchronized void ensureCG() {
        if (cgLoaded) return;
        ObjCRuntime.loadFramework("/System/Library/Frameworks/QuartzCore.framework/QuartzCore");
        try {
            NativeLibrary cg = NativeLibrary.getInstance(
                    "/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics");
            cgPathCreateMutable = cg.getFunction("CGPathCreateMutable");
            cgPathMove = cg.getFunction("CGPathMoveToPoint");
            cgPathLine = cg.getFunction("CGPathAddLineToPoint");
            cgPathArc = cg.getFunction("CGPathAddArcToPoint");
            cgPathClose = cg.getFunction("CGPathCloseSubpath");
            cgPathRelease = cg.getFunction("CGPathRelease");
        } catch (Throwable t) {
            System.out.println("WebView: failed to load CoreGraphics: " + t.getMessage());
        }
        cgLoaded = true;
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

    // ---- Live state ----------------------------------------------------------

    /**
     * @return the URL currently displayed by the web view, or {@code null} if none. When called on
     *         the JavaFX thread the value is read live from the {@code WKWebView}; otherwise the
     *         last value observed by the navigation delegate is returned.
     */
    public String getUrl() {
        if (Platform.isFxApplicationThread() && isValid(webView)) {
            String live = fromNSString(msg(msg(webView, sel("URL")), sel("absoluteString")));
            if (live != null) currentUrl = live;
        }
        return currentUrl;
    }

    /** @return the document title of the current page, or {@code null}. */
    public String getTitle() {
        if (Platform.isFxApplicationThread() && isValid(webView)) {
            String live = fromNSString(msg(webView, sel("title")));
            if (live != null) currentTitle = live;
        }
        return currentTitle;
    }

    /** @return whether the web view is currently loading a page (JavaFX thread only; else {@code false}). */
    public boolean isLoading() {
        if (Platform.isFxApplicationThread() && isValid(webView)) {
            return msgLong(webView, sel("isLoading")) != 0;
        }
        return false;
    }

    /** @return the estimated load progress in {@code [0.0, 1.0]} (JavaFX thread only; else {@code 0}). */
    public double getEstimatedProgress() {
        if (Platform.isFxApplicationThread() && isValid(webView)) {
            return msgDouble(webView, sel("estimatedProgress"));
        }
        return 0.0;
    }

    // ---- JavaScript ----------------------------------------------------------

    /** Evaluates a JavaScript string in the current page, discarding the result. */
    public void evaluateJavaScript(String script) {
        evaluateJavaScript(script, null);
    }

    /**
     * Evaluates a JavaScript string in the current page and delivers the (stringified) result to
     * {@code callback} on the JavaFX thread. If the view is not attached the callback fires with an
     * error.
     */
    public void evaluateJavaScript(String script, JsCallback callback) {
        runFx(() -> {
            if (!isValid(webView)) {
                if (callback != null) callback.onResult(null, "web view not attached");
                return;
            }
            Pointer block = Pointer.NULL;
            if (callback != null) {
                block = ObjCRuntime.createGlobalBlock((ObjCRuntime.ObjErrorBlock) (blk, result, error) -> {
                    String err = isValid(error)
                            ? fromNSString(msg(error, sel("localizedDescription"))) : null;
                    String res = isValid(result) ? fromNSString(msg(result, sel("description"))) : null;
                    Platform.runLater(() -> callback.onResult(res, err));
                });
            }
            msg(webView, sel("evaluateJavaScript:completionHandler:"), nsString(script), block);
        });
    }

    // ---- Content -------------------------------------------------------------

    /**
     * Retrieves the full serialized HTML of the current page (the document's {@code outerHTML}) and
     * delivers it to {@code callback} on the JavaFX thread. On failure {@code error} is non-null.
     */
    public void getHtml(JsCallback callback) {
        evaluateJavaScript("document.documentElement.outerHTML", callback);
    }

    /**
     * Retrieves the visible plain text of the current page (the body's {@code innerText}) and
     * delivers it to {@code callback} on the JavaFX thread. On failure {@code error} is non-null.
     */
    public void getText(JsCallback callback) {
        evaluateJavaScript("document.body ? document.body.innerText : ''", callback);
    }

    // ---- Load handlers -------------------------------------------------------

    /** Registers a listener for page-load lifecycle events. Multiple listeners are supported. */
    public WebViewManager addNavigationListener(NavigationListener listener) {
        if (listener != null) navListeners.add(listener);
        return this;
    }

    public WebViewManager removeNavigationListener(NavigationListener listener) {
        navListeners.remove(listener);
        return this;
    }

    private void notifyNav(java.util.function.Consumer<NavigationListener> event) {
        if (navListeners.isEmpty()) return;
        Platform.runLater(() -> {
            for (NavigationListener l : navListeners) {
                try { event.accept(l); } catch (Throwable ignored) { }
            }
        });
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

        installNavigationDelegate();
        applyRounding();

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

    /** Creates a {@code WKNavigationDelegate} for this manager and attaches it to the web view. */
    private void installNavigationDelegate() {
        if (!isValid(webView)) return;
        navDelegate = ObjCRuntime.newInstance(ensureNavDelegateClass());
        msg(navDelegate, sel("retain"));
        NAV_INSTANCES.put(Pointer.nativeValue(navDelegate), this);
        msg(webView, sel("setNavigationDelegate:"), navDelegate);
    }

    /** Builds (once) the runtime {@code WKNavigationDelegate} subclass shared by all instances. */
    private static synchronized Pointer ensureNavDelegateClass() {
        if (navDelegateClass != null) return navDelegateClass;
        if (ObjCRuntime.isRegistered(NAV_DELEGATE_CLASS)) {
            navDelegateClass = cls(NAV_DELEGATE_CLASS);
            return navDelegateClass;
        }
        Pointer c = ObjCRuntime.allocateClass(NAV_DELEGATE_CLASS, "NSObject");

        didStartCb = (self, cmd, view, nav) -> {
            WebViewManager m = NAV_INSTANCES.get(Pointer.nativeValue(self));
            if (m == null) return;
            String url = m.readUrl(view);
            m.currentUrl = url;
            m.notifyNav(l -> l.onStarted(url));
        };
        didCommitCb = (self, cmd, view, nav) -> {
            WebViewManager m = NAV_INSTANCES.get(Pointer.nativeValue(self));
            if (m == null) return;
            String url = m.readUrl(view);
            m.currentUrl = url;
            m.notifyNav(l -> l.onCommitted(url));
        };
        didFinishCb = (self, cmd, view, nav) -> {
            WebViewManager m = NAV_INSTANCES.get(Pointer.nativeValue(self));
            if (m == null) return;
            String url = m.readUrl(view);
            m.currentUrl = url;
            m.currentTitle = fromNSString(msg(view, sel("title")));
            m.notifyNav(l -> l.onFinished(url));
        };
        didFailCb = (self, cmd, view, nav, error) -> {
            WebViewManager m = NAV_INSTANCES.get(Pointer.nativeValue(self));
            if (m == null) return;
            String url = m.readUrl(view);
            String err = isValid(error) ? fromNSString(msg(error, sel("localizedDescription"))) : null;
            m.notifyNav(l -> l.onFailed(url, err));
        };
        didFailProvisionalCb = (self, cmd, view, nav, error) -> {
            WebViewManager m = NAV_INSTANCES.get(Pointer.nativeValue(self));
            if (m == null) return;
            String url = m.readUrl(view);
            String err = isValid(error) ? fromNSString(msg(error, sel("localizedDescription"))) : null;
            m.notifyNav(l -> l.onFailed(url, err));
        };

        ObjCRuntime.addMethod(c, "webView:didStartProvisionalNavigation:", didStartCb, "v@:@@");
        ObjCRuntime.addMethod(c, "webView:didCommitNavigation:", didCommitCb, "v@:@@");
        ObjCRuntime.addMethod(c, "webView:didFinishNavigation:", didFinishCb, "v@:@@");
        ObjCRuntime.addMethod(c, "webView:didFailNavigation:withError:", didFailCb, "v@:@@@");
        ObjCRuntime.addMethod(c, "webView:didFailProvisionalNavigation:withError:",
                didFailProvisionalCb, "v@:@@@");
        ObjCRuntime.registerClass(c);
        navDelegateClass = c;
        return navDelegateClass;
    }

    /** Reads the current URL string from a {@code WKWebView} pointer (main thread). */
    private String readUrl(Pointer view) {
        if (!isValid(view)) return null;
        return fromNSString(msg(msg(view, sel("URL")), sel("absoluteString")));
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
                if (perCorner && (lastMaskW != fw || lastMaskH != fh)) rebuildMask(fw, fh);
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
            msg(webView, sel("setNavigationDelegate:"), Pointer.NULL);
        }
        if (isValid(navDelegate)) {
            NAV_INSTANCES.remove(Pointer.nativeValue(navDelegate));
            msg(navDelegate, sel("release"));
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
        navDelegate = null;
        anchor = null;
        clip = null;
        lastRect = null;
        lastMaskW = -1; lastMaskH = -1;
        hidden = false;
    }

    // ---- Framework loading ----------------------------------------------------

    private static synchronized void ensureWebKit() {
        if (webKitLoaded) return;
        ObjCRuntime.loadFramework("/System/Library/Frameworks/WebKit.framework/WebKit");
        webKitLoaded = true;
    }
}
