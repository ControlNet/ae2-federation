package space.controlnet.ae2federation.test.scale;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.helpers.externalstorage.GenericStackItemStorage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Properties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.test.mixed.MixedFactoryObservation;
import space.controlnet.ae2federation.test.mixed.MixedMachineBlockEntity;
import space.controlnet.ae2federation.test.mixed.MixedMachineRegistration;

public final class ScaleLargeDirect256Replay {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScaleLargeDirect256Replay.class);
    private static final int HOST_COUNT = 16;
    private static final int SLOTS_PER_HOST = 16;
    private static final int JOB_COUNT = HOST_COUNT * SLOTS_PER_HOST;
    private static final int QUANTITY = 16;
    private static final List<BlockPos> HOSTS = IntStream.range(0, HOST_COUNT)
            .mapToObj(index -> new BlockPos(7 + 8 * (index % 4), 6, 5 + 8 * (index / 4))).toList();
    private static final List<BlockPos> MACHINES = HOSTS.stream().map(BlockPos::east).toList();

    private ScaleLargeDirect256Replay() {
    }

    public static void run(GameTestHelper helper) {
        var target = new Target(helper, false);
        ScaleSourceHosts.run(helper, target::tick, SLOTS_PER_HOST, 5);
    }

    public static void runTimed(GameTestHelper helper) {
        var target = new Target(helper, true);
        ScaleSourceHosts.run(helper, target::tick, SLOTS_PER_HOST, 5);
    }

    private static final class Target {
        private final GameTestHelper helper;
        private final MixedMachineBlockEntity[] machines = new MixedMachineBlockEntity[HOST_COUNT];
        private final List<ScaleNativeProcessingProbe.CatalogSelection> jobs = new ArrayList<>();
        private final Set<String> jobIds = new LinkedHashSet<>();
        private final boolean timed;
        private ScaleTimedWindow window;
        private int cycle;
        private int totalJobs;
        private boolean warmupReported;
        private Future<ICraftingPlan> plan;
        private int stage;
        private int jobIndex;
        private int waitTicks;

        private Target(GameTestHelper helper, boolean timed) {
            this.helper = helper;
            this.timed = timed;
        }

        private boolean tick(ScaleSourceHosts.Scene source) {
            if (stage == 0) {
                for (int index = 0; index < HOST_COUNT; index++) checkMachinePosition(source, index);
                for (var position : MACHINES) helper.setBlock(position, MixedMachineRegistration.BLOCK.get());
                stage = 1;
                return false;
            }
            if (stage == 1) {
                install(source);
                stage = 2;
                return false;
            }
            if (stage == 2) {
                if (source.grid().getCraftingService().getCraftables(key -> true).size() < JOB_COUNT - 1) return false;
                helper.assertValueEqual(source.grid().getCraftingService().getCraftables(key -> true).size(),
                        JOB_COUNT, "Missing 256th distinct physical Pattern after source publication settled");
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
                            "Native planner must calculate sixteen outputs for physical Pattern " + jobIndex);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Direct catalog planner interrupted", exception);
                } catch (ExecutionException exception) {
                    throw new IllegalStateException("Direct catalog planner failed", exception);
                }
                stage = 5;
            }
            if (stage == 5) {
                if (!source.requester().handleCrafting(selection.output(), QUANTITY, helper.getLevel(),
                        source.grid().getCraftingService())) return false;
                var link = source.requester().submittedLink();
                helper.assertTrue(link != null && !link.isCanceled() && jobIds.add(link.getCraftingID().toString()),
                        "Every physical Pattern requires a distinct live native link: " + jobIndex);
                stage = 6;
                return false;
            }
            if (!source.requester().observedDone() || source.requester().activeLink() != null
                    || source.requester().acceptedAmount(selection.output()) != (cycle + 1L) * QUANTITY) {
                if (++waitTicks > 120) helper.fail("Direct host " + hostIndex() + " slot " + localSlot()
                        + " missing native machine/callback work; sourceInput=" + amount(source, selection.input())
                        + " machineInput=" + machines[hostIndex()].inputCount(selection.input().getItem()));
                return false;
            }
            finishJob(source);
            totalJobs++;
            boolean complete = timed && window.completedJob(System.nanoTime(),
                    helper.getLevel().getServer().getTickCount());
            if (timed && !warmupReported && window.warmup() != null) {
                reportWindow("warmup", window.warmup());
                warmupReported = true;
            }
            if (timed && complete) reportWindow("sample", window.sample());
            if (++jobIndex < JOB_COUNT && !complete) {
                stage = 3;
                plan = null;
                waitTicks = 0;
                return false;
            }
            if (timed && !complete) {
                helper.assertValueEqual(jobIndex, JOB_COUNT, "One complete physical 256-Pattern replay");
                source.retention().drainCompletedReplay(jobs);
                MixedFactoryObservation.clearCompletedReplay();
                cycle++;
                jobIndex = 0;
                stage = 3;
                plan = null;
                waitTicks = 0;
                return false;
            }
            helper.assertValueEqual(jobIds.size(), totalJobs, "Every submitted native UUID remains distinct");
            helper.assertValueEqual(source.requester().acceptedAmount(), totalJobs * (long) QUANTITY,
                    "Every native callback unit must be accounted");
            helper.assertValueEqual(cycle * (long) JOB_COUNT * QUANTITY + jobIndex * (long) QUANTITY,
                    source.requester().acceptedAmount(), "Drained and still-retained Drive units reconcile");
            if (timed) writeTimedEvidence();
            LOGGER.info("AE2F_SCALE_LARGE_DIRECT_256_COMPLETE physicalPatterns={} submissions={} uniqueLinks={} "
                            + "callbackUnits={} retainedUnits={} drainedUnits={}", jobs.size(), totalJobs, jobIds.size(),
                    source.requester().acceptedAmount(), jobIndex * QUANTITY, cycle * JOB_COUNT * QUANTITY);
            MixedFactoryObservation.close();
            for (var position : MACHINES) helper.setBlock(position, Blocks.AIR);
            return true;
        }

        private int hostIndex() {
            return jobIndex / SLOTS_PER_HOST;
        }

        private int localSlot() {
            return jobIndex % SLOTS_PER_HOST;
        }

        private void install(ScaleSourceHosts.Scene source) {
            var hostOwners = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
            var machineOwners = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
            var returnOwners = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
            var handlers = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
            MixedFactoryObservation.begin(0, false);
            for (int hostIndex = 0; hostIndex < HOST_COUNT; hostIndex++) {
                var host = source.hosts().get(hostIndex);
                var entity = helper.getLevel().getBlockEntity(helper.absolutePos(MACHINES.get(hostIndex)));
                helper.assertTrue(entity instanceof MixedMachineBlockEntity,
                        "Physical machine missing after source settlement: " + hostIndex);
                var machine = (MixedMachineBlockEntity) entity;
                machines[hostIndex] = machine;
                var returns = host.lane(0).getReturnInv();
                helper.assertTrue(hostOwners.add(host.hostBlockEntity()) && machineOwners.add(machine)
                                && returnOwners.add(returns) && handlers.add(machine.inputHandler()),
                        "All sixteen direct hosts require distinct BEs, handlers and native return owners");
                helper.assertValueEqual(host.composition().patternInventory().size(), SLOTS_PER_HOST,
                        "Physical mapped Provider must have sixteen Pattern slots: " + hostIndex);
                var recipes = new ArrayList<MixedMachineBlockEntity.MachineRecipe>();
                for (int slot = 0; slot < SLOTS_PER_HOST; slot++) {
                    int global = hostIndex * SLOTS_PER_HOST + slot;
                    var recipe = ScaleProcessingCatalog.recipes().get(global);
                    var input = AEItemKey.of(recipe.input());
                    var output = AEItemKey.of(recipe.output());
                    jobs.add(new ScaleNativeProcessingProbe.CatalogSelection(slot, input, output));
                    host.setPattern(slot,
                            List.of(new appeng.api.stacks.GenericStack(input, 1)),
                            List.of(new appeng.api.stacks.GenericStack(output, 1)));
                    recipes.add(new MixedMachineBlockEntity.MachineRecipe(recipe.input(), recipe.output(), false,
                            returns, new GenericStackItemStorage(returns)));
                    MixedFactoryObservation.authorizeMachine(machine, machine.inputHandler(), machine.outputInventory(),
                            returns, input, output);
                }
                host.refreshPatterns();
                machine.processSingleItemPerTick();
                machine.configure(recipes);
            }
            helper.assertValueEqual(jobs.size(), JOB_COUNT, "Physical Pattern installation count");
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
                var node = ((PatternProviderBlockEntity) host.hostBlockEntity()).getMainNode().getNode();
                var decoded = PatternDetailsHelper.decodePattern(
                        host.composition().patternInventory().getStackInSlot(slot), helper.getLevel());
                var handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                        helper.absolutePos(MACHINES.get(hostIndex)), Direction.WEST);
                helper.assertTrue(node.getGrid() == source.grid() && node.isActive() && node.meetsChannelRequirements()
                                && node.getUsedChannels() > 0 && node.getInWorldConnections().containsKey(Direction.WEST)
                                && FabricRegistryAccess.confirmedNetworkId(node.getGrid()).filter(source.id()::equals).isPresent()
                                && decoded != null && decoded.getInputs().length == 1
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
                                && handler == machines[hostIndex].inputHandler()
                                && handler.isItemValid(0, new ItemStack(selection.input().getItem()))
                                && machines[hostIndex].recipeCount() == SLOTS_PER_HOST,
                        "Missing or misowned decoded physical Pattern/job " + index + " host=" + hostIndex + " slot=" + slot);
                helper.assertTrue(inputs.add(selection.input()) && outputs.add(selection.output()),
                        "Every physical Pattern must have disjoint unique typed keys: " + index);
                for (int other = 0; other < HOST_COUNT; other++) {
                    if (other != hostIndex) helper.assertTrue(
                            !source.hosts().get(other).lane(0).getAvailablePatterns().contains(decoded)
                                    && !machines[other].inputHandler().isItemValid(0,
                                            new ItemStack(selection.input().getItem())),
                            "Only the selected physical Lane and machine may own Pattern " + index);
                }
            }
            helper.assertTrue(inputs.size() == JOB_COUNT && outputs.size() == JOB_COUNT
                            && Collections.disjoint(inputs, outputs)
                            && source.grid().getCraftingService().getCraftables(key -> true).equals(outputs),
                    "Source Grid must publish exactly 256 distinct physical Processing Patterns");
            for (var host : source.hosts()) helper.assertValueEqual(host.lane(0).getAvailablePatterns().size(),
                    SLOTS_PER_HOST, "Sixteen decoded Patterns per physical Provider Lane");
            helper.assertTrue(source.retention().ready(source.crafting().node())
                            && source.requester().isReady(source.crafting().node())
                            && source.crafting().cpuNode().getGrid() == source.grid(),
                    "One source CPU/requester and five mounted Drive cells must be ready");
            var loadedChunks = new HashSet<Long>();
            for (var position : HOSTS) {
                var world = helper.absolutePos(position);
                helper.assertTrue(helper.getLevel().isLoaded(world), "Every physical host chunk must be loaded");
                loadedChunks.add(net.minecraft.world.level.ChunkPos.asLong(world.getX() >> 4, world.getZ() >> 4));
            }
            for (var position : MACHINES) {
                var world = helper.absolutePos(position);
                helper.assertTrue(helper.getLevel().isLoaded(world), "Every physical machine chunk must be loaded");
                loadedChunks.add(net.minecraft.world.level.ChunkPos.asLong(world.getX() >> 4, world.getZ() >> 4));
            }
            LOGGER.info("AE2F_SCALE_LARGE_DIRECT_256_LAYOUT physicalPatterns={} hosts={} recipesPerMachine={} "
                            + "submissions=0 sourceGrid={} gridChannels={} cpuCount={} mountedDriveCells={} "
                            + "networkPowered={} loadedHostMachineChunks={} chunkKeys={} hostPositions={}",
                    jobs.size(), HOST_COUNT, SLOTS_PER_HOST,
                    System.identityHashCode(source.grid()), source.grid().getPathingService().getUsedChannels(),
                    source.crafting().cpuCount(), 5, source.grid().getEnergyService().isNetworkPowered(),
                    loadedChunks.size(), loadedChunks, HOSTS);
        }

        private void beginJob(ScaleSourceHosts.Scene source) {
            var selection = jobs.get(jobIndex);
            helper.assertValueEqual(amount(source, selection.input()), 0L, "Source input before selected job");
            helper.assertValueEqual(amount(source, selection.output()), 0L, "Direct callback chest before job");
            helper.assertValueEqual(source.crafting().storage().insert(selection.input(), QUANTITY,
                    Actionable.MODULATE, source.requester().actionSource()), (long) QUANTITY,
                    "Physical source chest must accept sixteen selected inputs");
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
            var machine = machines[hostIndex()];
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
                    "Only the selected typed physical machine may perform sixteen transitions: " + jobIndex);
            for (int index = 0; index < HOST_COUNT; index++) {
                int completed = Math.max(0, Math.min(SLOTS_PER_HOST, jobIndex + 1 - index * SLOTS_PER_HOST));
                var machineOwner = Integer.toUnsignedString(System.identityHashCode(machines[index]));
                helper.assertValueEqual(transitions.stream()
                        .filter(receipt -> receipt.machineOwner().equals(machineOwner)).count(),
                        completed * (long) QUANTITY, "Unselected machine transition count: " + index);
                helper.assertTrue(source.hosts().get(index).lane(0).getReturnInv().isEmpty(),
                        "Every native Provider return inventory must drain: " + index);
            }
            helper.assertTrue(source.requester().uniqueNativeJobCount() == totalJobs + 1
                            && jobIds.equals(Set.copyOf(List.of(source.requester().nativeJobIds().split(","))))
                            && source.requester().acceptedAmount(selection.output()) == (cycle + 1L) * QUANTITY
                            && source.requester().acceptedAmount() == (totalJobs + 1L) * QUANTITY
                            && !source.requester().observedCanceled()
                            && source.grid().getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                    "Distinct native CPU/requester link and exact callback for job " + jobIndex);
            helper.assertValueEqual(amount(source, selection.input()), 0L, "Physical source input consumed");
            helper.assertValueEqual(machine.inputCount(selection.input().getItem()), 0L, "Machine input consumed");
            helper.assertValueEqual(amount(source, selection.output()), (long) QUANTITY,
                    "Actual callback output must occupy direct chest before Drive transfer");
            source.retention().retain(jobs, jobIndex);
            LOGGER.info("AE2F_SCALE_LARGE_DIRECT_256 job={} physicalPatterns={} submissions={} host={} slot={} "
                            + "machineOwner={} returnOwner={} jobId={} inputKey={} outputKey={} transitions={} "
                            + "callback={} retainedUnits={} sourceInput=0 chestOutput=0 cpuIdle=true returnsEmpty=true",
                    jobIndex + 1, jobs.size(), jobIds.size(), hostIndex(), localSlot(), owner, returnOwner,
                    source.requester().submittedLink().getCraftingID(), selection.input().getId(),
                    selection.output().getId(), selected.size(), source.requester().acceptedAmount(selection.output()),
                    (jobIndex + 1) * QUANTITY);
        }

        private void reportWindow(String phase, ScaleTimedWindow.Window measured) {
            LOGGER.info("AE2F_SCALE_TIMED layout=native-big-grid phase={} startNano={} endNano={} "
                            + "startTick={} endTick={} wallNanos={} ticks={} jobs={} units={} patternCount=256",
                    phase, measured.startNanos(), measured.endNanos(), measured.startTick(), measured.endTick(),
                    measured.wallNanos(), measured.ticks(), measured.jobs(), measured.units());
        }

        private void writeTimedEvidence() {
            var configured = System.getProperty("ae2federation.nativeEvidenceFile", "");
            if (configured.isBlank()) return;
            var properties = new Properties();
            properties.setProperty("schemaVersion", "1");
            properties.setProperty("status", "passed");
            properties.setProperty("kind", "benchmark");
            properties.setProperty("testId", "scalesmallnativebiggridtimed");
            properties.setProperty("structure", "ae2federation_test:scale_36_empty");
            properties.setProperty("assertions", "8");
            properties.setProperty("operations", "256");
            properties.setProperty("inserted", Long.toString(window.sample().units()));
            properties.setProperty("extracted", Long.toString(window.sample().units()));
            properties.setProperty("elapsedNanos", Long.toString(window.sample().wallNanos()));
            properties.setProperty("warmupNanos", Long.toString(window.warmup().wallNanos()));
            properties.setProperty("warmupTicks", Long.toString(window.warmup().ticks()));
            properties.setProperty("warmupJobs", Integer.toString(window.warmup().jobs()));
            properties.setProperty("sampleTicks", Long.toString(window.sample().ticks()));
            properties.setProperty("sampleJobs", Integer.toString(window.sample().jobs()));
            properties.setProperty("callbackUnits", Long.toString(totalJobs * (long) QUANTITY));
            properties.setProperty("drainedDriveUnits", Long.toString(cycle * (long) JOB_COUNT * QUANTITY));
            properties.setProperty("finalDriveUnits", Long.toString(jobIndex * (long) QUANTITY));
            try {
                var evidence = Path.of(configured).toAbsolutePath();
                Files.createDirectories(evidence.getParent());
                try (var output = Files.newOutputStream(evidence)) {
                    properties.store(output, "Native small timed window");
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot write native timed evidence", exception);
            }
        }

        private void checkMachinePosition(ScaleSourceHosts.Scene source, int index) {
            var position = MACHINES.get(index);
            var world = helper.absolutePos(position);
            helper.assertTrue(helper.getBounds().contains(world.getX() + 0.5, world.getY() + 0.5,
                            world.getZ() + 0.5) && helper.getLevel().isLoaded(world)
                            && helper.getLevel().getBlockEntity(world) == null
                            && (helper.getBlockState(position).isAir() || helper.getBlockState(position).is(Blocks.BARRIER))
                            && source.hosts().get(index).hostBlockEntity()
                                    == helper.getLevel().getBlockEntity(helper.absolutePos(HOSTS.get(index))),
                    "Host EAST machine must be bounded, empty and adjacent to its exact Provider: " + index);
            for (var direction : Direction.values()) {
                if (direction == Direction.WEST) continue;
                var neighbor = helper.absolutePos(position.relative(direction));
                helper.assertTrue(helper.getBounds().contains(neighbor.getX() + 0.5, neighbor.getY() + 0.5,
                                neighbor.getZ() + 0.5) && helper.getLevel().isLoaded(neighbor)
                                && helper.getLevel().getBlockEntity(neighbor) == null,
                        "Machine may touch only its selected Provider: " + index + " " + direction);
            }
        }

        private static long amount(ScaleSourceHosts.Scene source, AEItemKey key) {
            return source.crafting().storage().extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
        }
    }
}
