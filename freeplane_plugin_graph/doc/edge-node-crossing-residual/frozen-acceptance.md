# Frozen residual-fix acceptance — Task 7

Measured against source commit `828a2a2d37` plus the test-only mode assertion, using Zulu Java 21.0.8 and Gradle 9.0.0. All commands run from the integration worktree with `JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu` and `JAVA_TOOL_OPTIONS=-Djava.awt.headless=true`, except the explicitly identified display comparisons. `GRAPH_REGENERATE_SETTLED_FIXTURE` remained unset. No runtime, calibration, historical expectation, specification or plan was edited.

## Focused command receipts

Exact commands and exits are retained in `/tmp/edge-node-acceptance/vector-receipts.json` and transcribed below.

| Gate | Exit | Parsed JUnit result |
| --- | ---: | --- |
| `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.geometry.EdgeRouterShould --console=plain -PTestLoggingFull` | 0 | 30 tests / 0 failures / 0 skipped |
| `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould --console=plain -PTestLoggingFull` | 0 | 70 tests / 0 failures / 0 skipped |
| `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.performance.SettledReferenceFixtureShould --console=plain -PTestLoggingFull` | 0 | 2 tests / 0 failures / 0 skipped |
| `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.layout.TypedForcesShould --console=plain -PTestLoggingFull` | 0 | 16 tests / 0 failures / 0 skipped |
| `gradle :freeplane_plugin_graph:test --tests org.freeplane.plugin.graph.performance.PerformanceTripwiresShould --console=plain -PTestLoggingFull` | 0 | 17 tests / 0 failures / 0 skipped |
| `gradle :freeplane_plugin_graph:verifyGraphBundle --console=plain` | 0 | Java 8 bytecode / OSGi gate |

The final amended method also ran through direct JUnit against Gradle's runtime classpath to expose the measurements without adding another sampling pass to the ordinary test suite:

```text
JUNIT tests=1 failures=0 ignored=0 runtimeMs=12995
```

The test consumes the existing `runReferenceFixturesOnce()` observations; it does not rerun either diagnostic. Its direct zero-exhaustion check distinguishes an actual exhausted raw observation before checking route classification.

## Immutable inputs and named preservation checks

```text
e9e988b7967ea6bb4f503dfb4c5af7d3e74a3328e28e0987e71ff0f0b8ac0578  settled-reference.csv
f61c1d906fec2637e51ad9365e67808d82e51c81c606960e34d4eaa22937d251  edge-node-crossing-residual-legacy-paths.txt
f94170a4c47f0c86d5340fd5952bfb56dc82536dc245f49d9b2781a48d323fe5  edge-node-crossing-residual-a2-a3-search.txt
```

The immutable CSV retains 25 node rows and no pin/enclosure rows. `SettledReferenceFixtureShould.theCommittedLedgerIsTheDeterministicCompilationOfTheRecalibratedProjection` verifies compiler byte equality, with no regeneration. `EdgeRouterShould.preservesTheSettledReferenceAndNamedLadderFixtures` verifies the captured classifications, defining points and sample pins byte for byte, including the settled `21/1/0/0` routing result. The successful E1/E2/E3 tests are `repairsNineIndependentObstructionsInBoundedBatches`, `repairsOnTheOppositeSideWhenTheNearestSideBreaksTheGap`, and `repairsTheBlockedColumnWithTheFirstFeasibleDiagonal`. `stopsAtFourAcceptedRoundsWithoutClaimingExhaustion` retains four rounds, four verifications, 32 repairs and one residual.

`TypedForcesShould.settleRelationshipFreeNestedBoundaryProjectionToIdle` and `twoMapWorkspaceSettlesToIdle` ran unchanged. The actual focused-run output is:

```text
FIRST_IDLE_REGRESSION false 37 630091984
FIRST_IDLE_REGRESSION true 3103 196946435767
```

