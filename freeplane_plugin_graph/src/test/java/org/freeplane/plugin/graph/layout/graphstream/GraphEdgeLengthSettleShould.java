package org.freeplane.plugin.graph.layout.graphstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.freeplane.plugin.graph.layout.graphstream.InitialSpacingFixtures.*;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.freeplane.plugin.graph.canvas.*;
import org.freeplane.plugin.graph.control.CanvasState;
import org.freeplane.plugin.graph.control.OperationalStatus;
import org.freeplane.plugin.graph.geometry.*;
import org.freeplane.plugin.graph.layout.*;
import org.freeplane.plugin.graph.projection.*;
import org.freeplane.plugin.graph.projection.input.SafeNodeLabel;
import org.freeplane.plugin.graph.workspace.model.*;
import org.freeplane.plugin.graph.workspace.model.DisplaySettings.CanvasTheme;
import org.junit.Test;

/** D2 measurements, independent of D1's historical C0 and acceptance thresholds. */
public class GraphEdgeLengthSettleShould {
    private static final String DELIVERY = "448c0d679d2b1d46563eaf079352e1eefb254305";
    private static final int MAX_STEPS = 6000;
    private static final int FOLLOW_UP_FRAMES = 100;
    private static final long PHASE_SECONDS = 600;
    private static final List<String> FIXTURES = Arrays.asList("reference", "S1", "cross", "mixed",
        "nested-three-level", "pinned-neighbours", "multi-membership", "unenclosed-nn",
        "node-enclosure", "enclosure-enclosure", "wide-diagonal-label-z163");

