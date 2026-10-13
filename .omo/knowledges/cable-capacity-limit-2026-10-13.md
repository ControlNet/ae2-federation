# Cable capacity limit (2026-10-13)

Owner decisions: any cable over the limit stops the **whole** Federation domain; C = 8 by default (first 4, raised the same day), configurable;
enforced by default; the topology shows a stopped domain on a dark red background. Bundled cables come later.

## Where it lives

- `domain/CableCapacityLimit` — static, volatile `enforced`/`capacity`; `current()` is empty when not enforced.
- `config/FederationServerConfig` — NeoForge SERVER config; in NeoForge 21.1 the file is
  `config/ae2federation-server.toml` (a world's `serverconfig/` only overrides it). It reloads live when edited;
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
- Cable light: the M beam's load is N/C (`CableBeamStyle.loadOf`). The server recounts a cable when the registry's
  `evidenceRevision` or the limit changed, throttled to 10 ticks per cable, and sends it in the block entity update
  packet (not saved). Red alarm only while enforced; not enforced clamps to 1; no count yet = 0 (idle carrier).
  The drawn load eases with `smooth`; the alarm follows the synced count. Per cable: in a stopped domain only the
  over-limit cables are red.

## Gotchas

- C = 4 proved tight (2 Pattern Providers + 3 Endpoints behind one Switch segment is already 5), so the owner raised the
  default to 8. GameTests that need a stop set their own limit (4 or 1) and restore the default.
- GameTests that lower the limit must restore it; a test that fails mid-way leaves the limit changed for later tests
  in the same batch.
- The in-game check: hero scene + four `ae2:chest` around Switch B's right segment = 5 networks on one cable;
  set `cableCapacity = 4` in the world's server config (it reloads live) to see it stop.
