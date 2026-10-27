# Reported-workspace residual-fix acceptance — Task 8

**Recorded-input acceptance: PENDING.** The explicit external-data test failed, rather than skipping or changing the frozen expectations. The live set-theory map produces 204 projected source nodes and 11 graph groups, versus the archived 171 and 10. Its extra workspace disc is `1/ID_1271861869`; the workspace has 29 discs instead of 28. Neither the candidate nor delivery-base live run reached idle before the unchanged 600-second deadline. The current published paths are clear, but this does not establish the original 178-step replay or Goals 1/2 preservation on the recorded workspace.

Candidate production is the already-landed implementation at `828a2a2d37`, with Task 7 acceptance committed as `0d932104c7`. This lane changes tests and documentation only. Production, core/API, specification, design, plan, manifest, frozen fixtures and constants remain untouched; all 320 protected hashes match the pre-lane snapshot.

## Inputs, construction and reproducible command

The three original `.mm` files were read from `/home/henry/Documents/999.notebooks/01.math`. Their SHA-256 values still match the values taken before Task 7; no source map was edited. The archive does not supply corresponding original-file hashes, so matching historical input bytes cannot be certified.

| Input | SHA-256 |
| --- | --- |
| `Axiomatic_Set_Theory.mm` | `840358a03666d02f445e3aeb5ba84d9852e913318a3bf6c1776f50593aaff08c` |
| `Point-set_Topology.mm` | `86c34f802b5e67dee9c6cf4c6fe3d84d5f3d49c85e05cb9414588e9cebbb3fe8` |
| `Real_Anaylsis.mm` | `b1bad78b2db790164b78d80b9833050e7ef7b8df0d182f4f0239905469491206` |

[Replay source](../../../doc/edge-node-crossing/replay/RealWorkspaceCrossingProbe.java) stays outside `src/`. It retains the archived XML parser, snapshot filtering, map IDs, workspace construction, generation `1L`, no relationships, and the real worker submission/step loop. It reads live map contents and starts a fresh layout; it never injects archived coordinates. [The module acceptance test](../../src/test/java/org/freeplane/plugin/graph/probe/RealWorkspaceCrossingShould.java) pins every original metadata, correction, routing, identity and clearance expectation. Ordinary runs use `Assume` only when `GRAPH_REPORTED_MAP_ROOT` is absent; an explicitly configured root requires every map to exist.

To compile the standalone source only for the explicit replay, the measured run used this init script at `/tmp/edge-node-acceptance/replay-init.gradle`:
```groovy
allprojects { p ->
    if (p.path == ':freeplane_plugin_graph') {
        p.pluginManager.withPlugin('java') {
            p.sourceSets.test.java.srcDir(new File(p.rootDir, 'doc/edge-node-crossing/replay'))
        }
    }
}
```

With Zulu Java 21.0.8 and Gradle 9.0.0, from the integration worktree:
```bash
export JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS=-Djava.awt.headless=true
GRAPH_REPORTED_MAP_ROOT=/home/henry/Documents/999.notebooks/01.math \
  gradle -I /tmp/edge-node-acceptance/replay-init.gradle \
  :freeplane_plugin_graph:test \
  --tests org.freeplane.plugin.graph.probe.RealWorkspaceCrossingShould \
  --console=plain -PTestLoggingFull
```

The RED run before adding the probe returned exit 1 with `ClassNotFoundException: org.freeplane.plugin.graph.probe.RealWorkspaceCrossingProbe`. The completed explicit replay returned exit **1**, with **1 test / 1 failure / 0 skipped**, in 611.201 seconds overall (JUnit 601.719 seconds). The intended input assertion reports:
```text
Expecting actual:
  [7455, 204, 1445, 11]
to contain exactly (and in same order):
  [7455, 171, 1445, 10]
```

The initial init-script attempt accessed `sourceSets` before the Java plugin was applied and failed during configuration; the script above fixes that ordering. A foreground attempt exceeded the tool time window and is not acceptance evidence. The background run above completed and preserved its XML before the ordinary suite could replace Gradle outputs.

## Archived versus current replay

| Observation | Archived replay | Current live replay |
| --- | --- | --- |
| Set theory: source / projected / connectors / groups | 7455 / 171 / 1445 / 10 | 7455 / 204 / 1445 / 11 |
| Topology: source / projected / connectors / groups | 9025 / 247 / 1229 / 11 | unchanged |
| Analysis: source / projected / connectors / groups | 9834 / 150 / 1474 / 7 | unchanged |
| Workspace nodes / edges / enclosures / pins | 28 / 12 / 7 / 0 | 29 / 12 / 7 / 0 |
| Worker steps / idle / layout residual violations | 178 / true / 1 | 32388 / false / 1 |
| Conflicts detected: all / crossing / edge-over-node | 1 / 0 / 1 | 3 / 1 / 2 |
| Resolved: crossing / edge-over-node; conflict residual | 0 / 1; 0 | 1 / 2; 0 |
| Accepted rounds / verifications / repairs | 1 / 1 / 1 | 1 / 5 / 1 |
| Repair | TRANSLATION of `ID_1732260180`, batch 0 | MIRROR of `ID_1222855984`, batch 0 |
| Rounded repair dx / dy / magnitude | 1.177 / -3.016 / 3.238 | 19.585 / -17.686 / 26.389 |
| Verification work / index items / pair checks / subdivisions | 1207 / 300 / 84 / 0 | 2059 / 592 / 164 / 0 |
| Correction truncation / index / verification flags | false / false / false | unchanged |
| Routes: straight / bow / dogleg / fallback | 12 / 0 / 0 / 0 | unchanged |
| Routing exhausted / truncated | false / false | unchanged |
| Router obstacle / pair / index items | 8 / 0 / 2156 | 18 / 4 / 929 |
| Published paths violating clearance | 0 reported | 0 of 12, all 324 disc pairs checked |

The archive exposes 28 node-centre decimals and a rounded displacement, but no full defining/sample-path or repair-map signatures. Those rounded values cannot prove exact geometry preservation. [The current output](../../../doc/edge-node-crossing/replay/real-workspace-replay.txt) includes all node centres/radii, repair from/to maps, path defining/sample points and per-edge minimum surface clearances as hexadecimal doubles. The retained historical geometry section uses origin-based chord projections; its old “attachment” labels are not used as clearance evidence.

## Clearance audit and falsification

Every published sample segment is checked against every non-attached disc in `routedGeometry`, using `min(distance(centre, segment)) - actualRadius >= 3 - 1e-9`. Missing geometry and non-finite distances fail. The audit counts all 12 edges and `12 * (29 - 2) = 324` disc pairs on the actual input. The smallest measured surface clearance is **4.112147265472045 wu**.


