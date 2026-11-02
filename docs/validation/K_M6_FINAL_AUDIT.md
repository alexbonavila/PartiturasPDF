# K-M6 independent source audit — 2026-11-02

## Revision, scope and decision

Audited K HEAD: `089eab7f12102059e6decb22219aabca47358186`.
Original main/K baseline: `7f00a793a06e988859dbefc3e21814c21ec727dd`.
Branch: `feat/library-advanced-k`; dedicated K worktree, initially clean.
Four implementation/correction commits change 14 files, 943 insertions and 17
removals. The cumulative diff contains projection/comparator, Library Compose UI,
four resource bundles, JVM/Android tests, K-M4 evidence and the separately approved
localization-test count correction. ReaderHostNativeIntegrationTest is the explicit
test-only ownership exception. No production host, navigation, Reader, storage,
registry, schema, dependencies, Manifest, CI or shared contracts changed.

**No demonstrated blocking source defect was found. Standalone K is ready for
owner review, not formally accepted or merge-ready.** K-M5 is DEFERRED / NOT VERIFIED.
K-M6 is documentation consolidation, not new integration or runtime acceptance.
This is a bounded source/test audit, not an exhaustive security or memory audit.

## Source findings and classifications

| Finding | Classification / severity | Evidence and disposition |
|---|---|---|
| Search is a pure observed-name projection | No blocking defect found | LibraryProjection.kt trims query boundaries, NFD-normalizes accents, lowercases with Locale.ROOT and performs literal substring matching. It never searches URI/provider values, PDF content or descendants. Empty queries retain unnamed rows; nonempty queries exclude null/blank names. Internal whitespace/punctuation and original display names are preserved. Diacritic folding intentionally makes ñ/n equivalent; it is not general linguistic transliteration. |
| Membership preserves uncertainty | No blocking defect found | Registration is confirmed only by Registered; other unresolved association states remain separate. Favorites require Ready metadata and Idle operation. Pending/Reconciling/Failed never authorize requested membership. Original rows, keys and complete flag survive projection. |
| Ordering and original actions | No blocking defect found | LibraryPresentation's existing comparator is extracted unchanged. Descending reverses ordering only within type/null groups; provider/value ties stay deterministic. Both output lists are defensive immutable copies. Original keys flow into existing typed actions and unchanged controller/host guards. |
| Scope and local lifetime | No blocking defect found; manual evidence gap | LibraryScreen uses a scope digest of length-delimited root/current/version values, excluding load generation. Same-folder refresh/retry and canceled selection preserve controls; successful navigation changes scope. Host conditional composition removes Library state when leaving. Three StateRestorationTester cases and screen lifecycle regressions cover synthetic/saved-state paths. They are not full Activity/process-death acceptance. |
| Stable keys | No blocking defect found | Private ordinal map is keyed by full LibraryRowKey; emitted strings contain navigation/load generations and ordinal, not names, indices or provider references. Query/sort/metadata changes retain observed identity. Obsolete entries are pruned at the next Ready; loading retains at most the previous map, not an accumulating history. |
| Saved-state privacy | Unresolved hardening consideration / low, not demonstrated defect | Saved primitives include query text and an unsalted SHA-256 scope digest, not raw provider references or rows. A digest is not encryption or proof of anonymity: candidate identifiers could be guessed. Search text can itself be personal. No new logs or durable preference store were introduced. No stronger privacy guarantee is asserted. |
| Rendering truth and recovery | No blocking defect found | Complete source-empty, filtered no-match, unresolved-only, partial, loading and typed failures remain distinct. Partial warning is unconditional for incomplete Ready. Existing error/retry components are reused; no failed query establishes deletion. LibraryDocuments/Controller reconciliation and ownership are unchanged. |
| Accessibility/localization | Evidence gap, required acceptance | Labels are resource-backed, filters expose selected semantics, unresolved section is a heading, controls wrap and specify 48 dp minimum heights. Existing/new Compose cases exercise semantics, large font and themes. All bundles have parity. TalkBack, linguistic review, visual contrast/focus and physical tablet acceptance are not established by source or CI alone. |
| Performance/memory | Unresolved measurement limits; no proven defect | K-M4 tests 100/1,000/5,000-row projections with exact memberships and original identities. Projection is memoized by Ready/query. Metadata replacement still copies/maps authoritative rows; repeated normalization/sorting allocates. No approved budget or matched before/after comparison establishes a regression. No speculative cache, threading change or controller refactor is justified. |
| API26 and architecture | No blocking source defect found | Normalizer, SHA-256, Locale.ROOT and primitive saved state are available on API26. No new dependency/platform-only locale API, write capability, alternate registry, migration or provider traversal appears in K. Owner-reported API26 execution is separate evidence below. |

No source correction is recommended on the current evidence. If future execution
finds a defect, reproduce it and request the smallest authorized correction;
acceptance gaps must not be silently converted into defects or waived.

## Evidence and validation

See [K-M4 evidence](K_M4_REGRESSION_EVIDENCE.md) for exact regression mapping,
local timing methodology, historical limitations and source-attributed coverage.
Relevant suites inspected: LibraryProjectionTest, LibraryAdvancedRegressionTest,
LibraryControllerTest, LibraryConcurrencyTest, LibraryRegistrationTest,
LibraryFavoritesTest, LibraryDocumentLifecycleTest, LibraryOpeningTest,
LibraryScreenTest and LibraryScreenStateRestorationTest; host action/route wiring
and the corrective Reader helper were reviewed without changes.

Owner-supplied exact-SHA CI run `41457440097` at audited HEAD reports build/Lint
PASS; 653 JVM and 445 API26 instrumentation PASS with zero failures/skips;
Python 492 executed, one skipped, zero failures; localization PASS; 15 fixtures
PASS; quality gate PASS. Additional compatibility was SKIPPED, not PASS.
The reason for that Python skip is unavailable here and is carried forward for
owner explanation; the passing quality gate does not itself waive policy.
No CI logs/artifacts were retrieved or workflows executed.

Owner-reported manual K-M3 functional testing succeeded on an Android device.
Model/API and per-scenario acceptance are not supplied; this cannot stand in for
physical Android 8.1, TalkBack or current compatibility evidence.

K-M4 local execution at the audited source: 653 JVM PASS; 492 Python PASS (no local
skip); build/Lint/instrumentation compilation PASS; localization and 15 fixtures
PASS. Scoped projection coverage was 54/54 lines, 47/47 branches; projection plus
presentation 84/84 lines, 83/83 branches. These are JVM scopes, not UI coverage.

K-M6 reran only appropriate inexpensive checks: localization validator PASS;
16 localization infrastructure tests PASS; canonical verifier 15 PASS;
`git diff --check` PASS. No source changed, so Gradle/JVM/Android/full Python
campaigns were not repeated. New documentation does not inherit a new CI PASS;
all CI evidence remains attributed to the audited implementation SHA.

## Remaining decision

The [acceptance ledger](K_ACCEPTANCE_LEDGER.md) retains D1–D6 evidence, K-M5's
integration register and pending compatibility/manual requirements. Owner review
may approve standalone technical scope while explicitly retaining these limits;
it is not permission to merge or claim cross-phase integration. K-M5, API27/36,
physical API27 tablet, actual lifecycle/process recovery, accessibility and the
unexplained CI Python skip remain visible. No next phase is started.