The exact two-map expected first-idle step remains `3103`; the independent initial-spacing step limit remains `6000`, with the existing 600-second bound. These are observed first-idle steps, not a promise of persistent idle.

The independent same-source diagnostic reports actual counters below. Tuple meanings are explicit in each row; all three flags are detection truncation / index exhaustion / verification exhaustion.

```text
CORRECTION nineObstructionsFixture rounds=2 verifications=2 repairs=9 detected=9 resolved=9 work=1755 residual=[] flags=false/false/false
CORRECTION oppositeSideFixture rounds=1 verifications=2 repairs=1 detected=1 resolved=1 work=30 residual=[] flags=false/false/false
CORRECTION blockedColumnFixture rounds=1 verifications=3 repairs=1 detected=1 resolved=1 work=252 residual=[] flags=false/false/false
CORRECTION fullyBlockedFixture rounds=0 verifications=30 repairs=0 detected=1 resolved=0 work=2160 residual=[n:m:00000000-0000-0000-0000-000000000001|p:A~n:m:00000000-0000-0000-0000-000000000001|p:B|n:m:00000000-0000-0000-0000-000000000001|p:N] flags=false/false/false
CORRECTION duplicateChordFixture rounds=0 verifications=30 repairs=0 detected=4 resolved=0 work=3300 residual=[n:m:00000000-0000-0000-0000-000000000001|p:A~n:m:00000000-0000-0000-0000-000000000001|p:B|n:m:00000000-0000-0000-0000-000000000001|p:C, n:m:00000000-0000-0000-0000-000000000001|p:A~n:m:00000000-0000-0000-0000-000000000001|p:B|n:m:00000000-0000-0000-0000-000000000001|p:E, n:m:00000000-0000-0000-0000-000000000001|p:A~n:m:00000000-0000-0000-0000-000000000001|p:B|n:m:00000000-0000-0000-0000-000000000001|p:N, n:m:00000000-0000-0000-0000-000000000001|p:C~n:m:00000000-0000-0000-0000-000000000001|p:E|n:m:00000000-0000-0000-0000-000000000001|p:N] flags=false/false/false
CORRECTION crowdedRowsFixture rounds=4 verifications=4 repairs=32 detected=33 resolved=32 work=43659 residual=[n:m:00000000-0000-0000-0000-000000000001|p:A9~n:m:00000000-0000-0000-0000-000000000001|p:B9|n:m:00000000-0000-0000-0000-000000000001|p:N9] flags=false/false/false
```

Task 5 conditional outcome, verbatim: **A2/A3 ring-0-only veto is a search limit, not a defect. Test 7 (A1) carries Goal 4 on its own.** The fully blocked and duplicate-chord failed-round controls cost `30/2160` and `30/3300` (verifications/work), respectively, with unchanged residuals and no accepted repairs. This is measured extra search work, not a zero-cost preservation claim.

## Settled window: strict measurement, no exhausted substitutions

```text
SETTLED_SAMPLES=300
SETTLED_FLAGS {edgeDiagnostics.detectionTruncated=0, edgeDiagnostics.verificationBudgetExhausted=0, edgeDiagnostics.indexBudgetExhausted=0, routeDiagnostics.routeBudgetExhausted=0, routeDiagnostics.detectionTruncated=0}
RAW_FLAGS {edgeDiagnostics.detectionTruncated=8, edgeDiagnostics.verificationBudgetExhausted=0, edgeDiagnostics.indexBudgetExhausted=0, routeDiagnostics.routeBudgetExhausted=8, routeDiagnostics.detectionTruncated=8}
SETTLED_ROUTES 21/1/0/0
```

Actual CSV values, in nanoseconds:

```csv
scenario,stage,warmupCount,measuredCount,p50Nanos,p95Nanos,p99Nanos,maxNanos,normalThresholdNanos,strictThresholdNanos,failureCount,discardCount,pass
reference-2000-5000,snapshot,0,0,-1,-1,-1,-1,-1,-1,0,0,false
reference-2000-5000,projection,0,0,-1,-1,-1,-1,-1,-1,0,0,false
reference-2000-5000,diff,0,0,-1,-1,-1,-1,-1,-1,0,0,false
reference-2000-5000,mutation,0,0,-1,-1,-1,-1,-1,-1,0,0,false
reference-2000-5000,force,0,0,-1,-1,-1,-1,250000000,50000000,0,0,false
reference-2000-5000,correction,0,0,-1,-1,-1,-1,-1,-1,0,0,false
reference-2000-5000,separation,0,0,-1,-1,-1,-1,-1,-1,0,0,false
reference-2000-5000,hull,0,0,-1,-1,-1,-1,-1,-1,0,0,false
reference-2000-5000,edge-conflict,400,300,232819,410977,561643,861274,-1,25000000,0,0,true
reference-2000-5000,edge-routing,400,300,720810,1178862,1595085,3864645,-1,10000000,0,0,true
reference-2000-5000,placement,0,0,-1,-1,-1,-1,-1,-1,0,0,false
reference-2000-5000,full-worker,400,300,846079,1065007,1205524,3492610,500000000,100000000,0,0,true
reference-2000-5000,edt-swap,0,0,-1,-1,-1,-1,10000000,2000000,0,0,false
reference-2000-5000,repaint,0,0,-1,-1,-1,-1,-1,-1,0,0,false
reference-2000-5000,accepted-batch-first-frame,400,300,3033374,4392598,4963006,6182667,750000000,150000000,0,0,true
```

The force/full-worker p95 and p99 thresholds remain 50,000,000/100,000,000 ns; accepted-batch-first-frame p95/p99 remain 150,000,000/300,000,000 ns. Edge-conflict and edge-routing p95 remain 25,000,000 and 10,000,000 ns. Both added stages retain 300 measured samples and zero discards. Stages with no measurement in this particular diagnostic retain `-1` values rather than being promoted as measured passes. `exposeTheCanonicalStageOrderAndThresholds` retains `edge-conflict` and `edge-routing` immediately after `hull`, before `placement`.

## Raw A7b frames: bounded, exhausted flags visible

The complete-frame bound remains exactly **5,000,000,000 ns**, independently of the strict settled thresholds. The measured maximum is **4536184846 ns** over **8 frames**. Raw samples are not included in the settled sample list.

| Raw frame | Complete-frame ns | Edge index items/pairs/verification work | Router index items/obstacle/pair checks | Edge truncated/index/work flags | Router budget/truncated flags |
| --- | ---: | --- | --- | --- | --- |
| 0 | 4536184846 | 262144/0/0 | 167155/131072/131072 | true/false/false | true/true |
| 1 | 783492406 | 262144/0/0 | 167155/131072/131072 | true/false/false | true/true |
| 2 | 675065728 | 262144/0/0 | 167155/131072/131072 | true/false/false | true/true |
| 3 | 597216150 | 262144/0/0 | 167155/131072/131072 | true/false/false | true/true |
| 4 | 760740685 | 262144/0/0 | 167155/131072/131072 | true/false/false | true/true |
| 5 | 651542807 | 262144/0/0 | 167155/131072/131072 | true/false/false | true/true |
| 6 | 748520034 | 262144/0/0 | 167155/131072/131072 | true/false/false | true/true |
| 7 | 616317157 | 262144/0/0 | 167155/131072/131072 | true/false/false | true/true |

`referenceFixtureStaysBoundedAndFlagVisible` retains all counted-work and stop-flag assertions. `rejectsAFrameWhoseRoutingExceedsTheCompleteFrameBound` retains the real router call and injected clock advance, without sleeping or changing production timing.

## Falsification of the acceptance controls

All changes were confined to disposable class/resource overlays under `/tmp/edge-node-acceptance` and placed before the original classpath. Exit 1 was checked against the intended assertion, not just the process status:

- One coordinate byte changed from `-90...` to `-91...` in the copied ledger: the unchanged deterministic compiler/byte test fails with `ComparisonFailure`.
- One actual exhausted raw frame substituted for settled sample 0: `preservesSettledAndRawPerformanceModesSeparately` fails at `[settled samples must never include an exhausted raw observation] Expecting value to be false but was true`.
- Complete-frame bound assertion bypassed in the disposable test copy: the delayed-routing test fails with `Expecting code to raise a throwable.`

The unmodified final mode assertion and delayed-routing test pass in the focused class run. Disposable sources/classes are removed after evidence capture; command receipts and failure logs remain at the cited paths.

## Parent-suite failures and attribution

The original compiled parent test classes were preserved before recompilation. The full unedited-parent headless run returned exit **1**:

```text
1453 tests completed, 4 failed, 18 skipped
BUILD FAILED in 18m 38s
```

Its failures were two `AccessibleGraphCanvasShould` methods that create `JFrame` under headless mode (`receiveTabThroughSwingDispatchAndRestoreFocusTraversalConfiguration`, `ignoreEnterForSelectionsAbsentFromCurrentVisibleOrderAfterStateReplacement`), plus the initial-spacing acceptance driver directly and through `InitialSpacingShould`. The historical driver rejects the already-landed router extension with `Unexpected production difference: .../geometry/EdgeRouter.java` before this lane's test change can affect it. These are inherited failures, not defects repaired by this lane.

| Reported failure | Parent/current evidence | Attribution |
| --- | --- | --- |
| `GraphEdgeLengthSettleShould.measureDeliveryBaseAndCandidateInFreshEquivalentJvms` | Headful isolated parent/current both exit 1 at the existing `headless=true` precondition; the complete headless parent D2 run passes. | Pre-existing environment precondition; no regression caused here. |
| `InitialSpacingAcceptanceShould.compareFrozenC0AndCandidateInSerialFreshJvms` | Same isolated headful failure in both; headless parent fails on the historical production-overlay restriction. | Pre-existing driver restriction. |
| `InitialSpacingShould` suite wrapper | Same original acceptance test fails in parent/current headful runs and in the headless parent wrapper. | Same pre-existing restriction, counted separately by the suite. |
| `GraphWorkspaceWindowModelShould.f11RestoresBoundsAndExtendedStateFromMaximizedWindow` | Isolated Xvfb/Openbox parent/current both `1 test, 0 failures`; headless full parent skips it. | Reported failure not reproduced in isolation; test and runtime bytes are unchanged, so this lane does not introduce it. |
| `LayoutSettleLoopShould.replayFailureStagesFailAfterCleanupAndEscapesExternalText` | Isolated parent/current both `1 test, 0 failures`; full headless parent passes it. | Reported intermittent failure not reproduced; unchanged test and runtime bytes, no regression introduced here. |

The five source hashes and parent-byte comparisons are in `/tmp/edge-node-acceptance/known-failure-source-pins.txt`. Only `PerformanceTripwiresShould` changed. Full-module acceptance remains pending the Task 8 final run; this focused frozen/performance evidence does not claim a green complete suite or resolution of the inherited failures.

## Evidence paths and SHA-256

