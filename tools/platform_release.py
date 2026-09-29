"""Modrinth and CurseForge release metadata; standard library only.

The tags reproduce the manually published 0.0.1 files (Modrinth version eRN7VfQD,
CurseForge file 8982557). Credentials are read from the environment and never printed.
"""

import argparse
import json
import os
import re
import sys
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tools.release_metadata import properties

MODRINTH = "https://api.modrinth.com/v2"
CURSEFORGE = "https://minecraft.curseforge.com/api/game"
# Public file listing used by curseforge.com; the upload token cannot list project files.
CURSEFORGE_FILES = "https://www.curseforge.com/api/v1/mods"
CHANNELS = ("alpha", "beta", "release")
LOADER = "neoforge"
ENVIRONMENT = "client_and_server"
JAVA = 21
# Modrinth projects: Applied Energistics 2 (ae2) and LDLib, which publishes LDLib2 for 1.21.1.
MODRINTH_DEPENDENCIES = [{"project_id": "XxWD5pD3", "dependency_type": "required"},
                         {"project_id": "B1CBVXHX", "dependency_type": "required"}]


def platform_plan(values: dict[str, str], repository: str) -> dict:
    version, minecraft = values["mod_version"], values["minecraft_version"]
    if not re.fullmatch(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)", version):
        raise ValueError("Release version must be MAJOR.MINOR.PATCH without a prefix or suffix")
    channel = values.get("release_channel", "")
    if channel not in CHANNELS:
        raise ValueError(f"release_channel in gradle.properties must be one of {', '.join(CHANNELS)}")
    family = re.fullmatch(r"(\d+\.\d+)(?:\.\d+)?", minecraft)
    if not family:
        raise ValueError(f"Invalid Minecraft version: {minecraft}")
    return {
        "version": version, "name": version, "channel": channel, "loader": LOADER, "environment": ENVIRONMENT,
        "game_versions": [minecraft], "dependencies": MODRINTH_DEPENDENCIES,
        "filename": f"{values['mod_id']}-{LOADER}-{minecraft}-{version}.jar",
        "changelog": f"AE2 Federation {version} for Minecraft {minecraft} / NeoForge. "
                     f"Release notes: https://github.com/{repository}/releases/tag/v{version}",
        # CurseForge (name, type name) pairs; the type disambiguates duplicate names such as 1.21.1.
        "curseforge_tags": [[minecraft, f"Minecraft {family.group(1)}"], ["NeoForge", "Modloader"],
                            [f"Java {JAVA}", "Java"], ["Client", "Environment"], ["Server", "Environment"]],
    }


def resolve_curseforge(tags: list, versions: list[dict], types: list[dict]) -> list[int]:
    ids = []
    for name, type_name in tags:
        type_ids = {row["id"] for row in types if row["name"] == type_name}
        if not type_ids:
            raise ValueError(f"CurseForge tag type {type_name} not found")
        matches = {row["id"] for row in versions if row["name"] == name and row["gameVersionTypeID"] in type_ids}
        if len(matches) != 1 or type(value := next(iter(matches))) is not int or value <= 0:
            raise ValueError(f"CurseForge tag {name} ({type_name}): expected exactly one ID, found {len(matches)}")
        ids.append(value)
    return ids


def validate_modrinth_tags(plan: dict, game_versions: list[dict], loaders: list[dict]) -> None:
    stable = {row["version"] for row in game_versions if row["version_type"] == "release"}
    missing = set(plan["game_versions"]) - stable
    if missing:
        raise ValueError(f"Modrinth is missing stable version tags: {', '.join(sorted(missing))}")
    if plan["loader"] not in {row["name"] for row in loaders}:
        raise ValueError(f"Modrinth is missing loader tag: {plan['loader']}")


def validate_modrinth_project(project: dict) -> None:
    if "environment" in project:
        valid = project["environment"] == [ENVIRONMENT]
    else:
        valid = project.get("client_side") == "required" and project.get("server_side") == "required"
    if not valid:
        raise ValueError(f"Modrinth project environment must be {ENVIRONMENT}; check the project settings")


def modrinth_version_exists(versions: list[dict], version: str) -> bool:
    return any(row["version_number"] == version for row in versions)


def curseforge_file_exists(files: list[dict], filename: str) -> bool:
    return any(filename in (row.get("fileName"), row.get("displayName")) for row in files)


def curseforge_files(project: str, fetch, page_size: int = 50) -> list[dict]:
    files, index = [], 0
    while True:
        page = fetch(f"{CURSEFORGE_FILES}/{project}/files?pageIndex={index}&pageSize={page_size}&removeAlphas=false")
        files += page["data"]
        total = page["pagination"]["totalCount"]
        if len(files) >= total:
            return files
        if not page["data"] or index >= 200:
            raise ValueError(f"CurseForge file listing ended after {len(files)} of {total} files")
        index += 1


def verify_modrinth(actual: dict, plan: dict) -> None:
    def dependencies(rows):
        return sorted((row.get("project_id"), row.get("version_id"), row["dependency_type"]) for row in rows)

    expected = {"version_number": plan["version"], "version_type": plan["channel"],
                "game_versions": sorted(plan["game_versions"]), "loaders": [plan["loader"]],
                "environment": plan["environment"]}
    for key, value in expected.items():
        observed = actual.get(key)
        if isinstance(observed, list):
            observed = sorted(observed)
        if observed != value:
            raise ValueError(f"Published Modrinth {key} mismatch: expected {value}, got {observed}")
    if dependencies(actual.get("dependencies", [])) != dependencies(plan["dependencies"]):
        raise ValueError("Published Modrinth dependencies differ from the release plan")
    if [row["filename"] for row in actual.get("files", []) if row.get("primary")] != [plan["filename"]]:
        raise ValueError(f"Published Modrinth primary file is not {plan['filename']}")


