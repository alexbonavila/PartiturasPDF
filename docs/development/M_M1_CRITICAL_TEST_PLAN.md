# M-M1 critical test plan — 2026-11-02

**TEST IMPLEMENTATION: NOT STARTED.**
**TEST EXECUTION: NOT RUN (M-M1 documentation-only).**
Builds, JVM/Python tests, Lint, coverage, localization/fixture/backup validators,
Android instrumentation, CI and device operations were explicitly prohibited
for this assignment and were not executed. Tables below are future acceptance
plans, not results or claims of installed M behavior.

Source baseline: `7f00a793a06e988859dbefc3e21814c21ec727dd`,
feat/pdf-safe-write-m. M-D01–M-D14 come from the supplied approved register.
M-T01–M-T20 below preserve the complete 20-case seed matrix in the supplied
M_M1_SCOPE_AND_ACCEPTANCE document. The original Phase M handoff was not supplied
or found in the repository; exact parity with any additional original wording
remains an owner review item. No missing guarantee is invented as approved.
See [contract holds](M_M1_CONTRACT_AND_APPROVAL_MATRIX.md) and
[ground truth/threats](M_M1_CAPABILITY_THREAT_AUDIT.md).

## Shared preconditions and fixture policy

All cases require the relevant owner-approved M-M2 contract slice, injected
budgets, typed result semantics and owner-run execution. Proposed stage labels
M-M3–M-M8 refer to the **proposed**, not approved schedule in the contract matrix.
No test may silently exercise real user PDFs, existing grants or public outputs.

Authoritative identity is `test-fixtures/manifest.json` and its immutable SHA256/
byte-size expectations, not filename alone. Use instrumentation assets from the
existing androidTest wiring and test-owned copies before modification; never
regenerate or overwrite canonical originals. Read those hashes during test setup,
assert input identity, and check original bytes after every failure/cancel path.
Generated malformed/large fault streams must be deterministic, disposable and
clearly distinct from canonical fixture evidence. Do not add them to production.

Fixture aliases (all under `test-fixtures/pdf/`):

| ID | Exact path | Planned use |
|---|---|---|
| 01 | basic/01-single-a4.pdf | Minimal output/content boundary |
| 02 | basic/02-two-pages.pdf | Exact capture/readback and ordinary Save As |
| 03/04/05 | basic/03-ten-pages.pdf; basic/04-fifty-pages.pdf; basic/05-hundred-pages.pdf | Page/order/count/resource cases |
| 08 | page-sizes/08-mixed-sizes.pdf | Geometry/content preservation |
| 09 | page-sizes/09-rotated-pages.pdf | Rotation preservation |
| 10/11 | complex/10-scanned-score.pdf; complex/11-vector-score.pdf | Raster/vector/font fidelity |
| 12 | annotations/12-annotated.pdf | Annotation dictionaries and appearances |
| 13 | complex/13-complex.pdf | Fonts/transparency/complex structures |
| 14 | complex/14-password.pdf | Protection/capability rejection; public synthetic test password in fixture README |
| 15 | invalid/15-corrupted.pdf | Expected malformed input rejection |

## Complete critical scenario matrix

Every row is **NOT IMPLEMENTED / NOT RUN**; engine-dependent subcases additionally
remain **BLOCKED BY ENGINE APPROVAL (H-06)**. A fake or byte-copy transform can
exercise workflow mechanics but cannot close a structural/content-validation row.

