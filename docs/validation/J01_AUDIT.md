# J01 source and implementation-readiness audit

Date: 2026-10-20. Audited main: `37f51baf8b34b069a0c83e93b5780a6c9f676c16`.
The approved M1 branch starts at that revision. This audit does not certify new runtime execution.
Phase I squash `f06197a75a2e420fb87153c0e64e3fe2a28841ea` has the same app/scripts content as
feature delivery `aebddfd8adf0bab2954af9918140d668269d0a1f`; main subsequently added documentation.

## Architecture and authorities

MainActivity/AppHostViewModel retain one application graph, F/H and current Reader lifetime.
AppGraphFactory closes owned Room after graph users stop; application DataStore is borrowed.
F owns bounded listings/registration/favorites. G owns serialized rendering, latest pending
request, frame leases and actual presentation acknowledgment. The host owns navigation,
Activity/picker/window/lifecycle coordination. Settings observes authoritative preferences.
SAF persists READ only and bounds non-seekable read copies. Room owns registry/favorites;
SharedPreferencesReaderPositions alone owns current durable registered page positions.
Legacy Room position fields and DataStore language remain compatible but inactive authorities.
Locale uses primary es/ca/en, unsupported -> Spanish. Policy B includes only the settings protobuf.

The nonexported isolated :pdf worker has separate PID/UID/epoch, bounded pixel transport and
intentional cancellation retirement. D04 distinguishes supersession from native cancellation.
Completed late output is discarded exactly once; candidate session reuse requires fresh native
validation on resume. No isolated-worker coverage can be inferred from app/JVM probes.

## Issue register (triage proposals, not reproduced failures)

| ID | Observation and source | Status / proposed priority | Owner and next action |
|---|---|---|---|
| J-AUD-01 | Release/tag/APK exist, while docs/releases/MVP_RELEASE.md, status/roadmap/changelog retain pending-publication wording | Confirmed documentation discrepancy / P2 | J14 dated reconciliation separately; preserve historical snapshots. No release changes in M1. |
| J-AUD-02 | AppRuntimeLimits.kt and core/foundation/ReaderPolicies.kt constrain copies/bitmaps, not total main+worker memory | Confirmed evidence gap; runtime risk UNCONFIRMED | J04 characterize both PIDs; physical 2GB conclusion requires authorized hardware. |
| J-AUD-03 | library/LibraryController.kt messages uses Channel.UNLIMITED | UNCONFIRMED backlog/resource risk | J05 measure capped bursts before proposing behavior changes. |
| J-AUD-04 | data/foundation/SharedPreferencesReaderPositions.kt State retains per-document writer/sequence maps | UNCONFIRMED long-session growth risk | J04/J05 measure distinct-document retention; no demonstrated leak. |
| J-AUD-05 | PdfWorkerConnection.releaseOwner retains source when death is unobserved | Intentional safety guarantee; cleanup characterization pending | J06 observe actual death/cleanup; never force release on timeout. |
| J-AUD-06 | ReaderNativeIntegrationTest records render/heap/bitmap observations, not full latency distributions or process PSS | Confirmed measurement gap | J03/J04 precise endpoints, raw samples, overhead and matched conditions. |
| J-AUD-07 | docs/acceptance/PDF_WORKER_COVERAGE_FEASIBILITY.md documents absent worker probe export | BLOCKED / NOT MEASURED | J11 retain process attribution; any bridge/protocol/build change needs separate approval. |
| J-AUD-08 | I/Phase C acceptance records retain itemized physical, backup/restore and D2D gaps | Confirmed evidence gap | J10/J15 guarded scenarios and owner decisions; release publication is no waiver. |

Previously verified tag `v1.0.0-mvp` points directly to main (lightweight tag). Public release
lists PartiturasPDF.apk and source archives. Signing/distribution suitability were not independently
verified. Recorded CI99 attributes 630 JVM and407 ordinary Android tests per API26/27/36; CI101
records mandatory post-merge PASS with optional compatibility skipped. These are attributed
historical results, not M1 test counts. General owner tablet satisfaction is not itemized acceptance.

No new production defect is claimed from source suspicion. No broad refactoring recommended.
J08 is DEFERRED BY OWNER TO PHASE R, including new accessibility/visual/UX review. Existing tests
remain mandatory. See J_PHASE_PLAN.md for approved M1 execution and later milestone boundaries.
