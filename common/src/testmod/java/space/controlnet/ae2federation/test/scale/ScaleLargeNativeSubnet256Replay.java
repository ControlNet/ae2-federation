package space.controlnet.ae2federation.test.scale;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
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
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.test.mixed.MixedFactoryObservation;
import space.controlnet.ae2federation.test.mixed.MixedMachineBlockEntity;

public final class ScaleLargeNativeSubnet256Replay {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScaleLargeNativeSubnet256Replay.class);
    private static final int TARGET_COUNT = 16;
    private static final int SLOTS_PER_HOST = 16;
    private static final int JOB_COUNT = TARGET_COUNT * SLOTS_PER_HOST;
    private static final int QUANTITY = 16;
    private static final List<BlockPos> HOSTS = IntStream.range(0, TARGET_COUNT)
            .mapToObj(index -> new BlockPos(7 + 8 * (index % 4), 6, 5 + 8 * (index / 4))).toList();

    private ScaleLargeNativeSubnet256Replay() {
    }

    public static void run(GameTestHelper helper) {
        var replay = new Replay(helper, false);
        ScaleSourceHosts.run(helper, replay::tick, SLOTS_PER_HOST, 5, false);
    }

    public static void runTimed(GameTestHelper helper) {
        var replay = new Replay(helper, true);
        ScaleSourceHosts.run(helper, replay::tick, SLOTS_PER_HOST, 5, false);
    }

    private static final class Replay {
        private final GameTestHelper helper;
        private final List<ScaleNativeSubnetTarget> targets = new ArrayList<>();
        private final List<IGrid> grids = new ArrayList<>();
        private final List<NetworkId> ids = new ArrayList<>();
        private final List<ScaleNativeProcessingProbe.CatalogSelection> jobs = new ArrayList<>();
        private final Set<String> jobIds = new LinkedHashSet<>();
        private final Set<ChunkPos> podChunks = new HashSet<>();
        private final boolean timed;
        private ScaleTimedWindow window;
        private int cycle;
        private int totalJobs;
        private boolean warmupReported;
        private Future<ICraftingPlan> plan;
        private int stage;
        private int jobIndex;
        private int waitTicks;

        private Replay(GameTestHelper helper, boolean timed) {
            this.helper = helper;
            this.timed = timed;
        }

        private int hostIndex() {
            return jobIndex / SLOTS_PER_HOST;
        }

        private int localSlot() {
            return jobIndex % SLOTS_PER_HOST;
        }

        private boolean tick(ScaleSourceHosts.Scene source) {
            if (stage == 0) {
                preflight(source);
                for (var host : HOSTS) targets.add(new ScaleNativeSubnetTarget(helper, true, true,
                        host.east(), Direction.EAST));
                stage = 1;
                return false;
            }
            if (stage == 1) {
                for (int index = 0; index < targets.size(); index++) {
                    if (!targets.get(index).ready(source.crafting().node())) {
                        if (++waitTicks > 50) helper.fail("Subnet target not ready: " + index);
                        return false;
                    }
                }
            }
            helper.assertValueEqual(targets.size(), TARGET_COUNT, "Sixteen physical subnet pods after source settlement");
            if (stage == 1) {
                waitTicks = 0;
                assertGridLayout(source, true);
                install(source);
                stage = 2;
                return false;
            }
            if (stage == 2) {
                assertGridLayout(source, false);
                var craftables = source.grid().getCraftingService().getCraftables(key -> true);
                if (craftables.size() < JOB_COUNT - 1) {
                    if (++waitTicks > 50) helper.fail("Native subnet publication stalled at " + craftables.size()
                            + " craftables; laneCounts=" + source.hosts().stream()
                                    .map(host -> host.lane(0).getAvailablePatterns().size()).toList());
                    return false;
                }
                helper.assertValueEqual(craftables.size(), JOB_COUNT,
                        "Missing 256th distinct physical subnet Pattern after native publication settled");
                assertCatalog(source);
                if (timed) window = new ScaleTimedWindow(300, 600, JOB_COUNT, QUANTITY,
                        System.nanoTime(), helper.getLevel().getServer().getTickCount());
                stage = 3;
            }
            if (stage == 3) {
                beginJob(source);
                return false;
            }
            var selection = jobs.get(jobIndex);
            if (stage == 4) {
                if (!plan.isDone()) return false;
                try {
                    var result = plan.get();
                    helper.assertTrue(!result.simulation() && result.finalOutput().what().equals(selection.output())
                                    && result.finalOutput().amount() == QUANTITY,
                            "Native subnet planner must calculate sixteen outputs for Pattern " + jobIndex);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Subnet catalog planner interrupted", exception);
                } catch (ExecutionException exception) {
                    throw new IllegalStateException("Subnet catalog planner failed", exception);
                }
                stage = 5;
            }
            if (stage == 5) {
                if (!source.requester().handleCrafting(selection.output(), QUANTITY, helper.getLevel(),
                        source.grid().getCraftingService())) return false;
                var link = source.requester().submittedLink();
                helper.assertTrue(link != null && !link.isCanceled() && jobIds.add(link.getCraftingID().toString()),
                        "Each physical Pattern needs a distinct native requester/CPU link: " + jobIndex);
                stage = 6;
                return false;
            }
            if (stage == 6) {
                for (int other = 0; other < TARGET_COUNT; other++) {
                    if (other != hostIndex()) helper.assertValueEqual(targets.get(other).inputAmount(selection.input()),
                            0L, "Unselected native target must not receive selected input: " + other);
                }
                if (targets.get(hostIndex()).inputAmount(selection.input()) != QUANTITY) {
                    if (++waitTicks > 120) helper.fail("Host " + hostIndex() + " slot " + localSlot()
                            + " missing physical target input; source=" + amount(source, selection.input())
                            + " target=" + targets.get(hostIndex()).inputAmount(selection.input()));
                    return false;
                }
                helper.assertValueEqual(amount(source, selection.input()), 0L,
                        "Source input must move through the selected physical Interface into its target cell");
                targets.get(hostIndex()).export(selection.input());
                stage = 7;
                waitTicks = 0;
                return false;
            }
            if (!source.requester().observedDone() || source.requester().activeLink() != null
                    || source.requester().acceptedAmount(selection.output()) != (cycle + 1L) * QUANTITY) {
                if (++waitTicks > 2000) helper.fail("Host " + hostIndex() + " slot " + localSlot()
                        + " missing machine/return/callback; targetInput="
                        + targets.get(hostIndex()).inputAmount(selection.input()) + " machineInput="
                        + targets.get(hostIndex()).machine().inputCount(selection.input().getItem()) + " callback="
                        + source.requester().acceptedAmount(selection.output()));
                return false;
            }
            finishJob(source);
            totalJobs++;
            boolean complete = timed && window.completedJob(System.nanoTime(),
                    helper.getLevel().getServer().getTickCount());
            if (timed && !warmupReported && window.warmup() != null) {
                ScaleTimedNativeEvidence.report(LOGGER, "native-subnet", "warmup", window.warmup());
                warmupReported = true;
            }
            if (timed && complete) ScaleTimedNativeEvidence.report(LOGGER, "native-subnet", "sample", window.sample());
            if (++jobIndex < JOB_COUNT && !complete) {
                stage = 3;
                plan = null;
                waitTicks = 0;
                return false;
            }
            if (timed && !complete) {
                helper.assertValueEqual(jobIndex, JOB_COUNT, "One complete physical 256-Pattern subnet replay");
                source.retention().drainCompletedReplay(jobs);
                MixedFactoryObservation.clearCompletedReplay();
                cycle++;
                jobIndex = 0;
                stage = 3;
                plan = null;
                waitTicks = 0;
                return false;
            }
            helper.assertValueEqual(jobIds.size(), totalJobs, "Each subnet job needs a different native link");
            helper.assertValueEqual(source.requester().acceptedAmount(), totalJobs * (long) QUANTITY,
                    "Every actual subnet callback unit must be accounted");
            helper.assertValueEqual(source.requester().acceptedAmount(),
                    cycle * (long) JOB_COUNT * QUANTITY + jobIndex * (long) QUANTITY,
                    "Drained and still-retained subnet Drive units reconcile");
            if (timed) ScaleTimedNativeEvidence.write("scalesmallnativesubnettimed", window, totalJobs,
                    cycle * (long) JOB_COUNT * QUANTITY, jobIndex * (long) QUANTITY);
            LOGGER.info("AE2F_SCALE_LARGE_SUBNET_256_COMPLETE physicalPatterns={} submissions={} uniqueLinks={} "
                            + "callbackUnits={} retainedUnits={} drainedUnits={} targetGrids={}", jobs.size(), totalJobs,
                    jobIds.size(), source.requester().acceptedAmount(), jobIndex * QUANTITY,
                    cycle * JOB_COUNT * QUANTITY, grids.size());
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
                        "Subnet Interface must face its own physical Provider BE: " + index);
                var chest = host.east(2);
                for (var position : List.of(host.east(), chest, chest.below(), chest.north(), chest.east().north())) {
                    var world = helper.absolutePos(position);
                    helper.assertTrue(positions.add(position)
                                    && helper.getBounds().contains(world.getX() + 0.5, world.getY() + 0.5,
                                            world.getZ() + 0.5)
                                    && helper.getLevel().isLoaded(world)
                                    && helper.getLevel().getBlockEntity(world) == null
                                    && (helper.getBlockState(position).isAir()
                                            || helper.getBlockState(position).is(Blocks.BARRIER)),
                            "Pod position must be unique, bounded, loaded and BE-free: " + position);
                    podChunks.add(new ChunkPos(world));
                }
            }
            helper.assertValueEqual(positions.size(), TARGET_COUNT * 5, "Eighty separate physical pod positions");
        }

        private void assertGridLayout(ScaleSourceHosts.Scene source, boolean initial) {
            var uniqueGrids = Collections.newSetFromMap(new IdentityHashMap<IGrid, Boolean>());
            var uniqueIds = new HashSet<NetworkId>();
            var chests = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
            var interfaces = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
            var machines = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
            var handlers = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
            var returns = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
            uniqueGrids.add(source.grid());
            uniqueIds.add(source.id());
            helper.assertTrue(source.crafting().cpuCount() == 1 && source.crafting().cpuNode().getGrid() == source.grid()
                            && source.requester().isReady(source.crafting().node())
                            && source.retention().ready(source.crafting().node()),
                    "Original source CPU, requester and five-cell Drive must remain mounted");
            for (int index = 0; index < TARGET_COUNT; index++) {
                var host = HOSTS.get(index);
                var chest = helper.<MEChestBlockEntity>getBlockEntity(host.east(2));
                var interfaceEntity = helper.<InterfaceBlockEntity>getBlockEntity(host.east());
                var machine = targets.get(index).machine();
                var grid = targets.get(index).grid();
                var id = FederationDomainRegistryAccess.confirmedNetworkId(grid).orElseThrow();
                var handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                        helper.absolutePos(host.east(3).north()), Direction.WEST);
                helper.assertTrue(uniqueGrids.add(grid) && uniqueIds.add(id) && chests.add(chest)
                                && interfaces.add(interfaceEntity) && machines.add(machine) && handlers.add(handler)
                                && returns.add(source.hosts().get(index).lane(0).getReturnInv())
                                && grid != source.grid() && chest.getMainNode().getNode().getGrid() == grid
                                && interfaceEntity.getMainNode().getNode().getGrid() == grid
                                && handler == machine.inputHandler(),
                        "Distinct actual target Grid/ID/Interface/Chest/machine/handler/return: " + index);
                if (initial) {
                    grids.add(grid);
                    ids.add(id);
                } else helper.assertTrue(grids.get(index) == grid && ids.get(index).equals(id),
                        "Target Grid/identity changed during replay: " + index);
            }
            helper.assertValueEqual(uniqueGrids.size(), TARGET_COUNT + 1, "Source plus sixteen native target Grids");
            helper.assertValueEqual(uniqueIds.size(), TARGET_COUNT + 1, "Source plus sixteen confirmed identities");
            if (initial) LOGGER.info("AE2F_SCALE_LARGE_SUBNET_256_TOPOLOGY sourceGrid={} sourceId={} targetGrids={} "
                            + "targetIds={} sourceCpu=1 sourceDriveCells=5 sourceEnergy=1 targetInterfaces=16 "
                            + "targetChests=16 targetCells=16 targetEnergy=16 targetExportBuses=16 "
                            + "targetMachines=16 sourcePowered={} targetPowered={} loadedPodChunks={} podChunks={}",
                    System.identityHashCode(source.grid()), source.id().value(),
                    grids.stream().map(System::identityHashCode).toList(), ids.stream().map(NetworkId::value).toList(),
                    source.grid().getEnergyService().isNetworkPowered(),
                    grids.stream().map(grid -> grid.getEnergyService().isNetworkPowered()).toList(),
                    podChunks.size(), podChunks.stream().map(ChunkPos::toString).sorted().toList());
        }

        private void install(ScaleSourceHosts.Scene source) {
            MixedFactoryObservation.begin(0, false);
            for (int hostIndex = 0; hostIndex < TARGET_COUNT; hostIndex++) {
                var host = source.hosts().get(hostIndex);
                var machine = targets.get(hostIndex).machine();
                var returns = host.lane(0).getReturnInv();
                helper.assertValueEqual(host.composition().patternInventory().size(), SLOTS_PER_HOST,
                        "Each physical source Provider must have sixteen Pattern slots");
                var recipes = new ArrayList<MixedMachineBlockEntity.MachineRecipe>();
                for (int slot = 0; slot < SLOTS_PER_HOST; slot++) {
                    int global = hostIndex * SLOTS_PER_HOST + slot;
                    var recipe = ScaleProcessingCatalog.recipes().get(global);
                    var input = AEItemKey.of(recipe.input());
                    var output = AEItemKey.of(recipe.output());
                    jobs.add(new ScaleNativeProcessingProbe.CatalogSelection(slot, input, output));
                    host.setPattern(slot, List.of(new GenericStack(input, 1)),
                            List.of(new GenericStack(output, 1)));
                    recipes.add(new MixedMachineBlockEntity.MachineRecipe(recipe.input(), recipe.output(), false,
                            returns, new GenericStackItemStorage(returns)));
                    MixedFactoryObservation.authorizeMachine(machine, machine.inputHandler(), machine.outputInventory(),
                            returns, input, output);
                }
                host.refreshPatterns();
                machine.processSingleItemPerTick();
                machine.configure(recipes);
            }
            helper.assertValueEqual(jobs.size(), JOB_COUNT, "Catalog selection count before publication");
        }

        private void assertCatalog(ScaleSourceHosts.Scene source) {
            var inputs = new HashSet<AEItemKey>();
            var outputs = new HashSet<AEItemKey>();
            for (int index = 0; index < JOB_COUNT; index++) {
                int hostIndex = index / SLOTS_PER_HOST;
                int slot = index % SLOTS_PER_HOST;
                var host = source.hosts().get(hostIndex);
                var lane = host.lane(0);
                var selection = jobs.get(index);
                var decoded = PatternDetailsHelper.decodePattern(
                        host.composition().patternInventory().getStackInSlot(slot), helper.getLevel());
                var machine = targets.get(hostIndex).machine();
                helper.assertTrue(decoded != null && decoded.getInputs().length == 1
                                && decoded.getInputs()[0].getPossibleInputs().length == 1
                                && decoded.getInputs()[0].getPossibleInputs()[0].what().equals(selection.input())
                                && decoded.getInputs()[0].getPossibleInputs()[0].amount() == 1
                                && decoded.getOutputs().size() == 1
                                && decoded.getPrimaryOutput().what().equals(selection.output())
                                && decoded.getPrimaryOutput().amount() == 1
                                && lane.getAvailablePatterns().contains(decoded)
                                && host.publishedProviders(decoded).equals(List.of(lane))
                                && source.grid().getCraftingService().getCraftingFor(selection.output()).size() == 1
                                && source.grid().getCraftingService().getCraftingFor(selection.output()).contains(decoded)
                                && machine.recipeCount() == SLOTS_PER_HOST
                                && machine.inputHandler().isItemValid(0, new ItemStack(selection.input().getItem())),
                        "Decoded Pattern must belong only to its assigned subnet Lane/machine: " + index);
                helper.assertTrue(inputs.add(selection.input()) && outputs.add(selection.output()),
                        "Physical input/output type must be unique: " + index);
                for (int other = 0; other < TARGET_COUNT; other++) {
                    if (other != hostIndex) helper.assertTrue(
                            !source.hosts().get(other).lane(0).getAvailablePatterns().contains(decoded)
                                    && !targets.get(other).machine().inputHandler().isItemValid(0,
                                            new ItemStack(selection.input().getItem())),
                            "Unselected subnet Lane and machine must reject physical Pattern: " + index);
                }
            }
            helper.assertTrue(inputs.size() == JOB_COUNT && outputs.size() == JOB_COUNT
                            && Collections.disjoint(inputs, outputs)
                            && source.grid().getCraftingService().getCraftables(key -> true).equals(outputs),
                    "Exactly 256 distinct disjoint native source Patterns must be craftable");
            for (var host : source.hosts()) helper.assertValueEqual(host.lane(0).getAvailablePatterns().size(),
                    SLOTS_PER_HOST, "Sixteen decoded Patterns per physical source host");
            LOGGER.info("AE2F_SCALE_LARGE_SUBNET_256_CATALOG physicalPatterns={} hosts={} slotsPerHost={} "
                            + "recipesPerMachine={} submissions=0", jobs.size(), TARGET_COUNT, SLOTS_PER_HOST,
                    SLOTS_PER_HOST);
        }

        private void beginJob(ScaleSourceHosts.Scene source) {
            if (localSlot() == 0) assertGridLayout(source, false);
            var selection = jobs.get(jobIndex);
            helper.assertValueEqual(amount(source, selection.input()), 0L, "Source input before native job");
            helper.assertValueEqual(amount(source, selection.output()), 0L, "Callback chest before native job");
            helper.assertValueEqual(targets.get(hostIndex()).inputAmount(selection.input()), 0L,
                    "Selected physical subnet cell before job");
            helper.assertValueEqual(source.crafting().storage().insert(selection.input(), QUANTITY,
                    Actionable.MODULATE, source.requester().actionSource()), (long) QUANTITY,
                    "Physical source cell must accept sixteen typed inputs");
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
            stage = 4;
        }

        private void finishJob(ScaleSourceHosts.Scene source) {
            var selection = jobs.get(jobIndex);
            var machine = targets.get(hostIndex()).machine();
            var returns = source.hosts().get(hostIndex()).lane(0).getReturnInv();
            var owner = Integer.toUnsignedString(System.identityHashCode(machine));
            var returnOwner = Integer.toUnsignedString(System.identityHashCode(returns));
            var transitions = MixedFactoryObservation.machineTransitions();
            var selected = transitions.stream().filter(receipt -> receipt.machineOwner().equals(owner)
                    && receipt.input().equals(selection.input().getId().toString())
                    && receipt.output().equals(selection.output().getId().toString())).toList();
            helper.assertTrue(transitions.size() == (jobIndex + 1) * QUANTITY && selected.size() == QUANTITY
                            && selected.stream().allMatch(receipt -> receipt.returnOwner().equals(returnOwner)
                                    && receipt.accepted() == 1 && receipt.consumed() == 1 && receipt.produced() == 1),
                    "Selected physical machine must perform sixteen typed one-item transitions: " + jobIndex);
            for (int index = 0; index < TARGET_COUNT; index++) {
                int completed = Math.max(0, Math.min(SLOTS_PER_HOST, jobIndex + 1 - index * SLOTS_PER_HOST));
                var machineOwner = Integer.toUnsignedString(System.identityHashCode(targets.get(index).machine()));
                helper.assertValueEqual(transitions.stream()
                        .filter(receipt -> receipt.machineOwner().equals(machineOwner)).count(),
                        completed * (long) QUANTITY, "Unselected target machine transition count: " + index);
                helper.assertValueEqual(targets.get(index).inputAmount(selection.input()), 0L,
                        "Selected typed input must drain from every target cell: " + index);
                helper.assertValueEqual(targets.get(index).machine().inputCount(selection.input().getItem()), 0L,
                        "Selected typed input must drain from every machine: " + index);
                helper.assertTrue(source.hosts().get(index).lane(0).getReturnInv().isEmpty(),
                        "Every native return owner must drain: " + index);
            }
            helper.assertTrue(source.requester().uniqueNativeJobCount() == totalJobs + 1
                            && jobIds.equals(Set.copyOf(List.of(source.requester().nativeJobIds().split(","))))
                            && source.requester().acceptedAmount(selection.output()) == (cycle + 1L) * QUANTITY
                            && source.requester().acceptedAmount() == (totalJobs + 1L) * QUANTITY
                            && !source.requester().observedCanceled()
                            && source.grid().getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                    "Native UUID, retired CPU link and typed callback must match job " + jobIndex);
            helper.assertValueEqual(amount(source, selection.input()), 0L, "Source typed input consumed");
            helper.assertValueEqual(amount(source, selection.output()), (long) QUANTITY,
                    "Real native callback output must occupy source chest before Drive transfer");
            source.retention().retain(jobs, jobIndex);
            LOGGER.info("AE2F_SCALE_LARGE_SUBNET_256 job={} physicalPatterns={} submissions={} host={} slot={} "
                            + "sourceGrid={} targetGrid={} targetId={} jobId={} machineOwner={} returnOwner={} "
                            + "inputKey={} outputKey={} targetInputBeforeExport=16 transitions={} callback={} "
                            + "retainedUnits={} sourceInput=0 targetInput=0 chestOutput=0 cpuIdle=true returnsEmpty=true",
                    jobIndex + 1, jobs.size(), jobIds.size(), hostIndex(), localSlot(),
                    System.identityHashCode(source.grid()), System.identityHashCode(grids.get(hostIndex())),
                    ids.get(hostIndex()).value(), source.requester().submittedLink().getCraftingID(),
                    owner, returnOwner, selection.input().getId(), selection.output().getId(), selected.size(),
                    source.requester().acceptedAmount(selection.output()), (jobIndex + 1) * QUANTITY);
        }

        private static long amount(ScaleSourceHosts.Scene source, AEItemKey key) {
            return source.crafting().storage().extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
        }
    }
}
