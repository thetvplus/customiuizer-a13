import base64
import copy
import hashlib
import io
import json
import os
import shutil
import subprocess
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path
from unittest.mock import patch

from tools import signed_release as release
from tools.verify_apk_provenance import verify_apk_provenance

SOURCE = "a" * 40
BUILD_RUN = {"id": 123, "run_attempt": 2, "workflow_id": 9, "path": release.WORKFLOW,
             "head_sha": SOURCE, "head_branch": "main", "head_repository": {
                 "full_name": release.REPO, "id": release.POLICY["repository_id"]},
             "event": "workflow_dispatch", "status": "completed", "conclusion": "success"}


def metadata():
    return {"schema": 2, "repository": release.REPO, "repository_id": release.POLICY["repository_id"],
            "source_sha": SOURCE, "source_ref": "refs/heads/main", "workflow": release.WORKFLOW,
            "run_id": 123, "run_attempt": 2, "version_name": "r13.14.1", "version_code": 142,
            "certificate_sha256": release.CERT, "apk": "CustoMIUIzer-A13-r13.14.1.apk",
            "apk_sha256": "b" * 64, "apk_size": 7, "artifact_id": 45}


class SourceAndCITest(unittest.TestCase):
    def test_native_main_requires_repository_id_dispatch_ref_and_checkout(self):
        env = {"GITHUB_REPOSITORY": release.REPO, "GITHUB_REPOSITORY_ID": str(release.POLICY["repository_id"]),
               "GITHUB_EVENT_NAME": "workflow_dispatch", "GITHUB_REF": "refs/heads/main", "GITHUB_SHA": SOURCE}
        with patch.object(release, "command", side_effect=[SOURCE.encode(), b""]):
            self.assertEqual(SOURCE, release.native_source(env))
        for key, bad in (("GITHUB_REPOSITORY", "fork/a13"), ("GITHUB_REPOSITORY_ID", "99"),
                         ("GITHUB_EVENT_NAME", "workflow_run"), ("GITHUB_REF", "refs/tags/r13.14.1"),
                         ("GITHUB_SHA", "abc")):
            with self.subTest(key=key), self.assertRaises(ValueError):
                release.native_source(env | {key: bad})
        with patch.object(release, "command", return_value=b"b" * 40), self.assertRaises(ValueError):
            release.native_source(env)

    def test_reject_wrong_sha_branch_fork_event_skipped_or_unsuccessful_run(self):
        workflow = {"id": 9, "path": release.WORKFLOW}
        release.check_run(BUILD_RUN, workflow, SOURCE, {"workflow_dispatch"})
        for key, value in (("head_sha", "b" * 40), ("head_branch", "feature"), ("event", "pull_request"),
                           ("workflow_id", 8), ("conclusion", "cancelled"), ("conclusion", "skipped"),
                           ("conclusion", "failure"), ("status", "in_progress"),
                           ("head_repository", {"full_name": release.REPO, "id": 1})):
            with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                release.check_run(BUILD_RUN | {key: value}, workflow, SOURCE, {"workflow_dispatch"})

    def test_successful_workflow_cannot_hide_skipped_job_or_required_step(self):
        job = {"name": "full", "status": "completed", "conclusion": "success",
               "steps": [{"name": "Full verifier", "conclusion": "success"}]}
        for change in ({}, {"conclusion": "skipped"}, {"steps": [{"name": "Full verifier", "conclusion": "skipped"}]}):
            with self.subTest(change=change), patch.object(release, "api", return_value={"total_count": 1, "jobs": [job | change]}):
                if not change:
                    release.successful_jobs(BUILD_RUN, {"full": {"Full verifier"}})
                else:
                    with self.assertRaises(ValueError):
                        release.successful_jobs(BUILD_RUN, {"full": {"Full verifier"}})

    def test_signing_preflight_checks_native_confirmation_and_both_ci(self):
        with tempfile.TemporaryDirectory() as td:
            event = Path(td) / "event.json"
            event.write_text(json.dumps({"inputs": {"source_sha": "b" * 40}}))
            with patch.dict(os.environ, {"GITHUB_EVENT_PATH": str(event)}), patch.object(release, "native_source", return_value=SOURCE), self.assertRaisesRegex(ValueError, "Confirmed source"):
                release.preflight()

    def test_missing_and_failed_ci_cannot_pass(self):
        workflow = {"id": 9, "path": ".github/workflows/a13-fast-ci.yml", "state": "active"}
        with patch.object(release, "api", side_effect=[workflow, {"workflow_runs": []}]), self.assertRaisesRegex(ValueError, "No fully successful"):
            release.main_ci(SOURCE)

    def test_preflight_success_and_existing_release_requires_original_candidate(self):
        with tempfile.TemporaryDirectory() as td:
            event = Path(td) / "event.json"
            event.write_text(json.dumps({"inputs": {"source_sha": SOURCE}}))
            for existing in (None, {"id": 10}):
                with self.subTest(existing=existing), patch.dict(os.environ, {"GITHUB_EVENT_PATH": str(event)}), patch.object(release, "native_source", return_value=SOURCE), patch.object(release, "current_main"), patch.object(release, "environment_policy"), patch.object(release, "main_ci"), patch.object(release, "find_release", return_value=existing), patch.object(release, "optional_api", return_value=None), patch.object(release, "summary"), patch.object(release, "output"):
                    if existing is None:
                        self.assertEqual(SOURCE, release.preflight())
                    else:
                        with self.assertRaisesRegex(ValueError, "original signed candidate"):
                            release.preflight()


