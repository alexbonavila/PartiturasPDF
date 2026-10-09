#!/usr/bin/env python3
"""Validate en/ca/es resource parity and Java Formatter argument contracts."""

from __future__ import annotations

import argparse
from collections import Counter
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


TOKEN = re.compile(
    r"%(?:(?P<index>[1-9]\d*)\$)?(?P<flags>[-#+ 0,(<]*)"
    r"\d*(?:\.\d+)?(?P<date>[tT])?(?P<conversion>[a-zA-Z%n])"
)
DATE_CONVERSIONS = set("HIklMSLNpzZsQBbhAaCYyjmdeRTrDFc")
LOCALES = ("values", "values-ca", "values-es")


def arguments(value: str) -> Counter:
    """Preserve indices, conversions and multiplicity while allowing reordering."""
    contract = Counter()
    implicit = 0
    previous = None
    position = 0
    while True:
        position = value.find("%", position)
        if position < 0:
            break
        match = TOKEN.match(value, position)
        if match is None:
            raise ValueError("malformed format token; escape literal percent as %%")
        position = match.end()
        conversion = match["conversion"]
        if not match["date"] and conversion in ("%", "n"):
            continue
        if match["date"]:
            if conversion not in DATE_CONVERSIONS:
                raise ValueError(f"invalid date conversion: {match.group()}")
            kind = "date:" + conversion
        else:
            if conversion not in "bBhHsScCdoxXeEfgGaA":
                raise ValueError(f"invalid conversion: {match.group()}")
            kind = conversion.lower()
        if "<" in match["flags"]:
            if previous is None or match["index"]:
                raise ValueError("relative argument requires a preceding argument")
            index = previous
        elif match["index"]:
            index = int(match["index"])
        else:
            implicit += 1
            index = implicit
        previous = index
        contract[index, kind] += 1
    return contract


def read_resources(directory: Path) -> dict:
    if not (directory / "strings.xml").is_file():
        raise ValueError(f"missing required file: {directory / 'strings.xml'}")
    resources = {}
    for path in sorted(directory.glob("*.xml")):
        root = ET.parse(path).getroot()
        if root.tag != "resources":
            raise ValueError(f"{path}: expected resources root")
        for element in root:
            if element.tag not in ("string", "plurals", "string-array"):
                continue
            name = element.get("name")
            if not name or name in resources:
                raise ValueError(f"{path}: missing or duplicate resource name {name!r}")
            # All user-facing strings require translations, including app_name.
            if element.get("translatable") == "false":
                raise ValueError(f"{path}: {name}: do not exempt user-facing translations")
            formatted = element.get("formatted", "true")
            if formatted not in ("true", "false"):
                raise ValueError(f"{path}: {name}: invalid formatted attribute")
            entries = {}
            if element.tag == "string":
                entries["value"] = "".join(element.itertext())
            else:
                for index, item in enumerate(element):
                    if item.tag != "item":
                        raise ValueError(f"{path}: {name}: expected item")
                    key = item.get("quantity") if element.tag == "plurals" else str(index)
                    if key is None or key in entries:
                        raise ValueError(f"{path}: {name}: invalid or duplicate item")
                    if element.tag == "plurals" and key not in (
                        "zero", "one", "two", "few", "many", "other"
                    ):
                        raise ValueError(f"{path}: {name}: invalid plural quantity {key}")
                    entries[key] = "".join(item.itertext())
                if not entries or (element.tag == "plurals" and "other" not in entries):
                    raise ValueError(f"{path}: {name}: empty resource or missing other plural")
            contracts = {}
            for key, value in entries.items():
                if not value.strip():
                    raise ValueError(f"{path}: {name}: empty translation")
                # Resource aliases would require resolution; fail instead of claiming parity.
                if value.strip().startswith("@"):
                    raise ValueError(f"{path}: {name}: resource aliases are not supported")
                contracts[key] = arguments(value) if formatted == "true" else Counter()
            resources[name] = (element.tag, formatted, contracts)
    if not resources:
        raise ValueError(f"{directory}: no localized resources found")
    return resources


def validate(root: Path) -> list[str]:
    errors = []
    bundles = {}
    for locale in LOCALES:
        try:
            bundles[locale] = read_resources(root / locale)
        except (OSError, ET.ParseError, ValueError) as exc:
            errors.append(str(exc))
    base = bundles.get("values")
    if base is None:
        return errors
    for locale in LOCALES[1:]:
        translated = bundles.get(locale)
        if translated is None:
            continue
        for name in sorted(base.keys() - translated.keys()):
            errors.append(f"{locale}: missing translation: {name}")
        for name in sorted(translated.keys() - base.keys()):
            errors.append(f"{locale}: resource absent from English fallback: {name}")
        for name in sorted(base.keys() & translated.keys()):
            tag, formatted, contracts = base[name]
            other_tag, other_formatted, other_contracts = translated[name]
            if (tag, formatted) != (other_tag, other_formatted):
                errors.append(f"{locale}: {name}: type or formatted attribute differs")
                continue
            if tag == "plurals":
                # CLDR categories may differ by locale; all forms use the base other contract.
                expected = contracts["other"]
                if any(contract != expected for contract in contracts.values()):
                    errors.append(f"values: {name}: inconsistent plural format arguments")
                if any(contract != expected for contract in other_contracts.values()):
                    errors.append(f"{locale}: {name}: inconsistent plural format arguments")
            elif contracts != other_contracts:
                errors.append(f"{locale}: {name}: inconsistent items or format arguments")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--res-dir", type=Path, default=Path("app/src/main/res"))
    args = parser.parse_args()
    errors = validate(args.res_dir)
    if errors:
        for error in errors:
            print(f"FAIL {error}", file=sys.stderr)
        return 1
    print("PASS English, Catalan and Spanish resources and format arguments")
    return 0


if __name__ == "__main__":
    sys.exit(main())
