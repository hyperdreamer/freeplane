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
| `panDuringPresentationIsPreservedThroughExit` | a repaired presentation-disarm path | `-PgraphDisplayAcceptance`; see `ai-specs/tasks/in-progress/010-…` §5 |
| `GraphEdgeLengthSettleShould`, `InitialSpacingAcceptanceShould` | hours of subprocess work | excluded from `test`; run via `gradle :freeplane_plugin_graph:graphAcceptanceDrivers` |
| Raw-reference wall-clock bound | a dedicated performance run | enforced by `gradle :freeplane_plugin_graph:graphPerformanceDiagnostic` |

The unit suite itself asserts only deterministic properties: structural caps, budget flags, and the
synthetic-clock negative test for the frame bound.
