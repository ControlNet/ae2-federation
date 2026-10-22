package space.controlnet.ae2federation.crafting.projection;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.stacks.AEKey;
import appeng.api.networking.security.IActionSource;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only CPU lifecycle compatibility. Native internals are pinned to AE2 19.2.17. Neo ECO's optional public
 * concrete API is verified against 21.2.1: getOwner/getLogic/getLastLink/getWaitingFor. Lightning Tech 2.1.1
 * TimeWheelCraftingCPU uses getHost/getCraftingLogic. Advanced AE 1.6.12 exposes getSrc and the public craftingLogic
 * field. Neo ECO terminal evidence uses its persisted isTerminated API, guarded by isFormed and its private
 * deferredInit array (21.2.1): any deferred or quarantined slot suppresses the terminal inference. Only block-entity
 * anchors are recognized. Lightning Tech 2.1.1's loaded active Pigmee owner also proves termination once its full
 * native CPU pool has no persistent state, including pending-loaded jobs. Advanced AE 1.6.12's active formed host
 * requires the private cluster activeCpus map empty and every loaded member's previousState null. This avoids its
 * mutating getActiveCPUs accessor and refuses deferred restores. No job is scheduled here.
 */
public final class CraftingCpuLifecycle {
    record Observation(@Nullable ICraftingLink link, ServerLevel level, BlockPos position, boolean nativeCpu) {}

    private CraftingCpuLifecycle() {}

    static @Nullable Observation observe(ICraftingCPU cpu) {
        if (cpu instanceof CraftingCPUCluster nativeCpu) {
            var link = nativeCpu.craftingLogic.getLastLink();
            var level = nativeCpu.getLevel();
            return link != null && level instanceof ServerLevel serverLevel
                    ? new Observation(link, serverLevel, nativeCpu.getBoundsMin(), true) : null;
        }
        var access = ACCESS.get(cpu.getClass());
        if (access == null) return null;
        try {
            var owner = access.owner().read(cpu);
            var logic = access.logic().read(cpu);
            if (!(owner instanceof BlockEntity block) || !(block.getLevel() instanceof ServerLevel level)) return null;
            var link = access.link().invoke(logic);
            return new Observation(link instanceof ICraftingLink nativeLink ? nativeLink : null,
                    level, block.getBlockPos(), false);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return null;
        }
    }

    static long waiting(ICraftingCPU cpu, AEKey key) {
        if (cpu instanceof CraftingCPUCluster nativeCpu) return nativeCpu.craftingLogic.getWaitingFor(key);
        var access = ACCESS.get(cpu.getClass());
        if (access == null) return -1;
        try {
            return ((Number) access.waiting().invoke(access.logic().read(cpu), key)).longValue();
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return -1;
        }
    }

    /**
     * What the CPU's job will still produce of {@code key}: the outputs of pushed patterns it waits for plus those of
     * the patterns it has not pushed yet; -1 when the CPU cannot say, as an addon CPU without the second count.
     */
    public static long stillToMake(ICraftingCPU cpu, AEKey key) {
        if (cpu instanceof CraftingCPUCluster nativeCpu) {
            return nativeCpu.craftingLogic.getWaitingFor(key) + nativeCpu.craftingLogic.getPendingOutputs(key);
        }
        var access = ACCESS.get(cpu.getClass());
        if (access == null || access.pending() == null) return -1;
        try {
            var logic = access.logic().read(cpu);
            return ((Number) access.waiting().invoke(logic, key)).longValue()
                    + ((Number) access.pending().invoke(logic, key)).longValue();
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return -1;
        }
    }

