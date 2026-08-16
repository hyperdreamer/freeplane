package org.freeplane.plugin.graph.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Dimension;
import java.awt.geom.Dimension2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.EnumSet;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.freeplane.plugin.graph.adapter.EdtExecutor;
import org.freeplane.plugin.graph.geometry.GeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GraphGeometryEngine;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.layout.LayoutConflict;
import org.freeplane.plugin.graph.layout.LayoutFrame;
import org.freeplane.plugin.graph.layout.PerceptualIdlePolicy;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.ProjectionDiff;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;
import org.junit.Test;

public class LayoutSettleLoopShould {
    private static final WorkspaceId WORKSPACE =
        WorkspaceId.of("00000000-0000-0000-0000-000000000101");
    private static final MapReferenceId MAP =
        MapReferenceId.of("00000000-0000-0000-0000-000000000102");

    @Test
    public void publishesEverySettlingFrameOnTheEdtUntilIdle() {
        GraphProjection projection = populatedProjection(4L);
        TestStepper stepper = new TestStepper(frame(projection, 0L, false), frame(projection, 1L, false),
            frame(projection, 2L, true));
        ImmediateEdt edt = new ImmediateEdt();
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), edt);
        List<CanvasState> states = new ArrayList<CanvasState>();

        CompletionStage<Void> completion = loop.start(batch(4L), projection,
            ProjectionDiff.between(emptyProjection(3L), projection), states::add);

        assertThat(completion.toCompletableFuture().isCompletedExceptionally()).isFalse();
        await(completion);
        assertThat(states).hasSize(3);
        assertThat(states).extracting(CanvasState::generation).containsExactly(4L, 4L, 4L);
        assertThat(states).extracting(CanvasState::status)
            .containsExactly(OperationalStatus.SETTLING, OperationalStatus.SETTLING, OperationalStatus.IDLE);
        assertThat(stepper.submitCount).isEqualTo(1);
        assertThat(stepper.stepCount).isEqualTo(2);
        assertThat(states.get(0).projection()).isSameAs(projection);
        assertThat(states.get(0).geometry().nodes()).hasSize(1);
        assertThat(states.get(0).geometry().hulls()).hasSize(1);
        assertThat(edt.callbackCount).isEqualTo(3);
        assertThat(edt.allCallbacksOnEdt).isTrue();
        loop.close();
    }

    @Test
    public void publishesEmptyAfterAnAcceptedEmptyFrame() {
        TestStepper stepper = new TestStepper(frame(0L, true));
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), new ImmediateEdt());
        GraphProjection projection = emptyProjection(5L);
        List<CanvasState> states = new ArrayList<CanvasState>();

        await(loop.start(batch(5L), projection, ProjectionDiff.between(emptyProjection(4L), projection), states::add));

        assertThat(states).hasSize(1);
        assertThat(states.get(0).status()).isEqualTo(OperationalStatus.EMPTY);
        loop.close();
    }

    @Test
    public void publishesAFailedCurrentGenerationWhenItsInitialFrameFails() {
        GraphProjection projection = populatedProjection(6L);
        TestStepper stepper = new TestStepper(failedFrame(0L, LayoutPositions.of(
            Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap())));
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), new ImmediateEdt());
        List<CanvasState> states = new ArrayList<CanvasState>();

        await(loop.start(batch(6L), projection, ProjectionDiff.between(emptyProjection(5L), projection), states::add));

        assertThat(states).hasSize(1);
        CanvasState state = states.get(0);
        assertThat(state.generation()).isEqualTo(6L);
        assertThat(state.status()).isEqualTo(OperationalStatus.FAILED);
        assertThat(state.layout().failed()).isTrue();
        assertThat(state.layout().positions().nodes().keySet()).containsExactlyElementsOf(nodeKeys(projection));
        assertThat(state.layout().positions().anchors().keySet()).containsExactlyElementsOf(enclosureKeys(projection));
        loop.close();
    }

    @Test
    public void rejectsALowerGenerationAfterAHigherGenerationHasBeenAccepted() {
        GraphProjection higher = populatedProjection(8L);
        GraphProjection lower = populatedProjection(7L);
        TestStepper stepper = new TestStepper(frame(higher, 0L, true), frame(lower, 1L, true));
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), new ImmediateEdt());
        List<CanvasState> states = new ArrayList<CanvasState>();

        await(loop.start(batch(8L), higher, ProjectionDiff.between(emptyProjection(7L), higher), states::add));
        await(loop.start(batch(7L), lower, ProjectionDiff.between(emptyProjection(6L), lower), states::add));

        assertThat(stepper.submitCount).isEqualTo(1);
        assertThat(states).hasSize(1);
        assertThat(states.get(0).generation()).isEqualTo(8L);
        loop.close();
    }

    @Test
    public void suppressesAnInFlightFrameCompletedAfterPause() {
        GraphProjection projection = populatedProjection(9L);
        ControlledStepper stepper = new ControlledStepper();
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), new ImmediateEdt());
        List<CanvasState> states = new ArrayList<CanvasState>();

        CompletionStage<Void> completion = loop.start(batch(9L), projection,
            ProjectionDiff.between(emptyProjection(8L), projection), states::add);
        await(stepper.submitEntered);
        loop.pause();
        stepper.first.complete(frame(projection, 0L, true));
        stepper.allowStep = true;
        loop.restart();
        stepper.second.complete(frame(projection, 1L, true));
        await(completion);

        assertThat(states).hasSize(1);
        assertThat(states.get(0).layout().stepIndex()).isEqualTo(1L);
        assertThat(stepper.stepCount).isEqualTo(1);
        loop.close();
    }

    @Test
    public void suppressesAQueuedPublicationWhenPausePrecedesEdtDelivery() {
        GraphProjection projection = populatedProjection(10L);
        ControlledStepper stepper = new ControlledStepper();
        QueuedEdt edt = new QueuedEdt();
        RecordingMetrics metrics = new RecordingMetrics(edt);
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), metrics, edt);
        List<CanvasState> states = new ArrayList<CanvasState>();

        CompletionStage<Void> completion = loop.start(batch(10L), projection,
            ProjectionDiff.between(emptyProjection(9L), projection), states::add);
        await(stepper.submitEntered);
        stepper.first.complete(frame(projection, 0L, false));
        assertThat(await(metrics.measuredOnEdt)).isFalse();
        await(edt.executed);
        loop.pause();
        stepper.allowStep = true;
        loop.restart();
        edt.runQueued();
        await(stepper.stepCalled);
        stepper.second.complete(frame(projection, 1L, true));
        await(edt.secondExecuted);
        edt.runQueued();
        await(completion);

        assertThat(states).hasSize(1);
        assertThat(states.get(0).layout().stepIndex()).isEqualTo(1L);
        assertThat(stepper.stepCount).isEqualTo(1);
        loop.close();
    }

    @Test
    public void resetsAnIdleRunWithAFreshWorkerSeed() {
        GraphProjection projection = populatedProjection(11L);
        ResettableStepper stepper = new ResettableStepper();
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), new ImmediateEdt());
        List<CanvasState> states = new ArrayList<CanvasState>();
        CompletableFuture<CanvasState> resetPublication = new CompletableFuture<CanvasState>();

        await(loop.start(batch(11L), projection, ProjectionDiff.between(emptyProjection(10L), projection), state -> {
            states.add(state);
            if (states.size() == 2) {
                resetPublication.complete(state);
            }
        }));
        reset(loop);
        await(resetPublication);

        ProjectedNodeKey nodeKey = projection.nodes().get(0).key();
        assertThat(states).extracting(CanvasState::generation).containsExactly(11L, 11L);
        assertThat(states.get(0).layout().positions().nodes().get(nodeKey).x()).isEqualTo(0.0);
        assertThat(states.get(1).layout().positions().nodes().get(nodeKey).x()).isEqualTo(0.0);
        loop.close();
    }

    @Test
    public void continuesSettlingAfterAListenerThrows() {
        GraphProjection projection = populatedProjection(12L);
        TestStepper stepper = new TestStepper(frame(projection, 0L, false), frame(projection, 1L, true));
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), new ImmediateEdt());
        List<CanvasState> states = new ArrayList<CanvasState>();
        AtomicBoolean first = new AtomicBoolean(true);

        await(loop.start(batch(12L), projection, ProjectionDiff.between(emptyProjection(11L), projection), state -> {
            states.add(state);
            if (first.getAndSet(false)) {
                throw new IllegalStateException("listener failure");
            }
        }));

        assertThat(states).extracting(CanvasState::status)
            .containsExactly(OperationalStatus.SETTLING, OperationalStatus.IDLE);
        assertThat(stepper.stepCount).isEqualTo(1);
        loop.close();
    }

    @Test
    public void handlesAnAlreadyCompletedFrameOffTheEdt() {
        GraphProjection projection = populatedProjection(13L);
        TestStepper stepper = new TestStepper(frame(projection, 0L, true));
        QueuedEdt edt = new QueuedEdt();
        RecordingMetrics metrics = new RecordingMetrics(edt);
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), metrics, edt);
        final StageHolder completion = new StageHolder();
        AtomicBoolean listenerOnEdt = new AtomicBoolean();

        edt.runOnEdt(() -> completion.value = loop.start(batch(13L), projection,
            ProjectionDiff.between(emptyProjection(12L), projection), state -> listenerOnEdt.set(edt.isEdt())));
        assertThat(await(metrics.measuredOnEdt)).isFalse();
        await(edt.executed);
        edt.runQueued();
        await(completion.value);

        assertThat(listenerOnEdt).isTrue();
        loop.close();
    }

    @Test
    public void suppressesAStaleRunWhenANewerGenerationStarts() {
        ControlledStepper stepper = new ControlledStepper();
        ImmediateEdt edt = new ImmediateEdt();
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), edt);
        List<CanvasState> states = new ArrayList<CanvasState>();
        GraphProjection first = emptyProjection(7L);
        GraphProjection second = emptyProjection(8L);

        loop.start(batch(7L), first, ProjectionDiff.between(emptyProjection(6L), first), states::add);
        CompletionStage<Void> secondCompletion = loop.start(batch(8L), second,
            ProjectionDiff.between(first, second), states::add);

        stepper.first.complete(frame(0L, true));
        assertThat(states).isEmpty();
        stepper.second.complete(frame(1L, true));
        await(secondCompletion);

        assertThat(states).hasSize(1);
        assertThat(states.get(0).generation()).isEqualTo(8L);
        assertThat(states.get(0).projection()).isSameAs(second);
        loop.close();
    }

    @Test
    public void restartsThePausedWorkerBeforeANewerAcceptedGenerationSettles() {
        StickyPauseStepper stepper = new StickyPauseStepper();
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper,
            new GraphGeometryEngine(), new ImmediateEdt());
        GraphProjection first = populatedProjection(401L);
        GraphProjection newer = populatedProjection(402L);
        CompletableFuture<Void> newerIdlePublished = new CompletableFuture<Void>();

        try {
            loop.start(batch(401L), first, ProjectionDiff.between(emptyProjection(400L), first), state -> { });
            await(stepper.firstSubmitEntered);
            loop.pause();
            await(stepper.pauseObserved);
            CompletionStage<Void> completion = loop.start(batch(402L), newer,
                ProjectionDiff.between(first, newer), state -> {
                    if (state.generation() == 402L && state.status() == OperationalStatus.IDLE) {
                        newerIdlePublished.complete(null);
                    }
                });

            await(CompletableFuture.anyOf(stepper.pausedStepBlocked, newerIdlePublished));
            assertThat(stepper.pausedStepBlocked.isDone()).isFalse();
            await(newerIdlePublished);
            await(completion);
            assertThat(stepper.restartAfterPause).isTrue();
        }
        finally {
            stepper.releaseFirstFrame();
            loop.close();
        }
    }

    @Test
    public void doesNotSubmitANewerGenerationBeforeBlockedResetCompletes() {
        BlockingResetStepper stepper = new BlockingResetStepper();
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper,
            new GraphGeometryEngine(), new ImmediateEdt());
        GraphProjection first = populatedProjection(411L);
        GraphProjection newer = populatedProjection(412L);
        CompletableFuture<Void> resetCall = null;

        try {
            await(loop.start(batch(411L), first, ProjectionDiff.between(emptyProjection(410L), first), state -> { }));
            resetCall = CompletableFuture.runAsync(() -> reset(loop));
            await(stepper.resetEntered);
            CompletionStage<Void> newerCompletion = loop.start(batch(412L), newer,
                ProjectionDiff.between(first, newer), state -> { });

            assertThat(stepper.newerSubmittedBeforeResetRelease).isFalse();
            stepper.releaseReset();
            await(resetCall);
            await(newerCompletion);
        }
        finally {
            stepper.releaseReset();
            if (resetCall != null) {
                await(resetCall);
            }
            loop.close();
        }
    }

    @Test
    public void returnsFromResetBeforeBlockedPhysicalResetStarts() {
        ManualLifecycleDispatcher dispatcher = new ManualLifecycleDispatcher();
        RecordingStepper stepper = new RecordingStepper(dispatcher);
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper,
            new GraphGeometryEngine(), new ImmediateEdt(), dispatcher);
        GraphProjection projection = populatedProjection(415L);
        CompletableFuture<Void> resetReturned = new CompletableFuture<Void>();
        CompletableFuture<Void> physicalReset = null;

        try {
            CompletionStage<Void> initial = loop.start(batch(415L), projection,
                ProjectionDiff.between(emptyProjection(414L), projection), state -> { });
            dispatcher.runAll();
            await(initial);

            stepper.blockReset();
            CompletableFuture.runAsync(() -> {
                reset(loop);
                resetReturned.complete(null);
            });
            await(CompletableFuture.anyOf(resetReturned, stepper.resetEntered));
            assertThat(resetReturned.isDone()).isTrue();
            assertThat(stepper.resetEntered.isDone()).isFalse();

            physicalReset = runBlockedNext(dispatcher);
            await(stepper.resetEntered);
            stepper.releaseReset();
            await(physicalReset);
            await(resetReturned);
            dispatcher.runAll();
        }
        finally {
            stepper.releaseReset();
            if (physicalReset != null) {
                await(physicalReset);
            }
            closeFromExternalThread(loop, dispatcher, stepper);
        }
    }

    @Test
    public void executesOnlyTheNewestQueuedReset() {
        ManualLifecycleDispatcher dispatcher = new ManualLifecycleDispatcher();
        RecordingStepper stepper = new RecordingStepper(dispatcher);
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper,
            new GraphGeometryEngine(), new ImmediateEdt(), dispatcher);
        GraphProjection projection = populatedProjection(421L);

        try {
            CompletionStage<Void> initial = loop.start(batch(421L), projection,
                ProjectionDiff.between(emptyProjection(420L), projection), state -> { });
            dispatcher.runAll();
            await(initial);

            reset(loop);
            reset(loop);
            assertThat(stepper.resetCount()).isZero();
            assertThat(stepper.submitCount()).isEqualTo(1);
            dispatcher.runAll();

            assertThat(stepper.resetCount()).isEqualTo(1);
            assertThat(submissionGenerations(stepper)).containsExactly(421L, 421L);
            assertThat(stepper.operations()).containsSubsequence("reset", "restart", "submit");
        }
        finally {
            closeFromExternalThread(loop, dispatcher, stepper);
        }
    }

    @Test
    public void doesNotResetOrSubmitAfterCloseWinsBeforeQueuedReset() {
        ManualLifecycleDispatcher dispatcher = new ManualLifecycleDispatcher();
        RecordingStepper stepper = new RecordingStepper(dispatcher);
        ImmediateEdt edt = new ImmediateEdt();
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), edt, dispatcher);
        GraphProjection projection = populatedProjection(422L);
        CompletableFuture<Void> physicalClose = null;
        boolean closeQueued = false;

        try {
            CompletionStage<Void> initial = loop.start(batch(422L), projection,
                ProjectionDiff.between(emptyProjection(421L), projection), state -> { });
            dispatcher.runAll();
            await(initial);

            reset(loop);
            stepper.blockClose();
            int closeCommand = dispatcher.enqueueCount() + 1;
            edt.execute(loop::close);
            closeQueued = true;
            assertThat(stepper.resetEntered().isDone()).isFalse();
            dispatcher.runUntilExecuted(closeCommand - 1);
            physicalClose = runBlockedNext(dispatcher);
            await(stepper.closeEntered());
            stepper.releaseClose();
            await(physicalClose);

            assertThat(stepper.resetCount()).isZero();
            assertThat(stepper.submitCount()).isEqualTo(1);
            assertThat(stepper.closeCount()).isEqualTo(1);
        }
        finally {
            stepper.releaseClose();
            if (physicalClose != null) {
                await(physicalClose);
            }
            if (closeQueued) {
                dispatcher.runAll();
            }
            else {
                closeFromExternalThread(loop, dispatcher, stepper);
            }
        }
    }

    @Test
    public void doesNotFollowUpBlockedResetAfterNewerStartWins() {
        ManualLifecycleDispatcher dispatcher = new ManualLifecycleDispatcher();
        RecordingStepper stepper = new RecordingStepper(dispatcher);
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper,
            new GraphGeometryEngine(), new ImmediateEdt(), dispatcher);
        GraphProjection first = populatedProjection(411L);
        GraphProjection newer = populatedProjection(412L);
        CompletableFuture<Void> blockedReset = null;

        try {
            CompletionStage<Void> initial = loop.start(batch(411L), first,
                ProjectionDiff.between(emptyProjection(410L), first), state -> { });
            dispatcher.runAll();
            await(initial);

            stepper.blockReset();
            reset(loop);
            blockedReset = runBlockedNext(dispatcher);
            await(stepper.resetEntered());
            CompletionStage<Void> newerCompletion = loop.start(batch(412L), newer,
                ProjectionDiff.between(first, newer), state -> { });
            assertThat(stepper.submitCount()).isEqualTo(1);

            stepper.releaseReset();
            await(blockedReset);
            dispatcher.runAll();
            await(newerCompletion);

            assertThat(stepper.resetCount()).isEqualTo(1);
            assertThat(submissionGenerations(stepper)).containsExactly(411L, 412L);
        }
        finally {
            stepper.releaseReset();
            if (blockedReset != null) {
                await(blockedReset);
            }
            closeFromExternalThread(loop, dispatcher, stepper);
        }
    }

    @Test
    public void doesNotFollowUpBlockedResetAfterNewerResetWins() {
        ManualLifecycleDispatcher dispatcher = new ManualLifecycleDispatcher();
        RecordingStepper stepper = new RecordingStepper(dispatcher);
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper,
            new GraphGeometryEngine(), new ImmediateEdt(), dispatcher);
        GraphProjection projection = populatedProjection(413L);
        CompletableFuture<Void> blockedReset = null;

        try {
            CompletionStage<Void> initial = loop.start(batch(413L), projection,
                ProjectionDiff.between(emptyProjection(412L), projection), state -> { });
            dispatcher.runAll();
            await(initial);

            stepper.blockReset();
            reset(loop);
            blockedReset = runBlockedNext(dispatcher);
            await(stepper.resetEntered());
            reset(loop);
            await(dispatcher.enqueuedAt(dispatcher.enqueueCount()));

            stepper.releaseReset();
            await(blockedReset);
            dispatcher.runAll();

            assertThat(stepper.resetCount()).isEqualTo(2);
            assertThat(submissionGenerations(stepper)).containsExactly(413L, 413L);
        }
        finally {
            stepper.releaseReset();
            if (blockedReset != null) {
                await(blockedReset);
            }
            closeFromExternalThread(loop, dispatcher, stepper);
        }
    }

    @Test
    public void doesNotFollowUpBlockedResetAfterPauseWins() {
        ManualLifecycleDispatcher dispatcher = new ManualLifecycleDispatcher();
        RecordingStepper stepper = new RecordingStepper(dispatcher);
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper,
            new GraphGeometryEngine(), new ImmediateEdt(), dispatcher);
        GraphProjection projection = populatedProjection(414L);
        CompletableFuture<Void> blockedReset = null;

        try {
            CompletionStage<Void> initial = loop.start(batch(414L), projection,
                ProjectionDiff.between(emptyProjection(413L), projection), state -> { });
            dispatcher.runAll();
            await(initial);

            stepper.blockReset();
            reset(loop);
            blockedReset = runBlockedNext(dispatcher);
            await(stepper.resetEntered());
            loop.pause();
            assertThat(stepper.submitCount()).isEqualTo(1);

            stepper.releaseReset();
            await(blockedReset);
            dispatcher.runAll();
            assertThat(stepper.submitCount()).isEqualTo(1);
            assertThat(stepper.pauseCount()).isEqualTo(1);

            loop.restart();
            dispatcher.runAll();
            assertThat(stepper.submitCount()).isEqualTo(1);
            assertThat(stepper.stepCount()).isEqualTo(1);
        }
        finally {
            stepper.releaseReset();
            if (blockedReset != null) {
                await(blockedReset);
            }
            closeFromExternalThread(loop, dispatcher, stepper);
        }
    }

    @Test
    public void doesNotFollowUpBlockedResetAfterCloseWins() {
        ManualLifecycleDispatcher dispatcher = new ManualLifecycleDispatcher();
        RecordingStepper stepper = new RecordingStepper(dispatcher);
        ImmediateEdt edt = new ImmediateEdt();
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), edt, dispatcher);
        GraphProjection projection = populatedProjection(416L);
        CompletableFuture<Void> blockedReset = null;
        CompletableFuture<Void> physicalClose = null;
        boolean closeQueued = false;

        try {
            CompletionStage<Void> initial = loop.start(batch(416L), projection,
                ProjectionDiff.between(emptyProjection(415L), projection), state -> { });
            dispatcher.runAll();
            await(initial);

            stepper.blockReset();
            reset(loop);
            blockedReset = runBlockedNext(dispatcher);
            await(stepper.resetEntered());
            stepper.blockClose();
            int closeCommand = dispatcher.enqueueCount() + 1;
            edt.execute(loop::close);
            closeQueued = true;
            stepper.releaseReset();
            await(blockedReset);
            dispatcher.runUntilExecuted(closeCommand - 1);
            physicalClose = runBlockedNext(dispatcher);
            await(stepper.closeEntered());
            stepper.releaseClose();
            await(physicalClose);

            assertThat(stepper.submitCount()).isEqualTo(1);
            assertThat(stepper.closeCount()).isEqualTo(1);
        }
        finally {
            stepper.releaseReset();
            stepper.releaseClose();
            if (blockedReset != null) {
                await(blockedReset);
            }
            if (physicalClose != null) {
                await(physicalClose);
            }
            if (closeQueued) {
                dispatcher.runAll();
            }
            else {
                closeFromExternalThread(loop, dispatcher, stepper);
            }
        }
    }

    @Test
    public void releasesAClaimWhenPauseSupersedesQueuedStartDispatch() {
        ManualLifecycleDispatcher dispatcher = new ManualLifecycleDispatcher();
        RecordingStepper stepper = new RecordingStepper(dispatcher);
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper,
            new GraphGeometryEngine(), new ImmediateEdt(), dispatcher);
        GraphProjection projection = populatedProjection(423L);

        try {
            CompletionStage<Void> completion = loop.start(batch(423L), projection,
                ProjectionDiff.between(emptyProjection(422L), projection), state -> { });
            assertThat(stepper.submitCount()).isZero();
            loop.pause();
            assertThat(stepper.pauseCount()).isZero();
            dispatcher.runAll();
            assertThat(stepper.submitCount()).isZero();
            assertThat(stepper.pauseCount()).isEqualTo(1);

            stepper.prepareProjection(projection);
            stepper.blockStep();
            loop.restart();
            dispatcher.runAll();
            assertThat(stepper.stepCount()).isEqualTo(1);
            assertThat(stepper.operations()).containsExactly("pause", "restart", "step");

            stepper.releaseStep();
            dispatcher.runAll();
            await(completion);
        }
        finally {
            stepper.releaseStep();
            closeFromExternalThread(loop, dispatcher, stepper);
        }
    }

    @Test
    public void doesNotClobberAReentrantStartWhenSupersedingCompletionRuns() {
        ManualLifecycleDispatcher dispatcher = new ManualLifecycleDispatcher();
        RecordingStepper stepper = new RecordingStepper(dispatcher);
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper,
            new GraphGeometryEngine(), new ImmediateEdt(), dispatcher);
        GraphProjection first = populatedProjection(431L);
        GraphProjection second = populatedProjection(432L);
        GraphProjection replacement = populatedProjection(433L);

        try {
            CompletionStage<Void> firstCompletion = loop.start(batch(431L), first,
                ProjectionDiff.between(emptyProjection(430L), first), state -> { });
            firstCompletion.thenRun(() -> loop.start(batch(433L), replacement,
                ProjectionDiff.between(second, replacement), state -> { }));
            loop.start(batch(432L), second, ProjectionDiff.between(first, second), state -> { });
            dispatcher.runAll();

            assertThat(submissionGenerations(stepper)).containsExactly(433L);
        }
        finally {
            closeFromExternalThread(loop, dispatcher, stepper);
        }
    }

    @Test
    public void doesNotClobberAReentrantResetWhenANewerStartSupersedesTheCompletedRun() {
        ManualLifecycleDispatcher dispatcher = new ManualLifecycleDispatcher();
        RecordingStepper stepper = new RecordingStepper(dispatcher);
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper,
            new GraphGeometryEngine(), new ImmediateEdt(), dispatcher);
        GraphProjection first = populatedProjection(451L);
        GraphProjection newer = populatedProjection(452L);
        CompletableFuture<Void> resetRequested = new CompletableFuture<Void>();
        CompletableFuture<CanvasState> resetPublication = new CompletableFuture<CanvasState>();

        try {
            CompletionStage<Void> firstCompletion = loop.start(batch(451L), first,
                ProjectionDiff.between(emptyProjection(450L), first), state -> { });
            firstCompletion.thenRun(() -> {
                resetRequested.complete(null);
                reset(loop);
            });
            CompletionStage<Void> outerStart = loop.start(batch(452L), newer,
                ProjectionDiff.between(first, newer), resetPublication::complete);
            await(resetRequested);
            dispatcher.runAll();
            CanvasState state = await(resetPublication);
            await(outerStart);

            assertThat(state.generation()).isEqualTo(452L);
            assertThat(state.projection()).isSameAs(newer);
            assertThat(submissionGenerations(stepper)).containsExactly(452L);
            assertThat(stepper.resetCount()).isEqualTo(1);
        }
        finally {
            closeFromExternalThread(loop, dispatcher, stepper);
        }
    }

    @Test
    public void doesNotIssueWorkAfterAReentrantCloseDuringSupersedingCompletion() {
        ManualLifecycleDispatcher dispatcher = new ManualLifecycleDispatcher();
        RecordingStepper stepper = new RecordingStepper(dispatcher);
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper,
            new GraphGeometryEngine(), new ImmediateEdt(), dispatcher);
        GraphProjection first = populatedProjection(441L);
        GraphProjection newer = populatedProjection(442L);
        CompletableFuture<Void> physicalClose = null;
        boolean closeQueued = false;

        try {
            CompletionStage<Void> firstCompletion = loop.start(batch(441L), first,
                ProjectionDiff.between(emptyProjection(440L), first), state -> { });
            firstCompletion.thenRun(loop::close);
            stepper.blockClose();
            int closeCommand = dispatcher.enqueueCount() + 1;
            CompletableFuture<CompletionStage<Void>> outerStart = CompletableFuture.supplyAsync(() -> loop.start(
                batch(442L), newer, ProjectionDiff.between(first, newer), state -> { }));
            await(dispatcher.enqueuedAt(closeCommand));
            closeQueued = true;
            dispatcher.runUntilExecuted(closeCommand - 1);
            physicalClose = runBlockedNext(dispatcher);
            await(stepper.closeEntered());
            assertThat(outerStart.isDone()).isFalse();
            stepper.releaseClose();
            await(physicalClose);
            await(outerStart);

            assertThat(stepper.closeCount()).isEqualTo(1);
            assertThat(submissionGenerations(stepper)).isEmpty();
        }
        finally {
            stepper.releaseClose();
            if (physicalClose != null) {
                await(physicalClose);
            }
            if (closeQueued) {
                dispatcher.runAll();
            }
            else {
                closeFromExternalThread(loop, dispatcher, stepper);
            }
        }
    }

    @Test
    public void preservesAReentrantResetFromAnAlreadyCompletedRun() {
        ManualLifecycleDispatcher dispatcher = new ManualLifecycleDispatcher();
        RecordingStepper stepper = new RecordingStepper(dispatcher);
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper,
            new GraphGeometryEngine(), new ImmediateEdt(), dispatcher);
        GraphProjection projection = populatedProjection(461L);
        CompletableFuture<CanvasState> resetPublication = new CompletableFuture<CanvasState>();
        List<CanvasState> publications = new ArrayList<CanvasState>();

        try {
            CompletionStage<Void> completion = loop.start(batch(461L), projection,
                ProjectionDiff.between(emptyProjection(460L), projection), state -> {
                    publications.add(state);
                    if (publications.size() == 2) {
                        resetPublication.complete(state);
                    }
                });
            dispatcher.runAll();
            await(completion);

            completion.thenRun(() -> reset(loop));
            dispatcher.runAll();
            CanvasState state = await(resetPublication);

            assertThat(stepper.resetCount()).isEqualTo(1);
            assertThat(submissionGenerations(stepper)).containsExactly(461L, 461L);
            assertThat(state.generation()).isEqualTo(461L);
        }
        finally {
            closeFromExternalThread(loop, dispatcher, stepper);
        }
    }

    @Test
    public void retainsTheCurrentGenerationWhenPhysicalResetFails() {
        ManualLifecycleDispatcher dispatcher = new ManualLifecycleDispatcher();
        RecordingStepper stepper = new RecordingStepper(dispatcher);
        ImmediateEdt edt = new ImmediateEdt();
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), edt, dispatcher);
        GraphProjection projection = populatedProjection(471L);
        MapReferenceId otherMap = MapReferenceId.of("00000000-0000-0000-0000-000000000103");
        LayoutConflict conflict = LayoutConflict.of(MAP, otherMap, Collections.emptyList());
        PerceptualIdlePolicy.IdleMeasurement idle = new PerceptualIdlePolicy.IdleMeasurement(1.5, 2.5, 7, true);
        LayoutFrame retained = LayoutFrame.withDiagnostics(LayoutFrame.of(9L, positions(projection, 123.5), false),
            Collections.singletonList(conflict), idle);
        CompletableFuture<CanvasState> failedPublication = new CompletableFuture<CanvasState>();

        try {
            CompletionStage<Void> initial = loop.start(batch(471L), projection,
                ProjectionDiff.between(emptyProjection(470L), projection), state -> {
                    if (state.status() == OperationalStatus.FAILED) {
                        failedPublication.complete(state);
                    }
                });
            dispatcher.runAll();
            await(initial);

            stepper.retain(retained);
            stepper.failResetWith(new IllegalStateException("reset failure"));
            reset(loop);
            CompletionStage<Void> replacement = loop.start(batch(471L), projection,
                ProjectionDiff.between(emptyProjection(470L), projection), state -> { });
            dispatcher.runAll();
            CanvasState state = await(failedPublication);
            await(replacement);

            ProjectedNodeKey node = projection.nodes().get(0).key();
            assertThat(state.generation()).isEqualTo(471L);
            assertThat(state.projection()).isSameAs(projection);
            assertThat(state.status()).isEqualTo(OperationalStatus.FAILED);
            assertThat(state.layout().positions().nodes().get(node).x()).isEqualTo(123.5);
            assertThat(state.layout().stepIndex()).isEqualTo(9L);
            assertThat(state.layout().conflicts()).containsExactly(conflict);
            assertThat(state.layout().idle().rms()).isEqualTo(1.5);
            assertThat(state.layout().idle().max()).isEqualTo(2.5);
            assertThat(state.layout().idle().consecutiveStableFrames()).isEqualTo(7);
            assertThat(state.layout().idle().idle()).isTrue();
            assertThat(stepper.lastValidFrameCount()).isEqualTo(1);
            assertThat(stepper.submitCount()).isEqualTo(1);
            assertThat(stepper.restartCount()).isEqualTo(1);
            assertThat(edt.allCallbacksOnEdt).isTrue();
        }
        finally {
            closeFromExternalThread(loop, dispatcher, stepper);
        }
    }

    @Test
    public void returnsFromEdtCloseBeforeABlockedPhysicalCloseAndClosesOnce() {
        ManualLifecycleDispatcher dispatcher = new ManualLifecycleDispatcher();
        RecordingStepper stepper = new RecordingStepper(dispatcher);
        ImmediateEdt edt = new ImmediateEdt();
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper, new GraphGeometryEngine(), edt, dispatcher);
        CompletableFuture<Void> physicalClose = null;
        boolean closeQueued = false;

        try {
            stepper.blockClose();
            int closeCommand = dispatcher.enqueueCount() + 1;
            edt.runOnEdt(loop::close);
            closeQueued = true;
            assertThat(stepper.closeEntered().isDone()).isFalse();
            physicalClose = runBlockedNext(dispatcher);
            await(stepper.closeEntered());
            stepper.releaseClose();
            await(physicalClose);

            assertThat(stepper.closeCount()).isEqualTo(1);
        }
        finally {
            stepper.releaseClose();
            if (physicalClose != null) {
                await(physicalClose);
            }
            if (closeQueued) {
                dispatcher.runAll();
            }
            else {
                closeFromExternalThread(loop, dispatcher, stepper);
            }
        }
    }

    @Test
    public void waitsForAndRethrowsFailedExternalClose() {
        ManualLifecycleDispatcher dispatcher = new ManualLifecycleDispatcher();
        RecordingStepper stepper = new RecordingStepper(dispatcher);
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper,
            new GraphGeometryEngine(), new ImmediateEdt(), dispatcher);
        IllegalStateException closeFailure = new IllegalStateException("close failure");
        CompletableFuture<Void> closeCall = null;
        boolean closeQueued = false;

        try {
            stepper.failCloseWith(closeFailure);
            int closeCommand = dispatcher.enqueueCount() + 1;
            closeCall = CompletableFuture.runAsync(loop::close);
            await(dispatcher.enqueuedAt(closeCommand));
            closeQueued = true;
            assertThat(closeCall.isDone()).isFalse();
            dispatcher.runNext();
            try {
                closeCall.join();
                throw new AssertionError("External close must rethrow the worker failure");
            }
            catch (CompletionException failure) {
                assertThat(failure.getCause()).isSameAs(closeFailure);
            }
        }
        finally {
            if (closeCall != null) {
                try {
                    closeCall.join();
                }
                catch (CompletionException ignored) {
                    // The expected close failure has already been asserted above.
                }
            }
            if (closeQueued) {
                dispatcher.runAll();
            }
            else {
                closeFromExternalThread(loop, dispatcher, stepper);
            }
        }
    }

    @Test
    public void returnsFromLifecycleLaneCloseWithoutWaitingForItsQueuedClose() {
        ManualLifecycleDispatcher dispatcher = new ManualLifecycleDispatcher();
        RecordingStepper stepper = new RecordingStepper(dispatcher);
        LayoutSettleLoop loop = new LayoutSettleLoop(WORKSPACE, stepper,
            new GraphGeometryEngine(), new ImmediateEdt(), dispatcher);
        CompletableFuture<Void> returned = new CompletableFuture<Void>();
        CompletableFuture<Void> physicalClose = null;
        boolean closeQueued = false;

        try {
            stepper.blockClose();
            dispatcher.execute(() -> {
                loop.close();
                returned.complete(null);
            });
            dispatcher.runNext();
            closeQueued = true;
            await(returned);
            assertThat(stepper.closeEntered().isDone()).isFalse();
            physicalClose = runBlockedNext(dispatcher);
            await(stepper.closeEntered());
            assertThat(returned.isDone()).isTrue();
            stepper.releaseClose();
            await(physicalClose);

            assertThat(stepper.closeCount()).isEqualTo(1);
        }
        finally {
            stepper.releaseClose();
            if (physicalClose != null) {
                await(physicalClose);
            }
            if (closeQueued) {
                dispatcher.runAll();
            }
            else {
                closeFromExternalThread(loop, dispatcher, stepper);
            }
        }
    }

    @Test
    public void canvasStateCopiesItsStatusAndReferencesImmutably() {
        GraphProjection projection = emptyProjection(9L);
        CanvasState state = CanvasState.of(9L, projection, frame(0L, true),
            new GraphGeometryEngine().computeHulls(projection, LayoutPositions.of(
                Collections.emptyMap(), Collections.emptyMap())), OperationalStatus.IDLE);

        assertThat(state.generation()).isEqualTo(9L);
        assertThat(state.projection()).isSameAs(projection);
        assertThat(state.layout().positions().nodes()).isEmpty();
        assertThat(state.geometry().nodes()).isEmpty();
        assertThat(state.status()).isEqualTo(OperationalStatus.IDLE);
    }

    private static AcceptedBatch batch(long generation) {
        return new AcceptedBatch(generation, generation, EnumSet.of(ChangeKind.STRUCTURE));
    }

    private static GraphProjection emptyProjection(long generation) {
        return GraphProjection.projected(generation, Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    private static LayoutFrame frame(long index, boolean idle) {
        return frame(index, LayoutPositions.of(Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap()), false, idle);
    }

    private static LayoutFrame frame(GraphProjection projection, long index, boolean idle) {
        return frame(index, positions(projection), false, idle);
    }

    private static LayoutFrame failedFrame(long index, LayoutPositions positions) {
        return frame(index, positions, true, true);
    }

    private static LayoutFrame frame(long index, LayoutPositions positions, boolean failed, boolean idle) {
        return LayoutFrame.withDiagnostics(LayoutFrame.of(index, positions, failed),
            Collections.emptyList(), new PerceptualIdlePolicy.IdleMeasurement(0.0, 0.0,
                idle ? 8 : 1, idle));
    }

    private static LayoutPositions positions(GraphProjection projection) {
        return positions(projection, 0.0);
    }

    private static LayoutPositions positions(GraphProjection projection, double xOffset) {
        Map<ProjectedNodeKey, LayoutPoint> nodes = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        int nodeIndex = 0;
        for (ProjectedNode node : projection.nodes()) {
            nodes.put(node.key(), LayoutPoint.of(xOffset + nodeIndex++ * 32.0, 0.0));
        }
        Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        int enclosureIndex = 0;
        for (ProjectedEnclosure enclosure : projection.enclosures()) {
            anchors.put(enclosure.hullKey(), LayoutPoint.of(xOffset + enclosureIndex++ * 32.0, 0.0));
        }
        return LayoutPositions.of(nodes, anchors);
    }

    private static List<ProjectedNodeKey> nodeKeys(GraphProjection projection) {
        List<ProjectedNodeKey> keys = new ArrayList<ProjectedNodeKey>();
        for (ProjectedNode node : projection.nodes()) {
            keys.add(node.key());
        }
        return keys;
    }

    private static List<EnclosureHullKey> enclosureKeys(GraphProjection projection) {
        List<EnclosureHullKey> keys = new ArrayList<EnclosureHullKey>();
        for (ProjectedEnclosure enclosure : projection.enclosures()) {
            keys.add(enclosure.hullKey());
        }
        return keys;
    }

    private static GraphProjection populatedProjection(long generation) {
        ProjectedNodeKey nodeKey = ProjectedNodeKey.of(SourceNodeKey.persisted(reference("node-" + generation)));
        ProjectedNode node = ProjectedNode.of(nodeKey, SafeNodeLabel.of("Node", "Node"), "Map", false);
        EnclosureKey endpoint = EnclosureKey.of(SourceNodeKey.persisted(reference("hull-" + generation)));
        EnclosureHullKey hullKey = EnclosureHullKey.of(Collections.singletonList(endpoint));
        ProjectedEnclosure enclosure = ProjectedEnclosure.of(hullKey, Collections.singletonList(endpoint),
            Collections.singletonList(SafeNodeLabel.of("Map", "Map")), "Map", Optional.empty(),
            Collections.singletonList(nodeKey), Collections.emptyList(), true, BoundaryTier.SUBTLE);
        return GraphProjection.projected(generation, Collections.singletonList(node),
            Collections.singletonList(enclosure), Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList());
    }

    private static NodeReference reference(String id) {
        return NodeReference.of(MAP, PersistedNodeId.of(id));
    }

    private static void reset(LayoutSettleLoop loop) {
        loop.reset();
    }

    private static <T> T await(CompletionStage<T> stage) {
        try {
            return stage.toCompletableFuture().get(5L, TimeUnit.SECONDS);
        }
        catch (Exception exception) {
            throw new AssertionError("Timed out waiting for layout settlement", exception);
        }
    }

    static CompletableFuture<Void> runBlockedNext(final ManualLifecycleDispatcher dispatcher) {
        return CompletableFuture.runAsync(dispatcher::runNext);
    }

    private static List<Long> submissionGenerations(final RecordingStepper stepper) {
        List<Long> generations = new ArrayList<Long>();
        for (org.freeplane.plugin.graph.layout.LayoutRequest request : stepper.submissions()) {
            generations.add(request.projection().generation());
        }
        return generations;
    }

    static void closeFromExternalThread(final LayoutSettleLoop loop,
            final ManualLifecycleDispatcher dispatcher, final RecordingStepper stepper) {
        stepper.blockClose();
        final int closeCommand = dispatcher.enqueueCount() + 1;
        final CompletableFuture<Void> closeCall = CompletableFuture.runAsync(loop::close);
        await(dispatcher.enqueuedAt(closeCommand));
        dispatcher.runUntilExecuted(closeCommand - 1);
        final CompletableFuture<Void> physicalClose = runBlockedNext(dispatcher);
        await(stepper.closeEntered);
        assertThat(closeCall.isDone()).isFalse();
        stepper.releaseClose();
        await(physicalClose);
        await(closeCall);
    }

    private static final class TestStepper implements LayoutSettleLoop.FrameStepper {
        private final Deque<LayoutFrame> frames = new ArrayDeque<LayoutFrame>();
        private int submitCount;
        private int stepCount;

        private TestStepper(LayoutFrame... values) {
            Collections.addAll(frames, values);
        }

        @Override
        public CompletionStage<LayoutFrame> submit(org.freeplane.plugin.graph.layout.LayoutRequest request) {
            submitCount++;
            return CompletableFuture.completedFuture(frames.removeFirst());
        }

        @Override
        public CompletionStage<LayoutFrame> step() {
            stepCount++;
            return CompletableFuture.completedFuture(frames.removeFirst());
        }

        @Override
        public void pause() {
        }

        @Override
        public void restart() {
        }

        @Override
        public LayoutFrame lastValidFrame() {
            return frame(0L, true);
        }

        @Override
        public void close() {
        }
    }

    private static final class ResettableStepper implements LayoutSettleLoop.FrameStepper {
        private int seed;

        @Override
        public CompletionStage<LayoutFrame> submit(org.freeplane.plugin.graph.layout.LayoutRequest request) {
            LayoutFrame frame = frame(0L, positions(request.projection(), seed++ * 64.0), false, true);
            return CompletableFuture.completedFuture(frame);
        }

        @Override
        public CompletionStage<LayoutFrame> step() {
            throw new AssertionError("Idle reset run must not request another frame");
        }

        @Override
        public void pause() {
        }

        @Override
        public void restart() {
            // A healthy worker retains its existing engine state on restart.
        }

        @Override
        public void reset() {
            seed = 0;
        }

        @Override
        public LayoutFrame lastValidFrame() {
            return frame(0L, true);
        }

        @Override
        public void close() {
        }
    }

    private static final class ControlledStepper implements LayoutSettleLoop.FrameStepper {
        private final CompletableFuture<LayoutFrame> first = new CompletableFuture<LayoutFrame>();
        private final CompletableFuture<LayoutFrame> second = new CompletableFuture<LayoutFrame>();
        private final CompletableFuture<Void> submitEntered = new CompletableFuture<Void>();
        private final CompletableFuture<Void> stepCalled = new CompletableFuture<Void>();
        private int submissions;
        private int stepCount;
        private boolean allowStep;

        @Override
        public CompletionStage<LayoutFrame> submit(org.freeplane.plugin.graph.layout.LayoutRequest request) {
            submissions++;
            submitEntered.complete(null);
            return submissions == 1 ? first : second;
        }

        @Override
        public CompletionStage<LayoutFrame> step() {
            stepCount++;
            stepCalled.complete(null);
            if (!allowStep) {
                throw new AssertionError("stale run must not step");
            }
            return second;
        }

        @Override
        public void pause() {
        }

        @Override
        public void restart() {
        }

        @Override
        public LayoutFrame lastValidFrame() {
            return frame(0L, true);
        }

        @Override
        public void close() {
        }
    }

    private static final class StickyPauseStepper implements LayoutSettleLoop.FrameStepper {
        private final CompletableFuture<Void> firstSubmitEntered = new CompletableFuture<Void>();
        private final CompletableFuture<Void> pauseObserved = new CompletableFuture<Void>();
        private final CompletableFuture<Void> pausedStepBlocked = new CompletableFuture<Void>();
        private final CompletableFuture<LayoutFrame> firstFrame = new CompletableFuture<LayoutFrame>();
        private final CompletableFuture<LayoutFrame> pausedFrame = new CompletableFuture<LayoutFrame>();
        private volatile boolean physicallyPaused;
        private volatile boolean restartAfterPause;
        private volatile GraphProjection firstProjection;
        private volatile GraphProjection currentProjection;
        private volatile LayoutFrame lastValidFrame;

        @Override
        public CompletionStage<LayoutFrame> submit(final org.freeplane.plugin.graph.layout.LayoutRequest request) {
            if (request.projection().generation() == 401L) {
                firstProjection = request.projection();
                firstSubmitEntered.complete(null);
                return firstFrame;
            }
            currentProjection = request.projection();
            LayoutFrame settling = frame(currentProjection, 0L, false);
            lastValidFrame = settling;
            return CompletableFuture.completedFuture(settling);
        }

        @Override
        public CompletionStage<LayoutFrame> step() {
            if (physicallyPaused) {
                pausedStepBlocked.complete(null);
                return pausedFrame;
            }
            LayoutFrame idle = frame(currentProjection, 1L, true);
            lastValidFrame = idle;
            return CompletableFuture.completedFuture(idle);
        }

        @Override
        public void pause() {
            physicallyPaused = true;
            pauseObserved.complete(null);
        }

        @Override
        public void restart() {
            restartAfterPause = physicallyPaused;
            physicallyPaused = false;
        }

        @Override
        public LayoutFrame lastValidFrame() {
            return lastValidFrame == null ? frame(0L, true) : lastValidFrame;
        }

        @Override
        public void close() {
            releaseFirstFrame();
        }

        private void releaseFirstFrame() {
            if (firstProjection != null) {
                firstFrame.complete(frame(firstProjection, 0L, true));
            }
            if (currentProjection != null) {
                pausedFrame.complete(frame(currentProjection, 1L, true));
            }
        }
    }

    private static final class BlockingResetStepper implements LayoutSettleLoop.FrameStepper {
        private final CompletableFuture<Void> resetEntered = new CompletableFuture<Void>();
        private final CompletableFuture<Void> resetRelease = new CompletableFuture<Void>();
        private volatile boolean newerSubmittedBeforeResetRelease;
        private volatile LayoutFrame lastValidFrame;

        @Override
        public CompletionStage<LayoutFrame> submit(final org.freeplane.plugin.graph.layout.LayoutRequest request) {
            if (request.projection().generation() == 412L && !resetRelease.isDone()) {
                newerSubmittedBeforeResetRelease = true;
            }
            LayoutFrame frame = frame(request.projection(), 0L, true);
            lastValidFrame = frame;
            return CompletableFuture.completedFuture(frame);
        }

        @Override
        public CompletionStage<LayoutFrame> step() {
            throw new AssertionError("Idle reset run must not request another frame");
        }

        @Override
        public void pause() {
        }

        @Override
        public void restart() {
        }

        @Override
        public void reset() {
            resetEntered.complete(null);
            await(resetRelease);
        }

        @Override
        public LayoutFrame lastValidFrame() {
            return lastValidFrame == null ? frame(0L, true) : lastValidFrame;
        }

        @Override
        public void close() {
            releaseReset();
        }

        private void releaseReset() {
            resetRelease.complete(null);
        }
    }

    static final class ManualLifecycleDispatcher implements LayoutSettleLoop.LifecycleDispatcher {
        private final Deque<Runnable> commands = new ArrayDeque<Runnable>();
        private final List<EnqueueBarrier> enqueueBarriers = new ArrayList<EnqueueBarrier>();
        private final ThreadLocal<Boolean> lifecycleThread = new ThreadLocal<Boolean>();
        private int enqueueCount;
        private int executionCount;
        private boolean shutdown;

        @Override
        public void execute(final Runnable command) {
            synchronized (this) {
                if (shutdown) {
                    throw new RejectedExecutionException("Manual lifecycle dispatcher is shut down");
                }
                commands.addLast(command);
                ++enqueueCount;
                for (int index = enqueueBarriers.size() - 1; index >= 0; index--) {
                    EnqueueBarrier barrier = enqueueBarriers.get(index);
                    if (enqueueCount >= barrier.targetCount) {
                        barrier.reached.complete(null);
                        enqueueBarriers.remove(index);
                    }
                }
            }
        }

        @Override
        public boolean isLifecycleThread() {
            return Boolean.TRUE.equals(lifecycleThread.get());
        }

        @Override
        public synchronized void shutdown() {
            shutdown = true;
        }

        synchronized int enqueueCount() {
            return enqueueCount;
        }

        synchronized CompletableFuture<Void> enqueuedAt(final int targetCount) {
            CompletableFuture<Void> reached = new CompletableFuture<Void>();
            if (enqueueCount >= targetCount) {
                reached.complete(null);
            }
            else {
                enqueueBarriers.add(new EnqueueBarrier(targetCount, reached));
            }
            return reached;
        }

        void runNext() {
            final Runnable command = removeNext();
            if (command == null) {
                throw new AssertionError("No queued lifecycle command");
            }
            run(command);
        }

        void runAll() {
            for (;;) {
                final Runnable command = removeNext();
                if (command == null) {
                    return;
                }
                run(command);
            }
        }

        void runUntilExecuted(final int targetCount) {
            for (;;) {
                synchronized (this) {
                    if (executionCount >= targetCount) {
                        return;
                    }
                }
                runNext();
            }
        }

        private synchronized Runnable removeNext() {
            Runnable command = commands.pollFirst();
            if (command != null) {
                ++executionCount;
            }
            return command;
        }

        private void run(final Runnable command) {
            final Boolean previous = lifecycleThread.get();
            lifecycleThread.set(Boolean.TRUE);
            try {
                command.run();
            }
            finally {
                if (previous == null) {
                    lifecycleThread.remove();
                }
                else {
                    lifecycleThread.set(previous);
                }
            }
        }

        private static final class EnqueueBarrier {
            private final int targetCount;
            private final CompletableFuture<Void> reached;

            private EnqueueBarrier(final int targetCount, final CompletableFuture<Void> reached) {
                this.targetCount = targetCount;
                this.reached = reached;
            }
        }
    }

    static final class RecordingStepper implements LayoutSettleLoop.FrameStepper {
        private final ManualLifecycleDispatcher dispatcher;
        private final List<String> operations = new ArrayList<String>();
        private final List<org.freeplane.plugin.graph.layout.LayoutRequest> submissions =
            new ArrayList<org.freeplane.plugin.graph.layout.LayoutRequest>();
        private final CompletableFuture<Void> submitEntered = new CompletableFuture<Void>();
        private final CompletableFuture<Void> resetEntered = new CompletableFuture<Void>();
        private final CompletableFuture<Void> closeEntered = new CompletableFuture<Void>();
        private final CompletableFuture<Void> resetRelease = new CompletableFuture<Void>();
        private final CompletableFuture<Void> closeRelease = new CompletableFuture<Void>();
        private final CompletableFuture<LayoutFrame> heldStep = new CompletableFuture<LayoutFrame>();
        private boolean physicalOperationInProgress;
        private boolean resetBlocked;
        private boolean closeBlocked;
        private boolean stepBlocked;
        private IllegalStateException resetFailure;
        private IllegalStateException closeFailure;
        private GraphProjection lastProjection;
        private LayoutFrame retainedFrame;
        private int submitCount;
        private int stepCount;
        private int pauseCount;
        private int restartCount;
        private int resetCount;
        private int lastValidFrameCount;
        private int closeCount;

        RecordingStepper(final ManualLifecycleDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public CompletionStage<LayoutFrame> submit(final org.freeplane.plugin.graph.layout.LayoutRequest request) {
            begin("submit");
            try {
                ++submitCount;
                submissions.add(request);
                lastProjection = request.projection();
                submitEntered.complete(null);
                retainedFrame = frame(lastProjection, submitCount, true);
                return CompletableFuture.completedFuture(retainedFrame);
            }
            finally {
                end();
            }
        }

        @Override
        public CompletionStage<LayoutFrame> step() {
            begin("step");
            try {
                ++stepCount;
                if (stepBlocked) {
                    return heldStep;
                }
                LayoutFrame frame = frame(lastProjection, stepCount, true);
                retainedFrame = frame;
                return CompletableFuture.completedFuture(frame);
            }
            finally {
                end();
            }
        }

        @Override
        public void pause() {
            begin("pause");
            try {
                ++pauseCount;
            }
            finally {
                end();
            }
        }

        @Override
        public void restart() {
            begin("restart");
            try {
                ++restartCount;
            }
            finally {
                end();
            }
        }

        @Override
        public void reset() {
            begin("reset");
            try {
                ++resetCount;
                resetEntered.complete(null);
                if (resetBlocked) {
                    await(resetRelease);
                }
                if (resetFailure != null) {
                    throw resetFailure;
                }
            }
            finally {
                end();
            }
        }

        @Override
        public LayoutFrame lastValidFrame() {
            begin("lastValidFrame");
            try {
                ++lastValidFrameCount;
                return retainedFrame == null ? frame(0L, true) : retainedFrame;
            }
            finally {
                end();
            }
        }

        @Override
        public void close() {
            begin("close");
            try {
                ++closeCount;
                closeEntered.complete(null);
                if (closeBlocked) {
                    await(closeRelease);
                }
                if (closeFailure != null) {
                    throw closeFailure;
                }
            }
            finally {
                end();
            }
        }

        synchronized void blockStep() {
            stepBlocked = true;
        }

        void releaseStep() {
            final GraphProjection projection;
            synchronized (this) {
                projection = lastProjection;
            }
            heldStep.complete(frame(projection, stepCount, true));
        }

        synchronized void blockReset() {
            resetBlocked = true;
        }

        void releaseReset() {
            resetRelease.complete(null);
        }

        synchronized void blockClose() {
            closeBlocked = true;
        }

        void releaseClose() {
            closeRelease.complete(null);
        }

        synchronized void failResetWith(final IllegalStateException failure) {
            resetFailure = failure;
        }

        synchronized void failCloseWith(final IllegalStateException failure) {
            closeFailure = failure;
        }

        synchronized void retain(final LayoutFrame frame) {
            retainedFrame = frame;
        }

        synchronized void prepareProjection(final GraphProjection projection) {
            lastProjection = projection;
        }

        synchronized List<String> operations() {
            return new ArrayList<String>(operations);
        }

        synchronized int submitCount() {
            return submitCount;
        }

        synchronized int stepCount() {
            return stepCount;
        }

        synchronized int pauseCount() {
            return pauseCount;
        }

        synchronized int restartCount() {
            return restartCount;
        }

        synchronized int resetCount() {
            return resetCount;
        }

        synchronized int lastValidFrameCount() {
            return lastValidFrameCount;
        }

        synchronized int closeCount() {
            return closeCount;
        }

        synchronized List<org.freeplane.plugin.graph.layout.LayoutRequest> submissions() {
            return new ArrayList<org.freeplane.plugin.graph.layout.LayoutRequest>(submissions);
        }

        CompletableFuture<Void> resetEntered() {
            return resetEntered;
        }

        CompletableFuture<Void> closeEntered() {
            return closeEntered;
        }

        private void begin(final String operation) {
            if (!dispatcher.isLifecycleThread()) {
                throw new AssertionError("FrameStepper." + operation + " must execute on the lifecycle lane");
            }
            synchronized (this) {
                if (physicalOperationInProgress) {
                    throw new AssertionError("Overlapping FrameStepper operation: " + operation);
                }
                physicalOperationInProgress = true;
                operations.add(operation);
            }
        }

        private synchronized void end() {
            physicalOperationInProgress = false;
        }
    }

    private static final class ImmediateEdt implements EdtExecutor {
        private boolean edt;
        private int callbackCount;
        private boolean allCallbacksOnEdt = true;

        @Override
        public <T> T call(java.util.concurrent.Callable<T> task) {
            try {
                return task.call();
            }
            catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public void execute(Runnable task) {
            boolean previous = edt;
            edt = true;
            callbackCount++;
            try {
                task.run();
            }
            catch (RuntimeException failure) {
                allCallbacksOnEdt = false;
                throw failure;
            }
            finally {
                edt = previous;
            }
        }

        @Override
        public boolean isEdt() {
            return edt;
        }

        private void runOnEdt(final Runnable task) {
            boolean previous = edt;
            edt = true;
            try {
                task.run();
            }
            finally {
                edt = previous;
            }
        }
    }

    private static final class QueuedEdt implements EdtExecutor {
        private final Deque<Runnable> queued = new ArrayDeque<Runnable>();
        private final CompletableFuture<Void> executed = new CompletableFuture<Void>();
        private final CompletableFuture<Void> secondExecuted = new CompletableFuture<Void>();
        private int executionCount;
        private boolean edt;

        @Override
        public <T> T call(java.util.concurrent.Callable<T> task) {
            try {
                return task.call();
            }
            catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public void execute(Runnable task) {
            synchronized (this) {
                executionCount++;
                if (executionCount == 1) {
                    executed.complete(null);
                }
                else if (executionCount == 2) {
                    secondExecuted.complete(null);
                }
                if (edt) {
                    task.run();
                }
                else {
                    queued.add(task);
                }
            }
        }

        @Override
        public synchronized boolean isEdt() {
            return edt;
        }

        private void runOnEdt(Runnable task) {
            synchronized (this) {
                boolean previous = edt;
                edt = true;
                try {
                    task.run();
                }
                finally {
                    edt = previous;
                }
            }
        }

        private void runQueued() {
            for (;;) {
                final Runnable task;
                synchronized (this) {
                    task = queued.pollFirst();
                }
                if (task == null) {
                    return;
                }
                runOnEdt(task);
            }
        }
    }

    private static final class RecordingMetrics implements GeometryTextMetrics {
        private final QueuedEdt edt;
        private final CompletableFuture<Boolean> measuredOnEdt = new CompletableFuture<Boolean>();

        private RecordingMetrics(QueuedEdt edt) {
            this.edt = edt;
        }

        @Override
        public Dimension2D measure(String displayText, BoundaryTier tier) {
            measuredOnEdt.complete(edt.isEdt());
            return new Dimension(10, 10);
        }
    }

    private static final class StageHolder {
        private CompletionStage<Void> value;
    }
}
