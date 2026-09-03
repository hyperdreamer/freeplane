# Technical Specification: Multi-Monitor Pop-up Placement and HiDPI Scaling Fix

- **Date**: 2026-09-03
- **Author**: Antigravity (Pair Programming Assistant - Specification Drafter)
- **Status**: Draft (Ready for Review)
- **Design Document Reference**: `docs/superpowers/specs/2026-09-03-multimonitor-popup-misplaced-design.md`

---

## 1. Executive Summary & Problem Scope

In Freeplane, popups (such as `JComboBox` drop-downs, menu bars, context menus, and tooltips) become severely misplaced when Freeplane is displayed on secondary monitors in multi-monitor setups with mismatched resolutions or scale factors (e.g. QHD/4K primary display at 3200x1800 with 150%/200% scaling, and 1080p secondary displays at 1920x1080 with 100% scaling).

The defect was caused by an unconditional override in `FrameController.java` (`PopupFactory.setSharedInstance(basicPopupFactory);`), which discarded FlatLaf's multi-monitor-aware `FlatPopupFactory` and reverted to standard Swing `PopupFactory`. Standard Swing recycles heavyweight `JWindow` instances without checking whether their `GraphicsConfiguration` matches the target component's display, causing popups to render using coordinates and scaling from the wrong screen.

This specification defines the exact technical implementation to restore `FlatPopupFactory` across supported JVMs, manage configuration via a new `freeplane.popup_factory` preference property, handle JetBrains Runtime (JBR) edge cases safely, ensure clean Look-and-Feel dynamic switching, and verify behavior with automated unit and regression tests.

---

## 2. Component & File Changes

### 2.1 Summary of File Modifications and Additions

| Action | Path | Description |
| :--- | :--- | :--- |
| **Modify** | `freeplane/src/main/java/org/freeplane/core/util/Compat.java` | Add `isJetBrainsRuntime()` runtime detection method. |
| **Modify** | `freeplane/src/main/java/org/freeplane/features/ui/FrameController.java` | Remove unconditional discard of `FlatPopupFactory`, introduce `configurePopupFactory()`, invoke during L&F updates. |
| **Modify** | `freeplane/src/viewer/resources/freeplane.properties` | Add default property `freeplane.popup_factory=auto` with documentation. |
| **Create** | `freeplane/src/test/java/org/freeplane/core/util/CompatTest.java` | Unit tests for `Compat.isJetBrainsRuntime()`. |
| **Create** | `freeplane/src/test/java/org/freeplane/features/ui/PopupFactoryConfigurationTest.java` | Unit tests for `PopupFactory` selection, L&F dynamic transitions, and preference override logic. |

---

## 3. Detailed Technical Specifications

### 3.1 `org.freeplane.core.util.Compat`

#### Exact Package and Imports
- **Package**: `org.freeplane.core.util`
- **Imports to verify/add**: `java.util.Locale`

#### Method Signature & Implementation
```java
/**
 * Detects whether the current Java Virtual Machine is JetBrains Runtime (JBR).
 *
 * Checks java.vendor, java.vm.vendor, and java.vendor.version for presence of
 * "jetbrains" or "jbr" (case-insensitive).
 *
 * @return true if running under JetBrains Runtime, false otherwise
 */
public static boolean isJetBrainsRuntime() {
    String vendor = System.getProperty("java.vendor", "");
    String vmVendor = System.getProperty("java.vm.vendor", "");
    String vendorVersion = System.getProperty("java.vendor.version", "");
    return vendor.toLowerCase(Locale.ROOT).contains("jetbrains")
        || vmVendor.toLowerCase(Locale.ROOT).contains("jetbrains")
        || vendorVersion.toLowerCase(Locale.ROOT).contains("jbr");
}
```

#### Contract & Edge Cases
- Must never throw `NullPointerException` or `SecurityException` if system properties are `null` or inaccessible (safely fallback to empty string via `System.getProperty(key, "")`).
- Case-insensitive comparison using invariant `Locale.ROOT`.
- Recognizes all common JBR vendor variations:
  - `java.vendor` = `"JetBrains s.r.o."`
  - `java.vm.vendor` = `"JetBrains s.r.o."`
  - `java.vendor.version` = `"JBR-21.0.8+9-1004.2-nomod"` or similar release formats.