    @Test
    public void measureDeliveryBaseAndCandidateInFreshEquivalentJvms() throws Exception {
        assertThat(System.getProperty("java.awt.headless")).isEqualTo("true");
        Path repo = Paths.get("").toAbsolutePath();
        while (repo != null && !Files.exists(repo.resolve(".git"))) repo = repo.getParent();
        assertThat(repo).isNotNull();
        Path build = repo.resolve("freeplane_plugin_graph/build");
        Path output = Files.createTempDirectory(build, "edge-length-");
        Files.write(build.resolve("edge-length-current.txt"),
            (output.toString() + "\n").getBytes(StandardCharsets.UTF_8));
        Set<String> cp = new LinkedHashSet<String>();
        for (ClassLoader loader = getClass().getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof URLClassLoader) {
                for (URL url : ((URLClassLoader) loader).getURLs()) {
                    if (url.getProtocol().equals("file")) cp.add(Paths.get(url.toURI()).toString());
                }
            }
        }
        cp.addAll(Arrays.asList(System.getProperty("java.class.path").split(java.io.File.pathSeparator)));
        Process driver = new ProcessBuilder("python3", "-c", DRIVER, repo.toString(),
            String.join(java.io.File.pathSeparator, cp), output.toString(), DELIVERY,
            String.join(",", FIXTURES)).inheritIO().start();
        try {
            assertThat(driver.waitFor(8, TimeUnit.HOURS)).as("D2 driver completion; %s", output).isTrue();
            assertThat(driver.exitValue()).as("D2 driver exit; %s", output).isZero();
        }
        finally {
            if (driver.isAlive()) driver.destroyForcibly();
        }
        for (String file : Arrays.asList("manifest.json", "comparison.json", "summary.md")) {
            assertThat(Files.isRegularFile(output.resolve(file))).as(file).isTrue();
        }
    }

    /* Standard-library driver: all source bytes are materialized from delivery, then take precedence
       over the candidate classpath. The worker verifies code-source locations before measuring. */
    private static final String DRIVER = String.join("\n",
        "import datetime, hashlib, json, os, pathlib, subprocess, sys, time",
        "repo, cp, out, base, fixtures = sys.argv[1:]",
        "repo, out = pathlib.Path(repo), pathlib.Path(out)",
        "java = '/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin/java'",
        "prefix = 'freeplane_plugin_graph/src/main/java/'",
        "head = subprocess.check_output(['git','rev-parse','HEAD'],cwd=repo,text=True).strip()",
        "sha = lambda b: hashlib.sha256(b).hexdigest()",
        "sources = subprocess.check_output(['git','ls-tree','-r','--name-only',base,'--',prefix],cwd=repo,text=True).splitlines()",
        "manifest = dict(schema_version=1, baseline=base, candidate=head, utc=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'), commands=[], source_sha256={}, jar_sha256={}, fixture_input_sha256={}, measurement_schema=dict(first_idle='first published idle frame, or null on operational bound; never an equilibrium assertion',pairs='all projected relationship edges; centers and analytic endpoint radii, per-pair and type aggregates at first idle and +100',conflicts='published straight-chord clearance scan (3 wu); routed proper-crossing and node-clearance scan',routing='one row per published apply/step frame, including the 100-frame follow-up',bounds=dict(max_steps=6000,phase_seconds=600,follow_up_frames=100)))",
        "def save(): (out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\\n')",
        "def command(label, argv, seconds):",
        "    record = dict(label=label,argv=argv,cwd=str(repo)); manifest['commands'].append(record); save(); start=time.monotonic()",
        "    try:",
        "        with (out/(label+'.log')).open('w') as log: result=subprocess.run(argv,cwd=repo,env=dict(os.environ,JAVA_TOOL_OPTIONS='-Djava.awt.headless=true'),stdout=log,stderr=subprocess.STDOUT,timeout=seconds)",
        "        record['returncode']=result.returncode; assert result.returncode == 0, (label,out)",
        "    finally: record['elapsed_s']=time.monotonic()-start; save()",
        "command('java-version',[java,'-version'],60)",
        "source_root=out/'delivery-src'; classes=out/'delivery-classes'; classes.mkdir()",
        "files=[]",
        "for relative in sources:",
        "    if not relative.endswith('.java'): continue",
        "    data=subprocess.check_output(['git','show',base+':'+relative],cwd=repo)",
        "    target=source_root/relative; target.parent.mkdir(parents=True,exist_ok=True); target.write_bytes(data); files.append(str(target))",
        "    manifest['source_sha256'][relative]=dict(delivery=sha(data),candidate=sha((repo/relative).read_bytes()))",
        "manifest['test_source_sha256']={str(p.relative_to(repo)):sha(p.read_bytes()) for p in sorted((repo/'freeplane_plugin_graph/src/test/java').rglob('*.java'))}",
        "for item in cp.split(os.pathsep):",
        "    p=pathlib.Path(item)",
        "    if p.suffix == '.jar' and p.is_file(): manifest['jar_sha256'][str(p)]=sha(p.read_bytes())",
        "command('compile-delivery',[str(pathlib.Path(java).with_name('javac')),'-encoding','UTF-8','-source','8','-target','8','-classpath',cp,'-d',str(classes)]+files,120)",
        "comparison={}; findings=[]",
        "for fixture in fixtures.split(','):",
        "    rows={}",
        "    for arm in ('delivery-448c0d679d','candidate'):",
        "        target=out/(arm+'-'+fixture+'.json'); actual_cp=str(classes)+os.pathsep+cp if arm.startswith('delivery') else cp",
        "        command(arm+'-'+fixture,[java,'-Xms128m','-Xmx512m','-Djava.awt.headless=true','-cp',actual_cp,'org.freeplane.plugin.graph.layout.graphstream.GraphEdgeLengthSettleShould',fixture,str(target),str(classes) if arm.startswith('delivery') else 'candidate'],1260)",
        "        rows[arm]=json.loads(target.read_text())",
        "    before,after=rows.values(); assert before['input_sha256']==after['input_sha256'], fixture",
        "    assert before['runtime']==after['runtime'], fixture",
        "    manifest['fixture_input_sha256'][fixture]=after['input_sha256']",
        "    comparison[fixture]={arm:{k:r[k] for k in ('first_idle_step','first_idle_wall_s','status','first_idle','after_100','leaders')} for arm,r in rows.items()}",
        "    for arm,r in rows.items():",
        "        if r['status'] != 'settled': findings.append(fixture+' '+arm+': '+r['status'])",
        "        for p in r['first_idle']['pairs']:",
        "            if p['rim_gap'] < 6.0: findings.append(fixture+' '+arm+' sub-6 rim gap: '+str(p))",
        "        for kind,a in r['first_idle']['aggregates'].items():",
        "            if isinstance(a,dict) and a['minimum_rim_gap'] != a['median_rim_gap']: findings.append(fixture+' '+arm+' nonuniform (lopsided) '+kind+' gaps: '+str(a))",
        "    old={p['key']:p for p in before['first_idle']['pairs']}",
        "    for p in after['first_idle']['pairs']:",
        "        b=old[p['key']]; p['delivery_center_distance']=b['center_distance']; p['delivery_rim_gap']=b['rim_gap']",
        "    save()",
        "(out/'comparison.json').write_text(json.dumps(dict(fixtures=comparison,findings=findings),indent=2)+'\\n')",
        "lines=['# D2 fresh-relaxation measurements','', 'Baseline: '+base+'. Candidate: '+head+'.', '', 'Rest length is a force target. All pair values and routing rows remain in the arm receipts; no numeric performance or equilibrium gate is imposed.', '', '| Fixture | Delivery first idle | Candidate first idle |', '| --- | ---: | ---: |']",
        "for f,r in comparison.items(): lines.append('| '+f+' | '+str(r['delivery-448c0d679d']['first_idle_step'])+' | '+str(r['candidate']['first_idle_step'])+' |')",
        "lines += ['', 'Findings (including every observed sub-6 gap):', ''] + (['- '+s for s in findings] or ['None.'])",
        "lines += ['', 'Per-pair center distances and rim gaps:', '']",
        "for f,r in comparison.items():",
        "    for p in r['candidate']['first_idle']['pairs']: lines.append('- '+f+': '+str(p))",
        "(out/'summary.md').write_text('\\n'.join(lines)+'\\n'); save()",
        "print('D2 receipts:',out,flush=True)");

    public static void main(String[] args) throws Exception {
        String name = args[0];
        Path output = Paths.get(args[1]);
        String location = GraphStreamLayoutEngine.class.getProtectionDomain().getCodeSource().getLocation().toString();
        String painterLocation = Class.forName("org.freeplane.plugin.graph.canvas.GraphPainter")
            .getProtectionDomain().getCodeSource().getLocation().toString();
        if (args[2].equals("candidate")) {
            assertThat(location).doesNotContain("delivery-classes");
            assertThat(painterLocation).doesNotContain("delivery-classes");
        }
        else {
            String expected = Paths.get(args[2]).toUri().toString();
            assertThat(Paths.get(new java.net.URI(location))).isEqualTo(Paths.get(new java.net.URI(expected)));
            assertThat(Paths.get(new java.net.URI(painterLocation))).isEqualTo(Paths.get(new java.net.URI(expected)));
        }
        GraphProjection projection = fixture(name);
        verifyFixture(name, projection);
        Map<String, Object> inputs = inputs(projection);
        if (name.equals("wide-diagonal-label-z163")) inputs.put("fixed_render", wideInputs());
        Map<String, Object> receipt = map("fixture", name, "input", inputs,
            "input_sha256", hash(json(inputs).getBytes(StandardCharsets.UTF_8)),
            "engine_code_location", location, "painter_code_location", painterLocation,
            "driver_code_location", GraphEdgeLengthSettleShould.class.getProtectionDomain().getCodeSource().getLocation().toString(),
            "runtime", runtime(), "calibration", map("containment", LayoutCalibration.spikeDefaults().containment(),
                "hierarchy", LayoutCalibration.spikeDefaults().hierarchy(), "same_map", LayoutCalibration.spikeDefaults().sameMap()));
        List<Object> routing = new ArrayList<Object>();
        long start = System.nanoTime();
        Integer idleStep = null;
        Double idleSeconds = null;
        GraphGeometryEngine geometryEngine = new GraphGeometryEngine();
        EdgeRouter router = new EdgeRouter();
        Set<ProjectedEndpointKey> visible = ProjectedEndpointVisibility.visibleEndpoints(projection.nodes(), projection.enclosures());
        try (LayoutWorker worker = new LayoutWorker(LayoutCalibration.spikeDefaults())) {
            long deadline = start + TimeUnit.SECONDS.toNanos(PHASE_SECONDS);
            LayoutFrame frame = InitialSpacingFrameAssertions.awaitWithinDeadline(worker.submit(request(projection, projection)), deadline);
            publish(projection, frame, 0, geometryEngine, router, visible, routing);
            int step = 0;
            while (!frame.idle().idle() && step < MAX_STEPS && System.nanoTime() < deadline) {
                frame = InitialSpacingFrameAssertions.awaitWithinDeadline(worker.step(), deadline);
                publish(projection, frame, ++step, geometryEngine, router, visible, routing);
            }
            if (frame.idle().idle()) {
                idleStep = step;
                idleSeconds = (System.nanoTime() - start) / 1e9;
            }
            receipt.put("first_idle_step", idleStep);
            receipt.put("first_idle_wall_s", idleSeconds);
            receipt.put("status", frame.idle().idle() ? "settled" : "operational-bound-without-idle");
            receipt.put("first_idle", snapshot(projection, frame, geometryEngine, router, visible));
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(PHASE_SECONDS);
            int further = 0;
            while (further < FOLLOW_UP_FRAMES && System.nanoTime() < deadline) {
                frame = InitialSpacingFrameAssertions.awaitWithinDeadline(worker.step(), deadline);
                publish(projection, frame, ++step, geometryEngine, router, visible, routing);
                further++;
            }
            receipt.put("follow_up_frames", further);
            receipt.put("after_100", snapshot(projection, frame, geometryEngine, router, visible));
        }
        receipt.put("routing_per_published_frame", routing);
        receipt.put("leaders", name.equals("wide-diagonal-label-z163") ? wideLeaders() : Collections.emptyList());
        receipt.put("wall_s", (System.nanoTime() - start) / 1e9);
        Files.write(output, (json(receipt) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static GraphProjection fixture(String name) throws Exception {
        if (Arrays.asList("reference", "S1", "cross", "mixed").contains(name)) return named(name);
        if (name.equals("node-enclosure")) return matrix(true, "ne", false);
        if (name.equals("enclosure-enclosure")) return matrix(true, "ee", false);
        if (name.equals("pinned-neighbours")) {
            return single(4, 1L, false, Collections.singletonList(pin(key(A, "n0"), 0, 0)));
        }
        if (name.equals("multi-membership")) {
            GraphProjection p = single(4, 1L, false, Collections.<PinProjection>emptyList());
            List<ProjectedEnclosure> hulls = new ArrayList<ProjectedEnclosure>(p.enclosures());
            hulls.add(enclosure(A, "second-membership", "Second membership", Optional.<EnclosureHullKey>empty(),
                Arrays.asList(key(A, "n0"), key(A, "n1")), Collections.<EnclosureHullKey>emptyList(), true, BoundaryTier.SUBTLE));
            return projection(1L, p.nodes(), hulls, p.edges(), p.pins());
        }
        if (name.equals("nested-three-level")) {
            GraphProjection p = single(4, 1L, false, Collections.<PinProjection>emptyList());
            List<ProjectedEnclosure> hulls = Arrays.asList(
                enclosure(A, "sub", "Sub", Optional.of(hull(A, "middle")), p.enclosures().get(0).directNodes(),
                    Collections.<EnclosureHullKey>emptyList(), false, BoundaryTier.SUBTLE),
                enclosure(A, "middle", "Middle", Optional.of(hull(A, "root")), Collections.<ProjectedNodeKey>emptyList(),
                    Collections.singletonList(hull(A, "sub")), false, BoundaryTier.SUBTLE),
                enclosure(A, "root", "Root", Optional.<EnclosureHullKey>empty(), Collections.<ProjectedNodeKey>emptyList(),
                    Collections.singletonList(hull(A, "middle")), true, BoundaryTier.EMPHATIC));
            return projection(1L, p.nodes(), hulls, p.edges(), p.pins());
        }
        if (name.equals("wide-diagonal-label-z163")) return wideProjection();
        if (name.equals("unenclosed-nn")) {
            GraphProjection p = single(2, 1L, false, Collections.<PinProjection>emptyList());
            return projection(1L, p.nodes(), Collections.<ProjectedEnclosure>emptyList(), Collections.singletonList(
                nativeEdge(0, endpoint(p.nodes().get(0).key()), endpoint(p.nodes().get(1).key()), false)), p.pins());
        }
        throw new IllegalArgumentException(name);
    }

    private static void verifyFixture(String name, GraphProjection p) {
        if (name.equals("reference")) assertThat(p.edges()).isEmpty();
        else assertThat(p.edges()).as("actual relationship in %s", name).isNotEmpty();
        if (name.equals("node-enclosure")) assertThat(p.edges().get(0).first().isNode()
            != p.edges().get(0).second().isNode()).isTrue();
        if (name.equals("enclosure-enclosure")) {
            assertThat(p.edges().get(0).first().isEnclosure()).isTrue();
            assertThat(p.edges().get(0).second().isEnclosure()).isTrue();
        }
        if (name.equals("pinned-neighbours")) assertThat(p.pins()).hasSize(1);
        if (name.equals("multi-membership")) {
            int memberships = 0;
            for (ProjectedEnclosure e : p.enclosures()) if (e.directNodes().contains(key(A, "n0"))) memberships++;
            assertThat(memberships).isEqualTo(2);
        }
        if (name.equals("nested-three-level")) {
            assertThat(p.enclosures()).hasSize(3);
            assertThat(p.enclosures().get(0).parentHull()).contains(p.enclosures().get(1).hullKey());
            assertThat(p.enclosures().get(1).parentHull()).contains(p.enclosures().get(2).hullKey());
        }
    }

    private static void publish(GraphProjection p, LayoutFrame f, int step, GraphGeometryEngine engine,
            EdgeRouter router, Set<ProjectedEndpointKey> visible, List<Object> rows) {
        assertThat(f.failed()).as("published frame %s", step).isFalse();
        for (PinProjection pin : p.pins()) if (pin.active()) {
            assertThat(f.positions().nodes().get(pin.projectedNode().get())).isEqualTo(LayoutPoint.of(pin.x(), pin.y()));
        }
        EdgePaths paths = router.route(p, f.positions(), engine.computeHulls(p, f.positions(),
            InitialSpacingFrameAssertions.metrics()), visible);
        EdgeRouteDiagnostics d = paths.diagnostics();
        rows.add(map("frame", step, "idle", f.idle().idle(), "route_index_items", d.routeIndexItems(),
            "route_obstacle_checks", d.routeObstacleChecks(), "route_pair_checks", d.routePairChecks(),
            "budget_exhausted", d.routeBudgetExhausted(), "detection_truncated", d.detectionTruncated(),
            "straight", d.edgesStraight(), "bow", d.edgesBow(), "dogleg", d.edgesDogleg(), "fallback", d.edgesFallback(),
            "chord_crossings_detected", f.edgeConflictDiagnostics().crossingsDetected(),
            "chord_edge_over_node_detected", f.edgeConflictDiagnostics().edgeOverNodeDetected(),
            "route_index_nanos", paths.timings().indexNanos(), "route_selection_nanos", paths.timings().selectionNanos(),
            "route_residual_nanos", paths.timings().residualNanos()));
    }

    private static Map<String, Object> snapshot(GraphProjection p, LayoutFrame frame, GraphGeometryEngine engine,
            EdgeRouter router, Set<ProjectedEndpointKey> visible) {
        List<Object> pairs = new ArrayList<Object>();
        Map<String, List<Double>> centers = new TreeMap<String, List<Double>>();
        Map<String, List<Double>> gaps = new TreeMap<String, List<Double>>();
        for (ProjectedEdge e : p.edges()) {
            LayoutPoint first = position(p, frame.positions(), e.first());
            LayoutPoint second = position(p, frame.positions(), e.second());
            double center = distance(first, second);
            double r1 = InitialSpacingConsumers.endpointRadius(p, e.first(), 24.0);
            double r2 = InitialSpacingConsumers.endpointRadius(p, e.second(), 24.0);
            double gap = center - r1 - r2;
            String type = e.first().isNode() && e.second().isNode() ? "node-node"
                : e.first().isEnclosure() && e.second().isEnclosure() ? "enclosure-enclosure" : "node-enclosure";
            pairs.add(map("key", CanonicalKeys.edge(e.key()), "type", type, "first", point(first), "second", point(second),
                "first_radius", r1, "second_radius", r2, "center_distance", center, "rim_gap", gap,
                "contributors", e.contributors().size()));
            if (!centers.containsKey(type)) { centers.put(type, new ArrayList<Double>()); gaps.put(type, new ArrayList<Double>()); }
            centers.get(type).add(center); gaps.get(type).add(gap);
        }
        Map<String, Object> aggregates = new LinkedHashMap<String, Object>();
        for (String type : Arrays.asList("node-node", "node-enclosure", "enclosure-enclosure")) {
            aggregates.put(type, centers.containsKey(type) ? map("count", centers.get(type).size(),
                "minimum_center_distance", Collections.min(centers.get(type)), "median_center_distance", median(centers.get(type)),
                "minimum_rim_gap", Collections.min(gaps.get(type)), "median_rim_gap", median(gaps.get(type))) : "NOT_APPLICABLE");
        }
        GraphGeometry geometry = engine.computeHulls(p, frame.positions(), InitialSpacingFrameAssertions.metrics());
        EdgePaths paths = router.route(p, frame.positions(), geometry, visible);
        List<EdgePath> chords = new ArrayList<EdgePath>();
        for (ProjectedEdge edge : p.edges()) {
            LayoutPoint a = position(p, frame.positions(), edge.first()), b = position(p, frame.positions(), edge.second());
            chords.add(EdgePath.straight(geometry.edgeAttachment(edge.first(), b), geometry.edgeAttachment(edge.second(), a)));
        }
        List<EdgePath> routed = new ArrayList<EdgePath>();
        for (ProjectedEdge edge : p.edges()) routed.add(paths.path(edge.key()).get());
        return map("idle", frame.idle().idle(), "pairs", pairs, "aggregates", aggregates,
            "straight_chord_conflicts", conflicts(p, chords, geometry),
            "routed_conflicts", conflicts(p, routed, geometry),
            "published_detected", map("edge_to_node", frame.edgeConflictDiagnostics().edgeOverNodeDetected(),
                "edge_to_edge", frame.edgeConflictDiagnostics().crossingsDetected()),
            "residual_keys", frame.edgeConflictDiagnostics().conflictsResidual(), "positions", positions(frame.positions()));
    }

    private static Map<String, Object> conflicts(GraphProjection p, List<EdgePath> paths, GraphGeometry geometry) {
        int nodeConflicts = 0, edgeConflicts = 0;
        for (int i = 0; i < paths.size(); i++) {
            ProjectedEdge e = p.edges().get(i);
            List<LayoutPoint> samples = paths.get(i).samples();
            for (ProjectedNode n : p.nodes()) {
                ProjectedEndpointKey endpoint = endpoint(n.key());
                if (endpoint.equals(e.first()) || endpoint.equals(e.second())) continue;
                NodeGeometry node = geometry.nodes().get(n.key());
                double nearest = Double.POSITIVE_INFINITY;
                for (int j = 1; j < samples.size(); j++) {
                    LayoutPoint a = samples.get(j - 1), b = samples.get(j);
                    nearest = Math.min(nearest, Line2D.ptSegDist(a.x(), a.y(), b.x(), b.y(), node.center().x(), node.center().y()));
                }
                if (nearest < node.radius() + 3.0 - 1e-9) nodeConflicts++;
            }
            for (int j = i + 1; j < paths.size(); j++) if (EdgeRouter.properlyCross(paths.get(i), paths.get(j))) edgeConflicts++;
        }
        return map("edge_to_node", nodeConflicts, "edge_to_edge", edgeConflicts);
    }

    private static LayoutPoint position(GraphProjection p, LayoutPositions positions, ProjectedEndpointKey endpoint) {
        return endpoint.isNode() ? positions.nodes().get(endpoint.node().get())
            : positions.anchors().get(InitialSpacingConsumers.endpointHull(p, endpoint));
    }

    private static Map<String, Object> inputs(GraphProjection p) {
        List<Object> nodes = new ArrayList<Object>(), hulls = new ArrayList<Object>(), edges = new ArrayList<Object>(), pins = new ArrayList<Object>();
        for (ProjectedNode n : p.nodes()) nodes.add(map("key", CanonicalKeys.node(n.key()), "label", n.label().displayText(),
            "map", n.mapName(), "graph_group", n.graphGroup(), "radius", 8.0 * p.prominence().get(n.key()).scale()));
        for (ProjectedEnclosure e : p.enclosures()) {
            List<String> direct = new ArrayList<String>(), children = new ArrayList<String>(), labels = new ArrayList<String>();
            for (ProjectedNodeKey n : e.directNodes()) direct.add(CanonicalKeys.node(n));
            for (EnclosureHullKey h : e.directEnclosures()) children.add(CanonicalKeys.hull(h));
            for (SafeNodeLabel label : e.labels()) labels.add(label.displayText());
            hulls.add(map("key", CanonicalKeys.hull(e.hullKey()), "parent", e.parentHull().isPresent()
                ? CanonicalKeys.hull(e.parentHull().get()) : null, "direct_nodes", direct, "children", children,
                "labels", labels, "tier", e.boundaryTier().name(), "map_root", e.mapRoot()));
        }
        for (ProjectedEdge e : p.edges()) edges.add(map("key", CanonicalKeys.edge(e.key()),
            "first", CanonicalKeys.endpoint(e.first()), "second", CanonicalKeys.endpoint(e.second()),
            "arrows", Arrays.asList(e.arrowAtFirst(), e.arrowAtSecond())));
        for (PinProjection pin : p.pins()) pins.add(map("node", pin.projectedNode().isPresent()
            ? CanonicalKeys.node(pin.projectedNode().get()) : null, "active", pin.active(), "x", pin.x(), "y", pin.y()));
        return map("nodes", nodes, "enclosures", hulls, "relationships", edges, "pins", pins);
    }

    private static LabelPlacementRequest longRequest() throws Exception {
        Class<?> owner = Class.forName("org.freeplane.plugin.graph.canvas.ScreenLabelPlacementShould");
        Method longScene = owner.getDeclaredMethod("longScene"); longScene.setAccessible(true);
        List<?> scene = (List<?>) longScene.invoke(null);
        Method key = owner.getDeclaredMethod("key", String.class); key.setAccessible(true);
        Field name = scene.get(0).getClass().getDeclaredField("name"); name.setAccessible(true);
        Set<ProjectedEndpointKey> forced = Collections.singleton(endpoint((ProjectedNodeKey) key.invoke(null, name.get(scene.get(0)))));
        Method request = owner.getDeclaredMethod("request", List.class, double.class, Rectangle2D.class, Set.class, RenderingLevel.class);
        request.setAccessible(true);
        return (LabelPlacementRequest) request.invoke(null, scene, 1.63, new Rectangle2D.Double(0, 0, 1128, 364), forced, RenderingLevel.FULL);
    }

    private static GraphProjection wideProjection() throws Exception {
        LabelPlacementRequest request = longRequest();
        List<ProjectedNode> nodes = new ArrayList<ProjectedNode>(request.projection().nodes());
        for (String id : Arrays.asList("render-first", "render-second")) nodes.add(ProjectedNode.of(key(A, id),
            SafeNodeLabel.of(id, id), "Map", false));
        ProjectedEdge edge = nativeEdge(0, endpoint(key(A, "render-first")), endpoint(key(A, "render-second")), false);
        return projection(1L, nodes, Collections.<ProjectedEnclosure>emptyList(), Collections.singletonList(edge), Collections.<PinProjection>emptyList());
    }

    private static Map<String, Object> wideInputs() throws Exception {
        LabelPlacementRequest r = longRequest();
        return map("source", "ScreenLabelPlacementShould.longScene", "zoom", r.zoom(), "area", Arrays.asList(1128, 364),
            "positions", positions(r.positions()), "forced", CanonicalKeys.endpoint(r.forced().iterator().next()),
            "radius", 8.0, "stand_in", null, "theme", "LIGHT", "font", "Dialog 12", "edge_run_world", 20.0);
    }

    private static List<Object> wideLeaders() throws Exception {
        LabelPlacementRequest r = longRequest();
        Method place = ScreenLabelPlacement.class.getDeclaredMethod("place", LabelPlacementRequest.class,
            List.class, LabelFonts.class, List.class);
        place.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<PlacedLabel> labels = (List<PlacedLabel>) place.invoke(new ScreenLabelPlacement(), r, null,
            LabelFonts.from(GraphTheme.resolve(CanvasTheme.LIGHT)), Collections.<Rectangle2D>emptyList());
        List<Object> rows = new ArrayList<Object>();
        Map<String, Object> longest = null;
        double maximum = -1;
        for (PlacedLabel label : labels) if (label.leader().isPresent()) {
            LeaderLine line = label.leader().get();
            double length = distance(line.start(), line.end());
            assertThat(Double.isFinite(length)).isTrue();
            Method slotMethod = PlacedLabel.class.getDeclaredMethod("slot"); slotMethod.setAccessible(true);
            Object slot = slotMethod.invoke(label);
            assertThat(slot).isNotNull();
            Map<String, Object> row = map("slot", slot.toString(), "endpoint", CanonicalKeys.endpoint(label.endpoint()),
                "text", label.text(), "start", point(line.start()), "end", point(line.end()), "length_px", length);
            rows.add(row);
            if (length > maximum) { maximum = length; longest = row; }
        }
        assertThat(longest).isNotNull();
        rows.add(map("longest", longest));
        // The exact long-scene placement remains fixed; the accompanying two-node relationship
        // is drawn at y=70 with a 20 wu attachment run, independent of fresh settled coordinates.
        GraphProjection p = wideProjection();
        Map<ProjectedNodeKey, LayoutPoint> points = new LinkedHashMap<ProjectedNodeKey, LayoutPoint>(r.positions().nodes());
        points.put(key(A, "render-first"), LayoutPoint.of(-18, 70));
        points.put(key(A, "render-second"), LayoutPoint.of(18, 70));
        LayoutPositions positions = LayoutPositions.of(points, Collections.<EnclosureHullKey, LayoutPoint>emptyMap());
        GraphGeometry geometry = new GraphGeometryEngine().computeHulls(p, positions, InitialSpacingFrameAssertions.metrics());
        EdgePaths paths = new EdgeRouter().route(p, positions, geometry, ProjectedEndpointVisibility.visibleEndpoints(p.nodes(), p.enclosures()));
        assertThat(paths.byEdge()).hasSize(1);
        CanvasState state = CanvasState.of(1L, p, LayoutFrame.of(1L, positions, false, 0), geometry, paths, OperationalStatus.IDLE);
        BufferedImage image = new BufferedImage(1128, 364, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        Class<?> painter = Class.forName("org.freeplane.plugin.graph.canvas.GraphPainter");
        Constructor<?> constructor = painter.getDeclaredConstructor(); constructor.setAccessible(true);
        Method paint = painter.getDeclaredMethod("paint", Graphics2D.class, CanvasState.class, GraphPaintState.class,
            GraphViewport.class, Dimension.class, GraphTheme.class, List.class);
        paint.setAccessible(true);
        try { paint.invoke(constructor.newInstance(), graphics, state, GraphPaintState.empty(), GraphViewport.of(0, 0, 1.63),
            new Dimension(1128, 364), GraphTheme.resolve(CanvasTheme.LIGHT), labels); }
        finally { graphics.dispose(); }
        return rows;
    }

    private static Map<String, Object> runtime() {
        List<String> flags = new ArrayList<String>(ManagementFactory.getRuntimeMXBean().getInputArguments());
        return map("java_home", System.getProperty("java.home"), "version", System.getProperty("java.version"),
            "runtime", System.getProperty("java.runtime.version"), "vm", System.getProperty("java.vm.name"),
            "flags", flags, "max_heap", Runtime.getRuntime().maxMemory(), "os", System.getProperty("os.name"),
            "arch", System.getProperty("os.arch"));
    }

    private static Map<String, Object> positions(LayoutPositions positions) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        for (Map.Entry<ProjectedNodeKey, LayoutPoint> e : positions.nodes().entrySet()) values.put(CanonicalKeys.node(e.getKey()), point(e.getValue()));
        for (Map.Entry<EnclosureHullKey, LayoutPoint> e : positions.anchors().entrySet()) values.put(CanonicalKeys.hull(e.getKey()), point(e.getValue()));
        return values;
    }

    private static List<Double> point(LayoutPoint p) { return Arrays.asList(p.x(), p.y()); }
    private static double distance(LayoutPoint a, LayoutPoint b) { return Math.hypot(a.x() - b.x(), a.y() - b.y()); }
    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<Double>(values); Collections.sort(sorted);
        int n = sorted.size(); return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
    }
    private static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }
    private static String hash(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) result.append(String.format("%02x", b & 255));
        return result.toString();
    }
    private static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Number) {
            assertThat(Double.isFinite(((Number) value).doubleValue())).isTrue(); return value.toString();
        }
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Map) {
            List<String> items = new ArrayList<String>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) items.add(json(e.getKey()) + ":" + json(e.getValue()));
            return "{" + String.join(",", items) + "}";
        }
        if (value instanceof Iterable) {
            List<String> items = new ArrayList<String>();
            for (Object item : (Iterable<?>) value) items.add(json(item));
            return "[" + String.join(",", items) + "]";
        }
        StringBuilder text = new StringBuilder("\"");
        for (char c : value.toString().toCharArray()) {
            if (c == '"' || c == '\\') text.append('\\').append(c);
            else if (c < 32) text.append(String.format("\\u%04x", (int) c));
            else text.append(c);
        }
        return text.append('"').toString();
    }
}
