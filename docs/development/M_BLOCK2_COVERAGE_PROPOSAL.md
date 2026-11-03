# M Block 1 + 2 coverage proposal — NOT APPLIED

2026-11-03. **COVERAGE: BLOCKED FOR M PACKAGES UNTIL OWNER-APPROVED BUILD-ONLY CHANGE.**
Current app/build.gradle.kts:93–139 defines contractClasses over core/**,
storage/api/**, pdf/api/**, contractCoverageReport XML/HTML and
verifyContractCoverage (LINE 0.90 / BRANCH 0.80), finalized after testDebugUnitTest.
It omits storage/transaction/**, pdf/editing/**, pdf/workflow/**.

Propose a separate `mClasses` scope with exactly:

```kotlin
include("com/abdev/partituraspdf/storage/transaction/**")
include("com/abdev/partituraspdf/pdf/editing/**")
include("com/abdev/partituraspdf/pdf/workflow/**")
include("com/abdev/partituraspdf/storage/api/Snapshot*")
include("com/abdev/partituraspdf/storage/api/Recovery*")
include("com/abdev/partituraspdf/storage/api/TransactionId*")
include("com/abdev/partituraspdf/storage/api/M*")
include("com/abdev/partituraspdf/storage/api/WriteDestination*")
include("com/abdev/partituraspdf/storage/api/NewDestination*")
include("com/abdev/partituraspdf/storage/api/PublicationResult*")
include("com/abdev/partituraspdf/pdf/api/M*")
include("com/abdev/partituraspdf/pdf/api/PageSelection*")
include("com/abdev/partituraspdf/pdf/api/OrderedPagePlan*")
include("com/abdev/partituraspdf/pdf/api/PdfOperationFailure*")
include("com/abdev/partituraspdf/pdf/api/PdfValidationReport*")
include("com/abdev/partituraspdf/pdf/api/ValidatedPdfArtifact*")
```

Scope must enumerate compiled top-level public and internal classes (file names
are not class names), including coroutine/state machine and Android adapter logic.
The exact compiled class manifest must be reviewed after owner compilation; this
text is not a validated denominator. No blanket Android/exception-path exclusions.
Keep existing gates untouched; additional M-scoped XML/HTML and verification use
same JaCoCo version and >=90% lines/>=80% branches, supported JVM+Android execution
inputs with class identity validation. Any generated exclusions need explicit
justification. Separate Android instrumentation contributions are essential for
PFD/ContentResolver/provider logic; JVM-only reports cannot close those branches.

Existing owner commands (not run): bash ./gradlew :app:testDebugUnitTest
:app:contractCoverageReport :app:verifyContractCoverage. Proposed new M report/
verify task names must NOT be invoked until separately approved and implemented.
No new task/build edit, test execution or percentage is claimed here. Global
averages or CI #124 success do not satisfy M implementation-specific coverage.
