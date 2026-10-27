package space.controlnet.ae2federation.compat.adapters;

import java.util.List;
import space.controlnet.ae2federation.ae2.processing.AddonLaneTargets;
import space.controlnet.ae2federation.compat.advanced_ae.AdvancedAeCraftingCpus;
import space.controlnet.ae2federation.compat.advanced_ae.AdvancedAeProjections;
import space.controlnet.ae2federation.compat.ae2lt.LightningTechCraftingCpus;
import space.controlnet.ae2federation.compat.expandedae.ExpandedAeLaneTargets;
import space.controlnet.ae2federation.compat.neoecoae.NeoEcoBatching;
import space.controlnet.ae2federation.compat.neoecoae.NeoEcoCraftingCpus;
import space.controlnet.ae2federation.crafting.projection.AddonCraftingCpus;
import space.controlnet.ae2federation.crafting.projection.AddonProjections;

/**
 * The adapters the rest of the mod asks about add-ons, one package per add-on named by its mod id. Each recognises its
 * add-on's classes by name, so it stays inert without the add-on, and what it does not recognise falls back to what the
 * mod does for any add-on. Code for one add-on lives only under {@code compat/}.
 */
public final class CompatAdapters {
    public static final List<AddonCraftingCpus> CRAFTING_CPUS = List.of(new AdvancedAeCraftingCpus(),
            new LightningTechCraftingCpus(), new NeoEcoCraftingCpus());
    public static final List<AddonProjections> PROJECTIONS = List.of(new AdvancedAeProjections(), new NeoEcoBatching());
    public static final List<AddonLaneTargets> LANE_TARGETS = List.of(new ExpandedAeLaneTargets());

    private CompatAdapters() {
    }
}
