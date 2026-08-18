package org.freeplane.plugin.graph.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

import javax.swing.event.ChangeListener;

import org.freeplane.core.undo.IActor;
import org.freeplane.core.undo.IUndoHandler;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.link.ConnectorArrows;
import org.freeplane.features.link.ConnectorModel;
import org.freeplane.features.link.NodeLinkModel;
import org.freeplane.features.link.NodeLinks;
import org.freeplane.features.link.mindmapmode.MLinkController;
import org.freeplane.features.map.INodeDuplicator;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.plugin.graph.adapter.EdtExecutor;
import org.freeplane.plugin.graph.adapter.MapLease;
import org.freeplane.plugin.graph.adapter.MapOperationalState;
import org.freeplane.plugin.graph.projection.ContributorKey;
import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.GraphCommandResult;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;
import org.freeplane.plugin.graph.workspace.model.WorkspaceDocument;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

public class FreeplaneMapCommandExecutorShould {
    private MockedStatic<ResourceController> resourceControllers;
    private MockedStatic<TextUtils> textUtils;

    @Before
    public void setUp() {
        resourceControllers = org.mockito.Mockito.mockStatic(ResourceController.class);
        resourceControllers.when(ResourceController::getResourceController).thenReturn(mock(ResourceController.class));
        textUtils = org.mockito.Mockito.mockStatic(TextUtils.class);
        textUtils.when(() -> TextUtils.getText(any(String.class))).thenAnswer(invocation -> invocation.getArgument(0));
        textUtils.when(() -> TextUtils.getText(any(String.class), any(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @After
    public void tearDown() {
        textUtils.close();
        resourceControllers.close();
    }

    @Test
    public void materializesOneMissingEditorViewThenReusesItForRepeatedEdits() {
        // Catches a stale view tracker that either duplicates an open view or trusts a closed one.
        Fixture fixture = new Fixture(true);
        MapNodes nodes = fixture.addMap("source map");

        assertApplied(fixture.executor.createConnector(nodes.sourceKey, nodes.targetKey, RelationshipDirection.FORWARD));
        assertApplied(fixture.executor.createConnector(nodes.sourceKey, nodes.targetKey, RelationshipDirection.FORWARD));
        assertThat(fixture.viewCreations).isEqualTo(1);

        fixture.openViews.remove(nodes.map);
        assertApplied(fixture.executor.createConnector(nodes.sourceKey, nodes.targetKey, RelationshipDirection.FORWARD));
        assertThat(fixture.viewCreations).isEqualTo(2);

        MapNodes replacement = fixture.replaceMapModel(nodes);
        assertApplied(fixture.executor.createConnector(replacement.sourceKey, replacement.targetKey,
            RelationshipDirection.FORWARD));
        assertThat(fixture.viewCreations).isEqualTo(3);
    }

    @Test
    public void materializesEachOfThreeDifferentMapsAtMostOnceWhileTheirViewsRemainOpen() {
        // Catches materialization keyed by an operation rather than by the live editor view state.
        Fixture fixture = new Fixture(true);
        List<MapNodes> maps = Arrays.asList(fixture.addMap("one"), fixture.addMap("two"), fixture.addMap("three"));

        for (MapNodes nodes : maps) {
            assertApplied(fixture.executor.createConnector(nodes.sourceKey, nodes.targetKey, RelationshipDirection.FORWARD));
            assertApplied(fixture.executor.createConnector(nodes.sourceKey, nodes.targetKey, RelationshipDirection.FORWARD));
        }

        assertThat(fixture.viewCreations).isEqualTo(3);
        assertThat(fixture.openViews).containsExactlyInAnyOrder(maps.get(0).map, maps.get(1).map, maps.get(2).map);
    }

    @Test
    public void createsOneUndoTransactionWithTheRequestedDirectionAndOnlyMarksTheSourceMapDirty() {
        // Catches a connector mutation that uses the node overload, wrong arrow mapping, or wrong dirty-map result.
        assertCreateDirection(RelationshipDirection.UNDIRECTED, ConnectorArrows.NONE);
        assertCreateDirection(RelationshipDirection.FORWARD, ConnectorArrows.FORWARD);
        assertCreateDirection(RelationshipDirection.BIDIRECTIONAL, ConnectorArrows.BOTH);
    }

    @Test
    public void rejectsReadOnlyMapsBeforeStartingANativeConnectorTransaction() {
        // Catches editability checks that occur after native connector or undo mutation.
        Fixture fixture = new Fixture(true);
        MapNodes nodes = fixture.addMap("read only");
        nodes.map.setReadOnly(true);

        GraphCommandResult result = fixture.executor.createConnector(nodes.sourceKey, nodes.targetKey,
            RelationshipDirection.FORWARD);

        assertRejected(result, "graph_workspace.source_map.read_only");
        assertThat(connectors(nodes.source)).isEmpty();
        assertThat(fixture.nativeController.idAddCalls).isZero();
        assertThat(nodes.undo.startCalls).isZero();
        assertThat(fixture.viewCreations).isZero();
    }

    @Test
    public void rejectsMapEditsWhenMaterializationStillDoesNotProvideUndoSupport() {
        // Catches removal of the post-materialization undo guard before native mutation can begin.
        Fixture fixture = new Fixture(false);
        MapNodes nodes = fixture.addMap("no undo");

        GraphCommandResult result = fixture.executor.createConnector(nodes.sourceKey, nodes.targetKey,
            RelationshipDirection.FORWARD);

        assertThat(connectors(nodes.source)).isEmpty();
        assertThat(fixture.nativeController.idAddCalls).isZero();
        assertThat(nodes.undo.startCalls).isZero();
        assertRejected(result, "graph_workspace.source_map.undo_unavailable");
    }

    @Test
    public void rejectsAnIdlessTargetWithoutCreatingAnIdOrConnectorThenSucceedsAfterNormalIdAssignment() {
        // Catches connector creation that silently assigns a persistent ID to an idless target.
        Fixture fixture = new Fixture(true);
        MapNodes nodes = fixture.addMap("idless target");
        nodes.target.setID(null);
        nodes.targetKey = SourceNodeKey.transientPath(nodes.mapId, Collections.singletonList(Integer.valueOf(1)));
        fixture.resolvedNodes.put(nodes.targetKey, nodes.target);
        int registryCallsBeforeRequest = nodes.map.registryCalls();

        GraphCommandResult rejected = fixture.executor.createConnector(nodes.sourceKey, nodes.targetKey,
            RelationshipDirection.FORWARD);

        assertRejected(rejected, "graph_workspace.connector.target_requires_saved_id");
        assertThat(nodes.target.getID()).isNull();
        assertThat(nodes.map.registryCalls()).isEqualTo(registryCallsBeforeRequest);
        assertThat(connectors(nodes.source)).isEmpty();
        assertThat(fixture.nativeController.idAddCalls).isZero();

        nodes.target.setID("ID_TARGET_NOW_SAVED");
        GraphCommandResult applied = fixture.executor.createConnector(nodes.sourceKey, nodes.targetKey,
            RelationshipDirection.FORWARD);

        assertApplied(applied);
        assertThat(fixture.nativeController.lastTargetId).isEqualTo("ID_TARGET_NOW_SAVED");
        assertThat(connectors(nodes.source)).extracting(ConnectorModel::getTargetID)
            .containsExactly("ID_TARGET_NOW_SAVED");
    }

    @Test
    public void deletesASnapshotCompatibleRawConnectorThroughATreeCloneWithoutAssigningCloneTargetIds() {
        // Catches clone-expanded deletion enumeration that manufactures a clone target ID before transaction preflight.
        Fixture fixture = new Fixture(true);
        MapNodes nodes = fixture.addMap("tree clone");
        NodeModel root = nodes.source.getParentNode();
        NodeModel originalBranch = node(nodes.map, "original branch", "ID_CLONE_BRANCH");
        NodeModel originalSource = node(nodes.map, "original source", "ID_CLONE_SOURCE");
        NodeModel originalTarget = node(nodes.map, "original target", "ID_CLONE_TARGET");
        originalBranch.insert(originalSource);
        originalBranch.insert(originalTarget);
        root.insert(originalBranch);
        NodeLinks.createLinkExtension(originalSource)
            .addArrowlink(connector(originalSource, "ID_CLONE_TARGET", ConnectorArrows.BOTH, "source", "middle", "target"));
        NodeModel cloneBranch = originalBranch.cloneTree();
        root.insert(cloneBranch);
        NodeModel cloneSource = cloneBranch.getChildren().get(0);
        NodeModel cloneTarget = cloneBranch.getChildren().get(1);
        SourceNodeKey cloneSourceKey = SourceNodeKey.transientPath(nodes.mapId, Arrays.asList(
            Integer.valueOf(root.getIndex(cloneBranch)), Integer.valueOf(cloneBranch.getIndex(cloneSource))));
        fixture.resolvedNodes.put(cloneSourceKey, cloneSource);
        ConnectorDescriptor expected = descriptor(cloneSourceKey, nodes.mapId, "ID_CLONE_TARGET", true, true,
            "source", "middle", "target");
        int registryCallsBeforeDelete = nodes.map.registryCalls();

        assertThat(cloneTarget.getID()).isNull();
        GraphCommandResult result = fixture.executor.deleteConnector(
            ContributorKey.nativeConnector(nodes.mapId, cloneSourceKey, 0), expected);

        assertApplied(result);
        assertThat(connectors(originalSource)).isEmpty();
        assertThat(cloneTarget.getID()).isNull();
        assertThat(nodes.map.registryCalls()).isEqualTo(registryCallsBeforeDelete);
    }

    @Test
    public void rollsBackCreateMutationAndPreservesThePriorUndoTarget() {
        // Catches a native create failure that leaves its real connector mutation outside the undo rollback.
        Fixture fixture = new Fixture(true);
        MapNodes prior = fixture.addMap("prior create undo target");
        MapNodes failing = fixture.addMap("failed create");
        assertApplied(fixture.executor.createConnector(prior.sourceKey, prior.targetKey, RelationshipDirection.FORWARD));
        List<String> connectorsBeforeFailure = connectorFingerprint(failing.source);
        fixture.nativeController.throwAfterCreateMutation = true;

        GraphCommandResult result = fixture.executor.createConnector(failing.sourceKey, failing.targetKey,
            RelationshipDirection.FORWARD);

        assertRejected(result, "graph_workspace.source_map.unavailable");
        assertThat(connectorFingerprint(failing.source)).isEqualTo(connectorsBeforeFailure);
        assertThat(failing.undo.startCalls).isEqualTo(1);
        assertThat(failing.undo.commitCalls).isZero();
        assertThat(failing.undo.rollbackCalls).isEqualTo(1);
        assertThat(fixture.executor.currentUndoTarget())
            .contains(new MapUndoTarget(prior.mapId, "prior create undo target", false));
    }

    @Test
    public void rollsBackDeleteMutationAndPreservesThePriorUndoTarget() {
        // Catches a native delete failure that leaves its real connector removal outside the undo rollback.
        Fixture fixture = new Fixture(true);
        MapNodes prior = fixture.addMap("prior delete undo target");
        MapNodes failing = fixture.addMap("failed delete");
        assertApplied(fixture.executor.createConnector(prior.sourceKey, prior.targetKey, RelationshipDirection.FORWARD));
        NodeLinks.createLinkExtension(failing.source)
            .addArrowlink(connector(failing.source, "ID_TARGET", ConnectorArrows.FORWARD, "", "", ""));
        ConnectorDescriptor expected = descriptor(failing.sourceKey, failing.mapId, "ID_TARGET", false, true, "", "", "");
        List<String> connectorsBeforeFailure = connectorFingerprint(failing.source);
        fixture.nativeController.throwAfterDeleteMutation = true;

        GraphCommandResult result = fixture.executor.deleteConnector(
            ContributorKey.nativeConnector(failing.mapId, failing.sourceKey, 0), expected);

        assertRejected(result, "graph_workspace.connector.changed");
        assertThat(connectorFingerprint(failing.source)).isEqualTo(connectorsBeforeFailure);
        assertThat(failing.undo.startCalls).isEqualTo(1);
        assertThat(failing.undo.commitCalls).isZero();
        assertThat(failing.undo.rollbackCalls).isEqualTo(1);
        assertThat(fixture.executor.currentUndoTarget())
            .contains(new MapUndoTarget(prior.mapId, "prior delete undo target", false));
    }

    @Test
    public void deletesOnlyTheCurrentOccurrenceWhoseFullDescriptorStillMatches() {
        // Catches deletion that removes the first connector or a non-connector link instead of the requested occurrence.
        Fixture fixture = new Fixture(true);
        MapNodes nodes = fixture.addMap("delete exact");
        NodeLinks links = NodeLinks.createLinkExtension(nodes.source);
        links.setLocalHyperlink(nodes.source, "ID_TARGET");
        ConnectorModel first = connector(nodes.source, "ID_TARGET", ConnectorArrows.NONE, "first", "", "");
        ConnectorModel second = connector(nodes.source, "ID_TARGET", ConnectorArrows.BOTH, "source\r\nlabel",
            "middle\nlabel", "target");
        links.addArrowlink(first);
        links.addArrowlink(second);
        ConnectorDescriptor expected = descriptor(nodes.sourceKey, nodes.mapId, "ID_TARGET", true, true,
            "source label", "middle label", "target");
        ContributorKey key = ContributorKey.nativeConnector(nodes.mapId, nodes.sourceKey, 1);

        GraphCommandResult result = fixture.executor.deleteConnector(key, expected);

        assertApplied(result);
        assertThat(connectors(nodes.source)).hasSize(1);
        assertThat(connectors(nodes.source).get(0).getSourceLabel()).contains("first");
        assertThat(NodeLinks.getLinkExtension(nodes.source).getHyperLink(nodes.source)).isNotNull();
        assertThat(nodes.undo.startCalls).isEqualTo(1);
        assertThat(nodes.undo.commitCalls).isEqualTo(1);
        assertThat(nodes.undo.rollbackCalls).isZero();
    }

    @Test
    public void batchesTwoNativeDeletionsOnOneSourceMapIntoOneOpenUndoTransaction() {
        Fixture fixture = new Fixture(true);
        MapNodes nodes = fixture.addMap(MapReferenceId.of("00000000-0000-0000-0000-000000000501"), "same map");
        NodeLinks links = NodeLinks.createLinkExtension(nodes.source);
        ConnectorModel first = connector(nodes.source, "ID_TARGET", ConnectorArrows.FORWARD, "first", "", "");
        ConnectorModel second = connector(nodes.source, "ID_TARGET_REPLACED", ConnectorArrows.BOTH, "second", "", "");
        links.addArrowlink(first);
        links.addArrowlink(second);
        ContributorDeletionPlan plan = plan(
            edit(nodes.mapId, nodes.sourceKey, 0, descriptor(nodes.sourceKey, nodes.mapId, "ID_TARGET", false,
                true, "first", "", "")),
            edit(nodes.mapId, nodes.sourceKey, 1,
                descriptor(nodes.sourceKey, nodes.mapId, "ID_TARGET_REPLACED", true, true, "second", "", "")));

        FreeplaneMapCommandExecutor.ContributorDeletionTransaction transaction =
            fixture.edt.call(new Callable<FreeplaneMapCommandExecutor.ContributorDeletionTransaction>() {
                @Override
                public FreeplaneMapCommandExecutor.ContributorDeletionTransaction call() {
                    return fixture.executor.beginContributorDeletion(plan);
                }
            });

        assertApplied(transaction.outcome());
        assertThat(nodes.undo.startCalls).isEqualTo(1);
        assertThat(nodes.undo.commitCalls).isZero();
        assertThat(fixture.nativeController.removeCalls).isEqualTo(2);
        fixture.edt.call(new Callable<Void>() {
            @Override
            public Void call() {
                transaction.commit();
                return null;
            }
        });
        assertThat(nodes.undo.commitCalls).isEqualTo(1);
        assertThat(nodes.undo.rollbackCalls).isZero();
        assertThat(connectors(nodes.source)).isEmpty();
        assertThat(fixture.executor.currentUndoTarget())
            .contains(new MapUndoTarget(nodes.mapId, "same map", false));
    }

    @Test
    public void startsOneTransactionPerTouchedMapAndUsesTheDeterministicLastMapAsUndoOwner() {
        Fixture fixture = new Fixture(true);
        MapNodes first = fixture.addMap(MapReferenceId.of("00000000-0000-0000-0000-000000000511"), "first map");
        MapNodes second = fixture.addMap(MapReferenceId.of("00000000-0000-0000-0000-000000000512"), "second map");
        NodeLinks.createLinkExtension(first.source).addArrowlink(
            connector(first.source, "ID_TARGET", ConnectorArrows.FORWARD, "", "", ""));
        NodeLinks.createLinkExtension(second.source).addArrowlink(
            connector(second.source, "ID_TARGET", ConnectorArrows.FORWARD, "", "", ""));
        ContributorDeletionPlan plan = plan(
            edit(second.mapId, second.sourceKey, 0,
                descriptor(second.sourceKey, second.mapId, "ID_TARGET", false, true, "", "", "")),
            edit(first.mapId, first.sourceKey, 0,
                descriptor(first.sourceKey, first.mapId, "ID_TARGET", false, true, "", "", "")));

        FreeplaneMapCommandExecutor.ContributorDeletionTransaction transaction =
            fixture.edt.call(new Callable<FreeplaneMapCommandExecutor.ContributorDeletionTransaction>() {
                @Override
                public FreeplaneMapCommandExecutor.ContributorDeletionTransaction call() {
                    return fixture.executor.beginContributorDeletion(plan);
                }
            });
        fixture.edt.call(new Callable<Void>() {
            @Override
            public Void call() {
                transaction.commit();
                return null;
            }
        });

        assertApplied(transaction.outcome());
        assertThat(first.undo.startCalls).isEqualTo(1);
        assertThat(second.undo.startCalls).isEqualTo(1);
        assertThat(first.undo.commitCalls).isEqualTo(1);
        assertThat(second.undo.commitCalls).isEqualTo(1);
        assertThat(transaction.dirtySourceMaps()).containsExactly(first.mapId, second.mapId);
        assertThat(fixture.executor.currentUndoTarget())
            .contains(new MapUndoTarget(second.mapId, "second map", false));
    }

    @Test
    public void resolvesEveryConnectorBeforeStartingAnyMapTransaction() {
        Fixture fixture = new Fixture(true);
        MapNodes first = fixture.addMap(MapReferenceId.of("00000000-0000-0000-0000-000000000521"), "valid map");
        MapNodes second = fixture.addMap(MapReferenceId.of("00000000-0000-0000-0000-000000000522"), "changed map");
        NodeLinks.createLinkExtension(first.source).addArrowlink(
            connector(first.source, "ID_TARGET", ConnectorArrows.FORWARD, "", "", ""));
        NodeLinks.createLinkExtension(second.source).addArrowlink(
            connector(second.source, "ID_TARGET", ConnectorArrows.FORWARD, "changed", "", ""));
        ContributorDeletionPlan plan = plan(
            edit(first.mapId, first.sourceKey, 0,
                descriptor(first.sourceKey, first.mapId, "ID_TARGET", false, true, "", "", "")),
            edit(second.mapId, second.sourceKey, 0,
                descriptor(second.sourceKey, second.mapId, "ID_TARGET", false, true, "", "", "")));

        FreeplaneMapCommandExecutor.ContributorDeletionTransaction transaction =
            fixture.edt.call(new Callable<FreeplaneMapCommandExecutor.ContributorDeletionTransaction>() {
                @Override
                public FreeplaneMapCommandExecutor.ContributorDeletionTransaction call() {
                    return fixture.executor.beginContributorDeletion(plan);
                }
            });

        assertRejected(transaction.outcome(), "graph_workspace.connector.changed");
        assertThat(first.undo.startCalls).isZero();
        assertThat(second.undo.startCalls).isZero();
        assertThat(fixture.nativeController.removeCalls).isZero();
        assertThat(fixture.viewCreations).isZero();
        assertThat(connectors(first.source)).hasSize(1);
        assertThat(connectors(second.source)).hasSize(1);
    }

    @Test
    public void rollsBackAllStartedMapTransactionsInReverseOrderAfterALaterNativeFailure() {
        Fixture fixture = new Fixture(true);
        MapNodes first = fixture.addMap(MapReferenceId.of("00000000-0000-0000-0000-000000000531"), "first map");
        MapNodes second = fixture.addMap(MapReferenceId.of("00000000-0000-0000-0000-000000000532"), "second map");
        NodeLinks.createLinkExtension(first.source).addArrowlink(
            connector(first.source, "ID_TARGET", ConnectorArrows.FORWARD, "", "", ""));
        NodeLinks.createLinkExtension(second.source).addArrowlink(
            connector(second.source, "ID_TARGET", ConnectorArrows.FORWARD, "", "", ""));
        fixture.nativeController.throwOnDeleteCall = 2;
        ContributorDeletionPlan plan = plan(
            edit(first.mapId, first.sourceKey, 0,
                descriptor(first.sourceKey, first.mapId, "ID_TARGET", false, true, "", "", "")),
            edit(second.mapId, second.sourceKey, 0,
                descriptor(second.sourceKey, second.mapId, "ID_TARGET", false, true, "", "", "")));

        FreeplaneMapCommandExecutor.ContributorDeletionTransaction transaction =
            fixture.edt.call(new Callable<FreeplaneMapCommandExecutor.ContributorDeletionTransaction>() {
                @Override
                public FreeplaneMapCommandExecutor.ContributorDeletionTransaction call() {
                    return fixture.executor.beginContributorDeletion(plan);
                }
            });

        assertRejected(transaction.outcome(), "graph_workspace.connector.changed");
        assertThat(first.undo.startCalls).isEqualTo(1);
        assertThat(second.undo.startCalls).isEqualTo(1);
        assertThat(first.undo.rollbackCalls).isEqualTo(1);
        assertThat(second.undo.rollbackCalls).isEqualTo(1);
        assertThat(connectors(first.source)).hasSize(1);
        assertThat(connectors(second.source)).hasSize(1);
        assertThat(fixture.executor.currentUndoTarget()).isEmpty();
    }

    @Test
    public void exposesIncompleteRecoveryAndRetainsRetryStateWhenNativeRollbackFails() {
        Fixture fixture = new Fixture(true);
        MapNodes nodes = fixture.addMap(MapReferenceId.of("00000000-0000-0000-0000-000000000536"),
            "rollback failure");
        NodeLinks.createLinkExtension(nodes.source).addArrowlink(
            connector(nodes.source, "ID_TARGET", ConnectorArrows.FORWARD, "", "", ""));
        fixture.nativeController.throwAfterDeleteMutation = true;
        nodes.undo.throwOnRollback = true;
        ContributorDeletionPlan plan = plan(edit(nodes.mapId, nodes.sourceKey, 0,
            descriptor(nodes.sourceKey, nodes.mapId, "ID_TARGET", false, true, "", "", "")));

        FreeplaneMapCommandExecutor.ContributorDeletionTransaction transaction =
            fixture.edt.call(new Callable<FreeplaneMapCommandExecutor.ContributorDeletionTransaction>() {
                @Override
                public FreeplaneMapCommandExecutor.ContributorDeletionTransaction call() {
                    return fixture.executor.beginContributorDeletion(plan);
                }
            });

        assertRejected(transaction.outcome(), "graph_workspace.contributor.undo_incomplete");
        assertThat(transaction.dirtySourceMaps()).containsExactly(nodes.mapId);
        assertThat(connectors(nodes.source)).isEmpty();
        assertThat(fixture.executor.currentUndoTarget()).isEmpty();

        nodes.undo.throwOnRollback = false;
        fixture.edt.call(new Callable<Void>() {
            @Override
            public Void call() {
                transaction.rollback();
                return null;
            }
        });

        assertThat(connectors(nodes.source)).hasSize(1);
        assertRejected(transaction.outcome(), "graph_workspace.connector.changed");
        assertThat(fixture.executor.currentUndoTarget()).isEmpty();
    }

    @Test
    public void compensatesCommittedMapsWhenALaterNativeCommitFails() {
        Fixture fixture = new Fixture(true);
        MapNodes first = fixture.addMap(MapReferenceId.of("00000000-0000-0000-0000-000000000537"),
            "committed first");
        MapNodes second = fixture.addMap(MapReferenceId.of("00000000-0000-0000-0000-000000000538"),
            "failed second");
        NodeLinks.createLinkExtension(first.source).addArrowlink(
            connector(first.source, "ID_TARGET", ConnectorArrows.FORWARD, "", "", ""));
        NodeLinks.createLinkExtension(second.source).addArrowlink(
            connector(second.source, "ID_TARGET", ConnectorArrows.FORWARD, "", "", ""));
        first.undo.canUndo = true;
        second.undo.throwOnCommit = true;
        ContributorDeletionPlan plan = plan(
            edit(first.mapId, first.sourceKey, 0,
                descriptor(first.sourceKey, first.mapId, "ID_TARGET", false, true, "", "", "")),
            edit(second.mapId, second.sourceKey, 0,
                descriptor(second.sourceKey, second.mapId, "ID_TARGET", false, true, "", "", "")));
        FreeplaneMapCommandExecutor.ContributorDeletionTransaction transaction =
            fixture.edt.call(new Callable<FreeplaneMapCommandExecutor.ContributorDeletionTransaction>() {
                @Override
                public FreeplaneMapCommandExecutor.ContributorDeletionTransaction call() {
                    return fixture.executor.beginContributorDeletion(plan);
                }
            });

        assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() {
                fixture.edt.call(new Callable<Void>() {
                    @Override
                    public Void call() {
                        transaction.commit();
                        return null;
                    }
                });
            }
        }).isInstanceOf(RuntimeException.class);

        assertThat(connectors(first.source)).hasSize(1);
        assertThat(connectors(second.source)).hasSize(1);
        assertThat(first.undo.commitCalls).isEqualTo(1);
        assertThat(second.undo.commitCalls).isEqualTo(1);
        assertThat(first.undo.undoCalls).isEqualTo(1);
        assertThat(second.undo.rollbackCalls).isEqualTo(1);
        assertRejected(transaction.outcome(), "graph_workspace.contributor.native_commit_failed");
        assertThat(fixture.executor.currentUndoTarget()).isEmpty();
    }

    @Test
    public void keepsTheFullDescriptorGuardForAReorderedBatchOccurrence() {
        Fixture fixture = new Fixture(true);
        MapNodes nodes = fixture.addMap(MapReferenceId.of("00000000-0000-0000-0000-000000000541"), "reordered");
        NodeLinks links = NodeLinks.createLinkExtension(nodes.source);
        links.addArrowlink(connector(nodes.source, "ID_TARGET", ConnectorArrows.FORWARD, "wrong", "", ""));
        links.addArrowlink(connector(nodes.source, "ID_TARGET", ConnectorArrows.BOTH, "right", "", ""));
        ContributorDeletionPlan plan = plan(edit(nodes.mapId, nodes.sourceKey, 1,
            descriptor(nodes.sourceKey, nodes.mapId, "ID_TARGET", true, true, "expected", "", "")));

        FreeplaneMapCommandExecutor.ContributorDeletionTransaction transaction =
            fixture.edt.call(new Callable<FreeplaneMapCommandExecutor.ContributorDeletionTransaction>() {
                @Override
                public FreeplaneMapCommandExecutor.ContributorDeletionTransaction call() {
                    return fixture.executor.beginContributorDeletion(plan);
                }
            });

        assertRejected(transaction.outcome(), "graph_workspace.connector.changed");
        assertThat(fixture.nativeController.removeCalls).isZero();
        assertThat(connectors(nodes.source)).hasSize(2);
    }

    private static ContributorDeletionPlan plan(ContributorDeletionPlan.NativeEdit... edits) {
        Map<MapReferenceId, List<ContributorDeletionPlan.NativeEdit>> grouped =
            new LinkedHashMap<MapReferenceId, List<ContributorDeletionPlan.NativeEdit>>();
        for (ContributorDeletionPlan.NativeEdit edit : edits) {
            List<ContributorDeletionPlan.NativeEdit> values = grouped.get(edit.key().mapReferenceId().get());
            if (values == null) {
                values = new java.util.ArrayList<ContributorDeletionPlan.NativeEdit>();
                grouped.put(edit.key().mapReferenceId().get(), values);
            }
            values.add(edit);
        }
        return ContributorDeletionPlan.of(grouped, Collections.emptySet());
    }

    private static ContributorDeletionPlan.NativeEdit edit(MapReferenceId map, SourceNodeKey source, int occurrence,
            ConnectorDescriptor descriptor) {
        return ContributorDeletionPlan.NativeEdit.of(ContributorKey.nativeConnector(map, source, occurrence),
            descriptor);
    }

    @Test
    public void rejectsAStaleOccurrenceOrDescriptorWithoutDeletingAnotherConnector() {
        // Catches deletion that trusts an old occurrence or descriptor after the source links have changed.
        assertStaleDeletionRejected(StaleConnectorMutation.TARGET);
        assertStaleDeletionRejected(StaleConnectorMutation.ARROWS);
        assertStaleDeletionRejected(StaleConnectorMutation.LABEL);
    }


    @Test
    public void exposesAndUndoesOnlyTheLastSuccessfulSourceMapUndoTarget() {
        // Catches undo targeting an older successful map or replacing the target after a rejected command.
        Fixture fixture = new Fixture(true);
        MapNodes first = fixture.addMap("first undo target");
        MapNodes second = fixture.addMap("second undo target");

        assertThat(fixture.executor.currentUndoTarget()).isEmpty();
        assertApplied(fixture.executor.createConnector(first.sourceKey, first.targetKey, RelationshipDirection.FORWARD));
        assertThat(fixture.executor.currentUndoTarget()).contains(new MapUndoTarget(first.mapId, "first undo target", false));

        assertApplied(fixture.executor.createConnector(second.sourceKey, second.targetKey, RelationshipDirection.FORWARD));
        assertThat(fixture.executor.currentUndoTarget()).contains(new MapUndoTarget(second.mapId, "second undo target", false));

        GraphCommandResult noUndo = fixture.executor.undoCurrentSourceMap();
        assertThat(noUndo.status()).isEqualTo(GraphCommandResult.Status.NO_OP);
        assertThat(noUndo.messageKey()).isEqualTo("graph_workspace.source_map.nothing_to_undo");
        assertThat(first.undo.undoCalls).isZero();
        assertThat(second.undo.undoCalls).isZero();

        first.map.setReadOnly(true);
        GraphCommandResult rejected = fixture.executor.createConnector(first.sourceKey, first.targetKey,
            RelationshipDirection.FORWARD);
        assertRejected(rejected, "graph_workspace.source_map.read_only");
        assertThat(fixture.executor.currentUndoTarget()).contains(new MapUndoTarget(second.mapId, "second undo target", false));

        second.undo.canUndo = true;
        GraphCommandResult undone = fixture.executor.undoCurrentSourceMap();

        assertApplied(undone);
        assertThat(undone.messageKey()).isEqualTo("graph_workspace.source_map.undone");
        assertThat(undone.dirtySourceMaps()).containsExactly(second.mapId);
        assertThat(first.undo.undoCalls).isZero();
        assertThat(second.undo.undoCalls).isEqualTo(1);
    }

    @Test
    public void makesTheLastSuccessfulDeleteOwnTheUndoTarget() {
        // Catches successful deletion that fails to replace the source map remembered for a later undo.
        Fixture fixture = new Fixture(true);
        MapNodes created = fixture.addMap("create undo target");
        MapNodes deleted = fixture.addMap("delete undo target");
        assertApplied(fixture.executor.createConnector(created.sourceKey, created.targetKey, RelationshipDirection.FORWARD));
        NodeLinks.createLinkExtension(deleted.source)
            .addArrowlink(connector(deleted.source, "ID_TARGET", ConnectorArrows.FORWARD, "", "", ""));
        ConnectorDescriptor expected = descriptor(deleted.sourceKey, deleted.mapId, "ID_TARGET", false, true, "", "", "");
        ContributorKey key = ContributorKey.nativeConnector(deleted.mapId, deleted.sourceKey, 0);

        assertApplied(fixture.executor.deleteConnector(key, expected));
        assertThat(fixture.executor.currentUndoTarget())
            .contains(new MapUndoTarget(deleted.mapId, "delete undo target", false));

        GraphCommandResult rejected = fixture.executor.deleteConnector(key, expected);
        assertRejected(rejected, "graph_workspace.connector.changed");
        assertThat(fixture.executor.currentUndoTarget())
            .contains(new MapUndoTarget(deleted.mapId, "delete undo target", false));

        deleted.undo.canUndo = true;
        GraphCommandResult undone = fixture.executor.undoCurrentSourceMap();

        assertApplied(undone);
        assertThat(undone.dirtySourceMaps()).containsExactly(deleted.mapId);
        assertThat(created.undo.undoCalls).isZero();
        assertThat(deleted.undo.undoCalls).isEqualTo(1);
    }

    @Test
    public void entersTheEdtForEveryPublicCommand() {
        // Catches public command methods that invoke model, native, or result work outside EdtExecutor.call.
        Fixture fixture = new Fixture(true);
        MapNodes nodes = fixture.addMap("EDT commands");

        fixture.executor.currentUndoTarget();
        assertThat(fixture.edt.callCount()).isEqualTo(1);

        assertApplied(fixture.executor.createConnector(nodes.sourceKey, nodes.targetKey, RelationshipDirection.FORWARD));
        assertThat(fixture.edt.callCount()).isEqualTo(2);

        ConnectorDescriptor expected = descriptor(nodes.sourceKey, nodes.mapId, "ID_TARGET", false, true, "", "", "");
        assertApplied(fixture.executor.deleteConnector(ContributorKey.nativeConnector(nodes.mapId, nodes.sourceKey, 0),
            expected));
        assertThat(fixture.edt.callCount()).isEqualTo(3);

        nodes.undo.canUndo = true;
        assertApplied(fixture.executor.undoCurrentSourceMap());
        assertThat(fixture.edt.callCount()).isEqualTo(4);
    }

    @Test
    public void doesNotSaveWorkspaceOrSourceMapsWhileCreatingDeletingOrUndoing() {
        // Catches command execution routed through workspace persistence or a native-map save path.
        Fixture fixture = new Fixture(true);
        MapNodes nodes = fixture.addMap("no saves");
        assertApplied(fixture.executor.createConnector(nodes.sourceKey, nodes.targetKey, RelationshipDirection.FORWARD));
        ConnectorDescriptor expected = descriptor(nodes.sourceKey, nodes.mapId, "ID_TARGET", false, true, "", "", "");
        assertApplied(fixture.executor.deleteConnector(ContributorKey.nativeConnector(nodes.mapId, nodes.sourceKey, 0),
            expected));
        nodes.undo.canUndo = true;
        assertApplied(fixture.executor.undoCurrentSourceMap());

        assertThat(fixture.results.saveHookCalls()).isZero();
        assertThat(nodes.map.saveHookCalls()).isZero();
    }

    private void assertCreateDirection(RelationshipDirection direction, ConnectorArrows arrows) {
        Fixture fixture = new Fixture(true);
        MapNodes nodes = fixture.addMap("direction " + direction);

        GraphCommandResult result = fixture.executor.createConnector(nodes.sourceKey, nodes.targetKey, direction);

        assertApplied(result);
        assertThat(nodes.undo.startCalls).isEqualTo(1);
        assertThat(nodes.undo.commitCalls).isEqualTo(1);
        assertThat(nodes.undo.rollbackCalls).isZero();
        assertThat(fixture.nativeController.nodeAddCalls).isZero();
        assertThat(fixture.nativeController.lastTargetId).isEqualTo("ID_TARGET");
        assertThat(connectors(nodes.source)).hasSize(1);
        assertThat(connectors(nodes.source).get(0).getArrows()).contains(arrows);
        assertThat(result.dirtySourceMaps()).containsExactly(nodes.mapId);
        assertThat(result.identityChange()).isEmpty();
        assertThat(nodes.map.isSaved()).isFalse();
        assertThat(nodes.map.saveHookCalls()).isZero();
    }

    private void assertStaleDeletionRejected(StaleConnectorMutation mutation) {
        Fixture fixture = new Fixture(true);
        MapNodes nodes = fixture.addMap("stale " + mutation);
        ConnectorModel first = connector(nodes.source, "ID_TARGET", ConnectorArrows.NONE, "first", "", "");
        ConnectorModel second = connector(nodes.source, "ID_TARGET", ConnectorArrows.BOTH, "source", "middle", "target");
        NodeLinks links = NodeLinks.createLinkExtension(nodes.source);
        links.addArrowlink(first);
        links.addArrowlink(second);
        ConnectorDescriptor expected = descriptor(nodes.sourceKey, nodes.mapId, "ID_TARGET", true, true,
            "source", "middle", "target");

        if (mutation == StaleConnectorMutation.TARGET) {
            links.removeArrowlink(second);
            links.addArrowlink(connector(nodes.source, "ID_TARGET_REPLACED", ConnectorArrows.BOTH, "source", "middle",
                "target"));
        }
        else if (mutation == StaleConnectorMutation.ARROWS) {
            second.setArrows(Optional.of(ConnectorArrows.FORWARD));
        }
        else {
            second.setMiddleLabel("changed label");
        }
        List<String> beforeRequest = connectorFingerprint(nodes.source);

        GraphCommandResult result = fixture.executor.deleteConnector(
            ContributorKey.nativeConnector(nodes.mapId, nodes.sourceKey, 1), expected);

        assertRejected(result, "graph_workspace.connector.changed");
        assertThat(connectorFingerprint(nodes.source)).isEqualTo(beforeRequest);
        assertThat(connectors(nodes.source)).hasSize(2);
        assertThat(nodes.undo.startCalls).isZero();
        assertThat(fixture.nativeController.removeCalls).isZero();
    }

    private static ConnectorModel connector(NodeModel source, String targetId, ConnectorArrows arrows,
            String sourceLabel, String middleLabel, String targetLabel) {
        ConnectorModel connector = new ConnectorModel(source, targetId);
        connector.setArrows(Optional.of(arrows));
        connector.setSourceLabel(sourceLabel);
        connector.setMiddleLabel(middleLabel);
        connector.setTargetLabel(targetLabel);
        return connector;
    }

    private static ConnectorDescriptor descriptor(SourceNodeKey source, MapReferenceId mapId, String targetId,
            boolean arrowAtSource, boolean arrowAtTarget, String sourceLabel, String middleLabel, String targetLabel) {
        return ConnectorDescriptor.of(source, NodeReference.of(mapId, PersistedNodeId.of(targetId)), arrowAtSource,
            arrowAtTarget, sourceLabel, middleLabel, targetLabel);
    }

    private static List<ConnectorModel> connectors(NodeModel source) {
        List<ConnectorModel> result = new ArrayList<ConnectorModel>();
        NodeLinks links = NodeLinks.getLinkExtension(source);
        if (links == null) {
            return result;
        }
        for (NodeLinkModel link : links.getLinks()) {
            if (link instanceof ConnectorModel) {
                result.add((ConnectorModel) link);
            }
        }
        return result;
    }

    private static List<String> connectorFingerprint(NodeModel source) {
        List<String> result = new ArrayList<String>();
        for (ConnectorModel connector : connectors(source)) {
            result.add(connector.getTargetID() + ":" + connector.getArrows().orElse(ConnectorArrows.DEFAULT).name()
                + ":" + connector.getSourceLabel().orElse("") + ":" + connector.getMiddleLabel().orElse("")
                + ":" + connector.getTargetLabel().orElse(""));
        }
        return result;
    }

    private static void assertApplied(GraphCommandResult result) {
        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.APPLIED);
    }

    private static void assertRejected(GraphCommandResult result, String messageKey) {
        assertThat(result.status()).isEqualTo(GraphCommandResult.Status.REJECTED);
        assertThat(result.messageKey()).isEqualTo(messageKey);
    }

    private enum StaleConnectorMutation {
        TARGET,
        ARROWS,
        LABEL
    }

    private static final class Fixture {
        private final InlineEdt edt = new InlineEdt();
        private final Map<MapReferenceId, TestLease> leases = new HashMap<MapReferenceId, TestLease>();
        private final Map<SourceNodeKey, NodeModel> resolvedNodes = new HashMap<SourceNodeKey, NodeModel>();
        private final Set<MapModel> openViews = Collections.newSetFromMap(new java.util.IdentityHashMap<MapModel, Boolean>());
        private final ModeController modeController = mock(ModeController.class);
        private final MapController mapController = mock(MapController.class);
        private final Controller controller = mock(Controller.class);
        private final IMapViewManager viewManager = mock(IMapViewManager.class);
        private final ReadOnlyResultEnvelope results = new ReadOnlyResultEnvelope(edt);
        private final RecordingLinkController nativeController = new RecordingLinkController(modeController, edt);
        private final FreeplaneMapCommandExecutor executor;
        private int viewCreations;

        Fixture(boolean installsUndoOnViewCreation) {
            when(modeController.getMapController()).thenReturn(mapController);
            when(modeController.getController()).thenReturn(controller);
            when(modeController.canEdit(any(MapModel.class))).thenAnswer(invocation -> {
                edt.requireOnEdt("map editability access");
                return true;
            });
            when(controller.getMapViewManager()).thenReturn(viewManager);
            when(viewManager.containsView(any(MapModel.class))).thenAnswer(invocation -> {
                edt.requireOnEdt("view-manager lookup");
                return openViews.contains(invocation.getArgument(0));
            });
            doAnswer(invocation -> {
                edt.requireOnEdt("map-view materialization");
                MapModel map = invocation.getArgument(0);
                map.beforeViewCreated();
                openViews.add(map);
                viewCreations++;
                return null;
            }).when(mapController).createMapView(any(MapModel.class));
            ViewMaterializationTracker views = new ViewMaterializationTracker(modeController);
            executor = new FreeplaneMapCommandExecutor(new LeaseLookup(leases, edt), modeController, edt, views,
                new Resolver(resolvedNodes, edt), results, new NativeConnectorAdapter(nativeController, edt));
            this.installsUndoOnViewCreation = installsUndoOnViewCreation;
        }

        private final boolean installsUndoOnViewCreation;

        MapNodes addMap(String title) {
            return addMap(MapReferenceId.of(UUID.randomUUID()), title);
        }

        MapNodes replaceMapModel(MapNodes replaced) {
            openViews.remove(replaced.map);
            return addMap(replaced.mapId, replaced.map.getTitle());
        }

        private MapNodes addMap(MapReferenceId mapId, String title) {
            TrackingMapModel map = new TrackingMapModel(title, installsUndoOnViewCreation, edt);
            NodeModel root = node(map, "root", "ID_ROOT");
            map.setRoot(root);
            NodeModel source = node(map, "source", "ID_SOURCE");
            NodeModel target = node(map, "target", "ID_TARGET");
            NodeModel replacementTarget = node(map, "replacement target", "ID_TARGET_REPLACED");
            root.insert(source);
            root.insert(target);
            root.insert(replacementTarget);
            SourceNodeKey sourceKey = sourceKey(mapId, "ID_SOURCE");
            SourceNodeKey targetKey = sourceKey(mapId, "ID_TARGET");
            leases.put(mapId, new TestLease(mapId, MapOperationalState.AVAILABLE, edt));
            resolvedNodes.put(sourceKey, source);
            resolvedNodes.put(targetKey, target);
            return new MapNodes(mapId, map, source, target, sourceKey, targetKey, map.undo);
        }
    }

    private static final class MapNodes {
        private final MapReferenceId mapId;
        private final TrackingMapModel map;
        private final NodeModel source;
        private final NodeModel target;
        private final SourceNodeKey sourceKey;
        private SourceNodeKey targetKey;
        private final RecordingUndoHandler undo;

        private MapNodes(MapReferenceId mapId, TrackingMapModel map, NodeModel source, NodeModel target,
                SourceNodeKey sourceKey, SourceNodeKey targetKey, RecordingUndoHandler undo) {
            this.mapId = mapId;
            this.map = map;
            this.source = source;
            this.target = target;
            this.sourceKey = sourceKey;
            this.targetKey = targetKey;
            this.undo = undo;
        }
    }

    private static final class LeaseLookup implements FreeplaneMapCommandExecutor.MapLeaseLookup {
        private final Map<MapReferenceId, TestLease> leases;
        private final InlineEdt edt;

        private LeaseLookup(Map<MapReferenceId, TestLease> leases, InlineEdt edt) {
            this.leases = leases;
            this.edt = edt;
        }

        @Override
        public Optional<MapLease> find(MapReferenceId mapReferenceId) {
            edt.requireOnEdt("lease lookup");
            return Optional.<MapLease>ofNullable(leases.get(mapReferenceId));
        }
    }

    private static final class Resolver implements FreeplaneMapCommandExecutor.TraversalResolver {
        private final Map<SourceNodeKey, NodeModel> nodes;
        private final InlineEdt edt;

        private Resolver(Map<SourceNodeKey, NodeModel> nodes, InlineEdt edt) {
            this.nodes = nodes;
            this.edt = edt;
        }

        @Override
        public Optional<NodeModel> resolve(MapLease lease, SourceNodeKey key) {
            edt.requireOnEdt("traversal resolution");
            return Optional.ofNullable(nodes.get(key));
        }
    }

    private static final class ReadOnlyResultEnvelope implements FreeplaneMapCommandExecutor.ResultEnvelope {
        private final WorkspaceDocument document = WorkspaceDocument.createVersion1(WorkspaceId.of(UUID.randomUUID()));
        private final InlineEdt edt;
        private int saveHookCalls;

        private ReadOnlyResultEnvelope(InlineEdt edt) {
            this.edt = edt;
        }

        @Override
        public WorkspaceDocument currentDocument() {
            edt.requireOnEdt("result envelope access");
            return document;
        }

        int saveHookCalls() {
            return saveHookCalls;
        }
    }

    private static final class NativeConnectorAdapter implements FreeplaneMapCommandExecutor.NativeConnector {
        private final RecordingLinkController controller;
        private final InlineEdt edt;

        private NativeConnectorAdapter(RecordingLinkController controller, InlineEdt edt) {
            this.controller = controller;
            this.edt = edt;
        }

        @Override
        public ConnectorModel addConnector(NodeModel source, String targetId) {
            edt.requireOnEdt("native connector creation");
            return controller.addConnector(source, targetId);
        }

        @Override
        public void changeArrows(ConnectorModel connector, ConnectorArrows arrows) {
            edt.requireOnEdt("native connector arrow update");
            controller.changeArrowsOfArrowLink(connector, Optional.of(arrows));
        }

        @Override
        public void removeArrowLink(ConnectorModel connector) {
            edt.requireOnEdt("native connector deletion");
            controller.removeArrowLink(connector);
        }
    }

    private static final class RecordingLinkController extends MLinkController {
        private final InlineEdt edt;
        private int idAddCalls;
        private int nodeAddCalls;
        private int removeCalls;
        private String lastTargetId;
        private boolean throwAfterCreateMutation;
        private boolean throwAfterDeleteMutation;
        private int throwOnDeleteCall = -1;

        private RecordingLinkController(ModeController modeController, InlineEdt edt) {
            super(modeController);
            this.edt = edt;
        }

        @Override
        public ConnectorModel addConnector(NodeModel source, NodeModel target) {
            edt.requireOnEdt("native node-target overload");
            nodeAddCalls++;
            throw new AssertionError("The executor must call the saved target-ID overload");
        }

        @Override
        public ConnectorModel addConnector(final NodeModel source, String targetId) {
            edt.requireOnEdt("native ID-target overload");
            idAddCalls++;
            lastTargetId = targetId;
            final ConnectorModel connector = new ConnectorModel(source, targetId);
            final NodeLinks links = NodeLinks.createLinkExtension(source);
            links.addArrowlink(connector);
            source.getMap().setSaved(false);
            if (throwAfterCreateMutation) {
                restoreOnRollback(source, new Runnable() {
                    @Override
                    public void run() {
                        links.removeArrowlink(connector);
                    }
                });
                throw new IllegalStateException("native create failure");
            }
            return connector;
        }

        @Override
        public void changeArrowsOfArrowLink(ConnectorModel connector, Optional<ConnectorArrows> arrows) {
            edt.requireOnEdt("native arrow mutation");
            connector.setArrows(arrows);
        }

        @Override
        public void removeArrowLink(final ConnectorModel connector) {
            edt.requireOnEdt("native connector removal");
            removeCalls++;
            final NodeLinks links = NodeLinks.getLinkExtension(connector.getSource());
            links.removeArrowlink(connector);
            connector.getSource().getMap().setSaved(false);
            restoreOnRollback(connector.getSource(), new Runnable() {
                @Override
                public void run() {
                    links.addArrowlink(connector);
                }
            });
            if (throwAfterDeleteMutation || removeCalls == throwOnDeleteCall) {
                throw new IllegalStateException("native delete failure");
            }
        }

        private void restoreOnRollback(NodeModel source, Runnable restoration) {
            IUndoHandler handler = source.getMap().getExtension(IUndoHandler.class);
            if (!(handler instanceof RecordingUndoHandler)) {
                throw new AssertionError("The test native mutation requires a recording undo handler");
            }
            ((RecordingUndoHandler) handler).restoreOnRollback(restoration);
        }
    }

    private static final class TestLease implements MapLease {
        private final MapReferenceId mapId;
        private final MapOperationalState state;
        private final InlineEdt edt;

        private TestLease(MapReferenceId mapId, MapOperationalState state, InlineEdt edt) {
            this.mapId = mapId;
            this.state = state;
            this.edt = edt;
        }

        @Override
        public MapReferenceId mapReferenceId() {
            edt.requireOnEdt("lease map identity");
            return mapId;
        }

        @Override
        public MapOperationalState state() {
            edt.requireOnEdt("lease state");
            return state;
        }

        @Override
        public void close() {
        }
    }

    private static final class InlineEdt implements EdtExecutor {
        private boolean onEdt;
        private int callCount;

        @Override
        public <T> T call(Callable<T> task) {
            callCount++;
            boolean previous = onEdt;
            onEdt = true;
            try {
                return task.call();
            }
            catch (RuntimeException failure) {
                throw failure;
            }
            catch (Exception failure) {
                throw new AssertionError("EDT task failed", failure);
            }
            finally {
                onEdt = previous;
            }
        }

        @Override
        public void execute(Runnable task) {
            task.run();
        }

        @Override
        public boolean isEdt() {
            return onEdt;
        }

        private void requireOnEdt(String operation) {
            assertThat(onEdt).as(operation + " must run on the EDT").isTrue();
        }

        private int callCount() {
            return callCount;
        }
    }

    private static final class TrackingMapModel extends MapModel {
        private final String title;
        private final boolean installsUndoOnViewCreation;
        private final InlineEdt edt;
        private final RecordingUndoHandler undo;
        private int registryCalls;
        private int saveHookCalls;

        private TrackingMapModel(String title, boolean installsUndoOnViewCreation, InlineEdt edt) {
            super(new INodeDuplicator() {
                @Override
                public NodeModel duplicate(NodeModel source, MapModel targetMap, boolean withChildren) {
                    return null;
                }
            }, null, null);
            this.title = title;
            this.installsUndoOnViewCreation = installsUndoOnViewCreation;
            this.edt = edt;
            this.undo = new RecordingUndoHandler(edt);
        }

        @Override
        public <T extends org.freeplane.core.extension.IExtension> T getExtension(Class<T> clazz) {
            if (clazz == IUndoHandler.class) {
                edt.requireOnEdt("map undo-extension access");
            }
            return super.getExtension(clazz);
        }

        @Override
        public NodeModel getRootNode() {
            edt.requireOnEdt("map root access");
            return super.getRootNode();
        }

        @Override
        public void beforeViewCreated() {
            edt.requireOnEdt("map before-view hook");
            if (installsUndoOnViewCreation && getExtension(IUndoHandler.class) == null) {
                addExtension(IUndoHandler.class, undo);
            }
        }

        @Override
        public String registryNode(NodeModel nodeModel) {
            registryCalls++;
            return super.registryNode(nodeModel);
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public void setSaved(boolean saved) {
            if (saved) {
                saveHookCalls++;
            }
            super.setSaved(saved);
        }

        int registryCalls() {
            return registryCalls;
        }

        int saveHookCalls() {
            return saveHookCalls;
        }
    }

    private static final class RecordingUndoHandler implements IUndoHandler {
        private final InlineEdt edt;
        private int startCalls;
        private int commitCalls;
        private int rollbackCalls;
        private int undoCalls;
        private boolean canUndo;
        private boolean throwOnCommit;
        private boolean throwOnRollback;
        private Runnable rollbackRestoration;

        private RecordingUndoHandler(InlineEdt edt) {
            this.edt = edt;
        }

        @Override
        public void addActor(IActor actor) {
        }

        @Override
        public boolean canRedo() {
            return false;
        }

        @Override
        public boolean canUndo() {
            edt.requireOnEdt("undo availability check");
            return canUndo;
        }

        @Override
        public void addChangeListener(ChangeListener listener) {
        }

        @Override
        public void removeChangeListener(ChangeListener listener) {
        }

        @Override
        public void commit() {
            edt.requireOnEdt("undo commit");
            commitCalls++;
            if (throwOnCommit) {
                throw new IllegalStateException("undo commit failure");
            }
        }

        @Override
        public String getLastDescription() {
            return null;
        }

        @Override
        public ActionListener getRedoAction() {
            return null;
        }

        @Override
        public ActionListener getUndoAction() {
            return null;
        }

        @Override
        public boolean isUndoActionRunning() {
            return false;
        }

        @Override
        public void redo() {
        }

        @Override
        public void resetRedo() {
        }

        @Override
        public void rollback() {
            edt.requireOnEdt("undo rollback");
            rollbackCalls++;
            if (throwOnRollback) {
                throw new IllegalStateException("undo rollback failure");
            }
            if (rollbackRestoration != null) {
                Runnable restoration = rollbackRestoration;
                rollbackRestoration = null;
                restoration.run();
            }
        }

        private void restoreOnRollback(Runnable restoration) {
            rollbackRestoration = restoration;
        }

        @Override
        public void startTransaction() {
            edt.requireOnEdt("undo transaction start");
            startCalls++;
        }

        @Override
        public void forceNewTransaction() {
        }

        @Override
        public void undo() {
            edt.requireOnEdt("undo operation");
            undoCalls++;
            if (rollbackRestoration != null) {
                Runnable restoration = rollbackRestoration;
                rollbackRestoration = null;
                restoration.run();
            }
        }

        @Override
        public void deactivate() {
        }

        @Override
        public void delayedCommit() {
        }

        @Override
        public void delayedRollback() {
        }

        @Override
        public int getTransactionLevel() {
            return 0;
        }
    }

    private static NodeModel node(MapModel map, String text, String id) {
        NodeModel node = new NodeModel(text, map);
        if (id != null) {
            node.setID(id);
        }
        return node;
    }

    private static SourceNodeKey sourceKey(MapReferenceId mapId, String id) {
        return SourceNodeKey.persisted(NodeReference.of(mapId, PersistedNodeId.of(id)));
    }
}
