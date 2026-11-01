package space.controlnet.ae2federation.compat.advanced_ae;

import appeng.api.networking.security.IActionSource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.compat.adapters.CraftingLogicCpu;
import space.controlnet.ae2federation.crafting.projection.AddonCraftingCpus;

/**
 * Advanced AE 1.6.12's crafting CPUs: {@code AdvCraftingCPU} exposes getSrc and the public craftingLogic field. Its
 * active formed host proves a job ended when the private cluster activeCpus map is empty and every loaded member's
 * previousState is null. This avoids its mutating getActiveCPUs accessor and refuses deferred restores.
 */
public final class AdvancedAeCraftingCpus implements AddonCraftingCpus {
    private static final String CPU = "net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU";
    private static final String HOST = "net.pedroksl.advanced_ae.common.entities.AdvCraftingBlockEntity";

    @Override
    public @Nullable Cpu cpu(Class<?> type) {
        if (!type.getName().equals(CPU)) return null;
        try {
            var source = type.getMethod("getSrc");
            var logic = type.getField("craftingLogic");
            return CraftingLogicCpu.of(cpu -> ((IActionSource) source.invoke(cpu)).machine().orElse(null),
                    logic::get, logic.getType());
        } catch (ReflectiveOperationException unavailable) {
            return null;
        }
    }

    @Override
    public @Nullable Anchor anchor(Class<?> type) {
        if (!type.getName().equals(HOST)) return null;
        try {
            var cluster = type.getMethod("getCluster");
            var clusterType = cluster.getReturnType();
            var cpus = clusterType.getDeclaredField("activeCpus");
            if (!cpus.trySetAccessible()) return null;
            return new Host(type.getMethod("isActive"), cluster, clusterType.getMethod("isDestroyed"),
                    clusterType.getMethod("getBlockEntities"), type.getMethod("getPreviousState"), cpus);
        } catch (ReflectiveOperationException unavailable) {
            return null;
        }
    }

    private record Host(Method active, Method cluster, Method destroyed, Method members, Method pending, Field cpus)
            implements Anchor {
        @Override
        public boolean terminated(BlockEntity anchor, UUID craftingId) throws ReflectiveOperationException {
            if (anchor.isRemoved() || !Boolean.TRUE.equals(active.invoke(anchor))) return false;
            var host = cluster.invoke(anchor);
            if (host == null || Boolean.TRUE.equals(destroyed.invoke(host))) return false;
            var all = (Iterator<?>) members.invoke(host);
            boolean foundAnchor = false;
            while (all.hasNext()) {
                var member = all.next();
                if (!(member instanceof BlockEntity block) || block.isRemoved()
                        || block.getLevel() != anchor.getLevel()
                        || !block.getLevel().isLoaded(block.getBlockPos())
                        || pending.invoke(member) != null) return false;
                foundAnchor |= member == anchor;
            }
            // getActiveCPUs mutates the native cluster (pruning/killing slots); inspect the complete backing map
            // instead. Native restore inserts each slot before loading its CPU state, then clears pending NBT.
            return foundAnchor && ((Map<?, ?>) cpus.get(host)).isEmpty();
        }
    }
}
