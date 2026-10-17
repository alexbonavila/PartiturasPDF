# Pre-F/G/H deferred acceptance register and readiness review

**Recorded: 2026-10-17.** Starting readiness HEAD:
`e516665fded37c3a226b8a297302a6e84e51a4ca`, branch `chore/pre-fgh-readiness`.
Verified `main`: `f94841f9f1eead508dd81b670d0943f3b3ae19bb`.
This P05 consolidation is a review index, not a new acceptance procedure, policy exception,
feature authorization or declaration of formal Phase C/E acceptance.

Authority: [AGENTS](../../AGENTS.md), [testing policy](../TESTING_POLICY.md),
[Definition of Done](../DEFINITION_OF_DONE.md), [change control](../CHANGE_CONTROL.md).
The [Phase C register](../PHASE_C_DEFERRED_ACCEPTANCE.md),
[C06 audit](../C06_PERSISTENCE_ACCEPTANCE_AUDIT.md),
[guarded C06 procedure](../C06_BACKUP_RESTORE_ACCEPTANCE.md) and
[chronological Phase E audit](../integration/E_APP_COMPOSITION.md) retain their detailed evidence
and procedures. Earlier instructions to open/merge PR #10/#12/#14 or keep their old branches are
historical; those integrations are complete. This task creates no future testing/feature branch.

> **Owner decision update (2026-10-17):** D01–D11 below are the historical P05 pending snapshot. The owner subsequently approved the master foundation requirements in principle; implemented contracts are now recorded in [FGH_FINAL_CONTRACT_FREEZE.md](FGH_FINAL_CONTRACT_FREEZE.md). PR #15 has merged this readiness work at `121090a56ff6d2fdb98655eaf7104ee67d79caa4`. P05-A–G manual/physical obligations remain open; the new implementation does not waive them.

## 1. Status and priority rules

- **PASS:** only the identified executed check/evidence at its actual revision. It does not close
  another requirement, certify a later SHA or constitute owner sign-off.
- **FAIL:** an executed required check or assertion failed. No new production failure is asserted
  by this documentation review; missing evidence is not an invented implementation defect.
- **BLOCKED:** a specific prerequisite or trustworthy execution/evidence is unavailable.
- **NOT RUN:** no qualifying execution evidence for the named requirement is recorded.
- **DEFERRED:** the activity is scheduled for later; it does not become PASS or waive policy.
- **NOT APPLICABLE:** only with recorded scope/reason and owner review where required. UI device
  review is not applicable to the visual effects of this documentation-only task, but future
  feature UI obligations remain open, not automatically NOT APPLICABLE.

Priorities below are resumption urgency/dependencies, **not** P0/P1/P2/P3 defect reassignment.
A missing mandatory acceptance criterion remains blocking regardless of scheduling or priority.
On resumption, any failed assertion is FAIL; missing transport/device/permissions/evidence is
BLOCKED / NOT RUN. Preserve partial results rather than converting missing execution to zero tests
or zero coverage. Only the owner can accept evidence and make integration decisions under policy.

## 2. Consolidated acceptance matrix

| ID | Requirement / subsystem | Current acceptance status | Resumption priority / prerequisite | Responsible future work / formal consequence |
| --- | --- | --- | --- | --- |
| P05-A / C-DEF-03 | Physical Android 8.1/API27 tablet; applicable C/E and future functional phases | NOT RUN / DEFERRED | Mandatory before applicable functional-phase closure; physical hardware and explicitly scoped build/scenarios | Owner-approved physical acceptance task after purchase; hosted API27 cannot close it. |
| P05-B / C-DEF-01 | Real cloud-style Policy B backup/uninstall/reinstall/restore | NOT RUN / DEFERRED | High before relying on backup; disposable AVDs, supported local transport and separate destructive consent | Owner-led guarded C06 acceptance; missing runtime transport evidence leaves the C acceptance criterion open. |
| P05-C / C-DEF-02 | Genuine Android12+ device-to-device transfer | NOT RUN / DEFERRED | High before promising D2D; supported genuine source/destination pairing/transport | Owner-approved C06 D2D task; lack of transport becomes BLOCKED, never substituted cloud PASS. |
| P05-D / C-DEF-04 | Other platform/OEM/cross-platform restoration and reconciliation | NOT RUN / DEFERRED; CONDITIONAL applicability | Before supporting/relying on the specific mechanism; mechanism and security/persistence scope approved | Owner evaluates applicability; separately approved migration/security work if needed. Unassessed copying modes have no blanket acceptance. |
| P05-E | External SAF providers beyond controlled local E2E; storage/E/integrated F/G/I | NOT RUN / DEFERRED for outstanding manual provider scenarios | Before relevant provider/functional acceptance; approved provider matrix, synthetic documents and real READ authorization | Owner-approved provider/host acceptance; preserves E manual obligations and source/metadata safety. |
| P05-F | Actual reader performance, resources and lifecycle; E limits/G/I | NOT RUN / DEFERRED | D07/D08 approvals, representative hardware and implemented consumer for UI measurements | G/I performance task plus owner physical acceptance; provisional ceilings do not satisfy measured resource/performance criteria. |
| P05-G | Latest-revision API compatibility and future UI acceptance; all PRs/F/G/H/I | NOT RUN for final readiness-head CI and future feature UI; historical infrastructure PASS is separate | Mandatory latest-head CI/current base; feature implementation and approved UI/locale scope before feature acceptance | Owner-authorized PR/compatibility review and F/G/H/I tests; no interface or unexecuted API check is declared PASS. |

