# Federation operating power

Status: the rule, the ME Federation Beam Converter, the power service, the red beam, the topology gauge, Jade, the
guide page and the statistics metric are implemented on `dev` (2026-10-17). Design
decisions: [operating power idea](../ideas/federation-operating-power.md); plan: `.omo/plans/beam-converter.md`.
The converter's textures are temporary (the Pattern Provider's, recoloured) until the artist delivers.

## Behavior

- **Rule.** `power.requireOperatingPower` (on by default) and `power.operatingCostMultiplier` (1.0, 0 to 64) in
  `config/ae2federation-server.toml`. With the rule off no domain needs a converter and nothing is drawn.
- **Costs** (FE/t per block, times the multiplier, `OperatingPowerCosts`): Federation Cable 0.5; Router, Switch,
  Processing Endpoint and Federation Pattern Provider 2; Federation P2P tunnel and Beam Converter 0 (AE2 already
  charges the tunnel).
- **Converter.** `beam_converter` and `creative_beam_converter`: solid-cuboid devices like Router and Switch
  (`FormableDevice`), all members facing one way. The front's M x N must be a cable cross-section and the body
  2·max(M, N) long toward it (`BeamConverterShape`): 1x1x2, 1x2x4, 1x3x6, 2x2x4, 2x3x6, 3x3x6. Other shapes do not
  form (`WRONG_SHAPE`). The front links Federation like a Switch face; FE enters every other face. A formed body is
  one buffer of 16,000 FE per block: FE entering any block fills the whole body, every block's FE capability reads the
  body's total, and each block saves an equal share in its NBT, so a block broken out takes its share. Recipe
  `CEC / ELE / CEC` (Federation Cable, Energy Acceptor, Nexus Core), one per craft.
- **Matching.** A domain's converters must match its largest cable cross-section, read from its cable evidence's
  `capacityFactor` (`CableCrossSection`; a domain without bundles is 1x1). A converter of another size joins the domain
  and supplies nothing.
- **Ledger.** Each tick a domain pools its matching converters' buffers and drains its use in proportion
  (`PowerLedger`). It stops when they cannot pay and keeps drawing. As AE2's `EnergyService`, a new domain runs at once
  if it can pay; a stopped one runs again on its 32nd paid tick (more than 30 ticks after the change). A creative
  converter pays any use and reports the use as its input.
- **Stop.** An unpowered domain stops as one over capacity: `FederationDomainRegistry.setUnpowered`, a second stop
  cause beside `overCapacity`, read through `stopped(id)` by activation (`UNPOWERED`), diagnostics
  (`domain_unpowered`), provider targets and energy sharing.
- **Display.** Its cables draw red at the 0 %-load width. The topology turns dark red, and its corner gauge shows
  "Power", the buffer's fill and a rounded percentage: green while running, yellow while draining (the buffers lost
  more than 1 FE a block over the last 5 s, so whole-FE intake and bursty sources do not flicker it), a full red bar
  once stopped. Nothing of power is drawn while the rule is off. Jade on a converter block reads the body's "Buffer S / C FE ·
  input I FE/t", green when it matches and red when it does not. The statistics page's Federation power card charts
  the use by block group, stopped seconds, the buffer with the input in its tooltip, and each converter body
  ([statistics dashboard](statistics-dashboard.md)); it is greyed out while the rule is off.

## Server

`OperatingPowerService.tick` runs once per server tick after `FederationBindingRefresh.flushAll` and before the
services that link networks. It plans each domain (use, cross-section, converter sites and whether each body matches)
again only when the registry's `evidenceRevision` or the multiplier changes; each tick then reads the converters'
buffers and input, settles the ledger, levels each body's blocks to equal shares and writes the verdict. `OperatingPowerService.reading` exposes the last tick to
the topology session and Jade; `drainSecond` hands the statistics each domain's input and converter bodies averaged
over the ticks since the last second it recorded. Ledgers are in memory, keyed by domain id; energy lives in the
converter blocks, so merges and splits pool themselves.

The GameTest server, the compat runner and the UI test client turn the rule off at start, so their domains need no
converter; the power tests and the topology showcase turn it on themselves.

## Evidence

- Unit tests: `CableCrossSectionTest`, `OperatingPowerCostsTest`, `PowerLedgerTest`, `DomainUnpoweredTest`,
  `PolicyActivationTest`, `RuleHealthTest`, `BeamConverterShapeTest`, `FederationJadeTextTest`,
  `SurvivalRecipeContractTest`, `GuidePagesContractTest`.
- GameTests (manifest): `power.converter-shapes`, `power.converter-links-through-front`,
  `power.converter-body-shares-one-buffer`, `power.runs-a-domain`,
  `power.needs-a-matching-converter`, `power.converters-add-up`, `power.statistics`.
- UI scenario `ui.mapping`: `ui-showcase-topology` (domain running on a 1x1x2 converter, gauge green) and
  `ui-showcase-topology-unpowered` (converter removed, gauge red, canvas dark red),
  `ui-showcase-statistics-fedpower` and `ui-showcase-statistics-power-off` (rule off: no gauge, card greyed out).
- Guide: the ME Federation Beam Converter page, linked from the index and Network Capacity.
