package space.controlnet.ae2federation.energy;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.me.energy.IEnergyOverlayGridConnection;
import appeng.me.service.EnergyService;
import java.util.Collection;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * A Federation node's link into AE2's shared energy pool, the way a Quartz Fiber links its two Grids. AE2 asks each
 * Grid's links for the energy services they reach and merges everything reachable into one pool
 * ({@code EnergyOverlayGrid}), which every member then draws from and charges directly. This link reaches the Grids
 * that {@link EnergySharingService} shares its own Grid's energy with.
 */
public final class FederationEnergyConnection implements IEnergyOverlayGridConnection {
    /**
     * Runtime classes of the objects that own a node carrying a link. AE2 indexes a Grid's nodes by exactly this class
     * ({@link appeng.api.networking.IGrid#getMachineNodes}), so a Grid's links are found without visiting every node.
     */
    private static final java.util.Set<Class<?>> NODE_OWNER_CLASSES =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private @Nullable IManagedGridNode owner;

    /** Binds this link to {@code node}, which {@code nodeOwner} created as its own. */
    public void bind(Object nodeOwner, IManagedGridNode node) {
        if (this.owner != null) {
            throw new IllegalStateException("Federation energy connection is already bound");
        }
        NODE_OWNER_CLASSES.add(nodeOwner.getClass());
        this.owner = java.util.Objects.requireNonNull(node);
    }

    static Iterable<Class<?>> nodeOwnerClasses() {
        return NODE_OWNER_CLASSES;
    }

    @Override
    public Collection<EnergyService> connectedEnergyServices() {
        var node = node();
        if (node == null || !(node.getLevel() instanceof ServerLevel level)) {
            return List.of();
        }
        var service = EnergySharingService.find(level);
        return service == null ? List.of() : service.peers(node.getGrid());
    }

    /**
     * The energy services this link reached when sharing was last reconciled. Reading it never reconciles, so a view
     * that describes a pool cannot move sharing forward outside the energy operations AE2 runs.
     */
    public Collection<EnergyService> listedEnergyServices() {
        var node = node();
        if (node == null || !(node.getLevel() instanceof ServerLevel level)) {
            return List.of();
        }
        var service = EnergySharingService.find(level);
        return service == null ? List.of() : service.listedPeers(node.getGrid());
    }

    @Nullable
    IGridNode node() {
        return owner == null ? null : owner.getNode();
    }
}
