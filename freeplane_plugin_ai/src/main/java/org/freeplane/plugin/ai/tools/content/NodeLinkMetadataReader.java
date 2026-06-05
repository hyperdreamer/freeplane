package org.freeplane.plugin.ai.tools.content;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.freeplane.features.link.ConnectorModel;
import org.freeplane.features.link.MapLinks;
import org.freeplane.features.link.NodeLinkModel;
import org.freeplane.features.link.NodeLinks;
import org.freeplane.features.map.Clones;
import org.freeplane.features.map.NodeModel;

public class NodeLinkMetadataReader {
    private NodeLinkMetadataReader() {
    }

    public static String readHyperlink(NodeModel nodeModel) {
        if (nodeModel == null) {
            return null;
        }
        return NodeLinks.getLinkAsString(nodeModel);
    }

    public static List<ConnectorItem> readOutgoingConnectors(NodeModel nodeModel) {
        if (nodeModel == null) {
            return null;
        }
        Collection<NodeLinkModel> links = NodeLinks.getLinks(nodeModel);
        return toConnectorItems(links);
    }

    public static List<ConnectorItem> readIncomingConnectors(NodeModel nodeModel) {
        if (nodeModel == null || !nodeModel.hasID()) {
            return null;
        }
        MapLinks mapLinks = MapLinks.getLinks(nodeModel.getMap());
        if (mapLinks == null) {
            return null;
        }
        Collection<NodeLinkModel> links = mapLinks.get(nodeModel.getID());
        return toConnectorItems(links);
    }

    public static CloneMetadata readCloneMetadata(NodeModel nodeModel) {
        if (nodeModel == null) {
            return null;
        }
        List<String> cloneIdentifiers = readCloneIdentifiers(nodeModel);
        return new CloneMetadata(cloneIdentifiers, nodeModel.isCloneTreeRoot(), nodeModel.isCloneTreeNode());
    }

    private static List<String> readCloneIdentifiers(NodeModel nodeModel) {
        Clones clones = nodeModel.allClones();
        if (clones == null) {
            return null;
        }
        Collection<NodeModel> cloneNodes = clones.toCollection();
        if (cloneNodes == null || cloneNodes.isEmpty()) {
            return new ArrayList<>();
        }
        String currentId = nodeModel.createID();
        List<String> identifiers = new ArrayList<>();
        for (NodeModel clone : cloneNodes) {
            if (clone == null) {
                continue;
            }
            String cloneId = clone.createID();
            if (cloneId == null || cloneId.equals(currentId)) {
                continue;
            }
            identifiers.add(cloneId);
        }
        return identifiers;
    }

    private static List<ConnectorItem> toConnectorItems(Collection<NodeLinkModel> links) {
        if (links == null || links.isEmpty()) {
            return null;
        }
        List<ConnectorItem> results = new ArrayList<>();
        for (NodeLinkModel link : links) {
            if (!(link instanceof ConnectorModel)) {
                continue;
            }
            ConnectorItem item = ConnectorItem.fromConnector((ConnectorModel) link);
            if (item != null) {
                results.add(item);
            }
        }
        if (results.isEmpty()) {
            return null;
        }
        return results;
    }
}
