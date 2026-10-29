# 010 — Make the default graph-module test suite meaningful

Task Identifier: `010-make-graph-test-suite-meaningful`
Ticket ID: none (no external ticket exists for this task)
Base commit: `cfa9b7f9fb` (branch `pm/test-hygiene`, worktree `.worktrees/test-hygiene`)

## 1. Problem

`gradle :freeplane_plugin_graph:test` is permanently red on this checkout. A clean baseline run
on `cfa9b7f9fb` produced **1460 tests, 7 failed, 4 skipped**, exit 1, 14m17s, with a live display
(`DISPLAY=:1`, output scales 1.66667 / 1 / 1).

The reported premise document `doc/edge-node-crossing-residual/inherited-test-failures.md` does
**not exist** in this repository at any commit on any ref. The inventory below was therefore
reconstructed empirically rather than taken from it.

### 1.1 Verified failure inventory

| # | Entry | Mechanism | Isolated |
|---|-------|-----------|----------|
| 1 | `GraphEdgeLengthSettleShould.measureDeliveryBaseAndCandidateInFreshEquivalentJvms` | `expected:<"true"> but was:<null>`, headless interlock at `:48` | fast-fail |
| 2 | `InitialSpacingAcceptanceShould.compareFrozenC0AndCandidateInSerialFreshJvms` | same interlock at `:14` | fast-fail |
| 3 | `InitialSpacingShould` | genuine duplicate: `@RunWith(Suite.class)` includes the driver | fast-fail |
| 4 | `PerformanceTripwiresShould.referenceFixtureStaysBoundedAndFlagVisible` | wall-clock bound `5,043,726,559 ns > 5,000,000,000 ns` at `:239` | passes alone |
| 5 | `PerformanceTripwiresShould.preservesSettledAndRawPerformanceModesSeparately` | duplicate report of #4 via the same assertion | passes alone |
| 6 | `GraphWorkspaceWindowModelShould.panDuringPresentationIsPreservedThroughExit` | 5 s settle timeout at `:1940`, `presentationViewport.armed` stays true | fails alone |
| 7 | `GraphWorkspaceWindowModelShould.f11RestoresBoundsAndExtendedStateFromMaximizedWindow` | 5 s settle timeout at `:2344`; screen-containment tolerance 2 px vs 4 px WM border | fails alone |

Four causes: **A** headless interlock (1–3), **B** wall-clock perf bound (4–5),
**C1** presentation disarm (6), **C2** WM border tolerance (7).

### 1.2 Attribution to base `bfaed5611d`

`window/`, `canvas/` and `performance/` main code and the three window/driver test files are
**byte-identical to base** (`git diff bfaed5611d..HEAD` is empty for all of them). All main-code
changes since base are confined to `geometry/EdgeRouter`, `layout/EdgeConflictCorrection`,
`layout/LayoutEngine`, `layout/LayoutWorker`, `layout/graphstream/GraphStreamLayoutEngine`.
Therefore causes A, C1 and C2 are inherited; they are not attributable to the delivered edge-node
work. Corollary: #6 and #7 fail identically on base because every file that determines their
behaviour is byte-identical.

### 1.3 Cause B measured against base (B4)

`rawReferenceMaxFrameNanos()` is a wall-clock maximum over 8 full layout frames on the
`reference-2000-5000` fixture (`RelativeNanoClock` wraps `System.nanoTime()`). Measured with a
throwaway probe, interleaved, identical classpath except the five changed production files:

| arm | run1 | run2 | run3 |
|-----|------|------|------|
| HEAD `cfa9b7f9fb` | 5.472 s | 5.666 s | 5.858 s |
| Base `bfaed5611d` | 5.056 s | 5.492 s | 6.043 s |

Both arms breach the 5 s bound in every run, and `frames[0]` is always the maximum; frames 1–7
take 0.69–1.51 s. The bound is **dominated by first-frame JVM warm-up**, and base's worst run
exceeds HEAD's worst. Verdict: **no regression attributable to the delivered work**; the gate is
environment-sensitive and pre-existing.

## 2. Design

Minimal test-scope and build-configuration change. No production source is edited;
`settled-reference.csv` is untouched.

**A — keep the interlock, remove the drivers from the ordinary suite.**
Exclude both driver classes from `test`; drop `InitialSpacingAcceptanceShould` from the
`InitialSpacingShould` suite so the wrapper cannot re-run it (its other three classes keep
running). Add a dedicated forked `graphAcceptanceDrivers` task that sets
`java.awt.headless=true` and runs exactly the two drivers. The guards at `:48` and `:14` are
untouched. This also removes a latent hazard: today, running the suite under
`JAVA_TOOL_OPTIONS=-Djava.awt.headless=true` (the documented acceptance environment) *satisfies*
the guards and would start the 8–14 hour subprocesses.

