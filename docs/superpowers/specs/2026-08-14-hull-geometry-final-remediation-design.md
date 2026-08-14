# Hull Geometry Final Numerical Remediation Design

- **Task Identifier:** 2026-08-10-graph-workspace
- **Original backlog task:** Task 18, Compute deterministic hull and attachment geometry
- **Terminal predecessor run:** `.superpowers/sdd/2026-08-13-hull-geometry-numerical-remediation`, revision 89 `FINAL_BLOCKED`
- **Starting implementation:** `2fee7e4562be73888b31f90fd1bdb0b1d34ac8f9`
- **Original merge base for final review:** `04d39279c4eed35254b0f234c8ec0c27c79a04bf`

## Goal

Resolve the three load-bearing numerical findings reproduced by the final Frontier review while preserving all already-approved Task 18 geometry behavior and the public API.

## Scope And Finding Identity

The predecessor run is immutable and remains terminal. A new committed implementation plan and fresh deterministic SDD run will carry:

- `F-1`, robust mixed-exponent orientation: fixed and subject to final reconciliation;
- `F-9`, collinearity-paired segment bounds: fixed and subject to final reconciliation;
- `F-10`, subtraction-residual ray boundary behavior: fixed and subject to final reconciliation;
- `F-11`, large-offset enclosure-centroid overflow: open;
- `F-12`, representable smoothing-tangent minor component lost: open;
- `F-13`, adjacent-double final ray rounding error: open.

The final report reused report-local `F-10` for the distinct adjacent-double residual. Canonical state remapped that residual to `F-13`; the raw final report remains unchanged.

