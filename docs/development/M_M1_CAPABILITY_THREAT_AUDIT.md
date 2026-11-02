# M-M1 capability and threat audit — 2026-11-02

**DOCUMENTATION ONLY — READY FOR OWNER REVIEW, NOT FORMALLY ACCEPTED.**
TEST IMPLEMENTATION: NOT STARTED.
TEST EXECUTION: NOT RUN (M-M1 documentation-only; execution explicitly prohibited).

## Authority, revision and inspection boundary

The supplied owner request authorizes only this document, the contract/approval
matrix and the critical test plan. Supplied M-D01–M-D14 decisions are mandatory
future-design constraints, not installed code. Recommendations below are
**PROPOSED — OWNER APPROVAL REQUIRED**. The supplied Codex prompt describes the
same milestone and cannot expand the owner's three-file/no-execution boundary.

Actual M source/remote base: `7f00a793a06e988859dbefc3e21814c21ec727dd` on existing
`feat/pdf-safe-write-m`, using the clean main checkout at `/workspace/PartiturasPDF`.
Its existing remote branch was checked out locally; no alternate development
branch or worktree was introduced. The restricted fetch mapping required explicit
M/main fetches and a command-local mapping to establish tracking. No main/K/L
merge, rebase, reset or cherry-pick occurred. Fetched origin/main:
`c645e07656151b8e32927ed8a634b4f7ae6a0e78`; main...M is **1/0**, merge base is the
M base. Other worktrees, including L, were not modified. No M-M1 output existed
at the three target paths before this task.

All source references below describe that M revision, not newer K/L code. Read-only
inspection used git status/branch/rev-parse/log/show/diff, file inventories,
rg searches and numbered source reads. No build, test, validator, fixture checker,
coverage tool, CI, emulator, ADB, install or backup operation was executed.

The three supplied documents were read completely. The original
PHASE_M_SAFE_PDF_WRITING_CODEX_HANDOFF is not attached and was not found in the
tracked inventory. The supplied scope document supplies all 20 scenario seeds;
the test plan preserves them but cannot certify wording parity with an unavailable
original. This is a document-provenance limitation for owner review, not a reason
to invent missing original requirements.

## Verified capability inventory

Paths prefixed `src/` below mean `app/src/main/java/com/abdev/partituraspdf/`.
Line ranges refer to the inspected M revision. Existing tests are source evidence,
not execution evidence for M writing.

| Capability | Actual source evidence | Installed versus M requirement |
|---|---|---|
| Logical/provider identity | src/core/document/DocumentModels.kt:5–27,49–55 | DocumentRef registered identity and opaque StorageDocumentRef exist; no operation-local M identity |
| Byte/revision values | src/core/document/DocumentModels.kt:62–95 | SnapshotEvidence validates representation only; VerifiedSnapshot requires DocumentRef. No trusted producer is thereby installed |
| Registry/metadata | src/core/document/DocumentRepositories.kt:12–44,57–68; src/data/metadata/RoomDocumentRegistry.kt:19–85 | Explicit register/resolve/observation/rebind implemented; no provider mutation. Preserve favorite/position ownership; do not rebind Save As |
| Typed failures | src/core/result/Outcome.kt:3–20 | Outcome and access/resource/conflict errors exist. No BUSY, journal/publication state or uncertainty-specific result |
| Read-only grants | src/storage/saf/SafTreeGrantAccess.kt:11–49,52–93 | Persist/release READ only. Listing a grant is not provider availability or WRITE authority |
| Direct provider read | src/storage/saf/SafProviderRead.kt:24–84 | Resolver mode r, owned descriptor transfer, seek probe and typed access errors. FileNotFoundException is not inferred deletion |
| Seekable fallback | src/storage/api/DocumentReadAccess.kt:6–27; src/storage/saf/SafDocumentReadAccess.kt:25–68,70–137 | Complete bounded private read copy for nonseekable input; cacheDir/saf-read-copies, not sealed/hash-attested snapshot |
| Observed-byte limit | SafDocumentReadAccess.kt:79–113,140–148,170–172 | Overflow-safe observed bytes; ENOSPC classification; existing buffer **8192 B**, not M's approved 65536 B. Protected adapter unchanged |
| Handle ownership | src/storage/api/ReadHandle.kt:8–12,15–63 | OWNED/TRANSFERRED/CLOSED, independent acquiring wrapper; not a multi-lease snapshot owner. SnapshotLease provenance exists but is registered-revision-bound |
| Read-copy cleanup | src/storage/saf/SafReadCopyRecovery.kt:7–35,37–70 | Process-local active paths and selective cache orphan retry. NOT a durable M journal, cross-process lock or uncertain-publication retention policy |
| Renderer input bridge | src/pdf/api/PdfReadSource.kt:9–28 | Owned seekable handle; SnapshotLease revision must match document. Provider/read-copy may have unknown revision. Transient immutable M bridge unresolved |
| Existing engine/lifecycle | src/pdf/api/PdfReadEngine.kt:7–48; src/pdf/api/ReaderRoute.kt:7–15; src/pdf/android/isolated/IsolatedPdfReadEngine.kt:14–82 | Isolated read/render adapter consumes handle; failure cleanup/suppression and sessions exist. Not a transformer or independent output validator |
| Resource ceilings | src/app/di/AppRuntimeLimits.kt:6–20 | Read copy64MiB, dimension4096, bitmap4Mi pixels. NOT M output/aggregate/free-space/parser budgets |
| A0 writing contracts | docs/architecture/CONTRACTS.md:37–40,119–169,183–194,449–528 | Planned snapshot/publisher/validator/workflow/recovery names only. No corresponding installed Kotlin declarations found in production inventory |
| Editing implementation | Production pdf/api inventory: PdfReadEngine, PdfReadModels, PdfReadSource, ReaderRoute, ReaderBudget; gradle/libs.versions.toml:1–45 | No approved editing engine/dependency, composer, annotation writer or production PDF validation pipeline |
| Private M staging/journal | Production source inventory/search for storage/transaction, pdf/editing, pdf/workflow and writing contract names | No installed M transaction subtree, journal, coordinator or artifact capability |
| Save As | SafTreeGrantAccess.kt:27–49; production source search for ACTION_CREATE_DOCUMENT | Existing picker is OPEN_DOCUMENT_TREE, not an M publication capability or CREATE_DOCUMENT host bridge |
| Backup | app/src/main/AndroidManifest.xml:5–18; app/src/main/res/xml/backup_rules.xml:2–6; data_extraction_rules.xml:2–11 | Policy B exact settings-file allowlist, isolated read worker. Future M noBackupFilesDir subtree not yet implemented |
| Backup verification | scripts/verify_backup_policy.py:13–43,79–108 | Existing XML/merged-manifest/optional compiled APK verifier can be reused by owner later; NOT RUN here |
| Fixtures/build | test-fixtures/README.md:1–69; manifest.json; app/build.gradle.kts:22,56–75 | 15 immutable synthetic PDFs; assets wired to androidTest only; no new production assets |
| Test providers | app/src/androidTest/AndroidManifest.xml:3–13; storage/saf/SafFixtureContentProvider.java:28; app/integration/CanonicalPdfContentProvider.java:23 | Existing test APK ContentProviders, **not DocumentsProviders**. Do not claim create-document/picker semantics are already covered |

