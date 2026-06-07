package org.freeplane.plugin.script.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.freeplane.plugin.script.ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_AI_REQUEST_RESTRICTION;

import java.lang.reflect.Field;
import java.util.Map;
import org.junit.Test;

public class ScriptProxyPermissionTest {

    @Test
    public void accessingAiEnablesAiRequestPermission() throws Exception {
        StringScriptProxy scriptProxy = new StringScriptProxy("return 1", "groovy", null);

        scriptProxy.accessingAi();

        assertThat(permissionsOf(scriptProxy).get(RESOURCES_EXECUTE_SCRIPTS_WITHOUT_AI_REQUEST_RESTRICTION))
            .isTrue();
    }

    @Test
    public void withAllPermissionsIncludesAiRequestPermission() throws Exception {
        StringScriptProxy scriptProxy = new StringScriptProxy("return 1", "groovy", null);

        scriptProxy.withAllPermissions();

        assertThat(permissionsOf(scriptProxy).get(RESOURCES_EXECUTE_SCRIPTS_WITHOUT_AI_REQUEST_RESTRICTION))
            .isTrue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Boolean> permissionsOf(ScriptProxy scriptProxy) throws Exception {
        Field field = ScriptProxy.class.getDeclaredField("permissions");
        field.setAccessible(true);
        return (Map<String, Boolean>) field.get(scriptProxy);
    }
}
