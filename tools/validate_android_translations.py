#!/usr/bin/env python3
"""Validate resource coverage, ordering and printf placeholders for TIARCA translations."""

from __future__ import annotations

import collections
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


PLACEHOLDER_RE = re.compile(r"%(?:\d+\$)?[a-zA-Z]")
RESOURCE_REF_RE = re.compile(r"@[A-Za-z0-9_]+/[A-Za-z0-9_.]+")
QUALIFIERS = ("fi", "de", "fr", "pl", "ro", "pt-rBR", "es")
FORCED = {
    "pref_header_chat": "Chat",
    "theme_category_chat": "Chat",
    "pref_value_default_quit_message": "Quit",
    "pref_value_default_part_message": "Leaving",
}


def entries(path: Path):
    root = ET.parse(path).getroot()
    return [child for child in root if child.tag in ("string", "plurals", "string-array")]


def validate_value(key, source: str, target: str) -> list[str]:
    errors: list[str] = []
    source_args = collections.Counter(PLACEHOLDER_RE.findall(source))
    target_args = collections.Counter(PLACEHOLDER_RE.findall(target))
    if source_args != target_args:
        errors.append(f"{key}: placeholders {source_args} != {target_args}")
    if RESOURCE_REF_RE.findall(source) != RESOURCE_REF_RE.findall(target):
        errors.append(
            f"{key}: resource references {RESOURCE_REF_RE.findall(source)} != "
            f"{RESOURCE_REF_RE.findall(target)}"
        )
    if source.strip() and not target.strip():
        errors.append(f"{key}: non-empty source has an empty translation")
    if target.count("\n") > source.count("\n"):
        errors.append(f"{key}: translation contains an unexpected line break")
    return errors


def validate(base_path: Path, translated_path: Path) -> list[str]:
    base = entries(base_path)
    translated = entries(translated_path)
    errors: list[str] = []
    if [(node.tag, node.attrib.get("name")) for node in base] != [
            (node.tag, node.attrib.get("name")) for node in translated]:
        errors.append("resource keys/order differ from the English base")
        return errors
    for source_node, target_node in zip(base, translated):
        name = source_node.attrib.get("name")
        if source_node.tag == "string":
            source = source_node.text or ""
            target = target_node.text or ""
            errors.extend(validate_value(("string", name), source, target))
            if name in FORCED and target != FORCED[name]:
                errors.append(
                    f"{('string', name)}: expected exact value {FORCED[name]!r}, got {target!r}")
        elif source_node.tag == "string-array":
            source_items = source_node.findall("item")
            target_items = target_node.findall("item")
            if len(source_items) != len(target_items):
                errors.append(f"{('string-array', name)}: item count differs")
                continue
            for index, (source_item, target_item) in enumerate(zip(source_items, target_items)):
                errors.extend(validate_value(
                    ("string-array", name, str(index)), source_item.text or "",
                    target_item.text or ""))
        else:
            source_items = {
                item.attrib.get("quantity"): item.text or ""
                for item in source_node.findall("item")
            }
            target_items = {
                item.attrib.get("quantity"): item.text or ""
                for item in target_node.findall("item")
            }
            if "other" not in target_items:
                errors.append(f"{('plurals', name)}: missing required 'other' quantity")
                continue
            for quantity, target in target_items.items():
                source = source_items.get(quantity, source_items["other"])
                errors.extend(validate_value(("plurals", name, quantity), source, target))
    return errors


def main() -> int:
    res = Path("app/src/main/res")
    base = res / "values" / "strings.xml"
    failed = False
    for qualifier in QUALIFIERS:
        path = res / f"values-{qualifier}" / "strings.xml"
        if not path.exists():
            print(f"{qualifier}: MISSING")
            failed = True
            continue
        errors = validate(base, path)
        if errors:
            failed = True
            print(f"{qualifier}: {len(errors)} error(s)")
            for error in errors[:20]:
                print(f"  {error}")
        else:
            print(f"{qualifier}: OK")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