    /** Native addon evidence for jobs whose CPU retired; never infer termination from Grid CPU-list absence. */
    static boolean terminated(BlockEntity anchor, java.util.UUID craftingId) {
        var advanced = ADVANCED_TERMINAL.get(anchor.getClass());
        if (advanced != null) {
            try {
                if (anchor.isRemoved() || !Boolean.TRUE.equals(advanced.active().invoke(anchor))) return false;
                var cluster = advanced.cluster().invoke(anchor);
                if (cluster == null || Boolean.TRUE.equals(advanced.destroyed().invoke(cluster))) return false;
                var members = (java.util.Iterator<?>) advanced.members().invoke(cluster);
                boolean foundAnchor = false;
                while (members.hasNext()) {
                    var member = members.next();
                    if (!(member instanceof BlockEntity block) || block.isRemoved()
                            || block.getLevel() != anchor.getLevel()
                            || !block.getLevel().isLoaded(block.getBlockPos())
                            || advanced.pending().invoke(member) != null) return false;
                    foundAnchor |= member == anchor;
                }
                // getActiveCPUs mutates the native cluster (pruning/killing slots); inspect the complete backing
                // map instead. Native restore inserts each slot before loading its CPU state, then clears pending NBT.
                return foundAnchor && ((java.util.Map<?, ?>) advanced.cpus().get(cluster)).isEmpty();
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                return false;
            }
        }
        var pool = DRAINED_POOL.get(anchor.getClass());
        if (pool != null) {
            try {
                return Boolean.TRUE.equals(pool.active().invoke(anchor))
                        && Boolean.FALSE.equals(pool.persistent().invoke(pool.pool().invoke(anchor)));
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                return false;
            }
        }
        var access = TERMINAL.get(anchor.getClass());
        if (access == null) return false;
        try {
            if (!Boolean.TRUE.equals(access.formed().invoke(anchor))) return false;
            // Neo ECO can clear a cancellation marker when restoring a deferred CPU. Do not treat that temporary
            // marker as final while any slot still holds deferred state (including quarantined failed restores).
            for (var pending : (Object[]) access.deferred().get(anchor)) if (pending != null) return false;
            return Boolean.TRUE.equals(access.terminal().invoke(null, anchor.getLevel(), craftingId));
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
    }

    private record AdvancedTerminalAccess(Method active, Method cluster, Method destroyed, Method members,
            Method pending, java.lang.reflect.Field cpus) {}

    private static final ClassValue<AdvancedTerminalAccess> ADVANCED_TERMINAL = new ClassValue<>() {
        @Override
        protected AdvancedTerminalAccess computeValue(Class<?> type) {
            if (!type.getName().equals("net.pedroksl.advanced_ae.common.entities.AdvCraftingBlockEntity")) return null;
            try {
                var cluster = type.getMethod("getCluster");
                var clusterType = cluster.getReturnType();
                var cpus = clusterType.getDeclaredField("activeCpus");
                if (!cpus.trySetAccessible()) return null;
                return new AdvancedTerminalAccess(type.getMethod("isActive"), cluster,
                        clusterType.getMethod("isDestroyed"), clusterType.getMethod("getBlockEntities"),
                        type.getMethod("getPreviousState"), cpus);
            } catch (ReflectiveOperationException unavailable) {
                return null;
            }
        }
    };

    private record PoolAccess(Method active, Method pool, Method persistent) {}

    private static final ClassValue<PoolAccess> DRAINED_POOL = new ClassValue<>() {
        @Override
        protected PoolAccess computeValue(Class<?> type) {
            if (!type.getName().equals("com.moakiee.ae2lt.blockentity.PigmeeMentalmathUnitBlockEntity")) return null;
            try {
                var pool = type.getMethod("getTimeWheelCraftingCpuPool");
                // Lightning Tech 2.1.1: the full pool map includes pending-loaded CPUs and removes a CPU only after
                // all of its persistent state is drained. No state in an active loaded owner proves old jobs ended.
                return new PoolAccess(type.getMethod("isCpuActive"), pool,
                        pool.getReturnType().getMethod("hasPersistentState"));
            } catch (ReflectiveOperationException unavailable) {
                return null;
            }
        }
    };

    private record TerminalAccess(Method formed, java.lang.reflect.Field deferred, Method terminal) {}

    private static final ClassValue<TerminalAccess> TERMINAL = new ClassValue<>() {
        @Override
        protected TerminalAccess computeValue(Class<?> type) {
            if (!type.getName().equals("cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationThreadingCoreBlockEntity")) {
                return null;
            }
            try {
                var deferred = type.getDeclaredField("deferredInit");
                if (!deferred.trySetAccessible()) return null;
                var terminal = Class.forName("cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle",
                        false, type.getClassLoader()).getMethod("isTerminated", net.minecraft.world.level.Level.class,
                                java.util.UUID.class);
                return new TerminalAccess(type.getMethod("isFormed"), deferred, terminal);
            } catch (ReflectiveOperationException unavailable) {
                return null;
            }
        }
    };

    @FunctionalInterface
    private interface Reader {
        Object read(Object target) throws ReflectiveOperationException;
    }

    private record Access(Reader owner, Reader logic, Method link, Method waiting, @Nullable Method pending) {}
    private static final ClassValue<Access> ACCESS = new ClassValue<>() {
        @Override
        protected Access computeValue(Class<?> type) {
            if (type.getName().equals("net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU")) {
                try {
                    var source = type.getMethod("getSrc");
                    var logic = type.getField("craftingLogic");
                    return new Access(cpu -> ((IActionSource) source.invoke(cpu)).machine().orElse(null),
                            logic::get, logic.getType().getMethod("getLastLink"),
                            logic.getType().getMethod("getWaitingFor", AEKey.class),
                            optionalMethod(logic.getType(), "getPendingOutputs"));
                } catch (ReflectiveOperationException unavailable) {
                    return null;
                }
            }
            String ownerMethod;
            String logicMethod;
            switch (type.getName()) {
                case "cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU" -> {
                    ownerMethod = "getOwner";
                    logicMethod = "getLogic";
                }
                case "com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU" -> {
                    ownerMethod = "getHost";
                    logicMethod = "getCraftingLogic";
                }
                default -> { return null; }
            }
            try {
                var logic = type.getMethod(logicMethod);
                var owner = type.getMethod(ownerMethod);
                return new Access(owner::invoke, logic::invoke,
                        logic.getReturnType().getMethod("getLastLink"),
                        logic.getReturnType().getMethod("getWaitingFor", AEKey.class),
                        optionalMethod(logic.getReturnType(), "getPendingOutputs"));
            } catch (ReflectiveOperationException unavailable) {
                return null;
            }
        }
    };

    private static @Nullable Method optionalMethod(Class<?> type, String name) {
        try {
            return type.getMethod(name, AEKey.class);
        } catch (NoSuchMethodException absent) {
            return null;
        }
    }
}
