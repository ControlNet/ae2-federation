# GuideME use-case tutorials

Status: implemented; confirmed by the owner on 2026-10-06.

The original [idea discussion](../archive/ideas/guideme-use-cases.md) is archived. Its candidate scenarios and
historical compatibility gaps are not the current feature specification. Maintain player instructions in the
actual guide pages and compatibility evidence in the existing compatibility documentation.

## Current guide content

The tutorials are part of AE2's existing GuideME guide. The
[English examples index](../../common/src/main/resources/assets/ae2federation/ae2guide/examples/index.md) and
[Simplified Chinese examples index](../../common/src/main/resources/assets/ae2federation/ae2guide/_zh_cn/examples/index.md)
introduce builds through network boundaries, pattern and CPU placement, rule direction, Endpoint mappings,
input delivery and output return paths.

Base examples cover a shared warehouse, remote assembly, Endpoint furnaces, partner workshops and a market hub.
Optional examples cover Create, Mekanism, Applied Mekanistics, Applied Flux, Advanced AE, ExtendedAE,
ExtendedAE-Plus, AE2 Lightning Tech, Data Energistics, Neo ECO AE Extension and OmniSequence: Transfinite.
The shipped pages, rather than the original proposal list, define the available scenarios; the historical Thermal
Expansion candidate is not a shipped optional tutorial pack.

Pages use GuideME scenes and custom Federation topology diagrams to explain the builds. Machine construction and
power setup remain the responsibility of each machine mod's own documentation where the tutorial refers to it.

## Optional-mod visibility

[GuideExamplePacks](../../common/src/main/java/space/controlnet/ae2federation/client/guide/GuideExamplePacks.java)
defines the required mod IDs for each built-in tutorial resource pack. A pack is selected only when every required
mod is loaded, including combined requirements such as Mekanism plus Applied Mekanistics or Applied Flux. The packs are
hidden, the way NeoForge hides each mod's own resources: always active, and not listed in the Resource Packs screen.

The pages and scene resources live under
[resourcepacks/](../../common/src/main/resources/resourcepacks/). This keeps unavailable tutorials out of the guide's
resource set instead of asking GuideME to render blocks from absent mods. English and Simplified Chinese versions
are provided alongside each other.

## Verification boundary

This documentation migration inspected the guide resources and pack selection source. It did not rerun the game
or certify new mod/version combinations. See [production compatibility runs](../compatibility/production-runs.md)
for integration evidence and the existing
[GuidePagesContractTest](../../common/src/test/java/space/controlnet/ae2federation/qa/GuidePagesContractTest.java)
for guide resource checks. Historical investigation notes remain in the archived discussion.
