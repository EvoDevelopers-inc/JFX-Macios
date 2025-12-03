package evo.developers.ru.macios.ux.sound;

import com.sun.jna.Pointer;
import evo.developers.ru.macios.base.Manager;
import javafx.stage.Stage;

import static evo.developers.ru.macios.core.ObjC.*;

public class SoundManager extends Manager {
    public SoundManager(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
    }

    /**
     * Play system notification sound.
     */
    public static void playNotificationSound() {
        playSound("Blow"); // Default notification sound
    }

    /**
     * Play a system sound by name.
     * Examples: "Basso", "Blow", "Bottle", "Frog", "Funk", "Glass", "Hero",
     *           "Morse", "Ping", "Pop", "Purr", "Sosumi", "Submarine", "Tink"
     */
    public static void playSound(String soundName) {
        try {
            Pointer name = msg(cls("NSString"), sel("stringWithUTF8String:"), soundName);
            Pointer sound = msg(cls("NSSound"), sel("soundNamed:"), name);
            if (sound != null && Pointer.nativeValue(sound) != 0) {
                msg(sound, sel("play"));
            }
        } catch (Exception e) {
            System.out.println("WindowBlur playSound error: " + e.getMessage());
        }
    }
}
