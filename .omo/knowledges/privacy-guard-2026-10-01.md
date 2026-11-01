# Privacy guard and the 2026-10-01 history rewrite

## Rule

No personal identifier goes into a tracked file, a commit message or a tag message: no names or user names, no
institutions, no host names, no SSH identity files, no home directory paths. Write generic forms instead, such as
`--remote <user@host> --key <identity>`, "a remote isolated benchmark host", "in the repository root".

## Guard

- `tools/privacy_guard.py` checks the staged diff (`staged`), a commit message (`message <file>`), every commit and
  tag a push sends (`push`, reading pre-push's stdin), or every tracked text file of a revision (`tree [<rev>]`).
- Built-in patterns: home directory paths (Linux, macOS, Windows) and private key headers. The quick CI workflow runs
  the tests and `tree HEAD` with them.
- Personal words live in `<git common dir>/info/private-patterns` (one case-insensitive regex per line), which is
  never tracked, so the guard's own sources never contain them. Each clone has to create that file.
- A line that must show a pattern (the guard's own rules, its tests) carries `privacy-guard: allow`.
- Hooks: `tools/git-hooks/{pre-commit,commit-msg,pre-push}`, enabled per clone with
  `git config core.hooksPath "$(git rev-parse --show-toplevel)/tools/git-hooks"`. They run the guard script next to
  them (so a worktree of an old commit is still checked) and then the hook of the same name from the global
  `core.hooksPath`, which a repository-level `core.hooksPath` would otherwise hide.

## History rewrite (2026-10-01)

- A remote benchmark host, its login and identity file had been written into two `.omo/knowledges` docs and the
  `perf_benchmark.py` docstring and `--remote-root` default; two older docs carried local home directory paths
  (`docs/testing/production-jar.md`, `tools/blockbench/docs/mcp.md`, since before v0.0.1).
- Rewritten with `git filter-branch --tree-filter ... --msg-filter ... --tag-name-filter cat -- dev master
  release/v0.0.1 feature/gui-v2 v0.0.1 v0.0.2 ^<first leaking commit>^`: 85 commits, both annotated tags (same tag
  message and tagger, new target). Author, committer and dates are unchanged.
- `--remote-root` now defaults to `ae2f/perf` under the remote login's `$HOME`, read over SSH, instead of a path that
  named the user.
- Commit hashes quoted in commit messages and `.omo/knowledges` (7 and 40 characters) were remapped with
  filter-branch's `map`; a commit can only quote earlier commits, which are already mapped when it is rewritten.
- Pitfalls: filter-branch runs filters with `/bin/sh` (dash): no `${var:0:7}`; and in zsh an unquoted `$REFS` is
  one word, use an array.
- Pushing master re-runs the Release workflow; with the release already published it uploads nothing new
  (`release_plan` sees the moved tag on the same commit, the publish step finds the release published, and the
  platform uploads skip versions they have). Push tags before master.
- GitHub can still serve the old commits by hash until GitHub Support purges them; local backups were kept under
  `refs/heads/backup/*-before-rewrite` and `refs/backup-tags/*`.

## Secret Guard interoperability

- The external `secret-guard` scanner flags `tests/test_privacy_guard.py:25`, an intentional positive fixture in
  `PrivacyGuardTest.test_built_in_patterns_find_home_paths_and_private_keys`. Its string is only the 35-character
  OpenSSH private-key opening marker, not key material. Keep the fixture; do not delete it to silence the scan.
- The fixture's `privacy-guard: allow` comment is understood by this repository's guard, not by the external
  scanner. Classify this match manually rather than exempting the entire test file.
- An empty `staged` scan has examined no files. The external scanner's `tracked` mode examines current worktree
  contents of tracked paths, not repository history or untracked files.
- Credential scanning does not establish absence of personal identifiers. Audit current tracked files and
  non-ignored untracked files with the repository's private patterns and personal-path rules before staging.
- A worktree privacy audit found 13 username-bearing home paths in
  `tests/compat/version-boundaries-evidence.json`, under `serverExperiments[*].result.detail`. The evidence file
  was untracked and not ignored, so the preceding external `tracked` and empty `staged` scans missed it.
  Preserve diagnostic meaning when sanitizing machine-local paths; do not hide evidence by ignoring the file.
- Remediated all 13 paths to repository-relative paths. JSON parsing and a recursive before/after comparison
  confirmed only those diagnostic strings changed; all other evidence remained intact. A subsequent scan of
  tracked and non-ignored untracked text found no personal-pattern or username matches. Ignored files and Git
  history were outside that verification scope.
