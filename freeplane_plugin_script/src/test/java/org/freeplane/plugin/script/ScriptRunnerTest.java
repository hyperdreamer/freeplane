package org.freeplane.plugin.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicReference;
import org.freeplane.features.map.NodeModel;
import org.junit.Test;

public class ScriptRunnerTest {

    @Test
    public void nonEditorScriptCallbackWritesToOriginatingOutputStreamWithoutAttachingEditor() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream outputStream = new PrintStream(output, true, "UTF-8");
        AtomicReference<ScriptContext> seenContext = new AtomicReference<ScriptContext>();
        IScript script = new RecordingScript(seenContext);

        new ScriptRunner(script)
            .setOutStream(outputStream)
            .execute(mock(NodeModel.class));

        assertThat(seenContext.get().getCallbackOutputStream()).isSameAs(outputStream);
    }

    private static class RecordingScript implements IScript {
        private final AtomicReference<ScriptContext> seenContext;

        private RecordingScript(AtomicReference<ScriptContext> seenContext) {
            this.seenContext = seenContext;
        }

        @Override
        public Object execute(NodeModel node,
                              PrintStream outStream,
                              IFreeplaneScriptErrorHandler pErrorHandler,
                              ScriptContext scriptContext) {
            seenContext.set(scriptContext);
            return null;
        }

        @Override
        public boolean hasPermissions(ScriptingPermissions permissions) {
            return true;
        }
    }
}
