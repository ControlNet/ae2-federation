package space.controlnet.ae2federation.test.scale;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.test.mixed.MixedFactoryObservation;
import space.controlnet.ae2federation.test.mixed.MixedMachineBlockEntity;
import space.controlnet.ae2federation.test.processing.ProcessingRegressionFixtures;

public final class ScaleLargeNativeSubnetSixteenTargets {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScaleLargeNativeSubnetSixteenTargets.class);
    private static final int TARGET_COUNT = 16;
    private static final int QUANTITY = 16;
    private static final List<BlockPos> HOSTS = IntStream.range(0, TARGET_COUNT)
            .mapToObj(index -> new BlockPos(7 + 8 * (index % 4), 6, 5 + 8 * (index / 4))).toList();
    private static final List<ScaleNativeProcessingProbe.CatalogSelection> JOBS = IntStream.range(0, TARGET_COUNT)
            .mapToObj(index -> ScaleProcessingCatalog.recipes().get(index))
            .map(recipe -> new ScaleNativeProcessingProbe.CatalogSelection(0,
                    AEItemKey.of(recipe.input()), AEItemKey.of(recipe.output()))).toList();

    private ScaleLargeNativeSubnetSixteenTargets() {
    }

    public static void run(GameTestHelper helper) {
        var target = new Target(helper);
        ScaleSourceHosts.run(helper, target::tick);
    }

    private static final class Target {
        private final GameTestHelper helper;
        private final List<ScaleNativeSubnetTarget> targets = new ArrayList<>();
        private final List<IGrid> grids = new ArrayList<>();
        private final List<NetworkId> ids = new ArrayList<>();
        private final Set<String> jobIds = new LinkedHashSet<>();
        private final Set<ChunkPos> podChunks = new HashSet<>();
        private Future<ICraftingPlan> plan;
        private int stage;
        private int jobIndex;
        private int waitTicks;

        private Target(GameTestHelper helper) {
            this.helper = helper;
        }

        private boolean tick(ScaleSourceHosts.Scene source) {
            if (stage == 0) {
                preflight(source);
                for (var host : HOSTS) {
                    targets.add(new ScaleNativeSubnetTarget(helper, true, true, host.east(), Direction.EAST));
                }
                stage = 1;
                return false;
            }
            for (var target : targets) {
                if (!target.ready(source.crafting().node())) return false;
            }
            helper.assertValueEqual(targets.size(), TARGET_COUNT,
                    "Missing sixteenth physical native-subnet target after all source channels settled");
            if (stage == 1) {
                assertGridLayout(source, true);
                installDestinations(source);
                MixedFactoryObservation.begin(0, false);
                for (int index = 0; index < TARGET_COUNT; index++) {
                    var selection = JOBS.get(index);
                    var machine = targets.get(index).machine();
                    var returns = source.hosts().get(index).lane(0).getReturnInv();
                    MixedFactoryObservation.authorizeMachine(machine, machine.inputHandler(),
                            machine.outputInventory(), returns, selection.input(), selection.output());
                }
                stage = 2;
                return false;
            }
            assertGridLayout(source, false);
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
                            "Native subnet planner must calculate sixteen outputs for host " + jobIndex);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Native subnet planner interrupted", exception);
                } catch (ExecutionException exception) {
                    throw new IllegalStateException("Native subnet planner failed", exception);
                }
                stage = 4;
            }
            if (stage == 4) {
                if (!source.requester().handleCrafting(selection.output(), QUANTITY, helper.getLevel(),
                        source.grid().getCraftingService())) return false;
                var link = source.requester().submittedLink();
                helper.assertTrue(link != null && !link.isCanceled() && jobIds.add(link.getCraftingID().toString()),
                        "Each native subnet must submit a distinct live CPU link: " + jobIndex);
                stage = 5;
                return false;
            }
            if (stage == 5) {
                for (int index = 0; index < TARGET_COUNT; index++) {
                    if (index != jobIndex) helper.assertValueEqual(targets.get(index).inputAmount(selection.input()), 0L,
                            "Unselected subnet must not receive selected typed input: " + index);
                }
                if (targets.get(jobIndex).inputAmount(selection.input()) != QUANTITY) {
                    if (++waitTicks > 120) helper.fail("Selected subnet " + jobIndex + " missing native inputs: source="
                            + amount(source, selection.input()) + " target="
                            + targets.get(jobIndex).inputAmount(selection.input()));
                    return false;
                }
                helper.assertValueEqual(amount(source, selection.input()), 0L,
                        "Source cell must release selected input to its physical target Interface");
                targets.get(jobIndex).export(selection.input());
                stage = 6;
                waitTicks = 0;
                return false;
            }
            if (!source.requester().observedDone() || source.requester().activeLink() != null
                    || source.requester().acceptedAmount(selection.output()) != QUANTITY) {
                if (++waitTicks > 2000) helper.fail("Selected subnet " + jobIndex
                        + " missing physical machine output or native requester callback: targetInput="
                        + targets.get(jobIndex).inputAmount(selection.input()) + " machineInput="
                        + targets.get(jobIndex).machine().inputCount(selection.input().getItem())
                        + " transitions=" + MixedFactoryObservation.machineTransitions().stream()
                                .filter(receipt -> receipt.machineOwner().equals(Integer.toUnsignedString(
                                        System.identityHashCode(targets.get(jobIndex).machine())))).count()
                        + " callback=" + source.requester().acceptedAmount(selection.output())
                        + " returnEmpty=" + source.hosts().get(jobIndex).lane(0).getReturnInv().isEmpty());
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
            targets.forEach(ScaleNativeSubnetTarget::close);
            return true;
        }

        private void preflight(ScaleSourceHosts.Scene source) {
            var positions = new HashSet<BlockPos>();
            for (int index = 0; index < TARGET_COUNT; index++) {
                var host = HOSTS.get(index);
                helper.assertTrue(source.hosts().get(index).hostBlockEntity()
                                == helper.getLevel().getBlockEntity(helper.absolutePos(host)),
                        "Subnet Interface must be adjacent to the exact active Provider BE: " + index);
                var interfacePosition = host.east();
                var chest = interfacePosition.east();
                var export = chest.north();
                for (var position : List.of(interfacePosition, chest, chest.below(), export, export.east())) {
                    var world = helper.absolutePos(position);
                    helper.assertTrue(positions.add(position)
                                    && helper.getBounds().contains(world.getX() + 0.5, world.getY() + 0.5,
                                            world.getZ() + 0.5)
                                    && helper.getLevel().isLoaded(world)
                                    && helper.getLevel().getBlockEntity(world) == null
                                    && (helper.getBlockState(position).isAir()
                                            || helper.getBlockState(position).is(Blocks.BARRIER)),
                            "Native subnet candidate must be unique, bounded, loaded and unoccupied: " + position);
                    podChunks.add(new ChunkPos(world));
                }
            }
            helper.assertValueEqual(positions.size(), TARGET_COUNT * 5,
                    "Sixteen isolated native subnet pods require eighty unique block positions");
        }

        private void assertGridLayout(ScaleSourceHosts.Scene source, boolean initial) {
            var uniqueGrids = Collections.newSetFromMap(new IdentityHashMap<IGrid, Boolean>());
            var uniqueIds = new HashSet<NetworkId>();
            var uniqueChestEntities = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
            var uniqueInterfaces = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
            var uniqueMachines = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
            var uniqueHandlers = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
            var uniqueReturns = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
            uniqueGrids.add(source.grid());
            uniqueIds.add(source.id());
            helper.assertTrue(source.crafting().cpuCount() == 1 && source.crafting().cpuNode().getGrid() == source.grid()
                            && source.requester().isReady(source.crafting().node())
                            && source.retention().ready(source.crafting().node()),
                    "Original source Grid must retain its sole CPU, requester and mounted Drive");
            for (int index = 0; index < TARGET_COUNT; index++) {
                var host = HOSTS.get(index);
                var target = targets.get(index);
                var chest = helper.<MEChestBlockEntity>getBlockEntity(host.east(2));
                var interfaceEntity = helper.<InterfaceBlockEntity>getBlockEntity(host.east());
                var machine = target.machine();
                var lane = source.hosts().get(index).lane(0);
                var grid = target.grid();
                var id = FabricRegistryAccess.confirmedNetworkId(grid).orElseThrow();
                var handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                        helper.absolutePos(host.east(3).north()), Direction.WEST);
                helper.assertTrue(uniqueGrids.add(grid) && uniqueIds.add(id)
                                && uniqueChestEntities.add(chest) && uniqueInterfaces.add(interfaceEntity)
                                && uniqueMachines.add(machine) && uniqueHandlers.add(handler)
                                && uniqueReturns.add(lane.getReturnInv())
                                && grid != source.grid()
                                && chest.getMainNode().getNode().getGrid() == grid
                                && interfaceEntity.getMainNode().getNode().getGrid() == grid
                                && handler == machine.inputHandler() && machine.inputHandler() != null,
                        "Each native target needs a distinct confirmed Grid, Interface, Chest, machine and return owner: "
                                + index);
                if (initial) {
                    grids.add(grid);
                    ids.add(id);
                } else helper.assertTrue(grids.get(index) == grid && ids.get(index).equals(id),
                        "Native target Grid and confirmed identity must not change during serial jobs: " + index);
            }
            helper.assertValueEqual(uniqueGrids.size(), TARGET_COUNT + 1, "Source plus sixteen separate target Grids");
            helper.assertValueEqual(uniqueIds.size(), TARGET_COUNT + 1, "Source plus sixteen confirmed IDs");
            if (initial) LOGGER.info("AE2F_SCALE_LARGE_SUBNET_TOPOLOGY sourceGrid={} sourceId={} targetGrids={} "
                            + "targetIds={} sourceProviders=16 sourceCpu=1 sourceDrive=1 sourceEnergy=1 "
                            + "targetInterfaces=16 targetChests=16 targetCells=16 targetEnergy=16 "
                            + "targetExportBuses=16 targetMachines=16 podChunks={}",
                    System.identityHashCode(source.grid()), source.id().value(),
                    grids.stream().map(System::identityHashCode).toList(), ids.stream().map(NetworkId::value).toList(),
                    podChunks.stream().map(ChunkPos::toString).sorted().toList());
        }

        private void installDestinations(ScaleSourceHosts.Scene source) {
            var inputs = Set.copyOf(JOBS.stream().map(ScaleNativeProcessingProbe.CatalogSelection::input).toList());
            var outputs = Set.copyOf(JOBS.stream().map(ScaleNativeProcessingProbe.CatalogSelection::output).toList());
            helper.assertTrue(inputs.size() == TARGET_COUNT && outputs.size() == TARGET_COUNT
                            && Collections.disjoint(inputs, outputs),
                    "Sixteen source Patterns must use disjoint physical input/output types");
            for (int index = 0; index < TARGET_COUNT; index++) {
                var selection = JOBS.get(index);
                var machine = targets.get(index).machine();
                var returns = source.hosts().get(index).lane(0).getReturnInv();
                machine.processSingleItemPerTick();
                machine.configure(List.of(new MixedMachineBlockEntity.MachineRecipe(selection.input().getItem(),
                        selection.output().getItem(), false, returns, new GenericStackItemStorage(returns))));
                source.hosts().get(index).installPattern(0,
                        List.of(ProcessingRegressionFixtures.item(selection.input().getItem(), 1)),
                        List.of(ProcessingRegressionFixtures.item(selection.output().getItem(), 1)));
            }
        }

        private void assertPatterns(ScaleSourceHosts.Scene source) {
            helper.assertTrue(source.grid().getCraftingService().getCraftables(key -> true).equals(
                            Set.copyOf(JOBS.stream().map(ScaleNativeProcessingProbe.CatalogSelection::output).toList())),
                    "Exactly sixteen native source Patterns must be craftable");
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
                                && source.grid().getCraftingService().getCraftingFor(selection.output()).contains(decoded)
                                && targets.get(index).machine().recipeCount() == 1
                                && targets.get(index).machine().inputHandler().isItemValid(0,
                                        new ItemStack(selection.input().getItem())),
                        "Each real Provider must exclusively publish its typed Pattern to its subnet: " + index);
            }
        }

        private void beginJob(ScaleSourceHosts.Scene source) {
            var selection = JOBS.get(jobIndex);
            for (var job : JOBS) helper.assertValueEqual(amount(source, job.input()), 0L,
                    "Source chest cannot retain an earlier native Processing input");
            helper.assertValueEqual(amount(source, selection.output()), 0L,
                    "Selected output must not pre-exist in the callback chest");
            helper.assertValueEqual(source.crafting().storage().insert(selection.input(), QUANTITY,
                    Actionable.MODULATE, source.requester().actionSource()), (long) QUANTITY,
                    "Physical source cell must accept the selected typed input");
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
            var machine = targets.get(jobIndex).machine();
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
                    "Selected subnet must make exactly sixteen typed one-item capability transitions");
            for (int index = 0; index < TARGET_COUNT; index++) {
                var target = targets.get(index);
                var machineOwner = Integer.toUnsignedString(System.identityHashCode(target.machine()));
                helper.assertValueEqual(transitions.stream()
                                .filter(receipt -> receipt.machineOwner().equals(machineOwner)).count(),
                        index <= jobIndex ? (long) QUANTITY : 0L,
                        "Only a completed subnet machine may have physical transitions: " + index);
                helper.assertValueEqual(target.inputAmount(selection.input()), 0L,
                        "Selected input must be absent from every subnet cell after the job: " + index);
                helper.assertValueEqual(target.machine().inputCount(selection.input().getItem()), 0L,
                        "No subnet machine may retain selected typed input: " + index);
                helper.assertTrue(source.hosts().get(index).lane(0).getReturnInv().isEmpty(),
                        "Every native Provider return owner must drain: " + index);
            }
            helper.assertTrue(source.requester().uniqueNativeJobCount() == jobIndex + 1
                            && jobIds.equals(Set.copyOf(List.of(source.requester().nativeJobIds().split(","))))
                            && source.requester().acceptedAmount(selection.output()) == QUANTITY
                            && source.requester().acceptedAmount() == (jobIndex + 1) * QUANTITY
                            && !source.requester().observedCanceled()
                            && source.grid().getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                    "Each distinct native link must retire with exact per-key callback on the original CPU");
            helper.assertValueEqual(amount(source, selection.input()), 0L, "Source typed input fully consumed");
            helper.assertValueEqual(amount(source, selection.output()), (long) QUANTITY,
                    "Real native callback output must occupy the physical source chest");
            source.retention().retain(JOBS, jobIndex);
            for (var job : JOBS) helper.assertValueEqual(amount(source, job.output()), 0L,
                    "All callback output must be retained in the mounted source Drive");
            LOGGER.info("AE2F_SCALE_LARGE_SUBNET_SIXTEEN_TARGETS job={} sourceGrid={} targetGrid={} "
                            + "sourceId={} targetId={} host={} interface={} chest={} export={} machine={} "
                            + "machineOwner={} returnOwner={} jobId={} inputKey={} outputKey={} planner=true "
                            + "targetInputBeforeExport=16 transitions={} accepted={} consumed={} produced={} "
                            + "callback={} retainedUnits={} sourceInput=0 targetInput=0 cpuIdle=true returnsEmpty=true",
                    jobIndex + 1, System.identityHashCode(source.grid()), System.identityHashCode(grids.get(jobIndex)),
                    source.id().value(), ids.get(jobIndex).value(), HOSTS.get(jobIndex), HOSTS.get(jobIndex).east(),
                    HOSTS.get(jobIndex).east(2), HOSTS.get(jobIndex).east(2).north(),
                    HOSTS.get(jobIndex).east(3).north(), owner, returnOwner,
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
