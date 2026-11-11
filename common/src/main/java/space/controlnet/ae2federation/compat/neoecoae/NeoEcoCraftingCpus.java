package space.controlnet.ae2federation.compat.neoecoae;

import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.crafting.projection.AddonCraftingCpus;

/**
 * Neo ECO's crafting CPUs: getOwner, getLogic, and the logic's getLastLink and getWaitingFor. Terminal evidence uses its
 * isTerminated, guarded by isFormed and its private deferredInit array: any deferred or quarantined slot suppresses the
 * terminal inference. This class only names Neo ECO's classes; {@link NeoEcoCraftingCpusDirect}, which calls them, is
 * loaded once one of them was seen, and a Neo ECO whose API differs from the one it was built against gets null.
 */
public final class NeoEcoCraftingCpus implements AddonCraftingCpus {
    private static final String CPU = "cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU";
    private static final String HOST =
            "cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationThreadingCoreBlockEntity";

    @Override
    public @Nullable Cpu cpu(Class<?> type) {
        if (!type.getName().equals(CPU)) return null;
        try {
            return NeoEcoCraftingCpusDirect.cpu();
        } catch (LinkageError otherApi) {
            return null;
        }
    }

    @Override
    public @Nullable Anchor anchor(Class<?> type) {
        if (!type.getName().equals(HOST)) return null;
        try {
            return NeoEcoCraftingCpusDirect.anchor();
        } catch (LinkageError otherApi) {
            return null;
        }
    }
}
