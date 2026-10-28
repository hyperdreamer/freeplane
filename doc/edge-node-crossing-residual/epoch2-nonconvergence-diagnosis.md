# Non-convergence diagnosis: a C1 swap repair that the relaxation undoes every frame

Task identifier: `2026-10-26-edge-node-crossing-residual`
Defect: `graph-layout-non-convergence-29-discs`
Status: **diagnosed, not fixed.** Root cause mechanism identified with measurement; no fix is authorised by this
document.

## Reproduction

Add `<graph_group version="1"/>` to node `ID_1271861869` in `Axiomatic_Set_Theory.mm` and the workspace reaches
29 discs and never idles. Remove it and the same maps idle at step 178. Evidence:
`doc/edge-node-crossing/epoch2/minimal-experiment.txt`.

## What actually happens each frame

The correction is not failing. It succeeds every single frame:

```
TRACE step=1    idle=false resid=0 repaired=1 rounds=1 work=1244 keys=[]
TRACE step=24   idle=false resid=0 repaired=1 rounds=1 work=1244 keys=[]
TRACE step=400  idle=false resid=0 repaired=1 rounds=1 work=1244 keys=[]
TRACE step=1600 idle=false resid=0 repaired=1 rounds=1 work=1244 keys=[]
TRACE step=2400 idle=false resid=0 repaired=1 rounds=1 work=2871 keys=[]
```

`resid=0` means no edge conflict survives the correction. Every frame it repairs exactly once, so the residual set
is empty on publication. Yet `idle=false` forever, because the repair itself is movement: the perceptual idle
policy never sees the positions stop changing.

## The repair that repeats

The repeated repair is a **C1 swap**, and at steps 400 and 1600 it is the same pair:

```
step 400 : ID_1222855984 ID_1887656421 SWAP  from=(64.72,38.55) to=(46.37,26.41) |d|=22.00
step 1600: ID_1222855984 ID_1887656421 SWAP  from=(58.91,37.36) to=(41.89,23.41) |d|=22.00
```

`|d| = 22.00` is exactly `2r + MIN_GAP`, the minimum legal centre separation, consistent with two discs
exchanging places. The pair is `Theorem: ⋃α ≤ α` and `Theorem: initial segement` — the same nodes whose edge the
user photographed.

## Mechanism

1. Two chords cross in a given frame, so `EdgeConflictCorrection` sees a C1 conflict.
2. The canonical corner-cycle swap exchanges two of the four chord endpoints. The crossing disappears and the
   residual is empty.
3. The force relaxation does not share that preference. Its equilibrium is the arrangement that crosses, so the
   next frames pull the pair back toward the swapped-out configuration.
4. The crossing returns, the correction swaps again, and the frame publishes `resid=0` with a 22 wu displacement.

The two subsystems are in a stable two-cycle. Neither is individually defective: the correction satisfies its
contract, and the relaxation satisfies its own. They disagree about the equilibrium, and nothing detects the
disagreement because each frame independently looks correct.

## Why this defeats both delivered changes

The routing control-position group and the relocation ring expansion both operate inside a frame and both do
their jobs. Non-convergence is a property of the *sequence* of frames, which neither addresses. Adding states to
the relocation search cannot help because the correction already succeeds; the failure is that its success is
reversible.

## Candidate fix directions, unranked and unauthorised

- **Break the cycle by detecting it.** If the same repair identity with the same moved-node set is re-applied
  within a short frame window, treat it as non-convergent: keep the repair, stop re-deriving it, and let the idle
  policy observe stability. Requires a bounded, deterministic record of recent repairs.
- **Make the repair durable.** Feed accepted C1 swaps (and C2 translations) back into the layout state as
  constraints or anchors rather than as post-hoc position edits, so the relaxation starts from the repaired
  arrangement instead of fighting it.
- **Make C1 swaps force-aware.** Reject a swap whose resulting arrangement has higher force potential than the
  current one, so the correction never produces an arrangement the relaxation will immediately undo.
- **Escalate instead of oscillating.** After N repeats, publish the residual and surface it, rather than
  publishing `resid=0` on a frame that will be undone.

Each is a design change with its own acceptance question. None can be adopted without a design review, because
the first and fourth change what the canvas reports and the second and third change layout behaviour that the
frozen settled fixtures pin.

## Acceptance question the fix must answer

"With `ID_1271861869` marked as a group, does the workspace reach `idle=true` within a bounded frame count, and
does the published settled frame have every published path clear of every non-incident disc?" The answer today is
no on the first and unused on the second, because no settled frame exists.