| ID / decisions | Required guarantee, fixtures and preconditions | Proposed JVM faults/assertions | API26 provider/descriptor evidence and later integration | Dependencies, stage, acceptance |
|---|---|---|---|---|
| M-T01 / D01–04 | Fixture02 snapshot has exact observed SHA/size and immutable owner; both registered and transient identity | Valid capture, reopen/hash, seal-failure injection; no writable alias or fabricated registered identity; independent leases; close owner with active readers rejects new leases and defers deletion | API26 independent read-only descriptors/positions, native consumer closes FD, owner closes once, cleanup pending observable | H-01/02; M-M3. No snapshot escapes before complete close/hash/seal; exact manifest match; NOT RUN |
| M-T02 / D03 | Unknown/false provider length cannot bypass observed-byte ceiling | Unknown, under/overreported length; exact64MiB and +1 observed byte; overflow-safe near-Long bounds without allocating giant files; bounded64KiB blocks | Test provider advertises false/null length and supplies seekable/pipe streams; actual measured bytes decide admission | H-07; M-M3/7. Limit failure yields no SEALED snapshot, all retained bytes charged; NOT RUN |
| M-T03 / D02/04/05 | Read exception/cancel/denial closes acquired ownership and preserves original | Throw before acquire, after FD acquire, during copy/seal, simultaneous cleanup failure; propagate cancellation/primary defect, no leaked lease | Test-owned URI/read permissions, pipe and descriptor-close counts; access denied is not deletion; no actual user revocation | H-01/02; M-M3/7. No delivered partial snapshot and deterministic owned cleanup or pending evidence; NOT RUN |
| M-T04 / D01/07 | Snapshot attests captured private bytes, not current provider contents | Controlled source mutation before/during/after capture; compare actual captured sequence; metadata stable but bytes differ; no provider-current revision claim | Provider changes stream/version at barrier; read-only original access; resulting structurally invalid mixture rejected by approved validation | H-01/06; M-M3/5/7. Explicit limit acknowledged: no universal external consistency guarantee; NOT RUN; structural subcase BLOCKED BY ENGINE APPROVAL |
| M-T05 / D02/07/11 | Write/staging close failure cannot publish | Separate fake staging writer throws mid-write/flush/close; validated capability never constructed; publisher call count zero; quota/cleanup state retained | API26 private owned .part files with controlled close/ENOSPC seams; independent input unchanged | H-02/04; M-M4. Fail before public mutation, preserve primary+cleanup causes; NOT RUN |
| M-T06 / D07/08 | Fixture15 corrupt and fixture14 protected rejected appropriately, no validation/publication success | Stub typed reject outcomes to verify orchestration only; no substitute parser-quality claim | Approved engine opens fresh sealed leases, exact error/capability classification and safe closure on API26; no password bypass | H-06; M-M5/7. BLOCKED BY ENGINE APPROVAL; no approved engine/structure validator exists |
| M-T07 / D07–09 | Preserve required geometry/order/content/annotations for08/09/12/13, with10/11 where relevant | Typed expectation/plan validation; intentionally wrong page count/order/rotation/content report must not mint artifact | Real engine output reopened by approved structural/content checks; compare page geometry, retained fonts/images/transparency/annotations/appearances as operation requires; human visual review supplements, never replaces structure | H-01/06/07; M-M5/7/8. BLOCKED BY ENGINE APPROVAL; unsupported fidelity must reject, not silently drop |
| M-T08 / D05–07/12 | Save As to disposable new destination, original unchanged | Inject validated output and narrowed fake destination, pinned byte identity, readback exact match; no mutable-path reopen; journal ordering/capability binding | Test DocumentsProvider creates isolated destination, writes/closes/readbacks exact hash/length; later host ACTION_CREATE_DOCUMENT integration and real-provider acceptance separate | H-03/04/06; M-M6/7/8. VERIFIED_COMMITTED only with validated output + readback + required terminal durable state; no implicit registry identity; NOT RUN |
| M-T09 / D06 | No WRITE authority or provider refusing create cannot be bypassed | Capability absent/expired/refused; assert no writer call, no fallback tree permission request | Test provider denies create/write; host result flags lack WRITE; existing SAF READ grants unchanged. Distinguish refused creation from unknown preexisting picker side effect | H-03/05; M-M6/7, later host integration. Typed failure/uncertainty, no grant escalation; NOT RUN |
| M-T10 / D06/12 | Duplicate display name never silently overwrites another document | Collision/alias/inability-to-prove-independence capabilities; assert no original write and no unapproved rename/truncate | Prepopulate test provider with original/same-name documents; provider may choose different name/ID or refuse. Inspect actual IDs/content, not assumed filename uniqueness; lost picker result separately modeled | H-03/05; M-M6/7/8. Reject unverifiable independence, preserve originals; NOT RUN |
| M-T11 / D03/05/06 | Mid-write ENOSPC/provider death/partial write remains uncertain, never verified success | Inject short writes, write exception, close exception, ack loss and journal failure at deterministic byte offsets; retain validated output, destination evidence and quota charge | API26 provider fault channels/disposable provider process, under specific owner execution authorization; readback partial/truncated bytes; no automatic delete/retry | H-03/04; M-M6/7. No VERIFIED_COMMITTED or replay; original unchanged; NOT RUN |
| M-T12 / D02/05/10 | Cancel before/after possible commit preserves uncertainty and never replays | Cancel before launch, after possible create, mid-write, after close/readback/terminal checkpoint; deterministic barriers, late result rejection; bounded noncancellable record/close | API26 controlled picker/result/provider ack timing and later restart; no automatic write on recovery; editor not resumed | H-03/04/08; M-M4/6/7. ABORTED_CLEAN only if no public effect proved; committed/uncertain not reported clean-aborted; NOT RUN |
| M-T13 / D02/05/11 | Durable journal rehydrates every valid state; corrupt/torn states fail closed | Fault every checkpoint write/close/sync/replace/ack boundary, unknown version, stale transaction/path, checksum corruption; no destructive cleanup on ambiguous records | Actual API26 noBackup storage + process restart after controlled checkpoints with owner authorization; distinguish process-crash evidence from unproved physical power-loss durability | H-04/08; M-M4/7. Idempotent authorized read-only recovery, no restored lease-count fiction, retain uncertain artifacts; NOT RUN |
| M-T14 / D05/06 | Permission revoked between check and use cannot create false success | Revoke capability at open/write/readback barriers; initial check never caches authority as permanent | Test-owned provider denial/expired grant after preflight; genuine OS grant-revocation behavior later with exact disposable destination authorization; do not revoke user's existing roots | H-03/05; M-M6/7/8. Distinguish partial-write uncertainty from pre-write refusal; no reacquisition or overwrite; NOT RUN |
| M-T15 / D01/12 | No automatic rebind/favorite/checkpoint/registration transfer | Spy registry/metadata: zero mutations during Save As; source logical ID/grants unchanged; new storage receipt without DocumentRef; later explicit registration failure cannot undo publication | Later host integration with actual Room/preferences observations before/after verified publication and failed separate registration, no schema change | H-01/02/05; M-M6/8. Explicit registration only, no invented transient revision, preserve original state; NOT RUN |
| M-T16 / D03/08/10/11 | Hostile/large input is rejected within enforced resource guarantees | Limits at64MiB source/128MiB output/256MiB aggregate;64MiB free margin; retained uncertainty consumes admission; immediate BUSY/no queue; no overflow; one active transaction | API26 bounded adversarial disposable inputs; close leases under pressure; owner measures engine heap/CPU separately, no arbitrary benchmark budget or claim buffer bounds parser peak memory | H-06/07; M-M3/4/5/7. No destructive eviction, Reader independent; engine stress subcases BLOCKED BY ENGINE APPROVAL; NOT RUN |
| M-T17 / D04/06/13 | API26 seekable/nonseekable/create/readback/provider variants behave correctly | Narrow capability doubles for each supported/unsupported operation; independent lease offsets and cancellation | Test-owned DocumentsProvider: pipes/FDs, advertised capabilities versus actual failures, create return flags, short write/close/readback failure; genuine host picker later | H-03/05; M-M7/8. Actual API26 execution mandatory; fake resolver tests alone insufficient; NOT RUN |
| M-T18 / D11/13/14 | Test PDFs/provider/transaction artifacts absent from production APK and backup | Source-set/path allowlist and packaging checks; no instrumentation provider in production manifest | Owner inspects debug/release APK entries and merged manifest, existing backup-policy checker and compiled resources; actual noBackup/D2D transport evidence separately authorized | H-05; M-M7/8. No fixture/provider/journal/PDF leaks; policy B unchanged; NOT RUN |
| M-T19 / D04/05/10 | Cleanup failure never conceals primary write/read/cancel error | Primary fault plus FD/lease/writer/journal cleanup fault; assert suppressed/typed secondary context, primary identity and cancellation propagation; no duplicate native close retries | API26 FD closure counts and close-failure adapters; pending artifact retained and quota charged | H-02/04; M-M3/4/6/7. Programming defects propagate; no successful receipt after cleanup uncertainty; NOT RUN |
| M-T20 / D06/09/12/14 | No public overwrite/delete/editor/real-user write route in independent M | Static/package/dependency boundary review; no ReplaceOriginal implementation, generic engine commands, UI launch or real grants; fake destination injection only | Manifest/provider/permission inspection on API26 test APK, production APK has no test provider; later host integration separately approved with new Save As only | H-02/05; every milestone plus M-M8. No code under protected K/L/host/worker/core/SAF without authorization; NOT RUN |

