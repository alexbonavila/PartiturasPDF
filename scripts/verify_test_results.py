#!/usr/bin/env python3
"""Reject absent, empty, failed or skipped JUnit XML test evidence."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def verify(directory: Path, required_test: str | None = None) -> int:
    reports = sorted(directory.rglob("*.xml"))
    if not reports:
        raise ValueError(f"no test XML reports in {directory}")
    executed = []
    for report in reports:
        root = ET.parse(report).getroot()
        if root.tag not in ("testsuite", "testsuites"):
            raise ValueError(f"unexpected test report root: {report}")
        for suite in root.iter("testsuite"):
            for attribute in ("failures", "errors", "skipped", "disabled"):
                if int(suite.get(attribute, "0")):
                    raise ValueError(f"{report}: {attribute} is nonzero")
        for case in root.iter("testcase"):
            if any(case.find(tag) is not None for tag in ("failure", "error", "skipped")):
                raise ValueError(f"{report}: failed or skipped test: {case.get('name')}")
            executed.append(f"{case.get('classname')}.{case.get('name')}")
    if not executed:
        raise ValueError("no tests executed")
    if required_test and required_test not in executed:
        raise ValueError(f"required smoke test did not execute: {required_test}")
    return len(executed)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--required-test")
    args = parser.parse_args()
    try:
        count = verify(args.directory, args.required_test)
    except (OSError, ET.ParseError, ValueError) as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        return 1
    print(f"PASS {count} executed test(s); no failures or skips")
    return 0


if __name__ == "__main__":
    sys.exit(main())
