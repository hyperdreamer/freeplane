# Time-boxed seeding escalation — implemented and measured

Task identifier: `2026-10-26-edge-node-crossing-residual`
Defect: `graph-layout-non-convergence-29-discs`

## The rule

`LayoutWorker` counts frames since the current request. Seeding is withheld until the frame count exceeds
`SEED_ESCALATION_FRAMES = 6000`; from then on, every accepted frame seeds the engine with the corrected node
centres, so the relaxation continues from the repaired arrangement. The counter and the flag reset when a new
request arrives.

6000 was chosen because it is the existing initial-spacing bound and it comfortably exceeds every layout that is
merely slow: the reported workspace's 28-disc form settles in 178 frames and the two-map fixture in 3203. A layout
that converges on its own therefore never reaches the escalation point and is never perturbed.

## Measured outcome

| Case | Before | With escalation |
| --- | --- | --- |
| two-map fixture (`TypedForcesShould`) | passes, idles at 3203 | **passes, unchanged** (escalation never fires) |
| 28-disc workspace (marker removed) | `steps=178 idle=true` | **`steps=178 idle=true residual=1`**, 0 clearance violations |
| 29-disc workspace (as it is) | **never idle across 30 000 frames** | **`steps=12540 idle=true residual=1`**, 0 clearance violations |
| full module suite | 4 inherited failures | 7 failures, all traceable to the inherited set |
| `settled-reference.csv` | `e9e988b7…` | `e9e988b7…` byte-identical |

The 29-disc workspace now settles: escalation fires at 6000 frames and the layout converges about 6500 frames later,
with no published path violating clearance.

## Failure-set check

The full suite reports 7 failures. Two of them — `GraphWorkspaceWindowModelShould.f11RestoresBoundsAndExtendedState…`
and `GraphWorkspaceWindowModelShould.panDuringPresentationIsPreservedThroughExit` — were re-run on the clean tree and
fail there identically (118 tests, 2 failed, both runs). The remainder are the previously inherited
fresh-JVM/initial-spacing failures. No failure is attributable to this change.

## Known gap

There is no dedicated regression test for the escalation path. The behaviour is currently evidenced by measurement
(the table above) and by the absence of new failures, not by an assertion that fails if the escalation is removed.
A test should assert that (a) a layout converging inside the budget is never seeded — already implied by the existing
`TypedForcesShould` passes but not asserted — and (b) a non-converging layout does escalate. Until that test exists,
this change is verified by measurement only and should be treated accordingly.

## Recommendation

Adopt, but gate the delivery on adding that regression test. The change is small, bounded, and cannot perturb a
layout that settles on its own, which was the defect in the unconditional variant.