Reusable source-level tests include storage/api/ReadHandleTest's transfer and
cleanup cases, storage/saf/SafDocumentReadAccessTest's direct/pipe/limit/cancel
cases and SafTreeGrantAccessTest. Their existence does not establish M snapshot,
publication or recovery acceptance. Extend patterns, not protected implementation.

### Observation corrections and documentation conflicts

- AGENTS starter-version table is historical; catalog actually says AGP9.5.0,
  Kotlin Compose2.2.10 and Room2.8.5. No dependency change is needed for this audit.
- PROJECT_STATUS and J_PHASE_J_CLOSEOUT include dated unmerged/pending J records;
  git ancestry/source are authoritative for this base. Historical wording is not
  evidence of current M implementation and is not edited.
- CONTRACTS.md:119–123,145–169 requires registered snapshot/output identity;
  M-D01/D12 prohibit fabricated registration for transient sources and Save As.
  This is **H-01**, not permission to reinterpret DocumentRef locally.
- CONTRACTS.md:482–509 includes ReplaceOriginal, output DocumentRef/reconciliation;
  M-D06/D12 allow only new unregistered Save As. Narrow first-phase contracts need
  approved adaptation, not an implementation of the broader historical design.
- Policy normally requires Codex validation attempts. The explicit owner M-M1
  prohibition takes precedence for this assignment. No policy was changed or
  test exemption for future acceptance inferred; owner runs future validations.

## Trust boundaries and risk register

Severity describes potential harm, **not observed production defects**:
P0 original-data loss; P1 unsafe publication/privacy; P2 blocked/correctness;
P3 noncritical diagnostic/ergonomic debt. No write path currently exists to
reproduce these future M failures.

