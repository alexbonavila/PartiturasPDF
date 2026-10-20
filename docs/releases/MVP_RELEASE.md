# PartiturasPDF — functional MVP milestone

**State:** Documentation closeout draft on branch `docs/mvp-release-closeout`, dated 2026-10-20. A version tag and GitHub Release have **not** been created. This record does not claim an installable public release.

## Provenance and version

| Item | Verified value |
|---|---|
| Integrated pull request | [#24](https://github.com/alexbonavila/PartiturasPDF/pull/24), merged 2026-10-20 |
| `main` integration SHA | `f06197a75a2e420fb87153c0e64e3fe2a28841ea` |
| Feature HEAD before squash | `aebddfd8adf0bab2954af9918140d668269d0a1f` |
| Content comparison | Identical Git tree on both commits |
| Proposed annotated milestone tag | `v1.0.0-mvp`, **not created yet** |
| Actual Android version at code closeout | `versionCode=1`, `versionName=1.0` (unchanged) |

**Tag procedure:** After the documentation PR passes the existing quality gates and the owner merges it, create the annotated tag on the exact resulting `main` commit. Record the new SHA and tag in GitHub Release notes. No code, Gradle version, signing configuration or CI workflow is changed in this documentation branch.

## Scope delivered

- **Library:** Android SAF root picker and rooted navigation, registration and favorites, registered and temporary opening.
- **Reader:** isolated native PDF worker, one-page rendering, previous/next navigation, PAGE/WIDTH fit, visual zoom, session lifecycle and confirmed registered-page checkpoint handling.
- **Settings and language:** SYSTEM/LIGHT/DARK, default fit, Spanish/Catalan/English resource bundles with Spanish fallback, no manual locale selector; AAB language splits disabled.
- **Lifecycle/recovery:** retained host, controlled permission/picker callbacks, memory/background/Back cleanup, eligible registered restoration and typed failures.
- **Minimum platform:** Android 8.0 / API 26.

## Automated evidence

| Execution | Verified result |
|---|---|
| [CI #99 — D04-D](https://github.com/alexbonavila/PartiturasPDF/actions/runs/39424923611) | 9/9 jobs PASS; JVM 630 PASS; 407/407 ordinary Android tests on each API 26/27/36; lint, localization, 15 PDF fixtures and quality gate PASS |
| CI #99 PDFium controlled runs | `DIAGNOSTICS_VALIDATED` per existing policy; known raw platform defects are not relabeled success |
| [CI #101 — post-merge `main`](https://github.com/alexbonavila/PartiturasPDF/actions/runs/39456085172) | All seven mandatory jobs PASS on merge SHA `f06197a`; optional API27/36 jobs SKIPPED by push configuration |

## Owner assessment and manual evidence boundary

On 2026-10-20 the owner reported having tested the MVP on a physical tablet and in an emulator and liking the functional outcome. This constitutes a **general owner-reported functional acceptance**, not a detailed recorded execution of all manual checks in [I-T01–I-T17](../acceptance/I_APP_MVP_ACCEPTANCE.md). No model, physical Android release/API, captured screenshots, step-by-step results or evidence hashes have been provided.

Do not invent observed results. If later available, record tested device identifiers, verified app revision, actual DocumentsUI READ authorization, favorite/page restart, rotation, simulated versus genuine memory pressure, process death, actual offline system-language changes, portrait/landscape, font sizes, TalkBack and external provider failure/recovery separately. The original [20-action manual procedure](../acceptance/I_APP_MVP_ACCEPTANCE.md#physical-android81manual-reusable-script) remains the recommended source for such evidence.

## Exclusions and known limitations

- No PDF editing, rotate/reorder/merge/extract, annotation tools, Drive exchange, global search, file mutation or page prefetch in this MVP.
- Known PDFium platform defects remain under the existing controlled-diagnostic classifications. The green CI does not mean every raw diagnostic succeeded.
- No full physical performance/battery/memory characterization, complete manual accessibility/linguistic certification, authenticated provider matrix or offline system-language-switch acceptance is recorded.
- Real backup/restore and device-to-device transfer remain deferred in [Phase C](../PHASE_C_DEFERRED_ACCEPTANCE.md).
- Before public binary distribution, confirm license suitability (root CC BY-NC-ND 4.0), third-party notices, signing, security and release packaging separately.

**Milestone meaning:** The application is integrated, automatically verified and considered functionally satisfactory by its owner. This does not assert that all detailed Phase I manual evidence/DoD requirements have been formally signed off or authorize the next phase.

## Finalization checklist

- [x] Phase I squash-merged without loss of content
- [x] Feature branch CI nine checks PASS (including API 26/27/36)
- [x] Merged `main` CI seven mandatory checks PASS
- [x] Owner reported satisfactory tablet and emulator usage
- [ ] Itemized physical and accessibility/language acceptance evidence, if required for formal Phase I DoD
- [ ] Documentation-only PR reviewed, CI verified and merged
- [ ] Annotated tag created on the final merged SHA
- [ ] Optional GitHub pre-release notes reviewed/published
