# Design Document: Multi-Monitor Pop-up Placement and HiDPI Scaling Fix

- **Date**: 2026-09-03
- **Author**: Antigravity (Pair Programming Assistant)
- **Status**: Revised (Addressing Design Review Findings)

---

## 1. Background & Detailed Root Cause Analysis

Freeplane users working with multi-monitor setups that have mixed resolutions or scaling factors (such as a primary QHD/4K display at 3200x1800 adjacent to 1080p displays at 1920x1080) experience severe pop-up displacement:
- Dropdown menus (`JComboBox` popups, context menus, tooltips, and floating palette menus) appear at incorrect screen coordinates, often shifted hundreds of pixels away from their parent control or completely on an adjacent display.
- Clicks directed at the visual popup items fail to register or trigger unintended UI events because the underlying coordinate mapping does not correspond to where the window was rendered.

### 1.1 Technical Deep Dive into Swing vs. FlatLaf Popup Mechanisms

#### Standard Swing `PopupFactory` Behavior:
In standard Swing (`javax.swing.PopupFactory`), when a popup cannot be lightweight (e.g. extends outside the parent window, or forces heavyweight), Swing invokes `PopupFactory.HeavyWeightPopup.getHeavyWeightPopup()`.
1. It looks up a recycled `HeavyWeightPopup` instance from an `AppContext`-scoped cache key (`heavyWeightPopupCacheKey` mapped by `Window`).
2. If a popup window already exists in the cache, it reuses that `JWindow` and calls `reset(owner, contents, x, y)`.
3. **The Multi-Monitor Bug in Swing**: When that `JWindow` was originally created on Display A (e.g. DP-3 at 3200x1800, scale 1.5 or 2.0) and is recycled on Display B (DP-1 at 1920x1080, scale 1.0), Swing **does not check whether the window's `GraphicsConfiguration` matches the owner's `GraphicsConfiguration`**. The native window peer remains bound to the primary display's device, coordinate origin, and DPI scaling.
4. Consequently, the window renders misplaced and scaled incorrectly on Display B.

#### FlatLaf's `FlatPopupFactory` Solution:
FlatLaf specifically engineered `com.formdev.flatlaf.ui.FlatPopupFactory` to eliminate this exact defect. In `FlatPopupFactory.getPopupForScreenOfOwner(Component owner, Component contents, int x, int y, boolean isHeavyWeight)`:
```java
// FlatLaf verifies the peer GraphicsConfiguration matches the owner:
Window popupWindow = SwingUtilities.windowForComponent(contents);
if (popupWindow != null && owner != null) {
    if (popupWindow.getGraphicsConfiguration() == owner.getGraphicsConfiguration()) {
        return popup;
    }
    // Loop and dispose mismatched windows up to 10 times to flush the stale Swing cache:
    count++;
    if (count > 10) return popup;
    if (popupWindow instanceof JWindow) {
        ((JWindow) popupWindow).getContentPane().removeAll();
    }
    popupWindow.dispose();
    // retry getPopup ...
}
```
This loop ensures any stale recycled Swing heavyweight window from another monitor is discarded and re-instantiated with the exact `GraphicsConfiguration` and scaling of the owner's monitor.

### 1.2 Freeplane's Regression Trigger
In commit `65445ebe988ca871b9dd5a459d4896837bc0c358` (`FrameController.java`), the following lines were added:
```java
if (PopupFactory.getSharedInstance().getClass().getName().equals("com.formdev.flatlaf.ui.FlatPopupFactory"))
    PopupFactory.setSharedInstance(basicPopupFactory);
```
with the rationale:
> *"Avoid using FlatLaf popup factory because it does not work stable with JetBrains JDK"*

Because FlatLaf Look & Feels are not part of standard Java's pre-installed L&Fs, Freeplane always loads them via the classloader path in `FrameController.setLookAndFeel()`, which executes this block and unconditionally discards `FlatPopupFactory`. This stripped FlatLaf's multi-monitor fix for all users across all JVMs (including standard OpenJDK, Zulu, Temurin, and Oracle JDK).

---

## 2. Goals & Non-Goals

