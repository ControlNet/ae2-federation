# Cable capacity counting (2026-10-13)

User order: counting first, limits later. Decisions (recorded in `docs/ideas/multiblock-routing-and-cable-capacity.md`,
"Confirmed counting rules"): Switch passes a region through, P2P pair counts as cable, no-Router region counts locally,
a network on two regions counts once in each, Pattern Provider's own network is NOT a unit, Endpoints are. Display via
Jade. Contract: `docs/architecture/cable-capacity-counting.md`.

## Implementation notes

- Node kind travels in `FederationDomainNodeEvidence` (new `FederationDomainNodeKind`; the two-argument constructor
  defaults to OTHER, so test and synthetic evidence still compile). Publishers: cable, Router/Switch (by BE type),
  Provider, Endpoint, P2P part.
- `CableCapacityCounter` is pure (8 JUnit cases). `CableCapacity` caches per domain until
  `FederationDomainRegistry.evidenceRevision()` changes; that counter is new because `topologyRevision` does not move
  when links change inside an unchanged node/member set. Cleared in `FederationDomainRegistryAccess.closeServer`.
- Jade 15.10.6 (Modrinth `eYz2YBGT`) is in a `jadeApi` configuration added to the main compile classpath only, not
  `compileOnly`: the testmod/GameTest runtime classpath includes `configurations.compileClasspath`, and a loaded Jade
  failed 12 fake-player GameTests (`Payload jade:server_ping_v1 may not be sent to the client!`); sha512 checked against Modrinth before adding the sha256 to
  `gradle/verification-metadata.xml`. `compat/jade/FederationJadePlugin` is the only Jade-referencing entry point.
- GameTest `capacity.cable-regions` (`cablecapacitycountsregions`) stays within CENTER +-3 and clears its box first:
  in a batch another test's leftover ME chest beside a Switch counted as an extra network. Pitfall: `assertValueEqual(null, ...)` throws an
  NPE, which fails a `succeedWhen` at once instead of retrying; assert presence first.

## In-game capture

`tools/visual/build_hero_scene.py` `function hero_scene:capacity` (origin x+75): Switch(2 chests) - 3 cables - Router -
3 cables - Switch(1 chest) - 3 cables - Router - 3 cables - Endpoint. Expected and seen: 2 local / 4 whole domain /
4 whole domain / 1 local. AE2's ME chest block id is `ae2:chest`. Teleport the player to the scene before running the
function. `tp ... facing` aims from the feet, so the crosshair misses a block below; give yaw/pitch computed from eye
height (+1.62). Jade needs the HUD: no F1; it works in creative and spectator. Jade jar must be in both server and
client mods. Shots: `build/beam-compare/capacity-{before,after}/`, sheet `cable-capacity-before-after.png`, crop
`cable-capacity-jade-lines.png`. With Jade installed the beam scene looks as before (no beam regression).
