import os
import subprocess
import tempfile
import unittest
from pathlib import Path

from tools.privacy_guard import BUILT_IN, added_lines, find_leaks, load_patterns, main

# A made-up identifier standing in for a private word in the local pattern file.
PRIVATE = "examplehost-private"


def git(root: Path, *args: str, stdin: str | None = None) -> str:
    env = {**os.environ, "GIT_AUTHOR_NAME": "t", "GIT_AUTHOR_EMAIL": "t@example.org",
           "GIT_COMMITTER_NAME": "t", "GIT_COMMITTER_EMAIL": "t@example.org"}
    return subprocess.run(["git", "-c", "core.hooksPath=/dev/null", *args], cwd=root, check=True, text=True,
                          input=stdin, stdout=subprocess.PIPE, env=env).stdout


class PrivacyGuardTest(unittest.TestCase):
    def test_built_in_patterns_find_home_paths_and_private_keys(self):
        lines = [("a.md", 1, "cd /home/someone/repo"),  # privacy-guard: allow
                 ("b.md", 2, "open /Users/someone/x"),  # privacy-guard: allow
                 ("c.md", 3, r"C:\Users\someone\x"),  # privacy-guard: allow
                 ("d.md", 4, "-----BEGIN OPENSSH PRIVATE KEY-----"),  # privacy-guard: allow
                 ("e.md", 5, "python3 tools/perf_benchmark.py --remote user@example.org --key ~/.ssh/id"),
                 ("f.md", 6, "an example path /home/someone/x  # privacy-guard: allow")]  # privacy-guard: allow
        self.assertEqual([leak[:2] for leak in find_leaks(lines, BUILT_IN)],
                         [("a.md", 1), ("b.md", 2), ("c.md", 3), ("d.md", 4)])

    def test_local_patterns_ignore_comments_and_blank_lines_and_match_case_insensitively(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "private-patterns"
            path.write_text(f"# one regex per line\n\n{PRIVATE}\n")
            patterns = load_patterns(path)
        self.assertEqual(len(patterns), 1)
        self.assertEqual(len(find_leaks([("x", 1, f"see {PRIVATE.upper()}")], patterns)), 1)
        self.assertEqual(load_patterns(Path(directory) / "missing"), [])

    def test_added_lines_keep_file_and_new_line_numbers(self):
        diff = ("diff --git a/x.md b/x.md\n--- a/x.md\n+++ b/x.md\n@@ -1,0 +2,2 @@\n+first\n+second\n"
                "diff --git a/y.md b/y.md\n--- /dev/null\n+++ b/y.md\n@@ -0,0 +1 @@\n+third\n-gone\n")
        self.assertEqual(added_lines(diff), [("x.md", 2, "first"), ("x.md", 3, "second"), ("y.md", 1, "third")])

    def test_hooks_block_private_words_in_staged_files_messages_and_pushed_commits(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            git(root, "init", "-q", "-b", "main")
            (root / ".git/info").mkdir(exist_ok=True)
            (root / ".git/info/private-patterns").write_text(PRIVATE + "\n")
            (root / "clean.md").write_text("nothing personal\n")
            git(root, "add", "clean.md")
            self.assertEqual(main(["staged"], root), 0)
            git(root, "commit", "-q", "-m", "clean")
            base = git(root, "rev-parse", "HEAD").strip()

            (root / "leak.md").write_text(f"host {PRIVATE}\n")
            git(root, "add", "leak.md")
            self.assertEqual(main(["staged"], root), 1)

            message = root / "message.txt"
            message.write_text(f"Benchmark on {PRIVATE}\n")
            self.assertEqual(main(["message", str(message)], root), 1)
            message.write_text(f"Benchmark\n# {PRIVATE} in a comment is stripped by git\n")
            self.assertEqual(main(["message", str(message)], root), 0)

            git(root, "commit", "-q", "-m", "leaky")
            head = git(root, "rev-parse", "HEAD").strip()
            zero = "0" * 40
            self.assertEqual(main(["push"], root, f"refs/heads/main {head} refs/heads/main {base}\n"), 1)
            self.assertEqual(main(["push"], root, f"refs/heads/main {base} refs/heads/main {zero}\n"), 0)
            self.assertEqual(main(["push"], root, f"(delete) {zero} refs/heads/old {base}\n"), 0)
            self.assertEqual(main(["tree", "HEAD"], root), 1)
            self.assertEqual(main(["tree", base], root), 0)


if __name__ == "__main__":
    unittest.main()