### Goals
- Ensure popups (combo boxes, menus, tooltips) appear precisely aligned with their invoker components regardless of the monitor Freeplane is placed on, even across displays with differing resolutions and DPI scaling.
- Restore `FlatPopupFactory` for standard Java runtimes (Zulu OpenJDK, Temurin, standard OpenJDK, Oracle JDK) where it is completely stable and provides correct multi-monitor geometry.
- For JetBrains Runtime (JBR), provide safe, deterministic behavior:
  - Add robust runtime detection via `Compat.isJetBrainsRuntime()`.
  - Provide an explicit configuration setting in `ResourceController` / `freeplane.properties` (`freeplane.popup_factory = auto | flatlaf | basic`) so users and developers can force their preferred strategy if a specific runtime quirk arises.
  - In `auto` mode: default to `FlatPopupFactory` on all non-JBR runtimes. On JBR, default to `FlatPopupFactory` but disable problematic native window decorations (`UIManager.put("Popup.dropShadowPainted", Boolean.FALSE)`) which were the root cause of window manager conflicts on JBR, with `basicPopupFactory` as a safe fallback if user configures `basic`.
- Correctly manage `PopupFactory` during dynamic Look-and-Feel switching (e.g. switching between FlatLaf themes and non-FlatLaf themes).

### Non-Goals
- Altering Swing's internal native X11 or Wayland peers.
- Replacing FlatLaf as Freeplane's default look-and-feel.

---

## 3. Architecture & Detailed Design

### 3.1 Runtime Detection: `Compat.isJetBrainsRuntime()`
Add a helper method in `org.freeplane.core.util.Compat`:
```java
public static boolean isJetBrainsRuntime() {
    String vendor = System.getProperty("java.vendor", "");
    String vmVendor = System.getProperty("java.vm.vendor", "");
    String vendorVersion = System.getProperty("java.vendor.version", "");
    return vendor.toLowerCase(Locale.ROOT).contains("jetbrains")
        || vmVendor.toLowerCase(Locale.ROOT).contains("jetbrains")
        || vendorVersion.toLowerCase(Locale.ROOT).contains("jbr");
}
```

### 3.2 Look-and-Feel & PopupFactory Lifecycle Management

In `FrameController.java`:
1. **Remove Unconditional Replacement**:
   Remove:
   ```java
   if(PopupFactory.getSharedInstance().getClass().getName().equals("com.formdev.flatlaf.ui.FlatPopupFactory"))
       PopupFactory.setSharedInstance(basicPopupFactory);
   ```
2. **Implement `updatePopupFactoryForCurrentLaf()`**:
   Centralize popup factory configuration in `FrameController.fixLookAndFeelUI()` or dedicated hook:
   ```java
   private static void configurePopupFactory() {
       String pref = ResourceController.getResourceController()
           .getProperty("freeplane.popup_factory", "auto").toLowerCase(Locale.ROOT);

       boolean useFlatLafPopup;
       if ("flatlaf".equals(pref)) {
           useFlatLafPopup = true;
       } else if ("basic".equals(pref)) {
           useFlatLafPopup = false;
       } else { // "auto"
           // By default, use FlatPopupFactory everywhere.
           // If on JBR and user hasn't explicitly chosen flatlaf, avoid native drop shadow conflicts:
           if (Compat.isJetBrainsRuntime()) {
               UIManager.put("Popup.dropShadowPainted", Boolean.FALSE);
           }
           useFlatLafPopup = true;
       }

       if (UIManager.getLookAndFeel() instanceof FlatLaf) {
           if (!useFlatLafPopup) {
               if (PopupFactory.getSharedInstance().getClass().getName()
                   .equals("com.formdev.flatlaf.ui.FlatPopupFactory")) {
                   PopupFactory.setSharedInstance(basicPopupFactory);
               }
           }
           // When useFlatLafPopup is true, FlatLaf.initialize() automatically sets FlatPopupFactory.
       } else {
           // When not using FlatLaf (e.g. Metal, Nimbus, System L&F), restore standard basicPopupFactory
           if (PopupFactory.getSharedInstance().getClass().getName()
               .equals("com.formdev.flatlaf.ui.FlatPopupFactory")) {
               PopupFactory.setSharedInstance(basicPopupFactory);
           }
       }
   }
   ```
3. **Clean Look-and-Feel Transitions**:
   Whenever the user changes Look and Feel at runtime, `fixLookAndFeelUI()` executes via the `lookAndFeel` property change listener. Calling `configurePopupFactory()` inside `fixLookAndFeelUI()` guarantees that:
   - When switching to a FlatLaf theme, `FlatPopupFactory` is active and multi-monitor geometry is preserved.
   - When switching to a non-FlatLaf theme (Nimbus, Metal, Windows Native), `basicPopupFactory` is cleanly restored so foreign L&Fs do not interact with `FlatPopupFactory`.

