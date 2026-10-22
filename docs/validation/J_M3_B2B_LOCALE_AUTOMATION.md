## Continued manual validation on API27 and API36

After the separately recorded API26 pilot, run only the remaining emulators in a
**new** evidence directory. This does not mutate or claim to resume the earlier
15-step run, which remains an archived BLOCKED result. Each selected emulator
gets its own current baseline, all four locale scenarios and an exact restoration.
The CLI does not silently include API26:

```bash
cd "/Volumes/Intenso/Code/Prs/PartiturasPDF"
git pull --ff-only origin test/mvp-stabilization-j
TMPDIR=/private/tmp python3 -m unittest discover -s scripts/tests -p 'test_j_m3_b2b_locale_matrix.py' -q
matrix="build/j-m3-b2b/$(python3 -c 'import uuid; print(uuid.uuid4().hex)')"
python3 scripts/j_m3_b2b_locale_matrix.py manual-run --apis 27,36 --output "$matrix"
python3 scripts/j_m3_b2b_locale_matrix.py report --output "$matrix"
```

`--apis` accepts unique comma-separated API values among `26,27,36`; the
default remains all three. Reports declare `selected_apis` and require complete
coverage only for that explicit selection. Do not merge evidence runs together or
claim that a subset alone proves the three-device matrix.

Read-only `getprop` calls used in emulator identity validation receive at most
three attempts after ADB transport failure. No retries are applied to Android
instrumentation, APK installation, language edits, or assertions. Identity
checks remain required and any persistent failure is BLOCKED.

---

## Simplified manual acceptance (current workflow)

The manual acceptance commands now use **primary-language precedence**, not exact matching
of the entire Android language list. The first language is decisive for Spanish,
Catalan and English: `es-ES,en-US`, `ca-ES,es-ES`, and `en-US,es-ES` are valid
examples. The unsupported-language fallback case requires an explicitly observed
`fr-FR,en-US` prefix in that order; further secondary languages are permitted.
Both the ADB preflight and the independent instrumentation observation still verify
the **complete actual ordered list**, and JUnit is passed that full list; no locale
ordering is inferred or hidden.

A manual run captures the complete **current** language list at its start, verifies it
via ADB and baseline instrumentation, and never requires historical recovery before
functional tests. A new `manual-pilot` requires **no** `--recovery-from` argument.
The legacy automatic pilot, historic runs and their evidence are unchanged.

The terminal requests the target primary language, accepts additional secondary
languages, waits for Enter, and checks ADB before each JUnit stage. After tests, it
asks the owner to restore exactly the entire initial list. Restoration is an
**independently reported** status: a BLOCKED restoration must never alter a genuine
completed functional JUnit PASS. A top-level complete acceptance PASS still requires
verified restoration; `functional_status` and `restoration_status` report separately.
If exact cleanup is not possible, the owner must reconcile the disposable emulator
manually, without rewriting evidence.

From the already synchronized Mac working tree after publishing:

```bash
cd "/Volumes/Intenso/Code/Prs/PartiturasPDF"
git pull --ff-only origin test/mvp-stabilization-j
pilot="build/j-m3-b2b/$(python3 -c 'import uuid; print(uuid.uuid4().hex)')"
python3 scripts/j_m3_b2b_locale_matrix.py manual-pilot --output "$pilot"
python3 scripts/j_m3_b2b_locale_matrix.py report --output "$pilot"
```

Run `manual-run --output "$output"` in a new output directory only after reviewing
API26. The old `run` / `pilot` workflows remain strict and automatic: do not
conflate them with the simplified commands. Live Activity locale transitions,
offline, AAB and physical acceptance are still NOT RUN.

---

# M3-B2-B sequential system-locale automation

Implementation baseline: `1099f0c85c3fe1b0872693df5342fb926af7763c`, 2026-10-21.
Host-only implementation/compilation validation is not an Android locale PASS.
[M3-B2-A](J_M3_B2A_J09_ANDROID_EVIDENCE.md) remains unchanged historical evidence.

## Scope and interfaces

`scripts/j_m3_b2b_locale_matrix.py` extends the existing Phase J tooling: it uses
local_e2e.execute for bounded argv execution, local_test_evidence.parse_android,
write_junit, j_capture and confined atomic report writes. It introduces no independent
JUnit parser, dependency, production hook, CI dispatch or general-purpose device runner.

