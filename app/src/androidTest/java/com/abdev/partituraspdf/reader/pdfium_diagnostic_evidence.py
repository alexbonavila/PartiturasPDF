"""Fail-closed validation of controlled native diagnostics, never production acceptance."""
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

APP = "com.abdev.partituraspdf"
PROCESS_CLASS = APP + ".reader.ReaderPdfiumProcessStateTest"
ANNOTATION = APP + ".pdf.android.FreshPdfiumProcess"
DIFFERENTIAL = APP + ".reader.ReaderNativeIntegrationTest#a0_sameCanonicalBytesOpenDirectThroughEngineAndSaf"
FIXTURE_CLASS = APP + ".pdf.android.AndroidPdfFixturesInstrumentedTest"
CLEAN = [DIFFERENTIAL,
         FIXTURE_CLASS + "#y_deliberatelyCorruptedFixtureIsRejectedAndDoesNotLeakDescriptor",
         FIXTURE_CLASS + "#z_encryptedFixtureRequiresPasswordAndDoesNotLeakDescriptor"]
ISOLATED = {APP + ".pdf.android.isolated.IsolatedPdfReadEngineTest": 19,
            APP + ".pdf.android.isolated.PdfWorkerProtocolTest": 5}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def invocation_plan(scenarios):
    methods = list(scenarios)
    experiments = methods[:2] + [item for method in methods[2:] if method != "d_freshProcessRecoveryDirect"
                                for item in (method, "d_freshProcessRecoveryDirect", "k_cleanAdapterRepeatedValidOpens")]
    return CLEAN + [PROCESS_CLASS + "#" + method for method in experiments]


def test_inventory(repo, scenarios):
    """Exact source inventory plus annotation audit; actual JUnit IDs must match it later."""
    all_tests, annotated = set(), set()
    for path in (Path(repo) / "app/src/androidTest/java").rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        methods = re.findall(r"@Test\s+fun\s+(\w+)\s*\(", text)
        require(len(methods) == len(re.findall(r"^\s*@Test\b", text, re.M)), f"Unaccounted test declaration: {path}")
        if not methods:
            continue
        package = re.search(r"^package (\S+)", text, re.M).group(1)
        cls = re.search(r"^class (\w+)", text, re.M).group(1)
        ids = {package + "." + cls + "#" + method for method in methods}
        require(len(ids) == len(methods) and not ids & all_tests, "Duplicate source test identity")
        all_tests |= ids
        marked = re.findall(r"@FreshPdfiumProcess\s+@Test\s+fun\s+(\w+)\s*\(", text)
        annotated |= {package + "." + cls + "#" + method for method in marked}
    assigned = set(CLEAN) | {PROCESS_CLASS + "#" + name for name in scenarios}
    require(annotated == assigned, "Fresh-process annotation/mandatory assignment mismatch")
    require(assigned <= all_tests, "Missing mandatory diagnostic test")
    standard = all_tests - assigned
    require(all(sum(name.startswith(cls + "#") for name in standard) == count for cls, count in ISOLATED.items()),
            "All 24 production isolation tests must remain standard acceptance")
    return {"standard": sorted(standard), "assigned": sorted(assigned), "total": len(all_tests)}