---

### 3.2 `org.freeplane.features.ui.FrameController`

#### Exact Package and Imports
- **Package**: `org.freeplane.features.ui`
- **Existing Imports**:
  - `javax.swing.PopupFactory`
  - `javax.swing.UIManager`
  - `com.formdev.flatlaf.FlatLaf`
  - `org.freeplane.core.resources.ResourceController`
  - `org.freeplane.core.util.Compat`
- **Constant Names**:
  ```java
  public static final String POPUP_FACTORY_PROPERTY = "freeplane.popup_factory";
  public static final String POPUP_FACTORY_AUTO = "auto";
  public static final String POPUP_FACTORY_FLATLAF = "flatlaf";
  public static final String POPUP_FACTORY_BASIC = "basic";
  public static final String FLAT_POPUP_FACTORY_CLASS = "com.formdev.flatlaf.ui.FlatPopupFactory";
  ```

#### Modifications to Existing Code

##### 1. Removal in `setLookAndFeel(final String lookAndFeel)`
Around line 668-670 of `freeplane/src/main/java/org/freeplane/features/ui/FrameController.java`:
**Delete**:
```java
if(PopupFactory.getSharedInstance().getClass().getName().equals("com.formdev.flatlaf.ui.FlatPopupFactory"))
    PopupFactory.setSharedInstance(basicPopupFactory);
```
*Rationale*: When FlatLaf is set, it installs `FlatPopupFactory` by default. Stripping it immediately in `setLookAndFeel()` prevented `FlatPopupFactory` from functioning on all platforms.

##### 2. Addition of `configurePopupFactory()`
Add method to `FrameController.java`:
```java
public static void configurePopupFactory() {
    if (Compat.isApplet()) {
        return;
    }

    final ResourceController resourceController = ResourceController.getResourceController();
    final String pref = (resourceController != null)
        ? resourceController.getProperty(POPUP_FACTORY_PROPERTY, POPUP_FACTORY_AUTO).trim().toLowerCase(Locale.ROOT)
        : POPUP_FACTORY_AUTO;

    final boolean isFlatLaf = UIManager.getLookAndFeel() instanceof FlatLaf;

    if (POPUP_FACTORY_BASIC.equals(pref)) {
        // User explicitly forced basic Swing PopupFactory
        if (FLAT_POPUP_FACTORY_CLASS.equals(PopupFactory.getSharedInstance().getClass().getName())) {
            PopupFactory.setSharedInstance(basicPopupFactory);
        }
    } else if (POPUP_FACTORY_FLATLAF.equals(pref)) {
        // User explicitly forced FlatLaf PopupFactory: ensure it is active if current L&F is FlatLaf
        if (isFlatLaf) {
            ensureFlatPopupFactoryInstalled();
        } else {
            if (FLAT_POPUP_FACTORY_CLASS.equals(PopupFactory.getSharedInstance().getClass().getName())) {
                PopupFactory.setSharedInstance(basicPopupFactory);
            }
        }
    } else {
        // "auto" (default)
        if (isFlatLaf) {
            if (Compat.isJetBrainsRuntime()) {
                // Avoid native drop shadow window artifacts / WM conflicts on JBR
                UIManager.put("Popup.dropShadowPainted", Boolean.FALSE);
            }
            ensureFlatPopupFactoryInstalled();
        } else {
            // Non-FlatLaf L&F (e.g. Metal, Nimbus, Windows)
            if (FLAT_POPUP_FACTORY_CLASS.equals(PopupFactory.getSharedInstance().getClass().getName())) {
                PopupFactory.setSharedInstance(basicPopupFactory);
            }
        }
    }
}

private static void ensureFlatPopupFactoryInstalled() {
    if (!FLAT_POPUP_FACTORY_CLASS.equals(PopupFactory.getSharedInstance().getClass().getName())) {
        try {
            Class<?> factoryClass = FrameController.class.getClassLoader().loadClass(FLAT_POPUP_FACTORY_CLASS);
            PopupFactory flatFactory = (PopupFactory) factoryClass.getDeclaredConstructor().newInstance();
            PopupFactory.setSharedInstance(flatFactory);
        } catch (Throwable t) {
            LogUtils.warn("Could not instantiate FlatPopupFactory, falling back to basicPopupFactory: " + t.getMessage());
            PopupFactory.setSharedInstance(basicPopupFactory);
        }
    }
}
```

