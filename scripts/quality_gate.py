#!/usr/bin/env python3
"""Require successful job results; missing/cancelled/skipped checks fail closed."""

import json
import os
import sys

MANDATORY = (
    "build", "lint", "unit-tests", "instrumented-api26", "localization", "pdf-fixtures"
)


def failures(results: dict, compatibility_required: bool = False) -> list[str]:
    required = MANDATORY + (("compatibility",) if compatibility_required else ())
    return [job for job in required if results.get(job, {}).get("result") != "success"]


def main() -> int:
    results = json.loads(os.environ["NEEDS_JSON"])
    compatibility_required = os.environ.get("COMPATIBILITY_REQUIRED") == "true"
    for job in MANDATORY + (("compatibility",) if compatibility_required else ()):
        print(f"{job}: {results.get(job, {}).get('result', 'MISSING')}")
    blocked = failures(results, compatibility_required)
    if blocked:
        print("FAIL quality gate: " + ", ".join(blocked), file=sys.stderr)
        return 1
    print("PASS all mandatory checks succeeded")
    return 0


if __name__ == "__main__":
    sys.exit(main())
