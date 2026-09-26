package space.controlnet.ae2federation.test.scale;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.helpers.externalstorage.GenericStackItemStorage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.test.mixed.MixedFactoryObservation;
import space.controlnet.ae2federation.test.mixed.MixedMachineBlockEntity;
import space.controlnet.ae2federation.test.processing.ProcessingRegressionFixtures;

public final class ScaleLargeFederationSixteenTargets {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScaleLargeFederationSixteenTargets.class);
    private static final int TARGET_COUNT = 16;
    private static final int QUANTITY = 16;
    private static final List<BlockPos> HOSTS = IntStream.range(0, TARGET_COUNT)
            .mapToObj(index -> new BlockPos(7 + 8 * (index % 4), 6, 5 + 8 * (index / 4))).toList();
    private static final List<ScaleNativeProcessingProbe.CatalogSelection> JOBS = IntStream.range(0, TARGET_COUNT)
            .mapToObj(index -> ScaleProcessingCatalog.recipes().get(index))
            .map(recipe -> new ScaleNativeProcessingProbe.CatalogSelection(0,
                    AEItemKey.of(recipe.input()), AEItemKey.of(recipe.output()))).toList();

    private ScaleLargeFederationSixteenTargets() {
    }

    public static void run(GameTestHelper helper) {
        var scene = new Scene(helper);
        ScaleSourceHosts.run(helper, scene::tick);
    }

    private static final class Scene {
        private final GameTestHelper helper;
        private final List<ScaleLargeFederationTarget> targets = new ArrayList<>();
        private final List<MixedMachineBlockEntity> machines = new ArrayList<>();
        private final List<IGrid> grids = new ArrayList<>();
        private final List<NetworkId> ids = new ArrayList<>();
        private final Set<String> jobIds = new LinkedHashSet<>();
        private final Set<ChunkPos> podChunks = new HashSet<>();
        private Future<ICraftingPlan> plan;
        private ScaleLargeFederationTarget pending;
        private int stage;
        private int jobIndex;
        private int waitTicks;

        private Scene(GameTestHelper helper) {
            this.helper = helper;
        }

        private boolean tick(ScaleSourceHosts.Scene source) {
            if (stage == 0) {
                preflight(source);
                pending = new ScaleLargeFederationTarget(helper, source, 0, HOSTS.get(0));
                stage = 1;
                return false;
            }
            if (stage == 1) {
                if (!pending.tick()) return false;
                targets.add(pending);
                if (targets.size() < TARGET_COUNT) {
                    pending = new ScaleLargeFederationTarget(helper, source, targets.size(), HOSTS.get(targets.size()));
                    return false;
                }
                helper.assertValueEqual(targets.size(), TARGET_COUNT,
                        "Sixteen actual physical Federation destinations after all source channels settled");
                assertLayout(source, true);
                MixedFactoryObservation.begin(0, false);
                for (var target : targets) {
                    var machine = target.target().placeProcessingMachine();
                    machine.processSingleItemPerTick();
                    machines.add(machine);
                }
                installPatterns(source);
                stage = 2;
                return false;
            }
            assertLayout(source, false);
            if (stage == 2) {
                assertPatterns(source);
                beginJob(source);
                return false;
            }
            var selection = JOBS.get(jobIndex);
            if (stage == 3) {
                if (!plan.isDone()) return false;
                try {
                    var result = plan.get();
                    helper.assertTrue(!result.simulation() && result.finalOutput().what().equals(selection.output())
                                    && result.finalOutput().amount() == QUANTITY,
                            "Native Federation planner must calculate sixteen outputs for host " + jobIndex);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Federation planner interrupted", exception);
                } catch (ExecutionException exception) {
                    throw new IllegalStateException("Federation planner failed", exception);
                }
                stage = 4;
            }
            if (stage == 4) {
                if (!source.requester().handleCrafting(selection.output(), QUANTITY, helper.getLevel(),
                        source.grid().getCraftingService())) return false;
                var link = source.requester().submittedLink();
                helper.assertTrue(link != null && !link.isCanceled() && jobIds.add(link.getCraftingID().toString()),
                        "Each Federation destination must submit a distinct native CPU link: " + jobIndex);
                stage = 5;
                return false;
            }
            if (stage == 5) {
                for (int index = 0; index < TARGET_COUNT; index++) {
                    if (index != jobIndex) helper.assertValueEqual(targets.get(index).target().inputAmount(selection.input()),
                            0L, "Unselected Federation target must not receive selected typed input: " + index);
                }
                var target = targets.get(jobIndex);
                var endpoint = target.target();
                var context = endpoint.endpoint().binding().runtime().itemReturnContext();
                if (endpoint.inputAmount(selection.input()) != QUANTITY || context.isEmpty()) {
                    if (++waitTicks > 2000) helper.fail("Federation target " + jobIndex
                            + " missing native routed input: source=" + amount(source, selection.input())
                            + " target=" + endpoint.inputAmount(selection.input())
                            + " returnContext=" + context.isPresent());
                    return false;
                }
                var owner = context.orElseThrow().owner();
                var lane = source.hosts().get(jobIndex).lane(0);
                var handler = endpoint.returnHandler();
                helper.assertTrue(owner.logic() == lane && owner.inventory() == lane.getReturnInv()
                                && owner.lane().isPresent() && handler == context.orElseThrow().capability(),
                        "Endpoint return capability must be owned by the selected real native Provider Lane");
                target.assertAuthorized(owner.lane().orElseThrow());
                helper.assertValueEqual(amount(source, selection.input()), 0L,
                        "Source physical cell must release all selected input before Export Bus activation");
                for (int index = 0; index < TARGET_COUNT; index++) {
                    if (index != jobIndex) helper.assertValueEqual(machines.get(index).inputCount(
                            selection.input().getItem()), 0L,
                            "Unselected Federation machine must not receive selected input: " + index);
                }
                var machine = machines.get(jobIndex);
                machine.configure(List.of(new MixedMachineBlockEntity.MachineRecipe(selection.input().getItem(),
                        selection.output().getItem(), false, owner.inventory(), handler)));
                helper.assertTrue(machine.inputHandler().isItemValid(0,
                        new ItemStack(selection.input().getItem())),
                        "Selected physical machine must accept its configured typed Federation input");
                MixedFactoryObservation.authorizeMachine(machine, machine.inputHandler(), machine.outputInventory(),
                        owner.inventory(), selection.input(), selection.output());
                endpoint.export(selection.input());
                waitTicks = 0;
                stage = 6;
                return false;
            }
            if (!source.requester().observedDone() || source.requester().activeLink() != null
                    || source.requester().acceptedAmount(selection.output()) != QUANTITY) {
                if (++waitTicks > 2000) helper.fail("Federation target " + jobIndex
                        + " missing physical machine result or native callback: targetInput="
                        + targets.get(jobIndex).target().inputAmount(selection.input()) + " machineInput="
                        + machines.get(jobIndex).inputCount(selection.input().getItem()) + " callback="
                        + source.requester().acceptedAmount(selection.output()));
                return false;
            }
            finishJob(source);
            if (++jobIndex < TARGET_COUNT) {
                stage = 2;
                plan = null;
                waitTicks = 0;
                return false;
            }
            MixedFactoryObservation.close();
            targets.forEach(ScaleLargeFederationTarget::close);
            return true;
        }

        private void preflight(ScaleSourceHosts.Scene source) {
            var positions = new HashSet<BlockPos>();
            helper.assertTrue(helper.getBounds().getXsize() == 36 && helper.getBounds().getYsize() == 8
                            && helper.getBounds().getZsize() == 36,
                    "Sixteen Federation pods require the actual 36x8x36 structure");
            for (int index = 0; index < TARGET_COUNT; index++) {
                var host = HOSTS.get(index);
                var node = helper.<appeng.blockentity.crafting.PatternProviderBlockEntity>getBlockEntity(host)
                        .getMainNode().getNode();
                helper.assertTrue(source.hosts().get(index).hostBlockEntity()
                                == helper.getLevel().getBlockEntity(helper.absolutePos(host))
                                && node.getGrid() == source.grid() && node.meetsChannelRequirements()
                                && node.getUsedChannels() > 0
                                && node.getInWorldConnections().containsKey(Direction.WEST),
                        "Federation source leg must originate at the exact active channeled Provider: " + index);
                for (var position : ScaleLargeFederationTarget.footprint(host)) {
                    var world = helper.absolutePos(position);
                    helper.assertTrue(positions.add(position)
                                    && helper.getBounds().contains(world.getX() + 0.5, world.getY() + 0.5,
                                            world.getZ() + 0.5)
                                    && helper.getLevel().isLoaded(world)
                                    && helper.getLevel().getBlockEntity(world) == null
                                    && (helper.getBlockState(position).isAir()
                                            || helper.getBlockState(position).is(Blocks.BARRIER)),
                            "Federation pod block must be unique, bounded, loaded and BE-free: " + position);
                    podChunks.add(new ChunkPos(world));
                }
            }
            helper.assertValueEqual(positions.size(), TARGET_COUNT * 9,
                    "Sixteen physical Federation pods require 144 distinct planned blocks");
        }

        private void assertLayout(ScaleSourceHosts.Scene source, boolean initial) {
            var uniqueGrids = Collections.newSetFromMap(new IdentityHashMap<IGrid, Boolean>());
            var uniqueIds = new HashSet<NetworkId>();
            var uniqueMachines = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
            var uniqueReturns = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
            uniqueGrids.add(source.grid());
            uniqueIds.add(source.id());
            helper.assertTrue(source.crafting().cpuCount() == 1 && source.crafting().cpuNode().getGrid() == source.grid()
                            && source.requester().isReady(source.crafting().node())
                            && source.retention().ready(source.crafting().node()),
                    "One source Grid must retain its native CPU/requester/mounted Drive");
            for (int index = 0; index < TARGET_COUNT; index++) {
                var target = targets.get(index);
                target.assertReady();
                var grid = target.grid();
                var id = FederationDomainRegistryAccess.confirmedNetworkId(grid).orElseThrow();
                helper.assertTrue(uniqueGrids.add(grid) && uniqueIds.add(id) && grid != source.grid()
                                && target.target().grid() == grid && target.target().anchorId().equals(id)
                                && target.target().onlyAnchorClaim() && target.target().settled()
                                && uniqueReturns.add(source.hosts().get(index).lane(0).getReturnInv()),
                        "Each physical Federation target and return owner must be distinct: " + index);
                if (initial) {
                    grids.add(grid);
                    ids.add(id);
                } else {
                    helper.assertTrue(grids.get(index) == grid && ids.get(index).equals(id),
                            "Federation target Grid/confirmed ID must remain stable through all jobs: " + index);
                    var machine = machines.get(index);
                    var handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                            helper.absolutePos(target.endpointPosition().south()), Direction.WEST);
                    helper.assertTrue(uniqueMachines.add(machine) && handler == machine.inputHandler()
                                    && machine.inputHandler() != null,
                            "Each target needs its distinct registered sided capability machine: " + index);
                }
            }
            helper.assertValueEqual(uniqueGrids.size(), TARGET_COUNT + 1,
                    "Source plus sixteen separate physical Federation target Grids");
            helper.assertValueEqual(uniqueIds.size(), TARGET_COUNT + 1,
                    "Source plus sixteen separate confirmed Federation identities");
            if (initial) LOGGER.info("AE2F_SCALE_LARGE_FEDERATION_TOPOLOGY sourceGrid={} sourceId={} targetGrids={} "
                            + "targetIds={} sourceProviders=16 sourceCpu=1 sourceDrive=1 sourceEnergy=1 "
                            + "bridges=16 targetEndpoints=16 targetChests=16 targetCells=16 targetEnergy=16 "
                            + "targetExportBuses=16 targetMachines=16 podChunks={}",
                    System.identityHashCode(source.grid()), source.id().value(),
                    grids.stream().map(System::identityHashCode).toList(), ids.stream().map(NetworkId::value).toList(),
                    podChunks.stream().map(ChunkPos::toString).sorted().toList());
        }

        private void installPatterns(ScaleSourceHosts.Scene source) {
            var inputs = Set.copyOf(JOBS.stream().map(ScaleNativeProcessingProbe.CatalogSelection::input).toList());
            var outputs = Set.copyOf(JOBS.stream().map(ScaleNativeProcessingProbe.CatalogSelection::output).toList());
            helper.assertTrue(inputs.size() == TARGET_COUNT && outputs.size() == TARGET_COUNT
                            && Collections.disjoint(inputs, outputs),
                    "Sixteen Federation Processing Patterns must have disjoint typed input/output keys");
            for (int index = 0; index < TARGET_COUNT; index++) {
                var selection = JOBS.get(index);
                source.hosts().get(index).installPattern(0,
                        List.of(ProcessingRegressionFixtures.item(selection.input().getItem(), 1)),
                        List.of(ProcessingRegressionFixtures.item(selection.output().getItem(), 1)));
            }
        }

        private void assertPatterns(ScaleSourceHosts.Scene source) {
            helper.assertTrue(source.grid().getCraftingService().getCraftables(key -> true).equals(
                            Set.copyOf(JOBS.stream().map(ScaleNativeProcessingProbe.CatalogSelection::output).toList())),
                    "Exactly sixteen Federation source Patterns must be natively craftable");
            for (int index = 0; index < TARGET_COUNT; index++) {
                var selection = JOBS.get(index);
                var host = source.hosts().get(index);
                var inventory = host.composition().patternInventory();
                var decoded = PatternDetailsHelper.decodePattern(inventory.getStackInSlot(0), helper.getLevel());
                var lane = host.lane(0);
                helper.assertTrue(inventory.size() == 1 && decoded != null && decoded.getInputs().length == 1
                                && decoded.getInputs()[0].getPossibleInputs().length == 1
                                && decoded.getInputs()[0].getPossibleInputs()[0].what().equals(selection.input())
                                && decoded.getInputs()[0].getPossibleInputs()[0].amount() == 1
                                && decoded.getPrimaryOutput().what().equals(selection.output())
                                && decoded.getPrimaryOutput().amount() == 1 && decoded.getOutputs().size() == 1
                                && lane.getAvailablePatterns().equals(List.of(decoded))
                                && host.publishedProviders(decoded).equals(List.of(lane))
                                && source.grid().getCraftingService().getCraftingFor(selection.output()).size() == 1
                                && source.grid().getCraftingService().getCraftingFor(selection.output()).contains(decoded),
                        "Each real source Provider must exclusively publish its typed Federation Pattern: " + index);
            }
        }

        private void beginJob(ScaleSourceHosts.Scene source) {
            var selection = JOBS.get(jobIndex);
            for (var job : JOBS) helper.assertValueEqual(amount(source, job.input()), 0L,
                    "Source chest cannot retain an earlier Federation Processing input");
            helper.assertValueEqual(amount(source, selection.output()), 0L,
                    "Selected callback output must not pre-exist in the physical source cell");
            helper.assertValueEqual(source.crafting().storage().insert(selection.input(), QUANTITY,
                    Actionable.MODULATE, source.requester().actionSource()), (long) QUANTITY,
                    "Physical source cell must accept selected typed Federation input");
            var sourceNode = source.crafting().node();
            plan = source.grid().getCraftingService().beginCraftingCalculation(helper.getLevel(),
                    new ICraftingSimulationRequester() {
                        @Override
                        public IActionSource getActionSource() {
                            return source.requester().actionSource();
                        }

                        @Override
                        public appeng.api.networking.IGridNode getGridNode() {
                            return sourceNode;
                        }
                    }, selection.output(), QUANTITY, CalculationStrategy.REPORT_MISSING_ITEMS);
            stage = 3;
        }

        private void finishJob(ScaleSourceHosts.Scene source) {
            var selection = JOBS.get(jobIndex);
            var target = targets.get(jobIndex);
            var machine = machines.get(jobIndex);
            var lane = source.hosts().get(jobIndex).lane(0);
            var owner = Integer.toUnsignedString(System.identityHashCode(machine));
            var returnOwner = Integer.toUnsignedString(System.identityHashCode(lane.getReturnInv()));
            var transitions = MixedFactoryObservation.machineTransitions();
            var selected = transitions.stream().filter(receipt -> receipt.machineOwner().equals(owner)).toList();
            helper.assertTrue(transitions.size() == (jobIndex + 1) * QUANTITY && selected.size() == QUANTITY
                            && selected.stream().allMatch(receipt -> receipt.returnOwner().equals(returnOwner)
                                    && receipt.input().equals(selection.input().getId().toString())
                                    && receipt.output().equals(selection.output().getId().toString())
                                    && receipt.accepted() == 1 && receipt.consumed() == 1 && receipt.produced() == 1),
                    "Only the selected physical Federation machine may perform sixteen typed native transitions");
            for (int index = 0; index < TARGET_COUNT; index++) {
                var machineOwner = Integer.toUnsignedString(System.identityHashCode(machines.get(index)));
                helper.assertValueEqual(transitions.stream()
                                .filter(receipt -> receipt.machineOwner().equals(machineOwner)).count(),
                        index <= jobIndex ? (long) QUANTITY : 0L,
                        "Unselected Federation machine cannot transition: " + index);
                helper.assertValueEqual(targets.get(index).target().inputAmount(selection.input()), 0L,
                        "Selected input cannot remain in any target physical cell: " + index);
                helper.assertValueEqual(machines.get(index).inputCount(selection.input().getItem()), 0L,
                        "Selected input cannot remain in any target machine: " + index);
                helper.assertTrue(source.hosts().get(index).lane(0).getReturnInv().isEmpty(),
                        "Every source Provider native return owner must drain: " + index);
            }
            helper.assertTrue(source.requester().uniqueNativeJobCount() == jobIndex + 1
                            && jobIds.equals(Set.copyOf(List.of(source.requester().nativeJobIds().split(","))))
                            && source.requester().acceptedAmount(selection.output()) == QUANTITY
                            && source.requester().acceptedAmount() == (jobIndex + 1) * QUANTITY
                            && !source.requester().observedCanceled()
                            && source.grid().getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                    "Distinct native links must retire with exact typed callback on the original CPU");
            target.assertReady();
            helper.assertTrue(target.target().returnHandler() != null,
                    "Physical Endpoint item return capability must remain accessible");
            helper.assertValueEqual(amount(source, selection.input()), 0L, "Source typed input fully consumed");
            helper.assertValueEqual(amount(source, selection.output()), (long) QUANTITY,
                    "Native callback must reach the physical source chest before Drive retention");
            source.retention().retain(JOBS, jobIndex);
            for (var job : JOBS) helper.assertValueEqual(amount(source, job.output()), 0L,
                    "Every callback output must be retained in the mounted source Drive");
            LOGGER.info("AE2F_SCALE_LARGE_FEDERATION_SIXTEEN_TARGETS job={} sourceGrid={} targetGrid={} "
                            + "sourceId={} targetId={} host={} bridge={} endpoint={} machine={} "
                            + "machineOwner={} returnOwner={} jobId={} inputKey={} outputKey={} planner=true "
                            + "route=ACTIVE targetInputBeforeExport=16 transitions={} accepted={} consumed={} "
                            + "produced={} callback={} retainedUnits={} sourceInput=0 targetInput=0 "
                            + "cpuIdle=true returnsEmpty=true",
                    jobIndex + 1, System.identityHashCode(source.grid()), System.identityHashCode(grids.get(jobIndex)),
                    source.id().value(), ids.get(jobIndex).value(), HOSTS.get(jobIndex), target.bridgePosition(),
                    target.endpointPosition(), target.endpointPosition().south(), owner, returnOwner,
                    jobIds.stream().skip(jobIndex).findFirst().orElseThrow(), selection.input().getId(),
                    selection.output().getId(), selected.size(),
                    selected.stream().mapToLong(MixedFactoryObservation.HandlerReceipt::accepted).sum(),
                    selected.stream().mapToLong(MixedFactoryObservation.HandlerReceipt::consumed).sum(),
                    selected.stream().mapToLong(MixedFactoryObservation.HandlerReceipt::produced).sum(),
                    source.requester().acceptedAmount(selection.output()), (jobIndex + 1) * QUANTITY);
        }

        private static long amount(ScaleSourceHosts.Scene source, AEItemKey key) {
            return source.crafting().storage().extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
        }
    }
}
