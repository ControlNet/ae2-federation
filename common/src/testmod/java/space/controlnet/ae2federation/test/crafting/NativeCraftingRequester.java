package space.controlnet.ae2federation.test.crafting;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.helpers.MultiCraftingTracker;
import appeng.me.helpers.MachineSource;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;

public final class NativeCraftingRequester implements ICraftingRequester, AutoCloseable {
    private static final IGridNodeListener<NativeCraftingRequester> LISTENER = (owner, node) -> {
    };
    private final IManagedGridNode managedNode;
    private final MultiCraftingTracker tracker = new MultiCraftingTracker(this, 1);
    private final MEStorage destination;
    private final IActionSource actionSource = new MachineSource(this);
    private final Set<String> observedCraftingIds = new TreeSet<>();
    private ICraftingLink submittedLink;
    private long acceptedAmount;
    private int stateChanges;
    private boolean observedDone;
    private boolean observedCanceled;

    public NativeCraftingRequester(Level level, BlockPos position, MEStorage destination) {
        this(level, position, destination, null, null);
    }

    public NativeCraftingRequester(Level level, BlockPos position, MEStorage destination, NetworkId networkId,
            CompoundTag persistedState) {
        this.destination = destination;
        var inWorld = networkId == null;
        managedNode = GridHelper.createManagedNode(this, LISTENER)
                .setTagName("gn")
                .setInWorldNode(inWorld)
                .setIdlePowerUsage(0)
                .addService(ICraftingRequester.class, this);
        if (persistedState != null) {
            tracker.readFromNBT(persistedState);
            managedNode.loadFromNBT(persistedState);
        } else if (networkId != null) {
            managedNode.loadFromNBT(NetworkIdentityNodeSeed.managedNode("gn", networkId));
        }
        managedNode.create(level, inWorld ? position : null);
    }

    public void connect(IGridNode gridNode) {
        var requesterNode = managedNode.getNode();
        if (requesterNode != null && requesterNode.getGrid() != gridNode.getGrid()) {
            GridHelper.createConnection(requesterNode, gridNode);
        }
    }

    public boolean handleCrafting(AEKey what, long amount, Level level, ICraftingService service) {
        var submitted = tracker.handleCrafting(0, what, amount, level, service, actionSource);
        for (var link : tracker.getRequestedJobs()) {
            observedCraftingIds.add(link.getCraftingID().toString());
            if (submitted) {
                submittedLink = link;
            }
        }
        return submitted;
    }

    public int uniqueNativeJobCount() {
        return observedCraftingIds.size();
    }

    public String nativeJobIds() {
        return String.join(",", observedCraftingIds);
    }

    public boolean isReady(IGridNode gridNode) {
        var requesterNode = managedNode.getNode();
        return requesterNode != null && requesterNode.isActive() && requesterNode.hasGridBooted()
                && requesterNode.getGrid() == gridNode.getGrid();
    }

    public String readiness(IGridNode gridNode) {
        var requesterNode = managedNode.getNode();
        if (requesterNode == null) {
            return "node-missing";
        }
        if (requesterNode.getGrid() != gridNode.getGrid()) {
            return "grid-mismatch";
        }
        if (!requesterNode.hasGridBooted()) {
            return "grid-booting";
        }
        return requesterNode.isActive() ? "ready" : "inactive";
    }

    public ICraftingLink activeLink() {
        return tracker.getRequestedJobs().stream().findFirst().orElse(null);
    }

    public ICraftingLink submittedLink() {
        return submittedLink;
    }

    public CompoundTag writeState() {
        var tag = new CompoundTag();
        tracker.writeToNBT(tag);
        managedNode.saveToNBT(tag);
        return tag;
    }

    public long acceptedAmount() {
        return acceptedAmount;
    }

    public int stateChanges() {
        return stateChanges;
    }

    public boolean observedDone() {
        return observedDone;
    }

    public boolean observedCanceled() {
        return observedCanceled;
    }

    public IActionSource actionSource() {
        return actionSource;
    }

    @Override
    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return tracker.getRequestedJobs();
    }

    @Override
    public long insertCraftedItems(ICraftingLink link, AEKey what, long amount, Actionable mode) {
        var accepted = destination.insert(what, amount, mode, actionSource);
        if (mode == Actionable.MODULATE) {
            acceptedAmount += accepted;
        }
        return accepted;
    }

    @Override
    public void jobStateChange(ICraftingLink link) {
        stateChanges++;
        observedDone |= link.isDone();
        observedCanceled |= link.isCanceled();
        tracker.jobStateChange(link);
    }

    @Override
    public IGridNode getActionableNode() {
        return managedNode.getNode();
    }

    @Override
    public void close() {
        managedNode.destroy();
    }
}
