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

## 8. Tooling note — **corrected, see 013 §2**

At the time this task was written it was believed that `sdd-state` and `plan-policy.mjs` are not
installed on this machine, so `sdd-state validate-plan` could not be executed. Plan structure was
validated in-session instead (unique task ids, capitalised tier values, every specification clause
mapped to a task).

**That belief was wrong.** Both ship with this machine's SDD skill and always did:

```
/home/henry/.pi/agent/skills/subagent-driven-development/scripts/sdd-state
/home/henry/.pi/agent/skills/subagent-driven-development/scripts/sdd-state.mjs
/home/henry/.pi/agent/skills/subagent-driven-development/scripts/lib/plan-policy.mjs
```

They are not on `PATH`, and the original search looked for a `sdd-state` binary rather than at the
skill's own entrypoint. The gate was runnable throughout. Task 013 runs it and records the exit
status.

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

### 12.1 The premise was wrong: the hold is released by design

Reading `scheduleQuietObservation` shows the flag is **not** stuck. Settlement calls `disarm()` after
`lastTransitionGeometryAt + 2500` ms once the frame and viewport events have been quiet for 200 ms, with a
hard `+ 5000` ms cap (`GraphWorkspaceWindow.java:857` and `:863`). The hold is kept armed on purpose across
the restore so it can still correct post-restore geometry drift.

Measured on the real fixture: `armedClearedAfterMillis=2288`.

So the defect is **in the test, not the product**: `panDuringPresentationIsPreservedThroughExit` waited exactly
`5000 ms` for the hold to be released, which is exactly the settlement cap. The wait raced the cap it was
waiting on. Option B (disarm immediately on exit) would have cancelled the drift-correction window that
preserves the user's pan — the opposite of a fix. It was **not** implemented.

### 12.2 Change made instead

- `DisplayFixture.awaitWithin(condition, budgetMillis)` added, so a wait can state a budget explicitly.
- The pan test now waits `10000 ms` for the hold release, strictly above the 5000 ms cap.
- The `-PgraphDisplayAcceptance` gate added earlier is removed: the test now runs in the default suite.

Verified: the test passes 3/3 under `Xvfb` and reports 0 skipped, i.e. it genuinely executes.
Suite skips drop from 5 to 4.

### 12.3 New pre-existing flake surfaced during verification (not caused here)

`GraphCanvasPaintShould.keepBothWorldExtremaReachableAfterOffCenterUserPan` failed once in a full suite at
`GraphCanvasPaintShould.java:2337` (`assertHorizontallyReachable`): `visibleX = -1278.0` not between
`[0.0, 302.0]`. It also fails **in isolation**, 1 of 3 runs, so it is independent of this change and
pre-existing. Its `assertHorizontallyReachable` computes its bound from `viewport.getView().getWidth()`, the
same quantity-mismatch family as 11.3, but the reported negative value does not follow from that alone, so the
root cause is not established and it was left alone rather than guessed at.

### 12.4 Follow-up

`GraphCanvasPaintShould.keepBothWorldExtremaReachableAfterOffCenterUserPan` deserves its own diagnosis. The
suite is green only when it happens to pass.

### 12.5 The canvas reachability flake, diagnosed and fixed

**Instrumented values** (probe since removed) on passing runs:

```
extentW=302 compW=2580 viewSizeW=2580 maximumX=2278 surfaceX=80   requestedX=0   actualX=0   visibleX=80.0   edt=false
extentW=302 compW=2580 viewSizeW=2580 maximumX=2278 surfaceX=1100 requestedX=949 actualX=949 visibleX=151.0  edt=false
```

**Mechanism.** The observed failure was `visibleX = -1278.0` against `[0, 302]`. With `surfaceX = 80` that
requires `actualX = 1358` — but the requested value for that call is `0`. So `setViewPosition` was not
honoured and the viewport was found at a position nobody asked for. The probe shows `edt=false`: this
assertion mutates and reads `JViewport` from the test thread while the event dispatch thread is live. A queued
layout pass (invalidated by the preceding `panByPixels`) can therefore reposition the viewport between the set
and the read. That is an AWT threading violation, not an arithmetic error.

