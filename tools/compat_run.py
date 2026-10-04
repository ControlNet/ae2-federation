"""Compatibility runs: AE2 Federation's released jar inside production NeoForge servers beside other mods.

A profile (tests/compat/profiles/<name>.json) names a NeoForge version, an optional modpack server zip, mods by their
key in tests/compat/mods.json (each a pinned URL and SHA-512) and the compatibility tests or test groups to run. Each run installs that NeoForge server once into a cache, assembles a fresh server directory under
build/compat/runs/<profile>, adds AE2 Federation and the compatibility test mod (built by Gradle), starts the server
with the test mod's runner, and reads its JSON report. Profiles run in parallel, one server each.

    python3 tools/compat_run.py --accept-eula baseline               # one profile
    python3 tools/compat_run.py --accept-eula --all -j 8             # every profile, eight servers at a time
    python3 tools/compat_run.py --accept-eula --group addons         # every profile in a group
    python3 tools/compat_run.py --accept-eula --bare addons-all    # the same mods without AE2 Federation
    python3 tools/compat_run.py --list
    python3 tools/compat_run.py --list --json --group addons        # profile names, for a CI matrix
    python3 tools/compat_run.py report build/compat-results           # Markdown table of summary JSON files
    python3 tools/compat_run.py pin modrinth extended-ae 2.2.35      # print a pinned file entry
    python3 tools/compat_run.py pin url https://example.invalid/mod.jar

Starting a server accepts the Minecraft EULA (https://aka.ms/MinecraftEULA), so runs require --accept-eula.
Downloads are cached in $AE2F_COMPAT_CACHE (default ~/.cache/ae2federation-compat) and checked against their pinned
SHA-512. Third-party jars are never committed or uploaded; only logs and reports are kept.
"""
import argparse
import concurrent.futures
import hashlib
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROFILES = ROOT / "tests/compat/profiles"
RUNS = ROOT / "neoforge-1.21.1/build/compat/runs"
CACHE = Path(os.environ.get("AE2F_COMPAT_CACHE", Path.home() / ".cache/ae2federation-compat"))
# Seconds a server may keep running after it wrote its report before it is stopped.
REPORTED_GRACE = 30
USER_AGENT = "ae2federation-compat-tests (github.com/ControlNet/ae2-federation)"
SERVER_PROPERTIES = """\
level-type=minecraft\\:flat
generate-structures=false
online-mode=false
server-ip=127.0.0.1
server-port=0
spawn-protection=0
max-tick-time=-1
enable-rcon=false
enable-query=false
sync-chunk-writes=false
"""


def link(source: Path, target: Path) -> None:
    """A hard link where the cache and the run share a file system, else a copy."""
    try:
        os.link(source, target)
    except OSError:
        shutil.copy2(source, target)


def load_profiles() -> dict[str, dict]:
    """Profiles with their mods resolved from the shared pins in tests/compat/mods.json."""
    catalogue = json.loads((PROFILES.parent / "mods.json").read_text())
    profiles = {}
    for path in sorted(PROFILES.glob("*.json")):
        profile = json.loads(path.read_text())
        if profile.get("name") != path.stem:
            raise SystemExit(f"{path}: name must be {path.stem!r}")
        unknown = [key for key in profile.get("mods", []) if key not in catalogue]
        if unknown:
            raise SystemExit(f"{path}: mods {unknown} are not pinned in tests/compat/mods.json")
        profile["mods"] = [catalogue[key] for key in profile.get("mods", [])]
        profiles[path.stem] = profile
    return profiles


def sha512(path: Path) -> str:
    digest = hashlib.sha512()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def fetch(url: str, target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    partial = target.with_name(f"{target.name}.{os.getpid()}.part")
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=120) as response, partial.open("wb") as stream:
        shutil.copyfileobj(response, stream, 1 << 20)
    partial.replace(target)


def download(entry: dict) -> Path:
    """The cached file for a pinned {url, sha512, file} entry, downloaded and checked on first use."""
    target = CACHE / "downloads" / entry["sha512"][:16] / entry["file"]
    if not target.exists():
        fetch(entry["url"], target)
    actual = sha512(target)
    if actual != entry["sha512"]:
        target.unlink()
        raise RuntimeError(f"{entry['file']}: SHA-512 {actual} does not match the pinned {entry['sha512']}")
    return target


