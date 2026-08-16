#!/usr/bin/env python3
"""Generate reviewable Android string translations without changing language selection."""

from __future__ import annotations

import argparse
import html
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path


LANGUAGES = {
    "fi": "fi",
    "de": "de",
    "fr": "fr",
    "pl": "pl",
    "ro": "ro",
    "pt-rBR": "pt",
    "es": "es",
}

ENTRY_RE = re.compile(
    r"<(?P<tag>string|item)(?![-\w])(?P<attrs>[^>]*)>(?P<text>.*?)</(?P=tag)>", re.DOTALL
)
NAME_RE = re.compile(r'\bname="([^"]+)"')
PROTECTED_RE = re.compile(
    r"@[A-Za-z0-9_]+/[A-Za-z0-9_.]+|%(?:\d+\$)?[a-zA-Z]|\\.|&(?:#\d+|#x[0-9A-Fa-f]+|[A-Za-z]+);|"
    r"https?://[^\s<]+|\b[A-Z][A-Z0-9]{1,}\b|"
    r"\b(?:TIARCA|Simosnap|Android|YouTube|Google|NickServ|ChanServ|Kickban)\b"
)
MARKER_RE = re.compile(r"ZXQ(\d{4})QXZ")

FORCED_VALUES = {
    "pref_header_chat": "Chat",
    "theme_category_chat": "Chat",
    "pref_value_default_quit_message": "Quit",
    "pref_value_default_part_message": "Leaving",
    "app_name": "TIARCA",
    "app_version_label": "v.%1$s",
}


def protect(text: str) -> tuple[str, dict[str, str]]:
    values: dict[str, str] = {}

    def replace(match: re.Match[str]) -> str:
        token = f"ZXPH{len(values):03d}QXZ"
        values[token] = match.group(0)
        return token

    return PROTECTED_RE.sub(replace, text), values


def restore_and_escape(text: str, values: dict[str, str]) -> str:
    text = html.escape(text.strip(), quote=False)
    for token, value in values.items():
        text = text.replace(token, value)
    text = re.sub(r"(?<!\\)'", r"\\'", text)
    text = re.sub(r'(?<!\\)"', r'\\"', text)
    return text


def translate_request(text: str, target: str) -> str:
    # The lightweight public web form avoids the stricter rate limit of the
    # JSON endpoint while keeping this preparation script free of API keys.
    mobile_url = "https://translate.google.com/m?" + urllib.parse.urlencode(
        {"sl": "en", "tl": target, "q": text}
    )
    mobile_request = urllib.request.Request(
        mobile_url, headers={"User-Agent": "Mozilla/5.0"}
    )
    try:
        with urllib.request.urlopen(mobile_request, timeout=60) as response:
            page = response.read().decode("utf-8", errors="replace")
        match = re.search(r'class="result-container">(.*?)</div>', page, re.DOTALL)
        if not match:
            raise RuntimeError("translation result not found in mobile response")
        return html.unescape(re.sub(r"<[^>]+>", "", match.group(1)))
    except Exception as mobile_error:
        raise RuntimeError(f"translation request failed: {mobile_error}") from mobile_error


def make_batches(values: list[str], max_items: int = 28, max_chars: int = 4600):
    batch: list[tuple[int, str]] = []
    size = 0
    for index, value in enumerate(values):
        addition = len(value) + 20
        if batch and (len(batch) >= max_items or size + addition > max_chars):
            yield batch
            batch = []
            size = 0
        batch.append((index, value))
        size += addition
    if batch:
        yield batch


def translate_values(values: list[str], target: str, cache_path: Path) -> list[str]:
    cache: dict[str, str] = {}
    if cache_path.exists():
        cache = json.loads(cache_path.read_text(encoding="utf-8"))
    # Empty cached values are failed marker extractions, never valid
    # translations for the non-empty strings passed to this function.
    missing = [
        value for value in dict.fromkeys(values)
        if value not in cache or not cache[value].strip()
    ]
    batches = list(make_batches(missing))
    for number, batch in enumerate(batches, 1):
        payload = "\n".join(f"ZXQ{i:04d}QXZ {value}" for i, value in batch)
        translated = translate_request(payload, target)
        markers = list(MARKER_RE.finditer(translated))
        if len(markers) != len(batch):
            raise RuntimeError(
                f"marker mismatch for {target}, batch {number}: {len(markers)} != {len(batch)}"
            )
        for marker_index, marker in enumerate(markers):
            start = marker.end()
            end = markers[marker_index + 1].start() if marker_index + 1 < len(markers) else len(translated)
            source_index = int(marker.group(1))
            cache[missing[source_index]] = translated[start:end].strip()
        cache_path.write_text(json.dumps(cache, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"  {target}: batch {number}/{len(batches)}", flush=True)
        time.sleep(1.0)
    return [cache[value] for value in values]


def generate(base: Path, output: Path, target: str, cache_dir: Path) -> None:
    source = base.read_text(encoding="utf-8")
    matches = list(ENTRY_RE.finditer(source))
    prepared: list[str] = []
    metadata: list[tuple[dict[str, str], str | None]] = []
    translatable_indices: list[int] = []

    for index, match in enumerate(matches):
        attrs = match.group("attrs")
        name_match = NAME_RE.search(attrs)
        name = name_match.group(1) if name_match else None
        raw = match.group("text")
        forced = FORCED_VALUES.get(name or "")
        if forced is not None:
            prepared.append(forced)
            metadata.append(({}, name))
            continue
        protected, tokens = protect(raw)
        prepared.append(protected)
        metadata.append((tokens, name))
        if re.search(r"[A-Za-z]", protected):
            translatable_indices.append(index)

    unique_inputs = [prepared[i] for i in translatable_indices]
    translated = translate_values(unique_inputs, target, cache_dir / f"{target}.json")
    translated_by_index = dict(zip(translatable_indices, translated))

    replacements: list[str] = []
    for index, match in enumerate(matches):
        tokens, name = metadata[index]
        if name in FORCED_VALUES:
            value = FORCED_VALUES[name]
        elif index in translated_by_index:
            value = restore_and_escape(translated_by_index[index], tokens)
        else:
            value = match.group("text")
        replacements.append(
            f'<{match.group("tag")}{match.group("attrs")}>{value}</{match.group("tag")}>'
        )

    chunks: list[str] = []
    cursor = 0
    for match, replacement in zip(matches, replacements):
        chunks.append(source[cursor:match.start()])
        chunks.append(replacement)
        cursor = match.end()
    chunks.append(source[cursor:])
    output.mkdir(parents=True, exist_ok=True)
    (output / "strings.xml").write_text("".join(chunks), encoding="utf-8", newline="\n")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--res", type=Path, default=Path("app/src/main/res"))
    parser.add_argument("--cache", type=Path, default=Path("tools/.translation-cache"))
    args = parser.parse_args()
    base = args.res / "values" / "strings.xml"
    args.cache.mkdir(parents=True, exist_ok=True)
    for qualifier, target in LANGUAGES.items():
        print(f"Generating values-{qualifier}", flush=True)
        generate(base, args.res / f"values-{qualifier}", target, args.cache)
    return 0


if __name__ == "__main__":
    sys.exit(main())