def fetch_json(url: str, headers: dict[str, str]):
    request = Request(url, headers={"User-Agent": "ControlNet/ae2-federation release", **headers})
    for attempt in range(3):
        try:
            with urlopen(request, timeout=45) as response:
                return json.load(response)
        except HTTPError as error:
            if attempt == 2 or (error.code != 429 and error.code < 500):
                # Never print request headers, credentials, or response bodies.
                raise ValueError(f"Metadata request failed: {url} (HTTP {error.code})") from None
        except URLError:
            if attempt == 2:
                raise ValueError(f"Metadata request failed: {url} (network error)") from None
        time.sleep(2 ** attempt)


def credentials(*names: str) -> list[str]:
    values = [os.environ.get(name, "") for name in names]
    missing = [name for name, value in zip(names, values) if not value]
    if missing:
        raise ValueError(f"Missing required environment variables: {', '.join(missing)}")
    return values


def modrinth_project() -> tuple[str, dict[str, str]]:
    token, project = credentials("MODRINTH_TOKEN", "MODRINTH_PROJECT_ID")
    if not re.fullmatch(r"[A-Za-z0-9_-]+", project):
        raise ValueError("Invalid MODRINTH_PROJECT_ID")
    # Authenticated reads also cover projects and versions that are still under review.
    return project, {"Authorization": token}


def write_outputs(outputs: dict[str, str]) -> None:
    text = "".join(f"{key}={value}\n" for key, value in outputs.items())
    print(text, end="")
    if "GITHUB_OUTPUT" in os.environ:
        with open(os.environ["GITHUB_OUTPUT"], "a") as stream:
            stream.write(text)


def preflight(plan: dict) -> None:
    project, headers = modrinth_project()
    validate_modrinth_project(fetch_json(f"{MODRINTH}/project/{project}", headers))
    validate_modrinth_tags(plan, fetch_json(f"{MODRINTH}/tag/game_version", {}),
                           fetch_json(f"{MODRINTH}/tag/loader", {}))
    token, curseforge_project = credentials("CURSEFORGE_TOKEN", "CURSEFORGE_PROJECT_ID")
    if not re.fullmatch(r"[0-9]+", curseforge_project):
        raise ValueError("Invalid CURSEFORGE_PROJECT_ID")
    curseforge_headers = {"X-Api-Token": token}
    ids = resolve_curseforge(plan["curseforge_tags"], fetch_json(f"{CURSEFORGE}/versions", curseforge_headers),
                             fetch_json(f"{CURSEFORGE}/version-types", curseforge_headers))
    if "GITHUB_STEP_SUMMARY" in os.environ:
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
            summary.write("## Platform release metadata\n\n| Field | Value |\n| --- | --- |\n"
                          f"| File | `{plan['filename']}` |\n| Channel | {plan['channel']} |\n"
                          f"| Modrinth | {', '.join(plan['game_versions'])}, {plan['loader']}, "
                          f"{plan['environment']} |\n"
                          f"| CurseForge | {', '.join(name for name, _ in plan['curseforge_tags'])} "
                          f"(IDs {', '.join(map(str, ids))}) |\n")
    write_outputs({"channel": plan["channel"], "name": plan["name"], "filename": plan["filename"],
                   "changelog": plan["changelog"], "environment": plan["environment"],
                   "game_versions": json.dumps(plan["game_versions"]),
                   "dependencies": json.dumps(plan["dependencies"], separators=(",", ":")),
                   "curseforge_ids": ",".join(map(str, ids))})


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("plan", help="Print the release plan without network access")
    commands.add_parser("preflight", help="Validate every platform tag before publication")
    commands.add_parser("modrinth-exists", help="Report whether this version is already on Modrinth")
    commands.add_parser("curseforge-exists", help="Report whether this file is already public on CurseForge")
    commands.add_parser("verify-modrinth", help="Check an uploaded version").add_argument("version_id")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    plan = platform_plan(properties(root), os.environ.get("GITHUB_REPOSITORY", "ControlNet/ae2-federation"))
    if args.command == "plan":
        print(json.dumps(plan, indent=2))
    elif args.command == "preflight":
        preflight(plan)
    elif args.command == "modrinth-exists":
        project, headers = modrinth_project()
        exists = modrinth_version_exists(fetch_json(f"{MODRINTH}/project/{project}/version", headers),
                                         plan["version"])
        write_outputs({"exists": str(exists).lower()})
    elif args.command == "curseforge-exists":
        (project,) = credentials("CURSEFORGE_PROJECT_ID")
        if not re.fullmatch(r"[0-9]+", project):
            raise ValueError("Invalid CURSEFORGE_PROJECT_ID")
        files = curseforge_files(project, lambda url: fetch_json(url, {}))
        write_outputs({"exists": str(curseforge_file_exists(files, plan["filename"])).lower()})
    else:
        if not re.fullmatch(r"[A-Za-z0-9]+", args.version_id):
            raise ValueError("Invalid Modrinth version ID")
        _, headers = modrinth_project()
        verify_modrinth(fetch_json(f"{MODRINTH}/version/{args.version_id}", headers), plan)
        print(f"Verified Modrinth version {args.version_id}")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, OSError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        sys.exit(1)
