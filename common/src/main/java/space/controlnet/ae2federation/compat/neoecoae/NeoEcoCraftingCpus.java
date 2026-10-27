package space.controlnet.ae2federation.compat.neoecoae;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.compat.adapters.CraftingLogicCpu;
import space.controlnet.ae2federation.crafting.projection.AddonCraftingCpus;

/**
 * Neo ECO's crafting CPUs, through its public concrete API as of 21.2.1: getOwner/getLogic/getLastLink/getWaitingFor.
 * Terminal evidence uses its persisted isTerminated API, guarded by isFormed and its private deferredInit array: any
 * deferred or quarantined slot suppresses the terminal inference.
 */
public final class NeoEcoCraftingCpus implements AddonCraftingCpus {
    private static final String CPU = "cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU";
    private static final String HOST =
            "cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationThreadingCoreBlockEntity";
    private static final String JOB_LIFECYCLE = "cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle";

    @Override
    public @Nullable Cpu cpu(Class<?> type) {
        if (!type.getName().equals(CPU)) return null;
        try {
            var logic = type.getMethod("getLogic");
            var owner = type.getMethod("getOwner");
            return CraftingLogicCpu.of(owner::invoke, logic::invoke, logic.getReturnType());
        } catch (ReflectiveOperationException unavailable) {
            return null;
        }
    }

    @Override
    public @Nullable Anchor anchor(Class<?> type) {
        if (!type.getName().equals(HOST)) return null;
        try {
            var deferred = type.getDeclaredField("deferredInit");
            if (!deferred.trySetAccessible()) return null;
            var terminal = Class.forName(JOB_LIFECYCLE, false, type.getClassLoader())
                    .getMethod("isTerminated", Level.class, UUID.class);
            return new ThreadingCore(type.getMethod("isFormed"), deferred, terminal);
        } catch (ReflectiveOperationException unavailable) {
            return null;
        }
    }

    private record ThreadingCore(Method formed, Field deferred, Method terminal) implements Anchor {
        @Override
        public boolean terminated(BlockEntity anchor, UUID craftingId) throws ReflectiveOperationException {
            if (!Boolean.TRUE.equals(formed.invoke(anchor))) return false;
            // Neo ECO can clear a cancellation marker when restoring a deferred CPU. Do not treat that temporary
            // marker as final while any slot still holds deferred state (including quarantined failed restores).
            for (var pending : (Object[]) deferred.get(anchor)) if (pending != null) return false;
            return Boolean.TRUE.equals(terminal.invoke(null, anchor.getLevel(), craftingId));
        }
    }
}
