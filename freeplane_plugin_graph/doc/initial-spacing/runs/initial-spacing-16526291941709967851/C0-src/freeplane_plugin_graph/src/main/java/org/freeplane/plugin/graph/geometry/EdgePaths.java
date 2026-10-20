package org.freeplane.plugin.graph.geometry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;

/**
 * Immutable router result (spec 2.4): the per-edge published paths in canonical
 * {@code CanonicalKeys.edge} order, the route diagnostics and the route timings, published
 * through one {@code CanvasState} factory parameter.
 */
public final class EdgePaths {
    private final Map<ProjectedEdgeKey, EdgePath> byEdge;
    private final EdgeRouteDiagnostics diagnostics;
    private final EdgeRouteTimings timings;

    private EdgePaths(final Map<ProjectedEdgeKey, EdgePath> byEdge, final EdgeRouteDiagnostics diagnostics,
            final EdgeRouteTimings timings) {
        this.byEdge = byEdge;
        this.diagnostics = diagnostics;
        this.timings = timings;
    }

    public static EdgePaths empty() {
        return of(Collections.<ProjectedEdgeKey, EdgePath>emptyMap(), EdgeRouteDiagnostics.empty(),
            new EdgeRouteTimings(0L, 0L, 0L));
    }

    public static EdgePaths of(final Map<ProjectedEdgeKey, EdgePath> byEdge,
            final EdgeRouteDiagnostics diagnostics, final EdgeRouteTimings timings) {
        Objects.requireNonNull(byEdge, "byEdge");
        final Map<ProjectedEdgeKey, EdgePath> copy = new LinkedHashMap<ProjectedEdgeKey, EdgePath>();
        for (final Map.Entry<ProjectedEdgeKey, EdgePath> entry : byEdge.entrySet()) {
            copy.put(Objects.requireNonNull(entry.getKey(), "byEdge key"),
                Objects.requireNonNull(entry.getValue(), "byEdge value"));
        }
        return new EdgePaths(Collections.unmodifiableMap(copy),
            Objects.requireNonNull(diagnostics, "diagnostics"),
            Objects.requireNonNull(timings, "timings"));
    }

    public Map<ProjectedEdgeKey, EdgePath> byEdge() {
        return byEdge;
    }

    public Optional<EdgePath> path(final ProjectedEdgeKey key) {
        return Optional.ofNullable(byEdge.get(Objects.requireNonNull(key, "key")));
    }

    public EdgeRouteDiagnostics diagnostics() {
        return diagnostics;
    }

    public EdgeRouteTimings timings() {
        return timings;
    }
}
