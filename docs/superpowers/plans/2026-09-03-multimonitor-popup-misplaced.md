# Multi-Monitor Pop-up Placement and HiDPI Scaling Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore multi-monitor and HiDPI-aware popup behavior by utilizing FlatLaf's `FlatPopupFactory` across supported JVMs, governed by a configurable `freeplane.popup_factory` preference property, with JetBrains Runtime (JBR) edge-case accommodations and clean dynamic Look-and-Feel switching.

**Architecture:** Add `Compat.isJetBrainsRuntime()` for runtime JVM detection; introduce `freeplane.popup_factory` (`auto` default, `flatlaf`, `basic`) in `freeplane.properties`; remove the unconditional discard of `FlatPopupFactory` in `FrameController.setLookAndFeel()`; introduce `FrameController.configurePopupFactory()` integrated into `FrameController.fixLookAndFeelUI()`; and guard fallback behaviors with comprehensive unit and integration tests.

**Tech Stack:** Java 8 language target (JDK 21.0.8-zulu runtime), Swing, FlatLaf (`com.formdev.flatlaf`), Gradle multi-project build, JUnit 4, AssertJ, Mockito.

## Global Constraints

- Use `gradle` (never `gradlew`); build/test using Java 21 from `~/.sdkman/candidates/java/21.0.8-zulu`.
- Java 8 language target; 4-space indentation; UTF-8 source encoding; no new runtime dependencies.
- Keep changes minimal and focused on `freeplane/src/main/java/org/freeplane/core/util/Compat.java`, `freeplane/src/main/java/org/freeplane/features/ui/FrameController.java`, and `freeplane/src/viewer/resources/freeplane.properties`.
- Do NOT modify public API interfaces in `freeplane_api`.
- Never put a plain `##` heading inside a task body; use `###` for subheadings.
- Test commands must specify target classes via `gradle :freeplane:test --tests "..."`.
- All test fixtures mutating system properties or `PopupFactory.setSharedInstance(...)` must clean up state in `finally` or `@After`/`@AfterClass` blocks to ensure isolation across test suites.

## Task 1: Add Compat.isJetBrainsRuntime() with TDD

**Implementer tier:** Frontier

**Files:**

- Modify: `freeplane/src/main/java/org/freeplane/core/util/Compat.java`
- Create: `freeplane/src/test/java/org/freeplane/core/util/CompatTest.java`

**Interfaces:**

- Consumes: `java.lang.System.getProperty(String, String)`.
- Produces: `public static boolean Compat.isJetBrainsRuntime()` in `org.freeplane.core.util.Compat`. Returns `true` if `java.vendor`, `java.vm.vendor`, or `java.vendor.version` contains `"jetbrains"` or `"jbr"` (case-insensitive with `Locale.ROOT`), otherwise returns `false`.

- [ ] **Step 1: Write the failing unit tests in CompatTest**

Create `freeplane/src/test/java/org/freeplane/core/util/CompatTest.java`:

