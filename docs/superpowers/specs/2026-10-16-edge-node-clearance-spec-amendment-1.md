# Edge–Node Clearance — Specification Amendment 1 (fixture relationship)

**Status:** Amendment authorized by the user; supersedes one sentence of the approved
specification.

**Amends:** `docs/superpowers/specs/2026-10-16-edge-node-clearance-spec.md`,
SHA-256 `a18d4a88c8bdbebc41c70d57f9702a08d0e3307dd9f636b49b4cda6c62f56f5e`.
That file remains byte-identical and unedited.

**Task identifier:** `2026-10-16-edge-node-clearance`

## Reason for this amendment

§8.4.1 requires the installed-evidence fixture workspace to contain "just the A–B
relationship". That requirement cannot be satisfied by any valid workspace document:

- `freeplane_plugin_graph/src/main/java/org/freeplane/plugin/graph/workspace/model/GraphRelationshipRecord.java:24-25`
  unconditionally rejects a relationship whose endpoints share a map:
  `if (source.equals(target) || source.mapReferenceId().equals(target.mapReferenceId())) {
  throw new IllegalArgumentException("Relationships must connect distinct nodes across maps"); }`
- `WorkspaceXmlCodec.java:254-260` routes every persisted relationship through that
  constructor, so a same-map A–B relationship fails at read time.
- `OpenGraphWorkspaceAction` catches the failed open and shows the workspace chooser
  instead of the graph window, so the evidence agent can never find
  `graph-workspace-window` and terminates at the outer deadline.

The requirement therefore describes a fixture that the production model is designed to
reject. It is an internal contradiction in the specification, not an implementation
defect.

## What this amendment changes

**Superseded sentence** (in §8.4.1):

> Mark the nine leaf nodes with `<graph_group version="1"/>`; the workspace contains just
> the A–B relationship, no pins/enclosures, and a viewport showing the fixture.

**Replacement:**

> Mark the nine leaf nodes with `<graph_group version="1"/>`; the workspace contains no
> relationships (the `<relationships/>` element is present and empty), no pins/enclosures,
> and a viewport showing the fixture. The E3 A–B edge is supplied to the replay projection
> as the native connector, exactly as the typed replay fixture has modelled it since Task 5;
> the installed window therefore shows the same nine nodes and the same single undirected,
> arrow-less A–B connector.

## What this amendment does NOT change

Every other §8.4 requirement stands unchanged, in particular:

- the nine-node E3 fixture, its exact raw centers, node identities and map/workspace
  identities;
- the single installed `graph-workspace-window`, the real `LayoutWorker`/`LayoutSettleLoop`/
  `EdgeRouter`, and the raw uncorrected replay;
- `rounds = 1`, `verifications = 3`, `verificationWork = 252`, minimum clearance
  ≥ `3.0 - 1e-9`, no fallback and no exhaustion;
- the security-prerequisite checks, loader/class-definition protocol, Robot capture,
  digest/reference/viewport re-verification, deadline budget, cleanup ordering, artifact
  schema and the requirement that a human inspect the saved PNG;
- no production model change, no core change, no startup change, no new public API.

## Justification for omitting the relationship rather than relabelling it

The A–B edge in the E3 projection is already a native connector
(`EdgeContributor.nativeConnector`), not a workspace relationship; the projection is
constructed with an empty `RelationshipResolution` list. Omitting the relationship makes the
installed fixture agree with the projection the acceptance tests already validate, and it
does not touch the production relationship policy — which this plan forbids changing.

The rejected alternatives were:

- modelling A and B in two maps joined by a cross-map relationship, which would change the
  E3 identities that §6.1 and the replay helper pin;
- relaxing `GraphRelationshipRecord`'s same-map rule, which is a product decision outside
  this fix's authority.