### P05-A — Physical Android 8.1 tablet

| Field | Actionable record |
| --- | --- |
| Existing evidence | Hosted API27 emulator PASS at `f94841f9`; historical API26/native AVD runs and backend tests. These are not physical tablet results. |
| Missing evidence | Actual tablet installation/compatibility, applicable persistence/restart/lifecycle, authentic SAF access/revocation/recovery, repeated PDF open/render/close and resource behavior. Reading/UI/locale/performance scenarios must match functionality actually present at the selected revision. |
| Environment / prerequisites | Purchase/select actual Android8.1/API27 tablet; owner approves device-specific scope and safe test installation/data isolation. Record model, hardware, OS/build/security level, API, serial alias, tool versions, exact tested SHA and dirty state, package/version, APK SHA256 and signing/build identity. |
| Responsible task / resume | Owner initiates separately approved physical acceptance after hardware availability; use then-current reviewed revision and agree scenarios before execution. Do not create the future short-lived physical-test branch now. |
| Safety | Synthetic documents only, preserve originals and pre-existing permissions/data. No wipe, reset, clear, uninstall, backup or migration on the owner's tablet without separate explicit authorization and appropriate isolation. Existing local E2E runner deliberately refuses physical devices; it is not a tablet acceptance runner. |
| Closure / retention | Retain actual instrumentation XML/logs where applicable, scenario/action/outcome checklist, screenshots, resource measurements, exceptions and owner review, tied to device and APK/SHA. Record failures/blocked/unimplemented scenarios honestly; rerun affected checks after fixes. |
| Formal implication / sources | Required for applicable functional-phase closure, including outstanding C/E obligations. No emulator substitution or implicit waiver. [Phase C C-DEF-03](../PHASE_C_DEFERRED_ACCEPTANCE.md), [L04 physical deferral](../LOCAL_COMPATIBILITY_VALIDATION.md), [E audit](../integration/E_APP_COMPOSITION.md). |

### P05-B — Real cloud-style backup and restore

| Field | Actionable record |
| --- | --- |
| Existing evidence | Policy B XML, merged-manifest/APK checks and focused Python tests; Room/DataStore automated behavior. Manual fixture and guarded PowerShell script exist. These do not prove that any transport backed up/restored data. |
| Missing evidence | Actual backup, uninstall/reinstall and restore recovering non-default theme DARK (`dark`), language CATALAN (`ca`) and fit WIDTH (`width`) exactly; default settings do not count as restoration. |
| Policy / exclusion assertions | Only the entire `file:datastore/app_preferences.preferences_pb` is eligible. Before opening Room after restore, no restored database/main/WAL/SHM/journal files may exist; after a fresh Room open, the seeded registration and favorite/reading metadata must be absent. Unrelated private marker must be absent. User PDFs, provider content/locations outside the allowlist, credentials and tokens must not be included; the fixture uses opaque synthetic identifiers, no user/provider data. Record which sidecars actually existed before backup; do not claim to have tested transfer of absent sidecars. |
| Environment / prerequisites | Separate deliberately disposable emulator, not the dedicated SAF E2E AVD; synthetic clean installation, matching app/manual test APKs, supported platform tools/PowerShell and exactly listed permitted local test transport. Exercise API26–30 legacy and API31+ cloud-rule variants as applicable. Recheck the historical procedure on current tooling; unsupported transport/output/run-as is BLOCKED. Never switch to a personal/cloud-account transport as fallback. |
| Responsible task / resume | Owner approves guarded C06 runtime acceptance, exact selected serial/AVD/API/package and scope. Follow the existing procedure; do not recreate it in this register. Before each uninstall/clear/restore phase obtain explicit authorization; the existing script also requires typed `ERASE ONLY <serial> com.abdev.partituraspdf` consent and fresh UUID markers. |
| Safety / actual tooling | Manual-only `BackupPolicyAcceptanceRunner` is selected via `scripts/backup-acceptance.init.gradle`, not ordinary CI/JUnit. It deliberately seeds the default production-format database/preferences only in its confirmed fresh disposable installation and refuses existing persistence. The host script can uninstall both test/app packages and wipe this package's local test backup: review all guards first. Never invoke it on personal data or use it for physical acceptance. |
| Closure / retention | Retain Git/APK hashes, explicit environment/consent record, `identity.json`, `commands.log`, seed/backup/restore-set/restore/verify outputs and `result.txt`, plus backup settings restoration outcomes. Require actual package/overall backup success and restore success, exact restored values and exclusions. Missing/ambiguous restore token, incomplete assertions or settings cleanup failure cannot yield overall PASS. |
| Formal implication / sources | C-DEF-01 remains open until real transport evidence and owner review; static Policy B PASS is insufficient. [C06 procedure](../C06_BACKUP_RESTORE_ACCEPTANCE.md), [PowerShell script](../../scripts/Invoke-BackupPolicyAcceptance.ps1), [manual init script](../../scripts/backup-acceptance.init.gradle), [C register](../PHASE_C_DEFERRED_ACCEPTANCE.md). |

