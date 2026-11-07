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

## Fixed (2026-11-07)

Each code fix has a test that fails on 7b265a97. In game: `build/review-2026-11-07-check/*-before-after.png`, from the
UI scenario `ui.review-fixes` (`runUiTestClient -PfederationUiSelection=ui.review-fixes`) run on 7b265a97 and on dev.

| Finding | Commit | Test |
| --- | --- | --- |
| Observation snapshot throws past its limits; per-tick rebuilds | 914f963f (bounded canonical prefix + omitted counts + byte budget; data-only rebuilds every 20 ticks) | GameTests `observe.large-domain-bounded`, `observe.traffic-coalesced`; JUnit |
| LDLib2 32767-char strings (choices, overview, graph, flows) | 9268c1ea (`SyncBudget`: unchanged when it fits, else first entries + selected + `omitted`), 3c322384 (graph bounded, members first) | `domain-menu.sync-many-endpoints`, `domain-menu.sync-many-networks`; manual `domainmenusyncthousandnetworks` (1000 networks: choices 14.6k, overview 32.5k, graph 29.2k chars) |
| Rename refused on a one-network domain | 9268c1ea (`matchesNamingContext`) | `domain-menu.rename-one-network` |
| Workspace mapping lands on another Provider | 9268c1ea (refuse, `rejected-no-provider`) | `domain-menu.mapping-provider-gone` |
| Statistics pins/requests without session checks; request flood | 9268c1ea (valid + current; rebuild at most once per 5 reads) | `domain-menu.statistics-need-live-session`, `StatisticsRebuildTest` |
| Lane byproducts never reach the consumer | a8c59044 (match the Lane logic's own action source) | `storelesslanebyproduct` |
| Permanent crafting debt | e6153617 (ended jobs forgotten; unknown-CPU flag cleared; late output only while the consumer still projects the provider; operator `/ae2federation returns clear-debts <consumer>`, no expiry) | `endedjobforgottenwhileowed`, `lateoutputneedscraftingrule`, `clearconsumerdebtscommand` |
| Storage Bus loop counts storage twice | a9bb2d65 (`NativeStorageReentry`: AE2 `mountsInUse` + thread-local entered set) | `storage.bus-loop-counts-once`, `storage.chain-bus-loop-counts-once` |
| Source index keeps dead Grids | 5b0d6c54 (storage), 5e01514f (statistics' own index) | `storage.source-index-forgets-dismantled` |
| Lane binding weak map pinned | 4523ccb8 (IdentityHashMap, explicit unbind) | provider GameTests |
| Fluid statistics x81000 | edcb1264, 27a85777 (unit renamed FLUID_MILLIBUCKET; key keeps components) | `statistics.count-fluid-return-in-millibuckets` |
| "1.3kk", "1000k", English time units, ", " joins | d86d759d, 9268c1ea | `StatisticsFormatTest`, `ProviderReturnsTextTest` |
| Identity settlements / policy tombstones grow | 443f72a5 (settlements removed; tombstones dropped on load) | `NetworkIdentityRegistryTest`, `PolicyStoreTest` |
| Statistics memory | 2cc608f2 (flat TopKeys arrays, lazy Timeline tiers, one tag per range; save format 4) | `StatisticsFootprintTest` (5-key ranking 187 KB -> 32 KB per route) |
| mmcr startup race (upstream) | patch in `build/upstream/mmcr/` for the owner to submit | compiles against mmcr 0.1.3 + KubeJS |

Not changed, by judgement: `FederationStatistics.pollJobs` per tick (polling every N ticks would miss short jobs).
Test-only mechanism: `observelargedomainbounded` builds its 300-member domain with a synthetic hub node.
Also fixed: `domainmenusyncmanyendpoints` placed Endpoints outside its structure (unloaded chunks, flaky).
Open for the owner: cut lists are not shown as "N more" in the screens (paging would be the full answer); a Storage
Bus on another network's unconfigured Interface is still exported as a source.
