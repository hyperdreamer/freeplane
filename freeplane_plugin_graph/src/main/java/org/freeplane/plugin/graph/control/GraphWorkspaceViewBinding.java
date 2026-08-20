package org.freeplane.plugin.graph.control;

import java.util.List;
import java.util.Objects;

import org.freeplane.plugin.graph.projection.input.MapAvailability;
import org.freeplane.plugin.graph.workspace.ListenerRegistration;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.Viewport;

public interface GraphWorkspaceViewBinding {
    CanvasState currentCanvasState();
    Viewport currentViewport();
    List<MapRegistration> currentMapRows();
    default boolean isReadOnly() {
        return false;
    }
    ListenerRegistration addCanvasStateListener(CanvasStateListener listener);

    final class MapRegistration {
        private final MapReferenceId mapReferenceId;
        private final String displayName;
        private final MapAvailability availability;

        private MapRegistration(final MapReferenceId mapReferenceId, final String displayName,
                final MapAvailability availability) {
            this.mapReferenceId = Objects.requireNonNull(mapReferenceId, "mapReferenceId");
            this.displayName = requireDisplayName(displayName);
            this.availability = Objects.requireNonNull(availability, "availability");
        }

        public static MapRegistration of(final MapReferenceId mapReferenceId, final String displayName,
                final MapAvailability availability) {
            return new MapRegistration(mapReferenceId, displayName, availability);
        }

        public MapReferenceId mapReferenceId() {
            return mapReferenceId;
        }

        public String displayName() {
            return displayName;
        }

        public MapAvailability availability() {
            return availability;
        }

        private static String requireDisplayName(final String value) {
            Objects.requireNonNull(value, "displayName");
            if (value.trim().isEmpty()) {
                throw new IllegalArgumentException("displayName must not be empty");
            }
            return value;
        }
    }
}
