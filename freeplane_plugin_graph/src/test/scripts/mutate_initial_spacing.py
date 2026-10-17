#!/usr/bin/env python3
from pathlib import Path
import hashlib, json, os, re, subprocess, sys
root=Path(sys.argv[1]).resolve(); out=Path(sys.argv[2]).resolve()
out.mkdir(parents=True,exist_ok=False)
java=Path('/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin')
env=dict(os.environ,JAVA_HOME=str(java.parent),JAVA_TOOL_OPTIONS='-Djava.awt.headless=true')
env['PATH']=str(java)+os.pathsep+env['PATH']
records=[]
def run(argv,name,timeout=180):
    p=subprocess.run([str(x) for x in argv],cwd=root,env=env,text=True,capture_output=True,timeout=timeout)
    (out/(name+'.stdout')).write_text(p.stdout);(out/(name+'.stderr')).write_text(p.stderr)
    records.append(dict(name=name,argv=[str(x) for x in argv],cwd=str(root),exit=p.returncode))
    return p
init=out/'classpath.init.gradle'
init.write_text('''allprojects { p ->
  afterEvaluate {
    if(p.path == ':freeplane_plugin_graph') {
      tasks.register('printSpacingTestClasspath') {
        doLast { println('SPACING_TEST_CP=' + sourceSets.test.runtimeClasspath.asPath) }
      }
    }
  }
}
''')
built=run(['gradle',':freeplane_plugin_graph:testClasses',':freeplane_plugin_graph:printSpacingTestClasspath','-I',init,'--console=plain'],'classpath')
assert built.returncode==0,'classpath build failed'
lines=[x.removeprefix('SPACING_TEST_CP=') for x in built.stdout.splitlines() if x.startswith('SPACING_TEST_CP=')]
assert len(lines)==1,'missing/duplicate classpath'
cp=lines[0]
tests=['org.freeplane.plugin.graph.layout.graphstream.InitialSpacingShould',
       'org.freeplane.plugin.graph.control.InitialSpacingLifecycleShould']
control=run([java/'java','-cp',cp,'org.junit.runner.JUnitCore',*tests],'control',120)
assert control.returncode==0 and re.search(r'OK \(\d+ tests?\)',control.stdout),'clean JUnit control failed'
base='freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/'
engine=base+'layout/graphstream/GraphStreamLayoutEngine.java'
box=base+'layout/graphstream/TypedSpringBox.java'
loop=base+'control/LayoutSettleLoop.java'
def replace_once(text,before,after):
    assert text.count(before)==1,repr(before)
    return text.replace(before,after,1)
def old_containment(text):
    # Anchored on the containment rest call site: the bare
    # sizes.contentRingRadius(enclosure.hullKey()))); substring also occurs at the
    # endpoint-radius registration, so the ForceKind prefix disambiguates it.
    text=replace_once(text,'''ForceKind.CONTAINMENT, false,
                        sizes.contentRingRadius(enclosure.hullKey())));''',
                      '''ForceKind.CONTAINMENT, false,
                        oldContainmentRing(projection, enclosure)));''')
    helper='''    private static double oldContainmentRing(GraphProjection projection, ProjectedEnclosure enclosure) {
        int n = enclosure.directNodes().size();
        if (n <= 1) return 0.0;
        double maximum = 0.0;
        for (ProjectedNodeKey node : enclosure.directNodes())
            maximum = Math.max(maximum, 8.0 * projection.prominence().get(node).scale());
        return (2.0 * maximum + 6.0) / (2.0 * Math.sin(Math.PI / n));
    }

'''
    return replace_once(text,'    private static double radiusOf(',helper+'    private static double radiusOf(')
mutants=[
 ('gap-six',engine,lambda t:replace_once(t,'PREFERRED_NODE_GAP = 24.0','PREFERRED_NODE_GAP = 6.0'),
  'pinLiteralFreshRingsAndFootprints'),
 ('old-containment',engine,old_containment,'registerAllEndpointAndMapVariantsAndConsumeTheirActualRests'),
 # Control (c): the forbidden widened relationship term must be rejected by the
 # frozen 22/34/46 registrations and the 23.6 mixed-prominence registrations.
 ('relationship-preferred',engine,lambda t:replace_once(t,
  '+ NodeSeparationProjection.MIN_GAP));','+ PREFERRED_NODE_GAP));'),
  'registerAllEndpointAndMapVariantsAndConsumeTheirActualRests'),
 ('ignore-rest',box,lambda t:replace_once(t,'(distance - link.restLength) * ATTRACTION_FACTOR','distance * ATTRACTION_FACTOR'),
  'registerAllEndpointAndMapVariantsAndConsumeTheirActualRests'),
 ('reseed-survivors',engine,lambda t:replace_once(t,'            state.radius = desired.radius;',
  '''            if (lastRequest != null) {
                Position replacement = seeds.positionFor(desired);
                state.x = replacement.x;
                state.y = replacement.y;
                springBox.setParticlePosition(desired.id, state.x, state.y);
            }
            state.radius = desired.radius;'''), 'retainAllSurvivorsAcrossRealTopologyAndProminenceChanges'),
 ('restart-recreates',loop,lambda t:replace_once(t,
  'public synchronized void restart() {\n            worker.restart();',
  'public synchronized void restart() {\n            reset();\n            worker.restart();'),
  'healthyRestartRetainsRawCoordinatesAndResetReplacesTheRealWorker'),
 ('reset-preserves',loop,lambda t:replace_once(t,'            worker.close();\n            worker = new LayoutWorker(calibration);',
  '            worker.restart();'),'healthyRestartRetainsRawCoordinatesAndResetReplacesTheRealWorker')]
source_hashes={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in [engine,box,loop]}
try:
    for name,path,mutate,assertion in mutants:
        d=out/name; source=d/'source'/Path(path).name; classes=d/'classes'
        source.parent.mkdir(parents=True);classes.mkdir()
        source.write_text(mutate((root/path).read_text()))
        compiled=run([java/'javac','--release','8','-cp',cp,'-d',classes,source],name+'-compile')
        assert compiled.returncode==0,name+': compile failure is not a killed mutant'
        result=run([java/'java','-cp',str(classes)+os.pathsep+cp,'org.junit.runner.JUnitCore',*tests],name+'-test',120)
        text=result.stdout+result.stderr
        assert result.returncode!=0 and 'FAILURES!!!' in text and assertion in text,name+': expected assertion did not fail'
        assert 'AssertionError' in text or 'ComparisonFailure' in text,name+': failure was not an assertion'
        for bad in ['NoSuchMethodError','NoClassDefFoundError','ClassNotFoundException','VerifyError','UnsupportedClassVersionError']:
            assert bad not in text,name+': '+bad
        records[-1].update(expectedAssertion=assertion,mutantSha256=hashlib.sha256(source.read_bytes()).hexdigest(),killed=True)
finally:
    assert source_hashes=={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in source_hashes},'active source changed'
    (out/'mutations.json').write_text(json.dumps(dict(commands=records,activeSourceHashes=source_hashes),indent=2)+'\n')
print('PASS: clean control and all seven isolated mutation assertions, including control (c)')