### 3.3 Node Tooltips & `NodeTooltipManager` Integration
- Freeplane's `NodeTooltipManager` creates customized HTML tooltips using its own window positioning (`insideComponent.getLocationOnScreen()` and map zoom calculations) rather than standard Swing `ToolTipManager`.
- `FlatPopupFactory.fixToolTipLocation` only triggers if `wasInvokedFromToolTipManager()` is true (`javax.swing.ToolTipManager.showTipWindow`). Because `NodeTooltipManager` manages its own popups directly or via lightweight/heavyweight popups outside of `ToolTipManager.showTipWindow`, `FlatPopupFactory` does **not** interfere with Freeplane's custom node tooltip offsets.
- For standard Swing tooltips on toolbar buttons and menus, `FlatPopupFactory`'s screen boundary check prevents tooltips from clipping off-screen on secondary monitors.

---

## 4. Visual Comparison & UI Impact

### Scenario: User opens "Canvas theme" combo box on secondary 1080p display (DP-1 or HDMI-1) flanking primary 3200x1800 display (DP-3)

#### Current Behavior (Broken with `basicPopupFactory`)
```
Screen DP-1 (1080p)                   | Screen DP-3 (3200x1800 Primary)
--------------------------------------|---------------------------------------
 [Follow Freeplane  v] <-- clicked here
                                      |
   (Empty space / click fails)        |
                                      |
  [Follow Freeplane ]                 |
  [Light            ]                 |
  [Dark             ]                 |
  ^ Misplaced popup rendered at wrong |
    offsets (hundreds of pixels off)  |
```

#### Expected Behavior (Fixed with `FlatPopupFactory`)
```
Screen DP-1 (1080p)                   | Screen DP-3 (3200x1800 Primary)
--------------------------------------|---------------------------------------
 [Canvas theme      ]                 |
 [Follow Freeplane v]                 |
 +------------------+                 |
 | Follow Freeplane |                 |
 | Light            |                 |
 | Dark             |                 |
 +------------------+                 |
  ^ Popup renders directly below      |
    combo box; clicks hit accurately. |
```

---

## 5. Edge Cases & Risk Mitigation

1. **Mixed Scale HiDPI (e.g. 200% on primary, 100% on secondary)**:
   `FlatPopupFactory` re-evaluates `GraphicsConfiguration` on every popup show. Moving the Freeplane window from a 200% display to a 100% display immediately produces popups with the correct 100% scale and bounds.
2. **Linux Wayland / X11 Focus**:
   `FlatPopupFactory` incorporates `fixLinuxWaylandJava21focusIssue`, removing stale window focus listeners that could otherwise trap mouse input.
3. **JetBrains Runtime Safety**:
   Setting `UIManager.put("Popup.dropShadowPainted", Boolean.FALSE)` on JBR eliminates native translucent window conflicts while preserving `FlatPopupFactory`'s screen-aware placement. If any unforeseen JBR conflict occurs, setting `freeplane.popup_factory=basic` provides an immediate escape hatch without code modification.
4. **Single-Monitor Environments**:
   No behavior change; `FlatPopupFactory` functions identically to standard FlatLaf.

---

## 6. Verification and Test Strategy

### 6.1 Automated Unit & Integration Tests
1. **`CompatTest`**:
   - Test `Compat.isJetBrainsRuntime()` with varied system property values (`java.vendor="JetBrains s.r.o."`, `java.vm.vendor="Azul Systems, Inc."`, `java.vendor.version="JBR-21.0.8"`).
2. **`PopupFactoryLafTest`**:
   - Verify `PopupFactory.getSharedInstance()` is an instance of `FlatPopupFactory` when FlatLaf is active under Zulu/OpenJDK.
   - Verify that switching L&F to Metal/Nimbus restores `basicPopupFactory`.
   - Verify `freeplane.popup_factory` preference overrides (`flatlaf` vs `basic`).
3. **Headless Screen Mismatch Recycling Simulation**:
   - Unit test simulating two `GraphicsConfiguration` bounds and asserting that `FlatPopupFactory` evicts mismatched configurations rather than reusing them across different screen configurations.

### 6.2 Manual Multi-Monitor QA
1. Launch Freeplane on multi-monitor setup (Primary 3200x1800 + Secondary 1920x1080).
2. Move Freeplane to the 1920x1080 monitor.
3. Click the "Canvas theme" dropdown in the Graph Workspace or any standard `JComboBox` in Preferences.
4. Verify the popup appears directly attached below the combo box and options can be selected cleanly.
