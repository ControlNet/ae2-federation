package space.controlnet.ae2federation.crafting.projection;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.stacks.AEKey;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only CPU lifecycle compatibility. Native internals are pinned to AE2 19.2.17. Neo ECO's optional public
 * concrete API is verified against 21.2.1: getOwner/getLogic/getLastLink/getWaitingFor. Lightning Tech 2.1.1
 * TimeWheelCraftingCPU uses getHost/getCraftingLogic. Only block-entity anchors are recognized. No job is scheduled here.
 */
final class CraftingCpuLifecycle {
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
            var owner = access.owner().invoke(cpu);
            var logic = access.logic().invoke(cpu);
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
            return ((Number) access.waiting().invoke(access.logic().invoke(cpu), key)).longValue();
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return -1;
        }
    }

    private record Access(Method owner, Method logic, Method link, Method waiting) {}
    private static final ClassValue<Access> ACCESS = new ClassValue<>() {
        @Override
        protected Access computeValue(Class<?> type) {
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
                return new Access(type.getMethod(ownerMethod), logic,
                        logic.getReturnType().getMethod("getLastLink"),
                        logic.getReturnType().getMethod("getWaitingFor", AEKey.class));
            } catch (ReflectiveOperationException unavailable) {
                return null;
            }
        }
    };
}