### P05-C — Genuine device-to-device transfer

| Field | Actionable record |
| --- | --- |
| Existing evidence | Explicit API31+ device-transfer exact-file XML allowlist and static/APK validation. No genuine source-to-destination transport execution is recorded. |
| Missing evidence | Real supported D2D transport participation by this application, exact restored non-default theme/language/fit, and absence of Room/SQLite sidecars, metadata and unrelated private data. Prove mechanism identity, not just destination file contents. |
| Environment / prerequisites | Two distinct isolated disposable source/destination devices supported by the real migration mechanism (Android12+ applicability); record vendor/image, OS/API/build, app/test APK versions/hashes, source/destination identities and actual transport/UI sequence. Existing C06 fixture/procedure supports explicitly guarded emulator use only; if genuine pairing or required debug-app transfer is unavailable, retain BLOCKED / NOT RUN. Physical D2D would need a separately approved procedure, not bypassed emulator guards. |
| Responsible task / resume | Owner-approved C06 D2D task after supported mechanism/prerequisites are demonstrated. Use the existing procedure's separate D2D section; it does not provide a universal migration command. |
| Safety | Confirm both identities and separately authorize any destructive source/destination operation. Seed only source with synthetic data. Do not seed, clear or reinstall the destination app after transfer to manufacture a result. No personal accounts/data or arbitrary file copying. |
| Closure / retention | Retain pairing/transport logs proving DEVICE_TRANSFER, both environments, before/after states, seed and destination verification assertions, exact SHA/APKs and owner review. A LocalTransport/bmgr restore, altered transport flag, API36 test PASS or manually copied protobuf is **not** D2D evidence. |
| Formal implication / sources | C-DEF-02 remains unresolved; no D2D acceptance/promise from cloud-style PASS. [C register](../PHASE_C_DEFERRED_ACCEPTANCE.md), [separate D2D procedure](../C06_BACKUP_RESTORE_ACCEPTANCE.md), [C06 transfer boundary](../C06_PERSISTENCE_ACCEPTANCE_AUDIT.md). |

### P05-D — Other transfer and restoration mechanisms

| Field | Actionable record |
| --- | --- |
| Existing evidence | C06 distinguishes ordinary framework CLOUD/DEVICE_TRANSFER XML handling from cross-platform, OEM/privileged copying, future platform modes and app-level export. No production migration/reconciliation workflow exists. |
| Missing evidence | Applicability and actual behavior of each mechanism the product intends to support; whether Policy B is honored; treatment of SAF grants, restored provider locations, logical IDs, stale availability/revisions and schema/data migration. A copied URI/AVAILABLE row never proves authorization or current content. |
| Environment / prerequisites | Actual proposed mechanism and version/vendor-specific primary documentation, isolated synthetic source/destination installations, approved privacy/persistence/migration design. Android16 r1 analysis in C06 does not certify future releases/QPRs or all privileged copying. |
| Responsible task / resume | Owner triggers a mechanism-specific assessment before adopting/promising it, in coordination with P04 D01/D02/D03/D06/D11. Separate shared-contract/persistence/backup/dependency work requires explicit approval if implementation changes are necessary. |
| Safety | No new XML opt-ins, migration copying, provider-location storage or restored grant assumptions in this task. Preserve identity only with confirmed reconciliation; no automatic name/hash-based rebinding. Never place sensitive locations/tokens in the allowlisted preferences proto. |
| Closure / retention | Record mechanism/OS/app identities, supported scope, primary-document references, actual retained/excluded data, grant/access revalidation and recovery results, plus owner sign-off. If a mechanism is out of product scope, owner may record a reasoned NOT APPLICABLE assessment for that mechanism only; current conditional item is not silently closed. |
| Formal implication / sources | C-DEF-04 remains CONDITIONAL / NOT RUN / DEFERRED; blocks acceptance of the affected migration claim when applicable. No blanket policy waiver or assertion of an observed data leak. [C register](../PHASE_C_DEFERRED_ACCEPTANCE.md), [C06 audit](../C06_PERSISTENCE_ACCEPTANCE_AUDIT.md), [P04 decisions](FGH_INTERFACE_BOUNDARIES.md). |

