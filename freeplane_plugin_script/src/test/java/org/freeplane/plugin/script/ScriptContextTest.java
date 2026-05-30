package org.freeplane.plugin.script;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.junit.Test;

public class ScriptContextTest {

    @Test
    public void accessNodeTracksDependenciesWhenEnabled() {
        MapModel map = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel accessingNode = new NodeModel("accessing", map);
        NodeModel accessedNode = new NodeModel("accessed", map);
        ScriptContext scriptContext = new ScriptContext(new NodeScript(accessingNode, "script"));

        scriptContext.accessNode(accessedNode);

        assertThat(toList(FormulaDependencies.getPossibleDependencies(accessedNode))).contains(accessingNode);
    }

    @Test
    public void accessNodeDoesNotTrackDependenciesWhenDisabled() {
        MapModel map = new MapModel((source, targetMap, withChildren) -> null, null, null);
        NodeModel accessingNode = new NodeModel("accessing", map);
        NodeModel accessedNode = new NodeModel("accessed", map);
        ScriptContext scriptContext = new ScriptContext(new NodeScript(accessingNode, "script"))
            .withDependencyTracking(false);

        scriptContext.accessNode(accessedNode);

        assertThat(toList(FormulaDependencies.getPossibleDependencies(accessedNode))).isEmpty();
    }

    private List<NodeModel> toList(Iterable<NodeModel> nodes) {
        List<NodeModel> list = new ArrayList<NodeModel>();
        for (NodeModel node : nodes) {
            list.add(node);
        }
        return list;
    }
}
