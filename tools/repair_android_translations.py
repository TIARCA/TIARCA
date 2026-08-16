#!/usr/bin/env python3
"""Repair empty or placeholder-damaged generated translations one at a time."""

from __future__ import annotations

import collections
import json
import re
import time
import xml.etree.ElementTree as ET
from pathlib import Path

import generate_android_translations as generator


PLACEHOLDER_RE = re.compile(r"%(?:\d+\$)?[a-zA-Z]")
QUALIFIERS = generator.LANGUAGES


def entry_map(path: Path) -> dict[tuple[str, ...], str]:
    root = ET.parse(path).getroot()
    result: dict[tuple[str, ...], str] = {}
    for child in root:
        name = child.attrib.get("name")
        if child.tag == "string" and name:
            result[("string", name)] = child.text or ""
        elif child.tag in ("plurals", "string-array") and name:
            for index, item in enumerate(child.findall("item")):
                discriminator = item.attrib.get("quantity", str(index))
                result[(child.tag, name, discriminator)] = item.text or ""
    return result


MANUAL_RESULTS = {
    ("de", ("string", "about_body")): (
        "ZXPH000QXZ – ZXPH001QXZ ist eine weitere Relay-Chat-AppZXPH002QXZVersion ZXPH003QXZZXPH004QXZZXPH005QXZ"
        "Basierend auf Revolution IRC von Martin Řehák (mcmrarm).\\n\\n"
        "TIARCA-Anpassungen und -Wartung: Community Edition.\\n\\n"
        "Lizenziert unter der GNU General Public License v3. Der zugehörige Quellcode und die Hinweise müssen bei öffentlichen Veröffentlichungen bereitgestellt werden.\\n\\n"
        "Enthält quelloffene Android-Bibliotheken; deren jeweilige Lizenzen bleiben gültig.\\n\\n"
        "Die Medienfreigabe kann den externen Simosnap-Dienst verwenden. Hochgeladene Dateien unterliegen den Verfügbarkeits- und Aufbewahrungsrichtlinien dieses Dienstes.\\n\\n"
        "Dieses Produkt verwendet die TMDB-API, wird jedoch nicht von TMDB unterstützt oder zertifiziert."
    ),
}


def main() -> int:
    res = Path("app/src/main/res")
    cache_dir = Path("tools/.translation-cache")
    base_path = res / "values" / "strings.xml"
    base = entry_map(base_path)

    for qualifier, target_language in QUALIFIERS.items():
        translated_path = res / f"values-{qualifier}" / "strings.xml"
        translated = entry_map(translated_path)
        damaged = []
        for key, source in base.items():
            target = translated.get(key, "")
            if source.strip() and not target.strip():
                damaged.append(key)
            elif collections.Counter(PLACEHOLDER_RE.findall(source)) != collections.Counter(
                PLACEHOLDER_RE.findall(target)
            ):
                damaged.append(key)
            elif target.count("\n") > source.count("\n"):
                damaged.append(key)

        if not damaged:
            continue

        cache_path = cache_dir / f"{target_language}.json"
        cache = json.loads(cache_path.read_text(encoding="utf-8"))
        print(f"{qualifier}: repairing {len(damaged)} entries", flush=True)
        for number, key in enumerate(damaged, 1):
            protected, _ = generator.protect(base[key])
            result = MANUAL_RESULTS.get((qualifier, key))
            if result is None:
                result = generator.translate_request(protected, target_language).strip()
            if not result:
                raise RuntimeError(f"{qualifier}/{key}: translator returned an empty value")
            cache[protected] = result
            print(f"  {number}/{len(damaged)} {'/'.join(key)}", flush=True)
            time.sleep(0.05)
        cache_path.write_text(
            json.dumps(cache, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        generator.generate(base_path, res / f"values-{qualifier}", target_language, cache_dir)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
