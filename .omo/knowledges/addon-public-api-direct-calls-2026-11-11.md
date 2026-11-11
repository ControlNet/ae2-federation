# Add-on public API: direct calls, not reflection (2026-11-11)

Owner rule: an add-on's public API is called directly; reflection only for private members.

## Pattern

- `neoforge-1.21.1/build.gradle` configuration `addonApi`: on `sourceSets.main.compileClasspath` only (not run or
  test classpaths, not packaged, not declared in `neoforge.mods.toml`, like the Jade and Data Energistics plugins).
  Each entry is `transitive = false` and has a sha256 in `gradle/verification-metadata.xml` whose jar sha512 matches
  the compat manifests.
- Each add-on package under `compat/` has a gate (the class `CompatAdapters` lists) that never names the add-on's
  types, and a package-private `*Direct` class that does. The gate loads the Direct class only after it recognised one
  of the add-on's classes by name, or after `ModList` reports the mod, and catches `LinkageError` (another API than
  the one compiled against) as the old reflective code returned null. `CraftingCpuLifecycle` also catches
  `LinkageError` from a Direct CPU's methods.
- Neo ECO's batch hand-over runs later inside Neo ECO, outside the gate: `NeoEcoBatchingDirect.handOver` reads the
  batch before `pushBatch` and declines on `LinkageError` before anything is taken.

## Compiled against

| Add-on | Version (source) | Notes |
| --- | --- | --- |
| Neo ECO | 21.2.15 (Modrinth udZtKfzP/c26NIHTF) | batch API and CPU accessors identical in 21.2.0, 21.2.1, 21.2.9 |
| Advanced AE | 1.6.12 (Modrinth rxYaglEe/ablTMAjP) | |
| AE2 Lightning Tech | 2.1.3 (Modrinth ArHeh5Fz/GGx4RqQt) | needs Thunderbolt Core 2.0.3 (M5jhzZXH/i3H7uX2O): its CPU pool implements Thunderbolt interfaces |
| ExpandedAE | 2.1.4 (CurseForge 1213351/9021958) | Modrinth stops at 2.1.1. The 5-argument target cache exists from 2.1.2; `neoforge.mods.toml` declares ExpandedAE optional `[2.1.2,)` (2.1.1 overwrites `pushPattern`), so the gate only checks that it is loaded |
| JustEnoughCharacters | 4.5.30 (Modrinth I7k4B65h/iNnJuUnx) | `Match.contains` overloads use JDK types only; no PinIn needed |

## Still reflection

- Private fields: Advanced AE `AdvCraftingCPUCluster.activeCpus`, Neo ECO `ECOComputationThreadingCoreBlockEntity.deferredInit`.
- Data Energistics record constructors: 3.3 and 3.4 differ (`List` vs `ObjectList` components), no single compile
  target links against both.
- `DataEnergisticsBatching` finds DE's interfaces by name (type detection, stays inert without DE).
- `PatternProjection` uses `java.lang.reflect.Proxy` to implement a dynamic set of add-on and marker interfaces; the
  handler then calls the real provider directly.
- `NativeStorageAliasProbe` (generic, any add-on's storage wrappers) and GuideME's private `GuideNavBar$Row`.

## Checks (2026-11-11)

Compat profiles neoecoae, neoecoae-21.2.0, neoecoae-advanced-ae, neoecoprototype, advanced-ae, ae2-lightning-tech,
addons-all, useless-mod-addons all pass; ExpandedAE boundary cases expanded-2.1.2/2.1.3/2.1.4-all-deps 6/6
(`smartblockinglane`); UI scenario `ui.chinese-scales` with JECh 4.5.30 in `run-uitest/mods` 34/34. The other
ExpandedAE boundary cases crash at startup without Federation too (missing dependencies), except
expanded-2.1.1-matched-extended, which fails on Federation's `PatternProviderLogicTargetBinding` mixin exactly as
recorded for 0.0.8 in `tests/compat/version-boundaries-evidence.json`.
