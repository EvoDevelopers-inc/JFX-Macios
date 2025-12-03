package evo.developers.ru.macios.ux.haptic;

import com.sun.jna.Pointer;
import evo.developers.ru.macios.base.Manager;
import javafx.stage.Stage;

import static evo.developers.ru.macios.core.ObjC.*;

public class HapticManager extends Manager {

    public HapticManager(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
    }

    public enum HapticPattern {
        GENERIC(0),           // Generic feedback
        ALIGNMENT(1),         // Alignment guides
        LEVEL_CHANGE(2);      // Level or value change

        final long value;
        HapticPattern(long value) { this.value = value; }
    }


    public enum HapticTime {
        DEFAULT(0),           // Default timing
        NOW(1),               // Perform immediately
        DRAW_COMPLETED(2);    // After drawing completes

        final long value;
        HapticTime(long value) { this.value = value; }
    }

    public void haptic(HapticPattern pattern, HapticTime time) {
        try {
            // [NSHapticFeedbackManager defaultPerformer]
            Pointer manager = msg(cls("NSHapticFeedbackManager"), sel("defaultPerformer"));
            if (manager == null || Pointer.nativeValue(manager) == 0) {
                System.out.println("WindowBlur: No haptic feedback manager");
                return;
            }

            // [performer performFeedbackPattern:performanceTime:]
            msg(manager, sel("performFeedbackPattern:performanceTime:"), pattern.value, time.value);

        } catch (Exception e) {
            System.out.println("WindowBlur haptic error: " + e.getMessage());
        }
    }
}