Commands:

- `inspect`: read-only source/host and dynamically discovered named-emulator identity.
  It does not prove that the Settings language UI can be controlled.
- `run`: build matching APKs, verify/install them, capture original settings, run
  the sequential matrix and attempt exact language restoration in finally cleanup.
- `resume`: validate source/APKs and retained raw/XML/UI hashes; skip validated cells.
  No failed functional assertion is retried automatically. Incomplete/blocked cells
  use new attempt directories; original artifacts remain. Restore a pending device first.
- `restore`: use saved original ordering on the same identified emulator/boot. It
  never guesses originals or treats a replacement device as the previous owner.
- `report`: reparse retained raw JUnit, verify inventory, UI hashes, identities and
  restoration evidence without accessing a device. A JSON status field alone is not PASS.

Only named ARM64 emulators are eligible: API26 Tablet2, API27 Tablet_27_Edited,
API36 Tablet_36. Discover actual serials; historical ports are never adopted blindly.
Physical and foreign devices are ignored. Duplicate target AVD names, wrong API/ABI,
qemu/boot mismatches and changed boot IDs block writes. All three must already be booted;
the tool never provisions, starts, wipes or resets an AVD. Broad owner authorization
exists for these disposable targets, but only the needed operations are implemented.

## Exact matrix and selected methods

Per API, sequentially establish es-ES, ca-ES, en-US, then **[fr-FR,en-US]**.
The app must show es, ca, en, then es respectively. A single French locale cannot
satisfy the requested ordered-secondary case: it remains BLOCKED if the UI cannot
establish the exact list. Twelve cells, three existing methods each: **36 primary
JUnit executions**, not 36 distinct methods. The stage inventories are:

1. One method, while the app is already running:
   `com.abdev.partituraspdf.app.integration.AppLocaleIntegrationTest.productionActivityUsesActualSystemPolicyAndRecreationRetainsHostAndLocalizedControls`.
2. Two methods, after the change is independently observed:
   `com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.libraryReaderSettingsRoundTripPersistsUiChoicesAndRegisteredPosition`
   and `com.abdev.partituraspdf.ExampleInstrumentedTest.useAppContext`.

Additional original-state probe and restoration invocations reuse only method 1,
one of each per device in an uninterrupted successful run: **six auxiliary JUnit
executions**, separate from the 36-cell-method total. Resume/recovery can add auxiliary
restoration attempts; report actual attempts, never overwrite counts or relabel them
as primary matrix results. No new JUnit methods/classes or global inventory count
are introduced. Ordinary runs without jLocaleRun arguments retain original behavior.

The opt-in androidTest helper LocaleMatrixUi drives actual Settings.ACTION_LOCALE_SETTINGS
via existing UI Automator. It uses AOSP resource IDs, native locale/region labels
and drag handles located from actual objects; no fixed coordinates or shell locale
setter is used. Drag destinations come from the first visible language handle.
It promotes requested languages in reverse order and removes only extras to establish
the exact list. These changes are confined to explicitly authorized disposable emulators.
No shell exit code is treated as proof: Resources.getSystem locale ordering and
independent host system_locales observations must both match. Missing or contradictory
ordered-language channels are BLOCKED rather than guessed.

The first Activity method captures its original live state, enters Settings, changes
languages, returns through the existing task, and then checks actual application locale
against an independently requested language (French -> Spanish). It verifies visible
Library/Settings strings from explicitly localized production resources and genuine
Activity recreation. Process/boot replacement cannot certify the existing-app transition.
A cell already in the requested language can establish visible locale acceptance but
reports the change transition NOT APPLICABLE, not a fabricated change.

The registered native journey adds opt-in visible page counter, navigation and PAGE/WIDTH
menu assertions using expected-language resources, with screenshots/hierarchy while
instrumentation is running. Its existing posted-pixel, registration, favorite, preference,
checkpoint, source-integrity and grant-preservation assertions remain. It uses test-owned
capabilities/stores, not a genuine DocumentsUI authorization. Visible contextual-error
acceptance remains explicitly BLOCKED; scoped resource tests are not substituted.

## macOS usage — owner executes locally, not run in cloud

