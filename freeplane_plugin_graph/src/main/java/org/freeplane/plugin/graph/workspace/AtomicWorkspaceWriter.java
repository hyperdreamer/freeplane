package org.freeplane.plugin.graph.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;

public interface AtomicWorkspaceWriter {
    void write(Path target, byte[] bytes) throws WorkspaceSaveException;

    static AtomicWorkspaceWriter standard() {
        return new DefaultAtomicWorkspaceWriter();
    }
}

interface MoveOperation {
    void move(Path source, Path target) throws IOException;
}

final class DefaultAtomicWorkspaceWriter implements AtomicWorkspaceWriter {
    private final MoveOperation moveOperation;

    DefaultAtomicWorkspaceWriter() {
        this(new MoveOperation() {
            @Override
            public void move(Path source, Path target) throws IOException {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
        });
    }

    DefaultAtomicWorkspaceWriter(MoveOperation moveOperation) {
        this.moveOperation = Objects.requireNonNull(moveOperation, "moveOperation");
    }

    @Override
    public void write(Path target, byte[] bytes) throws WorkspaceSaveException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(bytes, "bytes");
        final Path absoluteTarget = target.toAbsolutePath().normalize();
        final Path parent = absoluteTarget.getParent();
        if (parent == null || absoluteTarget.getFileName() == null) {
            throw new IllegalArgumentException("Workspace target must have a parent directory: " + target);
        }

        Path temporary = null;
        try {
            temporary = Files.createTempFile(parent, "." + absoluteTarget.getFileName() + ".", ".tmp");
            Files.write(temporary, Arrays.copyOf(bytes, bytes.length), StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
            moveOperation.move(temporary, absoluteTarget);
        }
        catch (Exception failure) {
            cleanupTemporaryFile(temporary, failure);
            throw new WorkspaceSaveException(target, failure);
        }
    }

    private static void cleanupTemporaryFile(Path temporary, Exception failure) {
        if (temporary == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporary);
        }
        catch (Exception cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }
}
