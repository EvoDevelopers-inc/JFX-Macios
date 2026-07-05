package evo.developers.ru.macios.ux.share;

import com.sun.jna.Pointer;
import evo.developers.ru.macios.base.Manager;
import evo.developers.ru.macios.core.ObjC;
import javafx.stage.Stage;

import static evo.developers.ru.macios.core.ObjC.*;

/**
 * The native macOS Share sheet ({@code NSSharingServicePicker}) — AirDrop, Messages,
 * Mail, Notes, etc.
 *
 * <p>The picker is anchored to the window's content view at the current mouse location,
 * so trigger it from a JavaFX button or menu handler.</p>
 *
 * <pre>{@code
 * shareButton.setOnAction(e -> helper.getShare().shareText("Hello from JFX-Macios!"));
 * helper.getShare().shareURL("https://example.com");
 * helper.getShare().shareFiles("/path/to/file.pdf");
 * }</pre>
 */
public class ShareManager extends Manager {

    /** {@code NSMinYEdge} — the picker prefers to appear below the anchor rect. */
    private static final long EDGE_MIN_Y = 1;

    /** Retains the currently displayed picker so it survives until dismissed. */
    private Pointer currentPicker;

    public ShareManager(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
    }

    /** Shares plain text. */
    public void shareText(String text) {
        share(new Pointer[]{ nsString(text) });
    }

    /** Shares a URL (rendered as a link). */
    public void shareURL(String url) {
        Pointer nsurl = msg(cls("NSURL"), sel("URLWithString:"), nsString(url));
        if (isValid(nsurl)) share(new Pointer[]{ nsurl });
    }

    /** Shares one or more files by path. */
    public void shareFiles(String... paths) {
        if (paths == null || paths.length == 0) return;
        Pointer[] items = new Pointer[paths.length];
        for (int i = 0; i < paths.length; i++) {
            items[i] = msg(cls("NSURL"), sel("fileURLWithPath:"), nsString(paths[i]));
        }
        share(items);
    }

    /** Shares an image loaded from a file path. */
    public void shareImage(String path) {
        Pointer image = msg(msg(cls("NSImage"), sel("alloc")),
                sel("initWithContentsOfFile:"), nsString(path));
        if (isValid(image)) share(new Pointer[]{ image });
    }

    private void share(Pointer[] items) {
        runFx(() -> {
            try {
                Pointer window = window();
                if (window == null) return;
                Pointer contentView = msg(window, sel("contentView"));
                if (!isValid(contentView)) return;

                Pointer array = msg(cls("NSMutableArray"), sel("array"));
                for (Pointer item : items) {
                    if (isValid(item)) msg(array, sel("addObject:"), item);
                }

                Pointer picker = msg(msg(cls("NSSharingServicePicker"), sel("alloc")),
                        sel("initWithItems:"), array);
                if (!isValid(picker)) return;

                if (isValid(currentPicker)) msg(currentPicker, sel("release"));
                msg(picker, sel("retain"));
                currentPicker = picker;

                // Anchor a 1x1 rect at the mouse location, converted into the view's coordinates.
                ObjC.CGPoint screen = msgPoint(cls("NSEvent"), sel("mouseLocation"));
                ObjC.CGPoint winPt = msgPoint(window, sel("convertPointFromScreen:"), screen);
                ObjC.CGPoint viewPt = msgPoint(contentView, sel("convertPoint:fromView:"), winPt, Pointer.NULL);

                ObjC.CGRect rect = new ObjC.CGRect(viewPt.x, viewPt.y, 1, 1);
                msg(picker, sel("showRelativeToRect:ofView:preferredEdge:"), rect, contentView, EDGE_MIN_Y);
            } catch (Throwable t) {
                System.out.println("Share error: " + t.getMessage());
            }
        });
    }
}
