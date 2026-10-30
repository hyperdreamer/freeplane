# Running the graph module test suite

## Default

```bash
JAVA_HOME=~/.sdkman/candidates/java/21.0.8-zulu PATH="$JAVA_HOME/bin:$PATH" \
  gradle :freeplane_plugin_graph:test
```

This is the right command on a quiet, headless, or CI machine.

## When your desktop is in use

Run the suite on a private virtual display instead:

```bash
freeplane_plugin_graph/scripts/run-suite-on-xvfb.sh
freeplane_plugin_graph/scripts/run-suite-on-xvfb.sh -PTestLoggingFull
```

Arguments are passed through to the Gradle test invocation. The script requires `xvfb-run`, `Xvfb` and
`openbox`, picks a free display, and tears the X server down with the process.

### Why this exists

`GraphWorkspaceWindowModelShould` and the focus-traversal methods in `AccessibleGraphCanvasShould` create real
frames and call `requestFocusInWindow`. When another application holds focus on the live desktop, those
frames cannot be activated and the tests fail with:

```
Canvas did not regain focus within 5000 ms
Graph canvas did not receive focus
```

That is the desktop, not the code. Measured directly: the same single test failed on a live KWin desktop
where a Freeplane window and GoldenDict were open with no window focused, and failed identically with the
change under test stashed. The full suite on `Xvfb` + `openbox` passed: 1458 tests, 0 failures.

Do not "fix" these failures by relaxing the focus timeouts — the timeout is what makes a genuine
focus regression visible. Change the display, not the assertion.

## Environment-sensitive tests in this module

| Test(s) | Needs | Gated by |
|---|---|---|
| Focus and window tests | a display that can be activated | skipped when `java.awt.headless=true`; use Xvfb when the desktop is busy |
| `GraphEdgeLengthSettleShould`, `InitialSpacingAcceptanceShould` | hours of subprocess work | excluded from `test`; run via `gradle :freeplane_plugin_graph:graphAcceptanceDrivers` |
| Raw-reference wall-clock bound | a dedicated performance run | enforced by `gradle :freeplane_plugin_graph:graphPerformanceDiagnostic` |

The unit suite itself asserts only deterministic properties: structural caps, budget flags, and the
synthetic-clock negative test for the frame bound. No test is skipped behind a property switch: a
switch nobody flips is not coverage.

## Two rules this suite has already broken once

**A liveness budget is not a latency assertion.** A wait that only exists to let a thread, the window
manager or the event dispatch thread run must be sized so that a loaded host is never reported as a
defect, and its error message must say what actually failed. `DISPLAY_SETTLE_BUDGET_MILLIS`,
`OPERATION_TIMEOUT_NANOS` and `EDT_LIVENESS_BUDGET_MILLIS` exist for this. Do not "fix" a slow-host
failure by deleting the wait's condition, and do not tighten one of these to catch a regression.

**A concurrency probe must assert ordering or a measured fact, never a sampled thread state.**
`Thread.getState()` cannot distinguish "blocked because it is waiting for X" from "idle because it
already finished X". `MapLeaseManagerShould`'s callback-close probes once read an active flag and a
thread state non-atomically, so a load-induced preemption between the two reads reported
`EDT invalidation waited for lease settlement` for an invalidation that had already completed. They
now assert that the event dispatch task finished while the settlement lock was held, that it took the
non-blocking try-lock path, and that it recorded no park and no intrinsic-monitor block while doing so. See
`ai-specs/tasks/done/013-graph-test-hygiene-open-items.md` §3.
