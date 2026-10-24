#!/usr/bin/env python3
"""Protected A13 signing and promotion. Ordinary CI never invokes signing.

Manual main dispatch -> exact Fast/Full CI -> approved signing -> immutable
attested candidate. A separate approved promotion consumes that candidate.
No replacement keys, force pushes, asset replacement, or automatic publication.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import io
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path
from urllib.parse import quote

try:
    from .verify_apk_provenance import verify_apk_provenance
except ImportError:
    from verify_apk_provenance import verify_apk_provenance

ROOT = Path(__file__).resolve().parent.parent
POLICY = json.loads((ROOT / "tools/release-policy.json").read_text(encoding="utf-8"))
REPO = POLICY["repository"]
DOWNLOAD = POLICY["download_repository"]
CERT = POLICY["certificate_sha256"]
WORKFLOW = ".github/workflows/a13-signed-build.yml"
SHA = re.compile(r"[0-9a-f]{40}")
VERSION = re.compile(r"r13\.(\d+)\.(\d+)")
SECRETS = ("A13_KEYSTORE_BASE64", "A13_STORE_PASSWORD", "A13_KEY_ALIAS", "A13_KEY_PASSWORD")
PUBLIC_FILES = {"build-metadata.json", "SHA256SUMS", "release-notes.md", "attestation.sigstore.json"}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def command(*args: str, env: dict | None = None, data: bytes | None = None) -> bytes:
    result = subprocess.run(list(args), cwd=ROOT, env=env, input=data, capture_output=True)
    # Never include arguments, environment, or child output in an exception:
    # keytool and Gradle diagnostics can contain signing values.
    require(result.returncode == 0, f"{Path(args[0]).name} failed; operation stopped")
    return result.stdout


def api(endpoint: str, *, env: dict | None = None, method: str = "GET", body=None):
    args = ["gh", "api", endpoint, "--method", method]
    if body is not None:
        args += ["--input", "-"]
    return json.loads(command(*args, env=env, data=None if body is None else json.dumps(body).encode()))


def optional_api(endpoint: str, *, env: dict | None = None):
    result = subprocess.run(["gh", "api", endpoint], cwd=ROOT, env=env, capture_output=True)
    if result.returncode == 0:
        return json.loads(result.stdout)
    require(b"(HTTP 404)" in result.stderr, "GitHub request failed; absence was not established")
    return None


def digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def write_json(path: Path, value) -> None:
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def output(**values) -> None:
    if os.environ.get("GITHUB_OUTPUT"):
        with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as stream:
            for key, value in values.items():
                stream.write(f"{key}={value}\n")


def summary(message: str) -> None:
    print(message)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a", encoding="utf-8") as stream:
            stream.write(message + "\n\n")


def native_source(env: dict = os.environ) -> str:
    require(env.get("GITHUB_REPOSITORY") == REPO
            and env.get("GITHUB_REPOSITORY_ID") == str(POLICY["repository_id"])
            and env.get("GITHUB_EVENT_NAME") == "workflow_dispatch"
            and env.get("GITHUB_REF") == "refs/heads/main", "Only native main workflow_dispatch is trusted")
    sha = env.get("GITHUB_SHA", "")
    require(SHA.fullmatch(sha) is not None, "A full native source SHA is required")
    require(command("git", "rev-parse", "HEAD").decode().strip() == sha, "Checkout differs from native source SHA")
    require(not command("git", "status", "--porcelain", "--untracked-files=no").strip(), "Tracked checkout is dirty")
    return sha


def current_main(sha: str) -> None:
    require(api(f"repos/{REPO}/git/ref/heads/main")["object"]["sha"] == sha,
            "main moved; run complete CI and confirm its new exact SHA")


def check_environment(record: dict, branches: dict) -> None:
    require(any(rule.get("type") == "required_reviewers" and rule.get("reviewers")
                for rule in record.get("protection_rules", [])), "Environment requires human reviewers")
    require(record.get("can_admins_bypass") is False, "Environment must disable admin bypass")
    require(record.get("deployment_branch_policy") == {"protected_branches": False, "custom_branch_policies": True},
            "Environment requires an explicit main-only branch policy")
    rules = branches.get("branch_policies", [])
    require(branches.get("total_count") == 1 and len(rules) == 1
            and rules[0].get("name") == "main" and rules[0].get("type") == "branch",
            "Environment must allow only the main branch, with no tag/wildcard rules")


def environment_policy(name: str) -> None:
    check_environment(api(f"repos/{REPO}/environments/{name}"),
                      api(f"repos/{REPO}/environments/{name}/deployment-branch-policies?per_page=100"))


def check_run(record: dict, workflow: dict, sha: str, events: set[str]) -> None:
    require(record.get("workflow_id") == workflow["id"] and record.get("path") == workflow["path"]
            and record.get("head_sha") == sha and record.get("head_branch") == "main"
            and record.get("head_repository", {}).get("full_name") == REPO
            and record.get("head_repository", {}).get("id") == POLICY["repository_id"]
            and record.get("event") in events and record.get("status") == "completed"
            and record.get("conclusion") == "success", "Run is not successful native exact-main CI/build")


def successful_jobs(record: dict, expected: dict[str, set[str]]) -> None:
    jobs = api(f"repos/{REPO}/actions/runs/{record['id']}/attempts/{record['run_attempt']}/jobs?per_page=100")
    require(jobs.get("total_count") == len(expected) and len(jobs["jobs"]) == len(expected), "Unexpected or missing jobs")
    require({job["name"] for job in jobs["jobs"]} == set(expected), "Wrong CI/build jobs")
    for job in jobs["jobs"]:
        require(job.get("status") == "completed" and job.get("conclusion") == "success", "A required job was skipped or failed")
        passed = {step["name"] for step in job.get("steps", []) if step.get("conclusion") == "success"}
        require(expected[job["name"]] <= passed, "A required verification/build step was skipped or failed")


def main_ci(sha: str) -> dict:
    receipts = {}
    for filename, job, required_steps in (
        ("a13-fast-ci.yml", "fast", {"Full local verifier", "Python tool tests", "Static governance checks"}),
        ("a13-full-ci.yml", "full", {"Full verifier", "R8 without APK assembly or signing", "Python tool tests"}),
    ):
        workflow = api(f"repos/{REPO}/actions/workflows/{filename}")
        require(workflow["path"] == f".github/workflows/{filename}" and workflow.get("state") == "active", "Wrong CI workflow")
        records = api(f"repos/{REPO}/actions/workflows/{filename}/runs?head_sha={sha}&branch=main&per_page=100")["workflow_runs"]
        found = None
        for record in records:
            try:
                # Re-read instead of trusting a workflow_run event or list entry.
                fresh = api(f"repos/{REPO}/actions/runs/{record['id']}")
                check_run(fresh, workflow, sha, {"push", "workflow_dispatch"})
                successful_jobs(fresh, {job: required_steps})
            except ValueError:
                continue
            found = fresh
            break
        require(found is not None, f"No fully successful {filename} on the exact main SHA")
        receipts[filename] = {"run_id": found["id"], "run_attempt": found["run_attempt"]}
    return receipts


def source_version(text: str) -> tuple[str, int]:
    code = re.search(r"val\s+lastVersion\s*=\s*(\d+)", text)
    name = re.search(r'val\s+lastVersionName\s*=\s*"(r13\.\d+\.\d+)"', text)
    require(code is not None and name is not None, "Missing A13 version metadata")
    return name[1], int(code[1])


def preflight() -> str:
    sha = native_source()
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
    require(event.get("inputs", {}).get("source_sha") == sha, "Confirmed source SHA differs from native main SHA")
    current_main(sha)
    environment_policy("release-signing")
    main_ci(sha)
    name, code = source_version((ROOT / "app/build.gradle.kts").read_text(encoding="utf-8"))
    require(code > POLICY["baseline_version_code"] and name != POLICY["baseline_version"], "Bump version before signing a new candidate")
    require(find_release(REPO, name, {"version_name": name, "version_code": code}, dict(os.environ)) is None
            and optional_api(f"repos/{REPO}/git/ref/tags/{name}") is None,
            "Version already has a release/tag; recover its original signed candidate instead of rebuilding")
    current_main(sha)
    output(source_sha=sha)
    summary(f"Signing request: {name} ({code}), exact main `{sha}`. Native Fast/Full CI and release-signing protection verified; human Environment approval is required.")
    return sha


def property_escape(value: str) -> str:
    escaped = []
    # Properties.load(InputStream) is ISO-8859-1; emit UTF-16 Unicode escapes.
    encoded = value.encode("utf-16-be")
    for unit in (int.from_bytes(encoded[i:i + 2], "big") for i in range(0, len(encoded), 2)):
        char = chr(unit)
        if char in "\\ :=#!":
            escaped.append("\\" + char)
        elif char in "\t\n\r\f":
            escaped.append({"\t": "\\t", "\n": "\\n", "\r": "\\r", "\f": "\\f"}[char])
        elif unit < 32 or unit > 126:
            escaped.append(f"\\u{unit:04x}")
        else:
            escaped.append(char)
    return "".join(escaped)


def signing_directory() -> Path:
    temp = Path(os.environ["RUNNER_TEMP"]).resolve(strict=True)
    folder = temp / "a13-signing"
    require(not folder.is_symlink() and folder.resolve().parent == temp, "Invalid temporary signing directory")
    return folder


def cleanup() -> None:
    folder = signing_directory()
    if folder.exists():
        shutil.rmtree(folder)


def prepare_signing(env: dict = os.environ) -> Path:
    require(all(env.get(key) for key in SECRETS), "Missing A13 signing Environment secret; no replacement key is permitted")
    folder = signing_directory()
    folder.mkdir(mode=0o700, exist_ok=False)
    store = folder / "release.p12"
    try:
        encoded = "".join(env["A13_KEYSTORE_BASE64"].split())
        decoded = base64.b64decode(encoded, validate=True)
        # PKCS#12 is ASN.1 DER. Reject JKS (and empty/other formats) before
        # keytool's compatibility auto-detection can accept a different store.
        require(decoded[:1] == b"\x30", "Signing secret must contain a PKCS#12 store")
        store.write_bytes(decoded)
        store.chmod(0o600)
        certificate = command("keytool", "-exportcert", "-storetype", "PKCS12", "-keystore", str(store),
                              "-storepass:env", "A13_STORE_PASSWORD", "-alias", env["A13_KEY_ALIAS"], env=env)
        require(hashlib.sha256(certificate).hexdigest() == CERT, "A13 signing certificate mismatch; refusing to build")
        properties = {"storeFile": str(store), "storePassword": env["A13_STORE_PASSWORD"],
                      "keyAlias": env["A13_KEY_ALIAS"], "keyPassword": env["A13_KEY_PASSWORD"]}
        config = folder / "keystore.properties"
        config.write_text("".join(f"{key}={property_escape(value)}\n" for key, value in properties.items()), encoding="ascii")
        config.chmod(0o600)
        return config
    except Exception:
        cleanup()
        raise


def build() -> None:
    # Revalidate after approval, immediately before touching any signing secret.
    sha = preflight()
    try:
        config = prepare_signing()
        build_env = {key: value for key, value in os.environ.items() if key not in SECRETS}
        build_env["CUSTOMIUIZER_A13_KEYSTORE_PROPERTIES"] = str(config)
        # Never save Gradle caches containing signing task inputs/configuration.
        build_env["GRADLE_USER_HOME"] = str(signing_directory() / "gradle-home")
        gradlew = str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew"))
        command(gradlew, "--no-daemon", "--no-configuration-cache", "--no-build-cache", "--no-scan",
                "--project-cache-dir", str(signing_directory() / "project-cache"),
                ":app:assembleRelease", ":app:lintVitalRelease", "-PofficialRelease=true",
                f"-PreleaseSourceSha={sha}", env=build_env)
    finally:
        cleanup()


def sdk_tool(name: str) -> str:
    base = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    require(bool(base), "Android SDK is required for APK verification")
    suffix = ".bat" if os.name == "nt" and name == "apksigner" else ".exe" if os.name == "nt" else ""
    tool = Path(base) / "build-tools/36.0.0" / (name + suffix)
    require(tool.is_file(), f"Missing pinned Android tool: {name}")
    return str(tool)


def check_signature(text: str) -> None:
    signers = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)", text)
    require(signers == [CERT] and "Verified using v2 scheme (APK Signature Scheme v2): true" in text,
            "APK must have the one expected certificate and a valid v2 signature")


def check_badging(text: str, name: str, code: int) -> None:
    match = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", text)
    require(match is not None and match.groups() == ("tv.withaibuild.customiuizer.r13", str(code), name), "APK package/version mismatch")
    # Current A13 has no native libraries. If libraries are added, only the
    # declared arm64-v8a ABI is allowed. New aapt2 uses minSdkVersion.
    require(re.search(r"(?:sdkVersion|minSdkVersion):'33'", text) is not None and "targetSdkVersion:'34'" in text
            and re.findall(r"native-code: (.+)", text) in ([], ["'arm64-v8a'"])
            and "application-debuggable" not in text and "application-testOnly" not in text,
            "APK SDK, ABI or release flags differ")


def verify_apk(apk: Path, sha: str, name: str, code: int) -> dict:
    check_signature(command(sdk_tool("apksigner"), "verify", "--verbose", "--print-certs", str(apk)).decode())
    command(sdk_tool("zipalign"), "-c", "-P", "16", "4", str(apk))
    check_badging(command(sdk_tool("aapt2"), "dump", "badging", str(apk)).decode(), name, code)
    verify_apk_provenance(apk, sha, name, code)
    with zipfile.ZipFile(apk) as archive:
        native = {name.split("/")[1] for name in archive.namelist() if name.startswith("lib/") and name.endswith(".so")}
        require(native <= {"arm64-v8a"}, "APK contains an unsupported native ABI")
        require(archive.read("META-INF/xposed/java_init.list").decode().strip() == "tv.withaibuild.customiuizer.MainModule", "APK Xposed entry differs")
        props = archive.read("META-INF/xposed/module.prop").decode()
        require("minApiVersion=102" in props and "targetApiVersion=102" in props, "APK Xposed API differs")
    return {"apk": apk.name, "apk_sha256": digest(apk), "apk_size": apk.stat().st_size,
            "certificate_sha256": CERT, "version_name": name, "version_code": code}


def changelog_section(text: str, version: str) -> str:
    match = re.search(rf"(?ms)^## {re.escape(version)}(?:\s|$).*?(?=^## |\Z)", text)
    require(match is not None, f"Missing changelog section for {version}")
    return match[0].strip()


def release_notes(metadata: dict, chinese: str, english: str) -> str:
    return (f"## 简体中文\n\n{changelog_section(chinese, metadata['version_name'])}\n\n"
            f"## English\n\n{changelog_section(english, metadata['version_name'])}\n\n"
            f"## APK / Provenance\n\nSource: `{metadata['source_sha']}`\n\n"
            f"[Signed build](https://github.com/{REPO}/actions/runs/{metadata['run_id']}/attempts/{metadata['run_attempt']})\n\n"
            f"APK: `{metadata['apk']}`\n\nSHA-256: `{metadata['apk_sha256']}`\n\n"
            f"Certificate SHA-256: `{CERT}`\n")


def candidate(folder: Path) -> None:
    sha = native_source()
    current_main(sha)
    apks = list((ROOT / "app/build/outputs/apk/release").glob("*.apk"))
    require(len(apks) == 1, "Expected exactly one Release APK")
    for name in ("mapping.txt", "configuration.txt", "usage.txt"):
        require((ROOT / "app/build/outputs/mapping/release" / name).is_file(), "Missing R8 output")
    version, code = source_version((ROOT / "app/build.gradle.kts").read_text(encoding="utf-8"))
    folder.mkdir(exist_ok=False)
    apk = folder / f"CustoMIUIzer-A13-{version}-{sha[:6]}.apk"
    shutil.copyfile(apks[0], apk)
    metadata = verify_apk(apk, sha, version, code)
    metadata.update(schema=1, repository=REPO, repository_id=POLICY["repository_id"], source_sha=sha,
                    source_ref="refs/heads/main", workflow=WORKFLOW, run_id=int(os.environ["GITHUB_RUN_ID"]),
                    run_attempt=int(os.environ["GITHUB_RUN_ATTEMPT"]), ci=main_ci(sha))
    notes = folder / "release-notes.md"
    notes.write_text(release_notes(metadata, *( (ROOT / f).read_text(encoding="utf-8")
                                             for f in ("CHANGELOG.md", "CHANGELOG_EN.md"))), encoding="utf-8")
    metadata["notes_sha256"] = digest(notes)
    write_json(folder / "build-metadata.json", metadata)
    (folder / "SHA256SUMS").write_text(checksums(folder, metadata), encoding="ascii")
    summary(f"Verified {version} ({code}); source `{sha}`; APK SHA-256 `{metadata['apk_sha256']}`. Candidate only; no Release created.")


def checksums(folder: Path, metadata: dict) -> str:
    return "".join(f"{digest(folder / name)}  {name}\n" for name in (metadata["apk"], "build-metadata.json", "release-notes.md"))


def check_attestation(results: list, metadata: dict) -> None:
    invocation = f"https://github.com/{REPO}/actions/runs/{metadata['run_id']}/attempts/{metadata['run_attempt']}"
    for result in results:
        verified = result.get("verificationResult", {})
        statement = verified.get("statement", {})
        predicate = statement.get("predicate", {})
        definition = predicate.get("buildDefinition", {})
        if (str(verified.get("signature", {}).get("certificate", {}).get("sourceRepositoryIdentifier")) == str(POLICY["repository_id"])
                and statement.get("predicateType") == "https://slsa.dev/provenance/v1"
                and predicate.get("runDetails", {}).get("metadata", {}).get("invocationId") == invocation
                and definition.get("externalParameters", {}).get("workflow") ==
                {"repository": f"https://github.com/{REPO}", "path": WORKFLOW, "ref": "refs/heads/main"}
                and any(item.get("digest", {}).get("gitCommit") == metadata["source_sha"]
                        for item in definition.get("resolvedDependencies", []))):
            return
    raise ValueError("Verified attestation has another repository/workflow/source/run/attempt")


def attest(folder: Path, metadata: dict) -> None:
    for name in (metadata["apk"], "build-metadata.json"):
        verified = json.loads(command("gh", "attestation", "verify", str(folder / name), "--repo", REPO,
            "--bundle", str(folder / "attestation.sigstore.json"), "--signer-workflow", f"{REPO}/{WORKFLOW}",
            "--signer-digest", metadata["source_sha"], "--source-digest", metadata["source_sha"],
            "--source-ref", "refs/heads/main", "--deny-self-hosted-runners", "--format", "json"))
        check_attestation(verified, metadata)


def source_file(path: str, sha: str) -> str:
    record = api(f"repos/{REPO}/contents/{path}?ref={sha}")
    return base64.b64decode(record["content"]).decode("utf-8")


def check_metadata(metadata: dict, record: dict) -> None:
    expected = {"schema": 1, "repository": REPO, "repository_id": POLICY["repository_id"], "source_sha": record["head_sha"],
                "source_ref": "refs/heads/main", "workflow": WORKFLOW, "run_id": record["id"],
                "run_attempt": record["run_attempt"], "certificate_sha256": CERT}
    require(all(metadata.get(key) == value for key, value in expected.items()), "Candidate metadata differs from selected build")
    require(VERSION.fullmatch(metadata.get("version_name", "")) is not None, "Invalid A13 version")
    require(metadata.get("apk") == f"CustoMIUIzer-A13-{metadata['version_name']}-{record['head_sha'][:6]}.apk", "Wrong candidate APK name")


def extract_candidate(data: bytes, folder: Path) -> None:
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        entries = archive.infolist()
        names = [entry.filename for entry in entries]
        apk_names = [name for name in names if re.fullmatch(r"CustoMIUIzer-A13-r13\.\d+\.\d+-[0-9a-f]{6}\.apk", name)]
        require(len(apk_names) == 1 and len(names) == 5 and set(names) == PUBLIC_FILES | set(apk_names), "Unexpected candidate files")
        require(sum(entry.file_size for entry in entries) < 64 * 1024 * 1024
                and all((entry.external_attr >> 16) & 0o170000 != 0o120000 for entry in entries), "Unsafe candidate archive")
        folder.mkdir(exist_ok=False)
        for entry in entries:
            (folder / entry.filename).write_bytes(archive.read(entry))


def fetch_candidate(run_id: str, folder: Path) -> dict:
    require(run_id.isdecimal() and int(run_id) > 0, "Build run ID must be a positive integer")
    native_source()
    workflow = api(f"repos/{REPO}/actions/workflows/a13-signed-build.yml")
    record = api(f"repos/{REPO}/actions/runs/{run_id}")
    require(workflow["path"] == WORKFLOW and SHA.fullmatch(record.get("head_sha", "")) is not None, "Wrong build workflow/source")
    check_run(record, workflow, record["head_sha"], {"workflow_dispatch"})
    successful_jobs(record, {"validate": {"Check exact main and CI"}, "sign": {
        "Build official APK once", "Verify signed APK", "Attest APK and metadata", "Verify attestation", "Upload signed candidate"}})
    artifacts = api(f"repos/{REPO}/actions/runs/{run_id}/artifacts?per_page=100")
    require(artifacts.get("total_count", 0) <= 100, "Ambiguous artifact listing")
    matches = [item for item in artifacts["artifacts"]
               if item["name"] == f"a13-signed-{run_id}-{record['run_attempt']}" and not item["expired"]]
    require(len(matches) == 1, "Original signed artifact missing/expired; do not rebuild for recovery")
    artifact = matches[0]
    archive = command("gh", "api", f"repos/{REPO}/actions/artifacts/{artifact['id']}/zip")
    require(artifact.get("digest") == "sha256:" + hashlib.sha256(archive).hexdigest(), "Artifact archive digest mismatch")
    extract_candidate(archive, folder)
    metadata = json.loads((folder / "build-metadata.json").read_text(encoding="utf-8"))
    check_metadata(metadata, record)
    attest(folder, metadata)
    version, code = source_version(source_file("app/build.gradle.kts", metadata["source_sha"]))
    require((metadata["version_name"], metadata["version_code"]) == (version, code), "Candidate version differs from source")
    require(verify_apk(folder / metadata["apk"], metadata["source_sha"], version, code) ==
            {key: metadata[key] for key in ("apk", "apk_sha256", "apk_size", "certificate_sha256", "version_name", "version_code")}, "APK damaged or replaced")
    require((folder / "SHA256SUMS").read_text(encoding="ascii") == checksums(folder, metadata)
            and digest(folder / "release-notes.md") == metadata["notes_sha256"], "Candidate checksum/notes mismatch")
    expected_notes = release_notes(metadata, *(source_file(f, metadata["source_sha"]) for f in ("CHANGELOG.md", "CHANGELOG_EN.md")))
    require((folder / "release-notes.md").read_text(encoding="utf-8") == expected_notes, "Release notes differ from exact source")
    metadata["artifact_id"] = artifact["id"]  # In-memory receipt, never rewrite attested metadata.
    output(source_sha=metadata["source_sha"], apk_sha256=metadata["apk_sha256"],
           run_attempt=metadata["run_attempt"], artifact_id=artifact["id"])
    return metadata


def authorize(metadata: dict, destination: str, authorization: str) -> None:
    require(destination in {"actions-only", "source", "both"}, "Unknown release destination")
    if destination != "actions-only":
        require(authorization == f"publish {metadata['version_name']} from {metadata['run_id']}", "Explicit version/build publication authorization is required")


def check_receipt(metadata: dict, env: dict = os.environ) -> None:
    expected = {"VERIFIED_SOURCE_SHA": "source_sha", "VERIFIED_APK_SHA256": "apk_sha256",
                "VERIFIED_RUN_ATTEMPT": "run_attempt", "VERIFIED_ARTIFACT_ID": "artifact_id"}
    require(all(env.get(key) == str(metadata[value]) for key, value in expected.items()), "Original verified candidate/attempt/artifact changed after approval")


def check_tag(repository: str, tag: str, sha: str, env: dict, *, must_exist: bool = False) -> bool:
    record = optional_api(f"repos/{repository}/git/ref/tags/{tag}", env=env)
    if record is None:
        require(not must_exist, "Expected release tag is missing")
        return False
    obj = record["object"]
    for _ in range(4):
        if obj["type"] != "tag":
            break
        obj = api(f"repos/{repository}/git/tags/{obj['sha']}", env=env)["object"]
    require(obj["type"] == "commit" and obj["sha"] == sha, "Existing tag points elsewhere; never move it")
    return True


def find_release(repository: str, tag: str, metadata: dict, env: dict):
    found = None
    for page in range(1, 101):
        records = api(f"repos/{repository}/releases?per_page=100&page={page}", env=env)
        for record in records:
            if record["tag_name"] == tag:
                require(found is None, "Duplicate release for tag")
                found = record
            elif not record["draft"]:
                existing = record["tag_name"]
                if repository == DOWNLOAD:
                    match = re.fullmatch(r"(\d+)-r13\.\d+\.\d+", existing)
                    require(match is None or int(match[1]) < metadata["version_code"], "Xposed has a newer/equal versionCode")
                elif VERSION.fullmatch(existing):
                    require(tuple(map(int, VERSION.fullmatch(existing).groups())) <
                            tuple(map(int, VERSION.fullmatch(metadata["version_name"]).groups())), "Source has a newer/equal version")
        if len(records) < 100:
            return found
    raise ValueError("Release history exceeds bounded scan")


def check_release(record: dict, repository: str, tag: str, metadata: dict, folder: Path, env: dict, *, partial: bool = False) -> None:
    require(record.get("tag_name") == tag and record.get("target_commitish") == metadata["source_sha"]
            and record.get("name") == f"CustoMIUIzer A13 · {metadata['version_name']}"
            and record.get("body") == (folder / "release-notes.md").read_text(encoding="utf-8")
            and record.get("prerelease") is False, "Existing release source/title/notes/state differ")
    files = PUBLIC_FILES | {metadata["apk"]}
    assets = record.get("assets", [])
    names = [asset["name"] for asset in assets]
    require(len(names) == len(set(names)) and set(names) <= files
            and (partial and record["draft"] or set(names) == files), "Release has missing, duplicate, or unrelated assets")
    for asset in assets:
        local = folder / asset["name"]
        require(asset["size"] == local.stat().st_size and asset.get("digest") == "sha256:" + digest(local), "Same-name release asset has different bytes")
        binary = command("gh", "api", f"repos/{repository}/releases/assets/{asset['id']}",
                         "-H", "Accept: application/octet-stream", env=env)
        require(hashlib.sha256(binary).hexdigest() == digest(local), "Downloaded release asset differs from original candidate")


def publish_one(repository: str, tag: str, metadata: dict, folder: Path, env: dict, existing) -> None:
    sha = metadata["source_sha"]
    if existing is not None and not existing["draft"]:
        check_tag(repository, tag, sha, env, must_exist=True)
        summary(f"Already verified; unchanged: {existing['html_url']}")
        return
    if not check_tag(repository, tag, sha, env):
        if repository == REPO:
            api(f"repos/{repository}/git/refs", env=env, method="POST", body={"ref": f"refs/tags/{tag}", "sha": sha})
        else:
            command("git", "fetch", "--no-tags", f"https://github.com/{REPO}.git", sha)
            command("git", "-c", "credential.helper=", "-c", "credential.helper=!gh auth git-credential",
                    "push", f"https://github.com/{repository}.git", f"{sha}:refs/tags/{tag}", env=env)
    check_tag(repository, tag, sha, env, must_exist=True)
    record = existing or api(f"repos/{repository}/releases", env=env, method="POST", body={
        "tag_name": tag, "target_commitish": sha, "name": f"CustoMIUIzer A13 · {metadata['version_name']}",
        "body": (folder / "release-notes.md").read_text(encoding="utf-8"), "draft": True, "prerelease": False})
    require(record.get("draft") is True, "Expected a draft release")
    check_release(record, repository, tag, metadata, folder, env, partial=True)
    present = {asset["name"] for asset in record.get("assets", [])}
    for name in sorted((PUBLIC_FILES | {metadata["apk"]}) - present):
        command("gh", "api", f"https://uploads.github.com/repos/{repository}/releases/{record['id']}/assets?name={quote(name)}",
                "--method", "POST", "-H", "Content-Type: application/octet-stream", "--input", str(folder / name), env=env)
    ready = api(f"repos/{repository}/releases/{record['id']}", env=env)
    require(ready.get("draft") is True, "Draft state changed during upload")
    check_release(ready, repository, tag, metadata, folder, env)
    if repository == REPO:
        current_main(sha)
    check_tag(repository, tag, sha, env, must_exist=True)
    api(f"repos/{repository}/releases/{record['id']}", env=env, method="PATCH", body={"draft": False, "prerelease": False, "make_latest": "true"})
    published = api(f"repos/{repository}/releases/tags/{tag}", env=env)
    require(published.get("id") == record["id"] and published.get("draft") is False, "Public release readback differs")
    check_release(published, repository, tag, metadata, folder, env)
    check_tag(repository, tag, sha, env, must_exist=True)
    require(api(f"repos/{repository}/releases/latest", env=env)["id"] == record["id"], "Stable release is not Latest")
    summary(f"Published and downloaded assets verified: {published['html_url']}; APK SHA-256 `{metadata['apk_sha256']}`")


def publish(metadata: dict, folder: Path, destination: str) -> None:
    authorize(metadata, destination, os.environ.get("RELEASE_AUTHORIZATION", ""))
    check_receipt(metadata)
    environment_policy("release-publishing")
    require(destination != "actions-only", "actions-only cannot enter publishing")
    repositories = [(REPO, metadata["version_name"], dict(os.environ))]
    if destination == "both":
        token = os.environ.get("A13_XPOSED_RELEASE_TOKEN")
        require(bool(token), "Missing A13_XPOSED_RELEASE_TOKEN in release-publishing")
        cross_env = dict(os.environ, GH_TOKEN=token)
        require(api(f"repos/{DOWNLOAD}", env=cross_env).get("permissions", {}).get("push") is True, "Xposed Contents/Workflows write permission is required")
        repositories.append((DOWNLOAD, f"{metadata['version_code']}-{metadata['version_name']}", cross_env))
    existing = {}
    for repository, tag, env in repositories:
        check_tag(repository, tag, metadata["source_sha"], env)
        record = find_release(repository, tag, metadata, env)
        if record is not None:
            check_release(record, repository, tag, metadata, folder, env, partial=record["draft"])
        existing[repository] = record
    source = existing[REPO]
    if source is None or source["draft"]:
        current_main(metadata["source_sha"])
        latest = api(f"repos/{REPO}/releases/latest")
        _, previous_code = source_version(source_file("app/build.gradle.kts", latest["target_commitish"]))
        require(metadata["version_code"] > previous_code, "versionCode must increase from the latest release")
    # Old main is accepted only for recovery after its unchanged source Release
    # has been fully verified. Exact successful CI is always required.
    main_ci(metadata["source_sha"])
    for repository, tag, env in repositories:
        try:
            publish_one(repository, tag, metadata, folder, env, existing[repository])
        except Exception:
            if repository == DOWNLOAD:
                summary(f"PARTIAL SUCCESS: source Release retained. Xposed sync failed; retry promotion of original build {metadata['run_id']}. Do not rebuild, re-sign, move tags, or delete the source Release.")
            raise


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["preflight", "build", "cleanup", "candidate", "attest", "fetch", "publish"])
    parser.add_argument("--directory", type=Path)
    args = parser.parse_args()
    try:
        if args.command == "preflight":
            preflight()
        elif args.command == "build":
            build()
        elif args.command == "cleanup":
            cleanup()
        elif args.command == "candidate":
            candidate(args.directory)
        elif args.command == "attest":
            attest(args.directory, json.loads((args.directory / "build-metadata.json").read_text(encoding="utf-8")))
        else:
            native_source()
            metadata = fetch_candidate(os.environ["BUILD_RUN_ID"], args.directory)
            destination = os.environ["RELEASE_DESTINATION"]
            authorize(metadata, destination, os.environ.get("RELEASE_AUTHORIZATION", ""))
            if args.command == "publish":
                publish(metadata, args.directory, destination)
            else:
                summary(f"Original {metadata['version_name']} ({metadata['version_code']}), build {metadata['run_id']}/attempt {metadata['run_attempt']}, artifact {metadata['artifact_id']}. Source `{metadata['source_sha']}`; APK SHA-256 `{metadata['apk_sha256']}`. Full provenance verified; destination {destination}.")
        return 0
    except (ValueError, OSError, KeyError, zipfile.BadZipFile) as error:
        # Exception types produced by external commands never include secrets.
        print(f"A13 release stopped: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
