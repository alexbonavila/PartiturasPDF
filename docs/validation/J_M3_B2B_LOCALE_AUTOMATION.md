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
