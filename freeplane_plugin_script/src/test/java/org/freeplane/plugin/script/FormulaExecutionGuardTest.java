package org.freeplane.plugin.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class FormulaExecutionGuardTest {

    @Test
    public void executeBlockingHelperRunsDirectlyWithoutCurrentController() {
        try (MockedStatic<Controller> controllerMock = Mockito.mockStatic(Controller.class)) {
            controllerMock.when(Controller::getCurrentController).thenReturn(null);
            AtomicBoolean supplierRan = new AtomicBoolean(false);

            String result = FormulaUtils.callWithExecuteBlockedIfEnabled(() -> {
                supplierRan.set(true);
                return "ok";
            });

            assertThat(result).isEqualTo("ok");
            assertThat(supplierRan).isTrue();
        }
    }

    @Test
    public void executeBlockingHelperRunsDirectlyWhenPreferenceIsDisabled() {
        Controller controller = mock(Controller.class);
        ResourceController resourceController = mock(ResourceController.class);
        MModeController modeController = mock(MModeController.class);
        when(controller.getResourceController()).thenReturn(resourceController);
        when(controller.getModeController()).thenReturn(modeController);
        when(resourceController.getBooleanProperty(FormulaUtils.FORMULA_BLOCK_MODE_CONTROLLER_EXECUTE, true)).thenReturn(false);

        try (MockedStatic<Controller> controllerMock = Mockito.mockStatic(Controller.class)) {
            controllerMock.when(Controller::getCurrentController).thenReturn(controller);
            AtomicBoolean supplierRan = new AtomicBoolean(false);

            String result = FormulaUtils.callWithExecuteBlockedIfEnabled(() -> {
                supplierRan.set(true);
                return "ok";
            });

            assertThat(result).isEqualTo("ok");
            assertThat(supplierRan).isTrue();
            verify(modeController, never()).callWithExecuteBlocked(any());
        }
    }

    @Test
    public void executeBlockingHelperDelegatesToCurrentModeControllerWhenPreferenceIsEnabled() {
        Controller controller = mock(Controller.class);
        ResourceController resourceController = mock(ResourceController.class);
        MModeController modeController = mock(MModeController.class);
        when(controller.getResourceController()).thenReturn(resourceController);
        when(controller.getModeController()).thenReturn(modeController);
        when(resourceController.getBooleanProperty(FormulaUtils.FORMULA_BLOCK_MODE_CONTROLLER_EXECUTE, true)).thenReturn(true);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<String> supplier = (Supplier<String>) invocation.getArgument(0);
            return supplier.get();
        }).when(modeController).callWithExecuteBlocked(any());

        try (MockedStatic<Controller> controllerMock = Mockito.mockStatic(Controller.class)) {
            controllerMock.when(Controller::getCurrentController).thenReturn(controller);
            AtomicBoolean supplierRan = new AtomicBoolean(false);

            String result = FormulaUtils.callWithExecuteBlockedIfEnabled(() -> {
                supplierRan.set(true);
                return "ok";
            });

            assertThat(result).isEqualTo("ok");
            assertThat(supplierRan).isTrue();
            verify(modeController).callWithExecuteBlocked(any());
        }
    }
}
