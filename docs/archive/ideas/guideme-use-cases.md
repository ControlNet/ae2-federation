# GuideME use-case tutorials — archived discussion

Archived: 2026-10-06, after the owner confirmed implementation was complete.

See [implemented GuideME use-case tutorials](../../features/guideme-use-cases.md) for current resources and behavior.
The discussion below is historical: candidate scenarios, evidence gaps and future-tense statements reflect the
original brainstorming stage, not the current implementation or compatibility status. Not every proposed scenario
became a shipped tutorial.

## Original discussion


Discussion started: 2026-10-04.

Status: brainstorming. The owner wants realistic usage examples in the existing GuideME guide, covering AE2 itself,
technology mods, and AE2 addons. This document proposes scenarios; it does not schedule implementation or establish
new compatibility guarantees. Only idea documentation is in scope for this discussion.

## Confirmed intent

- Explain useful builds and why Federation helps, alongside the existing device and mechanism reference pages.
- Cover both ordinary technology machines and AE2 addons. Requested references include Create, Mekanism, Thermal
  Expansion, AE2 Lightning Tech, Neo ECO AE Extension, Data Energistics, ExtendedAE, ExtendedAE-Plus, and
  OmniSequence: Transfinite.
- Scenarios must use real mechanics. Do not invent recipes, cross-network CPU sharing, addon integration, or future
  Federation features to make a tutorial attractive.
- Read an unfamiliar addon's author documentation and implementation before proposing its scenarios. Lack of prior
  knowledge is a research task, not a reason to omit its distinctive machines from this idea.
- Keep the current work in the idea library. Actual pages, scenes, implementation and validation come later.
- Optional-mod tutorials must be shown only when all mods required by that particular tutorial are installed. Missing
  mods must not leave broken scenes or visible entries for unusable tutorials. This visibility requirement was confirmed
  by the owner during the discussion; the implementation mechanism remains undecided.

## Proposed editorial approach

Organize around player goals: share a warehouse, outsource a processing step, reuse a workshop's patterns, expand
capacity, and diagnose an incomplete job. Name the relevant mods in each example, and provide a secondary mod index.
Teach one new Federation concept per initial scene, then combine them in an advanced factory example.

Each future tutorial should specify the outcome, exact tested versions, prerequisite mods and machines, survival
materials, network boundaries, pattern location, CPU location, rule direction or Endpoint mapping, machine power,
input route, output route, expected visible result, and one realistic troubleshooting exercise. Third-party machine
construction belongs in the original mod's guide; our pages should explain the Federation connection.

The proposed visual presentation uses an overview followed by small annotated GuideME scenes for input, processing,
and output. Use consistent network colors and separate material flow from ME connectivity. Show relevant UI settings
alongside the physical layout. A large decorative scene must not hide a missing return path or power connection.

## Mechanism constraints

These follow the current [mechanics](../../../common/src/main/resources/assets/ae2federation/ae2guide/mechanics.md) and
[remote-processing guide](../../../common/src/main/resources/assets/ae2federation/ae2guide/remote-processing.md).

- Federation keeps ME networks independent. It does not merge channels or turn remote CPUs into local CPUs.
- For native pattern projection, consumer A enables use of provider B's crafting, which also enables the same-direction
  storage rule. A's CPU runs the job; B supplies the patterns and machines and does not need its own CPU for that job.
- For Federation Pattern Provider processing, patterns live in that Provider and map to Endpoints. No storage/crafting
  rule is required for that path. The Endpoint's subnet accepts input; actual outputs must enter an Endpoint return
  face. Merely leaving outputs in subnet storage does not complete this return path.
- One Endpoint belongs to one Provider at a time. Multiple Endpoints mapped by one Provider need distinct subnets.
  A proposed multi-machine tutorial must preserve this and must not promise linear scaling or automatic load balancing.
- Federation ME energy sharing is not a general replacement for machine FE power or Create rotational power.
- Current policy grants network-level storage access, including insertion and extraction. It is not item-filtered,
  read-only access or a security boundary against players. Custom filters/conditions remain a separate future idea.
- Wireless Federation bridges and Federation P2P are separate ideas; these examples must not silently depend on them.

## Evidence levels

- **Existing integration evidence:** the repository reports a passing relevant compatibility path. This is not yet a
  survival-build or client GuideME validation.
- **Mechanism-backed candidate:** upstream documents a real mechanism, but its particular Federation interaction
  still needs a dedicated test.
