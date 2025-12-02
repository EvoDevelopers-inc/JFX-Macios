# 🪟 WindowBlur

<p align="center">
  <img src="https://img.shields.io/badge/Platform-macOS-blue?style=for-the-badge&logo=apple" alt="Platform">
  <img src="https://img.shields.io/badge/Java-17+-orange?style=for-the-badge&logo=openjdk" alt="Java">
  <img src="https://img.shields.io/badge/JavaFX-21+-green?style=for-the-badge" alt="JavaFX">
  <img src="https://img.shields.io/badge/License-Apache%202.0-red?style=for-the-badge" alt="License">
</p>

<p align="center">
  <b>Native macOS features for JavaFX applications via JNA</b><br>
  <i>No Xcode, no native compilation, pure Java!</i>
</p>

---

## ✨ Features

### 🎨 Window Effects
- **Blur effects** - Native `NSVisualEffectView` with light/dark vibrancy
- **Transparent titlebar** - Full-size content with native traffic lights
- **Liquid Glass panels** - Floating blur panels (macOS Tahoe style)

### 🔒 Security
- **Anti-Screenshot** - Window invisible to screenshots & screen recording
- **Anti-Keylogger** - Secure input mode blocks all keyloggers
- **Screen Recording Detection** - Detect when someone is recording
- **Keychain** - Secure encrypted storage for tokens & passwords

### 📱 Messenger Features
- **Dock Badge** - Unread count on app icon
- **Dock Bounce** - Attention request animation
- **Native Notifications** - macOS notification center
- **System Sounds** - Play system sounds (Glass, Ping, Pop, etc.)

### 🛠️ Utilities
- **Haptic Feedback** - Force Touch trackpad feedback
- **Clipboard** - Read/write text, detect images
- **Dark Mode Detection** - Auto-react to system theme changes
- **Prevent Sleep** - Keep Mac awake during file transfers
- **Open URLs/Files** - Native workspace integration

---

## 📸 Screenshots

<p align="center">
  <img src="/img.png" alt="Dark Mode" width="45%">
</p>

---

## 🚀 Quick Start

### Gradle

```groovy
dependencies {
       TODO
}
```

### Maven

```xml
TODO
```

---

## 💻 Usage

### Basic Blur Effect

```java
import com.carlfx.windowblur.WindowBlur;

public class App extends Application {
    @Override
    public void start(Stage stage) {
        stage.initStyle(StageStyle.UNIFIED);
        
        // ... setup scene ...
        
        stage.setOnShown(e -> Platform.runLater(() -> {
            WindowBlur.setupTransparentTitlebar();
            WindowBlur.dark();  // or WindowBlur.light()
        }));
        
        stage.show();
    }
}
```

### Security Features

```java
// Make window invisible to screenshots
WindowBlur.makeWindowSecure();

// Block keyloggers
WindowBlur.enableSecureInput();

// Full paranoid mode (both)
WindowBlur.enableParanoidMode();

// Check if being recorded
if (WindowBlur.isScreenBeingRecorded()) {
    // Warning: screen recording detected!
}
```

### Messenger Features

```java
// Set unread badge
WindowBlur.setDockBadge(5);

// Bounce dock icon
WindowBlur.bounceDockIcon(BounceType.CRITICAL);

// Send notification
WindowBlur.sendNotification("New Message", "John", "Hey!", "msg-123");

// Play sound
WindowBlur.playSound("Glass");
```

### Keychain (Secure Storage)

```java
// Store secret
WindowBlur.keychainStore("com.myapp", "authToken", "secret123");

// Retrieve secret
String token = WindowBlur.keychainGet("com.myapp", "authToken");

// Delete
WindowBlur.keychainDelete("com.myapp", "authToken");
```

### Auto Dark/Light Mode

```java
WindowBlur.startAppearanceListener(isDark -> {
    Platform.runLater(() -> {
        if (isDark) {
            WindowBlur.dark();
        } else {
            WindowBlur.light();
        }
    });
});
```

### Haptic Feedback

```java
WindowBlur.haptic();              // Generic
WindowBlur.hapticAlignment();     // Snap to guides
WindowBlur.hapticLevelChange();   // Slider tick
```

---

## 🔧 API Reference

### Window Effects
| Method | Description |
|--------|-------------|
| `dark()` | Apply dark vibrancy blur |
| `light()` | Apply light vibrancy blur |
| `setupTransparentTitlebar()` | Transparent titlebar with native controls |
| `hideTrafficLights()` | Hide close/minimize/zoom buttons |
| `startWindowDrag()` | Start native window drag |

### Security
| Method | Description |
|--------|-------------|
| `makeWindowSecure()` | Block screenshots & screen recording |
| `makeWindowNormal()` | Allow screenshots again |
| `enableSecureInput()` | Block keyloggers |
| `disableSecureInput()` | Allow key capture |
| `enableParanoidMode()` | Both protections |
| `isScreenBeingRecorded()` | Detect screen recording |
| `assessThreatLevel()` | Get security threat level |

### Dock
| Method | Description |
|--------|-------------|
| `setDockBadge(String/int)` | Set badge text/number |
| `clearDockBadge()` | Remove badge |
| `bounceDockIcon(BounceType)` | Bounce animation |
| `stopDockBounce(id)` | Stop bouncing |

### Notifications
| Method | Description |
|--------|-------------|
| `sendNotification(title, message, id)` | Show notification |
| `sendNotification(title, subtitle, message, id)` | With subtitle |
| `clearNotifications()` | Remove all notifications |
| `playSound(name)` | Play system sound |
| `playNotificationSound()` | Default notification sound |

### Keychain
| Method | Description |
|--------|-------------|
| `keychainStore(service, account, secret)` | Store encrypted |
| `keychainGet(service, account)` | Retrieve |
| `keychainDelete(service, account)` | Delete |
| `keychainExists(service, account)` | Check exists |

### System
| Method | Description |
|--------|-------------|
| `isDarkMode()` | Check dark mode |
| `getAccentColor()` | Get system accent color |
| `startAppearanceListener(callback)` | Listen for theme changes |
| `haptic() / hapticAlignment() / hapticLevelChange()` | Haptic feedback |
| `preventSleep(reason)` | Keep Mac awake |
| `allowSleep(id)` | Allow sleep again |

## 📋 Requirements

- **macOS** 10.14+ (Mojave or later)
- **Java** 17+
- **JavaFX** 21+
- **JNA** 5.13.0+

---

<p align="center">
  Made with ❤️ for JavaFX on macOS
</p>
