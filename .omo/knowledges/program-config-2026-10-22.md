# Server switches for outside calls (2026-10-22)

Section `[automation]` of `config/ae2federation-server.toml` (a world's `serverconfig` copy overrides it), read live
like the other sections through `FederationServerConfig.apply`:

| Key | Default | Range | Effect |
| --- | --- | --- | --- |
| `allowFileAccess` | true | | `fs.*` calls |
| `allowHttp` | true | | `net.http` |
| `allowWebsockets` | true | | `net.connect/send/receive/connected/disconnect`, `ws_message`; off closes open connections |
| `networkTimeoutSeconds` | 10 | 1-300 | HTTP request, websocket connect and send |
| `workspaceSizeMb` | 16 | 1-4096 | workspace folder cap |

- Defaults on (user, 2026-10-22). Names: assistant. The design's `httpTimeoutSeconds` became
  `networkTimeoutSeconds`, since it also bounds websocket connect/send.
- `automation/AutomationRule`: static volatile values like `BeamLinkRule`; `require(call)` throws
  `PermissionError: file access is turned off on this server (allowFileAccess)` (and the HTTP/websocket variants).
  `ProgramHost.call` checks it before the dry-run answers, so the editor's dry run reports it too. The language has no
  try/except, so no error hierarchy is needed.
- `HttpRequests.of`/`WebSockets.of` take `AutomationRule::timeout`; `ProgramHost` gives `WorkspaceFiles`
  `AutomationRule::workspaceCapBytes`; both read per call, so a reload applies at once.
- Scheduler: `checkSocket` logs `event not connected: websockets are turned off ...` (red); each tick with the switch
  off calls `WebSockets.ifPresent(server, WebSockets::disconnectAll)`.
- GameTest `programs.host-switches` (`theserverturnsoutsidecallsoff`) flips the static rule off and back within one
  synchronous step, because every test in the batch shares it.
