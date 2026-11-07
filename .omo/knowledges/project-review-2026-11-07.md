# Project review (2026-11-07)

Second pass after `project-review-2026-11-04.md` (dev 89a66e9d). That review covered recent commits; this one read
whole subsystems: client->server payloads, processing/crafting, storage/ae2/bridge/p2p/energy,
domain/router/identity/policy/persistence/config, observability/compat/mixin, client GUI. Six read-only agents, each
finding then checked against the code by hand where marked. Findings only; nothing fixed yet.

## Large domains (the 1000-subnet target)
- **Observation snapshot limits throw.** `ObservationLimits` caps members 256, policies 1024, providers/endpoints 256,
  locks/tasks 512; `FederationDomainStateSnapshot` throws `IllegalArgumentException` above them (checked). Built on
  screen open (`FederationDomainPolicyObservation.open`, no catch, checked) and every tick in
  `LevelObservabilityService.sweep()` while a domain screen is open. Domain size has no limit since 3bd89462, and
  `perfbusesthousandsubnetslargedomain` builds a 1000-network domain. Not reproduced in game.
- **LDLib2 string bindings cap at 32767 chars.** `DataBindingBuilder.stringS2C` uses `ByteBufCodecs.STRING_UTF8`
  (checked in LDLib2 sources). `workspaceChoices()` (~1.2k chars per Endpoint, agent estimate) and
  `networkOverviewText()` are JSON strings with no size bound; writing one over the cap throws on the server thread.
  `PerformanceBenchmarkGameTests` calls `workspaceChoices()` directly, so the codec never runs in tests. Thresholds
  are estimates, not measured.
- Every tick with traffic, an open domain screen rebuilds a full snapshot (server-wide policy/provider/endpoint scan)
  and sends a delta into `ObservationClientStates`, which no screen reads.
- Statistics memory: `TopKeys` per route (444 points x up to 31 keys) and per-key `Timeline`s (~9 KB each, kept 24 h)
  grow with routes and item turnover; estimated hundreds of MB and heavy autosaves at 1000 subnets after a day.
  Estimate only.

## Gameplay correctness
- Renaming a network fails on a one-network domain: `RENAME_NETWORK` goes through `matchesContext`, which requires
  `selection != null`, and the session sets `selection = null` below 2 members (checked). Server answers `WRONG_MENU`.
- Lane byproducts never reach the consumer: `CraftingProjectionService.pusher` looks up `ICraftingProvider` on the
  action source's node, but a Lane's `CapturedManagedGridNode` never forwards `ICraftingProvider` to the physical node
  (checked). Unlisted byproducts of a Federation Pattern Provider Lane in a storeless, CPU-less provider network stay
  in the 9-slot return inventory and back the machine up.
- Undeliverable crafting debt is permanent (no expiry or clear command): later arrivals of that key in the provider
  move to the consumer outside any storage rule; the consumer's job records never get forgotten; an `unknownJobs`
  flag (add-on CPU without lifecycle adapter) keeps `jobsEnded` false for good. Agent-traced, not reproduced.
- Storage Bus on another network's ME Interface plus a storage rule the other way counts the provider's stock twice
  (projection reaches the bus-side native handles through a separate aggregate `NetworkStorage` guard). Display and
  planning only; inserts/extracts stay real. Agent-traced, not reproduced.
- Workspace mapping: if the edited Provider vanishes, `selectedProvider()` falls back to another Provider by index and
  a wire sent in the next 5 ticks lands on that one's slot. Provider screen is guarded by `editsOpenedProvider()`.

## Smaller
- `ObservedFluidReturnHandler` multiplies mB by 81 000 (Fabric droplets); NeoForge AE2 uses 1000 per bucket (checked).
  Statistics only.
- `pinStatistics`/`statistics()` skip `isStillValid`/`contextCurrent()`; a client may alternate statistics requests to
  force a rebuild every tick.
- Display: CPU storage "1.3kk" (`FederationStatisticsView.bytes`), "1000k" just under unit boundaries, English unit
  letters h/m/s and ", " joins in zh_cn.
- Unbounded save growth: `NetworkIdentityRegistry.settlements` (written, never read), policy tombstones.
- `NativeSourceDomainRegistry` keeps entries (dead Grids) of networks that unloaded until server stop.
- `FederationPatternProviderTargetCache.BINDINGS` weak keys are pinned by their values (latent; `close()` unbinds).

## Outside code
- Compat pins drifted since 8ad51f6b (2026-10-26): ATM10 8.3 -> 8.5, CUS2 12.9.0 -> 13.1.0 (the weekly newest job
  passed on 2026-11-01), and Modrinth/GitHub addons (neoecoae 21.2.15, neoecoprototype 1.4.6, ae2-lightning-tech
  2.1.3, extendedae-plus 1.6.4.1, guideme 21.1.22, ae2-pattern-disk 0.9.11, useless-mod 2.4.6.5, ...). CurseForge-only
  addons not checked: the site search API answers 403.
- Playthrough flake 2026-11-01 (feature branch, rerun green): NAST's `mmcr` 0.1.3 threw
  `ConcurrentModificationException` in `ModItems.<clinit>` during NeoForge's parallel mod construction. Upstream race,
  no Federation frames.

## Checked clean
Payload decoding and dispatch (caps, nonce/sequence, containerId, same-thread), menu lifetime and subscription
cleanup, SavedData dirty marking and codec symmetry, domain registry recompute and capacity counting, multiblock
forming, AuthorityEpoch inputs, energy pools, P2P teardown, compat class loading, mixin styles, lang parity
(653 keys), CI history.
