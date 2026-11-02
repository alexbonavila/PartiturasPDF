# L-M3C-R3 — controlled IME policy experiment

**EXPERIMENTAL — API26 VALIDATION PENDING**, 2026-11-02.
Branch: `feat/reader-advanced-l`; base:
`49cf9f262e68ec86853380844a3a8ef4c0ff4ef9`.
Implementation: `127ca0540213424c354e95e3d039f05c33cc6c7d`.
This is a testable candidate, not a verified fix or phase acceptance.

## Rationale and exact implementation

The owner-provided R1 trace establishes IME-related Reader height changes,
additional renders/frames and acknowledgments, with no duplicate guarded actions,
extra opening or concurrent leases. Raw owner traces were not independently
replayed here. Historical 45/47 PASS and two failures remain unchanged.

The exact installed Compose UI 1.10.4 AndroidDialog source/bytecode inspected in R2
shows updateParameters unconditionally sets softInputMode. The API26 value with
decorFitsSystemWindows=false is ADJUST_RESIZE, not ADJUST_NOTHING.

ReaderDialogImePolicy is a small Reader-private owner of only the Compose Dialog
window. ReaderPageJumpDialog obtains it through LocalView.parent implementing the
public DialogWindowProvider inside the dialog's separate text composition. It
never obtains or mutates the Activity Window. DisposableEffect installs the owner
before the keyed LaunchedEffect requests the input focus.

The candidate chooses **ADJUST_PAN** before ADJUST_NOTHING: Android can pan the
focused dialog editor into view rather than resizing the underlying content or
leaving the input obscured. This is a platform-policy rationale, not proof that
all buttons remain visible under every API26 keyboard/font configuration.

The owner wraps the existing public Window.Callback using Kotlin delegation,
overriding only onWindowAttributesChanged. It replaces only SOFT_INPUT_MASK_ADJUST
bits, preserving the current requested state bits and unrelated attributes.
When Compose writes UNSPECIFIED/RESIZE again, the callback synchronously reapplies
PAN. The nested notification delegates the effective attributes to the original
callback once; an explicit reentry guard prevents recursive enforcement. All
other input, focus, Back, accessibility and window callbacks delegate unchanged.
No private framework introspection, reflection or global listener is used.

Disposal closes the owner idempotently. If still the installed callback, it restores
the previous callback and original adjustment bits while preserving other current
soft-input bits. If another owner has taken over, it does not overwrite that
owner. References are scoped to the Dialog composition, with no global retention.
No activity-wide setting or window flag is changed.

ReaderScreen, viewport measurement and controller dispatch remain unchanged.
Physical dimension changes continue to reach onSizeChanged and render normally.
Frame, presenter, native surface, cancellation and checkpoint ownership are
untouched. No acknowledgments are deduplicated and no viewport height is frozen.
No host, manifest, Gradle, CI, resources, storage, library or localization script
changed. R1 diagnostics remain available.

## Tests and limitations

Both original failing methods retain their strict frame, viewport/generation,
acknowledgment, action, fit/zoom and lease assertions. The first/last jump test now
also requires exactly three render requests, one opening and maximum one lease.
Every dialog opening and input edit checks the effective attached dialog window
adjustment bits with Espresso's dialog-root layout parameters; this exercises
policy retention across editing and validation recompositions when run.

Two new Android methods in ReaderScreenTest:

- `pageJumpPolicyDoesNotFreezeGenuineContentResize`: change real Reader parent
  dimensions with the modal open, require a new viewport/generation/render while
  retaining one opening/lease, policy PAN and reachable input/Cancel.
- `dialogPolicyReappliesAdjustmentAndRestoresOnlyItsOwnedBits`: use an Android
  Dialog Window to check initial PAN, a later framework-like UNSPECIFIED update,
  unrelated state-bit preservation, original callback/adjustment restoration and
  idempotent close. This method alone is not a Compose/IME journey.

The class now has 49 methods. All instrumentation compiles, but **none executed
here**. Existing large-font/dark, keyboard/IME Done, Back/Escape, cancellation,
requested/presented and genuine-resize tests remain enabled. PAN can move dialog
content; landscape/large-font button reachability, physical focus restoration,
TalkBack and actual keyboard visibility still require owner runtime evaluation.
If any of those regress, reject the experiment rather than relax assertions.
Four separately reported CI-only visibility failures remain outside this scope.

## Available validation and source provenance

Linux x86_64, existing JDK25/SDK37/Gradle9.8/AGP9.5 activation:

