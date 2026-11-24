package space.controlnet.ae2federation.compat.ae2lt;

import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.crafting.projection.AddonCraftingCpus;

/**
 * Lightning Tech 2.1.3's crafting CPUs: {@code TimeWheelCraftingCPU} uses getHost/getCraftingLogic. Its loaded active
 * Pigmee owner proves a job ended once its full native CPU pool has no persistent state, including pending-loaded jobs.
 * This class only names Lightning Tech's classes; {@link LightningTechCraftingCpusDirect}, which calls them, is loaded
 * once one of them was seen, and a Lightning Tech whose API differs from the one it was built against gets null.
 */
public final class LightningTechCraftingCpus implements AddonCraftingCpus {
    private static final String CPU = "com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU";
    private static final String HOST = "com.moakiee.ae2lt.blockentity.PigmeeMentalmathUnitBlockEntity";

    @Override
    public @Nullable Cpu cpu(Class<?> type) {
        if (!type.getName().equals(CPU)) return null;
        try {
            return LightningTechCraftingCpusDirect.cpu();
        } catch (LinkageError otherApi) {
            return null;
        }
    }

    @Override
    public @Nullable Anchor anchor(Class<?> type) {
        if (!type.getName().equals(HOST)) return null;
        try {
            return LightningTechCraftingCpusDirect.anchor();
        } catch (LinkageError otherApi) {
            return null;
        }
    }
}
