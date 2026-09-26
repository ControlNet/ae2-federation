package space.controlnet.ae2federation.test.mixed;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.misc.InterfaceBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import space.controlnet.ae2federation.crafting.terminal.NativeTerminalAdapter;
import space.controlnet.ae2federation.crafting.terminal.NativeTerminalRequest;
import space.controlnet.ae2federation.test.automation.AutomationAuthorityObservation;
import space.controlnet.ae2federation.test.automation.AutomationNativeObservation;
import space.controlnet.ae2federation.test.automation.NativeAutomationFixture;
import space.controlnet.ae2federation.test.crafting.NativeCraftingRequester;
import space.controlnet.ae2federation.test.crafting.TerminalNativeObservation;
import space.controlnet.ae2federation.test.processing.NativeProviderLaneFixtures;
import space.controlnet.ae2federation.test.processing.ProcessingNativeObservation;

public final class MixedFactoryScene implements AutoCloseable {
    private static final BlockPos STOCKING_INTERFACE = new BlockPos(5, 3, 7);
    private final GameTestHelper helper;
    private final MixedFactoryProfile profile;
    private final MixedFactoryTopology topology;
    private final int iteration;
    private final boolean warmup;
    private final NativeAutomationFixture automation;
    private final List<BlockPos> additionalCpus = new ArrayList<>();
    private final List<NativeCraftingRequester> requesters = new ArrayList<>();
    private final List<NativeTerminalRequest> requests = new ArrayList<>();
    private final List<Boolean> submitted = new ArrayList<>();
    private NativeProviderLaneFixtures processing;
    private MixedProcessingMachine machine;
    private InterfaceBlockEntity stockingInterface;
    private int stage;
    private int blockedTicks;
    private int consumedStockingCycles;
    private boolean finished;
    private boolean finalStockRecorded;
    private Map<String, Long> initialSource = Map.of();

    public MixedFactoryScene(GameTestHelper helper, MixedFactoryProfile profile, int iteration, boolean warmup) {
        this.helper = helper;
        this.profile = profile;
        this.topology = MixedFactoryTopology.from(profile);
        this.iteration = iteration;
        this.warmup = warmup;
        automation = new NativeAutomationFixture(helper);
    }

    public boolean ready() {
        if (!automation.ready()) return false;
        if (processing == null) {
            for (var index = 1; index < profile.cpuLimit(); index++) {
                var position = new BlockPos(3 + index, 3, 3);
                additionalCpus.add(position);
                automation.binding().addNativeCpu(position);
            }
            processing = new NativeProviderLaneFixtures(helper, laneAssignments(), false,
                    topology.processingRecipes().size(), automation.binding().key().providerNetworkId());
            processing.connectTo(automation.binding().sourceChest().getMainNode().getNode());
            processing.register();
            machine = new MixedProcessingMachine(helper, processing, topology);
            return false;
        }
        if (additionalCpus.stream().anyMatch(position -> !automation.binding().connectNativeCpu(position))) return false;
        processing.connectTo(automation.binding().sourceChest().getMainNode().getNode());
        return processing.managedNode().getNode() != null
                && processing.managedNode().getNode().getGrid() == automation.binding().providerGrid()
                && automation.binding().sourceService().getCpus().size() == profile.cpuLimit()
                && automation.binding().sourceService().isCraftable(AEItemKey.of(topology.chainResult()))
                && topology.blockedRecipes().stream().allMatch(recipe ->
                        automation.binding().sourceService().isCraftable(AEItemKey.of(recipe.output())));
    }

    public boolean tick() {
        if (finished) return true;
        if (stage == 0) initializeAutomation();
        if (stage == 1) beginRequests();
        if (stage == 2) submitRequests();
        if (stage == 3) observeExecution();
        if (stage == 4) finishWhenReconciled();
        return finished;
    }

    public String progress() {
        return "stage=" + stage + ",blockedTicks=" + blockedTicks + ",busy="
                + automation.binding().busyCpuCount() + ",cpus=" + automation.binding().sourceService().getCpus().size()
                + ",requests=" + requests.size() + ",submitted=" + submitted.stream().filter(Boolean::booleanValue).count();
    }

