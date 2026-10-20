# Changelog

Only verified implementation and milestone changes are listed. Detailed evidence is in [MVP release record](docs/releases/MVP_RELEASE.md) and [Phase I acceptance](docs/acceptance/I_APP_MVP_ACCEPTANCE.md).

## Unreleased — documentation closeout

- Reconcile Phase I merge status, CI/API test counts, read-only MVP scope and owner-reported manual acceptance.
- Propose `v1.0.0-mvp` as an annotated tag **after** documentation PR merge. Neither the tag nor a GitHub Release exists yet.

## Functional MVP code baseline — 2026-10-20 (not tagged)

- [PR #24](https://github.com/alexbonavila/PartiturasPDF/pull/24) delivered the production Library, isolated PDF Reader, Settings, SAF picker, locale handling and lifecycle/recovery integration.
- D04-A/B/C/D fixed Reader session and Retry recovery regressions.
- [CI #99](https://github.com/alexbonavila/PartiturasPDF/actions/runs/39424923611) verified 407 ordinary Android tests on API 26/27/36; [CI #101](https://github.com/alexbonavila/PartiturasPDF/actions/runs/39456085172) passed all mandatory post-merge checks.
- The owner reported satisfactory manual use on a tablet and emulator. Editing, annotations and Drive remain outside this MVP.