def neoforge_server(version: str) -> Path:
    """A NeoForge server installed once per version; runs link to its libraries."""
    home = CACHE / "servers" / f"neoforge-{version}"
    marker = home / "libraries/net/neoforged/neoforge" / version / "unix_args.txt"
    if marker.exists():
        return home
    installer = CACHE / "installers" / f"neoforge-{version}-installer.jar"
    if not installer.exists():
        fetch(f"https://maven.neoforged.net/releases/net/neoforged/neoforge/{version}/{installer.name}", installer)
    staging = home.with_name(home.name + ".staging")
    shutil.rmtree(staging, ignore_errors=True)
    staging.mkdir(parents=True)
    # The installer writes its log beside where it runs.
    result = subprocess.run(["java", "-jar", str(installer), "--installServer", str(staging)],
                            capture_output=True, text=True, cwd=staging)
    if result.returncode != 0 or not (staging / marker.relative_to(home)).exists():
        raise RuntimeError(f"NeoForge {version} installer failed:\n{result.stdout[-2000:]}{result.stderr[-2000:]}")
    shutil.rmtree(home, ignore_errors=True)
    staging.replace(home)
    return home


def unpack_pack(entry: dict) -> Path:
    """A modpack server zip, extracted once into the cache; `root` is the folder inside the zip holding mods/.

    Once extracted, the zip itself is no longer needed, so a cache may drop it."""
    home = CACHE / "packs" / entry["sha512"][:16]
    if not (home / ".complete").exists():
        archive = download(entry)
        shutil.rmtree(home, ignore_errors=True)
        with zipfile.ZipFile(archive) as zipped:
            zipped.extractall(home)
        (home / ".complete").write_text(entry["file"])
    return home / entry.get("root", "")


def built_jars() -> tuple[Path, Path]:
    version = next(line.split("=", 1)[1].strip() for line in (ROOT / "gradle.properties").read_text().splitlines()
                   if line.startswith("mod_version="))
    federation = ROOT / f"neoforge-1.21.1/build/libs/ae2federation-{version}.jar"
    tests = ROOT / f"neoforge-1.21.1/build/compat/ae2federation-compat-tests-{version}.jar"
    return federation, tests


def build() -> None:
    subprocess.run([str(ROOT / "gradlew"), ":neoforge-1.21.1:jar", ":neoforge-1.21.1:compatTestJar",
                    "--dependency-verification=strict", "--console=plain", "-q"], cwd=ROOT, check=True)


def assemble(profile: dict, directory: Path, bare: bool = False) -> None:
    shutil.rmtree(directory, ignore_errors=True)
    if "pack" in profile:
        # Hard links keep a 1 GB pack cheap to copy per run; the server only adds files.
        shutil.copytree(unpack_pack(profile["pack"]), directory, copy_function=link)
        for relative in profile.get("remove", []):
            for match in directory.glob(relative):
                shutil.rmtree(match) if match.is_dir() else match.unlink()
    directory.mkdir(parents=True, exist_ok=True)
    server = neoforge_server(profile["neoforge"])
    libraries = directory / "libraries"
    if libraries.is_symlink() or libraries.is_file():
        libraries.unlink()
    elif libraries.exists():
        shutil.rmtree(libraries)
    libraries.symlink_to(server / "libraries", target_is_directory=True)
    mods = directory / "mods"
    mods.mkdir(exist_ok=True)
    for entry in profile.get("mods", []):
        link(download(entry), mods / entry["file"])
    for jar in [] if bare else built_jars():
        shutil.copy2(jar, mods / jar.name)
    (directory / "eula.txt").write_text("eula=true\n")
    (directory / "server.properties").write_text(SERVER_PROPERTIES)


def run_bare(name: str, profile: dict, timeout: int) -> dict:
    """Starts the profile without AE2 Federation and stops it once it is up: does it start on its own?"""
    directory = RUNS / f"{name}-bare"
    started = time.monotonic()
    try:
        assemble(profile, directory, bare=True)
    except Exception as error:  # noqa: BLE001 - reported per profile
        return {"profile": name, "status": "setup-error", "detail": str(error), "seconds": 0}
    log = directory / "server-console.log"
    command = ["java", f"-Xmx{profile.get('memory', '4G')}", *profile.get("jvmArgs", []),
               f"@libraries/net/neoforged/neoforge/{profile['neoforge']}/unix_args.txt", "nogui"]
    with log.open("w") as stream:
        server = subprocess.Popen(command, cwd=directory, stdin=subprocess.PIPE, stdout=stream,
                                  stderr=subprocess.STDOUT, text=True)
        status = "timeout"
        while time.monotonic() - started < timeout:
            if server.poll() is not None:
                status = "crash"
                break
            if "]: Done (" in log.read_text(errors="replace"):
                status = "boots"
                server.stdin.write("stop\n")
                server.stdin.flush()
                break
            time.sleep(1)
        try:
            server.wait(timeout=120)
        except subprocess.TimeoutExpired:
            server.kill()
    result = {"profile": name, "status": status, "seconds": round(time.monotonic() - started)}
    if status != "boots":
        result["detail"] = failure_hint(log)
    return result


