# Websocket functions (2026-10-22)

All in the `net` namespace (user). Connections are keyed by installation ID, then URL.

| Function | Kind | Dry run |
| --- | --- | --- |
| `net.connect(url) -> bool` | action, waits (Pending) | `True`, opens nothing |
| `net.send(url, text) -> bool` | action, waits (Pending) | `True`, sends nothing |
| `net.receive(url) -> Optional[str]` | read, oldest queued message | `None` |
| `net.connected(url) -> bool` | read | `False` |
| `net.disconnect(url) -> bool` | action; `False` if none | `False` |

## WebSockets (`automation/WebSockets`)

- Java `HttpClient` websocket on 2 daemon threads; one instance per server (`WebSockets.of(server)`), closed by
  `ProgramScheduler.closeServer`.
- Lifetime: connections outlive the run that opened them and survive restarts and setting changes; the scheduler's
  removal branch (`states.removeIf`, installation gone) calls `closeOwner(id)`. Only installations run for real
  (the editor's dry run never connects; the program tab only calls `ae2`).
- Limits (assistant choices): 4 open per installation (`OSError: an installation keeps at most 4 connections open`);
  unread queue keeps the newest 256; a message over 100,000 characters closes the connection with 1009. Text only,
  binary frames skipped. Connect and send wait at most the timeout (10 s now, config later).
- Errors: not ws/wss → `ValueError: url must start with ws:// or wss://: '...'`; failed connect →
  `OSError: could not connect to '...'`; send without a connection → `OSError: not connected to '...'`.
- Sends on one connection are chained (`Connection.sending`), since the JDK allows one outstanding send.
- `received(owner, url)` counts all messages that arrived: meant for the `ws_message` event.

## Pitfall found

The test echo server bound `InetAddress.getLoopbackAddress()`, which is `::1` inside the game JVM, while its URL
says `127.0.0.1`. The JDK then reports the refused connect as a `ConnectException` caused by
`ClosedChannelException` (its retry reuses the closed channel), which looks like something closed the socket.
Bind `127.0.0.1` explicitly.

Checks: `WebSocketsTest` (8, against the test-only `test/automation/WebSocketEchoServer`, a hand-written RFC 6455
server, since neither the JDK nor the game ships one), GameTest `programs.host-websocket`
(`programtalksoverawebsocket`).

## ws_message event

- `ProgramEvents.Kind.SOCKET`, `Spec("ws_message", ...)`, argument `url: str` (checked with `WebSockets.check`).
- `ProgramScheduler.checkSocket`: keeps the installation's connection to the URL open (owner = installation ID, the
  same connection the run's `net` calls use), retries every interval (min gap) while down, logs
  `listening on <url>` once open and `event not connected: <error>` once per distinct problem (shown red in the
  Install view). Fires when `WebSockets.received` grows; a connection it opens resets the baseline to 0, one opened
  earlier (by a run) only sets the baseline. The run reads messages itself with `net.receive`.
- Bug fixed with it: `uninstall` and `deviceGone` remove the state through `stop()`, so the `removeIf` branch never
  closed their connections; both now go through `forget(id, reason)`, which also closes them.
- The first connect in a fresh JVM takes about 20 GameTest ticks (HttpClient start-up); tests wait with
  `Steps.until(...)` instead of a fixed delay.

Checks: GameTest `programs.websocket-event` (`awebsocketmessagestartsarun`); `AutomationTabScenario` now counts 12
events. Pre-existing, not from this work: the scenario's check "the aside shows the arguments, trigger, gap, overlap
and state" fails at d3e6b62f too (the aside's values now include the Cost row).

## JDK client misses abrupt closes (found 2026-10-22, fixed with the config commit)

When a server pushes a message and drops the TCP connection without a close frame, the JDK `java.net.http`
WebSocket (21.0.12) sometimes delivers `onText` but never `onClose`/`onError`, and `isInputClosed()` stays false
(reproduced 6/40, 35/40 with prints in `onText`; unlimited demand did not help). Workaround: `Connection.check()`
pings at most once a second whenever the connection is looked at (`connected`, `send`, the `ws_message` check every
tick); a ping to the dead peer fails and marks it closed, within about 3 s. Also: `connect`'s Pending now waits on the
`whenComplete` stage, so `net.connected` is True once `net.connect` answered True; cancelling stops the JDK handshake
(`building`). Demand is requested once (`Long.MAX_VALUE`) since the queue drops the oldest itself.

## CI: tick waits are not time (2026-10-23)

Quick correctness failed from 795f60df to af8a9b58 on `programtalksoverawebsocket`, in the one-batch GameTest step
only: the program waited `mc.wait(1)` up to 100 times for the echo, and the whole test took under a second of wall
time, because the batch GameTest server runs ticks back to back. On the slower CI runner the echo had not arrived.
Rule: a GameTest that waits for anything outside the game waits in real time with `test/automation/WallClock.await`
(10 s), never by counting ticks. The `run` helper of `ProgramHostGameTests` does this while a run waits for a Pending
(`Runner.answerArrived()`). Check CI after every push: `gh run list --branch feature/function-system`.
