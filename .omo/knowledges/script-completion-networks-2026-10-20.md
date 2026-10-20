# Script editor: network completion and Ctrl+Space (2026-10-20)

User report, five points; four are in the function-system editor:

1. **Value and type ran together** ("network-abcdefNetwork"). `CompletionPopup` drew a candidate's detail right after
   its label. Now `separation(candidate)` puts `GAP` between them, except before a function signature, which starts
   with "(" and stays attached to its name.
2. **Networks from the whole server.** The client's `FederationAutomationView.snapshot()` built its `GameCatalog` from
   every `ProgramSync.NetworkInfo`. `GameCatalog` now takes a separate `completedNetworks` supplier. The client
   completes only the networks whose `domain` is the tab's domain, while checking still accepts every network. The
   server catalog (`ProgramStore.catalog(server, domain)`) was already per domain.
3. **Completion ids did not match the topology.**
   - The topology names an unnamed network "Network 082A", the first four hex digits of its NetworkId
     (`FederationTopologyView.displayName`).
   - Short IDs (`network-x4k9pa`) are drawn at random by `NetworkShortIdBook`, so the two have nothing in common.
   - `NetworkInfo` now carries the NetworkId. Completion shows the topology's name beside each short ID, through the
     new `GameCatalog.Named.shown`.
4. **Duplicates.** The index lists one `NetworkInfo` per (network, domain). A network is a member of its physical
   domain and also of a Bridge's `direct:bridge:...` domain, so it came twice. Filtering by the tab's domain and
   keying by short ID removes this.
5. **Ctrl+Space seemed not to work.** It was already handled on key down, but platforms also type a space with it.
   - `onCharTyped` inserted that space, and the text change dropped the answer and hid the list.
   - Now a space typed while Ctrl is down is ignored.
   - A dash also closed the list, so `"network-` never completed. ':' and '-' now keep an open value list going.

Testing notes:
- In LDLib uitest, `UIEvent.isCtrlDown()` reads `KeyState`, which the synthetic driver fills only for keys that are
  held. A test must press `GLFW_KEY_LEFT_CONTROL` itself; a modifier flag on the Space event is not enough.
- `ui.automation` checks the network list against the Router's domain members, the details, Escape, and Ctrl+Space
  with its char event.
- Before/after image: `build/completion-check/` in the function worktree.
