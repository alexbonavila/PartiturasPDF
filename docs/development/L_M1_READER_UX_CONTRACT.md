# PartiturasPDF — Phase L / L-M1 Reader UX contract

> **Owner-approved supersession — L-M3C-R4 (2026-11-02):** Numeric dialog/input,
> IME and modal-specific page-jump requirements below are historical. Direct
> navigation now uses a bottom chrome page seek slider with local drag preview
> and one session-guarded commit on completion. No dialog or keyboard editor is
> part of this UX. All graphical acknowledgment, ownership, navigation safety,
> localization and lifecycle boundaries remain applicable. See
> [R4 evidence](L_M3C_R4_PAGE_SEEK_SLIDER_EVIDENCE.md). R1/R2/R3 results are preserved;
> the experimental IME window-policy approach is intentionally abandoned.


**Status:** Owner-approved UX decisions; design-only baseline, no production implementation authorized by this document\
**Date:** 2026-11-02\
**Repository:** https://github.com/alexbonavila/PartiturasPDF\
**Target branch:** `feat/reader-advanced-l`\
**Verified base commit:** `7f00a793a06e988859dbefc3e21814c21ec727dd`\
**Scope:** Reader-only behavior in L-M2 to L-M4; L-M1 records decisions and acceptance contracts.

## 1. Purpose and approval record

The owner expressly approved:

1. Touch zoning: left **25%** = previous page, central **50%** = show/hide controls, right **25%** = next page.
2. Reader chrome is **visible when the Reader opens**; central touch toggles it manually. **No timed auto-hide**.
3. Previous/next and direct page jump, with validation of user-facing 1-based page numbers.
4. Minimal overlay-style Reader controls, PAGE/WIDTH fitting and existing bounded visual zoom; preserve reader and native resource ownership.
5. No implementation before documenting this L-M1 contract.

This contract resolves the detailed edge cases necessary to test the approved design. It does **not** approve a parallel PDF engine, native resolution change, prefetch/cache, writing, annotations or any host/shared API modification. The owner remains responsible for approving implementation milestones and merges.

## 2. Verified baseline and implementation boundaries

Inspected at the base commit:

- `reader/ReaderScreen.kt`: fixed, scrollable upper chrome allocation (`minOf(128.dp, maxHeight * .3f)`), page indicator, Back/Previous/Next/Options, PAGE/WIDTH, zoom controls, keyboard semantics and a single pointer detector.
- `reader/ReaderInteraction.kt`: current edge bands **15%** or at least 48dp, visual `ReaderTransform` at **1×–4×**, clamped pan and `ReaderGestureGate`.
- `reader/ReaderUiState.kt`: `ReaderAction.Step(-1/+1)`, `ReaderAction.Page(index)`, phases, presented versus requested page, session page count.
- `reader/ReaderController.kt`: single route-local worker, bounded page requests, stale work supersession, visibility acknowledgment/checkpoint separation.
- `reader/ReaderPageSurface.kt` and `ReaderFrame.kt`: Android `SurfaceView` drawing with explicit graphical detach and one retained frame lease. Surface visibility and overlay ordering need **real API 26 verification**.
- `app/host/ReaderHostLifetime.kt`: host exclusively owns route lifecycle, intent sequencing, resume, Back/leave and checkpoint flush.
- `reader/ReaderInteractionTest.kt`, `reader/ReaderScreenTest.kt`: existing geometry, edge, gesture, keyboard, recovery and lifetime tests; many currently assume always-visible chrome.

No code change is required to record L-M1. Existing tests are **historical code evidence**, not new execution results.

## 3. Screen structure and chrome contract

### 3.1 Chrome presentation

- On opening a **new document intent**, `chromeVisible = true`.
- A valid single tap in the central 50% of the **reader content viewport** toggles visibility; no timer, timeout, animation completion, page transition or zoom event may auto-hide it.
- A page step, direct jump, fit selection or zoom control must not change the user's chrome choice.
- Chrome is a compact overlay on the page viewport, aligned with the safe screen insets. **Showing/hiding chrome shall not alter the ReaderViewport dimensions or cause a native re-render solely for the chrome change.** Surface redraw/recomposition is permitted if it does not allocate another page lease.
- Overlay controls must own their hit targets, so touching Back, page count, Options, etc. cannot leak through to page-edge navigation.
- Visible chrome includes Back, the presented page counter (an entry point to direct jump), fit/zoom Options and discoverable navigation affordances. Use existing Previous/Next semantics and keep controls reachable with keyboard/accessibility. Exact layout density may adapt to portrait/landscape and large fonts without shrinking real controls below 48dp.
- Opening a menu/dialog keeps chrome visible until it closes; the modal intercepts pointer and keyboard events. Dismissing a modal does not navigate PDF pages.
- Recovery must remain reachable when chrome is hidden. When a blocking Reader error occurs, expose a visible error/retry surface (which may force chrome visible); **never** hide recovery behind an unavailable central gesture. No timed auto-hide after recovery.

