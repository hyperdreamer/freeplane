package org.freeplane.features.mode.mindmapmode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicBoolean;

import org.freeplane.core.resources.ResourceBundles;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.undo.IActor;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.ui.ViewController;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MModeControllerExecuteBlockingTest {
    private Controller previousController;

    @Before
    public void rememberController() {
        previousController = Controller.getCurrentController();
    }

    @After
    public void restoreController() {
        clearThreadController();
        Controller.setCurrentController(previousController);
    }

    @Test
    public void callWithExecuteBlockedRunsSupplierAndReturnsResult() {
        clearThreadController();
        Controller.setCurrentController(null);
        ResourceController resourceController = mock(ResourceController.class);
        ResourceBundles resourceBundles = mock(ResourceBundles.class);
        doAnswer(invocation -> invocation.getArgument(0)).when(resourceBundles)
            .getResourceString(org.mockito.ArgumentMatchers.anyString());
        doAnswer(invocation -> invocation.getArgument(1)).when(resourceBundles)
            .getResourceString(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        when(resourceController.getResources()).thenReturn(resourceBundles);
        MModeController modeController = new MModeController(new Controller(resourceController)) {
            @Override
			void createActions() {/**/}
        };

        AtomicBoolean runnableRan = new AtomicBoolean(false);

        modeController.callWithExecuteBlocked(() -> runnableRan.getAndSet(true));

        assertThat(runnableRan).isTrue();
        assertThat(modeController.callWithExecuteBlocked(() -> "value")).isEqualTo("value");
    }

    @Test
    public void executeIsBlockedInsideNestedScopeAndRestoredAfterSuccess() throws Exception {
        TestEnvironment environment = new TestEnvironment();
        try {
            AtomicBoolean acted = new AtomicBoolean(false);
            IActor actor = actor(acted, true);

            assertThatThrownBy(() -> environment.modeController.callWithExecuteBlocked(() -> environment.modeController
                .callWithExecuteBlocked(() -> {
                    environment.modeController.execute(actor, environment.map);
                    return null;
                })))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Mode-controller execute calls are blocked during formula evaluation.");
            assertThat(acted).isFalse();

            environment.modeController.execute(actor, environment.map);
            assertThat(acted).isTrue();
        }
        finally {
            environment.close();
        }
    }

    @Test
    public void executeScopeIsRestoredAfterFailure() throws Exception {
        TestEnvironment environment = new TestEnvironment();
        try {
            assertThatThrownBy(() -> environment.modeController.callWithExecuteBlocked(() -> {
                throw new RuntimeException("boom");
            }))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");

            AtomicBoolean acted = new AtomicBoolean(false);
            environment.modeController.execute(actor(acted, false), environment.map);
            assertThat(acted).isTrue();
        }
        finally {
            environment.close();
        }
    }

    private IActor actor(AtomicBoolean acted, boolean readonly) {
        return new IActor() {
            @Override
            public void act() {
                acted.set(true);
            }

            @Override
            public String getDescription() {
                return "test";
            }

            @Override
            public void undo() {
            }

            @Override
            public boolean isReadonly() {
                return readonly;
            }
        };
    }

    private void clearThreadController() {
        try {
            java.lang.reflect.Field field = Controller.class.getDeclaredField("threadController");
            field.setAccessible(true);
            ThreadLocal<?> threadController = (ThreadLocal<?>) field.get(null);
            if (threadController != null) {
                threadController.remove();
            }
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private class TestEnvironment implements AutoCloseable {
        private final Controller previousController;
        private final Controller controller;
        private final MModeController modeController;
        private final MapModel map;

        private TestEnvironment() throws Exception {
            previousController = Controller.getCurrentController();
            MModeControllerExecuteBlockingTest.this.clearThreadController();
            Controller.setCurrentController(null);
            ResourceController resourceController = mock(ResourceController.class);
            ResourceBundles resourceBundles = mock(ResourceBundles.class);
            doAnswer(invocation -> invocation.getArgument(0)).when(resourceBundles)
                .getResourceString(org.mockito.ArgumentMatchers.anyString());
            doAnswer(invocation -> invocation.getArgument(1)).when(resourceBundles)
                .getResourceString(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
            org.mockito.Mockito.when(resourceController.getResources()).thenReturn(resourceBundles);
            controller = new Controller(resourceController);
            Controller.setCurrentController(controller);
            controller.setMapViewManager(mock(org.freeplane.features.ui.IMapViewManager.class));
            ViewController viewController = mock(ViewController.class);
            doAnswer(invocation -> {
                ((Runnable) invocation.getArgument(0)).run();
                return null;
            }).when(viewController).invokeAndWait(org.mockito.ArgumentMatchers.any(Runnable.class));
            controller.setViewController(viewController);
            modeController = new MModeController(controller);
            controller.selectModeForBuild(modeController);
            map = new MapModel((source, targetMap, withChildren) -> null, null, null);
        }

        @Override
        public void close() {
            MModeControllerExecuteBlockingTest.this.clearThreadController();
            Controller.setCurrentController(previousController);
        }
    }
}