    public NativeAutomationFixture automation() { return automation; }
    public MixedFactoryObservation.Snapshot observation() { return MixedFactoryObservation.snapshot(); }
    public Map<String, Long> initialSource() { return initialSource; }
    public Map<String, Long> finalSource() { return sourceSnapshot(); }
    public MixedFactoryTopology topology() { return topology; }

    public Map<String, Long> callbackResults() {
        var result = new TreeMap<String, Long>();
        for (var index = 0; index < requesters.size(); index++) {
            var key = index < topology.blockedRecipes().size()
                    ? topology.blockedRecipes().get(index).output() : topology.chainResult();
            result.put(id(key), requesters.get(index).acceptedAmount(AEItemKey.of(key)));
        }
        return Map.copyOf(result);
    }

    public long finalStocked() { return automation.interfaceAmount(stockingInterface, 0); }
    public long finalExported() { return automation.exportChestCount(Items.DIAMOND); }

    private void initializeAutomation() {
        MixedFactoryObservation.begin(iteration, warmup);
        TerminalNativeObservation.begin("mixedbenchmarksmall");
        ProcessingNativeObservation.reset();
        AutomationNativeObservation.begin("mixedbenchmarksmall");
        AutomationAuthorityObservation.begin("mixedbenchmarksmall", automation);
        AutomationAuthorityObservation.authorizeProjectionOperation("extract", AEItemKey.of(Items.DIAMOND));
        automation.clearConsumerCell();
        MixedBatchAxisWorkload.execute(automation, profile);
        requireInserted(Items.DIAMOND, profile.initial("diamonds"));
        for (var alternative : topology.alternatives()) {
            var quantity = alternative == Items.DIRT ? profile.initial("dirt") : profile.initial("cobblestone");
            requireInserted(alternative, quantity);
        }
        for (var index = 0; index < topology.blockedRecipes().size(); index++) {
            requireInserted(topology.blockedRecipes().get(index).input(),
                    index == 0 ? profile.initial("redstone") : profile.requested("glass"));
        }
        initialSource = sourceSnapshot();
        MixedFactoryRuntimeReceipt.inventory("initial", initialSource, 0, 0);
        stockingInterface = automation.placeConsumerInterface(STOCKING_INTERFACE);
        AutomationAuthorityObservation.authorizeInterface(stockingInterface.getInterfaceLogic());
        automation.configure(stockingInterface, 0, AEItemKey.of(Items.DIAMOND), profile.requested("stockedDiamonds"));
        MixedFactoryObservation.authorizeStocking(stockingInterface.getInterfaceLogic());
        automation.placeNativeBuses(ItemStack.EMPTY, AEItemKey.of(Items.DIAMOND));
        for (var index = 0; index < profile.blockedLanes() + 1; index++) {
            var requester = requester(new BlockPos(1, 1, index + 1));
            requesters.add(requester);
            AutomationAuthorityObservation.authorizeInterface(requester);
        }
        machine.authorizeHandlers();
        stage = 1;
    }

    private void beginRequests() {
        if (!automation.interfaceReady(stockingInterface, false) || requesters.stream().anyMatch(requester ->
                !requester.isReady(automation.binding().sourceChest().getMainNode().getNode()))) return;
        var terminal = NativeTerminalAdapter.discover(helper.getLevel(), automation.binding().consumerGrid(),
                automation.binding().key().providerNetworkId(), IActionSource.empty()).orElseThrow();
        for (var recipe : topology.blockedRecipes()) {
            requests.add(terminal.begin(AEItemKey.of(recipe.output()), profile.requested("glass")).orElseThrow());
        }
        requests.add(terminal.begin(AEItemKey.of(topology.chainResult()), profile.requested("emeralds")).orElseThrow());
        terminal.close();
        requests.forEach(ignored -> submitted.add(false));
        stage = 2;
    }

