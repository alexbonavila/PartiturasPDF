#!/usr/bin/env python3
"""Verify committed PDF fixture checksums and structural expectations.

Usage: python test-fixtures/scripts/verify_fixtures.py
Exit code 0 = all expectations pass. Run with PyMuPDF installed.
Never modifies fixture PDF files or manifest.
"""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import sys

import fitz

ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "manifest.json"


def check(entry, passwords):
    path = ROOT / entry["path"]
    assert path.is_file(), f"Missing: {path}"
    data = path.read_bytes()
    assert len(data) == entry["bytes"], f"Size mismatch: {path.name}"
    assert hashlib.sha256(data).hexdigest() == entry["sha256"], f"SHA-256 mismatch: {path.name}"
    if entry["expected_status"] == "corrupted":
        try:
            doc = fitz.open(stream=data, filetype="pdf")
        except (fitz.FileDataError, fitz.EmptyFileError):
            return "expected corruption detected"
        else:
            doc.close()
            raise AssertionError(f"Corrupt fixture unexpectedly opened: {path.name}")
    doc = fitz.open(stream=data, filetype="pdf")
    try:
        password_required = entry["expected_status"] == "password_required"
        assert bool(doc.needs_pass) == password_required, f"Encryption status: {path.name}"
        if password_required:
            secret = passwords[path.name]["user"]
            assert doc.authenticate(secret) > 0, f"Password invalid: {path.name}"
        assert len(doc) == entry["expected_pages"], f"Page count mismatch: {path.name}"
        for i,p in enumerate(doc):
            expected_size = entry["page_sizes_pt"][i]
            actual = [round(p.rect.width,1),round(p.rect.height,1)]
            assert actual == expected_size, f"Dimensions changed: {path.name} page {i+1}"
            assert p.rotation == entry["page_rotations_degrees"][i], f"Rotation changed: {path.name} page {i+1}"
            assert sum(1 for _ in (p.annots() or [])) == entry["annotation_counts"][i], f"Annotations changed: {path.name} page {i+1}"
            assert bool(p.get_text().strip()) == entry["searchable_text"][i], f"Searchable text changed: {path.name} page {i+1}"
            if i == 0 or i == len(doc)-1:
                # Confirm the renderer can decode a sample of each document.
                pix = p.get_pixmap(matrix=fitz.Matrix(.22,.22),alpha=False,annots=True)
                assert len(pix.samples) > 0, f"Render failure: {path.name} page {i+1}"
        if path.name.startswith('12-'):
            types={a.type[1] for page in doc for a in (page.annots() or [])}
            assert {'Highlight','Underline','Ink','FreeText'}.issubset(types), f"Annotation types missing: {types}"
        if path.name.startswith('10-'):
            assert all(page.get_images(full=True) for page in doc), "Scanned fixture should contain raster images"
        return f"{len(doc)} page(s), rendered successfully"
    finally:
        doc.close()


def main():
    manifest=json.loads(MANIFEST.read_text(encoding="utf-8"))
    expected=manifest["files"]
    paths={x['path'] for x in expected}
    actual={p.relative_to(ROOT).as_posix() for p in (ROOT/'pdf').rglob('*.pdf')}
    assert len(expected) == 15, f"Expected 15 fixtures, got {len(expected)}"
    assert len(paths) == len(expected), "Duplicate file entries"
    assert paths == actual, f"Manifest/files mismatch: missing {paths-actual}; extra {actual-paths}"
    for entry in expected:
        try:
            detail=check(entry,manifest.get('known_passwords',{}))
            print(f"PASS {entry['path']} ({detail})")
        except Exception as exc:
            print(f"FAIL {entry['path']}: {exc}",file=sys.stderr)
            return 1
    print(f"SUCCESS: verified {len(expected)} PDF fixtures; checksums, structure and rendering passed.")
    return 0

if __name__ == '__main__':
    sys.exit(main())
