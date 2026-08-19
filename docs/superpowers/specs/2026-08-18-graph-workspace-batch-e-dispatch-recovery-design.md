# Graph Workspace Batch E Dispatch-Recovery Design

## Purpose

Recover Batch E after the prior continuation reached `DISPATCH_MISMATCH_BLOCKED` during its Task 25 re-review dispatch. The valid Task 25 remediation commit `c7d4e898e48b0f5d6aab1bc333d182b844941ac9` is preserved as an immutable source baseline. The malformed re-review child, its report, and all other reports/prompts from the blocked run are diagnostic artifacts only and are not evidence for the successor run.

## Baseline And Isolation

The successor starts from the clean recovery branch containing `c7d4e898e48b0f5d6aab1bc333d182b844941ac9`. It uses a distinct ignored SDD run root and a distinct plan digest. The terminal run rooted at `.superpowers/sdd/2026-08-18-graph-workspace-batch-e-continuation` remains byte-preserved. The successor's first task independently audits the exact remediation range `0db0db9930b982cfd782f8f98b8a302f426974b9..c7d4e898e48b0f5d6aab1bc333d182b844941ac9` from source, Git, and fresh test execution.

## Execution Sequence

1. **Fresh Task 25 audit and review:** a read-only Capable implementer verifies the exact four-file remediation range, reruns the focused and compatibility tests, and writes a fresh audit report without consulting blocked-run reports. An independent Frontier task reviewer then reviews that exact range. Any load-bearing finding is handled by the normal bounded fix/re-review loop in the successor run.
2. **Task 26:** after the fresh Task 25 gate passes, implement hit testing, safe search, and interaction intents with its existing ten-file allowlist and focused tests.
3. **Task 27:** after Task 26's independent review passes, implement keyboard traversal and virtual accessibility with its existing six-file allowlist and focused tests.
4. **Final review:** a fresh Frontier reviewer inspects the complete branch from the pinned merge base through the final HEAD and reconciles the successor's finding ledger.

## Contracts And Scope

The existing committed Batch E design and backlog remain normative for Tasks 25-27. This recovery adds no API, production behavior, compatibility fallback, or source path. The Task 25 audit may modify no source; any subsequent Task 25 fix is limited to the original ten Task 25 paths and is reviewed by a fresh child. Tasks 26 and 27 retain the exact public signatures, keyboard rules, accessibility requirements, geometry authority, safe-text rules, count behavior, and allowlists in the continuation plan.

Every dispatch stores the exact rendered prompt before spawn, passes only `{ prompt, cwd, tier }` to the runtime, records the returned session immediately, and compares the completed child's initial message with the stored bytes before admitting its report. A mismatch is terminal for that run and requires another fresh successor rather than a reroll in place.

## Verification

The successor plan must parse through the controller's real validator. Its preflight must start from the clean `c7d4e898e48b0f5d6aab1bc333d182b844941ac9` baseline with no unexpected source changes. The fresh Task 25 audit must record exact test counts and path boundaries. Tasks 26 and 27 must pass their red/green focused gates, compatibility gates, staged allowlist checks, and commits. Completion requires fresh independent task reviews, a reconciled finding ledger, a whole-branch Frontier review, clean Git status, and the full graph-plugin suite under Java 21.0.8-zulu.
