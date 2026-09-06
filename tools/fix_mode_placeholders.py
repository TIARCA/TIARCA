#!/usr/bin/env python3
from pathlib import Path
import re

RES = Path('app/src/main/res')
FILES = [
    RES / 'values/strings.xml',
    RES / 'values-it/strings.xml',
    RES / 'values-de/strings.xml',
    RES / 'values-es/strings.xml',
    RES / 'values-fi/strings.xml',
    RES / 'values-fr/strings.xml',
    RES / 'values-pl/strings.xml',
    RES / 'values-pt-rBR/strings.xml',
    RES / 'values-ro/strings.xml',
]

ONE_ARG = {
    'message_mode_set', 'message_mode_unset', 'message_mode_gave',
    'message_mode_removed', 'message_mode_channel_ban'
}
TWO_ARG = {
    'message_mode', 'message_mode_gave_to', 'message_mode_removed_from',
    'message_mode_channel_value'
}
PH = re.compile(r'%(?:\d+\$)?([a-zA-Z])')

def reindex(text):
    i = 0
    def repl(m):
        nonlocal i
        i += 1
        return f'%{i}${m.group(1)}'
    return PH.sub(repl, text)

def fix_string(xml, name):
    pat = re.compile(rf'(<string\s+name="{re.escape(name)}"[^>]*>)(.*?)(</string>)', re.S)
    return pat.sub(lambda m: m.group(1) + reindex(m.group(2)) + m.group(3), xml)

def fix_plural(xml):
    pat = re.compile(r'(<plurals\s+name="message_mode_channel"[^>]*>)(.*?)(</plurals>)', re.S)
    def block(m):
        body = re.sub(r'(<item\b[^>]*>)(.*?)(</item>)',
                      lambda x: x.group(1) + reindex(x.group(2)) + x.group(3),
                      m.group(2), flags=re.S)
        return m.group(1) + body + m.group(3)
    return pat.sub(block, xml)

for path in FILES:
    xml = path.read_text(encoding='utf-8')
    original = xml
    for name in sorted(ONE_ARG | TWO_ARG):
        xml = fix_string(xml, name)
    xml = fix_plural(xml)
    if xml != original:
        path.write_text(xml, encoding='utf-8')
        print(f'fixed {path}')
