package org.freeplane.plugin.graph.layout.graphstream;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.Random;

import org.freeplane.plugin.graph.geometry.LayoutPoint;
import org.freeplane.plugin.graph.layout.LayoutCalibration;
import org.freeplane.plugin.graph.layout.LayoutEngine;
import org.freeplane.plugin.graph.layout.LayoutRequest;
import org.freeplane.plugin.graph.projection.EnclosureHullKey;
import org.freeplane.plugin.graph.projection.GraphProjection;
import org.freeplane.plugin.graph.projection.PinProjection;
import org.freeplane.plugin.graph.projection.ProjectedEnclosure;
import org.freeplane.plugin.graph.projection.ProjectionDiff;
import org.graphstream.ui.geom.Vector3;
import org.junit.Test;

/**
 * Force-level pin for the offset-aware anchor separation contract (specification
 * section 3.6): the force is inert at the packed positions, and the push direction
 * follows the footprint-box centres ({@code anchor - footprint anchor offset}) rather
 * than the anchors.
 *
 * <p>The extents and offsets asserted here are read back from the particles that
 * {@code GraphStreamLayoutEngine.synchronize()} configured, so the production
 * {@code configureAnchorExtents(..., -anchorX, -anchorY)} mapping is covered as well
 * as the force formula in {@code TypedSpringBox.addBoundaryRepulsion}. Both a flipped
 * offset sign at the call site and an anchor-based push direction turn a test in this
 * class red.
 */
