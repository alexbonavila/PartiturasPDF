#!/usr/bin/env python3
"""Verify Policy B in XML, the merged manifest, and optionally compiled APK resources."""
import argparse
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
APP_PREFERENCES = {"domain": "file", "path": "datastore/app_preferences.preferences_pb"}


def exact_file(section):
    children = list(section)
    if section.attrib or len(children) != 1:
        raise ValueError("Each backup transport must have exactly one file include")
    include = children[0]
    if include.tag != "include" or include.attrib != APP_PREFERENCES or list(include):
        raise ValueError("Only the exact non-sensitive DataStore file may be included")


def verify_rules(legacy, extraction):
    if legacy.tag != "full-backup-content":
        raise ValueError("Invalid legacy backup root")
    exact_file(legacy)
    sections = list(extraction)
    if (extraction.tag != "data-extraction-rules" or extraction.attrib or len(sections) != 2
            or {s.tag for s in sections} != {"cloud-backup", "device-transfer"}):
        raise ValueError("Explicit cloud and device-transfer restrictions are required")
    for section in sections:
        exact_file(section)


def verify_manifest(root, backup="@xml/backup_rules", extraction="@xml/data_extraction_rules"):
    apps = root.findall("application")
    if root.tag != "manifest" or len(apps) != 1:
        raise ValueError("Expected one merged application manifest")
    app = apps[0]
    expected = {"allowBackup": "true", "fullBackupContent": backup, "dataExtractionRules": extraction}
    if any(app.get(ANDROID + name) != value for name, value in expected.items()):
        raise ValueError("Backup must stay enabled and reference both restrictive XML resources")
    if app.get(ANDROID + "backupAgent") is not None:
        raise ValueError("A custom backup agent needs a separate policy review")


def xmltree(dump):
    """Decode aapt2's element/attribute tree; retain every node for fail-closed checks."""
    stack = []
    root = None
    for line in dump.splitlines():
        element = re.match(r"(\s*)E: ([\w-]+) \(line=\d+\)", line)
        if element:
            depth = len(element[1])
            while stack and stack[-1][0] >= depth:
                stack.pop()
            node = ET.Element(element[2])
            if stack:
                stack[-1][1].append(node)
            elif root is None:
                root = node
            else:
                raise ValueError("Multiple compiled XML roots")
            stack.append((depth, node))
        attribute = re.match(r"\s*A: ([^=]+?)=(.*)", line)
        if attribute:
            if not stack:
                raise ValueError("Compiled attribute without an element")
            name = re.sub(r"\(0x[0-9a-f]+\)$", "", attribute[1])
            if name.startswith("http://schemas.android.com/apk/res/android:"):
                name = ANDROID + name.rsplit(":", 1)[1]
            value = attribute[2]
            quoted = re.match(r'"([^"\n]*)"', value)
            stack[-1][1].set(name, quoted[1] if quoted else value)
    if root is None:
        raise ValueError("Missing compiled XML tree")
    return root


def verify_apk(apk, aapt2):
    def dump(*arguments):
        return subprocess.check_output([str(aapt2), "dump", *arguments], text=True)
    resources = dump("resources", str(apk))
    identifiers = {}
    for name in ["backup_rules", "data_extraction_rules"]:
        matches = re.findall(r"resource (0x[0-9a-f]+) xml/" + name + r"\b", resources)
        if len(matches) != 1:
            raise ValueError("Missing/ambiguous compiled backup resource")
        identifiers[name] = "@" + matches[0]
    def tree(path):
        return xmltree(dump("xmltree", str(apk), "--file", path))
    verify_manifest(tree("AndroidManifest.xml"), identifiers["backup_rules"], identifiers["data_extraction_rules"])
    verify_rules(tree("res/xml/backup_rules.xml"), tree("res/xml/data_extraction_rules.xml"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True, help="Final merged manifest")
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--aapt2", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    verify_rules(ET.parse(root / "app/src/main/res/xml/backup_rules.xml").getroot(),
                 ET.parse(root / "app/src/main/res/xml/data_extraction_rules.xml").getroot())
    verify_manifest(ET.parse(args.manifest).getroot())
    if bool(args.apk) != bool(args.aapt2):
        parser.error("--apk and --aapt2 must be supplied together")
    if args.apk:
        verify_apk(args.apk, args.aapt2)
    print("PASS Policy B exact-file allowlists and merged manifest" + ("; compiled APK verified" if args.apk else ""))


if __name__ == "__main__":
    main()
