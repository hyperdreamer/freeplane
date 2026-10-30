# 015 — F11 exit can lose the window position: diagnostic determination

Task Identifier: `015-f11-restore-position-race`
Ticket ID: none (no external ticket exists for this task)
Branch: `pm/graph-f11-bounds-015` (off `bd2be219d8`)
Status: **delivered** — diagnosis in §2–§5, approved repair implemented in §6, verification in §9

## 1. Symptom

`GraphWorkspaceWindowModelShould.f11RestoresBoundsAndExtendedStateFromNormalWindow` fails at
`DisplayFixture.await`:

```
Display condition did not settle within 20000 ms; bounds=java.awt.Rectangle[x=0,y=0,width=1000,height=700]
```

with the fixture's own record of the round trip:

```
DISPLAY before   bounds=Rectangle[x=120,y=100,width=1000,height=700] state=0
DISPLAY restored bounds=Rectangle[x=0,y=0,width=1000,height=700]     state=0
```

**Size honoured, position lost, extended state correct.** Observed once in a full-suite run of task
010, twice across the five full-suite runs recorded since (≈1 in 5).

## 2. What does not reproduce it

| Attempt | Runs | Failures |
|---|---|---|
| The class alone (118 tests) under `run-suite-on-xvfb.sh` | 9 | 0 |
| The class plus `AccessibleGraphCanvasShould` | 1 | 0 |
| 30 consecutive F11 round trips inside one `DisplayFixture` (throwaway test, since removed) | 1 | 0 |
| Instrumented full suite | 1 | 0 |

Repetition inside one fixture does not reproduce it, so the trigger is not "many round trips".

## 3. The mechanism, reproduced without Freeplane

`GraphWorkspaceWindow.rawRestoreWindowed` is:

```java
dispose();
setUndecorated(false);
setBounds(previous.bounds());
setExtendedState(previous.extendedState());
...
setVisible(true);
validate();
```

`setExtendedState(MAXIMIZED_BOTH)` is asynchronous at the window manager. If the frame is disposed and
remapped while that request is still in flight, the un-maximize lands after the mapping and the window
manager places the frame itself instead of using the requested position — **keeping the client's size
but choosing the origin**.

This was reproduced with a bare `JFrame` and no Freeplane code at all (`/tmp/awtprobe`,
Xvfb 1920×1080 + openbox, the same environment as the suite script):

| Variant | Rounds | Lost position | Maximize geometry contaminated |
|---|---|---|---|
| the shipped sequence | 60 | **1** | 6 |
| shipped sequence + one post-visibility bounds re-assert | 60 | **0** | 1 |

The failure and the contamination correlate: in the failing round the disposed frame still reported
`Rectangle[0,21,1920,1059]` instead of `Rectangle[0,0,1920,1080]`, i.e. the maximise had not settled when
the restore began. The recorded restored bounds in the real failure —
`Rectangle[0,0,1000,700]` — match the bare-`JFrame` failure exactly.

Two controls from the same probe rule out "just reorder the calls":

| Variant | Result |
|---|---|
| `setExtendedState(NORMAL)` **before** `setBounds` | restores correctly, no better than shipped |
| `setBounds` → `setVisible` → `setExtendedState(NORMAL)` | restored the **full-screen** bounds (1920×1080) — strictly worse |

So the shipped ordering is the better of the three; the defect is the missing confirmation after the
peer is mapped, not the order.

## 4. Determination

**An AWT / window-manager race in the presentation-restore idiom, not logic in the graph workspace.**
It is reachable with a plain `JFrame`, it is sensitive to whether the maximise request has settled, and
it is independent of projection, layout, leases or canvas code.

It is nonetheless a **real, if rare, user-visible defect**: a user who leaves presentation while the
window manager is still applying the maximise can get their window back at the top-left corner. The test
is therefore reporting the truth when it fires; it is not a bad assertion.

## 5. Why the two obvious "test fixes" are wrong

- **Widen the wait.** The condition never becomes true — the window manager's placement is final. This is
  not a settle timeout, which is why raising `DISPLAY_SETTLE_BUDGET_MILLIS` did not stop it (task 010
  §12.7 already learned that for the same test).
- **Relax the assertion to size and extended state only.** That would delete the only coverage of the
  position contract, and it would hide a genuine product defect behind a weaker test. This is the exact
  mistake task 013 §3 was created to remove.

## 6. The repair (implemented after approval)

One guarded re-assert in `GraphWorkspaceWindow.rawRestoreWindowed`, after `setVisible(true); validate();`:

