"""Keeps personal identifiers out of the repository: file contents, commit messages and pushed history.

The built-in patterns catch what is personal in any clone (home directory paths, private keys). Words that are
personal to one developer (names, hosts, institutions) go in ``<git common dir>/info/private-patterns``, one
case-insensitive Python regex per line, ``#`` comments allowed. That file is never tracked, so the words it guards
never appear in the repository either. A line that has to show such a pattern, like the ones below, carries
``privacy-guard: allow``.

    python3 tools/privacy_guard.py staged              # pre-commit: lines added by the staged diff
    python3 tools/privacy_guard.py message <file>      # commit-msg: the message, without git's # comments
    python3 tools/privacy_guard.py push < <refs>       # pre-push: messages and added lines of every pushed commit
    python3 tools/privacy_guard.py tree [<rev>]        # every tracked text file at <rev> (default HEAD)

``tools/git-hooks`` runs the first three; enable it in a clone with
``git config core.hooksPath "$(git rev-parse --show-toplevel)/tools/git-hooks"``.
"""
import re
import subprocess
import sys
from pathlib import Path

BUILT_IN = [re.compile(pattern, re.IGNORECASE) for pattern in (
    r"/home/[^/\s`'\"]+/",  # privacy-guard: allow
    r"/Users/[^/\s`'\"]+/",  # privacy-guard: allow
    r"[A-Z]:\\Users\\[^\\\s]+",  # privacy-guard: allow
    r"-----BEGIN [A-Z ]*PRIVATE KEY-----",  # privacy-guard: allow
)]
ALLOW = "privacy-guard: allow"
ZERO = "0" * 40
Line = tuple[str, int, str]


def load_patterns(path: Path) -> list[re.Pattern]:
    if not path.is_file():
        return []
    return [re.compile(line.strip(), re.IGNORECASE) for line in path.read_text().splitlines()
            if line.strip() and not line.lstrip().startswith("#")]


def find_leaks(lines: list[Line], patterns: list[re.Pattern]) -> list[tuple[str, int, str, str]]:
    return [(where, number, text, pattern.pattern) for where, number, text in lines if ALLOW not in text
            for pattern in patterns if pattern.search(text)][:1000]


def added_lines(diff: str) -> list[Line]:
    """The lines a unified diff adds, with their file and line number in the new version."""
    lines, path, number = [], "", 0
    for line in diff.splitlines():
        if line.startswith("+++ "):
            path = line[6:] if line.startswith("+++ b/") else line[4:]
        elif line.startswith("@@"):
            number = int(re.match(r"@@ -\S+ \+(\d+)", line).group(1))
        elif line.startswith("+"):
            lines.append((path, number, line[1:]))
            number += 1
        elif line.startswith(" "):
            number += 1
    return lines


def git(root: Path, *args: str) -> str:
    return subprocess.run(["git", *args], cwd=root, check=True, stdout=subprocess.PIPE,
                          text=True, errors="replace").stdout


def message_lines(where: str, text: str) -> list[Line]:
    return [(where, number, line) for number, line in enumerate(text.splitlines(), 1)
            if not line.startswith("#")]


def pushed_lines(root: Path, refs: str) -> list[Line]:
    lines = []
    for entry in refs.splitlines():
        if not entry.strip():
            continue
        local_ref, local, _, remote = entry.split()[:4]
        if local == ZERO:
            continue
        if git(root, "cat-file", "-t", local).strip() == "tag":
            lines += message_lines(f"tag {local_ref}", git(root, "cat-file", "-p", local))
        exclude = ["--not", "--remotes"] if remote == ZERO else [f"^{remote}"]
        for commit in git(root, "rev-list", local, *exclude).split():
            lines += message_lines(f"message {commit[:7]}", git(root, "log", "-1", "--format=%B", commit))
            diff = git(root, "diff-tree", "-p", "-U0", "--no-color", "--root", "--diff-merges=first-parent", commit)
            lines += [(f"{commit[:7]} {path}", number, text) for path, number, text in added_lines(diff)]
    return lines


def tree_lines(root: Path, rev: str) -> list[Line]:
    lines = []
    for path in git(root, "ls-tree", "-r", "-z", "--name-only", rev).split("\0"):
        if not path:
            continue
        data = subprocess.run(["git", "cat-file", "blob", f"{rev}:{path}"], cwd=root, check=True,
                              stdout=subprocess.PIPE).stdout
        if b"\0" not in data:
            lines += [(path, number, text) for number, text in
                      enumerate(data.decode(errors="replace").splitlines(), 1)]
    return lines


def main(argv: list[str], root: Path | None = None, stdin: str | None = None) -> int:
    root = root or Path.cwd()
    mode = argv[0] if argv else ""
    if mode == "staged":
        lines = added_lines(git(root, "diff", "--cached", "-U0", "--no-color", "--no-ext-diff"))
    elif mode == "message" and len(argv) == 2:
        lines = message_lines("commit message", Path(argv[1]).read_text())
    elif mode == "push":
        lines = pushed_lines(root, sys.stdin.read() if stdin is None else stdin)
    elif mode == "tree":
        lines = tree_lines(root, argv[1] if len(argv) > 1 else "HEAD")
    else:
        print(__doc__, file=sys.stderr)
        return 2
    common = Path(git(root, "rev-parse", "--path-format=absolute", "--git-common-dir").strip())
    leaks = find_leaks(lines, BUILT_IN + load_patterns(common / "info/private-patterns"))
    for where, number, text, pattern in leaks:
        print(f"privacy guard: {where}:{number}: matches /{pattern}/: {text.strip()[:160]}", file=sys.stderr)
    if leaks:
        print("privacy guard: remove these personal identifiers (or rewrite the commits) and try again",
              file=sys.stderr)
    return 1 if leaks else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