### 3.2 State lifetime

- Chrome visibility is presentation-only state, not PDF metadata or a document setting.
- Preserve it across ordinary recompositions, page changes, fit changes, and within the **same active Reader intent** across compatible configuration recreation, without changing `app/host`.
- A different document intent or a fresh Reader route starts with chrome visible. After process death, restarting Reader may legitimately start chrome visible; do not modify durable document position handling to save this UI detail.
- On rotation, restore the last **actually presented** page according to existing host/controller behavior; never claim a requested-only page was shown. If the current host cannot retain chrome state on a specific recreation path without a shared change, document that as an integration request rather than editing host files.

### 3.3 Overlay technology gate

Compose chrome must render and accept taps **above the existing software `SurfaceView` on API 26** and later. Verify its Z-order, window insets, touch handling, dialog layering, focus and disappearance/reappearance using instrumentation and a real surface. Do not replace `ReaderPageSurface`, switch drawing engines, add native worker processes, or change bitmap ownership to work around a failed overlay experiment without separate owner approval. If a safe overlay implementation is not demonstrable, stop at an evidence-backed scoped integration proposal.

## 4. Exact page-hit geometry

For a viewport width `W > 0`, and finite pointer coordinate `x` satisfying `0 <= x < W`:

| Zone | Exact interval | Result |
| --- | --- | --- |
| Previous | `0 <= x < 0.25 * W` | `ReaderAction.Step(-1)` |
| Controls | `0.25 * W <= x < 0.75 * W` | Toggle `chromeVisible` |
| Next | `0.75 * W <= x < W` | `ReaderAction.Step(+1)` |

Reject invalid widths and non-finite, negative or out-of-viewport coordinates. The old 15%/48dp minimum zone logic must not override this **exact proportional** mapping; 48dp remains the minimum *control touch target*, not a forced enlargement of the navigation zone. The test suite must explicitly cover the 25% and 75% boundaries and widths where rounding matters.

- At the first/last page, `Step` is bounded by the existing controller. No wraparound and no new error for a valid edge tap at an endpoint.
- Compute zones against the actual page-content viewport, not the bitmap width, toolbar width or entire raw screen (which includes system insets).
- One valid tap generates **at most one** action; no duplicate action from overlapping detectors or Compose recompositions.

## 5. Pointer arbitration and keyboard behavior

Priority: modal/menu/real chrome controls > an active multi-pointer/consumed/drag gesture > eligible single-tap zone. Keep one owning gesture detector for the page surface.

- Only a single-pointer, unconsumed press/release within touch slop qualifies for page-zone tap.
- Pan, pinch, multi-pointer contact (even if followed by one remaining finger), consumed events, cancellation, out-of-bounds motion and other transformed gestures cannot produce a final page step or toggle.
- The zoom/pan gesture is allowed when chrome is hidden; a completed pan may not trigger central toggle.
- No dedicated **double-tap-to-zoom** command is added in L-M1. Consecutive independently qualified taps may each produce their single-tap action; the implementation must not generate any *extra* page step from a double-tap recognizer. If a separate double-tap command is proposed, submit a new design approval request with latency/priority tests.
- Existing keyboard Left/Right -> bounded previous/next, Up/Down -> pan, +/- -> zoom and 0 -> zoom reset remain; keyboard shortcuts apply only when page surface has focus and a dialog/menu/input is not active.
- Add an accessible non-touch way to show/hide chrome (a named reader surface semantics action and suitable focused keyboard action, without stealing Enter/Space from buttons or text input). Keep System Back semantics and dialog dismissal separate.

## 6. Direct page jump contract

### 6.1 Entry and dialog