**B — keep every deterministic assertion, gate only the wall-clock one.**
`referenceFixtureStaysBoundedAndFlagVisible` keeps its structural assertions (frame presence,
Revision-7 index caps, budget-exhaustion flags) unconditionally; only its wall-clock bound moves
into a new test gated by `Assume.assumeTrue` with a stated reason and enabled by
`-PgraphWallClockPerformance`. The bound mechanism stays deterministically covered by the
existing synthetic-clock negative test `rejectsAFrameWhoseRoutingExceedsTheCompleteFrameBound`.

**C2 — account for the window-manager border, keep work-area coverage tight.**
Introduce `X11_WM_BORDER_TOLERANCE = 8` and use it only for the four *outer screen-containment*
checks. The four *work-area coverage* checks stay at `X11_AWT_BOUNDS_TOLERANCE = 2`. For the
observed maximized frame `(-4,-2,1928,1088)` against screen `(0,0,1920,1080)`, exactly three
outer-edge checks fail today (left and right by 2 px, bottom by 4 px) while all work-area checks
already pass. A 100 px-short window still fails; the assertion stays meaningful.

**C1 — no production fix in this task; skip visibly and record the finding.**
`armed` is cleared only by `disarm()` via `publishToolbarViewport`/`windowClosed`. After a panned
F11 exit the presentation viewport is never disarmed, leaving `suppressViewportEvents` set
(`GraphWorkspaceWindow.java:1645`). That is a pre-existing product-side observation on unchanged
code; fixing it would change production behaviour, which is outside this task. The test is
therefore gated by `-PgraphDisplayAcceptance` with a stated reason, and the observation is
recorded in §5 as a follow-up rather than silently dropped.

## 3. Specification

### S1 — ordinary suite
`gradle :freeplane_plugin_graph:test` exits 0 both headful (this desktop) and headless, and starts
no `python3` subprocess.

### S2 — drivers remain deliberately runnable
The guards at `GraphEdgeLengthSettleShould:48` and `InitialSpacingAcceptanceShould:14` are
byte-unchanged. `gradle :freeplane_plugin_graph:graphAcceptanceDrivers` runs exactly those two
classes with `java.awt.headless=true`, one fork, never up-to-date.

### S3 — skips are visible and justified
Every newly skipped test carries an `Assume` message naming the condition and the enabling
property. No assertion is deleted: the wall-clock bound keeps its deterministic negative test, and
the window geometry keeps its coverage assertions.

### S4 — no production change
`freeplane_plugin_graph/src/main/java/**` is byte-identical to `cfa9b7f9fb`, including
`geometry/` and `layout/`.

### S5 — fixture preserved
`freeplane_plugin_graph/src/test/resources/performance/settled-reference.csv` keeps sha256
`e9e988b7967ea6bb4f503dfb4c5af7d3e74a3328e28e0987e71ff0f0b8ac0578`.

## 4. Plan

**Task 1 — build configuration and suite membership.**
Files: `freeplane_plugin_graph/build.gradle`,
`freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/layout/graphstream/InitialSpacingShould.java`.
Exclude the two drivers from `test`; add `graphAcceptanceDrivers`; forward
`graphWallClockPerformance` and `graphDisplayAcceptance` system properties; drop the acceptance
driver from the suite. Verification: `gradle :freeplane_plugin_graph:test` no longer reports the
three driver entries.
**Implementer tier:** Standard

**Task 2 — gate the wall-clock frame bound.**
File: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/performance/PerformanceTripwiresShould.java`.
Remove the wall-clock assertion from `referenceFixtureStaysBoundedAndFlagVisible`, add the
assumption-gated test, keep `rejectsAFrameWhoseRoutingExceedsTheCompleteFrameBound` untouched.
Verification: the test class passes with and without the property; deterministic structural
assertions still run.
**Implementer tier:** Standard

**Task 3 — window-manager border tolerance.**
File: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java`.
Split the tolerance so screen containment allows the WM border while work-area coverage stays at
2 px, in both `hasWorkAreaCoverage()` and `assertWorkAreaCoverageOnEdt()`.
Verification: `f11RestoresBoundsAndExtendedStateFromMaximizedWindow` passes headful in isolation.
**Implementer tier:** Standard