Prerequisites: this exact reviewed source tree, Python3.10+, JDK25, project Gradle
wrapper, SDK37/build-tools37, existing UI Automator dependency, native accelerated
ARM64 API26/27/36 images, unlocked dedicated AVDs and sufficient disk space. Use the
existing configured toolchain; no packages are installed by this implementation.

From the repository root (set SDK path locally, never commit private SDK configuration):

```bash
export ANDROID_HOME="<your-existing-Android-SDK>"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
python3 scripts/j_m3_b2b_locale_matrix.py --adb "$ANDROID_HOME/platform-tools/adb" inspect
```

Review the discovered AVD/API/ABI/boot identities and disposable ownership. Inspection
must list all three expected targets before running. The following writes only those
verified targets; it does not alter connectivity or uninstall packages:

```bash
matrix_run=$(python3 -c 'import uuid; print(uuid.uuid4().hex)')
output="build/j-m3-b2b/$matrix_run"
python3 scripts/j_m3_b2b_locale_matrix.py --adb "$ANDROID_HOME/platform-tools/adb" run --output "$output"
# Retain the printed outcome and shell exit: 0 selected-matrix PASS, 1 FAIL, 2 BLOCKED.
python3 scripts/j_m3_b2b_locale_matrix.py report --output "$output"
```

Do not pipe away the original exit code. On interruption, preserve output and use:

```bash
python3 scripts/j_m3_b2b_locale_matrix.py --adb "$ANDROID_HOME/platform-tools/adb" restore --output "$output"
python3 scripts/j_m3_b2b_locale_matrix.py --adb "$ANDROID_HOME/platform-tools/adb" resume --output "$output"
```

Resume requires identical source digest, branch/revision, APK hashes and device/boot
identity. Do not edit/build concurrently. Interrupted/failed functional tests are not
silently retried. If a device restarted, restoration identity is BLOCKED rather than
automatically adopting the new boot; retain saved originals for owner-assisted recovery.
No application force-stop, ADB server reset or instrumentation retry is performed.
An interrupted instrumentation may still run; competing recovery launches are blocked
when active target instrumentation is reported. No cleanup operation cancels unknown work.

## Evidence schema and retention

Under the unique `build/j-m3-b2b/<run>/` root:

- build.txt: matching app/test APK compilation output.
- install-<uuid>.json: source APK hashes, per-write verified device identity, install
  output, installed-byte hashes, registered instrumentation and failure details.
- matrix.json: schema 1, source capture, host APK hashes, original device settings and
  ordered system locales, per-cell attempt paths/statuses, restoration paths/statuses.
- `<api>-<locale>-<uuid>-locale/` and `...-tests/`: immutable-attempt raw instrumentation,
  JUnit XML, live PNG/XML/JSON UI observations, execution.json with expected inventory,
  exact argv, run token, timestamps/exit, source/device/boot identity, before/after
  network/system configuration, installed APK hashes and retained-file SHA256 inventory.
- probe-/restore- UUID directories: separate auxiliary execution evidence.
- summary.json: revalidated per-cell JUnit/visible/transition dimensions, recorded
  restoration verification, contextual errors BLOCKED and explicit acceptance boundary.

The test-owned guest output is files/j-locale-<token>; only those explicitly listed,
constrained filenames are read using run-as. They are retained for troubleshooting,
not broadly deleted. Host and installed APK hashes are independently computed from
actual bytes read by the coordinator; the output does not retain APK binaries by default.
Retain build APKs separately if later independent binary attestation is required.
Do not reuse historical M3-B2-A hashes for newly compiled test binaries.

PASS requires complete exact JUnit results, live visible observation files, matching
requested system-language order, unchanged recorded network settings, unchanged source/
APKs and successful restoration on every device. Assertion failures are FAIL even
with nonzero subprocess exits when complete evidence exists. Unsupported Settings UI,
missing/truncated evidence, timeout/disconnect/restart or restoration uncertainty is
BLOCKED. Partial logs and completed cases are preserved; a nonzero return is never PASS.
Before/after network settings are endpoints, not continuous connectivity evidence.

Restoration reuses the actual Settings UI to recover the original exact ordering,
then verifies system locales, app-visible language and original Wi-Fi/mobile/airplane
settings. Network settings are never modified; unexpected changes remain BLOCKED and
require owner recovery rather than a speculative automated correction.

