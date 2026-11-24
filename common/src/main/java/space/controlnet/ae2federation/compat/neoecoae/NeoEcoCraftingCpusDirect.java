package space.controlnet.ae2federation.compat.neoecoae;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationThreadingCoreBlockEntity;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle;
import java.lang.reflect.Field;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.crafting.projection.AddonCraftingCpus;

/** {@link NeoEcoCraftingCpus} through Neo ECO's own types; loaded only after one of its classes was seen. */
final class NeoEcoCraftingCpusDirect {
    private NeoEcoCraftingCpusDirect() {
    }

    static AddonCraftingCpus.Cpu cpu() {
        return new AddonCraftingCpus.Cpu() {
            @Override
            public Object owner(ICraftingCPU cpu) {
                return ((ECOCraftingCPU) cpu).getOwner();
            }

            @Override
            public Object lastLink(ICraftingCPU cpu) {
                return ((ECOCraftingCPU) cpu).getLogic().getLastLink();
            }

            @Override
            public long waitingFor(ICraftingCPU cpu, AEKey key) {
                return ((ECOCraftingCPU) cpu).getLogic().getWaitingFor(key);
            }
        };
    }

    /** Null when the private deferredInit array cannot be read: without it no job is proven ended. */
    static AddonCraftingCpus.@Nullable Anchor anchor() {
        Field deferred;
        try {
            // Private: read by reflection, the one part of Neo ECO's CPUs with no public accessor.
            deferred = ECOComputationThreadingCoreBlockEntity.class.getDeclaredField("deferredInit");
        } catch (NoSuchFieldException otherVersion) {
            return null;
        }
        if (!deferred.trySetAccessible()) return null;
        return (anchor, craftingId) -> {
            var core = (ECOComputationThreadingCoreBlockEntity) anchor;
            if (!core.isFormed()) return false;
            // Neo ECO can clear a cancellation marker when restoring a deferred CPU. Do not treat that temporary
            // marker as final while any slot still holds deferred state (including quarantined failed restores).
            for (var pending : (Object[]) deferred.get(core)) if (pending != null) return false;
            return ECOCraftingJobLifecycle.isTerminated(core.getLevel(), craftingId);
        };
    }
}
