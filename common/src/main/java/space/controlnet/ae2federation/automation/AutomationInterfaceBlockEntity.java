package space.controlnet.ae2federation.automation;

import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.domain.FederationDomainNodeKind;

/**
 * ME Federation Automation Interface: a place in the world where programs act, on the block in front of it. Its other
 * five faces link ME Federation Cable, through which it is a device node of its domain, as an Endpoint is. Its ID is
 * kept in {@link AutomationInterfaces}.
 */
public final class AutomationInterfaceBlockEntity extends DomainDeviceBlockEntity {
    /** The signal a program set, sent out of the front face; kept across restarts. */
    private int redstone;

    public AutomationInterfaceBlockEntity(BlockPos position, BlockState state) {
        super(AutomationRegistration.INTERFACE_BLOCK_ENTITY.get(), position, state);
    }

    @Override
    public FederationDomainNodeKind kind() {
        return FederationDomainNodeKind.AUTOMATION_INTERFACE;
    }

    @Override
    public String claim(ServerLevel level, @Nullable String stored) {
        return AutomationInterfaces.get(level).claim(stored, level, worldPosition);
    }

    @Override
    public void release(ServerLevel level, String id) {
        AutomationInterfaces.get(level).release(id);
    }

    @Override
    public String alias(ServerLevel level) {
        return id() == null ? "" : AutomationInterfaces.get(level).alias(id());
    }

    @Override
    public AutomationInterfaceBook.AliasResult setAlias(ServerLevel level, String alias) {
        return id() == null ? AutomationInterfaceBook.AliasResult.INVALID
                : AutomationInterfaces.get(level).setAlias(level, id(), alias);
    }

    public int redstone() {
        return redstone;
    }

    /** Sets the signal out of the front face; returns whether it changed. */
    public boolean setRedstone(int level) {
        var clamped = Math.max(0, Math.min(15, level));
        if (clamped == redstone) {
            return false;
        }
        redstone = clamped;
        setChanged();
        if (this.level != null) {
            this.level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
        return true;
    }

    public Direction front() {
        return getOrientation().getSide(RelativeSide.FRONT);
    }

    @Override
    protected void onOrientationChanged(BlockOrientation orientation) {
        super.onOrientationChanged(orientation);
        // The front moved: the face that acted now links, and the new front no longer does.
        openPorts();
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
            if (redstone > 0) {
                // The signal leaves through the new front.
                level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            }
        }
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        redstone = tag.getInt("redstone");
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (redstone > 0) {
            tag.putInt("redstone", redstone);
        }
    }
}
