# ME Federation Quantum Cube: implementation notes (2026-10-17)

Feature: `docs/features/quantum-cube.md`; plan `.omo/plans/quantum-cube.md`.

- **AE2 Memory Card order.** In AE2 19.2.17 `MemoryCardItem.doesSneakBypassUse` returns true (checked in the
  bytecode), so a block's `useItemOn` runs before the card's `useOn` even while sneaking. The cube handles both the
  sneak copy and the paste and returns success; AE2's "clear card" on sneak in the air still works. Write AE2's
  `AEComponents.EXPORTED_SETTINGS_SOURCE` beside our own component so the card's tooltip names the copied block, and
  call `MemoryCardItem.clearCard` first so stale AE2 settings do not survive.
- **Partial bundle contacts.** `BundleContacts.contact` used to accept any formed body covering a bundle's end, so a
  bundle half on a cube's border would still be a whole contact and its centre cells would link. `FormableDevice.covers`
  (default true) lets a device veto faces; the cube answers with its face-centre rule.
- **No re-track loop.** The anchor's `settle` adopts the body's pair id through `bind`; `bind` must mark the anchor
  dirty only when a member actually changed, or every tick re-tracks the cube.
- **Server-wide service.** Pairs cross dimensions, so `QuantumLinkService` is keyed by `MinecraftServer`;
  `closeLevel` drops a dimension's cubes and `closeServer` drops the service.
- **Cross-dimension GameTests do exist.** `test/world/OtherDimensionSite` opens a ticketed nether region at the
  test's x/z; `releaseTickets()` lets its chunks really unload (wait on `loaded()`), `forceAgain()` reloads them
  (wait on `ticking()`). `quantum.pair-links-across-dimensions` uses it. On unload the registry already splits the
  domain on its own, so only the Jade status (`PARTNER_NOT_LOADED`) proves the cube left the pair index: a mutation
  that skips `untrack` in `setRemoved` fails on that assertion, not on the link.
- **In-game capture.** For overview shots put the probe player in spectator; in creative it falls after a `tp`, which
  changes the eye height and the view. Jade shots need a real standing eye height (feet + 1.62). Use the real Memory
  Card through XTEST (`tools/visual/game_input.py sneakrightclick`, which holds left Shift for 0.6 s so the server sees
  the sneak). `execute in minecraft:the_nether run forceload add|remove` drives the unload/reload check.
- **In-game check.** `build/quantum-cube-check/`: `compare.png` (before 1/8 on one network, after linked across the
  Overworld and the Nether, 2/8), `sequence.png` (unbound, card copied, bound, linked, unloaded, reloaded, centre vs
  border cable), `textures.png`, `full-manifest.txt` (262/262).
