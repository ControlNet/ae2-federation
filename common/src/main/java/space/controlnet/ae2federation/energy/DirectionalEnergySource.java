package space.controlnet.ae2federation.energy;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.networking.events.GridPowerStorageStateChanged;
import org.jetbrains.annotations.Nullable;

public final class DirectionalEnergySource implements IAEPowerStorage {
    /**
     * Runtime classes of the objects that own a node carrying a source. AE2 indexes a Grid's nodes by exactly this class
     * ({@link appeng.api.networking.IGrid#getMachineNodes}), so a Grid's sources are found without visiting every node.
     */
    private static final java.util.Set<Class<?>> NODE_OWNER_CLASSES =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private @Nullable IManagedGridNode owner;
    /**
     * This source's bindings in provider-network order as {@link EnergyBindingService} last listed them, with that
     * service and its candidate revision; the service lists them again once either differs.
     */
    @Nullable Object candidatesOwner;
    long candidatesRevision;
    @Nullable java.util.List<EnergyCapabilityBinding> candidates;

    /** Binds this source to {@code node}, which {@code nodeOwner} created as its own. */
    public void bind(Object nodeOwner, IManagedGridNode node) {
        if (this.owner != null) {
            throw new IllegalStateException("Directional energy source is already bound");
        }
        NODE_OWNER_CLASSES.add(nodeOwner.getClass());
        this.owner = java.util.Objects.requireNonNull(node);
    }

    static Iterable<Class<?>> nodeOwnerClasses() {
        return NODE_OWNER_CLASSES;
    }

    @Override
    public double injectAEPower(double amount, Actionable mode) {
        if (!Double.isFinite(amount) || amount < 0) {
            throw new IllegalArgumentException("Energy amount must be finite and nonnegative");
        }
        return amount;
    }

    @Override
    public double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) {
        if (!Double.isFinite(amount) || amount < 0) {
            throw new IllegalArgumentException("Energy amount must be finite and nonnegative");
        }
        var internalAmount = multiplier.multiply(amount);
        if (!Double.isFinite(internalAmount)) {
            throw new IllegalArgumentException("Converted energy amount must be finite");
        }
        var node = node();
        if (node == null || !(node.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
            return 0;
        }
        var service = EnergyBindingService.find(level);
        return service == null ? 0 : multiplier.divide(service.extract(this, node.getGrid(), internalAmount, mode));
    }

    @Override
    public boolean isAEPublicPowerStorage() {
        return true;
    }

    @Override
    public AccessRestriction getPowerFlow() {
        return AccessRestriction.READ;
    }

    @Override
    public double getAEMaxPower() {
        return 0;
    }

    @Override
    public double getAECurrentPower() {
        return extractAEPower(Double.MAX_VALUE, Actionable.SIMULATE, PowerMultiplier.ONE);
    }

    @Override
    public int getPriority() {
        return -1_000_000;
    }

    void announceAvailability() {
        var node = node();
        if (node != null) {
            node.getGrid().postEvent(new GridPowerStorageStateChanged(this,
                    GridPowerStorageStateChanged.PowerEventType.PROVIDE_POWER));
        }
    }

    @Nullable
    IGridNode node() {
        return owner == null ? null : owner.getNode();
    }
}