- The **presented-page counter** is the primary trigger: e.g., `Page 12 of 86`. It is not displayed as a confirmed page until `presentedPage` matches the actual presented frame; a pending requested page must never be mislabeled as visible.
- While opening/loading/without an acknowledged page, show the existing localized unpresented/waiting state. Do not advertise a successful page or a functional jump until the session and active state support it.
- Dialog contains localized title, numeric page input, known page total, Cancel and Go-to-page buttons. Input starts at the last presented page when available.
- The dialog is responsive to software keyboard, large text, landscape and accessibility. No thumbnails or native preview renderer.

### 6.2 Validation and dispatch

- User pages are **1 through N inclusive** (N = known current session page count), internal indices are **0 through N-1**.
- Accept only a trimmed, nonempty base-10 integer that fits an `Int`; reject signs with negative values, decimals, nondigits, numeric overflow, zero and greater than N without dispatching `ReaderAction.Page` or writing a checkpoint.
- Keep validation feedback local to the dialog, with an actionable localized explanation. Disable/ignore confirmation for invalid input; no native open/retry.
- Valid confirmation dispatches **exactly one** `ReaderAction.Page(page - 1)` to the existing controller and closes the dialog. It must not open a new PDF session.
- Revalidate against the **latest active session and page count** at confirmation. If the Reader intent changed, or the Reader is quiesced/retired, dismiss or invalidate the request rather than acting on a stale session.
- Jump to the currently requested/presented page is a bounded no-op; no duplicate checkpoint.
- Upon request, display pending/render status according to existing state. Update the visible counter and persistent last page only following actual frame acknowledgment. Cancelled/superseded requests have no successful presentation claim.

### 6.3 Dialog Back and focus

System Back / Escape while the dialog is open dismisses it; it must **not** leave the Reader. Cancel does not change page or zoom. Restore focus sensibly to the page-counter control if it still exists. Chrome remains visible after dialog dismissal.

## 7. Fit, zoom, recovery and lifecycle invariants

- Preserve PAGE/WIDTH fit mapping and the existing `ReaderTransform` 1–4× **visual** zoom; do not promise higher-resolution native pixels. Zoom reset returns to 1× and zero pan.
- Keep transform/pan clamping for mixed aspect ratios (A3/A5, portrait, landscape, rotated pages), and viewport resize; native render limits and one page lease remain unchanged.
- Preserve preexisting zoom-transient behavior across page steps; pan may reset when page changes. Do not save zoom to Room/DataStore.
- Preserve typed errors (`OPEN`, `RENDER`, `PRESENTATION`), retry paths and localized recovery; never expose provider URIs, passwords or stack traces.
- Preserve the exact `ReaderAction.Presented` graphical acknowledgment path. `ReaderAction.Page` and `Step` do not certify a PDF revision or a displayed page.
- Keep quiesce/retire/Back, lifecycle trim/resume, native session disposal and graphical frame detach sequencing unchanged. No new bitmap cache, prefetch, tile renderer, SAF mutation or PDF editing.

## 8. Resource/localization and accessibility contract

All user-visible copy and semantics must use **matching keys** in:

- `app/src/main/res/values/reader.xml` (Spanish default)
- `app/src/main/res/values-es/reader.xml`
- `app/src/main/res/values-ca/reader.xml`
- `app/src/main/res/values-en/reader.xml`

Reuse existing `reader_page_counter`, `reader_previous`, `reader_next`, `reader_retry`, `reader_fit_page`, `reader_fit_width`, `reader_zoom_*`, `reader_page_unpresented` where possible. Proposed phase-local additional resource names (refine by diff before coding):

- `reader_controls_show`, `reader_controls_hide`
- `reader_jump_title`, `reader_jump_page_input`, `reader_jump_total`
- `reader_jump_confirm`, `reader_jump_cancel`
- `reader_jump_invalid_number`, `reader_jump_out_of_range`

Keep argument types and plural handling aligned across all language bundles. No hardcoded Kotlin text. Chrome controls and jump dialog have discoverable semantics, keyboard focus order and min. 48dp practical targets. Verify dark/light and enlarged font settings; automated semantic assertions complement but do not replace TalkBack/manual device checks.

## 9. Allowed file ownership

Allowed Reader production and tests:

```text
app/src/main/java/com/abdev/partituraspdf/reader/**
app/src/test/java/com/abdev/partituraspdf/reader/**
app/src/androidTest/java/com/abdev/partituraspdf/reader/**
app/src/main/res/values/reader.xml
app/src/main/res/values-es/reader.xml
app/src/main/res/values-ca/reader.xml
app/src/main/res/values-en/reader.xml
docs/development/L_*.md
```

