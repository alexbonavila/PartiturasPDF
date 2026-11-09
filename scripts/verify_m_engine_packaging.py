"""Owner-run ZIP/DEX assertions for Block A. Not dependency/version/security attestation."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

NOTICES = {'PDFBOX-LICENSE.txt','PDFBOX-NOTICE.txt','BOUNCY-CASTLE-LICENSE.html','M-ENGINE-ATTRIBUTION.txt'}
LIMIT = 256 * 1024 * 1024


def inspect(apk, test=False):
    with zipfile.ZipFile(apk) as archive:
        names=archive.namelist()
        if len(set(names))!=len(names): raise ValueError('Duplicate ZIP entries')
        dex=[n for n in names if n.endswith('.dex')]
        if not dex: raise ValueError('Missing DEX')
        expected=[b'Lcom/tom_roush/pdfbox/',b'Lorg/bouncycastle/',b'Lcom/abdev/partituraspdf/pdf/editing/ProductionPdfAdapters;']
        found=[False]*len(expected)
        for name in dex:
            info=archive.getinfo(name)
            if info.file_size>LIMIT: raise ValueError('DEX inspection bound')
            data=archive.read(name)
            for i,marker in enumerate(expected):found[i]=found[i] or marker in data
            if not test and any(marker in data for marker in [b'Lcom/abdev/partituraspdf/pdf/engine/spike/',b'Lcom/abdev/partituraspdf/storage/transaction/testprovider/']):
                raise ValueError('Test code present in main APK')
        if not test:
            if not all(found): raise ValueError('Production engine/dependencies absent or renamed; requires APK Analyzer review')
            if any(n.startswith('assets/pdf/') or n=='assets/manifest.json' for n in names): raise ValueError('Canonical test assets in main APK')
            if not all('assets/third_party/'+n in names for n in NOTICES): raise ValueError('Missing attribution assets')
        digest=hashlib.sha256()
        with Path(apk).open('rb') as source:
            for block in iter(lambda:source.read(64*1024),b''):digest.update(block)
        return dict(file=Path(apk).name,bytes=Path(apk).stat().st_size,
                    sha256=digest.hexdigest(),
                    status='PASS',scope='Limited ZIP/DEX marker assertions; exact versions/merged manifest/security NOT VERIFIED',
                    native=[n for n in names if n.startswith('lib/')],markers=found)


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--debug',required=True);parser.add_argument('--release',required=True);parser.add_argument('--test',required=True)
    args=parser.parse_args(argv)
    try:
        print(json.dumps([inspect(args.debug),inspect(args.release),inspect(args.test,True)],indent=2));return 0
    except (OSError,ValueError,zipfile.BadZipFile) as failure:
        print(json.dumps(dict(status='BLOCKED',reason=str(failure))));return 1


if __name__=='__main__':raise SystemExit(main())