- **Version-dependent candidate:** the relevant mod is not established on the current Minecraft target.

The baseline evidence is [production compatibility runs](../../compatibility/production-runs.md) and the actual
[compatibility test sources](../../../common/src/compattest/java/space/controlnet/ae2federation/compat).
No new game tests were run for this brainstorming document.

Important limits discovered when reading those sources:

- Create's Millstone is real, but its test uses a Creative Motor and the harness extracts its output into ME storage.
  A survival tutorial needs a real rotational source and a placed return device, then an end-to-end check.
- Mekanism tests use creative FE sources. Replace these with survival power and recheck before publishing instructions.
- Data Energistics' processing test uses a chest as a **test-only simulated machine**; the harness converts input to
  output. It proves the provider interaction, not a real production line. It is not a tutorial build. Its crafting
  case uses a real Molecular Assembler and is the stronger starting example.
- A loaded NeoECO profile passing core tests does not establish its multiblock storage/crafting compatibility.
- The recorded OmniSequence profile runs ExtendedAE tests; it does not validate OmniSequence's own machines.
- A passing ExtendedAE-Plus cell test with ordinary counts does not establish arbitrary-precision transport through
  Federation. Do not promise unlimited displayed quantities or throughput.

## Candidate scenarios

### 1. AE2: a warehouse shared by independent workshops

Player goal: keep a warehouse and workshop on independent ME networks while accessing the warehouse from the
workshop terminal. Start with one workshop and then add a second. The warehouse uses ordinary AE2 cells and drives.

Federation lesson: bridge/router connectivity is separate from permission; enable workshop -> warehouse storage and
show a known inventory being visible and extractable. Explain that this grants access to that network's exposed
storage, not a selected subset of items. Keep private stock on a separate, unshared network if demonstrating separation.

Evidence: existing storageShare coverage. Proposed exercise: disable the rule and observe warehouse resources disappear
from the workshop view while the warehouse remains usable locally. This is a proposed walkthrough, not a new guarantee
of instant behavior under every lifecycle condition.

### 2. AE2: order from a separate assembly workshop

Player goal: use an existing Pattern Provider + Molecular Assembler installation from a second network without moving
the patterns. The ordering network has a crafting CPU; the assembly workshop owns the provider and machine.

Federation lesson: native pattern projection and the coupled crafting/storage rule. Compare this with scenario 3 so
players understand when they need a Federation Pattern Provider. Do not describe this as sharing the workshop's CPU.

Evidence: existing remoteCrafting / placed-block tests and the current remote-processing guide.

### 3. AE2 + vanilla: an outsourced furnace, then several independent processing cells

Player goal: request stone from a main terminal while an isolated furnace subnet handles cobblestone. Place the pattern
in the Federation Pattern Provider, map it to an Endpoint, feed the furnace via subnet storage and return stone through
a hopper into the Endpoint. Supply real furnace fuel in the survival build.

Federation lesson: Endpoint mapping, return faces, and the distinction from storage/crafting rules. An advanced follow-up
can use several Endpoints, each on its own subnet, to explain multiple destinations and Blocking mode. Validate the
observed scheduling before describing any throughput benefit.

Evidence: endpointFurnace and processingThreeEndpoints paths. This is a good first tutorial because it needs no addon.

### 4. Create: request gravel from a separate mechanical workshop

Player goal: request gravel while a separate Create workshop mills cobblestone. Use the ordinary AE2 Pattern Provider
next to the Millstone in the workshop and native crafting projection from the ordering network. A real return device
must collect gravel back into the workshop ME network. Provide rotational power locally.

Federation lesson: sharing a real machine workshop's patterns while keeping its ME network independent. The machine's
kinetic system remains Create's responsibility.

Evidence: recorded Create Millstone compatibility path, with the harness limitations above. A Crushing Wheels Endpoint
test also exists in current source, but its existence alone is not a passing-result claim. Treat it as a possible second
example after checking the corresponding run and survival layout. More elaborate sequenced assembly is deferred until
intermediate-item routing, reusable tools, and completion behavior have been demonstrated.

### 5. Mekanism: a remote Crusher with explicit input and output sides

Player goal: request gravel from cobblestone using a Crusher in a dedicated machine subnet. Feed input through a
Storage Bus, supply FE independently, and use the verified hopper return route into the Endpoint.

