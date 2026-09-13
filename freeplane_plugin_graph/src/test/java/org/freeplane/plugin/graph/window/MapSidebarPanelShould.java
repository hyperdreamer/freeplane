package org.freeplane.plugin.graph.window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.graph.control.GraphWorkspaceHandle;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

public class MapSidebarPanelShould {
    private MockedStatic<TextUtils> textUtils;
    private MockedStatic<ResourceController> resourceController;

    @Before
    public void setUp() {
        resourceController = org.mockito.Mockito.mockStatic(ResourceController.class);
        resourceController.when(ResourceController::getResourceController)
            .thenReturn(mock(ResourceController.class));
        textUtils = org.mockito.Mockito.mockStatic(TextUtils.class);
        textUtils.when(() -> TextUtils.getText(any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getText(any(String.class), any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getRawText(any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getRawText(any(String.class), any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.format(any(String.class), any(Object[].class)))
            .thenAnswer(invocation -> {
                Object[] arguments = invocation.getArguments();
                Object[] formatArguments = arguments.length == 2 && arguments[1] instanceof Object[]
                    ? (Object[]) arguments[1] : Arrays.copyOfRange(arguments, 1, arguments.length);
                return arguments[0] + Arrays.toString(formatArguments);
            });
    }

    @After
    public void tearDown() {
        textUtils.close();
        resourceController.close();
    }

    private static MapListPanel mapList() {
        GraphWorkspaceHandle handle = mock(GraphWorkspaceHandle.class);
        return new MapListPanel(handle, () -> Paths.get("/tmp/map.mm"), (parent, name) -> true);
    }

    private static MapSidebarPanel panel() {
        return new MapSidebarPanel(mapList(), collapsed -> { });
    }

    @Test
    public void showsOnlyOneOfRailAndMapList() {
        MapSidebarPanel panel = panel();
        assertThat(panel.mapList().isVisible()).isTrue();
        assertThat(panel.rail().isVisible()).isFalse();

        panel.setCollapsed(true);
        assertThat(panel.rail().isVisible()).isTrue();
        assertThat(panel.mapList().isVisible()).isFalse();

        panel.setCollapsed(false);
        assertThat(panel.mapList().isVisible()).isTrue();
        assertThat(panel.rail().isVisible()).isFalse();
    }

    @Test
    public void flipsThePreferredAndMinimumWidthsWithTheCollapsedFlag() {
        MapSidebarPanel panel = panel();
        assertThat(panel.getPreferredSize().width).isEqualTo(264);
        assertThat(panel.getMinimumSize().width).isEqualTo(180);

        panel.setCollapsed(true);
        assertThat(panel.getPreferredSize().width).isEqualTo(26);
        assertThat(panel.getMinimumSize().width).isEqualTo(26);
    }

    @Test
    public void notifiesTheCollapseListenerOncePerRealChange() {
        List<Boolean> changes = new ArrayList<Boolean>();
        MapSidebarPanel panel = new MapSidebarPanel(mapList(), changes::add);

        assertThat(changes).isEmpty();
        panel.setCollapsed(true);
        assertThat(changes).containsExactly(Boolean.TRUE);
        panel.setCollapsed(true);
        assertThat(changes).containsExactly(Boolean.TRUE);
        panel.setCollapsed(false);
        assertThat(changes).containsExactly(Boolean.TRUE, Boolean.FALSE);
    }

    @Test
    public void updatesTheRailBadgeFromTheActiveMapCount() {
        MapSidebarPanel panel = panel();

        panel.setActiveMapCount(3);
        assertThat(panel.rail().countBadge().getText()).isEqualTo("3");
        assertThat(panel.rail().countBadge().getToolTipText())
            .isEqualTo("graph_workspace.map_list.rail_count[3]");

        panel.setActiveMapCount(0);
        assertThat(panel.rail().countBadge().getText()).isEmpty();
        assertThat(panel.rail().countBadge().getToolTipText())
            .isEqualTo("graph_workspace.map_list.rail_count[0]");

        panel.setActiveMapCount(0);
        assertThat(panel.rail().countBadge().getText()).isEmpty();
    }

    @Test
    public void forwardsReadOnlyToTheMapListWithoutChangingVisibility() {
        MapSidebarPanel panel = panel();

        panel.setReadOnly(true);
        assertThat(panel.mapList().isReadOnly()).isTrue();
        assertThat(panel.mapList().isVisible()).isTrue();
        assertThat(panel.rail().isVisible()).isFalse();

        panel.setReadOnly(false);
        assertThat(panel.mapList().isReadOnly()).isFalse();
    }
}
