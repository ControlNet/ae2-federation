import unittest

from tools.platform_release import (curseforge_file_exists, curseforge_files, modrinth_version_exists,
                                    platform_plan, resolve_curseforge, validate_modrinth_project,
                                    validate_modrinth_tags, verify_modrinth)

# Synthetic test-only catalogs shaped like the platform APIs. The duplicated
# "1.21.1" names reproduce the ambiguity observed in the live CurseForge catalog.
VALUES = {"mod_id": "ae2federation", "mod_version": "0.0.2", "minecraft_version": "1.21.1",
          "release_channel": "alpha"}
TYPES = [{"id": 77784, "name": "Minecraft 1.21"}, {"id": 1, "name": "Addons"}, {"id": 68441, "name": "Modloader"},
         {"id": 2, "name": "Java"}, {"id": 75208, "name": "Environment"}]
VERSIONS = [
    {"id": 11779, "name": "1.21.1", "gameVersionTypeID": 77784},
    {"id": 12735, "name": "1.21.1", "gameVersionTypeID": 999},
    {"id": 16115, "name": "1.21.1", "gameVersionTypeID": 1},
    {"id": 11774, "name": "1.21.1-Snapshot", "gameVersionTypeID": 77784},
    {"id": 10150, "name": "NeoForge", "gameVersionTypeID": 68441},
    {"id": 11135, "name": "Java 21", "gameVersionTypeID": 2},
    {"id": 9638, "name": "Client", "gameVersionTypeID": 75208},
    {"id": 9639, "name": "Server", "gameVersionTypeID": 75208},
]


class PlatformReleaseTest(unittest.TestCase):
    def plan(self, **overrides):
        return platform_plan({**VALUES, **overrides}, "ControlNet/ae2-federation")

    def test_plan_matches_first_manual_release(self):
        plan = self.plan()
        self.assertEqual(plan["filename"], "ae2federation-neoforge-1.21.1-0.0.2.jar")
        self.assertEqual((plan["name"], plan["channel"], plan["loader"], plan["environment"]),
                         ("0.0.2", "alpha", "neoforge", "client_and_server"))
        self.assertEqual(plan["game_versions"], ["1.21.1"])
        self.assertEqual(plan["dependencies"], [{"project_id": "XxWD5pD3", "dependency_type": "required"},
                                                {"project_id": "B1CBVXHX", "dependency_type": "required"}])
        self.assertIn("https://github.com/ControlNet/ae2-federation/releases/tag/v0.0.2", plan["changelog"])

    def test_plan_rejects_invalid_channels_and_versions(self):
        for overrides in ({"release_channel": "stable"}, {"release_channel": ""}, {"mod_version": "0.0.2-dev"}):
            with self.subTest(overrides=overrides), self.assertRaises(ValueError):
                self.plan(**overrides)
        values = dict(VALUES)
        del values["release_channel"]
        with self.assertRaises(ValueError):
            platform_plan(values, "ControlNet/ae2-federation")

    def test_curseforge_resolves_minecraft_version_by_family_type(self):
        self.assertEqual(resolve_curseforge(self.plan()["curseforge_tags"], VERSIONS, TYPES),
                         [11779, 10150, 11135, 9638, 9639])

    def test_curseforge_rejects_missing_or_ambiguous_tags(self):
        tags = self.plan()["curseforge_tags"]
        missing = [row for row in VERSIONS if row["name"] != "Server"]
        with self.assertRaisesRegex(ValueError, "Server"):
            resolve_curseforge(tags, missing, TYPES)
        ambiguous = VERSIONS + [{"id": 11780, "name": "1.21.1", "gameVersionTypeID": 77784}]
        with self.assertRaisesRegex(ValueError, "1.21.1"):
            resolve_curseforge(tags, ambiguous, TYPES)
        with self.assertRaisesRegex(ValueError, "Minecraft 1.21"):
            resolve_curseforge(tags, VERSIONS, [row for row in TYPES if row["name"] != "Minecraft 1.21"])

    def test_modrinth_tags_must_exist_as_stable_releases(self):
        plan = self.plan()
        loaders = [{"name": "neoforge"}, {"name": "forge"}]
        validate_modrinth_tags(plan, [{"version": "1.21.1", "version_type": "release"}], loaders)
        with self.assertRaises(ValueError):
            validate_modrinth_tags(plan, [{"version": "1.21.1", "version_type": "snapshot"}], loaders)
        with self.assertRaises(ValueError):
            validate_modrinth_tags(plan, [{"version": "1.21.1", "version_type": "release"}], [{"name": "forge"}])

    def test_modrinth_project_environment(self):
        validate_modrinth_project({"environment": ["client_and_server"]})
        validate_modrinth_project({"client_side": "required", "server_side": "required"})
        for project in ({"environment": ["server_only"]}, {"client_side": "unsupported", "server_side": "required"}):
            with self.subTest(project=project), self.assertRaises(ValueError):
                validate_modrinth_project(project)

    def test_existing_modrinth_version_is_detected(self):
        self.assertTrue(modrinth_version_exists([{"version_number": "0.0.1"}], "0.0.1"))
        self.assertFalse(modrinth_version_exists([{"version_number": "0.0.1"}], "0.0.2"))

    def test_existing_curseforge_file_is_detected_by_filename(self):
        files = [{"fileName": "ae2federation-neoforge-1.21.1-0.0.1.jar",
                  "displayName": "ae2federation-neoforge-1.21.1-0.0.1.jar"}]
        self.assertTrue(curseforge_file_exists(files, "ae2federation-neoforge-1.21.1-0.0.1.jar"))
        self.assertFalse(curseforge_file_exists(files, self.plan()["filename"]))
        renamed = [{"fileName": "upload.jar", "displayName": self.plan()["filename"]}]
        self.assertTrue(curseforge_file_exists(renamed, self.plan()["filename"]))

    def test_curseforge_files_reads_every_page(self):
        pages = {0: [{"id": 1}, {"id": 2}], 1: [{"id": 3}]}
        requested = []

        def fetch(url):
            index = int(url.split("pageIndex=")[1].split("&")[0])
            requested.append(index)
            return {"data": pages[index], "pagination": {"index": index, "pageSize": 2, "totalCount": 3}}

        self.assertEqual([row["id"] for row in curseforge_files("1713078", fetch, page_size=2)], [1, 2, 3])
        self.assertEqual(requested, [0, 1])

    def test_curseforge_files_rejects_incomplete_listing(self):
        def fetch(url):
            return {"data": [], "pagination": {"index": 0, "pageSize": 2, "totalCount": 3}}

        with self.assertRaises(ValueError):
            curseforge_files("1713078", fetch, page_size=2)

    def test_published_modrinth_version_is_verified(self):
        plan = self.plan()
        actual = {"version_number": "0.0.2", "version_type": "alpha", "game_versions": ["1.21.1"],
                  "loaders": ["neoforge"], "environment": "client_and_server",
                  "files": [{"filename": plan["filename"], "primary": True}],
                  "dependencies": [{"project_id": "B1CBVXHX", "version_id": None, "dependency_type": "required"},
                                   {"project_id": "XxWD5pD3", "version_id": None, "dependency_type": "required"}]}
        verify_modrinth(actual, plan)
        for key, value in (("version_type", "release"), ("environment", "server_only"), ("dependencies", []),
                           ("files", [{"filename": plan["filename"], "primary": False}])):
            with self.subTest(key=key), self.assertRaises(ValueError):
                verify_modrinth({**actual, key: value}, plan)


if __name__ == "__main__":
    unittest.main()