```java
package org.freeplane.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CompatTest {
    private String originalVendor;
    private String originalVmVendor;
    private String originalVendorVersion;

    @Before
    public void setUp() {
        originalVendor = System.getProperty("java.vendor");
        originalVmVendor = System.getProperty("java.vm.vendor");
        originalVendorVersion = System.getProperty("java.vendor.version");
    }

    @After
    public void tearDown() {
        restoreProperty("java.vendor", originalVendor);
        restoreProperty("java.vm.vendor", originalVmVendor);
        restoreProperty("java.vendor.version", originalVendorVersion);
    }

    private void restoreProperty(String key, String value) {
        if (value != null) {
            System.setProperty(key, value);
        } else {
            System.clearProperty(key);
        }
    }

    @Test
    public void isJetBrainsRuntime_withStandardJdk_returnsFalse() {
        System.setProperty("java.vendor", "Azul Systems, Inc.");
        System.setProperty("java.vm.vendor", "Azul Systems, Inc.");
        System.setProperty("java.vendor.version", "Zulu21.38+21-CA");

        assertThat(Compat.isJetBrainsRuntime()).isFalse();
    }

    @Test
    public void isJetBrainsRuntime_withJetBrainsVendor_returnsTrue() {
        System.setProperty("java.vendor", "JetBrains s.r.o.");
        System.setProperty("java.vm.vendor", "OpenJDK 64-Bit Server VM");
        System.clearProperty("java.vendor.version");

        assertThat(Compat.isJetBrainsRuntime()).isTrue();
    }

    @Test
    public void isJetBrainsRuntime_withJetBrainsVmVendor_returnsTrue() {
        System.setProperty("java.vendor", "Oracle Corporation");
        System.setProperty("java.vm.vendor", "JetBrains s.r.o.");
        System.clearProperty("java.vendor.version");

        assertThat(Compat.isJetBrainsRuntime()).isTrue();
    }

    @Test
    public void isJetBrainsRuntime_withJbrVendorVersion_returnsTrue() {
        System.setProperty("java.vendor", "Custom");
        System.setProperty("java.vm.vendor", "Custom VM");
        System.setProperty("java.vendor.version", "JBR-21.0.8+9-1004.2-nomod");

        assertThat(Compat.isJetBrainsRuntime()).isTrue();
    }

    @Test
    public void isJetBrainsRuntime_withNullOrEmptyProperties_returnsFalseWithoutException() {
        System.clearProperty("java.vendor");
        System.clearProperty("java.vm.vendor");
        System.clearProperty("java.vendor.version");

        assertThat(Compat.isJetBrainsRuntime()).isFalse();
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails to compile or run**

Run: `JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane:test --tests "org.freeplane.core.util.CompatTest"`
Expected: FAIL, cannot find symbol `isJetBrainsRuntime()`.

- [ ] **Step 3: Implement Compat.isJetBrainsRuntime()**

Modify `freeplane/src/main/java/org/freeplane/core/util/Compat.java`:
Ensure `java.util.Locale` is imported.
Add method to `Compat`:

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

- [ ] **Step 4: Run the test and confirm it passes**

Run: `JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane:test --tests "org.freeplane.core.util.CompatTest"`
Expected: PASS, 5 tests completed, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add freeplane/src/main/java/org/freeplane/core/util/Compat.java freeplane/src/test/java/org/freeplane/core/util/CompatTest.java
git commit -m "Add Compat.isJetBrainsRuntime() for JBR detection"
```

## Task 2: Add freeplane.popup_factory properties & defaults

**Implementer tier:** Frontier

**Files:**

- Modify: `freeplane/src/viewer/resources/freeplane.properties`

**Interfaces:**

- Consumes: Nothing.
- Produces: Property entry `freeplane.popup_factory=auto` in `freeplane/src/viewer/resources/freeplane.properties` with explanatory documentation comments.

- [ ] **Step 1: Inspect freeplane.properties and append configuration**

Modify `freeplane/src/viewer/resources/freeplane.properties`:
Append the new property configuration with documentation:

```properties
# Popup factory strategy: 'auto' (recommended), 'flatlaf', or 'basic'
# 'auto': Uses multi-monitor-aware FlatPopupFactory for FlatLaf themes with JBR compatibility adjustments.
# 'flatlaf': Forces FlatPopupFactory when FlatLaf is active.
# 'basic': Forces standard Swing PopupFactory (fallback if custom window managers encounter issues).
freeplane.popup_factory=auto
```

- [ ] **Step 2: Verify properties load and resource compile**

Run: `JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane:processResources :freeplane:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add freeplane/src/viewer/resources/freeplane.properties
git commit -m "Add default freeplane.popup_factory=auto property"
```

## Task 3: Implement FrameController.configurePopupFactory() and integrate into fixLookAndFeelUI() / setLookAndFeel()

**Implementer tier:** Frontier

**Files:**

- Modify: `freeplane/src/main/java/org/freeplane/features/ui/FrameController.java:660-705`
- Create: `freeplane/src/test/java/org/freeplane/features/ui/PopupFactoryConfigurationTest.java`

**Interfaces:**

- Consumes:
  - `Compat.isApplet(): boolean`
  - `Compat.isJetBrainsRuntime(): boolean`
  - `ResourceController.getResourceController().getProperty(String, String): String`
  - `UIManager.getLookAndFeel(): LookAndFeel`
  - `UIManager.put(Object, Object): Object`
  - `PopupFactory.getSharedInstance(): PopupFactory`
  - `PopupFactory.setSharedInstance(PopupFactory): void`