Federation lesson: machine-side configuration and a complete return path. Proposed failure exercise: turn off the
machine input side, identify why the job stalls, then restore it. Do not instruct players to duplicate a pending order.

Evidence: endpointCrusher uses an actual Crusher and hopper. A Factory upgrade is a later candidate, not something this
single-machine test establishes.

### 6. Mekanism + Applied Mekanistics: return a chemical instead of an item

Player goal: send charcoal to a Chemical Oxidizer and receive carbon as an AE chemical resource. Use Applied Mekanistics
to represent/store the chemical in AE2, configure the oxidizer's chemical output toward the Endpoint, and provide FE.

Federation lesson: real addon resource types and different material return routes. Explicitly distinguish Mekanism
chemicals from ordinary fluids. A separate storage example can share hydrogen held in a chemical cell across networks.

Evidence: endpointOxidizerChemical and chemicalCellShared. Final pattern quantities must be read from the selected
version/pack recipes, not generalized from test constants.

### 7. ExtendedAE: reuse an expanded assembly workshop

Player goal: keep Extended Pattern Providers and Extended Molecular Assemblers in a dedicated production network and
order their recipes from the base network through native crafting projection.

Federation lesson: the remote producer can be an addon provider; players retain that workshop's configuration. Avoid
claiming that every ExtendedAE block or special crafting mode has been verified.

Evidence: ExtendedAE placed-block crafting coverage. Use a simple deterministic crafting recipe first.

**Distinctive follow-up: a remote Assembler Matrix workshop.** The author's guide describes the matrix as a combined
multiblock provider and assembler, with separate pattern, craft and speed cores. Source confirms
`TileAssemblerMatrixPattern` implements `ICraftingProvider` and registers that service on its node; its crafter inserts
completed items into the local ME inventory. A formed matrix on network B is therefore a concrete candidate for native
pattern projection to a CPU on A. Start with a simple crafting pattern, then a dependency chain to exercise intermediate
returns. Existing extended-provider tests do not qualify the matrix itself; validate formation, discovery, completion,
and withdrawal on dismantling. Describe the capacity of the selected build, not an unmeasured speed multiplier.

Another candidate is a **Crystal Assembler materials workshop**. This machine has its own recipes, including certain
in-world transformation equivalents; it is not the Assembler Matrix or a generic crafting table. Use an actual recipe
and an adjacent ordinary provider with a demonstrated output return. The source/guide identifies its screen face as
unable to connect to the ME network, a useful concrete troubleshooting detail.

### 8. AE2 Lightning Tech: make an Overloaded Pattern Provider workshop available remotely

Player goal: use the addon's Overloaded Pattern Provider for an ordinary crafting recipe on a separate network, then
request it from the main terminal through crafting projection.

Federation lesson: reuse an existing addon workshop without moving its patterns or merging its network.

Evidence: the recorded Overloaded Pattern Provider crafting path. This does not establish remote lightning-event
automation, lightning energy transport, or every overload mechanic. Those require separate real-machine examples.

**Distinctive follow-up: order overload circuit boards from a lightning workshop.** The inspected recipe
`lightning_simulation/overload_circuit_board.json` really consumes one Unoverloaded Circuit Board and one Overload
Crystal Dust to produce one Overload Circuit Board, with 20,000 FE total energy and one high-voltage lightning resource.
Use the Lightning Simulation Chamber, keep its lightning supply on its own local ME network, and provide FE locally.
The chamber has a dedicated automation inventory implementation. Candidate wiring: ordinary provider beside the
chamber on B, with an actual output collection path into B, and native crafting projection from A. The lesson is that
the main base can request a specialist workshop's product without becoming that workshop's lightning infrastructure.
The exact side configuration and complete cross-network job still require validation. These amounts belong to the
inspected snapshot and are not universal pack recipes.

The Lightning Assembly Chamber (more item inputs) and Overload Processing Factory (mixed item/fluid recipes) provide
later progression examples. Keep machine FE and lightning requirements explicit; Federation ME energy sharing alone
does not replace them. Use deterministic machine recipes before considering in-world lightning transformations.

### 9. Data Energistics: keep an Adaptive Pattern Provider workshop intact

Player goal: fit an AE2 Pattern Provider into Data Energistics' Adaptive Pattern Provider, pair it with a Molecular
Assembler, and expose its craftable recipe to another network.

Federation lesson: remote access to a configured addon provider. Show the fitted provider explicitly; otherwise a player
may not have the pattern slots used by the example.