- `/tmp/edge-node-acceptance/parent-suite.log` — `7ed0791aca93440156f26f8392e0380eaaabd60a33c15069bcde1b098776fff2`
- `/tmp/edge-node-acceptance/vector-receipts.json` — `9c16e1ac6437193a4053ceb3daf7863aa71fd4db80a4bd2070d8218e76c4a4d2`
- `/tmp/edge-node-acceptance/evidence-receipts.json` — `e6e095d5345d7903bedd71de1f3b5391d25e23a459ccfd5a22fdc2d3672157fa`
- `/tmp/edge-node-acceptance/known-failure-source-pins.txt` — `8e91531a3f5bd0de40bb835b912a889cba60f75e7907d3db38fddcdb14159597`
- `/tmp/edge-node-acceptance/performance-evidence.log` — `f66e5648c2d5aa132ebb0ea12ff1999bca17f6757431b134814139c3838a835e`
- `/tmp/edge-node-acceptance/correction-evidence.log` — `774112caa5bc02842eb04f537f6d92ad1c470917eba907a807f676e8612bc7d5`
- `/tmp/edge-node-acceptance/CorrectionEvidence.java` — `b5f69edd86414079aeb323067ef3c845ae87db8ce784f211c5c39118da355469`
- `/tmp/edge-node-acceptance/AcceptanceJUnit.java` — `ef8b47ef1dd72c5fe5bd949efe208f6be2edeeb2ef381338743f366a7a64f771`
- `/tmp/edge-node-acceptance/run-mutations.py` — `75d8682c68772770c65f3c750f15cb11fe7c6287cba37ce41246250bc1a5340d`
- `/tmp/edge-node-acceptance/mutation-receipts.json` — `984fc7494d7407770be6ecc6dd9b66c39ab3e439dc6e22a7b2049c2a2c2478a6`
- `/tmp/edge-node-acceptance/mutant-ledger.log` — `98236cef95c46d09ad96d1b0cdc327d0b07095817545086dc47d3f1aa6c5658a`
- `/tmp/edge-node-acceptance/mutant-mode.log` — `d25ef3abf11019810e42a74b7f6a6a5529c9f259dba954cfb30c50eb1e44880e`
- `/tmp/edge-node-acceptance/mutant-bound.log` — `664d35e3235cd94389cdc85b0557faa75a87a9da14df6b5a1a1bc3fa9cd2d1b0`
- `/tmp/edge-node-acceptance/known-comparison-receipts.json` — `a01f6607ac329909d250c887a816195be3f89a1ac65e06869edded0fae9491e5`
- `/tmp/edge-node-acceptance/parent-f11.log` — `3163ad1e2e52a2bffc6dcb1dde4d98c88465dae82bf9549bbb2d887f401b888f`
- `/tmp/edge-node-acceptance/candidate-f11.log` — `9695bfc87fe7fc302b54187a1915769032c4b30ae1757b4ea22d2595c3c60bda`
- `/tmp/edge-node-acceptance/parent-known-LayoutSettleLoopShould.log` — `764b1b36cd931f61298cdd83d683018bdfed7983c34e1b5ed91df00128fd079a`
- `/tmp/edge-node-acceptance/candidate-known-LayoutSettleLoopShould.log` — `0984fab8a04449b0a982ee6ff7af382dd57db421dfc60a282e0ac4841b03b6ee`
- `/tmp/edge-node-acceptance/EdgeRouterShould-xml/TEST-org.freeplane.plugin.graph.geometry.EdgeRouterShould.xml` — `6215ab77ab3e5b407dfc7cb040e13c30016255c2d1a3848b3ff11262a07fa2cc`
- `/tmp/edge-node-acceptance/EdgeConflictCorrectionShould-xml/TEST-org.freeplane.plugin.graph.layout.EdgeConflictCorrectionShould.xml` — `42d9c71ce109f815d6f09dd9a57eab2d783868efb60edc3a578a2da28a87290a`
- `/tmp/edge-node-acceptance/SettledReferenceFixtureShould-xml/TEST-org.freeplane.plugin.graph.performance.SettledReferenceFixtureShould.xml` — `8efb96b930158208c5498a7da01ea2d46f4f95edcd5acc8234703f45853d0228`
- `/tmp/edge-node-acceptance/TypedForcesShould-xml/TEST-org.freeplane.plugin.graph.layout.TypedForcesShould.xml` — `cf2736d3775e119bb287325f321cceefd87fe83d88e021d8242697bd2a9aa372`
- `/tmp/edge-node-acceptance/PerformanceTripwiresShould-xml/TEST-org.freeplane.plugin.graph.performance.PerformanceTripwiresShould.xml` — `60bea746219fda6201a2a05f3f792aee32f8af80503d71dab43d2b6ba3ecf23b`