### P05-E — External SAF providers

| Field | Actionable record |
| --- | --- |
| Existing evidence | Real Android DocumentsUI/persisted READ controlled-folder E2E at documented native revisions; normal provider fixtures, typed error/cancellation tests and E integration tests. Authentic controlled local grants are useful evidence, not universal external-provider acceptance. |
| Missing evidence | Approved real-provider matrix: genuine selection/READ authorization, restart retention, explicit revocation, disconnected/unavailable provider, separately confirmed deleted/moved documents, provider I/O failure and recovery. Verify metadata/favorites/reading hints survive access loss, PDFs remain unchanged, and missing permission is never inferred as deletion. |
| Environment / prerequisites | Owner-approved provider/version/device matrix, disposable test installation and synthetic folder/PDF copies only; real picker capability/returned flags, known persisted-grant history and supported revocation/disconnection controls. Current debug grant harness can support the historical manual flow; production F/G/I coordination is not yet implemented and requires its own future verification. |
| Responsible task / resume | Owner-approved external-provider/E acceptance task for current backend scenarios, followed by F/G/I integration acceptance once those consumers exist. Resolve D01–D06/D11 as applicable; agree which provider behaviors can be induced safely. |
| Safety | No fabricated Activity results, injected grants, broad storage permission, release of pre-existing grants, deletion of real PDFs or forced provider corruption. Confirm known prior grant before classifying its disappearance as revoked. Missing/partial listing or generic I/O is not deletion; a same-name file is not identity proof. |
| Closure / retention | Retain exact SHA/APK, provider/device identity, picker flags, OS grant state, before/restart/revoke/recover results, PDF hashes/source-survival evidence and metadata observations. Sanitize URI/path/account details. Unsupported fault control is a recorded blocker, not a synthetic universal PASS. |
| Formal implication / sources | Outstanding applicable E/storage/manual criteria and future integrated-feature acceptance remain open. [E audit](../integration/E_APP_COMPOSITION.md), [local SAF scope](../LOCAL_E2E_AUTOMATION.md), [native evidence](../LOCAL_COMPATIBILITY_VALIDATION.md), [P04](FGH_INTERFACE_BOUNDARIES.md). |

### P05-F — Reader performance and resource behavior

| Field | Actionable record |
| --- | --- |
| Existing evidence | Adapter/lease correctness assertions, cancellation/resource tests and immutable provisional AppRuntimeLimits. These do not measure a real reader UI, sustained workload or physical-device peak memory/latency. |
| Missing evidence | Repeated open/render/close; native/descriptor/bitmap memory release; temporary-file cleanup; multipage/large PDFs; actual page-turn responsiveness; bounded concurrent rendering/cancellation; cache eviction; session replacement; background/foreground and configuration transitions on representative constrained tablet hardware. |
| Limits / interpretation | 64 MiB per bounded temporary copy; 4096 pixels per bitmap axis; 4,194,304 pixels per bitmap. Neither these limits nor a single serialized native session bounds aggregate cache/copy/native memory or supplies performance guarantees. |
| Environment / prerequisites | Owner-approved D07 aggregate memory/cache/concurrency budgets and measurement method; D08 visible-page/durability timing; physical Android8.1 and recent-device coverage where applicable. UI page-turn/cache measurements require implemented approved G/I consumers; safe backend stress can be separately scoped earlier. Use canonical test copies; additional large stress inputs require approved reproducible generation, not fixture mutation. |
| Responsible task / resume | Owner-approved G/I performance/resource task and physical acceptance. First approve numerical budgets/scenarios (cold/warm, representative multipage/raster/rotated inputs, repeated cycles, median/tail latency and peak/steady memory), then collect data. No numerical target is invented by P05. |
| Safety | Preserve originals, avoid unbounded allocations/workers, do not recycle displayed bitmaps, close sessions off main after quiescence and release leases after consumers finish. No arbitrary cache deletion or app-data clearing. Existing per-bitmap limits cannot justify unrestricted prefetch or concurrent sessions. |
| Closure / retention | Keep environment/APK/SHA, test inputs/hashes, workload/settings, timings, memory/resource traces, cleanup before/after, lifecycle/cancellation outcomes and owner comparison against approved budgets. Compilation, scoped code coverage or emulator rendering success alone does not close measured acceptance. |
| Formal implication / sources | Applicable E device/resource limits and G/I functional performance acceptance remain outstanding; future UI does not exist to pass these scenarios now. [E limits/audit](../integration/E_APP_COMPOSITION.md), [testing performance policy](../TESTING_POLICY.md), [P04 D07/D08](FGH_INTERFACE_BOUNDARIES.md). |

