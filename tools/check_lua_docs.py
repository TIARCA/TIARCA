#!/usr/bin/env python3
"""Check bilingual TIARCA Lua developer docs without external dependencies."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "docs" / "lua"
PAGES = ("README.md", "API_REFERENCE.md", "EVENTS.md", "PERMISSIONS.md", "EXAMPLES.md")
EXPECTED_API = {
    "on", "log", "send", "pm", "notice", "get_channels", "get_users",
    "refresh_users", "maskfind", "show", "show_list", "storage.get",
    "storage.set", "storage.delete", "kick", "eject", "ban", "mode", "mute",
    "tban", "kickban", "kicktban", "blacklist_action",
    "last_message", "journal.append", "journal.export",
}
EXPECTED_EVENTS = {
    "connected", "disconnected", "message", "command", "join", "part",
    "quit", "nick", "timer", "kick", "ban", "history", "moderation_log",
}

def links_ok(path: Path, content: str) -> list[str]:
    problems = []
    for dest in re.findall(r"\[[^]]+\]\(([^)]+)\)", content):
        if dest.startswith(("http:", "https:", "#", "mailto:")):
            continue
        target = (path.parent / dest.split("#", 1)[0]).resolve()
        if not target.exists():
            problems.append(f"{path.relative_to(ROOT)}: broken link {dest}")
    return problems

def tokens(content: str, pattern: str) -> set[str]:
    return set(re.findall(pattern, content))

def main() -> int:
    problems = []
    for relative in ("README.md", "AGENTS.md"):
        path = BASE / relative
        if not path.is_file():
            problems.append(f"missing {path.relative_to(ROOT)}")
    for lang in ("it", "en"):
        for page in PAGES:
            path = BASE / lang / page
            if not path.is_file():
                problems.append(f"missing {path.relative_to(ROOT)}")
    if problems:
        print("\n".join(problems))
        return 1
    for path in BASE.rglob("*.md"):
        data = path.read_text(encoding="utf-8")
        if data.count("```") % 2:
            problems.append(f"{path.relative_to(ROOT)}: unbalanced code fences")
        problems.extend(links_ok(path, data))
    for lang in ("it", "en"):
        api = (BASE / lang / "API_REFERENCE.md").read_text(encoding="utf-8")
        found = tokens(api, r"tiarca\.([a-z_]+(?:\.[a-z_]+)?)")
        if found != EXPECTED_API:
            problems.append(f"{lang} API mismatch: missing={sorted(EXPECTED_API-found)}, extra={sorted(found-EXPECTED_API)}")
        events = (BASE / lang / "EVENTS.md").read_text(encoding="utf-8")
        found_events = tokens(events, r"\| `([a-z_]+)` \|")
        if found_events != EXPECTED_EVENTS:
            problems.append(f"{lang} events mismatch: missing={sorted(EXPECTED_EVENTS-found_events)}")
    if problems:
        print("\n".join(problems))
        return 1
    print("Lua documentation: bilingual pages, API/event parity, links and fences OK")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
