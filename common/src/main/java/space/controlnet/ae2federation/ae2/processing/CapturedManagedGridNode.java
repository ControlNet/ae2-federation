package space.controlnet.ae2federation.ae2.processing;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeService;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.AEColor;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

final class CapturedManagedGridNode implements IManagedGridNode {
    private final IManagedGridNode physicalNode;
    private final NativeProviderLaneServices services;

    CapturedManagedGridNode(IManagedGridNode physicalNode, NativeProviderLaneServices services) {
        this.physicalNode = physicalNode;
        this.services = services;
    }

    @Override
    public <T extends IGridNodeService> IManagedGridNode addService(Class<T> serviceClass, T service) {
        services.capture(serviceClass, service);
        return this;
    }

    @Override
    public IManagedGridNode setFlags(GridFlags... flags) {
        var created = physicalNode.getNode();
        if (created == null) {
            physicalNode.setFlags(flags);
        } else {
            // A Lane added after the physical node exists cannot change its flags; the owner configures them first.
            for (var flag : flags) {
                if (!created.hasFlag(flag)) {
                    throw new IllegalStateException("Physical Provider node lacks native flag " + flag);
                }
            }
        }
        return this;
    }

    @Override
    public void destroy() {
        throw new UnsupportedOperationException("Lane facades do not own the physical node");
    }

    @Override
    public void create(Level level, @Nullable BlockPos blockPos) {
        throw new UnsupportedOperationException("Lane facades do not create the physical node");
    }

    @Override
    public void loadFromNBT(CompoundTag nodeData) {
        throw new UnsupportedOperationException("Lane facades do not persist the physical node");
    }

    @Override
    public void saveToNBT(CompoundTag nodeData) {
        throw new UnsupportedOperationException("Lane facades do not persist the physical node");
    }

    @Override
    public IManagedGridNode setExposedOnSides(Set<Direction> directions) {
        throw new UnsupportedOperationException("Lane facades do not configure the physical node");
    }

    @Override
    public IManagedGridNode setIdlePowerUsage(double usagePerTick) {
        throw new UnsupportedOperationException("Lane facades do not configure the physical node");
    }

    @Override
    public IManagedGridNode setVisualRepresentation(@Nullable appeng.api.stacks.AEItemKey representation) {
        throw new UnsupportedOperationException("Lane facades do not configure the physical node");
    }

    @Override
    public IManagedGridNode setVisualRepresentation(ItemStack representation) {
        return IManagedGridNode.super.setVisualRepresentation(representation);
    }

    @Override
    public IManagedGridNode setVisualRepresentation(ItemLike representation) {
        return IManagedGridNode.super.setVisualRepresentation(representation);
    }

    @Override
    public IManagedGridNode setInWorldNode(boolean accessible) {
        throw new UnsupportedOperationException("Lane facades do not configure the physical node");
    }

    @Override
    public IManagedGridNode setTagName(String tagName) {
        throw new UnsupportedOperationException("Lane facades do not persist the physical node");
    }

    @Override
    public IManagedGridNode setGridColor(AEColor gridColor) {
        throw new UnsupportedOperationException("Lane facades do not configure the physical node");
    }

    @Override
    public boolean isReady() {
        return physicalNode.isReady();
    }

    @Override
    public boolean isActive() {
        return physicalNode.isActive();
    }

    @Override
    public boolean isOnline() {
        return physicalNode.isOnline();
    }

    @Override
    public boolean isPowered() {
        return physicalNode.isPowered();
    }

    @Override
    public boolean hasGridBooted() {
        return physicalNode.hasGridBooted();
    }

    @Override
    public void setOwningPlayerId(int ownerPlayerId) {
        throw new UnsupportedOperationException("Lane facades do not own security state");
    }

    @Override
    public void setOwningPlayer(Player ownerPlayer) {
        throw new UnsupportedOperationException("Lane facades do not own security state");
    }

    @Override
    public @Nullable IGridNode getNode() {
        return physicalNode.getNode();
    }
}