## Test-only DocumentsProvider design (not implemented)

Existing androidTest providers are plain ContentProvider subclasses, not a
DocumentsProvider implementation. Reuse their canonical-asset/unique-temp-copy
patterns and ReadHandle ownership tests, but do not describe them as existing
create-document coverage. New test-only provider/manifest details need H-05 exact
owner approval before adding them. Proposed characteristics:

- Isolated test authority and disposable namespace per transaction/test, Android
  DocumentsContract roots/documents/tree/create/open behavior only as needed;
  real URI grants restricted to those test objects. No production provider entry.
- Out-of-band test control for capabilities, truthful/false metadata, pipe versus
  seekable handles, partial/short writes, write/close/readback errors, revocation,
  callback loss and deterministic barriers. Controls cannot escape test scope.
- Preserve original synthetic provider objects as read-only; distinct destination
  IDs and explicitly inspected content establish test behavior, not a universal
  guarantee for malicious real providers. Non-provable independence fails closed.
- Test close/open counts, read offsets and retained artifact inventory; keep raw
  original output for failed/incomplete cases. No time sleeps as completion proof.
- A provider create test is not ACTION_CREATE_DOCUMENT system-UI acceptance.
  Later host tests must exercise pre-launch durable intent, actual picker creation,
  grant-result transfer and missing result/URI, including the unobservable orphan
  limitation in H-03. Never claim a canceled picker proves no public creation.
