#!/usr/bin/env bash
# Mandatory acceptance plus controlled native diagnostics on the CI-owned emulator.
set -euo pipefail
api=${1:?Expected Android API is required}
case "$api" in 26|27|36) ;; *) echo 'Unsupported mandatory API' >&2; exit 2 ;; esac
if [[ -z "${ANDROID_SERIAL:-}" ]]; then
  [[ "${GITHUB_ACTIONS:-}" == "true" ]] || { echo 'Explicit disposable emulator serial required' >&2; exit 2; }
  mapfile -t emulators < <(adb devices | awk '$1 ~ /^emulator-[0-9]+$/ && $2 == "device" {print $1}')
  (( ${#emulators[@]} == 1 )) || { echo 'Exactly one CI-owned emulator required' >&2; exit 2; }
  export ANDROID_SERIAL="${emulators[0]}"
fi
[[ "$ANDROID_SERIAL" =~ ^emulator-[0-9]+$ ]] || { echo 'Disposable emulator required' >&2; exit 2; }
test "$(adb -s "$ANDROID_SERIAL" shell getprop ro.kernel.qemu | tr -d '\r')" = "1"
test "$(adb -s "$ANDROID_SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')" = "$api"
mkdir -p build/pdfium-ci
standard_started=$(( $(date +%s) * 1000 ))
standard=0
bash ./gradlew :app:connectedDebugAndroidTest --stacktrace \
  -Pandroid.testInstrumentationRunnerArguments.notAnnotation=com.abdev.partituraspdf.pdf.android.FreshPdfiumProcess \
  > build/pdfium-ci/standard-gradle.txt 2>&1 || standard=$?
cat build/pdfium-ci/standard-gradle.txt
# Gradle may uninstall instrumentation after connectedDebugAndroidTest.
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$ANDROID_SERIAL" shell pm list instrumentation
diagnostics=0
python3 app/src/androidTest/java/com/abdev/partituraspdf/reader/run_pdfium_process_experiments.py \
  --serial "$ANDROID_SERIAL" --confirm "$ANDROID_SERIAL" --expected-api "$api" --ci \
  --standard-results app/build/outputs/androidTest-results/connected --standard-started-ms "$standard_started" \
  > build/pdfium-ci/diagnostics.txt 2>&1 || diagnostics=$?
cat build/pdfium-ci/diagnostics.txt
# Even when standard acceptance fails, execute diagnostics and retain their real results.
if ((standard != 0 || diagnostics != 0)); then exit 1; fi
python3 scripts/verify_test_results.py app/build/outputs/androidTest-results/connected \
  --required-test com.abdev.partituraspdf.ExampleInstrumentedTest.useAppContext