### P05-G — API compatibility and UI acceptance

| Field | Actionable record |
| --- | --- |
| Existing evidence | Main-baseline seven mandatory jobs and requested API27/API36 emulator PASS; native infrastructure results at separately identified SHAs; complete current en/ca/es resource validation. These establish tested infrastructure/backend behavior, not F/G/H/I UI acceptance. |
| Missing evidence | All seven mandatory checks on the eventual latest readiness PR SHA/current main; relevant latest-revision API27 and recent-Android phase-close checks; future library/reader/settings/navigation behavior, theme/language application, portrait/landscape/recreation, font scaling, touch targets, accessibility semantics and TalkBack/device observations. |
| Environment / prerequisites | Authorized PR and CI access/current base for API26 per-PR checks; API27/recent compatibility workflow at required final revision; physical Android8.1 separately under P05-A. Future UI needs approved implemented F/G/H/I, English/Catalan/Spanish, system/light/dark and D09 locale mechanism plus D10 resources. |
| Responsible task / resume | Owner authorizes PR preparation after this review; future feature owners provide independent tests, I cross-feature/device acceptance. Final compatibility and manual checks occur at the applicable final phase revision, not an unrelated old main SHA. |
| Safety | Keep minSdk26, original fixtures, tests and coverage gates; no policy/threshold/skip exemptions. Do not install destructive C06 tooling or use local E2E runner on physical/personal devices. UI fallback must not conceal unavailable data or corrupt preferences. |
| Closure / retention | Retain run URLs/head SHA/base, actual JUnit counts and zero unexplained skips, scoped coverage, localized screenshots, accessibility/rotation/recreation/locale restart observations and device identities. Report unimplemented UI as NOT RUN for future acceptance, not PASS; current documentation-only UI impact is NOT APPLICABLE. |
| Formal implication / sources | Latest-head checks/current base and human approval remain required before any merge; physical/manual criteria remain separate for C/E/functional phases. [Testing policy](../TESTING_POLICY.md), [DoD](../DEFINITION_OF_DONE.md), [L04 evidence](../LOCAL_COMPATIBILITY_VALIDATION.md), [P04](FGH_INTERFACE_BOUNDARIES.md). |

## 3. What the existing tooling can and cannot prove

Reviewed existing `scripts/local_e2e.py`, `scripts/local_coverage.py`, the manual C06 script/runner
and their linked guides; no runner or procedure is changed.

| Tool / supported invocation | Actual supported scope / boundary |
| --- | --- |
| `python scripts/local_e2e.py doctor` | Read-only host/native-image/SDK/acceleration/owned-AVD diagnostics. PASS means prerequisite readiness, not Android execution. |
| `run --suite host` | Existing Python/JVM/build/Lint/localization/fixture host regression; no device acceptance. |
| `run --suite android` | Ordinary instrumentation on the owned native accelerated API26 emulator; not a physical/API27 runner. |
| `run --suite saf` | Four authentic Android grant stages with controlled synthetic test folder, owned AVD/evidence/cleanup; not universal external provider acceptance. |
| `run --suite full` | Host + ordinary Android + SAF, not C06 restore, D2D, physical tablet or a real library/reader UI suite. |
| `run --suite coverage` | Authentic matching JVM/Android inputs and retained classes/APKs, ordinary full inventory and approved scoped gates, informational production source inventory. No new app/di permanent gate. |
| `run --suite reader` / `library` | CLI recognizes names but workflow raises BLOCKED because these suites/features are not implemented; no tests executed. |
| `backup-preflight --serial <explicit-disposable-emulator>` | Separate read-only C06 discovery of serial/AVD/API/transport/PowerShell prerequisites. Refuses physical and SAF-owned/reserved AVD. It never invokes seed, acceptance init script, install/uninstall, backup/restore or consent. Preflight PASS cannot close C-DEF-01/02. |
| `Invoke-BackupPolicyAcceptance.ps1`, manual init/fixture | Explicit separately confirmed cloud-style local test transport procedure only, per [C06](../C06_BACKUP_RESTORE_ACCEPTANCE.md). Not automatic CI or a genuine D2D/physical acceptance runner. |

