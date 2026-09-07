#!/usr/bin/env python3
# Structural audit for TIARCA locale resources.
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/res"
LOCALES = ["it", "de", "es", "fi", "fr", "pl", "pt-rBR", "ro"]

PLACEHOLDER_RE = re.compile(r'%(?:([1-9]\d*)\$)?([sd])')


def parse_file(path):
    if not path.exists():
        return {}, []
    tree = ET.parse(path)
    root = tree.getroot()
    items = {}
    duplicates = []
    for child in root:
        name = child.attrib.get("name")
        if not name or child.tag not in {"string", "plurals", "string-array"}:
            continue
        key = (child.tag, name)
        if key in items:
            duplicates.append(key)
        items[key] = child
    return items, duplicates


def text_of(elem):
    if elem.tag == "string":
        return "".join(elem.itertext())
    if elem.tag == "plurals":
        return {x.attrib.get("quantity"): "".join(x.itertext()) for x in elem.findall("item")}
    if elem.tag == "string-array":
        return ["".join(x.itertext()) for x in elem.findall("item")]
    return ""


def placeholders(text):
    return [(m.group(1) or "", m.group(2)) for m in PLACEHOLDER_RE.finditer(text)]


def compare_placeholders(base_elem, locale_elem):
    if base_elem.tag == "string":
        bp = placeholders(text_of(base_elem))
        lp = placeholders(text_of(locale_elem))
        return None if bp == lp else (bp, lp)

    if base_elem.tag == "string-array":
        bvals = text_of(base_elem)
        lvals = text_of(locale_elem)
        if len(bvals) != len(lvals):
            return ("array_length", len(bvals), len(lvals))
        bad = []
        for i, (b, l) in enumerate(zip(bvals, lvals)):
            bp = placeholders(b)
            lp = placeholders(l)
            if bp != lp:
                bad.append((i, bp, lp))
        return None if not bad else bad

    # Android locales may legitimately define additional plural categories
    # (for example Polish few/many or Romanian few). Compare each locale
    # category against the same English category when present, otherwise
    # against English "other". This validates formatting without forcing
    # English plural grammar onto other languages.
    bvals = text_of(base_elem)
    lvals = text_of(locale_elem)
    bad = []
    for quantity, ltext in lvals.items():
        btext = bvals.get(quantity, bvals.get("other"))
        if btext is None:
            bad.append((quantity, "missing_base_fallback", placeholders(ltext)))
            continue
        bp = placeholders(btext)
        lp = placeholders(ltext)
        if bp != lp:
            bad.append((quantity, bp, lp))
    for quantity in bvals:
        if quantity not in lvals and quantity not in {"zero", "two", "few", "many"}:
            bad.append((quantity, "missing_locale_quantity", placeholders(bvals[quantity])))
    return None if not bad else bad


def load_locale(dirname):
    combined = {}
    dups = []
    for filename in ("strings.xml", "strings_tiarca_sync.xml"):
        data, inner_dups = parse_file(ROOT / dirname / filename)
        dups.extend((filename, k) for k in inner_dups)
        for key, elem in data.items():
            if key in combined:
                dups.append(("cross-file", key))
            combined[key] = elem
    return combined, dups


def main():
    base, base_dups = load_locale("values")
    if base_dups:
        print("DEFAULT_DUPLICATES")
        for item in base_dups:
            print("  ", item)

    total_errors = 0
    for locale in LOCALES:
        dirname = f"values-{locale}"
        data, dups = load_locale(dirname)
        missing = sorted(set(base) - set(data))
        extra = sorted(set(data) - set(base))
        mismatches = []

        for key in sorted(set(base) & set(data)):
            b = base[key]
            l = data[key]
            if b.tag != l.tag:
                mismatches.append((key, "type", b.tag, l.tag))
                continue
            diff = compare_placeholders(b, l)
            if diff is not None:
                mismatches.append((key, "placeholders", diff))

        print(f"\n[{locale}]")
        print(f"resources={len(data)} missing={len(missing)} extra={len(extra)} duplicates={len(dups)} placeholder_mismatches={len(mismatches)}")
        if missing:
            print("MISSING")
            for k in missing:
                print("  ", k)
        if extra:
            print("EXTRA")
            for k in extra:
                print("  ", k)
        if dups:
            print("DUPLICATES")
            for item in dups:
                print("  ", item)
        if mismatches:
            print("PLACEHOLDER_MISMATCHES")
            for item in mismatches:
                print("  ", item)

        total_errors += len(dups) + len(mismatches)

    if total_errors:
        raise SystemExit(f"Structural translation errors: {total_errors}")


if __name__ == "__main__":
    main()
