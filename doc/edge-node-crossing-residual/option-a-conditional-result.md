# Conditional seeding attempt: repetition is not a usable discriminator

Task identifier: `2026-10-26-edge-node-crossing-residual`

## What was tried

Repair-signature repetition as the seeding trigger: normalise each accepted repair to `kind + sorted node ids`,
remember the signatures seen on the previous frame, and seed the repaired centres of any node whose signature
reappears. Nodes repaired once are never seeded. Patch preserved as `option-a-conditional-attempt.patch`.

## Result — negative on both counts

| Case | Unconditional seed | Conditional (repeat) seed | Control, no seed |
| --- | --- | --- | --- |
| two-map fixture | fails at frame 86, `verif=64` | **fails, same assertion** (`verificationBudgetExhausted` true) | passes, idles at 3203 |
| 29-disc workspace | `steps=3130 idle=true resid=0` | `steps=42413 idle=true resid=4` | never idles |

The conditional form is strictly worse than both alternatives: it does not protect the two-map fixture, and on the
reported workspace it degrades from 3130 frames with zero residual to 42 413 frames with four residuals.

## Why repetition cannot distinguish the two cases

The two-map fixture legitimately accepts repairs in every frame of a 3203-frame settle (`rounds=4` throughout) and
idles correctly at the end. Repeating repair signatures are therefore **normal operation**, not evidence of a limit
cycle. A rule keyed on repetition fires on healthy layouts and cannot separate them from the pathological one.

The real discriminator is not *whether* repairs repeat but *whether idle progress stalls*: the two-map fixture's
distance-to-idle decreases over 3203 frames, while the reported workspace's residual oscillates with no downward
trend across 30 000. Any seeding trigger must be keyed on the absence of progress, not on the presence of work.

## Consequence for the design

Option A remains directionally proven by the unconditional result (`steps=3130 idle=true resid=0`). What is missing
is a trigger that fires only on non-convergence. The two candidate triggers, in order of expected cost:

1. **Stall-based trigger.** Seed only after the idle measurement has failed to improve for a bounded number of
   consecutive frames. This uses the existing `PerceptualIdlePolicy.IdleMeasurement`, needs no new identity
   bookkeeping, and provably cannot fire during the two-map fixture's monotone 3203-frame descent — but the
   "improvement" predicate must be specified precisely before it is trusted.
2. **Time-boxed escalation.** Run without seeding for a bounded frame budget; if idle is not reached, enable seeding
   permanently for that workspace. Trivial to implement and impossible to misfire on a fixture that idles inside the
   budget, at the cost of a slow first settle on genuinely oscillating workspaces.

The second is the safer experiment because it cannot perturb any layout that converges on its own, and the two-map
fixture demonstrably does. It should be tried before the stall-based predicate is designed.

## Status

Unconditional A: proven on the target, breaks one fixture. Conditional-by-repetition: rejected by measurement,
both cases worse. No seeding variant is adopted. The audited tree is unchanged and green.