class EnvironmentAndSigningTest(unittest.TestCase):
    def test_environment_requires_reviewers_no_bypass_and_only_main(self):
        record = {"protection_rules": [{"type": "required_reviewers", "reviewers": [{"id": 1}]}],
                  "can_admins_bypass": False,
                  "deployment_branch_policy": {"protected_branches": False, "custom_branch_policies": True}}
        rules = {"total_count": 1, "branch_policies": [{"name": "main", "type": "branch"}]}
        release.check_environment(record, rules)
        for bad in (record | {"can_admins_bypass": True}, record | {"protection_rules": []}, record | {"deployment_branch_policy": None}):
            with self.assertRaises(ValueError):
                release.check_environment(bad, rules)
        for rule in ({"name": "*", "type": "branch"}, {"name": "main", "type": "tag"}):
            with self.assertRaises(ValueError):
                release.check_environment(record, {"total_count": 1, "branch_policies": [rule]})

    @unittest.skipUnless(shutil.which("java"), "JDK required for actual Properties.load round trip")
    def test_java_properties_round_trip_special_passwords(self):
        values = {"storePassword": " leading =:#!\\\t\r\n\f密碼😀 trailing ", "keyPassword": "\\u0041\\n:$()'\"`"}
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            props = root / "test.properties"
            props.write_text("".join(f"{k}={release.property_escape(v)}\n" for k, v in values.items()), encoding="ascii")
            source = root / "PropertyProbe.java"
            source.write_text('import java.util.*; import java.nio.file.*; public class PropertyProbe { public static void main(String[] a) throws Exception { Properties p=new Properties(); try(var s=Files.newInputStream(Path.of(a[0]))){p.load(s);} for(String k: new String[]{"storePassword","keyPassword"}) System.out.println(Base64.getEncoder().encodeToString(p.getProperty(k).getBytes(java.nio.charset.StandardCharsets.UTF_8))); }}', encoding="ascii")
            result = subprocess.run(["java", str(source), str(props)], capture_output=True, check=True)
            self.assertEqual(list(values.values()), [base64.b64decode(line).decode() for line in result.stdout.splitlines()])

    def test_missing_secrets_never_generate_any_key(self):
        with patch.object(release, "command") as command:
            for key in release.SECRETS:
                env = {item: "test-only" for item in release.SECRETS if item != key}
                with self.subTest(key=key), self.assertRaisesRegex(ValueError, "Missing A13"):
                    release.prepare_signing(env)
            command.assert_not_called()

    def test_certificate_mismatch_and_bad_pkcs12_cleanup_without_disclosing_values(self):
        env = {key: "test-only-private-value" for key in release.SECRETS}
        env["A13_KEYSTORE_BASE64"] = base64.b64encode(b"\x30test-only-pkcs12").decode()
        for result in (b"wrong-public-certificate", ValueError("keytool failed")):
            with self.subTest(result=str(result)), tempfile.TemporaryDirectory() as td, patch.dict(os.environ, {"RUNNER_TEMP": td}), patch.object(release, "command", **({"side_effect": result} if isinstance(result, Exception) else {"return_value": result})):
                with self.assertRaises(ValueError) as failure:
                    release.prepare_signing(env)
                self.assertNotIn("test-only-private-value", str(failure.exception))
                self.assertFalse((Path(td) / "a13-signing").exists())

    def test_prepare_pkcs12_and_config_success_without_real_private_material(self):
        certificate = b"test-only-public-certificate"
        env = {"A13_KEYSTORE_BASE64": base64.b64encode(b"\x30test-only-pkcs12").decode(),
               "A13_STORE_PASSWORD": " test:=\\\n密碼 ", "A13_KEY_ALIAS": "test-only-alias", "A13_KEY_PASSWORD": " test-only "}
        with tempfile.TemporaryDirectory() as td, patch.dict(os.environ, {"RUNNER_TEMP": td}), patch.object(release, "CERT", hashlib.sha256(certificate).hexdigest()), patch.object(release, "command", return_value=certificate) as command:
            config = release.prepare_signing(env)
            self.assertTrue(config.is_file())
            self.assertTrue((config.parent / "release.p12").is_file())
            self.assertIn("-storetype", command.call_args.args)
            self.assertIn("PKCS12", command.call_args.args)
            self.assertNotIn(env["A13_STORE_PASSWORD"], str(command.call_args.args))
            self.assertIn("storePassword=\\ test\\:\\=\\\\\\n\\u5bc6\\u78bc\\ ", config.read_text())
            release.cleanup()
            self.assertFalse(config.parent.exists())

    def test_jks_or_corrupt_base64_never_reaches_keytool(self):
        for encoded in (base64.b64encode(b"\xfe\xed\xfe\xedjks").decode(), "not valid base64!"):
            env = {key: "test-only" for key in release.SECRETS} | {"A13_KEYSTORE_BASE64": encoded}
            with tempfile.TemporaryDirectory() as td, patch.dict(os.environ, {"RUNNER_TEMP": td}), patch.object(release, "command") as command, self.assertRaises(ValueError):
                release.prepare_signing(env)
            command.assert_not_called()

    def test_build_failure_still_cleans_material_and_removes_secrets_from_child(self):
        with tempfile.TemporaryDirectory() as td:
            folder = Path(td) / "a13-signing"
            folder.mkdir()
            config = folder / "keystore.properties"
            config.write_text("test-only")
            with patch.dict(os.environ, {"RUNNER_TEMP": td, "GH_TOKEN": "test-only", "GITHUB_TOKEN": "test-only", **{key: "test-only" for key in release.SECRETS}}), patch.object(release, "preflight", return_value=SOURCE), patch.object(release, "prepare_signing", return_value=config), patch.object(release.subprocess, "run", return_value=subprocess.CompletedProcess([], 0)) as bootstrap, patch.object(release, "command", side_effect=ValueError("build stopped")) as command:
                with self.assertRaises(ValueError):
                    release.build()
                child_env = command.call_args.kwargs["env"]
                self.assertFalse(set(release.SECRETS) & set(child_env))
                self.assertNotIn("GH_TOKEN", child_env)
                self.assertNotIn("GITHUB_TOKEN", child_env)
                self.assertIn("--no-configuration-cache", command.call_args.args)
                unsigned_env = bootstrap.call_args.kwargs["env"]
                self.assertEqual(":app:help", bootstrap.call_args.args[0][-1])
                self.assertFalse((set(release.SECRETS) | {"GH_TOKEN", "GITHUB_TOKEN", "CUSTOMIUIZER_A13_KEYSTORE_PROPERTIES"}) & set(unsigned_env))
                self.assertFalse(Path(child_env["GRADLE_USER_HOME"]).parent.exists())
                self.assertFalse(folder.exists())

    def test_failed_unsigned_bootstrap_never_loads_the_signing_material(self):
        with tempfile.TemporaryDirectory() as td, patch.dict(os.environ, {"RUNNER_TEMP": td}), patch.object(release, "preflight", return_value=SOURCE), patch.object(release.subprocess, "run", return_value=subprocess.CompletedProcess([], 1)) as bootstrap, patch.object(release, "prepare_signing") as signing, patch.object(release, "command") as command:
            with self.assertRaisesRegex(ValueError, "Unsigned Gradle bootstrap failed"):
                release.build()
            signing.assert_not_called()
            command.assert_not_called()
            self.assertFalse(Path(bootstrap.call_args.kwargs["env"]["GRADLE_USER_HOME"]).parent.exists())

    def test_bootstrap_completes_before_any_signing_file_is_prepared(self):
        with tempfile.TemporaryDirectory() as td, patch.dict(os.environ, {"RUNNER_TEMP": td}), patch.object(release, "preflight", return_value=SOURCE):
            events = []
            def unsigned(*args, **kwargs):
                self.assertFalse(release.signing_directory().exists())
                events.append("unsigned")
                return subprocess.CompletedProcess([], 0)
            def signing():
                events.append("signing")
                return Path(td) / "test-only.properties"
            with patch.object(release.subprocess, "run", side_effect=unsigned), patch.object(release, "prepare_signing", side_effect=signing), patch.object(release, "command", side_effect=lambda *args, **kwargs: events.append("official")):
                release.build()
            self.assertEqual(["unsigned", "signing", "official"], events)

    def test_main_must_still_pass_after_the_cold_bootstrap(self):
        with tempfile.TemporaryDirectory() as td, patch.dict(os.environ, {"RUNNER_TEMP": td}), patch.object(release, "preflight", side_effect=[SOURCE, ValueError("main moved")]), patch.object(release.subprocess, "run", return_value=subprocess.CompletedProcess([], 0)), patch.object(release, "prepare_signing") as signing:
            with self.assertRaisesRegex(ValueError, "main moved"):
                release.build()
            signing.assert_not_called()


