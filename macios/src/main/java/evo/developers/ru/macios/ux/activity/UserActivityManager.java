package evo.developers.ru.macios.ux.activity;

import com.sun.jna.Pointer;
import evo.developers.ru.macios.base.Manager;
import evo.developers.ru.macios.core.ObjCRuntime;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;

import static evo.developers.ru.macios.core.ObjC.*;
import static evo.developers.ru.macios.core.ObjCRuntime.*;

/**
 * Advertises {@code NSUserActivity} for Handoff, Spotlight search, and Siri suggestions,
 * and (optionally) handles incoming continuations.
 *
 * <p>This is the closest first-party hook into the Apple ecosystem available without an
 * App Extension: an advertised activity can hand off to the user's other devices, appear
 * in Spotlight, and feed Siri Suggestions / Shortcuts.</p>
 *
 * <pre>{@code
 * helper.getUserActivity().newActivity("ru.evo.app.viewItem")
 *     .title("Viewing item 42")
 *     .info("id", "42")
 *     .handoff(true)
 *     .searchable(true)
 *     .prediction(true)
 *     .keywords("item", "42")
 *     .become();
 *
 * // Receive a Handoff / Spotlight continuation:
 * helper.getUserActivity().onContinue((type, title, info) -> ...);
 * }</pre>
 */
public class UserActivityManager extends Manager {

    private static volatile ContinueCallback continueCallback;
    private static ObjCRuntime.BoolObjObjObjMethod continueCb;
    private static boolean injected;

    private Pointer current;

    public UserActivityManager(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
    }

    public interface ContinueCallback {
        void onContinue(String activityType, String title, Map<String, String> userInfo);
    }

    /** Starts building a new activity of the given type (a reverse-DNS identifier). */
    public Activity newActivity(String activityType) {
        return new Activity(activityType);
    }

    /** Stops advertising the current activity. */
    public void resignCurrent() {
        runFx(() -> {
            if (isValid(current)) msg(current, sel("resignCurrent"));
        });
    }

    /** Invalidates the current activity entirely. */
    public void invalidate() {
        runFx(() -> {
            if (isValid(current)) {
                msg(current, sel("invalidate"));
                msg(current, sel("release"));
                current = null;
            }
        });
    }

    /** Registers a handler for incoming Handoff / Spotlight continuations. */
    public void onContinue(ContinueCallback callback) {
        continueCallback = callback;
        runFx(UserActivityManager::inject);
    }

    /** Fluent builder for an {@code NSUserActivity}. Call {@link #become()} to advertise it. */
    public class Activity {
        private final Pointer activity;
        private Pointer userInfo;
        private Pointer keywordSet;

        private Activity(String activityType) {
            activity = msg(msg(cls("NSUserActivity"), sel("alloc")),
                    sel("initWithActivityType:"), nsString(activityType));
        }

        public Activity title(String title) {
            msg(activity, sel("setTitle:"), nsString(title));
            return this;
        }

        public Activity info(String key, String value) {
            if (userInfo == null) userInfo = msg(cls("NSMutableDictionary"), sel("dictionary"));
            msg(userInfo, sel("setObject:forKey:"), nsString(value), nsString(key));
            return this;
        }

        public Activity handoff(boolean eligible) {
            msg(activity, sel("setEligibleForHandoff:"), eligible ? 1L : 0L);
            return this;
        }

        public Activity searchable(boolean eligible) {
            msg(activity, sel("setEligibleForSearch:"), eligible ? 1L : 0L);
            return this;
        }

        /** Siri Suggestions / prediction eligibility (macOS 10.15+). */
        public Activity prediction(boolean eligible) {
            if (msgLong(activity, sel("respondsToSelector:"), sel("setEligibleForPrediction:")) != 0) {
                msg(activity, sel("setEligibleForPrediction:"), eligible ? 1L : 0L);
            }
            return this;
        }

        public Activity keywords(String... keywords) {
            if (keywords == null || keywords.length == 0) return this;
            if (keywordSet == null) keywordSet = msg(cls("NSMutableSet"), sel("set"));
            for (String k : keywords) msg(keywordSet, sel("addObject:"), nsString(k));
            return this;
        }

        public Activity webpageURL(String url) {
            Pointer nsurl = msg(cls("NSURL"), sel("URLWithString:"), nsString(url));
            if (isValid(nsurl)) msg(activity, sel("setWebpageURL:"), nsurl);
            return this;
        }

        /** Applies accumulated attributes and makes this the current, advertised activity. */
        public void become() {
            runFx(() -> {
                try {
                    if (userInfo != null) msg(activity, sel("setUserInfo:"), userInfo);
                    if (keywordSet != null) msg(activity, sel("setKeywords:"), keywordSet);

                    if (isValid(current)) {
                        msg(current, sel("invalidate"));
                        msg(current, sel("release"));
                    }
                    msg(activity, sel("retain"));
                    current = activity;
                    msg(activity, sel("becomeCurrent"));
                } catch (Throwable t) {
                    System.out.println("UserActivity become error: " + t.getMessage());
                }
            });
        }
    }

    private static synchronized void inject() {
        if (injected) return;
        Pointer app = msg(cls("NSApplication"), sel("sharedApplication"));
        Pointer delegate = msg(app, sel("delegate"));
        if (!isValid(delegate)) {
            System.out.println("UserActivity: no application delegate yet, cannot install continuation handler");
            return;
        }
        Pointer delegateClass = classOf(delegate);
        continueCb = (self, cmd, application, activity, handler) -> {
            try {
                ContinueCallback cb = continueCallback;
                if (cb != null && isValid(activity)) {
                    String type = fromNSString(msg(activity, sel("activityType")));
                    String title = fromNSString(msg(activity, sel("title")));
                    Map<String, String> info = readUserInfo(msg(activity, sel("userInfo")));
                    Platform.runLater(() -> cb.onContinue(type, title, info));
                }
            } catch (Throwable ignored) { }
            return 1; // YES
        };
        addMethod(delegateClass, "application:continueUserActivity:restorationHandler:", continueCb, "c@:@@@");
        injected = true;
    }

    private static Map<String, String> readUserInfo(Pointer info) {
        Map<String, String> map = new HashMap<>();
        if (!isValid(info)) return map;
        Pointer keys = msg(info, sel("allKeys"));
        long count = msgLong(keys, sel("count"));
        for (long i = 0; i < count; i++) {
            Pointer key = msg(keys, sel("objectAtIndex:"), i);
            Pointer value = msg(info, sel("objectForKey:"), key);
            String k = fromNSString(key);
            String v = stringify(value);
            if (k != null) map.put(k, v);
        }
        return map;
    }

    private static String stringify(Pointer obj) {
        if (!isValid(obj)) return null;
        if (msgLong(obj, sel("respondsToSelector:"), sel("UTF8String")) != 0) {
            return fromNSString(obj);
        }
        return fromNSString(msg(obj, sel("description")));
    }
}