Evidence: adaptivePatternProviderCrafting. The processing variant uses a test-only simulated machine, so a real machine
must be selected and tested before proposing a detailed production tutorial. Do not extend this to arbitrary Data Flow
or data-reassembly transport without evidence.

**Distinctive follow-up: a remote multi-recipe Data Asynchronous Processing Factory.** Its guide and implementation
establish three processing channels and input colors associated with individual pattern definitions. Its base class
implements `ICraftingMachine`; ordinary `pushPattern` applies the pattern color before committing inputs. Use an
ordinary provider directly adjacent to this factory on B, and project that provider to the requesting network A. Start
with one real supported recipe, then submit two different recipes with sufficient output capacity and an explicit return
path. This teaches remote access to an actual asynchronous workshop, not the test harness's simulated furnace.

The factory also has an `ExternalFactoryRecipeCatalog` for ExtendedAE Crystal Assembler and AdvancedAE Reaction Chamber
recipes when those mods are installed. This makes a concrete later multi-addon example: request two verified recipes
from one dedicated workshop. The selected recipe files, power requirements, output routing and simultaneous execution
must be checked before publishing exact instructions. Native machine pattern delivery matters here: do not assume that
pushing loose items into storage through an Endpoint preserves the factory's per-pattern input colors.

### 10. ExtendedAE-Plus: an enhanced local CPU using a remote workshop

Player goal: equip the ordering network's CPU with the addon's 4x Crafting Accelerator and request a recipe from another
network's ordinary provider/assembler workshop.

Federation lesson: the requesting network still owns the CPU, even when production is elsewhere. Compatibility with the
accelerator does not imply a fourfold end-to-end speedup; recipe dependencies and machines can remain bottlenecks.

Evidence: acceleratedCpuCrafting. A second example may share a BigInteger storage cell, with explicit limits on what
quantity ranges the eventual tutorial has actually verified.

**Distinctive follow-up: maintain one set of patterns for several local machines.** ExtendedAE-Plus' Mirror Pattern
Provider follows a master provider's patterns and settings, while remaining read-only to players. Its implementation
extends AE2's PatternProviderBlockEntity and uses a PatternProviderLogic subclass. Keep master and mirrors in workshop
B; use the addon's binding tool locally and expose the workshop to A through native crafting projection. Editing the
master should update the available workshop recipes; test projection refresh and machine output return explicitly.
This combines local pattern maintenance with cross-network access. Do not claim that mirrors themselves transport
ingredients, or that Federation provides the mirror binding mechanism. Master chunk availability and removal behavior
are documented by the addon and should appear in the eventual troubleshooting exercise.

The **Super Assembler Matrix** is another actual multiblock-provider candidate: its guide separates normal and ultimate
structures and documents hybrid cores. Study its selected release separately rather than applying ordinary ExtendedAE
matrix dimensions or test results to it. The **Super Circuit Cutter** is a smaller alternative for mixed item/fluid
processing and output backpressure. These source-backed features are not all established in the pinned 1.6.3 test
profile; confirm release availability before choosing the tutorial's dependency versions.

### 11. Neo ECO AE Extension: separate storage and crafting districts

Player goal: connect a formed ECO storage multiblock to its own ME network and make that warehouse accessible to other
workshops through Federation storage rules.

Real mechanism: the author's guide describes an ECO storage multiblock with a storage interface connecting it to ME.
This is a mechanism-backed candidate, not a verified Federation multiblock scenario. Check actual storage mounts,
updates, extraction/insertion, and multiblock dismantle/reform behavior before promoting it to a tutorial.

A secondary candidate is outsourcing an Integrated Working Station recipe with item/fluid inputs. First verify the
selected recipe, capabilities, power behavior and product return; no exact build is asserted here. Do not call either
example cross-network sharing of NeoECO computation hosts.

**Distinctive second scene: order from the ECO Crafting System.** This is a multiblock pattern provider, distinct from
the ECO Computation System. The guide describes pattern buses holding patterns and worker blocks executing them.
Source confirms `ECOCraftingPatternBusBlockEntity` implements and registers `ICraftingProvider`, advertises its patterns,
and forwards `pushPattern` to available workers. Federation discovers active grid-node crafting-provider services, so
native projection is a concrete integration route supported by source inspection.

