# Phase M consolidated final validation and acceptance plan

2026-11-10. Block C starts at `4b62cd7c16b4cdfdc7665b66f27f5e913cf60797`,
`feat/pdf-safe-write-m`. The publication containing this document is the Block C
source delivery, not an execution result. Freeze its full SHA before the campaign.
**IMPLEMENTED — NOT RUNTIME VERIFIED. PHASE M NOT ACCEPTED. PRODUCTION WRITES BLOCKED.**
No release, merge, PR, dependency change or activation is authorized by this plan.
All commands below are OWNER ONLY / NOT RUN by Codex.

## Evidence baseline and source findings

- Block A implemented working-copy transformation, strict qualification and
  semantic/text/native validation behind an explicit factory. Block B implemented
  a retained draft/editor/picker owner and recovery binding, leaving both the
  production authorization and provider proof closed.
- Owner campaign at `1e24c95c1d694123e4f76a6f254d736c15838ac7`: 46 API26 methods,
  spike21 PASS + test-adapter Save As11 PASS + production8 PASS/6 FAIL. Raw failure
  logs are unavailable here. These six FAILs stay OPEN until executed on the new SHA.
- Earlier independent synthetic fidelity evidence is scoped to its original
  [campaign and hashes](M_PDFBOX_INDEPENDENT_VALIDATION.md#completed-owner-campaign--2026-11-09).
  It is neither a production-engine nor Block B/C PASS.
- Codex inspected source, immutable synthetic PDF dictionary text and diffs, not
  runtime artifacts. No build, test, compiler, validator, scanner or device ran.

### Block A: identified rejection and remaining diagnosis

Fixture02's actual page dictionary contains `/Trans <<>>`; fixture01 also has
this representation. The previous page-key allowlist rejected it before page-index
checking or transformation. This is a precise logical counterexample to the
claimed support for these ordinary pages, independent of guessing from a cast.
An empty PDF transition dictionary has only default Replace behavior, not an
animated transition. Qualification now permits ONLY an empty non-stream resolved
dictionary and preserves it; nonempty, stream and malformed values still reject.
No catalog/action/filter/font/annotation/resource/geometry/raster rule is relaxed.

| Historical failing method in MProductionPdfEngineTest | Source-supported finding / correction | Still required |
| --- | --- | --- |
| productionFactoryReordersRotatesPublishesAndCleans | Default02 rejected by empty Trans; qualify semantic no-op, retain full validator/publication assertions | Fresh exact output/readback/cleanup evidence |
| supportedCorpusReorderExtractionGeometryAndAllQuarterTurns | First01 has empty Trans; same structural rejection blocks corpus start | Every listed fixture/turn must execute; other document-specific rejections are not ruled out |
| actualProtectedCorruptAndOutOfRangeRequestsRejectWithoutPicker | Out-of-range02 never reaches index check because qualification rejects first | Protected/corrupt remain rejected; supported02 yields InvalidPlan; no picker |
| productionValidatorRejectsWrongPlan | Source02 rejected before output is Produced | Produce valid output, then refuse wrong plan |
| alteredResourceGraphCannotReceiveValidationAuthority | Source02 rejected before mutation test can start | Deliberate resource mutation must still refuse validation |
| realProductionSealCheckpointAndCleanupFaultsNeverInventSuccess | Its success/cleanup-debt branch prepares02, encountering the same blocker | Seal/checkpoint faults reject, and verified output with cleanup PENDING stays verified |

This establishes a common first source blocker, NOT that every runtime failure
has only that cause. Exact native raster comparison on fractional geometry,
other qualification rules and parser behavior may reveal additional failures.
Do not add pixel tolerances, fixture privileges, lenient parsing or new supported
features to manufacture success. Retain exact typed reason/cleanup diagnostics.
Qualification still precedes plan indexing: protected/unsupported data does not
become InvalidPlan just because the request is also out of range. No precedence
contract was changed. New tests pin this distinction.

### Block C ownership and recovery corrections

| Finding | Severity / correction | Regression evidence source (NOT RUN) |
| --- | --- | --- |
| Canceled IO return after Ready could lose the live session reference | P2: retain Pending before conservative settlement, including when preparation's finally installs it during drain | MEditCoordinatorTest.canceledDispatcherReturnRetainsReadyOwnerAndBlocksAnotherTransaction |
| Repeated settlement consumed the token again and replaced a precise uncertain outcome | P2: cache first result, prefer session's verified record, never replay complete on a known result | dismissalRetainsExactUncertainOutcomeRatherThanConsumingCallbackAgain |
| Verified cleanup debt disappeared on reopening | P2: retain verified/PENDING result; cannot save again or falsely show cleanup complete | verifiedCleanupDebtSurvivesCloseReopenAndFreshRecoveryWithoutBecomingFailure |
| Recovery only ran once, leaving stale empty observations | P2: explicit entry refresh, one concurrent scan, save blocked before successful scan; every preparation dirties freshness | recoveryRefreshesAtEachEntryCoalescesConcurrentScansAndBlocksSaveUntilComplete |
| Repeated entry reset a user-edited draft | P2: same-route DRAFT entry is idempotent | repeatedEntryKeepsEditedDraftAndNeverRestoresDefaultPageSelection |
| Recovery/cleanup defects could leave misleading UI/diagnostics | P2: keep failed scan required/BLOCKED, retain primary and suppressed secondary evidence, never false empty success | canceledOrFailedRecoveryNeverTurnsAnUnknownScanIntoEmptySuccess; existing exceptional-cleanup tests |
| Restore marker only covered an outstanding picker request | P2: Boolean now covers preparing/publishing/retained uncertain session/cleanup debt; still no restored authority | coordinator marker assertions, existing process-restored and actual Activity tests |
| Recovery's absent-root check had a create-on-disappearance race | P2: existing-only PrivateRoot mode fails rather than mkdir; defaults for actual capture unchanged | PrivateJournalTest.recoveryRootOpeningNeverCreatesMissingDirectoryAndRetainsConfinement |
| Destination binding could proceed without both observed/live READ+WRITE | P2: require both at bind and revalidation, before writer; provider proof still mandatory | MDocumentsProviderTest.destinationRequiresObservedAndLiveReadWriteBeforeProvenanceOrWriter |

No new transaction grammar, owner-acknowledgement API, storage ledger, Reader,
metadata migration, provider proof or dependency. The retained host joins editing
before Reader/graph teardown. Background native-session replacement is not a new
Reader intent; navigation/document replacement invalidates callbacks. The editor
remains an overlay on the single Reader, with no IME or thumbnail renderer.

| UI phase | Live ownership / permitted disposition |
| --- | --- |
| DRAFT | Data-only plan; same-route entry preserves edits; closed gate prevents capture |
| PREPARING | Joined work owns capture; no callback yet; cancellation before Ready cannot authorize creation; any unhanded Ready is retained |
| PICKER | Exact original handoff, token and one launch claim; missing callback remains uncertain |
| PUBLISHING | Session serializes at most one writer; cancellation joins before settlement; no retry/delete/rollback claim |
| RESULT | Verified + cleanup independent; uncertain session/admission retained; dismiss removes presentation, not evidence or authority |

Every explicit editing entry may perform one bounded IO refresh; concurrent entry
coalesces and active prepare/publish/drain suppresses scanning. New preparation
marks observations stale. Retrying a rejected preparation requires a completed
fresh entry scan. No periodic work, new permission, picker launch or publication
occurs during recovery. A matching historical read is never a new receipt.
Canceled/failed scans stay unverified. Known destinations are read only with
already-held READ permission. Existing journal, file/confinement and quota limits
remain unchanged. A scan cannot create a missing root, even in a disappearance race.

## Requirement-to-evidence and open acceptance ledger

References below name existing test classes under the corresponding JVM/Android
packages. Test existence is IMPLEMENTED; current-source execution is NOT RUN.
See [original critical plan](M_M1_CRITICAL_TEST_PLAN.md) for the full scenarios.

| Requirement | Implemented evidence source / acceptance still required | Current gate |
| --- | --- | --- |
| M-T01 | Block1PrivateStorageTest, PrivateSnapshotStoreTest: sealed bytes, registered/transient identity and independent leases; actual API26 closure needed | NOT RUN |
| M-T02 | MStorageBudgetTest + Block1PrivateStorageTest: observed-byte ceilings, overflow/retained charges; full boundary/provider coverage inventory required | NOT RUN |
| M-T03 | PrivateSnapshotStoreTest, MPdfboxSaveAsIntegrationTest, MEditCoordinatorTest: cancellation/read failures and joined owners; real provider responsiveness not proved | NOT RUN |
| M-T04 | Snapshot source is captured-byte evidence, not a live external revision; controlled mutation/independent validation required | BLOCKED for universal external consistency; NOT RUN for defined cases |
| M-T05 | PrivateSnapshotStoreTest + production seal faults; no partial output minting | NOT RUN |
| M-T06 | MProductionPdfEngineTest: protected14/corrupt15/invalid plans, typed reasons | FAIL history; new SHA NOT RUN |
| M-T07 | Production corpus/geometry/rotation/resource mutation tests, separate historical fidelity campaign | FAIL history; new SHA NOT RUN |
| M-T08 | MPdfboxSaveAsIntegrationTest + MEditPickerBindingTest: disposable output/readback, original survival; real picker/grant acceptance separate | NOT RUN; production provider BLOCKED |
| M-T09 | MDocumentsProviderTest, missing-rights/cancellation callback tests; no grant escalation | NOT RUN |
| M-T10 | Disposable same-name/new-ID and alias tests; names/URI inequality are not proof | NOT RUN; production proof BLOCKED |
| M-T11 | Publisher/provider partial-write/close/readback tests; one attempt, uncertain artifact retention | NOT RUN |
| M-T12 | Session/coordinator cancellation and late-callback tests; real process/picker interruption remains pending | NOT RUN |
| M-T13 | PrivateJournalTest, MJournalStateTest, MJournalRecoveryTest; bounded read-only reconciliation and corruption retention | NOT RUN; genuine process/power-loss evidence not inferred |
| M-T14 | Provider refusal and live-right checks; no automatic regrant; authentic grant revocation needs disposable owner authorization | NOT RUN |
| M-T15 | Coordinator registered/transient tests and unchanged read-only registry/metadata dependencies; no automatic rebind/favorite/page/root transfer | IMPLEMENTED; native/Room observation pending |
| M-T16 | Fixed byte/scratch/graph/token/page/image/raster bounds; not a universal parser heap/CPU guarantee | BLOCKED security/resource risk disposition; separate benchmark OMITTED BY OWNER |
| M-T17 | Real descriptors/disposable provider + registry tests; API26 then27/36, actual external provider behavior separate | NOT RUN |
| M-T18 | Packaging script and APK Analyzer/manual protocol; no-backup location is not transport acceptance | NOT RUN / OWNER DECISION REQUIRED |
| M-T19 | Existing primary/secondary/cancellation/release tests plus retained verified-cleanup results | NOT RUN |
| M-T20 | Production gate UNAPPROVED, default unavailable engine, proof rejects all; Block B/C host edits expressly authorized; no Replace Original/merge/annotation feature | IMPLEMENTED; packaging/activation acceptance BLOCKED |

M01 capability audit and M02 approved contracts remain their accepted source
findings; M03/M04 storage/staging, M05 validation, M07 Save As binding and M08
recovery now have source implementations. M06 engine/security qualification, M09
native metadata-independence evidence, M10 real provider/API compatibility, M11
packaging/backup/legal and M12 final acceptance still require their respective
owner evidence/decisions. No original H/S/D decision is replaced by this delivery.

Open issues to retain until explicit evidence/decision:

1. **FAIL:** six owner-reported production tests; source correction is not a PASS.
2. **NOT RUN:** all Block B/C builds/tests/localization/fixtures/Lint/current CI.
   Block C adds eight JVM and seven Android methods; no executed count claimed.
3. **BLOCKED:** no approved provider-general or allowlisted production independence
   proof. READ+WRITE, CREATE_DOCUMENT, different URI/name and empty size do not suffice.
4. **BLOCKED:** no owner-resolution UI/contract for safely disposing uncertain
   public effects. No automatic acknowledgement, retained-file deletion or replay.
   Rejected candidate URI/flags remain in memory; the existing null-capability
   journal records DESTINATION_UNKNOWN. Durable rejected-candidate attribution
   needs a separately reviewed contract, not a fabricated write capability.
5. **BLOCKED / OWNER DECISION REQUIRED:** complete current advisory inventory,
   fork patch/reachability, actual PDFBox2.0.27.0 and BC1.86 resolved binaries,
   licenses/NOTICE and distribution. Declared fixed ranges are not security clearance.
6. **NOT RUN:** new-logic scoped coverage. Existing A1/B5/C05 gates do not cover
   all M presentation/workflow/editing/transaction classes. Do not infer >=90% line /
   >=80% branch M coverage from those older gates. A complete M compiled denominator,
   authenticated JVM/Android probe attribution and scoped report still require an
   approved coverage integration/measurement step; no fictitious Gradle task exists.
7. **NOT RUN:** actual picker/OS grants, process death, delayed external callbacks,
   supported external providers, physical Android8.1, rotation/theme/language,
   TalkBack, narrow/large-font and human linguistic review. CI cannot replace these.
8. **UNRESOLVED:** cooperative parser/native/provider cancellation is not a hard
   deadline or proof against malicious resource exhaustion. No dedicated physical
   memory/performance campaign is scheduled; owner omission is not leak-free PASS.
9. **NOT RUN:** latest-source API26/27/36 and current-base integration/CI. No PR,
   merge, Phase K/L integration, production activation or release approval exists.

## One owner-controlled final campaign (PowerShell)

Prerequisites: reviewed Block C SHA, existing JDK/SDK/Gradle setup, Python with
`test-fixtures/requirements-fixtures.txt` already satisfied, `python3` available
for existing Gradle persistence tasks, adb/APK Analyzer on PATH, and an explicitly
owned disposable emulator. No dependency/tool installation is hidden in these
commands. Retain raw reports/APKs privately; do not commit or automatically upload.
Execute stages once per frozen revision; retain each actual failure. Stop the
relevant acceptance claim on FAIL/BLOCKED, diagnose, and rerun only after a reviewed
correction. Never relabel a partial run PASS or automatically retry a public write.

### 1–2. Freeze branch, revision and clean source

```powershell
$expected = Read-Host 'Reviewed full Block C commit SHA'
if ($expected -notmatch '^[0-9a-f]{40}$') { throw 'Full SHA required' }
if ((git branch --show-current) -ne 'feat/pdf-safe-write-m') { throw 'Wrong branch' }
if ((git rev-parse HEAD) -ne $expected) { throw 'Wrong revision' }
if (git status --porcelain) { throw 'Dirty source' }
git diff --check
if ($LASTEXITCODE -ne 0) { throw 'Whitespace failure' }
$audit = Join-Path (Get-Location) ('build\m-final-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $audit | Out-Null
$expected | Set-Content -Encoding utf8 (Join-Path $audit 'source-sha.txt')
```

Record OS/JDK/SDK/Python/tool versions, source digest using existing attribution
infrastructure, timestamps, branch, emulator model/API/ABI/AVD/boot identity.
No old SHA or independently reviewed historical result becomes this run's evidence.

### 3–8. Build, JVM, Python, localization, fixtures, Lint

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest --stacktrace
if ($LASTEXITCODE -ne 0) { throw 'Build BLOCKED; retain diagnostics' }
.\gradlew.bat :app:testDebugUnitTest --stacktrace --rerun-tasks
if ($LASTEXITCODE -ne 0) { throw 'JVM or A1 coverage FAIL/BLOCKED' }
python scripts\verify_test_results.py app\build\test-results\testDebugUnitTest --required-test com.abdev.partituraspdf.ExampleUnitTest.addition_isCorrect
if ($LASTEXITCODE -ne 0) { throw 'Incomplete or failed JVM inventory' }
python -m unittest discover -s scripts/tests -v
if ($LASTEXITCODE -ne 0) { throw 'Python failure' }
python scripts\validate_localization.py
if ($LASTEXITCODE -ne 0) { throw 'Localization failure' }
python test-fixtures\scripts\verify_fixtures.py
if ($LASTEXITCODE -ne 0) { throw 'Canonical fixture failure' }
.\gradlew.bat :app:lintDebug --stacktrace
if ($LASTEXITCODE -ne 0) { throw 'Lint failure' }
```

Retain actual counts/skips, `app/build/test-results/testDebugUnitTest/`,
`app/build/reports/tests/testDebugUnitTest/`, `app/build/reports/lint-results-debug.*`,
all command outputs/exits and fixture results. Complete localization inventory
remains187 strings, including32 M keys in each default/es/ca/en bundle. Check
placeholders, default=es, actual language delivery and human copy separately.
No unexplained skip counts as complete acceptance.

### 9. Existing coverage gates and the M coverage hold point

```powershell
.\gradlew.bat :app:verifyContractCoverage --stacktrace
if ($LASTEXITCODE -ne 0) { throw 'A1 coverage failure' }
```

A1 XML/HTML is under `app/build/reports/tests/testDebugUnitTest/`. B5 and C05
require genuine matched Android inputs: do not invoke them here pretending they
are host-only tasks. `verifyPdfAdapterCoverage` depends on connected instrumentation.
After stage12 use the existing owned collector for A1/B5/C05:

```powershell
python scripts\local_e2e.py doctor
# Proceed only if the existing dedicated API26 AVD ownership/readiness is verified.
python scripts\local_e2e.py run --suite coverage
if ($LASTEXITCODE -ne 0) { throw 'Coverage FAIL/BLOCKED; preserve original producer report' }
```

It runs real tests, captures classes/APKs/JaCoCo IDs/sessions and worker snapshots,
then existing report/gate verification. Retain its unique
`build/local-e2e/reports/<run>/coverage/` evidence and summary. Do not provision,
adopt/reset another AVD or rewrite a BLOCKED capture. Collector APKs are a separate
instrumented build: retain their own hashes; do not assign them to stage10 APKs.
This does NOT supply the missing M-scoped report in open issue6. Obtain the reviewed
coverage extension/evidence before final acceptance; do not change denominators
or thresholds or use a favorable project-wide percentage.

### 10. Exact Phase M/API26 selected campaign and original evidence

Verify one owned disposable emulator serial explicitly; never select the first
ADB device. No real-user documents, permission revocation or data deletion.

```powershell
$serial = Read-Host 'Owner-verified disposable API26 emulator serial'
if ($serial -notmatch '^emulator-[0-9]+$') { throw 'Explicit emulator required' }
if ((adb -s $serial shell getprop ro.kernel.qemu).Trim() -ne '1') { throw 'Not an emulator' }
if ((adb -s $serial shell getprop ro.build.version.sdk).Trim() -ne '26') { throw 'Wrong API' }
$env:ANDROID_SERIAL = $serial
$apks = @('app\build\outputs\apk\debug\app-debug.apk',
          'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk')
$apks | ForEach-Object { Get-FileHash $_ -Algorithm SHA256 }
adb -s $serial install -r $apks[0]
if ($LASTEXITCODE -ne 0) { throw 'Application install BLOCKED' }
adb -s $serial install -r -t $apks[1]
if ($LASTEXITCODE -ne 0) { throw 'Test install BLOCKED' }
adb -s $serial shell pm list instrumentation
```

Independently record `pm path` for `com.abdev.partituraspdf` and its `.test` package,
pull/hash every returned installed base/split APK into the private audit directory
and compare with the correct host APKs. Retain actual AVD/API/ABI/boot identities.
Missing/ambiguous paths, mismatched packages/APKs or unavailable attribution BLOCK
acceptance; do not claim independent installed-binary attestation from recorded
hash strings alone.

Generate source-derived selection with the existing inventory function. This is
an owner command snippet, not a new test runner or committed fixture inventory:

```powershell
@'
from pathlib import Path
import json, sys
sys.path.insert(0, 'scripts')
import local_test_evidence as e
classes = {
 'com.abdev.partituraspdf.storage.transaction.Block1PrivateStorageTest',
 'com.abdev.partituraspdf.storage.transaction.MProductionPdfEngineTest',
 'com.abdev.partituraspdf.storage.transaction.MPdfboxSaveAsIntegrationTest',
 'com.abdev.partituraspdf.storage.transaction.MDocumentsProviderTest',
 'com.abdev.partituraspdf.storage.transaction.MEditPickerBindingTest',
 'com.abdev.partituraspdf.pdf.presentation.MPageEditorTest',
 'com.abdev.partituraspdf.pdf.engine.spike.PdfboxAndroidSpikeTest',
 'com.abdev.partituraspdf.app.integration.AppHostActivityTest',
}
inventory = e.android_inventory(Path('app/src/androidTest'))
assert classes <= {n.rsplit('.', 1)[0] for n in inventory}
expected = sorted(n for n in inventory if n.rsplit('.', 1)[0] in classes or n == e.SMOKE)
assert expected and len(expected) == len(set(expected))
audit = Path(sys.argv[1])
(audit/'expected.json').write_text(json.dumps(expected, indent=2), encoding='utf-8')
selectors = [n.rsplit('.', 1)[0]+'#'+n.rsplit('.', 1)[1] for n in expected]
(audit/'selection.txt').write_text(','.join(selectors), encoding='utf-8')
print('Exact selected method count:', len(expected))
'@ | python - $audit
if ($LASTEXITCODE -ne 0) { throw 'Selection BLOCKED' }
$selection = Get-Content -Raw (Join-Path $audit 'selection.txt')
$raw = & adb -s $serial shell am instrument -w -r -e class $selection com.abdev.partituraspdf.test/androidx.test.runner.AndroidJUnitRunner 2>&1
$adbExit = $LASTEXITCODE
$raw | Set-Content -Encoding utf8 (Join-Path $audit 'instrumentation.log')
$adbExit | Set-Content -Encoding utf8 (Join-Path $audit 'adb-exit.txt')
```

This retains normalized UTF-8 stdout/stderr lines, not a claim about original
wire-byte encoding. No automatic functional retry. Parse exact identities and
instrumentation completion with the existing strict parser, retaining failures:

```powershell
@'
from pathlib import Path
import json, sys
sys.path.insert(0, 'scripts')
import local_test_evidence as e
p = Path(sys.argv[1]); expected = json.loads((p/'expected.json').read_text(encoding='utf-8'))
exit_code = int((p/'adb-exit.txt').read_text(encoding='utf-8-sig').strip())
try:
    cases = e.parse_android((p/'instrumentation.log').read_text(encoding='utf-8-sig'), expected)
except e.IncompleteInstrumentation as failure:
    e.write_junit(p/'junit.xml', 'Phase M API26', failure.cases, str(failure))
    (p/'summary.json').write_text(json.dumps({'status':'BLOCKED','complete':False,'adb_exit':exit_code}), encoding='utf-8')
    raise SystemExit(2)
assert len(cases) == len(expected) == len({c['name'] for c in cases})
status = 'BLOCKED' if exit_code else ('FAIL' if any(c['status'] != 'PASS' for c in cases) else 'PASS')
e.write_junit(p/'junit.xml', 'Phase M API26', cases, 'ADB exit nonzero' if exit_code else None)
(p/'summary.json').write_text(json.dumps({'status':status,'complete':not exit_code,'adb_exit':exit_code,'tests':len(cases)}, indent=2), encoding='utf-8')
raise SystemExit({'PASS':0,'FAIL':1,'BLOCKED':2}[status])
'@ | python - $audit
if ($LASTEXITCODE -ne 0) { throw 'Native campaign FAIL/BLOCKED; retain all artifacts' }
Get-ChildItem $audit -File | Get-FileHash -Algorithm SHA256
```

PASS requires the exact expected set, no duplicate/missing/unexpected/skipped test,
all completions PASS, valid `INSTRUMENTATION_CODE: -1` and ADB exit0. These M tests
do not authorize auxiliary progress events. Strict parser default is intentional.
Historical totals must not be confused with the larger current source
inventory. Record the actual count and individual six original methods.

### 11–12. Compatibility and existing Reader/Library/SAF regressions

The existing `CI` workflow `.github/workflows/ci.yml` supports dispatch on the
reviewed branch with `compatibility=true`. Only after owner execution authorization:

```powershell
gh workflow run ci.yml --repo alexbonavila/PartiturasPDF --ref feat/pdf-safe-write-m -f compatibility=true
```

No dispatch was performed by Codex. Verify actual source SHA, all required jobs
including compatibility-api27/36 and quality-gate, original logs/JUnit/coverage
artifacts and counts. Never equate queued/skipped/cancelled jobs with PASS. This
workflow includes the existing controlled native diagnostics on CI-owned emulators;
review that execution scope before dispatch. No new destructive local campaign.

Existing local regression commands, ONLY for their already-owned API26 environment:

```powershell
python scripts\local_e2e.py doctor
python scripts\local_e2e.py run --suite full
if ($LASTEXITCODE -ne 0) { throw 'Regression FAIL/BLOCKED' }
```

Retain `build/local-e2e/reports/<run>/` source/device/APK/log/JUnit attribution.
The suite includes existing interactive SAF acceptance; it is not an unattended
or physical-device command. Review its own prompts/ownership prerequisites. Do
not provision/reset an AVD, grant access to user documents, adopt a foreign device,
kill workers or alter settings outside a separately approved disposable campaign.
Full CI/ordinary tests and controlled diagnostics retain their own classification;
no external SAF or OS process-death acceptance is inferred from aggregate totals.
Run the existing coverage step9 only after this regression evidence is retained.

### 13–14. Packaging, dependency, advisory and legal review

Use the [current Block A protocol](M_PDFBOX_SECURITY_AND_PACKAGING_GATES.md#block-a-current-gates--2026-11-09)
at the frozen Block C SHA (historical earlier protocol's hard-coded SHA is not current).
Commands include actual debug/release/test graphs and dependencyInsight, archive
hashes and APK Analyzer, not just source declarations. In particular:

```powershell
python scripts\verify_m_engine_packaging.py --debug app\build\outputs\apk\debug\app-debug.apk --release app\build\outputs\apk\release\app-release-unsigned.apk --test app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
if ($LASTEXITCODE -ne 0) { throw 'Packaging FAIL/BLOCKED; do not weaken markers' }
```

Preserve a copy/hash of each actual build before another variant overwrites it.
Confirm PDFBox/BC production inclusion is intentional, all three BC modules resolve
strict1.86, test provider/spike/fixtures/private artifacts are absent from main
APKs, glyph/resources/license assets exist, and permissions/native ABI payloads are
fully attributed. Release proof cannot come from debug compilation. Inspect exact
resolved archives and current advisories/reachability/NOTICE obligations. Missing
inventory or legal disposition is BLOCKED / OWNER DECISION REQUIRED, not all-clear.
No automatic dependency upgrade, codec install, signing or redistribution.

### 15–17. Manual checks, final ledger and owner decision

Record exact SHA/APKs/API/device/steps/results for draft reorder/rotation/exclusion,
empty selection, one-based labels, repeated entry/Back, rotation/theme/language,
background/foreground, narrow/large fonts, keyboard and TalkBack. Default/es/ca/en
copy and verified/uncertain/cleanup wording require human review. Physical Android8.1
functional acceptance remains pending; the separate profiling campaign stays omitted.

In the current production app the expected outcome is a usable read-only Reader
and draft with Save As disabled, no resolver writer and no metadata changes. Real
production publication is not an allowed acceptance experiment before the provider
and activation decisions. Disposable injected-test flows do not waive that boundary.
A real external picker/provenance test or OS interruption scenario needs separately
reviewed authority, retained evidence and no real-user document exposure.

Populate every ledger row above with exact artifacts, source, results and owner
classification. Resolve the six historical FAILs with new individual executions,
all new/old regressions, complete M coverage, compatibility, security and legal
requirements. Only then may the owner separately approve a narrowly reviewed source
activation (qualified factory AND approved destination proof), final acceptance,
PR/integration and merge. None is implied by publishing Block C. No tag, APK upload
or release is authorized. Phase M remains open until these decisions are recorded.