- Produces:
  - Constants in `FrameController`:
    - `public static final String POPUP_FACTORY_PROPERTY = "freeplane.popup_factory";`
    - `public static final String POPUP_FACTORY_AUTO = "auto";`
    - `public static final String POPUP_FACTORY_FLATLAF = "flatlaf";`
    - `public static final String POPUP_FACTORY_BASIC = "basic";`
    - `public static final String FLAT_POPUP_FACTORY_CLASS = "com.formdev.flatlaf.ui.FlatPopupFactory";`
  - Method `public static void FrameController.configurePopupFactory()`
  - Removed unconditional reset of `PopupFactory.setSharedInstance(basicPopupFactory)` in `setLookAndFeel()`.
  - Invocation of `configurePopupFactory()` inside `FrameController.fixLookAndFeelUI()`.

- [ ] **Step 1: Write the failing unit and integration tests in PopupFactoryConfigurationTest**

Create `freeplane/src/test/java/org/freeplane/features/ui/PopupFactoryConfigurationTest.java`:

```java
package org.freeplane.features.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Locale;
import javax.swing.PopupFactory;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.Compat;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.formdev.flatlaf.FlatLightLaf;

public class PopupFactoryConfigurationTest {
    private static PopupFactory initialPopupFactory;
    private MockedStatic<ResourceController> mockedResourceControllerStatic;
    private ResourceController resourceControllerMock;
    private String originalVendor;
    private String originalVmVendor;
    private String originalVendorVersion;

    @BeforeClass
    public static void saveInitialState() {
        initialPopupFactory = PopupFactory.getSharedInstance();
    }

    @AfterClass
    public static void restoreInitialState() {
        PopupFactory.setSharedInstance(initialPopupFactory);
    }

    @Before
    public void setUp() {
        originalVendor = System.getProperty("java.vendor");
        originalVmVendor = System.getProperty("java.vm.vendor");
        originalVendorVersion = System.getProperty("java.vendor.version");

        mockedResourceControllerStatic = Mockito.mockStatic(ResourceController.class);
        resourceControllerMock = mock(ResourceController.class);
        mockedResourceControllerStatic.when(ResourceController::getResourceController).thenReturn(resourceControllerMock);
        when(resourceControllerMock.getProperty(eq(FrameController.POPUP_FACTORY_PROPERTY), anyString()))
            .thenReturn(FrameController.POPUP_FACTORY_AUTO);
    }

    @After
    public void tearDown() {
        mockedResourceControllerStatic.close();
        restoreProperty("java.vendor", originalVendor);
        restoreProperty("java.vm.vendor", originalVmVendor);
        restoreProperty("java.vendor.version", originalVendorVersion);
        UIManager.put("Popup.dropShadowPainted", null);
        PopupFactory.setSharedInstance(new PopupFactory());
    }

    private void restoreProperty(String key, String value) {
        if (value != null) {
            System.setProperty(key, value);
        } else {
            System.clearProperty(key);
        }
    }

    @Test
    public void configurePopupFactory_withFlatLafAndAuto_installsFlatPopupFactory() throws Exception {
        when(resourceControllerMock.getProperty(eq(FrameController.POPUP_FACTORY_PROPERTY), anyString()))
            .thenReturn("auto");
        UIManager.setLookAndFeel(new FlatLightLaf());

        FrameController.configurePopupFactory();

        assertThat(PopupFactory.getSharedInstance().getClass().getName())
            .isEqualTo("com.formdev.flatlaf.ui.FlatPopupFactory");
    }

    @Test
    public void configurePopupFactory_withFlatLafAndBasicPref_installsBasicPopupFactory() throws Exception {
        when(resourceControllerMock.getProperty(eq(FrameController.POPUP_FACTORY_PROPERTY), anyString()))
            .thenReturn("basic");
        UIManager.setLookAndFeel(new FlatLightLaf());

        FrameController.configurePopupFactory();

        assertThat(PopupFactory.getSharedInstance().getClass().getName())
            .doesNotContain("FlatPopupFactory");
    }

    @Test
    public void configurePopupFactory_withNonFlatLaf_revertsToBasicPopupFactory() throws Exception {
        when(resourceControllerMock.getProperty(eq(FrameController.POPUP_FACTORY_PROPERTY), anyString()))
            .thenReturn("auto");
        UIManager.setLookAndFeel(new MetalLookAndFeel());

        FrameController.configurePopupFactory();

        assertThat(PopupFactory.getSharedInstance().getClass().getName())
            .doesNotContain("FlatPopupFactory");
    }

    @Test
    public void configurePopupFactory_onJbrRuntime_disablesDropShadowOnAuto() throws Exception {
        System.setProperty("java.vendor", "JetBrains s.r.o.");
        when(resourceControllerMock.getProperty(eq(FrameController.POPUP_FACTORY_PROPERTY), anyString()))
            .thenReturn("auto");
        UIManager.setLookAndFeel(new FlatLightLaf());

        FrameController.configurePopupFactory();

        assertThat(PopupFactory.getSharedInstance().getClass().getName())
            .isEqualTo("com.formdev.flatlaf.ui.FlatPopupFactory");
        assertThat(UIManager.get("Popup.dropShadowPainted")).isEqualTo(Boolean.FALSE);
    }

    @Test
    public void configurePopupFactory_dynamicLafSwitching() throws Exception {
        when(resourceControllerMock.getProperty(eq(FrameController.POPUP_FACTORY_PROPERTY), anyString()))
            .thenReturn("auto");

        // Step A: Set FlatLaf -> verify FlatPopupFactory installed
        UIManager.setLookAndFeel(new FlatLightLaf());
        FrameController.configurePopupFactory();
        assertThat(PopupFactory.getSharedInstance().getClass().getName())
            .isEqualTo("com.formdev.flatlaf.ui.FlatPopupFactory");

        // Step B: Switch to Metal -> verify basic PopupFactory restored
        UIManager.setLookAndFeel(new MetalLookAndFeel());
        FrameController.configurePopupFactory();
        assertThat(PopupFactory.getSharedInstance().getClass().getName())
            .doesNotContain("FlatPopupFactory");

        // Step C: Switch back to FlatLaf -> verify FlatPopupFactory restored
        UIManager.setLookAndFeel(new FlatLightLaf());
        FrameController.configurePopupFactory();
        assertThat(PopupFactory.getSharedInstance().getClass().getName())
            .isEqualTo("com.formdev.flatlaf.ui.FlatPopupFactory");
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane:test --tests "org.freeplane.features.ui.PopupFactoryConfigurationTest"`
Expected: FAIL, compilation failure or missing symbols in `FrameController`.

