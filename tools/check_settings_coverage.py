#!/usr/bin/env python3
"""Check every settings XML, route and saved-value contract without Android or Git."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
A = "{http://schemas.android.com/apk/res/android}"
APP = "{http://schemas.android.com/apk/res-auto}"
ROOT_PAGES = {"prefs_system", "prefs_launcher", "prefs_controls", "prefs_various"}
OTHER_PAGES = {"prefs_main", "prefs_about", "prefs_system_cat", "prefs_launcher_cat", "prefs_controls_cat"}
PRESENTATION = {"title", "summary", "icon", "layout_width", "layout_height", "layout", "order", "indentLevel"}


def is_group(node: ET.Element) -> bool:
    return node.tag.endswith(("PreferenceScreen", "PreferenceCategoryEx", "PreferenceCategory"))


def visible_keys(tree: ET.Element) -> set[str]:
    if tree.get(APP + "isPreferenceVisible") == "false":
        return set()
    keys = {tree.get(A + "key")} if tree.get(A + "key") else set()
    for child in tree:
        keys.update(visible_keys(child))
    return keys


def value_contract(tree: ET.Element, added_navigation: tuple[str, ...] = ()) -> str:
    """Order and grouping may change; keys, types, defaults and dependencies may not."""
    rows = []
    for node in tree.iter():
        key = node.get(A + "key")
        if not key or is_group(node) or key in added_navigation:
            continue
        attrs = {k: v for k, v in node.attrib.items() if k.split("}")[-1] not in PRESENTATION}
        rows.append((key, node.tag, sorted(attrs.items())))
    encoded = json.dumps(sorted(rows), ensure_ascii=True, separators=(",", ":")).encode()
    return hashlib.sha256(encoded).hexdigest()


def audit(root: Path = ROOT) -> tuple[list[str], dict[str, int]]:
    xml_dir = root / "app/src/main/res/xml"
    code_dir = root / "app/src/main/java/tv/withaibuild/customiuizer"
    trees = {p.stem: ET.parse(p).getroot() for p in sorted(xml_dir.glob("prefs*.xml"))}
    strings = {n.get("name") for n in ET.parse(root / "app/src/main/res/values/strings.xml").getroot()}
    arrays = {n.get("name") for n in ET.parse(root / "app/src/main/res/values/arrays.xml").getroot()}
    routes = (code_dir / "SettingsPages.kt").read_text(encoding="utf-8")
    details = re.findall(r'Page\(R\.xml\.(\w+),\s*"([^"]+)",\s*::(\w+)', routes)
    errors = []
    expected = ROOT_PAGES | OTHER_PAGES | {page for page, _, _ in details}
    if set(trees) != expected:
        errors.append(f"unreachable or missing pages: {sorted(set(trees) ^ expected)}")
    if len(details) != len({p for p, _, _ in details}):
        errors.append("duplicate detail page route")
    root_keys = {node.get(A + "key") for page in ROOT_PAGES for node in trees[page].iter()}
    source = "\n".join(p.read_text(encoding="utf-8") for p in (code_dir / "subs").glob("*.kt"))
    for page, entry, factory in details:
        if entry not in root_keys:
            errors.append(f"{page}: entry {entry} is missing")
        if f'R.xml.{page}' not in source or f'"{entry}"' not in source:
            errors.append(f"{page}: existing entry handler is missing")
        if factory != "SubFragment" and not (code_dir / "subs" / (factory + ".kt")).is_file():
            errors.append(f"{page}: fragment {factory} is missing")
    leaf_count = 0
    searchable = 0
    for page, tree in trees.items():
        visible = visible_keys(tree)
        keys = [node.get(A + "key") for node in tree.iter() if node.get(A + "key")]
        if len(keys) != len(set(keys)):
            errors.append(f"{page}: duplicate preference key")
        for node in tree.iter():
            key = node.get(A + "key")
            dependency = node.get(A + "dependency")
            if dependency and dependency not in keys:
                errors.append(f"{page}: {key} depends on missing {dependency}")
            title = node.get(A + "title")
            if title and title.startswith("@string/") and title[8:] not in strings:
                errors.append(f"{page}: missing title {title}")
            if key and not is_group(node):
                leaf_count += 1
                informational = node.tag.endswith("PreferenceEx") and (node.get(A + "summary") or node.get(APP + "isPreferenceVisible") == "false")
                if not title and not informational:
                    errors.append(f"{page}: {key} has no title")
                if title and key in visible and node.get(APP + "child") != "true" and page not in OTHER_PAGES:
                    searchable += 1
                for name in ("entries", "entryValues"):
                    value = node.get(A + name) or node.get(APP + name)
                    if value and value.startswith("@array/") and value[7:] not in arrays:
                        errors.append(f"{page}: missing {value}")
    for family in ("system", "launcher", "controls"):
        tree = trees[f"prefs_{family}"]
        sections = {n.get(A + "key") for n in tree if is_group(n)}
        selector = {n.get(A + "key") for n in trees[f"prefs_{family}_cat"] if not is_group(n)}
        selector.discard("pref_key_launcher_mods")
        if sections != selector:
            errors.append(f"{family}: category navigation mismatch {sorted(sections ^ selector)}")
    contract = json.loads((root / "tools/settings_value_contract.json").read_text(encoding="utf-8"))
    added_navigation = tuple(contract.get("added_navigation_keys", []))
    for tree in trees.values():
        for node in tree.iter():
            if node.get(A + "key") in added_navigation and not is_group(node):
                if not node.tag.endswith("PreferenceEx") or node.get(A + "defaultValue") is not None:
                    errors.append("new navigation key must remain a non-persistent page entry")
    all_keys = {n.get(A + "key") for tree in trees.values() for n in tree.iter()}
    if all_keys.intersection(contract["retired"]):
        errors.append("retired amplitude preference is reachable again")
    for page, digest in contract["pages"].items():
        if page not in trees or value_contract(trees[page], added_navigation) != digest:
            errors.append(f"{page}: saved preference contract changed; review keys, types, defaults and dependencies")
    return errors, {"pages": len(trees), "detail_routes": len(details), "leaf_entries": leaf_count, "searchable_entries": searchable}


def main() -> int:
    errors, counts = audit()
    for error in errors:
        print("[settings] " + error)
    print("[settings] " + json.dumps(counts, sort_keys=True))
    return int(bool(errors))


if __name__ == "__main__":
    sys.exit(main())