def run_profile(name: str, profile: dict, tests: str, timeout: int) -> dict:
    directory = RUNS / name
    started = time.monotonic()
    try:
        assemble(profile, directory)
    except Exception as error:  # noqa: BLE001 - reported per profile
        return {"profile": name, "status": "setup-error", "detail": str(error), "seconds": 0}
    report = directory / "compat-report.json"
    log = directory / "server-console.log"
    command = ["java", f"-Xmx{profile.get('memory', '4G')}", f"-Dae2federation.compat.tests={tests}",
               f"-Dae2federation.compat.report={report}", *profile.get("jvmArgs", []),
               f"@libraries/net/neoforged/neoforge/{profile['neoforge']}/unix_args.txt", "nogui"]
    with log.open("w") as stream:
        server = subprocess.Popen(command, cwd=directory, stdin=subprocess.DEVNULL, stdout=stream,
                                  stderr=subprocess.STDOUT)
        exit_code, reported = None, None
        while time.monotonic() - started < timeout:
            exit_code = server.poll()
            if exit_code is not None:
                break
            if reported is None and report.exists():
                reported = time.monotonic()
            # Some packs leave threads running after the server has stopped; the report is what counts.
            if reported is not None and time.monotonic() - reported > REPORTED_GRACE:
                break
            time.sleep(1)
        if exit_code is None:
            server.kill()
            server.wait()
    seconds = round(time.monotonic() - started)
    if not report.exists():
        status = "timeout" if exit_code is None else "no-report"
        return {"profile": name, "status": status, "exit": exit_code, "seconds": seconds,
                "detail": failure_hint(log)}
    data = json.loads(report.read_text())
    failed = [test for test in data["tests"] if test["status"] != "pass"]
    status = "setup-error" if data.get("setupError") else "fail" if failed else "pass"
    return {"profile": name, "status": status, "exit": exit_code, "seconds": seconds,
            "passed": len(data["tests"]) - len(failed), "total": len(data["tests"]), "failed": failed,
            "detail": data.get("setupError"), "mods": len(data.get("mods", {}))}


def failure_hint(log: Path) -> str:
    """The first loader error or crash line of a server that wrote no report."""
    if not log.exists():
        return "no server log"
    lines = log.read_text(errors="replace").splitlines()
    for marker in ("Mod loading has failed", "Missing or unsupported mandatory dependencies", "Exception in thread",
                   "Crash report", "ERROR"):
        for index, line in enumerate(lines):
            if marker in line:
                return "\n".join(lines[index:index + 12])
    return "\n".join(lines[-12:])


def pin(source: str, value: str, version: str | None) -> dict:
    if source == "modrinth":
        query = urllib.parse.urlencode({"loaders": '["neoforge"]', "game_versions": '["1.21.1"]'})
        request = urllib.request.Request(f"https://api.modrinth.com/v2/project/{value}/version?{query}",
                                         headers={"User-Agent": USER_AGENT})
        versions = json.load(urllib.request.urlopen(request, timeout=60))
        match = next((item for item in versions if version is None or version in item["version_number"]), None)
        if match is None:
            raise SystemExit(f"No 1.21.1 NeoForge version of {value} matches {version!r}")
        file = next((item for item in match["files"] if item["primary"]), match["files"][0])
        return {"name": value, "version": match["version_number"], "file": file["filename"], "url": file["url"],
                "sha512": file["hashes"]["sha512"]}
    file = urllib.parse.unquote(Path(urllib.parse.urlparse(value).path).name)
    staging = CACHE / "pins" / file
    fetch(value, staging)
    digest = sha512(staging)
    # Keep it where runs look for it, so a large pack is not downloaded again.
    cached = CACHE / "downloads" / digest[:16] / file
    cached.parent.mkdir(parents=True, exist_ok=True)
    staging.replace(cached)
    return {"name": Path(file).stem, "file": file, "url": value, "sha512": digest}


