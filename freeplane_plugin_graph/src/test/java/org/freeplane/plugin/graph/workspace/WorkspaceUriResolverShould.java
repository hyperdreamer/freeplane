package org.freeplane.plugin.graph.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class WorkspaceUriResolverShould {
    private final WorkspaceUriResolver resolver = new WorkspaceUriResolver();

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void storeAndResolveRelativeSameRootUri() throws Exception {
        Path directory = temporaryFolder.newFolder("relative").toPath();
        Path workspace = directory.resolve("workspace.fpg");
        Path map = directory.resolve("maps").resolve("one.mm");
        Files.createDirectories(map.getParent());
        Files.write(workspace, bytes("workspace"));
        Files.write(map, bytes("map"));

        URI stored = resolver.toStoredUri(workspace, map);

        assertThat(stored.isAbsolute()).isFalse();
        assertThat(stored.toString()).isEqualTo("maps/one.mm");
        assertThat(resolver.resolve(workspace, stored)).isEqualTo(resolver.canonical(map));
    }

    @Test
    public void relativeColonPathRoundTripsAsHierarchicalUri() throws Exception {
        assumeNonWindowsPathSyntax();
        Path directory = temporaryFolder.newFolder("colon-same-directory").toPath();
        Path workspace = directory.resolve("workspace.fpg");
        Path map = directory.resolve("a:b.mm");
        Files.write(workspace, bytes("workspace"));
        Files.write(map, bytes("map"));

        URI stored = resolver.toStoredUri(workspace, map);
        URI reparsed = URI.create(stored.toString());

        assertThat(reparsed.isAbsolute()).isFalse();
        assertThat(reparsed.isOpaque()).isFalse();
        assertThat(resolver.resolve(workspace, reparsed)).isEqualTo(resolver.canonical(map));
    }

    @Test
    public void nestedRelativeColonPathRoundTripsAsHierarchicalUri() throws Exception {
        assumeNonWindowsPathSyntax();
        Path directory = temporaryFolder.newFolder("colon-nested").toPath();
        Path workspace = directory.resolve("workspace.fpg");
        Path map = directory.resolve("a:b").resolve("nested").resolve("one.mm");
        Files.createDirectories(map.getParent());
        Files.write(workspace, bytes("workspace"));
        Files.write(map, bytes("map"));

        URI stored = resolver.toStoredUri(workspace, map);
        URI reparsed = URI.create(stored.toString());

        assertThat(reparsed.isAbsolute()).isFalse();
        assertThat(reparsed.isOpaque()).isFalse();
        assertThat(resolver.resolve(workspace, reparsed)).isEqualTo(resolver.canonical(map));
    }

    @Test
    public void rejectEncodedLeadingSeparatorInRelativeUri() throws Exception {
        Path directory = temporaryFolder.newFolder("encoded-leading-separator").toPath();
        Path workspace = directory.resolve("workspace.fpg");
        Files.write(workspace, bytes("workspace"));

        assertThatThrownBy(() -> resolver.resolve(workspace, URI.create("%2Ftmp")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void rejectEncodedLeadingBackslashInRelativeUriOnWindows() throws Exception {
        Assume.assumeTrue("requires Windows path semantics", "\\".equals(FileSystems.getDefault().getSeparator()));
        Path directory = temporaryFolder.newFolder("encoded-leading-backslash").toPath();
        Path workspace = directory.resolve("workspace.fpg");
        Files.write(workspace, bytes("workspace"));

        assertThatThrownBy(() -> resolver.resolve(workspace, URI.create("%5Ctmp")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void storeAbsoluteUriWhenRootsDiffer() throws Exception {
        List<Path> roots = existingRoots();
        Assume.assumeTrue("requires at least two existing file-system roots", roots.size() > 1);
        Path workspace = roots.get(0).resolve("freeplane-workspace.fpg");
        Path map = roots.get(1).resolve("freeplane-map.mm");

        URI stored = resolver.toStoredUri(workspace, map);

        assertThat(stored).isEqualTo(resolver.canonical(map).toUri());
        assertThat(stored.isAbsolute()).isTrue();
    }

    @Test
    public void rewriteRelativeUriForSaveAsWithoutChangingItsCanonicalTarget() throws Exception {
        Path directory = temporaryFolder.newFolder("save-as").toPath();
        Path oldDirectory = directory.resolve("old");
        Path newDirectory = directory.resolve("new");
        Path mapsDirectory = directory.resolve("maps");
        Files.createDirectories(oldDirectory);
        Files.createDirectories(newDirectory);
        Files.createDirectories(mapsDirectory);
        Path oldWorkspace = oldDirectory.resolve("workspace.fpg");
        Path newWorkspace = newDirectory.resolve("workspace.fpg");
        Path map = mapsDirectory.resolve("one.mm");
        Files.write(oldWorkspace, bytes("old"));
        Files.write(newWorkspace, bytes("new"));
        Files.write(map, bytes("map"));
        URI stored = resolver.toStoredUri(oldWorkspace, map);

        URI rewritten = resolver.rewriteForSaveAs(oldWorkspace, newWorkspace, stored);

        assertThat(rewritten.toString()).isEqualTo("../maps/one.mm");
        assertThat(resolver.resolve(newWorkspace, rewritten)).isEqualTo(resolver.canonical(map));
    }

    @Test
    public void keepRelativeUriValidWhenWorkspaceAndMapTreeMoveTogether() throws Exception {
        Path directory = temporaryFolder.newFolder("moved-tree").toPath();
        Path originalTree = directory.resolve("original");
        Path movedTree = directory.resolve("moved");
        Path originalWorkspace = originalTree.resolve("nested").resolve("workspace.fpg");
        Path originalMap = originalTree.resolve("maps").resolve("deep").resolve("one.mm");
        Files.createDirectories(originalWorkspace.getParent());
        Files.createDirectories(originalMap.getParent());
        Files.write(originalWorkspace, bytes("workspace"));
        Files.write(originalMap, bytes("map"));
        URI stored = resolver.toStoredUri(originalWorkspace, originalMap);

        Files.move(originalTree, movedTree);
        Path movedWorkspace = movedTree.resolve("nested").resolve("workspace.fpg");
        Path movedMap = movedTree.resolve("maps").resolve("deep").resolve("one.mm");

        assertThat(resolver.resolve(movedWorkspace, stored)).isEqualTo(resolver.canonical(movedMap));
    }

    @Test
    public void encodeFilenameCharactersAndRoundTripThem() throws Exception {
        Path directory = temporaryFolder.newFolder("encoded").toPath();
        Path workspace = directory.resolve("workspace.fpg");
        Path map = directory.resolve("maps").resolve("one % # & + ; = @ \u00FC.mm");
        Files.createDirectories(map.getParent());
        Files.write(workspace, bytes("workspace"));
        Files.write(map, bytes("map"));

        URI stored = resolver.toStoredUri(workspace, map);

        assertThat(stored.toString()).isEqualTo("maps/one%20%25%20%23%20&%20+%20;%20=%20@%20%C3%BC.mm");
        assertThat(stored.getPath()).isEqualTo("maps/one % # & + ; = @ \u00FC.mm");
        assertThat(resolver.resolve(workspace, stored)).isEqualTo(resolver.canonical(map));
    }

    @Test
    public void resolveSameRootParentTraversal() throws Exception {
        Path directory = temporaryFolder.newFolder("parent-traversal").toPath();
        Path workspace = directory.resolve("nested").resolve("workspace.fpg");
        Path map = directory.resolve("maps").resolve("one.mm");
        Files.createDirectories(workspace.getParent());
        Files.createDirectories(map.getParent());
        Files.write(workspace, bytes("workspace"));
        Files.write(map, bytes("map"));

        assertThat(resolver.resolve(workspace, URI.create("../maps/one.mm")))
            .isEqualTo(resolver.canonical(map));
    }

    @Test
    public void canonicalizeNormalizedAndMissingLeafPaths() throws Exception {
        Path directory = temporaryFolder.newFolder("canonical").toPath();
        Path existingDirectory = directory.resolve("existing");
        Files.createDirectories(existingDirectory);
        Path existing = existingDirectory.resolve("map.mm");
        Files.write(existing, bytes("map"));
        Path normalized = existingDirectory.resolve("child").resolve("..").resolve("map.mm");
        Path missing = existingDirectory.resolve("missing").resolve("nested").resolve("map.mm");

        assertThat(resolver.canonical(normalized)).isEqualTo(resolver.canonical(existing));
        assertThat(resolver.canonical(missing))
            .isEqualTo(resolver.canonical(existingDirectory).resolve("missing").resolve("nested").resolve("map.mm"));
    }

    @Test
    public void canonicalizeSymlinkAliasesEquallyWhenSupported() throws Exception {
        Path directory = temporaryFolder.newFolder("symlink").toPath();
        Path realDirectory = directory.resolve("real");
        Path aliasDirectory = directory.resolve("alias");
        Files.createDirectories(realDirectory);
        Path map = realDirectory.resolve("map.mm");
        Files.write(map, bytes("map"));
        try {
            Files.createSymbolicLink(aliasDirectory, realDirectory);
        }
        catch (UnsupportedOperationException exception) {
            Assume.assumeTrue("symbolic links are unsupported", false);
            return;
        }
        catch (SecurityException exception) {
            Assume.assumeTrue("symbolic links are not permitted", false);
            return;
        }
        catch (IOException exception) {
            Assume.assumeTrue("symbolic links are unavailable", false);
            return;
        }

        assertThat(resolver.canonical(aliasDirectory.resolve("map.mm")))
            .isEqualTo(resolver.canonical(map));
    }

    @Test
    public void rejectMalformedStoredUris() throws Exception {
        Path directory = temporaryFolder.newFolder("malformed").toPath();
        Path workspace = directory.resolve("workspace.fpg");
        Files.write(workspace, bytes("workspace"));

        assertThatThrownBy(() -> resolver.resolve(workspace, URI.create("http://example.test/map.mm")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolver.resolve(workspace, URI.create("file:map.mm")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolver.resolve(workspace, URI.create("maps/one.mm?query")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolver.resolve(workspace, URI.create("maps/one.mm#fragment")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolver.resolve(workspace, URI.create("//server/map.mm")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolver.resolve(workspace, URI.create("")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolver.resolve(workspace, URI.create("file://server/map.mm")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void rejectNullUriInputsImmediately() {
        Path workspace = temporaryFolder.getRoot().toPath().resolve("workspace.fpg");
        Path map = temporaryFolder.getRoot().toPath().resolve("map.mm");

        assertThatThrownBy(() -> resolver.toStoredUri(null, map)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> resolver.toStoredUri(workspace, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> resolver.resolve(null, URI.create("map.mm")))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> resolver.resolve(workspace, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> resolver.rewriteForSaveAs(null, workspace, URI.create("map.mm")))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> resolver.rewriteForSaveAs(workspace, null, URI.create("map.mm")))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> resolver.rewriteForSaveAs(workspace, workspace, null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> resolver.canonical(null)).isInstanceOf(NullPointerException.class);
    }

    private static void assumeNonWindowsPathSyntax() {
        Assume.assumeTrue("requires non-Windows path semantics", !"\\".equals(FileSystems.getDefault().getSeparator()));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static List<Path> existingRoots() {
        List<Path> roots = new ArrayList<Path>();
        FileSystem fileSystem = FileSystems.getDefault();
        for (Path root : fileSystem.getRootDirectories()) {
            if (Files.isDirectory(root)) {
                roots.add(root);
            }
        }
        return roots;
    }
}
