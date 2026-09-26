package space.controlnet.ae2federation.test.processing;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.test.crafting.NativeCraftingRequester;

final class ProcessingCraftingCoordinator implements AutoCloseable {
    private final GameTestHelper helper;
    private final ProcessingBenchmarkObservation.Scene scene;
    private final NetworkId networkId;
    private final ProcessingCraftingGrid grid;
    private final ProcessingCraftingOutputStorage outputStorage = new ProcessingCraftingOutputStorage();
    private Future<ICraftingPlan> planFuture;
    private ICraftingPlan plan;
    private NativeCraftingRequester requester;
    private String originalLinkId;
    private String reloadedLinkId;
    private CompoundTag persistedRequester;
    private AEItemKey requestedOutputKey;
    private long requestedOutputAmount;
    private boolean materialsInserted;
    private boolean submitted;
    private boolean requesterReloaded;
    private String readiness = "not-started";

    ProcessingCraftingCoordinator(GameTestHelper helper, ProcessingBenchmarkObservation.Scene scene, NetworkId networkId) {
        this.helper = helper;
        this.scene = scene;
        this.networkId = networkId;
        grid = new ProcessingCraftingGrid(helper, networkId);
    }

    boolean ready(IGridNode providerNode) {
        var storageNode = grid.node();
        if (providerNode == null) {
            readiness = "provider-node-missing";
            return false;
        }
        if (storageNode == null) {
            readiness = "storage-node-missing";
            return false;
        }
        if (!storageNode.hasGridBooted()) {
            readiness = "storage-grid-booting";
            return false;
        }
        if (providerNode.getGrid() != storageNode.getGrid()) {
            readiness = "provider-storage-grid-mismatch";
            GridHelper.createConnection(providerNode, storageNode);
            return false;
        }
        var cpuCount = storageNode.getGrid().getCraftingService().getCpus().size();
        if (cpuCount != 1) {
            readiness = "cpu-count-" + cpuCount;
            return false;
        }
        if (requester == null) {
            requester = new NativeCraftingRequester(helper.getLevel(),
                    helper.absolutePos(ProcessingCraftingGrid.REQUESTER_POS), outputStorage, networkId, null);
            requester.connect(storageNode);
            readiness = "requester-created";
            return false;
        }
        if (!requester.isReady(storageNode)) {
            readiness = "requester-" + requester.readiness(storageNode);
            return false;
        }
        readiness = "ready";
        return true;
    }

    String readiness() {
        return readiness;
    }

    void insertStartingInput(long amount) {
        if (materialsInserted) {
            return;
        }
        var inserted = storage().insert(AEItemKey.of(Items.COBBLESTONE), amount, Actionable.MODULATE,
                requester.actionSource());
        if (inserted != amount) {
            throw new IllegalStateException("Processing benchmark source storage rejected initial resources");
        }
        materialsInserted = true;
    }

    void begin(AEItemKey outputKey, long outputAmount) {
        if (planFuture != null) {
            if (!submitted) {
                return;
            }
            planFuture = null;
            plan = null;
            submitted = false;
        }
        requestedOutputKey = outputKey;
        requestedOutputAmount = outputAmount;
        var sourceNode = grid.node();
        var simulationRequester = new ICraftingSimulationRequester() {
            @Override
            public IActionSource getActionSource() {
                return requester.actionSource();
            }

            @Override
            public IGridNode getGridNode() {
                return sourceNode;
            }
        };
        ProcessingBenchmarkObservation.select(scene);
        ProcessingBenchmarkObservation.recordPlannerCall();
        planFuture = sourceNode.getGrid().getCraftingService().beginCraftingCalculation(helper.getLevel(),
                simulationRequester, requestedOutputKey, outputAmount, CalculationStrategy.REPORT_MISSING_ITEMS);
    }

    boolean planReady() {
        if (plan != null) {
            return true;
        }
        if (planFuture == null || !planFuture.isDone()) {
            return false;
        }
        try {
            plan = planFuture.get();
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Processing benchmark calculation interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Processing benchmark calculation failed", exception);
        }
    }

    boolean submit() {
        if (submitted) {
            return true;
        }
        var service = grid.node().getGrid().getCraftingService();
        if (!requester.handleCrafting(requestedOutputKey, requestedOutputAmount,
                helper.getLevel(), service)) {
            return false;
        }
        var original = requester.submittedLink();
        if (original == null) {
            throw new IllegalStateException("Processing benchmark requester submitted without a tracked link");
        }
        ProcessingBenchmarkObservation.select(scene);
        ProcessingBenchmarkObservation.recordSubmittedJob();
        if (originalLinkId == null) {
            originalLinkId = original.getCraftingID().toString();
        }
        submitted = true;
        return true;
    }

    void beginRequesterReload() {
        if (persistedRequester != null || requesterReloaded) {
            return;
        }
        persistedRequester = requester.writeState();
        requester.close();
    }

    void finishRequesterReload() {
        if (requesterReloaded) {
            return;
        }
        if (persistedRequester == null) {
            throw new IllegalStateException("Processing benchmark requester reload was not started");
        }
        requester = new NativeCraftingRequester(helper.getLevel(),
                helper.absolutePos(ProcessingCraftingGrid.REQUESTER_POS), outputStorage, networkId, persistedRequester);
        requester.connect(grid.node());
        var reloaded = requester.activeLink();
        if (reloaded == null) {
            throw new IllegalStateException("Processing benchmark requester link did not reload");
        }
        reloadedLinkId = reloaded.getCraftingID().toString();
        ProcessingBenchmarkObservation.recordReload();
        requesterReloaded = true;
        persistedRequester = null;
    }

    void observeCpuTick() {
        if (cpuBusy()) {
            ProcessingBenchmarkObservation.select(scene);
            ProcessingBenchmarkObservation.recordCpuBusyTick();
        }
    }

    boolean cpuBusy() {
        return grid.node().getGrid().getCraftingService().getCpus().stream().anyMatch(cpu -> cpu.isBusy());
    }

    String requesterProgress() {
        var link = requester.activeLink();
        return "accepted=" + requester.acceptedAmount() + ",stateChanges=" + requester.stateChanges()
                + ",observedDone=" + requester.observedDone() + ",link="
                + (link == null ? "absent" : "done=" + link.isDone() + "/canceled=" + link.isCanceled());
    }

    boolean complete(long expectedOutput) {
        return requesterReloaded && requester.observedDone() && requester.acceptedAmount() == expectedOutput
                && !cpuBusy();
    }

    boolean planIsComplete() { return plan != null && !plan.simulation(); }

    int cpuCount() { return grid.cpuCount(); }

    long inputAmount() {
        return storage().extract(AEItemKey.of(Items.COBBLESTONE), Long.MAX_VALUE, Actionable.SIMULATE,
                IActionSource.empty());
    }

    long outputAmount() { return outputStorage.itemAmount(Items.DIAMOND); }

    MEStorage storage() {
        return grid.node().getGrid().getStorageService().getInventory();
    }

    String originalLinkId() { return originalLinkId; }

    String reloadedLinkId() { return reloadedLinkId; }

    @Override
    public void close() {
        if (requester != null) {
            requester.close();
        }
        grid.close();
    }
}