def report(directory: Path) -> str:
    """A Markdown table of every summary JSON under a directory, as the CI job summary shows it."""
    rows = []
    for path in sorted(directory.rglob("summary*.json")):
        rows.extend(json.loads(path.read_text()))
    lines = ["| Profile | Result | Tests | Time | Failed |", "|---|---|---|---|---|"]
    for row in sorted(rows, key=lambda item: item["profile"]):
        tests = f"{row['passed']}/{row['total']}" if "total" in row else ""
        failed = "<br>".join(f"`{test['id']}`: {test.get('error', test['status'])}" for test in row.get("failed", []))
        if not failed and row["status"] not in ("pass", "boots"):
            failed = (row.get("detail") or "").splitlines()[0] if row.get("detail") else ""
        lines.append(f"| {row['profile']} | {row['status']} | {tests} | {row['seconds']} s | {failed} |")
    return "\n".join(lines) + "\n"


def main() -> int:
    if len(sys.argv) > 1 and sys.argv[1] == "report":
        print(report(Path(sys.argv[2])), end="")
        return 0
    if len(sys.argv) > 1 and sys.argv[1] == "pin":
        parser = argparse.ArgumentParser(prog="compat_run.py pin")
        parser.add_argument("source", choices=["modrinth", "url"])
        parser.add_argument("value")
        parser.add_argument("version", nargs="?")
        args = parser.parse_args(sys.argv[2:])
        print(json.dumps(pin(args.source, args.value, args.version), indent=2))
        return 0
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("profiles", nargs="*")
    parser.add_argument("--all", action="store_true")
    parser.add_argument("--group", action="append", default=[])
    parser.add_argument("--list", action="store_true")
    parser.add_argument("--json", action="store_true", help="with --list: the selected profile names as JSON")
    parser.add_argument("--tests", help="comma-separated test ids, overriding each profile's own list")
    parser.add_argument("-j", "--jobs", type=int, default=4)
    parser.add_argument("--no-build", action="store_true", help="use the jars Gradle already built")
    parser.add_argument("--bare", action="store_true",
                        help="start each profile without AE2 Federation, to see whether a failure is its own")
    parser.add_argument("--accept-eula", action="store_true", help="accept the Minecraft EULA for the test servers")
    parser.add_argument("--summary", type=Path, help="write the results as JSON")
    args = parser.parse_args()
    profiles = load_profiles()
    names = list(profiles) if args.all else [name for name, profile in profiles.items()
                                             if profile.get("group") in args.group] + args.profiles
    if args.list:
        if args.json:
            print(json.dumps(names))
            return 0
        for name, profile in profiles.items():
            print(f"{name:28} {profile.get('group', ''):10} {profile.get('description', '')}")
        return 0
    unknown = [name for name in names if name not in profiles]
    if unknown or not names:
        parser.error(f"unknown profiles {unknown}" if unknown else "name profiles, --group or --all")
    if not args.accept_eula:
        parser.error("starting a Minecraft server accepts its EULA (https://aka.ms/MinecraftEULA): pass --accept-eula")
    if not args.no_build and not args.bare:
        build()
    for jar in [] if args.bare else built_jars():
        if not jar.exists():
            parser.error(f"{jar} is missing; build without --no-build")
    # Install each NeoForge version and fetch each file once, before the parallel runs share them.
    for version in sorted({profiles[name]["neoforge"] for name in names}):
        neoforge_server(version)
    files = {entry["sha512"]: entry for name in names for entry in profiles[name].get("mods", [])}
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        list(pool.map(download, files.values()))
    for name in names:
        if "pack" in profiles[name]:
            unpack_pack(profiles[name]["pack"])
    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=max(1, args.jobs)) as pool:
        futures = {(pool.submit(run_bare, name, profiles[name], profiles[name].get("timeout", 900)) if args.bare else
                    pool.submit(run_profile, name, profiles[name], args.tests or ",".join(profiles[name]["tests"]),
                                profiles[name].get("timeout", 900))): name for name in dict.fromkeys(names)}
        for future in concurrent.futures.as_completed(futures):
            result = future.result()
            results.append(result)
            counts = f" {result['passed']}/{result['total']}" if "total" in result else ""
            print(f"{result['status'].upper():12} {result['profile']}{counts} ({result['seconds']} s)", flush=True)
            for test in result.get("failed", []):
                print(f"    {test['id']}: {test.get('error', test['status'])}")
            if result["status"] not in ("pass", "fail", "boots") and result.get("detail"):
                print("    " + result["detail"].replace("\n", "\n    "))
    if args.summary:
        args.summary.parent.mkdir(parents=True, exist_ok=True)
        args.summary.write_text(json.dumps(sorted(results, key=lambda item: item["profile"]), indent=2))
    return 0 if all(result["status"] in ("pass", "boots") for result in results) else 1


if __name__ == "__main__":
    sys.exit(main())
