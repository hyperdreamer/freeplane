# Option A experiment result: converges on the reported workspace, regresses the two-map fixture

Task identifier: `2026-10-26-edge-node-crossing-residual`
Defect: `graph-layout-non-convergence-29-discs`
Decision rule fixed in advance: convergence means A is the fix; non-convergence means the equilibrium itself prefers
a crossing arrangement and option C becomes mandatory.

## Verdict on the decision rule: A CONVERGES. Option C is not required.

The seeding implementation is preserved as `option-a-experiment.patch` (76 lines, three files). It was applied,
built, and measured, then reverted so the audited tree stays intact.

## Result on the reported workspace — the decisive number

| Condition | Result |
| --- | --- |
| before seeding, 29 discs | never idle; residual oscillated 0..4 across 30 000 frames |
| **with seeding, 29 discs** | **`settle: steps=3130 idle=true residualViolations=0`** |

So the answer to the acceptance question *"does the workspace reach idle within a bounded frame count, and does the
settled frame have every published path clear of every non-incident disc?"* is **yes on both counts** with A applied.
The layout now stops fighting the correction: when the engine continues from the repaired arrangement, it converges
instead of undoing the repair. My earlier reading — that the equilibrium might prefer the crossed arrangement — is
disproved.

## Regression: the two-map fixture

```
TypedForcesShould > twoMapWorkspaceSettlesToIdle FAILED
  org.junit.ComparisonFailure at InitialSpacingFrameAssertions.java:91
  assertThat(edge.verificationBudgetExhausted()).isFalse()  ->  was true
```

`InitialSpacingFrameAssertions.check` line 91 asserts that a frame did not exhaust its verification budget. With
seeding, some frame of the two-map fixture does exhaust it. Every other assertion in the focused set passed: the
frozen `settled-reference.csv` fixture, `EdgeRouterShould`, and `EdgeConflictCorrectionShould` were green, and the
full `TypedForcesShould` class passes again on the reverted tree.

## What this means

A is directionally right and empirically effective on the case that matters, but **it is not shippable as written**:
feeding corrected positions back changes how much correction work each frame performs, and on at least one existing
fixture that pushes the counted work to its allowance.

That is a bounded, diagnosable problem rather than a wrong direction — most likely the seed makes the correction
re-solve a conflict that the previous frame's own seed had already resolved, so work accrues where it previously
drained. The next step is not a new design cycle from scratch; it is a narrow question:

> When the engine already holds the corrected positions, why does a frame still consume enough verification work to
> hit the allowance on the two-map fixture, and which of the four counted allowances is reached?

## Recommended next increment

1. Instrument the two-map fixture to report `verifications`, `verificationWork`, `rounds` and which allowance is
   reached, with and without seeding, per frame.
2. If the seed causes redundant re-verification, avoid re-seeding when the accepted positions are unchanged from the
   previous frame, or seed only the nodes whose centres the correction actually moved.
3. Re-run the 29-disc reproduction and the two-map fixture together; A is acceptable only when both are green.

Do not adopt the patch on the strength of the reported-workspace result alone. The measured pair of outcomes above is
the complete evidence: a decisive win on the reported defect, one hard regression on an existing fixture.
