# Graph Workspace Boundary Separation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use the deterministic
> subagent-driven-development controller to implement this plan task-by-task.

**Goal:** Enforce non-overlap of non-nested boundary hulls and verify ancestor
containment on every published non-failed layout frame, with pin-aware exact
correction and explicit per-pair conflict records.

**Architecture:** One new `BoundarySeparationCorrection` component owns the whole
post-engine correction: it runs the node-separation projection, computes hulls,
detects violations with an exact predicate plus a tolerance split, plans
deterministic pin-aware displacements (whole-map rigid translation for cross-map
pairs, facing-edge cap-set displacement for same-map pairs), applies them in a
bounded loop, and returns positions plus `BoundarySeparationDiagnostics` whose
final residual pairs are in exact bijection with `BoundaryConflict` records.
`LayoutWorker` calls the component and publishes its diagnostics on `LayoutFrame`;
`MapTierCorrection` and `LayoutConflict` are deleted.

**Tech Stack:** Java 8 source level, Gradle (`gradle :freeplane_plugin_graph:test`),
JUnit 4 + AssertJ, Freeplane graph plugin internals
(`org.freeplane.plugin.graph.layout`, `...geometry`, `...projection`).

## Global Constraints

- Java 8 source level; UTF-8; 4-space indentation.
- No new dependencies.
- No `freeplane_api`, OSGi export/import, persistence or schema change.
- All new production types live in `org.freeplane.plugin.graph.layout`; GraphStream types stay inside `org.freeplane.plugin.graph.layout.graphstream`.
- Use `gradle`, never `gradlew`; use Java from `~/.sdkman/candidates/java/21.0.8-zulu`; run tests with `gradle :freeplane_plugin_graph:test`.
- JUnit 4 with AssertJ; name test classes `*Should`; tests must never read user Dropbox paths.
- Touch only the paths listed in the task's Ownership line.

## Task 1: Canonical layout keys

**Implementer tier:** Standard

**Lane:** types

**Depends on:** none

**Ownership:** freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/CanonicalLayoutKeys.java, freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/CanonicalLayoutKeysShould.java

**Validation:** gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.CanonicalLayoutKeysShould

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/CanonicalLayoutKeys.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/CanonicalLayoutKeysShould.java`

**Interfaces:**
- Consumes: `SourceNodeKey` (`persistent()`, `persistedReference()`, `structuralPath()`, `mapReferenceId()`), `ProjectedNodeKey.source()`, `EnclosureHullKey.endpointKeys()`, `EnclosureKey.source()`, `MapReferenceId.of(String)`.
- Produces: package-private `CanonicalLayoutKeys` with `static String escape(String)`, `static String endpoint(SourceNodeKey)`, `static String hull(EnclosureHullKey)`, `static String nodeField(ProjectedNodeKey)`, `static String anchorField(EnclosureHullKey)`, `static String pair(EnclosureHullKey, EnclosureHullKey)`, `static MapReferenceId mapOfField(String)`.

- [ ] **Step 1: Write the failing test**

```java
package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;

import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.junit.Test;

public class CanonicalLayoutKeysShould {
    private static final MapReferenceId MAP = MapReferenceId.of("8055d8c8-d71e-40f3-a8ed-5bf2502f2cad");
    private static final MapReferenceId OTHER_MAP = MapReferenceId.of("76650fda-9b84-4f8b-858f-27b18d08c535");

    @Test
    public void escapeReservedCharactersInFixedOrder() {
        assertThat(CanonicalLayoutKeys.escape("a%b|c:d,e")).isEqualTo("a%25b%7Cc%3Ad%2Ce");
        assertThat(CanonicalLayoutKeys.escape("plain")).isEqualTo("plain");
    }

    @Test
    public void encodePersistedAndTransientEndpointsDistinctly() {
        SourceNodeKey persisted = source(MAP, "ID_1");
        SourceNodeKey transientKey = SourceNodeKey.transientPath(MAP,
            Arrays.asList(Integer.valueOf(1), Integer.valueOf(2)));

        assertThat(CanonicalLayoutKeys.endpoint(persisted)).isEqualTo("m:" + MAP.value() + "|p:ID_1");
        assertThat(CanonicalLayoutKeys.endpoint(transientKey)).isEqualTo("m:" + MAP.value() + "|t:1.2");
        assertThat(CanonicalLayoutKeys.endpoint(SourceNodeKey.transientPath(MAP,
            Collections.<Integer>emptyList()))).isEqualTo("m:" + MAP.value() + "|t:");
        assertThat(CanonicalLayoutKeys.endpoint(persisted))
            .isNotEqualTo(CanonicalLayoutKeys.endpoint(source(MAP, "p:ID_1")));
        assertThat(CanonicalLayoutKeys.endpoint(source(MAP, "x|t:1.2")))
            .isNotEqualTo(CanonicalLayoutKeys.endpoint(transientKey));
    }

    @Test
    public void buildHullKeysIndependentOfEndpointOrder() {
        EnclosureHullKey forward = EnclosureHullKey.of(Arrays.asList(
            EnclosureKey.of(source(MAP, "a")), EnclosureKey.of(source(MAP, "b"))));
        EnclosureHullKey reversed = EnclosureHullKey.of(Arrays.asList(
            EnclosureKey.of(source(MAP, "b")), EnclosureKey.of(source(MAP, "a"))));

        assertThat(CanonicalLayoutKeys.hull(forward)).isEqualTo(CanonicalLayoutKeys.hull(reversed));
        assertThat(CanonicalLayoutKeys.hull(forward)).isEqualTo(
            "m:" + MAP.value() + "|p:a,m:" + MAP.value() + "|p:b");
    }

    @Test
    public void orderPairsCanonicallyAndSymmetrically() {
        EnclosureHullKey first = hull("a");
        EnclosureHullKey second = hull("b");

        assertThat(CanonicalLayoutKeys.pair(first, second))
            .isEqualTo(CanonicalLayoutKeys.pair(second, first));
        assertThat(CanonicalLayoutKeys.pair(first, second))
            .isEqualTo(CanonicalLayoutKeys.hull(first) + "|" + CanonicalLayoutKeys.hull(second));
    }

    @Test
    public void prefixNodeAndAnchorFields() {
        ProjectedNodeKey node = ProjectedNodeKey.of(source(MAP, "node"));

        assertThat(CanonicalLayoutKeys.nodeField(node)).isEqualTo("n:m:" + MAP.value() + "|p:node");
        assertThat(CanonicalLayoutKeys.anchorField(hull("hull"))).isEqualTo("a:m:" + MAP.value() + "|p:hull");
    }

    @Test
    public void recoverTheMapFromADisplacementField() {
        ProjectedNodeKey node = ProjectedNodeKey.of(source(MAP, "node"));

        assertThat(CanonicalLayoutKeys.mapOfField(CanonicalLayoutKeys.nodeField(node))).isEqualTo(MAP);
        assertThat(CanonicalLayoutKeys.mapOfField(CanonicalLayoutKeys.anchorField(hull("hull")))).isEqualTo(MAP);
        assertThat(CanonicalLayoutKeys.mapOfField(CanonicalLayoutKeys.nodeField(
            ProjectedNodeKey.of(source(OTHER_MAP, "node"))))).isEqualTo(OTHER_MAP);
        assertThatThrownBy(() -> CanonicalLayoutKeys.mapOfField("x:y")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CanonicalLayoutKeys.mapOfField("n:garbage"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static EnclosureHullKey hull(String id) {
        return EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(source(MAP, id))));
    }

    private static SourceNodeKey source(MapReferenceId map, String id) {
        return SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id)));
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.CanonicalLayoutKeysShould`
Expected: FAIL — compilation error, `CanonicalLayoutKeys` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package org.freeplane.plugin.graph.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

final class CanonicalLayoutKeys {
    private CanonicalLayoutKeys() {
    }

    static String escape(final String value) {
        Objects.requireNonNull(value, "value");
        return value.replace("%", "%25").replace("|", "%7C").replace(":", "%3A").replace(",", "%2C");
    }

    static String endpoint(final SourceNodeKey source) {
        Objects.requireNonNull(source, "source");
        final StringBuilder builder = new StringBuilder();
        builder.append("m:").append(source.mapReferenceId().value()).append('|');
        if (source.persistent()) {
            builder.append("p:").append(escape(source.persistedReference().get().nodeId().value()));
        }
        else {
            builder.append("t:");
            final List<Integer> path = source.structuralPath();
            for (int index = 0; index < path.size(); index++) {
                if (index > 0) {
                    builder.append('.');
                }
                builder.append(path.get(index).intValue());
            }
        }
        return builder.toString();
    }

    static String hull(final EnclosureHullKey hull) {
        Objects.requireNonNull(hull, "hull");
        final List<String> endpoints = new ArrayList<String>();
        for (final EnclosureKey endpoint : hull.endpointKeys()) {
            endpoints.add(endpoint(endpoint.source()));
        }
        Collections.sort(endpoints);
        final StringBuilder builder = new StringBuilder();
        for (int index = 0; index < endpoints.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(endpoints.get(index));
        }
        return builder.toString();
    }

    static String nodeField(final ProjectedNodeKey node) {
        Objects.requireNonNull(node, "node");
        return "n:" + endpoint(node.source());
    }

    static String anchorField(final EnclosureHullKey hull) {
        return "a:" + hull(hull);
    }

    static String pair(final EnclosureHullKey first, final EnclosureHullKey second) {
        final String firstKey = hull(first);
        final String secondKey = hull(second);
        if (firstKey.compareTo(secondKey) <= 0) {
            return firstKey + "|" + secondKey;
        }
        return secondKey + "|" + firstKey;
    }

    static MapReferenceId mapOfField(final String field) {
        Objects.requireNonNull(field, "field");
        if (field.length() < 5
                || (field.charAt(0) != 'n' && field.charAt(0) != 'a') || field.charAt(1) != ':') {
            throw new IllegalArgumentException("Not a displacement field key: " + field);
        }
        final String endpoint = field.substring(2);
        if (!endpoint.startsWith("m:")) {
            throw new IllegalArgumentException("Not a displacement field key: " + field);
        }
        final int separator = endpoint.indexOf('|');
        if (separator < 0) {
            throw new IllegalArgumentException("Not a displacement field key: " + field);
        }
        return MapReferenceId.of(endpoint.substring(2, separator));
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.CanonicalLayoutKeysShould`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/CanonicalLayoutKeys.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/CanonicalLayoutKeysShould.java
git commit -m "feat(graph-layout): add collision-free canonical layout keys"
```

## Task 2: Boundary separation exception and timings

**Implementer tier:** Fast

**Lane:** types

**Depends on:** 1

**Ownership:** freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationException.java, freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationTimings.java, freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationExceptionShould.java, freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationTimingsShould.java

**Validation:** gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationExceptionShould --tests org.freeplane.plugin.graph.layout.BoundarySeparationTimingsShould

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationException.java`
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationTimings.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationExceptionShould.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationTimingsShould.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `public final class BoundarySeparationException extends RuntimeException` with `(String)`, `(String, Throwable)`, `(Throwable)` constructors; `public final class BoundarySeparationTimings` with `(long separationNanos, long hullNanos, long planNanos, long applyNanos)` and the four accessors.

- [ ] **Step 1: Write the failing tests**

```java
package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

public class BoundarySeparationExceptionShould {
    @Test
    public void carryMessageAndCause() {
        final IllegalArgumentException cause = new IllegalArgumentException("non-finite product");
        final BoundarySeparationException exception = new BoundarySeparationException("mst failed", cause);

        assertThat(exception).hasMessage("mst failed");
        assertThat(exception).hasCause(cause);
        assertThat(new BoundarySeparationException(cause)).hasCause(cause);
        assertThat(new BoundarySeparationException("plain")).hasMessage("plain");
        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new BoundarySeparationException((String) null))
            .isInstanceOf(NullPointerException.class);
    }
}
```

```java
package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

public class BoundarySeparationTimingsShould {
    @Test
    public void exposeNonnegativeStageSums() {
        final BoundarySeparationTimings timings = new BoundarySeparationTimings(11L, 22L, 33L, 44L);

        assertThat(timings.separationNanos()).isEqualTo(11L);
        assertThat(timings.hullNanos()).isEqualTo(22L);
        assertThat(timings.planNanos()).isEqualTo(33L);
        assertThat(timings.applyNanos()).isEqualTo(44L);
        assertThatThrownBy(() -> new BoundarySeparationTimings(-1L, 0L, 0L, 0L))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationExceptionShould --tests org.freeplane.plugin.graph.layout.BoundarySeparationTimingsShould`
Expected: FAIL — compilation errors, both types do not exist.

- [ ] **Step 3: Write the implementations**

```java
package org.freeplane.plugin.graph.layout;

import java.util.Objects;

public final class BoundarySeparationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BoundarySeparationException(final String message) {
        super(Objects.requireNonNull(message, "message"));
    }

    public BoundarySeparationException(final String message, final Throwable cause) {
        super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
    }

    public BoundarySeparationException(final Throwable cause) {
        super(Objects.requireNonNull(cause, "cause"));
    }
}
```

```java
package org.freeplane.plugin.graph.layout;

public final class BoundarySeparationTimings {
    private final long separationNanos;
    private final long hullNanos;
    private final long planNanos;
    private final long applyNanos;

    public BoundarySeparationTimings(final long separationNanos, final long hullNanos, final long planNanos,
            final long applyNanos) {
        if (separationNanos < 0L || hullNanos < 0L || planNanos < 0L || applyNanos < 0L) {
            throw new IllegalArgumentException("Boundary separation timings must be nonnegative");
        }
        this.separationNanos = separationNanos;
        this.hullNanos = hullNanos;
        this.planNanos = planNanos;
        this.applyNanos = applyNanos;
    }

    public long separationNanos() {
        return separationNanos;
    }

    public long hullNanos() {
        return hullNanos;
    }

    public long planNanos() {
        return planNanos;
    }

    public long applyNanos() {
        return applyNanos;
    }
}
```

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationExceptionShould --tests org.freeplane.plugin.graph.layout.BoundarySeparationTimingsShould`
Expected: PASS, 2 tests.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationException.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationTimings.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationExceptionShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationTimingsShould.java
git commit -m "feat(graph-layout): add boundary separation exception and timings"
```

## Task 3: Boundary conflict

**Implementer tier:** Standard

**Lane:** types

**Depends on:** 2

**Ownership:** freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundaryConflict.java, freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundaryConflictShould.java

**Validation:** gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundaryConflictShould

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundaryConflict.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundaryConflictShould.java`

**Interfaces:**
- Consumes: `CanonicalLayoutKeys.hull(EnclosureHullKey)` and `CanonicalLayoutKeys.pair(EnclosureHullKey, EnclosureHullKey)` from Task 1; `PinProjection.active()`, `PinProjection.source()`, `PinProjection.projectedNode()`; `EnclosureHullKey.mapReferenceId()`.
- Produces: `public final class BoundaryConflict` with `BoundaryConflict(EnclosureHullKey firstHull, EnclosureHullKey secondHull, Kind kind, Reason reason, List<PinProjection> blockingPins)`, accessors `firstHull()`, `secondHull()`, `firstMap()`, `secondMap()`, `pairKey()`, `kind()`, `reason()`, `blockingPins()`, and nested enums `Kind { SIBLING_CROSSING, SIBLING_CONTAINMENT, ANCESTOR_ESCAPE }`, `Reason { STRUCTURAL_ESCAPE, IMMOVABLE_SIDES, ROUND_LIMIT }`.

- [ ] **Step 1: Write the failing test**

```java
package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;

import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.junit.Test;

public class BoundaryConflictShould {
    private static final MapReferenceId MAP_ONE = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId MAP_TWO = MapReferenceId.of("00000000-0000-0000-0000-000000000002");

    @Test
    public void normalizeThePairIntoCanonicalOrder() {
        final EnclosureHullKey larger = hull(MAP_TWO, "z");
        final EnclosureHullKey smaller = hull(MAP_ONE, "a");

        final BoundaryConflict conflict = new BoundaryConflict(larger, smaller,
            BoundaryConflict.Kind.SIBLING_CROSSING, BoundaryConflict.Reason.IMMOVABLE_SIDES,
            Collections.<PinProjection>emptyList());

        assertThat(conflict.firstHull()).isEqualTo(smaller);
        assertThat(conflict.secondHull()).isEqualTo(larger);
        assertThat(conflict.pairKey()).isEqualTo(CanonicalLayoutKeys.pair(smaller, larger));
        assertThat(conflict.firstMap()).isEqualTo(MAP_ONE);
        assertThat(conflict.secondMap()).isEqualTo(MAP_TWO);
        assertThat(conflict.kind()).isEqualTo(BoundaryConflict.Kind.SIBLING_CROSSING);
        assertThat(conflict.reason()).isEqualTo(BoundaryConflict.Reason.IMMOVABLE_SIDES);
    }

