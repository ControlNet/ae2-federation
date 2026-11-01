package space.controlnet.ae2federation.crafting.projection;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.stacks.AEKey;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.compat.adapters.CompatAdapters;

/**
 * Read-only CPU lifecycle compatibility. Native internals are pinned to AE2 19.2.17; add-on CPUs are read through their
 * {@link AddonCraftingCpus} adapters in {@code compat/<mod id>}. Only block-entity anchors are recognized. No job is
 * scheduled here.
 */
public final class CraftingCpuLifecycle {
    record Observation(@Nullable ICraftingLink link, ServerLevel level, BlockPos position, boolean nativeCpu) {}

    private static final ClassValue<AddonCraftingCpus.Cpu> CPUS = new ClassValue<>() {
        @Override
        protected AddonCraftingCpus.Cpu computeValue(Class<?> type) {
            for (var addon : CompatAdapters.CRAFTING_CPUS) {
                var cpu = addon.cpu(type);
                if (cpu != null) return cpu;
            }
            return null;
        }
    };

    private static final ClassValue<AddonCraftingCpus.Anchor> ANCHORS = new ClassValue<>() {
        @Override
        protected AddonCraftingCpus.Anchor computeValue(Class<?> type) {
            for (var addon : CompatAdapters.CRAFTING_CPUS) {
                var anchor = addon.anchor(type);
                if (anchor != null) return anchor;
            }
            return null;
        }
    };

    private CraftingCpuLifecycle() {}

    static @Nullable Observation observe(ICraftingCPU cpu) {
        if (cpu instanceof CraftingCPUCluster nativeCpu) {
            var link = nativeCpu.craftingLogic.getLastLink();
            var level = nativeCpu.getLevel();
            return link != null && level instanceof ServerLevel serverLevel
                    ? new Observation(link, serverLevel, nativeCpu.getBoundsMin(), true) : null;
        }
        var access = CPUS.get(cpu.getClass());
        if (access == null) return null;
        try {
            if (!(access.owner(cpu) instanceof BlockEntity block) || !(block.getLevel() instanceof ServerLevel level)) {
                return null;
            }
            var link = access.lastLink(cpu);
            return new Observation(link instanceof ICraftingLink nativeLink ? nativeLink : null,
                    level, block.getBlockPos(), false);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return null;
        }
    }

    /** The link of the job the CPU runs now, which names that one job; {@code null} when idle or it cannot say. */
    public static @Nullable ICraftingLink jobLink(ICraftingCPU cpu) {
        if (cpu instanceof CraftingCPUCluster nativeCpu) return nativeCpu.craftingLogic.getLastLink();
        var access = CPUS.get(cpu.getClass());
        if (access == null) return null;
        try {
            return access.lastLink(cpu) instanceof ICraftingLink link ? link : null;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return null;
        }
    }

    static long waiting(ICraftingCPU cpu, AEKey key) {
        if (cpu instanceof CraftingCPUCluster nativeCpu) return nativeCpu.craftingLogic.getWaitingFor(key);
        var access = CPUS.get(cpu.getClass());
        if (access == null) return -1;
        try {
            return access.waitingFor(cpu, key);
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
        var access = CPUS.get(cpu.getClass());
        if (access == null) return -1;
        try {
            var pending = access.pendingOutputs(cpu, key);
            return pending < 0 ? -1 : access.waitingFor(cpu, key) + pending;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return -1;
        }
    }

    /** Native addon evidence for jobs whose CPU retired; never infer termination from Grid CPU-list absence. */
    static boolean terminated(BlockEntity anchor, java.util.UUID craftingId) {
        var access = ANCHORS.get(anchor.getClass());
        if (access == null) return false;
        try {
            return access.terminated(anchor, craftingId);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
    }
}