Forbidden without separate integration approval: `MainActivity`, `app/host/**`, `app/navigation/**`, `app/di/**`, `core/**`, `pdf/**`, `storage/**`, `library/**`, `settings/**`, Room/SAF, build/Gradle/manifest, CI and shared status/roadmap documents. Do not touch K/M worktrees or cross-merge their branches.

## 10. Acceptance scenarios / traceability

| ID | Required evidence |
| --- | --- |
| L-M1-T01 | 25/50/25 mapping, including coordinates exactly 25% and 75%, invalid values, narrow/wide viewport |
| L-M1-T02 | First/last page tap is a no-op without wraparound or false failure; step has one dispatch |
| L-M1-T03 | Chrome initially visible, center tap hides/shows, no timed auto-hide |
| L-M1-T04 | Page step/jump/zoom/fit does not change chrome preference during same Reader intent |
| L-M1-T05 | Hiding/showing chrome leaves render viewport stable and causes zero new native opens/renders due solely to chrome |
| L-M1-T06 | Controls, modal and system UI intercept taps; no page-edge click-through |
| L-M1-T07 | Pinch, drag, multi-pointer, consumed/canceled input produce no tap action; separate taps produce no duplicate actions |
| L-M1-T08 | Direct jump maps 1->0, N->N-1, 12->11; 1/2/10/50/100-page scenarios from suitable synthetic/generated PDFs |
| L-M1-T09 | Empty/0/negative/decimal/overflow/>N input never dispatches a page change or checkpoint |
| L-M1-T10 | Stale intent, quiesced, replaced or retired reader cannot accept a jump to an old session |
| L-M1-T11 | Requested-but-not-presented page never becomes visible counter or durable saved position |
| L-M1-T12 | Keyboard/semantics allow access when chrome hidden, no focus theft from dialogs, correct Back/Escape dismissal |
| L-M1-T13 | Error/retry and fatal recovery remain accessible even if chrome previously hidden |
| L-M1-T14 | PAGE/WIDTH, visual 1–4× zoom, pan bounds, rotation and mixed dimensions preserve previous invariants |
| L-M1-T15 | API26 overlay above real `SurfaceView` is visible, clickable, no flicker/canvas invalidation, no second bitmap lease |
| L-M1-T16 | Background/trim/retire/Back and Compose unmount retain release/close ordering; no extra native open/checkpoint |
| L-M1-T17 | es/ca/en parity, light/dark, portrait/landscape, large text and accessible control dimensions |
| L-M1-T18 | No new PDF worker, native render resolution, dependency, writer, fixture mutation or host/shared API change |

Existing G/I Reader tests remain mandatory and must be updated only where the intentional UX contract changes their assertions. These test IDs are *requirements*, not test results.

## 11. Evidence, testing and milestone gates

**L-M1** (this document): inventory and owner UX decisions recorded; no build or instrumentation is implied.

**L-M2**: pure page-number validator and zone/gesture arbitration, deterministic JVM regression tests; report affected logic coverage.

**L-M3**: Reader-only Compose chrome, modal and localized resources; Compose pointer, focus, accessibility and real `SurfaceView` API26 tests.

**L-M4**: lifecycle, viewport, navigation supersession, error/retry, frame/lease and position regression; no global refactor.

Before a PR is marked ready, run the repository's exact mandatory CI and applicable local verification; require **>=90% lines / >=80% branches** for newly testable business logic, resource parity, checked canonical-fixture hashes and API26 instrumentation. API27 / physical Android 8.1 acceptance and relevant manual UX review remain distinct evidence requirements; J08 is deferred to Phase R, but new accessibility regressions are not permitted. Report PASS/FAIL/BLOCKED/NOT RUN accurately. Never claim a test run from this design-only contract.

**Stop conditions:** no safe API26 overlay; any need for host/public API/native worker changes; altered memory lease counts; inability to preserve presentation/checkpoint authority. Record a narrow `docs/development/L_CONTRACT_REQUEST.md` and request owner authorization instead of editing forbidden files.

## 12. Implementation start conditions

1. Owner has approved this functional contract (including the prior explicit decisions).
2. Codex inspects the **current** branch/source/tests and confirms the starting SHA before code work.
3. Create a milestone-specific Reader-only diff plan and regression matrix, with any unresolved overlay/lifecycle uncertainty called out.
4. Do not start L-M2/L-M3 edits, open PR or merge as a side effect of recording L-M1.

**Ownership:** the project owner reviews every milestone and personally authorizes integration into `main`.
