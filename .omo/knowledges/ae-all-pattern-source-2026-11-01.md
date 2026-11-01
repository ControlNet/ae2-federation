# AE All Pattern (AE 全样板) source reading, for processing patterns (2026-11-01)

Read LangQi99/ae-all-pattern at 4195cc6f (0.2.8, MIT, 2026-10-28) to decide how Federation programs should get
processing patterns. Companion of the dev worktree's `server-processing-recipe-discovery-2026-11-01.md`.

## What it does

- Server-side recipe reading exists only through `MachineAdapter`s (`machine/MachineAdapterRegistry`): vanilla
  furnaces, Mekanism item-to-item families (`compat/mekanism/MekanismItemToItemAdapter`), Packaged crafting. Each
  adapter also inserts into and extracts from the machine for its Linker.
- Everything else comes from a client JEI/EMI scan (`client/ClientJeiAggregateScanner`): the clicked block's item is
  the JEI CATALYST focus; one category is picked by stripping tier prefixes and housing suffixes from the machine ID
  and matching the rest against category IDs, with a hand list of Mekanism renames; slots become `GenericStack`s; a
  slot whose candidates equal an item tag keeps the tag; an output counts as random when its slot name or tooltip
  contains "chance", "probab", "概率" and so on.
- `network/AggregateGenerationService` checks that the block at the position matches, that the player holds the
  Generator, and that no server adapter covers the machine. It does not compare the uploaded inputs/outputs with any
  server recipe: the catalog is what the client sent. This contradicts its own ADR-0001 ("RecipeManager is the
  authority").
- The Aggregate Pattern is a new item: one item refers to a server-side library of recipes and expands into many
  `IPatternDetails` (`aggregate/AggregatePatternExpander`, encode + decode per child, cached, spread over ticks).
  Providers need a mixin per addon to expand it (PatternProviderLogic, ExtendedAE, AdvancedAE, ECO, Packaged, ...).
- `AggregatePatternDetails` wraps the AE2 processing pattern with its own `IInput`s so a slot can take any item of a
  tag; plain AE2 processing patterns take one concrete key per input.
- The Linker/Binder is an execution layer of its own: virtual patterns, an input buffer, adapter insertion, and it
  pulls every output a bound machine exposes.

## AE2 facts checked in the 19.2.17 jar

- `AEProcessingPattern.MAX_INPUT_SLOTS = 81`, `MAX_OUTPUT_SLOTS = 27`.
- `PatternDetailsHelper.encodeProcessingPattern(List<GenericStack>, List<GenericStack>)` needs no recipe.

## Our state at 8b4f7e10

- `RecipeIndex` holds crafting, stonecutting and smithing recipes only; cooking recipes are not indexed.
- `ae2.provider_patterns` already reads a processing pattern as a `Recipe` with `id` None, kind "processing", inputs
  slot by slot as `Stack`s (`resource` = key ID, amount times multiplier) and outputs.
- `PatternCalls.write` throws NotImplementedError for kind "processing".
- `DomainCalls.key(resource)` resolves a resource ID to an `AEKey` for the other calls.
