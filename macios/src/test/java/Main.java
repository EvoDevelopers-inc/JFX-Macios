import evo.developers.ru.macios.HelperMacOS;
import evo.developers.ru.macios.core.WindowManager;
import evo.developers.ru.macios.effects.BlurEffectManager;
import evo.developers.ru.macios.effects.Material;
import evo.developers.ru.macios.permissions.Permission;
import evo.developers.ru.macios.permissions.PermissionStatus;
import evo.developers.ru.macios.ui.ThemeManager;
import evo.developers.ru.macios.ui.TitleBarManager;
import evo.developers.ru.macios.ux.dockbadge.DockBadgeManager;
import evo.developers.ru.macios.ux.haptic.HapticManager;
import evo.developers.ru.macios.ux.nowplaying.NowPlayingManager;
import evo.developers.ru.macios.ux.notification.NotificationManager;
import evo.developers.ru.macios.ux.sound.SoundManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class Main extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    private HelperMacOS helperMacOS;
    private Stage primaryStage;
    private Scene scene;
    private Label accentColorLabel;
    private Label darkModeLabel;
    private Slider alphaSlider;
    private ComboBox<BlurEffectManager.Style> styleCombo;
    private ComboBox<Material> materialCombo;
    private TextField badgeField;
    private TextField notificationTitleField;
    private TextField notificationMessageField;
    private ComboBox<String> soundCombo;
    private ToggleButton securityToggle;
    
    // Accent color
    private String currentAccentColor = "Blue";
    private Color accentColor = Color.web("#007AFF"); // Default Blue
    private Region colorIndicator; // Visual indicator for accent color
    private List<VBox> cards = new ArrayList<>(); // Store all cards for dynamic style updates
    private Timer accentColorCheckTimer; // Timer to check for accent color changes
    private Button minimizeBtn, zoomBtn, closeBtn; // Window control buttons
    private ScrollPane scrollPane; // Main scroll container (used as Liquid Glass clip region)

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        helperMacOS = HelperMacOS.getInstance(stage);

        stage.setTitle("JFX-Macios Demo");
        stage.initStyle(StageStyle.UNIFIED);


        // Initialize accent color from system
        String accent = ThemeManager.getAccentColor();
        if (accent != null) {
            currentAccentColor = accent;
        }
        accentColor = getAccentColorRGB(currentAccentColor);

        BorderPane root = new BorderPane();
        root.setBackground(Background.EMPTY);

        // Title bar
        HBox header = createTitleBar();
        header.setPrefHeight(50);
        header.setOnMousePressed(e -> helperMacOS.getTitleBarManager().startWindowDrag());

        root.setTop(header);

        // Main content with scroll
        scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);


        VBox mainContent = createMainContent();
        scrollPane.setContent(mainContent);

        root.setCenter(scrollPane);

        scene = new Scene(root, 1000, 700);
        scene.setFill(Color.TRANSPARENT);
        scene.getRoot().setStyle("-fx-font-family: 'SF Pro Display', 'Helvetica Neue', sans-serif;");
        
        // Add CSS styles for ScrollPane
        updateScrollPaneCSS();

        stage.setScene(scene);
        stage.setOnShown(e -> Platform.runLater(() -> {
            // Setup transparent titlebar
            helperMacOS.getTitleBarManager().setupTransparentTitlebar();
            helperMacOS.getTitleBarManager().enableWindowDrag();

            // Apply initial blur
            helperMacOS.getBlurEffect().apply(BlurEffectManager.Style.DARK_AQUA);

            // Update system info and apply accent color
            updateSystemInfo();
            applyAccentColor();

            // Start appearance listener with shorter interval to catch accent color changes
            ThemeManager.startAppearanceListener((isDark) -> {
                Platform.runLater(() -> {
                    updateSystemInfo();
                    applyAccentColor();
                    if (isDark) {
                        helperMacOS.getBlurEffect().apply(BlurEffectManager.Style.DARK_AQUA);
                        darkModeLabel.setText("🌙 Dark Mode");
                    } else {
                        helperMacOS.getBlurEffect().apply(BlurEffectManager.Style.LIGHT);
                        darkModeLabel.setText("☀️ Light Mode");
                    }
                    helperMacOS.getHapticManager().haptic(
                        HapticManager.HapticPattern.LEVEL_CHANGE,
                        HapticManager.HapticTime.NOW
                    );
                });
            }, 300); // Check every 300ms for faster response
            
            // Start separate timer to check for accent color changes
            startAccentColorChecker();
        }));

        stage.setOnHiding(e -> {
            ThemeManager.stopAppearanceListener();
            stopAccentColorChecker();
        });

        stage.show();
    }

    private HBox createTitleBar() {
        HBox titleBar = new HBox(15);
        titleBar.setPadding(new Insets(12, 20, 12, 20));
        titleBar.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("🎨 JFX-Macios Demo");
        title.setStyle(
            "-fx-text-fill: rgba(255,255,255,0.9);" +
            "-fx-font-size: 18px;" +
            "-fx-font-weight: bold;"
        );

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        minimizeBtn = createWindowButton("Minimize", "Minimize");
        minimizeBtn.setOnAction(e -> {
            helperMacOS.getWindowManager().minimize();
            helperMacOS.getHapticManager().haptic(
                HapticManager.HapticPattern.GENERIC,
                HapticManager.HapticTime.NOW
            );
        });

        zoomBtn = createWindowButton("Zoom", "Zoom");
        zoomBtn.setOnAction(e -> {
            helperMacOS.getWindowManager().zoom();
            helperMacOS.getHapticManager().haptic(
                HapticManager.HapticPattern.GENERIC,
                HapticManager.HapticTime.NOW
            );
        });

        closeBtn = createWindowButton("Close", "Close");
        closeBtn.setOnAction(e -> {
            helperMacOS.getWindowManager().close();
        });

        titleBar.getChildren().addAll(title, spacer, minimizeBtn, zoomBtn, closeBtn);
        return titleBar;
    }

    private Button createWindowButton(String icon, String tooltip) {
        Button btn = new Button(icon);
        btn.setTooltip(new Tooltip(tooltip));
        
        // Use accent color for buttons
        String accentRgb = String.format("rgba(%d,%d,%d,0.3)", 
            (int)(accentColor.getRed() * 255),
            (int)(accentColor.getGreen() * 255),
            (int)(accentColor.getBlue() * 255));
        
        String buttonStyle = 
            "-fx-background-color: " + accentRgb + ";" +
            "-fx-text-fill: rgba(255,255,255,0.9);" +
            "-fx-font-size: 14px;" +
            "-fx-font-weight: bold;" +
            "-fx-background-radius: 6;" +
            "-fx-padding: 6 12;" +
            "-fx-cursor: hand;" +
            "-fx-border-color: rgba(255,255,255,0.1);" +
            "-fx-border-radius: 6;" +
            "-fx-border-width: 1;";
        
        btn.setStyle(buttonStyle);
        
        // Setup hover effects
        setupWindowButtonHover(btn);

        return btn;
    }

    private VBox createMainContent() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(30));
        content.setAlignment(Pos.TOP_CENTER);
        
        cards.clear(); // Clear previous cards

        // Welcome card
        VBox welcomeCard = createCard("Welcome to JFX-Macios", "Demonstrating all macOS features");
        cards.add(welcomeCard);
        content.getChildren().add(welcomeCard);

        // System Info Card
        VBox systemCard = createSystemInfoCard();
        cards.add(systemCard);
        content.getChildren().add(systemCard);

        // Blur Effects Card
        VBox blurCard = createBlurEffectsCard();
        cards.add(blurCard);
        content.getChildren().add(blurCard);

        // Security Card
        VBox securityCard = createSecurityCard();
        cards.add(securityCard);
        content.getChildren().add(securityCard);

        // Dock Badge Card
        VBox dockCard = createDockBadgeCard();
        cards.add(dockCard);
        content.getChildren().add(dockCard);

        // Notifications Card
        VBox notificationCard = createNotificationCard();
        cards.add(notificationCard);
        content.getChildren().add(notificationCard);

        // Menu bar tray Card
        VBox trayCard = createTrayCard();
        cards.add(trayCard);
        content.getChildren().add(trayCard);

        // Native menus Card (app menu bar + Dock menu)
        VBox menuCard = createMenuCard();
        cards.add(menuCard);
        content.getChildren().add(menuCard);

        // Toolbar + context menu + alerts Card
        VBox nativeUiCard = createNativeUiCard();
        cards.add(nativeUiCard);
        content.getChildren().add(nativeUiCard);

        // Keychain Card
        VBox keychainCard = createKeychainCard();
        cards.add(keychainCard);
        content.getChildren().add(keychainCard);

        // User activity (Handoff / Spotlight) Card
        VBox activityCard = createActivityCard();
        cards.add(activityCard);
        content.getChildren().add(activityCard);

        // Share + window tabs Card
        VBox shareTabsCard = createShareTabsCard();
        cards.add(shareTabsCard);
        content.getChildren().add(shareTabsCard);

        // Liquid Glass Card
        VBox glassCard = createLiquidGlassCard();
        cards.add(glassCard);
        content.getChildren().add(glassCard);

        // Metal layer Card
        VBox metalCard = createMetalCard();
        cards.add(metalCard);
        content.getChildren().add(metalCard);

        // WebView (WKWebView) Card
        VBox webViewCard = createWebViewCard();
        cards.add(webViewCard);
        content.getChildren().add(webViewCard);

        // Permissions Card
        VBox permissionsCard = createPermissionsCard();
        cards.add(permissionsCard);
        content.getChildren().add(permissionsCard);

        // Push (APNs) Card
        VBox pushCard = createPushCard();
        cards.add(pushCard);
        content.getChildren().add(pushCard);

        // Touch ID Card
        VBox biometricCard = createBiometricCard();
        cards.add(biometricCard);
        content.getChildren().add(biometricCard);

        // Now Playing Card
        VBox nowPlayingCard = createNowPlayingCard();
        cards.add(nowPlayingCard);
        content.getChildren().add(nowPlayingCard);

        // Sounds Card
        VBox soundsCard = createSoundsCard();
        cards.add(soundsCard);
        content.getChildren().add(soundsCard);

        // Haptic Feedback Card
        VBox hapticCard = createHapticCard();
        cards.add(hapticCard);
        content.getChildren().add(hapticCard);

        return content;
    }

    private VBox createCard(String title, String subtitle) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(20));
        
        // Use accent color for border
        String accentRgb = String.format("rgba(%d,%d,%d,0.4)", 
            (int)(accentColor.getRed() * 255),
            (int)(accentColor.getGreen() * 255),
            (int)(accentColor.getBlue() * 255));
        
        card.setStyle(
            "-fx-background-color: rgba(255,255,255,0.1);" +
            "-fx-background-radius: 12;" +
            "-fx-border-color: " + accentRgb + ";" +
            "-fx-border-radius: 12;" +
            "-fx-border-width: 1;"
        );
        card.setMaxWidth(900);

        Label titleLabel = new Label(title);
        titleLabel.setStyle(
            "-fx-text-fill: rgba(255,255,255,0.95);" +
            "-fx-font-size: 24px;" +
            "-fx-font-weight: bold;"
        );

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setStyle(
            "-fx-text-fill: rgba(255,255,255,0.7);" +
            "-fx-font-size: 14px;"
        );

        card.getChildren().addAll(titleLabel, subtitleLabel);
        return card;
    }

    private VBox createSystemInfoCard() {
        VBox card = createCard("🖥️ System Information", "Current system appearance and accent color");

        VBox infoBox = new VBox(15);
        infoBox.setPadding(new Insets(15, 0, 0, 0));

        darkModeLabel = new Label();
        darkModeLabel.setStyle(
            "-fx-text-fill: rgba(255,255,255,0.9);" +
            "-fx-font-size: 16px;"
        );

        // Accent color with visual indicator
        HBox accentBox = new HBox(10);
        accentBox.setAlignment(Pos.CENTER_LEFT);
        
        accentColorLabel = new Label();
        accentColorLabel.setStyle(
            "-fx-text-fill: rgba(255,255,255,0.9);" +
            "-fx-font-size: 16px;"
        );
        
        // Color indicator box
        this.colorIndicator = new Region();
        this.colorIndicator.setPrefSize(30, 30);
        this.colorIndicator.setStyle(
            "-fx-background-radius: 6;" +
            "-fx-background-color: " + toHexColor(accentColor) + ";"
        );
        
        accentBox.getChildren().addAll(accentColorLabel, this.colorIndicator);

        Label infoLabel = new Label("💡 All buttons and borders use your system accent color!");
        infoLabel.setStyle(
            "-fx-text-fill: rgba(255,255,255,0.7);" +
            "-fx-font-size: 12px;"
        );

        Button refreshBtn = new Button("🔄 Refresh");
        styleButton(refreshBtn);
        refreshBtn.setOnAction(e -> {
            updateSystemInfo();
            applyAccentColor();
            helperMacOS.getHapticManager().haptic(
                HapticManager.HapticPattern.GENERIC,
                HapticManager.HapticTime.NOW
            );
        });

        infoBox.getChildren().addAll(darkModeLabel, accentBox, infoLabel, refreshBtn);
        card.getChildren().add(infoBox);

        return card;
    }
    
    /**
     * Convert Color to hex string
     */
    private String toHexColor(Color color) {
        return String.format("#%02X%02X%02X",
            (int)(color.getRed() * 255),
            (int)(color.getGreen() * 255),
            (int)(color.getBlue() * 255));
    }

    private void updateSystemInfo() {
        boolean isDark = ThemeManager.isDarkMode();
        darkModeLabel.setText(isDark ? "🌙 Dark Mode" : "☀️ Light Mode");

        String accent = ThemeManager.getAccentColor();
        if (accent != null) {
            // Check if accent color changed
            if (!accent.equals(currentAccentColor)) {
                currentAccentColor = accent;
                applyAccentColor(); // Apply new color immediately
            }
            accentColorLabel.setText("🎨 Accent Color: " + accent);
        } else {
            accentColorLabel.setText("🎨 Accent Color: Unknown");
        }
    }
    
    /**
     * Get RGB color for macOS accent color name
     */
    private Color getAccentColorRGB(String colorName) {
        if (colorName == null) colorName = "Blue";
        
        switch (colorName) {
            case "Blue": return Color.web("#007AFF");
            case "Purple": return Color.web("#AF52DE");
            case "Pink": return Color.web("#FF2D92");
            case "Red": return Color.web("#FF3B30");
            case "Orange": return Color.web("#FF9500");
            case "Yellow": return Color.web("#FFCC00");
            case "Green": return Color.web("#34C759");
            case "Graphite": return Color.web("#8E8E93");
            default: return Color.web("#007AFF"); // Default Blue
        }
    }
    
    /**
     * Apply accent color to all UI elements
     */
    private void applyAccentColor() {
        accentColor = getAccentColorRGB(currentAccentColor);
        
        Platform.runLater(() -> {
            // Update color indicator if it exists
            if (colorIndicator != null) {
                colorIndicator.setStyle(
                    "-fx-background-radius: 6;" +
                    "-fx-background-color: " + toHexColor(accentColor) + ";"
                );
            }
            
            // Update all card borders
            updateCardBorders();
            
            // Update window control buttons
            updateWindowButtons();
            
            // Update ScrollPane styles
            updateScrollPaneCSS();
            
            // Re-style all buttons in the scene
            updateButtonStyles();
        });
    }
    
    /**
     * Update window control buttons with current accent color
     */
    private void updateWindowButtons() {
        if (minimizeBtn == null || zoomBtn == null || closeBtn == null) return;
        
        String accentRgb = String.format("rgba(%d,%d,%d,0.3)", 
            (int)(accentColor.getRed() * 255),
            (int)(accentColor.getGreen() * 255),
            (int)(accentColor.getBlue() * 255));
        
        String buttonStyle = 
            "-fx-background-color: " + accentRgb + ";" +
            "-fx-text-fill: rgba(255,255,255,0.9);" +
            "-fx-font-size: 14px;" +
            "-fx-font-weight: bold;" +
            "-fx-background-radius: 6;" +
            "-fx-padding: 6 12;" +
            "-fx-cursor: hand;" +
            "-fx-border-color: rgba(255,255,255,0.1);" +
            "-fx-border-radius: 6;" +
            "-fx-border-width: 1;";
        
        minimizeBtn.setStyle(buttonStyle);
        zoomBtn.setStyle(buttonStyle);
        closeBtn.setStyle(buttonStyle);
        
        // Re-apply hover effects
        setupWindowButtonHover(minimizeBtn);
        setupWindowButtonHover(zoomBtn);
        setupWindowButtonHover(closeBtn);
    }
    
    /**
     * Setup hover effects for window button
     */
    private void setupWindowButtonHover(Button btn) {
        String accentRgb = String.format("rgba(%d,%d,%d,0.3)", 
            (int)(accentColor.getRed() * 255),
            (int)(accentColor.getGreen() * 255),
            (int)(accentColor.getBlue() * 255));
        
        String accentRgbHover = String.format("rgba(%d,%d,%d,0.5)", 
            (int)(accentColor.getRed() * 255),
            (int)(accentColor.getGreen() * 255),
            (int)(accentColor.getBlue() * 255));
        
        String normalStyle = 
            "-fx-background-color: " + accentRgb + ";" +
            "-fx-text-fill: rgba(255,255,255,0.9);" +
            "-fx-font-size: 14px;" +
            "-fx-font-weight: bold;" +
            "-fx-background-radius: 6;" +
            "-fx-padding: 6 12;" +
            "-fx-cursor: hand;" +
            "-fx-border-color: rgba(255,255,255,0.1);" +
            "-fx-border-radius: 6;" +
            "-fx-border-width: 1;";
        
        String hoverStyle = 
            "-fx-background-color: " + accentRgbHover + ";" +
            "-fx-text-fill: rgba(255,255,255,1);" +
            "-fx-font-size: 14px;" +
            "-fx-font-weight: bold;" +
            "-fx-background-radius: 6;" +
            "-fx-padding: 6 12;" +
            "-fx-cursor: hand;" +
            "-fx-border-color: rgba(255,255,255,0.2);" +
            "-fx-border-radius: 6;" +
            "-fx-border-width: 1;";
        
        btn.setOnMouseEntered(e -> btn.setStyle(hoverStyle));
        btn.setOnMouseExited(e -> btn.setStyle(normalStyle));
        btn.setOnMousePressed(e -> {
            btn.setStyle(
                "-fx-background-color: " + accentRgbHover + ";" +
                "-fx-text-fill: rgba(255,255,255,1);" +
                "-fx-font-size: 14px;" +
                "-fx-font-weight: bold;" +
                "-fx-background-radius: 6;" +
                "-fx-padding: 6 12;" +
                "-fx-cursor: hand;" +
                "-fx-border-color: rgba(255,255,255,0.3);" +
                "-fx-border-radius: 6;" +
                "-fx-border-width: 1;"
            );
        });
    }
    
    /**
     * Update borders of all cards with current accent color
     */
    private void updateCardBorders() {
        String accentRgb = String.format("rgba(%d,%d,%d,0.4)", 
            (int)(accentColor.getRed() * 255),
            (int)(accentColor.getGreen() * 255),
            (int)(accentColor.getBlue() * 255));
        
        String cardStyle = 
            "-fx-background-color: rgba(255,255,255,0.1);" +
            "-fx-background-radius: 12;" +
            "-fx-border-color: " + accentRgb + ";" +
            "-fx-border-radius: 12;" +
            "-fx-border-width: 1;";
        
        for (VBox card : cards) {
            card.setStyle(cardStyle);
        }
    }
    
    /**
     * Start timer to periodically check for accent color changes
     */
    private void startAccentColorChecker() {
        if (accentColorCheckTimer != null) {
            accentColorCheckTimer.cancel();
        }
        
        accentColorCheckTimer = new Timer("AccentColorChecker", true);
        accentColorCheckTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    String newAccent = ThemeManager.getAccentColor();
                    if (newAccent != null && !newAccent.equals(currentAccentColor)) {
                        currentAccentColor = newAccent;
                        updateSystemInfo();
                        applyAccentColor();
                    }
                });
            }
        }, 500, 500); // Check every 500ms
    }
    
    /**
     * Stop accent color checker timer
     */
    private void stopAccentColorChecker() {
        if (accentColorCheckTimer != null) {
            accentColorCheckTimer.cancel();
            accentColorCheckTimer = null;
        }
    }
    
    /**
     * Update styles for all buttons in the scene
     * Note: Buttons are styled dynamically when created, so accent color is applied automatically
     */
    private void updateButtonStyles() {
        // Buttons use accentColor variable which is updated in applyAccentColor()
        // So new buttons will automatically use the updated color
    }

    private VBox createBlurEffectsCard() {
        VBox card = createCard("✨ Blur Effects", "Control blur style, material, and transparency");

        VBox controls = new VBox(15);
        controls.setPadding(new Insets(15, 0, 0, 0));

        // Style selector
        HBox styleBox = new HBox(10);
        styleBox.setAlignment(Pos.CENTER_LEFT);
        Label styleLabel = createLabel("Style:");
        styleCombo = new ComboBox<>();
        styleCombo.getItems().addAll(
            BlurEffectManager.Style.LIGHT,
            BlurEffectManager.Style.DARK,
            BlurEffectManager.Style.AQUA,
            BlurEffectManager.Style.DARK_AQUA
        );
        styleCombo.setValue(BlurEffectManager.Style.DARK_AQUA);
        styleCombo.setStyle(
            "-fx-background-color: rgba(255,255,255,0.15);" +
            "-fx-text-fill: white;"
        );
        styleCombo.setOnAction(e -> {
            helperMacOS.getBlurEffect().apply(styleCombo.getValue());
            helperMacOS.getHapticManager().haptic(
                HapticManager.HapticPattern.LEVEL_CHANGE,
                HapticManager.HapticTime.NOW
            );
        });
        HBox.setHgrow(styleCombo, Priority.ALWAYS);
        styleBox.getChildren().addAll(styleLabel, styleCombo);

        // Material selector
        HBox materialBox = new HBox(10);
        materialBox.setAlignment(Pos.CENTER_LEFT);
        Label materialLabel = createLabel("Material:");
        materialCombo = new ComboBox<>();
        materialCombo.getItems().addAll(
            Material.SIDEBAR,
            Material.MENU,
            Material.POPOVER,
            Material.TITLEBAR,
            Material.SHEET,
            Material.HUD,
            Material.CONTENT_BACKGROUND
        );
        materialCombo.setValue(Material.SIDEBAR);
        materialCombo.setStyle(
            "-fx-background-color: rgba(255,255,255,0.15);" +
            "-fx-text-fill: white;"
        );
        materialCombo.setOnAction(e -> {
            helperMacOS.getBlurEffect().setMaterial(materialCombo.getValue());
            helperMacOS.getHapticManager().haptic(
                HapticManager.HapticPattern.ALIGNMENT,
                HapticManager.HapticTime.NOW
            );
        });
        HBox.setHgrow(materialCombo, Priority.ALWAYS);
        materialBox.getChildren().addAll(materialLabel, materialCombo);

        // Alpha slider
        HBox alphaBox = new HBox(10);
        alphaBox.setAlignment(Pos.CENTER_LEFT);
        Label alphaLabel = createLabel("Alpha:");
        alphaSlider = new Slider(0.0, 1.0, 1.0);
        alphaSlider.setShowTickLabels(true);
        alphaSlider.setShowTickMarks(true);
        alphaSlider.setMajorTickUnit(0.25);
        alphaSlider.setStyle(
            "-fx-control-inner-background: rgba(255,255,255,0.2);"
        );
        Label alphaValueLabel = new Label("1.0");
        alphaValueLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.9);");
        alphaSlider.valueProperty().addListener((obs, old, val) -> {
            double alpha = val.doubleValue();
            alphaValueLabel.setText(String.format("%.2f", alpha));
            helperMacOS.getBlurEffect().setAlpha(alpha);
        });
        HBox.setHgrow(alphaSlider, Priority.ALWAYS);
        alphaBox.getChildren().addAll(alphaLabel, alphaSlider, alphaValueLabel);

        controls.getChildren().addAll(styleBox, materialBox, alphaBox);
        card.getChildren().add(controls);

        return card;
    }

    private VBox createTrayCard() {
        VBox card = createCard("🧭 Menu Bar Tray", "NSStatusBar item with a native menu");

        VBox controls = new VBox(15);
        controls.setPadding(new Insets(15, 0, 0, 0));

        Label status = new Label("Tray: hidden");
        status.setStyle("-fx-text-fill: rgba(255,255,255,0.9); -fx-font-size: 14px;");

        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_LEFT);

        Button showBtn = new Button("Show tray icon");
        styleButton(showBtn);
        Button removeBtn = new Button("Remove tray");
        styleButton(removeBtn);

        showBtn.setOnAction(e -> {
            helperMacOS.getStatusBar().show("◆ JFX");
            helperMacOS.getStatusBar().setSymbolIcon("sparkles");
            helperMacOS.getStatusBar().addItem("Say hello", () ->
                NotificationManager.sendNotification("JFX-Macios", "Hello from the tray!", UUID.randomUUID().toString()));
            helperMacOS.getStatusBar().addItem("Bring window to front", () -> primaryStage.toFront());
            helperMacOS.getStatusBar().addSeparator();
            helperMacOS.getStatusBar().addQuitItem("Quit");
            status.setText("Tray: shown (click the ◆ in the menu bar)");
        });
        removeBtn.setOnAction(e -> {
            helperMacOS.getStatusBar().remove();
            status.setText("Tray: hidden");
        });

        buttons.getChildren().addAll(showBtn, removeBtn);
        controls.getChildren().addAll(status, buttons);
        card.getChildren().add(controls);
        return card;
    }

    private VBox createMenuCard() {
        VBox card = createCard("📋 Native Menus", "Custom app menu bar + Dock right-click menu");

        VBox controls = new VBox(15);
        controls.setPadding(new Insets(15, 0, 0, 0));

        Label status = new Label("Nothing installed yet");
        status.setStyle("-fx-text-fill: rgba(255,255,255,0.85); -fx-font-size: 13px;");

        Runnable notify = () -> Platform.runLater(() ->
            status.setText("Last action fired at " + java.time.LocalTime.now().withNano(0)));

        Button menuBarBtn = new Button("Install app menu bar");
        styleButton(menuBarBtn);
        menuBarBtn.setOnAction(e -> {
            helperMacOS.getMenuBar()
                .appName("JFX-Macios")
                .addMenu("File", m -> m
                    .item("New Window", "n", () -> { primaryStage.toFront(); notify.run(); })
                    .item("Send Notification", () -> {
                        NotificationManager.sendNotification("JFX-Macios", "Hello from the menu bar!",
                            UUID.randomUUID().toString());
                        notify.run();
                    })
                    .separator()
                    .appItem("Close Window", "performClose:", "w"))
                .addMenu("View", m -> m
                    .item("Bring to Front", () -> { primaryStage.toFront(); notify.run(); })
                    .item("Minimize", "m", () -> { primaryStage.setIconified(true); notify.run(); }))
                .addMenu("Help", m -> m
                    .item("About JFX-Macios", () -> {
                        NotificationManager.sendNotification("JFX-Macios",
                            "JavaFX + native macOS integration", UUID.randomUUID().toString());
                        notify.run();
                    }))
                .apply();
            status.setText("App menu bar installed — check the top menu bar");
        });

        Button dockBtn = new Button("Install Dock menu");
        styleButton(dockBtn);
        dockBtn.setOnAction(e -> {
            helperMacOS.getDockMenu().setMenu(m -> m
                .item("New Window", () -> { primaryStage.toFront(); notify.run(); })
                .separator()
                .item("Say Hello", () -> {
                    NotificationManager.sendNotification("JFX-Macios", "Hello from the Dock menu!",
                        UUID.randomUUID().toString());
                    notify.run();
                })
                .item("Bring to Front", () -> { primaryStage.toFront(); notify.run(); }));
            status.setText("Dock menu installed — right-click the Dock icon");
        });

        HBox buttons = new HBox(10, menuBarBtn, dockBtn);
        buttons.setAlignment(Pos.CENTER_LEFT);

        controls.getChildren().addAll(status, buttons);
        card.getChildren().add(controls);
        return card;
    }

    private VBox createNativeUiCard() {
        VBox card = createCard("🧰 Toolbar / Context menu / Alerts", "NSToolbar, right-click NSMenu, and NSAlert sheets");

        VBox controls = new VBox(15);
        controls.setPadding(new Insets(15, 0, 0, 0));

        Label status = new Label("Try the buttons below");
        status.setStyle("-fx-text-fill: rgba(255,255,255,0.85); -fx-font-size: 13px;");

        Button toolbarBtn = new Button("Install toolbar");
        styleButton(toolbarBtn);
        toolbarBtn.setOnAction(e -> {
            helperMacOS.getToolbar()
                .addButton("new", "New", "plus", () -> status.setText("Toolbar: New"))
                .addButton("share", "Share", "square.and.arrow.up", () -> status.setText("Toolbar: Share"))
                .addButton("delete", "Delete", "trash", () -> status.setText("Toolbar: Delete"))
                .apply();
            status.setText("Toolbar installed — see the window title bar");
        });

        Button removeToolbarBtn = new Button("Remove toolbar");
        styleButton(removeToolbarBtn);
        removeToolbarBtn.setOnAction(e -> {
            helperMacOS.getToolbar().remove();
            status.setText("Toolbar removed");
        });

        Label contextArea = new Label("⤵ Right-click anywhere in this box for a native context menu");
        contextArea.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-padding: 20;"
            + "-fx-background-color: rgba(255,255,255,0.08); -fx-background-radius: 10;");
        contextArea.setMaxWidth(Double.MAX_VALUE);
        contextArea.setOnContextMenuRequested(e -> helperMacOS.getContextMenu().show(m -> m
            .item("Copy", () -> status.setText("Context: Copy"))
            .item("Paste", () -> status.setText("Context: Paste"))
            .separator()
            .item("Delete", () -> status.setText("Context: Delete"))));

        Button sheetBtn = new Button("Show alert sheet");
        styleButton(sheetBtn);
        sheetBtn.setOnAction(e -> helperMacOS.getAlert().showSheet(
            "Delete this item?",
            "This action cannot be undone.",
            evo.developers.ru.macios.ux.alert.AlertManager.Style.WARNING,
            new String[]{ "Delete", "Cancel" },
            index -> status.setText("Sheet result: " + (index == 0 ? "Delete" : "Cancel"))));

        HBox toolbarButtons = new HBox(10, toolbarBtn, removeToolbarBtn, sheetBtn);
        toolbarButtons.setAlignment(Pos.CENTER_LEFT);

        controls.getChildren().addAll(status, toolbarButtons, contextArea);
        card.getChildren().add(controls);
        return card;
    }

    private VBox createLiquidGlassCard() {
        VBox card = createCard("💧 Liquid Glass", "Real NSGlassEffectView via a child-window overlay");

        VBox controls = new VBox(15);
        controls.setPadding(new Insets(15, 0, 0, 0));

        boolean supported = helperMacOS.getLiquidGlass().isNativeGlassSupported();
        Label support = new Label(supported
            ? "✅ NSGlassEffectView available (macOS 26+)"
            : "⚠️ Falling back to NSVisualEffectView (macOS < 26)");
        support.setStyle("-fx-text-fill: rgba(255,255,255,0.9); -fx-font-size: 13px;");

        // Placeholder region whose on-screen bounds define the glass area.
        Region glassArea = new Region();
        glassArea.setMinHeight(160);
        glassArea.setPrefHeight(160);
        glassArea.setMaxWidth(Double.MAX_VALUE);
        glassArea.setStyle("-fx-border-color: rgba(255,255,255,0.25); -fx-border-width: 1;"
            + "-fx-border-style: dashed; -fx-border-radius: 18;");

        // Colorful backdrop so the glass has something vivid to refract/blur (otherwise it just
        // samples the dark card behind it and looks like a flat gray rectangle).
        Region backdrop = new Region();
        backdrop.setMaxWidth(Double.MAX_VALUE);
        backdrop.setStyle("-fx-background-radius: 18;"
            + "-fx-background-color: linear-gradient(to bottom right,"
            + " #ff6ec4 0%, #7873f5 45%, #4ade80 100%);");

        Label hint = new Label("The glass panel overlays this area");
        hint.setStyle("-fx-text-fill: rgba(255,255,255,0.85); -fx-font-size: 12px;"
            + "-fx-font-weight: bold;");
        StackPane areaStack = new StackPane(backdrop, glassArea, hint);

        Button attachBtn = new Button("Attach glass");
        styleButton(attachBtn);
        attachBtn.setOnAction(e -> {
            helperMacOS.getLiquidGlass()
                .setCornerRadius(18)
                .setLightAppearance(true)
                .setTint(1.0, 1.0, 1.0, 0.08)
                .attach(glassArea, scrollPane);
            support.setText(helperMacOS.getLiquidGlass().isUsingNativeGlass()
                ? "💧 Native Liquid Glass attached"
                : "💧 Blur fallback attached");
        });

        Button detachBtn = new Button("Detach");
        styleButton(detachBtn);
        detachBtn.setOnAction(e -> helperMacOS.getLiquidGlass().detach());

        HBox buttons = new HBox(10, attachBtn, detachBtn);
        buttons.setAlignment(Pos.CENTER_LEFT);

        controls.getChildren().addAll(support, areaStack, buttons);
        card.getChildren().add(controls);
        return card;
    }

    private VBox createWebViewCard() {
        VBox card = createCard("\uD83C\uDF10 WebView (WKWebView)",
            "Heavy native Apple WebKit view overlaid on a JavaFX node");

        VBox controls = new VBox(12);
        controls.setPadding(new Insets(15, 0, 0, 0));

        boolean supported = helperMacOS.getWebView().isSupported();
        Label status = new Label(supported ? "✅ WKWebView available" : "⚠️ WebKit not available");
        status.setStyle("-fx-text-fill: rgba(255,255,255,0.9); -fx-font-size: 13px;");

        Region webArea = new Region();
        webArea.setMinHeight(240);
        webArea.setPrefHeight(240);
        webArea.setMaxWidth(Double.MAX_VALUE);
        webArea.setStyle("-fx-border-color: rgba(255,255,255,0.25); -fx-border-width: 1;"
            + "-fx-border-style: dashed; -fx-border-radius: 12;");

        Label hint = new Label("The WKWebView renders over this area");
        hint.setStyle("-fx-text-fill: rgba(255,255,255,0.6); -fx-font-size: 12px;");
        StackPane areaStack = new StackPane(webArea, hint);

        TextField urlField = new TextField("https://www.apple.com");
        urlField.setPromptText("https://…");
        HBox.setHgrow(urlField, Priority.ALWAYS);

        Button attach = new Button("Attach");
        styleButton(attach);
        attach.setOnAction(e -> {
            helperMacOS.getWebView().attach(webArea, scrollPane);
            helperMacOS.getWebView().loadUrl(urlField.getText());
            status.setText("🌐 Loading " + urlField.getText());
        });

        Button go = new Button("Load");
        styleButton(go);
        go.setOnAction(e -> {
            helperMacOS.getWebView().loadUrl(urlField.getText());
            status.setText("🌐 Loading " + urlField.getText());
        });

        Button back = new Button("◀");
        styleButton(back);
        back.setOnAction(e -> helperMacOS.getWebView().goBack());

        Button fwd = new Button("▶");
        styleButton(fwd);
        fwd.setOnAction(e -> helperMacOS.getWebView().goForward());

        Button reload = new Button("↻");
        styleButton(reload);
        reload.setOnAction(e -> helperMacOS.getWebView().reload());

        Button stop = new Button("Detach");
        styleButton(stop);
        stop.setOnAction(e -> {
            helperMacOS.getWebView().detach();
            status.setText(supported ? "✅ WKWebView available" : "⚠️ WebKit not available");
        });

        HBox urlRow = new HBox(10, urlField, go);
        urlRow.setAlignment(Pos.CENTER_LEFT);

        HBox buttons = new HBox(10, attach, back, fwd, reload, stop);
        buttons.setAlignment(Pos.CENTER_LEFT);

        controls.getChildren().addAll(status, areaStack, urlRow, buttons);
        card.getChildren().add(controls);
        return card;
    }

    private VBox createMetalCard() {
        VBox card = createCard("🎇 Metal Layer", "Real CAMetalLayer overlay with an animated render loop");

        VBox controls = new VBox(15);
        controls.setPadding(new Insets(15, 0, 0, 0));

        boolean supported = helperMacOS.getMetal().isMetalSupported();
        Label status = new Label(supported ? "✅ Metal device available" : "⚠️ Metal not available");
        status.setStyle("-fx-text-fill: rgba(255,255,255,0.9); -fx-font-size: 13px;");

        Region metalArea = new Region();
        metalArea.setMinHeight(160);
        metalArea.setPrefHeight(160);
        metalArea.setMaxWidth(Double.MAX_VALUE);
        metalArea.setStyle("-fx-border-color: rgba(255,255,255,0.25); -fx-border-width: 1;"
            + "-fx-border-style: dashed; -fx-border-radius: 12;");

        Label hint = new Label("The Metal surface renders over this area");
        hint.setStyle("-fx-text-fill: rgba(255,255,255,0.6); -fx-font-size: 12px;");
        StackPane areaStack = new StackPane(metalArea, hint);

        Button startBtn = new Button("Start");
        styleButton(startBtn);
        startBtn.setOnAction(e -> {
            helperMacOS.getMetal().attach(metalArea, scrollPane);
            status.setText("🎇 Metal layer rendering");
        });

        Button stopBtn = new Button("Stop");
        styleButton(stopBtn);
        stopBtn.setOnAction(e -> {
            helperMacOS.getMetal().detach();
            status.setText(supported ? "✅ Metal device available" : "⚠️ Metal not available");
        });

        HBox buttons = new HBox(10, startBtn, stopBtn);
        buttons.setAlignment(Pos.CENTER_LEFT);

        controls.getChildren().addAll(status, areaStack, buttons);
        card.getChildren().add(controls);
        return card;
    }

    private VBox createPermissionsCard() {
        VBox card = createCard("🔐 Permissions", "Check / request macOS privacy permissions (TCC)");

        VBox controls = new VBox(10);
        controls.setPadding(new Insets(15, 0, 0, 0));

        Permission[] perms = {
            Permission.CAMERA, Permission.MICROPHONE, Permission.SCREEN_RECORDING,
            Permission.ACCESSIBILITY, Permission.CONTACTS, Permission.CALENDAR,
            Permission.REMINDERS, Permission.PHOTOS, Permission.NOTIFICATIONS
        };

        for (Permission p : perms) {
            Label name = new Label(p.name());
            name.setStyle("-fx-text-fill: rgba(255,255,255,0.9); -fx-font-size: 13px; -fx-font-weight: bold;");
            name.setPrefWidth(160);

            Label statusLabel = new Label(statusText(helperMacOS.getPermissions().status(p)));
            statusLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 12px;");
            statusLabel.setPrefWidth(150);

            Button check = new Button("Check");
            styleButton(check);
            check.setOnAction(e -> statusLabel.setText(statusText(helperMacOS.getPermissions().status(p))));

            Button request = new Button("Request");
            styleButton(request);
            request.setOnAction(e -> helperMacOS.getPermissions().request(p, granted ->
                statusLabel.setText((granted ? "✅ granted" : "❌ denied")
                    + " (" + statusText(helperMacOS.getPermissions().status(p)) + ")")));

            Button settings = new Button("Settings");
            styleButton(settings);
            settings.setOnAction(e -> helperMacOS.getPermissions().openSettings(p));

            HBox row = new HBox(10, name, statusLabel, check, request, settings);
            row.setAlignment(Pos.CENTER_LEFT);
            controls.getChildren().add(row);
        }

        card.getChildren().add(controls);
        return card;
    }

    private String statusText(PermissionStatus s) {
        switch (s) {
            case GRANTED:        return "✅ granted";
            case DENIED:         return "❌ denied";
            case RESTRICTED:     return "🚫 restricted";
            case NOT_DETERMINED: return "❔ not determined";
            default:             return "… unknown (async)";
        }
    }

    private VBox createPushCard() {
        VBox card = createCard("📡 Push (APNs)", "Register for remote notifications and get the device token");

        VBox controls = new VBox(12);
        controls.setPadding(new Insets(15, 0, 0, 0));

        Label status = new Label("Not registered");
        status.setStyle("-fx-text-fill: rgba(255,255,255,0.9); -fx-font-size: 13px;");

        TextField tokenField = new TextField();
        tokenField.setEditable(false);
        tokenField.setPromptText("APNs device token appears here");
        tokenField.setStyle("-fx-font-size: 12px;");

        Button registerBtn = new Button("Register");
        styleButton(registerBtn);
        registerBtn.setOnAction(e -> {
            status.setText("Registering…");
            helperMacOS.getPush()
                .onToken(hex -> { tokenField.setText(hex); status.setText("✅ Registered"); })
                .onError(err -> status.setText("❌ " + err))
                .onRemoteNotification(payload -> status.setText("📩 Push received"))
                .register();
        });

        Button unregisterBtn = new Button("Unregister");
        styleButton(unregisterBtn);
        unregisterBtn.setOnAction(e -> {
            helperMacOS.getPush().unregister();
            status.setText("Unregistered");
        });

        HBox buttons = new HBox(10, registerBtn, unregisterBtn);
        buttons.setAlignment(Pos.CENTER_LEFT);

        Label note = new Label("Requires a signed .app with the aps-environment entitlement — "
            + "an unsigned dev launch usually reports a registration error.");
        note.setWrapText(true);
        note.setStyle("-fx-text-fill: rgba(255,255,255,0.55); -fx-font-size: 11px;");

        controls.getChildren().addAll(status, tokenField, buttons, note);
        card.getChildren().add(controls);
        return card;
    }

    private VBox createShareTabsCard() {
        VBox card = createCard("📤 Share / Window tabs", "NSSharingServicePicker + native NSWindow tabbing");

        VBox controls = new VBox(15);
        controls.setPadding(new Insets(15, 0, 0, 0));

        Label status = new Label("Try sharing or open a tabbed window");
        status.setStyle("-fx-text-fill: rgba(255,255,255,0.85); -fx-font-size: 13px;");

        Button shareTextBtn = new Button("Share text");
        styleButton(shareTextBtn);
        shareTextBtn.setOnAction(e -> {
            helperMacOS.getShare().shareText("Hello from JFX-Macios! Native macOS integration for JavaFX.");
            status.setText("Share sheet opened (text)");
        });

        Button shareUrlBtn = new Button("Share URL");
        styleButton(shareUrlBtn);
        shareUrlBtn.setOnAction(e -> {
            helperMacOS.getShare().shareURL("https://github.com/EvoDevelopers-inc/JFX-Macios");
            status.setText("Share sheet opened (URL)");
        });

        Button newTabBtn = new Button("Open window as tab");
        styleButton(newTabBtn);
        newTabBtn.setOnAction(e -> {
            tabCounter++;
            Stage tab = new Stage();
            tab.setTitle("JFX-Macios Tab " + tabCounter);
            Label content = new Label("Tab window #" + tabCounter);
            content.setStyle("-fx-text-fill: white; -fx-font-size: 20px;");
            StackPane pane = new StackPane(content);
            pane.setStyle("-fx-background-color: #202433;");
            tab.setScene(new Scene(pane, 520, 380));
            tab.setOnShown(ev -> Platform.runLater(() -> {
                helperMacOS.getWindowTab().addTab(tab);
                status.setText("Added tab (total: " + helperMacOS.getWindowTab().getTabCount() + ")");
            }));
            tab.show();
        });

        Button tabBarBtn = new Button("Toggle tab bar");
        styleButton(tabBarBtn);
        tabBarBtn.setOnAction(e -> helperMacOS.getWindowTab().toggleTabBar());

        HBox row1 = new HBox(10, shareTextBtn, shareUrlBtn);
        row1.setAlignment(Pos.CENTER_LEFT);
        HBox row2 = new HBox(10, newTabBtn, tabBarBtn);
        row2.setAlignment(Pos.CENTER_LEFT);

        controls.getChildren().addAll(status, row1, row2);
        card.getChildren().add(controls);
        return card;
    }

    private VBox createKeychainCard() {
        VBox card = createCard("🔑 Keychain", "Securely store secrets via Security.framework");

        VBox controls = new VBox(15);
        controls.setPadding(new Insets(15, 0, 0, 0));

        final String service = "ru.evo.jfxmacios.demo";
        final String account = "demoToken";

        TextField valueField = new TextField("s3cr3t-token");
        valueField.setPromptText("Secret value");
        valueField.setStyle("-fx-background-color: rgba(255,255,255,0.15); -fx-text-fill: white;");

        Label status = new Label("Not stored");
        status.setStyle("-fx-text-fill: rgba(255,255,255,0.85); -fx-font-size: 13px;");

        Button storeBtn = new Button("Store");
        styleButton(storeBtn);
        storeBtn.setOnAction(e -> {
            boolean ok = evo.developers.ru.macios.security.KeyChainManager
                .keychainStore(service, account, valueField.getText());
            status.setText(ok ? "✅ Stored in Keychain" : "❌ Store failed");
        });

        Button getBtn = new Button("Read");
        styleButton(getBtn);
        getBtn.setOnAction(e -> {
            String v = evo.developers.ru.macios.security.KeyChainManager.keychainGet(service, account);
            status.setText(v == null ? "⚠️ Not found" : "Read: " + v);
        });

        Button deleteBtn = new Button("Delete");
        styleButton(deleteBtn);
        deleteBtn.setOnAction(e -> {
            boolean ok = evo.developers.ru.macios.security.KeyChainManager.keychainDelete(service, account);
            status.setText(ok ? "🗑 Deleted" : "❌ Delete failed");
        });

        HBox buttons = new HBox(10, storeBtn, getBtn, deleteBtn);
        buttons.setAlignment(Pos.CENTER_LEFT);

        controls.getChildren().addAll(valueField, buttons, status);
        card.getChildren().add(controls);
        return card;
    }

    private VBox createActivityCard() {
        VBox card = createCard("🔗 Handoff / Spotlight", "Advertise NSUserActivity to the ecosystem");

        VBox controls = new VBox(15);
        controls.setPadding(new Insets(15, 0, 0, 0));

        Label status = new Label("No activity advertised");
        status.setStyle("-fx-text-fill: rgba(255,255,255,0.85); -fx-font-size: 13px;");

        Button advertiseBtn = new Button("Advertise activity");
        styleButton(advertiseBtn);
        advertiseBtn.setOnAction(e -> {
            helperMacOS.getUserActivity().onContinue((type, title, info) ->
                status.setText("Continued: " + title + " " + info));
            helperMacOS.getUserActivity().newActivity("ru.evo.jfxmacios.demo.view")
                .title("Viewing the JFX-Macios demo")
                .info("screen", "activity-card")
                .handoff(true)
                .searchable(true)
                .prediction(true)
                .keywords("jfx", "macios", "demo")
                .become();
            status.setText("Advertised — visible to Handoff / Spotlight");
        });

        Button resignBtn = new Button("Resign");
        styleButton(resignBtn);
        resignBtn.setOnAction(e -> {
            helperMacOS.getUserActivity().resignCurrent();
            status.setText("Activity resigned");
        });

        HBox buttons = new HBox(10, advertiseBtn, resignBtn);
        buttons.setAlignment(Pos.CENTER_LEFT);

        controls.getChildren().addAll(status, buttons);
        card.getChildren().add(controls);
        return card;
    }

    private VBox createBiometricCard() {
        VBox card = createCard("👆 Touch ID", "Biometric authentication via LocalAuthentication");

        VBox controls = new VBox(15);
        controls.setPadding(new Insets(15, 0, 0, 0));

        boolean available = evo.developers.ru.macios.security.BiometricManager.isBiometricsAvailable();
        Label availability = new Label(available ? "✅ Biometrics available" : "⚠️ Biometrics unavailable");
        availability.setStyle("-fx-text-fill: rgba(255,255,255,0.9); -fx-font-size: 14px;");

        Label result = new Label("Not authenticated");
        result.setStyle("-fx-text-fill: rgba(255,255,255,0.75); -fx-font-size: 13px;");

        Button authBtn = new Button("🔓 Authenticate");
        styleButton(authBtn);
        authBtn.setOnAction(e -> {
            result.setText("Waiting for Touch ID…");
            helperMacOS.getBiometric().authenticate("Confirm your identity to continue", (ok, err) ->
                Platform.runLater(() -> {
                    result.setText(ok ? "✅ Authenticated" : "❌ Failed: " + (err == null ? "cancelled" : err));
                    helperMacOS.getHapticManager().haptic(
                        HapticManager.HapticPattern.LEVEL_CHANGE, HapticManager.HapticTime.NOW);
                }));
        });

        controls.getChildren().addAll(availability, authBtn, result);
        card.getChildren().add(controls);
        return card;
    }

    private boolean nowPlayingReady = false;
    private int tabCounter = 0;

    private VBox createNowPlayingCard() {
        VBox card = createCard("🎵 Now Playing", "MPNowPlayingInfoCenter + media key handling");

        VBox controls = new VBox(15);
        controls.setPadding(new Insets(15, 0, 0, 0));

        TextField titleField = new TextField("Liquid Dreams");
        titleField.setPromptText("Track title");
        titleField.setStyle("-fx-background-color: rgba(255,255,255,0.15); -fx-text-fill: white;");
        TextField artistField = new TextField("JFX Macios");
        artistField.setPromptText("Artist");
        artistField.setStyle("-fx-background-color: rgba(255,255,255,0.15); -fx-text-fill: white;");

        Label lastCommand = new Label("Media keys: (press ▶/⏸ after setting)");
        lastCommand.setStyle("-fx-text-fill: rgba(255,255,255,0.75); -fx-font-size: 13px;");

        Runnable ensureCommands = () -> {
            if (nowPlayingReady) return;
            nowPlayingReady = true;
            NowPlayingManager np = helperMacOS.getNowPlaying();
            np.enableRemoteCommands();
            np.onToggle(() -> { Platform.runLater(() -> lastCommand.setText("Media keys: toggle ⏯")); return true; });
            np.onPlay(() -> { Platform.runLater(() -> lastCommand.setText("Media keys: play ▶")); return true; });
            np.onPause(() -> { Platform.runLater(() -> lastCommand.setText("Media keys: pause ⏸")); return true; });
            np.onNext(() -> { Platform.runLater(() -> lastCommand.setText("Media keys: next ⏭")); return true; });
            np.onPrevious(() -> { Platform.runLater(() -> lastCommand.setText("Media keys: previous ⏮")); return true; });
        };

        Button setBtn = new Button("Set Now Playing");
        styleButton(setBtn);
        setBtn.setOnAction(e -> {
            ensureCommands.run();
            helperMacOS.getNowPlaying().setNowPlaying(
                titleField.getText(), artistField.getText(), "JFX-Macios", 240, 0);
            helperMacOS.getNowPlaying().setPlaybackState(NowPlayingManager.STATE_PLAYING);
            lastCommand.setText("Now Playing set — check Control Center");
        });

        Button playBtn = new Button("▶ Playing");
        styleButton(playBtn);
        playBtn.setOnAction(e ->
            helperMacOS.getNowPlaying().setPlaybackState(NowPlayingManager.STATE_PLAYING));

        Button pauseBtn = new Button("⏸ Paused");
        styleButton(pauseBtn);
        pauseBtn.setOnAction(e ->
            helperMacOS.getNowPlaying().setPlaybackState(NowPlayingManager.STATE_PAUSED));

        Button clearBtn = new Button("Clear");
        styleButton(clearBtn);
        clearBtn.setOnAction(e -> {
            helperMacOS.getNowPlaying().clear();
            lastCommand.setText("Now Playing cleared");
        });

        HBox stateButtons = new HBox(10, setBtn, playBtn, pauseBtn, clearBtn);
        stateButtons.setAlignment(Pos.CENTER_LEFT);

        controls.getChildren().addAll(
            createLabel("Title:"), titleField,
            createLabel("Artist:"), artistField,
            stateButtons, lastCommand);
        card.getChildren().add(controls);
        return card;
    }

    private VBox createSecurityCard() {
        VBox card = createCard("🔒 Window Security", "Protect window from screenshots and screen recording");

        VBox controls = new VBox(15);
        controls.setPadding(new Insets(15, 0, 0, 0));

        HBox securityBox = new HBox(15);
        securityBox.setAlignment(Pos.CENTER_LEFT);
        Label securityLabel = createLabel("Screenshot Protection:");
        securityToggle = new ToggleButton("Disabled");
        securityToggle.setStyle(
            "-fx-background-color: rgba(255,100,100,0.3);" +
            "-fx-text-fill: white;"
        );
        securityToggle.selectedProperty().addListener((obs, old, selected) -> {
            helperMacOS.getWindowSecurity().setSecure(selected);
            String accentRgb = String.format("rgba(%d,%d,%d,0.5)", 
                (int)(accentColor.getRed() * 255),
                (int)(accentColor.getGreen() * 255),
                (int)(accentColor.getBlue() * 255));
            
            if (selected) {
                securityToggle.setText("Enabled");
                securityToggle.setStyle(
                    "-fx-background-color: " + accentRgb + ";" +
                    "-fx-text-fill: white;"
                );
            } else {
                securityToggle.setText("Disabled");
                securityToggle.setStyle(
                    "-fx-background-color: rgba(255,100,100,0.3);" +
                    "-fx-text-fill: white;"
                );
            }
            helperMacOS.getHapticManager().haptic(
                HapticManager.HapticPattern.GENERIC,
                HapticManager.HapticTime.NOW
            );
        });

        securityBox.getChildren().addAll(securityLabel, securityToggle);
        controls.getChildren().add(securityBox);

        Label infoLabel = new Label("⚠️ When enabled, window will be invisible to screenshots");
        infoLabel.setStyle(
            "-fx-text-fill: rgba(255,255,255,0.7);" +
            "-fx-font-size: 12px;"
        );
        controls.getChildren().add(infoLabel);

        card.getChildren().add(controls);
        return card;
    }

    private VBox createDockBadgeCard() {
        VBox card = createCard("📱 Dock Badge", "Set unread count badge on app icon");

        VBox controls = new VBox(15);
        controls.setPadding(new Insets(15, 0, 0, 0));

        HBox badgeBox = new HBox(10);
        badgeBox.setAlignment(Pos.CENTER_LEFT);
        Label badgeLabel = createLabel("Badge:");
        badgeField = new TextField();
        badgeField.setPromptText("Enter number or text");
        badgeField.setStyle(
            "-fx-background-color: rgba(255,255,255,0.15);" +
            "-fx-text-fill: white;"
        );
        HBox.setHgrow(badgeField, Priority.ALWAYS);

        Button setBadgeBtn = new Button("Set");
        styleButton(setBadgeBtn);
        setBadgeBtn.setOnAction(e -> {
            String text = badgeField.getText();
            if (text.isEmpty()) {
                DockBadgeManager.clearDockBadge();
            } else {
                try {
                    int num = Integer.parseInt(text);
                    DockBadgeManager.setDockBadge(num);
                } catch (NumberFormatException ex) {
                    DockBadgeManager.setDockBadge(text);
                }
            }
            helperMacOS.getHapticManager().haptic(
                HapticManager.HapticPattern.LEVEL_CHANGE,
                HapticManager.HapticTime.NOW
            );
            SoundManager.playSound("Glass");
        });

        Button clearBadgeBtn = new Button("Clear");
        styleButton(clearBadgeBtn);
        clearBadgeBtn.setOnAction(e -> {
            DockBadgeManager.clearDockBadge();
            badgeField.clear();
            helperMacOS.getHapticManager().haptic(
                HapticManager.HapticPattern.GENERIC,
                HapticManager.HapticTime.NOW
            );
        });

        badgeBox.getChildren().addAll(badgeLabel, badgeField, setBadgeBtn, clearBadgeBtn);
        controls.getChildren().add(badgeBox);

        card.getChildren().add(controls);
        return card;
    }

    private VBox createNotificationCard() {
        VBox card = createCard("🔔 Notifications", "Send native macOS notifications");

        VBox controls = new VBox(15);
        controls.setPadding(new Insets(15, 0, 0, 0));

        Label titleLabel = createLabel("Title:");
        notificationTitleField = new TextField();
        notificationTitleField.setPromptText("Notification title");
        notificationTitleField.setStyle(
            "-fx-background-color: rgba(255,255,255,0.15);" +
            "-fx-text-fill: white;"
        );

        Label messageLabel = createLabel("Message:");
        notificationMessageField = new TextField();
        notificationMessageField.setPromptText("Notification message");
        notificationMessageField.setStyle(
            "-fx-background-color: rgba(255,255,255,0.15);" +
            "-fx-text-fill: white;"
        );

        Label response = new Label("Response: (interact with a notification)");
        response.setStyle("-fx-text-fill: rgba(255,255,255,0.75); -fx-font-size: 12px;");
        response.setWrapText(true);

        // Listen for clicks / action button / inline replies.
        NotificationManager.setListener((identifier, activationType, replyText) ->
            Platform.runLater(() -> {
                String kind;
                switch (activationType) {
                    case NotificationManager.ACTIVATION_ACTION_BUTTON: kind = "action button"; break;
                    case NotificationManager.ACTIVATION_REPLIED: kind = "reply: \"" + replyText + "\""; break;
                    case NotificationManager.ACTIVATION_CONTENTS_CLICKED: kind = "clicked"; break;
                    default: kind = "type " + activationType;
                }
                response.setText("Response: " + kind);
                helperMacOS.getHapticManager().haptic(
                    HapticManager.HapticPattern.GENERIC, HapticManager.HapticTime.NOW);
            }));

        Button sendBtn = new Button("📤 Send Notification");
        styleButton(sendBtn);
        sendBtn.setOnAction(e -> {
            String title = notificationTitleField.getText().isEmpty() 
                ? "JFX-Macios" 
                : notificationTitleField.getText();
            String message = notificationMessageField.getText().isEmpty()
                ? "This is a test notification!"
                : notificationMessageField.getText();
            
            NotificationManager.sendNotification(
                title,
                "Demo",
                message,
                UUID.randomUUID().toString()
            );
            
            helperMacOS.getHapticManager().haptic(
                HapticManager.HapticPattern.GENERIC,
                HapticManager.HapticTime.NOW
            );
            SoundManager.playNotificationSound();
        });

        Button richBtn = new Button("✨ Send with action + reply");
        styleButton(richBtn);
        richBtn.setOnAction(e -> {
            String title = notificationTitleField.getText().isEmpty()
                ? "JFX-Macios" : notificationTitleField.getText();
            String message = notificationMessageField.getText().isEmpty()
                ? "Try the action button or reply field" : notificationMessageField.getText();
            NotificationManager.sendRich(
                title, "Demo", message, UUID.randomUUID().toString(),
                "Open", true, "Type a reply…", null);
            SoundManager.playNotificationSound();
        });

        Label hint = new Label("💡 Action/reply buttons require Alerts style in System Settings → Notifications");
        hint.setStyle("-fx-text-fill: rgba(255,255,255,0.55); -fx-font-size: 11px;");
        hint.setWrapText(true);

        HBox notifButtons = new HBox(10, sendBtn, richBtn);
        notifButtons.setAlignment(Pos.CENTER_LEFT);

        controls.getChildren().addAll(
            titleLabel, notificationTitleField,
            messageLabel, notificationMessageField,
            notifButtons, response, hint
        );
        card.getChildren().add(controls);

        return card;
    }

    private VBox createSoundsCard() {
        VBox card = createCard("🔊 System Sounds", "Play macOS system sounds");

        VBox controls = new VBox(15);
        controls.setPadding(new Insets(15, 0, 0, 0));

        HBox soundBox = new HBox(10);
        soundBox.setAlignment(Pos.CENTER_LEFT);
        Label soundLabel = createLabel("Sound:");
        soundCombo = new ComboBox<>();
        soundCombo.getItems().addAll(
            "Glass", "Ping", "Pop", "Basso", "Blow", "Bottle",
            "Frog", "Funk", "Hero", "Morse", "Purr", "Sosumi",
            "Submarine", "Tink"
        );
        soundCombo.setValue("Glass");
        soundCombo.setStyle(
            "-fx-background-color: rgba(255,255,255,0.15);" +
            "-fx-text-fill: white;"
        );
        HBox.setHgrow(soundCombo, Priority.ALWAYS);

        Button playBtn = new Button("▶ Play");
        styleButton(playBtn);
        playBtn.setOnAction(e -> {
            String sound = soundCombo.getValue();
            if (sound != null) {
                SoundManager.playSound(sound);
                helperMacOS.getHapticManager().haptic(
                    HapticManager.HapticPattern.GENERIC,
                    HapticManager.HapticTime.NOW
                );
            }
        });

        Button notificationSoundBtn = new Button("🔔 Notification");
        styleButton(notificationSoundBtn);
        notificationSoundBtn.setOnAction(e -> {
            SoundManager.playNotificationSound();
            helperMacOS.getHapticManager().haptic(
                HapticManager.HapticPattern.GENERIC,
                HapticManager.HapticTime.NOW
            );
        });

        soundBox.getChildren().addAll(soundLabel, soundCombo, playBtn, notificationSoundBtn);
        controls.getChildren().add(soundBox);

        card.getChildren().add(controls);
        return card;
    }

    private VBox createHapticCard() {
        VBox card = createCard("📳 Haptic Feedback", "Force Touch trackpad feedback");

        VBox controls = new VBox(15);
        controls.setPadding(new Insets(15, 0, 0, 0));

        HBox hapticBox = new HBox(15);
        hapticBox.setAlignment(Pos.CENTER);

        Button genericBtn = new Button("Generic");
        styleButton(genericBtn);
        genericBtn.setOnAction(e -> {
            helperMacOS.getHapticManager().haptic(
                HapticManager.HapticPattern.GENERIC,
                HapticManager.HapticTime.NOW
            );
        });

        Button alignmentBtn = new Button("Alignment");
        styleButton(alignmentBtn);
        alignmentBtn.setOnAction(e -> {
            helperMacOS.getHapticManager().haptic(
                HapticManager.HapticPattern.ALIGNMENT,
                HapticManager.HapticTime.NOW
            );
        });

        Button levelBtn = new Button("Level Change");
        styleButton(levelBtn);
        levelBtn.setOnAction(e -> {
            helperMacOS.getHapticManager().haptic(
                HapticManager.HapticPattern.LEVEL_CHANGE,
                HapticManager.HapticTime.NOW
            );
        });

        hapticBox.getChildren().addAll(genericBtn, alignmentBtn, levelBtn);
        controls.getChildren().add(hapticBox);

        Label infoLabel = new Label("💡 Requires Force Touch trackpad");
        infoLabel.setStyle(
            "-fx-text-fill: rgba(255,255,255,0.7);" +
            "-fx-font-size: 12px;"
        );
        controls.getChildren().add(infoLabel);

        card.getChildren().add(controls);
        return card;
    }

    private Label createLabel(String text) {
        Label label = new Label(text);
        label.setStyle(
            "-fx-text-fill: rgba(255,255,255,0.9);" +
            "-fx-font-size: 14px;"
        );
        label.setMinWidth(120);
        return label;
    }

    private void styleButton(Button btn) {
        // Use current accent color
        String accentRgb = String.format("rgba(%d,%d,%d,0.4)", 
            (int)(accentColor.getRed() * 255),
            (int)(accentColor.getGreen() * 255),
            (int)(accentColor.getBlue() * 255));
        
        String accentRgbHover = String.format("rgba(%d,%d,%d,0.6)", 
            (int)(accentColor.getRed() * 255),
            (int)(accentColor.getGreen() * 255),
            (int)(accentColor.getBlue() * 255));
        
        btn.setStyle(
            "-fx-background-color: " + accentRgb + ";" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 13px;" +
            "-fx-background-radius: 6;" +
            "-fx-padding: 8 16;" +
            "-fx-cursor: hand;"
        );

        btn.setOnMouseEntered(e -> {
            String hoverStyle = 
                "-fx-background-color: " + accentRgbHover + ";" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 13px;" +
                "-fx-background-radius: 6;" +
                "-fx-padding: 8 16;" +
                "-fx-cursor: hand;";
            btn.setStyle(hoverStyle);
        });

        btn.setOnMouseExited(e -> {
            String normalStyle = 
                "-fx-background-color: " + accentRgb + ";" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 13px;" +
                "-fx-background-radius: 6;" +
                "-fx-padding: 8 16;" +
                "-fx-cursor: hand;";
            btn.setStyle(normalStyle            );
        });
    }
    
    /**
     * Update CSS styles for ScrollPane with current accent color
     */
    private void updateScrollPaneCSS() {
        if (scene == null) return;
        
        // Clear existing stylesheets
        scene.getStylesheets().clear();
        
        // Get accent color in rgba format
        String accentRgb = String.format("rgba(%d,%d,%d,0.6)", 
            (int)(accentColor.getRed() * 255),
            (int)(accentColor.getGreen() * 255),
            (int)(accentColor.getBlue() * 255));
        
        String accentRgbHover = String.format("rgba(%d,%d,%d,0.8)", 
            (int)(accentColor.getRed() * 255),
            (int)(accentColor.getGreen() * 255),
            (int)(accentColor.getBlue() * 255));
        
        String scrollbarCSS = 
            ".scroll-pane {" +
            "    -fx-background-color: transparent;" +
            "}" +
            ".scroll-pane .scroll-bar:vertical {" +
            "    -fx-background-insets: 0;" +
            "    -fx-pref-width: 10;" +
            "    -fx-background-color: transparent;" +
            "}" +
            ".scroll-pane .scroll-bar:vertical .track {" +
            "    -fx-background-color: transparent;" +
            "    -fx-background-radius: 5;" +
            "}" +
            ".scroll-pane .scroll-bar:vertical .thumb {" +
            "    -fx-background-color: " + accentRgb + ";" +
            "    -fx-background-radius: 5;" +
            "    -fx-min-height: 30;" +
            "    -fx-pref-width: 8;" +
            "}" +
            ".scroll-pane .scroll-bar:vertical .thumb:hover {" +
            "    -fx-background-color: " + accentRgbHover + ";" +
            "}" +
            ".scroll-pane .scroll-bar:vertical .thumb:pressed {" +
            "    -fx-background-color: " + accentRgbHover + ";" +
            "}" +
            ".scroll-pane .scroll-bar:vertical .increment-button, " +
            ".scroll-pane .scroll-bar:vertical .decrement-button {" +
            "    -fx-background-color: transparent;" +
            "    -fx-pref-height: 0;" +
            "    -fx-pref-width: 0;" +
            "}" +
            ".scroll-pane .scroll-bar:vertical .increment-arrow, " +
            ".scroll-pane .scroll-bar:vertical .decrement-arrow {" +
            "    -fx-shape: \"\";" +
            "    -fx-pref-width: 0;" +
            "    -fx-pref-height: 0;" +
            "}" +
            ".scroll-pane .corner {" +
            "    -fx-background-color: transparent;" +
            "}" +
            ".scroll-pane .viewport {" +
            "    -fx-background-color: transparent;" +
            "}";
        
        scene.getStylesheets().add("data:text/css," + scrollbarCSS);
    }
}