public class AnchorSeparationShould {
    @Test
    public void configuredExtentsUseTheNegativeFootprintAnchorOffset() throws Exception {
        final GraphProjection projection = ClusterProximityShould.fixture('A');
        final GraphStreamLayoutEngine.BoundarySizes sizes =
            new GraphStreamLayoutEngine.BoundarySizes(projection);
        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            engine.apply(LayoutRequest.of(ClusterProximityShould.WORKSPACE, projection,
                ProjectionDiff.between(projection, projection), Collections.<PinProjection>emptyList()));
            final GraphStreamLayoutEngine typedEngine = (GraphStreamLayoutEngine) engine;
            boolean sawOffset = false;
            for (final ProjectedEnclosure enclosure : projection.enclosures()) {
                final EnclosureHullKey key = enclosure.hullKey();
                final GraphStreamLayoutEngine.BoundarySizes.Footprint footprint = sizes.footprint(key);
                final TypedNodeParticle particle = anchorParticle(typedEngine, key);
                assertThat(particle.separationHalfWidth()).as("half width of %s", key)
                    .isEqualTo(footprint.width * 0.5);
                assertThat(particle.separationHalfHeight()).as("half height of %s", key)
                    .isEqualTo(footprint.height * 0.5);
                assertThat(particle.separationOffsetX()).as("offset x of %s", key)
                    .isEqualTo(-footprint.anchorX);
                assertThat(particle.separationOffsetY()).as("offset y of %s", key)
                    .isEqualTo(-footprint.anchorY);
                if (footprint.anchorX != 0.0 || footprint.anchorY != 0.0) {
                    sawOffset = true;
                }
            }
            assertThat(sawOffset)
                .as("fixture A must contain an anchor whose footprint has a non-zero packing offset")
                .isTrue();
        }
    }

    @Test
    public void packedPositionsProduceNoSeparationDisplacement() throws Exception {
        final GraphProjection projection = ClusterProximityShould.fixture('A');
        try (LayoutEngine engine = GraphStreamLayoutFactory.create(LayoutCalibration.spikeDefaults())) {
            engine.apply(LayoutRequest.of(ClusterProximityShould.WORKSPACE, projection,
                ProjectionDiff.between(projection, projection), Collections.<PinProjection>emptyList()));
            final GraphStreamLayoutEngine typedEngine = (GraphStreamLayoutEngine) engine;
            final TypedSpringBox springBox = springBoxOf(typedEngine);
            for (final ProjectedEnclosure enclosure : projection.enclosures()) {
                final TypedNodeParticle particle = anchorParticle(typedEngine, enclosure.hullKey());
                final Vector3 displacement = new Vector3();
                springBox.addBoundaryRepulsion(particle, displacement);
                assertThat(LayoutPoint.of(displacement.at(0), displacement.at(1)))
                    .as("packed position separation displacement of %s", enclosure.hullKey())
                    .isEqualTo(LayoutPoint.of(0.0, 0.0));
            }
        }
    }

    @Test
    public void displacementFollowsTheBoxCentresNotTheAnchorsAlongX() {
        final TypedSpringBox springBox = separationBox();
        // Both boxes are tall and thin, so the x overlap is the smaller one and the
        // force resolves along x. Anchor order (0 < 55) is opposite to box-centre
        // order (30 > 25), so an "away from the other anchor" push reverses the sign.
        final TypedNodeParticle first = configure(springBox, 1.0, 100.0, 30.0, 10.0, -30.0, -10.0, 0.0,
            0.0, 55.0, 0.0);

        final Vector3 displacement = new Vector3();
        springBox.addBoundaryRepulsion(first, displacement);
        assertThat(displacement.at(0)).as("x displacement").isEqualTo(2.5);
        assertThat(displacement.at(1)).as("y displacement").isEqualTo(0.0);
    }

    @Test
    public void displacementFollowsTheBoxCentresNotTheAnchorsAlongY() {
        final TypedSpringBox springBox = separationBox();
        // Mirror of the x case: wide flat boxes resolve along y, and the box-centre
        // order (50 > 45) is again opposite to the anchor order (0 < 95).
        final TypedNodeParticle first = configure(springBox, 100.0, 1.0, 0.0, 50.0, 0.0, -50.0, 0.0, 0.0,
            0.0, 95.0);

        final Vector3 displacement = new Vector3();
        springBox.addBoundaryRepulsion(first, displacement);
        assertThat(displacement.at(0)).as("x displacement").isEqualTo(0.0);
        assertThat(displacement.at(1)).as("y displacement").isEqualTo(2.5);
    }

    @Test
    public void packedPairWithPositiveForceGapProducesNoDisplacement() {
        final TypedSpringBox springBox = separationBox();
        // x centre distance 50 - 20 = 30; the half extents plus SIBLING_GAP require
        // 1 + 1 + 8 = 10, so forceGapX = 20 >= 0 and the force is skipped.
        final TypedNodeParticle first = configure(springBox, 1.0, 100.0, 30.0, 10.0, -30.0, -10.0, 0.0,
            0.0, 80.0, 0.0);

        final Vector3 displacement = new Vector3();
        springBox.addBoundaryRepulsion(first, displacement);
        assertThat(LayoutPoint.of(displacement.at(0), displacement.at(1)))
            .isEqualTo(LayoutPoint.of(0.0, 0.0));
    }

    private static TypedSpringBox separationBox() {
        return new TypedSpringBox(LayoutCalibration.spikeDefaults(), new Random(20261013L));
    }

    private static TypedNodeParticle configure(final TypedSpringBox springBox, final double halfWidth,
            final double halfHeight, final double offsetAX, final double offsetAY, final double offsetBX,
            final double offsetBY, final double positionAX, final double positionAY, final double positionBX,
            final double positionBY) {
        final TypedNodeParticle first = springBox.newNodeParticle("anchor-a");
        final TypedNodeParticle second = springBox.newNodeParticle("anchor-b");
        springBox.configureParticle("anchor-a", 0.0, false, true, null);
        springBox.configureParticle("anchor-b", 0.0, false, true, null);
        springBox.configureAnchorExtents("anchor-a", halfWidth, halfHeight, offsetAX, offsetAY);
        springBox.configureAnchorExtents("anchor-b", halfWidth, halfHeight, offsetBX, offsetBY);
        first.moveTo(positionAX, positionAY, 0.0);
        second.moveTo(positionBX, positionBY, 0.0);
        return first;
    }

    private static TypedSpringBox springBoxOf(final GraphStreamLayoutEngine engine) throws Exception {
        final Field field = GraphStreamLayoutEngine.class.getDeclaredField("springBox");
        field.setAccessible(true);
        return (TypedSpringBox) field.get(engine);
    }

    private static TypedNodeParticle anchorParticle(final GraphStreamLayoutEngine engine,
            final EnclosureHullKey hull) throws Exception {
        final Field particlesField = GraphStreamLayoutEngine.class.getDeclaredField("particles");
        particlesField.setAccessible(true);
        final Map<?, ?> particles = (Map<?, ?>) particlesField.get(engine);
        String id = null;
        for (final Object value : particles.values()) {
            final GraphStreamLayoutEngine.ParticleState state = (GraphStreamLayoutEngine.ParticleState) value;
            if (hull.equals(state.anchorKey)) {
                id = state.id;
                break;
            }
        }
        assertThat(id).as("particle id of %s", hull).isNotNull();
        final Field typedParticles = TypedSpringBox.class.getDeclaredField("typedParticles");
        typedParticles.setAccessible(true);
        final Map<?, ?> typed = (Map<?, ?>) typedParticles.get(springBoxOf(engine));
        return (TypedNodeParticle) typed.get(id);
    }
}