##### 3. Integration into Look-and-Feel Lifecycle
In `FrameController.fixLookAndFeelUI()`:
`fixLookAndFeelUI()` is called both on initial startup and on every `"lookAndFeel"` property change event.
Invoke `configurePopupFactory()` inside `fixLookAndFeelUI()`:
```java
private static void fixLookAndFeelUI() {
    OSKeyBindingManager.applyToCurrentLookAndFeel();
    configureFlatLookAndFeel();
    configurePopupFactory();
    UIManager.put("Button.defaultButtonFollowsFocus", Boolean.TRUE);
    ...
```

---

### 3.3 Default Configuration in `freeplane.properties`

Add to `freeplane/src/viewer/resources/freeplane.properties`:
```properties
# Popup factory strategy: 'auto' (recommended), 'flatlaf', or 'basic'
# 'auto': Uses multi-monitor-aware FlatPopupFactory for FlatLaf themes with JBR compatibility adjustments.
# 'flatlaf': Forces FlatPopupFactory when FlatLaf is active.
# 'basic': Forces standard Swing PopupFactory (fallback if custom window managers encounter issues).
freeplane.popup_factory=auto
```

---

## 4. State Transitions and Lifecycle Sequencing

### 4.1 Application Startup Sequence
1. Freeplane initializes `FrameController` static initializer:
   - `UIManager.getInstalledLookAndFeels();`
   - `OSKeyBindingManager.initialize();`
   - Property change listener on `"lookAndFeel"` registered.
   - `basicPopupFactory = new PopupFactory();` captured as standard fallback reference.
2. Freeplane resolves the user or default Look and Feel (e.g. `FlatLightLaf`, `FlatDarculaLaf`).
3. `UIManager.setLookAndFeel(...)` installs the L&F:
   - If FlatLaf, FlatLaf's `initialize()` sets `PopupFactory.setSharedInstance(new FlatPopupFactory())`.
   - The `"lookAndFeel"` property change listener triggers `fixLookAndFeelUI()`.
4. `fixLookAndFeelUI()` calls `configurePopupFactory()`:
   - Checks `freeplane.popup_factory` (default `"auto"`).
   - If JBR detected via `Compat.isJetBrainsRuntime()`, puts `"Popup.dropShadowPainted" -> Boolean.FALSE`.
   - Verifies `FlatPopupFactory` is installed.
   - Popups created subsequent to startup are screen-geometry-aware.

### 4.2 Dynamic Look-and-Feel Switching
When the user changes the Look and Feel at runtime (e.g. from `FlatIntelliJLaf` to `Metal` or vice versa):
1. Swing invokes `UIManager.setLookAndFeel(newLaf)`.
2. As part of Swing's uninstallation of the old Look and Feel, `FlatLaf.uninitialize()` attempts to restore its internal `oldPopupFactory` field:
   ```java
   // In FlatLaf.uninitialize():
   if (this.oldPopupFactory != null) {
       PopupFactory.setSharedInstance(this.oldPopupFactory);
       this.oldPopupFactory = null;
   }
   ```
3. Immediately after `setLookAndFeel()` finishes, Swing fires a `PropertyChangeEvent` for `"lookAndFeel"`, which invokes `FrameController.fixLookAndFeelUI()`.
4. `FrameController.fixLookAndFeelUI()` calls `configurePopupFactory()`, which acts as the authoritative final reconciliation hook:
   - **User changes theme from FlatLaf to Metal/Nimbus/System**:
     - `UIManager.getLookAndFeel() instanceof FlatLaf` evaluates to `false`.
     - `configurePopupFactory()` checks if `FlatPopupFactory` is installed; detects it is (or whatever `uninitialize()` left), and ensures `PopupFactory.setSharedInstance(basicPopupFactory)` is restored.
     - Non-FlatLaf L&Fs do not suffer classloader or styling leaks from `FlatPopupFactory`.
   - **User changes theme from Metal/Nimbus/System back to FlatLaf**:
     - `UIManager.setLookAndFeel(...)` sets FlatLaf.
     - `fixLookAndFeelUI()` calls `configurePopupFactory()`.
     - `ensureFlatPopupFactoryInstalled()` ensures `FlatPopupFactory` is restored as the shared instance.
