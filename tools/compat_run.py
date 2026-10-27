"""Compatibility runs: AE2 Federation's released jar inside production NeoForge servers beside other mods.

A profile (tests/compat/profiles/<name>.json) names a NeoForge version, an optional modpack server zip, mods by their
key in tests/compat/mods.json (each a pinned URL and SHA-512) and the compatibility tests or test groups to run. Each run installs that NeoForge server once into a cache, assembles a fresh server directory under
build/compat/runs/<profile>, adds AE2 Federation and the compatibility test mod (built by Gradle), starts the server
with the test mod's runner, and reads its JSON report. Profiles run in parallel, one server each.

    python3 tools/compat_run.py --accept-eula baseline               # one profile
    python3 tools/compat_run.py --accept-eula --all -j 8             # every profile, eight servers at a time
    python3 tools/compat_run.py --accept-eula -p 1 baseline          # one test at a time, to rule out a neighbour
    python3 tools/compat_run.py --accept-eula --group addons         # every profile in a group
    python3 tools/compat_run.py --accept-eula --bare addons-all    # the same mods without AE2 Federation
    python3 tools/compat_run.py --accept-eula --latest atm10        # the pack's newest CurseForge release instead
    python3 tools/compat_run.py latest --group modpacks             # pinned against newest release, no download
    python3 tools/compat_run.py --list
    python3 tools/compat_run.py --list --json --group addons        # profile names, for a CI matrix
    python3 tools/compat_run.py report build/compat-results           # Markdown table of summary JSON files
    python3 tools/compat_run.py parity fed joined                     # runs *-fed against joined runs *-joined
    python3 tools/compat_run.py pin modrinth extended-ae 2.2.35      # print a pinned file entry
    python3 tools/compat_run.py pin url https://example.invalid/mod.jar

Starting a server accepts the Minecraft EULA (https://aka.ms/MinecraftEULA), so runs require --accept-eula.
Downloads are cached in $AE2F_COMPAT_CACHE (default ~/.cache/ae2federation-compat) and checked against their pinned
SHA-512. Third-party jars are never committed or uploaded; only logs and reports are kept.

With --latest, a modpack profile with a "curseforge" project runs on the newest 1.21.1 NeoForge release of that pack
instead of its pinned server zip, found through CurseForge's public website API without a key. The NeoForge version
comes from the new pack itself. When the newest release is the pinned one, the profile is skipped and reported so.
"""
import argparse
import concurrent.futures
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.error
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
# Public file listing used by curseforge.com; it needs no key. Files download from the CDN that serves the website.
CURSEFORGE_FILES = "https://www.curseforge.com/api/v1/mods"
FORGECDN = "https://mediafilez.forgecdn.net/files"
NEOFORGE_VERSION = re.compile(r"\d+\.\d+\.\d+(?:-beta)?")
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


def fetch_json(url: str):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/json"})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            if attempt == 2 or (error.code != 429 and error.code < 500):
                raise RuntimeError(f"{url}: HTTP {error.code}") from None
        except urllib.error.URLError as error:
            if attempt == 2:
                raise RuntimeError(f"{url}: {error.reason}") from None
        time.sleep(2 ** attempt)


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


def run_bare(name: str, profile: dict, timeout: int, directory: Path | None = None) -> dict:
    """Starts the profile without AE2 Federation and stops it once it is up: does it start on its own?"""
    directory = directory or RUNS / f"{name}-bare"
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


