package org.freeplane.plugin.script;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.Test;

public class ScriptingPermissionsTest {

    @Test
    public void permissivePermissionsIncludeAiRequestPermission() {
        assertThat(ScriptingPermissions.getPermissiveScriptingPermissions()
            .get(ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_AI_REQUEST_RESTRICTION)).isTrue();
    }

    @Test
    public void legacyAddOnPermissionInputsDoNotRequireAiAttributeAndDefaultItToDenied() {
        Properties properties = new Properties();
        properties.setProperty(ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_ASKING, "true");

        ScriptingPermissions permissions = new ScriptingPermissions(properties);

        assertThat(ScriptingPermissions.getRequiredExecutionPermissionNames())
            .doesNotContain(ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_AI_REQUEST_RESTRICTION);
        assertThat(ScriptingPermissions.getPermissionNames())
            .contains(ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_AI_REQUEST_RESTRICTION);
        assertThat(permissions.get(ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_AI_REQUEST_RESTRICTION))
            .isFalse();
    }
}
