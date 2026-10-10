package space.controlnet.ae2federation.ae2.processing;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeService;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.ticking.IGridTickable;
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

/**
 * The node a native PatternProviderLogic sees inside a Federation Pattern Provider. It captures the logic's services
 * and forwards state queries to the provider's physical node, which its owner configures. AE2 lets any code configure a
 * logic's node, and addons that mix into PatternProviderLogic do, so configuration that cannot reach the physical node
 * is accepted and left out instead of failing.
 */
final class CapturedManagedGridNode implements IManagedGridNode {
    private final IManagedGridNode physicalNode;
    private final NativeProviderLaneServices services;
    private final boolean forwardsAddonServices;

    CapturedManagedGridNode(IManagedGridNode physicalNode, NativeProviderLaneServices services) {
        this(physicalNode, services, false);
    }

    private CapturedManagedGridNode(IManagedGridNode physicalNode, NativeProviderLaneServices services,
            boolean forwardsAddonServices) {
        this.physicalNode = physicalNode;
        this.services = services;
        this.forwardsAddonServices = forwardsAddonServices;
    }

    /**
     * The owner logic's node. The owner is the Provider's native face, so a service of another kind that an addon adds
     * to it, such as Applied Flux's energy distributor, goes to the physical node once, as on AE2's own Pattern
     * Provider. A Lane's node leaves such services out: every Lane shares the physical node.
     */
    static CapturedManagedGridNode owner(IManagedGridNode physicalNode, NativeProviderLaneServices services) {
        return new CapturedManagedGridNode(physicalNode, services, true);
    }

    @Override
    public <T extends IGridNodeService> IManagedGridNode addService(Class<T> serviceClass, T service) {
        services.capture(serviceClass, service);
        if (forwardsAddonServices && serviceClass != IGridTickable.class && serviceClass != ICraftingProvider.class
                && physicalNode.getNode() == null) {
            // AE2 accepts services only before the node exists; the owner logic is built before that.
            physicalNode.addService(serviceClass, service);
        }
        return this;
    }

    @Override
    public IManagedGridNode setFlags(GridFlags... flags) {
        if (physicalNode.getNode() == null) {
            physicalNode.setFlags(flags);
        }
        // A Lane added after the physical node exists cannot change its flags; the owner configured them first.
        return this;
    }

    @Override
    public void destroy() {
    }

    @Override
    public void create(Level level, @Nullable BlockPos blockPos) {
    }

    @Override
    public void loadFromNBT(CompoundTag nodeData) {
    }

    @Override
    public void saveToNBT(CompoundTag nodeData) {
    }

    @Override
    public IManagedGridNode setExposedOnSides(Set<Direction> directions) {
        return this;
    }

    @Override
    public IManagedGridNode setIdlePowerUsage(double usagePerTick) {
        return this;
    }

    @Override
    public IManagedGridNode setVisualRepresentation(@Nullable appeng.api.stacks.AEItemKey representation) {
        return this;
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
        return this;
    }

    @Override
    public IManagedGridNode setTagName(String tagName) {
        return this;
    }

    @Override
    public IManagedGridNode setGridColor(AEColor gridColor) {
        return this;
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
    }

    @Override
    public void setOwningPlayer(Player ownerPlayer) {
    }

    @Override
    public @Nullable IGridNode getNode() {
        return physicalNode.getNode();
    }
}
