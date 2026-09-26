import argparse
import hashlib
import json
import os
import re
import secrets
import shutil
import signal
import socket
import subprocess
import sys
import tempfile
import time
import tomllib
import zipfile
from pathlib import Path
from typing import TypedDict

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tools.artifact_shutdown import close_client_window, stop_server
from tools.release_metadata import archive_names, properties


class ArtifactPins(TypedDict):
    licenseSha256: str
    modRanges: dict[str, str]
    manifest: dict[str, str]


class ArchiveInspection(TypedDict):
    sha256: str
    sourcesSha256: str
    entries: list[str]
    sourceEntries: list[str]
    tuple: dict[str, str]


def load_pins(root: Path) -> ArtifactPins:
    pins = json.loads((root / "docs/compatibility/artifact.json").read_text())
    pins["manifest"]["Implementation-Version"] = properties(root)["mod_version"]
    return pins


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def archive_entries(path: Path) -> dict[str, bytes]:
    try:
        with zipfile.ZipFile(path) as archive:
            if archive.testzip() is not None:
                raise ValueError("Corrupted JAR entry")
            names = archive.namelist()
            if len(names) != len(set(names)):
                raise ValueError("Duplicate JAR entry")
            return {name: archive.read(name) for name in names if not name.endswith("/")}
    except zipfile.BadZipFile as error:
        raise ValueError("Corrupted JAR") from error


def inspect_archive(jar: Path, sources: Path, pins: ArtifactPins) -> ArchiveInspection:
    binary = archive_entries(jar)
    source = archive_entries(sources)
    required = {"space/controlnet/ae2federation/CommonStartup.class",
                "assets/ae2federation/lang/en_us.json", "assets/ae2federation/ui/domain.xml",
                "META-INF/neoforge.mods.toml", "ae2federation.mixins.json", "META-INF/LICENSE"}
    if not required.issubset(binary) or "META-INF/LICENSE" not in source:
        raise ValueError("Missing production resource or license")
    entrypoint = "space/controlnet/ae2federation/neoforge/NeoForgeEntrypoint"
    client_entrypoint = "space/controlnet/ae2federation/neoforge/client/NeoForgeClientEntrypoint"
    if (b"halt" in binary[entrypoint + ".class"] or b"stop" in binary[client_entrypoint + ".class"]
            or b".halt(" in source[entrypoint + ".java"]
            or b".stop()" in source[client_entrypoint + ".java"]):
        raise ValueError("Production entrypoint contains task-only gameplay shutdown")
    if sha256_bytes(binary["META-INF/LICENSE"]) != pins["licenseSha256"] or binary["META-INF/LICENSE"] != source["META-INF/LICENSE"]:
        raise ValueError("License notice differs from pinned project license")
    forbidden = ("ae2federation_test", "space/controlnet/ae2federation/test/",
                 "com/glodblock/github/appflux/", "com/buuz135/functionalstorage/",
                 "de/maxhenkel/pipez/", "com/gregtechceu/gtceu/", "NativeStorageTrace")
    for name, content in list(binary.items()) + list(source.items()):
        if any(term in name or term.encode() in content for term in forbidden):
            raise ValueError("Test or optional content in production JAR")
    metadata = tomllib.loads(binary["META-INF/neoforge.mods.toml"].decode())
    if len(metadata["mods"]) != 1 or metadata["mods"][0]["modId"] != "ae2federation" or metadata["license"] != "AGPL-3.0-only":
        raise ValueError("Production metadata/license mismatch")
    if metadata["mods"][0]["version"] != pins["manifest"]["Implementation-Version"]:
        raise ValueError("Production mod version mismatch")
    dependencies = metadata["dependencies"]["ae2federation"]
    declared = {dependency["modId"]: dependency["versionRange"] for dependency in dependencies}
    if len(declared) != len(dependencies) or declared != pins["modRanges"]:
        raise ValueError("Unsupported dependency tuple")
    manifest = binary["META-INF/MANIFEST.MF"].decode()
    for key, value in pins["manifest"].items():
        if not re.search(rf"(?m)^{re.escape(key)}: {re.escape(value)}\r?$", manifest):
            raise ValueError("Unsupported manifest tuple")
    mixins = json.loads(binary["ae2federation.mixins.json"])
    if mixins["package"] != "space.controlnet.ae2federation.mixin" or len(metadata["mixins"]) != 1 or metadata["mixins"][0]["config"] != "ae2federation.mixins.json":
        raise ValueError("Production hook registration mismatch")
    return {"sha256": sha256(jar), "sourcesSha256": sha256(sources),
            "entries": sorted(binary), "sourceEntries": sorted(source), "tuple": declared}


