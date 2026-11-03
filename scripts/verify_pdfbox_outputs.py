"""Offline synthetic-PDF fidelity evaluation. Never a production validation authority."""
import argparse
from datetime import datetime, timezone
import sys
import base64
from collections import Counter
import io
import json
import math
import re
from pathlib import Path
import zlib

import collect_pdfbox_spike as evidence

LIMIT = 32 * 1024 * 1024
PIXELS = 8_000_000
ARTIFACTS = 256 * 1024 * 1024


class Unsupported(evidence.Blocked):
    pass


def same(actual, expected, label):
    if actual != expected:
        raise AssertionError(label)


def decoded(stream):
    """Bounded supported filter pipeline; no get_data() unbounded decompression."""
    data = stream._data  # pypdf 5/6 retained encoded bytes; unsupported APIs block.
    if not isinstance(data, bytes) or len(data) > LIMIT:
        raise Unsupported('Missing/oversized encoded stream')
    filters = stream.get('/Filter', [])
    if not isinstance(filters, list): filters = [filters]
    if stream.get('/DecodeParms') not in (None, []):
        raise Unsupported('DecodeParms/predictor requires separate oracle')
    for name in filters:
        if name == '/FlateDecode':
            decoder = zlib.decompressobj()
            data = decoder.decompress(data, LIMIT + 1)
            if len(data) > LIMIT or decoder.unconsumed_tail or not decoder.eof:
                raise Unsupported('Decoded stream limit/incomplete Flate')
            if decoder.unused_data: raise Unsupported('Trailing Flate data')
        elif name == '/ASCII85Decode':
            data = base64.a85decode(data.strip(), adobe=data.strip().endswith(b'~>'))
        elif name == '/DCTDecode':
            # DCT is an encoded image, independently decoded/compared by PyMuPDF rendering.
            # Preserve its exact encoded data here; do not call it decoded pixel evidence.
            pass
        else:
            raise Unsupported(f'Unsupported filter {name}')
        if len(data) > LIMIT: raise Unsupported('Decoded stream bound')
    return data


def semantic(raw, ignore=()):
    from pypdf.generic import (IndirectObject, DictionaryObject, StreamObject, ArrayObject,
        NullObject, BooleanObject, NumberObject, FloatObject, NameObject, TextStringObject, ByteStringObject)
    active = set()
    nodes, byte_count = 0, 0
    def walk(value, depth=0):
        nonlocal nodes, byte_count
        nodes += 1
        if nodes > 100000 or depth > 64: raise Unsupported('Semantic graph bound')
        if isinstance(value, IndirectObject): value = value.get_object()
        if value is None or isinstance(value, NullObject): return None
        identity = id(value)
        if identity in active: raise Unsupported('Cyclic semantic graph')
        active.add(identity)
        try:
            if isinstance(value, DictionaryObject):
                skipped = set(ignore) if depth == 0 else set()
                if isinstance(value, StreamObject): skipped |= {'/Filter', '/DecodeParms', '/Length'}
                items = tuple((str(k), walk(v, depth + 1)) for k, v in sorted(value.items()) if k not in skipped)
                if isinstance(value, StreamObject):
                    data = decoded(value); byte_count += len(data)
                    if byte_count > 128 * 1024 * 1024: raise Unsupported('Decoded graph byte bound')
                    return ('stream', items, __import__('hashlib').sha256(data).hexdigest())
                return ('dict', items)
            if isinstance(value, ArrayObject): return tuple(walk(v, depth + 1) for v in value)
            if isinstance(value, BooleanObject): return ('bool', value.value)
            if isinstance(value, (NumberObject, FloatObject)):
                number = float(value)
                if not math.isfinite(number): raise Unsupported('Nonfinite number')
                return ('number', number)
            if isinstance(value, ByteStringObject): return ('bytes', bytes(value))
            if isinstance(value, TextStringObject): return ('text', str(value))
            if isinstance(value, NameObject): return ('name', str(value))
            raise Unsupported('Unknown semantic object type')
        finally: active.remove(identity)
    return walk(raw)