```bash
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest \
  :app:lintDebug :app:testDebugUnitTest --console=plain --max-workers=4
bash ./gradlew :app:assembleDebugAndroidTest :app:lintDebug \
  --console=plain --max-workers=4
python3 -B -m unittest discover -s scripts/tests -v
python3 scripts/validate_localization.py
python3 test-fixtures/scripts/verify_fixtures.py
git diff --check
```

- Debug build, full instrumentation compilation, Lint: PASS.
- Full JVM: **653 PASS**, zero failures/errors/skips; contract coverage gate PASS.
- Python: **492 PASS**, zero failures/skips.
- Localization: PASS; canonical fixtures: **15 PASS** with structure/render checks.
- Scope/whitespace: PASS.
- Final extra three-render assertion was followed by successful instrumentation
  compilation/Lint; no production/JVM changes followed the full run.
- New Android policy-owner branch/line coverage: **NOT MEASURED / BLOCKED** by
  runtime availability. Existing JVM/contract coverage is not policy-owner or UI
  coverage and does not waive the applicable 90%/80% requirement.
- Android runtime: **NOT RUN**, no authorized runnable emulator and no /dev/kvm.
  Neither original runtime regression is claimed fixed. No CI accessed.

Logs: /tmp/l-m3c-r3-gradle.log, /tmp/l-m3c-r3-final-compile.log,
/tmp/l-m3c-r3-python.log, /tmp/l-m3c-r3-localization.log,
/tmp/l-m3c-r3-fixtures.log; JVM XML under app/build/test-results/testDebugUnitTest.
These are local generated evidence, not Git inputs. Committing documentation does
not constitute another test run.

## Windows 11 API26 owner validation

Use the already owner-authorized emulator only. Fetch this branch normally and
verify the final published SHA and clean checkout. Do not reset emulator data or
uninstall packages. The Gradle connected-test task builds/installs app and test
APKs; it requires the owner's authorization for that emulator. Stop if another
device might be selected. Replace the serial below with its verified identity.
PowerShell, from the repository root with the existing Android toolchain:

```powershell
$env:ANDROID_SERIAL = 'emulator-5554' # replace with the owner-approved serial
adb devices -l
adb -s $env:ANDROID_SERIAL shell getprop ro.build.version.sdk
adb -s $env:ANDROID_SERIAL emu avd name
# Verify API 26, the exact owned AVD and authorization before proceeding.
$out = "build\r3-owner-" + [guid]::NewGuid().ToString('N')
New-Item -ItemType Directory -Path $out | Out-Null
$log = Start-Process adb -ArgumentList @('-s',$env:ANDROID_SERIAL,'logcat','-v','threadtime','ReaderRuntimeSequence:I','*:S') -RedirectStandardOutput "$out\sequence.log" -RedirectStandardError "$out\logcat-errors.log" -PassThru -NoNewWindow
try {
  .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.abdev.partituraspdf.reader.ReaderScreenTest#pageJumpCancelBackAndEscapeKeepViewportFrameZoomAndToolbar,com.abdev.partituraspdf.reader.ReaderScreenTest#pageJumpFirstLastAndTrimmedInputDispatchExactlyOnce" --console=plain 2>&1 | Tee-Object "$out\focused.log"
  if ($LASTEXITCODE -ne 0) { throw 'Focused Android tests failed; preserve evidence and stop.' }
  Copy-Item app\build\outputs\androidTest-results\connected "$out\focused-results" -Recurse
  .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.abdev.partituraspdf.reader.ReaderScreenTest" --console=plain 2>&1 | Tee-Object "$out\class.log"
  if ($LASTEXITCODE -ne 0) { throw 'Full Reader class failed; preserve evidence and stop.' }
  Copy-Item app\build\outputs\androidTest-results\connected "$out\class-results" -Recurse
} finally {
  Stop-Process -Id $log.Id -ErrorAction SilentlyContinue # host Logcat client only
}
```

Retain source SHA, clean-tree record, built/installed APK hashes, exact device/API,
original JUnit XML, output and sequence log. Expect 2/2 then 49/49 completed tests,
zero failures/skips, effective PAN after edits, unchanged viewport/frame for modal
dismissal, and [0,7,0] with only three renders for the jump journey. Never call a
missing/truncated trace or skipped result PASS. Do not infer installed binary
identity from a host hash alone. Package the reports for independent review;
exclude private logs/document details from Git.

API27/recent compatibility, physical tablet, linguistic/accessibility acceptance,
L-M3D and final owner approval remain pending. No PR, merge, rebase, tag or release.