    @Test
    public void allowSameMapConflictsAndSortBlockingPins() {
        final EnclosureHullKey first = hull(MAP_ONE, "a");
        final EnclosureHullKey second = hull(MAP_ONE, "b");
        final PinProjection laterNode = active(MAP_ONE, "n2");
        final PinProjection earlierNode = active(MAP_ONE, "n1");
        final PinProjection otherMap = active(MAP_TWO, "n0");

        final BoundaryConflict conflict = new BoundaryConflict(first, second,
            BoundaryConflict.Kind.SIBLING_CONTAINMENT, BoundaryConflict.Reason.IMMOVABLE_SIDES,
            Arrays.asList(laterNode, otherMap, earlierNode));

        assertThat(conflict.firstMap()).isEqualTo(conflict.secondMap());
        assertThat(conflict.blockingPins()).containsExactly(earlierNode, laterNode, otherMap);
        assertThatThrownBy(() -> conflict.blockingPins().clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void rejectDormantBlockingPins() {
        final PinProjection active = active(MAP_ONE, "n1");

        assertThatThrownBy(() -> new BoundaryConflict(hull(MAP_ONE, "a"), hull(MAP_ONE, "b"),
            BoundaryConflict.Kind.SIBLING_CROSSING, BoundaryConflict.Reason.IMMOVABLE_SIDES,
            Collections.singletonList(PinProjection.dormant(active.record()))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static PinProjection active(MapReferenceId map, String id) {
        final ProjectedNodeKey node = ProjectedNodeKey.of(SourceNodeKey.persisted(
            NodeReference.of(map, PersistedNodeId.of(id))));
        return PinProjection.active(PinRecord.of(node.source().persistedReference().get(), 1.0, 2.0,
            Collections.<UnknownXml>emptyList()), node);
    }

    private static EnclosureHullKey hull(MapReferenceId map, String id) {
        return EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(
            SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id))))));
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundaryConflictShould`
Expected: FAIL — compilation error, `BoundaryConflict` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package org.freeplane.plugin.graph.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

public final class BoundaryConflict {
    public enum Kind {
        SIBLING_CROSSING,
        SIBLING_CONTAINMENT,
        ANCESTOR_ESCAPE
    }

    public enum Reason {
        STRUCTURAL_ESCAPE,
        IMMOVABLE_SIDES,
        ROUND_LIMIT
    }

    private final EnclosureHullKey firstHull;
    private final EnclosureHullKey secondHull;
    private final Kind kind;
    private final Reason reason;
    private final List<PinProjection> blockingPins;

    public BoundaryConflict(final EnclosureHullKey firstHull, final EnclosureHullKey secondHull, final Kind kind,
            final Reason reason, final List<PinProjection> blockingPins) {
        Objects.requireNonNull(firstHull, "firstHull");
        Objects.requireNonNull(secondHull, "secondHull");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.reason = Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(blockingPins, "blockingPins");
        final String firstKey = CanonicalLayoutKeys.hull(firstHull);
        final String secondKey = CanonicalLayoutKeys.hull(secondHull);
        if (firstKey.compareTo(secondKey) <= 0) {
            this.firstHull = firstHull;
            this.secondHull = secondHull;
        }
        else {
            this.firstHull = secondHull;
            this.secondHull = firstHull;
        }
        final List<PinProjection> copy = new ArrayList<PinProjection>(blockingPins.size());
        for (final PinProjection pin : blockingPins) {
            final PinProjection value = Objects.requireNonNull(pin, "blockingPins entry");
            if (!value.active()) {
                throw new IllegalArgumentException("Boundary conflicts can contain active pins only");
            }
            copy.add(value);
        }
        Collections.sort(copy, new Comparator<PinProjection>() {
            @Override
            public int compare(final PinProjection left, final PinProjection right) {
                final int byMap = left.source().mapReferenceId().value().toString()
                    .compareTo(right.source().mapReferenceId().value().toString());
                if (byMap != 0) {
                    return byMap;
                }
                return left.source().nodeId().value().compareTo(right.source().nodeId().value());
            }
        });
        this.blockingPins = Collections.unmodifiableList(copy);
    }

    public EnclosureHullKey firstHull() {
        return firstHull;
    }

    public EnclosureHullKey secondHull() {
        return secondHull;
    }

    public MapReferenceId firstMap() {
        return firstHull.mapReferenceId();
    }

    public MapReferenceId secondMap() {
        return secondHull.mapReferenceId();
    }

    public String pairKey() {
        return CanonicalLayoutKeys.pair(firstHull, secondHull);
    }

    public Kind kind() {
        return kind;
    }

    public Reason reason() {
        return reason;
    }

    public List<PinProjection> blockingPins() {
        return blockingPins;
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundaryConflictShould`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundaryConflict.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundaryConflictShould.java
git commit -m "feat(graph-layout): add canonical boundary conflict records"
```

## Task 4: Boundary separation diagnostics

**Implementer tier:** Standard

**Lane:** types

**Depends on:** 3

**Ownership:** freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationDiagnostics.java, freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationDiagnosticsShould.java

**Validation:** gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationDiagnosticsShould

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationDiagnostics.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationDiagnosticsShould.java`

**Interfaces:**
- Consumes: `BoundaryConflict.pairKey()` from Task 3; `CanonicalLayoutKeys.mapOfField(String)` from Task 1; `LayoutPoint` (`x()`, `y()`).
- Produces: `public final class BoundarySeparationDiagnostics` with the exact constructor `(List<BoundaryConflict>, List<String>, int, int, int, double, double, Map<String, LayoutPoint>, double, double)`, `static empty()`, accessors, `boundaryVerified()`, `boundaryCovered()`, `worstMapDisplacement()`, `withDeltas(double, double)`.

- [ ] **Step 1: Write the failing test**

```java
package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.junit.Test;

public class BoundarySeparationDiagnosticsShould {
    private static final MapReferenceId MAP_ONE = MapReferenceId.of("8055d8c8-d71e-40f3-a8ed-5bf2502f2cad");
    private static final MapReferenceId MAP_TWO = MapReferenceId.of("76650fda-9b84-4f8b-858f-27b18d08c535");

    @Test
    public void reportVerificationCoverageAndCompactness() {
        final EnclosureHullKey first = hull(MAP_ONE, "a");
        final EnclosureHullKey second = hull(MAP_ONE, "b");
        final BoundaryConflict conflict = new BoundaryConflict(first, second,
            BoundaryConflict.Kind.SIBLING_CROSSING, BoundaryConflict.Reason.ROUND_LIMIT,
            Collections.emptyList());
        final Map<String, LayoutPoint> field = new LinkedHashMap<String, LayoutPoint>();
        field.put("n:m:" + MAP_ONE.value() + "|p:a", LayoutPoint.of(3.0, 4.0));
        field.put("a:m:" + MAP_TWO.value() + "|p:h", LayoutPoint.of(2.0, 0.0));
        final BoundarySeparationDiagnostics diagnostics = new BoundarySeparationDiagnostics(
            Collections.singletonList(conflict), Collections.singletonList(conflict.pairKey()),
            2, 1, 3, 5.0, 5.0, field, 0.0, 0.0);

        assertThat(diagnostics.hullViolationsDetected()).isEqualTo(2);
        assertThat(diagnostics.hullResidualViolations()).isEqualTo(1);
        assertThat(diagnostics.rounds()).isEqualTo(3);
        assertThat(diagnostics.displacementRms()).isEqualTo(5.0);
        assertThat(diagnostics.displacementMax()).isEqualTo(5.0);
        assertThat(diagnostics.boundaryVerified()).isFalse();
        assertThat(diagnostics.boundaryCovered()).isTrue();
        assertThat(diagnostics.worstMapDisplacement()).isEqualTo(5.0);
        assertThat(diagnostics.appliedDisplacements()).isEqualTo(field);
        assertThat(diagnostics.conflicts()).containsExactly(conflict);
        assertThat(diagnostics.residualHullPairs()).containsExactly(conflict.pairKey());
    }

    @Test
    public void verifiedDiagnosticsAreVacuouslyCoveredAndEmptyIsZeroed() {
        final BoundarySeparationDiagnostics empty = BoundarySeparationDiagnostics.empty();

        assertThat(empty.boundaryVerified()).isTrue();
        assertThat(empty.boundaryCovered()).isTrue();
        assertThat(empty.worstMapDisplacement()).isZero();
        assertThat(empty.appliedDisplacements()).isEmpty();
        assertThat(empty.conflicts()).isEmpty();
        assertThat(empty.residualHullPairs()).isEmpty();
        assertThat(empty.withDeltas(1.5, 2.5).deltaRms()).isEqualTo(1.5);
        assertThat(empty.withDeltas(1.5, 2.5).deltaMax()).isEqualTo(2.5);
        assertThat(empty.deltaRms()).isZero();
        assertThat(empty.deltaMax()).isZero();
    }

    @Test
    public void rejectInconsistentInputs() {
        final Map<String, LayoutPoint> zeroVector = new LinkedHashMap<String, LayoutPoint>();
        zeroVector.put("n:m:" + MAP_ONE.value() + "|p:a", LayoutPoint.of(0.0, 0.0));

        assertThatThrownBy(() -> new BoundarySeparationDiagnostics(Collections.emptyList(),
            Collections.emptyList(), 0, 0, 0, 0.0, 0.0, zeroVector, 0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BoundarySeparationDiagnostics(Collections.emptyList(),
            Collections.emptyList(), -1, 0, 0, 0.0, 0.0,
            Collections.<String, LayoutPoint>emptyMap(), 0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BoundarySeparationDiagnostics(Collections.emptyList(),
            Collections.emptyList(), 0, 0, 0, Double.NaN, 0.0,
            Collections.<String, LayoutPoint>emptyMap(), 0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static EnclosureHullKey hull(MapReferenceId map, String id) {
        return EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(
            SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id))))));
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationDiagnosticsShould`
Expected: FAIL — compilation error, `BoundarySeparationDiagnostics` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package org.freeplane.plugin.graph.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

public final class BoundarySeparationDiagnostics {
    private final List<BoundaryConflict> conflicts;
    private final List<String> residualHullPairs;
    private final int hullViolationsDetected;
    private final int hullResidualViolations;
    private final int rounds;
    private final double displacementRms;
    private final double displacementMax;
    private final Map<String, LayoutPoint> appliedDisplacements;
    private final double deltaRms;
    private final double deltaMax;

    public BoundarySeparationDiagnostics(final List<BoundaryConflict> conflicts,
            final List<String> residualHullPairs, final int hullViolationsDetected,
            final int hullResidualViolations, final int rounds, final double displacementRms,
            final double displacementMax, final Map<String, LayoutPoint> appliedDisplacements,
            final double deltaRms, final double deltaMax) {
        Objects.requireNonNull(conflicts, "conflicts");
        Objects.requireNonNull(residualHullPairs, "residualHullPairs");
        Objects.requireNonNull(appliedDisplacements, "appliedDisplacements");
        if (hullViolationsDetected < 0 || hullResidualViolations < 0 || rounds < 0) {
            throw new IllegalArgumentException("Boundary diagnostics counts must be nonnegative");
        }
        requireNonnegativeFinite(displacementRms, "displacementRms");
        requireNonnegativeFinite(displacementMax, "displacementMax");
        requireNonnegativeFinite(deltaRms, "deltaRms");
        requireNonnegativeFinite(deltaMax, "deltaMax");
        final List<BoundaryConflict> conflictCopy = new ArrayList<BoundaryConflict>(conflicts.size());
        for (final BoundaryConflict conflict : conflicts) {
            conflictCopy.add(Objects.requireNonNull(conflict, "conflicts entry"));
        }
        final List<String> pairCopy = new ArrayList<String>(residualHullPairs.size());
        for (final String pair : residualHullPairs) {
            pairCopy.add(Objects.requireNonNull(pair, "residualHullPairs entry"));
        }
        final Map<String, LayoutPoint> displacementCopy = new LinkedHashMap<String, LayoutPoint>();
        for (final Map.Entry<String, LayoutPoint> entry : appliedDisplacements.entrySet()) {
            final String key = Objects.requireNonNull(entry.getKey(), "appliedDisplacements key");
            final LayoutPoint value = Objects.requireNonNull(entry.getValue(), "appliedDisplacements value");
            if (value.x() == 0.0 && value.y() == 0.0) {
                throw new IllegalArgumentException("Applied displacement keys must carry a non-zero vector: "
                    + key);
            }
            displacementCopy.put(key, value);
        }
        this.conflicts = Collections.unmodifiableList(conflictCopy);
        this.residualHullPairs = Collections.unmodifiableList(pairCopy);
        this.hullViolationsDetected = hullViolationsDetected;
        this.hullResidualViolations = hullResidualViolations;
        this.rounds = rounds;
        this.displacementRms = displacementRms;
        this.displacementMax = displacementMax;
        this.appliedDisplacements = Collections.unmodifiableMap(displacementCopy);
        this.deltaRms = deltaRms;
        this.deltaMax = deltaMax;
    }

    public static BoundarySeparationDiagnostics empty() {
        return new BoundarySeparationDiagnostics(Collections.<BoundaryConflict>emptyList(),
            Collections.<String>emptyList(), 0, 0, 0, 0.0, 0.0,
            Collections.<String, LayoutPoint>emptyMap(), 0.0, 0.0);
    }

    public BoundarySeparationDiagnostics withDeltas(final double deltaRms, final double deltaMax) {
        return new BoundarySeparationDiagnostics(conflicts, residualHullPairs, hullViolationsDetected,
            hullResidualViolations, rounds, displacementRms, displacementMax, appliedDisplacements,
            deltaRms, deltaMax);
    }

    public List<BoundaryConflict> conflicts() {
        return conflicts;
    }

    public List<String> residualHullPairs() {
        return residualHullPairs;
    }

    public int hullViolationsDetected() {
        return hullViolationsDetected;
    }

    public int hullResidualViolations() {
        return hullResidualViolations;
    }

    public int rounds() {
        return rounds;
    }

    public double displacementRms() {
        return displacementRms;
    }

    public double displacementMax() {
        return displacementMax;
    }

    public Map<String, LayoutPoint> appliedDisplacements() {
        return appliedDisplacements;
    }

    public double deltaRms() {
        return deltaRms;
    }

    public double deltaMax() {
        return deltaMax;
    }

    public boolean boundaryVerified() {
        return hullResidualViolations == 0;
    }

    public boolean boundaryCovered() {
        final Set<String> conflictPairs = new LinkedHashSet<String>();
        for (final BoundaryConflict conflict : conflicts) {
            conflictPairs.add(conflict.pairKey());
        }
        return conflictPairs.equals(new LinkedHashSet<String>(residualHullPairs));
    }

    public double worstMapDisplacement() {
        final Map<MapReferenceId, Double> perMap = new LinkedHashMap<MapReferenceId, Double>();
        for (final Map.Entry<String, LayoutPoint> entry : appliedDisplacements.entrySet()) {
            final MapReferenceId map = CanonicalLayoutKeys.mapOfField(entry.getKey());
            final LayoutPoint value = entry.getValue();
            final double magnitude = Math.hypot(value.x(), value.y());
            final Double previous = perMap.get(map);
            if (previous == null || magnitude > previous.doubleValue()) {
                perMap.put(map, Double.valueOf(magnitude));
            }
        }
        double worst = 0.0;
        for (final Double value : perMap.values()) {
            worst = Math.max(worst, value.doubleValue());
        }
        return worst;
    }

    private static void requireNonnegativeFinite(final double value, final String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and nonnegative");
        }
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationDiagnosticsShould`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationDiagnostics.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationDiagnosticsShould.java
git commit -m "feat(graph-layout): add boundary separation diagnostics"
```

## Task 5: Boundary separation result

**Implementer tier:** Fast

**Lane:** types

**Depends on:** 4

**Ownership:** freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationResult.java, freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationResultShould.java

**Validation:** gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationResultShould

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationResult.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationResultShould.java`

**Interfaces:**
- Consumes: `BoundarySeparationDiagnostics` (Task 4), `BoundarySeparationTimings` (Task 2), `LayoutPositions`, `LayoutPoint`.
- Produces: `public final class BoundarySeparationResult` with package-private constructor `(LayoutPositions, BoundarySeparationDiagnostics, int, BoundarySeparationTimings)` and public accessors `positions()`, `diagnostics()`, `nodeResidualViolations()`, `appliedDisplacements()`, `timings()`.

- [ ] **Step 1: Write the failing test**

```java
package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.junit.Test;

public class BoundarySeparationResultShould {
    @Test
    public void exposePositionsDiagnosticsAndTimings() {
        final BoundarySeparationDiagnostics diagnostics = BoundarySeparationDiagnostics.empty();
        final BoundarySeparationTimings timings = new BoundarySeparationTimings(1L, 2L, 3L, 4L);
        final LayoutPositions positions = LayoutPositions.of(
            Collections.<org.freeplane.plugin.graph.projection.ProjectedNodeKey,
                org.freeplane.plugin.graph.geometry.LayoutPoint>emptyMap(),
            Collections.<org.freeplane.plugin.graph.projection.EnclosureHullKey,
                org.freeplane.plugin.graph.geometry.LayoutPoint>emptyMap());

        final BoundarySeparationResult result = new BoundarySeparationResult(positions, diagnostics, 7, timings);

        assertThat(result.positions()).isSameAs(positions);
        assertThat(result.diagnostics()).isSameAs(diagnostics);
        assertThat(result.nodeResidualViolations()).isEqualTo(7);
        assertThat(result.timings()).isSameAs(timings);
        assertThat(result.appliedDisplacements()).isSameAs(diagnostics.appliedDisplacements());
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationResultShould`
Expected: FAIL — compilation error, `BoundarySeparationResult` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package org.freeplane.plugin.graph.layout;

import java.util.Map;
import java.util.Objects;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;

public final class BoundarySeparationResult {
    private final LayoutPositions positions;
    private final BoundarySeparationDiagnostics diagnostics;
    private final int nodeResidualViolations;
    private final BoundarySeparationTimings timings;

    BoundarySeparationResult(final LayoutPositions positions,
            final BoundarySeparationDiagnostics diagnostics, final int nodeResidualViolations,
            final BoundarySeparationTimings timings) {
        this.positions = Objects.requireNonNull(positions, "positions");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        if (nodeResidualViolations < 0) {
            throw new IllegalArgumentException("Node residual violations must be nonnegative");
        }
        this.nodeResidualViolations = nodeResidualViolations;
        this.timings = Objects.requireNonNull(timings, "timings");
    }

    public LayoutPositions positions() {
        return positions;
    }

    public BoundarySeparationDiagnostics diagnostics() {
        return diagnostics;
    }

    public int nodeResidualViolations() {
        return nodeResidualViolations;
    }

    public Map<String, LayoutPoint> appliedDisplacements() {
        return diagnostics.appliedDisplacements();
    }

    public BoundarySeparationTimings timings() {
        return timings;
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationResultShould`
Expected: PASS, 1 test.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationResult.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationResultShould.java
git commit -m "feat(graph-layout): add boundary separation result"
```

## Task 6: Boundary invariant assertion helper

**Implementer tier:** Standard

**Lane:** types

**Depends on:** 5

**Ownership:** freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundaryInvariantAssertions.java, freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundaryInvariantAssertionsShould.java

**Validation:** gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundaryInvariantAssertionsShould

**Files:**
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundaryInvariantAssertions.java`
- Test: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundaryInvariantAssertionsShould.java`

**Interfaces:**
- Consumes: `LayoutFrame` existing API only (`positions()`, `failed()`, `residualViolations()`, `of(long, LayoutPositions, boolean, int)`); `BoundarySeparationDiagnostics` (Task 4); `GraphGeometryEngine.computeHulls`; `HullIntersection.siblingOverlap/minimumSeparatingTranslation`; `HullGeometry.contains/exactPolygon`; `NodeSeparationProjection.project`; `PinProjection`. The diagnostics are always passed in explicitly, so this helper compiles before and after the `LayoutFrame` migration in Task 13.
- Produces: package-private `BoundaryInvariantAssertions` with `static void assertVerifiedFrame(GraphProjection, LayoutFrame, BoundarySeparationDiagnostics, GeometryTextMetrics, List<PinProjection>)` and `static void assertBijection(BoundarySeparationDiagnostics)`.

- [ ] **Step 1: Write the failing test**

```java
package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.freeplane.plugin.graph.geometry.AwtGeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.RelationshipResolution;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.junit.Test;

public class BoundaryInvariantAssertionsShould {
    private static final MapReferenceId MAP = MapReferenceId.of("8055d8c8-d71e-40f3-a8ed-5bf2502f2cad");

    @Test
    public void acceptACleanVerifiedFrame() {
        assertThatCode(() -> BoundaryInvariantAssertions.assertVerifiedFrame(projection(), frame(0.0, 60.0),
            BoundarySeparationDiagnostics.empty(), metrics(), Collections.<PinProjection>emptyList()))
                .doesNotThrowAnyException();
        assertThatCode(() -> BoundaryInvariantAssertions.assertBijection(BoundarySeparationDiagnostics.empty()))
            .doesNotThrowAnyException();
    }

    @Test
    public void rejectACrossingVerifiedFrame() {
        assertThatThrownBy(() -> BoundaryInvariantAssertions.assertVerifiedFrame(projection(), frame(0.0, 38.0),
            BoundarySeparationDiagnostics.empty(), metrics(), Collections.<PinProjection>emptyList()))
                .isInstanceOf(AssertionError.class);
    }

    private static GraphProjection projection() {
        final ProjectedNodeKey left = node("left");
        final ProjectedNodeKey right = node("right");
        final EnclosureHullKey leftHull = hull("left");
        final EnclosureHullKey rightHull = hull("right");
        final ProjectedEnclosure leftEnclosure = ProjectedEnclosure.of(leftHull,
            leftHull.endpointKeys(), Collections.singletonList(SafeNodeLabel.of("left", "left")), "map",
            Optional.<EnclosureHullKey>empty(), Collections.singletonList(left),
            Collections.<EnclosureHullKey>emptyList(), true, BoundaryTier.SUBTLE);
        final ProjectedEnclosure rightEnclosure = ProjectedEnclosure.of(rightHull,
            rightHull.endpointKeys(), Collections.singletonList(SafeNodeLabel.of("right", "right")), "map",
            Optional.<EnclosureHullKey>empty(), Collections.singletonList(right),
            Collections.<EnclosureHullKey>emptyList(), true, BoundaryTier.SUBTLE);
        return GraphProjection.projected(1L,
            Arrays.asList(ProjectedNode.of(left, SafeNodeLabel.of("left", "left"), "map", false),
                ProjectedNode.of(right, SafeNodeLabel.of("right", "right"), "map", false)),
            Arrays.asList(leftEnclosure, rightEnclosure),
            Collections.<org.freeplane.plugin.graph.projection.ProjectedEdge>emptyList(),
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
    }

    private static LayoutFrame frame(double leftX, double rightX) {
        final Map<ProjectedNodeKey, LayoutPoint> nodes = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        nodes.put(node("left"), LayoutPoint.of(leftX, 0.0));
        nodes.put(node("right"), LayoutPoint.of(rightX, 0.0));
        final Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        anchors.put(hull("left"), LayoutPoint.of(leftX, 0.0));
        anchors.put(hull("right"), LayoutPoint.of(rightX, 0.0));
        return LayoutFrame.of(0L, LayoutPositions.of(nodes, anchors), false, 0);
    }

    private static GeometryTextMetrics metrics() {
        return new AwtGeometryTextMetrics(new Font("Dialog", Font.PLAIN, 12),
            new FontRenderContext(null, true, true));
    }

    private static ProjectedNodeKey node(String id) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(id))));
    }

    private static EnclosureHullKey hull(String id) {
        return EnclosureHullKey.of(Collections.singletonList(
            EnclosureKey.of(SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(id))))));
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundaryInvariantAssertionsShould`
Expected: FAIL — compilation error, `BoundaryInvariantAssertions` does not exist.

- [ ] **Step 3: Write the helper**

```java
package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.freeplane.plugin.graph.geometry.GeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.HullIntersection;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;

final class BoundaryInvariantAssertions {
    private BoundaryInvariantAssertions() {
    }

    static void assertVerifiedFrame(final GraphProjection projection, final LayoutFrame frame,
            final BoundarySeparationDiagnostics diagnostics, final GeometryTextMetrics metrics,
            final List<PinProjection> pins) {
        assertThat(frame.failed()).as("verified frames must not be failed").isFalse();
        assertThat(diagnostics.boundaryVerified()).as("helper is scoped to boundary-verified frames").isTrue();
        final GraphGeometry geometry = new org.freeplane.plugin.graph.geometry.GraphGeometryEngine()
            .computeHulls(projection, frame.positions(), metrics);
        final Map<EnclosureHullKey, EnclosureHullKey> parents = parents(projection);
        final List<EnclosureHullKey> enforced = enforced(projection, geometry);
        for (int first = 0; first < enforced.size(); first++) {
            for (int second = first + 1; second < enforced.size(); second++) {
                final EnclosureHullKey firstHull = enforced.get(first);
                final EnclosureHullKey secondHull = enforced.get(second);
                if (related(parents, firstHull, secondHull)) {
                    continue;
                }
                final HullGeometry firstGeometry = geometry.hulls().get(firstHull);
                final HullGeometry secondGeometry = geometry.hulls().get(secondHull);
                final LayoutPoint translation = HullIntersection.minimumSeparatingTranslation(firstGeometry,
                    secondGeometry);
                final boolean overlap = HullIntersection.siblingOverlap(firstGeometry, secondGeometry)
                    || containsInclusive(firstGeometry, secondGeometry)
                    || containsInclusive(secondGeometry, firstGeometry);
                assertThat(overlap && !(translation.x() == 0.0 && translation.y() == 0.0))
                    .as("non-nested hull pair must not cross: %s <-> %s",
                        CanonicalLayoutKeys.hull(firstHull), CanonicalLayoutKeys.hull(secondHull))
                    .isFalse();
            }
        }
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            if (enclosure.boundaryTier() == BoundaryTier.SUPPRESSED || !enclosure.parentHull().isPresent()) {
                continue;
            }
            final EnclosureHullKey child = enclosure.hullKey();
            final EnclosureHullKey parent = enclosure.parentHull().get();
            if (!enforced.contains(child) || !enforced.contains(parent)) {
                continue;
            }
            for (final LayoutPoint vertex : geometry.hulls().get(child).exactPolygon()) {
                assertThat(geometry.hulls().get(parent).contains(vertex))
                    .as("child vertex must lie inside its parent hull: %s in %s",
                        CanonicalLayoutKeys.hull(child), CanonicalLayoutKeys.hull(parent))
                    .isTrue();
            }
        }
        for (final PinProjection pin : pins) {
            if (pin.active()) {
                final ProjectedNodeKey node = pin.projectedNode().get();
                assertThat(frame.positions().nodes().get(node))
                    .as("pin %s must keep its stored coordinates", pin.source().nodeId().value())
                    .isEqualTo(LayoutPoint.of(pin.x(), pin.y()));
            }
        }
        final Set<ProjectedNodeKey> pinned = new LinkedHashSet<ProjectedNodeKey>();
        for (final PinProjection pin : pins) {
            if (pin.active()) {
                pinned.add(pin.projectedNode().get());
            }
        }
        final NodeSeparationResult separation = new NodeSeparationProjection().project(projection,
            frame.positions(), pinned);
        assertThat(frame.residualViolations()).as("frame node residual must match the frame geometry")
            .isEqualTo(separation.residualViolations());
        assertBijection(diagnostics);
    }

    static void assertBijection(final BoundarySeparationDiagnostics diagnostics) {
        final List<String> conflictPairs = new ArrayList<String>();
        for (final BoundaryConflict conflict : diagnostics.conflicts()) {
            conflictPairs.add(conflict.pairKey());
        }
        assertThat(conflictPairs).as("conflict pair keys must be unique").doesNotHaveDuplicates();
        assertThat(diagnostics.residualHullPairs()).as("residual pair keys must be unique")
            .doesNotHaveDuplicates();
        assertThat(new LinkedHashSet<String>(conflictPairs))
            .as("every residual pair has exactly one conflict and no conflict is outside the residual set")
            .isEqualTo(new LinkedHashSet<String>(diagnostics.residualHullPairs()));
        assertThat(diagnostics.boundaryCovered()).isTrue();
    }

    private static List<EnclosureHullKey> enforced(final GraphProjection projection,
            final GraphGeometry geometry) {
        final List<EnclosureHullKey> result = new ArrayList<EnclosureHullKey>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            if (enclosure.boundaryTier() != BoundaryTier.SUPPRESSED
                    && geometry.hulls().get(enclosure.hullKey()) != null) {
                result.add(enclosure.hullKey());
            }
        }
        return result;
    }

