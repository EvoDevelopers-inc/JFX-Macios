package evo.developers.ru.macios;

import com.sun.jna.Pointer;
import evo.developers.ru.macios.core.WindowManager;
import evo.developers.ru.macios.effects.BlurEffectManager;
import evo.developers.ru.macios.security.WindowSecurity;
import evo.developers.ru.macios.ui.TitleBarManager;
import evo.developers.ru.macios.ux.haptic.HapticManager;
import javafx.stage.Stage;
import lombok.Getter;

@Getter
public class HelperMacOS {

    public static HelperMacOS getInstance(Stage stage) {
        return new HelperMacOS(stage);
    }

    private Stage stage;

    private TitleBarManager titleBarManager;
    private WindowManager windowManager;
    private WindowSecurity windowSecurity;
    private BlurEffectManager blurEffect;
    private HapticManager hapticManager;

    private HelperMacOS(Stage stage) {
        this.stage = stage;

        Pointer pointer = null;

        titleBarManager = new TitleBarManager(stage, pointer);
        windowManager = new WindowManager(stage, pointer);
        windowSecurity = new WindowSecurity(stage, pointer);
        blurEffect = new BlurEffectManager(stage, pointer);
        hapticManager = new HapticManager(stage, pointer);
    }


}