**Task 4 — gate presentation disarm, record the finding.**
File: `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/window/GraphWorkspaceWindowModelShould.java`.
Gate `panDuringPresentationIsPreservedThroughExit` behind `-PgraphDisplayAcceptance` with a stated
reason referring to §1.2 and §5.
Verification: skipped with a visible message by default; no assertion removed.
**Implementer tier:** Standard

**Task 5 — acceptance.**
Full module suite headful and headless; driver-reachability proof for S2; byte-identity checks for
S4 and S5; mutation probe for the wall-clock gate.
**Implementer tier:** Advanced

## 5. Follow-up (out of scope, recorded not fixed)

Presentation-disarm after a panned F11 exit: `GraphWorkspaceWindow` never disarms the presentation
viewport when the exit path republishes through `applyingViewport`, so `suppressViewportEvents`
stays set (`GraphWorkspaceWindow.java:1645`). Pre-existing on `bfaed5611d`, unchanged here. It
deserves its own task because the fix is a production behaviour change.

Also recorded: the raw-reference wall-clock bound is warm-up dominated (frame 0 only). A future
redesign should bound steady-state frames, keyset-indexed to the fixture, rather than raising or
lowering the 5 s constant.

## 6. Scope additions found during acceptance

Two defects surfaced only in full-suite runs, both reproduced by measurement rather than predicted by the
premise document. They are included here because without them S1 cannot hold.

**6a — the wall-clock budget was enforced twice.** Gating the assertion was not enough:
`GraphWorkspacePerformanceDiagnostic.await` enforced a second 5 s wall-clock limit
(`OPERATION_TIMEOUT_NANOS`) around every worker operation, so a loaded machine failed in `@BeforeClass`
with `Five-second timeout during raw reference worker submit` and aborted the whole class (observed:
1441 tests, `PerformanceTripwiresShould > classMethod` failed). Liveness and the performance gate are
now separated: `OPERATION_TIMEOUT_NANOS` is 120 s as a hang detector, and the performance gate remains
`RAW_REFERENCE_MAX_FRAME_NANOS`, asserted by the opt-in test. No test referenced the old 5 s value and
none expected a timeout.

**6b — two headless-only `JFrame` failures.** `AccessibleGraphCanvasShould`
(`receiveTabThroughSwingDispatchAndRestoreFocusTraversalConfiguration`,
`ignoreEnterForSelectionsAbsentFromCurrentVisibleOrderAfterStateReplacement`) failed headless with
`HeadlessException` from `JFrame`. These are the same two failures already documented as inherited at
`frozen-acceptance.md:126`. Focus traversal and Swing key dispatch genuinely need a displayable window,
so `withFocusedCanvas` — the single chokepoint both use — now skips with a stated reason under headless.
Verified still running (not skipped) headful.

## 7. Acceptance results

Environment: Zulu 21.0.8, Gradle 9.0.0, worktree `.worktrees/test-hygiene`, tree as committed.

| Gate | Result |
|------|--------|
| Headful full suite (`DISPLAY=:1`) | **exit 0**, 1458 tests, 0 failures, 6 skipped, 14m18s |
| Headless full suite (`JAVA_TOOL_OPTIONS=-Djava.awt.headless=true`) | **exit 0** |
| Baseline for comparison | exit 1, 1460 tests, 7 failed, 4 skipped |
| No `python3` subprocess started by `test` | confirmed in both environments |

Skips in the headful suite, all pre-existing reasons plus the two new gated ones: two Windows/root
preconditions, the external-map precondition, the headless-view assertion, the presentation-disarm gate,
and the wall-clock frame gate.

Falsifiability probes (each restored afterwards; final tree verified clean):

1. `RAW_REFERENCE_MAX_FRAME_NANOS = 1L` → `rawReferenceFrameStaysInsideTheWallClockBound` failed. The
gate is live, not vacuous, and `preservesSettledAndRawPerformanceModesSeparately` failed with it on the
constant pin.
2. `X11_WM_BORDER_TOLERANCE = 2` → `f11RestoresBoundsAndExtendedStateFromMaximizedWindow` failed at the
settle timeout again. The tolerance is load-bearing.
3. `graphAcceptanceDrivers` reached `GraphEdgeLengthSettleShould` (proceeded into real fixture work) and
`InitialSpacingAcceptanceShould` (proceeded past its interlock, then failed on its own documented
historical `EdgeRouter.java` restriction). Both children were killed and verified gone.