    private static Map<EnclosureHullKey, EnclosureHullKey> parents(final GraphProjection projection) {
        final Map<EnclosureHullKey, EnclosureHullKey> result =
            new LinkedHashMap<EnclosureHullKey, EnclosureHullKey>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            if (enclosure.parentHull().isPresent()) {
                result.put(enclosure.hullKey(), enclosure.parentHull().get());
            }
        }
        return result;
    }

    private static boolean related(final Map<EnclosureHullKey, EnclosureHullKey> parents,
            final EnclosureHullKey first, final EnclosureHullKey second) {
        for (EnclosureHullKey current = parents.get(first); current != null; current = parents.get(current)) {
            if (current.equals(second)) {
                return true;
            }
        }
        for (EnclosureHullKey current = parents.get(second); current != null; current = parents.get(current)) {
            if (current.equals(first)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsInclusive(final HullGeometry outer, final HullGeometry inner) {
        for (final LayoutPoint vertex : inner.exactPolygon()) {
            if (!outer.contains(vertex)) {
                return false;
            }
        }
        return true;
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundaryInvariantAssertionsShould`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundaryInvariantAssertions.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundaryInvariantAssertionsShould.java
git commit -m "test(graph-layout): add shared boundary invariant assertions"
```

## Task 7: Boundary separation component skeleton, detection, and terminal coverage

**Implementer tier:** Advanced

**Lane:** correction

**Depends on:** 5

**Ownership:** freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrection.java, freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrectionShould.java

**Validation:** gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould

**Files:**
- Create: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrection.java`
- Create: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrectionShould.java`

**Interfaces:**
- Consumes: `BoundarySeparationResult` (Task 5), `BoundarySeparationDiagnostics` (Task 4), `BoundaryConflict` (Task 3), `BoundarySeparationException`/`BoundarySeparationTimings` (Task 2), `CanonicalLayoutKeys` (Task 1), `NodeSeparationProjection.project(GraphProjection, LayoutPositions, Set<ProjectedNodeKey>)`, `GraphGeometryEngine.computeHulls(GraphProjection, LayoutPositions, GeometryTextMetrics)`, `HullIntersection.siblingOverlap/minimumSeparatingTranslation`, `HullGeometry.contains/exactPolygon/minX/minY/maxX/maxY`, `PinProjection`, `BoundaryTier.SUPPRESSED`.
- Produces: `public final class BoundarySeparationCorrection` with `public static final int MAX_DISPLACEMENT_ROUNDS = 4`, `public BoundarySeparationCorrection()`, package-private `BoundarySeparationCorrection(int)`, `public BoundarySeparationResult apply(GraphProjection, LayoutPositions, GeometryTextMetrics, List<PinProjection>)`, package-private `static LayoutPoint guardedMinimumSeparatingTranslation(HullGeometry, HullGeometry)`, package-private `static List<String> ancestorEscapePairKeys(GraphProjection, Map<EnclosureHullKey, HullGeometry>)`. In this task every detected violation becomes a conflict (no displacement yet), so the early `plan.isEmpty()` exit is always taken; `rounds` stays `0`.

- [ ] **Step 1: Write the failing test**

```java
package org.freeplane.plugin.graph.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.freeplane.plugin.graph.geometry.AwtGeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.HullIntersection;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.RelationshipResolution;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.junit.Test;

public class BoundarySeparationCorrectionShould {
    static final MapReferenceId MAP = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    static final GeometryTextMetrics METRICS = new AwtGeometryTextMetrics(new Font("Dialog", Font.PLAIN, 12),
        new FontRenderContext(null, true, true));

    @Test
    public void fastPathPublishesInputPositionsWithZeroRounds() {
        final ProjectedNodeKey first = key("first");
        final ProjectedNodeKey second = key("second");
        final EnclosureHullKey firstHull = hull("first-hull");
        final EnclosureHullKey secondHull = hull("second-hull");
        final GraphProjection projection = projection(Arrays.asList(first, second),
            Arrays.asList(root(firstHull, "first", Collections.singletonList(first)),
                root(secondHull, "second", Collections.singletonList(second))));
        final LayoutPositions positions = positions(
            Arrays.asList(nodeEntry(first, 0.0, 0.0), nodeEntry(second, 100.0, 0.0)),
            Arrays.asList(anchorEntry(firstHull, 0.0, 0.0), anchorEntry(secondHull, 100.0, 0.0)));

        final BoundarySeparationResult result = new BoundarySeparationCorrection().apply(projection, positions,
            METRICS, Collections.<PinProjection>emptyList());

        assertThat(result.positions()).isEqualTo(positions);
        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().hullViolationsDetected()).isZero();
        assertThat(result.diagnostics().hullResidualViolations()).isZero();
        assertThat(result.diagnostics().boundaryVerified()).isTrue();
        assertThat(result.diagnostics().conflicts()).isEmpty();
        assertThat(result.diagnostics().residualHullPairs()).isEmpty();
    }

    @Test
    public void bareContactIsNotAViolation() {
        final ProjectedNodeKey first = key("first");
        final ProjectedNodeKey second = key("second");
        final EnclosureHullKey firstHull = hull("first-hull");
        final EnclosureHullKey secondHull = hull("second-hull");
        final GraphProjection projection = projection(Arrays.asList(first, second),
            Arrays.asList(root(firstHull, "first", Collections.singletonList(first)),
                root(secondHull, "second", Collections.singletonList(second))));

        final BoundarySeparationResult result = new BoundarySeparationCorrection().apply(projection,
            positions(Arrays.asList(nodeEntry(first, 0.0, 0.0), nodeEntry(second, 48.0, 0.0)),
                Arrays.asList(anchorEntry(firstHull, 0.0, 0.0), anchorEntry(secondHull, 48.0, 0.0))),
            METRICS, Collections.<PinProjection>emptyList());

        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().hullViolationsDetected()).isZero();
        assertThat(result.diagnostics().hullResidualViolations()).isZero();
        assertThat(result.diagnostics().boundaryVerified()).isTrue();
    }

    @Test
    public void coincidentPinnedHullsAreContainmentViolationsWithOneConflict() {
        final ProjectedNodeKey first = key("first");
        final ProjectedNodeKey second = key("second");
        final EnclosureHullKey firstHull = hull("first-hull");
        final EnclosureHullKey secondHull = hull("second-hull");
        final PinProjection firstPin = pin(first, 0.0, 0.0);
        final PinProjection secondPin = pin(second, 0.0, 0.0);
        final GraphProjection projection = projection(Arrays.asList(first, second),
            Arrays.asList(root(firstHull, "first", Collections.singletonList(first)),
                root(secondHull, "second", Collections.singletonList(second))));

        final BoundarySeparationResult result = new BoundarySeparationCorrection().apply(projection,
            positions(Arrays.asList(nodeEntry(first, 0.0, 0.0), nodeEntry(second, 0.0, 0.0)),
                Arrays.asList(anchorEntry(firstHull, 0.0, 0.0), anchorEntry(secondHull, 0.0, 0.0))),
            METRICS, Arrays.asList(firstPin, secondPin));

        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().hullViolationsDetected()).isEqualTo(1);
        assertThat(result.diagnostics().hullResidualViolations()).isEqualTo(1);
        assertThat(result.diagnostics().boundaryVerified()).isFalse();
        assertThat(result.diagnostics().boundaryCovered()).isTrue();
        assertThat(result.diagnostics().conflicts()).hasSize(1);
        assertThat(result.diagnostics().conflicts().get(0).kind())
            .isEqualTo(BoundaryConflict.Kind.SIBLING_CONTAINMENT);
        assertThat(result.diagnostics().conflicts().get(0).reason())
            .isEqualTo(BoundaryConflict.Reason.IMMOVABLE_SIDES);
        assertThat(result.diagnostics().residualHullPairs()).containsExactly(
            result.diagnostics().conflicts().get(0).pairKey());
    }

    @Test
    public void suppressedEnclosuresAreNotEnforced() {
        final ProjectedNodeKey first = key("first");
        final EnclosureHullKey firstHull = hull("first-hull");
        final EnclosureHullKey suppressedHull = hull("suppressed-hull");
        final ProjectedEnclosure enforced = root(firstHull, "first", Collections.singletonList(first));
        final ProjectedEnclosure suppressed = ProjectedEnclosure.of(suppressedHull, suppressedHull.endpointKeys(),
            Collections.singletonList(SafeNodeLabel.of("suppressed", "suppressed")), "m",
            Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
            Collections.<EnclosureHullKey>emptyList(), true, BoundaryTier.SUPPRESSED);
        final GraphProjection projection = projection(Collections.singletonList(first),
            Arrays.asList(enforced, suppressed));
        final LayoutPositions positions = positions(Collections.singletonList(nodeEntry(first, 0.0, 0.0)),
            Arrays.asList(anchorEntry(firstHull, 0.0, 0.0), anchorEntry(suppressedHull, 10.0, 0.0)));

        final BoundarySeparationResult result = new BoundarySeparationCorrection().apply(projection, positions,
            METRICS, Collections.<PinProjection>emptyList());

        assertThat(result.positions()).isEqualTo(positions);
        assertThat(result.diagnostics().hullViolationsDetected()).isZero();
        assertThat(result.diagnostics().hullResidualViolations()).isZero();
        assertThat(result.diagnostics().conflicts()).isEmpty();
    }

    @Test
    public void subEpsilonOverlapIsContactByTolerance() {
        final HullGeometry first = square(0.0, 1.0);
        final HullGeometry second = square(2.0 - 1.0e-12, 1.0);

        assertThat(HullIntersection.siblingOverlap(first, second)).isTrue();
        assertThat(HullIntersection.minimumSeparatingTranslation(first, second))
            .isEqualTo(LayoutPoint.of(0.0, 0.0));
    }

    @Test
    public void guardedMstFailureIsWrappedWithItsCause() {
        final HullGeometry first = square(0.0, 1.7e308);
        final HullGeometry second = square(0.005e308, 1.69e308);

        assertThatThrownBy(() -> BoundarySeparationCorrection.guardedMinimumSeparatingTranslation(first, second))
            .isInstanceOf(BoundarySeparationException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void ancestorEscapePairKeysReportEscapingChildren() {
        final ProjectedNodeKey child = key("child");
        final EnclosureHullKey childHull = hull("child-hull");
        final EnclosureHullKey parentHull = hull("parent-hull");
        final ProjectedEnclosure parent = root(parentHull, "parent", Collections.<ProjectedNodeKey>emptyList());
        final ProjectedEnclosure escaping = ProjectedEnclosure.of(childHull, childHull.endpointKeys(),
            Collections.singletonList(SafeNodeLabel.of("child", "child")), "m", Optional.of(parentHull),
            Collections.singletonList(child), Collections.<EnclosureHullKey>emptyList(), false,
            BoundaryTier.SUBTLE);
        final GraphProjection projection = projection(Collections.singletonList(child),
            Arrays.asList(parent, escaping));
        final Map<EnclosureHullKey, HullGeometry> hulls =
            new LinkedHashMap<EnclosureHullKey, HullGeometry>();
        hulls.put(parentHull, square(0.0, 10.0));
        hulls.put(childHull, square(20.0, 1.0));

        assertThat(BoundarySeparationCorrection.ancestorEscapePairKeys(projection, hulls))
            .containsExactly(CanonicalLayoutKeys.pair(childHull, parentHull));
    }

    private static HullGeometry square(double centerX, double halfExtent) {
        return HullGeometry.of(Arrays.asList(LayoutPoint.of(centerX - halfExtent, -halfExtent),
            LayoutPoint.of(centerX + halfExtent, -halfExtent), LayoutPoint.of(centerX + halfExtent, halfExtent),
            LayoutPoint.of(centerX - halfExtent, halfExtent)), LayoutPoint.of(centerX, 0.0));
    }

    private static ProjectedEnclosure root(EnclosureHullKey hull, String label, List<ProjectedNodeKey> nodes) {
        return ProjectedEnclosure.of(hull, hull.endpointKeys(),
            Collections.singletonList(SafeNodeLabel.of(label, label)), "m", Optional.<EnclosureHullKey>empty(),
            nodes, Collections.<EnclosureHullKey>emptyList(), true, BoundaryTier.SUBTLE);
    }

    private static GraphProjection projection(List<ProjectedNodeKey> nodes, List<ProjectedEnclosure> enclosures) {
        final List<ProjectedNode> projected = new ArrayList<ProjectedNode>();
        for (final ProjectedNodeKey key : nodes) {
            projected.add(ProjectedNode.of(key, SafeNodeLabel.of("n", "n"), "m", false));
        }
        return GraphProjection.projected(1L, projected, enclosures,
            Collections.<org.freeplane.plugin.graph.projection.ProjectedEdge>emptyList(),
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
    }

    private static LayoutPositions positions(List<Map.Entry<ProjectedNodeKey, LayoutPoint>> nodes,
            List<Map.Entry<EnclosureHullKey, LayoutPoint>> anchors) {
        final Map<ProjectedNodeKey, LayoutPoint> nodeMap = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (final Map.Entry<ProjectedNodeKey, LayoutPoint> entry : nodes) {
            nodeMap.put(entry.getKey(), entry.getValue());
        }
        final Map<EnclosureHullKey, LayoutPoint> anchorMap = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        for (final Map.Entry<EnclosureHullKey, LayoutPoint> entry : anchors) {
            anchorMap.put(entry.getKey(), entry.getValue());
        }
        return LayoutPositions.of(nodeMap, anchorMap);
    }

    private static Map.Entry<ProjectedNodeKey, LayoutPoint> nodeEntry(ProjectedNodeKey key, double x, double y) {
        return new java.util.AbstractMap.SimpleImmutableEntry<ProjectedNodeKey, LayoutPoint>(key,
            LayoutPoint.of(x, y));
    }

    private static Map.Entry<EnclosureHullKey, LayoutPoint> anchorEntry(EnclosureHullKey key, double x,
            double y) {
        return new java.util.AbstractMap.SimpleImmutableEntry<EnclosureHullKey, LayoutPoint>(key,
            LayoutPoint.of(x, y));
    }

    private static PinProjection pin(ProjectedNodeKey key, double x, double y) {
        return PinProjection.active(PinRecord.of(key.source().persistedReference().get(), x, y,
            Collections.<UnknownXml>emptyList()), key);
    }

    private static ProjectedNodeKey key(String id) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(id))));
    }

    private static EnclosureHullKey hull(String id) {
        return EnclosureHullKey.of(Collections.singletonList(
            EnclosureKey.of(SourceNodeKey.persisted(NodeReference.of(MAP, PersistedNodeId.of(id))))));
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould`
Expected: FAIL — compilation error, `BoundarySeparationCorrection` does not exist.

- [ ] **Step 3: Write the component skeleton with detection and conflict-only terminal coverage**

```java
package org.freeplane.plugin.graph.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.freeplane.plugin.graph.geometry.GeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GraphGeometryEngine;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.HullIntersection;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;

public final class BoundarySeparationCorrection {
    public static final int MAX_DISPLACEMENT_ROUNDS = 4;
    static final double HULL_CLEARANCE = 16.0;
    static final double BASE_RADIUS = 8.0;
    static final double BOUNDARY_PADDING = 8.0;
    static final double SUPPORT_COMPARISON_EPSILON = 1e-9;

    private final GraphGeometryEngine geometryEngine = new GraphGeometryEngine();
    private final int maxDisplacementRounds;

    public BoundarySeparationCorrection() {
        this(MAX_DISPLACEMENT_ROUNDS);
    }

    BoundarySeparationCorrection(final int maxDisplacementRounds) {
        if (maxDisplacementRounds < 1) {
            throw new IllegalArgumentException("The displacement round bound must be positive");
        }
        this.maxDisplacementRounds = maxDisplacementRounds;
    }

    public BoundarySeparationResult apply(final GraphProjection projection, final LayoutPositions positions,
            final GeometryTextMetrics metrics, final List<PinProjection> pins) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(pins, "pins");
        try {
            return separate(projection, positions, metrics, pins);
        }
        catch (final BoundarySeparationException exception) {
            throw exception;
        }
        catch (final RuntimeException exception) {
            throw new BoundarySeparationException("Boundary separation failed", exception);
        }
    }

    private BoundarySeparationResult separate(final GraphProjection projection, final LayoutPositions positions,
            final GeometryTextMetrics metrics, final List<PinProjection> pins) {
        final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull = enclosuresByHull(projection);
        final Map<EnclosureHullKey, EnclosureHullKey> parents = parents(projection);
        final Set<ProjectedNodeKey> pinnedNodes = pinnedNodes(pins);

        long separationNanos = 0L;
        long hullNanos = 0L;
        long planNanos = 0L;
        long applyNanos = 0L;

        LayoutPositions current = positions;
        final Map<String, LayoutPoint> field = new LinkedHashMap<String, LayoutPoint>();
        int rounds = 0;
        int detected = 0;
        int terminalNodeResidual = 0;
        List<Violation> terminalViolations = Collections.emptyList();
        Map<EnclosureHullKey, HullGeometry> terminalHulls = Collections.emptyMap();

        while (true) {
            final long separationStart = System.nanoTime();
            final NodeSeparationResult separation = new NodeSeparationProjection().project(projection, current,
                pinnedNodes);
            separationNanos += System.nanoTime() - separationStart;
            current = separation.positions();
            terminalNodeResidual = separation.residualViolations();

            final long planStart = System.nanoTime();
            final long hullStart = System.nanoTime();
            final Map<EnclosureHullKey, HullGeometry> hulls = geometryEngine
                .computeHulls(projection, current, metrics).hulls();
            hullNanos += System.nanoTime() - hullStart;
            final List<Violation> violations = detect(projection, enclosuresByHull, parents, hulls);
            if (rounds == 0) {
                detected = violations.size();
            }
            if (violations.isEmpty() || rounds >= maxDisplacementRounds) {
                terminalViolations = violations;
                terminalHulls = hulls;
                planNanos += System.nanoTime() - planStart;
                break;
            }
            final PlanOutcome plan = plan(projection, enclosuresByHull, hulls, current, metrics, pins, pinnedNodes,
                violations);
            planNanos += System.nanoTime() - planStart;
            if (plan.isEmpty()) {
                terminalViolations = violations;
                terminalHulls = hulls;
                break;
            }
            final long applyStart = System.nanoTime();
            current = applyRound(projection, enclosuresByHull, current, plan, field);
            applyNanos += System.nanoTime() - applyStart;
            rounds++;
        }

        List<BoundaryConflict> conflicts = Collections.emptyList();
        if (!terminalViolations.isEmpty()) {
            final long planStart = System.nanoTime();
            final PlanOutcome terminalPlan = plan(projection, enclosuresByHull, terminalHulls, current, metrics, pins,
                pinnedNodes, terminalViolations);
            conflicts = terminalConflicts(terminalViolations, terminalPlan, projection, enclosuresByHull, pins);
            planNanos += System.nanoTime() - planStart;
        }
        final Map<String, LayoutPoint> applied = sortedNonZero(field);
        final double[] displacementMetrics = displacementMetrics(applied);
        final BoundarySeparationDiagnostics diagnostics = new BoundarySeparationDiagnostics(conflicts,
            pairKeys(terminalViolations), detected, terminalViolations.size(), rounds, displacementMetrics[0],
            displacementMetrics[1], applied, 0.0, 0.0);
        if (!diagnostics.boundaryVerified() && !diagnostics.boundaryCovered()) {
            throw new BoundarySeparationException("Boundary separation did not cover every residual pair");
        }
        return new BoundarySeparationResult(current, diagnostics, terminalNodeResidual,
            new BoundarySeparationTimings(separationNanos, hullNanos, planNanos, applyNanos));
    }

    static LayoutPoint guardedMinimumSeparatingTranslation(final HullGeometry first, final HullGeometry second) {
        try {
            return HullIntersection.minimumSeparatingTranslation(first, second);
        }
        catch (final RuntimeException exception) {
            throw new BoundarySeparationException("Minimum separating translation failed", exception);
        }
    }

    static List<String> ancestorEscapePairKeys(final GraphProjection projection,
            final Map<EnclosureHullKey, HullGeometry> hulls) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(hulls, "hulls");
        final List<String> result = new ArrayList<String>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            if (enclosure.boundaryTier() == BoundaryTier.SUPPRESSED || !enclosure.parentHull().isPresent()) {
                continue;
            }
            final EnclosureHullKey child = enclosure.hullKey();
            final EnclosureHullKey parent = enclosure.parentHull().get();
            final HullGeometry childHull = hulls.get(child);
            final HullGeometry parentHull = hulls.get(parent);
            if (childHull == null || parentHull == null) {
                continue;
            }
            for (final LayoutPoint vertex : childHull.exactPolygon()) {
                if (!parentHull.contains(vertex)) {
                    result.add(CanonicalLayoutKeys.pair(child, parent));
                    break;
                }
            }
        }
        Collections.sort(result);
        return Collections.unmodifiableList(result);
    }

    private List<Violation> detect(final GraphProjection projection,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull,
            final Map<EnclosureHullKey, EnclosureHullKey> parents,
            final Map<EnclosureHullKey, HullGeometry> hulls) {
        final Map<String, EnclosureHullKey> enforcedByCanonical = new LinkedHashMap<String, EnclosureHullKey>();
        final Set<String> ambiguous = new LinkedHashSet<String>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            if (enclosure.boundaryTier() == BoundaryTier.SUPPRESSED) {
                continue;
            }
            final EnclosureHullKey hull = enclosure.hullKey();
            if (!hulls.containsKey(hull)) {
                continue;
            }
            final String canonical = CanonicalLayoutKeys.hull(hull);
            if (enforcedByCanonical.put(canonical, hull) != null) {
                ambiguous.add(canonical);
            }
        }
        final List<EnclosureHullKey> enforced = new ArrayList<EnclosureHullKey>();
        for (final Map.Entry<String, EnclosureHullKey> entry : enforcedByCanonical.entrySet()) {
            if (!ambiguous.contains(entry.getKey())) {
                enforced.add(entry.getValue());
            }
        }
        final Map<String, Violation> violations = new LinkedHashMap<String, Violation>();
        final List<EnclosureHullKey> sorted = new ArrayList<EnclosureHullKey>(enforced);
        Collections.sort(sorted, new Comparator<EnclosureHullKey>() {
            @Override
            public int compare(final EnclosureHullKey left, final EnclosureHullKey right) {
                final HullGeometry leftHull = hulls.get(left);
                final HullGeometry rightHull = hulls.get(right);
                int result = Double.compare(leftHull.minX(), rightHull.minX());
                if (result != 0) {
                    return result;
                }
                result = Double.compare(leftHull.minY(), rightHull.minY());
                if (result != 0) {
                    return result;
                }
                return CanonicalLayoutKeys.hull(left).compareTo(CanonicalLayoutKeys.hull(right));
            }
        });
        final List<EnclosureHullKey> active = new ArrayList<EnclosureHullKey>();
        for (final EnclosureHullKey current : sorted) {
            final HullGeometry currentHull = hulls.get(current);
            for (final Iterator<EnclosureHullKey> iterator = active.iterator(); iterator.hasNext();) {
                if (hulls.get(iterator.next()).maxX() < currentHull.minX()) {
                    iterator.remove();
                }
            }
            for (final EnclosureHullKey other : active) {
                final HullGeometry otherHull = hulls.get(other);
                if (!(otherHull.minY() <= currentHull.maxY() && currentHull.minY() <= otherHull.maxY())) {
                    continue;
                }
                if (related(parents, other, current)) {
                    continue;
                }
                final EnclosureHullKey first;
                final EnclosureHullKey second;
                if (CanonicalLayoutKeys.hull(other).compareTo(CanonicalLayoutKeys.hull(current)) <= 0) {
                    first = other;
                    second = current;
                }
                else {
                    first = current;
                    second = other;
                }
                final HullGeometry firstHull = hulls.get(first);
                final HullGeometry secondHull = hulls.get(second);
                final boolean crossing = HullIntersection.siblingOverlap(firstHull, secondHull);
                if (!crossing && !containsInclusive(firstHull, secondHull)
                        && !containsInclusive(secondHull, firstHull)) {
                    continue;
                }
                final LayoutPoint translation = guardedMinimumSeparatingTranslation(firstHull, secondHull);
                if (isZero(translation)) {
                    continue;
                }
                final BoundaryConflict.Kind kind = crossing ? BoundaryConflict.Kind.SIBLING_CROSSING
                    : BoundaryConflict.Kind.SIBLING_CONTAINMENT;
                violations.put(CanonicalLayoutKeys.pair(first, second), new Violation(first, second, kind,
                    Math.hypot(translation.x(), translation.y()), translation));
            }
            active.add(current);
        }
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            if (enclosure.boundaryTier() == BoundaryTier.SUPPRESSED || !enclosure.parentHull().isPresent()) {
                continue;
            }
            final EnclosureHullKey child = enclosure.hullKey();
            final EnclosureHullKey parent = enclosure.parentHull().get();
            if (!enforced.contains(child) || !enforced.contains(parent)) {
                continue;
            }
            final HullGeometry childHull = hulls.get(child);
            final HullGeometry parentHull = hulls.get(parent);
            for (final LayoutPoint vertex : childHull.exactPolygon()) {
                if (!parentHull.contains(vertex)) {
                    final EnclosureHullKey first;
                    final EnclosureHullKey second;
                    if (CanonicalLayoutKeys.hull(child).compareTo(CanonicalLayoutKeys.hull(parent)) <= 0) {
                        first = child;
                        second = parent;
                    }
                    else {
                        first = parent;
                        second = child;
                    }
                    violations.put(CanonicalLayoutKeys.pair(child, parent), new Violation(first, second,
                        BoundaryConflict.Kind.ANCESTOR_ESCAPE, 0.0, null));
                    break;
                }
            }
        }
        final List<Violation> ordered = new ArrayList<Violation>(violations.values());
        Collections.sort(ordered, new Comparator<Violation>() {
            @Override
            public int compare(final Violation left, final Violation right) {
                final int byPenetration = Double.compare(right.penetration, left.penetration);
                if (byPenetration != 0) {
                    return byPenetration;
                }
                return left.pairKey.compareTo(right.pairKey);
            }
        });
        return Collections.unmodifiableList(ordered);
    }

    private PlanOutcome plan(final GraphProjection projection,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull,
            final Map<EnclosureHullKey, HullGeometry> hulls, final LayoutPositions positions,
            final GeometryTextMetrics metrics, final List<PinProjection> pins,
            final Set<ProjectedNodeKey> pinnedNodes, final List<Violation> violations) {
        final PlanOutcome outcome = new PlanOutcome();
        for (final Violation violation : violations) {
            final BoundaryConflict.Reason reason = violation.kind == BoundaryConflict.Kind.ANCESTOR_ESCAPE
                ? BoundaryConflict.Reason.STRUCTURAL_ESCAPE : BoundaryConflict.Reason.IMMOVABLE_SIDES;
            outcome.conflicts.put(violation.pairKey, conflict(violation, reason,
                blockingPins(violation, enclosuresByHull, pins)));
        }
        return outcome;
    }

    private LayoutPositions applyRound(final GraphProjection projection,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull, final LayoutPositions positions,
            final PlanOutcome plan, final Map<String, LayoutPoint> field) {
        return positions;
    }

    private static List<BoundaryConflict> terminalConflicts(final List<Violation> violations,
            final PlanOutcome plan, final GraphProjection projection,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull, final List<PinProjection> pins) {
        final List<BoundaryConflict> result = new ArrayList<BoundaryConflict>();
        for (final Violation violation : violations) {
            final BoundaryConflict existing = plan.conflicts.get(violation.pairKey);
            if (existing != null) {
                result.add(existing);
                continue;
            }
            final BoundaryConflict.Reason reason = violation.kind == BoundaryConflict.Kind.ANCESTOR_ESCAPE
                ? BoundaryConflict.Reason.STRUCTURAL_ESCAPE : BoundaryConflict.Reason.IMMOVABLE_SIDES;
            result.add(conflict(violation, reason, blockingPins(violation, enclosuresByHull, pins)));
        }
        return Collections.unmodifiableList(result);
    }

    private static List<PinProjection> blockingPins(final Violation violation,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull, final List<PinProjection> pins) {
        final boolean sameMap = violation.first.mapReferenceId().equals(violation.second.mapReferenceId());
        final Set<ProjectedNodeKey> subtree = new LinkedHashSet<ProjectedNodeKey>();
        if (sameMap) {
            subtree.addAll(subtreeNodes(enclosuresByHull.get(violation.first), enclosuresByHull));
            subtree.addAll(subtreeNodes(enclosuresByHull.get(violation.second), enclosuresByHull));
        }
        final Set<PinProjection> unique = new LinkedHashSet<PinProjection>();
        for (final PinProjection pin : pins) {
            if (!pin.active()) {
                continue;
            }
            final ProjectedNodeKey node = pin.projectedNode().get();
            if (sameMap) {
                if (subtree.contains(node)) {
                    unique.add(pin);
                }
            }
            else {
                final MapReferenceId map = node.mapReferenceId();
                if (map.equals(violation.first.mapReferenceId())
                        || map.equals(violation.second.mapReferenceId())) {
                    unique.add(pin);
                }
            }
        }
        return new ArrayList<PinProjection>(unique);
    }

    private static Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull(final GraphProjection projection) {
        final Map<EnclosureHullKey, ProjectedEnclosure> result =
            new LinkedHashMap<EnclosureHullKey, ProjectedEnclosure>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            result.put(enclosure.hullKey(), enclosure);
        }
        return result;
    }

    private static Map<EnclosureHullKey, EnclosureHullKey> parents(final GraphProjection projection) {
        final Map<EnclosureHullKey, EnclosureHullKey> result =
            new LinkedHashMap<EnclosureHullKey, EnclosureHullKey>();
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            if (enclosure.parentHull().isPresent()) {
                result.put(enclosure.hullKey(), enclosure.parentHull().get());
            }
        }
        return result;
    }

    private static Set<ProjectedNodeKey> pinnedNodes(final List<PinProjection> pins) {
        final Set<ProjectedNodeKey> result = new LinkedHashSet<ProjectedNodeKey>();
        for (final PinProjection pin : pins) {
            if (pin.active()) {
                result.add(pin.projectedNode().get());
            }
        }
        return result;
    }

    private static boolean related(final Map<EnclosureHullKey, EnclosureHullKey> parents,
            final EnclosureHullKey first, final EnclosureHullKey second) {
        for (EnclosureHullKey current = parents.get(first); current != null; current = parents.get(current)) {
            if (current.equals(second)) {
                return true;
            }
        }
        for (EnclosureHullKey current = parents.get(second); current != null; current = parents.get(current)) {
            if (current.equals(first)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsInclusive(final HullGeometry outer, final HullGeometry inner) {
        for (final LayoutPoint vertex : inner.exactPolygon()) {
            if (!outer.contains(vertex)) {
                return false;
            }
        }
        return true;
    }

    private static List<ProjectedNodeKey> subtreeNodes(final ProjectedEnclosure enclosure,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull) {
        final List<ProjectedNodeKey> result = new ArrayList<ProjectedNodeKey>();
        collectSubtreeNodes(enclosure, enclosuresByHull, new LinkedHashSet<EnclosureHullKey>(), result);
        return result;
    }

    private static void collectSubtreeNodes(final ProjectedEnclosure enclosure,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull,
            final Set<EnclosureHullKey> visited, final List<ProjectedNodeKey> result) {
        if (enclosure == null || !visited.add(enclosure.hullKey())) {
            return;
        }
        result.addAll(enclosure.directNodes());
        for (final EnclosureHullKey child : enclosure.directEnclosures()) {
            collectSubtreeNodes(enclosuresByHull.get(child), enclosuresByHull, visited, result);
        }
    }

    private static BoundaryConflict conflict(final Violation violation, final BoundaryConflict.Reason reason,
            final List<PinProjection> pins) {
        return new BoundaryConflict(violation.first, violation.second, violation.kind, reason, pins);
    }

    private static boolean isZero(final LayoutPoint value) {
        return value == null || value.x() == 0.0 && value.y() == 0.0;
    }

    private static List<String> pairKeys(final List<Violation> violations) {
        final List<String> result = new ArrayList<String>(violations.size());
        for (final Violation violation : violations) {
            result.add(violation.pairKey);
        }
        return Collections.unmodifiableList(result);
    }

    private static Map<String, LayoutPoint> sortedNonZero(final Map<String, LayoutPoint> field) {
        final List<String> keys = new ArrayList<String>(field.keySet());
        Collections.sort(keys);
        final Map<String, LayoutPoint> result = new LinkedHashMap<String, LayoutPoint>();
        for (final String key : keys) {
            final LayoutPoint value = field.get(key);
            if (!isZero(value)) {
                result.put(key, value);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static double[] displacementMetrics(final Map<String, LayoutPoint> field) {
        if (field.isEmpty()) {
            return new double[] {0.0, 0.0};
        }
        double sumSquares = 0.0;
        double maximum = 0.0;
        for (final LayoutPoint value : field.values()) {
            final double squared = value.x() * value.x() + value.y() * value.y();
            sumSquares += squared;
            maximum = Math.max(maximum, Math.sqrt(squared));
        }
        return new double[] {Math.sqrt(sumSquares / field.size()), maximum};
    }

    private static final class Violation {
        private final EnclosureHullKey first;
        private final EnclosureHullKey second;
        private final BoundaryConflict.Kind kind;
        private final double penetration;
        private final LayoutPoint translation;
        private final String pairKey;

        private Violation(final EnclosureHullKey first, final EnclosureHullKey second,
                final BoundaryConflict.Kind kind, final double penetration, final LayoutPoint translation) {
            this.first = first;
            this.second = second;
            this.kind = kind;
            this.penetration = penetration;
            this.translation = translation;
            this.pairKey = CanonicalLayoutKeys.pair(first, second);
        }
    }

    private static final class PlanOutcome {
        private final Map<MapReferenceId, LayoutPoint> mapDeltas =
            new LinkedHashMap<MapReferenceId, LayoutPoint>();
        private final Map<ProjectedNodeKey, LayoutPoint> nodeFields =
            new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        private final Map<EnclosureHullKey, LayoutPoint> anchorFields =
            new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        private final Map<String, BoundaryConflict> conflicts = new LinkedHashMap<String, BoundaryConflict>();
        private final Set<String> movedPairs = new LinkedHashSet<String>();

        private boolean isEmpty() {
            return mapDeltas.isEmpty() && nodeFields.isEmpty() && anchorFields.isEmpty();
        }
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrection.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrectionShould.java
git commit -m "feat(graph-layout): detect boundary violations with terminal coverage"
```

## Task 8: Cross-map rigid map translation

**Implementer tier:** Advanced

**Lane:** correction

**Depends on:** 7

**Ownership:** freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrection.java, freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrectionShould.java, freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/MapTierCorrectionShould.java

**Validation:** gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrection.java:150-175` (the `plan` method) and `:176-180` (the `applyRound` stub)
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrectionShould.java` (append tests and helpers before the final closing brace)
- Delete: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/MapTierCorrectionShould.java`

**Interfaces:**
- Consumes: everything from Task 7; `PinProjection.active()/projectedNode()`, `MapReferenceId`.
- Produces: the cross-map policy contract `planCrossMap` and `rigidMaps`; the full `applyRound` accumulation (node fields, anchor fields, map deltas); `anchorDeltas` (identity until Task 9 has node fields). Map deltas: neither map rigid → `-t/2` and `+t/2`; only the first rigid → second `+t`; only the second rigid → first `-t`; both rigid → one `IMMOVABLE_SIDES` conflict.

- [ ] **Step 1: Add the failing cross-map tests**

Append these members to `BoundarySeparationCorrectionShould`, immediately before its final closing brace. They reuse the helpers added by Task 7.

```java
    @Test
    public void translatesBothFreeMapsByHalfTheMinimumTranslation() {
        final CrossMapFixture fixture = crossMapFixture(false, false);

        final BoundarySeparationResult result = apply(fixture);

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().boundaryVerified()).isTrue();
        assertThat(result.positions().nodes().get(fixture.firstNode)).isEqualTo(LayoutPoint.of(-5.0, 0.0));
        assertThat(result.positions().nodes().get(fixture.secondNode)).isEqualTo(LayoutPoint.of(43.0, 0.0));
        assertThat(result.positions().anchors().get(fixture.firstHull)).isEqualTo(LayoutPoint.of(-5.0, 0.0));
        assertThat(result.positions().anchors().get(fixture.secondHull)).isEqualTo(LayoutPoint.of(43.0, 0.0));
    }

    @Test
    public void movesOnlyTheOtherMapWhenTheFirstMapHasAnActivePin() {
        final CrossMapFixture fixture = crossMapFixture(true, false);

        final BoundarySeparationResult result = apply(fixture);

        assertThat(result.positions().nodes().get(fixture.firstNode)).isEqualTo(LayoutPoint.of(0.0, 0.0));
        assertThat(result.positions().nodes().get(fixture.secondNode)).isEqualTo(LayoutPoint.of(48.0, 0.0));
        assertThat(result.positions().anchors().get(fixture.firstHull)).isEqualTo(LayoutPoint.of(0.0, 0.0));
        assertThat(result.positions().anchors().get(fixture.secondHull)).isEqualTo(LayoutPoint.of(48.0, 0.0));
        assertThat(result.diagnostics().rounds()).isEqualTo(1);
    }

    @Test
    public void movesOnlyTheFirstMapWhenTheSecondMapHasAnActivePin() {
        final CrossMapFixture fixture = crossMapFixture(false, true);

        final BoundarySeparationResult result = apply(fixture);

        assertThat(result.positions().nodes().get(fixture.firstNode)).isEqualTo(LayoutPoint.of(-10.0, 0.0));
        assertThat(result.positions().nodes().get(fixture.secondNode)).isEqualTo(LayoutPoint.of(38.0, 0.0));
        assertThat(result.diagnostics().rounds()).isEqualTo(1);
    }

    @Test
    public void bothRigidMapsReportOneImmovableConflictWithBothPins() {
        final CrossMapFixture fixture = crossMapFixture(true, true);

        final BoundarySeparationResult result = apply(fixture);

        assertThat(result.positions()).isEqualTo(fixture.positions);
        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().hullResidualViolations()).isEqualTo(1);
        assertThat(result.diagnostics().boundaryCovered()).isTrue();
        assertThat(result.diagnostics().conflicts()).hasSize(1);
        assertThat(result.diagnostics().conflicts().get(0).reason())
            .isEqualTo(BoundaryConflict.Reason.IMMOVABLE_SIDES);
        assertThat(result.diagnostics().conflicts().get(0).blockingPins()).hasSize(2);
    }

    @Test
    public void dormantPinsDoNotCreateRigidity() {
        final CrossMapFixture fixture = crossMapFixture(false, false);
        final PinProjection dormant = PinProjection.dormant(PinRecord.of(
            fixture.firstNode.source().persistedReference().get(), 0.0, 0.0, Collections.<UnknownXml>emptyList()));
        final BoundarySeparationResult result = new BoundarySeparationCorrection().apply(fixture.projection,
            fixture.positions, METRICS, Arrays.asList(dormant));

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.positions().nodes().get(fixture.firstNode)).isEqualTo(LayoutPoint.of(-5.0, 0.0));
        assertThat(result.positions().nodes().get(fixture.secondNode)).isEqualTo(LayoutPoint.of(43.0, 0.0));
    }

    @Test
    public void translatesTheWholeFreeMapForANonRootCrossMapPair() {
        final MapReferenceId mapOne = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
        final MapReferenceId mapTwo = MapReferenceId.of("00000000-0000-0000-0000-000000000002");
        final ProjectedNodeKey a = key(mapOne, "a-node");
        final ProjectedNodeKey c = key(mapOne, "c-node");
        final ProjectedNodeKey b = key(mapTwo, "b-node");
        final ProjectedNodeKey d = key(mapTwo, "d-node");
        final EnclosureHullKey rootOne = hull(mapOne, "root");
        final EnclosureHullKey aHull = hull(mapOne, "a");
        final EnclosureHullKey cHull = hull(mapOne, "c");
        final EnclosureHullKey rootTwo = hull(mapTwo, "root");
        final EnclosureHullKey bHull = hull(mapTwo, "b");
        final EnclosureHullKey dHull = hull(mapTwo, "d");
        final GraphProjection projection = projection(Arrays.asList(a, c, b, d), Arrays.asList(
            suppressedRoot(rootOne, Arrays.asList(aHull, cHull)),
            child(aHull, "a", rootOne, Collections.singletonList(a)),
            child(cHull, "c", rootOne, Collections.singletonList(c)),
            suppressedRoot(rootTwo, Arrays.asList(bHull, dHull)),
            child(bHull, "b", rootTwo, Collections.singletonList(b)),
            child(dHull, "d", rootTwo, Collections.singletonList(d))));
        final LayoutPositions positions = positions(
            Arrays.asList(nodeEntry(a, 0.0, 0.0), nodeEntry(c, 500.0, 0.0), nodeEntry(b, 38.0, 0.0),
                nodeEntry(d, 500.0, 500.0)),
            Arrays.asList(anchorEntry(rootOne, 0.0, 0.0), anchorEntry(aHull, 0.0, 0.0),
                anchorEntry(cHull, 500.0, 0.0), anchorEntry(rootTwo, 38.0, 0.0), anchorEntry(bHull, 38.0, 0.0),
                anchorEntry(dHull, 500.0, 500.0)));

        final BoundarySeparationResult result = new BoundarySeparationCorrection().apply(projection, positions,
            METRICS, Collections.<PinProjection>emptyList());

        assertThat(result.diagnostics().hullViolationsDetected()).isEqualTo(1);
        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.positions().nodes().get(a)).isEqualTo(LayoutPoint.of(-5.0, 0.0));
        assertThat(result.positions().nodes().get(c)).isEqualTo(LayoutPoint.of(495.0, 0.0));
        assertThat(result.positions().nodes().get(b)).isEqualTo(LayoutPoint.of(43.0, 0.0));
        assertThat(result.positions().nodes().get(d)).isEqualTo(LayoutPoint.of(505.0, 500.0));
        assertThat(result.positions().anchors().get(cHull)).isEqualTo(LayoutPoint.of(495.0, 0.0));
        assertThat(result.positions().anchors().get(dHull)).isEqualTo(LayoutPoint.of(505.0, 500.0));
    }

    @Test
    public void accumulatesAllPairDeltasFromTheRoundSnapshot() {
        final List<MapReferenceId> maps = Arrays.asList(
            MapReferenceId.of("00000000-0000-0000-0000-000000000001"),
            MapReferenceId.of("00000000-0000-0000-0000-000000000002"),
            MapReferenceId.of("00000000-0000-0000-0000-000000000003"));
        final double side = 38.0;
        final double height = side * Math.sqrt(3.0) / 2.0;
        final double[][] points = {{0.0, 0.0}, {side, 0.0}, {side / 2.0, height}};
        final List<ProjectedNodeKey> nodes = new ArrayList<ProjectedNodeKey>();
        final List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        final List<Map.Entry<ProjectedNodeKey, LayoutPoint>> nodeEntries =
            new ArrayList<Map.Entry<ProjectedNodeKey, LayoutPoint>>();
        final List<Map.Entry<EnclosureHullKey, LayoutPoint>> anchorEntries =
            new ArrayList<Map.Entry<EnclosureHullKey, LayoutPoint>>();
        for (int index = 0; index < maps.size(); index++) {
            final ProjectedNodeKey node = key(maps.get(index), "n");
            final EnclosureHullKey root = hull(maps.get(index), "root");
            nodes.add(node);
            enclosures.add(root(root, "root", Collections.singletonList(node)));
            nodeEntries.add(nodeEntry(node, points[index][0], points[index][1]));
            anchorEntries.add(anchorEntry(root, points[index][0], points[index][1]));
        }

        final BoundarySeparationResult result = new BoundarySeparationCorrection().apply(
            projection(nodes, enclosures), positions(nodeEntries, anchorEntries), METRICS,
            Collections.<PinProjection>emptyList());

        assertThat(result.diagnostics().hullViolationsDetected()).isEqualTo(3);
        assertThat(result.diagnostics().boundaryVerified()).isTrue();
        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.positions().nodes().get(nodes.get(0)))
            .isEqualTo(LayoutPoint.of(-8.993321412524974, -3.993321412524973));
        assertThat(result.positions().nodes().get(nodes.get(1)))
            .isEqualTo(LayoutPoint.of(46.99332141252498, -3.993321412524976));
        assertThat(result.positions().nodes().get(nodes.get(2)))
            .isEqualTo(LayoutPoint.of(19.0, 40.895608168858615));
        for (int index = 0; index < maps.size(); index++) {
            final ProjectedNodeKey node = nodes.get(index);
            final EnclosureHullKey root = enclosures.get(index).hullKey();
            assertThat(difference(result.positions().nodes().get(node),
                fixtureNodePoint(points[index][0], points[index][1])))
                    .as("map %s translates rigidly", maps.get(index))
                    .isEqualTo(difference(result.positions().anchors().get(root),
                        fixtureAnchorPoint(points[index][0], points[index][1])));
        }
    }

    @Test
    public void reportsOneConflictPerRigidMapPairInViolationOrder() {
        final List<MapReferenceId> maps = Arrays.asList(
            MapReferenceId.of("00000000-0000-0000-0000-000000000001"),
            MapReferenceId.of("00000000-0000-0000-0000-000000000002"),
            MapReferenceId.of("00000000-0000-0000-0000-000000000003"));
        final double side = 38.0;
        final double height = side * Math.sqrt(3.0) / 2.0;
        final double[][] points = {{0.0, 0.0}, {side, 0.0}, {side / 2.0, height}};
        final List<ProjectedNodeKey> nodes = new ArrayList<ProjectedNodeKey>();
        final List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        final List<PinProjection> pins = new ArrayList<PinProjection>();
        final List<Map.Entry<ProjectedNodeKey, LayoutPoint>> nodeEntries =
            new ArrayList<Map.Entry<ProjectedNodeKey, LayoutPoint>>();
        final List<Map.Entry<EnclosureHullKey, LayoutPoint>> anchorEntries =
            new ArrayList<Map.Entry<EnclosureHullKey, LayoutPoint>>();
        for (int index = 0; index < maps.size(); index++) {
            final ProjectedNodeKey node = key(maps.get(index), "n");
            final EnclosureHullKey root = hull(maps.get(index), "root");
            nodes.add(node);
            enclosures.add(root(root, "root", Collections.singletonList(node)));
            pins.add(pin(node, points[index][0], points[index][1]));
            nodeEntries.add(nodeEntry(node, points[index][0], points[index][1]));
            anchorEntries.add(anchorEntry(root, points[index][0], points[index][1]));
        }
        final LayoutPositions positions = positions(nodeEntries, anchorEntries);
        final GraphProjection projection = projection(nodes, enclosures);

        final BoundarySeparationResult result = new BoundarySeparationCorrection().apply(projection, positions,
            METRICS, pins);

        assertThat(result.positions()).isEqualTo(positions);
        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().hullResidualViolations()).isEqualTo(3);
        assertThat(result.diagnostics().conflicts()).hasSize(3);
        assertThat(result.diagnostics().conflicts().get(0).pairKey()).isEqualTo(
            CanonicalLayoutKeys.pair(hull(maps.get(1), "root"), hull(maps.get(2), "root")));
        assertThat(result.diagnostics().conflicts().get(1).pairKey()).isEqualTo(
            CanonicalLayoutKeys.pair(hull(maps.get(0), "root"), hull(maps.get(2), "root")));
        assertThat(result.diagnostics().conflicts().get(2).pairKey()).isEqualTo(
            CanonicalLayoutKeys.pair(hull(maps.get(0), "root"), hull(maps.get(1), "root")));
        for (final BoundaryConflict conflict : result.diagnostics().conflicts()) {
            assertThat(conflict.reason()).isEqualTo(BoundaryConflict.Reason.IMMOVABLE_SIDES);
            assertThat(conflict.blockingPins()).hasSize(2);
        }
        assertThat(result.diagnostics().boundaryCovered()).isTrue();
    }

    private static BoundarySeparationResult apply(CrossMapFixture fixture) {
        return new BoundarySeparationCorrection().apply(fixture.projection, fixture.positions, METRICS,
            fixture.pins);
    }

    private static CrossMapFixture crossMapFixture(boolean firstPinned, boolean secondPinned) {
        final MapReferenceId mapOne = MapReferenceId.of("00000000-0000-0000-0000-000000000001");
        final MapReferenceId mapTwo = MapReferenceId.of("00000000-0000-0000-0000-000000000002");
        final ProjectedNodeKey firstNode = key(mapOne, "n");
        final ProjectedNodeKey secondNode = key(mapTwo, "n");
        final EnclosureHullKey firstHull = hull(mapOne, "root");
        final EnclosureHullKey secondHull = hull(mapTwo, "root");
        final GraphProjection projection = projection(Arrays.asList(firstNode, secondNode),
            Arrays.asList(root(firstHull, "root", Collections.singletonList(firstNode)),
                root(secondHull, "root", Collections.singletonList(secondNode))));
        final LayoutPositions positions = positions(
            Arrays.asList(nodeEntry(firstNode, 0.0, 0.0), nodeEntry(secondNode, 38.0, 0.0)),
            Arrays.asList(anchorEntry(firstHull, 0.0, 0.0), anchorEntry(secondHull, 38.0, 0.0)));
        final List<PinProjection> pins = new ArrayList<PinProjection>();
        if (firstPinned) {
            pins.add(pin(firstNode, 0.0, 0.0));
        }
        if (secondPinned) {
            pins.add(pin(secondNode, 38.0, 0.0));
        }
        return new CrossMapFixture(projection, positions, pins, firstNode, secondNode, firstHull, secondHull);
    }

    private static ProjectedEnclosure suppressedRoot(EnclosureHullKey hull, List<EnclosureHullKey> children) {
        return ProjectedEnclosure.of(hull, hull.endpointKeys(),
            Collections.singletonList(SafeNodeLabel.of("root", "root")), "m",
            Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(), children, true,
            BoundaryTier.SUPPRESSED);
    }

    private static ProjectedEnclosure child(EnclosureHullKey hull, String label, EnclosureHullKey parent,
            List<ProjectedNodeKey> nodes) {
        return ProjectedEnclosure.of(hull, hull.endpointKeys(),
            Collections.singletonList(SafeNodeLabel.of(label, label)), "m", Optional.of(parent), nodes,
            Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE);
    }

    private static ProjectedNodeKey key(MapReferenceId map, String id) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id))));
    }

    private static EnclosureHullKey hull(MapReferenceId map, String id) {
        return EnclosureHullKey.of(Collections.singletonList(
            EnclosureKey.of(SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id))))));
    }

    private static LayoutPoint fixtureNodePoint(double x, double y) {
        return LayoutPoint.of(x, y);
    }

    private static LayoutPoint fixtureAnchorPoint(double x, double y) {
        return LayoutPoint.of(x, y);
    }

    private static LayoutPoint difference(LayoutPoint after, LayoutPoint before) {
        return LayoutPoint.of(after.x() - before.x(), after.y() - before.y());
    }

    private static final class CrossMapFixture {
        final GraphProjection projection;
        final LayoutPositions positions;
        final List<PinProjection> pins;
        final ProjectedNodeKey firstNode;
        final ProjectedNodeKey secondNode;
        final EnclosureHullKey firstHull;
        final EnclosureHullKey secondHull;

        CrossMapFixture(GraphProjection projection, LayoutPositions positions, List<PinProjection> pins,
                ProjectedNodeKey firstNode, ProjectedNodeKey secondNode, EnclosureHullKey firstHull,
                EnclosureHullKey secondHull) {
            this.projection = projection;
            this.positions = positions;
            this.pins = pins;
            this.firstNode = firstNode;
            this.secondNode = secondNode;
            this.firstHull = firstHull;
            this.secondHull = secondHull;
        }
    }