def page_contents(page):
    from pypdf.generic import ArrayObject, StreamObject
    value = page.get('/Contents')
    if value is None: return b''
    value = value.get_object()
    if isinstance(value, StreamObject): return decoded(value)
    if isinstance(value, ArrayObject):
        if len(value) > 1000: raise Unsupported('Page stream count bound')
        parts, total = [], 0
        for stream in value:
            part = decoded(stream.get_object()); total += len(part) + 1
            if total > LIMIT: raise Unsupported('Page content limit')
            parts.append(part)
        return b'\n'.join(parts) + (b'\n' if parts else b'')
    raise Unsupported('Unknown page contents')


def structure(source, output, source_index, output_index, turns):
    a, b = source.pages[source_index], output.pages[output_index]
    same(b.rotation, (a.rotation + 90 * turns) % 360, 'Wrong composed rotation')
    for box in ('mediabox', 'cropbox'):
        av, bv = list(getattr(a, box)), list(getattr(b, box))
        if len(av) != 4 or len(bv) != 4 or any(not math.isfinite(float(x)) for x in av + bv):
            raise Unsupported('Invalid page geometry')
        if any(abs(float(x) - float(y)) > .01 for x, y in zip(av, bv)):
            raise AssertionError(f'Changed {box} origin/extent')
    same(page_contents(a), page_contents(b), 'Changed/missing decoded page content or order')
    same(semantic(a.get('/Resources')), semantic(b.get('/Resources')), 'Changed resources/fonts/images/graphics state')
    aa, bb = a.get('/Annots', []), b.get('/Annots', [])
    aa, bb = aa.get_object() if hasattr(aa, 'get_object') else aa, bb.get_object() if hasattr(bb, 'get_object') else bb
    same(len(aa), len(bb), 'Annotation count changed')
    for old, new in zip(aa, bb):
        same(semantic(old, ('/P',)), semantic(new, ('/P',)), 'Annotation/AP dictionary changed')
        old_p = old.get_object().get('/P')
        p = new.get_object().get('/P')
        same(old_p is None, p is None, 'Annotation page association presence changed')
        if old_p is not None: same(old_p, a.indirect_reference, 'Source annotation association invalid')
        if p is not None:
            same(p, b.indirect_reference, 'Annotation /P is not its containing output page')
    return dict(status='PASS', checks=['rotation', 'media_crop_origins_extents', 'decoded_contents_order',
                                      'resources_fonts_images_graphics_state', 'annotations_appearance_association'])


def visual(reference, actual, dpi):
    import fitz
    from PIL import Image, ImageChops
    scale = dpi / 72
    for page in (reference, actual):
        width, height = math.ceil(page.rect.width * scale), math.ceil(page.rect.height * scale)
        if width <= 0 or height <= 0 or max(width, height) > 6000 or width * height > PIXELS:
            raise Unsupported('Render dimensions/pixel budget exceeded')
    pixmaps = [p.get_pixmap(matrix=fitz.Matrix(scale, scale), colorspace=fitz.csRGB, alpha=False, annots=True)
               for p in (reference, actual)]
    images = [Image.frombytes('RGB', (p.width, p.height), p.samples) for p in pixmaps]
    metrics, difference = image_metrics(*images)
    return metrics, images, difference


def image_metrics(expected, actual):
    from PIL import ImageChops
    metrics = dict(expected_dimensions=list(expected.size), actual_dimensions=list(actual.size))
    if expected.size != actual.size:
        width,height=max(expected.width,actual.width),max(expected.height,actual.height)
        difference=None
        if width*height <= PIXELS:
            from PIL import Image
            a,b=Image.new('RGB',(width,height)),Image.new('RGB',(width,height))
            a.paste(expected,(0,0)); b.paste(actual,(0,0)); difference=ImageChops.difference(a,b)
        return dict(metrics, status='FAIL', reason='Wrong rendered dimensions', exact_equal=False), difference
    difference = ImageChops.difference(expected, actual)
    histogram = difference.histogram()
    channels = 3 * expected.width * expected.height
    total = sum((i % 256) * count for i, count in enumerate(histogram))
    square = sum((i % 256)**2 * count for i, count in enumerate(histogram))
    different = sum(1 for pixel in difference.getdata() if any(pixel))
    maximum = max((i % 256 for i, count in enumerate(histogram) if count), default=0)
    metrics.update(status='PASS' if different == 0 else 'REVIEW', exact_equal=different == 0,
                   different_pixels=different, different_percent=100 * different / (expected.width * expected.height),
                   max_channel_difference=maximum, mean_absolute_difference=total / channels, rmse=math.sqrt(square / channels))
    return metrics, difference


