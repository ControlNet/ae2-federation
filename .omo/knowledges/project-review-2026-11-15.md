# Project review (2026-11-15)

Third pass, on dev ec5054cd, read by hand (no review agents; owner 2026-11-13: fan-out costs too many tokens). Scope:
what changed since `project-review-2026-11-07.md` (2x2x3 converter 10c50ee5, addon public API refactor 8bd777a9 /
30a455ed / d1c88c89, ExpandedAE metadata ec5054cd), plus static state and lifecycle, Quantum Cube, Beam link service,
operating power and the converter FE buffer, Endpoint return handlers and the Provider FE relay, the CPU receipt mixin,
and every GuideME mixin target compared across GuideME 21.1.1 (what the dev clients run), 21.1.19, 21.1.20 and 21.1.22
(what the compat profiles pin). Findings first; fixes are listed under Fixed.

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

## Fixed

| Finding | Fix |
| --- | --- |
| Guide crash on GuideME 21.1.22 (`hasChildren` final) | `GuideNavBarMixin` stands down when `GuideNavBar$Row` declares `levelsBelow` (GuideME nests natively); the shadow is `@Final @Mutable` as a backstop; both guide `index.md` set `navigation.max_depth: 2`, which 21.1.1 ignores. In game on 21.1.22 the sidebar shows three levels with GuideME's own indent; 21.1.1 is unchanged (`build/guideme-21.1.22-check/before-after.png`). |
| Portal hooks miss GuideME 21.1.20+ | `@WrapMethod` pinned to the two-argument `renderContent`; the TAIL inject covers `lambda$renderContent$0` (21.1.1-21.1.19) and `$1` (21.1.20+ export); the screen pass `lambda$renderContent$2` gets HEAD (`BeamPortalRenderer.beginScene`) and TAIL (draw, `endScene`) hooks. Checked with `-Dmixin.debug.export=true`: every hook lands on both 21.1.1 and 21.1.22. No shipped guide scene has a 2x2 or 3x3 former (the only portal sizes), so a throwaway scene with a linked 2x2 pair (not committed) showed it: no portal on 21.1.20 before, a portal on 21.1.1, 21.1.20 and 21.1.22 after (`portal-before-after-21.1.20.png`, `portal-after.png`). The portal draws after the translucent pass on every version, as it did on 21.1.1. |
| `FederationP2PTunnelPart.PENDING` kept on server stop | `FederationP2PTunnelPart.closeServer`, called from `onServerStopped` (6edb1251). |
| Quantum Cube copies and stale partners | `QuantumPairIndex.Cube` carries `boundTo`; with more than two cubes the mutually bound two still link; `stale()` marks a cube whose bound place holds another pair id, or a third cube beside a bound pair; `QuantumLinkService.tick` clears its pair id (eef11d4c); a rebound partner is confirmed with the live block first, since a cube in a loaded but non-ticking chunk reports a fresh bind late (GameTest `quantumcubebindsurvivesalatereport`). GameTest `quantumcubecopyleavesthepairlinked` copies a cube's block data as `/clone` does. Left as is: while the original is unloaded, a copy and the partner are exactly two and link; once the original loads, the copy is cleared. |
| Dev runtime on GuideME 21.1.1 | `runtimeOnly guideme:21.1.22` (c4a061b6); compile stays on 21.1.1, the oldest AE2 19.2.x accepts. `tools/guideme-version.init.gradle` with `-PguideMeVersion=21.1.1` runs our own sidebar nesting path, which no dev client or CI run loads any more. Jar/POM sha256 entries checked against Maven Central's sha1 files. |

GuideME 21.1.15, 21.1.19 and 21.1.20 (the versions compat packs pin) checked with javap: the navigation frontmatter parser reads known keys only, so `max_depth` is ignored; `Row(GuideNavBar, NavigationNode, Row)` and a non-final `hasChildren` match 21.1.1; 21.1.15/21.1.19 name the lambda `$0`.

How to run the guide client on another GuideME: `./gradlew -I tools/guideme-version.init.gradle -PguideMeVersion=<v>
:neoforge-1.21.1:runGuideClient` (add `--dependency-verification=lenient` for a version without verification entries).
The run directory is `neoforge-1.21.1/run-guide`.

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
