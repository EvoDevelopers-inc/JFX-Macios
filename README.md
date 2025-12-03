# 🪟 JFX-Macios - Native macOS Integration for JavaFX

> **Native macOS features for JavaFX applications via JNA** | No Xcode required | Pure Java implementation | macOS blur effects, notifications, security, and more

<p align="center">
  <img src="https://img.shields.io/badge/Platform-macOS-blue?style=for-the-badge&logo=apple" alt="macOS Platform">
  <img src="https://img.shields.io/badge/Java-11+-orange?style=for-the-badge&logo=openjdk" alt="Java 11+">
  <img src="https://img.shields.io/badge/JavaFX-Any-green?style=for-the-badge" alt="JavaFX">
  <img src="https://img.shields.io/badge/License-Apache%202.0-red?style=for-the-badge" alt="Apache 2.0 License">
</p>

<p align="center">
  <b>Native macOS features for JavaFX applications via JNA</b><br>
  <i>Beta version</i>
</p>

<p align="center">
  <a href="https://github.com/EvoDevelopers-inc/JFX-Macios/stargazers">
    <img src="https://img.shields.io/github/stars/EvoDevelopers-inc/JFX-Macios?style=social&label=Star" alt="GitHub stars">
  </a>
  <a href="https://github.com/EvoDevelopers-inc/JFX-Macios/fork">
    <img src="https://img.shields.io/github/forks/EvoDevelopers-inc/JFX-Macios?style=social&label=Fork" alt="GitHub forks">
  </a>
  <a href="https://github.com/EvoDevelopers-inc/JFX-Macios/watchers">
    <img src="https://img.shields.io/github/watchers/EvoDevelopers-inc/JFX-Macios?style=social&label=Watch" alt="GitHub watchers">
  </a>
</p>

<p align="center">
  ⭐ <b>If you find this project useful, please consider giving it a star!</b> ⭐<br>
  <i>Это помогает проекту быть более заметным в поиске</i>
</p>

## 🔍 What is JFX-Macios?

**JFX-Macios** is a powerful Java library that brings native macOS features to JavaFX applications. Whether you're building a desktop app, messenger, or any JavaFX application on macOS, this library provides seamless integration with macOS system features without requiring Xcode or native compilation.

### Key Benefits:
- ✅ **No Xcode Required** - Pure Java implementation using JNA
- ✅ **Native macOS Look** - Blur effects, transparent titlebars, native controls
- ✅ **Security Features** - Anti-screenshot, window security, secure input
- ✅ **System Integration** - Notifications, dock badges, system sounds, haptic feedback
- ✅ **Easy to Use** - Simple API, minimal setup
- ✅ **Lightweight** - No heavy dependencies, just JNA

### Popular Use Cases:
- 📱 **Messenger Apps** - Dock badges, notifications, system sounds
- 🎨 **Design Tools** - Native blur effects, transparent windows
- 🔒 **Security Apps** - Window protection, secure input
- 💼 **Business Apps** - Native macOS appearance, system integration


---

## 📸 Demo / Демонстрация

<p align="center">
  <img src="/example.gif" alt="JFX-Macios macOS blur effects and native features demo" width="80%">
</p>

<p align="center">
  <img src="/example_img2.png" alt="JFX-Macios transparent titlebar and window effects" width="80%">
</p>
<p align="center">
  <img src="/example_img.png" alt="JFX-Macios dark mode and system integration" width="80%">
</p>

---

## ✨ Features / Возможности

### 🎨 Window Effects / Эффекты окон

- **Blur effects** - Native `NSVisualEffectView` with light/dark vibrancy
- **Transparent titlebar** - Full-size content with native traffic lights
- **Material effects** - Various macOS materials (Sidebar, Menu, Popover, etc.)
- **Alpha control** - Adjust blur transparency

- **Эффекты размытия** - Нативный `NSVisualEffectView` со светлой/темной вибрацией
- **Прозрачный заголовок** - Полноразмерный контент с нативными кнопками управления
- **Материалы** - Различные материалы macOS (Боковая панель, Меню, Всплывающее окно и т.д.)
- **Контроль прозрачности** - Настройка прозрачности размытия

### 🔒 Security / Безопасность

- **Anti-Screenshot** - Window invisible to screenshots & screen recording
- **Window sharing control** - Control window sharing type

- **Защита от скриншотов** - Окно невидимо для скриншотов и записи экрана
- **Управление доступом к окну** - Контроль типа доступа к окну

### 📱 UX Features / UX возможности

