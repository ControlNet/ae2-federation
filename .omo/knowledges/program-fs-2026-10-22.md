# The fs namespace (2026-10-22)

Programs read and write text files in `<world>/ae2federation/workspace` (`ProgramHost.workspace(server)`), shared
by every program of the world (confirmed by the user 2026-10-23: one shared folder, not per installation or player,
until a permission system decides ownership). Code: `automation/WorkspaceFiles` (no Minecraft types, JUnit `WorkspaceFilesTest`),
wired in `ProgramHost.call`; library entries in `StandardLibrary` under `fs`.

| Function | Kind | Notes |
|---|---|---|
| `fs.read(path) -> Optional[str]` | read | `None` for a missing file; a folder, >100,000 characters or non-UTF-8 is `OSError` |
| `fs.exists(path) -> bool` | read | `""` is the workspace itself |
| `fs.list(path="") -> list[str]` | read | sorted, folders end with `/`; a missing folder lists `[]`; a file is `OSError` |
| `fs.write(path, text) -> bool` | action | makes folders |
| `fs.append(path, text) -> bool` | action | makes the file; the result must stay ≤100,000 characters |
| `fs.delete(path) -> bool` | action | file or empty folder; `False` when nothing; non-empty folder is `OSError` |

- Bad path shape (absolute, `\`, `:`, control characters, empty segment) → `ValueError: path must be relative, with
  '/' between names: ...`; `.`/`..` or a link leading out (including a dangling link) → `ValueError: path must stay
  inside the workspace: ...`. Confinement takes the real path of the nearest existing part of the path.
- Workspace cap 16 MB (`DEFAULT_CAP_BYTES`, behind a `LongSupplier` for the config step): each write/append walks the
  folder and sums file sizes, so files written outside the game count. Reads never walk.
- The folder is created by the first change only. Disk errors say `the disk refused it (<ExceptionClass>)` so the
  server's own paths never reach the run log.
- Dry runs (`ProgramHost.dryRun`) check paths and caps and change nothing; `fs.delete` answers whether the target
  exists.
- No `PermissionError` switch yet: the user ordered the config section last (after websockets).

Checks: `WorkspaceFilesTest` (20), `StandardLibraryTest` (examples check, action set pinned), GameTest
`programs.host-files` (`programkeepsfilesintheworldsworkspace`), image `build/fs-check/before-after.png`.