## API limitations and execution status

All Android runtime execution of this implementation is **NOT RUN** in Codex.
AOSP add_language/label/dragHandle/removal resource structures and native region
pickers are capability assumptions, not observed API26/27/36 compatibility evidence.
API36 Settings redesigns or different regional list layouts may BLOCK a cell/restoration.
Failure captures the current screen/hierarchy before returning an explicit capability
blocker. Do not claim this implementation has passed on macOS or any emulator from
mocked host tests or APK compilation. Owner-local execution must establish support.

This implementation deliberately does not use undocumented privileged locale setters,
change production locale policy, add resources/dependencies, or introduce an in-app
language selector. No offline M3-B2-C, AAB M3-B2-D, physical-tablet, genuine SAF, controlled
Pdfium, endurance, benchmark or M3-C coverage acceptance is established. J08 remains
DEFERRED TO PHASE R. Historical reports/catalogue statuses remain unchanged.

## API26 failure correction and single-cell pilot (2026-10-21)

The owner archive from execution `bac3c9366558c550ad881336d6ccf9815b098c79`
retains a PASS probe (`probe-06f8c8c0869345ad90d1172b56ac9ff3`), a BLOCKED
Spanish change (`26-es-ES-c75a418f8bd14b039b8eacc9936f1fe6-locale`) and a BLOCKED
restore (`restore-2ad81d18c4244507bb31d17cf07d3cba`). Their execution.json file
hash inventories match the retained payloads. The initial independent configuration
and instrumentation order was [en-US], with the database channel unavailable.
The change and restoration both ended [en-US,es-ES]. No primary cell passed.
These original artifacts and their classifications are not modified.

The change hierarchy identifies two native language rows and drag handles. It proves
that adding Spanish succeeded and promotion did not. UI Automator 2.3.0's drag already
includes a long-click; lack of any long press is **not** an established root cause.
Its default speed is 2500 density-independent pixels/second: this short move is brief.
The candidate fix injects one continuous touch sequence, holding longer than the
platform long-press timeout and moving over about 480ms between actual handle centers.
A cancelled/failed injection releases with ACTION_CANCEL. The final ordered configuration
must still change; injected events alone never constitute PASS. No fixed screen tap
locations, shell locale setters or functional assertion retries are introduced.

The restoration screenshot is dimmed but the associated hierarchy contains neither
the overflow action nor a removal item. It cannot establish exactly which popup was
visible or why the menu lookup failed. The prior implementation performed a single
lookup after waitForIdle, which does not establish menu readiness. The fix waits,
boundedly, for the actual resource-backed remove action, row checkbox, checked state,
remove action and positive confirmation. Unsupported structures still BLOCK. Live
PNG/XML/JSON checkpoints now precede and follow dragging and each removal phase.
They will distinguish gesture failure from menu/confirmation failure in the next run.
These UI corrections are compiled candidates, **not runtime-verified fixes**.

### Recovery before any fresh matrix baseline

Do not run `run` on the contaminated [en-US,es-ES] device. Do not adopt that list
as the new original. Preserve the complete old run directory locally, including its
matrix.json, successful probe, raw output and hashes. The following pilot consumes
that retained original evidence, leaves it unchanged, builds/installs matching current
APKs and first attempts exact [en-US] recovery on the **same API26 AVD/serial/boot**.
It rejects an unverified probe, changed original, changed device identity, modified
probe artifacts or a dirty original execution source. A reboot requires owner-assisted
reconciliation; this command never silently adopts a new boot.

Only the already authorized, disposable Tablet2 API26 ARM64 target is touched. API27/36
need not be running and are not installed or exercised by the pilot. Before execution,
review the read-only `inspect` output and compare the current device/boot with the old
matrix.json. Confirm the target is still owner-authorized and no instrumentation is active.
Cloud execution remains prohibited. On the owner's native Apple Silicon Mac:

