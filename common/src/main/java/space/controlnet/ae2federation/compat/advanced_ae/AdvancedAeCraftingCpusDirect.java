package space.controlnet.ae2federation.compat.advanced_ae;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEKey;
import java.lang.reflect.Field;
import java.util.Map;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPUCluster;
import net.pedroksl.advanced_ae.common.entities.AdvCraftingBlockEntity;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.crafting.projection.AddonCraftingCpus;

/** {@link AdvancedAeCraftingCpus} through Advanced AE's own types; loaded only after one of its classes was seen. */
final class AdvancedAeCraftingCpusDirect {
    private AdvancedAeCraftingCpusDirect() {
    }

    static AddonCraftingCpus.Cpu cpu() {
        return new AddonCraftingCpus.Cpu() {
            @Override
            public @Nullable Object owner(ICraftingCPU cpu) {
                return ((AdvCraftingCPU) cpu).getSrc().machine().orElse(null);
            }

            @Override
            public Object lastLink(ICraftingCPU cpu) {
                return ((AdvCraftingCPU) cpu).craftingLogic.getLastLink();
            }

            @Override
            public long waitingFor(ICraftingCPU cpu, AEKey key) {
                return ((AdvCraftingCPU) cpu).craftingLogic.getWaitingFor(key);
            }
        };
    }

    /** Null when the private activeCpus map cannot be read: without it no job is proven ended. */
    static AddonCraftingCpus.@Nullable Anchor anchor() {
        Field cpus;
        try {
            // Private: read by reflection, the one part of the cluster with no accessor that leaves it unchanged.
            cpus = AdvCraftingCPUCluster.class.getDeclaredField("activeCpus");
        } catch (NoSuchFieldException otherVersion) {
            return null;
        }
        if (!cpus.trySetAccessible()) return null;
        return (anchor, craftingId) -> {
            var host = (AdvCraftingBlockEntity) anchor;
            if (host.isRemoved() || !host.isActive()) return false;
            var cluster = host.getCluster();
            if (cluster == null || cluster.isDestroyed()) return false;
            var all = cluster.getBlockEntities();
            boolean foundAnchor = false;
            while (all.hasNext()) {
                var member = all.next();
                if (member == null || member.isRemoved() || member.getLevel() != anchor.getLevel()
                        || !member.getLevel().isLoaded(member.getBlockPos())
                        || member.getPreviousState() != null) return false;
                foundAnchor |= member == anchor;
            }
            // getActiveCPUs mutates the native cluster (pruning/killing slots); inspect the complete backing map
            // instead. Native restore inserts each slot before loading its CPU state, then clears pending NBT.
            return foundAnchor && ((Map<?, ?>) cpus.get(cluster)).isEmpty();
        };
    }
}
