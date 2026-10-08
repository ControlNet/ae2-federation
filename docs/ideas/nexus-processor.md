# Nexus Processor and Nexus Core

Discussion date: 2026-10-06.
Status: implemented on 2026-10-06, then revised on 2026-10-08 (below).

## Revision 2026-10-08: AE2-style chain

The owner asked for the chain and names to follow AE2's own processors and cores. This supersedes the recipe
sections and the "short chain" boundary further down:

| Item | AE2 counterpart | Recipe |
|---|---|---|
| Printed Nexus Circuit / 联结电路板 (`ae2federation:printed_nexus_circuit`) | Printed Logic Circuit / 逻辑电路板 | Inscriber, inscribe mode: Inscriber Logic Press (kept) + Ender Pearl |
| Nexus Processor / 联结处理器 | Logic Processor / 逻辑处理器 | Inscriber, press mode: circuit + Redstone Dust + Printed Silicon |
| Nexus Core / 联结核心 | Formation Core / 成型核心 | Shaped row: Fluix Crystal + Ender Dust + Nexus Processor -> 16 |

Every Federation device now takes a Nexus Core where it took the processor. The circuit was the artist's new item;
the owner accepted it and set its English name and ID after AE2's printed circuits. Current behavior is described in
the [survival feature document](../features/survival-playability.md).

## Purpose

Give the mod's shared crafting processor a distinctive name and a short manufacturing route based on Ender Dust.
The revision consists of three linked changes: rename the existing processor, introduce one new intermediate
item, and replace the processor's acquisition recipe. Early survival availability remains a design goal.

## Item changes

| Item | Current state | Requested state |
|---|---|---|
| Existing crafting processor | Federation Logic Processor / 联邦逻辑处理器 | **Nexus Processor / 联结处理器** |
| New crafting intermediate | Does not exist | **Nexus Core / 联结核心** |

The Nexus Processor takes over the existing processor's role in Federation device recipes. It is not an
additional tier alongside the old processor. The Nexus Core is the new consumable used to manufacture it.
"Nexus" expresses connections between independent network members; the Chinese name is an intentional
functional localization, not a claim that the Federation requires a central controller.

## Planned item IDs

These are the planned registry IDs for implementation, not open naming choices:

| Item | Planned item ID | Translation key |
|---|---|---|
| Nexus Processor / 联结处理器 | `ae2federation:nexus_processor` | `item.ae2federation.nexus_processor` |
| Nexus Core / 联结核心 | `ae2federation:nexus_core` | `item.ae2federation.nexus_core` |

The processor directly replaces the existing item ID `ae2federation:federation_logic_processor`. The user explicitly
does not require preserving old item stacks in existing saves. Do not add a legacy item registration, alias,
remapping, or save-migration mechanism for this change. Old-ID stacks may be lost or become unavailable after
updating. The old ID must not represent a second independently obtainable processor tier. Update internal device recipe inputs,
item models, translations, GuideME references, and tests to the planned IDs consistently.

The core crafting recipe outputs `16 × ae2federation:nexus_core`. The processor assembly consumes
`1 × ae2federation:nexus_core`, `1 × ae2:ender_dust` (or its selected conventional tag), and
`1 × ae2:printed_silicon`, and outputs `1 × ae2federation:nexus_processor`.

## New core recipe

**3 Redstone Dust + 3 Ender Dust -> 16 Nexus Cores**, using shaped crafting.

Each dust occupies its own crafting-grid slot: six ingredient slots in total, not stacks of three in two slots.
The quantities, output count, and shaped crafting method come from the user's proposal.

The assistant suggested two adjacent rows as a simple arrangement:

```text
R R R
E E E

R = Redstone Dust
E = Ender Dust
Output = 16 Nexus Cores
```

This exact arrangement is a recommendation, not a separately confirmed user requirement. It was the primary
layout used for the static collision investigation. A three-wide recipe requires a crafting table.

