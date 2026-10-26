# Reproduction

Both probes are test-only investigation sources. They are kept here, outside `src/`, so they never join the
plugin bundle or the CI test set. To run one, copy it to
`freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/probe/`, add the temporary Gradle init script
below, and delete the copy afterwards.

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/21.0.8-zulu"
export PATH="$JAVA_HOME/bin:$PATH"
cat > /tmp/probe-init.gradle <<'GRADLE'
allprojects { p ->
    if (p.path == ':freeplane_plugin_graph') {
        p.tasks.register('probeRun', JavaExec) {
            dependsOn p.tasks.named('testClasses')
            classpath = p.sourceSets.test.runtimeClasspath
            mainClass = System.getProperty('probeMain',
                'org.freeplane.plugin.graph.probe.RealWorkspaceCrossingProbe')
            maxHeapSize = '4g'
            jvmArgs = ['-Djava.awt.headless=true']
        }
    }
}
GRADLE
gradle -I /tmp/probe-init.gradle :freeplane_plugin_graph:probeRun --console=plain -q
```

`RealWorkspaceCrossingProbe` reads
`/home/henry/Documents/999.notebooks/01.math/{math.fpg}`'s three registered maps and reports the projection
sizes, the C2/C1 conflict counts, the accepted repairs, the router classifications and a clearance audit of
every published path. `RouterClearanceProbe` needs no input files.

Recorded outputs are the two `.txt` files beside this note. Both probes are deterministic: two consecutive
runs produced byte-identical stdout.

`RouterClearanceProbe` obstacle coordinates, where `L` is the anchor chord: the midpoint-of-drawn-chord variant
is `xN = -L/2 + 8 + (L - 16)/2`, `yN = 0`; the quarter variant is `xN = -L/2 + 8 + 0.25*(L - 16)`, `yN = 0`.
Clearance is printed as `distance from N's centre - N's radius`, measured on the published sampled polyline.

Caveat recorded by design review round 4: `RouterClearanceProbe.distance` also evaluates a dense quadratic that
is measured from the origin rather than from the obstacle centre. For every row in the recorded output the
selected path is either the straight fallback, which passes through the obstacle, or a midpoint-symmetric bow
about a centred obstacle, so the reported figures are correct for those rows; the dense term must be fixed
before the probe is reused for off-centre obstacles.
