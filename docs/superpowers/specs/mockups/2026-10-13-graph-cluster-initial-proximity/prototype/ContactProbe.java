package org.freeplane.plugin.graph.layout;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.freeplane.plugin.graph.geometry.AwtGeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GeometryTextMetrics;
import org.freeplane.plugin.graph.geometry.GraphGeometry;
import org.freeplane.plugin.graph.geometry.GraphGeometryEngine;
import org.freeplane.plugin.graph.geometry.HullGeometry;
import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.geometry.LayoutPositions;
import org.freeplane.plugin.graph.projection.BoundaryTier;
import org.freeplane.plugin.graph.projection.EdgeContributor;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.EnclosureKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEdge;
import org.freeplane.plugin.graph.projection.ProjectedEdgeKey;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectedEndpointKey;
import org.freeplane.plugin.graph.projection.ProjectedNode;
import org.freeplane.plugin.graph.projection.ProjectedNodeKey;
import org.freeplane.plugin.graph.projection.ProjectionDiff;
import org.freeplane.plugin.graph.projection.RelationshipResolution;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.projection.input.SourceNodeKey;
import org.freeplane.plugin.graph.workspace.model.GraphRelationshipRecord;
import org.freeplane.plugin.graph.workspace.model.MapReferenceId;
import org.freeplane.plugin.graph.workspace.model.NodeReference;
import org.freeplane.plugin.graph.workspace.model.PersistedNodeId;
import org.freeplane.plugin.graph.workspace.model.PinRecord;
import org.freeplane.plugin.graph.workspace.model.RelationshipDirection;
import org.freeplane.plugin.graph.workspace.model.RelationshipId;
import org.freeplane.plugin.graph.workspace.model.UnknownXml;
import org.freeplane.plugin.graph.workspace.model.WorkspaceId;

/** Scratch probe: contact-graph skeleton, anchor-centred footprints, per-side containment. */
public final class ContactProbe {
    private static final WorkspaceId WORKSPACE =
        WorkspaceId.of("00000000-0000-0000-0000-0000000000aa");
    private static final MapReferenceId MAP_ONE =
        MapReferenceId.of("00000000-0000-0000-0000-000000000001");
    private static final MapReferenceId MAP_TWO =
        MapReferenceId.of("00000000-0000-0000-0000-000000000002");
    private static final MapReferenceId MAP_THREE =
        MapReferenceId.of("00000000-0000-0000-0000-000000000003");
    private static final MapReferenceId MAP_FOUR =
        MapReferenceId.of("00000000-0000-0000-0000-000000000004");

    public static void main(String[] args) throws Exception {
        System.out.println("proto.packingMultiplier=" + System.getProperty("proto.packingMultiplier", "<default>")
            + " proto.boundary=" + System.getProperty("proto.boundary", "box"));
        run("A reference fixture (root > zfc > axioms + definitions)",
            ReferenceRepulsionFixture.referenceProjection(1L), Collections.<PinProjection>emptyList());
        run("B two maps", twoMapProjection(1L), Collections.<PinProjection>emptyList());
        run("C two maps + relationships + pin(60,20)", relationshipProjection(1L),
            Collections.singletonList(pin(ProjectedNodeKey.of(source(MAP_TWO, "b-n0")), 60.0, 20.0)));
        run("D two maps + relationships", relationshipProjection(1L), Collections.<PinProjection>emptyList());
        run("E dual enclosure (root 2 nodes + child 3 nodes)", dualProjection(1L),
            Collections.<PinProjection>emptyList());
        run("F four maps heterogeneous", fourMapProjection(1L), Collections.<PinProjection>emptyList());
        run("G one map four sibling subgroups", fourSiblingProjection(1L),
            Collections.<PinProjection>emptyList());
        run("H root with direct nodes + two nested sibling sub-clusters", asymmetricSiblingProjection(1L),
            Collections.<PinProjection>emptyList());
        runReorder();
        runTiming();
    }

