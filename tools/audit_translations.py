#!/usr/bin/env python3
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

RES = Path('app/src/main/res')
LOCALES = ['it', 'de', 'es', 'fi', 'fr', 'pl', 'pt-rBR', 'ro']
RESOURCE_FILES = ['strings.xml', 'strings_tiarca_sync.xml']
FMT = re.compile(r'%(?!%)(?:(\d+)\$)?([sdfoxegacbBhH])')


def positionalize_text(text: str) -> str:
    matches = list(FMT.finditer(text))
    if len(matches) < 2 or all(m.group(1) for m in matches):
        return text
    # Mixed positional/non-positional formats are unsafe: make every argument explicit
    out, last = [], 0
    next_index = 1
    for m in matches:
        out.append(text[last:m.start()])
        if m.group(1):
            out.append(m.group(0))
            next_index = max(next_index, int(m.group(1)) + 1)
        else:
            out.append(f'%{next_index}${m.group(2)}')
            next_index += 1
        last = m.end()
    out.append(text[last:])
    return ''.join(out)


def fix_file(path: Path) -> bool:
    raw = path.read_text(encoding='utf-8')
    fixed = positionalize_text(raw)
    if fixed != raw:
        path.write_text(fixed, encoding='utf-8')
        return True
    return False


def load_resources(folder: Path):
    found = {}
    duplicates = []
    for filename in RESOURCE_FILES:
        path = folder / filename
        if not path.exists():
            continue
        root = ET.parse(path).getroot()
        for el in root:
            name = el.get('name')
            if not name or el.tag not in ('string', 'plurals', 'string-array'):
                continue
            key = (el.tag, name)
            if key in found:
                duplicates.append(key)
            found[key] = el
    return found, duplicates


def signatures(el):
    texts = []
    if el.tag == 'string':
        texts = [''.join(el.itertext())]
    else:
        texts = [''.join(x.itertext()) for x in el if x.tag == 'item']
    return [tuple((m.group(1) or '', m.group(2)) for m in FMT.finditer(t)) for t in texts]


def normalized_types(sig):
    return tuple(t for _, t in sig)


def main():
    changed = []
    for folder in [RES / 'values'] + [RES / f'values-{x}' for x in LOCALES]:
        for filename in RESOURCE_FILES:
            p = folder / filename
            if p.exists() and fix_file(p):
                changed.append(str(p))

    base, base_dupes = load_resources(RES / 'values')
    errors = []
    if base_dupes:
        errors.append(f'base duplicate resources: {base_dupes}')

    for locale in LOCALES:
        loc, dupes = load_resources(RES / f'values-{locale}')
        if dupes:
            errors.append(f'{locale}: duplicate resources: {dupes}')
        missing = sorted(set(base) - set(loc))
        if missing:
            errors.append(f'{locale}: {len(missing)} missing resources: {missing[:20]}')
        # Extra locale resources are allowed for compatibility, but placeholders for
        # resources shared with base must preserve argument count/types.
        for key in sorted(set(base) & set(loc)):
            bs = signatures(base[key])
            ls = signatures(loc[key])
            if len(bs) != len(ls):
                # Plural languages legitimately have additional quantity forms.
                if key[0] != 'plurals':
                    errors.append(f'{locale}: item-count mismatch for {key}')
                continue
            for i, (b, l) in enumerate(zip(bs, ls)):
                if normalized_types(b) != normalized_types(l):
                    errors.append(f'{locale}: placeholder type/count mismatch for {key} item {i}: {b} vs {l}')

    print('Positional placeholder files changed:', len(changed))
    for p in changed:
        print('  ', p)
    print('Base resources:', len(base))
    if errors:
        print('\nAUDIT ERRORS:')
        for e in errors:
            print(' -', e)
        return 1
    print('Translation audit OK: complete resource coverage, no duplicates, placeholder signatures compatible.')
    return 0

if __name__ == '__main__':
    sys.exit(main())
