# Cable capacity limit (2026-10-13)

Owner decisions: any cable over the limit stops the **whole** Federation domain; C = 4 by default, configurable;
enforced by default; the topology shows a stopped domain on a dark red background. Bundled cables come later.

## Where it lives

- `domain/CableCapacityLimit` — static, volatile `enforced`/`capacity`; `current()` is empty when not enforced.
- `config/FederationServerConfig` — NeoForge SERVER config `serverconfig/ae2federation-server.toml`,
  `capacity.enforceCableCapacity` (true) and `capacity.cableCapacity` (4, 1–1024). Loading/Reloading call
  `CableCapacityLimit.set`; Unloading resets to the defaults so a later world does not inherit them.
- `FederationDomainRegistry` stores the verdict per domain (`overCapacity`, `shareRunningFederationDomain`).
  It never withdraws evidence: a stopped domain keeps its shape, so the count does not oscillate.
- The verdict is judged lazily in `flush()`, only for domains whose nodes changed or were installed, or for all of them
  when the limit changed (`judgedLimit`). Only a changed verdict bumps `topologyRevision` and `AuthorityEpoch`, which is
  how storage, crafting and energy notice without polling.

## Gates

- Rules: `PolicyActivation` → `OVER_CAPACITY` → `BindingDiagnostic.Reason.DOMAIN_OVER_CAPACITY`
  (blocking, red in `RuleHealth`).
- Processing lanes: `ProviderTargetAuthorization` → `FEDERATION_DOMAIN_OVER_CAPACITY`.
- Energy: `EnergySharingService.endpointEdges` skips Endpoints of stopped domains.

## Display

- Session JSON: `openedOverCapacity`, `overCapacityDomains`.
- Opened domain stopped and not clustered: canvas `FederationTheme.CANVAS_STOPPED`; caption gets red
  "over capacity, stopped" with the backend reason as tooltip. The header dot is the sync lamp and was left alone.
- Related view: stopped plates use `PLATE_STOPPED` and an ERROR-coloured name with the same suffix.
- Jade: "Capacity count: N/C", N red once over.

## Gotchas

- C = 4 is tight: 2 Pattern Providers + 3 Endpoints behind one Switch segment is already 5. The production Provider
  GameTest scene therefore uses a Router as its hub (a Router bounds regions; a Switch passes them through).
- GameTests that lower the limit must restore it; a test that fails mid-way leaves the limit changed for later tests
  in the same batch.
- The in-game check: hero scene + four `ae2:chest` around Switch B's right segment = 5 networks on one cable.
