#!/usr/bin/env python3
"""Structural, fail-closed exceptions for the two protected release workflows.

A filename alone never exempts a workflow from ordinary CI signing restrictions.
PyYAML is a pinned development/CI dependency, not an Android dependency.
"""
from __future__ import annotations

from pathlib import Path

GUARD = "github.repository == 'thetvplus/customiuizer-a13' && github.ref == 'refs/heads/main' && github.event_name == 'workflow_dispatch'"
PUBLISH_GUARD = GUARD + " && inputs.destination != 'actions-only' && inputs.authorization != ''"
FILES = {"a13-signed-build.yml", "a13-promote-release.yml"}
TOKEN = {"GH_TOKEN": "${{ github.token }}"}
SECRETS = {name: "${{ secrets." + name + " }}" for name in (
    "A13_KEYSTORE_BASE64", "A13_STORE_PASSWORD", "A13_KEY_ALIAS", "A13_KEY_PASSWORD")}
PROMOTION = {"BUILD_RUN_ID": "${{ inputs.build_run_id }}", "RELEASE_DESTINATION": "${{ inputs.destination }}",
             "RELEASE_AUTHORIZATION": "${{ inputs.authorization }}"}
RECEIPT = {"VERIFIED_" + key.upper(): "${{ needs.verify.outputs." + key + " }}"
           for key in ("source_sha", "apk_sha256", "run_attempt", "artifact_id")}
CHECKOUT = {"ref": "${{ github.sha }}", "fetch-depth": "0", "persist-credentials": "false"}
SDK = {"packages": "", "log-accepted-android-sdk-licenses": "false"}
UPLOAD = {"name": "a13-signed-${{ github.run_id }}-${{ github.run_attempt }}",
          "path": "\n".join("${{ runner.temp }}/candidate/" + name for name in (
              "*.apk", "SHA256SUMS", "build-metadata.json", "release-notes.md", "attestation.sigstore.json")),
          "if-no-files-found": "error", "retention-days": "30", "compression-level": "0"}
ATTEST = {"subject-path": "${{ runner.temp }}/candidate/*.apk\n${{ runner.temp }}/candidate/build-metadata.json",
          "create-storage-record": "false"}


def parse_workflow(text: str) -> dict:
    import yaml

    class UniqueLoader(yaml.BaseLoader):
        def construct_mapping(self, node, deep=False):
            mapping = {}
            for key_node, value_node in node.value:
                key = self.construct_object(key_node, deep=deep)
                if key in mapping:
                    raise ValueError("Duplicate YAML key")
                mapping[key] = self.construct_object(value_node, deep=deep)
            return mapping

    try:
        # Aliases/anchors/explicit tags add no value to these security boundaries.
        if any(isinstance(token, (yaml.AliasToken, yaml.AnchorToken, yaml.TagToken)) for token in yaml.scan(text)):
            raise ValueError("YAML aliases/anchors/tags are not permitted")
        result = yaml.load(text, Loader=UniqueLoader)
        if not isinstance(result, dict):
            raise ValueError("Workflow is not a mapping")
        return result
    except yaml.YAMLError as error:
        raise ValueError("Invalid workflow YAML") from error