The successor implementation allowlist is exactly:

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/NodeGeometry.java`;
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/HullGeometry.java`;
- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/geometry/GraphGeometryEngine.java`;
- `freeplane_plugin_graph/src/test/java/org/freeplane/plugin/graph/geometry/HullGeometryShould.java`.

No other production or test file may change during the three implementation tasks.

## Constraints Preserved From Task 18

- Java source and bytecode remain Java 8 compatible. Builds use Zulu JDK 21.0.8 and `gradle`, not Maven or the wrapper.
- Production geometry code may use only Java 8 primitives, `java.lang`, existing `java.util`, `java.awt.Shape`, `java.awt.geom.Path2D`, and immutable projection values already used by `GraphGeometryEngine`.
- Production code must not use `java.math`, `BigDecimal`, `Math.fma`, third-party numerical libraries, new utility classes, public helpers, overloads, legacy fallbacks, or fixture-specific branches.
- High-precision oracle programs remain external under `/tmp`; checked-in tests pin literal finite inputs and expected binary64 values or raw bits.
- Every finite `LayoutPoint` accepted by the public API remains valid input. Intermediate overflow, underflow, NaN, cancellation, or lost minor terms must not alter a representable finite answer.
- Preserve canonical polygon order, the absolute `1e-9` orientation/SAT policy, positive-zero separation, positive-X deterministic ties, exact immutable collections, defensive path copies, and the four-world-unit smoothing cap.
- Each correction follows literal RED-before-GREEN TDD and creates one new, non-amended commit.

## Architecture

Each numerical correction stays private to the class that owns the operation. There is no shared arithmetic utility and no parallel old/new execution path.

### 1. F-13: Finish Ray Boundary Rounding In `NodeGeometry`

`NodeGeometry.boundaryToward` already represents coordinate differences, products, and final additions with private tagged terms. The remaining defect is in the dominant-coordinate quotient correction: enough information is discarded before `finalSum` to select the adjacent binary64 value correctly.

The correction will carry the normalized norm and quotient correction as a sufficient exponent-tagged expansion through the final center-plus-radius addition. Final rounding remains explicit ties-to-even and must work for normal and subnormal outputs without `Math.fma` or decimal arithmetic.

The regression uses these exact finite values:

```text
center = (-2.30665597377219E56, -2.2117294275241294E-19)
toward = (7.09268585234678E-75, -2.3275574432766924E12)
radius = 1.0283265339240514E57
```

The published X coordinate must have raw bits `0x4bc043fc003baf8b`; the inherited implementation returns `0x4bc043fc003baf8c`. The Y coordinate and all existing ray fixtures remain unchanged.

### 2. F-12: Preserve Smoothing Minor Components In `HullGeometry`

`HullGeometry.pointAlong` currently handles an overflowing edge subtraction by dividing both coordinates by one world-coordinate scale. For an edge with a dominant span near `2e308`, that operation underflows a small but load-bearing Y delta before multiplying by the four-unit cut distance.

The correction will keep each edge component in an exponent-tagged private representation through normalization, length computation, and multiplication by the requested cut distance. Only the final endpoint coordinates are rounded to binary64 and added to the corner. The four-unit cap remains the sole smoothing-distance policy; a zeroed minor component is not an acceptable substitute when the rounded endpoint is representable.

The regression constructs the valid polygon beginning:

```text
(-1.0e308, 0.0)
( 1.0e308, 2^-52)
( 1.0e308, 1.0e100)
(-1.0e308, 1.0e100)
```

The first outgoing quadratic endpoint in `smoothPath()` must be `(-1.0e308, Double.MIN_VALUE)`, verified through `PathIterator`. The inherited endpoint is `(-1.0e308, +0.0)`.

### 3. F-11: Compute Enclosure Centroids Relative To A Local Origin

`GraphGeometryEngine.centroid` currently evaluates absolute shoelace products. Valid large-offset polygons therefore produce `Infinity` and `NaN` even when their area and centroid are finite and representable.

The correction will choose the polygon's canonical first vertex as a deterministic local origin. It will represent origin-relative coordinate differences, cross terms, twice-area, and first moments with private power-of-two-scaled or exponent-tagged terms. Both moments use the same represented area, and the final local centroid is added back to the origin with compensated binary64 rounding.

The implementation must retain the polygon area centroid. It must not substitute a vertex average, enclosure anchor, clamp, exception fallback, or alternate legacy path.

The public regression calls `GraphGeometryEngine.computeHulls` with four direct nodes at:

```text
(b,     b)
(b + d, b)
(b + d, b + d)
(b,     b + d)
```

where `b = 1.0e200` and `d = 2^620`. Construction must succeed and publish the representable label anchor `(b + d/2, b + d/2)`. The inherited implementation throws `IllegalArgumentException: Layout coordinates must be finite` from `centroid`.

## Task Decomposition

The implementation plan will contain exactly three independently reviewed tasks:

1. **Correct final ray rounding** — `NodeGeometry.java` and `HullGeometryShould.java`.
2. **Preserve smoothing minor components** — `HullGeometry.java` and `HullGeometryShould.java`.
3. **Make centroid arithmetic large-offset safe** — `GraphGeometryEngine.java` and `HullGeometryShould.java`.

The order isolates the two existing numerical owners before the newly authorized engine path. Each task has its own named RED regression, focused green gate, full `HullGeometryShould` and `HullIntersectionShould` gate, package verification, scoped diff inspection, and exact commit.

## Verification And Final Gate

For every task:

- prove the named regression fails against that task's inherited HEAD for the stated numerical mechanism;
- pass the named regression and both geometry test classes with `--rerun-tasks`;
- pass `:freeplane_plugin_graph:verifyGraphBundle`;
- aggregate all JUnit XML and require zero failures/errors;
- verify the seven geometry public classes remain Java 8 bytecode (major version 52);
- verify no new public geometry API and no `GraphGeometry.labels()` method;
- scan production geometry imports against the positive whitelist and require no `java.math`;
- require a clean task-scoped diff and one new non-amended commit.

After all three task reviews pass, a fresh Frontier reviewer examines the whole branch from `04d39279c4eed35254b0f234c8ec0c27c79a04bf` through the new HEAD. The final ledger must reconcile `F-1`, `F-9`, `F-10`, `F-11`, `F-12`, and `F-13` as absent. Completion requires `SPEC: PASS`, `QUALITY: APPROVED`, a clean worktree, and fresh full plugin/package verification.

## Error Handling And Recovery

- If a named regression passes before production edits, the task blocks because its RED gate is not falsifiable.
- Prompt bytes, tiers, report paths, commit ranges, and session IDs remain controller-pinned. Mismatched dispatch evidence is quarantined rather than trusted.
- A child that cannot satisfy the numerical contract within its task's allowlist returns `BLOCKED`; it must not broaden scope or add a fallback.
- The successor run receives one final-fix wave under the deterministic state machine. A residual after its mandatory Frontier re-review terminally blocks that run and requires another fresh plan rather than reopening state.
