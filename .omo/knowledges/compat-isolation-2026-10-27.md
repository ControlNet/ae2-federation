# Add-on code lives under compat/<mod id> (2026-10-27)

Code that names one optional mod sits in `common/src/main/java/space/controlnet/ae2federation/compat/<mod id>/`.
Everything else names only the platform and required libraries. `AddonCodeContractTest` (in `qa/`) enforces this.

## Layout

| Package | What it holds |
| --- | --- |
| `compat/adapters/CompatAdapters` | The registry: static lists of adapters that core code reads |
| `compat/adapters/CraftingLogicCpu` | Shared reader for add-on CPUs whose logic has `getLastLink`/`getWaitingFor` |
| `compat/advanced_ae` | CPU lifecycle (`AdvCraftingCPU`, `AdvCraftingBlockEntity`); shown provider logic |
| `compat/ae2lt` | CPU lifecycle (`TimeWheelCraftingCPU`, Pigmee pool) |
| `compat/neoecoae` | CPU lifecycle (`ECOCraftingCPU`, threading core); `NeoEcoBatching` (fast path, parallel) |
| `compat/expandedae` | Lane targets built with ExpandedAE's target cache |
| `compat/data_energistics` | Whether the guide leaves the sidebar nesting to Data Energistics; `DataEnergisticsBatching` (counted batches through a projection, by name) and `DataEnergisticsCountedProjections` (its DE plugin, compiled against DE's API) |
| `compat/jade`, `compat/jech` | Unchanged |

The core interfaces live next to the code that uses them:

- `crafting/projection/AddonCraftingCpus`: `cpu(Class)` and `anchor(Class)`. `CraftingCpuLifecycle` caches the first
  non-null answer per class.
- `crafting/projection/AddonProjections`: `showsLogic(Class)`, `interfaces(Class)` and `invoke(...)`. A projection's
  proxy sends a call of an add-on interface to the adapter that named it.
- `crafting/projection/ProjectedProvider`: what an adapter may call on a projection (`isLive`, `real`, `pushBatch`,
  `containerItems`).
- `ae2/processing/AddonLaneTargets`: `available`, `create` and `find`. The first available adapter wins.

## Rules

- An adapter recognises its add-on's classes by name, with reflection. It stays inert without the add-on and returns
  null when the API changed. Core then falls back to its behaviour for any add-on, such as the conservative unknown-CPU
  watch or AE2's own targets.
- No adapter is gated on `ModList`. The class-name checks are the gate.
- No class sits directly in `compat/`. The compat test mod (`ae2federation_compat`) owns package
  `space.controlnet.ae2federation.compat`, and JPMS refuses a package split between two mods. The server fails at
  boot with `ResolutionException: Modules ae2federation_compat and ae2federation export package ...`. Unit tests and
  dev GameTests do not catch this; only compat runs do. The contract test now does.
- The contract test scans imports and string literals with comments stripped. Comments may name other mods, for
  example as attribution. Reverse-domain names whose first segment has 2 or 3 letters must belong to a required
  library (`net.minecraft`, `net.neoforged`, `com.mojang`, `com.lowdragmc`, `dev.vfyjxf`, `com.llamalad7`,
  `org.spongepowered`, `org.jetbrains`, `org.joml`, `org.lwjgl`, `org.slf4j`, `com.google`, `it.unimi`).
  `isLoaded("...")` with a literal is also refused outside `compat/`.
- `GuideExamplePacks` lists mod ids for its guide resource packs. It is data, passed `ModList::isLoaded` as a
  predicate, and the test allows it.

## Adding support for an add-on

1. Add `compat/<mod id>/` with a class that implements the relevant core interface.
2. Register it in `CompatAdapters`.
3. If the core interface lacks the hook, add a default method to it. Do not name the add-on in core.
4. Prefer the add-on's own plugin API. Data Energistics' counted batches go through its public provider plugin (see
   `de-counted-projection-2026-10-27.md`). Compile against such an API in a configuration that feeds only the main
   compile classpath, like `jadeApi` and `dataEnergisticsApi`, and keep the typed classes out of `CompatAdapters`: an
   adapter there names the add-on's types and calls the typed class only once the add-on has made the call possible.
5. For add-on mixins, use a separate mod-gated mixin config whose package is outside `compat/`, with `require = 0`. A
   registered mixin package cannot hold ordinary classes.

## Verification (2026-10-27)

- Behaviour-preserving move. `./gradlew --dependency-verification=strict check build :neoforge-1.21.1:compatTestJar`
  passed.
- `python3 tools/dev_gametests.py --manifest --ci --no-isolate`: 293/293 passed.
- Compat profiles all passed: baseline 20/20, neoecoae 42/42, neoecoae-21.2.0 42/42, neoecoae-advanced-ae 37/37,
  advanced-ae 34/34, ae2-lightning-tech 35/35 and data-energistics 29/29.
- atm10 with `--tests expandedae,advanced-ae` passed 15/15.
- The Data Energistics sidebar check is client-only, so no server profile exercises it.