def expected_recipes():
    expected = Counter()
    def add(case, fixture, indices, turns=0, approach='WORKING_COPY', count=1):
        expected[(case, fixture, tuple(indices), turns, approach)] += count
    add('identity01','01',[0]); add('order02','02',[1,0]); add('order02','02',[1])
    for q in (1,2,3):
        for i in (0,1): add('quarterTurns02','02',[i],q)
    add('selection03','03',[9,4,0])
    for f in ('06','07'): add('geometry0607',f,[0])
    add('mixed08','08',[4,1,2],1); add('rotation09','09',[3,2,1,0],1)
    for f, case, indices in [('10','scanned10',[1,0]),('11','vector11',[1,0]),
                             ('12','annotations12',[1,0]),('13','complex13',[2,0,1])]: add(case,f,indices)
    for f,n in [('04',50),('05',100)]: add('stress0405',f,list(range(n)),count=2)
    for f,n in [('02',2),('08',5),('10',2),('11',2)]: add('importComparison',f,list(reversed(range(n))),approach='STOCK_IMPORT')
    add('import13','13',[2,1,0],approach='STOCK_IMPORT')
    return expected


def aggregate(results, missing):
    statuses = [r['status'] for r in results]
    for status in ('FAIL', 'BLOCKED', 'REVIEW', 'KNOWN_UNSAFE'):
        if status in statuses: return status
    return 'BLOCKED' if missing or not results else 'PASS'


