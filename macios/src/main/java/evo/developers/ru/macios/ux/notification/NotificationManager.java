package evo.developers.ru.macios.ux.notification;

import com.sun.jna.Pointer;
import evo.developers.ru.macios.base.Manager;
import evo.developers.ru.macios.core.ObjCRuntime;
import javafx.stage.Stage;

import static evo.developers.ru.macios.core.ObjC.*;
import static evo.developers.ru.macios.core.ObjCRuntime.*;

public class NotificationManager extends Manager {

    public NotificationManager(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
    }

    // ---- Activation types (NSUserNotificationActivationType) ----
    public static final int ACTIVATION_NONE = 0;
    public static final int ACTIVATION_CONTENTS_CLICKED = 1;
    public static final int ACTIVATION_ACTION_BUTTON = 2;
    public static final int ACTIVATION_REPLIED = 3;
    public static final int ACTIVATION_ADDITIONAL_ACTION = 4;

    private static final String DELEGATE_CLASS = "JFXNotificationDelegate";
    private static Pointer delegateInstance;
    private static volatile NotificationListener listener;
    private static ObjCRuntime.VoidObjObjMethod didActivateCb;
    private static ObjCRuntime.BoolObjObjMethod shouldPresentCb;

    public interface NotificationListener {
        /**
         * @param identifier     the notification identifier
         * @param activationType one of the {@code ACTIVATION_*} constants
         * @param replyText      the typed reply (only for {@link #ACTIVATION_REPLIED}), else {@code null}
         */
        void onActivated(String identifier, int activationType, String replyText);
    }

    public static void sendNotification(String title, String message, String identifier) {
        try {

            Pointer notification = msg(msg(cls("NSUserNotification"), sel("alloc")), sel("init"));

            Pointer titleStr = msg(cls("NSString"), sel("stringWithUTF8String:"), title);
            Pointer msgStr = msg(cls("NSString"), sel("stringWithUTF8String:"), message);
            Pointer idStr = msg(cls("NSString"), sel("stringWithUTF8String:"), identifier);

            msg(notification, sel("setTitle:"), titleStr);
            msg(notification, sel("setInformativeText:"), msgStr);
            msg(notification, sel("setIdentifier:"), idStr);
            msg(notification, sel("setSoundName:"), msg(cls("NSString"), sel("stringWithUTF8String:"), "default"));

            Pointer center = msg(cls("NSUserNotificationCenter"), sel("defaultUserNotificationCenter"));
            msg(center, sel("deliverNotification:"), notification);

        } catch (Exception e) {
            System.out.println("WindowBlur sendNotification error: " + e.getMessage());
        }
    }

    /**
     * Send notification with subtitle.
     */
    public static void sendNotification(String title, String subtitle, String message, String identifier) {
        try {
            Pointer notification = msg(msg(cls("NSUserNotification"), sel("alloc")), sel("init"));

            msg(notification, sel("setTitle:"), msg(cls("NSString"), sel("stringWithUTF8String:"), title));
            msg(notification, sel("setSubtitle:"), msg(cls("NSString"), sel("stringWithUTF8String:"), subtitle));
            msg(notification, sel("setInformativeText:"), msg(cls("NSString"), sel("stringWithUTF8String:"), message));
            msg(notification, sel("setIdentifier:"), msg(cls("NSString"), sel("stringWithUTF8String:"), identifier));
            msg(notification, sel("setSoundName:"), msg(cls("NSString"), sel("stringWithUTF8String:"), "default"));

            Pointer center = msg(cls("NSUserNotificationCenter"), sel("defaultUserNotificationCenter"));
            msg(center, sel("deliverNotification:"), notification);

        } catch (Exception e) {
            System.out.println("WindowBlur sendNotification error: " + e.getMessage());
        }
    }

