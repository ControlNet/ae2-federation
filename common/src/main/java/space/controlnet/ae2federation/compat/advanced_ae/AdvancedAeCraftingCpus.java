package space.controlnet.ae2federation.compat.advanced_ae;

import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.crafting.projection.AddonCraftingCpus;

/**
 * Advanced AE 1.6.12's crafting CPUs: {@code AdvCraftingCPU} exposes getSrc and the public craftingLogic field. Its
 * active formed host proves a job ended when the private cluster activeCpus map is empty and every loaded member's
 * previousState is null. This avoids its mutating getActiveCPUs accessor and refuses deferred restores. This class
 * only names Advanced AE's classes; {@link AdvancedAeCraftingCpusDirect}, which calls them, is loaded once one of them
 * was seen, and an Advanced AE whose API differs from the one it was built against gets null.
 */
public final class AdvancedAeCraftingCpus implements AddonCraftingCpus {
    private static final String CPU = "net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU";
    private static final String HOST = "net.pedroksl.advanced_ae.common.entities.AdvCraftingBlockEntity";

    @Override
    public @Nullable Cpu cpu(Class<?> type) {
        if (!type.getName().equals(CPU)) return null;
        try {
            return AdvancedAeCraftingCpusDirect.cpu();
        } catch (LinkageError otherApi) {
            return null;
        }
    }

    @Override
    public @Nullable Anchor anchor(Class<?> type) {
        if (!type.getName().equals(HOST)) return null;
        try {
            return AdvancedAeCraftingCpusDirect.anchor();
        } catch (LinkageError otherApi) {
            return null;
        }
    }
}
