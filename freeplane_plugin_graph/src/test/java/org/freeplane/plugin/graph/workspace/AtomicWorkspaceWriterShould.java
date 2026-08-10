package org.freeplane.plugin.graph.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AtomicWorkspaceWriterShould {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void replaceTargetBytesAndLeaveNoTemporaryFile() throws Exception {
        Path directory = temporaryFolder.newFolder("atomic").toPath();
        Path target = directory.resolve("workspace.fpg");
        Files.write(target, bytes("before"));

        new DefaultAtomicWorkspaceWriter().write(target, bytes("after"));

        assertThat(Files.readAllBytes(target)).isEqualTo(bytes("after"));
        assertThat(entries(directory)).containsExactly(target.getFileName());
    }

    @Test
    public void atomicFailureRetainsPreviousFile() throws Exception {
        Path directory = temporaryFolder.newFolder("atomic-failure").toPath();
        Path target = directory.resolve("workspace.fpg");
        byte[] previous = bytes("previous");
        Files.write(target, previous);
        final IOException moveFailure = new IOException("injected move failure");
        AtomicWorkspaceWriter writer = new DefaultAtomicWorkspaceWriter(new MoveOperation() {
            @Override
            public void move(Path source, Path destination) throws IOException {
                throw moveFailure;
            }
        });

        Throwable thrown = catchThrowable(() -> writer.write(target, bytes("replacement")));
        assertThat(thrown)
            .isInstanceOf(WorkspaceSaveException.class)
            .hasMessageContaining(target.toString());
        assertThat(thrown.getCause()).isSameAs(moveFailure);

        assertThat(Files.readAllBytes(target)).isEqualTo(previous);
        assertThat(entries(directory)).containsExactly(target.getFileName());
    }

    @Test
    public void rejectNullTargetBeforeWriting() {
        assertThatThrownBy(() -> new DefaultAtomicWorkspaceWriter().write(null, bytes("value")))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void rejectNullBytesBeforeWriting() throws Exception {
        Path target = temporaryFolder.newFolder("null-bytes").toPath().resolve("workspace.fpg");

        assertThatThrownBy(() -> new DefaultAtomicWorkspaceWriter().write(target, null))
            .isInstanceOf(NullPointerException.class);
        assertThat(Files.exists(target)).isFalse();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static List<Path> entries(Path directory) throws IOException {
        List<Path> entries = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path entry : stream) {
                entries.add(entry.getFileName());
            }
        }
        return entries;
    }
}
