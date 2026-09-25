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
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.LongSupplier;
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

public final class ScaleLargeFederation256Replay {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScaleLargeFederation256Replay.class);
    private static final int TARGET_COUNT = 16;
    private static final int SLOTS_PER_HOST = 16;
    private static final int JOB_COUNT = TARGET_COUNT * SLOTS_PER_HOST;
    private static final int QUANTITY = 16;
    private static final List<BlockPos> HOSTS = IntStream.range(0, TARGET_COUNT)
            .mapToObj(index -> new BlockPos(7 + 8 * (index % 4), 6, 5 + 8 * (index / 4))).toList();

    private ScaleLargeFederation256Replay() {
    }

    public static void run(GameTestHelper helper) {
        var replay = new Replay(helper, false);
        ScaleSourceHosts.run(helper, replay::tick, SLOTS_PER_HOST, 5, false);
    }

    public static void runTimed(GameTestHelper helper) {
        var replay = new Replay(helper, true, Boolean.getBoolean("ae2federation.federationStageDiagnostics"));
        ScaleSourceHosts.run(helper, replay::tick, SLOTS_PER_HOST, 5, false);
    }

    enum JobStage {
        PLANNING, NATIVE_SUBMISSION, TARGET_INPUT_CONTEXT, MACHINE_CALLBACK, RECEIPT_READBACK
    }

    static final class StageDiagnostics {
        private final long[] wallNanos = new long[JobStage.values().length];
        private final long[] serverTicks = new long[JobStage.values().length];
        private final long[] waits = new long[JobStage.values().length];
        private final long[] cpuNanos = new long[JobStage.values().length];
        private final LongSupplier cpuClock;
        private final Thread owner;
        private JobStage current = JobStage.PLANNING;
        private long stageStartNanos;
        private long stageStartTick;
        private long stageStartCpuNanos;
        private int completedJobs;

        StageDiagnostics(long startedNanos, long startedTick, LongSupplier cpuClock) {
            stageStartNanos = startedNanos;
            stageStartTick = startedTick;
            this.cpuClock = cpuClock;
            owner = Thread.currentThread();
            stageStartCpuNanos = cpuClock.getAsLong();
        }

        private long currentCpuNanos() {
            return stageStartCpuNanos < 0 || Thread.currentThread() != owner ? -1 : cpuClock.getAsLong();
        }

        void waitFor(JobStage stage) {
            waits[stage.ordinal()]++;
        }

        void transition(JobStage next, long nowNanos, long nowTick) {
            long nowCpuNanos = currentCpuNanos();
            if (nowCpuNanos < stageStartCpuNanos) stageStartCpuNanos = -1;
            if (stageStartCpuNanos >= 0) cpuNanos[current.ordinal()] += nowCpuNanos - stageStartCpuNanos;
            wallNanos[current.ordinal()] += nowNanos - stageStartNanos;
            serverTicks[current.ordinal()] += nowTick - stageStartTick;
            current = next;
            stageStartNanos = nowNanos;
            stageStartTick = nowTick;
            stageStartCpuNanos = stageStartCpuNanos < 0 ? -1 : nowCpuNanos;
        }

        void completedJob(long nowNanos, long nowTick) {
            transition(JobStage.PLANNING, nowNanos, nowTick);
            completedJobs++;
        }

        String snapshot(String phase, String status, long nowNanos, long nowTick) {
            long nowCpuNanos = currentCpuNanos();
            if (nowCpuNanos < stageStartCpuNanos) stageStartCpuNanos = -1;
            var message = new StringBuilder("AE2F_SCALE_FEDERATION_STAGE phase=").append(phase)
                    .append(" status=").append(status).append(" completedJobs=").append(completedJobs);
            for (var stage : JobStage.values()) {
                int index = stage.ordinal();
                message.append(' ').append(stage.name()).append(".wallNanos=")
                        .append(wallNanos[index] + (stage == current ? nowNanos - stageStartNanos : 0))
                        .append(' ').append(stage.name()).append(".serverTicks=")
                        .append(serverTicks[index] + (stage == current ? nowTick - stageStartTick : 0))
                        .append(' ').append(stage.name()).append(".waits=").append(waits[index])
                        .append(' ').append(stage.name()).append(".serverThreadCpuNanos=")
                        .append(stageStartCpuNanos < 0 ? "unavailable" : Long.toString(cpuNanos[index]
                                + (stage == current ? nowCpuNanos - stageStartCpuNanos : 0)));
            }
            return message.toString();
        }

        void reset(long nowNanos, long nowTick) {
            for (var stage : JobStage.values()) {
                int index = stage.ordinal();
                wallNanos[index] = 0;
                serverTicks[index] = 0;
                waits[index] = 0;
                cpuNanos[index] = 0;
            }
            completedJobs = 0;
            stageStartNanos = nowNanos;
            stageStartTick = nowTick;
            stageStartCpuNanos = currentCpuNanos();
        }
    }

    private static final class Replay {
        private final GameTestHelper helper;
        private final List<ScaleLargeFederationTarget> targets = new ArrayList<>();
        private final List<MixedMachineBlockEntity> machines = new ArrayList<>();
        private final List<IGrid> grids = new ArrayList<>();
        private final List<NetworkId> ids = new ArrayList<>();
        private final List<ScaleNativeProcessingProbe.CatalogSelection> jobs = new ArrayList<>();
        private final Set<String> jobIds = new LinkedHashSet<>();
        private final Set<ChunkPos> podChunks = new HashSet<>();
        private final boolean timed;
        private final boolean diagnoseStages;
        private ScaleTimedWindow window;
        private StageDiagnostics diagnostics;
        private int cycle;
        private int totalJobs;
        private boolean warmupReported;
        private ScaleLargeFederationTarget pending;
        private Future<ICraftingPlan> plan;
        private int stage;
        private int jobIndex;
        private int waitTicks;

        private Replay(GameTestHelper helper, boolean timed) {
            this(helper, timed, false);
        }

        private Replay(GameTestHelper helper, boolean timed, boolean diagnoseStages) {
            this.helper = helper;
            this.timed = timed;
            this.diagnoseStages = diagnoseStages;
        }

        private void transition(JobStage next) {
            if (diagnostics != null) diagnostics.transition(next, System.nanoTime(),
                    helper.getLevel().getServer().getTickCount());
        }

        private void waiting(JobStage stage) {
            if (diagnostics != null) diagnostics.waitFor(stage);
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
                pending = new ScaleLargeFederationTarget(helper, source, 0, HOSTS.getFirst());
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
                assertLayout(source, true);
                MixedFactoryObservation.begin(0, false);
                for (var target : targets) {
                    var machine = target.target().placeProcessingMachine();
                    machine.processSingleItemPerTick();
                    machines.add(machine);
                }
                install(source);
                stage = 2;
                return false;
            }
            if (stage == 2) {
                var craftables = source.grid().getCraftingService().getCraftables(key -> true);
                if (craftables.size() < JOB_COUNT - 1) {
                    if (++waitTicks > 100) helper.fail("Federation publication stalled at " + craftables.size()
                            + " craftables; laneCounts=" + source.hosts().stream()
                                    .map(host -> host.lane(0).getAvailablePatterns().size()).toList());
                    return false;
                }
                helper.assertValueEqual(craftables.size(), JOB_COUNT,
                        "Missing 256th distinct physical Federation Pattern after native publication settled");
                assertLayout(source, false);
                assertCatalog(source);
                if (timed) {
                    long startedNanos = System.nanoTime();
                    long startedTick = helper.getLevel().getServer().getTickCount();
                    window = new ScaleTimedWindow(300, 600, JOB_COUNT, QUANTITY, startedNanos, startedTick);
                    if (diagnoseStages) {
                        var bean = ManagementFactory.getThreadMXBean();
                        LongSupplier cpuClock = bean.isCurrentThreadCpuTimeSupported() && bean.isThreadCpuTimeEnabled()
                                ? bean::getCurrentThreadCpuTime : () -> -1;
                        diagnostics = new StageDiagnostics(startedNanos, startedTick, cpuClock);
                    }
                }
                stage = 3;
            }
            if (stage == 3) {
                beginJob(source);
                return false;
            }
            var selection = jobs.get(jobIndex);
            if (stage == 4) {
                if (!plan.isDone()) {
                    waiting(JobStage.PLANNING);
                    return false;
                }
                try {
                    var result = plan.get();
                    helper.assertTrue(!result.simulation() && result.finalOutput().what().equals(selection.output())
                                    && result.finalOutput().amount() == QUANTITY,
                            "Native Federation planner must calculate sixteen outputs for Pattern " + jobIndex);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Federation catalog planner interrupted", exception);
                } catch (ExecutionException exception) {
                    throw new IllegalStateException("Federation catalog planner failed", exception);
                }
                stage = 5;
                transition(JobStage.NATIVE_SUBMISSION);
            }
            if (stage == 5) {
                if (!source.requester().handleCrafting(selection.output(), QUANTITY, helper.getLevel(),
                        source.grid().getCraftingService())) {
                    waiting(JobStage.NATIVE_SUBMISSION);
                    return false;
                }
                var link = source.requester().submittedLink();
                helper.assertTrue(link != null && !link.isCanceled() && jobIds.add(link.getCraftingID().toString()),
                        "Each physical Federation Pattern needs a distinct native CPU link: " + jobIndex);
                stage = 6;
                transition(JobStage.TARGET_INPUT_CONTEXT);
                return false;
            }
            if (stage == 6) {
                for (int other = 0; other < TARGET_COUNT; other++) {
                    if (other != hostIndex()) helper.assertValueEqual(
                            targets.get(other).target().inputAmount(selection.input()), 0L,
                            "Unselected Federation target must not receive selected input: " + other);
                }
                var target = targets.get(hostIndex());
                var endpoint = target.target();
                var context = endpoint.endpoint().binding().runtime().itemReturnContext();
                if (endpoint.inputAmount(selection.input()) != QUANTITY || context.isEmpty()) {
                    if (++waitTicks > 2000) helper.fail("Host " + hostIndex() + " slot " + localSlot()
                            + " missing routed target input/Endpoint context; source=" + amount(source, selection.input())
                            + " target=" + endpoint.inputAmount(selection.input()) + " context=" + context.isPresent());
                    waiting(JobStage.TARGET_INPUT_CONTEXT);
                    return false;
                }
                var owner = context.orElseThrow().owner();
                var lane = source.hosts().get(hostIndex()).lane(0);
                var handler = endpoint.returnHandler();
                helper.assertTrue(owner.logic() == lane && owner.inventory() == lane.getReturnInv()
                                && owner.lane().isPresent() && handler == context.orElseThrow().capability(),
                        "Endpoint return capability must belong to selected physical Provider Lane");
                target.assertAuthorized(owner.lane().orElseThrow());
                helper.assertValueEqual(amount(source, selection.input()), 0L,
                        "Source physical input must route through the claimed Endpoint into target cell");
                for (int other = 0; other < TARGET_COUNT; other++) {
                    if (other != hostIndex()) helper.assertValueEqual(
                            machines.get(other).inputCount(selection.input().getItem()), 0L,
                            "Unselected Federation machine must not receive selected input: " + other);
                }
                var machine = machines.get(hostIndex());
                if (localSlot() == 0) configureMachine(machine, owner.inventory(), handler);
                helper.assertTrue(machine.inputHandler().isItemValid(0, new ItemStack(selection.input().getItem()))
                                && machine.recipeCount() == SLOTS_PER_HOST,
                        "Selected physical machine must accept this typed input before Export Bus activation");
                endpoint.export(selection.input());
                stage = 7;
                transition(JobStage.MACHINE_CALLBACK);
                waitTicks = 0;
                return false;
            }
            if (!source.requester().observedDone() || source.requester().activeLink() != null
                    || source.requester().acceptedAmount(selection.output()) != (cycle + 1L) * QUANTITY) {
                if (++waitTicks > 2000) helper.fail("Host " + hostIndex() + " slot " + localSlot()
                        + " missing machine/Endpoint return/callback; targetInput="
                        + targets.get(hostIndex()).target().inputAmount(selection.input()) + " machineInput="
                        + machines.get(hostIndex()).inputCount(selection.input().getItem()) + " callback="
                        + source.requester().acceptedAmount(selection.output()));
                waiting(JobStage.MACHINE_CALLBACK);
                return false;
            }
            transition(JobStage.RECEIPT_READBACK);
            finishJob(source);
            totalJobs++;
            long finishedNanos = timed ? System.nanoTime() : 0;
            long finishedTick = timed ? helper.getLevel().getServer().getTickCount() : 0;
            if (diagnostics != null) diagnostics.completedJob(finishedNanos, finishedTick);
            boolean complete;
            try {
                complete = timed && window.completedJob(finishedNanos, finishedTick);
            } catch (IllegalStateException exception) {
                if (diagnostics != null) LOGGER.info(diagnostics.snapshot(
                        window.warmup() == null ? "warmup" : "sample", "failed", finishedNanos, finishedTick));
                throw exception;
            }
            if (timed && !warmupReported && window.warmup() != null) {
                if (diagnostics != null) {
                    LOGGER.info(diagnostics.snapshot("warmup", "complete", finishedNanos, finishedTick));
                    diagnostics.reset(finishedNanos, finishedTick);
                }
                ScaleTimedNativeEvidence.report(LOGGER, "federation", "warmup", window.warmup());
                warmupReported = true;
            }
            if (timed && complete) {
                if (diagnostics != null) LOGGER.info(diagnostics.snapshot("sample", "complete", finishedNanos, finishedTick));
                ScaleTimedNativeEvidence.report(LOGGER, "federation", "sample", window.sample());
            }
            if (++jobIndex < JOB_COUNT && !complete) {
                stage = 3;
                plan = null;
                waitTicks = 0;
                return false;
            }
            if (timed && !complete) {
                helper.assertValueEqual(jobIndex, JOB_COUNT, "One complete physical 256-Pattern Federation replay");
                source.retention().drainCompletedReplay(jobs);
                MixedFactoryObservation.clearCompletedReplay();
                cycle++;
                jobIndex = 0;
                stage = 3;
                plan = null;
                waitTicks = 0;
                return false;
            }
            helper.assertValueEqual(jobIds.size(), totalJobs, "Each Federation job needs a different native link");
            helper.assertValueEqual(source.requester().acceptedAmount(), totalJobs * (long) QUANTITY,
                    "Every actual Federation callback unit must be accounted");
            helper.assertValueEqual(source.requester().acceptedAmount(),
                    cycle * (long) JOB_COUNT * QUANTITY + jobIndex * (long) QUANTITY,
                    "Drained and still-retained Federation Drive units reconcile");
            if (timed) ScaleTimedNativeEvidence.write("scalesmallfederationtimed", window, totalJobs,
                    cycle * (long) JOB_COUNT * QUANTITY, jobIndex * (long) QUANTITY);
            LOGGER.info("AE2F_SCALE_LARGE_FEDERATION_256_COMPLETE physicalPatterns={} submissions={} uniqueLinks={} "
                            + "callbackUnits={} retainedUnits={} drainedUnits={} targetGrids={}", jobs.size(), totalJobs,
                    jobIds.size(), source.requester().acceptedAmount(), jobIndex * QUANTITY,
                    cycle * JOB_COUNT * QUANTITY, grids.size());
            MixedFactoryObservation.close();
            targets.forEach(ScaleLargeFederationTarget::close);
            return true;
        }

        private void preflight(ScaleSourceHosts.Scene source) {
            var positions = new HashSet<BlockPos>();
            helper.assertTrue(helper.getBounds().getXsize() == 36 && helper.getBounds().getYsize() == 8
                            && helper.getBounds().getZsize() == 36,
                    "Federation pods require the actual 36x8x36 structure");
            for (int index = 0; index < TARGET_COUNT; index++) {
                var host = HOSTS.get(index);
                var node = helper.<appeng.blockentity.crafting.PatternProviderBlockEntity>getBlockEntity(host)
                        .getMainNode().getNode();
                helper.assertTrue(source.hosts().get(index).hostBlockEntity()
                                == helper.getLevel().getBlockEntity(helper.absolutePos(host))
                                && node.getGrid() == source.grid() && node.meetsChannelRequirements()
                                && node.getUsedChannels() > 0
                                && node.getInWorldConnections().containsKey(Direction.WEST),
                        "Federation source leg must begin at selected channeled Provider: " + index);
                for (var position : ScaleLargeFederationTarget.footprint(host)) {
                    var world = helper.absolutePos(position);
                    helper.assertTrue(positions.add(position)
                                    && helper.getBounds().contains(world.getX() + 0.5, world.getY() + 0.5,
                                            world.getZ() + 0.5)
                                    && helper.getLevel().isLoaded(world)
                                    && helper.getLevel().getBlockEntity(world) == null
                                    && (helper.getBlockState(position).isAir()
                                            || helper.getBlockState(position).is(Blocks.BARRIER)),
                            "Federation pod site must be unique, bounded, loaded and BE-free: " + position);
                    podChunks.add(new ChunkPos(world));
                }
            }
            helper.assertValueEqual(positions.size(), TARGET_COUNT * 9, "144 distinct physical Federation pod positions");
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
                    "Original source Grid must retain CPU, requester and five mounted Drive cells");
            for (int index = 0; index < TARGET_COUNT; index++) {
                var target = targets.get(index);
                target.assertReady();
                var grid = target.grid();
                var id = FederationDomainRegistryAccess.confirmedNetworkId(grid).orElseThrow();
                helper.assertTrue(uniqueGrids.add(grid) && uniqueIds.add(id) && grid != source.grid()
                                && target.target().grid() == grid && target.target().anchorId().equals(id)
                                && target.target().onlyAnchorClaim() && target.target().settled()
                                && uniqueReturns.add(source.hosts().get(index).lane(0).getReturnInv()),
                        "Separate physical Federation target Grid, identity and return: " + index);
                if (initial) {
                    grids.add(grid);
                    ids.add(id);
                } else {
                    helper.assertTrue(grids.get(index) == grid && ids.get(index).equals(id),
                            "Federation target Grid and identity changed: " + index);
                    var machine = machines.get(index);
                    var handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                            helper.absolutePos(target.endpointPosition().south()), Direction.WEST);
                    helper.assertTrue(uniqueMachines.add(machine) && handler == machine.inputHandler()
                                    && machine.inputHandler() != null,
                            "Distinct registered sided physical machine capability: " + index);
                }
            }
            helper.assertValueEqual(uniqueGrids.size(), TARGET_COUNT + 1, "Source plus sixteen target Grids");
            helper.assertValueEqual(uniqueIds.size(), TARGET_COUNT + 1, "Source plus sixteen confirmed target IDs");
            if (initial) LOGGER.info("AE2F_SCALE_LARGE_FEDERATION_256_TOPOLOGY sourceGrid={} sourceId={} "
                            + "targetGrids={} targetIds={} sourceCpu=1 sourceDriveCells=5 sourceEnergy=1 "
                            + "bridges=16 targetEndpoints=16 targetChests=16 targetCells=16 targetEnergy=16 "
                            + "targetExportBuses=16 targetMachines=16 sourcePowered={} targetPowered={} "
                            + "loadedPodChunks={} podChunks={}", System.identityHashCode(source.grid()),
                    source.id().value(), grids.stream().map(System::identityHashCode).toList(),
                    ids.stream().map(NetworkId::value).toList(), source.grid().getEnergyService().isNetworkPowered(),
                    grids.stream().map(grid -> grid.getEnergyService().isNetworkPowered()).toList(),
                    podChunks.size(), podChunks.stream().map(ChunkPos::toString).sorted().toList());
        }

        private void install(ScaleSourceHosts.Scene source) {
            for (int hostIndex = 0; hostIndex < TARGET_COUNT; hostIndex++) {
                var host = source.hosts().get(hostIndex);
                helper.assertValueEqual(host.composition().patternInventory().size(), SLOTS_PER_HOST,
                        "Each actual source Provider needs sixteen mapped Pattern slots");
                for (int slot = 0; slot < SLOTS_PER_HOST; slot++) {
                    int global = hostIndex * SLOTS_PER_HOST + slot;
                    var recipe = ScaleProcessingCatalog.recipes().get(global);
                    var input = AEItemKey.of(recipe.input());
                    var output = AEItemKey.of(recipe.output());
                    jobs.add(new ScaleNativeProcessingProbe.CatalogSelection(slot, input, output));
                    host.setPattern(slot, List.of(new GenericStack(input, 1)),
                            List.of(new GenericStack(output, 1)));
                }
                host.refreshPatterns();
            }
            helper.assertValueEqual(jobs.size(), JOB_COUNT, "256 host-major physical catalog selections");
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
                                && source.grid().getCraftingService().getCraftingFor(selection.output()).contains(decoded),
                        "Encoded physical Pattern must publish only on selected Federation Lane: " + index);
                helper.assertTrue(inputs.add(selection.input()) && outputs.add(selection.output()),
                        "Every Federation physical Pattern must have unique typed keys: " + index);
                for (int other = 0; other < TARGET_COUNT; other++) {
                    if (other != hostIndex) helper.assertTrue(
                            !source.hosts().get(other).lane(0).getAvailablePatterns().contains(decoded),
                            "Unselected physical Provider Lane cannot publish Pattern: " + index);
                }
            }
            helper.assertTrue(inputs.size() == JOB_COUNT && outputs.size() == JOB_COUNT
                            && Collections.disjoint(inputs, outputs)
                            && source.grid().getCraftingService().getCraftables(key -> true).equals(outputs),
                    "Exactly 256 disjoint distinct native Federation Patterns must be craftable");
            for (var host : source.hosts()) helper.assertValueEqual(host.lane(0).getAvailablePatterns().size(),
                    SLOTS_PER_HOST, "Sixteen decoded physical Patterns per Provider Lane");
            LOGGER.info("AE2F_SCALE_LARGE_FEDERATION_256_CATALOG physicalPatterns={} hosts={} slotsPerHost={} "
                            + "submissions=0", jobs.size(), TARGET_COUNT, SLOTS_PER_HOST);
        }

        private void configureMachine(MixedMachineBlockEntity machine,
                appeng.helpers.patternprovider.PatternProviderReturnInventory owner,
                net.neoforged.neoforge.items.IItemHandler handler) {
            var recipes = new ArrayList<MixedMachineBlockEntity.MachineRecipe>();
            for (int slot = 0; slot < SLOTS_PER_HOST; slot++) {
                var selection = jobs.get(hostIndex() * SLOTS_PER_HOST + slot);
                recipes.add(new MixedMachineBlockEntity.MachineRecipe(selection.input().getItem(),
                        selection.output().getItem(), false, owner, handler));
                MixedFactoryObservation.authorizeMachine(machine, machine.inputHandler(), machine.outputInventory(),
                        owner, selection.input(), selection.output());
            }
            machine.configure(recipes);
        }

        private void beginJob(ScaleSourceHosts.Scene source) {
            if (localSlot() == 0) assertLayout(source, false);
            var selection = jobs.get(jobIndex);
            helper.assertValueEqual(amount(source, selection.input()), 0L, "Source input before native job");
            helper.assertValueEqual(amount(source, selection.output()), 0L, "Callback chest before native job");
            helper.assertValueEqual(targets.get(hostIndex()).target().inputAmount(selection.input()), 0L,
                    "Physical target cell before selected job");
            helper.assertValueEqual(source.crafting().storage().insert(selection.input(), QUANTITY,
                    Actionable.MODULATE, source.requester().actionSource()), (long) QUANTITY,
                    "Source cell must accept sixteen typed physical inputs");
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
            var target = targets.get(hostIndex());
            var machine = machines.get(hostIndex());
            var lane = source.hosts().get(hostIndex()).lane(0);
            var owner = Integer.toUnsignedString(System.identityHashCode(machine));
            var returnOwner = Integer.toUnsignedString(System.identityHashCode(lane.getReturnInv()));
            var transitions = MixedFactoryObservation.machineTransitions();
            var selected = transitions.stream().filter(receipt -> receipt.machineOwner().equals(owner)
                    && receipt.input().equals(selection.input().getId().toString())
                    && receipt.output().equals(selection.output().getId().toString())).toList();
            helper.assertTrue(transitions.size() == (jobIndex + 1) * QUANTITY && selected.size() == QUANTITY
                            && selected.stream().allMatch(receipt -> receipt.returnOwner().equals(returnOwner)
                                    && receipt.accepted() == 1 && receipt.consumed() == 1 && receipt.produced() == 1),
                    "Selected physical Federation machine must perform sixteen typed return-owner transitions");
            for (int index = 0; index < TARGET_COUNT; index++) {
                int completed = Math.max(0, Math.min(SLOTS_PER_HOST, jobIndex + 1 - index * SLOTS_PER_HOST));
                var machineOwner = Integer.toUnsignedString(System.identityHashCode(machines.get(index)));
                helper.assertValueEqual(transitions.stream()
                        .filter(receipt -> receipt.machineOwner().equals(machineOwner)).count(),
                        completed * (long) QUANTITY, "Unselected machine transition count: " + index);
                helper.assertValueEqual(targets.get(index).target().inputAmount(selection.input()), 0L,
                        "Selected input must drain from every target cell: " + index);
                helper.assertValueEqual(machines.get(index).inputCount(selection.input().getItem()), 0L,
                        "Selected input must drain from every physical machine: " + index);
                helper.assertTrue(source.hosts().get(index).lane(0).getReturnInv().isEmpty(),
                        "Every Provider return inventory must drain: " + index);
            }
            helper.assertTrue(source.requester().uniqueNativeJobCount() == totalJobs + 1
                            && jobIds.equals(Set.copyOf(List.of(source.requester().nativeJobIds().split(","))))
                            && source.requester().acceptedAmount(selection.output()) == (cycle + 1L) * QUANTITY
                            && source.requester().acceptedAmount() == (totalJobs + 1L) * QUANTITY
                            && !source.requester().observedCanceled()
                            && source.grid().getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                    "Distinct native link, retired CPU and exact typed callback for job " + jobIndex);
            target.assertReady();
            helper.assertTrue(target.target().returnHandler() != null,
                    "Physical Endpoint return capability must remain accessible");
            helper.assertValueEqual(amount(source, selection.input()), 0L, "Source typed input consumed");
            helper.assertValueEqual(amount(source, selection.output()), (long) QUANTITY,
                    "Actual Endpoint callback must occupy physical source chest before Drive transfer");
            source.retention().retain(jobs, jobIndex);
            LOGGER.info("AE2F_SCALE_LARGE_FEDERATION_256 job={} physicalPatterns={} submissions={} host={} slot={} "
                            + "sourceGrid={} targetGrid={} targetId={} bridge={} endpoint={} jobId={} "
                            + "machineOwner={} returnOwner={} inputKey={} outputKey={} route=ACTIVE "
                            + "targetInputBeforeExport=16 transitions={} callback={} retainedUnits={} "
                            + "sourceInput=0 targetInput=0 chestOutput=0 cpuIdle=true returnsEmpty=true",
                    jobIndex + 1, jobs.size(), jobIds.size(), hostIndex(), localSlot(),
                    System.identityHashCode(source.grid()), System.identityHashCode(grids.get(hostIndex())),
                    ids.get(hostIndex()).value(), target.bridgePosition(), target.endpointPosition(),
                    source.requester().submittedLink().getCraftingID(), owner, returnOwner,
                    selection.input().getId(), selection.output().getId(), selected.size(),
                    source.requester().acceptedAmount(selection.output()), (jobIndex + 1) * QUANTITY);
        }

        private static long amount(ScaleSourceHosts.Scene source, AEItemKey key) {
            return source.crafting().storage().extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
        }
    }
}
