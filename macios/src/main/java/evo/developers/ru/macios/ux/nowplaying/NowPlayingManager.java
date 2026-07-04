package evo.developers.ru.macios.ux.nowplaying;

import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import evo.developers.ru.macios.base.Manager;
import evo.developers.ru.macios.core.ObjCRuntime;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static evo.developers.ru.macios.core.ObjC.*;
import static evo.developers.ru.macios.core.ObjCRuntime.*;

/**
 * "Now Playing" integration via {@code MPNowPlayingInfoCenter} plus media-key /
 * Control Center handling via {@code MPRemoteCommandCenter}.
 *
 * <p>Remote commands only route to an app that the system considers the current
 * "now playing" app, which normally means it is actually producing audio. Without
 * real playback the info still shows, but the hardware media keys may not reach it.</p>
 */
public class NowPlayingManager extends Manager {

    private static final String MEDIA_PLAYER =
            "/System/Library/Frameworks/MediaPlayer.framework/MediaPlayer";

    public static final int STATE_PLAYING = 1;
    public static final int STATE_PAUSED = 2;
    public static final int STATE_STOPPED = 3;

    static {
        loadFramework(MEDIA_PLAYER);
    }

    private static final String CMD_CLASS = "JFXRemoteCommandTarget";
    private static Pointer cmdClass;
    private static final Map<Long, NowPlayingManager> CMD_INSTANCES = new ConcurrentHashMap<>();
    /** Kept to avoid GC of the callbacks. */
    private static ObjCRuntime.LongObjMethod[] cmdCallbacks;

    private Pointer cmdTarget;
    private final Map<String, CommandHandler> handlers = new ConcurrentHashMap<>();

    public NowPlayingManager(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
    }

    public interface CommandHandler {
        /** @return {@code true} if the command was handled successfully. */
        boolean handle();
    }

    // ---- Now Playing info ----

    public void setNowPlaying(String title, String artist, String album,
                              double durationSeconds, double elapsedSeconds) {
        try {
            Pointer dict = msg(msg(cls("NSMutableDictionary"), sel("alloc")), sel("init"));
            putString(dict, "MPMediaItemPropertyTitle", title);
            putString(dict, "MPMediaItemPropertyArtist", artist);
            putString(dict, "MPMediaItemPropertyAlbumTitle", album);
            putNumber(dict, "MPMediaItemPropertyPlaybackDuration", durationSeconds);
            putNumber(dict, "MPNowPlayingInfoPropertyElapsedPlaybackTime", elapsedSeconds);
            putNumber(dict, "MPNowPlayingInfoPropertyPlaybackRate", 1.0);

            Pointer center = msg(cls("MPNowPlayingInfoCenter"), sel("defaultCenter"));
            msg(center, sel("setNowPlayingInfo:"), dict);
        } catch (Throwable t) {
            System.out.println("NowPlaying setNowPlaying error: " + t.getMessage());
        }
    }

    public void setPlaybackState(int state) {
        try {
            Pointer center = msg(cls("MPNowPlayingInfoCenter"), sel("defaultCenter"));
            msg(center, sel("setPlaybackState:"), (long) state);
        } catch (Throwable t) {
            System.out.println("NowPlaying setPlaybackState error: " + t.getMessage());
        }
    }

    public void clear() {
        try {
            Pointer center = msg(cls("MPNowPlayingInfoCenter"), sel("defaultCenter"));
            msg(center, sel("setNowPlayingInfo:"), Pointer.NULL);
        } catch (Throwable ignored) { }
    }

    // ---- Remote commands ----

    private static synchronized void ensureCmdClass() {
        if (cmdClass != null) return;
        if (isRegistered(CMD_CLASS)) {
            cmdClass = cls(CMD_CLASS);
            return;
        }
        Pointer c = allocateClass(CMD_CLASS, "NSObject");
        String[] names = { "play", "pause", "toggle", "next", "prev", "stop" };
        String[] selectors = { "onPlay:", "onPause:", "onToggle:", "onNext:", "onPrev:", "onStop:" };
        cmdCallbacks = new ObjCRuntime.LongObjMethod[names.length];
        for (int i = 0; i < names.length; i++) {
            final String key = names[i];
            ObjCRuntime.LongObjMethod cb = (self, cmd, event) -> dispatch(self, key);
            cmdCallbacks[i] = cb;
            addMethod(c, selectors[i], cb, "q@:@");
        }
        registerClass(c);
        cmdClass = c;
    }

    /** MPRemoteCommandHandlerStatus: success = 0, commandFailed = 200. */
    private static long dispatch(Pointer self, String key) {
        try {
            NowPlayingManager mgr = CMD_INSTANCES.get(Pointer.nativeValue(self));
            if (mgr == null) return 200;
            CommandHandler handler = mgr.handlers.get(key);
            if (handler == null) return 200;
            boolean[] ok = { false };
            handler(handler, ok);
            return ok[0] ? 0 : 200;
        } catch (Throwable t) {
            return 200;
        }
    }

    private static void handler(CommandHandler handler, boolean[] ok) {
        try {
            ok[0] = handler.handle();
        } catch (Throwable t) {
            ok[0] = false;
        }
    }

    /** Registers native targets for the media commands. Call once. */
    public void enableRemoteCommands() {
        Platform.runLater(() -> {
            try {
                ensureCmdClass();
                cmdTarget = newInstance(cmdClass);
                CMD_INSTANCES.put(Pointer.nativeValue(cmdTarget), this);

                Pointer center = msg(cls("MPRemoteCommandCenter"), sel("sharedCommandCenter"));
                bind(center, "playCommand", "onPlay:");
                bind(center, "pauseCommand", "onPause:");
                bind(center, "togglePlayPauseCommand", "onToggle:");
                bind(center, "nextTrackCommand", "onNext:");
                bind(center, "previousTrackCommand", "onPrev:");
                bind(center, "stopCommand", "onStop:");
            } catch (Throwable t) {
                System.out.println("NowPlaying enableRemoteCommands error: " + t.getMessage());
            }
        });
    }

    private void bind(Pointer center, String commandGetter, String actionSelector) {
        Pointer command = msg(center, sel(commandGetter));
        if (!isValid(command)) return;
        msg(command, sel("setEnabled:"), 1L);
        msg(command, sel("addTarget:action:"), cmdTarget, sel(actionSelector));
    }

    public void onPlay(CommandHandler h)   { handlers.put("play", h); }
    public void onPause(CommandHandler h)  { handlers.put("pause", h); }
    public void onToggle(CommandHandler h) { handlers.put("toggle", h); }
    public void onNext(CommandHandler h)   { handlers.put("next", h); }
    public void onPrevious(CommandHandler h) { handlers.put("prev", h); }
    public void onStop(CommandHandler h)   { handlers.put("stop", h); }

    // ---- helpers ----

    private static Pointer key(String symbol) {
        try {
            Pointer addr = NativeLibrary.getInstance(MEDIA_PLAYER).getGlobalVariableAddress(symbol);
            return (addr != null) ? addr.getPointer(0) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void putString(Pointer dict, String symbol, String value) {
        if (value == null) return;
        Pointer k = key(symbol);
        if (!isValid(k)) return;
        msg(dict, sel("setObject:forKey:"), nsString(value), k);
    }

    private static void putNumber(Pointer dict, String symbol, double value) {
        Pointer k = key(symbol);
        if (!isValid(k)) return;
        Pointer number = msg(cls("NSNumber"), sel("numberWithDouble:"), value);
        msg(dict, sel("setObject:forKey:"), number, k);
    }
}
