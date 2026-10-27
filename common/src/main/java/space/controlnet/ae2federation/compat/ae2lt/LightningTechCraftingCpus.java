package space.controlnet.ae2federation.compat.ae2lt;

import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.compat.adapters.CraftingLogicCpu;
import space.controlnet.ae2federation.crafting.projection.AddonCraftingCpus;

/**
 * Lightning Tech 2.1.1's crafting CPUs: {@code TimeWheelCraftingCPU} uses getHost/getCraftingLogic. Its loaded active
 * Pigmee owner proves a job ended once its full native CPU pool has no persistent state, including pending-loaded jobs.
 */
public final class LightningTechCraftingCpus implements AddonCraftingCpus {
    private static final String CPU = "com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU";
    private static final String HOST = "com.moakiee.ae2lt.blockentity.PigmeeMentalmathUnitBlockEntity";

    @Override
    public @Nullable Cpu cpu(Class<?> type) {
        if (!type.getName().equals(CPU)) return null;
        try {
            var logic = type.getMethod("getCraftingLogic");
            var owner = type.getMethod("getHost");
            return CraftingLogicCpu.of(owner::invoke, logic::invoke, logic.getReturnType());
        } catch (ReflectiveOperationException unavailable) {
            return null;
        }
    }

    @Override
    public @Nullable Anchor anchor(Class<?> type) {
        if (!type.getName().equals(HOST)) return null;
        try {
            var pool = type.getMethod("getTimeWheelCraftingCpuPool");
            // The full pool map includes pending-loaded CPUs and removes a CPU only after all of its persistent state
            // is drained. No state in an active loaded owner proves old jobs ended.
            return new DrainedPool(type.getMethod("isCpuActive"), pool,
                    pool.getReturnType().getMethod("hasPersistentState"));
        } catch (ReflectiveOperationException unavailable) {
            return null;
        }
    }

    private record DrainedPool(Method active, Method pool, Method persistent) implements Anchor {
        @Override
        public boolean terminated(BlockEntity anchor, UUID craftingId) throws ReflectiveOperationException {
            return Boolean.TRUE.equals(active.invoke(anchor))
                    && Boolean.FALSE.equals(persistent.invoke(pool.invoke(anchor)));
        }
    }
}