```text
edges violating node clearance: 0 of 12
auditedEdges=12 auditedDiscPairs=324
REPLAY VERDICT: all published paths clear
```

This verdict describes the 12 paths published on the final, non-idle live frame. It does not promote that frame to the archived settled replay.

A disposable Java 8-targeted harness recompiles the current replay source and acceptance test, obtains one fresh live frame, and invokes the same `assertPublishedPathsClear` helper as the real acceptance method. First it reruns the unmodified audit as a control. It then replaces one path with a straight chord through a non-attached disc and requires the clearance assertion to fail. Finally it supplies empty violations with zero audit counts and requires the count assertion to fail. Both failure causes are checked as `AssertionError` with the intended message; neither is counted merely because a process exited unsuccessfully. The control/mutation driver returns exit **0**.


```text
AUDIT_CONTROL edges=12 discPairs=324 violations=0 PASS
MUTATION chord edge=n:m:00000000-0000-0000-0000-000000000001|p:ID_1133378501~n:m:00000000-0000-0000-0000-000000000001|p:ID_1387156674 disc=n:m:00000000-0000-0000-0000-000000000001|p:ID_822182441
MUTANT_CHORD edges=12 discPairs=324 violations=[n:m:00000000-0000-0000-0000-000000000001|p:ID_1133378501~n:m:00000000-0000-0000-0000-000000000001|p:ID_1387156674 shape=STRAIGHT disc=n:m:00000000-0000-0000-0000-000000000001|p:ID_822182441 shortfall=11.000000000000000wu] intended_assertion=PASS
MUTANT_NO_AUDIT edges=0 discPairs=0 violations=[] intended_assertion=PASS
```

An earlier whole-method chord mutation failed first on the changed source counts and is explicitly excluded as clearance-falsification evidence. The final acceptance test checks the counted clearance result before the archived input inventory, so input drift cannot hide a bad path. No assertion or expected value was relaxed. Disposable classes are removed after capture; the runner source and exact commands are retained with the evidence hashes below.

## Delivery-base comparison and its limit

A detached read-only comparison worktree at delivery base `bfaed5611d170b5b0b3dc93919685bd6c54035c5` supplied the original `EdgeRouter.java` and `EdgeConflictCorrection.java`; these are exactly the two production paths changed between delivery base and the candidate. Their source bytes were checked against `git show`, then compiled with `javac -source 8 -target 8` ahead of the shared runtime classpath. The same replay source and external maps were used. Worktree add, compile and removal returned exit **0**; the direct JUnit run returned exit **1** at the same archived input-count assertion. The temporary worktree was removed. The direct delivery-base JVM used `-Xmx4g`; the candidate Gradle worker and the fresh audit-control JVM used `-Xmx512m`. Together with the wall-clock stopping rule, this prevents treating the final live frames as a controlled settled-frame comparison.

[Full delivery-base output](../../../doc/edge-node-crossing/replay/real-workspace-delivery-base.txt) retains the full-precision signatures. Its terminal observations are:
```text
settle: steps=31680 idle=false residualViolations=2
  conflictsDetected=3 (crossings=1 edgeOverNode=2)
  crossingsResolved=1 edgeOverNodeResolved=2 residual=0
  rounds=3 verifications=8 repairs=6
  indexInsertedItems=1009 indexPairChecks=283 verificationWork=3718 subdivisions=0
  straights=12 bows=0 doglegs=0 fallbacks=0
  routeBudgetExhausted=false detectionTruncated=false obstacleChecks=19 pairChecks=4 indexItems=978
  edges violating node clearance: 0 of 12
  auditedEdges=12 auditedDiscPairs=324
REPLAY VERDICT: all published paths clear
```

Both live runs have the same 29 node keys and 12 edge keys. **0/29 node signatures and 0/12 path signatures are equal**. The candidate stops at step 32388; delivery base stops at step 31680, both non-idle. Delivery base applies six repair records (seven per-node move rows), whereas the candidate applies one. These independently wall-bounded, non-idle frames are not equivalent settled observations; their differing coordinates/repairs neither establish nor falsify the exact archived Goal 1/2 preservation contract. That comparison remains pending the archived map state. Frozen fixture preservation is covered separately by Task 7.

## Final module and bundle commands

| Command | Exit |
| --- | ---: |
| `gradle :freeplane_plugin_graph:test --console=plain -PTestLoggingFull` | 1 |
| `gradle :freeplane_plugin_graph:verifyGraphBundle --console=plain` | 0 |


```text
1455 tests completed, 4 failed, 19 skipped
BUILD FAILED in 17m 52s
```


```text
BUILD SUCCESSFUL in 8s
```

Parent XML totals were **1453 tests / 4 failures / 18 skipped**; final totals are **1455 / 4 / 19**. The two additional tests are the new Task 7 mode assertion and Task 8 external replay. The failure identities, failure types and messages match after normalizing the generated `initial-spacing-<temporary>` receipt paths. No new full-suite failure is attributed to this lane.

| Final full-suite class | Tests | Failures | Skipped |
| --- | ---: | ---: | ---: |
| `LayoutSettleLoopShould` | 51 | 0 | 0 |
| `EdgeRouterShould` | 30 | 0 | 0 |
| `EdgeConflictCorrectionShould` | 70 | 0 | 0 |
| `TypedForcesShould` | 16 | 0 | 0 |
| `GraphEdgeLengthSettleShould` | 1 | 0 | 0 |
| `PerformanceTripwiresShould` | 17 | 0 | 0 |
| `SettledReferenceFixtureShould` | 2 | 0 | 0 |
| `RealWorkspaceCrossingShould` | 1 | 0 | 1 |

The unchanged first-idle assertions also passed in the final module run:
```text
FIRST_IDLE_REGRESSION false 37 430781300
FIRST_IDLE_REGRESSION true 3103 200628008567
```

The ordinary full suite has no `GRAPH_REPORTED_MAP_ROOT` and skips the external test. That skip is not replay acceptance. The same inherited two `AccessibleGraphCanvasShould` headless `JFrame` failures and direct/suite-wrapper initial-spacing failures are recorded below; their parent-source and environment comparisons are in [Task 7 evidence](frozen-acceptance.md).


```text
AssertionError: Unexpected production difference: freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/EdgeRouter.java
```