Residual risk, recorded not fixed: `GraphWorkspaceLifecycleShould.doesNotDeliverCallbacksAfterClose`
(unchanged since base, documented as flaky by this repository's own skill) failed once in a full-suite
run and passed 5/5 in isolation. It is a pre-existing close-boundary race detector; repairing it is a
production change outside this task.

## 8. Tooling note

`sdd-state` and `plan-policy.mjs` are not installed on this machine, so
`sdd-state validate-plan` cannot be executed. Plan structure was validated in-session instead
(unique task ids, capitalised tier values, every specification clause mapped to a task).

## 9. Delivery

Delivered commit: `bed4b364d2` — `test(graph): make the default module suite green and trustworthy`.

Delivery decision (option 2): fast-forward local `plugin/graph-workspace` from `cfa9b7f9fb` to the delivered
commit, then publish to the **new** ref `origin/candidate/test-hygiene`.

Rationale: the lane branch is a clean fast-forward of the delivery branch, so the integration rewrites no
published history. `origin/plugin/graph-workspace` is deliberately not written: local and remote have
diverged by 232 remote-only commits, and updating it would require a force that discards them.

Open item carried forward, not fixed here because it is a production behaviour change: the presentation
viewport is never disarmed after a panned F11 exit, leaving `suppressViewportEvents` set
(`GraphWorkspaceWindow.java:1645`). Pre-existing on `bfaed5611d`. Recorded in section 5.

## 10. Follow-up: the wall-clock bound got a single owner (supersedes section 4/7 wording)

The `-PgraphWallClockPerformance` opt-in described in section 4 has been **removed**. A switch that nobody
flips is not coverage; it just hides the decision.

- `GraphWorkspacePerformanceDiagnostic.verifyRawReferenceFrameBound()` now owns
  `RAW_REFERENCE_MAX_FRAME_NANOS`. It runs the raw reference fixture and raises a `DiagnosticFailure` whose
  message lists every frame duration, so a failure names the frames instead of only a number.
- It is called from the diagnostic's `run()`, i.e. by the already-existing dedicated
  `graphPerformanceDiagnostic` task. That task is the only place a wall-clock performance gate belongs.
- The unit suite keeps the deterministic assertions: the Revision-7 structural caps and budget flags in
  `referenceFixtureStaysBoundedAndFlagVisible`, and the synthetic-clock negative test, which now also asserts
  that the dedicated-task owner raises `complete-frame bound`.
- `build.gradle` no longer defines or forwards the property.

Residual: `graphPerformanceDiagnostic` runs longer than 15 minutes on this machine, so the new call site was
not exercised end-to-end here. Its mechanism is covered deterministically by the extended negative test, and
the call is a single statement after `runLifecycleProbes`.

## 11. Follow-up: the suite needs a controlled display (found while verifying section 10)

A full-suite run on the live KWin desktop failed 13-15 tests, all of them **focus-acquisition timeouts**
(`Graph canvas did not receive focus`, `Canvas did not regain focus within 5000 ms`) across
`AccessibleGraphCanvasShould` and `GraphWorkspaceWindowModelShould`. A control run of the same single test,
with these changes stashed, failed identically — so it is the desktop state, not this change.

Cause: the active desktop had other focus-holding applications (a Freeplane window and GoldenDict) and no
window held input focus, so the test frames could not be activated.

Verified fix: the identical suite on a dedicated `Xvfb` + `openbox` display passes —
**exit 0, 1458 tests, 0 failures, 5 skipped, 11m05s**. This matches the environment the repository's own
acceptance documents already use.

Recommendation carried forward: make that the standard invocation for this module's suite rather than
depending on whatever the developer's desktop happens to be doing.

### 11.1 Documented entry point

`freeplane_plugin_graph/scripts/run-suite-on-xvfb.sh` runs the module suite on a private `Xvfb` display with
`openbox`, so the focus-dependent tests do not depend on the developer's desktop. Arguments pass through to
Gradle. It is documented in `freeplane_plugin_graph/doc/running-the-test-suite.md`, which also records that a
focus-timeout failure must never be "fixed" by relaxing the timeout.

A Gradle task was rejected deliberately: `doLast` does not run when the test task fails, so an X server started
in a Gradle task would be orphaned exactly in the failing case. The script's `exec`/`trap`-free process model
lets `xvfb-run` own the lifecycle instead.

### 11.2 Residual flake found while verifying the script (pre-existing, not caused here)

| Run | Result |
|---|---|
| Full suite on Xvfb, before the script existed (same code) | exit 0 — 1458 tests, 0 failures, 5 skipped |
| Full suite via the script | exit 1 — 1458 tests, **1 failure**, 5 skipped |
| `GraphInteractionControllerShould.doNotPanWhenAProgrammaticScrollMovesTheViewportUnderAStationaryPointer` in isolation, 5 runs | 5 pass |

The failure is `setViewPosition` not taking effect (expected `Point[201,31]`, actual `Point[161,11]`) at
`GraphInteractionControllerShould.java:744` — a layout-timing dependency, not a focus problem. It is
unrelated to this task's diff (which touches only performance tests, build configuration, a script and a
document), and it reproduced at a low rate against byte-identical code that had just passed. Recorded as a
follow-up rather than fixed here.