## Replacement processor recipe

Use AE2's Inscriber to produce **one Nexus Processor**:

| Slot | Input | Consumption per operation |
|---|---|---:|
| Top | Nexus Core | 1 |
| Middle | Ender Dust | 1 |
| Bottom | AE2 Printed Silicon / 硅板 (`ae2:printed_silicon`) | 1 |

All three inputs are consumed. No completed native Logic Processor or Fluix Dust is required by this replacement
assembly recipe. It replaces the current native Logic Processor + Fluix Dust acquisition route rather than
silently retaining both recipes.

AE2's existing `press` mode supports this consumption behavior. Its normal upper/lower symmetry also permits
the core and Printed Silicon to exchange positions; the slot table above is the intended guide presentation.

## Scope and boundaries

- Keep the manufacturing chain short: craft the core, then assemble the processor in the existing Inscriber.
- Do not add a Printed Nexus Circuit, dedicated Nexus press, extra material-processing chain, new machine,
  ore, meteorite loot, or world-generation requirement as part of this revision.
- Existing Printed Silicon production still follows AE2's normal requirements.
- Existing Federation Cable, Bridge, Router, Pattern Provider, and Processing Endpoint recipes continue to use
  the renamed processor. This proposal does not change their ingredient quantities or yields.
- This revises item names, registry IDs, and acquisition. It does not add network behavior or another mod dependency.

## Artwork and player documentation

The user now has an artist available for additional 2D assets. The new core needs an item icon; the processor's
name, icon, and description should be reviewed together. The desired visual direction remains blue-green with
an Ender-material association. Exact shapes, pixel details, and any processor-icon replacement remain for art
design; they have not been finalized by this discussion.

Update English and Simplified Chinese item names, both GuideME item/acquisition pages, and any current recipe
examples or cost calculations affected by replacing the processor inputs. Add acquisition guidance for the core.
Update the implemented [survival feature document](../features/survival-playability.md) when the revision actually
ships; preserve the [archived original discussion](../archive/ideas/survival-playability.md) as historical context.

## Implementation decisions left open

- Exact shaped-core layout, subject to the six ingredients and sixteen-item output above.
- Whether recipes use exact AE2 Ender Dust or the conventional `c:dusts/ender_pearl` tag.
- Core icon and any processor artwork revision.

These details do not require adding more intermediate items. The future coding agent can resolve routine
implementation choices while preserving the requested names, material counts, outputs, and short recipe chain.

## Acceptance expectations

1. Players see Nexus Processor / 联结处理器 (`ae2federation:nexus_processor`) in place of the old processor,
   plus Nexus Core / 联结核心 (`ae2federation:nexus_core`).
2. Six correctly arranged dust inputs yield sixteen cores; the old processor acquisition recipe is replaced.
3. The Inscriber consumes one core, one Ender Dust, and one Printed Silicon per processor, including automation.
4. All current device recipes reference the new processor ID. Old-ID item preservation is outside scope;
   no compatibility item, alias, or migration mechanism is introduced.
5. GuideME and recipe-viewer integration show the new acquisition route consistently in both supported languages.
6. Validate the loaded recipes in a minimal AE2 environment and the project's ATM10 compatibility environment.

## Supporting research

- [Recipe feasibility and collision audit](../../.omo/knowledges/nexus-processor-recipe-feasibility-2026-10-06.md):
  native Inscriber support, no matching ordinary six-ingredient recipe found in the inspected artifacts,
  the non-conflicting ExtendedAE seed recipe, and runtime verification limits.
- [Expanded name audit](../../.omo/knowledges/processor-name-curseforge-expanded-audit-2026-10-06.md):
  no exact Nexus Processor collision found in the checked language files; Nexus is already used in other
  addon/project/device names. This was considered during naming and is not a new blocker.

Research supports feasibility but is not evidence that these changes are already implemented.