def protected_workflow_errors(path: Path, text: str) -> list[str]:
    if path.name not in FILES:
        return []
    errors = []

    def check(ok: bool, message: str):
        if not ok:
            errors.append("CI_RELEASE_CONTRACT: " + message)

    try:
        data = parse_workflow(text)
        signing = path.name == "a13-signed-build.yml"
        check(set(data) <= {"name", "run-name", "on", "permissions", "concurrency", "jobs"}, "unexpected workflow scope")
        check(set(data["on"]) == {"workflow_dispatch"}, "only manual workflow_dispatch is allowed")
        inputs = data["on"]["workflow_dispatch"]["inputs"]
        if signing:
            check(set(inputs) == {"source_sha"} and inputs["source_sha"]["required"] == "true"
                  and inputs["source_sha"]["type"] == "string", "signing requires exact SHA input")
        else:
            check(set(inputs) == {"build_run_id", "destination", "authorization"}
                  and inputs["build_run_id"]["required"] == "true"
                  and inputs["destination"]["default"] == "actions-only"
                  and inputs["destination"]["options"] == ["actions-only", "source", "both"], "promotion input/default policy changed")
        check(data["permissions"] == {"contents": "read"}, "default permissions must be read-only")
        check(data["concurrency"] == {"group": "a13-signed-build" if signing else "a13-release-publish",
                                      "cancel-in-progress": "false"}, "release operations must serialize without cancellation")
        jobs = data["jobs"]
        check(set(jobs) == ({"validate", "sign"} if signing else {"verify", "publish"}), "unexpected release jobs")
        for name, job in jobs.items():
            check(set(job) <= {"if", "runs-on", "timeout-minutes", "permissions", "steps", "environment", "needs", "outputs"}, f"{name}: unexpected job scope")
            check(job["if"] == (PUBLISH_GUARD if name == "publish" else GUARD), f"{name}: native main guard changed")
            check(job["runs-on"] == "ubuntu-24.04" and 0 < int(job["timeout-minutes"]) <= 60, f"{name}: runner/timeout changed")
            permissions = {"contents": "read", "actions": "read"}
            if name == "sign":
                permissions.update({"id-token": "write", "attestations": "write"})
            elif name == "verify":
                permissions["attestations"] = "read"
            elif name == "publish":
                permissions.update({"contents": "write", "attestations": "read"})
            check(job["permissions"] == permissions, f"{name}: excessive or missing permissions")
            if name in {"sign", "publish"}:
                check(job.get("needs") == ("validate" if signing else "verify"), f"{name}: missing validation dependency")
                check(job.get("environment") == ("release-signing" if signing else "release-publishing"), f"{name}: missing protected Environment")
            else:
                check("environment" not in job and "needs" not in job, f"{name}: validation must have no Environment/secret dependency")
            if name == "verify":
                check(job.get("outputs") == {key: "${{ steps.candidate.outputs." + key + " }}"
                      for key in ("source_sha", "apk_sha256", "run_attempt", "artifact_id")}, "immutable verification receipt changed")
            else:
                check("outputs" not in job, f"{name}: unexpected outputs")

            setup_steps = {"Set up Android SDK": ("android-actions/setup-android", SDK)}
            checkout_name = "Checkout exact main" if signing else "Checkout trusted tools"
            setup_steps[checkout_name] = ("actions/checkout", CHECKOUT)
            runs = {"Install pinned SDK": ("bash tools/ci_install_android_sdk.sh", {})}
            if name == "validate":
                setup_steps.pop("Set up Android SDK")
                runs = {"Check exact main and CI": ("python tools/signed_release.py preflight", TOKEN)}
            elif name == "sign":
                setup_steps.update({"Set up JDK 25": ("actions/setup-java", {"distribution": "temurin", "java-version": "25"}),
                                    "Attest APK and metadata": ("actions/attest", ATTEST),
                                    "Upload signed candidate": ("actions/upload-artifact", UPLOAD)})
                runs.update({"Build official APK once": ("python tools/signed_release.py build", TOKEN | SECRETS),
                             "Remove signing material": ("python tools/signed_release.py cleanup", {}),
                             "Verify signed APK": ('python tools/signed_release.py candidate --directory "$RUNNER_TEMP/candidate"', TOKEN),
                             "Verify attestation": ('cp "$ATTESTATION_BUNDLE" "$RUNNER_TEMP/candidate/attestation.sigstore.json"\npython tools/signed_release.py attest --directory "$RUNNER_TEMP/candidate"',
                                                    TOKEN | {"ATTESTATION_BUNDLE": "${{ steps.provenance.outputs.bundle-path }}"})})
            elif name == "verify":
                runs["Verify original candidate"] = ('python tools/signed_release.py fetch --directory "$RUNNER_TEMP/original-candidate"', TOKEN | PROMOTION)
            else:
                runs["Publish verified original candidate"] = ('python tools/signed_release.py publish --directory "$RUNNER_TEMP/original-candidate"',
                    TOKEN | PROMOTION | RECEIPT | {"A13_XPOSED_RELEASE_TOKEN": "${{ secrets.A13_XPOSED_RELEASE_TOKEN }}"})
            steps = job["steps"]
            step_names = [step["name"] for step in steps]
            check(len(step_names) == len(set(step_names)) and set(step_names) == set(setup_steps) | set(runs), f"{name}: unexpected or missing steps")
            for step in steps:
                label = step["name"]
                check(set(step) <= {"name", "uses", "with", "env", "run", "if", "id"}, f"{label}: unexpected step controls")
                check(step.get("if") == ("always()" if label == "Remove signing material" else None), f"{label}: verification/cleanup must not be skipped")
                expected_id = "provenance" if label == "Attest APK and metadata" else "candidate" if label == "Verify original candidate" else None
                check(step.get("id") == expected_id, f"{label}: unexpected step id")
                if label in setup_steps:
                    action, options = setup_steps[label]
                    check(step.get("uses", "").split("@")[0] == action and "run" not in step and not step.get("env"), f"{label}: action/secret boundary changed")
                    check({key: value.strip() for key, value in step.get("with", {}).items()} == options, f"{label}: action inputs changed")
                elif label in runs:
                    run, env = runs[label]
                    check(step.get("run", "").strip() == run and step.get("env", {}) == env
                          and "uses" not in step and "with" not in step, f"{label}: command or secret scope changed")
        if signing:
            labels = [step["name"] for step in jobs["sign"]["steps"]]
            check(labels.index("Remove signing material") == labels.index("Build official APK once") + 1
                  and labels.index("Upload signed candidate") > labels.index("Verify attestation") > labels.index("Verify signed APK"), "signing cleanup/verification/upload order changed")
    except (ValueError, TypeError, KeyError, AttributeError, ImportError):
        errors.append("CI_RELEASE_CONTRACT: malformed protected workflow or missing pinned PyYAML dependency")
    return errors