- [ ] **Step 3: Modify FrameController to remove unconditional override and add configurePopupFactory()**

Modify `freeplane/src/main/java/org/freeplane/features/ui/FrameController.java`:
1. Add constants to `FrameController`:
```java
	public static final String POPUP_FACTORY_PROPERTY = "freeplane.popup_factory";
	public static final String POPUP_FACTORY_AUTO = "auto";
	public static final String POPUP_FACTORY_FLATLAF = "flatlaf";
	public static final String POPUP_FACTORY_BASIC = "basic";
	public static final String FLAT_POPUP_FACTORY_CLASS = "com.formdev.flatlaf.ui.FlatPopupFactory";
```
2. In `setLookAndFeel(final String lookAndFeel)`:
Remove the unconditional reset:
```java
// REMOVE:
// if(PopupFactory.getSharedInstance().getClass().getName().equals("com.formdev.flatlaf.ui.FlatPopupFactory"))
//     PopupFactory.setSharedInstance(basicPopupFactory);
```
3. Add `configurePopupFactory()` and `ensureFlatPopupFactoryInstalled()` methods:
```java
	public static void configurePopupFactory() {
		try {
			if (Compat.isApplet()) {
				return;
			}
		}
		catch (IllegalStateException ex) {
			// Compat.isApplet() throws IllegalStateException if not set (e.g. in standalone unit tests)
		}

		final ResourceController resourceController = ResourceController.getResourceController();
		final String pref = (resourceController != null)
			? resourceController.getProperty(POPUP_FACTORY_PROPERTY, POPUP_FACTORY_AUTO).trim().toLowerCase(Locale.ROOT)
			: POPUP_FACTORY_AUTO;

		final boolean isFlatLaf = UIManager.getLookAndFeel() instanceof FlatLaf;

		if (POPUP_FACTORY_BASIC.equals(pref)) {
			if (FLAT_POPUP_FACTORY_CLASS.equals(PopupFactory.getSharedInstance().getClass().getName())) {
				PopupFactory.setSharedInstance(basicPopupFactory);
			}
		}
		else if (POPUP_FACTORY_FLATLAF.equals(pref)) {
			if (isFlatLaf) {
				ensureFlatPopupFactoryInstalled();
			}
			else {
				if (FLAT_POPUP_FACTORY_CLASS.equals(PopupFactory.getSharedInstance().getClass().getName())) {
					PopupFactory.setSharedInstance(basicPopupFactory);
				}
			}
		}
		else {
			// "auto" (default)
			if (isFlatLaf) {
				if (Compat.isJetBrainsRuntime()) {
					UIManager.put("Popup.dropShadowPainted", Boolean.FALSE);
				}
				ensureFlatPopupFactoryInstalled();
			}
			else {
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
			}
			catch (Throwable t) {
				LogUtils.warn("Could not instantiate FlatPopupFactory, falling back to basicPopupFactory: " + t.getMessage());
				PopupFactory.setSharedInstance(basicPopupFactory);
			}
		}
	}
```
4. In `fixLookAndFeelUI()`:
Insert `configurePopupFactory();` immediately after `configureFlatLookAndFeel();`:
```java
	private static void fixLookAndFeelUI(){
		OSKeyBindingManager.applyToCurrentLookAndFeel();
		configureFlatLookAndFeel();
		configurePopupFactory();
		UIManager.put("Button.defaultButtonFollowsFocus", Boolean.TRUE);
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane:test --tests "org.freeplane.features.ui.PopupFactoryConfigurationTest"`
Expected: PASS, 5 tests completed, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add freeplane/src/main/java/org/freeplane/features/ui/FrameController.java freeplane/src/test/java/org/freeplane/features/ui/PopupFactoryConfigurationTest.java
git commit -m "Implement FrameController.configurePopupFactory() and integrate into L&F lifecycle"
```

## Task 4: End-to-end integration and verification tests

**Implementer tier:** Frontier

**Files:**

- Modify: `freeplane/src/test/java/org/freeplane/features/ui/PopupFactoryConfigurationTest.java`

**Interfaces:**

- Consumes:
  - `FrameController.configurePopupFactory()`
  - `FrameController.POPUP_FACTORY_PROPERTY`
  - `Compat.isJetBrainsRuntime()`
  - `freeplane/src/viewer/resources/freeplane.properties`
- Produces:
  - Extended test coverage validating:
    - Default property resolution from `freeplane.properties` if `ResourceController` loads defaults.
    - Fallback behavior on invalid/unrecognized preference strings (defaults to auto).
    - Exception safety when `FlatPopupFactory` reflection fails (falls back to `basicPopupFactory`).

- [ ] **Step 1: Add edge-case and error recovery tests to PopupFactoryConfigurationTest**

Add the following tests to `PopupFactoryConfigurationTest.java`:

```java
    @Test
    public void configurePopupFactory_withInvalidPreference_fallsBackToAuto() throws Exception {
        when(resourceControllerMock.getProperty(eq(FrameController.POPUP_FACTORY_PROPERTY), anyString()))
            .thenReturn("invalid_value");
        UIManager.setLookAndFeel(new FlatLightLaf());

        FrameController.configurePopupFactory();

        assertThat(PopupFactory.getSharedInstance().getClass().getName())
            .isEqualTo("com.formdev.flatlaf.ui.FlatPopupFactory");
    }

    @Test
    public void configurePopupFactory_withCaseInsensitiveAndWhitespacePreference_handlesCorrectly() throws Exception {
        when(resourceControllerMock.getProperty(eq(FrameController.POPUP_FACTORY_PROPERTY), anyString()))
            .thenReturn("  BASIC  ");
        UIManager.setLookAndFeel(new FlatLightLaf());

        FrameController.configurePopupFactory();

        assertThat(PopupFactory.getSharedInstance().getClass().getName())
            .doesNotContain("FlatPopupFactory");
    }

    @Test
    public void configurePopupFactory_withNullResourceController_defaultsToAuto() throws Exception {
        mockedResourceControllerStatic.when(ResourceController::getResourceController).thenReturn(null);
        UIManager.setLookAndFeel(new FlatLightLaf());

        FrameController.configurePopupFactory();

        assertThat(PopupFactory.getSharedInstance().getClass().getName())
            .isEqualTo("com.formdev.flatlaf.ui.FlatPopupFactory");
    }
```

- [ ] **Step 2: Run all test suites across the changed areas**

Run:
```bash
JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane:test --tests "org.freeplane.core.util.CompatTest"
JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane:test --tests "org.freeplane.features.ui.PopupFactoryConfigurationTest"
```
Expected: PASS across all tests.

- [ ] **Step 3: Run full module compilation and verify clean build**

Run: `JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu gradle :freeplane:compileJava :freeplane:processResources`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add freeplane/src/test/java/org/freeplane/features/ui/PopupFactoryConfigurationTest.java
git commit -m "Add edge-case and fallback verification tests for PopupFactory configuration"
```
