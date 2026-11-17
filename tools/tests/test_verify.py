import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools import verify


class VerifySelectionTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.repo = Path(directory.name)
        self.enterContext(patch.object(verify, "REPO_ROOT", self.repo))
        self.git("init", "-q", "-b", "main")
        self.write("README.md", "base docs")
        self.write("app/src/Main.kt", "base source")
        self.git("add", ".")
        self.commit()
        self.git("update-ref", "refs/remotes/origin/main", "HEAD")

    def git(self, *args):
        subprocess.run(["git", *args], cwd=self.repo, check=True, capture_output=True)

    def write(self, name, contents):
        path = self.repo / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(contents, encoding="utf-8")

    def commit(self):
        self.git("-c", "user.name=Test", "-c", "user.email=test@example.invalid",
                 "commit", "-qm", "test input")

    def test_committed_source_and_dirty_docs_are_build_impact(self):
        self.write("app/src/Main.kt", "committed source change")
        self.git("add", ".")
        self.commit()
        self.assertFalse(verify.docs_only("origin/main"))
        self.write("README.md", "local docs")
        self.assertEqual({"README.md", "app/src/Main.kt"}, verify.changed_files("origin/main"))
        self.assertFalse(verify.docs_only("origin/main"))

    def test_committed_docs_on_a_stack_use_the_requested_base(self):
        self.write("app/src/Main.kt", "dependency source")
        self.git("add", ".")
        self.commit()
        self.git("branch", "dependency")
        self.write("docs/测试 guide.md", "governance docs")
        self.git("add", ".")
        self.commit()
        self.assertTrue(verify.docs_only("dependency"))
        self.assertFalse(verify.docs_only("origin/main"))

    def test_uses_common_ancestor_when_target_advances(self):
        self.git("switch", "-qc", "task")
        self.write("README.md", "task docs")
        self.git("add", ".")
        self.commit()
        self.git("switch", "-q", "main")
        self.write("app/src/Main.kt", "target source")
        self.git("add", ".")
        self.commit()
        self.git("switch", "-q", "task")
        self.assertEqual({"README.md"}, verify.changed_files("main"))

    def test_staged_rename_and_untracked_build_input_are_detected(self):
        self.git("mv", "app/src/Main.kt", "archived.md")
        self.write("app/proguard-rules.pro", "# local rule")
        self.assertEqual(
            {"app/src/Main.kt", "archived.md", "app/proguard-rules.pro"},
            verify.changed_files("origin/main"),
        )
        self.assertFalse(verify.docs_only("origin/main"))

    def test_empty_or_missing_base_keeps_android_checks(self):
        self.assertFalse(verify.docs_only("origin/main"))
        with patch("sys.stderr"):
            self.assertFalse(verify.docs_only("missing"))


class VerifyExecutionTest(unittest.TestCase):
    def test_compat_failure_stops_both_modes_before_gradle(self):
        for mode in ("fast", "full"):
            with self.subTest(mode=mode), \
                    patch.object(verify, "check_invariants", return_value=0), \
                    patch.object(verify, "check_compat_contracts", return_value=1), \
                    patch.object(verify, "check_android") as gradle:
                self.assertEqual(1, verify.verify(mode, None, False))
                gradle.assert_not_called()

    def test_requested_tests_override_documentation_skip(self):
        with patch.object(verify, "run", return_value=0) as run:
            self.assertEqual(0, verify.verify("fast", "PowerMenuResourceTest", True))
        self.assertEqual(
            [":app:testDebugUnitTest", "--tests=*PowerMenuResourceTest*"],
            run.call_args.args[0][1:],
        )

    def test_documentation_skip_still_executes_static_checks(self):
        with patch.object(verify, "run", return_value=0) as run, \
                patch.object(verify, "check_android") as gradle:
            self.assertEqual(0, verify.verify("full", None, True))
        self.assertIn("check-text-encoding", [call.args[1] for call in run.call_args_list])
        gradle.assert_not_called()

    def test_full_uses_one_gradle_invocation(self):
        with patch.object(verify, "run", return_value=0) as run:
            self.assertEqual(0, verify.check_android("full", None))
        run.assert_called_once()
        self.assertEqual([":app:testDebugUnitTest", ":app:lintDebug"], run.call_args.args[0][1:])