def sha256_bytes(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def verify_runtime_loads(server_jar: Path, client_jar: Path, built_jar: Path,
                         server_log: str, client_log: str) -> str:
    hashes = [sha256(path) for path in (server_jar, client_jar, built_jar)]
    if len(set(hashes)) != 1:
        raise ValueError("Server and client did not load the same JAR as the build")
    for path, log in ((server_jar, server_log), (client_jar, client_log)):
        expected = f"AE2F_ARTIFACT_LOAD path={path.resolve()} sha256={hashes[0]}"
        if log.count(expected) != 1 or len(re.findall(r"AE2F_ARTIFACT_LOAD path=", log)) != 1:
            raise ValueError("Runtime load path or SHA-256 did not match the installed JAR")
    if "Done (" not in server_log or "AE2F_ARTIFACT_JOIN player=ArtifactProbe" not in client_log or "ArtifactProbe" not in server_log:
        raise ValueError("Server readiness or client/server connection was not observed")
    return hashes[0]


def verify_rebuilt_hashes(reference: ArchiveInspection, rebuilt: ArchiveInspection) -> None:
    if reference["sha256"] != rebuilt["sha256"] or reference["sourcesSha256"] != rebuilt["sourcesSha256"]:
        raise ValueError("Isolated rebuild differs from current production JAR")


def verify_target_settings(settings: str) -> None:
    targets = re.findall(r"(?m)^\s*include\(['\"]([^'\"]+)['\"]\)", settings)
    if targets != ["neoforge-1.21.1"]:
        raise ValueError("Unexpected Minecraft target")


def child_processes(parent: int) -> list[int]:
    children: dict[int, list[int]] = {}
    for process in Path("/proc").iterdir():
        if not process.name.isdigit():
            continue
        try:
            status = (process / "status").read_text()
            match = re.search(r"(?m)^PPid:\s+(\d+)$", status)
            if match is not None:
                children.setdefault(int(match.group(1)), []).append(int(process.name))
        except (FileNotFoundError, PermissionError):
            continue
    descendants = []
    def visit(pid: int) -> None:
        for child in children.get(pid, []):
            visit(child)
            descendants.append(child)
    visit(parent)
    return descendants


def process_alive(pid: int) -> bool:
    try:
        status = (Path("/proc") / str(pid) / "status").read_text()
    except FileNotFoundError:
        return False
    return re.search(r"(?m)^State:\s+[ZX]", status) is None


def require_approved_eula(approved_eula_file: Path | None) -> bytes:
    if approved_eula_file is None:
        raise ValueError("Task 39 runtime requires an explicit user-approved Minecraft EULA file (--approved-eula-file)")
    if approved_eula_file.is_symlink() or not approved_eula_file.is_file():
        raise ValueError("Explicit Minecraft EULA file must be a regular, user-provided file")
    content = approved_eula_file.read_bytes()
    if content != b"eula=true\n":
        raise ValueError("Explicit Minecraft EULA file must contain exactly 'eula=true\\n'")
    return content


def runtime_probe(root: Path, report_path: Path, approved_eula_file: Path | None = None) -> None:
    eula_content = require_approved_eula(approved_eula_file)
    assert approved_eula_file is not None
    approved_eula_path = approved_eula_file.resolve()
    jar = root / "neoforge-1.21.1/build/libs" / archive_names(root)[0]
    installed = []
    processes = []
    logs = []
    owned_directories = []
    timed_out = []
    descendants: set[int] = set()
    forced_descendants = []
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        port = str(listener.getsockname()[1])
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        control_port = listener.getsockname()[1]
    control_password = secrets.token_urlsafe(32)
    gradle = str(root / "gradlew")
    try:
        for role in ("server", "client"):
            directory = report_path.parent / f"artifact-{role}-runtime"
            directory.mkdir(parents=True, exist_ok=False)
            owned_directories.append(directory)
            (directory / "mods").mkdir(exist_ok=True)
            target = directory / "mods" / jar.name
            shutil.copyfile(jar, target)
            installed.append(target)
            if role == "server":
                (directory / "eula.txt").write_bytes(eula_content)
                (directory / "server.properties").write_text(
                     f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\n"
                     f"enable-rcon=true\nrcon.ip=127.0.0.1\nrcon.port={control_port}\n"
                     f"rcon.password={control_password}\n"
                     "enforce-secure-profile=false\nlevel-name=artifact-world\n")
            else:
                (directory / "options.txt").write_text("onboardAccessibility:false\n")
        commands = [
            [gradle, ":neoforge-1.21.1:runArtifactServer", f"-PfederationArtifactGameDir={installed[0].parent.parent}",
             f"-PfederationApprovedEulaFile={approved_eula_path}"],
            ["xvfb-run", "-a", gradle, ":neoforge-1.21.1:runArtifactClient",
             f"-PfederationArtifactGameDir={installed[1].parent.parent}", f"-PfederationArtifactPort={port}",
             f"-PfederationApprovedEulaFile={approved_eula_path}"],
        ]
        for index, marker in enumerate(("Done (", "AE2F_ARTIFACT_JOIN player=ArtifactProbe")):
            log = report_path.parent / f"artifact-{('server', 'client')[index]}.log"
            stream = log.open("wb")
            logs.append((log, stream))
            processes.append(subprocess.Popen(commands[index] + ["--no-daemon", "--no-configuration-cache",
                "--dependency-verification=strict"], cwd=root, stdout=stream, stderr=subprocess.STDOUT,
                start_new_session=True))
            deadline = time.monotonic() + (240 if index == 0 else 300)
            while time.monotonic() < deadline:
                text = log.read_text(errors="replace")
                if marker in text:
                    descendants.update(child_processes(processes[-1].pid))
                    break
                if processes[-1].poll() is not None or "LoadingFailedException" in text or "Failed to create mod instance" in text:
                    raise ValueError(f"Artifact {('server', 'client')[index]} terminated or failed: {log}")
                time.sleep(0.5)
            else:
                raise ValueError(f"Artifact {('server', 'client')[index]} timed out: {log}")
        server_log, client_log = (entry[0].read_text(errors="replace") for entry in logs)
        result = verify_runtime_loads(installed[0], installed[1], jar, server_log, client_log)
        if "AE2F_ARTIFACT_SERVER_JOIN player=ArtifactProbe" not in server_log:
            raise ValueError("Server did not acknowledge the client")
        close_client_window(child_processes(processes[1].pid))
        try:
            processes[1].wait(timeout=60)
        except subprocess.TimeoutExpired as error:
            raise ValueError("Artifact client did not shut down after window close") from error
        stop_server(control_port, control_password)
        for index in (1, 0):
            try:
                processes[index].wait(timeout=60)
            except subprocess.TimeoutExpired as error:
                raise ValueError(f"Artifact {('server', 'client')[index]} did not shut down after join") from error
    finally:
        for process in reversed(processes):
            descendants.update(child_processes(process.pid))
            if process.poll() is None:
                for pid in child_processes(process.pid):
                    try:
                        os.kill(pid, signal.SIGTERM)
                    except ProcessLookupError:
                        pass
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    for pid in child_processes(process.pid):
                        try:
                            os.kill(pid, signal.SIGKILL)
                        except ProcessLookupError:
                            pass
                    os.killpg(process.pid, signal.SIGTERM)
                    try:
                        process.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        os.killpg(process.pid, signal.SIGKILL)
                        try:
                            process.wait(timeout=10)
                        except subprocess.TimeoutExpired:
                            timed_out.append(process.pid)
        for pid in descendants:
            if process_alive(pid):
                try:
                    os.kill(pid, signal.SIGKILL)
                    forced_descendants.append(pid)
                except ProcessLookupError:
                    pass
        for _, stream in logs:
            stream.close()
        for role, path in zip(("server", "client"), installed):
            if path.is_file():
                shutil.copyfile(path, report_path.parent / f"artifact-installed-{role}.jar")
        for directory in owned_directories:
            shutil.rmtree(directory)
    if timed_out:
        raise ValueError(f"Artifact wrapper did not stop after SIGKILL: {timed_out}")
    if forced_descendants:
        raise ValueError(f"Artifact descendants survived wrapper shutdown: {forced_descendants}")
    exits = {role: process.poll() for role, process in zip(("server", "client"), processes)}
    for role, exit_code in exits.items():
        if exit_code != 0:
            raise ValueError(f"Artifact {role} wrapper exit {exit_code}: {report_path.parent / f'artifact-{role}.log'}")
    for role, (log, _) in zip(("server", "client"), logs):
        if "FAILURE: Build failed with an exception." in log.read_text(errors="replace"):
            raise ValueError(f"Artifact {role} wrapper reported build failure: {log}")
    report_path.write_text(json.dumps({"sha256": result,
        "eulaApprovalSha256": sha256_bytes(eula_content),
        "serverLoadedPath": str(installed[0].resolve()), "clientLoadedPath": str(installed[1].resolve()),
        "serverJoined": True, "clientJoined": True,
        "serverWrapperExit": exits["server"], "clientWrapperExit": exits["client"]}, indent=2) + "\n")


def git_output(root: Path, *arguments: str) -> bytes:
    return subprocess.check_output(["git", "-C", str(root), *arguments],
                                   env={**os.environ, "GIT_MASTER": "1"})


def source_paths(root: Path) -> list[str]:
    tracked = git_output(root, "ls-files", "-z").split(b"\0")
    untracked = git_output(root, "ls-files", "--others", "--exclude-standard", "-z").split(b"\0")
    return sorted({os.fsdecode(name) for name in tracked + untracked if name and not name.startswith(b".omo/")})


def source_digest(root: Path, names: list[str]) -> str:
    digest = hashlib.sha256()
    for name in names:
        path = root / name
        digest.update(os.fsencode(name) + b"\0")
        if path.is_symlink():
            raise ValueError(f"Source snapshot contains symbolic link: {name}")
        if path.is_file():
            digest.update(bytes.fromhex(sha256(path)))
        else:
            digest.update(b"deleted\0")
    return digest.hexdigest()


def isolated_build(root: Path, pins: ArtifactPins, report_path: Path) -> ArchiveInspection:
    verify_target_settings((root / "settings.gradle").read_text())
    names = source_paths(root)
    before = source_digest(root, names)
    revision = git_output(root, "rev-parse", "HEAD").decode().strip()
    local_archives = root / "neoforge-1.21.1/build/libs"
    reference = inspect_archive(local_archives / archive_names(root)[0],
                                local_archives / archive_names(root)[1], pins)
    with tempfile.TemporaryDirectory(prefix="ae2f-task39-") as temporary:
        worktree = Path(temporary) / "checkout"
        cache = Path(temporary) / "gradle-home"
        log = report_path.parent / "clean-build.log"
        subprocess.run(["git", "-C", str(root), "worktree", "add", "--detach", str(worktree), revision],
                       check=True, stdout=subprocess.DEVNULL, env={**os.environ, "GIT_MASTER": "1"})
        try:
            for name in names:
                original = root / name
                target = worktree / name
                if original.is_file():
                    target.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(original, target)
                elif target.exists():
                    target.unlink()
            if source_paths(root) != names or source_digest(root, names) != before:
                raise ValueError("Source changed during snapshot")
            command = [str(worktree / "gradlew"), ":neoforge-1.21.1:check",
                       ":neoforge-1.21.1:build", "--dependency-verification=strict",
                       "--no-configuration-cache", "--no-daemon", "--gradle-user-home", str(cache)]
            with log.open("wb") as output:
                process = subprocess.Popen(command, cwd=worktree, stdout=output,
                                           stderr=subprocess.STDOUT, start_new_session=True)
                try:
                    exit_code = process.wait(timeout=1200)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait()
                    raise ValueError("Isolated build timed out") from None
            if exit_code != 0 or b"BUILD SUCCESSFUL" not in log.read_bytes():
                raise ValueError(f"Isolated build failed (exit {exit_code}): {log}")
            prefix = worktree / "neoforge-1.21.1/build/libs"
            jar = prefix / archive_names(root)[0]
            sources = prefix / archive_names(root)[1]
            inspected = inspect_archive(jar, sources, pins)
            try:
                verify_rebuilt_hashes(reference, inspected)
            except ValueError:
                differences = {}
                for name, local_path, rebuilt_path in (
                    ("binary", local_archives / jar.name, jar),
                    ("sources", local_archives / sources.name, sources),
                ):
                    local_entries = archive_entries(local_path)
                    rebuilt_entries = archive_entries(rebuilt_path)
                    differences[name] = {
                        "referenceSha256": sha256(local_path), "rebuiltSha256": sha256(rebuilt_path),
                        "differentEntries": sorted(entry for entry in local_entries.keys() | rebuilt_entries.keys()
                            if local_entries.get(entry) != rebuilt_entries.get(entry)),
                        "entryOrderEqual": list(local_entries) == list(rebuilt_entries),
                    }
                    with zipfile.ZipFile(local_path) as local_zip, zipfile.ZipFile(rebuilt_path) as rebuilt_zip:
                        metadata = lambda archive: {entry.filename: (entry.date_time, entry.external_attr,
                            entry.compress_type, entry.extra, entry.comment) for entry in archive.infolist()}
                        local_metadata = metadata(local_zip)
                        rebuilt_metadata = metadata(rebuilt_zip)
                        differences[name]["metadataDifferences"] = {
                            entry: {"reference": str(local_metadata[entry]), "rebuilt": str(rebuilt_metadata.get(entry))}
                            for entry in local_metadata if local_metadata[entry] != rebuilt_metadata.get(entry)
                        }
                (report_path.parent / "rebuild-differences.json").write_text(json.dumps(differences, indent=2) + "\n")
                raise
            if source_paths(root) != names or source_digest(root, names) != before:
                raise ValueError("Source changed during isolated build")
            report_path.write_text(json.dumps({"sourceRevision": revision, "sourceDigest": before,
                "dependencyLockSha256": sha256(root / "gradle/verification-metadata.xml"),
                "artifact": inspected, "referenceSha256": reference["sha256"],
                "referenceSourcesSha256": reference["sourcesSha256"],
                "buildExit": exit_code, "cleanCache": True}, indent=2) + "\n")
            return inspected
        finally:
            subprocess.run(["git", "-C", str(root), "worktree", "remove", "--force", str(worktree)],
                           check=True, env={**os.environ, "GIT_MASTER": "1"})


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=["inspect", "clean-build", "runtime"])
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--report", type=Path)
    parser.add_argument("--approved-eula-file", type=Path)
    args = parser.parse_args()
    if args.action == "runtime":
        require_approved_eula(args.approved_eula_file)
    root = args.root.resolve()
    pins = load_pins(root)
    if args.action == "inspect":
        verify_target_settings((root / "settings.gradle").read_text())
        directory = root / "neoforge-1.21.1/build/libs"
        result = inspect_archive(directory / archive_names(root)[0],
                                 directory / archive_names(root)[1], pins)
    elif args.action == "runtime":
        if args.report is None:
            parser.error("runtime requires --report")
        report = args.report.resolve()
        report.parent.mkdir(parents=True, exist_ok=True)
        runtime_probe(root, report, args.approved_eula_file)
        result = {"sha256": json.loads(report.read_text())["sha256"], "entries": []}
    else:
        if args.report is None:
            parser.error("clean-build requires --report")
        report = args.report.resolve()
        report.parent.mkdir(parents=True, exist_ok=True)
        result = isolated_build(root, pins, report)
    print(json.dumps({"sha256": result["sha256"], "entries": len(result["entries"])}, sort_keys=True))


if __name__ == "__main__":
    main()