5. All UI components are updated via `SwingUtilities.updateComponentTreeUI()`.

### 4.3 Dynamic Preference Update
If the user or a script modifies `freeplane.popup_factory` at runtime via `ResourceController.setProperty("freeplane.popup_factory", ...)`, calling `FrameController.configurePopupFactory()` immediately re-evaluates and switches the active `PopupFactory` instance without requiring an application restart.

---

## 5. Error Contracts & Fallback Matrix

| Configuration (`freeplane.popup_factory`) | Current L&F | Runtime (JVM) | Resulting `PopupFactory` | Additional UI Settings |
| :--- | :--- | :--- | :--- | :--- |
| `auto` (default) | `FlatLaf` (Light/Dark/Darcula) | Zulu, Temurin, OpenJDK, Oracle | `FlatPopupFactory` | Standard FlatLaf defaults |
| `auto` (default) | `FlatLaf` | JetBrains Runtime (JBR) | `FlatPopupFactory` | `Popup.dropShadowPainted = false` |
| `auto` (default) | Non-FlatLaf (Metal, Nimbus, etc.) | Any JVM | `basicPopupFactory` | None |
| `flatlaf` | `FlatLaf` | Any JVM | `FlatPopupFactory` | Drop shadow enabled (unless JBR explicitly disabled) |
| `flatlaf` | Non-FlatLaf | Any JVM | `basicPopupFactory` | Fallback to basic (cannot use FlatPopup with non-Flat L&F) |
| `basic` | Any L&F | Any JVM | `basicPopupFactory` | None |
| Invalid / unknown string | `FlatLaf` | Any JVM | Falls back to `auto` behavior (`FlatPopupFactory`) | Safe default |

### Exception Safety
If reflection or instantiation of `FlatPopupFactory` fails for any reason (e.g. strict OSGi module encapsulation or missing class in headless test harness), `ensureFlatPopupFactoryInstalled()` logs a warning via `LogUtils.warn(...)` and sets `PopupFactory.setSharedInstance(basicPopupFactory)`. Freeplane will never crash or fail to initialize menus/popups.

---

## 6. Detailed Automated Test Specifications

### 6.1 Test Class 1: `org.freeplane.core.util.CompatTest`
- **Location**: `freeplane/src/test/java/org/freeplane/core/util/CompatTest.java`
- **Framework**: JUnit 4 / AssertJ

#### Test Cases:
1. `isJetBrainsRuntime_withStandardJdk_returnsFalse`:
   - Set system properties:
     - `java.vendor` = `"Azul Systems, Inc."`
     - `java.vm.vendor` = `"Azul Systems, Inc."`
     - `java.vendor.version` = `"Zulu21.38+21-CA"`
   - Assert: `assertThat(Compat.isJetBrainsRuntime()).isFalse();`
2. `isJetBrainsRuntime_withJetBrainsVendor_returnsTrue`:
   - Set `java.vendor` = `"JetBrains s.r.o."`
   - Assert: `assertThat(Compat.isJetBrainsRuntime()).isTrue();`
3. `isJetBrainsRuntime_withJetBrainsVmVendor_returnsTrue`:
   - Set `java.vendor` = `"Oracle Corporation"`, `java.vm.vendor` = `"JetBrains s.r.o."`
   - Assert: `assertThat(Compat.isJetBrainsRuntime()).isTrue();`
4. `isJetBrainsRuntime_withJbrVendorVersion_returnsTrue`:
   - Set `java.vendor` = `"Custom"`, `java.vendor.version` = `"JBR-21.0.8+9-1004.2-nomod"`
   - Assert: `assertThat(Compat.isJetBrainsRuntime()).isTrue();`
5. `isJetBrainsRuntime_withNullOrEmptyProperties_returnsFalseWithoutException`:
   - Clear properties or set empty strings.
   - Assert: `assertThat(Compat.isJetBrainsRuntime()).isFalse();`

*Note: Property mutations in tests must be wrapped in `try ... finally` blocks restoring original system properties.*

---

