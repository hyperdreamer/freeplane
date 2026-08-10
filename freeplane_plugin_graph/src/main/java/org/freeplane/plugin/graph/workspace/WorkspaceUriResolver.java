package org.freeplane.plugin.graph.workspace;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class WorkspaceUriResolver {
    public URI toStoredUri(Path workspace, Path map) {
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(map, "map");
        Path canonicalWorkspace = canonical(workspace);
        Path canonicalMap = canonical(map);
        Path workspaceParent = canonicalWorkspace.getParent();
        if (workspaceParent == null) {
            throw new IllegalArgumentException("Workspace path must have a parent directory: " + workspace);
        }
        if (sameFileSystemAndRoot(workspaceParent, canonicalMap)) {
            return relativeUri(workspaceParent, canonicalMap);
        }
        return canonicalMap.toUri();
    }

    public Path resolve(Path workspace, URI stored) {
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(stored, "stored");
        validateStoredUri(stored);
        Path canonicalWorkspace = canonical(workspace);
        Path workspaceParent = canonicalWorkspace.getParent();
        if (workspaceParent == null) {
            throw new IllegalArgumentException("Workspace path must have a parent directory: " + workspace);
        }
        if (stored.isAbsolute()) {
            return canonical(Paths.get(stored));
        }
        return canonical(workspaceParent.resolve(stored.getPath()));
    }

    public URI rewriteForSaveAs(Path oldWorkspace, Path newWorkspace, URI stored) {
        Objects.requireNonNull(oldWorkspace, "oldWorkspace");
        Objects.requireNonNull(newWorkspace, "newWorkspace");
        Objects.requireNonNull(stored, "stored");
        return toStoredUri(newWorkspace, resolve(oldWorkspace, stored));
    }

    public Path canonical(Path path) {
        Objects.requireNonNull(path, "path");
        final Path absolute;
        try {
            absolute = path.toAbsolutePath().normalize();
        }
        catch (Exception failure) {
            throw invalidPath(path, failure);
        }
        try {
            return absolute.toRealPath();
        }
        catch (NoSuchFileException missingPath) {
            return canonicalWithMissingSuffix(absolute, path);
        }
        catch (IOException failure) {
            throw invalidPath(path, failure);
        }
    }

    private static Path canonicalWithMissingSuffix(Path absolute, Path original) {
        List<String> missingSuffix = new ArrayList<String>();
        Path current = absolute;
        while (true) {
            Path fileName = current.getFileName();
            if (fileName != null) {
                missingSuffix.add(0, fileName.toString());
            }
            Path parent = current.getParent();
            if (parent == null) {
                try {
                    return appendMissingSuffix(current.toRealPath(), missingSuffix);
                }
                catch (IOException failure) {
                    throw invalidPath(original, failure);
                }
            }
            try {
                return appendMissingSuffix(parent.toRealPath(), missingSuffix);
            }
            catch (NoSuchFileException ignored) {
                current = parent;
            }
            catch (IOException failure) {
                throw invalidPath(original, failure);
            }
        }
    }

    private static Path appendMissingSuffix(Path existing, List<String> missingSuffix) {
        Path result = existing;
        for (String component : missingSuffix) {
            result = result.resolve(component);
        }
        return result;
    }

    private static URI relativeUri(Path base, Path target) {
        Path relative = base.relativize(target);
        StringBuilder path = new StringBuilder();
        for (Path component : relative) {
            if (path.length() > 0) {
                path.append('/');
            }
            path.append(component.toString());
        }
        if (path.length() == 0) {
            throw new IllegalArgumentException("Map path must not equal the workspace directory");
        }
        try {
            URI relativeUri = new URI(null, null, path.toString(), null, null);
            return URI.create(relativeUri.toASCIIString());
        }
        catch (URISyntaxException failure) {
            throw new IllegalArgumentException("Unable to encode map URI: " + target, failure);
        }
    }

    private static boolean sameFileSystemAndRoot(Path first, Path second) {
        return first.getFileSystem().equals(second.getFileSystem())
            && Objects.equals(first.getRoot(), second.getRoot());
    }

    private static void validateStoredUri(URI stored) {
        if (stored.isOpaque()) {
            throw new IllegalArgumentException("Stored map URI must be hierarchical");
        }
        if (stored.getRawQuery() != null || stored.getRawFragment() != null) {
            throw new IllegalArgumentException("Stored map URI must not have a query or fragment");
        }
        String rawPath = stored.getRawPath();
        if (rawPath == null || rawPath.isEmpty()) {
            throw new IllegalArgumentException("Stored map URI must have a nonempty path");
        }
        if (stored.isAbsolute()) {
            if (!"file".equalsIgnoreCase(stored.getScheme())) {
                throw new IllegalArgumentException("Stored absolute URI must use the file scheme");
            }
            if (stored.getRawAuthority() != null && !stored.getRawAuthority().isEmpty()) {
                throw new IllegalArgumentException("Stored file URI must not have an authority");
            }
        }
        else {
            if (stored.getRawAuthority() != null) {
                throw new IllegalArgumentException("Relative stored URI must not have an authority");
            }
            if (rawPath.startsWith("/")) {
                throw new IllegalArgumentException("Relative stored URI must not be an absolute path");
            }
        }
    }

    private static IllegalArgumentException invalidPath(Path path, Exception failure) {
        return new IllegalArgumentException("Unable to canonicalize path: " + path, failure);
    }
}
