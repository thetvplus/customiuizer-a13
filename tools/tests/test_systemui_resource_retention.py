import unittest
import xml.etree.ElementTree as ET
from fnmatch import fnmatchcase
from pathlib import Path

RES = Path(__file__).resolve().parents[2] / "app/src/main/res"


class SystemUiResourceRetentionTest(unittest.TestCase):
    def test_dual_row_signal_lookup_names_are_present_and_kept(self):
        root = ET.parse(RES / "raw/a13_signal_keep.xml").getroot()
        keep = root.attrib["{http://schemas.android.com/tools}keep"]
        rules = [rule.strip() for rule in keep.split(",")]
        drawables = {
            f"@drawable/{path.stem}"
            for directory in RES.glob("drawable*")
            for path in directory.glob("statusbar_signal_*")
            if path.is_file()
        }
        # SystemUI uses the same levels and color modes for both rows.
        expected = {
            f"@drawable/statusbar_signal_{row}_{level}{color}{style}"
            for row in (1, 2)
            for level in range(6)
            for style in ("", "_thick", "_theme")
            for color in (("", "_dark") if style == "_theme" else ("", "_dark", "_tint"))
        }
        self.assertFalse(expected - drawables, f"Missing signal assets: {sorted(expected - drawables)}")
        unprotected = {asset for asset in expected if not any(fnmatchcase(asset, rule) for rule in rules)}
        self.assertFalse(unprotected, f"Signal assets without a keep rule: {sorted(unprotected)}")
