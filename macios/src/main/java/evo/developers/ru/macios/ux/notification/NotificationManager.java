package evo.developers.ru.macios.ux.notification;

import com.sun.jna.Pointer;
import evo.developers.ru.macios.base.Manager;
import javafx.stage.Stage;

import static evo.developers.ru.macios.core.ObjC.*;

public class NotificationManager extends Manager {

    public NotificationManager(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
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
     * Remove all delivered notifications.
     */
    public static void clearNotifications() {
        try {
            Pointer center = msg(cls("NSUserNotificationCenter"), sel("defaultUserNotificationCenter"));
            msg(center, sel("removeAllDeliveredNotifications"));
        } catch (Exception e) {}
    }

}