- **Dock Badge** - Unread count on app icon
- **Native Notifications** - macOS notification center
- **System Sounds** - Play system sounds (Glass, Ping, Pop, etc.)
- **Haptic Feedback** - Force Touch trackpad feedback

- **Бейдж в Dock** - Счетчик непрочитанных на иконке приложения
- **Нативные уведомления** - Центр уведомлений macOS
- **Системные звуки** - Воспроизведение системных звуков (Glass, Ping, Pop и т.д.)
- **Тактильная обратная связь** - Обратная связь через Force Touch трекпад

### 🛠️ System Integration / Интеграция с системой

- **Dark Mode Detection** - Auto-react to system theme changes
- **Accent Color** - Get system accent color
- **Window Management** - Native window operations (close, minimize, zoom)
- **Window Dragging** - Native window drag with snapping

- **Определение темного режима** - Автоматическая реакция на изменения темы системы
- **Акцентный цвет** - Получение акцентного цвета системы
- **Управление окнами** - Нативные операции с окнами (закрыть, свернуть, развернуть)
- **Перетаскивание окон** - Нативное перетаскивание окон с привязкой

---

## 📊 Feature Support Table / Таблица поддержки функций

| Feature / Функция | Status / Статус | Description / Описание |
|-------------------|-----------------|----------------------|
| **Blur Effects** / **Эффекты размытия** | ✅ Implemented / Реализовано | Light/Dark vibrancy blur with material support |
| **Transparent Titlebar** / **Прозрачный заголовок** | ✅ Implemented / Реализовано | Full-size content with native traffic lights |
| **Material Selection** / **Выбор материала** | ✅ Implemented / Реализовано | 18+ macOS materials (Sidebar, Menu, Popover, etc.) |
| **Alpha Control** / **Контроль прозрачности** | ✅ Implemented / Реализовано | Adjust blur transparency (0.0 - 1.0) |
| **Window Security** / **Безопасность окна** | ✅ Implemented / Реализовано | Block screenshots and screen recording |
| **Dock Badge** / **Бейдж в Dock** | ✅ Implemented / Реализовано | Set unread count on app icon |
| **Notifications** / **Уведомления** | ✅ Implemented / Реализовано | Native macOS notifications with title/subtitle |
| **System Sounds** / **Системные звуки** | ✅ Implemented / Реализовано | Play system sounds (Glass, Ping, Pop, etc.) |
| **Haptic Feedback** / **Тактильная обратная связь** | ✅ Implemented / Реализовано | Force Touch feedback (Generic, Alignment, Level) |
| **Dark Mode Detection** / **Определение темного режима** | ✅ Implemented / Реализовано | Detect and listen to system theme changes |
| **Accent Color** / **Акцентный цвет** | ✅ Implemented / Реализовано | Get system accent color (Blue, Purple, Pink, etc.) |
| **Window Dragging** / **Перетаскивание окон** | ✅ Implemented / Реализовано | Native window drag with snapping support |
| **Window Management** / **Управление окнами** | ✅ Implemented / Реализовано | Close, minimize, zoom operations |
| **Keychain** / **Связка ключей** | ⚠️ Deprecated / Устарело | Secure storage (marked as deprecated) |

---

## 🚀 Quick Start / Быстрый старт

### Gradle

```groovy
dependencies {
    // TODO implementation 'evo.developers.ru:macios:1.0-SNAPSHOT'
}
```



---

## 💻 Usage / Использование

### Basic Setup / Базовая настройка

```java
import evo.developers.ru.macios.HelperMacOS;
import javafx.application.Application;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class App extends Application {
    @Override
    public void start(Stage stage) {
        stage.initStyle(StageStyle.UNIFIED);
        
        // Initialize HelperMacOS
        HelperMacOS helper = HelperMacOS.getInstance(stage);
        
        // Setup transparent titlebar
        helper.getTitleBarManager().setupTransparentTitlebar();
        
        // Apply blur effect
        helper.getBlurEffect().apply(stage, BlurEffectManager.Style.DARK);
        
        stage.show();
    }
}
```

### Blur Effects / Эффекты размытия

```java
HelperMacOS helper = HelperMacOS.getInstance(stage);

// Apply dark blur
helper.getBlurEffect().apply(stage, BlurEffectManager.Style.DARK);

// Apply light blur
helper.getBlurEffect().apply(stage, BlurEffectManager.Style.LIGHT);

// Change material
helper.getBlurEffect().setMaterial(Material.SIDEBAR);

// Adjust alpha
helper.getBlurEffect().setAlpha(0.8);
```

### Security Features / Функции безопасности

```java
HelperMacOS helper = HelperMacOS.getInstance(stage);

// Make window invisible to screenshots
helper.getWindowSecurity().setSecure(true);

// Check if secure
boolean isSecure = helper.getWindowSecurity().isSecure();
```

