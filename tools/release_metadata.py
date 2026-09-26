"""Shared archive names and GitHub release planning; standard library only."""

import os
import re
import subprocess
from pathlib import Path


def properties(root: Path) -> dict[str, str]:
    return dict(line.strip().split("=", 1) for line in (root / "gradle.properties").read_text().splitlines()
                if "=" in line and not line.lstrip().startswith("#"))


def archive_names(root: Path) -> tuple[str, str]:
    values = properties(root)
    base = f"{values['mod_id']}-{values['mod_version']}"
    return f"{base}.jar", f"{base}-sources.jar"


def release_plan(version: str, tags: list[str], commit: str, tag_commit: str | None) -> dict[str, str]:
    pattern = r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)"
    if not re.fullmatch(pattern, version):
        raise ValueError("Release version must be MAJOR.MINOR.PATCH without a prefix or suffix")
    current = tuple(map(int, version.split(".")))
    previous = [tuple(map(int, tag[1:].split("."))) for tag in tags
                if re.fullmatch("v" + pattern, tag)]
    if previous and current < max(previous):
        raise ValueError("Release version is older than an existing release tag")
    return {"version": version, "tag": f"v{version}",
            "should_release": str(tag_commit is None or tag_commit == commit).lower()}


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    version = properties(root)["mod_version"]
    tags = subprocess.check_output(["git", "tag", "--list", "v*"], cwd=root, text=True).splitlines()
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
    tag_commit = None
    if f"v{version}" in tags:
        tag_commit = subprocess.check_output(
            ["git", "rev-parse", f"refs/tags/v{version}^{{commit}}"], cwd=root, text=True).strip()
    output = "".join(f"{key}={value}\n" for key, value in release_plan(version, tags, commit, tag_commit).items())
    print(output, end="")
    if "GITHUB_OUTPUT" in os.environ:
        with open(os.environ["GITHUB_OUTPUT"], "a") as stream:
            stream.write(output)


if __name__ == "__main__":
    main()