```

- [ ] **Step 2: Run the tests and confirm the new ones fail**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould`
Expected: FAIL — the cross-map tests report `rounds == 0` and unchanged positions because no displacement policy exists yet.

- [ ] **Step 3: Dispatch ancestor, cross-map and same-map pairs in `plan`**

Replace the whole `plan` method in `BoundarySeparationCorrection.java` with:

```java
    private PlanOutcome plan(final GraphProjection projection,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull,
            final Map<EnclosureHullKey, HullGeometry> hulls, final LayoutPositions positions,
            final GeometryTextMetrics metrics, final List<PinProjection> pins,
            final Set<ProjectedNodeKey> pinnedNodes, final List<Violation> violations) {
        final PlanOutcome outcome = new PlanOutcome();
        final Set<MapReferenceId> rigidMaps = rigidMaps(pins);
        for (final Violation violation : violations) {
            if (violation.kind == BoundaryConflict.Kind.ANCESTOR_ESCAPE) {
                outcome.conflicts.put(violation.pairKey, conflict(violation,
                    BoundaryConflict.Reason.STRUCTURAL_ESCAPE,
                    blockingPins(violation, enclosuresByHull, pins)));
                continue;
            }
            if (!violation.first.mapReferenceId().equals(violation.second.mapReferenceId())) {
                planCrossMap(violation, rigidMaps, enclosuresByHull, pins, outcome);
            }
            else {
                outcome.conflicts.put(violation.pairKey, conflict(violation,
                    BoundaryConflict.Reason.IMMOVABLE_SIDES,
                    blockingPins(violation, enclosuresByHull, pins)));
            }
        }
        return outcome;
    }
```

- [ ] **Step 4: Insert the cross-map policy immediately after `plan`**

Insert these methods directly after the `plan` method:

```java
    private static void planCrossMap(final Violation violation, final Set<MapReferenceId> rigidMaps,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull, final List<PinProjection> pins,
            final PlanOutcome outcome) {
        final boolean firstRigid = rigidMaps.contains(violation.first.mapReferenceId());
        final boolean secondRigid = rigidMaps.contains(violation.second.mapReferenceId());
        if (firstRigid && secondRigid) {
            outcome.conflicts.put(violation.pairKey, conflict(violation,
                BoundaryConflict.Reason.IMMOVABLE_SIDES, blockingPins(violation, enclosuresByHull, pins)));
            return;
        }
        if (firstRigid) {
            addMapDelta(outcome.mapDeltas, violation.second.mapReferenceId(), violation.translation);
        }
        else if (secondRigid) {
            addMapDelta(outcome.mapDeltas, violation.first.mapReferenceId(), negate(violation.translation));
        }
        else {
            addMapDelta(outcome.mapDeltas, violation.first.mapReferenceId(),
                scale(negate(violation.translation), 0.5));
            addMapDelta(outcome.mapDeltas, violation.second.mapReferenceId(),
                scale(violation.translation, 0.5));
        }
        outcome.movedPairs.add(violation.pairKey);
    }

    private static Set<MapReferenceId> rigidMaps(final List<PinProjection> pins) {
        final Set<MapReferenceId> result = new LinkedHashSet<MapReferenceId>();
        for (final PinProjection pin : pins) {
            if (pin.active()) {
                result.add(pin.projectedNode().get().mapReferenceId());
            }
        }
        return result;
    }

    private static void addMapDelta(final Map<MapReferenceId, LayoutPoint> deltas, final MapReferenceId map,
            final LayoutPoint delta) {
        final LayoutPoint previous = deltas.get(map);
        deltas.put(map, previous == null ? delta : add(previous, delta));
    }
```

- [ ] **Step 5: Replace the `applyRound` stub with the full accumulation and add its helpers**

Replace the `applyRound` stub with:

```java
    private LayoutPositions applyRound(final GraphProjection projection,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull, final LayoutPositions positions,
            final PlanOutcome plan, final Map<String, LayoutPoint> field) {
        final Map<EnclosureHullKey, LayoutPoint> anchorDeltas = anchorDeltas(projection, enclosuresByHull, plan);
        final Map<ProjectedNodeKey, LayoutPoint> nodes = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
        for (final Map.Entry<ProjectedNodeKey, LayoutPoint> entry : positions.nodes().entrySet()) {
            final ProjectedNodeKey key = entry.getKey();
            final LayoutPoint delta = sum(plan.nodeFields.get(key), plan.mapDeltas.get(key.mapReferenceId()));
            nodes.put(key, translated(entry.getValue(), delta));
            if (!isZero(delta)) {
                addField(field, CanonicalLayoutKeys.nodeField(key), delta);
            }
        }
        final Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        for (final Map.Entry<EnclosureHullKey, LayoutPoint> entry : positions.anchors().entrySet()) {
            final EnclosureHullKey key = entry.getKey();
            final LayoutPoint delta = sum(anchorDeltas.get(key), plan.mapDeltas.get(key.mapReferenceId()));
            anchors.put(key, translated(entry.getValue(), delta));
            if (!isZero(delta)) {
                addField(field, CanonicalLayoutKeys.anchorField(key), delta);
            }
        }
        return LayoutPositions.of(nodes, anchors);
    }

    private static Map<EnclosureHullKey, LayoutPoint> anchorDeltas(final GraphProjection projection,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull, final PlanOutcome plan) {
        final Map<EnclosureHullKey, LayoutPoint> result = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
        for (final Map.Entry<EnclosureHullKey, LayoutPoint> entry : plan.anchorFields.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            final EnclosureHullKey hull = enclosure.hullKey();
            if (result.containsKey(hull)) {
                continue;
            }
            double sumX = 0.0;
            double sumY = 0.0;
            int count = 0;
            for (final ProjectedNodeKey node : subtreeNodes(enclosure, enclosuresByHull)) {
                final LayoutPoint delta = plan.nodeFields.get(node);
                if (delta != null && !isZero(delta)) {
                    sumX += delta.x();
                    sumY += delta.y();
                    count++;
                }
            }
            if (count > 0) {
                result.put(hull, LayoutPoint.of(sumX / count, sumY / count));
            }
        }
        return result;
    }

    private static <K> void addVector(final Map<K, LayoutPoint> field, final K key, final LayoutPoint delta) {
        final LayoutPoint previous = field.get(key);
        field.put(key, previous == null ? delta : add(previous, delta));
    }

    private static void addField(final Map<String, LayoutPoint> field, final String key,
            final LayoutPoint delta) {
        final LayoutPoint previous = field.get(key);
        field.put(key, previous == null ? delta : add(previous, delta));
    }

    private static LayoutPoint sum(final LayoutPoint first, final LayoutPoint second) {
        if (first == null) {
            return second == null ? LayoutPoint.of(0.0, 0.0) : second;
        }
        return second == null ? first : add(first, second);
    }

    private static LayoutPoint translated(final LayoutPoint point, final LayoutPoint delta) {
        return isZero(delta) ? point : add(point, delta);
    }

    private static LayoutPoint add(final LayoutPoint first, final LayoutPoint second) {
        return LayoutPoint.of(first.x() + second.x(), first.y() + second.y());
    }

    private static LayoutPoint negate(final LayoutPoint value) {
        return LayoutPoint.of(-value.x(), -value.y());
    }

    private static LayoutPoint scale(final LayoutPoint value, final double factor) {
        return LayoutPoint.of(value.x() * factor, value.y() * factor);
    }
```

- [ ] **Step 6: Delete the obsolete MapTierCorrection test**

Delete `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/MapTierCorrectionShould.java`. Its cross-map cases are now covered by the node-based tests added above and its same-map cases belong to the new component's unit tests. Do not delete `MapTierCorrection.java` itself; Task 13 migrates the production caller.

- [ ] **Step 7: Run the tests and confirm they pass**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould`
Expected: PASS, 15 tests.

- [ ] **Step 8: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrection.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrectionShould.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/MapTierCorrectionShould.java
git commit -m "feat(graph-layout): translate whole maps rigidly for cross-map boundary pairs"
```

## Task 9: Same-map support, cap sets, and both-sides/full candidates

**Implementer tier:** Capable

**Lane:** correction

**Depends on:** 8

**Ownership:** freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrection.java, freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrectionShould.java

**Validation:** gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrection.java` (`plan` same-map branch; insert methods after `planCrossMap`/`rigidMaps`/`addMapDelta`; append nested types before the final closing brace)
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrectionShould.java` (append tests and helpers before the final closing brace)

**Interfaces:**
- Consumes: everything from Tasks 7–8; `ProjectedEnclosure.directNodes()/directEnclosures()/labels()/boundaryTier()`, `SafeNodeLabel.displayText()`, `GeometryTextMetrics.measure(String, BoundaryTier)`, `GraphProjection.prominence()`, `NodeProminence.scale()`.
- Produces: `planSameMap`, the recursive `Traversal` cap-set traversal (`movedNodes`, `movedAnchors`, `pins` with local `depth`), `support(H, u)`/`nodeContribution`/`polySupport`/`labelSize`, and `Candidate` selection for distributions 1 (both-sides half), 2 (first-only full) and 3 (second-only full). Distribution 4–5 and the tie/complementary tests arrive in Task 10; until then `selectCandidate` returns `null` after candidate 3, which produces `IMMOVABLE_SIDES` for those cases.

- [ ] **Step 1: Add the failing same-map tests**

Append these members to `BoundarySeparationCorrectionShould`, immediately before its final closing brace.

```java
    @Test
    public void resolvesTheAttemptFiveBothSidesHalfCase() {
        final ProjectedNodeKey aFree = key("a-free");
        final ProjectedNodeKey aPin = key("a-pin");
        final ProjectedNodeKey bFree = key("b-free");
        final ProjectedNodeKey bPin = key("b-pin");
        final SiblingFixture fixture = siblingFixture(Arrays.asList(aFree, aPin), Arrays.asList(bFree, bPin),
            Arrays.asList(nodeEntry(aFree, 0.0, 0.0), nodeEntry(aPin, -6.0, 30.0), nodeEntry(bFree, 38.0, 0.0),
                nodeEntry(bPin, 44.0, 30.0)),
            Arrays.asList(pin(aPin, -6.0, 30.0), pin(bPin, 44.0, 30.0)));

        final BoundarySeparationResult result = apply(fixture);

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().boundaryVerified()).isTrue();
        assertThat(result.appliedDisplacements().get(CanonicalLayoutKeys.nodeField(aFree)))
            .isEqualTo(LayoutPoint.of(-5.0, 0.0));
        assertThat(result.appliedDisplacements().get(CanonicalLayoutKeys.nodeField(bFree)))
            .isEqualTo(LayoutPoint.of(5.0, 0.0));
        assertThat(result.positions().nodes().get(aPin)).isEqualTo(LayoutPoint.of(-6.0, 30.0));
        assertThat(result.positions().nodes().get(bPin)).isEqualTo(LayoutPoint.of(44.0, 30.0));
        assertThat(result.positions().nodes().get(aFree)).isEqualTo(LayoutPoint.of(-5.0, 0.0));
        assertThat(result.positions().nodes().get(bFree)).isEqualTo(LayoutPoint.of(43.0, 0.0));
    }

    @Test
    public void resolvesAPinnedMaximumContributorWithTheSecondSideFullCandidate() {
        final ProjectedNodeKey aPin = key("a-pin");
        final ProjectedNodeKey bFree = key("b-free");
        final SiblingFixture fixture = siblingFixture(Collections.singletonList(aPin),
            Collections.singletonList(bFree),
            Arrays.asList(nodeEntry(aPin, 0.0, 0.0), nodeEntry(bFree, 38.0, 0.0)),
            Collections.singletonList(pin(aPin, 0.0, 0.0)));

        final BoundarySeparationResult result = apply(fixture);

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().boundaryVerified()).isTrue();
        assertThat(result.appliedDisplacements().get(CanonicalLayoutKeys.nodeField(bFree)))
            .isEqualTo(LayoutPoint.of(10.0, 0.0));
        assertThat(result.positions().nodes().get(aPin)).isEqualTo(LayoutPoint.of(0.0, 0.0));
        assertThat(result.positions().nodes().get(bFree)).isEqualTo(LayoutPoint.of(48.0, 0.0));
        assertThat(result.appliedDisplacements().keySet())
            .doesNotContain(CanonicalLayoutKeys.nodeField(aPin));
    }

    @Test
    public void recursesIntoNestedChildHullsWhenMoving() {
        final ProjectedNodeKey a1Free = key("a1-free");
        final ProjectedNodeKey bFree = key("b-free");
        final GraphProjection projection = nestedProjection(Arrays.asList(a1Free, bFree),
            Collections.singletonList(a1Free), Collections.singletonList(bFree));
        final LayoutPositions positions = positions(
            Arrays.asList(nodeEntry(a1Free, 0.0, 0.0), nodeEntry(bFree, 54.0, 0.0)),
            Arrays.asList(anchorEntry(hull("root"), 0.0, 0.0), anchorEntry(hull("a"), 0.0, 0.0),
                anchorEntry(hull("a1"), 0.0, 0.0), anchorEntry(hull("b"), 54.0, 0.0)));

        final BoundarySeparationResult result = new BoundarySeparationCorrection().apply(projection, positions,
            METRICS, Collections.<PinProjection>emptyList());

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().boundaryVerified()).isTrue();
        assertThat(result.appliedDisplacements().get(CanonicalLayoutKeys.nodeField(a1Free)))
            .isEqualTo(LayoutPoint.of(-5.0, 0.0));
        assertThat(result.appliedDisplacements().get(CanonicalLayoutKeys.nodeField(bFree)))
            .isEqualTo(LayoutPoint.of(5.0, 0.0));
    }

    @Test
    public void movesAnEmptyEnclosureByItsAnchor() {
        final ProjectedNodeKey aFree = key("a-free");
        final EnclosureHullKey rootHull = hull("root");
        final EnclosureHullKey aHull = hull("a");
        final EnclosureHullKey bHull = hull("b");
        final GraphProjection projection = projection(Collections.singletonList(aFree),
            Arrays.asList(parent(rootHull, "root", Arrays.asList(aHull, bHull)),
                child(aHull, "a", rootHull, Collections.singletonList(aFree)),
                child(bHull, "B", rootHull, Collections.<ProjectedNodeKey>emptyList())));
        final LayoutPositions positions = positions(Collections.singletonList(nodeEntry(aFree, 0.0, 0.0)),
            Arrays.asList(anchorEntry(rootHull, 10.0, 0.0), anchorEntry(aHull, 0.0, 0.0),
                anchorEntry(bHull, 20.0, 0.0)));
        final HullGeometry rawFirst = new org.freeplane.plugin.graph.geometry.GraphGeometryEngine()
            .computeHulls(projection, positions, METRICS).hulls().get(aHull);
        final HullGeometry rawSecond = new org.freeplane.plugin.graph.geometry.GraphGeometryEngine()
            .computeHulls(projection, positions, METRICS).hulls().get(bHull);
        final LayoutPoint translation = HullIntersection.minimumSeparatingTranslation(rawFirst, rawSecond);

        final BoundarySeparationResult result = new BoundarySeparationCorrection().apply(projection, positions,
            METRICS, Collections.<PinProjection>emptyList());

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().boundaryVerified()).isTrue();
        assertThat(result.appliedDisplacements().get(CanonicalLayoutKeys.anchorField(bHull)))
            .isEqualTo(LayoutPoint.of(translation.x() * 0.5, translation.y() * 0.5));
        assertThat(result.appliedDisplacements().get(CanonicalLayoutKeys.nodeField(aFree)))
            .isEqualTo(LayoutPoint.of(-translation.x() * 0.5, -translation.y() * 0.5));
        assertThat(result.positions().anchors().get(bHull)).isEqualTo(LayoutPoint.of(
            20.0 + translation.x() * 0.5, translation.y() * 0.5));
    }

    @Test
    public void movesEveryContributorInTheCapSetByTheSameVector() {
        final ProjectedNodeKey aLow = key("a-low");
        final ProjectedNodeKey aHigh = key("a-high");
        final ProjectedNodeKey bFree = key("b-free");
        final SiblingFixture fixture = siblingFixture(Arrays.asList(aLow, aHigh),
            Collections.singletonList(bFree),
            Arrays.asList(nodeEntry(aLow, 0.0, 0.0), nodeEntry(aHigh, 0.0, 10.0), nodeEntry(bFree, 38.0, 0.0)),
            Collections.<PinProjection>emptyList());

        final BoundarySeparationResult result = apply(fixture);

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.appliedDisplacements().get(CanonicalLayoutKeys.nodeField(aLow)))
            .isEqualTo(LayoutPoint.of(-5.0, 0.0));
        assertThat(result.appliedDisplacements().get(CanonicalLayoutKeys.nodeField(aHigh)))
            .isEqualTo(LayoutPoint.of(-5.0, 0.0));
    }

    private static BoundarySeparationResult apply(SiblingFixture fixture) {
        return new BoundarySeparationCorrection().apply(fixture.projection, fixture.positions, METRICS,
            fixture.pins);
    }

    private static SiblingFixture siblingFixture(List<ProjectedNodeKey> aNodes, List<ProjectedNodeKey> bNodes,
            List<Map.Entry<ProjectedNodeKey, LayoutPoint>> nodeEntries, List<PinProjection> pins) {
        final EnclosureHullKey rootHull = hull("root");
        final EnclosureHullKey aHull = hull("a");
        final EnclosureHullKey bHull = hull("b");
        final List<ProjectedNodeKey> allNodes = new ArrayList<ProjectedNodeKey>(aNodes);
        allNodes.addAll(bNodes);
        final List<Map.Entry<EnclosureHullKey, LayoutPoint>> anchorEntries =
            new ArrayList<Map.Entry<EnclosureHullKey, LayoutPoint>>();
        anchorEntries.add(anchorEntry(rootHull, 0.0, 0.0));
        anchorEntries.add(anchorEntry(aHull, 0.0, 0.0));
        anchorEntries.add(anchorEntry(bHull, 38.0, 0.0));
        final GraphProjection projection = projection(allNodes,
            Arrays.asList(parent(rootHull, "root", Arrays.asList(aHull, bHull)),
                child(aHull, "a", rootHull, aNodes), child(bHull, "b", rootHull, bNodes)));
        return new SiblingFixture(projection, positions(nodeEntries, anchorEntries), pins, aHull, bHull);
    }

    private static GraphProjection nestedProjection(List<ProjectedNodeKey> allNodes,
            List<ProjectedNodeKey> a1Nodes, List<ProjectedNodeKey> bNodes) {
        final EnclosureHullKey rootHull = hull("root");
        final EnclosureHullKey aHull = hull("a");
        final EnclosureHullKey a1Hull = hull("a1");
        final EnclosureHullKey bHull = hull("b");
        return projection(allNodes,
            Arrays.asList(parent(rootHull, "root", Arrays.asList(aHull, bHull)),
                nestedChild(aHull, "a", rootHull, Collections.singletonList(a1Hull)),
                child(a1Hull, "a1", aHull, a1Nodes), child(bHull, "b", rootHull, bNodes)));
    }

    private static ProjectedEnclosure nestedChild(EnclosureHullKey hull, String label, EnclosureHullKey parent,
            List<EnclosureHullKey> children) {
        return ProjectedEnclosure.of(hull, hull.endpointKeys(),
            Collections.singletonList(SafeNodeLabel.of(label, label)), "m", Optional.of(parent),
            Collections.<ProjectedNodeKey>emptyList(), children, false, BoundaryTier.SUBTLE);
    }

    private static ProjectedEnclosure parent(EnclosureHullKey hull, String label,
            List<EnclosureHullKey> children) {
        return ProjectedEnclosure.of(hull, hull.endpointKeys(),
            Collections.singletonList(SafeNodeLabel.of(label, label)), "m",
            Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(), children, true,
            BoundaryTier.EMPHATIC);
    }

    private static final class SiblingFixture {
        final GraphProjection projection;
        final LayoutPositions positions;
        final List<PinProjection> pins;
        final EnclosureHullKey hullA;
        final EnclosureHullKey hullB;

        SiblingFixture(GraphProjection projection, LayoutPositions positions, List<PinProjection> pins,
                EnclosureHullKey hullA, EnclosureHullKey hullB) {
            this.projection = projection;
            this.positions = positions;
            this.pins = pins;
            this.hullA = hullA;
            this.hullB = hullB;
        }
    }
```