def verify(args):
    from pypdf import PdfReader
    from pypdf.errors import PyPdfError
    import fitz
    collection = Path(args.collection).absolute()
    fixtures = Path(args.fixtures).absolute()
    report_dir = evidence.safe_path(Path(args.report).absolute().parent, Path(args.report).name)
    for input_root in (collection, fixtures):
        evidence.safe_path(input_root, 'sentinel')
        evidence.require(not (report_dir == input_root or report_dir in input_root.parents or input_root in report_dir.parents), 'Report/input overlap')
    evidence.require(not report_dir.exists(), 'Report directory must be new')
    evidence.require(72 <= args.dpi <= 200, 'DPI must be 72..200')
    report_dir.mkdir(parents=True); (report_dir / 'visual').mkdir()
    report = dict(verified_at=datetime.now(timezone.utc).isoformat(), python=sys.version,
                  verifier_sha256=evidence.sha(Path(__file__)), collector_schema_sha256=evidence.sha(Path(evidence.__file__)),
                  status='BLOCKED', scope='Collected synthetic positive recipes; not production or full PDF equivalence',
                  candidate=evidence.CANDIDATE, outputs=[], missing=[], empty_cases=[],
                  known_unsafe=[dict(fixture='12', approach='STOCK_IMPORT', status='KNOWN_UNSAFE',
                                     reason='Owner CI #132 negative regression: external annotation /P')])
    seen, artifact_bytes = Counter(), 0
    def save_images(prefix, images, difference):
        nonlocal artifact_bytes
        paths = []
        for suffix, image in zip(('expected','actual','difference'), [*images, difference]):
            if image is None: continue
            data = io.BytesIO(); image.save(data, format='PNG'); encoded = data.getvalue()
            if len(encoded) > ARTIFACTS - artifact_bytes: raise Unsupported('Accumulated PNG artifact budget exceeded')
            relative = f'visual/{prefix}-{suffix}.png'
            with (report_dir / relative).open('xb') as stream: stream.write(encoded)
            artifact_bytes += len(encoded); paths.append(relative)
        return paths
    try:
        manifest_hash, entries = evidence.canonical(fixtures)
        meta = evidence.load_json(evidence.safe_path(collection, 'collection-manifest.json'))
        evidence.require(meta.get('status') == 'COLLECTED' and meta.get('schema') == 1, 'Incomplete collection')
        evidence.require(meta.get('manifest_sha256') == manifest_hash and meta.get('candidate') == evidence.CANDIDATE, 'Collection identity mismatch')
        evidence.require(isinstance(meta.get('declared_commit'),str) and re.fullmatch('[0-9a-f]{40}',meta['declared_commit']), 'Invalid declared source SHA')
        evidence.require(isinstance(meta.get('serial'),str) and re.fullmatch(r'emulator-[0-9]+',meta['serial']), 'Invalid recorded emulator')
        report.update(collection=meta, manifest_sha256=manifest_hash, dpi=args.dpi,
                      renderer=fitz.VersionBind, pypdf=__import__('pypdf').__version__)
        namespaces = meta.get('namespaces')
        evidence.require(isinstance(namespaces,list) and 0 < len(namespaces) <= 64, 'Missing/excess namespaces')
        used_names = set()
        for record in namespaces:
            name = evidence.namespace(record['namespace'])
            evidence.require(name not in used_names, 'Duplicate namespace'); used_names.add(name)
            local = evidence.safe_path(collection, name)
            path = evidence.safe_path(local, 'evidence.json')
            evidence.require(evidence.sha(path) == record['evidence_sha256'], 'Evidence JSON hash mismatch')
            data = evidence.load_json(path)
            outputs = evidence.validate_evidence(data, manifest_hash, entries, meta['api'])
            evidence.require(record.get('files') == [dict(file=r['file'], bytes=r['bytes'], sha256=r['sha256']) for r in outputs], 'Collected file inventory mismatch')
            evidence.require({p.name for p in local.iterdir()} == {'evidence.json', *(r['file'] for r in outputs)}, 'Unexpected collected files')
            if not outputs:
                report['empty_cases'].append(dict(namespace=name, case=data['case'], status='BLOCKED', reason='No positive recipe; negative success not established'))
            for recipe in outputs:
                key = (data['case'],recipe['fixture'],tuple(recipe['indices']),recipe['extra_quarter_turns'],recipe['approach'])
                seen[key] += 1
                row = dict(namespace=name, recipe=recipe, pages=[], status='BLOCKED')
                report['outputs'].append(row)
                try:
                    entry = entries[recipe['fixture']]
                    source = evidence.safe_path(fixtures, entry['path']); output = evidence.safe_path(local, recipe['file'])
                    evidence.require(source.is_file() and source.stat().st_size == entry['bytes'] and evidence.sha(source) == entry['sha256'], 'Canonical source integrity failure')
                    evidence.require(output.is_file() and output.stat().st_size == recipe['bytes'] and evidence.sha(output) == recipe['sha256'], 'Collected output integrity failure')
                    if recipe['fixture'] == '12' and recipe['approach'] == 'STOCK_IMPORT':
                        row.update(status='KNOWN_UNSAFE', reason='Cannot accept known defective stock annotation import'); continue
                    src, dst = PdfReader(source, strict=True), PdfReader(output, strict=True)
                    same(src.is_encrypted, False, 'Protected source'); same(dst.is_encrypted, False, 'Unexpected encryption')
                    same(len(src.pages), entry['expected_pages'], 'Source page count')
                    same(len(dst.pages), recipe['pages'], 'Wrong output page count')
                    count = len(dst.pages)
                    sampled = count >= 50 and recipe['extra_quarter_turns'] == 0
                    visual_pages = {0, count//2, count-1} if sampled else set(range(count))
                    row['visual_coverage'] = dict(mode='SAMPLED' if sampled else 'FULL', pages=sorted(visual_pages), total=count)
                    with fitz.open(stream=source.read_bytes(), filetype='pdf') as reference, fitz.open(output) as actual:
                        for j,i in enumerate(recipe['indices']):
                            page = dict(index=j, source_index=i, status='BLOCKED'); row['pages'].append(page)
                            page['structural'] = structure(src,dst,i,j,recipe['extra_quarter_turns'])
                            # Text at identical ORIGINAL orientation, no lossy normalization or save.
                            raw_rotation = actual[j].rotation
                            try:
                                actual[j].set_rotation(reference[i].rotation)
                                same(actual[j].get_text(), reference[i].get_text(), 'Changed complete page text')
                            finally: actual[j].set_rotation(raw_rotation)
                            page['text'] = 'PASS'
                            reference_rotation = reference[i].rotation
                            try:
                                reference[i].set_rotation((reference_rotation + 90*recipe['extra_quarter_turns']) % 360)
                                if any(abs(x-y) > .01 for x,y in zip(tuple(reference[i].rect),tuple(actual[j].rect))):
                                    raise AssertionError('Wrong effective page dimensions')
                            finally: reference[i].set_rotation(reference_rotation)
                            page['effective_bounds'] = dict(status='PASS', actual=list(actual[j].rect))
                            if j in visual_pages:
                                original = reference[i].rotation
                                try:
                                    reference[i].set_rotation((original + 90*recipe['extra_quarter_turns']) % 360)
                                    metrics, images, diff = visual(reference[i],actual[j],args.dpi)
                                    page['visual'] = metrics
                                    if metrics['status'] != 'PASS' or args.retain_images:
                                        page['images'] = save_images(f'{len(report["outputs"])}-{j}',images,diff)
                                    page['status'] = metrics['status']
                                finally: reference[i].set_rotation(original)
                            else:
                                page.update(status='PASS', visual=dict(status='NOT_RENDERED', reason='Explicit long-document sampling'))
                    row['status'] = aggregate(row['pages'], [])
                except (AssertionError, PyPdfError) as failure:
                    row.update(status='FAIL', reason=str(failure))
                    if row['pages']: row['pages'][-1].update(status='FAIL', reason=str(failure))
                    row['unverified_page_indices'] = list(range(len(row['pages']),recipe['pages']))
                except Exception as failure:
                    row.update(status='BLOCKED', reason=f'{type(failure).__name__}: {failure}')
                    if row['pages']: row['pages'][-1].update(status='BLOCKED', reason=str(failure))
                    row['unverified_page_indices'] = list(range(len(row['pages']),recipe['pages']))
        evidence.require({p.name for p in collection.iterdir()} == {'collection-manifest.json', *used_names}, 'Unexpected collection entries')
        expected = expected_recipes()
        report['missing'] = [dict(case=k[0],fixture=k[1],indices=list(k[2]),turns=k[3],approach=k[4],count=n)
                             for k,n in (expected-seen).items()]
        report['unexpected_or_duplicate'] = [str(k) for k in (seen-expected)]
        if report['unexpected_or_duplicate']: report['missing'].append('Unexpected/duplicate scenarios')
        report['status'] = aggregate(report['outputs'], report['missing'])
    except Exception as failure:
        report.update(status='BLOCKED', error=f'{type(failure).__name__}: {failure}')
    report['fixtures'] = {f: dict(status=aggregate([r for r in report['outputs'] if r['recipe']['fixture']==f], []),
                                  outputs=sum(r['recipe']['fixture']==f for r in report['outputs']))
                          for f in sorted({r['recipe']['fixture'] for r in report['outputs']})}
    with (report_dir/'validation-report.json').open('x',encoding='utf-8') as stream: json.dump(report,stream,indent=2)
    lines = ['# Independent PDFBox synthetic-output report', '', f'Overall: **{report["status"]}**', '',
             'Declared commit is owner provenance, not device execution attestation.',
             'Stock import fixture 12 remains KNOWN_UNSAFE; negative empty recipes prove no success.', '',
             f'Missing scenarios: {len(report["missing"])}', '']
    for row in report['outputs']:
        lines.append(f'- {row["recipe"]["fixture"]} / {row["recipe"]["approach"]}: **{row["status"]}**; {row.get("reason", "see page metrics in JSON")}')
    if 'error' in report: lines.append('BLOCKED: '+report['error'])
    (report_dir/'validation-report.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    return 0 if report['status']=='PASS' else 1


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--collection',required=True)
    parser.add_argument('--fixtures',default=str(evidence.REPO/'test-fixtures'))
    parser.add_argument('--report',required=True)
    parser.add_argument('--dpi',type=int,default=144)
    parser.add_argument('--retain-images',action='store_true')
    args=parser.parse_args(argv)
    try: return verify(args)
    except Exception as failure:
        print(f'BLOCKED: {failure}'); return 2


if __name__=='__main__':
    raise SystemExit(main())
