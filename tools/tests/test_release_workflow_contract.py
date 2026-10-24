import tempfile
import unittest
from pathlib import Path

from tools import ci_contract_scan
from tools.release_workflow_contract import FILES, parse_workflow, protected_workflow_errors

ROOT = Path(__file__).resolve().parents[2]


class ReleaseWorkflowContractTest(unittest.TestCase):
    def test_real_protected_workflows_have_strict_structure(self):
        for name in FILES:
            path = ROOT / ".github/workflows" / name
            self.assertEqual([], protected_workflow_errors(path, path.read_text(encoding="utf-8")))

    def check_mutation(self, name, before, after):
        path = ROOT / ".github/workflows" / name
        text = path.read_text(encoding="utf-8")
        self.assertIn(before, text)
        errors = protected_workflow_errors(path, text.replace(before, after, 1))
        self.assertTrue(errors, f"Mutation escaped release contract: {before!r}")

    def test_signing_exemption_requires_real_environment_ref_and_cleanup(self):
        for before, after in (("environment: release-signing", "environment: unprotected"),
                              ("refs/heads/main", "refs/heads/feature"),
                              ("needs: validate", "needs: other"),
                              ("workflow_dispatch:", "pull_request:"),
                              ("if: always()", "if: success()"),
                              ("contents: read", "contents: write"),
                              ("/candidate/SHA256SUMS", "/a13-signing/keystore.properties"),
                              ("python tools/signed_release.py build", "echo $A13_STORE_PASSWORD"),
                              ("A13_KEY_PASSWORD }}", "A14_KEY_PASSWORD }}")):
            with self.subTest(before=before):
                self.check_mutation("a13-signed-build.yml", before, after)

    def test_publishing_default_authorization_receipt_and_environment_cannot_be_weakened(self):
        for before, after in (("default: actions-only", "default: both"),
                              ("environment: release-publishing", "environment: release-signing"),
                              (" && inputs.authorization != ''", ""),
                              ("VERIFIED_ARTIFACT_ID", "IGNORED_ARTIFACT_ID"),
                              ("A13_XPOSED_RELEASE_TOKEN }}", "OTHER_TOKEN }}")):
            with self.subTest(before=before):
                self.check_mutation("a13-promote-release.yml", before, after)

    def test_workflow_name_alone_never_exempts_unsafe_ci(self):
        with tempfile.TemporaryDirectory() as td:
            for name in ("ordinary-ci.yml", "a13-signed-build.yml"):
                path = Path(td) / name
                path.write_text("name: unsafe\non:\n  pull_request:\njobs:\n  build:\n    steps:\n      - run: ./gradlew -PofficialRelease=true assembleRelease\n        env:\n          KEY: ${{ secrets.A13_KEYSTORE_BASE64 }}\n", encoding="utf-8")
                self.assertIn("CI_SIGNING", "\n".join(ci_contract_scan.scan_workflow(path, "main", "main")))

    def test_duplicate_yaml_and_aliased_secret_scope_are_rejected(self):
        for text in ("jobs: {}\njobs: {}", "env: &shared {KEY: secret}\njobs: *shared"):
            with self.assertRaises(ValueError):
                parse_workflow(text)

    def test_full_ci_no_longer_depends_on_commit_marker_and_stays_secretless(self):
        for name in ("a13-fast-ci.yml", "a13-full-ci.yml"):
            data = parse_workflow((ROOT / ".github/workflows" / name).read_text(encoding="utf-8"))
            text = str(data)
            self.assertNotIn("secrets.", text)
            self.assertNotIn("[full-ci]", text)
            for job in data["jobs"].values():
                self.assertNotIn("environment", job)
                self.assertNotIn("if", job)


if __name__ == "__main__":
    unittest.main()
