package org.freeplane.plugin.graph.canvas;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;

public final class GraphPaintState {
    private final Optional<ProjectedEndpointKey> selection;
    private final Optional<ProjectedEndpointKey> hover;
    private final Set<ProjectedEndpointKey> searchMatches;
    private final Optional<ConnectionPreview> connectionPreview;
    private final boolean dimUnrelated;

    private GraphPaintState(final Optional<ProjectedEndpointKey> selection,
            final Optional<ProjectedEndpointKey> hover, final Set<ProjectedEndpointKey> searchMatches,
            final Optional<ConnectionPreview> connectionPreview, final boolean dimUnrelated) {
        this.selection = Objects.requireNonNull(selection, "selection");
        this.hover = Objects.requireNonNull(hover, "hover");
        this.searchMatches = copyMatches(searchMatches);
        this.connectionPreview = Objects.requireNonNull(connectionPreview, "connectionPreview");
        this.dimUnrelated = dimUnrelated;
    }

    public static GraphPaintState empty() {
        return new GraphPaintState(Optional.<ProjectedEndpointKey>empty(),
            Optional.<ProjectedEndpointKey>empty(), Collections.<ProjectedEndpointKey>emptySet(),
            Optional.<ConnectionPreview>empty(), false);
    }

    public GraphPaintState withSelection(final ProjectedEndpointKey selected) {
        return new GraphPaintState(Optional.of(Objects.requireNonNull(selected, "selected")), hover,
            searchMatches, connectionPreview, dimUnrelated);
    }

    public GraphPaintState withHover(final ProjectedEndpointKey hovered) {
        return new GraphPaintState(selection, Optional.of(Objects.requireNonNull(hovered, "hovered")),
            searchMatches, connectionPreview, dimUnrelated);
    }

    public GraphPaintState withSearchMatches(final Set<ProjectedEndpointKey> matches) {
        return new GraphPaintState(selection, hover, matches, connectionPreview, dimUnrelated);
    }

    GraphPaintState withConnectionPreview(final ConnectionPreview preview) {
        return new GraphPaintState(selection, hover, searchMatches,
            Optional.of(Objects.requireNonNull(preview, "preview")), dimUnrelated);
    }

    GraphPaintState withoutConnectionPreview() {
        return new GraphPaintState(selection, hover, searchMatches,
            Optional.<ConnectionPreview>empty(), dimUnrelated);
    }

    GraphPaintState withDimUnrelated(final boolean dim) {
        return new GraphPaintState(selection, hover, searchMatches, connectionPreview, dim);
    }

    GraphPaintState withoutSelection() {
        return new GraphPaintState(Optional.<ProjectedEndpointKey>empty(), hover, searchMatches,
            connectionPreview, dimUnrelated);
    }

    GraphPaintState withoutHover() {
        return new GraphPaintState(selection, Optional.<ProjectedEndpointKey>empty(), searchMatches,
            connectionPreview, dimUnrelated);
    }

    public Optional<ProjectedEndpointKey> selection() {
        return selection;
    }

    public Optional<ProjectedEndpointKey> hover() {
        return hover;
    }

    public Set<ProjectedEndpointKey> searchMatches() {
        return searchMatches;
    }

    Optional<ConnectionPreview> connectionPreview() {
        return connectionPreview;
    }

    boolean dimUnrelated() {
        return dimUnrelated;
    }

    private static Set<ProjectedEndpointKey> copyMatches(final Set<ProjectedEndpointKey> values) {
        Objects.requireNonNull(values, "matches");
        final LinkedHashSet<ProjectedEndpointKey> copy = new LinkedHashSet<ProjectedEndpointKey>();
        for (final ProjectedEndpointKey value : values) {
            copy.add(Objects.requireNonNull(value, "matches entry"));
        }
        return Collections.unmodifiableSet(copy);
    }

    static final class ConnectionPreview {
        private final ProjectedEndpointKey source;
        private final LayoutPoint from;
        private final LayoutPoint to;

        private ConnectionPreview(final ProjectedEndpointKey source, final LayoutPoint from,
                final LayoutPoint to) {
            this.source = Objects.requireNonNull(source, "source");
            this.from = requireFinitePoint(from, "from");
            this.to = requireFinitePoint(to, "to");
        }

        static ConnectionPreview of(final ProjectedEndpointKey source, final LayoutPoint from,
                final LayoutPoint to) {
            return new ConnectionPreview(source, from, to);
        }

        ProjectedEndpointKey source() {
            return source;
        }

        LayoutPoint from() {
            return from;
        }

        LayoutPoint to() {
            return to;
        }

        private static LayoutPoint requireFinitePoint(final LayoutPoint point, final String name) {
            final LayoutPoint value = Objects.requireNonNull(point, name);
            if (!Double.isFinite(value.x()) || !Double.isFinite(value.y())) {
                throw new IllegalArgumentException(name + " must be finite");
            }
            return value;
        }
    }
}
