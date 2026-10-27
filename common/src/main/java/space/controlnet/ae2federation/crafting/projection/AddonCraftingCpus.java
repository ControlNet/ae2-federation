package space.controlnet.ae2federation.crafting.projection;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEKey;
import java.util.UUID;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * What an add-on's own crafting CPUs tell about their jobs, read for {@link CraftingCpuLifecycle} and never changed. An
 * add-on's adapter recognises its classes by name, so it stays inert without the add-on; a class it does not know, or
 * whose API changed, gets null, and the job is then watched as for an unknown CPU.
 */
public interface AddonCraftingCpus {
    /** How to read a CPU of {@code type}; null for another type. */
    @Nullable Cpu cpu(Class<?> type);

    /** How to tell that a job anchored at a block entity of {@code type} ended; null for another type. */
    @Nullable Anchor anchor(Class<?> type);

    interface Cpu {
        /** The block that hosts {@code cpu}. */
        @Nullable Object owner(ICraftingCPU cpu) throws ReflectiveOperationException;

        /** The link of {@code cpu}'s current job. */
        @Nullable Object lastLink(ICraftingCPU cpu) throws ReflectiveOperationException;

        /** How much of {@code key} {@code cpu}'s job still waits for. */
        long waitingFor(ICraftingCPU cpu, AEKey key) throws ReflectiveOperationException;
    }

    interface Anchor {
        /**
         * Whether the add-on's own state proves that job {@code craftingId}, whose CPU {@code anchor} hosted, ended;
         * false when it cannot.
         */
        boolean terminated(BlockEntity anchor, UUID craftingId) throws ReflectiveOperationException;
    }
}
