package org.freeplane.plugin.script;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class ScriptSecurityTest {

    @Test
    public void trustedSignedScriptsReceivePermissiveAiRequestPermission() {
        ScriptSecurity scriptSecurity = new ScriptSecurity(
            "return 1",
            trustedSignedScriptPermissions(),
            output(),
            (script, outStream) -> true);

        ScriptingPermissions effectivePermissions = scriptSecurity.getEffectivePermissions();

        assertThat(effectivePermissions)
            .isSameAs(ScriptingPermissions.getPermissiveScriptingPermissions());
        assertThat(effectivePermissions
            .get(ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_AI_REQUEST_RESTRICTION)).isTrue();
    }

    @Test
    public void unsignedScriptsKeepRestrictedAiRequestPermission() {
        ScriptSecurity scriptSecurity = new ScriptSecurity(
            "return 1",
            trustedSignedScriptPermissions(),
            output(),
            (script, outStream) -> false);

        ScriptingPermissions effectivePermissions = scriptSecurity.getEffectivePermissions();

        assertThat(effectivePermissions)
            .isNotSameAs(ScriptingPermissions.getPermissiveScriptingPermissions());
        assertThat(effectivePermissions
            .get(ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_AI_REQUEST_RESTRICTION)).isFalse();
    }

    private ScriptingPermissions trustedSignedScriptPermissions() {
        Map<String, Boolean> permissions = new HashMap<String, Boolean>();
        permissions.put(ScriptingPermissions.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_ASKING, true);
        permissions.put(ScriptingPermissions.RESOURCES_SIGNED_SCRIPT_ARE_TRUSTED, true);
        return new ScriptingPermissions(permissions);
    }

    private PrintStream output() {
        return new PrintStream(new ByteArrayOutputStream());
    }
}
