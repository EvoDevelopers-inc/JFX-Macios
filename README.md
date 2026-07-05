# JFX-Macios

Native macOS integration for **JavaFX**, written in pure Java on top of [JNA](https://github.com/java-native-access/jna). No Xcode, no native compilation, no `.dylib` to ship — just add the dependency and call into AppKit from your JavaFX app.

<p align="center">
  <img src="https://img.shields.io/badge/platform-macOS-black?logo=apple" alt="macOS">
  <img src="https://img.shields.io/badge/Java-11%2B-orange?logo=openjdk" alt="Java 11+">
  <img src="https://img.shields.io/badge/JavaFX-any-blue" alt="JavaFX">
  <img src="https://img.shields.io/badge/license-Apache--2.0-green" alt="Apache 2.0">
  <img src="https://img.shields.io/badge/status-beta-yellow" alt="Beta">
</p>

> **Status:** beta. The API is usable and stable for experimentation, but may still change before `1.0`.

---

## Contents

- [Why](#why)
- [Demo](#demo)
- [Features](#features)
- [Installation](#installation)
- [Getting started](#getting-started)
- [Usage](#usage)
  - [Window effects](#window-effects)
  - [Liquid Glass](#liquid-glass)
  - [Metal layer](#metal-layer)
  - [WebView (WKWebView)](#webview-wkwebview)
  - [Title bar & window controls](#title-bar--window-controls)
  - [Window security](#window-security)
  - [Window tabs](#window-tabs)
  - [Share sheet](#share-sheet)
  - [Toolbar (NSToolbar)](#toolbar-nstoolbar)
  - [Menu bar (NSMenu)](#menu-bar-nsmenu)
  - [Context menu](#context-menu)
  - [Dock menu & badge](#dock-menu--badge)
  - [Menu-bar status item (tray)](#menu-bar-status-item-tray)
  - [Alerts & sheets](#alerts--sheets)
  - [Notifications](#notifications)
  - [Now Playing & media keys](#now-playing--media-keys)
  - [Touch ID / biometrics](#touch-id--biometrics)
  - [Keychain](#keychain)
  - [Permissions (TCC)](#permissions-tcc)
  - [Push notifications (APNs)](#push-notifications-apns)
  - [Handoff / Spotlight (NSUserActivity)](#handoff--spotlight-nsuseractivity)
  - [System sounds & haptics](#system-sounds--haptics)
  - [Theme & accent color](#theme--accent-color)
- [Architecture](#architecture)
- [Requirements](#requirements)
- [Building & running the demo](#building--running-the-demo)
- [Contributing](#contributing)
- [License](#license)

---

## Why

JavaFX has no built-in access to modern macOS UI and system APIs. JFX-Macios bridges that gap directly through the Objective-C runtime, so your JavaFX app can look and behave like a native Mac app:

- **No Xcode / no native build step** — everything runs through JNA at runtime.
- **Native look & feel** — real `NSVisualEffectView` blur, transparent title bars, native traffic lights.
- **Deep system integration** — menu bar, Dock menu & badge, notifications, Now Playing, Touch ID, haptics.
- **Lightweight** — the only runtime dependency is JNA.

---

## Demo

<p align="center">
  <img src="example.gif" alt="JFX-Macios demo" width="80%">
</p>

<p align="center">
  <img src="example_img2.png" alt="Transparent title bar and blur" width="80%">
</p>

<p align="center">
  <img src="example_img.png" alt="Dark mode and system integration" width="80%">
</p>

---

## Features

| Area | Feature | Status |
|------|---------|:------:|
| Effects | `NSVisualEffectView` blur (Light / Dark / Aqua / Dark Aqua) | ✅ |
| Effects | 18 materials (Sidebar, Menu, Popover, HUD, …) | ✅ |
| Effects | Alpha / transparency control | ✅ |
| Effects | **Liquid Glass** (`NSGlassEffectView`, macOS 26) via overlay window | ✅ |
| Window | Transparent / hidden title bar with native traffic lights | ✅ |
| Window | Native window drag | ✅ |
| Window | Close / minimize / zoom | ✅ |
| Window | Native **toolbar** (`NSToolbar`) with SF Symbol buttons | ✅ |
| Window | Native **window tabs** (`NSWindow` tabbing) | ✅ |
| System | **Share sheet** (`NSSharingServicePicker`) — AirDrop, Messages, Mail | ✅ |
| Security | Screenshot & screen-recording protection | ✅ |
| Dialogs | Native **alerts / sheets** (`NSAlert`) | ✅ |
| Menus | Custom app **menu bar** (`NSMenu`), sticky across focus changes | ✅ |
| Menus | **Context menu** (native right-click menu) | ✅ |
| Menus | **Dock menu** (right-click the Dock icon) | ✅ |
| Menus | Menu-bar **status item** (tray) with clickable items | ✅ |
| System | Dock **badge** (unread count / text) | ✅ |
| System | Native **notifications** (+ action button, inline reply, image, callbacks) | ✅ |
| System | **Now Playing** (`MPNowPlayingInfoCenter`) + media-key handling | ✅ |
| Security | **Touch ID / biometrics** (`LocalAuthentication`) | ✅ |
| System | System sounds | ✅ |
| System | Haptic feedback (Force Touch trackpad) | ✅ |
| System | Dark-mode detection & accent color | ✅ |
| System | **Handoff / Spotlight / Siri suggestions** (`NSUserActivity`) | ✅ |
| Security | **Keychain** secure storage (`SecItem*`) | ✅ |
| System | **Permissions** (TCC): check / request / open settings for camera, mic, screen recording, accessibility, contacts, calendar, reminders, photos, notifications | ✅ |
| System | **Push (APNs)** registration + device token & remote-notification callbacks | ✅ |
| Effects | **Metal layer** (`CAMetalLayer`) overlay with an animated render loop | ✅ |
| Effects | **WebView** (`WKWebView`) native Apple WebKit overlay, interactive | ✅ |

---

## Installation

Build artifact coordinates (`group = evo.developers.ru`, `artifactId = jfx-macios`).

The library is not yet on Maven Central. For now, publish to your local Maven repository:

```bash
cd macios
gradle publishToMavenLocal
```

Then depend on it:

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'evo.developers.ru:jfx-macios:1.0-SNAPSHOT'
    implementation 'net.java.dev.jna:jna:5.18.1'
    implementation 'net.java.dev.jna:jna-platform:5.18.1'
}
```

---

## Getting started

`HelperMacOS` is the single entry point. Create it once per `Stage`, then reach every feature through its getters.

```java
import evo.developers.ru.macios.HelperMacOS;
import evo.developers.ru.macios.effects.BlurEffectManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class App extends Application {
    @Override
    public void start(Stage stage) {
        stage.setTitle("My App");
        stage.initStyle(StageStyle.UNIFIED);

        HelperMacOS helper = HelperMacOS.getInstance(stage);

        Scene scene = new Scene(new StackPane(), 900, 600);
        scene.setFill(Color.TRANSPARENT);          // let the native blur show through
        stage.setScene(scene);

        // Native calls must run after the window exists.
        stage.setOnShown(e -> Platform.runLater(() -> {
            helper.getTitleBarManager().setupTransparentTitlebar();
            helper.getBlurEffect().apply(BlurEffectManager.Style.DARK_AQUA);
        }));

        stage.show();
    }

    public static void main(String[] args) { launch(args); }
}
```

> **Note:** every manager is bound to its `Stage`, so you never pass a window title — just call `helper.getX().doThing()`. Internally the window is resolved by its title, so give each window a unique, stable title, and call native APIs after `stage` is shown (e.g. from `setOnShown` + `Platform.runLater`).

---

## Usage

### Window effects

```java
BlurEffectManager blur = helper.getBlurEffect();

blur.apply(BlurEffectManager.Style.DARK_AQUA); // LIGHT | DARK | AQUA | DARK_AQUA
blur.setMaterial(Material.SIDEBAR);            // 18 materials available
blur.setAlpha(0.85);                           // 0.0 – 1.0
```

### Liquid Glass

Real macOS 26 `NSGlassEffectView`, rendered as a borderless child window overlaid on a JavaFX node, so it genuinely refracts what's behind it. Automatically falls back to `NSVisualEffectView` on macOS &lt; 26. The overlay ignores mouse events (clicks pass through), so attach it to an empty placeholder node that defines the glass region.

```java
LiquidGlassManager glass = helper.getLiquidGlass();

if (glass.isNativeGlassSupported()) { /* macOS 26+ */ }

glass.setCornerRadius(18)
     .setLightAppearance(true)                  // bright/frosty even in dark mode
     .setTint(1.0, 1.0, 1.0, 0.08)              // subtle tint (native glass)
     .setFallbackMaterial(Material.UNDER_WINDOW) // used only on macOS < 26
     .attach(glassPlaceholderRegion, scrollPane); // clip to a scroll region (optional)

glass.detach();
```

### Metal layer

A real `CAMetalLayer` rendered in a borderless child window overlaid on a JavaFX node, driven by an animated render loop. Uses the same anchor/clip/positioning approach as Liquid Glass and ignores mouse events. Subclass `MetalLayerManager` and override `render(drawable, texture, seconds)` to drive your own Metal pipeline; the default cycles an animated clear color.

```java
MetalLayerManager metal = helper.getMetal();

if (metal.isMetalSupported()) {
    metal.attach(metalPlaceholderRegion, scrollPane); // clip optional
}

metal.detach();
```

### WebView (WKWebView)

A real Apple `WKWebView` rendered in a borderless child window overlaid on a JavaFX node, using the same anchor/clip/positioning approach as the Metal layer. It is **interactive** — mouse clicks, scrolling, and keyboard input all work (the host window is a key-capable `NSWindow` subclass). When the anchor scrolls partly out of view, the content is **clipped, not resized** — the view keeps its full size and is offset inside a clipping container.

```java
WebViewManager web = helper.getWebView();

if (web.isSupported()) {
    web.attach(webPlaceholderRegion, scrollPane); // clip optional
    web.loadUrl("https://www.apple.com");
    // web.loadHtml("<h1>Hello</h1>");
    // web.goBack(); web.goForward(); web.reload();
}

web.detach();
```

### Title bar & window controls

```java
TitleBarManager bar = helper.getTitleBarManager();

bar.setupTransparentTitlebar(); // full-size content + native traffic lights
bar.enableWindowDrag();

// Drive a native drag from your custom header:
header.setOnMousePressed(e -> bar.startWindowDrag());

// Window operations:
helper.getWindowManager().minimize();
helper.getWindowManager().zoom();
helper.getWindowManager().close();
```

### Window security

```java
WindowSecurity security = helper.getWindowSecurity();

security.setSecure(true);          // hide from screenshots & screen recording
boolean secure = security.isSecure();
```

### Window tabs

Native `NSWindow` tabbing: give windows the same identifier and a preferred mode so the system groups them, or merge them explicitly.

```java
WindowTabManager tabs = helper.getWindowTab();

tabs.setTabbingIdentifier("editor");
tabs.setTabbingMode(WindowTabManager.TabbingMode.PREFERRED); // AUTOMATIC | PREFERRED | DISALLOWED

tabs.addTab(otherStage);   // pull another window in as a tab
tabs.toggleTabBar();
tabs.selectNextTab();
tabs.moveTabToNewWindow();
int count = tabs.getTabCount();
```

### Share sheet

The native Share sheet (`NSSharingServicePicker`) — AirDrop, Messages, Mail, Notes, etc. Anchored at the cursor, so trigger it from a button or menu handler.

```java
shareButton.setOnAction(e -> helper.getShare().shareText("Hello from JFX-Macios!"));

helper.getShare().shareURL("https://github.com/EvoDevelopers-inc/JFX-Macios");
helper.getShare().shareFiles("/path/to/report.pdf", "/path/to/photo.jpg");
helper.getShare().shareImage("/path/to/image.png");
```

### Toolbar (NSToolbar)

A native toolbar in the window title bar. Buttons use SF Symbols and run a `Runnable` on the JavaFX thread when clicked.

```java
helper.getToolbar()
    .addButton("new",    "New",    "plus",                  () -> newItem())
    .addButton("share",  "Share",  "square.and.arrow.up",   () -> share())
    .addButton("delete", "Delete", "trash",                 () -> delete())
    .apply();

helper.getToolbar().remove(); // when you no longer need it
```

### Menu bar (NSMenu)

Install a custom application menu bar. The standard app menu (About / Hide / Quit) is added automatically; add your own top-level menus with a fluent builder. The menu stays installed even after the app loses and regains focus.

```java
helper.getMenuBar()
    .appName("My App")
    .addMenu("File", m -> m
        .item("New Window", "n", () -> openWindow())
        .item("Save", "s", () -> save())
        .separator()
        .appItem("Close", "performClose:", "w"))   // wire to a standard NSApp selector
    .addMenu("View", m -> m
        .item("Bring to Front", () -> stage.toFront()))
    .apply();
```

- `item(title, action)` / `item(title, keyEquivalent, action)` — runs a Java `Runnable` on the JavaFX thread.
- `appItem(title, selector, key)` — wires an item to a standard `NSApp` selector (e.g. `terminate:`, `performClose:`, `hide:`).
- `separator()` — adds a divider.

### Context menu

A native right-click menu built with the same fluent builder. Call it from any JavaFX node's `setOnContextMenuRequested`; the menu pops up at the cursor.

```java
node.setOnContextMenuRequested(e -> helper.getContextMenu().show(m -> m
    .item("Copy", () -> copy())
    .item("Paste", () -> paste())
    .separator()
    .item("Delete", () -> delete())));
```

### Dock menu & badge

Menu shown when the user **right-clicks the Dock icon**:

```java
helper.getDockMenu().setMenu(m -> m
    .item("New Window", () -> openWindow())
    .separator()
    .item("Say Hello", () -> greet()));
```

Unread badge on the Dock icon:

```java
DockBadgeManager.setDockBadge(5);      // number
DockBadgeManager.setDockBadge("99+");  // text
DockBadgeManager.clearDockBadge();
```

### Menu-bar status item (tray)

```java
StatusBarManager tray = helper.getStatusBar();

tray.show("◆");                  // text title
tray.setSymbolIcon("sparkles");  // or an SF Symbol (macOS 11+)
tray.addItem("Open", () -> stage.toFront());
tray.addSeparator();
tray.addQuitItem("Quit");

tray.remove();                   // when you're done
```

### Alerts & sheets

Native `NSAlert`, shown either as a window-attached sheet (non-blocking, preferred) or as a modal dialog. The result is the zero-based index of the pressed button.

```java
// Sheet: callback runs on the JavaFX thread
helper.getAlert().showSheet(
    "Delete this item?", "This action cannot be undone.",
    AlertManager.Style.WARNING,           // WARNING | INFORMATIONAL | CRITICAL
    new String[]{ "Delete", "Cancel" },
    index -> { if (index == 0) delete(); });

// Modal: blocks until the user responds
int choice = helper.getAlert().showModal(
    "Quit?", "You have unsaved changes.",
    AlertManager.Style.INFORMATIONAL, "Save", "Discard", "Cancel");
```

### Notifications

```java
// Simple
NotificationManager.sendNotification("New message", "You have 1 unread message", "msg-1");

// With subtitle
NotificationManager.sendNotification("New message", "John", "Hey there!", "msg-1");

// Rich: action button, inline reply, content image
NotificationManager.sendRich(
    "New message", "John", "Hey there!", "msg-1",
    "Reply",                 // action button title
    true,                    // show inline reply field
    "Type a reply…",         // reply placeholder
    "/path/to/image.png");   // content image (or null)

// Handle interactions
NotificationManager.setListener((id, activationType, replyText) -> {
    if (activationType == NotificationManager.ACTIVATION_REPLIED) {
        System.out.println("Reply to " + id + ": " + replyText);
    }
});

NotificationManager.clearNotifications();
```

> Action & reply buttons only appear when the app's notification style is set to **Alerts** (System Settings → Notifications), not Banners.

### Now Playing & media keys

```java
NowPlayingManager np = helper.getNowPlaying();

np.setNowPlaying("Liquid Dreams", "JFX Macios", "Album", 240, 0); // title, artist, album, duration, elapsed (s)
np.setPlaybackState(NowPlayingManager.STATE_PLAYING);             // STATE_PLAYING | STATE_PAUSED | STATE_STOPPED

// React to media keys / Control Center (handlers return true when handled):
np.enableRemoteCommands();
np.onToggle(() -> { togglePlayback(); return true; });
np.onNext(()   -> { next();           return true; });
np.onPrevious(() -> { previous();     return true; });

np.clear();
```

### Touch ID / biometrics

```java
if (BiometricManager.isBiometricsAvailable()) {
    helper.getBiometric().authenticate("Confirm your identity to continue", (ok, error) -> {
        Platform.runLater(() -> {
            if (ok) unlock();
            else    System.out.println("Auth failed: " + error);
        });
    });
}

// Synchronous variant (blocks up to N seconds):
boolean ok = helper.getBiometric().authenticateBlocking("Confirm identity", 30);
```

### Keychain

Store secrets (tokens, API keys, passwords) encrypted in the macOS login keychain. Both static and instance (`helper.getKeychain()`) forms are available.

```java
String service = "com.myapp.messenger";
String account = "authToken";

KeyChainManager.keychainStore(service, account, "s3cr3t");   // add or replace
String token = KeyChainManager.keychainGet(service, account); // or null
boolean has  = KeyChainManager.keychainExists(service, account);
KeyChainManager.keychainDelete(service, account);
```

### Permissions (TCC)

Check, request, and jump to System Settings for macOS privacy permissions. Requesting triggers the native OS prompt the first time; afterwards only the settings pane can change the choice, so send denied users straight to it.

```java
PermissionsManager perms = helper.getPermissions();

PermissionStatus s = perms.status(Permission.CAMERA); // GRANTED / DENIED / NOT_DETERMINED / RESTRICTED / UNKNOWN

perms.request(Permission.MICROPHONE, granted -> {
    // called on the JavaFX thread
});

perms.openSettings(Permission.SCREEN_RECORDING); // exact Privacy pane
```

Supported: `NOTIFICATIONS`, `CAMERA`, `MICROPHONE`, `SCREEN_RECORDING`, `ACCESSIBILITY`, `INPUT_MONITORING`, `CONTACTS`, `CALENDAR`, `REMINDERS`, `PHOTOS`. Some prompts only appear for a code-signed `.app` with the matching `Info.plist` usage strings.

### Push notifications (APNs)

Register with Apple Push Notification service and receive the device token, failures, and incoming remote notifications. `register()` also requests notification authorization (a prerequisite for user-visible push alerts).

```java
helper.getPush()
    .onToken(hex -> System.out.println("APNs token: " + hex))
    .onError(err -> System.out.println("APNs error: " + err))
    .onRemoteNotification(payload -> System.out.println("push: " + payload))
    .register();

helper.getPush().unregister();
```

> APNs requires a properly code-signed `.app` bundle carrying the `aps-environment` entitlement. Unsigned dev launches typically report a `no valid "aps-environment"` registration error via `onError`.

### Handoff / Spotlight (NSUserActivity)

Advertise an `NSUserActivity` so the current task can hand off to the user's other devices, appear in Spotlight, and feed Siri Suggestions / Shortcuts. This is the closest ecosystem hook available without an App Extension.

```java
helper.getUserActivity().newActivity("com.myapp.viewItem")
    .title("Viewing item 42")
    .info("id", "42")
    .handoff(true)
    .searchable(true)     // Spotlight
    .prediction(true)     // Siri suggestions (macOS 10.15+)
    .keywords("item", "42")
    .become();

// Receive an incoming Handoff / Spotlight continuation:
helper.getUserActivity().onContinue((type, title, userInfo) -> {
    if ("com.myapp.viewItem".equals(type)) openItem(userInfo.get("id"));
});

helper.getUserActivity().resignCurrent();
```

> Handoff between devices and Siri/Shortcuts integration require the app to be a signed, bundled `.app`; advertising and Spotlight work in-process.

### System sounds & haptics

```java
SoundManager.playSound("Glass");        // Glass, Ping, Pop, Hero, Submarine, …
SoundManager.playNotificationSound();

helper.getHapticManager().haptic(
    HapticManager.HapticPattern.LEVEL_CHANGE,   // GENERIC | ALIGNMENT | LEVEL_CHANGE
    HapticManager.HapticTime.NOW);              // DEFAULT | NOW | DRAW_COMPLETED
```

### Theme & accent color

```java
boolean dark   = ThemeManager.isDarkMode();
String  accent = ThemeManager.getAccentColor();   // "Blue", "Purple", "Pink", …

ThemeManager.startAppearanceListener(isDark -> Platform.runLater(() ->
    helper.getBlurEffect().apply(
        isDark ? BlurEffectManager.Style.DARK : BlurEffectManager.Style.LIGHT)));
```

---

## Architecture

The library talks to AppKit through a tiny Objective-C runtime bridge:

- **`core/ObjC`** — thin wrappers over `objc_msgSend`, `objc_getClass`, `sel_registerName`, plus `NSString` helpers.
- **`core/ObjCRuntime`** — defines Objective-C classes at runtime and builds blocks, so native callbacks (menu clicks, notification activation, remote commands, Touch ID replies, Dock menu) can be routed back into Java.
- **Managers** — one focused class per feature (`BlurEffectManager`, `MenuBarManager`, `NotificationManager`, …), all reachable through `HelperMacOS`.

All UI-affecting calls are dispatched onto the JavaFX Application Thread.

---

## Requirements

- **macOS** 10.14 (Mojave) or later
- **Java** 11+
- **JavaFX** — any recent version
- **JNA** 5.18.1+

---

## Building & running the demo

The `macios` module contains a full demo app (`src/test/java/Main.java`) exercising every feature.

```bash
cd macios
gradle compileTestJava   # build

# Run the demo (requires a JDK with JavaFX bundled, e.g. Azul Zulu FX)
JDK=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home
JNA=$(find ~/.gradle/caches/modules-2 -name "jna-5.18.1.jar" | head -1)
JNAP=$(find ~/.gradle/caches/modules-2 -name "jna-platform-5.18.1.jar" | head -1)
"$JDK/bin/java" -cp "build/classes/java/main:build/classes/java/test:$JNA:$JNAP" Main
```

---

## Contributing

Issues and pull requests are welcome. If you're adding a native feature, follow the existing manager pattern and route any native callbacks through `ObjCRuntime`.

- Issues: <https://github.com/EvoDevelopers-inc/JFX-Macios/issues>
- Discussions: <https://github.com/EvoDevelopers-inc/JFX-Macios/discussions>

---

## License

Licensed under the **Apache License 2.0**.
