# ME Federation Beam Converter: implementation plan

Date: 2026-10-17. Status: draft for owner review; no code written yet.
Design source: [federation-operating-power.md](../../docs/archive/ideas/federation-operating-power.md) (decisions of 2026-10-17)
and design canvas row "联邦供能". Beam Transceiver and wireless cube costs are out of scope: those blocks do not
exist yet, and each adds its own cost row when it is built.

## Decisions this plan still needs

| # | Question | Proposal |
|---|---|---|
| D1 | A converter of the wrong size in a domain that also has a correct one | It supplies nothing; the correct one keeps the domain running. The domain stops only when the correct ones cannot pay. |
| D2 | Creative converter (owner, 2026-10-17; removed 2026-10-31) | Was: same sizes and rules as the ordinary converter, unlimited supply, creative purple. Removed by the owner: most FE mods ship a creative energy source that feeds an ordinary converter. |
| D3 | Which faces take FE | Every face of every converter block except the front. |
| D4 | Survival recipe (one block per craft, as Router and Switch) | `CEC / ELE / CEC`: C Federation Cable, E `ae2:energy_acceptor`, L Nexus Core. |
| D5 | Textures before the artist delivers (owner, 2026-10-17) | Temporary: the Pattern Provider's textures recoloured, since they show a front; purple for the creative one. Listed as temporary in the commit and the art hand-off note. |
| D6 | A domain seen for the first time (server start, split) | Starts powered if its pooled buffer can pay this tick, as AE2's `EnergyService` starts `hasPower = true`; only a domain that stopped waits, and runs again once more than 30 ticks have passed with power, on its 32nd paid tick (AE2: the change tick counts 0, then `ticksSinceHasPowerChange > 30`). |
| D7 | P2P tunnel nodes | Cost 0: AE2 already charges the tunnel itself. |
| D8 | How an unpowered domain's beams look (owner, 2026-10-17) | Red, at the thinnest width, the width a beam has at 0 % load. |
| D9 | When the requirement is configured off (owner, 2026-10-17) | The statistics card still shows but is disabled and cannot be opened; the topology draws nothing of Federation power. |
| D10 | Statistics key for domain-wide series (owner, 2026-10-17) | The domain id, made persistent (phase 2b): series survive a restart under the same id; per-converter series are keyed by position. |

## Architecture

- **Power ledger, per domain, in memory.** A new `OperatingPowerService` runs in `onServerTickBindings`
  (`NeoForgeEntrypoint` L80-85) before `StorageMountService.tick`, and closes in the server-stop hook (L117-125).
  Each tick, per domain: use = cost of its nodes (cached per `evidenceRevision`, counted by `FederationDomainNodeKind`),
  input = FE the matching converters took in this tick, buffer = sum of those converters' stored FE.
- **Energy lives in the converter blocks.** Each block entity stores up to 16 000 FE in NBT and drains in proportion.
  Domain ids are rebuilt on every start, so nothing is keyed by domain id on disk; merges and splits pool themselves.
  The 30-tick restart counter is kept in memory per domain id.
- **Second stop cause beside capacity.** `FederationDomainRegistry` gets an `unpowered` set next to `overCapacity`,
  set from the service with the same `topologyRevision++` and `AuthorityEpoch.advance()`, cleared in
  `removeFederationDomain`. `shareRunningFederationDomain` skips both; new `stopped(id)` and `unpowered(id)`.
  `PolicyActivationState.UNPOWERED`, `BindingDiagnostic.Reason.DOMAIN_UNPOWERED` (lang `domain_unpowered`, in
  `RuleHealth.BLOCKING_BACKENDS`), `ProviderTargetState.FEDERATION_DOMAIN_UNPOWERED`, and `EnergySharingService.stopped()`
  read `stopped(id)`. `PolicyActivation` must tell the two causes apart instead of mapping every "not running" to
  `OVER_CAPACITY`.
- **The block.** A solid-cuboid device like Router and Switch, reusing `DeviceStructure.evaluate` with an exact-shape
  check (all blocks share `FACING`, long axis along it, length 2·max(M, N)) and a new `Reason` for a wrong shape.
  `DeviceStructureUpdater` stops testing `instanceof RouterBlock` and tests a small `FormableDevice` interface.
  Body blocks publish evidence of a new node kind `CONVERTER`; only front-face blocks expose a Federation port, as
  `FederationPatternProviderBlockEntity.federationPort` does. `CableCapacityCounter` ignores the kind.
- **Size match.** The domain's largest cross-section comes from its cable evidence `capacityFactor`
  (1 → 1x1, 3 → 1x2, 4 → 1x3, 6 → 2x2, 9 → 2x3, 0 → 3x3); a converter matches when its front M x N is that section.
  This relies on the factors staying distinct; `CrossSectionTest` pins the table so a new bundle factor fails loudly.
- **Config.** `FederationServerConfig` gets `push("power")` with `requireOperatingPower` (true) and
  `operatingCostMultiplier` (1.0, range 0 to 64), applied into a static `OperatingPowerRule` like `CableCapacityLimit`.
- **Tests stay green.** The testmod turns the requirement off at start, so the existing GameTests keep their domains;
  power tests turn it on, as the capacity tests toggle the limit. The compat profiles set it off, except one run that keeps
  the default and places a converter fed by an addon's FE cable (Mekanism), to check real energy pushers.

