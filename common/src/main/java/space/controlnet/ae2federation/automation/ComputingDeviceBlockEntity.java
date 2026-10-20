package space.controlnet.ae2federation.automation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.domain.FederationDomainNodeKind;

/**
 * Creative Computing Device: runs the functions installed on it, for the domain it is in. A PLACEHOLDER the user
 * asked for to test automation (2026-10-18), with unlimited compute; the survival device and its costs come later.
 * It links ME Federation Cable on all six faces. Its installations are kept in {@link ComputingDevices}.
 */
public final class ComputingDeviceBlockEntity extends DomainDeviceBlockEntity implements ComputingHost {
    /** Whether a redstone signal reached it at the last update, so only a rising edge triggers; kept across loads. */
    private boolean powered;

    public ComputingDeviceBlockEntity(BlockPos position, BlockState state) {
        super(AutomationRegistration.DEVICE_BLOCK_ENTITY.get(), position, state);
    }

    @Override
    public FederationDomainNodeKind kind() {
        return FederationDomainNodeKind.COMPUTING_DEVICE;
    }

    @Override
    public Capacity capacity() {
        return Capacity.UNLIMITED;
    }

    @Override
    public String claim(ServerLevel level, @Nullable String stored) {
        return ComputingDevices.get(level).claim(stored, level, worldPosition);
    }

    @Override
    public void release(ServerLevel level, String id) {
        ProgramScheduler.get(level.getServer()).deviceGone(id);
        ComputingDevices.get(level).release(id);
    }

    @Override
    public String alias(ServerLevel level) {
        return id() == null ? "" : ComputingDevices.get(level).alias(id());
    }

    @Override
    public AutomationInterfaceBook.AliasResult setAlias(ServerLevel level, String alias) {
        return id() == null ? AutomationInterfaceBook.AliasResult.INVALID
                : ComputingDevices.get(level).setAlias(level, id(), alias);
    }

    /** A neighbour changed: starts the redstone-triggered installations when a signal arrives. */
    public void redstoneChanged() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        var now = serverLevel.hasNeighborSignal(worldPosition);
        if (now == powered) {
            return;
        }
        powered = now;
        setChanged();
        if (now && id() != null) {
            ProgramScheduler.get(serverLevel.getServer()).redstone(id());
        }
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        powered = tag.getBoolean("powered");
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (powered) {
            tag.putBoolean("powered", true);
        }
    }
}