- Crash/restart and genuine revocation require separately owner-authorized test
  device/process/data boundaries; never adopt or reset an arbitrary detected AVD,
  kill production workers or modify personal grants as part of ordinary testing.
- Place output/journal/snapshots in test-owned noBackup subtree; verify no backup
  inclusion without changing existing allowlists. Corrupted ownership records
  cannot authorize recursive deletion; symlinks/root escape/unrelated files rejected.

## Cross-case admission and recovery campaign

Before any real publication campaign, test one process-local exclusive coordinator
across source capture through reconciliation. Deterministic competing admission
must return typed BUSY immediately, never wait in a queue or borrow a snapshot
without a lease. Normal Reader operations are independent. Verify cleanup after
cancel and that uncertain destinations prevent conflicting replay without deadlock.
An in-memory mutex/reservation does not establish cross-process exclusivity.

Journal fault tests must enumerate CREATING/SEALED/CLOSING/RELEASED/CLEANUP_PENDING
snapshot lifecycle separately from transaction states, including ABORTED_CLEAN,
FAILED_RECOVERABLE, UNCERTAIN_PUBLICATION, PUBLISHED_UNVERIFIED and
VERIFIED_COMMITTED. Exact schemas/state names beyond approved categories remain
H-02/H-04, not new final definitions. Terminal commit requires exact destination
readback plus required durable recording; close/flush alone is insufficient.
Retain only evidence-authorized artifacts, but preserve unclassifiable recovery
records/files and charge aggregate quota. No time-only purge or automatic delete,
write, retry, overwrite or new permission acquisition during reconciliation.

## Evidence and owner-run gate sequence

1. Owner approves H-01–H-08 and exact M-M2 contract slice. No M-M1 code/test task.
2. Future tests are implemented in approved M packages and reviewed without Codex
   executing them under the supplied Phase M constraints.
3. Owner runs focused JVM cases, then complete JVM/Python/build/Lint/localization/
   canonical fixture checks using the unchanged existing runners/tasks; owner
   records command, commit/source digest, environment and exact method inventory.
4. Owner runs actual API26 test-provider instrumentation on an explicitly owned
   disposable namespace/device, then required CI/compatibility at the same source.
5. Engine-backed structural/semantic cases remain blocked until H-06 is satisfied;
   no fake transform or render-only result substitutes. Owner approval of real
   engine dependencies/build changes is separate.
6. Capture hashes of inputs/output/journal/APKs, expected/actual method identities,
   raw JUnit/logs, device/API, source revision and PASS/FAIL/BLOCKED/NOT RUN outcomes.
   Keep URI/PDF/private paths redacted; do not commit raw private artifacts.
7. Measure >=90% line / >=80% branch for all new testable business logic with
   existing scoped coverage tools; execute 100% of defined critical write/save
   scenarios. No thresholds, denominator exclusions or assertions weakened.
8. Before formal acceptance, obtain applicable API27/recent compatibility, physical
   Android8.1 tablet and provider/manual evidence required by repository policies.
   CI success is not physical-device or public-provider durability attestation.

No exact future instrumentation class names, journal schema, engine-dependent
commands or new runner are invented before contract approval. Existing owner-run
entry points include Gradle app assembleDebug/assembleDebugAndroidTest/lintDebug/
testDebugUnitTest, scripts/tests unittest discovery, validate_localization.py,
test-fixtures/scripts/verify_fixtures.py and verify_backup_policy.py with actual
merged manifest/APK arguments. **None was executed or authorized for execution
by Codex in M-M1.** The task ends with these three documents, not M-M2.
