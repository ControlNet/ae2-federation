package space.controlnet.ae2federation.compat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Add a mod adapter with one entry here; selection is validated before any layout is constructed. */
final class PlaythroughMachineAdapters {
    static final List<PlaythroughMachineAdapter> REGISTERED = List.of(
            new PlaythroughInscriberAdapter(),
            new PlaythroughChargerAdapter(),
            new PlaythroughCookingAdapter("furnace", "minecraft:smelting", "minecraft:furnace"),
            new PlaythroughCookingAdapter("smoker", "minecraft:smoking", "minecraft:smoker"),
            new PlaythroughCookingAdapter("blast-furnace", "minecraft:blasting", "minecraft:blast_furnace"),
            new PlaythroughCrystalAssemblerAdapter(),
            new PlaythroughReactionChamberAdapter(),
            PlaythroughLightningTechAdapter.overloadProcessing(),
            PlaythroughLightningTechAdapter.lightningAssembly(),
            PlaythroughLightningTechAdapter.lightningSimulation(),
            new PlaythroughSmithingAdapter(),
            new PlaythroughStonecuttingAdapter(),
            new PlaythroughCrystalAggregatorAdapter(),
            new PlaythroughEnergizingAdapter(),
            new PlaythroughDataReassemblerAdapter(),
            new PlaythroughReprocessorAdapter(),
            new PlaythroughMetallurgicInfuserAdapter(),
            new PlaythroughMekanismMachineAdapter("enrichment-chamber", "mekanism:enriching", "mekanism:enrichment_chamber",
                    "mekanism.common.tile.machine.TileEntityEnrichmentChamber"),
            new PlaythroughMekanismMachineAdapter("crusher", "mekanism:crushing", "mekanism:crusher",
                    "mekanism.common.tile.machine.TileEntityCrusher")
    );
    static final List<PlaythroughMachineAdapter> ENABLED = selected();

    private static List<PlaythroughMachineAdapter> selected() {
        for (var adapter : REGISTERED) if (adapter.inputFace() == net.minecraft.core.Direction.DOWN)
            throw new IllegalStateException("Machine adapter " + adapter.id() + " declares input face DOWN, which the output collector occupies");
        String property = System.getProperty("ae2federation.playthrough.machines", "all").trim();
        if (property.equals("none")) return List.of();
        if (property.equals("all")) return REGISTERED;
        Set<String> ids = Arrays.stream(property.split(",")).map(String::trim).collect(Collectors.toSet());
        var known = REGISTERED.stream().map(PlaythroughMachineAdapter::id).collect(Collectors.toSet());
        if (!known.containsAll(ids)) throw new IllegalArgumentException("Unknown machine adapters: " + ids + "; known=" + known);
        return REGISTERED.stream().filter(adapter -> ids.contains(adapter.id())).toList();
    }

    static PlaythroughMachineAdapter find(QuestCoverageCompatGameTests.Recipe recipe) {
        return ENABLED.stream().filter(adapter -> adapter.accepts(recipe)).findFirst().orElse(null);
    }

    static PlaythroughMachineAdapter byId(String id) {
        return ENABLED.stream().filter(adapter -> adapter.id().equals(id)).findFirst().orElseThrow();
    }
}