### Dock Badge / Бейдж в Dock

```java
import evo.developers.ru.macios.ux.dockbadge.DockBadgeManager;

// Set unread count
DockBadgeManager.setDockBadge(5);

// Clear badge
DockBadgeManager.clearDockBadge();
```

### Notifications / Уведомления

```java
import evo.developers.ru.macios.ux.notification.NotificationManager;

// Simple notification
NotificationManager.sendNotification(
    "New Message", 
    "You have a new message", 
    "msg-123"
);

// With subtitle
NotificationManager.sendNotification(
    "New Message", 
    "John", 
    "Hey, how are you?", 
    "msg-123"
);

// Clear all notifications
NotificationManager.clearNotifications();
```

### System Sounds / Системные звуки

```java
import evo.developers.ru.macios.ux.sound.SoundManager;

// Play notification sound
SoundManager.playNotificationSound();

// Play specific sound
SoundManager.playSound("Glass");
SoundManager.playSound("Ping");
SoundManager.playSound("Pop");
```

### Haptic Feedback / Тактильная обратная связь

```java
HelperMacOS helper = HelperMacOS.getInstance(stage);

// Generic haptic
helper.getHapticManager().haptic(
    HapticManager.HapticPattern.GENERIC, 
    HapticManager.HapticTime.NOW
);

// Alignment haptic
helper.getHapticManager().haptic(
    HapticManager.HapticPattern.ALIGNMENT, 
    HapticManager.HapticTime.NOW
);

// Level change haptic
helper.getHapticManager().haptic(
    HapticManager.HapticPattern.LEVEL_CHANGE, 
    HapticManager.HapticTime.NOW
);
```

### Dark Mode Detection / Определение темного режима

```java
import evo.developers.ru.macios.ui.ThemeManager;

// Check current mode
boolean isDark = ThemeManager.isDarkMode();

// Get appearance
ThemeManager.Appearance appearance = ThemeManager.getAppearance();

// Listen for changes
ThemeManager.startAppearanceListener((isDark) -> {
    Platform.runLater(() -> {
        if (isDark) {
            // Apply dark theme
            helper.getBlurEffect().apply(stage, BlurEffectManager.Style.DARK);
        } else {
            // Apply light theme
            helper.getBlurEffect().apply(stage, BlurEffectManager.Style.LIGHT);
        }
    });
});

// Get accent color
String accentColor = ThemeManager.getAccentColor(); // "Blue", "Purple", etc.
```

### Window Dragging / Перетаскивание окон

```java
import evo.developers.ru.macios.ui.TitleBarManager;

// Enable window dragging
TitleBarManager.enableWindowDrag();

// Start native drag (call from mousePressed event)
header.setOnMousePressed(e -> {
    TitleBarManager.startWindowDrag();
});
```

---

## 🔧 API Reference / Справочник API

### HelperMacOS

Main entry point for all macOS features.

Главная точка входа для всех функций macOS.

| Method | Description |
|--------|-------------|
| `getInstance(Stage)` | Get HelperMacOS instance |
| `getTitleBarManager()` | Get titlebar manager |
| `getWindowManager()` | Get window manager |
| `getWindowSecurity()` | Get security manager |
| `getBlurEffect()` | Get blur effect manager |
| `getHapticManager()` | Get haptic feedback manager |

### BlurEffectManager

| Method | Description |
|--------|-------------|
| `apply(Stage, Style)` | Apply blur effect (LIGHT, DARK, AQUA, DARK_AQUA) |
| `setMaterial(Material)` | Change material (SIDEBAR, MENU, POPOVER, etc.) |
| `setAlpha(double)` | Set transparency (0.0 - 1.0) |

### TitleBarManager

| Method | Description |
|--------|-------------|
| `setupTransparentTitlebar()` | Setup transparent titlebar with native controls |
| `enableWindowDrag()` | Enable window dragging |
| `startWindowDrag()` | Start native window drag |

### WindowSecurity

| Method | Description |
|--------|-------------|
| `setSecure(boolean)` | Enable/disable screenshot protection |
| `isSecure()` | Check if window is secure |

### DockBadgeManager

| Method | Description |
|--------|-------------|
| `setDockBadge(int)` | Set badge number |
| `setDockBadge(String)` | Set badge text |
| `clearDockBadge()` | Remove badge |

### NotificationManager

| Method | Description |
|--------|-------------|
| `sendNotification(title, message, id)` | Send notification |
| `sendNotification(title, subtitle, message, id)` | Send with subtitle |
| `clearNotifications()` | Remove all notifications |

