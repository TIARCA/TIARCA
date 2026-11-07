#!/usr/bin/env python3
"""Audit resolved Gradle dependency reports against OSV's Maven advisories.

This is an online known-vulnerability check, not a byte-integrity or provenance check.
Requires Python 3.8+, no external dependencies.
"""
import argparse
import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

API = "https://api.osv.dev/v1/querybatch"
COORD = re.compile(
    r"(?<![\w.-])([A-Za-z][\w.-]+):([\w.-]+):([A-Za-z0-9][\w.+-]*)"
    r"(?:\s+->\s+([A-Za-z0-9][\w.+-]*))?"
)


def parse_reports(root):
    found = {}
    reports = sorted(root.glob("*-runtime.txt"))
    if not reports:
        raise ValueError("No Gradle runtime inventory files found")
    for path in reports:
        data = path.read_text(encoding="utf-8")
        if "BUILD SUCCESSFUL" not in data:
            raise ValueError("Incomplete Gradle report: " + path.name)
        if "FAILED" in data:
            raise ValueError("Failed dependency resolution in " + path.name)
        coords = set()
        for line in data.splitlines():
            for match in COORD.finditer(line):
                group, module, requested, selected = match.groups()
                coords.add((group, module, selected or requested))
        if not coords:
            raise ValueError("No Maven coordinates in " + path.name)
        found[path.name] = coords
    return found


def osv_batch(packages):
    pending = [(p, None) for p in packages]
    hits = {p: set() for p in packages}
    while pending:
        next_pending = []
        for offset in range(0, len(pending), 100):
            batch = pending[offset:offset + 100]
            payload = {"queries": [
                {"version": p[2],
                 "package": {"ecosystem": "Maven", "name": p[0] + ":" + p[1]},
                 **({"page_token": token} if token else {})}
                for p, token in batch
            ]}
            req = urllib.request.Request(
                API,
                data=json.dumps(payload).encode("utf-8"),
                headers={"Content-Type": "application/json", "User-Agent": "TIARCA-phase-d-audit/1"},
                method="POST"
            )
            with urllib.request.urlopen(req, timeout=90) as response:
                body = json.loads(response.read())
            results = body.get("results")
            if not isinstance(results, list) or len(results) != len(batch):
                raise ValueError("Unexpected OSV batch response (must fail closed)")
            for (p, _), result in zip(batch, results):
                for vuln in result.get("vulns", []):
                    identifier = vuln.get("id")
                    if not identifier:
                        raise ValueError("OSV returned a vulnerability without an id")
                    hits[p].add(identifier)
                if result.get("next_page_token"):
                    next_pending.append((p, result["next_page_token"]))
        pending = next_pending
    return hits


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("inventory", type=Path)
    ap.add_argument("--output", type=Path, required=True)
    args = ap.parse_args()
    try:
        reports = parse_reports(args.inventory)
        versions = {(g, m): set() for coords in reports.values() for g, m, _ in coords}
        for coords in reports.values():
            for g, m, v in coords:
                versions[(g, m)].add(v)
        union = sorted(set().union(*reports.values()))
        results = osv_batch(union)
        findings = [
            {"package": g + ":" + m, "version": v,
             "advisory_ids": sorted(results[(g, m, v)]),
             "source_reports": sorted(name for name, coords in reports.items()
                                      if (g, m, v) in coords)}
            for g, m, v in union if results[(g, m, v)]
        ]
        payload = {
            "database": API,
            "ecosystem": "Maven",
            "audited_versions": len(union),
            "source_files": sorted(reports),
            "findings": findings,
            "note": "OSV absence of known advisories is not proof of safety; buildscript and included-build graphs are not included."
        }
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
        print("OSV Maven audit: %d unique resolved versions, %d affected packages" %
              (len(union), len(findings)))
        for finding in findings:
            print("  %s:%s: %s" % (finding["package"], finding["version"],
                                  ", ".join(finding["advisory_ids"])))
        return 1 if findings else 0
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as err:
        print("OSV audit not verified: " + str(err), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
