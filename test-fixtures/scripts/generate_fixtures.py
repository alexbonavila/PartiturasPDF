#!/usr/bin/env python3
"""Create synthetic, reproducible PDF test fixtures for PartiturasPDF.

Run from any directory: python scripts/generate_fixtures.py
External dependencies: reportlab, pymupdf (fitz), Pillow.
NotoMusic and Lato are optional fonts; a geometric fallback is provided.
All document content is original synthetic test content (CC0).
"""
from __future__ import annotations
import hashlib
import io
import json
import math
from pathlib import Path
import random

import fitz
from pypdf import PdfReader, PdfWriter
from pypdf.generic import ArrayObject, ByteStringObject
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter
from reportlab.lib import colors
from reportlab.lib.pagesizes import A3, A4, A5, letter, landscape
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas

ROOT = Path(__file__).resolve().parent.parent
PDF = ROOT / "pdf"
BUILD = ROOT / "scripts" / ".build"
BUILD.mkdir(exist_ok=True)
for d in ["basic", "page-sizes", "complex", "annotations", "invalid"]:
    (PDF / d).mkdir(parents=True, exist_ok=True)

FONT_PATHS = {
    "FixtureMusic": ["/usr/share/fonts/truetype/noto/NotoMusic-Regular.ttf", "C:/Windows/Fonts/NotoMusic-Regular.ttf"],
    "FixtureLato": ["/usr/share/fonts/truetype/lato/Lato-Regular.ttf", "C:/Windows/Fonts/Lato-Regular.ttf"],
    "FixtureLatoBold": ["/usr/share/fonts/truetype/lato/Lato-Bold.ttf", "C:/Windows/Fonts/Lato-Bold.ttf"],
}
for name, paths in FONT_PATHS.items():
    for path in paths:
        if Path(path).exists():
            pdfmetrics.registerFont(TTFont(name, path)); break
MUSIC = "FixtureMusic" if "FixtureMusic" in pdfmetrics.getRegisteredFontNames() else None
TEXT = "FixtureLato" if "FixtureLato" in pdfmetrics.getRegisteredFontNames() else "Helvetica"
BOLD = "FixtureLatoBold" if "FixtureLatoBold" in pdfmetrics.getRegisteredFontNames() else "Helvetica-Bold"

# Each named fixture has verifiable original text, page numbers, and notation.
def page(c: canvas.Canvas, name: str, index: int, total: int, shape: str = "standard", dense: bool = False):
    W, H = c._pagesize
    factor = min(W / A4[0], H / A4[1]); margin = 48 * factor
    black = colors.HexColor("#172B33"); gray = colors.HexColor("#536B72")
    c.setTitle(name); c.setAuthor("PartiturasPDF QA Fixtures")
    c.setFillColor(black); c.setFont(BOLD, 17 * factor)
    c.drawString(margin, H - 62*factor, "PARTITURAS PDF - QA SCORE")
    c.setFillColor(gray); c.setFont(TEXT, 9 * factor)
    c.drawString(margin, H-80*factor, f"{name} | page {index:03d} of {total:03d}")
    c.drawRightString(W-margin, H-80*factor, f"CASE {index:03d} / {total:03d}")
    c.setStrokeColor(colors.HexColor("#A5D6A7"));c.setLineWidth(1.7*factor)
    c.line(margin,H-92*factor,W-margin,H-92*factor)
    # Four or six staves with pseudo-original study music; deterministic pitch sequences.
    staff_count = 5 if dense else 4
    top = H - 155*factor; step = min(155*factor, (H-250*factor)/staff_count)
    for staff in range(staff_count):
        y = top - staff*step
        x0 = margin+20*factor; x1 = W-margin
        linegap = 8.8*factor
        c.setStrokeColor(black);c.setLineWidth(.7*factor)
        for k in range(5): c.line(x0,y+k*linegap,x1,y+k*linegap)
        # G clef; music font when available, geometric text substitute otherwise.
        c.setFillColor(black)
        if MUSIC:
            c.setFont(MUSIC,45*factor)
            c.drawString(x0+2*factor,y-12*factor,"\U0001D11E")
        else:
            c.setFont(BOLD,31*factor);c.drawString(x0+4*factor,y-5*factor,"G")
        c.setFont(BOLD,13*factor);c.drawString(x0+33*factor,y+5*factor,"4");c.drawString(x0+33*factor,y-11*factor,"4")
        notes = 24
        start = x0 + 59*factor; right = x1 - 3*factor
        delta=(right-start)/(notes-1)
        for i in range(notes):
            pitch=((i*3+staff*5+index*2)%13)-4
            xn = start+i*delta
            yn = y+pitch*(linegap/2)
            # Notehead + stem
            c.saveState();c.translate(xn,yn);c.rotate(-17);c.ellipse(-4.2*factor,-2.65*factor,4.2*factor,2.65*factor,fill=1,stroke=0);c.restoreState()
            stemlen=24*factor
            up = pitch < 4
            c.setLineWidth(.95*factor)
            if up: c.line(xn+3.5*factor,yn,xn+3.5*factor,yn+stemlen)
            else: c.line(xn-3.5*factor,yn,xn-3.5*factor,yn-stemlen)
            if i%6==5:
                c.setLineWidth(.95*factor)
                bar=(xn+delta/2 if i!=notes-1 else xn+6*factor)
                c.line(bar,y-2*factor,bar,y+4*linegap+2*factor)
        if staff==0:
            c.setFont(TEXT,8*factor);c.setFillColor(gray)
            c.drawString(start,y+57*factor,"Moderato - synthetic notation for regression tests")
    c.setStrokeColor(colors.HexColor("#90CAF9"));c.line(margin,49*factor,W-margin,49*factor)
    c.setFont(TEXT,8*factor);c.setFillColor(gray)
    c.drawString(margin,34*factor,"Original synthetic QA material - CC0 1.0")
    c.drawRightString(W-margin,34*factor,f"PAGE-ID {index:03d}")


