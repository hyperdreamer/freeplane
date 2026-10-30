# 015 — F11 exit can lose the window position: diagnostic determination

Task Identifier: `015-f11-restore-position-race`
Ticket ID: none (no external ticket exists for this task)
Branch: `pm/graph-f11-bounds-015` (off `bd2be219d8`)
Status: **diagnosis complete, no fix implemented** — the repair is a production change and needs approval (§6)

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

## 6. Proposed repair (production, needs approval)

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

**Not implemented.** Production source is byte-identical to `bd2be219d8`
(`git diff --exit-code bd2be219d8 -- freeplane_plugin_graph/src/main/java` → exit 0), because this is a
product behaviour change and the standing rule is that those need explicit approval.

## 7. How the repair would be verified, and the honest limit

| Check | Strength |
|---|---|
| Bare-`JFrame` probe, 60 rounds per arm | already run: 1 loss shipped vs 0 with the re-assert |
| `GraphWorkspaceWindowModelShould` (118 tests) | regression only — it already passes without the fix |
| Full suite | regression only — the failure rate is ≈1 in 5 |
| Mutation: make the re-assert a no-op | would show the branch is exercised only when the window manager misbehaves |

**Honest limit:** no deterministic reproduction inside Freeplane exists. The evidence is a
product-external reproduction plus the exact signature match, so the fix is justified by mechanism and by
a 60-round arm, not by a red-to-green Freeplane test. Anyone who wants stronger evidence should build the
reproducer into the product's own fixture (drive `rawRestoreWindowed` against a deliberately slow window
manager), which is a bigger job than the fix.

## 8. State on this branch

Record only. No test, production or build file is modified: `git status` is clean apart from this file.
The instrumentation used for §2 and §3 (a `System.out` probe in `rawRestoreWindowed` and a throwaway
repeated-round-trip test) was reverted and the tree verified byte-identical to `bd2be219d8`.