- [ ] **Step 2: Run the tests and confirm the new ones fail**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould`
Expected: FAIL — the same-map tests report unchanged positions or one `IMMOVABLE_SIDES` conflict.

- [ ] **Step 3: Dispatch same-map pairs to the new policy**

In `plan`, replace the same-map placeholder branch

```java
            else {
                outcome.conflicts.put(violation.pairKey, conflict(violation,
                    BoundaryConflict.Reason.IMMOVABLE_SIDES,
                    blockingPins(violation, enclosuresByHull, pins)));
            }
```

with

```java
            else {
                planSameMap(violation, projection, enclosuresByHull, hulls, positions, metrics, pins,
                    pinnedNodes, outcome);
            }
```

- [ ] **Step 4: Insert the same-map policy and cap-set recursion after `addMapDelta`**

```java
    private static void planSameMap(final Violation violation, final GraphProjection projection,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull,
            final Map<EnclosureHullKey, HullGeometry> hulls, final LayoutPositions positions,
            final GeometryTextMetrics metrics, final List<PinProjection> pins,
            final Set<ProjectedNodeKey> pinnedNodes, final PlanOutcome outcome) {
        final Candidate candidate = selectCandidate(violation, projection, enclosuresByHull, hulls, positions,
            metrics, pinnedNodes);
        if (candidate == null) {
            outcome.conflicts.put(violation.pairKey, conflict(violation,
                BoundaryConflict.Reason.IMMOVABLE_SIDES, blockingPins(violation, enclosuresByHull, pins)));
            return;
        }
        if (candidate.firstMagnitude > 0.0) {
            final Traversal moves = traverse(violation.first, candidate.firstUnit, candidate.firstMagnitude,
                projection, enclosuresByHull, hulls, positions, metrics, pinnedNodes);
            addMoves(outcome, moves, candidate.firstVector);
        }
        if (candidate.secondMagnitude > 0.0) {
            final Traversal moves = traverse(violation.second, candidate.secondUnit, candidate.secondMagnitude,
                projection, enclosuresByHull, hulls, positions, metrics, pinnedNodes);
            addMoves(outcome, moves, candidate.secondVector);
        }
        outcome.movedPairs.add(violation.pairKey);
    }

    private static void addMoves(final PlanOutcome outcome, final Traversal moves, final LayoutPoint vector) {
        if (moves.movedNodes.isEmpty() && moves.movedAnchors.isEmpty()) {
            throw new BoundarySeparationException("A valid candidate must have a non-empty displacement set");
        }
        for (final ProjectedNodeKey node : moves.movedNodes) {
            addVector(outcome.nodeFields, node, vector);
        }
        for (final EnclosureHullKey anchor : moves.movedAnchors) {
            addVector(outcome.anchorFields, anchor, vector);
        }
    }

    private static Candidate selectCandidate(final Violation violation, final GraphProjection projection,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull,
            final Map<EnclosureHullKey, HullGeometry> hulls, final LayoutPositions positions,
            final GeometryTextMetrics metrics, final Set<ProjectedNodeKey> pinnedNodes) {
        final LayoutPoint translation = violation.translation;
        final double magnitude = Math.hypot(translation.x(), translation.y());
        final LayoutPoint firstUnit = LayoutPoint.of(translation.x() / magnitude, translation.y() / magnitude);
        final LayoutPoint secondUnit = negate(firstUnit);
        final double half = magnitude / 2.0;
        if (valid(violation.first, firstUnit, half, projection, enclosuresByHull, hulls, positions, metrics,
                pinnedNodes)
                && valid(violation.second, secondUnit, half, projection, enclosuresByHull, hulls, positions,
                    metrics, pinnedNodes)) {
            return new Candidate(firstUnit, half, scale(translation, -0.5), secondUnit, half,
                scale(translation, 0.5));
        }
        if (valid(violation.first, firstUnit, magnitude, projection, enclosuresByHull, hulls, positions, metrics,
            pinnedNodes)) {
            return new Candidate(firstUnit, magnitude, negate(translation), secondUnit, 0.0,
                LayoutPoint.of(0.0, 0.0));
        }
        if (valid(violation.second, secondUnit, magnitude, projection, enclosuresByHull, hulls, positions, metrics,
            pinnedNodes)) {
            return new Candidate(firstUnit, 0.0, LayoutPoint.of(0.0, 0.0), secondUnit, magnitude, translation);
        }
        return null;
    }

    private static boolean valid(final EnclosureHullKey hull, final LayoutPoint unit, final double magnitude,
            final GraphProjection projection,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull,
            final Map<EnclosureHullKey, HullGeometry> hulls, final LayoutPositions positions,
            final GeometryTextMetrics metrics, final Set<ProjectedNodeKey> pinnedNodes) {
        final Traversal traversal = traverse(hull, unit, magnitude, projection, enclosuresByHull, hulls,
            positions, metrics, pinnedNodes);
        if (traversal.movedNodes.isEmpty() && traversal.movedAnchors.isEmpty()) {
            return false;
        }
        for (final PinDepth pin : traversal.pins) {
            if (pin.depth < magnitude) {
                return false;
            }
        }
        return true;
    }

    private static Traversal traverse(final EnclosureHullKey hull, final LayoutPoint unit, final double band,
            final GraphProjection projection,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull,
            final Map<EnclosureHullKey, HullGeometry> hulls, final LayoutPositions positions,
            final GeometryTextMetrics metrics, final Set<ProjectedNodeKey> pinnedNodes) {
        final Traversal traversal = new Traversal();
        collectCap(hull, unit, band, projection, enclosuresByHull, hulls, positions, metrics, pinnedNodes,
            traversal);
        return traversal;
    }

    private static void collectCap(final EnclosureHullKey hull, final LayoutPoint unit, final double band,
            final GraphProjection projection,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull,
            final Map<EnclosureHullKey, HullGeometry> hulls, final LayoutPositions positions,
            final GeometryTextMetrics metrics, final Set<ProjectedNodeKey> pinnedNodes,
            final Traversal traversal) {
        final ProjectedEnclosure enclosure = enclosuresByHull.get(hull);
        if (enclosure == null) {
            throw new BoundarySeparationException("Missing enclosure for hull " + CanonicalLayoutKeys.hull(hull));
        }
        final boolean empty = enclosure.directNodes().isEmpty() && enclosure.directEnclosures().isEmpty();
        final double support = support(hull, unit, projection, enclosuresByHull, hulls, positions, metrics);
        if (empty) {
            traversal.movedAnchors.add(hull);
            return;
        }
        for (final ProjectedNodeKey node : enclosure.directNodes()) {
            final double contribution = nodeContribution(node, unit, positions, projection);
            if (inBand(contribution, support, band)) {
                if (pinnedNodes.contains(node)) {
                    traversal.pins.add(new PinDepth(node, support - contribution));
                }
                else {
                    traversal.movedNodes.add(node);
                }
            }
        }
        for (final EnclosureHullKey child : enclosure.directEnclosures()) {
            final double contribution = polySupport(child, unit, hulls) + HULL_CLEARANCE;
            if (inBand(contribution, support, band)) {
                collectCap(child, unit, band, projection, enclosuresByHull, hulls, positions, metrics,
                    pinnedNodes, traversal);
            }
        }
    }

    private static boolean inBand(final double contribution, final double support, final double band) {
        final double epsilon = SUPPORT_COMPARISON_EPSILON * Math.max(Math.abs(contribution),
            Math.max(Math.abs(support), Math.abs(support - band)));
        return contribution >= support - band - epsilon;
    }

    private static double support(final EnclosureHullKey hull, final LayoutPoint unit,
            final GraphProjection projection,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull,
            final Map<EnclosureHullKey, HullGeometry> hulls, final LayoutPositions positions,
            final GeometryTextMetrics metrics) {
        final ProjectedEnclosure enclosure = enclosuresByHull.get(hull);
        if (enclosure == null) {
            throw new BoundarySeparationException("Missing enclosure for hull " + CanonicalLayoutKeys.hull(hull));
        }
        final boolean empty = enclosure.directNodes().isEmpty() && enclosure.directEnclosures().isEmpty();
        if (empty) {
            final LayoutPoint anchor = positions.anchors().get(hull);
            if (anchor == null) {
                throw new BoundarySeparationException("Missing anchor for hull " + CanonicalLayoutKeys.hull(hull));
            }
            final java.awt.geom.Dimension2D label = labelSize(enclosure, metrics);
            final double halfWidth = label.getWidth() * 0.5 + BOUNDARY_PADDING;
            final double halfHeight = label.getHeight() * 0.5 + BOUNDARY_PADDING;
            return unit.x() * anchor.x() + unit.y() * anchor.y()
                + Math.max(Math.abs(unit.x()) * halfWidth, Math.abs(unit.y()) * halfHeight);
        }
        double best = Double.NEGATIVE_INFINITY;
        for (final ProjectedNodeKey node : enclosure.directNodes()) {
            final LayoutPoint center = positions.nodes().get(node);
            if (center == null) {
                throw new BoundarySeparationException("Missing node position for " + node);
            }
            best = Math.max(best, unit.x() * center.x() + unit.y() * center.y() + nodeRadius(node, projection));
        }
        for (final EnclosureHullKey child : enclosure.directEnclosures()) {
            best = Math.max(best, polySupport(child, unit, hulls));
        }
        return best + HULL_CLEARANCE;
    }

    private static double nodeContribution(final ProjectedNodeKey node, final LayoutPoint unit,
            final LayoutPositions positions, final GraphProjection projection) {
        final LayoutPoint center = positions.nodes().get(node);
        if (center == null) {
            throw new BoundarySeparationException("Missing node position for " + node);
        }
        return unit.x() * center.x() + unit.y() * center.y() + nodeRadius(node, projection) + HULL_CLEARANCE;
    }

    private static double nodeRadius(final ProjectedNodeKey node, final GraphProjection projection) {
        final org.freeplane.plugin.graph.projection.NodeProminence prominence =
            projection.prominence().get(node);
        final double scale = prominence == null ? 1.0 : prominence.scale();
        return BASE_RADIUS * scale;
    }

    private static double polySupport(final EnclosureHullKey hull, final LayoutPoint unit,
            final Map<EnclosureHullKey, HullGeometry> hulls) {
        final HullGeometry geometry = hulls.get(hull);
        if (geometry == null) {
            throw new BoundarySeparationException("Missing hull " + CanonicalLayoutKeys.hull(hull));
        }
        double best = Double.NEGATIVE_INFINITY;
        for (final LayoutPoint vertex : geometry.exactPolygon()) {
            best = Math.max(best, unit.x() * vertex.x() + unit.y() * vertex.y());
        }
        return best;
    }

    private static java.awt.geom.Dimension2D labelSize(final ProjectedEnclosure enclosure,
            final GeometryTextMetrics metrics) {
        if (enclosure.boundaryTier() == BoundaryTier.SUPPRESSED) {
            return ZERO_SIZE;
        }
        java.awt.geom.Dimension2D largest = null;
        for (final org.freeplane.plugin.graph.projection.input.SafeNodeLabel label : enclosure.labels()) {
            final java.awt.geom.Dimension2D measured = metrics.measure(label.displayText(),
                enclosure.boundaryTier());
            if (largest == null || measured.getWidth() * measured.getHeight() > largest.getWidth()
                    * largest.getHeight()) {
                largest = measured;
            }
        }
        if (largest == null) {
            throw new BoundarySeparationException("Enclosures must carry at least one label");
        }
        return largest;
    }

    private static final java.awt.geom.Dimension2D ZERO_SIZE = new java.awt.geom.Dimension2D() {
        @Override
        public double getWidth() {
            return 0.0;
        }

        @Override
        public double getHeight() {
            return 0.0;
        }

        @Override
        public void setSize(final double width, final double height) {
            throw new UnsupportedOperationException("Geometry sizes are immutable");
        }
    };

    private static final class Traversal {
        private final List<ProjectedNodeKey> movedNodes = new ArrayList<ProjectedNodeKey>();
        private final List<EnclosureHullKey> movedAnchors = new ArrayList<EnclosureHullKey>();
        private final List<PinDepth> pins = new ArrayList<PinDepth>();
    }

    private static final class PinDepth {
        private final ProjectedNodeKey node;
        private final double depth;

        private PinDepth(final ProjectedNodeKey node, final double depth) {
            this.node = node;
            this.depth = depth;
        }
    }

    private static final class Candidate {
        private final LayoutPoint firstUnit;
        private final double firstMagnitude;
        private final LayoutPoint firstVector;
        private final LayoutPoint secondUnit;
        private final double secondMagnitude;
        private final LayoutPoint secondVector;

        private Candidate(final LayoutPoint firstUnit, final double firstMagnitude,
                final LayoutPoint firstVector, final LayoutPoint secondUnit, final double secondMagnitude,
                final LayoutPoint secondVector) {
            this.firstUnit = firstUnit;
            this.firstMagnitude = firstMagnitude;
            this.firstVector = firstVector;
            this.secondUnit = secondUnit;
            this.secondMagnitude = secondMagnitude;
            this.secondVector = secondVector;
        }
    }
```

- [ ] **Step 5: Run the tests and confirm they pass**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould`
Expected: PASS, 20 tests.

- [ ] **Step 6: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrection.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrectionShould.java
git commit -m "feat(graph-layout): displace same-map facing edges with pin-aware cap sets"
```

## Task 10: Complementary splits, ties, and immovable sides

**Implementer tier:** Capable

**Lane:** correction

**Depends on:** 9

**Ownership:** freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrection.java, freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrectionShould.java

**Validation:** gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrection.java` (`selectCandidate` before its `return null`; insert `complementaryDepth` after `valid`)
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrectionShould.java` (append tests before the final closing brace)

**Interfaces:**
- Consumes: everything from Task 9, especially `valid(EnclosureHullKey, LayoutPoint, double, ...)`, `traverse`, `Traversal.pins`, `PinDepth.depth`.
- Produces: `complementaryDepth(EnclosureHullKey, LayoutPoint, double, ...)` returning the smallest pinned local depth strictly inside `(0, magnitude)` of the recursive `band = magnitude` cap closure, or `null`; `selectCandidate` distributions 4 (first-side split `d_A` + `m - d_A`) and 5 (second-side split), with the depth-strict tie rule (`depth < a` invalid, `depth == a` valid); `IMMOVABLE_SIDES` when no distribution is valid.

- [ ] **Step 1: Add the failing complementary-split tests**

Append these members to `BoundarySeparationCorrectionShould`, immediately before its final closing brace.

```java
    @Test
    public void resolvesTheMixedComplementarySplitWithATieMove() {
        final ProjectedNodeKey aFree = key("a-free");
        final ProjectedNodeKey aPin = key("a-pin");
        final ProjectedNodeKey bFree = key("b-free");
        final ProjectedNodeKey bPin = key("b-pin");
        final SiblingFixture fixture = siblingFixture(Arrays.asList(aFree, aPin), Arrays.asList(bFree, bPin),
            Arrays.asList(nodeEntry(aFree, 0.0, 0.0), nodeEntry(aPin, -3.0, 30.0), nodeEntry(bFree, 38.0, 0.0),
                nodeEntry(bPin, 47.0, 30.0)),
            Arrays.asList(pin(aPin, -3.0, 30.0), pin(bPin, 47.0, 30.0)));

        final BoundarySeparationResult result = apply(fixture);

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().boundaryVerified()).isTrue();
        assertThat(result.appliedDisplacements().get(CanonicalLayoutKeys.nodeField(aFree)))
            .isEqualTo(LayoutPoint.of(-3.0, 0.0));
        assertThat(result.appliedDisplacements().get(CanonicalLayoutKeys.nodeField(bFree)))
            .isEqualTo(LayoutPoint.of(7.0, 0.0));
        assertThat(result.positions().nodes().get(aPin)).isEqualTo(LayoutPoint.of(-3.0, 30.0));
        assertThat(result.positions().nodes().get(bPin)).isEqualTo(LayoutPoint.of(47.0, 30.0));
        assertThat(result.positions().nodes().get(aFree)).isEqualTo(LayoutPoint.of(-3.0, 0.0));
        assertThat(result.positions().nodes().get(bFree)).isEqualTo(LayoutPoint.of(45.0, 0.0));
    }

    @Test
    public void aTieAtTheAppliedMagnitudeIsValidAndTheTiedPinIsNotDisplaced() {
        final ProjectedNodeKey aFree = key("a-free");
        final ProjectedNodeKey aPin = key("a-pin");
        final ProjectedNodeKey bFree = key("b-free");
        final ProjectedNodeKey bPin = key("b-pin");
        final SiblingFixture fixture = siblingFixture(Arrays.asList(aFree, aPin), Arrays.asList(bFree, bPin),
            Arrays.asList(nodeEntry(aFree, 0.0, 0.0), nodeEntry(aPin, -3.0, 30.0), nodeEntry(bFree, 38.0, 0.0),
                nodeEntry(bPin, 47.0, 30.0)),
            Arrays.asList(pin(aPin, -3.0, 30.0), pin(bPin, 47.0, 30.0)));

        final BoundarySeparationResult result = apply(fixture);

        assertThat(result.appliedDisplacements()).containsOnlyKeys(
            CanonicalLayoutKeys.nodeField(aFree), CanonicalLayoutKeys.nodeField(bFree),
            CanonicalLayoutKeys.anchorField(fixture.hullA), CanonicalLayoutKeys.anchorField(fixture.hullB),
            CanonicalLayoutKeys.anchorField(hull("root")));
        assertThat(result.appliedDisplacements().keySet())
            .doesNotContain(CanonicalLayoutKeys.nodeField(aPin))
            .doesNotContain(CanonicalLayoutKeys.nodeField(bPin));
        assertThat(result.positions().nodes().get(aPin)).isEqualTo(LayoutPoint.of(-3.0, 30.0));
        assertThat(result.positions().nodes().get(bPin)).isEqualTo(LayoutPoint.of(47.0, 30.0));
    }

    @Test
    public void pinnedMaximumContributorsOnBothSidesReportImmovableSides() {
        final ProjectedNodeKey aPin = key("a-pin");
        final ProjectedNodeKey bPin = key("b-pin");
        final SiblingFixture fixture = siblingFixture(Collections.singletonList(aPin),
            Collections.singletonList(bPin),
            Arrays.asList(nodeEntry(aPin, 0.0, 30.0), nodeEntry(bPin, 38.0, 30.0)),
            Arrays.asList(pin(aPin, 0.0, 30.0), pin(bPin, 38.0, 30.0)));

        final BoundarySeparationResult result = apply(fixture);

        assertThat(result.positions()).isEqualTo(fixture.positions);
        assertThat(result.diagnostics().rounds()).isZero();
        assertThat(result.diagnostics().hullResidualViolations()).isEqualTo(1);
        assertThat(result.diagnostics().boundaryCovered()).isTrue();
        assertThat(result.diagnostics().boundaryVerified()).isFalse();
        assertThat(result.diagnostics().conflicts()).hasSize(1);
        assertThat(result.diagnostics().conflicts().get(0).reason())
            .isEqualTo(BoundaryConflict.Reason.IMMOVABLE_SIDES);
        assertThat(result.diagnostics().conflicts().get(0).blockingPins()).hasSize(2);
        assertThat(result.diagnostics().residualHullPairs()).containsExactly(
            result.diagnostics().conflicts().get(0).pairKey());
    }

    @Test
    public void recursiveCapabilityFindsTheNestedPinDepth() {
        final ProjectedNodeKey a1Free = key("a1-free");
        final ProjectedNodeKey a1Pin = key("a1-pin");
        final ProjectedNodeKey bFree = key("b-free");
        final ProjectedNodeKey bPin = key("b-pin");
        final EnclosureHullKey rootHull = hull("root");
        final EnclosureHullKey aHull = hull("a");
        final EnclosureHullKey a1Hull = hull("a1");
        final EnclosureHullKey bHull = hull("b");
        final GraphProjection projection = projection(Arrays.asList(a1Free, a1Pin, bFree, bPin),
            Arrays.asList(parent(rootHull, "root", Arrays.asList(aHull, bHull)),
                nestedChild(aHull, "a", rootHull, Collections.singletonList(a1Hull)),
                child(a1Hull, "a1", aHull, Arrays.asList(a1Free, a1Pin)),
                child(bHull, "b", rootHull, Arrays.asList(bFree, bPin))));
        final LayoutPositions positions = positions(
            Arrays.asList(nodeEntry(a1Free, 0.0, 0.0), nodeEntry(a1Pin, -3.0, 30.0),
                nodeEntry(bFree, 54.0, 0.0), nodeEntry(bPin, 63.0, 30.0)),
            Arrays.asList(anchorEntry(rootHull, 0.0, 0.0), anchorEntry(aHull, 0.0, 0.0),
                anchorEntry(a1Hull, 0.0, 0.0), anchorEntry(bHull, 54.0, 0.0)));
        final List<PinProjection> pins = Arrays.asList(pin(a1Pin, -3.0, 30.0), pin(bPin, 63.0, 30.0));

        final BoundarySeparationResult result = new BoundarySeparationCorrection().apply(projection, positions,
            METRICS, pins);

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().boundaryVerified()).isTrue();
        assertThat(result.appliedDisplacements().get(CanonicalLayoutKeys.nodeField(a1Free)))
            .isEqualTo(LayoutPoint.of(-3.0, 0.0));
        assertThat(result.appliedDisplacements().get(CanonicalLayoutKeys.nodeField(bFree)))
            .isEqualTo(LayoutPoint.of(7.0, 0.0));
        assertThat(result.positions().nodes().get(a1Pin)).isEqualTo(LayoutPoint.of(-3.0, 30.0));
        assertThat(result.positions().nodes().get(bPin)).isEqualTo(LayoutPoint.of(63.0, 30.0));
    }

    @Test
    public void repeatedRunsAreDeterministic() {
        final ProjectedNodeKey aFree = key("a-free");
        final ProjectedNodeKey aPin = key("a-pin");
        final ProjectedNodeKey bFree = key("b-free");
        final ProjectedNodeKey bPin = key("b-pin");
        final SiblingFixture fixture = siblingFixture(Arrays.asList(aFree, aPin), Arrays.asList(bFree, bPin),
            Arrays.asList(nodeEntry(aFree, 0.0, 0.0), nodeEntry(aPin, -3.0, 30.0), nodeEntry(bFree, 38.0, 0.0),
                nodeEntry(bPin, 47.0, 30.0)),
            Arrays.asList(pin(aPin, -3.0, 30.0), pin(bPin, 47.0, 30.0)));

        final BoundarySeparationResult first = new BoundarySeparationCorrection().apply(fixture.projection,
            fixture.positions, METRICS, fixture.pins);
        final BoundarySeparationResult second = new BoundarySeparationCorrection().apply(fixture.projection,
            fixture.positions, METRICS, fixture.pins);

        assertThat(second.positions()).isEqualTo(first.positions());
        assertThat(second.diagnostics().rounds()).isEqualTo(first.diagnostics().rounds());
        assertThat(second.diagnostics().hullViolationsDetected())
            .isEqualTo(first.diagnostics().hullViolationsDetected());
        assertThat(second.diagnostics().hullResidualViolations())
            .isEqualTo(first.diagnostics().hullResidualViolations());
        assertThat(second.diagnostics().appliedDisplacements())
            .isEqualTo(first.diagnostics().appliedDisplacements());
        assertThat(second.diagnostics().conflicts().size()).isEqualTo(first.diagnostics().conflicts().size());
        assertThat(second.diagnostics().residualHullPairs())
            .isEqualTo(first.diagnostics().residualHullPairs());
    }
```

- [ ] **Step 2: Run the tests and confirm the new ones fail**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould`
Expected: FAIL — the mixed, tie and nested-capability tests produce `IMMOVABLE_SIDES` instead of the complementary split, because distributions 4 and 5 do not exist yet.

- [ ] **Step 3: Add the complementary-split distributions to `selectCandidate`**

In `selectCandidate`, insert these two blocks immediately before the final `return null;`:

```java
        final Double firstDepth = complementaryDepth(violation.first, firstUnit, magnitude, projection,
            enclosuresByHull, hulls, positions, metrics, pinnedNodes);
        if (firstDepth != null && firstDepth.doubleValue() > 0.0 && firstDepth.doubleValue() < magnitude) {
            final double firstMagnitude = firstDepth.doubleValue();
            final double secondMagnitude = magnitude - firstMagnitude;
            if (valid(violation.first, firstUnit, firstMagnitude, projection, enclosuresByHull, hulls, positions,
                    metrics, pinnedNodes)
                    && valid(violation.second, secondUnit, secondMagnitude, projection, enclosuresByHull, hulls,
                        positions, metrics, pinnedNodes)) {
                return new Candidate(firstUnit, firstMagnitude, scale(translation, -firstMagnitude / magnitude),
                    secondUnit, secondMagnitude, scale(translation, secondMagnitude / magnitude));
            }
        }
        final Double secondDepth = complementaryDepth(violation.second, secondUnit, magnitude, projection,
            enclosuresByHull, hulls, positions, metrics, pinnedNodes);
        if (secondDepth != null && secondDepth.doubleValue() > 0.0 && secondDepth.doubleValue() < magnitude) {
            final double secondMagnitude = secondDepth.doubleValue();
            final double firstMagnitude = magnitude - secondMagnitude;
            if (valid(violation.first, firstUnit, firstMagnitude, projection, enclosuresByHull, hulls, positions,
                    metrics, pinnedNodes)
                    && valid(violation.second, secondUnit, secondMagnitude, projection, enclosuresByHull, hulls,
                        positions, metrics, pinnedNodes)) {
                return new Candidate(firstUnit, firstMagnitude, scale(translation, -firstMagnitude / magnitude),
                    secondUnit, secondMagnitude, scale(translation, secondMagnitude / magnitude));
            }
        }
```

- [ ] **Step 4: Insert `complementaryDepth` immediately after `valid`**

```java
    private static Double complementaryDepth(final EnclosureHullKey hull, final LayoutPoint unit,
            final double magnitude, final GraphProjection projection,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull,
            final Map<EnclosureHullKey, HullGeometry> hulls, final LayoutPositions positions,
            final GeometryTextMetrics metrics, final Set<ProjectedNodeKey> pinnedNodes) {
        final Traversal traversal = traverse(hull, unit, magnitude, projection, enclosuresByHull, hulls,
            positions, metrics, pinnedNodes);
        double best = Double.POSITIVE_INFINITY;
        for (final PinDepth pin : traversal.pins) {
            if (pin.depth > 0.0 && pin.depth < magnitude) {
                best = Math.min(best, pin.depth);
            }
        }
        return best == Double.POSITIVE_INFINITY ? null : Double.valueOf(best);
    }
```

- [ ] **Step 5: Run the tests and confirm they pass**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould`
Expected: PASS, 25 tests.

- [ ] **Step 6: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrection.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrectionShould.java
git commit -m "feat(graph-layout): add complementary split candidates and tie semantics"
```

## Task 11: Bounded loop, ROUND_LIMIT reasons, and the I6 bijection

**Implementer tier:** Advanced

**Lane:** correction

**Depends on:** 10

**Ownership:** freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrection.java, freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrectionShould.java

**Validation:** gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrection.java` (the post-loop conflict block in `separate`; the `terminalConflicts` signature and body)
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrectionShould.java` (append tests before the final closing brace)

**Interfaces:**
- Consumes: everything from Tasks 7–10, especially `PlanOutcome.movedPairs`, `plan.conflicts`, `pairKeys`, `applyRound`.
- Produces: `terminalConflicts(List<Violation>, PlanOutcome, boolean boundExhausted, GraphProjection, Map<EnclosureHullKey, ProjectedEnclosure>, List<PinProjection>)`; `ROUND_LIMIT` for residual pairs that still have a valid displacement distribution when the bound was reached; one conflict per residual pair in violation order with reasons recomputed on the final detection set.

- [ ] **Step 1: Add the failing bound-path test**

Append this member to `BoundarySeparationCorrectionShould`, immediately before its final closing brace.

```java
    @Test
    public void roundLimitCoverageRecomputesReasonsOnTheFinalResidualSet() {
        final EnclosureHullKey rootHull = hull("root");
        final EnclosureHullKey aHull = hull("a");
        final EnclosureHullKey bHull = hull("b");
        final EnclosureHullKey cHull = hull("c");
        final ProjectedNodeKey aNode = key("a-node");
        final ProjectedNodeKey bNode = key("b-node");
        final ProjectedNodeKey cNode = key("c-node");
        final GraphProjection projection = projection(Arrays.asList(aNode, bNode, cNode),
            Arrays.asList(parent(rootHull, "root", Arrays.asList(aHull, bHull, cHull)),
                child(aHull, "a", rootHull, Collections.singletonList(aNode)),
                child(bHull, "b", rootHull, Collections.singletonList(bNode)),
                child(cHull, "c", rootHull, Collections.singletonList(cNode))));
        final LayoutPositions positions = positions(
            Arrays.asList(nodeEntry(aNode, 0.0, 0.0), nodeEntry(bNode, 38.0, 0.0), nodeEntry(cNode, 76.0, 0.0)),
            Arrays.asList(anchorEntry(rootHull, 0.0, 0.0), anchorEntry(aHull, 0.0, 0.0),
                anchorEntry(bHull, 38.0, 0.0), anchorEntry(cHull, 76.0, 0.0)));

        final BoundarySeparationResult result = new BoundarySeparationCorrection(1).apply(projection, positions,
            METRICS, Collections.<PinProjection>emptyList());

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().hullViolationsDetected()).isEqualTo(2);
        assertThat(result.diagnostics().hullResidualViolations()).isEqualTo(2);
        assertThat(result.diagnostics().boundaryVerified()).isFalse();
        assertThat(result.diagnostics().boundaryCovered()).isTrue();
        assertThat(result.diagnostics().conflicts()).hasSize(2);
        assertThat(result.diagnostics().conflicts().get(0).reason())
            .isEqualTo(BoundaryConflict.Reason.ROUND_LIMIT);
        assertThat(result.diagnostics().conflicts().get(1).reason())
            .isEqualTo(BoundaryConflict.Reason.ROUND_LIMIT);
        assertThat(result.diagnostics().residualHullPairs()).containsExactly(
            result.diagnostics().conflicts().get(0).pairKey(),
            result.diagnostics().conflicts().get(1).pairKey());
        assertThat(result.positions().nodes().get(aNode)).isEqualTo(LayoutPoint.of(-5.0, 0.0));
        assertThat(result.positions().nodes().get(bNode)).isEqualTo(LayoutPoint.of(38.0, 0.0));
        assertThat(result.positions().nodes().get(cNode)).isEqualTo(LayoutPoint.of(81.0, 0.0));
    }
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould`
Expected: FAIL — the two residual pairs are reported with `IMMOVABLE_SIDES`, not `ROUND_LIMIT`.

- [ ] **Step 3: Pass the bound state to `terminalConflicts`**

In `separate`, replace

```java
            conflicts = terminalConflicts(terminalViolations, terminalPlan, projection, enclosuresByHull, pins);
```

with

```java
            final boolean boundExhausted = rounds >= maxDisplacementRounds;
            conflicts = terminalConflicts(terminalViolations, terminalPlan, boundExhausted, projection,
                enclosuresByHull, pins);
```

- [ ] **Step 4: Add the ROUND_LIMIT branch to `terminalConflicts`**

Replace the whole `terminalConflicts` method with:

```java
    private static List<BoundaryConflict> terminalConflicts(final List<Violation> violations,
            final PlanOutcome plan, final boolean boundExhausted, final GraphProjection projection,
            final Map<EnclosureHullKey, ProjectedEnclosure> enclosuresByHull, final List<PinProjection> pins) {
        final List<BoundaryConflict> result = new ArrayList<BoundaryConflict>();
        for (final Violation violation : violations) {
            final BoundaryConflict existing = plan.conflicts.get(violation.pairKey);
            if (existing != null) {
                result.add(existing);
                continue;
            }
            if (boundExhausted && plan.movedPairs.contains(violation.pairKey)) {
                result.add(conflict(violation, BoundaryConflict.Reason.ROUND_LIMIT,
                    blockingPins(violation, enclosuresByHull, pins)));
                continue;
            }
            final BoundaryConflict.Reason reason = violation.kind == BoundaryConflict.Kind.ANCESTOR_ESCAPE
                ? BoundaryConflict.Reason.STRUCTURAL_ESCAPE : BoundaryConflict.Reason.IMMOVABLE_SIDES;
            result.add(conflict(violation, reason, blockingPins(violation, enclosuresByHull, pins)));
        }
        return Collections.unmodifiableList(result);
    }
```

- [ ] **Step 5: Run the tests and confirm they pass**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould`
Expected: PASS, 26 tests.

- [ ] **Step 6: Commit**

```bash
git add freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrection.java freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrectionShould.java
git commit -m "feat(graph-layout): cover round-limit residuals with recomputed reasons"
```

## Task 12: Frozen real-case fixture and one-round regression

**Implementer tier:** Advanced

**Lane:** correction

**Depends on:** 11

**Ownership:** freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrectionShould.java

**Validation:** gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould

**Files:**
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrectionShould.java` (append the frozen fixture and its tests before the final closing brace)

**Interfaces:**
- Consumes: `GraphProjection.projected(long, List<ProjectedNode>, List<ProjectedEnclosure>, List<ProjectedEdge>, List<RelationshipResolution>, List<PinProjection>)`, `EdgeContributor.nativeConnector(ConnectorSnapshot, ProjectedEndpointKey, ProjectedEndpointKey)`, `ConnectorDescriptor.of(SourceNodeKey, NodeReference, boolean, boolean, String, String, String)`, `ProjectedEdge.of(ProjectedEdgeKey, List<EdgeContributor>)`, `GraphGeometryEngine.computeHulls`, `HullIntersection`, `BoundarySeparationCorrection.apply`.
- Produces: the frozen 5-node/4-enclosure/2-pin fixture with captured full-precision positions and its eight ordered assertions.

**Captured constants:** the frozen node positions are the full-precision probe capture from 2026-09-12 (`RealPipelineOverlapProbe` re-run with `%.17g`): Regularity `(-173.26206169386748, 1.8301628933959680)`, Replacement `(248.60834750232004, -59.339657536064465)`, Choice `(-24.832420395427746, -34.920469854404410)`, pinned Theorem `(-209.31397564145126, 9.820904009249132)`, free Theorem `(-241.79904871734790, 14.381230674273278)`. The captured pre-correction MST is exactly `(-13.548086052416210, 0.0)`.

- [ ] **Step 1: Re-run the capture probe and confirm the constants**

The probe lives outside the worktree. Regenerate its classpath from this worktree and run it with full-precision printing:

```bash
cd freeplane_plugin_graph && gradle -q -I /tmp/pm-probe/print-classpath.gradle pmPrintTestClasspath > /tmp/pm-probe/cp-worktree.txt
```

```bash
sed -e 's/%s %s (%.1f, %.1f)%n/%s %s (%.17g, %.17g)%n/' -e 's/mst=(%.1f,%.1f)/mst=(%.17g,%.17g)/' /tmp/pm-probe/RealPipelineOverlapProbe.java > /tmp/pm-probe/fp/RealPipelineOverlapProbe.java
```

```bash
CP=$(grep -o 'PM_TEST_CLASSPATH=.*' /tmp/pm-probe/cp-worktree.txt | sed 's/PM_TEST_CLASSPATH=//'); /data/home/henry-arch/.sdkman/candidates/java/21.0.8-zulu/bin/javac -nowarn -cp "/tmp/pm-probe/classes:$CP" -d /tmp/pm-probe/fpclasses /tmp/pm-probe/fp/RealPipelineOverlapProbe.java
```

```bash
CP=$(grep -o 'PM_TEST_CLASSPATH=.*' /tmp/pm-probe/cp-worktree.txt | sed 's/PM_TEST_CLASSPATH=//'); /data/home/henry-arch/.sdkman/candidates/java/21.0.8-zulu/bin/java -cp "/tmp/pm-probe/fpclasses:/tmp/pm-probe/classes:$CP:/data/Dropbox/Documents/999.notebooks/01.math/00.foundation.of.math/02.set_theory/mindmap" org.freeplane.plugin.graph.adapter.RealPipelineOverlapProbe | sed -n '/=== NODE POSITIONS/,$p'
```

Expected: the five positions printed above (full precision) and `mst=(-13.548086052416210,0.0000000000000000)`. If the printed values differ, rewrite the constants in the fixture below with the printed literals and say so in the task report; the assertion structure is unchanged. The probe and its log are not part of the repository and must not be added to it.

- [ ] **Step 2: Write the failing frozen-fixture test**

Append these members to `BoundarySeparationCorrectionShould`, immediately before its final closing brace.

```java
    private static final MapReferenceId FROZEN_MAP =
        MapReferenceId.of("8055d8c8-d71e-40f3-a8ed-5bf2502f2cad");
    private static final String FROZEN_REGULARITY = "ID_1133378501";
    private static final String FROZEN_REPLACEMENT = "ID_822182441";
    private static final String FROZEN_CHOICE = "ID_130337169";
    private static final String FROZEN_PINNED_THEOREM = "ID_1901523076";
    private static final String FROZEN_FREE_THEOREM = "ID_1387156674";
    private static final String FROZEN_SUPPRESSED_ROOT = "ID_435635462";
    private static final String FROZEN_ZFC = "ID_1675547143";
    private static final String FROZEN_AXIOMS = "ID_1912952190";
    private static final String FROZEN_DEFINITIONS = "ID_978732953";
    private static final double FROZEN_MST_X = -13.548086052416210;
    private static final double FROZEN_REGULARITY_DELTA_X = 13.54808605241621;

    @Test
    public void frozenRealCaseViolatesBeforeAndSettlesInOneRoundAfterCorrection() {
        final FrozenFixture fixture = frozenFixture();

        assertThat(fixture.projection.prominence().get(fixture.regularity).visibleOutgoingTargets())
            .isEqualTo(2);
        assertThat(fixture.projection.prominence().get(fixture.replacement).visibleOutgoingTargets())
            .isEqualTo(0);
        final GraphGeometryEngine engine = new GraphGeometryEngine();
        final HullGeometry rawAxioms = engine.computeHulls(fixture.projection, fixture.positions, METRICS)
            .hulls().get(fixture.axiomsHull);
        final HullGeometry rawDefinitions = engine.computeHulls(fixture.projection, fixture.positions, METRICS)
            .hulls().get(fixture.definitionsHull);
        assertThat(HullIntersection.siblingOverlap(rawAxioms, rawDefinitions)).isTrue();
        assertThat(HullIntersection.minimumSeparatingTranslation(rawAxioms, rawDefinitions))
            .isEqualTo(LayoutPoint.of(FROZEN_MST_X, 0.0));
        assertThat(containsAll(rawAxioms, rawDefinitions)).isFalse();
        assertThat(containsAll(rawDefinitions, rawAxioms)).isFalse();

        final GraphGeometry rawGeometry = engine.computeHulls(fixture.projection, fixture.positions, METRICS);
        assertThat(containsAll(rawGeometry.hulls().get(fixture.zfcHull), rawAxioms)).isTrue();
        assertThat(containsAll(rawGeometry.hulls().get(fixture.zfcHull), rawDefinitions)).isTrue();

        final BoundarySeparationResult result = new BoundarySeparationCorrection().apply(fixture.projection,
            fixture.positions, METRICS, fixture.pins);

        assertThat(result.diagnostics().rounds()).isEqualTo(1);
        assertThat(result.diagnostics().hullViolationsDetected()).isGreaterThanOrEqualTo(1);
        assertThat(result.diagnostics().hullResidualViolations()).isZero();
        assertThat(result.diagnostics().boundaryVerified()).isTrue();
        assertThat(result.diagnostics().conflicts()).isEmpty();
        assertThat(result.diagnostics().residualHullPairs()).isEmpty();
        assertThat(result.positions().nodes().get(fixture.choice))
            .isEqualTo(LayoutPoint.of(-24.832420395427746, -34.920469854404410));
        assertThat(result.positions().nodes().get(fixture.pinnedTheorem))
            .isEqualTo(LayoutPoint.of(-209.31397564145126, 9.820904009249132));
        assertThat(result.appliedDisplacements().get(CanonicalLayoutKeys.nodeField(fixture.regularity)))
            .isEqualTo(LayoutPoint.of(FROZEN_REGULARITY_DELTA_X, 0.0));
        assertThat(result.appliedDisplacements().keySet())
            .doesNotContain(CanonicalLayoutKeys.nodeField(fixture.choice))
            .doesNotContain(CanonicalLayoutKeys.nodeField(fixture.pinnedTheorem));

        final GraphGeometry correctedGeometry = engine.computeHulls(fixture.projection, result.positions(),
            METRICS);
        assertThat(HullIntersection.siblingOverlap(correctedGeometry.hulls().get(fixture.axiomsHull),
            correctedGeometry.hulls().get(fixture.definitionsHull))).isFalse();
        assertThat(HullIntersection.minimumSeparatingTranslation(
            correctedGeometry.hulls().get(fixture.axiomsHull),
            correctedGeometry.hulls().get(fixture.definitionsHull))).isEqualTo(LayoutPoint.of(0.0, 0.0));
        assertThat(containsAll(correctedGeometry.hulls().get(fixture.zfcHull),
            correctedGeometry.hulls().get(fixture.axiomsHull))).isTrue();
        assertThat(containsAll(correctedGeometry.hulls().get(fixture.zfcHull),
            correctedGeometry.hulls().get(fixture.definitionsHull))).isTrue();

        final LayoutPoint regularityDelta = LayoutPoint.of(FROZEN_REGULARITY_DELTA_X, 0.0);
        assertThat(result.positions().anchors().get(fixture.axiomsHull)).isEqualTo(regularityDelta);
        assertThat(result.positions().anchors().get(fixture.zfcHull)).isEqualTo(regularityDelta);
        assertThat(result.positions().anchors().get(fixture.rootHull)).isEqualTo(regularityDelta);
        assertThat(result.positions().anchors().get(fixture.definitionsHull))
            .isEqualTo(LayoutPoint.of(0.0, 0.0));

        final BoundarySeparationResult second = new BoundarySeparationCorrection().apply(fixture.projection,
            fixture.positions, METRICS, fixture.pins);
        assertThat(second.positions()).isEqualTo(result.positions());
        assertThat(second.diagnostics().rounds()).isEqualTo(result.diagnostics().rounds());
        assertThat(second.diagnostics().hullResidualViolations())
            .isEqualTo(result.diagnostics().hullResidualViolations());
        assertThat(second.diagnostics().conflicts()).hasSize(result.diagnostics().conflicts().size());
    }

    @Test
    public void canonicalKeysUseTheCapturedRealHullStrings() {
        assertThat(CanonicalLayoutKeys.hull(hull(FROZEN_MAP, FROZEN_AXIOMS)))
            .isEqualTo("m:" + FROZEN_MAP.value() + "|p:" + FROZEN_AXIOMS);
        assertThat(CanonicalLayoutKeys.pair(hull(FROZEN_MAP, FROZEN_AXIOMS),
            hull(FROZEN_MAP, FROZEN_DEFINITIONS)))
                .isEqualTo("m:" + FROZEN_MAP.value() + "|p:" + FROZEN_AXIOMS + "|m:" + FROZEN_MAP.value()
                    + "|p:" + FROZEN_DEFINITIONS);
    }

    private static boolean containsAll(HullGeometry outer, HullGeometry inner) {
        for (final LayoutPoint vertex : inner.exactPolygon()) {
            if (!outer.contains(vertex)) {
                return false;
            }
        }
        return true;
    }

    private static FrozenFixture frozenFixture() {
        final ProjectedNodeKey regularity = frozenKey(FROZEN_REGULARITY);
        final ProjectedNodeKey replacement = frozenKey(FROZEN_REPLACEMENT);
        final ProjectedNodeKey choice = frozenKey(FROZEN_CHOICE);
        final ProjectedNodeKey pinnedTheorem = frozenKey(FROZEN_PINNED_THEOREM);
        final ProjectedNodeKey freeTheorem = frozenKey(FROZEN_FREE_THEOREM);
        final EnclosureHullKey rootHull = hull(FROZEN_MAP, FROZEN_SUPPRESSED_ROOT);
        final EnclosureHullKey zfcHull = hull(FROZEN_MAP, FROZEN_ZFC);
        final EnclosureHullKey axiomsHull = hull(FROZEN_MAP, FROZEN_AXIOMS);
        final EnclosureHullKey definitionsHull = hull(FROZEN_MAP, FROZEN_DEFINITIONS);
        final List<ProjectedNode> nodes = Arrays.asList(
            frozenNode(regularity, "Fundation / Regularity"), frozenNode(replacement, "Replacement Scheme"),
            frozenNode(choice, "Axiom of Choice"), frozenNode(pinnedTheorem, "Theorem"),
            frozenNode(freeTheorem, "Theorem"));
        final List<ProjectedEnclosure> enclosures = Arrays.asList(
            frozenEnclosure(rootHull, "Axiomatic Set Theory", Optional.<EnclosureHullKey>empty(),
                Collections.<ProjectedNodeKey>emptyList(), Collections.singletonList(zfcHull), true,
                BoundaryTier.SUPPRESSED),
            frozenEnclosure(zfcHull, "ZFC", Optional.of(rootHull), Collections.<ProjectedNodeKey>emptyList(),
                Arrays.asList(axiomsHull, definitionsHull), false, BoundaryTier.EMPHATIC),
            frozenEnclosure(axiomsHull, "Axioms", Optional.of(zfcHull),
                Arrays.asList(regularity, replacement, choice), Collections.<EnclosureHullKey>emptyList(), false,
                BoundaryTier.SUBTLE),
            frozenEnclosure(definitionsHull, "Basic Definitions and Theorems", Optional.of(zfcHull),
                Arrays.asList(pinnedTheorem, freeTheorem), Collections.<EnclosureHullKey>emptyList(), false,
                BoundaryTier.SUBTLE));
        final List<ProjectedEdge> edges = Arrays.asList(frozenEdge(regularity, freeTheorem, 0),
            frozenEdge(regularity, pinnedTheorem, 1));
        final PinProjection choicePin = pin(choice, -24.832420395427746, -34.920469854404410);
        final PinProjection theoremPin = pin(pinnedTheorem, -209.31397564145126, 9.820904009249132);
        final List<PinProjection> pins = Arrays.asList(choicePin, theoremPin);
        final GraphProjection projection = GraphProjection.projected(1L, nodes, enclosures, edges,
            Collections.<RelationshipResolution>emptyList(), pins);
        final LayoutPositions positions = positions(
            Arrays.asList(nodeEntry(regularity, -173.26206169386748, 1.8301628933959680),
                nodeEntry(replacement, 248.60834750232004, -59.339657536064465),
                nodeEntry(choice, -24.832420395427746, -34.920469854404410),
                nodeEntry(pinnedTheorem, -209.31397564145126, 9.820904009249132),
                nodeEntry(freeTheorem, -241.79904871734790, 14.381230674273278)),
            Arrays.asList(anchorEntry(rootHull, 0.0, 0.0), anchorEntry(zfcHull, 0.0, 0.0),
                anchorEntry(axiomsHull, 0.0, 0.0), anchorEntry(definitionsHull, 0.0, 0.0)));
        return new FrozenFixture(projection, positions, pins, regularity, replacement, choice, pinnedTheorem,
            freeTheorem, rootHull, zfcHull, axiomsHull, definitionsHull);
    }

    private static ProjectedNode frozenNode(ProjectedNodeKey key, String label) {
        return ProjectedNode.of(key, SafeNodeLabel.of(label, label), "map", false);
    }

    private static ProjectedEnclosure frozenEnclosure(EnclosureHullKey hull, String label,
            Optional<EnclosureHullKey> parent, List<ProjectedNodeKey> nodes,
            List<EnclosureHullKey> children, boolean mapRoot, BoundaryTier tier) {
        return ProjectedEnclosure.of(hull, hull.endpointKeys(),
            Collections.singletonList(SafeNodeLabel.of(label, label)), "map", parent, nodes, children, mapRoot,
            tier);
    }

    private static ProjectedEdge frozenEdge(ProjectedNodeKey source, ProjectedNodeKey target, int occurrence) {
        final ProjectedEndpointKey first = ProjectedEndpointKey.ofNode(source);
        final ProjectedEndpointKey second = ProjectedEndpointKey.ofNode(target);
        final ConnectorDescriptor descriptor = ConnectorDescriptor.of(source.source(),
            target.source().persistedReference().get(), false, true, "", "", "");
        final EdgeContributor contributor = EdgeContributor.nativeConnector(
            ConnectorSnapshot.of(occurrence, descriptor), first, second);
        return ProjectedEdge.of(ProjectedEdgeKey.of(first, second), Collections.singletonList(contributor));
    }

    private static ProjectedNodeKey frozenKey(String id) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(
            NodeReference.of(FROZEN_MAP, PersistedNodeId.of(id))));
    }

    private static final class FrozenFixture {
        final GraphProjection projection;
        final LayoutPositions positions;
        final List<PinProjection> pins;
        final ProjectedNodeKey regularity;
        final ProjectedNodeKey replacement;
        final ProjectedNodeKey choice;
        final ProjectedNodeKey pinnedTheorem;
        final ProjectedNodeKey freeTheorem;
        final EnclosureHullKey rootHull;
        final EnclosureHullKey zfcHull;
        final EnclosureHullKey axiomsHull;
        final EnclosureHullKey definitionsHull;

        FrozenFixture(GraphProjection projection, LayoutPositions positions, List<PinProjection> pins,
                ProjectedNodeKey regularity, ProjectedNodeKey replacement, ProjectedNodeKey choice,
                ProjectedNodeKey pinnedTheorem, ProjectedNodeKey freeTheorem, EnclosureHullKey rootHull,
                EnclosureHullKey zfcHull, EnclosureHullKey axiomsHull, EnclosureHullKey definitionsHull) {
            this.projection = projection;
            this.positions = positions;
            this.pins = pins;
            this.regularity = regularity;
            this.replacement = replacement;
            this.choice = choice;
            this.pinnedTheorem = pinnedTheorem;
            this.freeTheorem = freeTheorem;
            this.rootHull = rootHull;
            this.zfcHull = zfcHull;
            this.axiomsHull = axiomsHull;
            this.definitionsHull = definitionsHull;
        }
    }
```

Add these imports to the test file:

```java
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.GraphGeometryEngine;
import org.freeplane.plugin.graph.projection.EdgeContributor;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.input.ConnectorDescriptor;
import org.freeplane.plugin.graph.projection.input.ConnectorSnapshot;
```

- [ ] **Step 3: Run the test and confirm it passes**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould`
Expected: PASS, 28 tests.

- [ ] **Step 4: Commit**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationCorrectionShould.java
git commit -m "test(graph-layout): freeze the real-case sibling crossing regression"
```

## Task 13: Migrate production call sites to BoundarySeparationCorrection

**Implementer tier:** Advanced

**Lane:** integration

**Depends on:** 12

**Ownership:** freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutFrame.java, freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutWorker.java, freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java, freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/MapTierCorrection.java, freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutConflict.java, freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/GraphStreamBoundaryShould.java, freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java, freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinatorShould.java, freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java

**Validation:** gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.LayoutWorkerShould --tests org.freeplane.plugin.graph.control.LayoutSettleLoopShould --tests org.freeplane.plugin.graph.control.GraphUpdateCoordinatorShould --tests org.freeplane.plugin.graph.integration.GraphWorkspaceCommandAcceptanceShould --tests org.freeplane.plugin.graph.layout.GraphStreamBoundaryShould

**Files:**
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutFrame.java:15-104`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutWorker.java:36-49`, `:278-304`, `:339-364`
- Modify: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/control/LayoutSettleLoop.java:755-762`
- Delete: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/MapTierCorrection.java`
- Delete: `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutConflict.java`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/GraphStreamBoundaryShould.java:84-88`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/LayoutSettleLoopShould.java:31`, `:1193-1198`, `:1401-1407`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/control/GraphUpdateCoordinatorShould.java:1191-1195`, `:1601-1605`, `:1642-1648`
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/integration/GraphWorkspaceCommandAcceptanceShould.java:95`, `:420-424`

