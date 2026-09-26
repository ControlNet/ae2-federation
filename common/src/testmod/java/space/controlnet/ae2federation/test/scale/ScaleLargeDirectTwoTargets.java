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
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.test.mixed.MixedFactoryObservation;
import space.controlnet.ae2federation.test.mixed.MixedMachineBlockEntity;
import space.controlnet.ae2federation.test.mixed.MixedMachineRegistration;
import space.controlnet.ae2federation.test.processing.ProcessingRegressionFixtures;

public final class ScaleLargeDirectTwoTargets {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScaleLargeDirectTwoTargets.class);
    private static final List<BlockPos> HOSTS = List.of(new BlockPos(7, 6, 5), new BlockPos(15, 6, 5));
    private static final List<BlockPos> MACHINES = HOSTS.stream().map(BlockPos::east).toList();
    private static final List<ScaleNativeProcessingProbe.CatalogSelection> JOBS = List.of(
            new ScaleNativeProcessingProbe.CatalogSelection(0, AEItemKey.of(Items.COBBLESTONE),
                    AEItemKey.of(Items.DIAMOND)),
            new ScaleNativeProcessingProbe.CatalogSelection(0, AEItemKey.of(Items.DIRT),
                    AEItemKey.of(Items.GOLD_INGOT)));
    private static final int QUANTITY = 16;

    private ScaleLargeDirectTwoTargets() {
    }

    public static void run(GameTestHelper helper) {
        var target = new Target(helper);
        ScaleSourceHosts.run(helper, target::tick);
    }

    private static final class Target {
        private final GameTestHelper helper;
        private final MixedMachineBlockEntity[] machines = new MixedMachineBlockEntity[2];
        private final List<String> jobIds = new ArrayList<>();
        private Future<ICraftingPlan> plan;
        private int stage;
        private int jobIndex;
        private int waitTicks;

        private Target(GameTestHelper helper) {
            this.helper = helper;
        }

        private boolean tick(ScaleSourceHosts.Scene source) {
            if (stage == 0) {
                placeMachines(source);
                stage = 1;
                return false;
            }
            assertLayout(source);
            if (stage == 1) {
                beginJob(source);
                return false;
            }
            var selection = JOBS.get(jobIndex);
            if (stage == 2) {
                if (!plan.isDone()) return false;
                try {
                    var result = plan.get();
                    helper.assertTrue(!result.simulation() && result.finalOutput().what().equals(selection.output())
                                    && result.finalOutput().amount() == QUANTITY,
                            "Native planner must calculate sixteen physical outputs for host " + jobIndex);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Native two-target plan interrupted", exception);
                } catch (ExecutionException exception) {
                    throw new IllegalStateException("Native two-target planner failed", exception);
                }
                stage = 3;
            }
            if (stage == 3) {
                if (!source.requester().handleCrafting(selection.output(), QUANTITY, helper.getLevel(),
                        source.grid().getCraftingService())) return false;
                var link = source.requester().submittedLink();
                helper.assertTrue(link != null && !link.isCanceled()
                                && !jobIds.contains(link.getCraftingID().toString()),
                        "Native CPU must submit a distinct live requester link for host " + jobIndex);
                jobIds.add(link.getCraftingID().toString());
                stage = 4;
                return false;
            }
            if (!source.requester().observedDone() || source.requester().activeLink() != null
                    || source.requester().acceptedAmount(selection.output()) != QUANTITY) {
                if (++waitTicks > 120) helper.fail("Selected host " + jobIndex + " machine missing native work: sourceInput="
                        + amount(source, selection.input()) + " selectedMachineInput="
                        + machines[jobIndex].inputCount(selection.input().getItem()));
                return false;
            }
            finishJob(source);
            if (++jobIndex < JOBS.size()) {
                stage = 1;
                plan = null;
                waitTicks = 0;
                return false;
            }
            MixedFactoryObservation.close();
            for (var position : MACHINES) helper.setBlock(position, Blocks.AIR);
            return true;
        }

        private void placeMachines(ScaleSourceHosts.Scene source) {
            for (int index = 0; index < MACHINES.size(); index++) checkMachinePosition(source, index);
            for (var position : MACHINES) helper.setBlock(position, MixedMachineRegistration.BLOCK.get());
            for (int index = 0; index < MACHINES.size(); index++) {
                var entity = helper.getLevel().getBlockEntity(helper.absolutePos(MACHINES.get(index)));
                helper.assertTrue(entity instanceof MixedMachineBlockEntity,
                        "Host " + index + " EAST physical capability machine is missing after sixteen active source channels");
                machines[index] = (MixedMachineBlockEntity) entity;
            }
            helper.assertTrue(machines[0] != machines[1] && source.hosts().get(0).hostBlockEntity()
                            != source.hosts().get(1).hostBlockEntity()
                            && source.hosts().get(0).lane(0) != source.hosts().get(1).lane(0)
                            && source.hosts().get(0).lane(0).getReturnInv()
                                    != source.hosts().get(1).lane(0).getReturnInv(),
                    "Two destinations require distinct physical BEs, native Lanes and return owners");
            MixedFactoryObservation.begin(0, false);
            for (int index = 0; index < MACHINES.size(); index++) {
                var selection = JOBS.get(index);
                var machine = machines[index];
                var returns = source.hosts().get(index).lane(0).getReturnInv();
                machine.processSingleItemPerTick();
                machine.configure(List.of(new MixedMachineBlockEntity.MachineRecipe(selection.input().getItem(),
                        selection.output().getItem(), false, returns, new GenericStackItemStorage(returns))));
                MixedFactoryObservation.authorizeMachine(machine, machine.inputHandler(), machine.outputInventory(),
                        returns, selection.input(), selection.output());
                source.hosts().get(index).installPattern(0,
                        List.of(ProcessingRegressionFixtures.item(selection.input().getItem(), 1)),
                        List.of(ProcessingRegressionFixtures.item(selection.output().getItem(), 1)));
            }
        }

        private void assertLayout(ScaleSourceHosts.Scene source) {
            helper.assertTrue(source.grid() == source.crafting().node().getGrid()
                            && source.requester().isReady(source.crafting().node())
                            && source.grid().getCraftingService().getCraftables(key -> true).equals(
                                    Set.of(JOBS.get(0).output(), JOBS.get(1).output())),
                    "Both exclusive physical Patterns must be craftable on the original source Grid");
            for (int index = 0; index < MACHINES.size(); index++) {
                var selection = JOBS.get(index);
                var host = source.hosts().get(index);
                var lane = host.lane(0);
                var node = ((PatternProviderBlockEntity) host.hostBlockEntity()).getMainNode().getNode();
                var inventory = host.composition().patternInventory();
                var decoded = PatternDetailsHelper.decodePattern(inventory.getStackInSlot(0), helper.getLevel());
                var handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                        helper.absolutePos(MACHINES.get(index)), Direction.WEST);
                helper.assertTrue(node.getGrid() == source.grid() && node.isActive() && node.meetsChannelRequirements()
                                && node.getUsedChannels() > 0 && node.getInWorldConnections().containsKey(Direction.WEST)
                                && FederationDomainRegistryAccess.confirmedNetworkId(node.getGrid()).filter(source.id()::equals).isPresent()
                                && decoded != null && inventory.size() == 1 && decoded.getInputs().length == 1
                                && decoded.getInputs()[0].getPossibleInputs().length == 1
                                && decoded.getInputs()[0].getPossibleInputs()[0].what().equals(selection.input())
                                && decoded.getInputs()[0].getPossibleInputs()[0].amount() == 1
                                && decoded.getPrimaryOutput().what().equals(selection.output())
                                && decoded.getPrimaryOutput().amount() == 1
                                && lane.getAvailablePatterns().equals(List.of(decoded))
                                && host.publishedProviders(decoded).equals(List.of(lane))
                                && source.grid().getCraftingService().getCraftingFor(selection.output()).contains(decoded)
                                && handler == machines[index].inputHandler() && machines[index].recipeCount() == 1
                                && handler.isItemValid(0, new ItemStack(selection.input().getItem())),
                        "Host " + index + " must exclusively publish its decoded Pattern to its own active EAST machine");
            }
            helper.assertTrue(source.hosts().stream().skip(2).allMatch(host ->
                            host.lane(0).getAvailablePatterns().isEmpty()
                                    && host.composition().patternInventory().getStackInSlot(0).isEmpty()),
                    "Other fourteen channelled Provider Lanes must remain unpublished");
        }

        private void beginJob(ScaleSourceHosts.Scene source) {
            var selection = JOBS.get(jobIndex);
            for (var job : JOBS) helper.assertValueEqual(amount(source, job.input()), 0L,
                    "Source chest must have no retained Processing input before the next job");
            helper.assertValueEqual(amount(source, selection.output()), 0L,
                    "Selected output must begin outside the direct callback chest");
            helper.assertValueEqual(source.crafting().storage().insert(selection.input(), QUANTITY,
                    Actionable.MODULATE, source.requester().actionSource()), (long) QUANTITY,
                    "Physical source chest must accept sixteen selected Processing inputs");
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
            stage = 2;
        }

        private void finishJob(ScaleSourceHosts.Scene source) {
            var selection = JOBS.get(jobIndex);
            var machine = machines[jobIndex];
            var lane = source.hosts().get(jobIndex).lane(0);
            var owner = Integer.toUnsignedString(System.identityHashCode(machine));
            var returnOwner = Integer.toUnsignedString(System.identityHashCode(lane.getReturnInv()));
            var transitions = MixedFactoryObservation.machineTransitions();
            var selected = transitions.stream().filter(receipt -> receipt.machineOwner().equals(owner)).toList();
            helper.assertTrue(transitions.size() == (jobIndex + 1) * QUANTITY && selected.size() == QUANTITY
                            && selected.stream().allMatch(receipt -> receipt.returnOwner().equals(returnOwner)
                                    && receipt.input().equals(selection.input().getId().toString())
                                    && receipt.output().equals(selection.output().getId().toString())
                                    && receipt.accepted() == 1 && receipt.consumed() == 1 && receipt.produced() == 1)
                            && transitions.stream().filter(receipt -> !receipt.machineOwner().equals(owner)).count()
                                    == jobIndex * QUANTITY,
                    "Only selected host " + jobIndex + " may make sixteen typed one-item machine transitions");
            helper.assertTrue(source.requester().uniqueNativeJobCount() == jobIndex + 1
                            && Set.copyOf(jobIds).equals(Set.copyOf(List.of(source.requester().nativeJobIds().split(","))))
                            && source.requester().acceptedAmount(selection.output()) == QUANTITY
                            && source.requester().acceptedAmount() == (jobIndex + 1) * QUANTITY
                            && !source.requester().observedCanceled()
                            && source.grid().getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                    "Two distinct native links must complete exact per-key callbacks on the original CPU");
            for (var job : JOBS) helper.assertValueEqual(amount(source, job.input()), 0L,
                    "Native Processing input must be consumed from the source chest");
            for (int index = 0; index < MACHINES.size(); index++) {
                helper.assertValueEqual(machines[index].inputCount(JOBS.get(index).input().getItem()), 0L,
                        "Selected and unselected machine inputs must be empty after the job");
                helper.assertTrue(source.hosts().get(index).lane(0).getReturnInv().isEmpty(),
                        "Both native Provider return inventories must drain");
            }
            helper.assertValueEqual(amount(source, selection.output()), (long) QUANTITY,
                    "Selected callback output must occupy the original physical chest before retention");
            source.retention().retain(JOBS, jobIndex);
            for (var job : JOBS) helper.assertValueEqual(amount(source, job.output()), 0L,
                    "Direct callback chest must be empty after physical Drive retention");
            LOGGER.info("AE2F_SCALE_LARGE_DIRECT_TWO_TARGETS job={} grid={} networkId={} host={} machine={} lane={} "
                            + "machineOwner={} returnOwner={} jobId={} machineTransitions={} machineAccepted={} "
                            + "machineConsumed={} machineOutput={} callback={} sourceInput=0 chestOutput=0 "
                            + "driveTotal={} cpuIdle=true returnsEmpty=true",
                    jobIndex + 1, System.identityHashCode(source.grid()), source.id().value(), HOSTS.get(jobIndex),
                    MACHINES.get(jobIndex), System.identityHashCode(lane), owner, returnOwner, jobIds.get(jobIndex),
                    selected.size(), selected.stream().mapToLong(MixedFactoryObservation.HandlerReceipt::accepted).sum(),
                    selected.stream().mapToLong(MixedFactoryObservation.HandlerReceipt::consumed).sum(),
                    selected.stream().mapToLong(MixedFactoryObservation.HandlerReceipt::produced).sum(),
                    source.requester().acceptedAmount(selection.output()), (jobIndex + 1) * QUANTITY);
        }

        private void checkMachinePosition(ScaleSourceHosts.Scene source, int index) {
            var position = MACHINES.get(index);
            var world = helper.absolutePos(position);
            helper.assertTrue(helper.getBounds().contains(world.getX() + 0.5, world.getY() + 0.5,
                            world.getZ() + 0.5) && helper.getLevel().isLoaded(world)
                            && helper.getLevel().getBlockEntity(world) == null
                            && (helper.getBlockState(position).isAir() || helper.getBlockState(position).is(Blocks.BARRIER)),
                    "Host " + index + " EAST machine position must be bounded, loaded and BE-free before placement");
            helper.assertTrue(source.hosts().get(index).hostBlockEntity()
                            == helper.getLevel().getBlockEntity(helper.absolutePos(HOSTS.get(index))),
                    "Machine WEST neighbor must be the exact physical Provider BE for host " + index);
            for (var direction : Direction.values()) {
                if (direction == Direction.WEST) continue;
                var neighbor = helper.absolutePos(position.relative(direction));
                helper.assertTrue(helper.getBounds().contains(neighbor.getX() + 0.5, neighbor.getY() + 0.5,
                                neighbor.getZ() + 0.5) && helper.getLevel().isLoaded(neighbor)
                                && helper.getLevel().getBlockEntity(neighbor) == null,
                        "Machine " + index + " must touch only its selected Provider BE: " + direction);
            }
        }

        private static long amount(ScaleSourceHosts.Scene source, AEItemKey key) {
            return source.crafting().storage().extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
        }
    }
}
