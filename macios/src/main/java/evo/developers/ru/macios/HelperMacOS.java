package evo.developers.ru.macios;

import com.sun.jna.Pointer;
import evo.developers.ru.macios.core.WindowManager;
import evo.developers.ru.macios.effects.BlurEffectManager;
import evo.developers.ru.macios.effects.LiquidGlassManager;
import evo.developers.ru.macios.effects.metal.MetalLayerManager;
import evo.developers.ru.macios.permissions.PermissionsManager;
import evo.developers.ru.macios.security.BiometricManager;
import evo.developers.ru.macios.security.KeyChainManager;
import evo.developers.ru.macios.security.WindowSecurity;
import evo.developers.ru.macios.ui.TitleBarManager;
import evo.developers.ru.macios.ux.activity.UserActivityManager;
import evo.developers.ru.macios.ux.alert.AlertManager;
import evo.developers.ru.macios.ux.haptic.HapticManager;
import evo.developers.ru.macios.ux.menu.ContextMenuManager;
import evo.developers.ru.macios.ux.menu.DockMenuManager;
import evo.developers.ru.macios.ux.menu.MenuBarManager;
import evo.developers.ru.macios.ux.nowplaying.NowPlayingManager;
import evo.developers.ru.macios.ux.push.PushNotificationManager;
import evo.developers.ru.macios.ux.share.ShareManager;
import evo.developers.ru.macios.ux.toolbar.ToolbarManager;
import evo.developers.ru.macios.ux.tray.StatusBarManager;
import evo.developers.ru.macios.ux.window.WindowTabManager;
import javafx.stage.Stage;
import lombok.Getter;

/**
 * Facade exposing every native macOS feature for a given {@link Stage}.
 *
 * <p>Create one per window with {@link #getInstance(Stage)} and reach each feature through
 * its getter, e.g. {@code helper.getBlurEffect().apply(Style.DARK)}.</p>
 */
@Getter
public class HelperMacOS {

    public static HelperMacOS getInstance(Stage stage) {
        return new HelperMacOS(stage);
    }

    private final Stage stage;

    private final TitleBarManager titleBarManager;
    private final WindowManager windowManager;
    private final WindowSecurity windowSecurity;
    private final BlurEffectManager blurEffect;
    private final HapticManager hapticManager;
    private final StatusBarManager statusBar;
    private final BiometricManager biometric;
    private final NowPlayingManager nowPlaying;
    private final MenuBarManager menuBar;
    private final DockMenuManager dockMenu;
    private final ContextMenuManager contextMenu;
    private final ToolbarManager toolbar;
    private final AlertManager alert;
    private final KeyChainManager keychain;
    private final UserActivityManager userActivity;
    private final ShareManager share;
    private final WindowTabManager windowTab;
    private final LiquidGlassManager liquidGlass;
    private final MetalLayerManager metal;
    private final PermissionsManager permissions;
    private final PushNotificationManager push;

    private HelperMacOS(Stage stage) {
        this.stage = stage;

        Pointer pointer = null;

        titleBarManager = new TitleBarManager(stage, pointer);
        windowManager = new WindowManager(stage, pointer);
        windowSecurity = new WindowSecurity(stage, pointer);
        blurEffect = new BlurEffectManager(stage, pointer);
        hapticManager = new HapticManager(stage, pointer);
        statusBar = new StatusBarManager(stage, pointer);
        biometric = new BiometricManager(stage, pointer);
        nowPlaying = new NowPlayingManager(stage, pointer);
        menuBar = new MenuBarManager(stage, pointer);
        dockMenu = new DockMenuManager(stage, pointer);
        contextMenu = new ContextMenuManager(stage, pointer);
        toolbar = new ToolbarManager(stage, pointer);
        alert = new AlertManager(stage, pointer);
        keychain = new KeyChainManager(stage, pointer);
        userActivity = new UserActivityManager(stage, pointer);
        share = new ShareManager(stage, pointer);
        windowTab = new WindowTabManager(stage, pointer);
        liquidGlass = new LiquidGlassManager(stage, pointer);
        metal = new MetalLayerManager(stage, pointer);
        permissions = new PermissionsManager(stage, pointer);
        push = new PushNotificationManager(stage, pointer);
    }
}
