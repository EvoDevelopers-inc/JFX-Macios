package evo.developers.ru.macios.effects;

import com.sun.jna.Pointer;
import evo.developers.ru.macios.base.Manager;
import evo.developers.ru.macios.core.WindowManager;
import javafx.stage.Stage;

import static evo.developers.ru.macios.core.ObjC.*;

public class BlurEffectManager extends Manager {
    public BlurEffectManager(Stage stage, Pointer pointerWindow) {
        super(stage, pointerWindow);
    }

    public enum Style {
        LIGHT("NSAppearanceNameVibrantLight"),
        DARK("NSAppearanceNameVibrantDark"),
        AQUA("NSAppearanceNameAqua"),
        DARK_AQUA("NSAppearanceNameDarkAqua");
        
        final String name;
        Style(String name) { this.name = name; }
    }
    
    public enum BlendingMode {
        BEHIND_WINDOW(0),
        WITHIN_WINDOW(1);
        
        public final long value;
        BlendingMode(long value) { this.value = value; }
    }
    
    private Pointer currentBlurView = null;
    private double currentAlpha = 1.0;
    private Material currentMaterial = Material.SIDEBAR;
    
    public void apply(Stage stage, Style style) {
        try {
            Pointer window = WindowManager.getCurrentWindow(stage.getTitle());
            if (window == null) {
                System.out.println("WindowBlur: No windows");
                return;
            }

            Pointer contentView = msg(window, sel("contentView"));
            if (contentView == null || Pointer.nativeValue(contentView) == 0) return;

            Pointer subviews = msg(contentView, sel("subviews"));
            long subCount = msgLong(subviews, sel("count"));
            if (subCount == 0) return;

            // Remove existing blur views
            Pointer vfxClass = cls("NSVisualEffectView");
            for (long i = subCount - 1; i >= 0; i--) {
                Pointer sub = msg(subviews, sel("objectAtIndex:"), i);
                if (msgLong(sub, sel("isKindOfClass:"), vfxClass) != 0) {
                    msg(sub, sel("removeFromSuperview"));
                }
            }

            // Create blur view
            Pointer vfxView = msg(msg(vfxClass, sel("alloc")), sel("init"));
            currentBlurView = vfxView;

            // Set appearance
            Pointer styleName = msg(cls("NSString"), sel("stringWithUTF8String:"), style.name);
            Pointer appearance = msg(cls("NSAppearance"), sel("appearanceNamed:"), styleName);

            msg(vfxView, sel("setAppearance:"), appearance);
            msg(vfxView, sel("setBlendingMode:"), BlendingMode.BEHIND_WINDOW.value);
            msg(vfxView, sel("setMaterial:"), currentMaterial.value);
            msg(vfxView, sel("setState:"), 1L);
            msg(vfxView, sel("setTranslatesAutoresizingMaskIntoConstraints:"), 0L);
            msg(vfxView, sel("setWantsLayer:"), 1L);

            // Apply alpha if not 1.0
            if (currentAlpha < 1.0) {
                msg(vfxView, sel("setAlphaValue:"), currentAlpha);
            }

            // Add as bottom view
            msg(contentView, sel("addSubview:positioned:relativeTo:"), vfxView, -1L, Pointer.NULL);

            // Constraints to fill
            Pointer vfxLeading = msg(vfxView, sel("leadingAnchor"));
            Pointer contentLeading = msg(contentView, sel("leadingAnchor"));
            msg(msg(vfxLeading, sel("constraintEqualToAnchor:"), contentLeading), sel("setActive:"), 1L);

            Pointer vfxTrailing = msg(vfxView, sel("trailingAnchor"));
            Pointer contentTrailing = msg(contentView, sel("trailingAnchor"));
            msg(msg(vfxTrailing, sel("constraintEqualToAnchor:"), contentTrailing), sel("setActive:"), 1L);

            Pointer vfxTop = msg(vfxView, sel("topAnchor"));
            Pointer contentTop = msg(contentView, sel("topAnchor"));
            msg(msg(vfxTop, sel("constraintEqualToAnchor:"), contentTop), sel("setActive:"), 1L);

            Pointer vfxBottom = msg(vfxView, sel("bottomAnchor"));
            Pointer contentBottom = msg(contentView, sel("bottomAnchor"));
            msg(msg(vfxBottom, sel("constraintEqualToAnchor:"), contentBottom), sel("setActive:"), 1L);

            msg(contentView, sel("layoutSubtreeIfNeeded"));

            System.out.println("WindowBlur: Applied " + style.name + " material=" + currentMaterial + " alpha=" + currentAlpha);

        } catch (Exception e) {
            System.out.println("WindowBlur error: " + e.getMessage());
        }
    }
    
    public void setMaterial(Material material) {

        currentMaterial = material;
        if (currentBlurView != null) {
            msg(currentBlurView, sel("setMaterial:"), material.value);
            System.out.println("WindowBlur: Material changed to " + material);
        }

    }
    
    public void setAlpha(double alpha) {
        currentAlpha = Math.max(0.0, Math.min(1.0, alpha));
        if (currentBlurView != null) {
            msg(currentBlurView, sel("setAlphaValue:"), currentAlpha);
            System.out.println("WindowBlur: Alpha changed to " + currentAlpha);
        }
    }
}