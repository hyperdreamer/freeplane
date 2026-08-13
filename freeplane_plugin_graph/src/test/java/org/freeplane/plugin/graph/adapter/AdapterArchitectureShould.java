package org.freeplane.plugin.graph.adapter;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.freeplane.features.link.NodeLinkModel;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.junit.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

public class AdapterArchitectureShould {
    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("org.freeplane.plugin.graph..");

    @Test
    public void productionCodeDoesNotUseFlatLookupIdentityCreationOrConvenienceTargets() {
        noClasses().should().callMethod(MapModel.class, "getNodeForID", String.class)
            .orShould().callMethod(MapController.class, "getNodeFromID_", String.class)
            .orShould().callMethod(NodeModel.class, "createID")
            .orShould().callMethod(NodeLinkModel.class, "getTarget")
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
}