def verify_standard(directory, inventory, started_ms=None):
    reports = sorted(Path(directory).rglob("*.xml"))
    require(bool(reports), "Missing standard JUnit XML")
    observed = []
    for path in reports:
        if started_ms is not None:
            require(path.stat().st_mtime_ns // 1_000_000 >= started_ms, "Stale standard JUnit XML")
        root = ET.parse(path).getroot()
        require(root.tag in {"testsuite", "testsuites"}, "Unknown JUnit report")
        for suite in root.iter("testsuite"):
            require(suite.get("tests") is None or int(suite.get("tests")) == len(list(suite.iter("testcase"))), "Incorrect JUnit test total")
            require(all(int(suite.get(k, "0")) == 0 for k in ["failures", "errors", "skipped", "disabled"]),
                    "Standard acceptance contains failures/errors/skips")
        for case in root.iter("testcase"):
            require(not any(case.find(k) is not None for k in ["failure", "error", "skipped"]), "Nonpassing standard test")
            observed.append(case.get("classname", "") + "#" + case.get("name", ""))
    require(len(observed) == len(set(observed)), "Duplicate standard JUnit test")
    require(set(observed) == set(inventory["standard"]), "Missing/unexpected standard tests (including all 24 isolated tests)")
    return {"executed": len(observed), "passed": len(observed), "failed": 0, "errored": 0, "skipped": 0}


def completion(output, selected, token, before, after, previous, api):
    output = output.replace("\r\n", "\n")
    cls, method = selected.split("#")
    starts = re.findall(r"^INSTRUMENTATION_STATUS_CODE: 1\s*$", output, re.M)
    ends = re.findall(r"^INSTRUMENTATION_STATUS_CODE: (0|-[1-4])\s*$", output, re.M)
    require(len(starts) == len(ends) == 1, "Missing/duplicate executed JUnit completion")
    for key, expected in [("class", cls), ("test", method), ("numtests", "1")]:
        values = re.findall(r"^INSTRUMENTATION_STATUS: " + key + r"=(.+)$", output, re.M)
        require(bool(values) and set(values) == {expected}, "Incorrect selected JUnit " + key)
    require(re.findall(r"^INSTRUMENTATION_CODE: (-?\d+)\s*$", output, re.M) == ["-1"], "Instrumentation did not complete")
    require("INSTRUMENTATION_FAILED" not in output and "INSTRUMENTATION_RESULT: shortMsg=" not in output, "Instrumentation crashed")
    require(ends[0] not in {"-3", "-4"}, "Skipped/assumed diagnostic")
    data = re.findall(r"^INSTRUMENTATION_RESULT: pdfiumProcessIdentity=(.+)$", output, re.M)
    require(len(data) == 1, "Missing/duplicate process identity")
    identity = json.loads(data[0])
    pid, started = identity["pid"], identity["processStartElapsedMs"]
    require(type(pid) is int and type(started) is int and pid > 0 and started > 0, "Invalid process identity")
    require((pid, started) not in previous and before - 20 <= started <= after + 20, "Old/reused process identity")
    require(identity["api"] == api and identity["class"] == cls and identity["test"] == method and identity["invocation"] == token,
            "Wrong API/selected test/invocation identity")
    stacks = re.findall(r"^INSTRUMENTATION_STATUS: stack=(.*?)(?=^INSTRUMENTATION_STATUS: |^INSTRUMENTATION_STATUS_CODE:)", output, re.M | re.S)
    require((ends[0] == "0" and not stacks) or (ends[0] != "0" and len(stacks) == 1 and stacks[0].strip()), "Missing/unexpected JUnit failure stack")
    return {"status": "PASS" if ends[0] == "0" else "FAIL", "completionCode": int(ends[0]),
            "stack": stacks[0].strip() if stacks else "", "processIdentity": [pid, started], "processIsolationVerified": True}


def check_attempt(row, fixture, engine):
    require({"exception", "cleanupException", "ownerCloseSucceeded"} <= row.keys(), "Missing native failure/cleanup fields")
    require(type(row.get("resourceCloses")) is int, "Invalid native close count")
    require(row.get("sha256Verified") is True and row.get("sha256") == fixture["sha256"] and row.get("size") == fixture["bytes"], "Wrong/unverified fixture bytes")
    require(row.get("engine") == engine and row.get("descriptorValid") is True and row.get("descriptorBytes") == fixture["bytes"], "Wrong native engine/descriptor")
    require(row.get("descriptorClosed") is True and row.get("cleanupException") is None, "Native cleanup failed")
    require(row.get("resourceCloses") == (1 if engine == "AndroidPdfReadEngine" else 0), "Incorrect source close count")
    require(row.get("ownership") == ("TRANSFERRED" if engine == "AndroidPdfReadEngine" else "direct"), "Incorrect source ownership")


def rendered(row, fixture):
    require(row.get("rendered") is True and row.get("constructed") is True and row.get("pageCountRead") is True,
            "Missing successful native render milestones")
    require(row.get("pageCount") == fixture["expected_pages"] and row.get("exception") is None,
            "Invalid native page count or unexpected exception")
    require(row.get("ownerCloseAttempted") is True and row.get("ownerCloseSucceeded") is True, "Successful native owner not released")
    if row["engine"] == "AndroidPdfReadEngine":
        require(row.get("typedOutcome") == "Success(open and render)", "Fake native success")


def rejected(row, negative):
    require(row.get("rendered") is False and row.get("constructed") is False and row.get("pageCountRead") is False,
            "Failure did not occur in native opening")
    require(row.get("operation") in {"engine.open", "PdfRenderer constructor"}, "Unexpected failure operation")
    require(row.get("ownerCloseAttempted") is False and row.get("ownerCloseSucceeded") is None, "Unexpected failure ownership")
    if row["engine"] == "PdfRenderer":
        require(row.get("exception") in {"java.io.IOException", "java.lang.SecurityException"}, "Unexpected native exception")
        require(row.get("typedOutcome") == "not applicable to direct PdfRenderer", "Unexpected direct result")
    else:
        allowed = {"Failure(error=INVALID_DOCUMENT)"}
        if negative == "14":
            allowed.add("Failure(error=DOCUMENT_FORMAT)")
        require(row.get("exception") is None and row.get("typedOutcome") in allowed, "Unexpected native adapter failure")


def classify(runs, api, scenarios, fixtures):
    plan = invocation_plan(scenarios)
    require([r.get("selected") for r in runs] == plan, "Missing/extra/reordered mandatory diagnostic invocation")
    identities = set()
    for entry in runs:
        identity = tuple(entry["processIdentity"])
        require(entry.get("processIsolationVerified") is True and identity not in identities, "Unverified/reused diagnostic process")
        require(entry.get("processes", {}).get("afterStop") == {APP: [], APP + ".test": []}, "Previous process did not terminate")
        identities.add(identity)
        require(entry["status"] in {"PASS", "FAIL"}, "Incomplete diagnostic")
        if entry["selected"] in CLEAN:
            require(entry["status"] == "PASS" and entry["completionCode"] == 0, "Clean native acceptance failed")
            entry["classification"] = "CLEAN_NATIVE_ACCEPTANCE_PASSED"
            continue
        method = entry["selected"].split("#")[1]
        rows = entry["attempts"]
        require(len(rows) == len(scenarios[method]), "Incomplete native sequence")
        engine = "AndroidPdfReadEngine" if method.endswith("Adapter") or method.startswith("k_") else "PdfRenderer"
        for index, row in enumerate(rows):
            require(row.get("fixture") == scenarios[method][index] and row.get("sequence") == index + 1,
                    "Wrong native fixture/order")
            require((row.get("pid"), row.get("processStartElapsedMs")) == identity and row.get("api") == api and
                    row.get("class") == PROCESS_CLASS and row.get("test") == method and row.get("invocation") == entry["invocation"],
                    "Wrong native operation identity")
            check_attempt(row, fixtures[row["fixture"]], engine)
        negative = any(row["fixture"] in {"14", "15"} for row in rows)
        for index, row in enumerate(rows):
            if row["fixture"] in {"14", "15"}:
                rejected(row, row["fixture"])
            elif index != len(rows) - 1 or not negative or entry["status"] == "PASS":
                rendered(row, fixtures[row["fixture"]])
        if entry["status"] == "PASS":
            require(entry["completionCode"] == 0, "Fake JUnit PASS")
            entry["classification"] = "NATIVE_REOPEN_SUCCEEDED" if negative else "CLEAN_NATIVE_ACCEPTANCE_PASSED"
        else:
            require(negative and api in {26, 27} and entry["completionCode"] == -2, "Unexpected platform/test failure")
            require(entry["stack"].startswith("java.lang.AssertionError: Valid fixture 01 did not open/render; inspect ReaderPdfiumProcess PID/sequence:"),
                    "Unrelated assertion is not a known Pdfium defect")
            rejected(rows[-1], rows[-2]["fixture"])
            entry["classification"] = "KNOWN_PLATFORM_DEFECT_REPRODUCED"
    # Negative invocations must be followed by independently successful DIRECT and ADAPTER recovery.
    for index, entry in enumerate(runs):
        if entry.get("classification") in {"KNOWN_PLATFORM_DEFECT_REPRODUCED", "NATIVE_REOPEN_SUCCEEDED"}:
            recovery = runs[index + 1:index + 3]
            require(len(recovery) == 2 and all(r["classification"] == "CLEAN_NATIVE_ACCEPTANCE_PASSED" for r in recovery), "Missing successful fresh-process recovery")
    return {"status": "DIAGNOSTICS_VALIDATED", "invocations": len(runs), "uniqueAssignedTests": len(set(plan)),
            "rawPassed": sum(r["status"] == "PASS" for r in runs), "rawFailed": sum(r["status"] == "FAIL" for r in runs),
            "knownPlatformDefects": sum(r["classification"] == "KNOWN_PLATFORM_DEFECT_REPRODUCED" for r in runs)}


def write_junit(path, selected, entry, output):
    """Lossless failure status derived from actual instrumentation; never relabel raw FAIL as PASS."""
    cls, method = selected.split("#")
    error = entry["status"] == "FAIL" and entry["completionCode"] == -1
    root = ET.Element("testsuite", name=cls, tests="1", failures="1" if entry["status"] == "FAIL" and not error else "0",
                      errors="1" if error else "0", skipped="0")
    case = ET.SubElement(root, "testcase", classname=cls, name=method)
    if entry["status"] == "FAIL":
        ET.SubElement(case, "error" if error else "failure", message="Raw Android instrumentation failure").text = entry["stack"]
    ET.SubElement(root, "system-out").text = output
    ET.ElementTree(root).write(path, encoding="utf-8", xml_declaration=True)