```java
// The window manager applies the un-maximize asynchronously and can re-place a frame that was
// disposed and remapped while that request was still in flight, silently dropping the saved
// position. Re-asserting the saved bounds once the peer is mapped is a no-op when it already
// honoured them.
if (!previous.bounds().equals(getBounds())) {
    setBounds(previous.bounds());
    validate();
}
```

Why it is safe: when the window manager already honoured the request the branch does not execute; when
it did not, this restates the same rectangle the method already asked for, after the peer exists. It
touches no other platform path and no other restore behaviour.

Implemented exactly as proposed, with no other change to the method or to any other platform path.

## 7. How the repair would be verified, and the honest limit

| Check | Strength |
|---|---|
| Bare-`JFrame` probe, 60 rounds per arm | already run: 1 loss shipped vs 0 with the re-assert |
| `GraphWorkspaceWindowModelShould` (118 tests) | regression only — it already passes without the fix |
| Full suite | regression only — the failure rate is ≈1 in 5 |
| Mutation: make the re-assert a no-op | would show the branch is exercised only when the window manager misbehaves |

**Honest limit:** no deterministic reproduction inside Freeplane exists. The evidence is a
product-external reproduction plus the exact signature match, so the fix is justified by mechanism and by
a 60-round arm, not by a red-to-green Freeplane test. A green full suite is a *regression* result, not
proof that the flake is gone: at ≈1 in 5 it would take twenty-odd clean full-suite runs to say that, and
each costs thirteen minutes. Anyone who wants that strength should build the reproducer into the
product's own fixture — drive `rawRestoreWindowed` against a deliberately slow window manager — which is
a bigger job than the fix.
**The mutation check that would prove the branch is exercised cannot be built honestly here:** making the
re-assert a no-op leaves the suite green, because the suite cannot force the window manager to misplace the
frame. That is stated rather than papered over.

## 8. Instrumentation discipline

Every probe used for §2 and §3 was reverted before anything was committed, and the tree verified
byte-identical to `bd2be219d8`: a `System.out` probe inside `rawRestoreWindowed` and a throwaway
repeated-round-trip test in `GraphWorkspaceWindowModelShould`. The delivered diff touches exactly one
production method, `GraphWorkspaceWindow.rawRestoreWindowed`, and adds ten lines to it.

## 9. Verification of the delivered fix

All commands in `.worktrees/f11-bounds-015` with `JAVA_HOME=…/21.0.8-zulu`.

| Gate | Command | Result |
|---|---|---|
| Compile | `gradle :freeplane_plugin_graph:compileJava` | BUILD SUCCESSFUL |
| Window suite ×3 | `scripts/run-suite-on-xvfb.sh --tests '*GraphWorkspaceWindowModelShould'` | **118 tests, 0 failures, 0 skipped**, three times |
| Full suite | `scripts/run-suite-on-xvfb.sh` | **exit 0 — 1459 tests, 0 failures, 4 skipped, 13m25s** |
| Diff scope | `git diff --stat` | one file, `+10`, production only where intended |

The bare-`JFrame` arms of §3 were measured **before** the fix and are what justify it: the shipped
sequence lost the position in 1 of 60 round trips, the sequence with this re-assert in 0 of 60, in the
same Xvfb + openbox environment the suite uses.

## 10. Consequence found later: the frame-operation contract test

Task 017's module-suite run surfaced `GraphWorkspaceWindowModelShould.rawFrameOperationsFollowSpecifiedOrder`
failing. The guard added in §6 inserts one `setBounds`/`validate` pair when the window manager has not
honoured the saved rectangle by the time the peer is mapped, and that test asserted the *exact* operation
sequence of `rawRestoreWindowed`.

The specified sequence therefore changed, and the test now asserts the mandatory prefix exactly and permits
at most one trailing re-assert of the same rectangle, with a new test that forces the branch deterministically
by reporting displaced bounds. The guard itself is unchanged and still delivered: the failure was in the test
that describes the contract, not in the fix.

Two corrections worth keeping with this record:

- **The §7 honest limit was right and should be trusted.** The test passed in every verification run here
  because the guard only fires when the window manager misplaces the frame. A conditional production change
  needs its *contract* tests updated at the time, not discovered later.
- **A single-green control run is not evidence when the budget it races is 20 s.** While investigating,
  running this class through plain `gradle` instead of `scripts/run-suite-on-xvfb.sh` made
  `panDuringPresentationIsPreservedThroughExit` fail 3/3, and one passing run with the guard compiled out
  looked like proof the fix was the cause. On the Xvfb script the class is 118/118 green with the guard,
  twice. It was the documented live-desktop failure mode.

Residual unchanged: `f11RestoresBoundsAndExtendedStateFromNormalWindow` still fails occasionally (1 of 3
Xvfb class runs during 017). As §7 said, the guard reduces the race; it does not prove it away.