Proposed build: an assembled, normally powered ECO Crafting System on B with one compatible crafting pattern, and an
ordinary AE2 CPU/terminal on A. Enable A -> B crafting/storage. Start without optional overclocking/cooling, verify a
completed order and its return, then consider explaining those local upgrades. Do not require or promise a remotely
shared ECO computation host. Runtime validation must cover the multiblock's output path and formation lifecycle.

Sources: [ECO storage guide](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/c33f736a821683c6aa4715665ec7c3dadad7765f/guidebook/_zh_cn/neoecoae_intro/storage_system.md),
[workstation guide](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/c33f736a821683c6aa4715665ec7c3dadad7765f/guidebook/_zh_cn/neoecoae_intro/integrated_working_station.md).

### 12. OmniSequence: Transfinite: a specialist production district

Player goal: keep the addon's large production structures in a dedicated district/network and expose a completed,
researched production route to a main base. Study the Matter Fabrication Well's documented item/fluid ports and Pattern
Assemblies as the concrete integration candidates.

Real mechanism: author guides document item/fluid processing, local research prerequisites, ports, and Pattern
Assemblies with buffers and persistent tasks. Deeper source inspection confirms that
`MatterFabricationPatternAssemblyBlockEntity` extends AE2's PatternProviderBlockEntity and creates
`MatterFabricationPatternLogic`, a PatternProviderLogic subclass with ordinary `getAvailablePatterns` and `pushPattern`.
This identifies a concrete native-projection route, beyond merely assuming that the mods can load together.

**Concrete proposed scene: request a researched material from the Matter Fabrication Well.** Form and power the well
on B, complete its required local research, install a Pattern Assembly in a valid service position, and insert a
processing pattern matching one real well recipe. Let an ordinary CPU on A order that pattern via A -> B crafting.
The assembly queues the inputs and pushes completed output into B's ME network, where Federation's native crafting
return path is intended to intercept it. Test the full order, unavailable research, backpressure and restart before
calling this compatible. A real inspected recipe example is `fabrication_budding_amethyst.json` (amethyst blocks,
echo shard and nether star to budding amethyst); check its research eligibility in the chosen release rather than
assuming that the recipe's existence makes it immediately usable.

This example should explain that research belongs to the remote controller: enabling a Federation rule does not grant
research to the requesting base. Keep the original mod's guide responsible for the large structure and local research;
our scene should focus on its assembly, network boundary and completed-product route.

Status: mechanism-backed candidate. The current OmniSequence profile's ExtendedAE tests are insufficient. Do not
promise shared virtual CPUs, research unlocks, computation, or batching merely because both mods load together.

The addon's separate Omni Batch Provider API is a concrete reason for caution about performance promises:
Federation's `PatternProjection` currently implements `ICraftingProvider`, not `OmniBatchCraftingProvider`. A normal
remote recipe path and transparent access to an addon's specialized atomic-batch dispatch are different capabilities.
No new batch integration is included in this tutorial idea.

