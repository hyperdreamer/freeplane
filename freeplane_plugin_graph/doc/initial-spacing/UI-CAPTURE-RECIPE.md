# Initial spacing application capture receipt

Status: NOT_MEASURED. Delivery gate: OPEN.

Use the actual application built from the implementation revision and artifact set in collection.json. Record that revision (git rev-parse HEAD at build), every runnable launcher/bundle artifact path and SHA-256, JDK/runtime, OS, display server, font/look-and-feel, application version, fixture input/projection identity and generation. A pre-evidence-commit implementation OID is valid only while its recorded source hashes and built artifacts remain unchanged.

Collect three paired same-build application captures outside the SDD lane:

1. Fresh map/new workspace: an enclosure with multiple direct nodes and a visibly wider candidate initial ring.
2. Nested/multi-map graph: wider content footprints, sibling/top-level packing, and a relationship between actual endpoints.
3. At least two active pins: exact pinned coordinates before and after stepping/restart, with the finite corrected frame visible.

Use zoom exactly 1.0. For every case pair the first displayed published worker frame with the actual first idle=true frame from the same unchanged build/input sequence. Keep original PNGs and their SHA-256; pair names must identify the same build and fixture. Record canvas logical-pixel coordinates and rendered-disc radii for the closest relevant same-enclosure unpinned pair, its measured boundary-to-boundary gap, and the corresponding model-node keys/frame sample. Record observed label boxes and pin markers where they affect interpretation. Keep raw device-pixel coordinates, OS display scale and Graphics2D device transform separately; do not call a scaled device-pixel measurement a logical-pixel measurement.

Receipt JSON must contain:

{
  "status": "NOT_MEASURED",
  "implementation_head": null,
  "artifact_sha256": {},
  "jdk_runtime": null,
  "os_display_server": null,
  "font_look_and_feel": null,
  "zoom": 1.0,
  "display_scale": null,
  "device_transform": null,
  "cases": [],
  "gap_claim": "24 model units at an eligible raw/corrected initial ring; logical-pixel evidence pending",
  "gate": "OPEN"
}

For each received case append an object with case ID, fixture and input hashes, workspace/generation, active pin node keys and exact coordinates, frame sample/stage and idle flag, screenshot paths/hashes, selected node keys, logical-pixel centres/radii/measured surface gap, device coordinates/transform, notes on label boxes and worker repairs, and model-frame receipt link. Keep settled gaps descriptive and preserve every decrease/negative endpoint gap.

Only after all three paired captures match collection.json and all required fields and measurements are present may the external delivery reviewer replace status with MEASURED and gate with PASSED. Any source/build mismatch, missing pair, nonfinite measurement, missing transform or unknown frame provenance keeps the gate OPEN. No capture may be inferred from the headless acceptance log.
