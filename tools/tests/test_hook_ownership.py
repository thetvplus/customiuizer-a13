import contextlib
import io
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools import audit_hook_ownership


class HookOwnershipTest(unittest.TestCase):
    def test_check_reports_unclassified_multiline_call_with_location(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "Unowned.kt").write_text(
                "fun install() {\n    ModuleHelper\n        .findAndHookMethod()\n}\n",
                encoding="utf-8",
            )
            with patch.object(audit_hook_ownership, "SOURCE_ROOT", root), \
                    patch.object(audit_hook_ownership, "file_typed_functions", return_value={}), \
                    contextlib.redirect_stdout(io.StringIO()) as output:
                self.assertEqual(1, audit_hook_ownership.main(["--check"]))
            self.assertIn("Unclassified hook site: Unowned.kt:2", output.getvalue())
