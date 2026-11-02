# L-M4 owner execution addendum — 2026-11-02

Exact executed source: `c417db8c17f9b8174f0be37c168a204c88c1cf34`.
The owner reports successful API26 execution of both new methods:

- `com.abdev.partituraspdf.reader.ReaderNativeIntegrationTest.g_hundredPageTransientSeekPreviewsWithoutNativeRenderAndCommitsOnce`
- `com.abdev.partituraspdf.app.integration.ReaderHostNativeIntegrationTest.registeredHundredPageSeekFlushesDurablePositionAndReopensThroughHost`

**2 PASS**, zero failures/errors/skips. These results establish owner-reported
native seek and registered host checkpoint/reopen execution, not genuine external
SAF authorization or physical-tablet acceptance.

[CI #119](https://github.com/alexbonavila/PartiturasPDF/actions/runs/41522941069)
is SUCCESS at that exact SHA, according to the owner. All nine reported categories
passed: build, lint, unit tests, API26, API27, API36, localization, fixtures and
quality gate. Actual job-name strings, detailed counts and excluded diagnostics
were not independently downloaded in this task; no extra execution is inferred.
No GitHub Actions access or dispatch was performed.

The original L-M4 local NOT RUN report remains unchanged. This dated addendum
records subsequent owner evidence; it does not rewrite earlier attempts or make
results apply to a different production source. L-M3C remains owner-accepted.
Manual L-M3D rotation/chrome restoration is still unconfirmed. Physical Android8.1
acceptance, TalkBack/linguistic/manual checks, applicable external-provider and
performance obligations remain separate. This addendum is not final Phase L
acceptance, PR approval, merge permission or release authorization.