**Interfaces:**
- Consumes: `BoundarySeparationCorrection.apply(GraphProjection projection, LayoutPositions positions, GeometryTextMetrics metrics, List<PinProjection> pins) -> BoundarySeparationResult` (Tasks 7–12); `BoundarySeparationResult.positions()/diagnostics()/nodeResidualViolations()/appliedDisplacements()/timings()`; `BoundarySeparationDiagnostics.boundaryVerified()/boundaryCovered()/conflicts()/withDeltas(double, double)`; `BoundaryConflict` (Task 3); `BoundarySeparationException` (Task 2).
- Produces: `LayoutFrame.boundaryDiagnostics()`/`conflicts()`; `LayoutFrame.withDiagnostics(LayoutFrame, BoundarySeparationDiagnostics, PerceptualIdlePolicy.IdleMeasurement)`; `LayoutWorker` migration with `previousAppliedDisplacements`, fail-closed coverage assertion and `restart()` reset; `LayoutSettleLoop` retained-diagnostics consumption; the updated `publicLayoutTypes()` gate.

- [ ] **Step 1: Migrate `LayoutFrame` to the diagnostics type**

Replace the whole file with:

```java
package org.freeplane.plugin.graph.layout;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;

public final class LayoutFrame {
    public static final int UNVERIFIED = -1;

    private final long stepIndex;
    private final LayoutPositions positions;
    private final boolean failed;
    private final int residualViolations;
    private final BoundarySeparationDiagnostics boundaryDiagnostics;
    private final PerceptualIdlePolicy.IdleMeasurement idle;

    private LayoutFrame(final long stepIndex, final LayoutPositions positions, final boolean failed,
            final int residualViolations, final BoundarySeparationDiagnostics boundaryDiagnostics,
            final PerceptualIdlePolicy.IdleMeasurement idle) {
        if (stepIndex < 0) {
            throw new IllegalArgumentException("Layout frame index must be nonnegative");
        }
        if (residualViolations < UNVERIFIED) {
            throw new IllegalArgumentException("Residual violations must be >= UNVERIFIED");
        }
        this.stepIndex = stepIndex;
        this.positions = Objects.requireNonNull(positions, "positions");
        validateFinite(positions.nodes(), "node");
        validateFinite(positions.anchors(), "anchor");
        this.failed = failed;
        this.residualViolations = residualViolations;
        this.boundaryDiagnostics = Objects.requireNonNull(boundaryDiagnostics, "boundaryDiagnostics");
        this.idle = Objects.requireNonNull(idle, "idle");
    }

    /** Engine-internal frames only: the residual is unknown and `LayoutWorker.accept` re-wraps them. */
    public static LayoutFrame of(final long stepIndex, final LayoutPositions positions, final boolean failed) {
        return of(stepIndex, positions, failed, UNVERIFIED);
    }

    public static LayoutFrame of(final long stepIndex, final LayoutPositions positions, final boolean failed,
            final int residualViolations) {
        return new LayoutFrame(stepIndex, positions, failed, residualViolations,
            BoundarySeparationDiagnostics.empty(), PerceptualIdlePolicy.IdleMeasurement.initial());
    }

    public static LayoutFrame withDiagnostics(final LayoutFrame raw,
            final BoundarySeparationDiagnostics diagnostics,
            final PerceptualIdlePolicy.IdleMeasurement idle) {
        final LayoutFrame value = Objects.requireNonNull(raw, "raw");
        return new LayoutFrame(value.stepIndex, value.positions, value.failed, value.residualViolations,
            diagnostics, idle);
    }

    public long stepIndex() {
        return stepIndex;
    }

    public LayoutPositions positions() {
        return positions;
    }

    public boolean failed() {
        return failed;
    }

    public int residualViolations() {
        return residualViolations;
    }

    public boolean verified() {
        return residualViolations >= 0;
    }

    public BoundarySeparationDiagnostics boundaryDiagnostics() {
        return boundaryDiagnostics;
    }

    public List<BoundaryConflict> conflicts() {
        return boundaryDiagnostics.conflicts();
    }

    public PerceptualIdlePolicy.IdleMeasurement idle() {
        return idle;
    }

    private static <K> void validateFinite(final Map<K, LayoutPoint> values, final String kind) {
        for (final Map.Entry<K, LayoutPoint> entry : values.entrySet()) {
            final LayoutPoint point = Objects.requireNonNull(entry.getValue(), kind + " position");
            if (!Double.isFinite(point.x()) || !Double.isFinite(point.y())) {
                throw new IllegalArgumentException("Layout frame coordinates must be finite");
            }
        }
    }
}
```

- [ ] **Step 2: Migrate `LayoutWorker` to the component**

In `LayoutWorker.java`, add the imports `import java.util.Map;` and `import org.freeplane.plugin.graph.geometry.LayoutPoint;` and delete the imports of `GraphGeometry` and `GraphGeometryEngine`.

Replace the `EMPTY_FAILED_FRAME` constant with:

```java
    private static final LayoutFrame EMPTY_FAILED_FRAME = LayoutFrame.withDiagnostics(
        LayoutFrame.of(0L, LayoutPositions.of(
            Collections.<ProjectedNodeKey, LayoutPoint>emptyMap(),
            Collections.<EnclosureHullKey, LayoutPoint>emptyMap()), true, 0),
        BoundarySeparationDiagnostics.empty(), PerceptualIdlePolicy.IdleMeasurement.initial());
```

Replace the `geometryEngine` and `mapCorrection` fields with:

```java
    private final BoundarySeparationCorrection boundaryCorrection = new BoundarySeparationCorrection();
```

Add next to `previousCorrectedPositions`:

```java
    private Map<String, LayoutPoint> previousAppliedDisplacements = Collections.emptyMap();
```

Replace the whole `accept` method with:

```java
    private LayoutFrame accept(final LayoutRequest request, final LayoutFrame raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Layout engine returned no frame");
        }
        if (raw.failed()) {
            failedEngine = true;
            return failedFrame(raw.stepIndex());
        }
        validateCoverage(request.projection(), raw.positions());
        final BoundarySeparationResult result = boundaryCorrection.apply(request.projection(), raw.positions(),
            defaultMetrics(), request.pins());
        if (!result.diagnostics().boundaryVerified() && !result.diagnostics().boundaryCovered()) {
            throw new BoundarySeparationException("Boundary separation did not cover every residual pair");
        }
        final LayoutPositions corrected = result.positions();
        final LayoutPositions before = previousCorrectedPositions == null ? corrected
            : previousCorrectedPositions;
        final PerceptualIdlePolicy.IdleMeasurement idle = idlePolicy.observe(before, corrected);
        final double[] deltas = displacementDeltas(previousAppliedDisplacements,
            result.appliedDisplacements());
        final LayoutFrame decorated = LayoutFrame.withDiagnostics(
            LayoutFrame.of(raw.stepIndex(), corrected, false, result.nodeResidualViolations()),
            result.diagnostics().withDeltas(deltas[0], deltas[1]), idle);
        previousAppliedDisplacements = result.appliedDisplacements();
        currentRequest = request;
        hasRequest = true;
        previousCorrectedPositions = corrected;
        lastValidFrame = decorated;
        return decorated;
    }

    private static double[] displacementDeltas(final Map<String, LayoutPoint> previous,
            final Map<String, LayoutPoint> current) {
        final Set<String> keys = new LinkedHashSet<String>(previous.keySet());
        keys.addAll(current.keySet());
        if (keys.isEmpty()) {
            return new double[] {0.0, 0.0};
        }
        double sumSquares = 0.0;
        double maximum = 0.0;
        for (final String key : keys) {
            final LayoutPoint before = previous.get(key);
            final LayoutPoint after = current.get(key);
            final double deltaX = (after == null ? 0.0 : after.x()) - (before == null ? 0.0 : before.x());
            final double deltaY = (after == null ? 0.0 : after.y()) - (before == null ? 0.0 : before.y());
            final double squared = deltaX * deltaX + deltaY * deltaY;
            sumSquares += squared;
            maximum = Math.max(maximum, Math.sqrt(squared));
        }
        return new double[] {Math.sqrt(sumSquares / keys.size()), maximum};
    }
```

Delete the now-unused `pinnedNodes(final List<PinProjection> pins)` helper. Keep `defaultMetrics()` and `validateCoverage(...)` unchanged.

In `runRestart`, add `previousAppliedDisplacements = Collections.emptyMap();` after `failedEngine = false;`.

Replace the `failedFrame` diagnostics argument:

```java
        return LayoutFrame.withDiagnostics(
            LayoutFrame.of(index, retained.positions(), true, retained.residualViolations()),
            retained.boundaryDiagnostics(), retained.idle());
```

- [ ] **Step 3: Migrate `LayoutSettleLoop` retained-frame consumption**

Delete the import `import org.freeplane.plugin.graph.layout.LayoutConflict;` and replace the tail of `failedFrame` with:

```java
        final BoundarySeparationDiagnostics diagnostics = retained.boundaryDiagnostics();
        return LayoutFrame.withDiagnostics(LayoutFrame.of(index, positions, true, residual), diagnostics,
            retained.idle());
```

Add the import `import org.freeplane.plugin.graph.layout.BoundarySeparationDiagnostics;`. The `NodeSeparationProjection` fallback path and the worker hull computation stay unchanged.

- [ ] **Step 4: Delete the replaced production types**

```bash
git rm freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/MapTierCorrection.java freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/layout/LayoutConflict.java
```

- [ ] **Step 5: Migrate the four affected test call sites**

In `LayoutSettleLoopShould.java`, delete the `LayoutConflict` import, and in `retainsTheCurrentGenerationWhenPhysicalResetFails` replace

```java
        MapReferenceId otherMap = MapReferenceId.of("00000000-0000-0000-0000-000000000103");
        LayoutConflict conflict = LayoutConflict.of(MAP, otherMap, Collections.emptyList());
        PerceptualIdlePolicy.IdleMeasurement idle = new PerceptualIdlePolicy.IdleMeasurement(1.5, 2.5, 7, true);
        LayoutFrame retained = LayoutFrame.withDiagnostics(LayoutFrame.of(9L, positions(projection, 123.5), false, 0),
            Collections.singletonList(conflict), idle);
```

with

```java
        BoundaryConflict conflict = new BoundaryConflict(projection.enclosures().get(0).hullKey(),
            EnclosureHullKey.of(Collections.singletonList(
                EnclosureKey.of(SourceNodeKey.persisted(reference("conflict-hull"))))),
            BoundaryConflict.Kind.SIBLING_CROSSING, BoundaryConflict.Reason.ROUND_LIMIT,
            Collections.emptyList());
        BoundarySeparationDiagnostics diagnostics = new BoundarySeparationDiagnostics(
            Collections.singletonList(conflict), Collections.singletonList(conflict.pairKey()), 1, 1, 0, 0.0,
            0.0, Collections.<String, org.freeplane.plugin.graph.geometry.LayoutPoint>emptyMap(), 0.0, 0.0);
        PerceptualIdlePolicy.IdleMeasurement idle = new PerceptualIdlePolicy.IdleMeasurement(1.5, 2.5, 7, true);
        LayoutFrame retained = LayoutFrame.withDiagnostics(LayoutFrame.of(9L, positions(projection, 123.5), false, 0),
            diagnostics, idle);
```

and replace the frame-factory helper's `LayoutFrame.withDiagnostics(..., Collections.emptyList(), ...)` with `BoundarySeparationDiagnostics.empty()`. Add the imports `org.freeplane.plugin.graph.layout.BoundaryConflict` and `org.freeplane.plugin.graph.layout.BoundarySeparationDiagnostics`. The helper uses the test's existing `reference(String)` factory for the second hull key.

`LayoutWorkerShould.java` needs no type migration: it constructs no frames with `withDiagnostics`, names no `LayoutConflict`, and reads only `conflicts()`/`blockingPins()`, which keep their meaning; Task 14 adds the new worker tests to it.

In `GraphUpdateCoordinatorShould.java`, replace all three `LayoutFrame.withDiagnostics(..., Collections.emptyList(), ...)` calls with `BoundarySeparationDiagnostics.empty()` and add the `org.freeplane.plugin.graph.layout.BoundarySeparationDiagnostics` import.

In `GraphWorkspaceCommandAcceptanceShould.java`, delete the `LayoutConflict` import and replace

```java
            final LayoutConflict conflict = LayoutConflict.of(MAP_ONE, MAP_TWO, Collections.singletonList(pin));
```

with

```java
            final EnclosureHullKey firstHull = EnclosureHullKey.of(Collections.singletonList(
                EnclosureKey.of(SourceNodeKey.persisted(node(MAP_ONE, "root-one")))));
            final EnclosureHullKey secondHull = EnclosureHullKey.of(Collections.singletonList(
                EnclosureKey.of(SourceNodeKey.persisted(node(MAP_TWO, "root-two")))));
            final BoundaryConflict conflict = new BoundaryConflict(firstHull, secondHull,
                BoundaryConflict.Kind.SIBLING_CROSSING, BoundaryConflict.Reason.IMMOVABLE_SIDES,
                Collections.singletonList(pin));
```

Add the imports `org.freeplane.plugin.graph.layout.BoundaryConflict`, `org.freeplane.plugin.graph.projection.EnclosureHullKey` and `org.freeplane.plugin.graph.projection.EnclosureKey` if they are not already present; the test's existing `node(MapReferenceId, String)` helper returns the `NodeReference` to wrap.

- [ ] **Step 6: Add the new public layout types to the GraphStream-freedom gate**

In `GraphStreamBoundaryShould.publicLayoutTypes()`, replace the returned list with:

```java
        return Arrays.<Class<?>>asList(LayoutEngine.class, LayoutCalibration.class, LayoutRequest.class,
            LayoutFrame.class, NodeSeparationProjection.class, NodeSeparationResult.class,
            BoundarySeparationCorrection.class, BoundarySeparationResult.class,
            BoundarySeparationDiagnostics.class, BoundarySeparationTimings.class, BoundaryConflict.class,
            BoundarySeparationException.class, GraphStreamLayoutFactory.class);
```

- [ ] **Step 7: Run the affected suites**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.LayoutWorkerShould --tests org.freeplane.plugin.graph.control.LayoutSettleLoopShould --tests org.freeplane.plugin.graph.control.GraphUpdateCoordinatorShould --tests org.freeplane.plugin.graph.integration.GraphWorkspaceCommandAcceptanceShould --tests org.freeplane.plugin.graph.layout.GraphStreamBoundaryShould`
Expected: PASS. `LayoutWorkerShould` is not edited; it exercises the migrated worker through the new component. If `decorateNonEmptyFramesWithTemporaryGeometryAndCorrection` or `applyActiveDormantAndUnpinnedTransitions` fails on pin or delta expectations, fix the production migration, not the assertions: those tests pin the preserved whole-map rigidity semantics.

- [ ] **Step 8: Commit**

```bash
git add -A freeplane_plugin_graph/src/main freeplane_plugin_graph/src/test
git commit -m "refactor(graph-layout): publish boundary diagnostics through the worker and frame"
```

## Task 14: Worker failure channel, displacement deltas, and restart reset

**Implementer tier:** Standard

**Lane:** integration

**Depends on:** 13

**Ownership:** freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/LayoutWorkerShould.java

**Validation:** gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.LayoutWorkerShould

**Files:**
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/LayoutWorkerShould.java` (append tests and two fake engines before the final closing brace)

**Interfaces:**
- Consumes: `LayoutWorker` (submit/step/restart/close), `LayoutFrame.boundaryDiagnostics()`, `BoundarySeparationDiagnostics.appliedDisplacements()/deltaRms()/deltaMax()/displacementRms()/displacementMax()`, `BoundarySeparationException`, the existing `CountingEngine`/`FixedEngineSupplier`/`SequenceEngineSupplier`/`request()` helpers.
- Produces: the fail-closed state-transition test (§4.2/R19/R20) and the delta-decoration test.

- [ ] **Step 1: Write the failing tests**

Append these members to `LayoutWorkerShould`, immediately before its final closing brace.

```java
    @Test
    public void retainBoundaryDiagnosticsWhenTheComponentFailsClosed() throws Exception {
        ThrowingBoundaryEngine throwing = new ThrowingBoundaryEngine();
        CountingEngine recovered = new CountingEngine(new AtomicInteger(), new AtomicInteger());
        LayoutWorker worker = new LayoutWorker(
            new SequenceEngineSupplier(Arrays.<LayoutEngine>asList(throwing, recovered)),
            PerceptualIdlePolicy.spikeDefaults());
        try {
            LayoutFrame first = await(worker.submit(request()));
            assertThat(first.failed()).isFalse();

            LayoutFrame failed = await(worker.step());

            assertThat(failed.failed()).isTrue();
            assertThat(failed.positions()).isEqualTo(first.positions());
            assertThat(failed.residualViolations()).isEqualTo(first.residualViolations());
            assertThat(failed.boundaryDiagnostics()).isSameAs(first.boundaryDiagnostics());

            worker.restart();
            LayoutFrame restored = await(worker.submit(request()));

            assertThat(restored.failed()).isFalse();
            assertThat(restored.boundaryDiagnostics().boundaryVerified()
                || restored.boundaryDiagnostics().boundaryCovered()).isTrue();
        }
        finally {
            worker.close();
        }
    }

    @Test
    public void decorateConsecutiveFramesWithDisplacementDeltas() throws Exception {
        LayoutWorker worker = new LayoutWorker(new FixedEngineSupplier(new ShiftingEngine()),
            PerceptualIdlePolicy.spikeDefaults());
        try {
            LayoutFrame first = await(worker.submit(request()));

            assertThat(first.boundaryDiagnostics().deltaMax())
                .isEqualTo(first.boundaryDiagnostics().displacementMax());
            assertThat(first.boundaryDiagnostics().deltaRms())
                .isEqualTo(first.boundaryDiagnostics().displacementRms());

            LayoutFrame stepped = await(worker.step());
            double[] expected = expectedDeltas(first.boundaryDiagnostics().appliedDisplacements(),
                stepped.boundaryDiagnostics().appliedDisplacements());

            assertThat(stepped.boundaryDiagnostics().deltaMax()).isGreaterThan(0.0);
            assertThat(stepped.boundaryDiagnostics().deltaMax()).isEqualTo(expected[1]);
            assertThat(stepped.boundaryDiagnostics().deltaRms()).isEqualTo(expected[0]);
        }
        finally {
            worker.close();
        }
    }

    @Test
    public void restartResetsTheDisplacementField() throws Exception {
        ThrowingBoundaryEngine throwing = new ThrowingBoundaryEngine();
        CountingEngine recovered = new CountingEngine(new AtomicInteger(), new AtomicInteger());
        LayoutWorker worker = new LayoutWorker(
            new SequenceEngineSupplier(Arrays.<LayoutEngine>asList(throwing, recovered)),
            PerceptualIdlePolicy.spikeDefaults());
        try {
            LayoutFrame first = await(worker.submit(request()));
            assertThat(first.boundaryDiagnostics().displacementMax()).isGreaterThan(0.0);
            assertThat(await(worker.step()).failed()).isTrue();

            worker.restart();
            LayoutFrame restored = await(worker.submit(request()));

            assertThat(restored.failed()).isFalse();
            assertThat(restored.boundaryDiagnostics().deltaMax())
                .isEqualTo(restored.boundaryDiagnostics().displacementMax());
            assertThat(restored.boundaryDiagnostics().deltaRms())
                .isEqualTo(restored.boundaryDiagnostics().displacementRms());
        }
        finally {
            worker.close();
        }
    }

    private static double[] expectedDeltas(Map<String, LayoutPoint> previous,
            Map<String, LayoutPoint> current) {
        Set<String> keys = new LinkedHashSet<String>(previous.keySet());
        keys.addAll(current.keySet());
        if (keys.isEmpty()) {
            return new double[] {0.0, 0.0};
        }
        double sumSquares = 0.0;
        double maximum = 0.0;
        for (String key : keys) {
            LayoutPoint before = previous.get(key);
            LayoutPoint after = current.get(key);
            double deltaX = (after == null ? 0.0 : after.x()) - (before == null ? 0.0 : before.x());
            double deltaY = (after == null ? 0.0 : after.y()) - (before == null ? 0.0 : before.y());
            double squared = deltaX * deltaX + deltaY * deltaY;
            sumSquares += squared;
            maximum = Math.max(maximum, Math.sqrt(squared));
        }
        return new double[] {Math.sqrt(sumSquares / keys.size()), maximum};
    }

    private static final class ShiftingEngine implements LayoutEngine {
        private GraphProjection projection;
        private int step;

        @Override
        public LayoutFrame apply(LayoutRequest request) {
            projection = request.projection();
            step = 0;
            return frame(0.0);
        }

        @Override
        public LayoutFrame step() {
            step++;
            return frame(step * 5.0);
        }

        @Override
        public void reset() {
        }

        @Override
        public void close() {
        }

        private LayoutFrame frame(double shift) {
            LayoutPositions raw = rawPositions(projection);
            Map<ProjectedNodeKey, LayoutPoint> nodes = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>();
            for (Map.Entry<ProjectedNodeKey, LayoutPoint> entry : raw.nodes().entrySet()) {
                double x = entry.getKey().mapReferenceId().equals(MAP_ONE)
                    ? entry.getValue().x() + shift : entry.getValue().x();
                nodes.put(entry.getKey(), LayoutPoint.of(x, entry.getValue().y()));
            }
            Map<EnclosureHullKey, LayoutPoint> anchors = new LinkedHashMap<EnclosureHullKey, LayoutPoint>();
            for (Map.Entry<EnclosureHullKey, LayoutPoint> entry : raw.anchors().entrySet()) {
                double x = entry.getKey().mapReferenceId().equals(MAP_ONE)
                    ? entry.getValue().x() + shift : entry.getValue().x();
                anchors.put(entry.getKey(), LayoutPoint.of(x, entry.getValue().y()));
            }
            return LayoutFrame.of(step, LayoutPositions.of(nodes, anchors), false);
        }
    }

    private static final class ThrowingBoundaryEngine extends CountingEngine {
        ThrowingBoundaryEngine() {
            super(new AtomicInteger(), new AtomicInteger());
        }

        @Override
        public LayoutFrame step() {
            throw new BoundarySeparationException("fake MST failure");
        }
    }
```

Add the imports `org.freeplane.plugin.graph.geometry.LayoutPoint`, `java.util.LinkedHashSet` and `java.util.Set` if they are not already present.

- [ ] **Step 2: Run the tests and confirm they pass**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.LayoutWorkerShould`
Expected: PASS. `BoundarySeparationException` from the engine rides the existing `RuntimeException` fail-closed channel; the retained diagnostics are the last valid frame's diagnostics unchanged; `restart()` clears `previousAppliedDisplacements`, so the first recovered frame's deltas equal its displacement magnitudes.

- [ ] **Step 3: Commit**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/LayoutWorkerShould.java
git commit -m "test(graph-layout): cover worker fail-closed diagnostics and displacement deltas"
```

## Task 15: Performance diagnostic stage mapping and golden conflict expectations

**Implementer tier:** Advanced

**Lane:** integration

**Depends on:** 14

**Ownership:** freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java

**Validation:** gradle :freeplane_plugin_graph:graphPerformanceDiagnostic --rerun-tasks

**Files:**
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java:61-66` (imports), `:90-100` (fields), `:244-300` (worker-probe validation), `:341-400` (direct probe), `:531-553` (pin conflicts)

**Interfaces:**
- Consumes: `BoundarySeparationCorrection`, `BoundarySeparationResult`, `BoundarySeparationDiagnostics`, `BoundaryConflict` (Tasks 2–12).
- Produces: `SEPARATION` ← `timings().separationNanos()`, `HULL` ← `timings().hullNanos()`, `CORRECTION` ← `planNanos() + applyNanos()`, each recorded exactly once per sample; boundary diagnostics validation in both probes; the re-derived `TWO_PINNED_MAPS` golden constant and per-pair pin-identity checks.

- [ ] **Step 1: Replace the correction field and add the goldens**

Replace the imports of `org.freeplane.plugin.graph.layout.LayoutConflict` and `org.freeplane.plugin.graph.layout.MapTierCorrection` with:

```java
import org.freeplane.plugin.graph.layout.BoundaryConflict;
import org.freeplane.plugin.graph.layout.BoundarySeparationCorrection;
import org.freeplane.plugin.graph.layout.BoundarySeparationDiagnostics;
import org.freeplane.plugin.graph.layout.BoundarySeparationResult;
```

Replace the field

```java
    private final MapTierCorrection correctionEngine = new MapTierCorrection();
```

with

```java
    private final BoundarySeparationCorrection correctionEngine = new BoundarySeparationCorrection();
```

Add next to the other private constants:

```java
    /**
     * Golden conflict count for the TWO_PINNED_MAPS scenario under generalized enforcement.
     * Captured from the first measured run and updated only from a measured run; see the task step.
     */
    private static final int TWO_PINNED_MAPS_EXPECTED_CONFLICTS = 1;
```

- [ ] **Step 2: Replace the direct-probe correction block with the component call**

Replace the block from `final long correctionStart = clock.nanoTime();` through the `HULL` record with:

```java
            final BoundarySeparationResult corrected = correctionEngine.apply(projection, applied.positions(),
                textMetrics, request.pins());
            recordTiming(measurements, PerformanceMeasurements.Stage.SEPARATION,
                corrected.timings().separationNanos(), warmup);
            recordTiming(measurements, PerformanceMeasurements.Stage.HULL, corrected.timings().hullNanos(),
                warmup);
            recordTiming(measurements, PerformanceMeasurements.Stage.CORRECTION,
                corrected.timings().planNanos() + corrected.timings().applyNanos(), warmup);
            validateBoundaryDiagnostics(corrected.diagnostics(), PerformanceMeasurements.Stage.CORRECTION);
```

Delete the now-unused `pinnedNodes(final List<PinProjection> pins)` helper. Add the helpers:

```java
    private static void recordTiming(final PerformanceMeasurements measurements,
            final PerformanceMeasurements.Stage stage, final long nanos, final boolean warmup) {
        if (warmup) {
            measurements.recordWarmup(stage, nanos);
        }
        else {
            measurements.recordMeasured(stage, nanos);
        }
    }

    private static void validateBoundaryDiagnostics(final BoundarySeparationDiagnostics diagnostics,
            final PerformanceMeasurements.Stage stage) {
        if (diagnostics.rounds() < 0
                || diagnostics.rounds() > BoundarySeparationCorrection.MAX_DISPLACEMENT_ROUNDS) {
            throw new DiagnosticFailure(stage, "Boundary displacement rounds outside the production bound: "
                + diagnostics.rounds());
        }
        if (!diagnostics.boundaryVerified() && !diagnostics.boundaryCovered()) {
            throw new DiagnosticFailure(stage,
                "Boundary diagnostics are neither verified nor covered; rounds=" + diagnostics.rounds()
                    + " detected=" + diagnostics.hullViolationsDetected()
                    + " residual=" + diagnostics.hullResidualViolations()
                    + " residualPairs=" + diagnostics.residualHullPairs()
                    + " displacementRms=" + diagnostics.displacementRms()
                    + " displacementMax=" + diagnostics.displacementMax()
                    + " worstMapDisplacement=" + diagnostics.worstMapDisplacement());
        }
    }
```

- [ ] **Step 3: Validate the worker probe diagnostics**

In `runSample`, immediately after `validatePinConflicts(generated, workerFrame);`, add:

```java
        validateBoundaryDiagnostics(workerFrame.boundaryDiagnostics(), PerformanceMeasurements.Stage.FULL_WORKER);
