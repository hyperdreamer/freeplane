# Correctly Rounded Public Hull Centroids

## Decision

The public `computeHulls` centroid contract is correctly rounded binary64 area-centroid publication. Independent high-precision decimal and exact integer-rational oracles show that the existing public expanded-hull fixture rounds to raw y bits `0x40325e018e582689`. The former `0x40325e018e582688` expectation was an incorrect oracle value, not a required tie result.

## Approaches considered

1. Preserve `0x40325e018e582688` with a leading-term override. Rejected: it returns a value below the exact midpoint decision and violates the stated correctly-rounded contract.
2. Remove the leading-term override and let the represented residual comparison decide every non-equal case, retaining ties-to-even only when the represented residual equals the half-gap. Selected: smallest correction, matches the existing tagged-expansion arithmetic, and preserves Java 8/private-API constraints.
3. Replace the centroid implementation with a general arbitrary-precision or alternate centroid algorithm. Rejected: unnecessary architectural expansion, forbidden imports/runtime dependencies, and greater regression surface.

## Implementation boundary

`GraphGeometryEngine.roundingDirection` must return the candidate only when the represented residual is below the half-gap or exactly equal with an even candidate. A residual strictly greater than the half-gap must advance to the adjacent candidate regardless of the candidate's parity. The existing `TaggedSum.compareMagnitude` full-expansion subtraction remains the comparison mechanism.

`HullGeometryShould` must assert the corrected public base fixture bits `0x40325e018e582689` and retain the nearby public ULP regression at `0x40325e018e58267d`. No production or test path outside `GraphGeometryEngine.java` and `HullGeometryShould.java` is changed by the implementation commit.

## Verification boundary

The red phase changes the base assertion first and proves inherited `e5c1fdb3` still publishes `0x40325e018e582688`. The green phase removes the override, then runs the named tests, focused geometry and bundle gates, XML aggregation, Java 8 bytecode, public API/import scans, and external exact-oracle cases including the base fixture, nearby ULP variants, translation, orientation reversal, and extreme spans.
