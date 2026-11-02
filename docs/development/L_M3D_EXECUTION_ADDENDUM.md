# L-M3D owner execution addendum — 2026-11-02

Source: `b79850758e579db0987b3cdac389d818c20c72a7`.
This new dated addendum preserves the original L-M3D NOT RUN report.

The owner supplies the following verified baseline in the L-M4 assignment:

- Windows Android API26 emulator ReaderScreenTest: **52/52 PASS**, zero failures,
  errors or skips.
- [CI #118](https://github.com/alexbonavila/PartiturasPDF/actions/runs/41518537605):
  SUCCESS, all nine jobs PASS: build, lint, unit tests, API26, API27, API36,
  localization, fixtures and quality gate (owner-reported job categories).
- JVM: **651 PASS**; Python: **492 PASS**; canonical fixtures: **15 PASS**.

These are owner-provided execution findings, not CI logs independently downloaded
in this task. The new local L-M4 checks are recorded separately and do not transfer
these exact-revision results to later code. No CI was accessed or dispatched here.

The owner accepts L-M3C, but has **not expressly confirmed manual L-M3D rotation/
chrome-restoration validation**. That check remains PENDING. API26 instrumentation
and compatibility CI do not establish physical Android8.1 tablet, TalkBack,
linguistic/layout review or formal final Phase L acceptance. No manual device is
inferred from the earlier unspecified successful slider-navigation report.

L-M4 is separately authorized for integration/regression. No PR, merge, tag,
release, L-M5 or Phase M authorization follows from these results.
