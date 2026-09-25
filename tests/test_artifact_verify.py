import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tools.artifact_verify import inspect_archive, runtime_probe, sha256, verify_rebuilt_hashes, verify_runtime_loads, verify_target_settings


ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / "neoforge-1.21.1/build/libs/ae2federation-0.1.0-dev.jar"
SOURCES = ROOT / "neoforge-1.21.1/build/libs/ae2federation-0.1.0-dev-sources.jar"
PINS = json.loads((ROOT / "docs/compatibility/artifact.json").read_text())


class ArtifactVerificationTest(unittest.TestCase):
    def _probe_with_server_exit(self, server_exit: int, stuck_descendant: bool = False,
                                orderly_descendant: bool = False):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            jar = root / "neoforge-1.21.1/build/libs/ae2federation-0.1.0-dev.jar"
            jar.parent.mkdir(parents=True)
            jar.write_bytes(b"synthetic JAR for isolated lifecycle test")
            approved = root / "approved-eula.txt"
            approved.write_bytes(b"eula=true\n")
            report = root / "evidence/runtime.json"
            report.parent.mkdir()

            def launch(command, **kwargs):
                server = ":neoforge-1.21.1:runArtifactServer" in command
                role = "server" if server else "client"
                installed = report.parent / f"artifact-{role}-runtime/mods/{jar.name}"
                log = kwargs["stdout"]
                joined = "AE2F_ARTIFACT_SERVER_JOIN player=ArtifactProbe\nDone (" if server else "AE2F_ARTIFACT_JOIN player=ArtifactProbe"
                log.write(f"AE2F_ARTIFACT_LOAD path={installed.resolve()} sha256={sha256(jar)}\n{joined}\n".encode())
                log.flush()
                process = mock.Mock(pid=100 if server else 101)
                process.poll.return_value = None

                def wait(timeout=None):
                    if server and server_exit:
                        log.write(b"FAILURE: Build failed with an exception.\n")
                        log.flush()
                    process.poll.return_value = server_exit if server else 0
                    return process.poll.return_value

                process.wait.side_effect = wait
                return process

            with mock.patch("tools.artifact_verify.socket.socket") as socket_factory, \
                    mock.patch("tools.artifact_verify.subprocess.Popen", side_effect=launch), \
                    mock.patch("tools.artifact_verify.close_client_window") as close_client, \
                    mock.patch("tools.artifact_verify.stop_server") as stop_dedicated_server, \
                    mock.patch("tools.artifact_verify.child_processes", return_value=[888] if stuck_descendant or orderly_descendant else []), \
                    mock.patch("tools.artifact_verify.process_alive", return_value=stuck_descendant), \
                    mock.patch("tools.artifact_verify.os.kill") as signal_child:
                if orderly_descendant:
                    signal_child.side_effect = AssertionError("Gradle daemon received SIGTERM before orderly game exit")
                socket_factory.return_value.__enter__.return_value.getsockname.return_value = ("127.0.0.1", 54405)
                if server_exit or stuck_descendant:
                    expected = "server.*exit 1" if server_exit else "descendants survived"
                    with self.assertRaisesRegex(ValueError, expected):
                        runtime_probe(root, report, approved)
                    self.assertFalse(report.exists())
                else:
                    runtime_probe(root, report, approved)
                    facts = json.loads(report.read_text())
                    self.assertEqual((facts["serverWrapperExit"], facts["clientWrapperExit"]), (0, 0))
                    close_client.assert_called_once()
                    stop_dedicated_server.assert_called_once()
            self.assertFalse((report.parent / "artifact-server-runtime").exists())
            self.assertFalse((report.parent / "artifact-client-runtime").exists())

    def test_runtime_rejects_nested_server_failure_after_join(self):
        self._probe_with_server_exit(1)

    def test_runtime_records_successful_nested_wrapper_exits(self):
        self._probe_with_server_exit(0)

    def test_runtime_rejects_surviving_descendant_after_join(self):
        self._probe_with_server_exit(0, stuck_descendant=True)

    def test_runtime_waits_for_orderly_game_exit_before_signaling_descendants(self):
        self._probe_with_server_exit(0, orderly_descendant=True)

    def test_runtime_without_approved_eula_has_no_side_effects(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = root / "evidence" / "runtime.json"
            with mock.patch("tools.artifact_verify.socket.socket", side_effect=AssertionError("port opened")), \
                    mock.patch("tools.artifact_verify.subprocess.Popen", side_effect=AssertionError("process started")):
                with self.assertRaisesRegex(ValueError, "explicit.*EULA"):
                    runtime_probe(root, report)
            self.assertEqual(list(root.iterdir()), [])

    def test_runtime_rejects_unapproved_eula_contents_before_side_effects(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            supplied = root / "eula.txt"
            supplied.write_text("eula=false\n")
            with mock.patch("tools.artifact_verify.socket.socket", side_effect=AssertionError("port opened")), \
                    mock.patch("tools.artifact_verify.subprocess.Popen", side_effect=AssertionError("process started")):
                with self.assertRaisesRegex(ValueError, "must contain exactly"):
                    runtime_probe(root, root / "evidence" / "runtime.json", supplied)
            self.assertEqual(list(root.iterdir()), [supplied])

    def test_baseline_archive(self):
        self.assertTrue(inspect_archive(JAR, SOURCES, PINS)["entries"])

    def test_release_entrypoints_do_not_control_gameplay_shutdown(self):
        server = "space/controlnet/ae2federation/neoforge/NeoForgeEntrypoint"
        client = "space/controlnet/ae2federation/neoforge/client/NeoForgeClientEntrypoint"
        with zipfile.ZipFile(JAR) as binary, zipfile.ZipFile(SOURCES) as sources:
            self.assertNotIn(b"halt", binary.read(server + ".class"))
            self.assertNotIn(b"stop", binary.read(client + ".class"))
            self.assertNotIn(b".halt(", sources.read(server + ".java"))
            self.assertNotIn(b".stop()", sources.read(client + ".java"))

    def test_reject_release_entrypoint_shutdown_mutation(self):
        with tempfile.TemporaryDirectory() as directory:
            modified = Path(directory) / "shutdown.jar"
            with zipfile.ZipFile(JAR) as original, zipfile.ZipFile(modified, "w") as rewritten:
                for entry in original.infolist():
                    content = original.read(entry.filename)
                    if entry.filename.endswith("/neoforge/NeoForgeEntrypoint.class"):
                        content += b"halt"
                    rewritten.writestr(entry, content)
            with self.assertRaisesRegex(ValueError, "gameplay shutdown"):
                inspect_archive(modified, SOURCES, PINS)

    def test_reject_wrong_tuple(self):
        with tempfile.TemporaryDirectory() as directory:
            modified = Path(directory) / "wrong-tuple.jar"
            with zipfile.ZipFile(JAR) as original, zipfile.ZipFile(modified, "w") as rewritten:
                for entry in original.infolist():
                    content = original.read(entry.filename)
                    if entry.filename == "META-INF/neoforge.mods.toml":
                        content = content.replace(b'versionRange="[1.21.1]"', b'versionRange="[1.21.2]"')
                    rewritten.writestr(entry, content)
            with self.assertRaisesRegex(ValueError, "tuple"):
                inspect_archive(modified, SOURCES, PINS)

    def test_reject_corrupted_jar(self):
        with tempfile.TemporaryDirectory() as directory:
            modified = Path(directory) / "corrupted.jar"
            modified.write_bytes(JAR.read_bytes()[:-20])
            with self.assertRaises((ValueError, zipfile.BadZipFile)):
                inspect_archive(modified, SOURCES, PINS)

    def test_reject_split_runtime_jars(self):
        with tempfile.TemporaryDirectory() as directory:
            server = Path(directory) / "server.jar"
            client = Path(directory) / "client.jar"
            server.write_bytes(JAR.read_bytes())
            client.write_bytes(JAR.read_bytes() + b"different")
            with self.assertRaisesRegex(ValueError, "same JAR"):
                verify_runtime_loads(server, client, JAR,
                    f"AE2F_ARTIFACT_LOAD path={server.resolve()} sha256=bogus\nDone (",
                    f"AE2F_ARTIFACT_LOAD path={client.resolve()} sha256=bogus\nAE2F_ARTIFACT_JOIN player=ArtifactProbe")

    def test_reject_unobserved_or_rebound_runtime_load(self):
        with tempfile.TemporaryDirectory() as directory:
            server = Path(directory) / "server.jar"
            client = Path(directory) / "client.jar"
            server.write_bytes(JAR.read_bytes())
            client.write_bytes(JAR.read_bytes())
            with self.assertRaisesRegex(ValueError, "load path"):
                verify_runtime_loads(server, client, JAR, "Done (", "AE2F_ARTIFACT_JOIN player=ArtifactProbe")
            with self.assertRaisesRegex(ValueError, "load path"):
                verify_runtime_loads(server, client, JAR,
                    f"AE2F_ARTIFACT_LOAD path={JAR.resolve()} sha256=fake\nDone (",
                    f"AE2F_ARTIFACT_LOAD path={client.resolve()} sha256=fake\nAE2F_ARTIFACT_JOIN player=ArtifactProbe")

    def test_reject_stale_rebuild(self):
        inspected = inspect_archive(JAR, SOURCES, PINS)
        with self.assertRaisesRegex(ValueError, "Isolated rebuild differs"):
            verify_rebuilt_hashes(inspected, {**inspected, "sha256": "0" * 64})

    def test_reject_extra_target(self):
        settings = (ROOT / "settings.gradle").read_text()
        verify_target_settings(settings)
        with self.assertRaisesRegex(ValueError, "target"):
            verify_target_settings(settings + "\ninclude('neoforge-1.21.2')\n")


if __name__ == "__main__":
    unittest.main()
