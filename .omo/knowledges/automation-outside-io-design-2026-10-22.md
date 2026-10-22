# Programs outside the game: files, HTTP, websockets (design, 2026-10-22)

Status: agreed. Order (user): dicts and sets (done), `fs` (done, `program-fs-2026-10-22.md`), `net.http` (done,
`program-http-2026-10-22.md`; headers are a dict, not lines), websockets (functions done,
`program-websocket-2026-10-22.md`, with the `ws_message` event), then the config section (done,
`program-config-2026-10-22.md`). All five steps are on feature/function-system.

## Scope decided by the user

- Programs get actions outside the game: files inside `<world>/ae2federation/workspace`, HTTP requests and
  websockets.
- Security is deferred until the mod has a permission system: no host allowlist, no private-address filter, no
  per-player rights for now.
- The server config can turn each group off. All groups are on by default.

CC: Tweaked and OpenComputers both shipped network APIs with a default "allow all, deny private" filter. Both later
had SSRF advisories (CVE-2023-37261, CVE-2023-37262, CVE-2026-47695). The permission system should bring an allowlist.

## Decisions

| Decision | Choice | By |
|---|---|---|
| Workspace | One folder per world, `<world>/ae2federation/workspace/`, beside `programs/` (`ProgramStore` resolves from `LevelResource.ROOT`) | user |
| Security | None beyond config switches until the permission system | user |
| Config | Section `automation` in `ae2federation-server.toml`: `allowFileAccess`, `allowHttp`, `allowWebsockets`, all `true` | user (names: assistant) |
| A switched-off group | The functions stay in the Library panel and the checker; a call stops the run with `PermissionError: <group> is turned off on this server` | assistant |
| Path confinement | Normalize; reject absolute paths and `..`; the real path must stay under the workspace root; no symlinks out. This keeps runs correct and the server stable; it is not a permission. | assistant |
| File content | Text only (`str`, UTF-8). One file at most 100,000 characters, the language's string cap | assistant |
| Workspace size | Configurable cap, default 16 MB in all; a write past it stops the run with `OSError` | assistant |
| `fs` reads | `fs.read`, `fs.exists` and `fs.list` are reads, not actions (conditions may use them). The size caps keep them cheap on the server thread | assistant |
| HTTP shape | Text only: `net.http(method, url, body="", headers=[])` returns the built-in record `Response(status: int, text: str, headers: list[str])`; headers are `"Name: value"` lines | assistant; see the question below |
| HTTP execution | Asynchronous on a fixed pool of 4 daemon threads. The call takes the tick's one action; waiting for the answer takes none | assistant |
| HTTP limits | Timeout default 10 s (config `httpTimeoutSeconds`, never "no limit"); request and response bodies at most 100,000 characters; at most 4 requests in flight per computing device | assistant |
| Failure | Timeout, refused connection or bad URL stop the run with `OSError`; any HTTP status, 404 or 500 too, is a `Response` | assistant |
| Run stopped mid-request | Unload, server stop or reinstall drops the request; the answer is discarded. A paused device keeps it for resume | assistant |
| Dry run | Never touches disk or network. `fs.write`/`append`/`delete` return as if done; `net.http` returns `Response(0, "", [])` and the editor notes "not sent in a dry run" | assistant |
| Cost | No change: `Cost` counts graph nodes and moving calls; these calls move no resources | assistant |

## Library

`fs` (files under the workspace; paths are relative, `/` separated):

| Function | Gives |
|---|---|
| `fs.read(path)` | the file's text, or `None` if there is none |
| `fs.exists(path)` | whether the file or folder exists |
| `fs.list(path="")` | the names in a folder, folders ending in `/` |
| `fs.write(path, text)` | **action:** writes the file, making its folders |
| `fs.append(path, text)` | **action:** adds to the end of the file |
| `fs.delete(path)` | **action:** deletes a file or an empty folder; `False` if there was nothing |

`net`:

| Function | Gives |
|---|---|
| `net.http(method, url, body="", headers=[])` | **action:** sends the request and waits for its `Response` |

## Runner change (phase 2)

Today `Evaluator.Host.call` is synchronous and only `mc.wait` suspends a run (`Runner.sleeping`).

- Add `script.Pending` with `isDone()`, `value()` and `error()`, free of Minecraft types so JUnit can drive it.
- A host call may return a `Pending`. `Runner` keeps it with the node's result local and answers `WAITING` until it
  is done. It then binds the value or throws the `RunError`, and goes on in the same tick.
- `Runner.wake()` does not end a pending wait.
- `ProgramHost` completes the futures on the pool and reads them only on the server thread, through this poll.
- `ProgramScheduler.closeServer` shuts the pool and cancels open requests.

## Websockets (phase 3; functions built as outlined, see `program-websocket-2026-10-22.md`)

`ProgramEvents` passes no value into a run, and its events take fixed arguments. A socket made at run time cannot be
an event argument. A workable shape:

- Connections are keyed by URL per installation. `net.connect(url)` opens one or returns the open one.
- `net.send(url, text)` is an action. `net.receive(url)` is a read that takes the oldest queued message, or `None`.
- A new event kind `SOCKET` takes the URL as its argument: `ws_message` starts a run when a message is queued.
- A connection lives as long as its installation: removing the installation, breaking the device or stopping the
  server closes it.
- Config `allowWebsockets`; at most 4 open connections per installation (built per installation, not per device);
  each message at most 100,000 characters.

## Phases

1. `fs` and the config section. Synchronous, no Runner change.
2. `net.http` and `script.Pending`.
3. Websockets, after the outline above is agreed.

Each phase includes:

- JUnit for the `script` part (`Pending`, path confinement) and GameTests for the host part. The HTTP GameTest serves
  from a local `com.sun.net.httpserver.HttpServer`.
- Library entries and lang strings in EN and ZH; `CompletionTest` pins descriptions.
- Guide rows in `library.md` (EN and ZH) and the config switches on the guide page.
- An in-game before and after under `build/<topic>-check/`.

## Dictionaries first (settled)

The user chose to add dictionaries and sets to the language before this work; they are done (see
`script-dict-set-2026-10-22.md`). Headers and a later `json` helper can use dicts.