### 6.2 Test Class 2: `org.freeplane.features.ui.PopupFactoryConfigurationTest`
- **Location**: `freeplane/src/test/java/org/freeplane/features/ui/PopupFactoryConfigurationTest.java`
- **Framework**: JUnit 4 / AssertJ
- **Prerequisites**: Headless test environment (`java.awt.headless=true`).
- **Test Fixture Isolation & Clean-up**:
  - In `@Before`: ensure `Compat.setIsApplet(false)` is set so tests run deterministically.
  - In `@After` and `@AfterClass`: strictly restore all mutated system properties (`java.vendor`, `java.vm.vendor`, `java.vendor.version`), restore `PopupFactory.setSharedInstance(new PopupFactory())`, clear `UIManager.put("Popup.dropShadowPainted", null)`, and reset `ResourceController` test properties to prevent test pollution in shared Gradle test workers.

#### Test Cases:
1. `configurePopupFactory_withFlatLafAndAuto_installsFlatPopupFactory`:
   - Setup: Initialize `ResourceController` with test properties. Set property `freeplane.popup_factory` to `"auto"`. Set `UIManager.setLookAndFeel(new FlatLightLaf())`.
   - Action: `FrameController.configurePopupFactory();`
   - Assert: `assertThat(PopupFactory.getSharedInstance().getClass().getName()).isEqualTo("com.formdev.flatlaf.ui.FlatPopupFactory");`
2. `configurePopupFactory_withFlatLafAndBasicPref_installsBasicPopupFactory`:
   - Setup: Set `freeplane.popup_factory` to `"basic"`. Set `UIManager.setLookAndFeel(new FlatLightLaf())`.
   - Action: `FrameController.configurePopupFactory();`
   - Assert: `assertThat(PopupFactory.getSharedInstance().getClass().getName()).doesNotContain("FlatPopupFactory");`
3. `configurePopupFactory_withNonFlatLaf_revertsToBasicPopupFactory`:
   - Setup: Set `freeplane.popup_factory` to `"auto"`. Set `UIManager.setLookAndFeel(new javax.swing.plaf.metal.MetalLookAndFeel())`.
   - Action: `FrameController.configurePopupFactory();`
   - Assert: `assertThat(PopupFactory.getSharedInstance().getClass().getName()).doesNotContain("FlatPopupFactory");`
4. `configurePopupFactory_onJbrRuntime_disablesDropShadowOnAuto`:
   - Setup: Simulate JBR (`System.setProperty("java.vendor", "JetBrains s.r.o.")`). Set `freeplane.popup_factory` to `"auto"`. Set `UIManager.setLookAndFeel(new FlatLightLaf())`.
   - Action: `FrameController.configurePopupFactory();`
   - Assert:
     - `assertThat(PopupFactory.getSharedInstance().getClass().getName()).isEqualTo("com.formdev.flatlaf.ui.FlatPopupFactory");`
     - `assertThat(UIManager.get("Popup.dropShadowPainted")).isEqualTo(Boolean.FALSE);`
5. `configurePopupFactory_dynamicLafSwitching`:
   - Step A: Set FlatLaf -> verify `FlatPopupFactory` installed.
   - Step B: Switch to Metal -> verify `basicPopupFactory` restored.
   - Step C: Switch back to FlatLaf -> verify `FlatPopupFactory` restored.

---

### 6.3 Verification Commands
To compile and execute the test suites:
```bash
gradle :freeplane:test --tests "org.freeplane.core.util.CompatTest"
gradle :freeplane:test --tests "org.freeplane.features.ui.PopupFactoryConfigurationTest"
```

---

## 7. Migration, Compatibility & Rollback

1. **Backward Compatibility**:
   Existing user configuration directories (`.freeplane`) will not have `freeplane.popup_factory` set. The default value `"auto"` automatically kicks in via `getProperty(..., "auto")`.
2. **Rollback Option**:
   If an unexpected window manager conflict is encountered on Linux or an unsupported custom environment, the user can set:
   ```properties
   freeplane.popup_factory=basic
   ```
   in their preferences, immediately reverting to legacy Swing behavior without needing to patch or downgrade Freeplane.
3. **No Breaking API Changes**:
   No public API interfaces in `freeplane_api` are altered. All modifications are internal to `FrameController`, `Compat`, and resource configuration.
