package org.freeplane.plugin.graph.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.xml.namespace.QName;

import org.freeplane.plugin.graph.workspace.model.DisplaySettings;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings.CanvasTheme;
import org.junit.Test;

public class GraphWorkspacePresentationShould {
    private static final MapReferenceId FIRST_ID =
        MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId SECOND_ID =
        MapReferenceId.of("00000000-0000-0000-0000-000000000002");

    @Test
    public void defensivelyCopiesOrderedMapColorsAndPreservesEveryDisplaySetting() {
        UnknownXml unknown = UnknownXml.attribute(UnknownXml.Owner.RECORD,
            new QName("urn:test", "display"), "preserved");
        DisplaySettings settings = DisplaySettings.of(false, CanvasTheme.DARK, false, false,
            Collections.singletonList(unknown));
        List<GraphWorkspacePresentation.MapColor> input = new ArrayList<GraphWorkspacePresentation.MapColor>(
            Arrays.asList(color(SECOND_ID, "#E15759"), color(FIRST_ID, "#4E79A7")));

        GraphWorkspacePresentation presentation = GraphWorkspacePresentation.of(settings, input);
        input.clear();

        assertThat(presentation.displaySettings().showArrowheads()).isFalse();
        assertThat(presentation.displaySettings().canvasTheme()).isEqualTo(CanvasTheme.DARK);
        assertThat(presentation.displaySettings().rememberViewport()).isFalse();
        assertThat(presentation.displaySettings().dimUnrelatedNodes()).isFalse();
        assertThat(presentation.displaySettings().unknownXml()).containsExactly(unknown);
        assertThat(presentation.mapColors()).hasSize(2);
        assertThat(presentation.mapColors().get(0).mapReferenceId()).isEqualTo(SECOND_ID);
        assertThat(presentation.mapColors().get(0).color()).isEqualTo("#E15759");
        assertThat(presentation.mapColors().get(1).mapReferenceId()).isEqualTo(FIRST_ID);
        assertThatThrownBy(() -> presentation.mapColors().clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void retainsDeterministicInputOrderingAcrossEquivalentPresentations() {
        DisplaySettings settings = DisplaySettings.defaults();
        List<GraphWorkspacePresentation.MapColor> colors = Arrays.asList(
            color(FIRST_ID, "#4E79A7"), color(SECOND_ID, "#E15759"));

        GraphWorkspacePresentation first = GraphWorkspacePresentation.of(settings, colors);
        GraphWorkspacePresentation second = GraphWorkspacePresentation.of(settings,
            new ArrayList<GraphWorkspacePresentation.MapColor>(colors));

        assertThat(first.mapColors()).containsExactlyElementsOf(second.mapColors());
        assertThat(first.mapColors().get(0).mapReferenceId()).isEqualTo(FIRST_ID);
        assertThat(first.mapColors().get(1).mapReferenceId()).isEqualTo(SECOND_ID);
    }

    @Test
    public void rejectsNullDuplicateAndInvalidMapColorEntries() {
        DisplaySettings settings = DisplaySettings.defaults();

        assertThatThrownBy(() -> GraphWorkspacePresentation.of(settings, null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> GraphWorkspacePresentation.of(settings,
            Arrays.asList(color(FIRST_ID, "#4E79A7"), color(FIRST_ID, "#E15759"))))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GraphWorkspacePresentation.MapColor.of(null, "#4E79A7"))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> GraphWorkspacePresentation.MapColor.of(FIRST_ID, null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> GraphWorkspacePresentation.MapColor.of(FIRST_ID, "#000000"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static GraphWorkspacePresentation.MapColor color(final MapReferenceId id, final String value) {
        return GraphWorkspacePresentation.MapColor.of(id, value);
    }
}
