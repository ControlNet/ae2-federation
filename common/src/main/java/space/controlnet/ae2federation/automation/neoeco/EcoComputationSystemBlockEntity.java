package space.controlnet.ae2federation.automation.neoeco;

import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.automation.AutomationInterfaceBook;
import space.controlnet.ae2federation.automation.ComputingDevices;
import space.controlnet.ae2federation.automation.ComputingHost;
import space.controlnet.ae2federation.automation.DomainDevice;
import space.controlnet.ae2federation.automation.ProgramScheduler;
import space.controlnet.ae2federation.domain.FederationDomainNodeKind;
import space.controlnet.ae2federation.domain.FederationDomainPortEvidence;
import space.controlnet.ae2federation.domain.FederationDomainPortId;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;

/**
 * The controller of a Federation computation system built from Neo ECO's computation parts: Neo ECO's own controller
 * block entity, so Neo ECO's structure checks find it, and a computing device of its Federation domain through the
 * structure's communication interfaces. Its installations are kept in {@link ComputingDevices}, as the Creative
 * Computing Device's are.
 */
public final class EcoComputationSystemBlockEntity extends ECOComputationSystemBlockEntity
        implements ComputingHost, DomainDevice.Host {
    private final DomainDevice device = new DomainDevice(this, this);
    /** Whether a redstone signal reached it at the last update, so only a rising edge triggers; kept across loads. */
    private boolean powered;

    public EcoComputationSystemBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state, IECOTier tier) {
        super(type, position, state, tier);
    }

    @Override
    public FederationDomainNodeKind kind() {
        return FederationDomainNodeKind.COMPUTING_DEVICE;
    }

    /**
     * Its parallel cores' accelerators in CU, its computation cells' bytes in B/s (user decisions 2026-10-20) and its
     * threading cores' threads in installations (user, 2026-10-20); nothing while it is not formed.
     */
    @Override
    public Capacity capacity() {
        var cluster = getCluster();
        return cluster == null ? Capacity.NONE
                : new Capacity(cluster.getCPUAccelerators(), cluster.getTotalStorage(), cluster.getMaxThreads());
    }

    /** A link to each communication interface of the formed structure, which names this node back. */
    @Override
    public Map<String, FederationDomainPortEvidence> remotePorts() {
        var cluster = getCluster();
        if (cluster == null || !(level instanceof ServerLevel serverLevel)) return Map.of();
        var ports = new TreeMap<String, FederationDomainPortEvidence>();
        cluster.getBlockEntities().forEachRemaining(member -> {
            if (member instanceof EcoComputationInterfaceBlockEntity part) {
                ports.put(EcoComputationInterfaceBlockEntity.portOf(part.getBlockPos()),
                        new FederationDomainPortEvidence.Federation(new FederationDomainPortId(
                                FederationDomainRegistryAccess.nodeId(serverLevel, part.getBlockPos()),
                                EcoComputationInterfaceBlockEntity.CONTROLLER_PORT)));
            }
        });
        return ports;
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
    public @Nullable String id() {
        return device.id();
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

    /** Neo ECO's controller tick, then the device's. */
    public static void serverTick(Level level, BlockPos position, BlockState state, EcoComputationSystemBlockEntity entity) {
        entity.tick(level, position, state);
        entity.device.tick();
    }

    /** A neighbour changed: a redstone signal starts the redstone-triggered installations. */
    public void neighborChanged(BlockPos neighborPosition) {
        device.neighborChanged(neighborPosition);
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

    /** The structure formed or broke: it links to the interfaces only while it is formed. */
    @Override
    public void updateState(boolean updateExposed) {
        super.updateState(updateExposed);
        device.openPorts();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        device.clearRemoved();
    }

    @Override
    public void onReady() {
        super.onReady();
        device.onReady();
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
        powered = tag.getBoolean("powered");
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        device.save(tag);
        if (powered) {
            tag.putBoolean("powered", true);
        }
    }
}
