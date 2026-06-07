package org.freeplane.plugin.ai.tools.content;

import org.freeplane.features.map.FirstGroupNodeFlag;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.SummaryNodeFlag;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NodeContentItemReaderTest {
    @Test
    public void readNodeContentItem_addsSummaryQualifier() {
        NodeModel nodeModel = mock(NodeModel.class);
        NodeContentReader nodeContentReader = mock(NodeContentReader.class);
        when(nodeModel.createID()).thenReturn("ID_1");
        when(nodeContentReader.readNodeContent(nodeModel, NodeContentPreset.BRIEF))
            .thenReturn(new NodeContentResponse("Brief", null, null, null, null, null, null, null));
        when(nodeModel.containsExtension(SummaryNodeFlag.class)).thenReturn(true);
        when(nodeModel.containsExtension(FirstGroupNodeFlag.class)).thenReturn(false);
        NodeContentItemReader uut = new NodeContentItemReader(nodeContentReader);

        NodeContentItem item = uut.readNodeContentItem(nodeModel, NodeContentPreset.BRIEF);

        assertThat(item.getQualifiers()).containsExactly("summary_node");
    }

    @Test
    public void readNodeContentItem_addsFirstGroupQualifier() {
        NodeModel nodeModel = mock(NodeModel.class);
        NodeContentReader nodeContentReader = mock(NodeContentReader.class);
        when(nodeModel.createID()).thenReturn("ID_2");
        when(nodeContentReader.readNodeContent(nodeModel, NodeContentPreset.BRIEF))
            .thenReturn(new NodeContentResponse("Brief", null, null, null, null, null, null, null));
        when(nodeModel.containsExtension(SummaryNodeFlag.class)).thenReturn(false);
        when(nodeModel.containsExtension(FirstGroupNodeFlag.class)).thenReturn(true);
        NodeContentItemReader uut = new NodeContentItemReader(nodeContentReader);

        NodeContentItem item = uut.readNodeContentItem(nodeModel, NodeContentPreset.BRIEF);

        assertThat(item.getQualifiers()).containsExactly("first_group_node");
    }

    @Test
    public void readNodeContentItem_returnsEmptyQualifiersWhenNoneApply() {
        NodeModel nodeModel = mock(NodeModel.class);
        NodeContentReader nodeContentReader = mock(NodeContentReader.class);
        when(nodeModel.createID()).thenReturn("ID_3");
        when(nodeContentReader.readNodeContent(nodeModel, NodeContentPreset.BRIEF))
            .thenReturn(new NodeContentResponse("Brief", null, null, null, null, null, null, null));
        when(nodeModel.containsExtension(SummaryNodeFlag.class)).thenReturn(false);
        when(nodeModel.containsExtension(FirstGroupNodeFlag.class)).thenReturn(false);
        NodeContentItemReader uut = new NodeContentItemReader(nodeContentReader);

        NodeContentItem item = uut.readNodeContentItem(nodeModel, NodeContentPreset.BRIEF);

        assertThat(item.getQualifiers()).isEmpty();
    }

    @Test
    public void readNodeContentItem_omitsNodeIdentifierWhenDisabled() {
        NodeModel nodeModel = mock(NodeModel.class);
        NodeContentReader nodeContentReader = mock(NodeContentReader.class);
        when(nodeContentReader.readNodeContent(nodeModel, NodeContentPreset.BRIEF))
            .thenReturn(new NodeContentResponse("Brief", null, null, null, null, null, null, null));
        NodeContentItemReader uut = new NodeContentItemReader(nodeContentReader);

        NodeContentItem item = uut.readNodeContentItem(nodeModel, NodeContentPreset.BRIEF, false);

        assertThat(item.getNodeIdentifier()).isNull();
        verify(nodeModel, never()).createID();
    }
}