Local runner reports distinguish PASS/0, FAIL/1 and BLOCKED/2 using trustworthy executed evidence.
No provisioning, emulator boot, `backup-preflight`, restore, D2D or physical operation is authorized
or executed by this P05 task. Future commands above are capability descriptions, not execution logs.
Historical guides retain older counts/branch instructions: derive actual inventory at the new
run rather than treating their 165/228 or 128/205 examples as current executed counts.

## 4. P04 decision dependencies

Every decision remains **PENDING OWNER APPROVAL**. This matrix only indexes the unchanged
[P04 D01–D11 recommendations](FGH_INTERFACE_BOUNDARIES.md); it neither resolves them nor adds
new behavior. “Before” means before the affected approved feature task can rely on that choice.
Presentation-only work while integration is unresolved requires explicit owner-limited scope,
not a silent exception. I may finalize later only the implementation details consistent with
previously approved handoffs; it cannot defer the listed prerequisites until after consumers ship.

| Decision | F prerequisite | G prerequisite | H prerequisite | I / later integration | Separate approved work if needed | Status |
| --- | --- | --- | --- | --- | --- | --- |
| D01 root persistence | Resolve selected-root/session-only scope before browsing/restoration work | Coordinate access/restoration assumptions for selected roots | No root-setting addition implied | Implement approved state/restore behavior; revalidate grants | Persistence/shared contract if durable root store is chosen | PENDING OWNER APPROVAL |
| D02 provider-location backup | Resolve before any durable root/association storage change | Resolve before retaining/restoring locations | Keep existing proto non-sensitive; no URI setting | Verify approved restore/security treatment, including future transfer modes | Persistence/backup-policy PR if format or eligibility changes; no automatic Policy B expansion | PENDING OWNER APPROVAL |
| D03 registration owner | Resolve before register/favorite coordination | Resolve before registered open/availability handoff | None beyond common entry contract | Single approved registration/observation/rebind coordinator | Shared contract only if existing APIs/handoffs insufficient | PENDING OWNER APPROVAL |
| D04 registered/transient handoff | Freeze open intents before F begins dependent entry-point work | Freeze open input/metadata semantics before G begins | None beyond shared navigation plan | Route encoding, private-state restoration details and result wiring | Separate shared-contract change for any new shared type | PENDING OWNER APPROVAL |
| D05 nested folders | Resolve conversion/root/breadcrumb boundary before F navigation | No G-side URI conversion or folder logic | None | Implement approved bridge and combined root/grant tests | Separate storage/core contract work if approved bridge cannot use existing APIs | PENDING OWNER APPROVAL |
| D06 reader restoration | Agree callback/return-state needs with G/I | Resolve lifecycle/bounds/hint rules before G restoration/session state | Coordinate effects of theme/locale recreation | Final saved-state/navigation/recreation wiring; no live native state restored | Shared/persistence work if approved state requires new capability | PENDING OWNER APPROVAL |
| D07 cache/resource budgets | No competing thumbnail/cache expansion without scope | Approve initial ownership/concurrency/cache bounds and measurement plan before G consumer implementation | Coordinate page-fit changes with G bounds | Measure/finalize real-device budgets before performance acceptance | Separate dependency/contract change only if genuinely needed; no new P05 numbers | PENDING OWNER APPROVAL |
| D08 position timing | Favorite writes must preserve independent position data | Resolve visible-page checkpoints/loss window before G persistence scheduling | None | Integrate navigation/background/cancellation checkpoints; verify chosen timing | Separate contract/persistence PR only if existing atomic operation is insufficient | PENDING OWNER APPROVAL |
| D09 API26 locales | Coordinate recreation assumptions | Coordinate session/lease handling across locale changes | Resolve persistence-versus-host responsibility and approved language scope before H; limited presentation-only scope needs explicit approval | Validate/apply locale mechanism on API26/27, restart/system reset | Separately approved dependency/activity/manifest work if selected mechanism requires it | PENDING OWNER APPROVAL |
| D10 localization ownership | Agree prefixed files/keys before F | Agree prefixed files/keys before G | Agree prefixed files/keys before H | Own shared resources and final localized navigation/error copy | Shared-resource prerequisite allocation; no parallel edits to existing shared strings | PENDING OWNER APPROVAL |
| D11 handoffs/dependencies | Freeze entry inputs/events and allowed imports before F | Same before G; inject read/render values, no global graph | Same before H; repository persistence only | Own navigation/startup/Activity Results/shared dependency queue; finalize wiring after reviewed feature handoffs | Owner-approved shared contracts/dependencies first; no feature-side Gradle additions | PENDING OWNER APPROVAL |