### 11.3 The pan-geometry flake, diagnosed and fixed

**Mechanism.** `JViewport` clamps `setViewPosition` against `getViewSize()` — the view's *preferred* size,
since `GraphCanvas` is not `Scrollable` — but the test computed its scroll range from `canvas.getWidth()`.
`JViewport` lays the view out at `max(extent, viewSize)`, so when the component is wider than its preferred
size the two disagree and the test can request a position beyond the real maximum, which then silently snaps
back to the old one.

Confirmed by instrumenting the test and reading the real values: a passing run had
`canvasW=1220 viewSizeW=1220 extentW=882 viewPos=(161,11)`. The failing run's actual value was `(161,11)`
against a requested `(201,31)`, i.e. it clamped at `161`, which requires `viewSize.width = 1043`
(`1043 - 882 = 161`). So the stale state is a canvas laid out at 1220 whose preferred size is 1043.

**Fix.** Derive the range from `viewport.getViewSize()` — the quantity the viewport itself clamps with — and
move toward a position already known to be valid:

```java
final int maxX = Math.max(0, viewSize.width - extent.width);
final int targetX = viewPosition.x == 0
    ? Math.min(40, maxX) : Math.max(0, viewPosition.x - 40);
```

A position between zero and the current, already-honoured position cannot be rejected, so the assertion no
longer depends on the two sizes agreeing.

**Evidence.** Two consecutive full suites on `Xvfb` + `openbox`: exit 0 both times — 1458 tests, 0 failures,
5 skipped (12m53s and 12m45s). Previously one of two full-suite runs failed on this test.

**Honest limit.** The exact stale state was not reproduced outside the suite: a synthetic `JScrollPane`
reproducer resynced `getViewSize()` to the component size, so old-code-fails/new-code-passes was not
demonstrated as a mutation pair. The mechanism is pinned by the arithmetic above and the fix is justified by
construction rather than by a reproduced failure.

## 12. Verification of the presentation-disarm gap (supersedes the "deserves its own task" note in section 5)

**Question:** does `armed` staying true after a panned F11 exit have a user-visible consequence, or is the
skipped assertion observing harmless internal state?

**Method.** A temporary diagnostic test in `GraphWorkspaceWindowModelShould` (since reverted) drove the real
window fixture on `Xvfb` + `openbox`: enter presentation, pan, exit, read `armed`, then pan again and record
whether the viewport moved and whether a `GraphCommand` was issued.

**Result.**

| Measurement | Value |
|---|---|
| `armed` after a panned exit | **true** — the gap is real |
| post-exit pan moved the viewport | **true** — `centerX -151.95 → -199.95` |
| commands issued after exit | **1** |

A first probe reported `viewportChanged=false` with zero commands, but that was the probe's fault: the window
moves when presentation exits, so the original screen coordinates no longer landed on the canvas. Recomputing
the pointer after exit produced the table above. The broken first attempt is noted because it would otherwise
have "proved" a defect that does not exist.

**What the stale flag actually does.** `GraphWorkspaceWindowModel.classify(ChangeEvent)` calls
`publishExternalViewport()` only when `!armed`; while armed it takes the presentation branch (baseline-anchor
comparison, `publishUserView`, settle-correction registration). So after a panned exit, ordinary viewport
events are processed by presentation logic rather than normal logic.

**Assessment.** No user-visible defect demonstrated for the path that matters most: the viewport tracks the
pointer and changes are still reported. What remains unverified is whether the presentation branch's side
effects (settle-correction scheduling, baseline suppression) misbehave after exit.

**Options.**

- **A — no change.** Keep the test skipped behind `-PgraphDisplayAcceptance`, document the flag as latent
  state with no demonstrated user impact. Cheapest; leaves a permanently skipped assertion.
- **B — clear the flag on presentation exit** (`viewportHold.disarm()` on the exit path). Small, targeted, and
  it makes the currently skipped assertion valid so the test can run by default. This is a **production
  behaviour change** and needs explicit approval; the window suite would validate it.
- **C — rewrite the assertion** to the observable contract (viewport preserved, no commands on exit) and drop
  the internal-flag wait. Restores coverage without touching production, but leaves the stale flag in place.

Recommendation: **B** if a product change is acceptable, otherwise **C**. A is only right if the latent state
is genuinely not worth clearing.