## Phases

Each phase is a commit on `dev`, pushed once its checks pass. TDD: tests are written first in each phase.

### 1. Rule and ledger (pure logic)

- `OperatingPowerRule`, `OperatingPowerCosts` (FE/t per kind × multiplier), `CrossSection` (order and
  `capacityFactor` mapping), `PowerLedger` (pool, proportional drain, input above use, 30-tick restart, D6).
- Config block and its docs in `docs/architecture/cable-capacity-counting.md` sibling page.
- Tests: `OperatingPowerCostsTest`, `CrossSectionTest`, `PowerLedgerTest` (drain to zero stops on that tick; a stopped
  domain keeps drawing; power returns on the 32nd paid tick; fresh domain with stock starts powered).

### 2. Second stop cause

- Registry set, activation state, diagnostic reason, provider state, energy-sharing gate, lang keys, session JSON
  (`openedStopped`/`stoppedDomains` replacing the capacity-only names, read by `FederationTopologyView`).
- Tests: extend `PolicyActivationTest`, `BindingDiagnosticTest`, `RuleHealthTest`; a registry unit test that marks a
  domain unpowered and checks `shareRunningFederationDomain`. The JSON rename touches `FederationTopologyView`
  parsing (L373-381) and `TaskThirtyThreeMappingScenario` (L468-525); `capacity.*` scenarios must stay green.

### 2b. Persistent domain ids (separate worktree, in parallel)

- A `SavedData` on the overworld keeps the id sequence and, per domain, anchor node ids (non-cable nodes; a cable-only
  domain keeps a few of its smallest cable nodes). After a start, the saved records join the old snapshots that
  `FederationDomainRegistry.inherit` matches against, so a domain gets its id back under the existing rules. Anchors go
  only when their block is removed, not when a chunk unloads.
- Tests: unit tests for the matching; a `gametest-restart` scenario asserting the same id before and after a restart.
- Merged into `dev` before phase 5, which keys statistics by domain id.

### 3. The block

- `beam_converter` and `creative_beam_converter`: registration, block entity, `FACING`, FE capability (D3), NBT buffer,
  `CONVERTER` evidence, front port, `FormableDevice`, formed model (`DeviceStructureGeometry.Device.CONVERTER`), cable
  visuals (`CableVisualConnections.kind`, `BundleContacts`, `BundleLook`, `BundleSelectionClient`), Provider relay
  exclusion (`ProcessingRegistration.federationBlock`), creative tab, blockstates, models, temporary textures (D5),
  loot, recipe (D4), advancement, mineable tag, lang en_us and zh_cn.
- Contract tests updated: `SurvivalRecipeContractTest`, `GuidePagesContractTest`, `LanguageContractTest`,
  `DeviceOcclusionContractTest`, `device-structure-parity.json`.
- GameTests (`BeamConverterGameTests`): each of the six sizes forms; a 2x2x3 and a mixed-facing body do not; cable joins
  the front face only.

### 4. Power service

- `OperatingPowerService` wired into the server tick, reading the registry and the converters, writing the `unpowered`
  set; testmod default off.
- GameTests (each with a manifest entry): no converter stops the domain (sharing off, provider paused); a matching
  converter with FE keeps it running; input below use drains then stops; restart on the 32nd paid tick; a larger bundle
  added makes the old converter stop matching (D1); two converters add up; config off is unlimited; creative works;
  stored FE survives a save (`gametest-restart` backend).

### 5. Client

- Beam (D8): a synced "domain stopped" flag on `FederationCableBlockEntity` (`syncCapacity` early-return, update
  tag); `CableBeamStyle` then draws red at the 0 %-load width.
- Topology (not drawn when the requirement is off, D9): corner gauge after the legend in `domain.xml` / `domain.lss` ("Power", bar, %, green / yellow / red; hidden
  with the legend in compact mode); canvas tint already follows `openedStopped`.
- Statistics (keys per D10): gauges (`fedpower/<key>/use/<kind>`, `input`, `stored`, `stopped`), a top-level
  `domain` compound in `StatisticsPayload`, the seventh `Metric`, its card, stacked chart with the input line,
  stored % chart, and the converter and block-type lists; network filter greyed for this metric; card disabled when the requirement is off (D9).
- Tests: `StatisticsPayloadTest`, `StatisticsBookTest`, `CableBeamTest`; `StatisticsGameTests` for the new gauges.
- Render the screens and compare against the design boards before calling it done.

### 6. Jade, guide and docs

- Jade: converter shows stored / capacity, input FE/t, and whether it matches (one line, colour only).
- Guide: `beam_converter.md` and `_zh_cn`, links from the cable-capacity and statistics pages, item index; the config
  key in `docs/features/`; `docs/ideas/README.md` status; knowledge note in `.omo/knowledges/`.

## Verification

```bash
./gradlew --no-daemon --dependency-verification=strict :neoforge-1.21.1:test
python3 tools/dev_gametests.py --manifest --match power
python3 tools/dev_gametests.py --manifest --match capacity
python3 -B tools/required_gametests.py
```

Expected: unit tests pass; every `power.*` and `capacity.*` scenario passes with its assertion count; the required gate
reports no failure. Then the in-game before/after comparison image: one domain with a converter, its gauge and
statistics card, and the same domain stopped (red canvas, red beams, gauge 0 %).