    private void submitRequests() {
        if (requests.stream().anyMatch(request -> request.completedPlan().isEmpty())) return;
        for (var index = 0; index < requests.size(); index++) {
            if (!submitted.get(index)) {
                var requester = requesters.get(index);
                submitted.set(index, requests.get(index).submitTracked(0, requester,
                        requester::handleCrafting).isPresent());
            }
        }
        if (submitted.stream().allMatch(Boolean::booleanValue)) stage = 3;
    }

    private void observeExecution() {
        for (var index = 0; index < topology.blockedRecipes().size(); index++) {
            MixedFactoryObservation.observeWaiting(requesters.get(index).activeLink(),
                    automation.binding().busyCpuCount(), machine.machine().inputCount(
                            topology.blockedRecipes().get(index).input()));
        }
        if (consumedStockingCycles < profile.stockingCycles() - 1
                && automation.interfaceAmount(stockingInterface, 0) == profile.requested("stockedDiamonds")) {
            var before = automation.interfaceAmount(stockingInterface, 0);
            var consumed = automation.consumeInterfaceSlot(stockingInterface, 0);
            var after = automation.interfaceAmount(stockingInterface, 0);
            if (consumed != before) throw new IllegalStateException("Native Interface stocking cycle did not reconcile");
            MixedFactoryObservation.stocking(stockingInterface.getInterfaceLogic(), before, -consumed, after);
            consumedStockingCycles++;
        }
        var blockedInputPresent = topology.blockedRecipes().stream().anyMatch(recipe ->
                machine.machine().inputCount(recipe.input()) > 0);
        if (blockedInputPresent && blockedTicks++ < 4) return;
        if (blockedTicks > 4) machine.releaseBlocked();
        if (consumedStockingCycles == profile.stockingCycles() - 1
                && requesters.stream().allMatch(NativeCraftingRequester::observedDone)
                && automation.binding().busyCpuCount() == 0) stage = 4;
    }

    private void finishWhenReconciled() {
        if (automation.interfaceAmount(stockingInterface, 0) != profile.requested("stockedDiamonds")
                || automation.exportChestCount(Items.DIAMOND) != profile.requested("busDiamonds")) return;
        if (!finalStockRecorded) {
            MixedFactoryObservation.stocking(stockingInterface.getInterfaceLogic(), 0,
                    automation.interfaceAmount(stockingInterface, 0), automation.interfaceAmount(stockingInterface, 0));
            finalStockRecorded = true;
            MixedFactoryRuntimeReceipt.inventory("final", sourceSnapshot(),
                    automation.interfaceAmount(stockingInterface, 0), automation.exportChestCount(Items.DIAMOND));
        }
        finished = true;
    }

    private List<IntPredicate> laneAssignments() {
        var patterns = topology.lanesByPattern();
        var result = new ArrayList<IntPredicate>();
        for (var lane = 0; lane < profile.blockedLanes() + 1; lane++) {
            var laneIndex = lane;
            result.add(slot -> patterns.get(slot).contains(laneIndex));
        }
        return List.copyOf(result);
    }

    private Map<String, Long> sourceSnapshot() {
        var result = new TreeMap<String, Long>();
        for (var item : topology.resources()) result.put(id(item), automation.sourceAmount(AEItemKey.of(item)));
        return Map.copyOf(result);
    }

    private NativeCraftingRequester requester(BlockPos position) {
        var requester = new NativeCraftingRequester(helper.getLevel(), helper.absolutePos(position),
                automation.binding().sourcePhysicalStorage(), automation.binding().key().providerNetworkId(), null, false);
        requester.connect(automation.binding().sourceChest().getMainNode().getNode());
        return requester;
    }

    private void requireInserted(Item item, long amount) {
        if (automation.insertSource(AEItemKey.of(item), amount) != amount) {
            throw new IllegalStateException("Native source rejected " + item);
        }
    }

    private static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    @Override
    public void close() {
        requesters.forEach(NativeCraftingRequester::close);
        if (processing != null) processing.close();
        automation.close();
        AutomationNativeObservation.close();
        AutomationAuthorityObservation.close();
        TerminalNativeObservation.close();
        MixedFactoryObservation.close();
    }
}
