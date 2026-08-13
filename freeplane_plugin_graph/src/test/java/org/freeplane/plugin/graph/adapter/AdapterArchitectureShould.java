package org.freeplane.plugin.graph.adapter;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.conditions.ArchConditions.callMethodWhere;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.freeplane.features.link.NodeLinkModel;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.junit.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;

public class AdapterArchitectureShould {
    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("org.freeplane.plugin.graph..");

    @Test
    public void productionCodeDoesNotUseFlatLookupIdentityCreationOrConvenienceTargets() {
        noClasses().should(forbiddenCall("getNodeForID", MapModel.class))
            .orShould(forbiddenCall("getNodeFromID_", MapController.class))
            .orShould(forbiddenCall("createID", NodeModel.class))
            .orShould(forbiddenCall("getTarget", NodeLinkModel.class))
            .check(PRODUCTION_CLASSES);
    }

    @Test
    public void pureGraphPackagesDoNotDependOnMutableFreeplaneTypes() {
        noClasses().that().resideInAPackage("org.freeplane.plugin.graph.projection..")
            .or().resideInAPackage("org.freeplane.plugin.graph.geometry..")
            .or().resideInAPackage("org.freeplane.plugin.graph.layout..")
            .or().resideInAPackage("org.freeplane.plugin.graph.canvas..")
            .should().dependOnClassesThat().resideInAnyPackage("org.freeplane.features..", "org.freeplane.view..")
            .check(PRODUCTION_CLASSES);
    }

    /**
     * Matches a forbidden call by method name and by owner assignability, because javac records the
     * receiver's static type as the bytecode call owner. Matching only the declaring type would let a
     * subclass-typed receiver, such as {@code MMapModel} or {@code ConnectorModel}, evade the rule.
     */
    private static ArchCondition<JavaClass> forbiddenCall(final String methodName, final Class<?> declaringType) {
        final DescribedPredicate<JavaAccess<?>> forbidden =
            target(name(methodName)).and(target(owner(assignableTo(declaringType))));
        return callMethodWhere(forbidden.as(
            "call method " + declaringType.getSimpleName() + "." + methodName + "() on any assignable owner"));
    }
}
