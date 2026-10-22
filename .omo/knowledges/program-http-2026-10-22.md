# net.http and waiting runs (2026-10-22)

`net.http(method: HttpMethod, url: str, body: str = "", headers: dict[str, str] = {}) -> Response` is an action.
`Response` is a built-in record: `status: int`, `text: str`, `headers: dict[str, str]`. `HttpMethod` is a typed
string: GET, POST, PUT, PATCH, DELETE, HEAD.

## Waiting in the runner

- `script.Pending` (`done()`, `value()`, `cancel()`): a host call may return one. `Runner` keeps it with the action
  and frame, answers `WAITING` until `done()`, then binds the value (or fails at the call with its `RunError`) and
  goes on in that tick. The wait uses no action, so the answer's tick may act again.
- `Runner.wake()` does not end a pending wait; `Runner.awaiting()` tells; `Runner.cancel()` gives the answer up.
- `ProgramScheduler` cancels the pending answers of runs it drops (stop, restart, removed installation);
  `closeServer` also closes `HttpRequests`. `DryRun` breaks out (and cancels) if a host ever returned a Pending.

## HttpRequests (`automation/HttpRequests`)

- Java `HttpClient` on 4 daemon threads, redirects NORMAL. One instance per server (`HttpRequests.of(server)`).
- Timeout 10 s for the whole request (request timeout plus `orTimeout`, so a trickling body is bounded too).
- At most 16 requests waiting per server (assistant choice; the design's "4 per device" was simplified):
  `OSError: at most 16 HTTP requests can wait at once`.
- Answers over 400,000 bytes are cut off while streaming; over 100,000 characters after decoding (charset from
  Content-Type, else UTF-8) is `OSError: the response is longer than 100000 characters`.
- Errors: bad method/URL/restricted header → `ValueError` at the call; timeout, refused, unknown host → `OSError`.
  Any HTTP status is an answer. Header names lower case, repeated values joined with ", ".
- Dry run sends nothing: `Response(0, "", {})`.
- No host allowlist yet (permission system later, user 2026-10-22). Timeout and switches come with the config step.

## Also fixed on the way

- `Ops.literal` now fills string, `[]` and `{}` defaults of library calls (`fs.list()` crashed, 805b703f).
- `dict`/`set` offered as type hints in completion (`Completion.TYPES`).

Checks: `HttpRequestsTest` (local `com.sun.net.httpserver`), RunnerTest pending cases, GameTest `programs.host-http`
(`programasksalocalwebserver`), image `build/http-check/before-after.png`.
