package evo.developers.ru.macios.base;

import com.sun.jna.Pointer;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;

abstract public class Manager {

    @Getter
    private final Stage stage;

    @Getter
    @Setter
    private Pointer pointerWindow;

    public Manager(Stage stage, Pointer pointerWindow) {
        this.stage = stage;
        this.pointerWindow = pointerWindow;
    }

}
