# Project review (2026-11-15)

Third pass, on dev ec5054cd, read by hand (no review agents; owner 2026-11-13: fan-out costs too many tokens). Scope:
what changed since `project-review-2026-11-07.md` (2x2x3 converter 10c50ee5, addon public API refactor 8bd777a9 /
30a455ed / d1c88c89, ExpandedAE metadata ec5054cd), plus static state and lifecycle, Quantum Cube, Beam link service,
operating power and the converter FE buffer, Endpoint return handlers and the Provider FE relay, the CPU receipt mixin,
and every GuideME mixin target compared across GuideME 21.1.1 (what the dev clients run), 21.1.19, 21.1.20 and 21.1.22
(what the compat profiles pin). Findings only; nothing fixed yet.

## GuideME 21.1.22 (client)

- **Opening the guide throws on GuideME 21.1.22 without Data Energistics.** `GuideNavBar$Row.hasChildren` is `final`
  from 21.1.22 (plain `boolean` in 21.1.1/21.1.19/21.1.20, checked with javap). `GuideNavBarRowMixin` shadows it without
  `@Final` and writes it in `ae2federation$nest`, which `GuideNavBarMixin.recreateRows` calls for every row. Mixin only
  logs a verbose warning for that shadow and leaves the field final, so the merged method is a `putfield` on a final
  field outside `<init>`. A minimal ASM class with the same shape on JDK 21 throws
  `IllegalAccessError: Update to non-static final field Row.hasChildren attempted from a different method (nest) than
  the initializer method <init>`. Reproduced in game: `runGuideClient` with a Gradle init script forcing GuideME
  21.1.22 on the run classpaths (no repository file changed) crashes on the guide's first frame with that error from
  `GuideNavBar$Row.ae2federation$nest` (crash report and init script in `build/guideme-21.1.22-check/`). Nothing
  caught it before: the dev clients load GuideME 21.1.1 (`compileOnly guideme:21.1.1`, runtime from AE2), and CI's
  compat runs are servers, so no check loads our client mixins on 21.1.22.
- **GuideME 21.1.22 nests the sidebar itself.** Its `Row` has `parent`, `depth`, `levelsBelow`, and `recreateRows`
  builds rows through a recursive `addRow(node, parent)`. 21.1.20 has neither `depth`/`levelsBelow` nor `addRow`;
  21.1.21 was not in the caches, so the first native version is 21.1.21 or 21.1.22. Making the write legal (`@Mutable`) would still nest and
  indent twice. The nesting should stand down when GuideME nests natively, as it already does for Data Energistics
  (`DataEnergisticsGuide.nestsSidebar()`).
- **Guide scenes lose the Beam Former portal from GuideME 21.1.20.** `GuidebookLevelRendererMixin` injects into
  `lambda$renderContent$0` with `require = 0`; 21.1.20 and 21.1.22 split `renderContent` into an overload with a
  `SceneMeshCache` and name the lambdas `$1`/`$2`, so the inject silently does nothing and the portal is drawn under
  the re-queued solid quads (the case its own comment describes). The `@WrapMethod(method = "renderContent")` still
  applies: a name-only selector is `Quantifier.SINGLE` (Mixin `TargetSelectors.findRootTargets` takes the first
  match in class order), and the public two-argument overload comes first in 21.1.20 and 21.1.22. It holds by class
  order only; a descriptor would pin it.

## Smaller

- `FederationP2PTunnelPart.PENDING` (WeakHashMap keyed by server) is drained by a `TickTask` one tick later and is
  not cleared in `onServerStopped`. A P2P change in the last tick before a stop leaves an entry whose values (parts ->
  block entities -> level -> server) pin the key, so single-player keeps the closed world in memory.
- Quantum Cube pair ids live in each block's NBT. A creative pick-block with data (or `/clone`) makes a third cube of
  the same id, and the real pair goes to `MORE_THAN_TWO` and unlinks. Rebinding a cube whose old partner is unloaded
  leaves that partner with the old id; once loaded it reports "partner not loaded" for a pair that no longer exists.

## Not read this pass

Storage projections, domain/identity/policy and their payloads, client GUI other than the guide, observability and
statistics: the 2026-11-04 and 2026-11-07 passes covered them and nothing there changed since.

## Checked clean

- 2x2x3 converter: `CableCrossSection.converterLength` is the only length source (no `2 * wide` left);
  `BeamConverterShape` axis is unique (test covers boxes up to 8); `RefinedDeviceBody.find` walks at most 6 steps,
  enough for 3x3x6; 3x3x6 corners keep the mesh; the carrier starts at half the body length.
- Addon refactor: gate classes name addon classes only as strings; every `*Direct` call is reached after a class-name
  or mod check, and `LinkageError` is caught both where the adapter is built and where it is called
  (`CraftingCpuLifecycle`, `NeoEcoBatching.invoke`, `NeoEcoBatchingDirect.handOver`,
  `FederationPatternProviderTargetCache.find`). The two private fields stay reflective.
- Static state: per-server and per-level maps are cleared on `LevelEvent.Unload` / `ServerStoppedEvent`; the client
  observation state resets on logout. `CraftingReturnLedger.OWING` only ever over-counts (documented: costs a lookup).
- Operating power: drain is proportional, a failed tick empties the buffers as AE2 does, body shares are equalised
  after the draw; converter drops carry no FE; FE only on the rear exterior face of a formed body; capabilities are
  invalidated on every state change.
- Beam link service: scans bounded per beam and per tick, unloaded positions keep their last result, debounce both
  ways. Quantum link service: both ends publish in one pass; dimension unload unlinks partners.
- Endpoint return handlers and the Provider FE relay: no amount truncation; the relay skips Federation blocks and other
  relays, so it cannot loop.
- `CraftingServiceReceiptMixin`: observe-only, failures logged, never thrown into AE2.
