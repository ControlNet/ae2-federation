import json
import re
import tempfile
import unittest
from pathlib import Path

from tools.compat_run import PROFILES, ROOT, load_profiles, report

GROUPS_SOURCE = ROOT / "common/src/compattest/java/space/controlnet/ae2federation/compat/CompatTestClasses.java"


class CompatRunTest(unittest.TestCase):
    def test_every_profile_resolves_its_mods_from_the_pins(self):
        profiles = load_profiles()
        self.assertIn("baseline", profiles)
        for name, profile in profiles.items():
            with self.subTest(profile=name):
                self.assertTrue(profile["neoforge"])
                self.assertTrue(profile["tests"])
                files = [entry["file"] for entry in profile["mods"]]
                self.assertEqual(len(files), len(set(files)), "a mod is listed twice")

    def test_every_pin_has_a_url_and_a_sha512(self):
        pins = json.loads((PROFILES.parent / "mods.json").read_text())
        for key, entry in pins.items():
            with self.subTest(pin=key):
                self.assertTrue(entry["url"].startswith("https://"))
                self.assertRegex(entry["sha512"], r"^[0-9a-f]{128}$")
                self.assertTrue(entry["file"].endswith(".jar"))

    def test_profiles_name_only_groups_the_test_mod_has(self):
        groups = set(re.findall(r'groups\.put\("([a-z0-9-]+)"', GROUPS_SOURCE.read_text())) | {"all"}
        for name, profile in load_profiles().items():
            for selection in profile["tests"]:
                with self.subTest(profile=name, selection=selection):
                    self.assertIn(selection, groups)

    def test_report_lists_each_profile_with_its_failures(self):
        with tempfile.TemporaryDirectory() as directory:
            (Path(directory) / "one").mkdir()
            (Path(directory) / "one/summary-b.json").write_text(json.dumps([{
                "profile": "b", "status": "fail", "seconds": 40, "passed": 1, "total": 2,
                "failed": [{"id": "storageshare", "status": "fail", "error": "no iron"}]}]))
            (Path(directory) / "summary-a.json").write_text(json.dumps([{
                "profile": "a", "status": "no-report", "seconds": 9, "detail": "Mod loading has failed\nmore"}]))
            (Path(directory) / "compat-report.json").write_text(json.dumps({"tests": []}))
            table = report(Path(directory)).splitlines()
        self.assertEqual(table[2], "| a | no-report |  | 9 s | Mod loading has failed |")
        self.assertEqual(table[3], "| b | fail | 1/2 | 40 s | `storageshare`: no iron |")
        self.assertEqual(len(table), 4)


if __name__ == "__main__":
    unittest.main()