**Fix.** Perform the set and the read in a single EDT task via `SwingUtilities.invokeAndWait`, so no queued
layout can interleave. `maximumX` is now also taken from `getViewSize()`, the size `JViewport` itself clamps
with, instead of the laid-out component width.

**Evidence.**

| Check | Result |
|---|---|
| Falsifiability: force `maximumX = 0` | 3 failures at the assertion, so the check is live |
| `GraphCanvasPaintShould` in isolation, 8 runs | **8 pass, 0 fail** (before: 1 of 3 failed) |

**Honest limit.** 8 clean runs against a ~1/3 baseline is strong but not proof; the mechanism and the
mutation result are the stronger evidence.

### 12.6 The suite is still not reliably green: `MapLeaseManagerShould`

A full suite run after 12.5 failed on a different test:
`MapLeaseManagerShould.completesExternalReloadCallbackCloseWithoutBlockingTheDetectingEdt`
(`expected:<null> but was:<AssertionError: EDT invalidation waited for lease settlement>`).

Investigating it produced a worse result than the original failure:

| Batch | Command | Result |
|---|---|---|
| 1 | class, 5 runs | **4 failed** (1 at `:755`, 3 at `:1762` `java.awt.AWTError`) |
| 2 | class, 4 runs, `-PTestLoggingFull` | 4 passed |
| 3 | `loadsTheRealFixtureThroughMapLoaderOnTheSuppliedEdtWithoutCreatingAView`, 3 runs | 3 passed |

So this is **not a deterministic defect and not attributable to this task**: the class fails in isolation, in a
different package from every file changed here, and its failure rate changes between batches. `:1762` is
`starter.createFrame()` inside a heavyweight test that boots a real Freeplane controller, and the intermittent
`java.awt.AWTError` there is an environment/toolkit condition.

**Consequence, stated plainly:** `gradle :freeplane_plugin_graph:test` is **not yet reliably green on this
machine.** Four independent intermittent failures have been found in the module during this task
(`GraphWorkspaceLifecycleShould` — fixed; `GraphCanvasPaintShould` — fixed; `GraphInteractionControllerShould`
— fixed; `MapLeaseManagerShould` — open). Each green full-suite run so far has been green modulo whichever
flake happened not to fire.

This is the honest end state of the "make the suite meaningful" goal: the deterministic failures are gone, the
skips are justified, and the remaining risk is a tail of independent low-to-medium-rate flakes rather than a
broken suite.

### 12.7 The dominant remaining flake class: 5 s settle budgets under host load

A full suite during a busy period failed `f11RestoresBoundsAndExtendedStateFromNormalWindow` with
`Display condition did not settle within 5000 ms`, in a run that took 19 minutes instead of the usual 13, at
load average 25. Two other low-rate failures earlier in this task were also 5 s settle timeouts.

`GraphWorkspaceWindowModelShould.DisplayFixture` used a hard-coded 5000 ms budget for every display condition
and for canvas focus. Those conditions wait on the window manager and the EDT, so the budget is a **liveness
bound**, not an assertion about UI latency — the same distinction already applied to
`GraphWorkspacePerformanceDiagnostic.OPERATION_TIMEOUT_NANOS` in section 6a. At 5000 ms it reported the host's
load as a code defect.

`DISPLAY_SETTLE_BUDGET_MILLIS = 20000` now backs `await`, `awaitCanvasFocus` and the presentation-hold wait
(which must in any case exceed the 5000 ms settlement cap).

**Verified under the failing condition, not an idle one:** the whole class was run at load average 22–33 —
**118 tests, 0 failures, 0 skipped**, in 1m06s. No synthetic load was added; this is the machine's own load.

### 12.8 `MapLeaseManagerShould`: load-sensitive, and deliberately not loosened

