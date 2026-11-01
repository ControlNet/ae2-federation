package space.controlnet.ae2federation.compat.adapters;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEKey;
import java.lang.reflect.Method;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.crafting.projection.AddonCraftingCpus;

/**
 * An add-on CPU whose crafting logic, as AE2's own, has {@code getLastLink()} and {@code getWaitingFor(AEKey)}, and
 * {@code getPendingOutputs(AEKey)} where the add-on keeps it: the owner and the logic are read from the CPU, the link
 * and the amounts from the logic.
 */
public record CraftingLogicCpu(Reader owner, Reader logic, Method link, Method waiting, @Nullable Method pending)
        implements AddonCraftingCpus.Cpu {
    @FunctionalInterface
    public interface Reader {
        Object read(Object target) throws ReflectiveOperationException;
    }

    /** The CPU whose logic, of {@code logicType}, {@code logic} reads. */
    public static CraftingLogicCpu of(Reader owner, Reader logic, Class<?> logicType) throws NoSuchMethodException {
        Method pending;
        try {
            pending = logicType.getMethod("getPendingOutputs", AEKey.class);
        } catch (NoSuchMethodException absent) {
            pending = null;
        }
        return new CraftingLogicCpu(owner, logic, logicType.getMethod("getLastLink"),
                logicType.getMethod("getWaitingFor", AEKey.class), pending);
    }

    @Override
    public Object owner(ICraftingCPU cpu) throws ReflectiveOperationException {
        return owner.read(cpu);
    }

    @Override
    public Object lastLink(ICraftingCPU cpu) throws ReflectiveOperationException {
        return link.invoke(logic.read(cpu));
    }

    @Override
    public long waitingFor(ICraftingCPU cpu, AEKey key) throws ReflectiveOperationException {
        return ((Number) waiting.invoke(logic.read(cpu), key)).longValue();
    }

    @Override
    public long pendingOutputs(ICraftingCPU cpu, AEKey key) throws ReflectiveOperationException {
        return pending == null ? -1 : ((Number) pending.invoke(logic.read(cpu), key)).longValue();
    }
}