def make_pdf(path, sizes, shape="standard", dense=False):
    path.parent.mkdir(parents=True,exist_ok=True)
    canv = canvas.Canvas(str(path), pagesize=sizes[0], pageCompression=1, invariant=1)
    canv.setCreator("PartiturasPDF test fixture generator")
    for ix, size in enumerate(sizes,1):
        canv.setPageSize(size)
        page(canv, path.stem, ix, len(sizes), shape, dense=dense)
        canv.showPage()
    canv.save()


def metadata(path, title):
    d=fitz.open(path)
    d.set_metadata({"title":title,"author":"PartiturasPDF QA","subject":"Synthetic music score - regression fixture","keywords":"pdf, sheet music, qa, synthetic"})
    d.save(path.with_suffix('.tmp.pdf'),garbage=4,deflate=True, no_new_id=True)
    d.close();path.with_suffix('.tmp.pdf').replace(path)

# Plain scores with distinct page numbering.
for filename,count in [("01-single-a4.pdf",1),("02-two-pages.pdf",2),("03-ten-pages.pdf",10),("04-fifty-pages.pdf",50),("05-hundred-pages.pdf",100)]:
    out=PDF/"basic"/filename
    make_pdf(out,[A4]*count,dense=count>=50)
    metadata(out,filename)

make_pdf(PDF/"page-sizes"/"06-a3-landscape.pdf",[landscape(A3)])
make_pdf(PDF/"page-sizes"/"07-a5-portrait.pdf",[A5])
make_pdf(PDF/"page-sizes"/"08-mixed-sizes.pdf",[A4,landscape(A3),A5,letter,(600,600)])
make_pdf(BUILD/"pre-rotated.pdf",[A4]*4)
d=fitz.open(BUILD/"pre-rotated.pdf")
for i,p in enumerate(d):p.set_rotation(i*90)
d.save(PDF/"page-sizes"/"09-rotated-pages.pdf",garbage=4,deflate=True)
d.close()