Source: [author's 2.0.0 release notes](https://www.curseforge.com/minecraft/mc-mods/omnisequence-transfinite/files/8831811).
Recheck mechanics against the eventual tested version (the recorded compatibility profile uses 2.0.7).

### 13. Thermal Expansion: a future machine-workshop variant

Player goal: apply the remote-workshop pattern to Thermal processing machines, including input/output face settings
and handling secondary outputs where the selected recipe has them.

Status: version-dependent candidate. The official file list inspected on 2026-10-04 lists 1.20.1 and older targets,
not a current 1.21.1 release. Do not publish it as a working current-target Federation tutorial. Revisit only when both
mods have a compatible supported target; this idea does not authorize a Federation backport.

Source: [TeamCoFH's Thermal Expansion files](https://www.curseforge.com/minecraft/mc-mods/thermal-expansion/files/all).
Select a specific machine and actual recipe only after that version prerequisite is satisfied.

## Proposed first set and later expansion

Suggested first set, not yet owner-approved: shared AE2 warehouse; ordinary remote assembly; Endpoint furnace; Create
Millstone; Mekanism Crusher; Applied Mekanistics chemical return; one addon-provider example (ExtendedAE).

Add Lightning Tech, Data Energistics and ExtendedAE-Plus examples to teach distinct configuration details rather than
copying the same tutorial with a different block. Keep NeoECO and OmniSequence as visible research candidates until
their own integrations are checked. Thermal remains tied to version availability.

A later capstone could connect a warehouse, assembly workshop and processing workshop while keeping their networks
independent. Verify it as a complete build; isolated successful examples do not establish every combined interaction.

## Optional-mod visibility

Required behavior:

- Base AE2/Federation tutorials remain available without any optional addons.
- A tutorial requiring Create appears only with Create installed. A Mekanism-only example must not unnecessarily
  require Applied Mekanistics; a chemical example using Applied Mekanistics requires both.
- Check the dependencies of each example, rather than only the main mod in its title. Version-specific machines also
  need a supported-version or required-content check where necessary; an installed older addon may lack a block.
- Excluded tutorials must be absent from navigation, search, category/subpage lists, and tutorial landing-page links.
  Excluding only the navigation entry is insufficient. Do not compile their scenes, recipes or missing item references.
- An old bookmark or direct link to an excluded tutorial must fail gracefully without attempting to render missing
  blocks. Translations must follow the same eligibility rule. Recompute availability on the appropriate guide/resource
  reload; installing or removing mods normally takes effect after restarting the game.
- Keep any optional scene files isolated from always-visible base examples. Do not import an optional structure from a
  base tutorial merely because the link to its standalone page is hidden.

Research finding (source inspection, not a client test): GuideME's normal live block rendering requires the registered
block and the installed mod's client assets/rendering code. `BlockImageTagCompiler` uses
`MdxAttrs.getRequiredBlockAndId`; an unknown ID produces `Missing block` and the block image is not built. Missing item
IDs similarly produce `Missing item`. Structure imports use Minecraft's block registry to load a StructureTemplate;
NBT/SNBT stores block IDs/states, not a self-contained copy of an absent mod's block implementation and renderer.
Packaging a structure therefore does not make its missing-mod blocks renderable. A separately packaged screenshot
could display without the mod, but it is not an interactive block scene and is not the selected missing-mod behavior.

GuideME 21.1.1, 21.1.15 and 21.1.19 were inspected. Their page-loading path scans Markdown resources across namespaces;
no built-in loaded-mod page filter was found in the inspected loader/frontmatter implementation. Merely placing a page
under an optional mod's namespace does not gate it. Do not invent a `requires_mods` YAML field or assume NeoForge recipe
conditions automatically apply to GuideME Markdown. The future implementation can investigate conditionally exposing
page resources or filtering eligible pages before guide indexing/compilation, while preserving the existing AE2 guide
integration. This is an implementation investigation direction, not a chosen API or a request to patch GuideME now.

Sources: [GuideME authoring documentation](https://guideme.appliedenergistics.org/authoring/),
[21.1.15 source artifact](https://repo.maven.apache.org/maven2/org/appliedenergistics/guideme/21.1.15/guideme-21.1.15-sources.jar),
[21.1.19 source artifact](https://repo.maven.apache.org/maven2/org/appliedenergistics/guideme/21.1.19/guideme-21.1.19-sources.jar).
Relevant classes: `guideme.internal.GuideReloadListener`, `guideme.compiler.Frontmatter`,
`guideme.compiler.tags.MdxAttrs`, `guideme.scene.BlockImageTagCompiler`,
`guideme.scene.element.ImportStructureElementCompiler`.

## Decisions still open

- Which examples enter the first tutorial set and how much prerequisite AE2 knowledge is assumed.
- Whether to add downloadable survival demonstration worlds or structures alongside GuideME.
- How to implement the confirmed conditional visibility behavior within AE2's existing GuideME guide; resource
  packaging and filtering hooks remain implementation research.
- Exact survival builds, recipes, version pins, scenes and troubleshooting steps after end-to-end validation.

## Source investigation appendix (2026-10-04)

Documentation and implementation were both inspected for all six requested AE2 addons. This investigation uses
pinned source snapshots; a source feature is not automatically present in every published jar. No addon was launched
as part of this discussion. Earlier compatibility runs are separately identified above.

| Addon | Inspected commit | Author documentation and implementation |
|---|---|---|
| ExtendedAE | `2dc79a8b28ab9ab842fb93e497f067ddabf2f4cc` (`1.21-neoforge`) | [Matrix guide](https://github.com/GlodBlock/ExtendedAE/blob/2dc79a8b28ab9ab842fb93e497f067ddabf2f4cc/src/main/resources/assets/extendedae/ae2guide/epp_intro/assembler_matrix.md); [provider registration and dispatch](https://github.com/GlodBlock/ExtendedAE/blob/2dc79a8b28ab9ab842fb93e497f067ddabf2f4cc/src/main/java/com/glodblock/github/extendedae/common/tileentities/matrix/TileAssemblerMatrixPattern.java); [Crystal Assembler guide](https://github.com/GlodBlock/ExtendedAE/blob/2dc79a8b28ab9ab842fb93e497f067ddabf2f4cc/src/main/resources/assets/extendedae/ae2guide/epp_intro/crystal_assembler.md) |
| ExtendedAE-Plus | `eae9ebbca84cc88243f648a24c5579d1b1bbbeca` (`master`) | [Mirror guide](https://github.com/GaLicn/ExtendedAE_Plus/blob/eae9ebbca84cc88243f648a24c5579d1b1bbbeca/src/main/resources/assets/extendedae_plus/ae2guide/_zh_cn/introduction/devices/mirror_pattern_provider.md); [mirror implementation](https://github.com/GaLicn/ExtendedAE_Plus/blob/eae9ebbca84cc88243f648a24c5579d1b1bbbeca/src/main/java/com/extendedae_plus/content/ae2/MirrorPatternProviderBlockEntity.java); [Super Matrix guide](https://github.com/GaLicn/ExtendedAE_Plus/blob/eae9ebbca84cc88243f648a24c5579d1b1bbbeca/src/main/resources/assets/extendedae_plus/ae2guide/_zh_cn/introduction/devices/super_assembler_matrix.md) |
| AE2 Lightning Tech | `1d4589b6bd50672051f78d766505530beacfebc0` | Inspected the existing local source checkout at the pinned commit: `src/main/resources/assets/ae2lt/ae2guide/_zh_cn/machines/`, recipe `src/main/resources/data/ae2lt/recipe/lightning_simulation/overload_circuit_board.json`, and `src/main/java/com/moakiee/ae2lt/machine/lightningchamber/LightningSimulationChamberAutomationInventory.java`. The previously recorded public repository `ae2lt/AE2-Lightning-Tech` returned HTTP 404 during this research; the source evidence is the retained pinned checkout, not a claim of current repository availability. |
| Data Energistics | `ddef032e7ca10744c52efc179a7b80b738e06ad9` | [Asynchronous factory guide](https://github.com/ModularMCLib/DataEnergistics/blob/ddef032e7ca10744c52efc179a7b80b738e06ad9/src/main/resources/assets/data_energistics/ae2guide/_zh_cn/items-blocks-machines/6.13_data_asynchronous_processing_factory.md); [factory implementation](https://github.com/ModularMCLib/DataEnergistics/blob/ddef032e7ca10744c52efc179a7b80b738e06ad9/src/main/java/com/fish_dan_/data_energistics/blockentity/machine/DataAsynchronousProcessingFactoryBlockEntity.java); [native crafting-machine input path](https://github.com/ModularMCLib/DataEnergistics/blob/ddef032e7ca10744c52efc179a7b80b738e06ad9/src/main/java/com/fish_dan_/data_energistics/blockentity/machine/DataRipperReassemblerBlockEntity.java) |
| NeoECO | `c33f736a821683c6aa4715665ec7c3dadad7765f` | [Crafting system guide](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/c33f736a821683c6aa4715665ec7c3dadad7765f/guidebook/_zh_cn/neoecoae_intro/crafting_system.md); [pattern bus implementation](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/c33f736a821683c6aa4715665ec7c3dadad7765f/src/main/java/cn/dancingsnow/neoecoae/blocks/entity/crafting/ECOCraftingPatternBusBlockEntity.java) |
| OmniSequence: Transfinite | `567e73def8dca77dbc1b43462fd7495d4290c373` | [Pattern Assembly guide](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/567e73def8dca77dbc1b43462fd7495d4290c373/src/main/resources/assets/molecularmanipulator/ae2guide/_zh_cn/items-blocks-machines/matter_fabrication_pattern_assembly.md); [provider logic](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/567e73def8dca77dbc1b43462fd7495d4290c373/src/main/java/com/atir/molecularmanipulator/blockentity/MatterFabricationPatternLogic.java); [separate batch API](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/567e73def8dca77dbc1b43462fd7495d4290c373/docs/omni-batch-provider-api.md) |

Local Federation sources checked against these mechanisms: `crafting/projection/RealCraftingProviders.java` discovers
active node services; `crafting/projection/PatternProjection.java` delegates ordinary patterns, priority, busy state and
pushes to the real provider. This is source-level evidence for the proposed connection, not runtime qualification of
the above addon multiblocks or their optional optimizations.