Further investigation of the class left open in 12.6.

**Load correlation (observational, not proven).** Two batches only:

| Ambient load | Command | Result |
|---|---|---|
| ~20-25 | class, 5 runs | **4 failed** |
| ~7-13 | class, 5 runs, later | **5 passed** |

**Why it is not a timing tweak.** The reported failure is raised here:

```java
waitingAtBoundary.set(observationToUse.awaitWaitingOrFinished());
if (waitingAtBoundary.get()) {
    callbackFailure.compareAndSet(null,
        new AssertionError("EDT invalidation waited for lease settlement"));
```

That is a **product guarantee**: closing must not block the event dispatch thread. Its sibling windows
(`operationStarted.await(1, TimeUnit.SECONDS)`, `observationToUse.awaitStarted()`) are also one-second
liveness windows around the same non-blocking contract. Raising them would make a real regression
indistinguishable from a slow host, which is exactly the failure mode this task exists to remove. They were
therefore **left unchanged**.

**The correct repair, if this is taken further,** is to observe the ordering rather than the wait: have the EDT
invalidation task record its completion and compare that ordering against lease settlement, so the assertion
becomes "the EDT task completed before settlement finished" instead of "the EDT task was not still waiting when
we sampled". That is a redesign of the probe, not a budget change, and it is not something to attempt
speculatively at the end of this task.

The intermittent `java.awt.AWTError` at `starter.createFrame()` (12.6) is a separate, environment-side symptom
of the same heavyweight test booting a real Freeplane controller under load.

## 13. Close-out (superseded by 013 for everything still open)

This task's own work is complete and was delivered in §9 and §10–§12. The residuals it recorded are
now either closed by
[`013-graph-test-hygiene-open-items`](../done/013-graph-test-hygiene-open-items.md) or tracked there
as explicit open items, so nothing in this record still describes open work *of this task*.

| Left open here | Disposition |
|---|---|
| §12.8 "the correct repair, if taken further, is to observe ordering rather than the wait" | **Closed in 013 §3.** The probe now asserts lock ownership, completion ordering, observation identity and zero park time, and deletes the sampler. Verified against an unbounded-block mutant and a bounded-stall mutant, both of which it catches. |
| §8 "`sdd-state` and `plan-policy.mjs` are not installed on this machine" | **Corrected above.** They were shipped with the SDD skill; only `PATH` was missing. 013 runs the gate. |
| §6a the `-PgraphWallClockPerformance` opt-in | Already removed in §10. No further action. |
| §10 residual: `graphPerformanceDiagnostic` call site never exercised | **Still open.** 013 §9 ran it to completion of the process deadline and established that the heuristic is worse than "needs an idle machine": the diagnostic spends its whole 40-minute budget in the scenario sweep and never reaches the gate. Tracked in 013 §9. |
| §12.6 `MapLeaseManagerShould` intermittent failure | **Closed for the reported assertion** (013 §3). A *different* test in the class, `retriesDeferredLifecycleRemovalWhenRetrySchedulerRejects`, remains a pre-existing flake at a rate that does not change with this work: 013 §3.6, §10. |
| §12.3–§12.5 `GraphCanvasPaintShould` | Closed in §12.5. |
| §12.7 the 5 s settle budgets | Closed in §12.7. One full-suite run during 013 still failed `f11RestoresBoundsAndExtendedStateFromNormalWindow`, but on a window-manager placement race rather than a settle timeout (`bounds=(120,100,1000,700)` before, `(0,0,1000,700)` after) - see 013 §8.3. |
| §5, §12 presentation disarm | Resolved as a test defect in §12.1–§12.2; the gate was removed, the test runs by default, and the now-dead `graphDisplayAcceptance` property was removed in 013 §7.2. |

Suite state at close-out: `1458 tests, 0 failures, 4 skipped` on the retry run, 13m33s, on Xvfb +
openbox (013 §8.3).
