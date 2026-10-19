import tempfile
import unittest
from pathlib import Path

from tools.required_gametests import (direct_command, freeze_argument_files, gametest_command, required_test_ids,
                                      select_shard, verify_one)

LAUNCH = {"java": "/jdk/bin/java", "jvmArgs": ["-Dfile.encoding=UTF-8", "@/build/vm.txt"],
          "classpath": ["/a.jar", "/b.jar"], "mainClass": "net.neoforged.devlaunch.Main",
          "args": ["@/build/program.txt"], "environment": {}}


class RequiredGameTestsTest(unittest.TestCase):
    def test_release_shards_cover_manifest_exactly_once(self):
        tests = required_test_ids()
        partitions = [select_shard(tests, index, 16) for index in range(16)]
        combined = [test for partition in partitions for test in partition]
        self.assertCountEqual(combined, tests)
        self.assertEqual(len(combined), len(set(combined)))
        self.assertTrue(all(partitions))

    def test_default_shard_preserves_full_suite(self):
        tests = required_test_ids()
        self.assertEqual(select_shard(tests, 0, 1), tests)

    def test_invalid_shards_are_rejected(self):
        tests = required_test_ids()
        for index, count in ((-1, 8), (8, 8), (0, 0), (0, len(tests) + 1)):
            with self.subTest(index=index, count=count), self.assertRaises(ValueError):
                select_shard(tests, index, count)

    def test_each_test_gets_its_own_server_through_a_reused_gradle_daemon(self):
        command = gametest_command("harnessnativesmoke")
        self.assertIn("-PfederationGameTestId=harnessnativesmoke", command)
        self.assertIn("-PfederationGameTestSelection=positive", command)
        self.assertIn("--dependency-verification=strict", command)
        # The server runs in its own process and level either way; the daemon only saves Gradle's start-up.
        self.assertNotIn("--no-daemon", command)

    def test_completion_requires_one_successful_test(self):
        verify_one("harnessnativesmoke", "All 1 required tests passed :)", 0)
        for output, code in (("All 2 required tests passed :)", 0),
                             ("All 1 required tests passed :)", 1), ("", 0)):
            with self.subTest(output=output, code=code), self.assertRaises(ValueError):
                verify_one("harnessnativesmoke", output, code)

    def test_a_direct_launch_names_its_test_after_the_baked_arguments(self):
        command = direct_command(LAUNCH, "harnessnativesmoke")
        # A later -D wins, and the argument file holds the test id of whichever Gradle run wrote it last.
        self.assertLess(command.index("@/build/vm.txt"), command.index("-Dae2federation.testId=harnessnativesmoke"))
        self.assertIn("-Dae2federation.testSelection=positive", command)
        self.assertEqual(command[0], "/jdk/bin/java")
        self.assertEqual(command[command.index("-cp") + 1], "/a.jar:/b.jar")
        self.assertEqual(command[-2:], ["net.neoforged.devlaunch.Main", "@/build/program.txt"])

    def test_argument_files_are_copied_so_a_later_gradle_run_cannot_change_them(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "vm.txt"
            source.write_text("-Dae2federation.testId=other\n")
            program = Path(directory) / "program.txt"
            program.write_text("--launchTarget\nforgeserverdev\n")
            launch = dict(LAUNCH, jvmArgs=["-Dfile.encoding=UTF-8", f"@{source}"], args=[f"@{program}"])
            frozen = freeze_argument_files(launch, Path(directory) / "frozen")
            copied = Path(frozen["jvmArgs"][1][1:])
            self.assertNotEqual(copied, source)
            self.assertEqual(copied.read_text(), source.read_text())
            source.write_text("changed")
            self.assertEqual(copied.read_text(), "-Dae2federation.testId=other\n")
            self.assertEqual(frozen["jvmArgs"][0], "-Dfile.encoding=UTF-8")
