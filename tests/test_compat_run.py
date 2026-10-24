import json
import re
import tempfile
import unittest
import zipfile
from pathlib import Path

from tools.compat_run import (PROFILES, ROOT, curseforge_file_id, load_profiles, pack_neoforge, report,
                              resolve_latest, server_pack)

GROUPS_SOURCE = ROOT / "common/src/compattest/java/space/controlnet/ae2federation/compat/CompatTestClasses.java"
# Responses recorded from https://www.curseforge.com/api/v1/mods on 2026-10-24, trimmed to the fields the runner reads.
CURSEFORGE = PROFILES.parent / "curseforge"


def recorded(project: int, prefix: str):
    """A stand-in for fetch_json that answers from the recorded responses of one pack."""
    def get(url: str):
        file = re.search(rf"/mods/{project}/files/(\d+)/additional-files$", url)
        name = f"{prefix}-{file[1]}-additional-files.json" if file else f"{prefix}-files.json"
        return json.loads((CURSEFORGE / name).read_text())
    return get


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


    def test_report_puts_newest_release_runs_in_their_own_table(self):
        with tempfile.TemporaryDirectory() as directory:
            (Path(directory) / "summary-latest-atm10.json").write_text(json.dumps([{
                "profile": "atm10", "status": "pass", "seconds": 300, "passed": 79, "total": 79,
                "latest": {"pinned": "ServerFiles-8.2.zip", "pinnedNeoforge": "21.1.251", "tested": "ServerFiles-8.3.zip",
                           "fileId": 9190182, "neoforge": "21.1.252", "outdated": True}}]))
            (Path(directory) / "summary-latest-nast.json").write_text(json.dumps([{
                "profile": "nast", "status": "pinned-is-latest", "seconds": 0,
                "latest": {"pinned": "nast server 2.2.1.zip", "pinnedNeoforge": "21.1.251",
                           "tested": "nast server 2.2.1.zip", "fileId": 9286724, "neoforge": "21.1.251",
                           "outdated": False}}]))
            table = report(Path(directory)).splitlines()
        self.assertEqual(table[0], "Newest modpack releases (--latest):")
        self.assertEqual(table[4], "| atm10 | ServerFiles-8.2.zip | ServerFiles-8.3.zip (file 9190182) | "
                                   "21.1.252 (pinned 21.1.251) | pass | 79/79 | 300 s |  |")
        self.assertEqual(table[5], "| nast | nast server 2.2.1.zip | same as pinned, not run again | 21.1.251 | "
                                   "pinned-is-latest |  | 0 s |  |")
        self.assertEqual(len(table), 6)

    def test_every_modpack_names_its_curseforge_project_and_pins_a_cdn_file(self):
        for name, profile in load_profiles().items():
            if profile.get("group") != "modpacks":
                continue
            with self.subTest(profile=name):
                self.assertIsInstance(profile["curseforge"]["project"], int)
                self.assertIsNotNone(curseforge_file_id(profile["pack"]["url"]))


class LatestModpackTest(unittest.TestCase):
    def test_resolves_the_newest_release_and_its_server_pack(self):
        latest = resolve_latest(925200, recorded(925200, "atm10"))
        self.assertEqual(latest, {
            "project": 925200, "release": 9190157, "version": "All the Mods 10-8.3", "id": 9190182,
            "file": "ServerFiles-8.3.zip", "url": "https://mediafilez.forgecdn.net/files/9190/182/ServerFiles-8.3.zip"})
        cus2 = resolve_latest(1324409, recorded(1324409, "cus2"))
        self.assertEqual(cus2["url"], "https://mediafilez.forgecdn.net/files/9299/724/Create%20Ultimate%20Selection"
                                      "%202%20Serverpack%20MC%201.21.1-12.9.0.zip")

    def test_the_pinned_cdn_url_names_the_same_file_id(self):
        profile = load_profiles()["nast"]
        self.assertEqual(curseforge_file_id(profile["pack"]["url"]), 9286724)
        self.assertEqual(resolve_latest(1582045, recorded(1582045, "nast"))["id"], 9286724)
        self.assertEqual(curseforge_file_id("https://mediafilez.forgecdn.net/files/8945/94/ServerFiles-8.2.zip"),
                         8945094)

    def test_betas_and_other_loaders_are_not_releases(self):
        listing = json.loads((CURSEFORGE / "nast-files.json").read_text())
        beta_first = {"data": listing["data"][::-1], "pagination": listing["pagination"]}
        self.assertEqual(resolve_latest(1582045, lambda url: beta_first if "additional" not in url else
                                        recorded(1582045, "nast")(url))["release"], 9286722)
        # All the Mods 9 is a Forge 1.20.1 pack: no file in its listing qualifies.
        with self.assertRaisesRegex(RuntimeError, "no 1.21.1 NeoForge release"):
            resolve_latest(715572, recorded(715572, "atm9"))

    def test_a_server_pack_is_taken_only_where_the_parent_marks_one(self):
        parents = {row["id"]: row for row in json.loads((CURSEFORGE / "atm9-files.json").read_text())["data"]}
        additional = json.loads((CURSEFORGE / "atm9-6985843-additional-files.json").read_text())["data"]
        # 1.1.0 has an additional server zip, but CurseForge does not count it as a server pack.
        with self.assertRaisesRegex(RuntimeError, "has no server pack"):
            server_pack(parents[6985843], additional)
        two = {**parents[6985843], "hasServerPack": True, "additionalServerPackFilesCount": 1,
               "additionalFilesCount": 2}
        with self.assertRaisesRegex(RuntimeError, "cannot tell which: Server-Files-1.1.0.zip"):
            server_pack(two, additional)