### SoundManager

| Method | Description |
|--------|-------------|
| `playSound(String)` | Play system sound by name |
| `playNotificationSound()` | Play default notification sound |

### HapticManager

| Method | Description |
|--------|-------------|
| `haptic(pattern, time)` | Perform haptic feedback |

**Patterns:** `GENERIC`, `ALIGNMENT`, `LEVEL_CHANGE`  
**Time:** `DEFAULT`, `NOW`, `DRAW_COMPLETED`

### ThemeManager

| Method | Description |
|--------|-------------|
| `isDarkMode()` | Check if dark mode is active |
| `getAppearance()` | Get current appearance (LIGHT/DARK) |
| `getAccentColor()` | Get system accent color |
| `getHighlightColor()` | Get highlight color |
| `startAppearanceListener(listener)` | Listen for theme changes |
| `startAppearanceListener(listener, interval)` | Listen with custom interval |

### WindowManager

| Method | Description |
|--------|-------------|
| `closeWindow()` | Close window |
| `minimizeWindow()` | Minimize window |
| `zoomWindow()` | Zoom window |

---

## 📋 Requirements / Требования

- **macOS** 10.14+ (Mojave or later)
- **Java** 11+
- **JavaFX** Any version
- **JNA** 5.18.1+

---

## 📝 License / Лицензия

Apache License 2.0

---

## 🔎 SEO Keywords & Search Terms

This library helps with searches like:
- `javafx macOS blur effect`
- `javafx native macOS features`
- `javafx transparent titlebar`
- `javafx macOS notifications`
- `javafx dock badge`
- `javafx macOS integration`
- `javafx native window effects`
- `javafx macOS security`
- `javafx dark mode detection`
- `javafx haptic feedback`
- `javafx system sounds`
- `javafx anti screenshot`
- `javafx JNA macOS`
- `javafx native look`

---

## ❓ FAQ / Часто задаваемые вопросы

### Q: Do I need Xcode to use this library?
**A:** No! JFX-Macios uses JNA (Java Native Access) and doesn't require Xcode or any native compilation.

### Q: What Java version is required?
**A:** Java 11 or higher is required.

### Q: Does it work with all JavaFX versions?
**A:** Yes, it works with any JavaFX version (OpenJFX, Gluon, etc.).

### Q: Can I use this in production?
**A:** The library is in beta, but it's stable and ready for testing. Use at your own discretion.

### Q: Does it work on Windows or Linux?
**A:** No, this library is macOS-only as it uses native macOS APIs.

### Q: How do I add blur effects to my JavaFX window?
**A:** Simply call `helper.getBlurEffect().apply(stage, BlurEffectManager.Style.DARK)` after initializing HelperMacOS.

### Q: Can I customize the blur material?
**A:** Yes! Use `helper.getBlurEffect().setMaterial(Material.SIDEBAR)` to change materials.

### Q: How do I send macOS notifications?
**A:** Use `NotificationManager.sendNotification(title, message, id)` - it's that simple!

---

## ⭐ Star This Repository / Поставьте звездочку

<p align="center">
  <b>🌟 If you find this project useful, please give it a star! 🌟</b>
</p>

<p align="center">
  <a href="https://github.com/EvoDevelopers-inc/JFX-Macios/stargazers">
    <img src="https://img.shields.io/github/stars/EvoDevelopers-inc/JFX-Macios?style=for-the-badge&logo=github&logoColor=white&labelColor=181717&color=yellow" alt="GitHub stars">
  </a>
</p>

<p align="center">
  ⭐ <b>Starring helps this project:</b> ⭐<br>
  • 📈 Increases visibility in GitHub search<br>
  • 🔍 Improves discoverability in Google<br>
  • 💪 Shows your support for the project<br>
  • 🚀 Motivates further development
</p>

<p align="center">
  <b>Thank you for your support! 🙏</b><br>
  <i>Спасибо за вашу поддержку!</i>
</p>

---

## 🤝 Contributing / Вклад в проект

Contributions are welcome! Please feel free to submit a Pull Request.

Вклад в проект приветствуется! Пожалуйста, отправляйте Pull Request.

---

## 📞 Support / Поддержка

- 🐛 **Issues**: [GitHub Issues](https://github.com/EvoDevelopers-inc/JFX-Macios/issues)
- 💬 **Discussions**: [GitHub Discussions](https://github.com/EvoDevelopers-inc/JFX-Macios/discussions)

---

<p align="center">
  Made with ❤️ for JavaFX on macOS<br>
  Сделано с ❤️ для JavaFX на macOS
</p>
