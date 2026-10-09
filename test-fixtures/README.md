# PartiturasPDF - PDF regression test fixtures

This directory is intended to be copied to the root of the
[`alexbonavila/PartiturasPDF`](https://github.com/alexbonavila/PartiturasPDF)
repository as `test-fixtures/`.

Contains **15 original, synthetic score PDFs** (not real compositions) with
identifiable page labels and musical notation. The PDFs are deliberately kept
outside `app/src/main`, so **they are never embedded in the production APK**.

## Directory structure

```text
test-fixtures/
  pdf/
    basic/             01 - 05, page count and navigation
    page-sizes/        06 - 09, sheet sizes and rotations
    complex/           10, 11, 13, 14, raster/vector/fonts/password
    annotations/       12, PDF annotation dictionaries
    invalid/           15, intentionally invalid PDF
  scripts/
    generate_fixtures.py
    verify_fixtures.py
  manifest.json       Expected properties and SHA-256 checksums
  requirements-fixtures.txt
  SOURCES.md
  README.md
```

## Fixtures

| ID | PDF | Pages | Test purpose |
|----|-----|------:|--------------|
| 01 | `basic/01-single-a4.pdf` | 1 | A4 reading and first/last page |
| 02 | `basic/02-two-pages.pdf` | 2 | Next/previous page |
| 03 | `basic/03-ten-pages.pdf` | 10 | Navigation, ordering and thumbnails |
| 04 | `basic/04-fifty-pages.pdf` | 50 | Long document and page cache |
| 05 | `basic/05-hundred-pages.pdf` | 100 | Large page count and memory pressure |
| 06 | `page-sizes/06-a3-landscape.pdf` | 1 | A3 landscape fitting |
| 07 | `page-sizes/07-a5-portrait.pdf` | 1 | A5 portrait fitting |
| 08 | `page-sizes/08-mixed-sizes.pdf` | 5 | Mixed A4/A3/A5/Letter/square document |
| 09 | `page-sizes/09-rotated-pages.pdf` | 4 | Metadata rotations 0/90/180/270 |
| 10 | `complex/10-scanned-score.pdf` | 2 | Image-only PDF, no searchable text |
| 11 | `complex/11-vector-score.pdf` | 2 | Vector notation and embedded fonts |
| 12 | `annotations/12-annotated.pdf` | 2 | Highlight, underline, ink and FreeText annotations |
| 13 | `complex/13-complex.pdf` | 3 | Embedded fonts, vector artwork and transparency |
| 14 | `complex/14-password.pdf` | 2 | Encrypted/password-protected document |
| 15 | `invalid/15-corrupted.pdf` | N/A | Parser failure / graceful error handling |

**Test password for `14-password.pdf`: `test1234`.** It is not a secret. This
fixture uses AES-256 encryption; the owner password is for test use only.

The corrupted file must **fail** to open: that is its expected, passing behavior.

## Integrity verification

Install test-only dependencies (requires Python):

```bash
python -m pip install -r test-fixtures/requirements-fixtures.txt
python test-fixtures/scripts/verify_fixtures.py
```

The verifier checks the **SHA-256 checksum, byte size, page count, page
geometry, rotation, presence of searchable text, annotation count, password
requirements and sample page rendering**. It exits with a failure code if any
expectation changes.

## Rules for automated tests

1. **Never edit fixture PDFs in place.** Always copy into per-test temporary
   directories before rotating, editing, reordering, joining or annotating them.
2. A fixture may be modified in source control only in a dedicated reviewed PR.
3. Use `manifest.json` as the canonical source of test expectations.
4. JVM and Android instrumentation tests must reference the **same authoritative
   files**, without maintaining separately edited copies.
5. Configure Gradle to expose the test files to each kind of test only where
   necessary. Keep them out of release APKs and the user library.
6. When adding cases for a new bug, add a new fixture or deterministic
   reproducer and include explicit acceptance tests.
7. Do not replace the deliberately invalid file with a repaired document.
8. For result comparison, inspect the resulting **content, order, page size,
   rotations and annotations**. Avoid raw-byte equality for edited output PDFs,
   whose internal object order and metadata may legitimately differ.
9. Large 100 MB+ load-test PDFs are not included in Git; generate these in a
   separately documented, repeatable performance-test workflow.

## Regeneration

The test PDFs are already generated and ready to commit. **Normally there is
no need to regenerate them.** If you must regenerate, install the fixture
requirements and run:

```bash
python test-fixtures/scripts/generate_fixtures.py
python test-fixtures/scripts/verify_fixtures.py
```

The generator optionally uses locally installed `Noto Music` and `Lato`
fonts. The committed documents have those fonts embedded where appropriate.
Regeneration on machines without those fonts may change the artwork and
SHA-256 hashes; **never regenerate in CI**. Also, AES-256 encryption salt may
change its file checksum when the password-protected PDF is regenerated.

## Licensing

All 15 PDFs contain independently authored, synthetic test-only music
notation and explanatory text, released as **CC0 1.0**. No externally sourced
PDF has been copied into this dataset. See `SOURCES.md` for an optional real
public-domain music score to add in a separate, approved future test case.