# Raster/image-only scanning simulation: no searchable page text.
scan_target=PDF/"complex"/"10-scanned-score.pdf"
make_pdf(BUILD/"scan-sample.pdf",[A4,A4])
src=fitz.open(BUILD/"scan-sample.pdf")
doc=fitz.open()
for index,p in enumerate(src):
    pix=p.get_pixmap(matrix=fitz.Matrix(2.1,2.1),colorspace=fitz.csGRAY,alpha=False)
    im=Image.frombytes("L",[pix.width,pix.height],pix.samples)
    # Light scanner artifacts, deterministic and gentle enough for readable notation.
    rng=random.Random(931+index)
    draw=ImageDraw.Draw(im)
    for k in range(120):
        xx=rng.randrange(im.width); yy=rng.randrange(im.height)
        shade=rng.randint(226,249)
        draw.point((xx,yy),fill=shade)
    im=im.filter(ImageFilter.GaussianBlur(.22))
    bio=io.BytesIO();im.save(bio,format="JPEG",quality=83,optimize=True)
    dst=doc.new_page(width=A4[0],height=A4[1])
    dst.insert_image(dst.rect,stream=bio.getvalue())
doc.save(scan_target,garbage=4,deflate=True);doc.close();src.close()

# High-fidelity selectable/vector text and notation with embedded fonts.
make_pdf(PDF/"complex"/"11-vector-score.pdf",[A4,A4],dense=True)

# Annotations: standard PDF annotation objects, not flattened into page content.
make_pdf(BUILD/"annotation-base.pdf",[A4,A4])
d=fitz.open(BUILD/"annotation-base.pdf")
p=d[0]
text_rect=p.search_for("PARTITURAS PDF")
if text_rect:
    hl=p.add_highlight_annot(text_rect[0]);hl.set_colors(stroke=(1,0.78,0));hl.update(opacity=.40)
    under=p.add_underline_annot(text_rect[0]);under.set_colors(stroke=(.2,.4,.8));under.update()
ink=p.add_ink_annot([[(100,360),(116,349),(134,358),(150,341),(170,348),(190,327)]])
ink.set_colors(stroke=(.13,.50,.80));ink.set_border(width=2.5);ink.update()
free=p.add_freetext_annot(fitz.Rect(180,420,460,468),"Pencil note: repeat section A",fontsize=13,
                          fontname="helv",text_color=(.1,.3,.22),fill_color=(.9,1,.93))
free.update()
p2=d[1];hl2=p2.add_highlight_annot(fitz.Rect(150,175,420,202));hl2.set_colors(stroke=(1,.85,0));hl2.update(opacity=.35)
d.save(PDF/"annotations"/"12-annotated.pdf",garbage=4,deflate=True);d.close()

# Complex: fonts, vector gradients-ish elements, transparent filled shapes,
# clipping and multiple pages; no encryption.
complex_out=PDF/"complex"/"13-complex.pdf"
canv=canvas.Canvas(str(complex_out),pagesize=A4,invariant=1,pageCompression=1)
for ix in range(3):
    canv.setPageSize(A4); page(canv,"13-complex",ix+1,3,dense=True)
    W,H=A4
    canv.saveState();canv.setFillColorRGB(.13,.39,.62)
    if hasattr(canv,"setFillAlpha"):canv.setFillAlpha(.12)
    for k in range(5):
        canv.circle(W-90-k*11,124+k*8,18+k*2,stroke=0,fill=1)
    if hasattr(canv,"setFillAlpha"):canv.setFillAlpha(1)
    canv.restoreState()
    canv.setFont(TEXT,10)
    canv.drawString(60,80,f"EMBEDDED FONT / TRANSPARENCY TEST / {ix+1}")
    canv.showPage()
canv.save()

# Password-protected document: user password is documented solely for test use.
make_pdf(BUILD/"password-plaintext.pdf",[A4,A4])
d=fitz.open(BUILD/"password-plaintext.pdf")
d.save(PDF/"complex"/"14-password.pdf", encryption=fitz.PDF_ENCRYPT_AES_256,
       user_pw="test1234",owner_pw="qa-fixture-owner",permissions=fitz.PDF_PERM_PRINT|fitz.PDF_PERM_COPY)
d.close()

# A deliberately UNOPENABLE PDF: not a valid PDF by design.
# Break the cross-reference and truncate the body; no reader should render it.
raw=(PDF/"basic"/"02-two-pages.pdf").read_bytes()
(PDF/"invalid"/"15-corrupted.pdf").write_bytes(b"%PDF-1.7\nNOT ACTUALLY A VALID PDF: deliberately truncated before any objects or xref.\n")