    private static void runTiming() throws Exception {
        final GraphProjection projection = wideProjection(1L);
        final LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults());
        try {
            final long start = System.nanoTime();
            LayoutFrame frame = worker.submit(LayoutRequest.of(WORKSPACE, projection,
                ProjectionDiff.between(projection, projection), Collections.<PinProjection>emptyList()))
                .toCompletableFuture().get(30L, TimeUnit.SECONDS);
            final long submitNanos = System.nanoTime() - start;
            final long stepStart = System.nanoTime();
            for (int step = 0; step < 10; step++) {
                frame = worker.step().toCompletableFuture().get(30L, TimeUnit.SECONDS);
            }
            final long stepNanos = System.nanoTime() - stepStart;
            System.out.printf("timing 200-child fixture: submit(apply+packing) %.3f s, 10 steps %.3f s, anchors %d%n",
                submitNanos / 1e9, stepNanos / 1e9, frame.positions().anchors().size());
        }
        finally {
            worker.close();
        }
    }

    private static GraphProjection wideProjection(long generation) {
        final List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        final EnclosureHullKey rootHull = hull(MAP_ONE, "wide-root");
        final List<EnclosureHullKey> children = new ArrayList<EnclosureHullKey>();
        for (int index = 0; index < 200; index++) {
            final EnclosureHullKey childHull = hull(MAP_ONE, "wide-" + index);
            children.add(childHull);
            enclosures.add(ProjectedEnclosure.of(childHull,
                Collections.singletonList(EnclosureKey.of(source(MAP_ONE, "wide-" + index))),
                Collections.singletonList(SafeNodeLabel.of("wide-" + index, "wide-" + index)), "Map",
                Optional.of(rootHull), Collections.<ProjectedNodeKey>emptyList(),
                Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE));
        }
        enclosures.add(ProjectedEnclosure.of(rootHull,
            Collections.singletonList(EnclosureKey.of(source(MAP_ONE, "wide-root"))),
            Collections.singletonList(SafeNodeLabel.of("Root", "Root")), "Map",
            Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(), children, true,
            BoundaryTier.EMPHATIC));
        return GraphProjection.projected(generation, Collections.<ProjectedNode>emptyList(), enclosures,
            Collections.<ProjectedEdge>emptyList(), Collections.<RelationshipResolution>emptyList(),
            Collections.<PinProjection>emptyList());
    }

    private static void run(String title, GraphProjection projection, List<PinProjection> pins)
            throws Exception {
        System.out.println();
        System.out.println("### " + title);
        final Object sizes = boundarySizes(projection);
        final LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults());
        try {
            final CompletionStage<LayoutFrame> submitted = worker.submit(LayoutRequest.of(WORKSPACE,
                projection, ProjectionDiff.between(projection, projection), pins));
            LayoutFrame frame = submitted.toCompletableFuture().get(5L, TimeUnit.SECONDS);
            int idleStep = -1;
            for (int step = 1; step <= 2500; step++) {
                frame = worker.step().toCompletableFuture().get(5L, TimeUnit.SECONDS);
                if (frame.idle().idle()) {
                    idleStep = step;
                    break;
                }
            }
            for (int step = 0; step < 200; step++) {
                frame = worker.step().toCompletableFuture().get(5L, TimeUnit.SECONDS);
            }
            System.out.println("idle step = " + idleStep + " (then +200)");
            final LayoutPositions positions = frame.positions();
            final GeometryTextMetrics metrics = new AwtGeometryTextMetrics(new Font("Dialog", Font.PLAIN, 12),
                new FontRenderContext(null, true, true));
            final GraphGeometry geometry = new GraphGeometryEngine().computeHulls(projection, positions, metrics);

            System.out.println("enclosure | footprint w x h (anchor offset) | settled anchor | per-side containment | drift");
            for (final ProjectedEnclosure enclosure : projection.enclosures()) {
                final Object footprint = invoke(sizes, "footprint", EnclosureHullKey.class, enclosure.hullKey());
                final double width = field(footprint, "width");
                final double height = field(footprint, "height");
                final double anchorX = field(footprint, "anchorX");
                final double anchorY = field(footprint, "anchorY");
                final LayoutPoint anchor = positions.anchors().get(enclosure.hullKey());
                final double[] bbox = bbox(geometry.hulls().get(enclosure.hullKey()));
                final double left = bbox[0] - anchor.x();
                final double right = bbox[2] - anchor.x();
                final double bottom = bbox[1] - anchor.y();
                final double top = bbox[3] - anchor.y();
                final double allowedLeft = -anchorX - width * 0.5;
                final double allowedRight = -anchorX + width * 0.5;
                final double allowedBottom = -anchorY - height * 0.5;
                final double allowedTop = -anchorY + height * 0.5;
                final boolean contained = left >= allowedLeft - 0.01 && right <= allowedRight + 0.01
                    && bottom >= allowedBottom - 0.01 && top <= allowedTop + 0.01;
                double drift = 0.0;
                if (!enclosure.directNodes().isEmpty()) {
                    final double ring = (Double) invoke(sizes, "contentRingRadius", EnclosureHullKey.class,
                        enclosure.hullKey());
                    for (final ProjectedNodeKey nodeKey : enclosure.directNodes()) {
                        final LayoutPoint node = positions.nodes().get(nodeKey);
                        drift = Math.max(drift, Math.hypot(node.x() - anchor.x(), node.y() - anchor.y()) - ring);
                    }
                }
                System.out.printf("%-28s %7.1f x %6.1f (%6.1f,%6.1f)  (%8.1f,%8.1f)  %s  x[%7.1f..%7.1f]/[%7.1f..%7.1f] y[%7.1f..%7.1f]/[%7.1f..%7.1f]  %+5.1f%n",
                    label(enclosure), width, height, anchorX, anchorY, anchor.x(), anchor.y(),
                    contained ? "OK  " : "FAIL", left, right, allowedLeft, allowedRight,
                    bottom, top, allowedBottom, allowedTop, drift);
            }

            final List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>(projection.enclosures());
            System.out.println("packed contacts -> settled anchor distance / packed rest / hull distance:");
            for (final ProjectedEnclosure parent : projection.enclosures()) {
                final List<?> placements = (List<?>) invoke(sizes, "placements", EnclosureHullKey.class,
                    parent.hullKey());
                for (final Object placement : placements) {
                    final Object item = objectField(placement, "item");
                    final Object anchorKey = objectField(item, "anchorKey");
                    if (anchorKey == null) {
                        continue;
                    }
                    final double childX = placementAnchorX(placement);
                    final double childY = placementAnchorY(placement);
                    final LayoutPoint childAnchor = positions.anchors().get(anchorKey);
                    for (final Object contact : (List<?>) objectField(placement, "contacts")) {
                        final Object contactKey = objectField(contact, "anchorKey");
                        if (contactKey == null) {
                            final LayoutPoint parentAnchor = positions.anchors().get(parent.hullKey());
                            final double settled = Math.hypot(childAnchor.x() - parentAnchor.x(),
                                childAnchor.y() - parentAnchor.y());
                            System.out.printf("   %-52s settled %7.1f  packed %7.1f  hull %8.1f%n",
                                label(enclosures, (EnclosureHullKey) anchorKey) + " <-> parent anchor",
                                settled, Math.hypot(childX, childY),
                                hullDistance(geometry.hulls().get(anchorKey),
                                    geometry.hulls().get(parent.hullKey())));
                            continue;
                        }
                        final Object other = placementOf(sizes, parent.hullKey(),
                            (EnclosureHullKey) contactKey);
                        if (other == null) {
                            continue;
                        }
                        final double otherX = placementAnchorX(other);
                        final double otherY = placementAnchorY(other);
                        final LayoutPoint otherAnchor = positions.anchors().get(contactKey);
                        final double settled = Math.hypot(childAnchor.x() - otherAnchor.x(),
                            childAnchor.y() - otherAnchor.y());
                        System.out.printf("   %-52s settled %7.1f  packed %7.1f  hull %8.1f  %s%n",
                            label(enclosures, (EnclosureHullKey) anchorKey) + " <-> "
                                + label(enclosures, (EnclosureHullKey) contactKey),
                            settled, Math.hypot(childX - otherX, childY - otherY),
                            hullDistance(geometry.hulls().get(anchorKey),
                                geometry.hulls().get(contactKey)),
                            contactType(placement, other, 8.0));
                    }
                }
            }
            final List<?> topPlacements = (List<?>) invokeNoArg(sizes, "topLevelPlacements");
            for (final Object placement : topPlacements) {
                final Object item = objectField(placement, "item");
                final Object anchorKey = objectField(item, "anchorKey");
                if (anchorKey == null) {
                    continue;
                }
                final double anchorPositionX = placementAnchorX(placement);
                final double anchorPositionY = placementAnchorY(placement);
                final LayoutPoint anchorPoint = positions.anchors().get(anchorKey);
                for (final Object contact : (List<?>) objectField(placement, "contacts")) {
                    final Object contactKey = objectField(contact, "anchorKey");
                    final Object other = topLevelPlacementOf(sizes, (EnclosureHullKey) contactKey);
                    if (other == null) {
                        continue;
                    }
                    final LayoutPoint otherPoint = positions.anchors().get(contactKey);
                    final double settled = Math.hypot(anchorPoint.x() - otherPoint.x(),
                        anchorPoint.y() - otherPoint.y());
                    System.out.printf("   %-52s settled %7.1f  packed %7.1f  hull %8.1f  %s%n",
                        "TOP " + label(enclosures, (EnclosureHullKey) anchorKey) + " <-> "
                            + label(enclosures, (EnclosureHullKey) contactKey),
                        settled,
                        Math.hypot(anchorPositionX - placementAnchorX(other),
                            anchorPositionY - placementAnchorY(other)),
                        hullDistance(geometry.hulls().get(anchorKey),
                            geometry.hulls().get(contactKey)),
                        contactType(placement, other, 8.0));
                }
            }
            int boxViolations = 0;
            final List<EnclosureHullKey> keys = new ArrayList<EnclosureHullKey>(positions.anchors().keySet());
            for (int first = 0; first < keys.size(); first++) {
                for (int second = first + 1; second < keys.size(); second++) {
                    final EnclosureHullKey firstKey = keys.get(first);
                    final EnclosureHullKey secondKey = keys.get(second);
                    if (isAncestor(projection, firstKey, secondKey)
                            || isAncestor(projection, secondKey, firstKey)) {
                        continue;
                    }
                    final Object firstFootprint = invoke(sizes, "footprint", EnclosureHullKey.class, firstKey);
                    final Object secondFootprint = invoke(sizes, "footprint", EnclosureHullKey.class, secondKey);
                    final LayoutPoint firstAnchor = positions.anchors().get(firstKey);
                    final LayoutPoint secondAnchor = positions.anchors().get(secondKey);
                    final double firstX = firstAnchor.x() - field(firstFootprint, "anchorX");
                    final double firstY = firstAnchor.y() - field(firstFootprint, "anchorY");
                    final double secondX = secondAnchor.x() - field(secondFootprint, "anchorX");
                    final double secondY = secondAnchor.y() - field(secondFootprint, "anchorY");
                    final double gapX = Math.abs(firstX - secondX)
                        - (field(firstFootprint, "width") + field(secondFootprint, "width")) * 0.5 - 8.0;
                    final double gapY = Math.abs(firstY - secondY)
                        - (field(firstFootprint, "height") + field(secondFootprint, "height")) * 0.5 - 8.0;
                    if (gapX < -1e-6 && gapY < -1e-6) {
                        boxViolations++;
                        System.out.printf("   BOX-FORCE NONZERO %s <-> %s gapX %.1f gapY %.1f%n",
                            label(enclosures, firstKey), label(enclosures, secondKey), gapX, gapY);
                    }
                }
            }
            System.out.println("settled box-force violations (non-ancestor pairs): " + boxViolations);
            final List<EnclosureHullKey> windowFirst = new ArrayList<EnclosureHullKey>();
            final List<EnclosureHullKey> windowSecond = new ArrayList<EnclosureHullKey>();
            final List<Double> windowPacked = new ArrayList<Double>();
            final List<String> windowNames = new ArrayList<String>();
            for (final ProjectedEnclosure parent : projection.enclosures()) {
                final List<?> parentPlacements = (List<?>) invoke(sizes, "placements", EnclosureHullKey.class,
                    parent.hullKey());
                for (final Object placement : parentPlacements) {
                    final Object item = objectField(placement, "item");
                    final Object anchorKey = objectField(item, "anchorKey");
                    if (anchorKey == null) {
                        continue;
                    }
                    final double childX = placementAnchorX(placement);
                    final double childY = placementAnchorY(placement);
                    for (final Object contact : (List<?>) objectField(placement, "contacts")) {
                        final Object contactKey = objectField(contact, "anchorKey");
                        if (contactKey == null) {
                            windowFirst.add((EnclosureHullKey) anchorKey);
                            windowSecond.add(parent.hullKey());
                            windowPacked.add(Double.valueOf(Math.hypot(childX, childY)));
                            windowNames.add(label(enclosures, (EnclosureHullKey) anchorKey) + " <-> parent");
                            continue;
                        }
                        final Object other = placementOf(sizes, parent.hullKey(),
                            (EnclosureHullKey) contactKey);
                        if (other == null) {
                            continue;
                        }
                        windowFirst.add((EnclosureHullKey) anchorKey);
                        windowSecond.add((EnclosureHullKey) contactKey);
                        windowPacked.add(Double.valueOf(Math.hypot(childX - placementAnchorX(other),
                            childY - placementAnchorY(other))));
                        windowNames.add(label(enclosures, (EnclosureHullKey) anchorKey) + " <-> "
                            + label(enclosures, (EnclosureHullKey) contactKey));
                    }
                }
            }
            final List<?> topPlacementsForWindow = (List<?>) invokeNoArg(sizes, "topLevelPlacements");
            for (final Object placement : topPlacementsForWindow) {
                final Object item = objectField(placement, "item");
                final Object anchorKey = objectField(item, "anchorKey");
                if (anchorKey == null) {
                    continue;
                }
                final double anchorX = placementAnchorX(placement);
                final double anchorY = placementAnchorY(placement);
                for (final Object contact : (List<?>) objectField(placement, "contacts")) {
                    final Object contactKey = objectField(contact, "anchorKey");
                    final Object other = topLevelPlacementOf(sizes, (EnclosureHullKey) contactKey);
                    if (other == null) {
                        continue;
                    }
                    windowFirst.add((EnclosureHullKey) anchorKey);
                    windowSecond.add((EnclosureHullKey) contactKey);
                    windowPacked.add(Double.valueOf(Math.hypot(anchorX - placementAnchorX(other),
                        anchorY - placementAnchorY(other))));
                    windowNames.add("TOP " + label(enclosures, (EnclosureHullKey) anchorKey) + " <-> "
                        + label(enclosures, (EnclosureHullKey) contactKey));
                }
            }
            double worstHold = 0.0;
            String worstName = "";
            double worstDocumented = 0.0;
            for (int step = 0; step < 1000; step++) {
                frame = worker.step().toCompletableFuture().get(5L, TimeUnit.SECONDS);
                final LayoutPositions sampled = frame.positions();
                for (int index = 0; index < windowFirst.size(); index++) {
                    final LayoutPoint first = sampled.anchors().get(windowFirst.get(index));
                    final LayoutPoint second = sampled.anchors().get(windowSecond.get(index));
                    if (first == null || second == null) {
                        continue;
                    }
                    final double hold = Math.abs(Math.hypot(first.x() - second.x(), first.y() - second.y())
                        - windowPacked.get(index).doubleValue());
                    if (hold > worstHold) {
                        worstHold = hold;
                        worstName = windowNames.get(index);
                    }
                }
            }
            System.out.printf("stability window (1000 steps after the documented endpoint): max hold deviation %.1f at %s%n",
                worstHold, worstName);
            int crossings = 0;
            double minimumSeparation = Double.POSITIVE_INFINITY;
            for (int first = 0; first < keys.size(); first++) {
                for (int second = first + 1; second < keys.size(); second++) {
                    if (isAncestor(projection, keys.get(first), keys.get(second))
                            || isAncestor(projection, keys.get(second), keys.get(first))) {
                        continue;
                    }
                    final double separation = hullDistance(geometry.hulls().get(keys.get(first)),
                        geometry.hulls().get(keys.get(second)));
                    minimumSeparation = Math.min(minimumSeparation, separation);
                    if (separation < 0.0) {
                        crossings++;
                    }
                }
            }
            System.out.printf("all-pairs non-ancestor crossings: %d, minimum separation %.2f%n", crossings,
                minimumSeparation);
        }
        finally {
            worker.close();
        }
    }

    private static boolean isAncestor(GraphProjection projection, EnclosureHullKey ancestor,
            EnclosureHullKey child) {
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            if (!enclosure.hullKey().equals(child)) {
                continue;
            }
            Optional<EnclosureHullKey> parent = enclosure.parentHull();
            while (parent.isPresent()) {
                if (parent.get().equals(ancestor)) {
                    return true;
                }
                parent = parentOf(projection, parent.get());
            }
            return false;
        }
        return false;
    }

    private static Optional<EnclosureHullKey> parentOf(GraphProjection projection, EnclosureHullKey key) {
        for (final ProjectedEnclosure enclosure : projection.enclosures()) {
            if (enclosure.hullKey().equals(key)) {
                return enclosure.parentHull();
            }
        }
        return Optional.empty();
    }

    private static double placementAnchorX(Object placement) throws Exception {
        return field(placement, "x") + field(objectField(placement, "item"), "anchorX");
    }

    private static double placementAnchorY(Object placement) throws Exception {
        return field(placement, "y") + field(objectField(placement, "item"), "anchorY");
    }

    private static Object placementOf(Object sizes, EnclosureHullKey parentKey, EnclosureHullKey childKey)
            throws Exception {
        final Method method = sizes.getClass().getDeclaredMethod("placementOf", EnclosureHullKey.class,
            EnclosureHullKey.class);
        method.setAccessible(true);
        return method.invoke(sizes, parentKey, childKey);
    }

    private static Object topLevelPlacementOf(Object sizes, EnclosureHullKey key) throws Exception {
        final Method method = sizes.getClass().getDeclaredMethod("topLevelPlacementOf", EnclosureHullKey.class);
        method.setAccessible(true);
        return method.invoke(sizes, key);
    }

    private static String label(List<ProjectedEnclosure> enclosures, EnclosureHullKey key) {
        for (final ProjectedEnclosure enclosure : enclosures) {
            if (enclosure.hullKey().equals(key)) {
                return label(enclosure);
            }
        }
        return key.toString();
    }

    private static String label(ProjectedEnclosure enclosure) {
        final StringBuilder result = new StringBuilder();
        for (SafeNodeLabel label : enclosure.labels()) {
            if (result.length() > 0) {
                result.append('/');
            }
            result.append(label.displayText());
        }
        return result.toString();
    }

    private static double[] bbox(HullGeometry hull) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (LayoutPoint point : hull.exactPolygon()) {
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            maxX = Math.max(maxX, point.x());
            maxY = Math.max(maxY, point.y());
        }
        return new double[] {minX, minY, maxX, maxY};
    }

    private static String contactType(Object first, Object second, double gap) throws Exception {
        final double dx = Math.abs(placementAnchorX(first) - placementAnchorX(second))
            - (field(objectField(first, "item"), "width") + field(objectField(second, "item"), "width")) * 0.5;
        final double dy = Math.abs(placementAnchorY(first) - placementAnchorY(second))
            - (field(objectField(first, "item"), "height") + field(objectField(second, "item"), "height")) * 0.5;
        return (dx <= gap + 0.75 && dy < -0.75) || (dy <= gap + 0.75 && dx < -0.75)
            ? "EDGE" : "CORNER";
    }

    private static double hullDistance(HullGeometry first, HullGeometry second) {
        final List<LayoutPoint> a = first.exactPolygon();
        final List<LayoutPoint> b = second.exactPolygon();
        final List<double[]> axes = new ArrayList<double[]>();
        addAxes(axes, a);
        addAxes(axes, b);
        double cx = 0.0;
        double cy = 0.0;
        for (LayoutPoint p : a) {
            cx += p.x();
            cy += p.y();
        }
        cx /= a.size();
        cy /= a.size();
        double dx = 0.0;
        double dy = 0.0;
        for (LayoutPoint p : b) {
            dx += p.x();
            dy += p.y();
        }
        dx = dx / b.size() - cx;
        dy = dy / b.size() - cy;
        final double length = Math.hypot(dx, dy);
        if (length > 0.0) {
            axes.add(new double[] {dx / length, dy / length});
        }
        double best = Double.NEGATIVE_INFINITY;
        for (final double[] axis : axes) {
            double minB = Double.POSITIVE_INFINITY;
            double maxA = Double.NEGATIVE_INFINITY;
            for (LayoutPoint p : b) {
                minB = Math.min(minB, axis[0] * p.x() + axis[1] * p.y());
            }
            for (LayoutPoint p : a) {
                maxA = Math.max(maxA, axis[0] * p.x() + axis[1] * p.y());
            }
            best = Math.max(best, minB - maxA);
        }
        return best;
    }

    private static void addAxes(List<double[]> axes, List<LayoutPoint> polygon) {
        for (int index = 0; index < polygon.size(); index++) {
            final LayoutPoint first = polygon.get(index);
            final LayoutPoint second = polygon.get((index + 1) % polygon.size());
            final double dx = second.x() - first.x();
            final double dy = second.y() - first.y();
            final double length = Math.hypot(dx, dy);
            if (length > 0.0) {
                axes.add(new double[] {-dy / length, dx / length});
            }
        }
    }

    private static Object boundarySizes(GraphProjection projection) throws Exception {
        final Class<?> type = Class.forName(
            "org.freeplane.plugin.graph.layout.graphstream.GraphStreamLayoutEngine$BoundarySizes");
        final Constructor<?> constructor = type.getDeclaredConstructor(GraphProjection.class);
        constructor.setAccessible(true);
        return constructor.newInstance(projection);
    }

    private static Object invoke(Object target, String name, Class<?> parameter, Object... arguments)
            throws Exception {
        final Method method = target.getClass().getDeclaredMethod(name, parameter);
        method.setAccessible(true);
        return method.invoke(target, arguments);
    }

    private static Object invokeNoArg(Object target, String name) throws Exception {
        final Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static double field(Object target, String name) throws Exception {
        final Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getDouble(target);
    }

    private static Object objectField(Object target, String name) throws Exception {
        final Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    // ---------------------------------------------------------------- fixtures

    private static void runReorder() throws Exception {
        final GraphProjection before = mapProjection(1L, 4);
        final GraphProjection after = mapProjection(2L, 5);
        final LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults());
        try {
            LayoutFrame frame = worker.submit(LayoutRequest.of(WORKSPACE, before,
                ProjectionDiff.between(before, before), Collections.<PinProjection>emptyList()))
                .toCompletableFuture().get(5L, TimeUnit.SECONDS);
            for (int step = 1; step <= 2000; step++) {
                frame = worker.step().toCompletableFuture().get(5L, TimeUnit.SECONDS);
                if (frame.idle().idle()) {
                    break;
                }
            }
            final LayoutPositions settled = frame.positions();
            final LayoutFrame applied = worker.submit(LayoutRequest.of(WORKSPACE, after,
                ProjectionDiff.between(before, after), Collections.<PinProjection>emptyList()))
                .toCompletableFuture().get(5L, TimeUnit.SECONDS);
            final LayoutFrame stepped = worker.step().toCompletableFuture().get(5L, TimeUnit.SECONDS);
            double applyMax = 0.0;
            double applySum = 0.0;
            double stepMax = 0.0;
            for (EnclosureHullKey key : settled.anchors().keySet()) {
                if (!stepped.positions().anchors().containsKey(key)) {
                    continue;
                }
                final LayoutPoint before1 = settled.anchors().get(key);
                final LayoutPoint apply2 = applied.positions().anchors().get(key);
                final LayoutPoint step3 = stepped.positions().anchors().get(key);
                final double first = Math.hypot(apply2.x() - before1.x(), apply2.y() - before1.y());
                final double second = Math.hypot(step3.x() - apply2.x(), step3.y() - apply2.y());
                applyMax = Math.max(applyMax, first);
                applySum += first;
                stepMax = Math.max(stepMax, second);
                System.out.printf("reorder retained %-40s apply %7.1f next-step %7.1f%n",
                    label(before.enclosures(), key), first, second);
            }
            System.out.printf("reorder totals: applyMax %.1f applySum %.1f stepMax %.1f%n", applyMax, applySum,
                stepMax);
        }
        finally {
            worker.close();
        }
    }

    private static GraphProjection mapProjection(long generation, int mapCount) {
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        final List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        final MapReferenceId[] maps = new MapReferenceId[] {MAP_ONE, MAP_TWO, MAP_THREE, MAP_FOUR};
        for (int m = 0; m < mapCount; m++) {
            final MapReferenceId map = m < maps.length ? maps[m] : MapReferenceId.of(
                String.format("00000000-0000-0000-0000-%012d", Long.valueOf(100 + m)));
            final String prefix = "m" + m;
            final int nodeCount = m == mapCount - 1 && mapCount > 4 ? 8 : 2 + m;
            final EnclosureHullKey rootHull = hull(map, prefix + "-root");
            final EnclosureHullKey subHull = hull(map, prefix + "-sub");
            final List<ProjectedNodeKey> subNodes = new ArrayList<ProjectedNodeKey>();
            for (int i = 0; i < nodeCount; i++) {
                final ProjectedNodeKey nodeKey = ProjectedNodeKey.of(source(map, prefix + "-n" + i));
                nodes.add(node(nodeKey, "Theorem: " + prefix + " statement " + i + " with a long label"));
                subNodes.add(nodeKey);
            }
            enclosures.add(ProjectedEnclosure.of(subHull,
                Collections.singletonList(EnclosureKey.of(source(map, prefix + "-sub"))),
                Collections.singletonList(SafeNodeLabel.of(prefix + " fundamentals", prefix + " fundamentals")),
                "Map", Optional.of(rootHull), subNodes, Collections.<EnclosureHullKey>emptyList(), false,
                BoundaryTier.SUBTLE));
            enclosures.add(ProjectedEnclosure.of(rootHull,
                Collections.singletonList(EnclosureKey.of(source(map, prefix + "-root"))),
                Collections.singletonList(SafeNodeLabel.of("Map " + prefix, "Map " + prefix)), "Map",
                Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
                Collections.singletonList(subHull), true, BoundaryTier.EMPHATIC));
        }
        return GraphProjection.projected(generation, nodes, enclosures,
            Collections.<ProjectedEdge>emptyList(), Collections.<RelationshipResolution>emptyList(),
            Collections.<PinProjection>emptyList());
    }

    private static GraphProjection asymmetricSiblingProjection(long generation) {
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        final List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        final EnclosureHullKey rootHull = hull(MAP_ONE, "h-root");
        final EnclosureHullKey childOneHull = hull(MAP_ONE, "h-c1");
        final EnclosureHullKey grandHull = hull(MAP_ONE, "h-g1");
        final EnclosureHullKey childTwoHull = hull(MAP_ONE, "h-c2");
        final List<ProjectedNodeKey> rootNodes = new ArrayList<ProjectedNodeKey>();
        for (int i = 0; i < 2; i++) {
            final ProjectedNodeKey key = ProjectedNodeKey.of(source(MAP_ONE, "r" + i));
            nodes.add(node(key, "Theorem: root statement " + i + " with a long label"));
            rootNodes.add(key);
        }
        final List<ProjectedNodeKey> childOneNodes = new ArrayList<ProjectedNodeKey>();
        for (int i = 0; i < 2; i++) {
            final ProjectedNodeKey key = ProjectedNodeKey.of(source(MAP_ONE, "c1-" + i));
            nodes.add(node(key, "Theorem: child one statement " + i + " with a long label"));
            childOneNodes.add(key);
        }
        final List<ProjectedNodeKey> grandNodes = new ArrayList<ProjectedNodeKey>();
        for (int i = 0; i < 2; i++) {
            final ProjectedNodeKey key = ProjectedNodeKey.of(source(MAP_ONE, "g1-" + i));
            nodes.add(node(key, "Theorem: grandchild statement " + i + " with a long label"));
            grandNodes.add(key);
        }
        final List<ProjectedNodeKey> childTwoNodes = new ArrayList<ProjectedNodeKey>();
        for (int i = 0; i < 2; i++) {
            final ProjectedNodeKey key = ProjectedNodeKey.of(source(MAP_ONE, "c2-" + i));
            nodes.add(node(key, "Theorem: child two statement " + i + " with a long label"));
            childTwoNodes.add(key);
        }
        enclosures.add(ProjectedEnclosure.of(grandHull,
            Collections.singletonList(EnclosureKey.of(source(MAP_ONE, "h-g1"))),
            Collections.singletonList(SafeNodeLabel.of("grandchild group", "grandchild group")), "Map",
            Optional.of(childOneHull), grandNodes, Collections.<EnclosureHullKey>emptyList(), false,
            BoundaryTier.SUBTLE));
        enclosures.add(ProjectedEnclosure.of(childOneHull,
            Collections.singletonList(EnclosureKey.of(source(MAP_ONE, "h-c1"))),
            Collections.singletonList(SafeNodeLabel.of("child one group", "child one group")), "Map",
            Optional.of(rootHull), childOneNodes, Collections.singletonList(grandHull), false,
            BoundaryTier.EMPHATIC));
        enclosures.add(ProjectedEnclosure.of(childTwoHull,
            Collections.singletonList(EnclosureKey.of(source(MAP_ONE, "h-c2"))),
            Collections.singletonList(SafeNodeLabel.of("child two group", "child two group")), "Map",
            Optional.of(rootHull), childTwoNodes, Collections.<EnclosureHullKey>emptyList(), false,
            BoundaryTier.SUBTLE));
        enclosures.add(ProjectedEnclosure.of(rootHull,
            Collections.singletonList(EnclosureKey.of(source(MAP_ONE, "h-root"))),
            Collections.singletonList(SafeNodeLabel.of("Root map", "Root map")), "Map",
            Optional.<EnclosureHullKey>empty(), rootNodes, Arrays.asList(childOneHull, childTwoHull), true,
            BoundaryTier.SUPPRESSED));
        return GraphProjection.projected(generation, nodes, enclosures,
            Collections.<ProjectedEdge>emptyList(), Collections.<RelationshipResolution>emptyList(),
            Collections.<PinProjection>emptyList());
    }

    private static GraphProjection dualProjection(long generation) {
        final ProjectedNodeKey rootA = ProjectedNodeKey.of(source(MAP_ONE, "r0"));
        final ProjectedNodeKey rootB = ProjectedNodeKey.of(source(MAP_ONE, "r1"));
        final List<ProjectedNodeKey> childNodes = new ArrayList<ProjectedNodeKey>();
        for (int i = 0; i < 3; i++) {
            childNodes.add(ProjectedNodeKey.of(source(MAP_ONE, "c" + i)));
        }
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        nodes.add(node(rootA, "Theorem: root statement zero with a long label"));
        nodes.add(node(rootB, "Theorem: root statement one with a long label"));
        for (int i = 0; i < 3; i++) {
            nodes.add(node(childNodes.get(i), "Theorem: child statement " + i + " with a long label"));
        }
        final EnclosureHullKey rootHull = hull(MAP_ONE, "root");
        final EnclosureHullKey childHull = hull(MAP_ONE, "child");
        final List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        enclosures.add(ProjectedEnclosure.of(childHull,
            Collections.singletonList(EnclosureKey.of(source(MAP_ONE, "child"))),
            Collections.singletonList(SafeNodeLabel.of("child group", "child group")), "Map",
            Optional.of(rootHull), childNodes, Collections.<EnclosureHullKey>emptyList(), false,
            BoundaryTier.SUBTLE));
        enclosures.add(ProjectedEnclosure.of(rootHull,
            Collections.singletonList(EnclosureKey.of(source(MAP_ONE, "root"))),
            Collections.singletonList(SafeNodeLabel.of("Root map", "Root map")), "Map",
            Optional.<EnclosureHullKey>empty(), Arrays.asList(rootA, rootB),
            Collections.singletonList(childHull), true, BoundaryTier.SUPPRESSED));
        return GraphProjection.projected(generation, nodes, enclosures,
            Collections.<ProjectedEdge>emptyList(), Collections.<RelationshipResolution>emptyList(),
            Collections.<PinProjection>emptyList());
    }

    private static GraphProjection fourMapProjection(long generation) {
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        final List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        final MapReferenceId[] maps = new MapReferenceId[] {MAP_ONE, MAP_TWO, MAP_THREE, MAP_FOUR};
        for (int m = 0; m < maps.length; m++) {
            final MapReferenceId map = maps[m];
            final String prefix = "m" + m;
            final int nodeCount = 2 + m;
            final EnclosureHullKey rootHull = hull(map, prefix + "-root");
            final EnclosureHullKey subHull = hull(map, prefix + "-sub");
            final List<ProjectedNodeKey> subNodes = new ArrayList<ProjectedNodeKey>();
            for (int i = 0; i < nodeCount; i++) {
                final ProjectedNodeKey nodeKey = ProjectedNodeKey.of(source(map, prefix + "-n" + i));
                nodes.add(node(nodeKey, "Theorem: " + prefix + " statement " + i + " with a long label"));
                subNodes.add(nodeKey);
            }
            enclosures.add(ProjectedEnclosure.of(subHull,
                Collections.singletonList(EnclosureKey.of(source(map, prefix + "-sub"))),
                Collections.singletonList(SafeNodeLabel.of(prefix + " fundamentals", prefix + " fundamentals")),
                "Map", Optional.of(rootHull), subNodes, Collections.<EnclosureHullKey>emptyList(), false,
                BoundaryTier.SUBTLE));
            enclosures.add(ProjectedEnclosure.of(rootHull,
                Collections.singletonList(EnclosureKey.of(source(map, prefix + "-root"))),
                Collections.singletonList(SafeNodeLabel.of("Map " + prefix, "Map " + prefix)), "Map",
                Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
                Collections.singletonList(subHull), true, BoundaryTier.EMPHATIC));
        }
        return GraphProjection.projected(generation, nodes, enclosures,
            Collections.<ProjectedEdge>emptyList(), Collections.<RelationshipResolution>emptyList(),
            Collections.<PinProjection>emptyList());
    }

    private static GraphProjection fourSiblingProjection(long generation) {
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        final List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        final EnclosureHullKey rootHull = hull(MAP_ONE, "g-root");
        final List<EnclosureHullKey> childHulls = new ArrayList<EnclosureHullKey>();
        for (int g = 0; g < 4; g++) {
            final EnclosureHullKey childHull = hull(MAP_ONE, "g" + g);
            childHulls.add(childHull);
            final List<ProjectedNodeKey> childNodes = new ArrayList<ProjectedNodeKey>();
            for (int i = 0; i < 2; i++) {
                final ProjectedNodeKey nodeKey = ProjectedNodeKey.of(source(MAP_ONE, "g" + g + "-n" + i));
                nodes.add(node(nodeKey, "Theorem: g" + g + " statement " + i + " with a long label"));
                childNodes.add(nodeKey);
            }
            enclosures.add(ProjectedEnclosure.of(childHull,
                Collections.singletonList(EnclosureKey.of(source(MAP_ONE, "g" + g))),
                Collections.singletonList(SafeNodeLabel.of("group " + g, "group " + g)), "Map",
                Optional.of(rootHull), childNodes, Collections.<EnclosureHullKey>emptyList(), false,
                BoundaryTier.SUBTLE));
        }
        enclosures.add(ProjectedEnclosure.of(rootHull,
            Collections.singletonList(EnclosureKey.of(source(MAP_ONE, "g-root"))),
            Collections.singletonList(SafeNodeLabel.of("Map g", "Map g")), "Map",
            Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(), childHulls, true,
            BoundaryTier.EMPHATIC));
        return GraphProjection.projected(generation, nodes, enclosures,
            Collections.<ProjectedEdge>emptyList(), Collections.<RelationshipResolution>emptyList(),
            Collections.<PinProjection>emptyList());
    }

    private static GraphProjection twoMapProjection(long generation) {
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        final List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        final MapReferenceId[] maps = new MapReferenceId[] {MAP_ONE, MAP_TWO};
        for (int m = 0; m < maps.length; m++) {
            final MapReferenceId map = maps[m];
            final String prefix = m == 0 ? "a" : "b";
            final int nodeCount = m == 0 ? 6 : 2;
            final EnclosureHullKey rootHull = hull(map, prefix + "-root");
            final EnclosureHullKey subHull = hull(map, prefix + "-sub");
            final List<ProjectedNodeKey> subNodes = new ArrayList<ProjectedNodeKey>();
            for (int i = 0; i < nodeCount; i++) {
                final ProjectedNodeKey nodeKey = ProjectedNodeKey.of(source(map, prefix + "-n" + i));
                nodes.add(node(nodeKey, "Theorem: " + prefix + " statement " + i + " with a long label"));
                subNodes.add(nodeKey);
            }
            enclosures.add(ProjectedEnclosure.of(subHull,
                Collections.singletonList(EnclosureKey.of(source(map, prefix + "-sub"))),
                Collections.singletonList(SafeNodeLabel.of(prefix + " fundamentals", prefix + " fundamentals")),
                "Map", Optional.of(rootHull), subNodes, Collections.<EnclosureHullKey>emptyList(), false,
                BoundaryTier.SUBTLE));
            enclosures.add(ProjectedEnclosure.of(rootHull,
                Collections.singletonList(EnclosureKey.of(source(map, prefix + "-root"))),
                Collections.singletonList(SafeNodeLabel.of("Map " + prefix, "Map " + prefix)), "Map",
                Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
                Collections.singletonList(subHull), true, BoundaryTier.EMPHATIC));
        }
        return GraphProjection.projected(generation, nodes, enclosures,
            Collections.<ProjectedEdge>emptyList(), Collections.<RelationshipResolution>emptyList(),
            Collections.<PinProjection>emptyList());
    }

    private static GraphProjection relationshipProjection(long generation) {
        final List<ProjectedNode> nodes = new ArrayList<ProjectedNode>();
        final List<ProjectedEnclosure> enclosures = new ArrayList<ProjectedEnclosure>();
        final List<ProjectedEdge> edges = new ArrayList<ProjectedEdge>();
        for (int m = 0; m < 2; m++) {
            final MapReferenceId map = m == 0 ? MAP_ONE : MAP_TWO;
            final String prefix = m == 0 ? "a" : "b";
            final EnclosureHullKey rootHull = hull(map, prefix + "-root");
            final EnclosureHullKey subHull = hull(map, prefix + "-sub");
            final List<ProjectedNodeKey> subNodes = new ArrayList<ProjectedNodeKey>();
            for (int i = 0; i < 4; i++) {
                final ProjectedNodeKey nodeKey = ProjectedNodeKey.of(source(map, prefix + "-n" + i));
                nodes.add(node(nodeKey, "Theorem: " + prefix + " statement " + i + " with a long label"));
                subNodes.add(nodeKey);
            }
            enclosures.add(ProjectedEnclosure.of(subHull,
                Collections.singletonList(EnclosureKey.of(source(map, prefix + "-sub"))),
                Collections.singletonList(SafeNodeLabel.of(prefix + " fundamentals", prefix + " fundamentals")),
                "Map", Optional.of(rootHull), subNodes, Collections.<EnclosureHullKey>emptyList(), false,
                BoundaryTier.SUBTLE));
            enclosures.add(ProjectedEnclosure.of(rootHull,
                Collections.singletonList(EnclosureKey.of(source(map, prefix + "-root"))),
                Collections.singletonList(SafeNodeLabel.of("Map " + prefix, "Map " + prefix)), "Map",
                Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
                Collections.singletonList(subHull), true, BoundaryTier.EMPHATIC));
        }
        for (int i = 0; i < 3; i++) {
            final long sequence = i + 1L;
            final GraphRelationshipRecord relationship = GraphRelationshipRecord.of(
                RelationshipId.of(String.format("20000000-0000-0000-0000-%012d", Long.valueOf(sequence))),
                sequence,
                source(MAP_ONE, "a-n" + i).persistedReference().get(),
                source(MAP_TWO, "b-n" + i).persistedReference().get(),
                RelationshipDirection.FORWARD,
                Collections.<UnknownXml>emptyList());
            final ProjectedEndpointKey sourceEndpoint =
                ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source(MAP_ONE, "a-n" + i)));
            final ProjectedEndpointKey targetEndpoint =
                ProjectedEndpointKey.ofNode(ProjectedNodeKey.of(source(MAP_TWO, "b-n" + i)));
            edges.add(ProjectedEdge.of(ProjectedEdgeKey.of(sourceEndpoint, targetEndpoint),
                Collections.singletonList(
                    EdgeContributor.graphRelationship(relationship, sourceEndpoint, targetEndpoint))));
        }
        return GraphProjection.projected(generation, nodes, enclosures, edges,
            Collections.<RelationshipResolution>emptyList(), Collections.<PinProjection>emptyList());
    }

    private static ProjectedNode node(ProjectedNodeKey key, String label) {
        return ProjectedNode.of(key, SafeNodeLabel.of(label, label), "Map", false);
    }

    private static PinProjection pin(ProjectedNodeKey node, double x, double y) {
        final PinRecord record = PinRecord.of(node.source().persistedReference().get(), x, y,
            Collections.<UnknownXml>emptyList());
        return PinProjection.active(record, node);
    }

    private static EnclosureHullKey hull(MapReferenceId map, String id) {
        return EnclosureHullKey.of(Collections.singletonList(EnclosureKey.of(source(map, id))));
    }

    private static SourceNodeKey source(MapReferenceId map, String id) {
        return SourceNodeKey.persisted(NodeReference.of(map, PersistedNodeId.of(id)));
    }

    private ContactProbe() {
    }
}
