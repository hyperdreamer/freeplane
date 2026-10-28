# Seeding escalation — regression test and mutation evidence

Task identifier: `2026-10-26-edge-node-crossing-residual`

## Tests added to `LayoutWorkerShould`

| Test | Asserts |
| --- | --- |
| `pinTheEscalationBudget` | `SEED_ESCALATION_FRAMES == 6000`, so a change to the budget is deliberate |
| `withholdSeedingWhileTheLayoutCanStillConverge` | 200 frames never seed |
| `escalateToSeedingOnlyWhenTheLayoutExceedsTheFrameBudget` | the last frame inside the budget does not seed, the first frame past it seeds exactly once |
| `restartTheEscalationCountForANewRequest` | a fresh `submit` restarts the count and stops seeding until the budget is spent again |

Observability comes from a `SeedingCountingEngine` fake that records `seedPositions` calls, so no production
accessor was added.

## Mutation evidence — every mutation is caught

| Mutation | Failing tests |
| --- | --- |
| budget `6000L` → `0L` (seed immediately) | withhold, escalate, pin, restart (4) |
| budget `6000L` → `1000000L` (never seed) | escalate, pin, restart (3) |
| delete the reset in `submit` | escalate, restart (2) |

Two defects were found by writing these tests, not by reading the code:

1. **A vacuous test.** The first version derived its frame budget from `LayoutWorker.SEED_ESCALATION_FRAMES`, so
   raising the constant to `1000000` merely made it step a million frames and still pass. It now pins `6000`
   literally, which is why mutation 2 fails.
2. **A real production bug.** The escalation reset was keyed on `!hasRequest`, which is true only for the very
   first request ever received; any later request inherited the previous request's frame count. The reset now
   happens in `submit` and in `restart`. `restartTheEscalationCountForANewRequest` fails without it.

## Full-suite position

`1460 tests, 5 failed, 4 skipped`. All five are the inherited set: the two fresh-JVM acceptance drivers, their
suite wrapper, and the two `GraphWorkspaceWindowModelShould` window tests, which were re-run on the clean tree and
fail there identically. `settled-reference.csv` remains `e9e988b7…`.

The change is now defended by tests that fail when the mechanism is removed.