def run_profile(name: str, profile: dict, tests: str, timeout: int, parallel: int = 1,
                properties: dict[str, str] | None = None, fresh: bool = True, directory: Path | None = None) -> dict:
    """Runs the tests in a newly assembled directory, or with fresh=False in the existing one and its saved world."""
    directory = directory or RUNS / name
    started = time.monotonic()
    try:
        if fresh:
            assemble(profile, directory)
        elif not (directory / "server.properties").exists():
            raise FileNotFoundError(f"{directory} holds no assembled server")
    except Exception as error:  # noqa: BLE001 - reported per profile
        return {"profile": name, "status": "setup-error", "detail": str(error), "seconds": 0}
    report = directory / "compat-report.json"
    report.unlink(missing_ok=True)
    log = directory / "server-console.log"
    command = ["java", f"-Xmx{profile.get('memory', '4G')}", f"-Dae2federation.compat.tests={tests}",
               f"-Dae2federation.compat.report={report}", f"-Dae2federation.compat.parallel={parallel}",
               *profile.get("jvmArgs", []), *[f"-D{key}={value}" for key, value in (properties or {}).items()],
               f"-Dae2federation.compat.deadlineEpochMillis={int((time.time() + timeout - (time.monotonic() - started)) * 1000)}",
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
    # A joined run skips the tests that check only Federation; they are neither run nor failed.
    ran = [test for test in data["tests"] if test["status"] != "skipped"]
    failed = [test for test in ran if test["status"] != "pass"]
    status = "setup-error" if data.get("setupError") else "fail" if failed else "pass"
    return {"profile": name, "status": status, "exit": exit_code, "seconds": seconds,
            "passed": len(ran) - len(failed), "total": len(ran), "skipped": len(data["tests"]) - len(ran),
            "failed": failed,
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
    return download_unpinned(value)


def download_unpinned(url: str) -> dict:
    """Downloads a file with no known hash into the cache and returns its pinned {name, file, url, sha512} entry."""
    file = urllib.parse.unquote(Path(urllib.parse.urlparse(url).path).name)
    staging = CACHE / "pins" / file
    fetch(url, staging)
    digest = sha512(staging)
    # Keep it where runs look for it, so a large pack is not downloaded again.
    cached = CACHE / "downloads" / digest[:16] / file
    cached.parent.mkdir(parents=True, exist_ok=True)
    staging.replace(cached)
    return {"name": Path(file).stem, "file": file, "url": url, "sha512": digest}


def curseforge_url(file_id: int, file_name: str) -> str:
    return f"{FORGECDN}/{file_id // 1000}/{file_id % 1000}/{urllib.parse.quote(file_name)}"


def curseforge_file_id(url: str) -> int | None:
    """The CurseForge file id in a CDN URL: .../files/9190/182/name.zip is 9190182."""
    match = re.search(r"/files/(\d+)/(\d+)/[^/]+$", urllib.parse.urlparse(url).path)
    return int(match[1]) * 1000 + int(match[2]) if match else None


def newest_release(files: list[dict]) -> dict | None:
    """The first release for Minecraft 1.21.1 on NeoForge in a listing sorted newest first; betas are skipped."""
    return next((row for row in files if row.get("releaseType") == 1 and not row.get("isEarlyAccessContent")
                 and {"1.21.1", "NeoForge"} <= set(row.get("gameVersions", []))), None)


def server_pack(parent: dict, additional: list[dict]) -> dict:
    """The server pack among a file's additional files.

    The website API marks a server pack only on its parent: hasServerPack and additionalServerPackFilesCount. An
    additional file carries no mark of its own, so the pack is found only when it is the parent's one additional file;
    anything else is an error rather than a guess from file names."""
    label = f"{parent.get('displayName')} (file {parent.get('id')})"
    if not parent.get("hasServerPack") or parent.get("additionalServerPackFilesCount", 0) < 1:
        raise RuntimeError(f"{label} has no server pack")
    files = [row for row in additional if row.get("parentProjectFileId") == parent["id"]]
    if parent.get("additionalServerPackFilesCount") != 1 or parent.get("additionalFilesCount") != 1 or len(files) != 1:
        found = ", ".join(f"{row.get('fileName')} ({row.get('id')})" for row in files) or "none"
        raise RuntimeError(f"{label} marks {parent.get('additionalServerPackFilesCount')} server packs among "
                           f"{parent.get('additionalFilesCount')} additional files; cannot tell which: {found}")
    return files[0]


def resolve_latest(project: int, get=fetch_json, page_size: int = 50) -> dict:
    """The newest 1.21.1 NeoForge release of a CurseForge modpack and its server pack."""
    index = 0
    while True:
        page = get(f"{CURSEFORGE_FILES}/{project}/files?pageIndex={index}&pageSize={page_size}"
                   "&sort=dateCreated&sortDescending=true&removeAlphas=true")
        parent = newest_release(page["data"])
        if parent is not None:
            break
        if not page["data"] or (index + 1) * page_size >= page["pagination"]["totalCount"]:
            raise RuntimeError(f"CurseForge project {project} has no 1.21.1 NeoForge release")
        index += 1
    pack = server_pack(parent, get(f"{CURSEFORGE_FILES}/{project}/files/{parent['id']}/additional-files")["data"])
    return {"project": project, "release": parent["id"], "version": parent["displayName"], "id": pack["id"],
            "file": pack["fileName"], "url": curseforge_url(pack["id"], pack["fileName"])}


def pack_neoforge(root: Path) -> tuple[str, str]:
    """The NeoForge version a server pack asks for, and the file that says so.

    Packs state it in different places: ServerPackCreator's variables.txt and manifest.json (Create Ultimate Selection
    2), a CurseForge manifest, a start script's NEOFORGE_VERSION (All the Mods 10), installed libraries, or the
    installer jar itself, whose install_profile.json names the version even when the jar is renamed (New Age Science
    and Technology ships it as neoforge-1.21.1.jar)."""
    def valid(value: str | None) -> str | None:
        return value if value and NEOFORGE_VERSION.fullmatch(value) else None

    variables = root / "variables.txt"
    if variables.is_file():
        values = {key.strip(): value.strip() for key, value in (
            line.split("=", 1) for line in variables.read_text(errors="replace").splitlines()
            if "=" in line and not line.lstrip().startswith("#"))}
        if values.get("MODLOADER", "").lower() == "neoforge" and valid(values.get("MODLOADER_VERSION")):
            return values["MODLOADER_VERSION"], "variables.txt"
    manifest = root / "manifest.json"
    if manifest.is_file():
        try:
            data = json.loads(manifest.read_text(errors="replace"))
        except ValueError:
            data = {}
        if str(data.get("modloader", "")).lower() == "neoforge" and valid(data.get("modloaderVersion")):
            return data["modloaderVersion"], "manifest.json"
        minecraft = data.get("minecraft") if isinstance(data.get("minecraft"), dict) else {}
        for loader in minecraft.get("modLoaders", []):
            if str(loader.get("id", "")).startswith("neoforge-") and valid(loader["id"][len("neoforge-"):]):
                return loader["id"][len("neoforge-"):], "manifest.json"
    for script in sorted([*root.glob("*.sh"), *root.glob("*.bat"), *root.glob("*.ps1")]):
        match = re.search(r"^\s*(?:set\s+|\$)?NEOFORGE_VERSION\s*=\s*\"?([0-9][^\s\"]*)",
                          script.read_text(errors="replace"), re.MULTILINE | re.IGNORECASE)
        if match and valid(match[1]):
            return match[1], script.name
    installed = sorted(path.name for path in (root / "libraries/net/neoforged/neoforge").glob("*") if valid(path.name))
    if len(installed) == 1:
        return installed[0], f"libraries/net/neoforged/neoforge/{installed[0]}"
    for jar in sorted(root.glob("*.jar")):
        try:
            with zipfile.ZipFile(jar) as zipped:
                version = json.loads(zipped.read("install_profile.json")).get("version", "")
        except (KeyError, ValueError, zipfile.BadZipFile):
            continue
        if version.startswith("neoforge-") and valid(version[len("neoforge-"):]):
            return version[len("neoforge-"):], f"{jar.name} (install_profile.json)"
    raise RuntimeError(f"{root.name}: no NeoForge version found in variables.txt, manifest.json, start scripts, "
                       "libraries/net/neoforged/neoforge/ or an installer jar's install_profile.json")


def latest_pack(profile: dict, get=fetch_json) -> tuple[dict, dict]:
    """The profile on its pack's newest release, and what that release is against the pinned one.

    The new pack is downloaded and unpacked into the cache once per CurseForge file, with no hash to check against,
    and its zip is dropped once extracted. A pin that is still the newest release is returned unchanged."""
    latest = resolve_latest(profile["curseforge"]["project"], get)
    pinned = profile["pack"]
    info = {"pinned": pinned["file"], "pinnedNeoforge": profile["neoforge"], "tested": latest["file"],
            "fileId": latest["id"], "version": latest["version"],
            "outdated": latest["id"] != curseforge_file_id(pinned["url"])}
    if not info["outdated"]:
        return profile, {**info, "neoforge": profile["neoforge"]}
    index = CACHE / "curseforge" / f"{latest['id']}.json"
    entry = json.loads(index.read_text()) if index.exists() else None
    if entry is None or not (CACHE / "packs" / entry["sha512"][:16] / ".complete").exists():
        entry = {**download_unpinned(latest["url"]), "name": Path(latest["file"]).stem}
        home = unpack_pack(entry)
        # A pack zipped with one top folder keeps mods/ in it.
        folders = [path for path in home.iterdir() if path.is_dir()]
        if not (home / "mods").is_dir() and len(folders) == 1:
            entry["root"] = folders[0].name
        (CACHE / "downloads" / entry["sha512"][:16] / entry["file"]).unlink(missing_ok=True)
        index.parent.mkdir(parents=True, exist_ok=True)
        index.write_text(json.dumps(entry, indent=2))
    version, source = pack_neoforge(unpack_pack(entry))
    return {**profile, "pack": entry, "neoforge": version}, {**info, "neoforge": version, "neoforgeSource": source}


def report(directory: Path) -> str:
    """A Markdown table of every summary JSON under a directory, as the CI job summary shows it.

    Runs on a pack's newest release (--latest) get their own table, pinned file beside tested file."""
    rows = []
    for path in sorted(directory.rglob("summary*.json")):
        rows.extend(json.loads(path.read_text()))

    def failures(row: dict) -> str:
        failed = "<br>".join(f"`{test['id']}`: {test.get('error', test['status'])}" for test in row.get("failed", []))
        if not failed and row["status"] not in ("pass", "boots", "pinned-is-latest"):
            failed = (row.get("detail") or "").splitlines()[0] if row.get("detail") else ""
        return failed

    def tests(row: dict) -> str:
        if "total" not in row:
            return ""
        return f"{row['passed']}/{row['total']}" + (f" ({row['skipped']} skipped)" if row.get("skipped") else "")

    pinned = [row for row in rows if "latest" not in row]
    newest = [row for row in rows if "latest" in row]
    lines = []
    if pinned or not newest:
        lines += ["| Profile | Result | Tests | Time | Failed |", "|---|---|---|---|---|"]
        for row in sorted(pinned, key=lambda item: item["profile"]):
            lines.append(f"| {row['profile']} | {row['status']} | {tests(row)} | {row['seconds']} s "
                         f"| {failures(row)} |")
    if newest:
        lines += [""] if lines else []
        lines += ["Newest modpack releases (--latest):", "",
                  "| Profile | Pinned | Tested | NeoForge | Result | Tests | Time | Failed |",
                  "|---|---|---|---|---|---|---|---|"]
        for row in sorted(newest, key=lambda item: item["profile"]):
            latest = row["latest"]
            tested = (f"{latest['tested']} (file {latest['fileId']})" if latest.get("outdated")
                      else "same as pinned, not run again" if "tested" in latest else "")
            neoforge = latest.get("neoforge", "")
            if neoforge and neoforge != latest.get("pinnedNeoforge"):
                neoforge = f"{neoforge} (pinned {latest['pinnedNeoforge']})"
            lines.append(f"| {row['profile']} | {latest.get('pinned', '')} | {tested} | {neoforge} | {row['status']} | "
                         f"{tests(row)} | {row['seconds']} s | {failures(row)} |")
    return "\n".join(lines) + "\n"


def parity_class(federation: dict, joined: dict | None) -> str:
    """How a test through Federation compares with the same test on one joined Grid.

    A Federation job is "slower" when it takes more than half again the joined job's ticks, and at least 10 more."""
    if joined is None:
        return "no control"
    if joined["status"] == "skipped":
        return "Federation-only"
    passed = federation["status"] == "pass", joined["status"] == "pass"
    if passed == (False, True):
        return "Federation fails"
    if passed == (True, False):
        return "joined fails"
    if passed == (False, False):
        return "both fail"
    if "jobTicks" not in federation or "jobTicks" not in joined:
        return "no control"
    slack = max(10, joined["jobTicks"] // 2)
    return "slower" if federation["jobTicks"] > joined["jobTicks"] + slack else "parity"


def parity(federation: dict[str, dict], joined: dict[str, dict]) -> str:
    """A Markdown table of the tests a Federation run and a joined run (-Dae2federation.compat.joinBeforeOrder=true)
    both reported, by profile, from each run's compat-report.json; tests neither side compared are only counted."""
    for name, data in federation.items():
        if data.get("joinBeforeOrder"):
            raise ValueError(f"{name}: the Federation run ordered joined")
    for name, data in joined.items():
        if not data.get("joinBeforeOrder"):
            raise ValueError(f"{name}: the joined run ordered through Federation")

    def shown(test: dict | None) -> str:
        if test is None:
            return "missing"
        if test["status"] == "skipped":
            return "skipped"
        if test["status"] != "pass":
            return (test.get("error") or test["status"]).splitlines()[0][:160].replace("|", "/")
        return f"pass, {test['jobTicks']} ticks" if "jobTicks" in test else "pass"

    rows, counts = [], {}
    for profile in sorted(set(federation) & set(joined)):
        native = {test["id"]: test for test in joined[profile]["tests"]}
        for test in federation[profile]["tests"]:
            other = native.get(test["id"])
            kind = parity_class(test, other)
            counts[kind] = counts.get(kind, 0) + 1
            if kind == "no control":
                continue
            gap = ""
            if kind in ("parity", "slower"):
                difference = test["jobTicks"] - other["jobTicks"]
                gap = f"{difference:+d} ({test['jobTicks'] / max(1, other['jobTicks']):.2f}x)"
            name = test["id"].rsplit(".", 1)[-1]
            rows.append(f"| {profile} | {name} | {shown(test)} | {shown(other)} | {gap} | {kind} |")
    lines = [", ".join(f"{kind}: {count}" for kind, count in sorted(counts.items())), "",
             "| Profile | Test | Federation | Joined | Ticks | Class |", "|---|---|---|---|---|---|", *rows]
    return "\n".join(lines) + "\n"


def parity_reports(suffix: str) -> dict[str, dict]:
    """Each profile's compat-report.json from the runs named <profile>-<suffix>."""
    return {path.parent.name.removesuffix(f"-{suffix}"): json.loads(path.read_text())
            for path in sorted(RUNS.glob(f"*-{suffix}/compat-report.json"))}


def playthrough_chunk_properties(properties: dict[str, str], tests: str, chunk: int, size: int) -> dict[str, str]:
    """Partition topology seeds or Phase 1 target offsets without changing caller settings."""
    settings = dict(properties)
    topology = any(test.lower().replace("-", "") == "playthroughtopology" for test in tests.split(","))
    prefix = "ae2federation.playthrough." + ("topology." if topology else "")
    offset, count = ("seedStart", "seedCount") if topology else ("skipTargets", "maxTargets")
    settings[prefix + offset] = str(int(properties.get(prefix + offset, "0")) + chunk * size)
    settings[prefix + count] = str(size)
    return settings


def latest_status(names: list[str], profiles: dict[str, dict]) -> list[dict]:
    """Each profile's pinned pack against its newest CurseForge release, without downloading either."""
    rows = []
    for name in names:
        pinned = profiles[name]["pack"]
        try:
            latest = resolve_latest(profiles[name]["curseforge"]["project"])
        except Exception as error:  # noqa: BLE001 - reported per profile
            rows.append({"profile": name, "pinned": pinned["file"], "error": str(error)})
            continue
        rows.append({"profile": name, "pinned": pinned["file"], "tested": latest["file"], "fileId": latest["id"],
                     "version": latest["version"], "outdated": latest["id"] != curseforge_file_id(pinned["url"])})
    return rows


def main() -> int:
    if len(sys.argv) > 1 and sys.argv[1] == "report":
        print(report(Path(sys.argv[2])), end="")
        return 0
    if len(sys.argv) > 1 and sys.argv[1] == "parity":
        print(parity(parity_reports(sys.argv[2]), parity_reports(sys.argv[3])), end="")
        return 0
    if len(sys.argv) > 1 and sys.argv[1] == "latest":
        parser = argparse.ArgumentParser(prog="compat_run.py latest",
                                         description="pinned modpack against its newest CurseForge release")
        parser.add_argument("profiles", nargs="*")
        parser.add_argument("--group", action="append", default=[])
        parser.add_argument("--json", action="store_true")
        args = parser.parse_args(sys.argv[2:])
        profiles = load_profiles()
        names = [name for name, profile in profiles.items() if profile.get("group") in args.group] + args.profiles
        unknown = [name for name in names if "curseforge" not in profiles.get(name, {})]
        if unknown or not names:
            parser.error(f"profiles without a CurseForge project: {unknown}" if unknown else "name profiles or --group")
        rows = latest_status(list(dict.fromkeys(names)), profiles)
        if args.json:
            print(json.dumps(rows, indent=2))
        for row in [] if args.json else rows:
            state = row.get("error") or ("newer release" if row["outdated"] else "pin is the newest release")
            print(f"{row['profile']:8} pinned {row['pinned']}\n{'':8} newest {row.get('tested', '?')} "
                  f"(file {row.get('fileId', '?')}): {state}")
        return 0 if all("error" not in row for row in rows) else 1
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
    parser.add_argument("-p", "--parallel", type=int, default=32,
                        help="tests run side by side in each server; 1 runs them one after another")
    parser.add_argument("--no-build", action="store_true", help="use the jars Gradle already built")
    parser.add_argument("--bare", action="store_true",
                        help="start each profile without AE2 Federation, to see whether a failure is its own")
    parser.add_argument("--accept-eula", action="store_true", help="accept the Minecraft EULA for the test servers")
    parser.add_argument("--latest", action="store_true",
                        help="run each modpack profile on its newest CurseForge release; skip it when that is the pin")
    parser.add_argument("--summary", type=Path, help="write the results as JSON")
    parser.add_argument("--run-suffix", default="", help="suffix isolated run directories, e.g. runs/nast-smoke")
    parser.add_argument("--jvm-property", action="append", default=[], metavar="KEY=VALUE",
                        help="per-process JVM system property; repeat for multiple properties")
    parser.add_argument("--chunks", type=int, default=1, help="run each profile in N isolated playthrough chunks")
    parser.add_argument("--chunk-size", type=int, help="eligible targets (topology: seeds) per chunk; requires --chunks")
    args = parser.parse_args()
    if args.run_suffix and not re.fullmatch(r"[A-Za-z0-9_-]+", args.run_suffix):
        parser.error("run suffix must contain only letters, numbers, underscores or hyphens")
    properties = {}
    for value in args.jvm_property:
        key, separator, setting = value.partition("=")
        if not separator or not re.fullmatch(r"[A-Za-z0-9_.-]+", key): parser.error("JVM properties must be KEY=VALUE")
        if key in {"ae2federation.compat.report", "ae2federation.compat.deadlineEpochMillis"}:
            parser.error(f"{key} is owned by the runner")
        properties[key] = setting
    if args.chunks < 1 or args.chunk_size is not None and args.chunk_size < 1:
        parser.error("chunks and chunk-size must be positive")
    if args.chunks > 1 and args.chunk_size is None: parser.error("--chunks requires --chunk-size")
    if args.bare and (properties or args.chunks > 1): parser.error("JVM properties/chunks require compat tests")
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
    names = list(dict.fromkeys(names))
    results, latest = [], {}
    if args.latest:
        missing = [name for name in names if "curseforge" not in profiles[name]]
        if missing:
            parser.error(f"--latest needs a CurseForge project in {missing}")
        for name in list(names):
            try:
                profiles[name], latest[name] = latest_pack(profiles[name])
            except Exception as error:  # noqa: BLE001 - reported per profile
                results.append({"profile": name, "status": "setup-error", "detail": str(error), "seconds": 0,
                                "latest": {"pinned": profiles[name]["pack"]["file"],
                                           "pinnedNeoforge": profiles[name]["neoforge"]}})
                names.remove(name)
                print(f"SETUP-ERROR  {name}: newest release: {error}", flush=True)
                continue
            info = latest[name]
            print(f"LATEST       {name}: pinned {info['pinned']}, newest {info['tested']} (file {info['fileId']}), "
                  f"NeoForge {info['neoforge']}" + ("" if info["outdated"] else ": the pin is the newest release, "
                                                   "not run again"), flush=True)
            if not info["outdated"]:
                results.append({"profile": name, "status": "pinned-is-latest", "seconds": 0, "latest": info})
                names.remove(name)
    if names and not args.no_build and not args.bare:
        build()
    for jar in [] if args.bare or not names else built_jars():
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
    suffix = "-latest" if args.latest else ""
    with concurrent.futures.ThreadPoolExecutor(max_workers=max(1, args.jobs)) as pool:
        futures = {}
        for name in dict.fromkeys(names):
            for chunk in range(args.chunks):
                run_name = name + ("-" + args.run_suffix if args.run_suffix else "")
                settings = dict(properties)
                if args.chunk_size is not None:
                    run_name += f"-chunk{chunk + 1}"
                    settings = playthrough_chunk_properties(properties, args.tests or "", chunk, args.chunk_size)
                future = (pool.submit(run_bare, run_name, profiles[name], profiles[name].get("timeout", 900),
                                      RUNS / f"{run_name}{suffix}-bare") if args.bare else
                          pool.submit(run_profile, run_name, profiles[name], args.tests or ",".join(profiles[name]["tests"]),
                                      profiles[name].get("timeout", 900), args.parallel, settings,
                                      directory=RUNS / f"{run_name}{suffix}"))
                futures[future] = name
        for future in concurrent.futures.as_completed(futures):
            result = future.result()
            if futures[future] in latest:
                result["latest"] = latest[futures[future]]
            results.append(result)
            counts = f" {result['passed']}/{result['total']}" if "total" in result else ""
            counts += f" ({result['skipped']} skipped)" if result.get("skipped") else ""
            print(f"{result['status'].upper():12} {result['profile']}{counts} ({result['seconds']} s)", flush=True)
            for test in result.get("failed", []):
                print(f"    {test['id']}: {test.get('error', test['status'])}")
            if result["status"] not in ("pass", "fail", "boots") and result.get("detail"):
                print("    " + result["detail"].replace("\n", "\n    "))
    if args.summary:
        args.summary.parent.mkdir(parents=True, exist_ok=True)
        args.summary.write_text(json.dumps(sorted(results, key=lambda item: item["profile"]), indent=2))
    return 0 if all(result["status"] in ("pass", "boots", "pinned-is-latest") for result in results) else 1


if __name__ == "__main__":
    sys.exit(main())