| Boundary/assets | Threat/failure and harm | Existing protection; missing M guarantee | Proposed mitigation/test dependency |
|---|---|---|---|
| External source, P0/P1 | Concurrent external edits, lying metadata, wrong URI association | READ-only access; no immutable attestation | Hash observed sealed bytes, never claim provider-current identity; H-01 and M-T01–04 |
| Private capture, P1 | Partial/seal race, path traversal/symlink, descriptor reuse | ReadHandle exclusive transfer only | Confined owned paths, single seal owner, independent read leases, no writable alias; M-T01/03/19 |
| Snapshot lease, P1 | Owner deletes before reader closes; native double-close | Existing descriptor close rules | Reject new leases after CLOSING, join closes, CLEANUP_PENDING; lost process counts untrusted; H-02 |
| Private storage, P2 | Unknown length/overflow/ENOSPC; retained uncertainty exhausts quota | Existing read-copy ceiling not aggregate quota | Enforce 64/128/256MiB, margin64MiB and observed bytes with 64KiB buffer; H-07 |
| Untrusted engine, P1/P2 | Hostile parser, OOM/CPU blowup, dropped fonts/annotations/rotation | Isolated read engine is not an editing sandbox/validator | H-06 engine/license/security/fidelity evaluation, bounded operations, no parser-memory guarantee from buffer size |
| Validation, P1 | Same transform and check share a bug; bytes/hash correct but content wrong | No installed validator | Closed output then independent operation-specific byte/structure/content checks; M-T06/07 |
| Picker/provider, P0/P1 | Destination aliases original, name collision, callback lost, partial creation | No M destination capability | Host-owned CREATE_DOCUMENT, fail closed on unverifiable independence; no root WRITE; H-03 |
| Journal, P1 | Torn/stale record or false durable acknowledgment authorizes replay/deletion | No M journal | Schema/version/checksum/durable ordering, fail closed, read-only reconciliation; H-04 |
| Concurrency/cancel, P1/P2 | Two writers, late result after cancellation, lock held during picker | Reader locks are unrelated | One process coordinator, reservation/BUSY/no queue; no synchronous lock during I/O; H-02 |
| Recovery, P1 | Unknown destination exists but auto retry duplicates/truncates it | Read-copy orphan sweeping cannot safely serve M | Preserve unknown artifacts, never auto retry/delete/write or reacquire permission; H-03/08 |
| Metadata, P0/P1 | Save As inherits original identity/favorite/position | Registry mutation APIs exist but do not authorize use | Separate storage result, explicit later registration only; M-T15 |
| Backup/privacy, P1 | Journals/grants/PDFs logged or backed up | Policy B allowlist; no M subtree yet | noBackupFilesDir + owner APK/backup evidence, redacted diagnostics; no URI/hash-of-private-document logs by default |
| Parallel K/L, P2 | Shared API drift or new main assumptions | M at original base | Record integration requests only; protect K/L/host/worker and revalidate under later authorization |

## Failure-window ledger (design requirements, NOT executed)

| Window / observed signal | Authority/durable prerequisite | Required safe outcome, including cancellation |
|---|---|---|
| Before source read / denied grant | Explicit source READ capability | No public effects; typed denied/revoked distinctions only with evidence |
| Open source changes during capture | Capture owns read handle, no external lock guarantee | Evidence identifies captured bytes only; validate structure, no provider-current claim |
| Mid-copy / false size / limit / ENOSPC | Owned .part charged to quota | No SEALED result; close owners, retain cleanup evidence, preserve primary failure |
| Copy complete before seal/hash/reopen | No attested capability yet | Incomplete artifact cannot escape as snapshot |
| Snapshot owner closes with live leases | SEALED owner enters CLOSING | No new leases; deletion only after all known live leases close; CLEANUP_PENDING on failure |
| Mid-transform or writer close fails | Input immutable; output isolated | Never publish; track unvalidated output and cleanup |
| Structure/content validation fails | Output closed, independent expectation | No validated artifact; typed report, original unchanged |
| Before launching external picker | Durable pre-publication intent before any possible create | H-03: record operation with unknown destination; host must participate later |
| Picker cancel/result loss/URI absent after possible create | Creation may already have occurred | Cancellation is not proof of no destination; record observability gap; no blind retry/rollback |
| Capability returned before journal association durable | Need result/grant evidence and destination association checkpoint | Do not start bytes until required journal state acknowledged; retain possible orphan uncertainty |
| Permission lost before first byte | Creation may already be public | No fallback WRITE grants; distinguish no bytes from no created document |
| Mid-public write / provider death / ENOSPC | Durable possible-publication state + pinned validated output | Partial/uncertain destination, not VERIFIED_COMMITTED; retain artifacts, no overwrite/delete |
| Close/flush reports completion but no readback | Provider signal not universal durability | PUBLISHED_UNVERIFIED or UNCERTAIN_PUBLICATION per approved schema; never ordinary success |
| Readback length/hash differs | Compare to exact pinned validated artifact | No verified commit, no destructive automatic repair |
| Readback matches before terminal journal durable | Verification evidence may be lost on crash | Read-only idempotent reinspection; no second publication |
| Cancel before any possible create | Prove no public mutation boundary crossed | ABORTED_CLEAN only with evidence; cleanup failures tracked |
| Cancel after create/write/ack | Cannot infer rollback or undo commit | Record known/unknown state in bounded noncancellable cleanup, propagate cancellation |
| Process death / corrupt journal / abandoned .part | In-memory leases/reservation are lost, not serialized truth | No editor resume; preserve ambiguous files, reject unknown schema, authorized read-only reconciliation |
| Cleanup fails alongside primary error | Both facts matter | Suppress/attach cleanup diagnostics without hiding primary error; retain quota charge |
| Second transaction / conflict with uncertainty | Single process reservation and durable conflict evidence | Immediate typed BUSY/no queue; regular Reader independent; no deadlock during picker |

No fsync/rename sequence is declared power-loss-safe merely by invoking APIs.
No time-based purge, wildcard deletion, overwrite or automatic grant acquisition
is allowed. See H-01–H-08 in the companion matrix before M-M2.
