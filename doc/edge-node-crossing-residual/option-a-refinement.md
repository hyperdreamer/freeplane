# Option A refinement analysis

Task identifier: `2026-10-26-edge-node-crossing-residual`

## What the per-frame diagnostics establish

| Condition | Frames | Outcome | Steady state |
| --- | ---: | --- | --- |
| seeding repaired nodes into the engine | 88 | **fails at frame 86**: `verif=64` = `MAX_VERIFICATIONS_PER_FRAME`, `verificationBudgetExhausted=true` | never reached |
| control, no seeding | 3204 | **passes**, idles | `rounds=4 verif=28 work=4786 detected=12 resid=8` |

Narrowing the seed from "all corrected nodes" to "only the nodes the correction moved" produced a byte-identical
trace, so the perturbing act is the teleport itself, not its breadth.

## Why A-naive is harmful here

The two-map fixture tolerates up to 8 residual edge conflicts per frame and still idles after 3204 frames. Teleporting
repaired nodes changes the arrangement enough that the next frame detects a different conflict set, so the correction
answers with more repairs, which are teleported again. On the reported workspace that is exactly what is wanted (it
converges in 3130 frames with zero residual); on the two-map fixture it turns a tolerant steady state into a frame
that cannot finish its verification allowance.

So A is not "wrong", it is **unconditional**, and unconditionality is what breaks an existing fixture.

## Recommended bounded form — and it is now a small, well-specified change

Seed **only when a repair repeats**, not on every repair:

- Keep a small bounded record of recent repair identities per node, e.g. the last two accepted `Repair` identities in
  a frame window.
- If the accepted repair for a node repeats an identity already seen in that window, that node is oscillating: seed
  its repaired position so the relaxation cannot undo it again.
- Otherwise do not seed at all, which restores the exact control behaviour on the two-map fixture.

This targets the measured pathology (one pair swapping every frame) and leaves every non-oscillating layout untouched,
including the 3204-frame two-map trajectory and therefore `TWO_MAP_FIRST_IDLE_STEP = 3103`.

## Acceptance for that change

1. The 29-disc reproduction reaches `idle=true` with `residual=0` (it does under unconditional A, so a conditional
   seed must not lose it).
2. `TypedForcesShould.twoMapWorkspaceSettlesToIdle` stays green with the control trace unchanged — ideally proven by
   comparing the per-frame diagnostics above against the conditional-seed run.
3. No other fixture changes; `settled-reference.csv` stays byte-identical.

## Status

Not implemented. The unconditional patch is preserved as `option-a-experiment.patch`; the two measurements above are
the specification for the conditional variant.
