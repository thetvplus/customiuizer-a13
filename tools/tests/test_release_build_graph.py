import tempfile
import unittest
from pathlib import Path

from tools.check_release_build_graph import build_command, check_plan, fixture


class ReleaseBuildGraphTest(unittest.TestCase):
    plan = "\n".join(f":app:{name} SKIPPED" for name in (
        "generateA13ReleaseProvenance", "mergeReleaseAssets", "packageRelease",
        "assembleRelease", "lintVitalRelease"))

    def test_placeholder_never_contains_or_creates_a_real_keystore(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            config = fixture(directory)
            self.assertEqual([config], list(directory.iterdir()))
            self.assertIn("unavailable-test-only.p12", config.read_text())
            args = build_command(config, "a" * 40)
            self.assertIn("--dry-run", args)
            self.assertIn("-PofficialRelease=true", args)
            self.assertIn(f"-PcustomiuizerA13KeystoreProperties={config}", args)

    def test_real_task_plan_requires_provenance_before_asset_merge(self):
        check_plan(self.plan)
        with self.assertRaises(ValueError):
            check_plan(self.plan.replace(":app:generateA13ReleaseProvenance SKIPPED", ""))
        lines = self.plan.splitlines()
        lines[0], lines[1] = lines[1], lines[0]
        with self.assertRaises(ValueError):
            check_plan("\n".join(lines))

    def test_an_executed_or_missing_packaging_step_cannot_masquerade_as_a_dry_run(self):
        for changed in (self.plan.replace(":app:packageRelease SKIPPED", ":app:packageRelease"),
                        self.plan.replace(":app:lintVitalRelease SKIPPED", "")):
            with self.assertRaises(ValueError):
                check_plan(changed)
