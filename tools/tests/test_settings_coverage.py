import shutil
import tempfile
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

from tools import check_settings_coverage as coverage


class SettingsCoverageTest(unittest.TestCase):
    def fixture(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        root = Path(temp.name)
        for relative in ("app/src/main/res/xml", "app/src/main/res/values", "app/src/main/java/tv/withaibuild/customiuizer"):
            shutil.copytree(coverage.ROOT / relative, root / relative)
        (root / "tools").mkdir()
        shutil.copy(coverage.ROOT / "tools/settings_value_contract.json", root / "tools")
        return root

    def test_current_pages_are_complete_and_keep_saved_values(self):
        errors, counts = coverage.audit()
        self.assertEqual([], errors)
        self.assertEqual(32, counts["pages"])
        self.assertEqual(23, counts["detail_routes"])

    def test_unrouted_page_fails(self):
        root = self.fixture()
        (root / "app/src/main/res/xml/prefs_orphan.xml").write_text("<PreferenceScreen />", encoding="utf-8")
        self.assertTrue(any("unreachable" in error for error in coverage.audit(root)[0]))

    def test_hidden_groups_never_expose_retired_search_entries(self):
        tree = ET.fromstring('<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android" xmlns:app="http://schemas.android.com/apk/res-auto"><PreferenceCategory app:isPreferenceVisible="false"><Preference android:key="hidden" /></PreferenceCategory><Preference android:key="visible" /></PreferenceScreen>')
        self.assertEqual({"visible"}, coverage.visible_keys(tree))

    def test_launcher_selector_saved_default_is_also_protected(self):
        root = self.fixture()
        path = root / "app/src/main/res/xml/prefs_launcher_cat.xml"
        tree = ET.parse(path)
        node = next(n for n in tree.iter() if n.get(coverage.A + "key") == "pref_key_launcher_mods")
        node.set(coverage.A + "defaultValue", "2")
        tree.write(path, encoding="utf-8")
        self.assertTrue(any("prefs_launcher_cat: saved preference contract changed" in error for error in coverage.audit(root)[0]))

    def test_changed_default_and_removed_key_fail_compatibility(self):
        root = self.fixture()
        path = root / "app/src/main/res/xml/prefs_launcher.xml"
        tree = ET.parse(path)
        node = next(n for n in tree.iter() if n.get(coverage.A + "key") == "pref_key_launcher_iconscale")
        node.set(coverage.A + "defaultValue", "100")
        tree.write(path, encoding="utf-8")
        self.assertTrue(any("saved preference contract changed" in error for error in coverage.audit(root)[0]))
        node.attrib.pop(coverage.A + "key")
        tree.write(path, encoding="utf-8")
        self.assertTrue(any("saved preference contract changed" in error for error in coverage.audit(root)[0]))

    def test_missing_dependency_and_duplicate_key_fail(self):
        root = self.fixture()
        path = root / "app/src/main/res/xml/prefs_launcher.xml"
        tree = ET.parse(path)
        node = next(n for n in tree.iter() if n.get(coverage.A + "key") == "pref_key_launcher_iconscale")
        node.set(coverage.A + "dependency", "missing_dependency")
        node.set(coverage.A + "key", "pref_key_launcher_hidetitles")
        tree.write(path, encoding="utf-8")
        errors = coverage.audit(root)[0]
        self.assertTrue(any("depends on missing" in error for error in errors))
        self.assertTrue(any("duplicate preference key" in error for error in errors))

    def test_missing_search_destination_and_category_entry_fail(self):
        root = self.fixture()
        path = root / "app/src/main/java/tv/withaibuild/customiuizer/SettingsPages.kt"
        source = path.read_text(encoding="utf-8")
        source = source.replace("Page(R.xml.prefs_system_statusbar_clock,", "Page(R.xml.prefs_missing,")
        source = source.replace('"pref_key_system_visualizer_cat"', '"missing_entry"')
        path.write_text(source, encoding="utf-8")
        errors = coverage.audit(root)[0]
        self.assertTrue(any("unreachable" in error for error in errors))
        self.assertTrue(any("missing_entry is missing" in error for error in errors))

    def test_presentation_changes_do_not_change_stored_value_contract(self):
        root = ET.fromstring('<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"><CheckBox android:key="k" android:title="old" android:defaultValue="false" /></PreferenceScreen>')
        old = coverage.value_contract(root)
        root[0].set(coverage.A + "title", "new")
        root[0].set(coverage.A + "order", "20")
        self.assertEqual(old, coverage.value_contract(root))


if __name__ == "__main__":
    unittest.main()