| Previously reported failure | Evidence and attribution |
| --- | --- |
| `GraphEdgeLengthSettleShould.measureDeliveryBaseAndCandidateInFreshEquivalentJvms` | Parent/current isolated headful runs both fail the old headless precondition; the full headless parent and final candidate runs pass. Pre-existing environment precondition, not caused here. |
| `InitialSpacingAcceptanceShould.compareFrozenC0AndCandidateInSerialFreshJvms` | The historical headless driver rejects the already-landed `EdgeRouter.java` production difference; the parent fails identically. Pre-existing driver restriction. |
| `InitialSpacingShould` | Wraps the same failing driver, counted separately. Pre-existing. |
| `GraphWorkspaceWindowModelShould.f11RestoresBoundsAndExtendedStateFromMaximizedWindow` | Parent and candidate both pass under isolated Xvfb/Openbox; source/runtime unchanged. Reported failure not reproduced and not caused by this lane. |
| `LayoutSettleLoopShould.replayFailureStagesFailAfterCleanupAndEscapesExternalText` | Parent/current isolated runs and both full headless runs pass; source/runtime unchanged. Reported intermittent failure not reproduced and not caused by this lane. |

## Concerns and acceptance status

1. **Original replay acceptance is pending.** The current map state does not match the archive; obtaining the recorded 171-node/10-group set-theory state is necessary before the hard-coded 28-disc, 178-step, exact translation/counter assertions can pass. The user maps were left read-only.
2. **The current workspace does not settle inside the existing bound.** The published-path audit is valid for the measured final frame only. Neither its coordinates nor the independent delivery-base frame is a frozen settled substitute.
3. **The full module gate retains inherited failures.** This lane does not repair or suppress them. Task 7 focused preservation/performance gates and the actual Task 8 path audit do not imply full implementation acceptance.
4. **A2/A3 remain a declared search limit.** Task 5 records “A2/A3 ring-0-only veto is a search limit, not a defect. Test 7 (A1) carries Goal 4 on its own.” That classification is unchanged.

## Evidence and source SHA-256

- `doc/edge-node-crossing/replay/real-workspace-replay.txt` — `e0e41d4a51fe19f734f147af68238844780399b0e65ec8864ee184adc83a1be2`

- `doc/edge-node-crossing/replay/real-workspace-delivery-base.txt` — `ab9fd2cd4231cedf4ff4dae400aea2547304adb12ff47478b81a69b3e5b845d0`

- `doc/edge-node-crossing/replay/RealWorkspaceCrossingProbe.java` — `b3642e652c94fc48cf4d8c5cc79cc3c8456d8a99892acd82b1fe5f53a31fc3ca`

- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/probe/RealWorkspaceCrossingShould.java` — `e86c684e8373da1e04f7e610a6f62e8c53a9643d650a05c095b4fff7a445dfd6`

- `/tmp/edge-node-acceptance/replay-red.log` — `1fbf6e8905ee705a87a5a41a607755fb3345f15243a8e932fa4eb542eb7b7b1e`

- `/tmp/edge-node-acceptance/replay-green.log` — `88bdd1fbe63c8d7b1db4990452395bd016debe5cfaed510a1915d63477371919`

- `/tmp/edge-node-acceptance/replay-suite-receipts.json` — `7779df935877baca235d839b0c2f8a45f55aec31cbefc1098ae820b0b9c4b1a5`

- `/tmp/edge-node-acceptance/replay-baseline-receipts.json` — `9f3556ef9b736a99a8be8046ed9c1900222b2fe55b5d21c6621ff6a035579676`

- `/tmp/edge-node-acceptance/replay-production-provenance.json` — `bb1504aa0946443c6ef73a696ac32577008033964f329bbaf947fab50942001d`

- `/tmp/edge-node-acceptance/replay-precision-comparison.json` — `11447fad39dbf306f7cd98259d59ba30d012d5ede707cb8c582fdc3b2435ac2d`

- `/tmp/edge-node-acceptance/run-replay-suite.py` — `98c7da5d2677b966f72d5c2b5db131814ebf63305c4b45dde237861698f42e58`

- `/tmp/edge-node-acceptance/run-replay-baseline.py` — `f3e6cb96fe9f194ae283004fd2936e3f156032c7775f44c4041a8b30605535ae`

- `/tmp/edge-node-acceptance/run-replay-audit-controls.py` — `3c221e472c94aeb12a06766a5cf4d8010464dd70802fc61891b722f4dd203998`

- `/tmp/edge-node-acceptance/ReplayAuditMutations.java` — `9a46e3973374c14b8f9a95cb8945569fc24b48dca0448e01aa5eec90e5e94a71`

- `/tmp/edge-node-acceptance/replay-audit-control-receipts.json` — `70f4072bfd4b05515f933a105c88e9c6a6a947ed1c49f96f82b2debdfc1d558c`

- `/tmp/edge-node-acceptance/replay-audit-controls.log` — `a6c8b421a8ed2b9be5c9e26f1d9bc087155216762e3e0ea65fb3726c1eea43fd`

- `/tmp/edge-node-acceptance/final-suite-receipts.json` — `1c0f21e57f7fac2689996aa84a12fbc71f66ac6859c4f10c56489d6287fc3a4e`

- `/tmp/edge-node-acceptance/final-suite-comparison.json` — `8d6cb7163f08fd256db32dbf9365508d6625b82da162b640ed838ab87ffe12a4`

- `/tmp/edge-node-acceptance/final-suite.log` — `acf15d49c45e04894ff222a592ee30eb9f2764ab5ffd42fb4a3783819cabc5ee`

- `/tmp/edge-node-acceptance/final-bundle.log` — `a4d1a02e41b3bd9a7d8231fb080380d81f8b3a439dc57c1711acb55a307de231`

- `/tmp/edge-node-acceptance/replay-green-xml/TEST-org.freeplane.plugin.graph.probe.RealWorkspaceCrossingShould.xml` — `dec2a5051fdc04effcfad3b6345a82c409096530a75a7f45299cba9794a8832b`

- `/tmp/edge-node-acceptance/final-suite-xml/TEST-org.freeplane.plugin.graph.canvas.AccessibleGraphCanvasShould.xml` — `51e378f14fd874d127d0e6e1379591401f61c4d6aed8d1c8ffecda8a53239896`

- `/tmp/edge-node-acceptance/final-suite-xml/TEST-org.freeplane.plugin.graph.control.LayoutSettleLoopShould.xml` — `60b54d1f627a63a8f1708b6651cb061c14fca996d615e0c9b0fe3e0c0a037cea`

- `/tmp/edge-node-acceptance/final-suite-xml/TEST-org.freeplane.plugin.graph.geometry.EdgeRouterShould.xml` — `6487214d53349f10234de51fe515d776b4e3bd254bc811260e26bb35d4c199a5`

- `/tmp/edge-node-acceptance/final-suite-xml/TEST-org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould.xml` — `49467a108b274c5324e135ec657a3bca32e32513afe0e5d4e36213c154154f02`

- `/tmp/edge-node-acceptance/final-suite-xml/TEST-org.freeplane.plugin.graph.layout.TypedForcesShould.xml` — `bd95d18bb37a6527e3d3d4ad8f99e2c760f58883d17747586eeb343552128a1b`

- `/tmp/edge-node-acceptance/final-suite-xml/TEST-org.freeplane.plugin.graph.layout.graphstream.GraphEdgeLengthSettleShould.xml` — `de3ed10764d6f3417cfa285cad2b6143bccf5356b058b858d840d800e0630eca`

- `/tmp/edge-node-acceptance/final-suite-xml/TEST-org.freeplane.plugin.graph.layout.graphstream.InitialSpacingAcceptanceShould.xml` — `d376423f5c36d1a832da4dc1f8e31127035b75a6ffd606e7d74065e18e32f3a0`

- `/tmp/edge-node-acceptance/final-suite-xml/TEST-org.freeplane.plugin.graph.performance.PerformanceTripwiresShould.xml` — `1bcc982c0aaa017551566dd6209ecac6e20afeba84782c50b264a9fd84604983`

- `/tmp/edge-node-acceptance/final-suite-xml/TEST-org.freeplane.plugin.graph.performance.SettledReferenceFixtureShould.xml` — `3a2a12488f337d46cc01ad56caf404f0ebc5b73b18ae5adea5a4a03f778d3775`

- `/tmp/edge-node-acceptance/final-suite-xml/TEST-org.freeplane.plugin.graph.probe.RealWorkspaceCrossingShould.xml` — `aad0e320099be4f8e26b66b1ff922552367f6696d57715210cec6fcb0712cf2c`

- `/tmp/edge-node-acceptance/final-suite-xml/TEST-org.freeplane.plugin.graph.window.GraphWorkspaceWindowModelShould.xml` — `fc868acfe154d4929a19f0436bdb2df26bc02cf8473226341f48c6a7bfcd2857`


## Full replay diff against the archived output

All changed lines follow, with zero context.


```diff
--- doc/edge-node-crossing/probe/real-workspace-probe.txt
+++ doc/edge-node-crossing/replay/real-workspace-replay.txt
@@ -1 +1 @@
-map[1] Axiomatic_Set_Theory.mm      sourceNodes=  7455 projectedNodes= 171 connectors=1445 graphGroups=10
+map[1] Axiomatic_Set_Theory.mm      sourceNodes=  7455 projectedNodes= 204 connectors=1445 graphGroups=11
@@ -4,2 +4,34 @@
-projection: nodes=28 edges=12 enclosures=7 pins=0
-settle: steps=178 idle=true residualViolations=1
+projection: nodes=29 edges=12 enclosures=7 pins=0
+  ... step 1000
+  ... step 2000
+  ... step 3000
+  ... step 4000
+  ... step 5000
+  ... step 6000
+  ... step 7000
+  ... step 8000
+  ... step 9000
+  ... step 10000
+  ... step 11000
+  ... step 12000
+  ... step 13000
+  ... step 14000
+  ... step 15000
+  ... step 16000
+  ... step 17000
+  ... step 18000
+  ... step 19000
+  ... step 20000
+  ... step 21000
+  ... step 22000
+  ... step 23000
+  ... step 24000
+  ... step 25000
+  ... step 26000
+  ... step 27000
+  ... step 28000
+  ... step 29000
+  ... step 30000
+  ... step 31000
+  ... step 32000
+settle: steps=32388 idle=false residualViolations=1
@@ -8,28 +40,29 @@
-  ID_1133378501                              label=Fundation / Regularity                         center=LayoutPoint{x=104.46420667902956, y=-224.98453805640872} r=9.6 group=true
-  ID_822182441                               label=Replacement Scheme                             center=LayoutPoint{x=85.44637197392952, y=-171.213919652232} r=8.0 group=true
-  ID_130337169                               label=Axiom of Choice                                center=LayoutPoint{x=38.686598760115714, y=-190.87219907996854} r=8.0 group=true
-  ID_1901523076                              label=Theorem                                        center=LayoutPoint{x=62.1007476684317, y=-237.31087846818235} r=8.0 group=true
-  ID_1387156674                              label=Theorem                                        center=LayoutPoint{x=80.82661318591157, y=-248.85825741845753} r=8.0 group=true
-  ID_297617399                               label=Theorem: Equivalence of $\leqslant$ and $...   center=LayoutPoint{x=-48.564075160622956, y=-226.99190477383434} r=8.0 group=true
-  ID_215702338                               label=Theorem: $ON$ is well-ordered by $\in$         center=LayoutPoint{x=-69.314297361256, y=-175.6251405199732} r=8.0 group=true
-  ID_1887656421                              label=Theorem: initial segement                      center=LayoutPoint{x=-104.03334197218959, y=-185.80824711206662} r=8.0 group=true
-  ID_1222855984                              label=Theorem: $\bigcup\alpha\leqslant\alpha$        center=LayoutPoint{x=-86.91931900152834, y=-241.9541706754423} r=8.0 group=true
-  ID_1732260180                              label=Theorem                                        center=LayoutPoint{x=-63.74403616437911, y=-244.72090126759775} r=8.0 group=true
-  ID_8832923                                 label=synthetic basis                                center=LayoutPoint{x=72.14806255041611, y=2.287330526987308} r=8.0 group=true
-  ID_379163670                               label=analytic subbasis                              center=LayoutPoint{x=61.411732222969285, y=37.962643425551825} r=8.0 group=true
-  ID_1007737944                              label=Theorem                                        center=LayoutPoint{x=28.979107583160594, y=65.76607944656138} r=8.0 group=true
-  ID_1121122434                              label=Theorem: Comparison between Topologies         center=LayoutPoint{x=-9.106924386746634, y=70.781811456558} r=8.0 group=true
-  ID_845954544                               label=Theorem                                        center=LayoutPoint{x=-48.043796232606226, y=52.21036969085537} r=8.0 group=true
-  ID_626846666                               label=Theorem                                        center=LayoutPoint{x=-67.10495944993325, y=21.938948894707615} r=8.0 group=true
-  ID_801357044                               label=Theorem                                        center=LayoutPoint{x=-67.72638078532272, y=-18.86339907897916} r=8.0 group=true
-  ID_675241169                               label=Theorem I                                      center=LayoutPoint{x=-45.25948235692915, y=-53.73579569443794} r=8.0 group=true
-  ID_192579155                               label=Theorem II                                     center=LayoutPoint{x=-12.66973172580646, y=-69.18415891974112} r=8.0 group=true
-  ID_1955681225                              label=Theorem                                        center=LayoutPoint{x=30.451499009403825, y=-64.22102140846964} r=8.0 group=true
-  ID_43431859                                label=Theorem                                        center=LayoutPoint{x=61.10465422634267, y=-37.66520173144638} r=8.0 group=true
-  ID_295118693                               label=Lemma II                                       center=LayoutPoint{x=57.23077385749022, y=239.58716715471076} r=8.0 group=true
-  ID_1721568649                              label=Theorem                                        center=LayoutPoint{x=47.50276098490488, y=274.28645323541446} r=8.0 group=true
-  ID_309040602                               label=Theorem                                        center=LayoutPoint{x=-41.72104675149096, y=233.4548674053434} r=10.53594000115385 group=true
-  ID_1165913806                              label=Theorem                                        center=LayoutPoint{x=-40.763714615787414, y=271.0586070339917} r=8.0 group=true
-  ID_1763143138                              label=Theorem                                        center=LayoutPoint{x=-39.998245070825966, y=199.91460123363362} r=9.6 group=true
-  ID_1437543342                              label=Corllary                                       center=LayoutPoint{x=-12.872196010357943, y=204.99102482375477} r=8.0 group=true
-  ID_792798100                               label=Theorem                                        center=LayoutPoint{x=-0.1460685129971556, y=187.04539363059476} r=8.0 group=true
+  ID_1133378501                              label=Fundation / Regularity                         center=LayoutPoint{x=-55.115168602829904, y=-10.812506140648434} r=9.6 group=true
+  ID_822182441                               label=Replacement Scheme                             center=LayoutPoint{x=-46.27970327952893, y=11.071153345843905} r=8.0 group=true
+  ID_130337169                               label=Axiom of Choice                                center=LayoutPoint{x=-110.36300967027198, y=34.014939768659296} r=8.0 group=true
+  ID_1901523076                              label=Theorem                                        center=LayoutPoint{x=-87.74406646112077, y=-27.166767284586932} r=8.0 group=true
+  ID_1387156674                              label=Theorem                                        center=LayoutPoint{x=-66.38971078903909, y=-32.45746608816982} r=8.0 group=true
+  ID_297617399                               label=Theorem: Equivalence of $\leqslant$ and $...   center=LayoutPoint{x=69.4750324538572, y=38.26679713047491} r=8.0 group=true
+  ID_215702338                               label=Theorem: $ON$ is well-ordered by $\in$         center=LayoutPoint{x=47.475405038612045, y=38.39483458746975} r=8.0 group=true
+  ID_1887656421                              label=Theorem: initial segement                      center=LayoutPoint{x=18.820691344227743, y=6.661903478092506} r=8.0 group=true
+  ID_1222855984                              label=Theorem: $\bigcup\alpha\leqslant\alpha$        center=LayoutPoint{x=48.87828873589957, y=8.324022841367913} r=8.0 group=true
+  ID_1732260180                              label=Theorem                                        center=LayoutPoint{x=23.70621517965161, y=-14.788776158075324} r=8.0 group=true
+  ID_1271861869                              label=Theorem                                        center=LayoutPoint{x=70.51019694920244, y=-41.807991250439045} r=8.0 group=true
+  ID_8832923                                 label=synthetic basis                                center=LayoutPoint{x=-482.4716467700062, y=-17.64414174049249} r=8.0 group=true
+  ID_379163670                               label=analytic subbasis                              center=LayoutPoint{x=-490.8552350334099, y=34.86367958985779} r=8.0 group=true
+  ID_1007737944                              label=Theorem                                        center=LayoutPoint{x=-522.919712209071, y=63.25369216369731} r=8.0 group=true
+  ID_1121122434                              label=Theorem: Comparison between Topologies         center=LayoutPoint{x=-566.9814317020857, y=67.66406814615392} r=8.0 group=true
+  ID_845954544                               label=Theorem                                        center=LayoutPoint{x=-611.4869223391356, y=36.70588231076151} r=8.0 group=true
+  ID_626846666                               label=Theorem                                        center=LayoutPoint{x=-622.4290415461004, y=10.785120259984451} r=8.0 group=true
+  ID_801357044                               label=Theorem                                        center=LayoutPoint{x=-623.2101752925267, y=-11.201007868228436} r=8.0 group=true
+  ID_675241169                               label=Theorem I                                      center=LayoutPoint{x=-608.3994027023431, y=-45.03111897882039} r=8.0 group=true
+  ID_192579155                               label=Theorem II                                     center=LayoutPoint{x=-579.9904561419264, y=-67.33275638991059} r=8.0 group=true
+  ID_1955681225                              label=Theorem                                        center=LayoutPoint{x=-537.8962098363114, y=-71.8884937483162} r=8.0 group=true
+  ID_43431859                                label=Theorem                                        center=LayoutPoint{x=-503.3281767105738, y=-54.26360969614409} r=8.0 group=true
+  ID_295118693                               label=Lemma II                                       center=LayoutPoint{x=523.6617266024169, y=45.76372935093962} r=8.0 group=true
+  ID_1721568649                              label=Theorem                                        center=LayoutPoint{x=493.8749772974022, y=66.37067059589039} r=8.0 group=true
+  ID_309040602                               label=Theorem                                        center=LayoutPoint{x=437.33668279115227, y=-2.3782780686850193} r=10.53594000115385 group=true
+  ID_1165913806                              label=Theorem                                        center=LayoutPoint{x=432.7361456458552, y=35.86019669862408} r=8.0 group=true
+  ID_1763143138                              label=Theorem                                        center=LayoutPoint{x=446.6883888736204, y=-35.41049192832414} r=9.6 group=true
+  ID_1437543342                              label=Corllary                                       center=LayoutPoint{x=472.0057897543315, y=-25.80345869286478} r=8.0 group=true
+  ID_792798100                               label=Theorem                                        center=LayoutPoint{x=489.05578334554167, y=-39.70661367796414} r=8.0 group=true
@@ -52,3 +85,3 @@
-  conflictsDetected=1 (crossings=0 edgeOverNode=1)
-  crossingsResolved=0 edgeOverNodeResolved=1 residual=0
-  rounds=1 verifications=1 repairs=1
+  conflictsDetected=3 (crossings=1 edgeOverNode=2)
+  crossingsResolved=1 edgeOverNodeResolved=2 residual=0
+  rounds=1 verifications=5 repairs=1
@@ -56,2 +89,2 @@
-  repair TRANSLATION batch=0 node=ID_1732260180 move=(1.177,-3.016) |d|=3.238
-  indexInsertedItems=300 indexPairChecks=84 verificationWork=1207 subdivisions=0
+  repair MIRROR batch=0 node=ID_1222855984 move=(19.585,-17.686) |d|=26.389
+  indexInsertedItems=592 indexPairChecks=164 verificationWork=2059 subdivisions=0
@@ -61 +94 @@
-  routeBudgetExhausted=false detectionTruncated=false obstacleChecks=8 pairChecks=0 indexItems=2156
+  routeBudgetExhausted=false detectionTruncated=false obstacleChecks=18 pairChecks=4 indexItems=929
@@ -64,19 +97,26 @@
-  chord length between attachment points = 33.596 wu
-  required clearance = r + 3 = 11 wu; max bow apex = 0.25*L = 8.399 wu; max dogleg offset = 0.30*L = 10.079 wu
-  disc ID_1901523076 r=8.00  perpOffset=21.431  distToFirstAnchor=44.120  distToSecondAnchor=22.000  required=11.000  routable=true
-  chord length between attachment points = 44.120 wu
-  required clearance = r + 3 = 11 wu; max bow apex = 0.25*L = 11.030 wu; max dogleg offset = 0.30*L = 13.236 wu
-  disc ID_1387156674 r=8.00  perpOffset=16.319  distToFirstAnchor=33.596  distToSecondAnchor=22.000  required=11.000  routable=true
-  disc ID_297617399 r=8.00  perpOffset=40.826  distToFirstAnchor=153.041  distToSecondAnchor=111.145  required=11.000  routable=true
-  disc ID_1222855984 r=8.00  perpOffset=37.175  distToFirstAnchor=192.134  distToSecondAnchor=149.092  required=11.000  routable=true
-  disc ID_1732260180 r=8.00  perpOffset=28.044  distToFirstAnchor=169.362  distToSecondAnchor=126.063  required=11.000  routable=true
-  chord length between attachment points = 41.170 wu
-  required clearance = r + 3 = 11 wu; max bow apex = 0.25*L = 10.293 wu; max dogleg offset = 0.30*L = 12.351 wu
-  disc ID_822182441 r=8.00  perpOffset=3.262  distToFirstAnchor=186.317  distToSecondAnchor=145.155  required=11.000  routable=false
-  disc ID_130337169 r=8.00  perpOffset=1.941  distToFirstAnchor=135.596  distToSecondAnchor=94.432  required=11.000  routable=false
-  disc ID_1901523076 r=8.00  perpOffset=49.832  distToFirstAnchor=149.092  distToSecondAnchor=111.145  required=11.000  routable=true
-  disc ID_1732260180 r=8.00  perpOffset=11.000  distToFirstAnchor=23.340  distToSecondAnchor=23.340  required=11.000  routable=true
-  chord length between attachment points = 36.182 wu
-  required clearance = r + 3 = 11 wu; max bow apex = 0.25*L = 9.045 wu; max dogleg offset = 0.30*L = 10.854 wu
-  disc ID_822182441 r=8.00  perpOffset=39.324  distToFirstAnchor=190.041  distToSecondAnchor=154.824  required=11.000  routable=true
-  disc ID_130337169 r=8.00  perpOffset=45.027  distToFirstAnchor=142.810  distToSecondAnchor=109.072  required=11.000  routable=true
+  chord length between attachment points = 24.405 wu
+  required clearance = r + 3 = 11 wu; max bow apex = 0.25*L = 6.101 wu; max dogleg offset = 0.30*L = 7.322 wu
+  disc ID_822182441 r=8.00  perpOffset=2.273  distToFirstAnchor=23.600  distToSecondAnchor=47.949  required=11.000  routable=false
+  disc ID_1901523076 r=8.00  perpOffset=21.383  distToFirstAnchor=36.498  distToSecondAnchor=22.000  required=11.000  routable=true
+  chord length between attachment points = 36.498 wu
+  required clearance = r + 3 = 11 wu; max bow apex = 0.25*L = 9.125 wu; max dogleg offset = 0.30*L = 10.949 wu
+  disc ID_822182441 r=8.00  perpOffset=15.605  distToFirstAnchor=23.600  distToSecondAnchor=56.404  required=11.000  routable=true
+  disc ID_1387156674 r=8.00  perpOffset=14.298  distToFirstAnchor=24.405  distToSecondAnchor=22.000  required=11.000  routable=true
+  disc ID_297617399 r=8.00  perpOffset=11.951  distToFirstAnchor=133.909  distToSecondAnchor=170.292  required=11.000  routable=true
+  disc ID_215702338 r=8.00  perpOffset=1.979  distToFirstAnchor=113.781  distToSecondAnchor=150.275  required=11.000  routable=false
+  disc ID_1887656421 r=8.00  perpOffset=17.508  distToFirstAnchor=75.973  distToSecondAnchor=111.805  required=11.000  routable=true
+  disc ID_1222855984 r=8.00  perpOffset=29.490  distToFirstAnchor=105.740  distToSecondAnchor=141.157  required=11.000  routable=true
+  disc ID_1732260180 r=8.00  perpOffset=38.874  distToFirstAnchor=78.922  distToSecondAnchor=112.136  required=11.000  routable=true
+  chord length between attachment points = 36.343 wu
+  required clearance = r + 3 = 11 wu; max bow apex = 0.25*L = 9.086 wu; max dogleg offset = 0.30*L = 10.903 wu
+  disc ID_215702338 r=8.00  perpOffset=18.198  distToFirstAnchor=30.104  distToSecondAnchor=22.000  required=11.000  routable=true
+  disc ID_1887656421 r=8.00  perpOffset=23.822  distToFirstAnchor=30.104  distToSecondAnchor=59.705  required=11.000  routable=true
+  disc ID_1732260180 r=8.00  perpOffset=7.640  distToFirstAnchor=34.174  distToSecondAnchor=70.069  required=11.000  routable=false
+  disc ID_1271861869 r=8.00  perpOffset=46.234  distToFirstAnchor=54.600  distToSecondAnchor=80.081  required=11.000  routable=true
+  chord length between attachment points = 42.756 wu
+  required clearance = r + 3 = 11 wu; max bow apex = 0.25*L = 10.689 wu; max dogleg offset = 0.30*L = 12.827 wu
+  disc ID_1133378501 r=9.60  perpOffset=43.163  distToFirstAnchor=75.973  distToSecondAnchor=113.781  required=12.600  routable=true
+  disc ID_1387156674 r=8.00  perpOffset=37.025  distToFirstAnchor=93.761  distToSecondAnchor=134.109  required=11.000  routable=true
+  disc ID_297617399 r=8.00  perpOffset=16.414  distToFirstAnchor=59.705  distToSecondAnchor=22.000  required=11.000  routable=true
+  disc ID_1222855984 r=8.00  perpOffset=21.194  distToFirstAnchor=30.104  distToSecondAnchor=30.104  required=11.000  routable=true
+  disc ID_1732260180 r=8.00  perpOffset=18.002  distToFirstAnchor=22.000  distToSecondAnchor=58.254  required=11.000  routable=true
@@ -85,0 +126,46 @@
+  auditedEdges=12 auditedDiscPairs=324
+
+=== full-precision replay signatures (hexadecimal doubles) ===
+NODE n:m:00000000-0000-0000-0000-000000000001|p:ID_1133378501 centre=(-0x1.b8ebdd8435719p5,-0x1.5a000ce0bc4b6p3) radius=0x1.3333333333333p3
+NODE n:m:00000000-0000-0000-0000-000000000001|p:ID_822182441 centre=(-0x1.723cd512b1492p5,0x1.6246e361acd15p3) radius=0x1.0p3
+NODE n:m:00000000-0000-0000-0000-000000000001|p:ID_130337169 centre=(-0x1.b973b8ce97ccbp6,0x1.101e98bdce697p5) radius=0x1.0p3
+NODE n:m:00000000-0000-0000-0000-000000000001|p:ID_1901523076 centre=(-0x1.5ef9ec8ef241bp6,-0x1.b2ab142c157f6p4) radius=0x1.0p3
+NODE n:m:00000000-0000-0000-0000-000000000001|p:ID_1387156674 centre=(-0x1.098f10585748fp6,-0x1.03a8e3fafdbf5p5) radius=0x1.0p3
+NODE n:m:00000000-0000-0000-0000-000000000001|p:ID_297617399 centre=(0x1.15e66ee8576bdp6,0x1.32226688b0738p5) radius=0x1.0p3
+NODE n:m:00000000-0000-0000-0000-000000000001|p:ID_215702338 centre=(0x1.7bcda1282989fp5,0x1.33289f0944191p5) radius=0x1.0p3
+NODE n:m:00000000-0000-0000-0000-000000000001|p:ID_1887656421 centre=(0x1.2d218d3f39185p4,0x1.aa5ca067e1059p2) radius=0x1.0p3
+NODE n:m:00000000-0000-0000-0000-000000000001|p:ID_1222855984 centre=(0x1.8706bc3ea9121p5,0x1.0a5e65265aa14p3) radius=0x1.0p3
+NODE n:m:00000000-0000-0000-0000-000000000001|p:ID_1732260180 centre=(0x1.7b4ca849c8addp4,-0x1.d93da77f59899p3) radius=0x1.0p3
+NODE n:m:00000000-0000-0000-0000-000000000001|p:ID_1271861869 centre=(0x1.1a0a7111ad5fbp6,-0x1.4e76c41de0b8p5) radius=0x1.0p3
+NODE n:m:00000000-0000-0000-0000-000000000002|p:ID_8832923 centre=(-0x1.e278bdd7bc70dp8,-0x1.1a4e6791d675dp4) radius=0x1.0p3
+NODE n:m:00000000-0000-0000-0000-000000000002|p:ID_379163670 centre=(-0x1.eadaf0aee2e39p8,0x1.16e8d0d8454b9p5) radius=0x1.0p3
+NODE n:m:00000000-0000-0000-0000-000000000002|p:ID_1007737944 centre=(-0x1.0575b92131d89p9,0x1.fa078fc1d2a6cp5) radius=0x1.0p3
+NODE n:m:00000000-0000-0000-0000-000000000002|p:ID_1121122434 centre=(-0x1.1b7d9f8dd3dbap9,0x1.0ea8017ae82f9p6) radius=0x1.0p3
+NODE n:m:00000000-0000-0000-0000-000000000002|p:ID_845954544 centre=(-0x1.31be5378a123cp9,0x1.25a5a59ffc5d6p5) radius=0x1.0p3
+NODE n:m:00000000-0000-0000-0000-000000000002|p:ID_626846666 centre=(-0x1.3736ead558903p9,0x1.591fb48601ed7p3) radius=0x1.0p3
+NODE n:m:00000000-0000-0000-0000-000000000002|p:ID_801357044 centre=(-0x1.379ae70623ea3p9,-0x1.666ea80d88f43p3) radius=0x1.0p3
+NODE n:m:00000000-0000-0000-0000-000000000002|p:ID_675241169 centre=(-0x1.30331fa0b43fbp9,-0x1.683fbb4ea28c4p5) radius=0x1.0p3
+NODE n:m:00000000-0000-0000-0000-000000000002|p:ID_192579155 centre=(-0x1.21fec74450d92p9,-0x1.0d54be1750cddp6) radius=0x1.0p3
+NODE n:m:00000000-0000-0000-0000-000000000002|p:ID_1955681225 centre=(-0x1.0cf2b70100a7dp9,-0x1.1f8dd14e1edfcp6) radius=0x1.0p3
+NODE n:m:00000000-0000-0000-0000-000000000002|p:ID_43431859 centre=(-0x1.f75403638f393p8,-0x1.b21bdf667ec76p5) radius=0x1.0p3
+NODE n:m:00000000-0000-0000-0000-000000000003|p:ID_295118693 centre=(0x1.05d4b37512231p9,0x1.6e1c1e224a3f7p5) radius=0x1.0p3
+NODE n:m:00000000-0000-0000-0000-000000000003|p:ID_1721568649 centre=(0x1.eddffe831d15cp8,0x1.097b91129bc09p6) radius=0x1.0p3
+NODE n:m:00000000-0000-0000-0000-000000000003|p:ID_309040602 centre=(0x1.b55630d7e92p8,-0x1.306b6a6ee5e9ep1) radius=0x1.51266ba5325e2p3
+NODE n:m:00000000-0000-0000-0000-000000000003|p:ID_1165913806 centre=(0x1.b0bc740a820a7p8,0x1.1ee1aece85bdap5) radius=0x1.0p3
+NODE n:m:00000000-0000-0000-0000-000000000003|p:ID_1763143138 centre=(0x1.beb03a40d3214p8,-0x1.1b48affdfb64bp5) radius=0x1.3333333333333p3
+NODE n:m:00000000-0000-0000-0000-000000000003|p:ID_1437543342 centre=(0x1.d8017b6ff5817p8,-0x1.9cdaf78098a88p4) radius=0x1.0p3
+NODE n:m:00000000-0000-0000-0000-000000000003|p:ID_792798100 centre=(0x1.e90e47d13cc35p8,-0x1.3da725126e192p5) radius=0x1.0p3
+REPAIR MIRROR batch=0 node=n:m:00000000-0000-0000-0000-000000000001|p:ID_1222855984 from=(0x1.d4af55ee9519dp4,0x1.a02779691235cp4) to=(0x1.8706bc3ea9121p5,0x1.0a5e65265aa14p3)
+PATH n:m:00000000-0000-0000-0000-000000000001|p:ID_1133378501~n:m:00000000-0000-0000-0000-000000000001|p:ID_1387156674 shape=STRAIGHT defining=[(-0x1.dc669429f9edbp5,-0x1.353a29ba974aep4), (-0x1.f58d33268ad51p5,-0x1.95cbffe2cbdfap4)] samples=[(-0x1.dc669429f9edbp5,-0x1.353a29ba974aep4), (-0x1.f58d33268ad51p5,-0x1.95cbffe2cbdfap4)] minimumSurfaceClearance=0x1.11d72831f2c16p4
+PATH n:m:00000000-0000-0000-0000-000000000001|p:ID_1133378501~n:m:00000000-0000-0000-0000-000000000001|p:ID_1901523076 shape=STRAIGHT defining=[(-0x1.fd946ebfb045p5,-0x1.e3a6f821d9a93p3), (-0x1.425e5ab629befp6,-0x1.795032264942fp4)] samples=[(-0x1.fd946ebfb045p5,-0x1.e3a6f821d9a93p3), (-0x1.425e5ab629befp6,-0x1.795032264942fp4)] minimumSurfaceClearance=0x1.93195adc910ccp2
+PATH n:m:00000000-0000-0000-0000-000000000001|p:ID_1222855984~n:m:00000000-0000-0000-0000-000000000001|p:ID_297617399 shape=STRAIGHT defining=[(0x1.ab4c23199f678p5,0x1.dd4978ce80229p3), (0x1.03c3bb7adc411p6,0x1.facf433d4e265p4)] samples=[(0x1.ab4c23199f678p5,0x1.dd4978ce80229p3), (0x1.03c3bb7adc411p6,0x1.facf433d4e265p4)] minimumSurfaceClearance=0x1.465662d47ec0ap3
+PATH n:m:00000000-0000-0000-0000-000000000001|p:ID_1887656421~n:m:00000000-0000-0000-0000-000000000001|p:ID_215702338 shape=STRAIGHT defining=[(0x1.82ea6a57598eep4,0x1.932e4f104b946p3), (0x1.50e9329c194eap5,0x1.03a89f522d54bp5)] samples=[(0x1.82ea6a57598eep4,0x1.932e4f104b946p3), (0x1.50e9329c194eap5,0x1.03a89f522d54bp5)] minimumSurfaceClearance=0x1.a638c7ffa93dp3
+PATH n:m:00000000-0000-0000-0000-000000000002|p:ID_192579155~n:m:00000000-0000-0000-0000-000000000002|p:ID_675241169 shape=STRAIGHT defining=[(-0x1.25243d448382ep9,-0x1.f3249df2253e1p5), (-0x1.2d0da9a08195fp9,-0x1.8fc4998b1ee9dp5)] samples=[(-0x1.25243d448382ep9,-0x1.f3249df2253e1p5), (-0x1.2d0da9a08195fp9,-0x1.8fc4998b1ee9dp5)] minimumSurfaceClearance=0x1.2121ace971ad4p5
+PATH n:m:00000000-0000-0000-0000-000000000002|p:ID_626846666~n:m:00000000-0000-0000-0000-000000000002|p:ID_845954544 shape=STRAIGHT defining=[(-0x1.35a8ad90859f6p9,0x1.227c46ffeb8fdp4), (-0x1.334c90bd74149p9,0x1.d55ede830e21bp4)] samples=[(-0x1.35a8ad90859f6p9,0x1.227c46ffeb8fdp4), (-0x1.334c90bd74149p9,0x1.d55ede830e21bp4)] minimumSurfaceClearance=0x1.59cff595d069cp4
+PATH n:m:00000000-0000-0000-0000-000000000003|p:ID_1165913806~n:m:00000000-0000-0000-0000-000000000003|p:ID_309040602 shape=STRAIGHT defining=[(0x1.b1b11669542d8p8,0x1.beadfa8c0d56cp4), (0x1.b41402621eb24p8,0x1.02a199705110dp3)] samples=[(0x1.b1b11669542d8p8,0x1.beadfa8c0d56cp4), (0x1.b41402621eb24p8,0x1.02a199705110dp3)] minimumSurfaceClearance=0x1.1958837f7c398p5
+PATH n:m:00000000-0000-0000-0000-000000000003|p:ID_1437543342~n:m:00000000-0000-0000-0000-000000000003|p:ID_1763143138 shape=STRAIGHT defining=[(0x1.d086b46080291p8,-0x1.ca4461eb88855p4), (0x1.c7a9f5ecf98b5p8,-0x1.000970243846ap5)] samples=[(0x1.d086b46080291p8,-0x1.ca4461eb88855p4), (0x1.c7a9f5ecf98b5p8,-0x1.000970243846ap5)] minimumSurfaceClearance=0x1.2e8e3d7434298p4
+PATH n:m:00000000-0000-0000-0000-000000000003|p:ID_1437543342~n:m:00000000-0000-0000-0000-000000000003|p:ID_309040602 shape=STRAIGHT defining=[(0x1.d16088d05ec93p8,-0x1.5531803aa1d7p4), (0x1.be110ee98eabep8,-0x1.08dc89357c6e7p3)] samples=[(0x1.d16088d05ec93p8,-0x1.5531803aa1d7p4), (0x1.be110ee98eabep8,-0x1.08dc89357c6e7p3)] minimumSurfaceClearance=0x1.911a365c551ebp3
+PATH n:m:00000000-0000-0000-0000-000000000003|p:ID_1721568649~n:m:00000000-0000-0000-0000-000000000003|p:ID_295118693 shape=STRAIGHT defining=[(0x1.f4743b430dfcap8,0x1.ee8dadc03a218p5), (0x1.028a951519afap9,0x1.92859287479f1p5)] samples=[(0x1.f4743b430dfcap8,0x1.ee8dadc03a218p5), (0x1.028a951519afap9,0x1.92859287479f1p5)] minimumSurfaceClearance=0x1.02178445a3b3ap6
+PATH n:m:00000000-0000-0000-0000-000000000003|p:ID_1763143138~n:m:00000000-0000-0000-0000-000000000003|p:ID_309040602 shape=STRAIGHT defining=[(0x1.bc12c571681d1p8,-0x1.a2c6c8b9ae249p4), (0x1.b834ea3625ddap8,-0x1.90814ed5510cdp3)] samples=[(0x1.bc12c571681d1p8,-0x1.a2c6c8b9ae249p4), (0x1.b834ea3625ddap8,-0x1.90814ed5510cdp3)] minimumSurfaceClearance=0x1.3ef56a65837bp4
+PATH n:m:00000000-0000-0000-0000-000000000003|p:ID_1763143138~n:m:00000000-0000-0000-0000-000000000003|p:ID_792798100 shape=STRAIGHT defining=[(0x1.c83d4a0ed129ep8,-0x1.23082725e4d35p5), (0x1.e118baa593bc2p8,-0x1.373241c680e7ap5)] samples=[(0x1.c83d4a0ed129ep8,-0x1.23082725e4d35p5), (0x1.e118baa593bc2p8,-0x1.373241c680e7ap5)] minimumSurfaceClearance=0x1.072d6bb96272ep2
+REPLAY VERDICT: all published paths clear
```