```

- [ ] **Step 4: Migrate the pin-conflict validation under generalized enforcement**

Replace the whole `validatePinConflicts` method with:

```java
    private void validatePinConflicts(final GeneratedWorkspace generated, final LayoutFrame frame) {
        final GeneratedWorkspace.Scenario scenario = generated.scenario();
        if (scenario == GeneratedWorkspace.Scenario.ONE_PINNED_MAP && !frame.conflicts().isEmpty()) {
            throw new DiagnosticFailure(PerformanceMeasurements.Stage.FULL_WORKER,
                "One-pinned-map unexpectedly produced rigid conflicts");
        }
        if (scenario != GeneratedWorkspace.Scenario.TWO_PINNED_MAPS) {
            return;
        }
        if (frame.conflicts().size() != TWO_PINNED_MAPS_EXPECTED_CONFLICTS) {
            throw new DiagnosticFailure(PerformanceMeasurements.Stage.FULL_WORKER,
                "Two-pinned-maps conflict count changed: expected " + TWO_PINNED_MAPS_EXPECTED_CONFLICTS
                    + " but measured " + frame.conflicts().size());
        }
        for (final BoundaryConflict conflict : frame.conflicts()) {
            if (conflict.firstMap().equals(conflict.secondMap())) {
                for (final PinProjection pin : conflict.blockingPins()) {
                    if (!pin.source().mapReferenceId().equals(conflict.firstMap())) {
                        throw new DiagnosticFailure(PerformanceMeasurements.Stage.FULL_WORKER,
                            "A same-map conflict listed a pin from another map");
                    }
                }
                continue;
            }
            if (conflict.blockingPins().size() != 2
                    || !conflict.blockingPins().get(0).source().nodeId().value().equals("m00-n0001")
                    || !conflict.blockingPins().get(1).source().nodeId().value().equals("m01-n0001")) {
                throw new DiagnosticFailure(PerformanceMeasurements.Stage.FULL_WORKER,
                    "Two-pinned-maps conflict did not retain both pin identities");
            }
        }
    }
```

- [ ] **Step 5: Re-derive the golden conflict count from a measured run**

Run: `gradle :freeplane_plugin_graph:graphPerformanceDiagnostic --rerun-tasks`

The first run either passes (the generalized enforcement still produces exactly one rigid conflict for `TWO_PINNED_MAPS`) or fails with `Two-pinned-maps conflict count changed: expected 1 but measured N`. In the failure case set `TWO_PINNED_MAPS_EXPECTED_CONFLICTS = N` and run again. Record the measured N and the per-conflict pair kinds in the task report. If any cross-map conflict between the two pinned maps does not carry both pin identities, that is a correctness defect in Tasks 7–12, not a golden to update: stop and report `DONE_WITH_CONCERNS` with the conflict details.

- [ ] **Step 6: Confirm the tripwire suites still pass**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.performance.PerformanceTripwiresShould`
Expected: PASS. The ledger must show full warmup/measured counts for every stage (the diagnostic's `validateScenarioLifecycle` enforces this).

- [ ] **Step 7: Commit**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/GraphWorkspacePerformanceDiagnostic.java
git commit -m "test(graph-performance): map boundary correction timings and re-derive pin goldens"
```

## Task 16: Performance gate measurement and evidence

**Implementer tier:** Standard

**Lane:** integration

**Depends on:** 15

**Ownership:** docs/superpowers/plans/2026-09-12-graph-boundary-separation-performance.md

**Validation:** gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PgraphStrictPerformance --rerun-tasks -PTestLoggingFull

**Files:**
- Create: `docs/superpowers/plans/2026-09-12-graph-boundary-separation-performance.md`

**Interfaces:**
- Consumes: the migrated performance diagnostic (Task 15) and its ledger at `freeplane_plugin_graph/build/graph-performance/performance-ledger.csv`.
- Produces: the recorded strict-gate result, the correction overhead split, and the chosen hull-recomputation interface statement.

- [ ] **Step 1: Run the strict performance diagnostic**

Run: `gradle :freeplane_plugin_graph:graphPerformanceDiagnostic -PgraphStrictPerformance --rerun-tasks -PTestLoggingFull`
Expected: PASS with every ledger row `pass=true`, in particular the `reference-2000-5000` `full-worker` row against `PerformanceMeasurements.STRICT_FULL_WORKER_NANOS = 100_000_000L` (and its strict `force` neighbour). This run is the authoritative gate; do not weaken any threshold.

- [ ] **Step 2: Extract and record the measured numbers**

Read `freeplane_plugin_graph/build/graph-performance/performance-ledger.csv` and record in `docs/superpowers/plans/2026-09-12-graph-boundary-separation-performance.md`:

- the `reference-2000-5000` rows for `correction`, `separation`, `hull`, `full-worker` with their p50/p95/p99/max and pass flags;
- the correction overhead split (`correction` = plan + apply, per Task 15's mapping);
- the chosen implementation interface statement: "The component recomputes all hulls each detection pass through its private `GraphGeometryEngine` (R15 default). The affected-only recomputation interface is not implemented because the strict gate passed; it remains the documented contingency if a future measurement exceeds the gate."

- [ ] **Step 3: Handle a strict-gate failure without weakening the gate**

If any strict row fails, do not change thresholds and do not add a parallel execution path. Stop and report `BLOCKED` with: the failing row, the measured p50/p95/p99/max, the strict threshold, and the candidate-ledger excerpt. The affected-only recomputation fallback requires the measured numbers and is deliberately a follow-up plan task; the AGENTS.md "ask for help when blocked, don't guess" rule governs here.

- [ ] **Step 4: Commit the evidence file**

```bash
git add docs/superpowers/plans/2026-09-12-graph-boundary-separation-performance.md
git commit -m "docs(graph-performance): record boundary correction strict-gate evidence"
```

## Task 17: Reproduced-case pipeline fixture with red-before/green-after

**Implementer tier:** Capable

**Lane:** pipeline

**Depends on:** 13, 6

**Ownership:** freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationShould.java

**Validation:** gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationShould

**Files:**
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationShould.java` (append the reproduced-case fixture and its two tests before the final closing brace; add imports)

**Interfaces:**
- Consumes: `GraphStreamLayoutFactory.create(LayoutCalibration)`, `LayoutEngine.apply/step/close`, `LayoutWorker.submit/step/close`, `LayoutFrame.boundaryDiagnostics()`, `BoundaryInvariantAssertions.assertVerifiedFrame/assertBijection` (Task 6), `GraphGeometryEngine.computeHulls`, `HullIntersection.siblingOverlap/minimumSeparatingTranslation`, `CanonicalLayoutKeys.nodeField`.
- Produces: a deterministic, self-contained rebuild of the reproduced `math.fpg` projection driven through the real engine and the real worker.

**Fixture design.** Build the five real nodes (`ID_1133378501`, `ID_822182441`, `ID_130337169`, `ID_1901523076`, `ID_1387156674`), the four real enclosures (suppressed `ID_435635462`, `ID_1675547143` ZFC, `ID_1912952190` Axioms, `ID_978732953` Basic Definitions), the two real connectors `Regularity -> free Theorem` and `Regularity -> pinned Theorem`, and the two real pins (`ID_130337169` at `(-24.832420395427746, -34.920469854404410)`; `ID_1901523076` at `(-209.31397564145126, 9.820904009249132)`), exactly as the frozen fixture does. Request 1 seals all five nodes at their recorded settled positions, so the engine's transient trajectory is bypassed and the raw settle is the reproduced equilibrium; request 2 keeps only the two real pins. The raw frame after request 2 is therefore the recorded crossing (the design's reproduced defect), and the correction resolves it in one round.

**Why the reproduced projection instead of a synthetic pinned fixture.** A synthetic two-sibling fixture with free facing nodes and pinned back nodes at depth 6 is statically correct but dynamically unstable: the free front nodes swing across each other in the production force model and later frames become immovable residuals. The per-candidate capability pattern (attempt-5 half, mixed `3 + 7`, nested recursion, ties) is therefore covered deterministically at the unit level in Tasks 9 and 10, where the correction is fed exact fixtures. This pipeline fixture covers the integration property the spec requires: the real crossing appears in the raw settle, the worker observes it (`hullViolationsDetected >= 1`), every published frame satisfies the I6 bijection and `rounds <= 2`, and every verified frame satisfies I1/I2/I3/I4.

- [ ] **Step 1: Write the failing pipeline tests**

Append these members to `BoundarySeparationShould`, immediately before its final closing brace.

```java
    private static final MapReferenceId PIPELINE_MAP =
        MapReferenceId.of("8055d8c8-d71e-40f3-a8ed-5bf2502f2cad");
    private static final WorkspaceId PIPELINE_WORKSPACE =
        WorkspaceId.of("1df8f60e-2643-4661-b126-ab13064f4091");
    private static final String PIPELINE_REGULARITY = "ID_1133378501";
    private static final String PIPELINE_REPLACEMENT = "ID_822182441";
    private static final String PIPELINE_CHOICE = "ID_130337169";
    private static final String PIPELINE_PINNED_THEOREM = "ID_1901523076";
    private static final String PIPELINE_FREE_THEOREM = "ID_1387156674";
    private static final String PIPELINE_ROOT = "ID_435635462";
    private static final String PIPELINE_ZFC = "ID_1675547143";
    private static final String PIPELINE_AXIOMS = "ID_1912952190";
    private static final String PIPELINE_DEFINITIONS = "ID_978732953";
    private static final org.freeplane.plugin.graph.geometry.GeometryTextMetrics PIPELINE_METRICS =
        new org.freeplane.plugin.graph.geometry.AwtGeometryTextMetrics(
            new java.awt.Font(java.awt.Font.DIALOG, java.awt.Font.PLAIN, 12),
            new java.awt.font.FontRenderContext(null, true, true));

    @Test
    public void reproducedCasePipelineFixtureCrossesAtTheRawSettle() {
        final GraphProjection projection = pipelineProjection();
        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            engine.apply(pipelineRequest(projection, pipelineAllPins()));
            for (int step = 0; step < 1500; step++) {
                engine.step();
            }
            final LayoutFrame raw = engine.apply(pipelineRequest(projection, pipelineRealPins()));
            final org.freeplane.plugin.graph.geometry.GraphGeometry geometry =
                new org.freeplane.plugin.graph.geometry.GraphGeometryEngine().computeHulls(projection,
                    raw.positions(), PIPELINE_METRICS);
            final org.freeplane.plugin.graph.geometry.HullGeometry first = geometry.hulls()
                .get(pipelineHull(PIPELINE_AXIOMS));
            final org.freeplane.plugin.graph.geometry.HullGeometry second = geometry.hulls()
                .get(pipelineHull(PIPELINE_DEFINITIONS));

            assertThat(raw.positions().nodes().get(pipelineKey(PIPELINE_REGULARITY)))
                .isEqualTo(LayoutPoint.of(-173.26206169386748, 1.8301628933959680));
            assertThat(org.freeplane.plugin.graph.geometry.HullIntersection.siblingOverlap(first, second))
                .isTrue();
            final LayoutPoint translation =
                org.freeplane.plugin.graph.geometry.HullIntersection.minimumSeparatingTranslation(first,
                    second);
            assertThat(translation).isEqualTo(LayoutPoint.of(-13.548086052416210, 0.0));
        }
    }

    @Test
    public void reproducedCasePipelineFixturePublishesRepairedFrames() throws Exception {
        final GraphProjection projection = pipelineProjection();
        final LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults());
        try {
            await(worker.submit(pipelineRequest(projection, pipelineAllPins())));
            for (int step = 0; step < 1500; step++) {
                final LayoutFrame pinnedFrame = await(worker.step());
                BoundaryInvariantAssertions.assertBijection(pinnedFrame.boundaryDiagnostics());
            }
            final LayoutFrame crossing = await(worker.submit(pipelineRequest(projection,
                pipelineRealPins())));

            assertThat(crossing.failed()).isFalse();
            assertThat(crossing.boundaryDiagnostics().hullViolationsDetected()).isGreaterThanOrEqualTo(1);
            assertThat(crossing.boundaryDiagnostics().rounds()).isEqualTo(1);
            assertThat(crossing.boundaryDiagnostics().boundaryVerified()).isTrue();
            assertThat(crossing.boundaryDiagnostics().appliedDisplacements().keySet())
                .contains(CanonicalLayoutKeys.nodeField(pipelineKey(PIPELINE_REGULARITY)))
                .doesNotContain(CanonicalLayoutKeys.nodeField(pipelineKey(PIPELINE_CHOICE)))
                .doesNotContain(CanonicalLayoutKeys.nodeField(pipelineKey(PIPELINE_PINNED_THEOREM)));
            BoundaryInvariantAssertions.assertVerifiedFrame(projection, crossing,
                crossing.boundaryDiagnostics(), PIPELINE_METRICS, pipelineRealPins());

            LayoutFrame frame = crossing;
            for (int step = 1; step <= 1000; step++) {
                frame = await(worker.step());
                assertThat(frame.failed()).isFalse();
                assertThat(frame.boundaryDiagnostics().rounds()).isLessThanOrEqualTo(2);
                assertThat(frame.boundaryDiagnostics().hullResidualViolations()).isZero();
                BoundaryInvariantAssertions.assertBijection(frame.boundaryDiagnostics());
                if (frame.boundaryDiagnostics().boundaryVerified()) {
                    BoundaryInvariantAssertions.assertVerifiedFrame(projection, frame,
                        frame.boundaryDiagnostics(), PIPELINE_METRICS, pipelineRealPins());
                }
                if (frame.idle().idle()) {
                    break;
                }
            }
            assertThat(frame.boundaryDiagnostics().boundaryVerified()).isTrue();
            BoundaryInvariantAssertions.assertVerifiedFrame(projection, frame,
                frame.boundaryDiagnostics(), PIPELINE_METRICS, pipelineRealPins());
        }
        finally {
            worker.close();
        }
    }

    private static GraphProjection pipelineProjection() {
        return GraphProjection.projected(1L,
            Arrays.asList(pipelineNode(PIPELINE_REGULARITY, "Fundation / Regularity"),
                pipelineNode(PIPELINE_REPLACEMENT, "Replacement Scheme"),
                pipelineNode(PIPELINE_CHOICE, "Axiom of Choice"),
                pipelineNode(PIPELINE_PINNED_THEOREM, "Theorem"),
                pipelineNode(PIPELINE_FREE_THEOREM, "Theorem")),
            Arrays.asList(
                pipelineEnclosure(PIPELINE_ROOT, "Axiomatic Set Theory", Optional.<EnclosureHullKey>empty(),
                    Collections.<ProjectedNodeKey>emptyList(),
                    Collections.singletonList(pipelineHull(PIPELINE_ZFC)), true, BoundaryTier.SUPPRESSED),
                pipelineEnclosure(PIPELINE_ZFC, "ZFC", Optional.of(pipelineHull(PIPELINE_ROOT)),
                    Collections.<ProjectedNodeKey>emptyList(),
                    Arrays.asList(pipelineHull(PIPELINE_AXIOMS), pipelineHull(PIPELINE_DEFINITIONS)), false,
                    BoundaryTier.EMPHATIC),
                pipelineEnclosure(PIPELINE_AXIOMS, "Axioms", Optional.of(pipelineHull(PIPELINE_ZFC)),
                    Arrays.asList(pipelineKey(PIPELINE_REGULARITY), pipelineKey(PIPELINE_REPLACEMENT),
                        pipelineKey(PIPELINE_CHOICE)),
                    Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE),
                pipelineEnclosure(PIPELINE_DEFINITIONS, "Basic Definitions and Theorems",
                    Optional.of(pipelineHull(PIPELINE_ZFC)),
                    Arrays.asList(pipelineKey(PIPELINE_PINNED_THEOREM), pipelineKey(PIPELINE_FREE_THEOREM)),
                    Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE)),
            Arrays.asList(pipelineEdge(PIPELINE_REGULARITY, PIPELINE_FREE_THEOREM, 0),
                pipelineEdge(PIPELINE_REGULARITY, PIPELINE_PINNED_THEOREM, 1)),
            Collections.<org.freeplane.plugin.graph.projection.RelationshipResolution>emptyList(),
            Collections.<PinProjection>emptyList());
    }

    private static LayoutRequest pipelineRequest(GraphProjection projection, List<PinProjection> pins) {
        return LayoutRequest.of(PIPELINE_WORKSPACE, projection,
            ProjectionDiff.between(projection, projection), pins);
    }

    private static List<PinProjection> pipelineAllPins() {
        return Arrays.asList(
            pipelinePin(PIPELINE_REGULARITY, -173.26206169386748, 1.8301628933959680),
            pipelinePin(PIPELINE_REPLACEMENT, 248.60834750232004, -59.339657536064465),
            pipelinePin(PIPELINE_CHOICE, -24.832420395427746, -34.920469854404410),
            pipelinePin(PIPELINE_PINNED_THEOREM, -209.31397564145126, 9.820904009249132),
            pipelinePin(PIPELINE_FREE_THEOREM, -241.79904871734790, 14.381230674273278));
    }

    private static List<PinProjection> pipelineRealPins() {
        return Arrays.asList(
            pipelinePin(PIPELINE_CHOICE, -24.832420395427746, -34.920469854404410),
            pipelinePin(PIPELINE_PINNED_THEOREM, -209.31397564145126, 9.820904009249132));
    }

    private static PinProjection pipelinePin(String id, double x, double y) {
        final ProjectedNodeKey key = pipelineKey(id);
        return PinProjection.active(PinRecord.of(key.source().persistedReference().get(), x, y,
            Collections.<org.freeplane.plugin.graph.workspace.model.UnknownXml>emptyList()), key);
    }

    private static ProjectedNode pipelineNode(String id, String label) {
        return ProjectedNode.of(pipelineKey(id), SafeNodeLabel.of(label, label), "Pipeline", false);
    }

    private static ProjectedEnclosure pipelineEnclosure(String id, String label,
            Optional<EnclosureHullKey> parent, List<ProjectedNodeKey> nodes, List<EnclosureHullKey> children,
            boolean mapRoot, BoundaryTier tier) {
        final EnclosureHullKey hull = pipelineHull(id);
        return ProjectedEnclosure.of(hull, hull.endpointKeys(),
            Collections.singletonList(SafeNodeLabel.of(label, label)), "Pipeline", parent, nodes, children,
            mapRoot, tier);
    }

    private static ProjectedEdge pipelineEdge(String sourceId, String targetId, int occurrence) {
        final ProjectedNodeKey source = pipelineKey(sourceId);
        final ProjectedNodeKey target = pipelineKey(targetId);
        final ProjectedEndpointKey first = ProjectedEndpointKey.ofNode(source);
        final ProjectedEndpointKey second = ProjectedEndpointKey.ofNode(target);
        final ConnectorDescriptor descriptor = ConnectorDescriptor.of(source.source(),
            target.source().persistedReference().get(), false, true, "", "", "");
        final EdgeContributor contributor = EdgeContributor.nativeConnector(
            ConnectorSnapshot.of(occurrence, descriptor), first, second);
        return ProjectedEdge.of(ProjectedEdgeKey.of(first, second), Collections.singletonList(contributor));
    }

    private static ProjectedNodeKey pipelineKey(String id) {
        return ProjectedNodeKey.of(SourceNodeKey.persisted(
            NodeReference.of(PIPELINE_MAP, PersistedNodeId.of(id))));
    }

    private static EnclosureHullKey pipelineHull(String id) {
        return EnclosureHullKey.of(Collections.singletonList(
            EnclosureKey.of(SourceNodeKey.persisted(NodeReference.of(PIPELINE_MAP, PersistedNodeId.of(id))))));
    }
```

Add these imports to `BoundarySeparationShould` if they are missing: `org.freeplane.plugin.graph.projection.EdgeContributor`, `org.freeplane.plugin.graph.projection.ProjectedEdgeKey`, `org.freeplane.plugin.graph.projection.ProjectedEndpointKey`, `org.freeplane.plugin.graph.projection.input.ConnectorDescriptor`, `org.freeplane.plugin.graph.projection.input.ConnectorSnapshot`, `org.freeplane.plugin.graph.workspace.model.WorkspaceId`.

**Red-before/green-after evidence.** The first test is the recorded red run: sealing the five nodes at the recorded settled positions and then unpinning the two free nodes reproduces the design's sibling crossing (`siblingOverlap == true`, `mst = (-13.548086052416210, 0.0)`). On the pre-fix tree `MapTierCorrection` corrected cross-map map roots only, so the worker republished exactly that crossing; the second test therefore failed before this plan's implementation and passes after it. Record both test outputs in the task report.

- [ ] **Step 2: Run the tests and confirm they pass**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationShould`
Expected: PASS. If the raw settle does not cross, do not inject positions: compare the fixture's node/enclosure order and labels against the frozen fixture in `BoundarySeparationCorrectionShould` and the design's Appendix B, because the engine's seeds and the retained particle positions are order-sensitive.

- [ ] **Step 3: Commit**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationShould.java
git commit -m "test(graph-layout): drive the reproduced case through the real worker"
```

## Task 18: Settle-sequence measurement and section-7 diagnostics gates

**Implementer tier:** Advanced

**Lane:** pipeline

**Depends on:** 17

**Ownership:** freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationShould.java

**Validation:** gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationShould

**Files:**
- Modify: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationShould.java` (append the settle-sequence test before the final closing brace)

**Interfaces:**
- Consumes: the Task 17 reproduced-case fixture helpers (`pipelineProjection`, `pipelineRequest`, `pipelineAllPins`, `pipelineRealPins`), `PerceptualIdlePolicy.spikeDefaults()` (`SPIKE_CONSECUTIVE = 8`, `SPIKE_RMS = 0.05`, `SPIKE_MAX = 0.10`), the pinned slack `0.0505` / `0.10` pinned by `BoundarySeparationShould.pinnedFixtureSettlesWithPinPositionUnchanged`.
- Produces: the §7.2/§7.3 measurement test asserting idle, stable deltas, zero terminal residual, no `ROUND_LIMIT`, and recording `worstMapDisplacement`.

- [ ] **Step 1: Write the settle-sequence test**

Append this member to `BoundarySeparationShould`, immediately before its final closing brace.

```java
    @Test
    public void reproducedCaseSettleSequenceReachesIdleWithStableCorrectionDeltas() throws Exception {
        final GraphProjection projection = pipelineProjection();
        final LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults());
        try {
            await(worker.submit(pipelineRequest(projection, pipelineAllPins())));
            for (int step = 0; step < 1500; step++) {
                await(worker.step());
            }
            await(worker.submit(pipelineRequest(projection, pipelineRealPins())));
            LayoutFrame idleFrame = null;
            double worstWorstMapDisplacement = 0.0;
            int worstRounds = 0;
            int observedDetections = 0;
            for (int step = 1; step <= 1000; step++) {
                final LayoutFrame frame = await(worker.step());
                assertThat(frame.failed()).isFalse();
                assertThat(frame.boundaryDiagnostics().boundaryVerified()
                    || frame.boundaryDiagnostics().boundaryCovered()).isTrue();
                assertThat(frame.boundaryDiagnostics().rounds()).isLessThanOrEqualTo(2);
                worstRounds = Math.max(worstRounds, frame.boundaryDiagnostics().rounds());
                observedDetections = Math.max(observedDetections,
                    frame.boundaryDiagnostics().hullViolationsDetected());
                for (final BoundaryConflict conflict : frame.boundaryDiagnostics().conflicts()) {
                    assertThat(conflict.reason()).isNotEqualTo(BoundaryConflict.Reason.ROUND_LIMIT);
                }
                worstWorstMapDisplacement = Math.max(worstWorstMapDisplacement,
                    frame.boundaryDiagnostics().worstMapDisplacement());
                BoundaryInvariantAssertions.assertBijection(frame.boundaryDiagnostics());
                if (frame.idle().idle()) {
                    idleFrame = frame;
                    break;
                }
            }
            assertThat(idleFrame).as("the settle sequence must reach idle within the step budget").isNotNull();
            assertThat(idleFrame.boundaryDiagnostics().boundaryVerified()).isTrue();
            assertThat(idleFrame.boundaryDiagnostics().hullResidualViolations()).isZero();
            assertThat(idleFrame.boundaryDiagnostics().residualHullPairs()).isEmpty();
            assertThat(idleFrame.idle().rms()).isLessThanOrEqualTo(0.0505);
            assertThat(idleFrame.idle().max()).isLessThanOrEqualTo(0.10);
            assertThat(idleFrame.boundaryDiagnostics().deltaRms()).isLessThanOrEqualTo(0.05);
            assertThat(idleFrame.boundaryDiagnostics().deltaMax()).isLessThanOrEqualTo(0.10);
            assertThat(worstRounds).isLessThanOrEqualTo(2);
            assertThat(observedDetections).isGreaterThanOrEqualTo(1);
            assertThat(worstWorstMapDisplacement).isGreaterThan(0.0);
        }
        finally {
            worker.close();
        }
    }
```

- [ ] **Step 2: Run the test**

Run: `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationShould`
Expected: PASS. The recorded `worstWorstMapDisplacement` is the §7.2 item 5 compactness number for the Task 19 report. If the sequence does not reach idle within the budget, do not relax the budget: report `DONE_WITH_CONCERNS` with the frame trace, because §7.3 then requires the measured escalation decision recorded in Task 19.

- [ ] **Step 3: Commit**

```bash
git add freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/BoundarySeparationShould.java
git commit -m "test(graph-layout): measure reproduced-case settle stability"
```

## Task 19: Final acceptance evidence and full-suite verification

**Implementer tier:** Standard

**Lane:** pipeline

**Depends on:** 16, 18

**Ownership:** docs/superpowers/plans/2026-09-12-graph-boundary-separation-evidence.md

**Validation:** gradle :freeplane_plugin_graph:test

**Files:**
- Create: `docs/superpowers/plans/2026-09-12-graph-boundary-separation-evidence.md`

**Interfaces:**
- Consumes: the test results and recorded measurements from Tasks 12, 16, 17 and 18; the strict ledger at `freeplane_plugin_graph/build/graph-performance/performance-ledger.csv`.
- Produces: the consolidated acceptance evidence document with the section-7 decision.

- [ ] **Step 1: Run the full module suite**

Run: `gradle :freeplane_plugin_graph:test`
Expected: PASS, including `BoundarySeparationCorrectionShould`, `BoundaryConflictShould`, `BoundarySeparationDiagnosticsShould`, `CanonicalLayoutKeysShould`, `BoundaryInvariantAssertionsShould`, `LayoutWorkerShould`, `LayoutSettleLoopShould`, `GraphUpdateCoordinatorShould`, `GraphWorkspaceCommandAcceptanceShould`, `GraphStreamBoundaryShould`, `BoundarySeparationShould`, `PerformanceTripwiresShould`.

- [ ] **Step 2: Write the evidence document**

Create `docs/superpowers/plans/2026-09-12-graph-boundary-separation-evidence.md`. Fill every angle-bracket field with the measured number from the named task; no field may remain unfilled:

```markdown
# Graph Workspace Boundary Separation Evidence

## Real-case regression
- Frozen fixture: full-precision positions and captured MST from the 2026-09-12 probe capture.
- Before: siblingOverlap=true, mst=(-13.54808605241621, 0.0).
- After: rounds=1, hullResidualViolations=0, both pins exact, mst=(0.0, 0.0), ancestor containment holds.
- Test: `:freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationCorrectionShould`.

## Pipeline adversarial fixture (red before / green after)
- Raw engine settle after the seeding request: siblingOverlap=true, mst=(-13.54808605241621, 0.0), the unpinned Regularity node defines the Axioms facing edge.
- First published frame after the final request: hullViolationsDetected=1, rounds=1, boundaryVerified=true; later frames rounds<=2, verified||covered, I6 bijection.
- Idle reached with rms/max and delta within the pinned slack; worstMapDisplacement recorded.
- Test: `:freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.BoundarySeparationShould`.

## Section 7 measurements
1. rounds <= 2 on every corpus frame; reproduced case rounds == 1.
2. Terminal cleanliness: idle frame hullResidualViolations == 0; no ANCESTOR_ESCAPE residual; no zero-MST overlap counted.
3. Stability: idle rms <= 0.0505, max <= 0.10; deltaRms <= 0.05, deltaMax <= 0.10; no ROUND_LIMIT conflict; no BoundarySeparationException.
4. Cross-map compactness: worstMapDisplacement = <recorded value>.
5. Strict performance gate: `reference-2000-5000` full-worker p50/p95/p99/max = <ledger values>; correction overhead split = <plan/apply values>.
- Escalation decision: <"no force-side clearance change" | "blocked with the failing measurement">.

## Manual acceptance checklist (operator)
- [ ] Full build, then `BIN/freeplane.sh`.
- [ ] Open the workspace containing `math.fpg`, open Graph Workspace, select the ZFC map.
- [ ] Inspect the `ZFC -> Basic Definitions and Theorems` region: the Axioms and Basic Definitions hulls must not cross.
- [ ] Confirm the layout reaches idle and both pins are at their stored positions.
```

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/plans/2026-09-12-graph-boundary-separation-evidence.md
git commit -m "docs(graph-layout): record boundary separation acceptance evidence"
```