```bash
# In the reviewed repository tree, using its configured SDK/JDK25 toolchain:
export PATH="$ANDROID_HOME/platform-tools:$PATH"
python3 scripts/j_m3_b2b_locale_matrix.py --adb "$ANDROID_HOME/platform-tools/adb" inspect
# Replace this placeholder with the ORIGINAL failed run directory, not its summary.json.
previous="build/j-m3-b2b/<original-failed-run-id>"
pilot="build/j-m3-b2b/$(python3 -c 'import uuid; print(uuid.uuid4().hex)')"
python3 scripts/j_m3_b2b_locale_matrix.py --adb "$ANDROID_HOME/platform-tools/adb" \
  pilot --recovery-from "$previous" --output "$pilot"
pilot_exit=$?
printf 'Pilot exit: %s\n' "$pilot_exit"
python3 scripts/j_m3_b2b_locale_matrix.py report --output "$pilot"
```

The pilot runs sequentially: saved-original recovery, fresh original-state probe,
Spanish-primary change with the existing production Activity/recreation method,
existing native registered Reader journey plus separate smoke, then exact final
restoration in finally cleanup. Nominally **three primary and three auxiliary JUnit
executions** (recovery/probe/restoration), not the full 36 primary matrix executions.
Each functional stage is attempted once; a failure stops progression but still attempts
final restoration. Failed pre-pilot recovery prevents the new probe and Spanish cell.
Do not use resume to expand a pilot into a full matrix; pilot ledgers reject resume.

summary.json has profile `api26-spanish-pilot`, separately revalidated `recovery_status`
and per-device `restorations`, as well as the pilot's overall status. A recovery PASS
may coexist with a BLOCKED pilot. Both require raw/JUnit/UI/source/APK/network and
exact locale checks; declared PASS metadata alone is insufficient. Keep all output,
including incomplete logs and recovery directories, for review. Do not start the full
matrix until the pilot's recovery, Spanish transition, visible app assertions, selected
JUnit results and final exact restoration have been reviewed.

API27/36 interaction compatibility remains NOT RUN. French **[fr-FR,en-US]** ordering
remains mandatory in the full matrix; the Spanish-only pilot supplies no French,
Catalan, offline, AAB, genuine SAF, physical-device or contextual-error acceptance.

## Current workflow: interactive manual selection (2026-10-21)

Use **manual-pilot** and **manual-run** for new acceptance work. The automatic commands
and earlier sections above are retained for historical evidence compatibility, not as
an instruction to continue developing or using automated Settings gestures. No old
archive, result, run classification or source attribution is changed by this addition.

The owner changes Android languages through Settings. The coordinator does not open
Settings, drag, click, remove languages, use privileged setters or override app locales
in either manual mode. It prompts in Spanish, records Enter's timestamp, and then
reads the actual effective configuration (`am get-config`) plus `system_locales`.
Enter is **not** evidence. An exact explicit effective ordered list is mandatory;
an absent database key is recorded as unavailable, not fabricated. An explicit database
value must agree; malformed or contradictory evidence blocks. In particular, French
alone never establishes **[fr-FR,en-US]**. The existing instrumentation subsequently
reads Resources.getSystem independently and must agree with the host observations.
No system-language inference or manual app-language selector is introduced.

The app is launched for the already selected system configuration. The existing
production Activity, localized Library/Settings, retained-host recreation, native
Reader journey and smoke methods remain the selected inventory. The new explicit
`jLocaleAction=manual` validates the exact system list, captures live PNG/XML/JSON,
and returns **before** automatic Settings control. Activity recreation remains tested;
**live Activity system-language transition acceptance remains NOT RUN**. Startup in
a selected locale does not prove survival of an existing Activity across a language change.

### macOS commands (after review, on the matching source tree)

Use the owner's existing native Apple Silicon SDK/JDK25 setup and unlocked dedicated
ARM64 AVDs. No tool installs packages in the SDK, starts AVDs, resets data, changes
networking or operates a physical tablet. The runner installs matching debug/test APKs
with `-r` after identity checks. Retain the original failed run directory in this
checkout, unchanged. Do not start with the contaminated [en-US,es-ES] list as a new original.

Read-only discovery first:

```bash
python3 scripts/j_m3_b2b_locale_matrix.py inspect
```

API26-only pilot, using the known **original** recovery record, with no placeholders:

```bash
pilot="build/j-m3-b2b/$(python3 -c 'import uuid; print(uuid.uuid4().hex)')"
python3 scripts/j_m3_b2b_locale_matrix.py manual-pilot \
  --recovery-from build/j-m3-b2b/a76283a483e94fe9be1fd5d19c51a316 \
  --output "$pilot"
pilot_exit=$?
printf 'Pilot exit: %s\n' "$pilot_exit"
python3 scripts/j_m3_b2b_locale_matrix.py report --output "$pilot"
```

This command requires the retained original probe, its hashes, original [en-US]
ordering and the **same AVD/serial/API/ABI/boot identity**. It first asks for exact
manual recovery to [en-US], verifies ADB ordering and independently runs the existing
locale method. Only then does it ask for [es-ES], execute the locale method followed
by native Reader plus smoke, and finally ask for [en-US] restoration and verify it.
Nominally three primary JUnit executions plus two auxiliary executions (recovery and
final restoration). No automatic locale interaction occurs. If boot identity differs,
execution is BLOCKED: retain both identities and obtain explicit owner reconciliation;
never rewrite the historical boot or original list to bypass this check.

After the pilot and exact restoration have been reviewed, the full manual matrix is:

```bash
matrix="build/j-m3-b2b/$(python3 -c 'import uuid; print(uuid.uuid4().hex)')"
python3 scripts/j_m3_b2b_locale_matrix.py manual-run --output "$matrix"
matrix_exit=$?
printf 'Matrix exit: %s\n' "$matrix_exit"
python3 scripts/j_m3_b2b_locale_matrix.py report --output "$matrix"
```

This requires already running Tablet2/API26, Tablet_27_Edited/API27 and Tablet_36/API36.
For each it records the independently verified original order, runs one read-only
baseline locale method, then prompts for es-ES, ca-ES, en-US and [fr-FR,en-US]. Finally
it requests and verifies exact restoration of that device's original order. There are
15 owner selection/restoration steps, 36 primary and six baseline/restoration JUnit
executions in a complete run. Raw attempt counts are authoritative when interrupted.

Example terminal interaction:

```text
Paso 2/3 — Tablet2 API26 (emulator-5554) — Selección de idioma para pruebas
Orden COMPLETO requerido: ['es-ES']. Cambie los idiomas manualmente en Ajustes de Android.
No cambie la conectividad. Enter solicita verificación; no confirma un resultado.
Pulse Enter para verificar o q para cancelar:
VERIFICADO por ADB: orden exacto. La comprobación independiente de instrumentación sigue pendiente.
```

The serial is discovered and verified, not assumed from this example. A wrong language
prints the observed/required lists. The owner may explicitly choose `r`, correct
Settings and press Enter again, for at most three verification attempts. All attempts
are preserved. This never retries a failed functional JUnit assertion. `q`, EOF or
Ctrl-C blocks progression. Finally cleanup offers manual restoration; if cancelled or
interrupted again, the report retains the pending original list and blocker. Restore
that exact saved list manually before further testing. Do not run legacy `restore`
or `resume` against manual ledgers; they reject manual profiles and never fall through
to automatic Settings UI. Keep incomplete evidence; start a fresh run after review.

### Evidence and acceptance boundaries

Manual profiles (`manual-api26-spanish-pilot`, `manual-full-matrix`) are separate from
automatic histories. `manual-selection-*.json` retains raw channels, exact requested
order, identity, source, owner confirmation timestamps, unsuccessful attempts and final
verification. Each successful selection is hash-bound from matrix.json. Every JUnit
stage rechecks source/device/APK/configuration before execution and retains the existing
raw log, exact inventory, XML, live screenshots/hierarchy, hashes and network endpoint
checks. Final report generation reparses retained evidence without device access.

summary.json separates **configuration verification**, **visible localization**, **JUnit**,
**recovery/baseline**, **exact restoration**, and **live_transition: NOT RUN**. A manual
matrix PASS is scoped to the first five dimensions, never a live-transition PASS.
A missing confirmation, cancelled restoration, truncated JUnit result or evidence mismatch
cannot become PASS. Full French secondary ordering remains mandatory. No disconnected
offline journey, AAB delivery, contextual-error screen, external SAF, physical-device,
PDFium diagnostic, performance/endurance or M3-C acceptance is supplied by this workflow.
All new manual Android execution remains **NOT RUN** until the owner actually runs it.
