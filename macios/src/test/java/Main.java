import evo.developers.ru.macios.HelperMacOS;
import evo.developers.ru.macios.core.WindowManager;
import evo.developers.ru.macios.effects.BlurEffectManager;
import evo.developers.ru.macios.effects.Material;
import evo.developers.ru.macios.ui.ThemeManager;
import evo.developers.ru.macios.ui.TitleBarManager;
import evo.developers.ru.macios.ux.dockbadge.DockBadgeManager;
import evo.developers.ru.macios.ux.haptic.HapticManager;
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
        ScrollPane scrollPane = new ScrollPane();
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
            helperMacOS.getBlurEffect().apply(stage, BlurEffectManager.Style.DARK_AQUA);

            // Update system info and apply accent color
            updateSystemInfo();
            applyAccentColor();

            // Start appearance listener with shorter interval to catch accent color changes
            ThemeManager.startAppearanceListener((isDark) -> {
                Platform.runLater(() -> {
                    updateSystemInfo();
                    applyAccentColor();
                    if (isDark) {
                        helperMacOS.getBlurEffect().apply(stage, BlurEffectManager.Style.DARK_AQUA);
                        darkModeLabel.setText("🌙 Dark Mode");
                    } else {
                        helperMacOS.getBlurEffect().apply(stage, BlurEffectManager.Style.LIGHT);
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
            WindowManager.minimizeWindow();
            helperMacOS.getHapticManager().haptic(
                HapticManager.HapticPattern.GENERIC,
                HapticManager.HapticTime.NOW
            );
        });

        zoomBtn = createWindowButton("Zoom", "Zoom");
        zoomBtn.setOnAction(e -> {
            WindowManager.zoomWindow();
            helperMacOS.getHapticManager().haptic(
                HapticManager.HapticPattern.GENERIC,
                HapticManager.HapticTime.NOW
            );
        });

        closeBtn = createWindowButton("Close", "Close");
        closeBtn.setOnAction(e -> {
            WindowManager.closeWindow();
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
            helperMacOS.getBlurEffect().apply(primaryStage, styleCombo.getValue());
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

        controls.getChildren().addAll(
            titleLabel, notificationTitleField,
            messageLabel, notificationMessageField,
            sendBtn
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
