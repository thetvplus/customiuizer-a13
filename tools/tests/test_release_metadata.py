import unittest

from tools.check_release_metadata import validate_metadata


class ReleaseMetadataTest(unittest.TestCase):
    def setUp(self):
        self.gradle = 'val lastVersion = 142\nval lastVersionName = "r13.14.1"'
        self.logs = {"CHANGELOG.md": "## r13.14.1\n`versionCode 142`\n## r13.13.0\n`versionCode 141`",
                     "CHANGELOG_EN.md": "## r13.14.1\n`versionCode 142`"}

    def test_both_languages_current_version_and_tag(self):
        self.assertEqual(("r13.14.1", 142), validate_metadata(self.gradle, self.logs, "r13.14.1"))

    def test_wrong_or_missing_tag_fails(self):
        for tag in ("", "r13.13.0", "142-r13.14.1"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                validate_metadata(self.gradle, self.logs, tag)

    def test_stale_english_or_chinese_version_code_fails(self):
        for filename in self.logs:
            for old, new in (("r13.14.1", "r13.13.0"), ("142", "141")):
                bad = self.logs | {filename: self.logs[filename].replace(old, new)}
                with self.subTest(filename=filename, old=old), self.assertRaises(ValueError):
                    validate_metadata(self.gradle, bad)

    def test_a14_metadata_cannot_pass(self):
        with self.assertRaises(ValueError):
            validate_metadata(self.gradle.replace("r13.14.1", "r14.14.0"), self.logs)
