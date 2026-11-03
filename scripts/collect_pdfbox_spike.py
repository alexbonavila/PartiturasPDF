"""Owner-run, bounded collection of explicitly named synthetic PDFBox evidence only."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import threading
import uuid

APP = 'com.abdev.partituraspdf'
CANDIDATE = '2.0.27.0'
JSON_LIMIT = 8 * 1024 * 1024
OUTPUT_LIMIT = 128 * 1024 * 1024
TOTAL_LIMIT = 256 * 1024 * 1024
REPO = Path(__file__).resolve().parents[1]


class Blocked(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise Blocked(message)


def namespace(value):
    prefix = 'm-pdfbox-spike-'
    require(isinstance(value, str) and value.startswith(prefix), 'Invalid namespace')
    try:
        parsed = uuid.UUID(value[len(prefix):])
    except (ValueError, AttributeError):
        raise Blocked('Invalid namespace UUID') from None
    require(str(parsed) == value[len(prefix):], 'Noncanonical namespace UUID')
    return value


def safe_path(root, relative):
    require(isinstance(relative, str) and relative and '\\' not in relative, 'Invalid path')
    parts = relative.split('/')
    require(all(p not in ('', '.', '..') and ':' not in p for p in parts), 'Unsafe path')
    root = Path(root).absolute()
    for parent in (root, *root.parents):
        require(not parent.is_symlink(), 'Symlink root/ancestor')
    target = root
    for part in parts:
        target = target / part
        require(not target.is_symlink(), 'Symlink path')
    return target


def sha(path):
    digest = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for chunk in iter(lambda: stream.read(65536), b''):
            digest.update(chunk)
    return digest.hexdigest()


def load_json(path, limit=JSON_LIMIT):
    require(path.is_file() and path.stat().st_size <= limit, 'Missing/oversized JSON')
    def pairs(items):
        result = {}
        for key, value in items:
            require(key not in result, 'Duplicate JSON key')
            result[key] = value
        return result
    try:
        return json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=pairs)
    except (UnicodeError, json.JSONDecodeError) as failure:
        raise Blocked('Invalid UTF-8 JSON') from failure


def canonical(root):
    path = safe_path(root, 'manifest.json')
    manifest = load_json(path)
    entries = manifest['files']
    require(isinstance(entries, list) and len(entries) == 15, 'Expected canonical 15-fixture manifest')
    result = {e['id']: e for e in entries}
    require(len(result) == len(entries), 'Duplicate fixture ID')
    return sha(path), result


def validate_evidence(data, manifest_hash, entries, api=None):
    require(isinstance(data, dict), 'Evidence must be an object')
    require(data.get('candidate') == CANDIDATE, 'Wrong candidate')
    require(type(data.get('api')) is int and data['api'] in (26, 27, 36), 'Invalid API')
    require(api is None or api == data['api'], 'Connected device API mismatch')
    require(type(data.get('pid')) is int and data['pid'] > 0, 'Invalid recorded PID')
    require(isinstance(data.get('case'), str) and re.fullmatch(r'[A-Za-z0-9]+', data['case']), 'Invalid case')
    require(data.get('cleanup') == 'GENERATED_OUTPUTS_RETAINED_BY_OWNER_REQUEST', 'Unrecognized retention status')
    require(isinstance(data.get('measurements'), list) and len(data['measurements']) <= 10000, 'Missing measurements')
    require(data.get('manifest_sha256') == manifest_hash, 'Fixture manifest mismatch')
    outputs = data.get('outputs')
    require(isinstance(outputs, list) and len(outputs) <= 64, 'Invalid output inventory')
    seen = set()
    total = 0
    for recipe in outputs:
        require(isinstance(recipe, dict), 'Invalid recipe')
        name = recipe.get('file')
        require(isinstance(name, str) and re.fullmatch(r'result-[1-9][0-9]*\.pdf', name), 'Unsafe PDF basename')
        require(name not in seen, 'Duplicate output recipe'); seen.add(name)
        require(type(recipe.get('bytes')) is int and 0 < recipe['bytes'] <= OUTPUT_LIMIT, 'Invalid output size')
        total += recipe['bytes']; require(total <= TOTAL_LIMIT, 'Namespace byte limit')
        require(isinstance(recipe.get('sha256'), str) and re.fullmatch('[0-9a-f]{64}', recipe['sha256']), 'Invalid output digest')
        entry = entries.get(recipe.get('fixture'))
        require(entry is not None and entry['expected_status'] == 'valid', 'Unrecognized/unsupported fixture')
        require(recipe.get('source_sha256') == entry['sha256'], 'Source hash mismatch')
        indices = recipe.get('indices')
        require(isinstance(indices, list) and indices and all(type(i) is int and 0 <= i < entry['expected_pages'] for i in indices), 'Invalid page selection')
        require(len(set(indices)) == len(indices) and type(recipe.get('pages')) is int and recipe['pages'] == len(indices), 'Duplicate indices/page count')
        require(type(recipe.get('extra_quarter_turns')) is int and recipe['extra_quarter_turns'] in range(4), 'Invalid rotation')
        require(recipe.get('approach') in ('WORKING_COPY', 'STOCK_IMPORT'), 'Unknown strategy')
    return outputs


def transfer(command, destination, limit, timeout=120):
    """Binary pipe to exclusive file, bounded stderr, process lifetime and streaming size."""
    with destination.open('xb') as output:
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, shell=False)
        failures, errors = [], bytearray()
        def drain():
            try:
                count = 0
                while True:
                    chunk = process.stdout.read(65536)
                    if not chunk: break
                    count += len(chunk)
                    if count > limit: raise Blocked('Transfer exceeds byte limit')
                    output.write(chunk)
            except BaseException as failure:
                failures.append(failure); process.kill()
        def diagnostics():
            try:
                while True:
                    chunk = process.stderr.read(4096)
                    if not chunk: break
                    if len(errors) + len(chunk) > 16384:
                        raise Blocked('ADB diagnostic limit exceeded')
                    errors.extend(chunk)
            except BaseException as failure:
                failures.append(failure); process.kill()
        threads = [threading.Thread(target=drain, daemon=True), threading.Thread(target=diagnostics, daemon=True)]
        for thread in threads: thread.start()
        try:
            process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            process.kill(); process.wait(); failures.append(Blocked('ADB timeout'))
        finally:
            for thread in threads: thread.join(timeout=5)
            if any(thread.is_alive() for thread in threads):
                failures.append(Blocked('ADB pipe shutdown unconfirmed'))
            else:
                process.stdout.close(); process.stderr.close()
        if failures: raise Blocked(str(failures[0]))
        require(process.returncode == 0, f'ADB transfer exit {process.returncode}; run-as/device read unavailable')
        require(not errors, 'ADB emitted diagnostics; transfer is uncertain')


def collect(args):
    require(re.fullmatch(r'emulator-[0-9]+', args.serial or ''), 'Explicit emulator serial required')
    require(re.fullmatch('[0-9a-f]{40}', args.commit or ''), 'Full owner-declared Git SHA required')
    names = [namespace(n) for n in args.namespace]
    require(len(set(names)) == len(names) and 0 < len(names) <= 64, 'Duplicate/excess namespaces')
    directory = safe_path(Path(args.output).absolute().parent, Path(args.output).name)
    require(not directory.exists(), 'Collection directory must be new')
    manifest_hash, entries = canonical(REPO / 'test-fixtures')
    # Only metadata query; all private-file reads below use fixed run-as/cat paths.
    result = subprocess.run([args.adb, '-s', args.serial, 'shell', 'getprop', 'ro.build.version.sdk'],
                            capture_output=True, timeout=30, check=False, shell=False)
    require(result.returncode == 0 and not result.stderr and re.fullmatch(rb'[0-9]+\s*', result.stdout), 'Unverifiable connected API')
    api = int(result.stdout)
    directory.mkdir(parents=True, exist_ok=False)
    report = dict(schema=1, status='BLOCKED', declared_commit=args.commit,
                  provenance='Owner-declared SHA; not device execution attestation', serial=args.serial,
                  api=api, candidate=CANDIDATE, manifest_sha256=manifest_hash,
                  retrieved_at=datetime.now(timezone.utc).isoformat(), namespaces=[])
    total_received = 0
    try:
        for name in names:
            local = directory / name; local.mkdir()
            def read(file, limit):
                transfer([args.adb, '-s', args.serial, 'exec-out', 'run-as', APP, 'cat',
                          f'no_backup/{name}/{file}'], local / file, limit)
            read('evidence.json', JSON_LIMIT)
            data = load_json(local / 'evidence.json')
            outputs = validate_evidence(data, manifest_hash, entries, api)
            record = dict(namespace=name, evidence_sha256=sha(local / 'evidence.json'), files=[],
                          classification='RECIPES_COLLECTED' if outputs else 'NO_POSITIVE_OUTPUTS_UNVERIFIED')
            report['namespaces'].append(record)
            for recipe in outputs:
                total_received += recipe['bytes']
                require(total_received <= TOTAL_LIMIT, 'Collection aggregate byte limit')
                read(recipe['file'], recipe['bytes'])
                file = local / recipe['file']
                require(file.stat().st_size == recipe['bytes'] and sha(file) == recipe['sha256'], 'Incomplete transfer/hash mismatch')
                record['files'].append(dict(file=recipe['file'], bytes=file.stat().st_size, sha256=sha(file)))
        report['status'] = 'COLLECTED'
    except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError) as failure:
        report['error'] = f'{type(failure).__name__}: {failure}'
    with (directory / 'collection-manifest.json').open('x', encoding='utf-8') as stream:
        json.dump(report, stream, indent=2)
    return 0 if report['status'] == 'COLLECTED' else 2


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--namespace', action='append', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--commit', required=True)
    parser.add_argument('--adb', default='adb')
    args = parser.parse_args(argv)
    try: return collect(args)
    except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError) as failure:
        print(f'BLOCKED: {failure}'); return 2


if __name__ == '__main__':
    raise SystemExit(main())
