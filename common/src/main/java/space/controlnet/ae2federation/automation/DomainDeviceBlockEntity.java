package space.controlnet.ae2federation.automation;

import appeng.blockentity.AEBaseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.domain.port.FederationPort;
import space.controlnet.ae2federation.router.CableLinkedBlock;

/**
 * A block of the automation system that is a device node of its Federation domain, as an Endpoint is; the block
 * entity forwards its lifecycle to its {@link DomainDevice}.
 */
public abstract class DomainDeviceBlockEntity extends AEBaseBlockEntity implements AutomationDevice, DomainDevice.Host {
    private final DomainDevice device = new DomainDevice(this, this);

    protected DomainDeviceBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state) {
        super(type, position, state);
    }

    /** Whether the face links ME Federation Cable, as the block says ({@link CableLinkedBlock}). */
    protected final boolean links(Direction face) {
        return device.links(face);
    }

    @Override
    public @Nullable String id() {
        return device.id();
    }

    public @Nullable FederationPort federationPort(@Nullable Direction face) {
        return device.federationPort(face);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        device.clearRemoved();
        scheduleInit();
    }

    @Override
    public void onReady() {
        super.onReady();
        device.onReady();
    }

    /** Opens a cable port on every linking face; again when the faces that link change. */
    protected void openPorts() {
        device.openPorts();
    }

    public static void serverTick(Level level, BlockPos position, BlockState state, DomainDeviceBlockEntity entity) {
        entity.device.tick();
    }

    public void neighborChanged(BlockPos neighborPosition) {
        device.neighborChanged(neighborPosition);
    }

    @Override
    public void onChunkUnloaded() {
        device.onChunkUnloaded();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        device.setRemoved();
        super.setRemoved();
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        device.load(tag);
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        device.save(tag);
    }
}
