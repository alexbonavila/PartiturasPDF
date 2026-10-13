# GUI Development Workflow — PartiturasPDF

**Status:** Supplemental development guidance, proposed for adoption by the project owner.  
**Applies to:** Approved Android UI changes implemented with Kotlin, Jetpack Compose and Material 3.  
**Authority:** [`AGENTS.md`](../AGENTS.md), [`TESTING_POLICY.md`](TESTING_POLICY.md), [`DEFINITION_OF_DONE.md`](DEFINITION_OF_DONE.md), and [`CHANGE_CONTROL.md`](CHANGE_CONTROL.md) take precedence. This guide changes no requirement, gate, ownership rule, or approval process.

## 1. Goal and guiding principles

Produce usable, accessible, localized and maintainable interfaces **without spending cycles debugging brittle rendering assertions**. Design and validate observable user behavior first; test internal layout details only when the accepted design contains an actual measurable layout contract.

- Prefer standard Material 3 components and existing theme tokens over speculative abstractions.
- Implement shared components only for approved, named consumers; keep feature state outside presentation-only components.
- Keep changes within the approved branch's file ownership. A new preview or test is not permission to edit shared themes, strings, Gradle files, navigation or another branch's feature code.
- Do not replace functional tests with screenshots or visual judgment. Conversely, a pixel-level assertion does not replace visual validation.
- When a mandatory check fails, stop and investigate rather than reducing its coverage or repeatedly adjusting thresholds.

## 2. Sequence for each UI change

### Stage A — Agree on the contract

Before coding, record:

1. Component or screen purpose and the specific approved consumer(s).
2. Inputs, states, events/callbacks, and ownership of business logic.
3. User-visible copy and accessibility labels; identify who owns English, Catalan and Spanish resources. Shared presentation components may receive already-localized strings from their callers.
4. Supported viewports, orientations, light/dark themes, large text and accessible interaction requirements.
5. Explicit acceptance criteria, out-of-scope work, allowed files and any relevant API 26 constraints.

If the UI cannot be meaningfully reviewed in isolation, agree on a scoped preview or test host **before** implementation. Do not create unused production navigation or fake repositories solely for previewing.

### Stage B — Implement a minimum renderable slice

- Build one component/state or a narrowly scoped screen slice at a time.
- Use Compose layout constraints (`fillMaxWidth`, `widthIn`, `heightIn`, spacing, scrolling) in the appropriate **parent/child ownership**. The parent screen owns viewport navigation and scrolling; reusable components must not silently assume full-screen height.
- Supply callbacks instead of implementing feature-side actions in common UI.
- Use existing theme typography and color schemes; account for accessible foreground/background contrast, touch targets and semantics.
- Compile the app before expanding the test suite. Keep sample preview labels out of runtime behavior.

### Stage C — Review previews and real rendering

For UI work, prepare a small, relevant `@Preview` matrix using existing project theme values:

| Scenario | Minimum check |
| --- | --- |
| Narrow width | Long message and long action label can be read and controls remain reachable |
| Tablet/wide width | Content width, grouping and spacing are sensible; no unnecessary stretching |
| Large text (e.g. 2×) | Text remains readable and actions reachable with real font scaling |
| Light and dark | Correct theme colors, contrast and disabled states |
| English, Catalan and Spanish | Caller-supplied or resource-based text supports expansion and accents |
| Landscape / scrolling | Review when the UI can be constrained vertically or the feature requires it |

Previews are **design evidence, not runtime test results**. The owner reviews and accepts the relevant variants. Then validate critical variants on an emulator or real device (including scrolling, tap targets and accessibility/TalkBack where applicable). Record any defect before modifying tests to accommodate it.

### Stage D — Write behavior-first automated tests

Prioritize tests that have a clear user-observable failure:

- Expected text and accessibility semantics exist and match the supplied strings.
- Optional controls are present/absent as required, respect enabled/disabled states, and invoke the supplied callback exactly as specified.
- Recomposition does not trigger actions or lose current callback behavior.
- Long text, narrow layouts, scrolling and large font scale preserve readable content and reachable actions.
- Relevant light/dark colors and contrast meet the existing project criteria.
- Invalid input contracts are tested when they are part of the approved API.

**Do not use `TextLayoutResult.hasVisualOverflow` or exact glyph/line pixel positions as the sole proof of a visible defect.** Compose paragraph metrics may reflect internal rounding or measurement behavior. If measuring text geometry is genuinely required, establish an explicit visual/layout requirement, inspect actual rendered evidence, and document the tolerance and device configuration. Never increase tolerances merely to make a test pass.