    /**
     * Send a rich notification with an optional action button, an optional inline reply
     * field, and an optional content image loaded from a file path.
     *
     * <p>Action / reply buttons only appear when the app's notification style is set to
     * "Alerts" (System Settings → Notifications), not "Banners".</p>
     */
    public static void sendRich(String title, String subtitle, String message, String identifier,
                                String actionButtonTitle, boolean hasReply,
                                String replyPlaceholder, String imagePath) {
        try {
            Pointer n = msg(msg(cls("NSUserNotification"), sel("alloc")), sel("init"));

            msg(n, sel("setTitle:"), nsString(title));
            if (subtitle != null) msg(n, sel("setSubtitle:"), nsString(subtitle));
            msg(n, sel("setInformativeText:"), nsString(message));
            msg(n, sel("setIdentifier:"), nsString(identifier));
            msg(n, sel("setSoundName:"), nsString("default"));

            if (actionButtonTitle != null) {
                msg(n, sel("setHasActionButton:"), 1L);
                msg(n, sel("setActionButtonTitle:"), nsString(actionButtonTitle));
            }
            if (hasReply) {
                msg(n, sel("setHasReplyButton:"), 1L);
                if (replyPlaceholder != null) {
                    msg(n, sel("setResponsePlaceholder:"), nsString(replyPlaceholder));
                }
            }
            if (imagePath != null) {
                Pointer image = msg(msg(cls("NSImage"), sel("alloc")),
                        sel("initWithContentsOfFile:"), nsString(imagePath));
                if (isValid(image)) msg(n, sel("setContentImage:"), image);
            }

            Pointer center = msg(cls("NSUserNotificationCenter"), sel("defaultUserNotificationCenter"));
            msg(center, sel("deliverNotification:"), n);
        } catch (Exception e) {
            System.out.println("Notification sendRich error: " + e.getMessage());
        }
    }

    /**
     * Register a listener for notification activations (clicks, action button, reply).
     * Installs a native delegate on the default notification center.
     */
    public static synchronized void setListener(NotificationListener l) {
        listener = l;
        try {
            ensureDelegate();
        } catch (Throwable t) {
            System.out.println("Notification setListener error: " + t.getMessage());
        }
    }

    private static void ensureDelegate() {
        Pointer center = msg(cls("NSUserNotificationCenter"), sel("defaultUserNotificationCenter"));
        if (delegateInstance == null) {
            Pointer c;
            if (isRegistered(DELEGATE_CLASS)) {
                c = cls(DELEGATE_CLASS);
            } else {
                c = allocateClass(DELEGATE_CLASS, "NSObject");

                didActivateCb = (self, cmd, notifCenter, notif) -> {
                    try {
                        int type = (int) msgLong(notif, sel("activationType"));
                        String id = fromNSString(msg(notif, sel("identifier")));
                        String reply = null;
                        if (type == ACTIVATION_REPLIED) {
                            Pointer response = msg(notif, sel("response"));
                            if (isValid(response)) reply = fromNSString(msg(response, sel("string")));
                        }
                        NotificationListener l = listener;
                        if (l != null) l.onActivated(id, type, reply);
                    } catch (Throwable ignored) { }
                };

                // Present notifications even when the app is frontmost.
                shouldPresentCb = (self, cmd, notifCenter, notif) -> (byte) 1;

                addMethod(c, "userNotificationCenter:didActivateNotification:", didActivateCb, "v@:@@");
                addMethod(c, "userNotificationCenter:shouldPresentNotification:", shouldPresentCb, "c@:@@");
                registerClass(c);
            }
            delegateInstance = newInstance(c);
        }
        msg(center, sel("setDelegate:"), delegateInstance);
    }

    /**
     * Remove all delivered notifications.
     */
    public static void clearNotifications() {
        try {
            Pointer center = msg(cls("NSUserNotificationCenter"), sel("defaultUserNotificationCenter"));
            msg(center, sel("removeAllDeliveredNotifications"));
        } catch (Exception e) {}
    }

}
