import tempfile
import unittest
from pathlib import Path

from tools.release_metadata import archive_names, release_plan


class ReleaseMetadataTest(unittest.TestCase):
    def test_first_release(self):
        self.assertEqual(release_plan("0.0.1", [], "abc", None),
                         {"version": "0.0.1", "tag": "v0.0.1", "should_release": "true"})

    def test_versions_are_compared_numerically(self):
        self.assertEqual(release_plan("0.0.10", ["v0.0.9"], "abc", None)["should_release"], "true")
        with self.assertRaises(ValueError):
            release_plan("0.0.2", ["v0.0.10"], "abc", None)

    def test_retry_only_reuses_same_commit(self):
        self.assertEqual(release_plan("0.0.1", ["v0.0.1"], "abc", "abc")["should_release"], "true")
        self.assertEqual(release_plan("0.0.1", ["v0.0.1"], "def", "abc")["should_release"], "false")

    def test_invalid_release_versions(self):
        for version in ("0.0.1-dev", "v0.0.1", "00.0.1", "0.0", "0.0.1\ninjected=true"):
            with self.subTest(version=version), self.assertRaises(ValueError):
                release_plan(version, [], "abc", None)

    def test_archive_names_follow_project_version(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "gradle.properties").write_text("mod_id=ae2federation\nmod_version=0.0.2-dev\n")
            self.assertEqual(archive_names(root),
                             ("ae2federation-0.0.2-dev.jar", "ae2federation-0.0.2-dev-sources.jar"))


if __name__ == "__main__":
    unittest.main()
