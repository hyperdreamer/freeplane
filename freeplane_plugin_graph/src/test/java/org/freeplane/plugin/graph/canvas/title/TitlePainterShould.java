package org.freeplane.plugin.graph.canvas.title;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class TitlePainterShould {
    private static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

    private static Graphics2D recording(final List<String> texts, final List<Float> xs, final List<Float> ys,
            final List<Float> sizes) {
        final Graphics2D graphics = mock(Graphics2D.class);
        when(graphics.create()).thenReturn(graphics);
        when(graphics.getTransform()).thenReturn(new AffineTransform());
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(final InvocationOnMock invocation) {
                texts.add((String) invocation.getArguments()[0]);
                xs.add((Float) invocation.getArguments()[1]);
                ys.add((Float) invocation.getArguments()[2]);
                return null;
            }
        }).when(graphics).drawString(anyString(), anyFloat(), anyFloat());
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(final InvocationOnMock invocation) {
                sizes.add(((Font) invocation.getArguments()[0]).getSize2D());
                return null;
            }
        }).when(graphics).setFont(org.mockito.ArgumentMatchers.any(Font.class));
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(final InvocationOnMock invocation) {
                return null;
            }
        }).when(graphics).setColor(org.mockito.ArgumentMatchers.any(Color.class));
        return graphics;
    }

    @Test
    public void plainRowDrawsAtTheCentredAnchor() {
        final TitleRow row = TitleRow.plain("Theorem: initial segment", FONT, 1.0);
        final List<String> texts = new ArrayList<String>();
        final List<Float> xs = new ArrayList<Float>();
        final List<Float> ys = new ArrayList<Float>();
        final List<Float> sizes = new ArrayList<Float>();
        TitlePainter.paint(recording(texts, xs, ys, sizes), row, 100.0, 50.0, 1.0, Color.BLACK,
            new AffineTransform());
        assertThat(texts).hasSize(1);
        assertThat(xs.get(0).doubleValue()).isCloseTo(100.0 - row.width() / 2.0, offset(1.0e-4));
        assertThat(ys.get(0).doubleValue())
            .isCloseTo(50.0 + ((float) row.ascent() - (float) row.descent()) * 0.5f, offset(1.0e-4));
    }

    @Test
    public void plainRowAtZoomAboveTheClampUsesTheClampedFont() {
        final TitleRow row = TitleRow.plain("clamped", FONT, 24.0);
        final PlainRunRow run = (PlainRunRow) row.runs().get(0);
        assertThat(run.worldFont().getSize2D()).isEqualTo(1.0f);
        final List<String> texts = new ArrayList<String>();
        final List<Float> xs = new ArrayList<Float>();
        final List<Float> ys = new ArrayList<Float>();
        final List<Float> sizes = new ArrayList<Float>();
        TitlePainter.paint(recording(texts, xs, ys, sizes), row, 10.0, 10.0, 24.0, Color.BLACK,
            new AffineTransform());
        assertThat(sizes).containsExactly(Float.valueOf(1.0f));
    }

    @Test
    public void formulaBaselineMatchesTheTextBaseline() {
        final FormulaIcons icons = new FormulaIcons();
        final MeasuredRun measured = MeasuredRun.math("\\alpha", "$\\alpha$", 0.0, 9.0, 9.0, 3.0);
        final MeasuredTitle measuredTitle = MeasuredTitle.rendered(
            java.util.Collections.singletonList(measured), 9.0, 12.0, 9.0, 3.0);
        final TitleRow row = TitleRow.resolve(measuredTitle, icons, FONT, 1.0);
        final MathRunRow run = (MathRunRow) row.runs().get(0);
        final Point2D top = TitlePainter.iconTopComponent(run, 20.0, 40.0, 1.0, new AffineTransform(),
            new AffineTransform());
        assertThat(top.getX()).isCloseTo(20.0, offset(1.0e-9));
        assertThat(top.getY()).isCloseTo(40.0 - 9.0, offset(1.0e-9));
    }

    @Test
    public void nonIdentityBaseTransformPositionsTheIconOnce() {
        final FormulaIcons icons = new FormulaIcons();
        final MeasuredRun measured = MeasuredRun.math("\\alpha", "$\\alpha$", 0.0, 9.0, 10.0, 3.0);
        final MeasuredTitle measuredTitle = MeasuredTitle.rendered(
            java.util.Collections.singletonList(measured), 9.0, 13.0, 10.0, 3.0);
        final TitleRow row = TitleRow.resolve(measuredTitle, icons, FONT, 1.6);
        final MathRunRow run = (MathRunRow) row.runs().get(0);
        final AffineTransform base = AffineTransform.getTranslateInstance(37.0, 11.0);
        base.scale(1.25, 1.25);
        final AffineTransform world = AffineTransform.getScaleInstance(1.6, 1.6);
        final AffineTransform current = new AffineTransform(base);
        current.concatenate(world);
        final Point2D top = TitlePainter.iconTopComponent(run, 100.0, 50.0, 1.6, current, base);
        final Point2D deviceTop = base.transform(top, null);
        final Point2D deviceBaseline = current.transform(new Point2D.Double(100.0, 50.0), null);
        final double expectedTop = deviceBaseline.getY() - run.ascent() * 1.6 * 1.25;
        assertThat(deviceTop.getY()).isCloseTo(expectedTop, offset(1.0e-6));
        assertThat(deviceTop.getX()).isCloseTo(deviceBaseline.getX(), offset(1.0e-6));
    }

    @Test
    public void painterHasNoMeasurementDependency() throws Exception {
        for (final Field field : TitlePainter.class.getDeclaredFields()) {
            assertThat(field.getType().getName()).doesNotContain("TitleMeasurer").doesNotContain("FormulaIcons")
                .doesNotContain("MeasuredTitle");
        }
        for (final Method method : TitlePainter.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            for (final Parameter parameter : method.getParameters()) {
                assertThat(parameter.getType().getName()).doesNotContain("TitleMeasurer")
                    .doesNotContain("FormulaIcons").doesNotContain("MeasuredTitle");
            }
        }
        final Path source = Paths.get("src/main/java/org/freeplane/plugin/graph/canvas/title/TitlePainter.java");
        assertThat(Files.isRegularFile(source)).isTrue();
        final String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        assertThat(text).doesNotContain("getStringBounds").doesNotContain("getLineMetrics")
            .doesNotContain("TitleMeasurer").doesNotContain("FormulaIcons");
    }
}
