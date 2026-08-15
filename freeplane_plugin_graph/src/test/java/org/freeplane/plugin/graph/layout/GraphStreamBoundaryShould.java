package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import org.freeplane.plugin.graph.layout.graphstream.GraphStreamLayoutFactory;
import org.junit.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public class GraphStreamBoundaryShould {
    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("org.freeplane.plugin.graph..");

    @Test
    public void keepGraphStreamDependenciesInsideThePrivateAdapterPackage() {
        noClasses().that().resideOutsideOfPackage("org.freeplane.plugin.graph.layout.graphstream..")
            .should().dependOnClassesThat().resideInAnyPackage("org.graphstream..").check(PRODUCTION_CLASSES);
    }

    @Test
    public void neverDependOnTheGraphStreamLayoutRunner() {
        noClasses().should().dependOnClassesThat()
            .haveFullyQualifiedName("org.graphstream.ui.layout.LayoutRunner").check(PRODUCTION_CLASSES);
    }

    @Test
    public void exposeOnlyGraphStreamFreePublicLayoutSignatures() {
        for (Class<?> type : publicLayoutTypes()) {
            for (Constructor<?> constructor : type.getConstructors()) {
                assertGraphStreamFree(constructor.getParameterTypes(), type.getName() + " constructor parameters");
                assertGraphStreamFree(constructor.getExceptionTypes(), type.getName() + " constructor exceptions");
            }
            for (Method method : type.getMethods()) {
                assertGraphStreamFree(method.getParameterTypes(), type.getName() + "." + method.getName()
                    + " parameters");
                assertGraphStreamFree(method.getExceptionTypes(), type.getName() + "." + method.getName()
                    + " exceptions");
                assertGraphStreamFree(method.getReturnType(), type.getName() + "." + method.getName()
                    + " return type");
            }
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isPublic(field.getModifiers())) {
                    assertGraphStreamFree(field.getType(), type.getName() + "." + field.getName() + " field");
                }
            }
        }
    }

    @Test
    public void createEnginesOnlyThroughThePublicLayoutInterface() throws Exception {
        Method factoryMethod = GraphStreamLayoutFactory.class.getMethod("create", LayoutCalibration.class);

        assertThat(factoryMethod.getReturnType()).isEqualTo(LayoutEngine.class);
        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            assertThat(engine).isInstanceOf(LayoutEngine.class);
            Class<?> implementation = Class.forName("org.freeplane.plugin.graph.layout.graphstream.GraphStreamLayoutEngine");
            assertThat(Modifier.isPublic(implementation.getModifiers())).isFalse();
        }
    }

    @Test
    public void provideOrderedPositiveCalibrationStrengths() {
        LayoutCalibration calibration = LayoutCalibration.spikeDefaults();

        assertThat(calibration.containment()).isPositive();
        assertThat(calibration.hierarchy()).isPositive();
        assertThat(calibration.sameMap()).isPositive();
        assertThat(calibration.containment()).isLessThan(calibration.hierarchy());
        assertThat(calibration.hierarchy()).isLessThan(calibration.sameMap());
    }

    private static List<Class<?>> publicLayoutTypes() {
        return Arrays.<Class<?>>asList(LayoutEngine.class, LayoutCalibration.class, LayoutRequest.class,
            LayoutFrame.class, GraphStreamLayoutFactory.class);
    }

    private static void assertGraphStreamFree(Class<?>[] types, String description) {
        for (Class<?> type : types) {
            assertGraphStreamFree(type, description);
        }
    }

    private static void assertGraphStreamFree(Class<?> type, String description) {
        Class<?> component = type;
        while (component.isArray()) {
            component = component.getComponentType();
        }
        Package packageValue = component.getPackage();
        String packageName = packageValue == null ? "" : packageValue.getName();
        assertThat(packageName).as(description).doesNotStartWith("org.graphstream");
    }
}