class PackNeoForgeTest(unittest.TestCase):
    def pack(self, files: dict[str, str | bytes]) -> Path:
        directory = Path(self.enterContext(tempfile.TemporaryDirectory()))
        for name, content in files.items():
            (directory / name).parent.mkdir(parents=True, exist_ok=True)
            if name.endswith(".jar"):
                with zipfile.ZipFile(directory / name, "w") as jar:
                    jar.writestr("install_profile.json", content)
            else:
                (directory / name).write_text(content)
        return directory

    def test_server_pack_creator_variables(self):
        root = self.pack({"variables.txt": "# MODLOADER_VERSION=1.0.0\nMINECRAFT_VERSION=1.21.1\nMODLOADER=NeoForge\n"
                                           "MODLOADER_VERSION=21.1.243\n", "start.sh": "NEOFORGE_VERSION=21.1.1\n"})
        self.assertEqual(pack_neoforge(root), ("21.1.243", "variables.txt"))

    def test_server_pack_creator_and_curseforge_manifests(self):
        creator = self.pack({"manifest.json": json.dumps({"modloader": "NeoForge", "modloaderVersion": "21.1.243"})})
        self.assertEqual(pack_neoforge(creator), ("21.1.243", "manifest.json"))
        curseforge = self.pack({"manifest.json": json.dumps({"minecraft": {"version": "1.21.1", "modLoaders": [
            {"id": "neoforge-21.1.251", "primary": True}]}})})
        self.assertEqual(pack_neoforge(curseforge), ("21.1.251", "manifest.json"))

    def test_start_script(self):
        root = self.pack({"startserver.sh": "#!/bin/bash\nset -eo pipefail\nNEOFORGE_VERSION=21.1.251\n"
                                            'INSTALLER="neoforge-$NEOFORGE_VERSION-installer.jar"\n'})
        self.assertEqual(pack_neoforge(root), ("21.1.251", "startserver.sh"))

    def test_installed_libraries(self):
        root = self.pack({"libraries/net/neoforged/neoforge/21.1.250/unix_args.txt": ""})
        self.assertEqual(pack_neoforge(root), ("21.1.250", "libraries/net/neoforged/neoforge/21.1.250"))

    def test_renamed_installer_is_read_from_its_profile_not_its_name(self):
        root = self.pack({"neoforge-1.21.1.jar": json.dumps({"version": "neoforge-21.1.251", "minecraft": "1.21.1"}),
                          "server.properties": "motd=x\n"})
        self.assertEqual(pack_neoforge(root), ("21.1.251", "neoforge-1.21.1.jar (install_profile.json)"))

    def test_no_version_is_an_error_naming_what_was_searched(self):
        root = self.pack({"mods/readme.txt": "", "manifest.json": "{}", "other.jar": json.dumps({"version": "1.0"})})
        with self.assertRaisesRegex(RuntimeError, "no NeoForge version found in variables.txt, manifest.json"):
            pack_neoforge(root)


if __name__ == "__main__":
    unittest.main()
