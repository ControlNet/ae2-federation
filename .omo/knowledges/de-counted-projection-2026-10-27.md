# Data Energistics counted batches through a projection (2026-10-27)

Data Energistics' Trinity CPU pushes many crafts as one batch only to providers it knows as counted. Before this
change, a projection got one craft per push, one push a tick: 1024 runs took 1025 ticks through Federation and 4 ticks
on one joined Grid. Now a projection of a counted provider is counted too, and all three benchmark sizes take 4 ticks
both ways.

## How DE decides (3.3.5)

`CountedCraftingProviderAdapters.resolve(provider)`, checked in this order:

1. An adapter in its registry, keyed by provider identity. Its provider plugins fill the registry.
2. `TargetedCountedCraftingProvider`, the internal interface that DE's mixin gives `PatternProviderLogic`.
3. `ProviderCapacityView`, internal.
4. `CountedCraftingProvider`, internal.
5. Otherwise, one craft per push.

The public route is step 1:

- A plugin class is annotated `@DataEnergisticsEntrypoint` and implements `DataEnergisticsPlugin`. DE finds it by
  annotation scan and creates it with its no-argument constructor at common setup.
- The plugin calls `registry.patternProviders().registerFactory(metadata, factory)`, where the metadata's identity is
  `ProviderIdentityDescriptor.External(type, schema)`.
- When a provider mounts on a crafting service (`NetworkCraftingProviders$ProviderState.mount`), DE binds it:
  - It needs a `PatternContainer`. It takes it from `PatternProviderRuntimeLink.patternContainer()`, from the provider
    itself, or from its own accessor for AE2 logic.
  - It needs an identity. It takes it from `PatternProviderIdentitySource.providerIdentity()`.
  - When both match a registration, the factory's adapter goes into the registry. Unmount removes it.
- The registry is frozen after common setup, so registering through the plugin is the only public way in.

## What we do

- `compat/data_energistics/DataEnergisticsBatching` names DE's types only.
  - A real provider whose type implements the public `CountedCraftingProviderAdapter` gives its projection proxy three
    DE interfaces: `CountedCraftingProviderAdapter`, `PatternProviderIdentitySource` and `PatternProviderRuntimeLink`.
- `compat/data_energistics/DataEnergisticsCountedProjections` is the plugin, compiled against DE's API.
  - It registers the identity `ae2federation:pattern_projection`, schema 1. The factory returns the proxy itself, since
    the proxy is the adapter.
  - `captureCapacityFast` returns one aggregate target with unknown capacity, or nothing once the projection is
    withdrawn.
  - `prepareBatch` asks the real provider's public `prepareBatch` and wraps the admission. On commit, the batch goes
    through `ProjectedProvider.pushBatch`, so the return ledger owes the batch's outputs.
  - `patternContainer()` is an empty, hidden container. DE only needs it to be non-null, and AE2 has no public getter
    for a logic's host.
- The CPU commits with **one craft's** inputs (the "prototype"), and the provider pushes them `count` times. The ledger's
  inputs and container items are therefore the prototype's amounts times `admission.count()`.
- `dataEnergisticsApi` in `neoforge-1.21.1/build.gradle` comes from CurseMaven: `curse.maven:data-energistics-1565514:9095664`,
  the same file as the compat pin, with the same sha512. Like `jadeApi`, it feeds only the main compile classpath.

## Limits

- A real provider that DE counts only through another plugin's registration, not through the public interface,
  stays at one craft per push. Reaching DE's registry for it would need DE internals.
- A Federation Pattern Provider's Lane subclasses `PatternProviderLogic`. DE's exact-class gate then gives single
  admissions: one craft per push, as before.

## Benchmark (opt-in group `data-energistics-benchmark`)

| Runs | Federation before | Federation after | Joined |
| --- | --- | --- | --- |
| 16 | 19 ticks | 4 ticks | 4 ticks |
| 64 | 63 ticks | 4 ticks | 4 ticks |
| 256 | 255 ticks | 4 ticks | 4 ticks |
| 1024 | 1025 ticks | 4 ticks | 4 ticks |

The "machine" is a chest whose cobblestone the test turns into stone every tick. It is a test mechanism, the same on
both sides. Through Federation, 1024 runs crossed in one push.
