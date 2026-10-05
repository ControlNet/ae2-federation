# Minecraft 1.20.1 backport feasibility

Research date: 2026-10-04. Source inspection only; no port compilation or game validation. The user's prerequisite is an already released AE2 target; waiting for AE2 is excluded from the estimate.

## Evidence

- AE2 officially publishes 15.4.11 for Minecraft 1.20.1 Forge and Fabric. The inspected baseline is tag `forge/v15.4.11`; this is a candidate platform choice, not authorization to change the project's NeoForge-only design.
- At that tag, MEStorage insert/extract retain AEKey, amount, Actionable and IActionSource parameters. StorageService retains postWatcherUpdate, onServerEndTick and private ProviderState. PatternProviderLogic retains pushPattern and findAdapter. IEnergyOverlayGridConnection also exists.
- ICraftingMachine.of(Level, BlockPos, Direction, ...) has an additional parameter relative to our current three-argument injection descriptor and queries blockEntity.getCapability(...). The current redirect must be adapted, even though the method concept survives.
- AE2's build uses JavaLanguageVersion.of(17). Its gradle.properties declares GuideME 20.1.7, so documentation support must not be dismissed as absent. Existing Federation guide features and structures still require compatibility validation.
- Current Federation uses Java 21, List.getFirst in production code, registry-aware save/load signatures, and StreamCodec / CustomPacketPayload network code. Supporting the ordinary Java 17 target requires checking and adapting Java 21 usage; a newer build JVM alone does not make Java 21 APIs available on Java 17.
- Forge 1.20.1 capabilities use getCapability / LazyOptional and explicit invalidation. This differs from the current NeoForge capability registration/query model. Endpoint handlers, lifecycle, packet registration, and platform bootstrapping need target implementations.
- No official LDLib2 1.20.1 target was established in this research. Original LDLib and LDLib2 are different libraries; the original library's 1.20.1 availability does not imply source compatibility.
- Found `weiliangyan/LDLib2-1.20.1-Forge`, explicitly an unofficial backport. Its README reports client, dedicated server, multiplayer join, and UI editor validation on Forge 47.4.10 / Java 17+. Those are maintainer claims, not independently reproduced Federation compatibility tests. Do not assume the project's custom UI, scene previews, threading mixins, and synchronization work unchanged.

## Assessment

- The federation design is feasible on the inspected old AE2 API; no fundamental storage/crafting redesign was established as necessary.
- Full-feature backport is provisionally high effort / higher uncertainty than a target with all required libraries already supported. If the unofficial UI library passes targeted compatibility checks, the estimate can fall toward medium-high, comparable to a first forward port. It is not proven inherently harder than every 26.x port: old client rendering can be closer to 1.21.1.
- Main uncertainties: UI library compatibility and maintenance, Forge/legacy platform integration, Java baseline and persistence/network conversions, and preserving native AE2 lifecycle behavior.
- Do not give a precise week estimate before a UI and native-integration compile/run spike. Assume several weeks under usable-dependency assumptions; maintaining a library backport or rebuilding UI expands scope materially.
- Preserve shared domain/policy/routing logic and isolate actual platform differences. Choosing Forge 1.20.1 would be an explicit expansion of the current NeoForge-only product policy. Do not assert that NeoForge never supported 1.20.1; the old platform still differs from NeoForge 1.21.1 and needs separate verification if selected.
- First proof points: load the real Federation UI against the candidate library; register and connect a bridge/router to AE2; verify storage subscriptions/deduplication; verify remote processing, cancellation, return conservation, and restart. Retain full regression coverage before shipping.
- This scope concerns running the mod in 1.20.1 worlds, not downgrading existing 1.21.1 worlds.

## Sources

- https://github.com/AppliedEnergistics/Applied-Energistics-2/releases/tag/forge%2Fv15.4.11
- https://github.com/AppliedEnergistics/Applied-Energistics-2/tree/forge/v15.4.11
- https://github.com/Low-Drag-MC/LDLib2
- https://github.com/weiliangyan/LDLib2-1.20.1-Forge
- https://docs.minecraftforge.net/en/1.20.1/datastorage/capabilities/
- https://docs.minecraftforge.net/en/1.20.1/gettingstarted/