class ArtifactAndProvenanceTest(unittest.TestCase):
    def test_metadata_rejects_wrong_source_version_name_attempt_and_certificate(self):
        release.check_metadata(metadata(), BUILD_RUN)
        for key, value in (("source_sha", "b" * 40), ("run_attempt", 3), ("certificate_sha256", "0" * 64),
                           ("version_name", "r14.1.0"), ("apk", "other.apk"), ("repository_id", 1),
                           ("schema", 1), ("apk", "CustoMIUIzer-A13-r13.14.1-aaaaaa.apk")):
            with self.subTest(key=key), self.assertRaises(ValueError):
                release.check_metadata(metadata() | {key: value}, BUILD_RUN)

    def test_apk_provenance_rejects_wrong_full_sha_version_and_build_type(self):
        props = f"sourceCommit={SOURCE}\nversionName=r13.14.1\nversionCode=142\nbuildType=release\napplicationId=tv.withaibuild.customiuizer.r13\n"
        with tempfile.TemporaryDirectory() as td:
            apk = Path(td) / "test.apk"
            for original, changed in ((props, props), (SOURCE, "b" * 40), ("142", "143"), ("release", "debug"), ("r13.14.1", "r13.13.0")):
                data = changed if original == props else props.replace(original, changed)
                with zipfile.ZipFile(apk, "w") as archive:
                    archive.writestr("assets/build-provenance.properties", data)
                if data == props:
                    verify_apk_provenance(apk, SOURCE, "r13.14.1", 142)
                else:
                    with self.assertRaises(ValueError):
                        verify_apk_provenance(apk, SOURCE, "r13.14.1", 142)

    def test_signature_rejects_wrong_or_additional_signers(self):
        valid = f"Verified using v2 scheme (APK Signature Scheme v2): true\nSigner #1 certificate SHA-256 digest: {release.CERT}"
        release.check_signature(valid)
        for text in (valid.replace(release.CERT, "0" * 64), valid.replace("true", "false"), valid + "\nSigner #2 certificate SHA-256 digest: " + release.CERT):
            with self.assertRaises(ValueError):
                release.check_signature(text)

    def test_badging_rejects_wrong_version_package_sdk_abi_or_debug(self):
        valid = "package: name='tv.withaibuild.customiuizer.r13' versionCode='142' versionName='r13.14.1'\nsdkVersion:'33'\ntargetSdkVersion:'34'\nnative-code: 'arm64-v8a'"
        release.check_badging(valid, "r13.14.1", 142)
        release.check_badging(valid.replace("sdkVersion:", "minSdkVersion:").replace("\nnative-code: 'arm64-v8a'", ""), "r13.14.1", 142)
        for text in (valid.replace("142", "141"), valid.replace(".r13", ".r14"), valid.replace("'33'", "'34'"), valid.replace("'arm64-v8a'", "'x86'"), valid + "\napplication-debuggable"):
            with self.assertRaises(ValueError):
                release.check_badging(text, "r13.14.1", 142)

    def test_archive_rejects_added_private_file_traversal_duplicate_and_corruption(self):
        names = sorted(release.CANDIDATE_FILES | {metadata()["apk"]})
        for extra in ("release.p12", "../outside", names[0]):
            with tempfile.TemporaryDirectory() as td, self.assertRaises(ValueError):
                archive = io.BytesIO()
                with warnings.catch_warnings(), zipfile.ZipFile(archive, "w") as z:
                    warnings.simplefilter("ignore", UserWarning)
                    for name in names + [extra]:
                        z.writestr(name, "test")
                release.extract_candidate(archive.getvalue(), Path(td) / "candidate")
        with self.assertRaises(zipfile.BadZipFile):
            release.extract_candidate(b"corrupt archive", Path("unused"))

    def test_artifact_digest_rejects_replacement_before_extraction(self):
        workflow = {"id": 9, "path": release.WORKFLOW}
        records = {"total_count": 1, "artifacts": [{"id": 45, "name": "a13-signed-123-2", "expired": False, "digest": "sha256:" + "0" * 64}]}
        with patch.object(release, "native_source"), patch.object(release, "successful_jobs"), patch.object(release, "api", side_effect=[workflow, BUILD_RUN, records]), patch.object(release, "command", return_value=b"replaced archive"), patch.object(release, "extract_candidate") as extract, self.assertRaisesRegex(ValueError, "digest mismatch"):
            release.fetch_candidate("123", Path("unused"))
        extract.assert_not_called()

    def test_candidate_still_requires_all_original_integrity_material(self):
        names = release.CANDIDATE_FILES | {metadata()["apk"]}
        for missing in names:
            with self.subTest(missing=missing), tempfile.TemporaryDirectory() as td:
                archive = io.BytesIO()
                with zipfile.ZipFile(archive, "w") as z:
                    for name in names - {missing}:
                        z.writestr(name, "test-only")
                with self.assertRaisesRegex(ValueError, "Unexpected candidate files"):
                    release.extract_candidate(archive.getvalue(), Path(td) / "candidate")

    def test_fetch_success_verifies_original_archive_and_emits_immutable_receipt(self):
        meta = metadata()
        meta.pop("artifact_id")
        with tempfile.TemporaryDirectory() as td:
            source = Path(td) / "source"
            source.mkdir()
            (source / meta["apk"]).write_bytes(b"test-apk")
            meta["apk_size"] = 8
            meta["apk_sha256"] = release.digest(source / meta["apk"])
            chinese, english = "## r13.14.1\n\n简体更新。\n", "## r13.14.1\n\nChanges.\n"
            (source / "release-notes.md").write_text(release.release_notes(meta, chinese, english), encoding="utf-8")
            meta["notes_sha256"] = release.digest(source / "release-notes.md")
            release.write_json(source / "build-metadata.json", meta)
            (source / "SHA256SUMS").write_text(release.checksums(source, meta), encoding="ascii")
            (source / "attestation.sigstore.json").write_text("test-bundle")
            archive = io.BytesIO()
            with zipfile.ZipFile(archive, "w") as z:
                for path in source.iterdir():
                    z.writestr(path.name, path.read_bytes())
            data = archive.getvalue()
            artifacts = {"total_count": 1, "artifacts": [{"id": 45, "name": "a13-signed-123-2", "expired": False,
                                                          "digest": "sha256:" + hashlib.sha256(data).hexdigest()}]}
            verified = {key: meta[key] for key in ("apk", "apk_sha256", "apk_size", "certificate_sha256", "version_name", "version_code")}
            with patch.object(release, "native_source"), patch.object(release, "api", side_effect=[{"id": 9, "path": release.WORKFLOW}, BUILD_RUN, artifacts]), patch.object(release, "successful_jobs"), patch.object(release, "command", return_value=data), patch.object(release, "attest") as attest, patch.object(release, "source_file", side_effect=['val lastVersion = 142\nval lastVersionName = "r13.14.1"', chinese, english]), patch.object(release, "verify_apk", return_value=verified), patch.object(release, "output") as output:
                result = release.fetch_candidate("123", Path(td) / "download")
                self.assertEqual(45, result["artifact_id"])
                self.assertEqual(2, result["run_attempt"])
                attest.assert_called_once()
                self.assertEqual(45, output.call_args.kwargs["artifact_id"])
                self.assertEqual(meta, json.loads((Path(td) / "download/build-metadata.json").read_text()))

    def test_approval_receipt_binds_artifact_id_attempt_sha_and_apk(self):
        receipt = {"VERIFIED_SOURCE_SHA": SOURCE, "VERIFIED_APK_SHA256": "b" * 64,
                   "VERIFIED_RUN_ATTEMPT": "2", "VERIFIED_ARTIFACT_ID": "45"}
        release.check_receipt(metadata(), receipt)
        for field in receipt:
            with self.subTest(field=field), self.assertRaises(ValueError):
                release.check_receipt(metadata(), receipt | {field: "changed"})

    def test_attestation_binds_repository_workflow_sha_run_and_attempt(self):
        verified = {"signature": {"certificate": {"sourceRepositoryIdentifier": str(release.POLICY["repository_id"]) }}, "statement": {
            "predicateType": "https://slsa.dev/provenance/v1", "predicate": {
                "runDetails": {"metadata": {"invocationId": f"https://github.com/{release.REPO}/actions/runs/123/attempts/2"}},
                "buildDefinition": {"externalParameters": {"workflow": {"repository": f"https://github.com/{release.REPO}", "path": release.WORKFLOW, "ref": "refs/heads/main"}},
                                    "resolvedDependencies": [{"digest": {"gitCommit": SOURCE}}]}}}}
        release.check_attestation([{"verificationResult": verified}], metadata())
        for before, after in ((SOURCE, "b" * 40), ("attempts/2", "attempts/1"), ("refs/heads/main", "refs/pull/1/merge"), (release.WORKFLOW, "other.yml"), (str(release.POLICY["repository_id"]), "1")):
            bad = json.loads(json.dumps(verified).replace(before, after))
            with self.subTest(before=before), self.assertRaises(ValueError):
                release.check_attestation([{"verificationResult": bad}], metadata())


class PublishingTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)
        self.meta = metadata()
        for name in release.CANDIDATE_FILES | {self.meta["apk"]}:
            (self.folder / name).write_bytes(("original-" + name).encode())
        self.record = {"id": 10, "tag_name": "r13.14.1", "target_commitish": SOURCE, "draft": False,
                       "prerelease": False, "name": "r13.14.1", "html_url": "https://example.test/release",
                       "body": (self.folder / "release-notes.md").read_text(), "assets": []}
        for index, name in enumerate(sorted(release.RELEASE_FILES | {self.meta["apk"]})):
            path = self.folder / name
            self.record["assets"].append({"id": index, "name": path.name, "size": path.stat().st_size,
                                          "digest": "sha256:" + release.digest(path)})

    def asset_download(self, *args, **kwargs):
        index = int(args[2].split("/")[-1])
        return (self.folder / self.record["assets"][index]["name"]).read_bytes()

    def test_requires_explicit_matching_version_authorization(self):
        release.authorize(self.meta, "actions-only", "")
        release.authorize(self.meta, "both", "publish r13.14.1 from 123")
        for text in ("", "publish r13.13.0 from 123", "publish r13.14.1 from 124"):
            with self.assertRaises(ValueError):
                release.authorize(self.meta, "source", text)

    def test_same_name_different_content_rejected_and_draft_only_allows_missing_assets(self):
        with patch.object(release, "command", side_effect=self.asset_download):
            release.check_release(self.record, release.REPO, "r13.14.1", self.meta, self.folder, {})
            corrupt = copy.deepcopy(self.record)
            corrupt["assets"][0]["digest"] = "sha256:" + "0" * 64
            with self.assertRaises(ValueError):
                release.check_release(corrupt, release.REPO, "r13.14.1", self.meta, self.folder, {})
            partial = self.record | {"draft": True, "assets": self.record["assets"][:1]}
            release.check_release(partial, release.REPO, "r13.14.1", self.meta, self.folder, {}, partial=True)
            with self.assertRaises(ValueError):
                release.check_release(partial | {"draft": False}, release.REPO, "r13.14.1", self.meta, self.folder, {}, partial=True)

    def test_published_identical_release_is_read_only(self):
        with patch.object(release, "check_tag"), patch.object(release, "summary"), patch.object(release, "api") as api, patch.object(release, "command") as command:
            release.publish_one(release.REPO, "r13.14.1", self.meta, self.folder, {}, self.record)
            api.assert_not_called()
            command.assert_not_called()

    def test_public_release_has_exact_title_apk_and_metadata_while_evidence_stays_in_artifact(self):
        self.assertEqual({"CustoMIUIzer-A13-r13.14.1.apk", "build-metadata.json"},
                         {asset["name"] for asset in self.record["assets"]})
        with patch.object(release, "command", side_effect=self.asset_download):
            for bad in (self.record | {"name": "CustoMIUIzer A13 · r13.14.1"},
                        self.record | {"assets": self.record["assets"] + [{"name": "SHA256SUMS"}]}):
                with self.assertRaises(ValueError):
                    release.check_release(bad, release.REPO, "r13.14.1", self.meta, self.folder, {})

    def test_draft_recovery_uploads_only_missing_assets_and_checks_before_publication(self):
        draft = self.record | {"draft": True, "assets": self.record["assets"][:1]}
        complete = self.record | {"draft": True}
        mutation_order = []

        def api(endpoint, **kwargs):
            if kwargs.get("method") == "PATCH":
                mutation_order.append("publish")
                return self.record
            if endpoint.endswith("/latest") or "/tags/" in endpoint:
                return self.record
            return complete

        def command(*args, **kwargs):
            mutation_order.append("upload")
            return b"{}"

        with patch.object(release, "check_tag", return_value=True), patch.object(release, "check_release") as check, patch.object(release, "current_main"), patch.object(release, "api", side_effect=api), patch.object(release, "command", side_effect=command) as upload, patch.object(release, "summary"):
            release.publish_one(release.REPO, "r13.14.1", self.meta, self.folder, {}, draft)
            self.assertEqual(1, upload.call_count)
            self.assertEqual(["upload", "publish"], mutation_order)
            self.assertEqual(3, check.call_count)
            for call in upload.call_args_list:
                name = call.args[2].split("?name=")[1]
                self.assertNotIn(name, {asset["name"] for asset in draft["assets"]})

    def test_existing_tag_never_moves(self):
        with patch.object(release, "optional_api", return_value={"object": {"type": "commit", "sha": "b" * 40}}), self.assertRaisesRegex(ValueError, "never move"):
            release.check_tag(release.REPO, "r13.14.1", SOURCE, {})

    def test_duplicate_release_and_newer_version_are_rejected(self):
        for records in ([self.record, self.record], [self.record | {"tag_name": "r13.15.0"}]):
            with patch.object(release, "api", return_value=records), self.assertRaises(ValueError):
                release.find_release(release.REPO, "r13.14.1", self.meta, {})

    def test_missing_cross_token_blocks_before_source_mutation(self):
        with patch.dict(os.environ, {"RELEASE_AUTHORIZATION": "publish r13.14.1 from 123"}, clear=True), patch.object(release, "check_receipt"), patch.object(release, "environment_policy"), patch.object(release, "publish_one") as publish, self.assertRaisesRegex(ValueError, "A13_XPOSED_RELEASE_TOKEN"):
            release.publish(self.meta, self.folder, "both")
        publish.assert_not_called()

    def test_xposed_failure_retains_source_and_retry_uses_original_candidate_after_main_moves(self):
        env = {"RELEASE_AUTHORIZATION": "publish r13.14.1 from 123", "A13_XPOSED_RELEASE_TOKEN": "test-only"}
        order = []

        def publish(repository, *args):
            order.append(repository)
            if repository == release.DOWNLOAD:
                raise ValueError("interrupted Xposed upload")

        with patch.dict(os.environ, env), patch.object(release, "check_receipt"), patch.object(release, "environment_policy"), patch.object(release, "api", return_value={"permissions": {"push": True}}), patch.object(release, "check_tag"), patch.object(release, "find_release", side_effect=[self.record, None]), patch.object(release, "check_release"), patch.object(release, "main_ci"), patch.object(release, "current_main") as main, patch.object(release, "publish_one", side_effect=publish), patch.object(release, "build") as build, patch.object(release, "summary") as summary:
            with self.assertRaisesRegex(ValueError, "interrupted"):
                release.publish(self.meta, self.folder, "both")
            self.assertEqual([release.REPO, release.DOWNLOAD], order)
            main.assert_not_called()  # Recovery of an already published source is allowed.
            build.assert_not_called()
            self.assertIn("PARTIAL SUCCESS", summary.call_args.args[0])


if __name__ == "__main__":
    unittest.main()