# Canonicalize unencrypted PDFs to remove variable trailer IDs produced by the
# PDF editor. Keep encrypted PDFs untouched to test their original encryption.
def canonicalize_pdf(path):
    reader = PdfReader(path)
    writer = PdfWriter()
    writer.clone_document_from_reader(reader)
    stable_id = hashlib.sha256(path.name.encode("utf-8")).digest()[:16]
    writer._ID = ArrayObject([ByteStringObject(stable_id), ByteStringObject(stable_id)])
    writer.add_metadata({"/CreationDate": "D:20000101000000Z", "/ModDate": "D:20000101000000Z"})
    interim = path.with_suffix(".canonical.tmp")
    with interim.open("wb") as output:
        writer.write(output)
    interim.replace(path)

for p in PDF.glob("*/*.pdf"):
    if p.name not in {"14-password.pdf", "15-corrupted.pdf"}:
        canonicalize_pdf(p)

cases={
 "01-single-a4.pdf": ("basic",1,"Single-page A4 vector score"),
 "02-two-pages.pdf": ("basic",2,"Two-page A4 score; previous/next navigation"),
 "03-ten-pages.pdf": ("basic",10,"Ten pages with unique labels; sorting and thumbnails"),
 "04-fifty-pages.pdf": ("basic",50,"Fifty pages; pagination and cache checks"),
 "05-hundred-pages.pdf": ("basic",100,"One hundred pages; memory and navigation"),
 "06-a3-landscape.pdf": ("page-sizes",1,"Landscape A3 page"),
 "07-a5-portrait.pdf": ("page-sizes",1,"Portrait A5 page"),
 "08-mixed-sizes.pdf": ("page-sizes",5,"A4, landscape A3, A5, Letter and square pages"),
 "09-rotated-pages.pdf": ("page-sizes",4,"Per-page rotation 0, 90, 180 and 270 degrees"),
 "10-scanned-score.pdf": ("complex",2,"Two image-only scanned-style pages"),
 "11-vector-score.pdf": ("complex",2,"Vector score and embedded music font"),
 "12-annotated.pdf": ("annotations",2,"PDF Ink, Highlight, Underline and FreeText annotations"),
 "13-complex.pdf": ("complex",3,"Embedded fonts, vector drawing and transparency"),
 "14-password.pdf": ("complex",2,"AES-256 protected score; test user password: test1234"),
 "15-corrupted.pdf": ("invalid",None,"Intentionally truncated and unopenable test file"),
}

entries=[]
for filename,(folder,n,description) in cases.items():
    path=PDF/folder/filename
    record={"id":filename[:2],"path":str(path.relative_to(ROOT).as_posix()),"description":description,
            "bytes":path.stat().st_size,"sha256":hashlib.sha256(path.read_bytes()).hexdigest(),
            "expected_pages":n,"expected_status":"corrupted" if n is None else ("password_required" if filename.startswith("14") else "valid")}
    if n is not None:
        pdf=fitz.open(path)
        if pdf.needs_pass:
            assert pdf.authenticate("test1234")
        record["page_sizes_pt"]=[[round(p.rect.width,1),round(p.rect.height,1)] for p in pdf]
        record["page_rotations_degrees"]=[p.rotation for p in pdf]
        record["annotation_counts"]=[sum(1 for _ in (p.annots() or [])) for p in pdf]
        record["searchable_text"]=[bool(p.get_text().strip()) for p in pdf]
        pdf.close()
    else:
        try:
            broken=fitz.open(path)
            broken.close()
            raise ValueError("Corruption fixture could be opened; regenerate a stronger corruption")
        except (fitz.FileDataError,fitz.EmptyFileError):pass
    entries.append(record)
manifest={"schema_version":1,"project":"PartiturasPDF", "fixture_set":"Synthetic QA scores",
          "license":"CC0-1.0 (all synthetic content)",
          "authoring_note":"Generated files are synthetic musical-notation test documents, not arrangements of an existing composition.",
          "source_reference":"https://www.mutopiaproject.org/ftp/MuellerAE/muller-siciliano/muller-siciliano-a4.pdf",
          "known_passwords":{"14-password.pdf":{"user":"test1234","purpose":"test-only, nonsecret"}},
          "files":entries}
(ROOT/"manifest.json").write_text(json.dumps(manifest,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
print("Generated",len(entries),"PDF fixtures; total PDF bytes",sum(e['bytes'] for e in entries))
for row in entries:print(row['path'],row['expected_pages'],row['bytes'])