F's immediate decisions are D01–D05, D10/D11 and relevant D06 handoff assumptions; G needs
D03/D04/D06–D08/D11 plus D10 and applicable D01/D02 access-state decisions; H needs D09–D11
and D02's prohibition on unreviewed sensitive fields. All require feature-specific owner scope.
I depends on all applicable rows. Integration may finalize destination wiring, configured host
lifecycle and resource application after these boundaries are approved; it may not invent new
contracts, persistence or dependencies independently. No feature branch is created by this task.

## 5. Evidence and provenance

| Evidence | Revision / result | Inspection and limits |
| --- | --- | --- |
| Main integration | `f94841f9f1eead508dd81b670d0943f3b3ae19bb`; [PR #12](https://github.com/alexbonavila/PartiturasPDF/pull/12), [#13](https://github.com/alexbonavila/PartiturasPDF/pull/13), [#14](https://github.com/alexbonavila/PartiturasPDF/pull/14) merged | Git refs and public PR pages rechecked 2026-10-17. Not a formal C/E acceptance decision. |
| Main push [CI #58](https://github.com/alexbonavila/PartiturasPDF/actions/runs/38926693866) | `f94841f9`; seven mandatory jobs PASS | Public run result page rechecked; API27/36 were not requested here. No new raw log/XML count inspection. |
| Manual compatibility [CI #59](https://github.com/alexbonavila/PartiturasPDF/actions/runs/38926745562) | `f94841f9`; seven mandatory jobs and API27/API36 emulator jobs PASS | Public run and compatibility job result pages rechecked; not physical-device or latest readiness-commit execution. |
| P01/P02 | `97d0be7e217f07a61ddf6b3055ebb5040cabdc75` | Status correction and append-only E reconciliation delivered; original E findings retained. |
| P03 code | `618c9aa753e9597835b80c4862876e8d34e2fc85` | Internal production-used cleanup helper; three added JVM cases, strengthened existing ownership/retry/cancellation cases and isolated Room Android test. Not a new public contract. |
| P03 native coverage | Owner-reported Windows/API26 PASS; `20261016T230454Z-941f1f3e04d0482ba9ea5063aeed730a` at exactly `618c9aa` | Supplied summary: factory 37/37 lines and 14/14 branches; entire app/di 53/53 and 14/14, zero missed lines/branches. Raw `.exec`/`.ec`/XML/classes not independently inspected; actual Windows test counts not supplied, not inferred. Classification stays INFORMATIONAL; no new gate. |
| P04 planning/P03 record | `e516665fded37c3a226b8a297302a6e84e51a4ca` | Existing interface inventory, F/G/H/I ownership and eleven pending recommendations. No feature implementation authorized or delivered. |
| Native L01–L04 historical evidence | Exact revisions and owner-reported numbers in [L04D](../LOCAL_COMPATIBILITY_VALIDATION.md) | macOS `87b864de`, earlier Windows `27cef9f` / `9d10739d`; not newly rerun by P05 and not new Windows counts for `618c9aa`. |

Archive native run directories with raw execution data, original matching bytecode/APKs, hashes,
JUnit XML, logs, settings/cleanup outcomes and exact commit/environment. Public CI success pages
are useful status evidence, not proof that this task downloaded retained artifacts or verified
branch protections. CI artifacts have finite retention (historically 14 days); owner preserves
necessary evidence before expiry. Source inventory and historical counts are not new execution.

## 6. Three independent readiness questions

### A. Is the readiness branch technically delivered?

**P01–P05 implementation/documentation delivered and locally validated for review**;
P05 verification is recorded in section 8 and publication in the final delivery report. R0 established branch ancestry and clean starting state; P01/P02
corrected status/preserved E history; P03 fixed testability and gained owner-reported authentic
numerical coverage; P04 supplied planning recommendations; P05 supplies this consolidated register.
The complete branch includes three scoped P03 Kotlin/test files as well as documentation; P05
itself changes only authorized Markdown. No additional production defect is demonstrated by this
review. Missing root persistence, nested-folder handoffs, locale application and aggregate cache
budgets are documented future capability/decision gaps, not permission to implement them here.

Technical delivery is not unconditional phase acceptance or approval to merge. P04 decisions
remain pending, P05 executes no deferred test and no later feature is authorized.

### B. Is the branch ready for PR review?

**Suitable for the owner to review scope and prepare a PR when explicitly authorized; not
merge-approved.** This task creates no PR. Before opening, owner reviews the complete
`origin/main..chore/pre-fgh-readiness` diff (including P03), validation/attribution and P04/P05
boundaries, and rechecks latest base/head. The final P05 report records executed local checks and
published SHA. No latest-head hosted CI PASS is claimed; feature-only branch push does not trigger
this repository's main/PR CI by itself.

Before integration, the latest authorized PR/current base must pass build, lint, unit-tests,
instrumented-api26, localization, pdf-fixtures and quality-gate (including existing scoped
coverage), with trustworthy actual execution and no unexplained skips. Obtain required applicable
final-revision API27/recent compatibility evidence, resolve review conversations and any defects,
and retain evidence/provenance. Existing main CI and owner coverage at `618c9aa` do not replace
checks at the new PR revision. Verify actual protection/check status when access permits; no bypass
or settings change is authorized. Only the owner may authorize and perform a merge after the
applicable policy conditions are met; reviewability is not a declaration that every merge criterion
is already satisfied.

### C. Are Phases C and E formally accepted?

**Unconditional formal acceptance is not established.** Physical Android8.1 is still missing for
applicable functional phases; C06 real cloud-style restore and genuine D2D remain deferred, with
other transfer mechanisms conditional. Applicable E external-provider/manual and real device/resource
criteria are unresolved. Future F/G/H/I UI/performance cannot have passed before implementation.
P03's numerical coverage objective now has attributed evidence, but it does not close these
independent obligations. Existing merges record technical integration, not retroactive manual PASS.

Deferral is a scheduling decision, not a policy exception. If an applicable requirement prevents
phase closure or integration under current policy, it remains blocking until fulfilled or resolved
through a separately approved compliant change-control process. This register grants no waiver,
does not propose weakening policy, and does not reinterpret incomplete mandatory acceptance as a
nonblocking defect category merely to merge the readiness branch.

## 7. Resumption and final review sequence

1. Owner reviews P01–P05 and the cumulative diff, evidence SHAs and attribution. Preserve this
   register's open statuses; acknowledge P03 coverage evidence separately from formal C/E closure.
2. Review pending P04 D01–D11 choices and identify required approvals/shared prerequisite work.
   They may remain open for the documentation PR review; resolve affected decisions before
   F/G/H/I implementation depends on them. Do not move missing capability into a feature package.
3. For each acceptance item being resumed, approve exact scenario/environment/SHA, responsible
   task, synthetic inputs, safety/consent and expected evidence before any operation. Physical,
   backup and genuine D2D are separate workstreams; do not provision or execute them now.
4. Recheck current main/branch, review local validation and remaining policy criteria, and obtain
   explicit authorization to open the readiness PR. No PR is created by this P05 delivery.
5. Obtain all mandatory latest-head CI/current-base evidence and applicable compatibility/review
   outcomes. Record actual failures/blockers and new evidence without rewriting historical audits.
6. Owner decides integration only under existing change control; complete applicable manual/
   physical acceptance before claiming formal phase closure. Later feature development still
   requires its own explicitly approved task. No automatic start follows from this register.


## 8. P05 local verification — 2026-10-17

Executed in the Linux x86_64 cloud checkout based on `e516665` with this scoped documentation diff,
using Python3.12.14 and the existing JDK25/SDK37 setup. These are cloud outcomes, not new native
Windows/Android results or latest-head GitHub CI. No test, dependency or toolchain change was made.

| Command / review | Observed outcome |
| --- | --- |
| `bash ./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest --rerun-tasks --no-build-cache --max-workers=4 --console=plain` | PASS; 57 tasks executed; no Lint errors, 20 warnings. |
| `python3 scripts/verify_test_results.py app/build/test-results/testDebugUnitTest --required-test com.abdev.partituraspdf.ExampleUnitTest.addition_isCorrect` | PASS; 168 actual JVM tests, zero failures/errors/skips, independently checked in JUnit XML. |
| `python3 -m unittest discover -s scripts/tests -v` | PASS; 246 infrastructure tests. |
| `python3 scripts/validate_localization.py` | PASS; en/ca/es resources/format contracts. |
| `python3 test-fixtures/scripts/verify_fixtures.py` | PASS; all 15 canonical hashes/structure/render checks. |
| `git diff --check`; complete scoped diff, links and preservation review | PASS; authorized Markdown only, existing C/E histories byte-for-byte unchanged, P04 recommendations unchanged apart from the cross-reference. Public PR/main CI links rechecked as section 5 describes. |
| Android instrumentation / fresh combined coverage | NOT RUN here; no connected device and `/dev/kvm` absent. No software-emulator fallback or provisioning attempted. |
| Physical tablet, backup/restore, D2D, external-provider/manual and reader-performance acceptance | NOT RUN; deferred operations were not executed. No consent was requested and no device/data operation performed. |

Owner review, eventual latest-PR-revision CI and the open acceptance requirements above remain
independent prerequisites. Local validation does not constitute merge or formal phase approval.
