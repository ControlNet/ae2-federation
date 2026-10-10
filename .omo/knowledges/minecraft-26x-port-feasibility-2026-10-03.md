# Minecraft 26.x port feasibility

Research date: 2026-10-03. Read-only source and upstream inspection; no target port, compilation, or runtime validation was performed. Estimates below are engineering judgments, not measured schedules.

## Current project

- Minecraft 1.21.1, NeoForge 21.1.250, AE2 19.2.17, LDLib2 2.2.34, Java 21, Gradle 9.2.1, ModDevGradle 2.0.146.
- `settings.gradle` includes only `neoforge-1.21.1`; root `verifyTaskOneStructure` explicitly checks that this is the only target. The target build attaches common sources directly. Metadata pins Minecraft and NeoForge to exact current versions.
- DESIGN.md already calls for common implementation plus thin version directories and verified compatibility boundaries, not one universal JAR.
- Source inventory: 351 common main Java files and 11 target main Java files. In common, 101 files import AE2 public APIs, 37 import AE2 non-API packages, 106 import Minecraft, 23 import NeoForge, and 17 import LDLib2. Categories overlap. 158 common files directly import at least one of these four dependencies. Absence of direct imports does not establish transitive independence or an unchanged-reuse percentage.
- 93 common test Java files and 270 common testmod Java files provide substantial regression material; file counts are not test counts or coverage measurements.
- Production mixin configuration lists seven common and three client mixins, with required injections. Sensitive targets include PatternProviderLogic, StorageService, its private ProviderState, CableBusContainer, LDLib text, GuideME tooltip, and MouseHandler.

## Upstream availability

- AE2 releases inspected via GitHub API: v26.1.13-beta (2026-09-30, Minecraft 26.1.2), v26.2.1-alpha (2026-10-01, Minecraft 26.2). A `port/26.3` branch exists; no 26.3 release was observed in the recent release listing. Branch existence is not a supported release.
- LDLib2 publishes 26.1.x and 26.2 artifacts, including 26.1.2.41 and 26.2.2.41.a on its author-maintained CurseForge files page. Branches 26.1, 26.2, and 26.3 exist. The 26.3 branch gradle.properties targets Minecraft 26.3 / NeoForge 26.3.0.16-beta with library version 26.3.2.41; this alone does not verify publication or compatibility with Federation.
- AE2 target gradle.properties confirms Java 25 and GuideME 26.1.14-beta / 26.2.1-alpha respectively.

## Concrete risks and favorable evidence

- Examined AE2 source at both v26.1.13-beta and v26.2.1-alpha: PatternProviderLogic still has pushPattern and findAdapter; StorageService still has postWatcherUpdate, onServerEndTick, and ProviderState; ICraftingMachine still has of(Level, BlockPos, Direction); MEStorage still has insert/extract with AEKey, amount, Actionable, and IActionSource. This supports preserving the integration design, not assuming binary compatibility or validated mixin injection counts.
- NeoForge's 1.21.9 transfer rework affects IItemHandler, IFluidHandler, IEnergyStorage, and capabilities. Federation's ProcessingRegistration registers old ItemHandler/FluidHandler capabilities; endpoint integration must be adapted and tested for resource conservation and rollback behavior.
- Current CableBakedModel uses BakedModelWrapper; WorldHighlight and FederationScenePreview directly manipulate RenderSystem. GuiGraphics custom textures and UI previews require adaptation to newer extraction/render submission APIs. LDLib2 has version-specific UI API changes, including overflow/clip.
- FederationScreenSwitch directly invokes GLFW for cursor restoration. The 26.3 primer documents SDL replacing GLFW and changed input handling, alongside Renderpearl and further rendering changes.
- SavedData, CompoundTag-based persistence, block entity save/load, AE2 resource serialization, network codecs, item models, recipes, and GuideME scene resources need target-specific review. Supporting new worlds and upgrading old worlds are separate validation scopes.
- Java 25 is required for 26.1. The existing Gradle 9.2.1 already exceeds the documented 9.1 minimum. AE2's actual 26.2 port uses ModDevGradle 2.0.148; do not assume the project's 2.0.146 is sufficient for every target.

## Assessment and approach

- First 1.21.1 -> 26.1.2 port: medium-high effort; preserve federation algorithms and behavior, adapt platform integrations and client rendering.
- First 1.21.1 -> 26.2 port: medium-high to high, including additional rendering changes; dependency releases exist but AE2's inspected release is alpha.
- After a working 26.1 port, 26.2 is an incremental medium-effort target, not a trivial version bump.
- 26.3: elevated uncertainty while AE2 is on a port branch; known client/input changes already justify a separate adaptation budget.
- A rough planning envelope for one developer familiar with this code is several weeks for the first reliable new target (approximately 3-6 person-weeks under ready-dependency assumptions); broad third-party compatibility, old-world migration, and upstream blockers can extend this. No compile spike was done, so no precise commitment is justified.
- Preserve the design's shared common logic and thin target-specific implementation. Do not duplicate the entire project or introduce a broad abstraction layer preemptively. Let actual compile and behavior differences determine source splits. Build separate artifacts and run separate regression jobs for verified compatibility ranges.
- Prefer one initial target, plausibly 26.1.2 given current AE2 maturity, while keeping 1.21.1 supported. Choosing 26.2 directly is possible if its alpha dependency maturity is acceptable. There is no requirement to release every intervening Minecraft version.
- Validate storage deduplication and subscriptions, domain split/merge and policy restoration, remote processing and returns, native crafting completion/cancellation/restart, energy accounting, dedicated-server startup, client UI/preview/cursor behavior, recipes and guide pages. Only then expand the external-mod compatibility matrix using versions actually available for that target.

## Sources

- https://github.com/AppliedEnergistics/Applied-Energistics-2/releases
- https://github.com/AppliedEnergistics/Applied-Energistics-2/pull/9012
- https://github.com/AppliedEnergistics/Applied-Energistics-2/tree/port/26.3
- https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/v26.1.13-beta/src/main/java/appeng/helpers/patternprovider/PatternProviderLogic.java
- https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/v26.2.1-alpha/src/main/java/appeng/me/service/StorageService.java
- https://github.com/Low-Drag-MC/LDLib2/blob/26.3/gradle.properties
- https://www.curseforge.com/minecraft/mc-mods/ldlib/files/all
- https://github.com/Low-Drag-MC/LowDragMC-Doc/blob/v2/docs/en/ldlib2/ui/components/element.md
- https://neoforged.net/news/26.1release/
- https://neoforged.net/news/21.9release/
- https://docs.neoforged.net/primer/docs/26.1/
- https://docs.neoforged.net/primer/docs/26.2/
- https://docs.neoforged.net/primer/docs/26.3/