Tests simulating a tablet must not alter `LocalDensity.density` to squeeze an arbitrary logical width onto a different physical viewport. Use an appropriate real emulator, available width constraints or a dedicated responsive host. When testing font scaling, set `fontScale` intentionally, independently from viewport density, and ensure the test actually exercises the specified scale. Do not rename or remove a test while silently losing its coverage.

Screenshots/golden tests are an optional additional tool for stable, justified visual regressions—not a default requirement for every simple component.

### Stage E — Triage the first failure before changing code

Classify the failure:

1. **Build/test infrastructure:** compiler errors, stale APKs, missing SDK/JDK, ADB offline, unstable emulator, package installation or test runner errors.
2. **Test defect:** wrong fixture, artificial sizing, incorrect semantics lookup, brittle pixel metric or assertion that does not represent the agreed requirement.
3. **Product defect:** reproducible visible clipping, inaccessible or unreachable control, wrong state/action, incorrect localization or another approved requirement not met.
4. **Uncertain:** insufficient evidence to distinguish the preceding cases.

Capture the failing test, exact error, device/API, code revision and screenshot/layout evidence when relevant. For infrastructure issues, repair the environment **without modifying production assertions**. For a test defect, replace the flawed assertion with meaningful coverage of the same requirement; do not simply delete it. For a product defect, stop and discuss a small targeted fix versus redesign with the owner before changing behavior. For an uncertain result, gather one focused diagnostic rather than running a chain of speculative changes.

After the correction, rerun the failed test, the component suite and the broader required regression suite. A success reported by Gradle is not evidence that instrumentation ran: inspect the number of executed tests and their XML/HTML results.

## 3. Final validation and handoff

Follow the existing [`TESTING_POLICY.md`](TESTING_POLICY.md) without exceptions:

- Build and Android Lint.
- JVM tests and applicable scoped coverage checks.
- Full instrumented suite **executed on API 26**; record the number of tests run, failures and skips.
- Localization validation for English/Catalan/Spanish.
- Authoritative immutable PDF fixture verification.
- Required CI checks at the latest commit; API 27/recent-API and physical Android 8.1 tablet acceptance when due under project policy.
- Manual review of relevant themes, orientations, text scale, accessibility and previews on the final revision.

Review the diff for assigned-file scope, no accidental binaries, no hardcoded runtime UI strings, no dependency/configuration changes without approval, and no unrelated edits. Record **PASS / FAIL / BLOCKED / NOT RUN** truthfully. Only the owner authorizes integration; a passing build is not authorization to merge.

### Evidence template

| Field | Value |
| --- | --- |
| Branch and commit | `<branch>` / `<SHA>` |
| Approved UI contract | `<requirement IDs, consumers and states>` |
| Preview scenarios reviewed | `<variants and screenshots/location>` |
| Manual device check | `<device / API / orientation / theme / font scale / result>` |
| Compose/component tests | `<command, count, failures, result>` |
| Complete API 26 instrumentation | `<command, device, count, failures, result>` |
| Build, Lint, JVM, localization, PDF fixtures | `<individual observed outcomes>` |
| Open risks or blockers | `<items, or none>` |
| Owner review / merge | `<pending or explicitly authorized>` |

## 4. Instructions to include in future Codex UI tasks

> Read `AGENTS.md`, `docs/TESTING_POLICY.md`, `docs/DEFINITION_OF_DONE.md`, the task's branch ownership plan, and `docs/GUI_DEVELOPMENT_WORKFLOW.md` before editing. Implement the smallest approved UI slice, create relevant Compose previews for human visual review, and stop for feedback before writing or rewriting extensive layout tests. Test observable user behavior; avoid brittle text-metric assertions unless the layout contract explicitly requires them. Preserve the existing gates and keep all changes inside the task's approved file allowlist. Report what actually compiled, rendered, and executed; do not claim unrun tests passed.

This section is a prompt template, not a change to `AGENTS.md`. Existing coding agents will need to be directed to read this document until a separately approved policy/process change adds a permanent reference.

## 5. Lessons learned from FeedbackPanel

The `FeedbackPanel` experience showed why visual review and behavior-driven tests must be separated: early measurements of Compose text bounds produced failures without confirming visible clipping; artificial density changes confused responsive-size tests; stale APK execution and emulator disconnections added unrelated noise. Preview inspection, an API 26 emulator, and a revised behavior-preserving test finally supplied usable evidence. The preventive action is **a better test design and staged workflow**, not an automatic redesign of every reusable component.
