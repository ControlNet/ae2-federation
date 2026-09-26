package space.controlnet.ae2federation.test.scale;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.helpers.externalstorage.GenericStackItemStorage;
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
import space.controlnet.ae2federation.test.mixed.MixedFactoryObservation;
import space.controlnet.ae2federation.test.mixed.MixedMachineBlockEntity;
import space.controlnet.ae2federation.test.mixed.MixedMachineRegistration;
import space.controlnet.ae2federation.test.processing.ProcessingRegressionFixtures;

public final class ScaleLargeDirectOneTarget {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScaleLargeDirectOneTarget.class);
    private static final BlockPos HOST = new BlockPos(7, 6, 5);
    private static final BlockPos MACHINE = HOST.east();
    private static final AEItemKey INPUT = AEItemKey.of(Items.COBBLESTONE);
    private static final AEItemKey OUTPUT = AEItemKey.of(Items.DIAMOND);
    private static final int QUANTITY = 16;

    private ScaleLargeDirectOneTarget() {
    }

    public static void run(GameTestHelper helper) {
        var target = new Target(helper);
        ScaleSourceHosts.run(helper, target::tick);
    }

    private static final class Target {
        private final GameTestHelper helper;
        private MixedMachineBlockEntity machine;
        private Future<ICraftingPlan> plan;
        private String jobId;
        private int stage;

        private Target(GameTestHelper helper) {
            this.helper = helper;
        }

        private boolean tick(ScaleSourceHosts.Scene source) {
            var host = source.hosts().get(0);
            var lane = host.lane(0);
            if (stage == 0) {
                checkMachinePosition(source);
                helper.setBlock(MACHINE, MixedMachineRegistration.BLOCK.get());
                var entity = helper.getLevel().getBlockEntity(helper.absolutePos(MACHINE));
                helper.assertTrue(entity instanceof MixedMachineBlockEntity,
                        "Host 0 EAST physical capability machine is missing after sixteen active source channels");
                machine = (MixedMachineBlockEntity) entity;
                MixedFactoryObservation.begin(0, false);
                machine.processSingleItemPerTick();
                var returns = lane.getReturnInv();
                machine.configure(List.of(new MixedMachineBlockEntity.MachineRecipe(Items.COBBLESTONE, Items.DIAMOND,
                        false, returns, new GenericStackItemStorage(returns))));
                MixedFactoryObservation.authorizeMachine(machine, machine.inputHandler(), machine.outputInventory(),
                        returns, INPUT, OUTPUT);
                host.installPattern(0, List.of(ProcessingRegressionFixtures.item(Items.COBBLESTONE, 1)),
                        List.of(ProcessingRegressionFixtures.item(Items.DIAMOND, 1)));
                stage = 1;
                return false;
            }
            var inventory = host.composition().patternInventory();
            var decoded = PatternDetailsHelper.decodePattern(inventory.getStackInSlot(0), helper.getLevel());
            var handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                    helper.absolutePos(MACHINE), Direction.WEST);
            helper.assertTrue(decoded != null && inventory.size() == 1
                            && decoded.getInputs().length == 1
                            && decoded.getInputs()[0].getPossibleInputs().length == 1
                            && decoded.getInputs()[0].getPossibleInputs()[0].what().equals(INPUT)
                            && decoded.getInputs()[0].getPossibleInputs()[0].amount() == 1
                            && decoded.getPrimaryOutput().what().equals(OUTPUT)
                            && decoded.getPrimaryOutput().amount() == 1,
                    "Host 0 physical slot must decode one cobblestone-to-diamond Processing Pattern");
            helper.assertTrue(lane.getAvailablePatterns().equals(List.of(decoded))
                            && host.publishedProviders(decoded).equals(List.of(lane))
                            && source.grid().getCraftingService().getCraftingFor(OUTPUT).contains(decoded)
                            && source.grid().getCraftingService().getCraftables(key -> true).equals(Set.of(OUTPUT)),
                    "Only host 0 must advertise the decoded Pattern: lane=" + lane.getAvailablePatterns().size()
                            + " providers=" + host.publishedProviders(decoded).size()
                            + " craftables=" + source.grid().getCraftingService().getCraftables(key -> true));
            helper.assertTrue(source.hosts().stream().skip(1).allMatch(other ->
                            other.lane(0).getAvailablePatterns().isEmpty()
                                    && other.composition().patternInventory().getStackInSlot(0).isEmpty()),
                    "Other fifteen channelled Provider Lanes must not publish a duplicate Pattern");
            helper.assertTrue(handler == machine.inputHandler() && machine.recipeCount() == 1
                            && handler.isItemValid(0, new ItemStack(Items.COBBLESTONE))
                            && source.requester().isReady(source.crafting().node()),
                    "Host 0 EAST registered capability and original source requester must be ready");
            if (stage == 1) {
                helper.assertValueEqual(source.crafting().storage().insert(INPUT, QUANTITY, Actionable.MODULATE,
                        source.requester().actionSource()), (long) QUANTITY,
                        "Physical source chest must accept sixteen Processing inputs");
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
                        }, OUTPUT, QUANTITY, CalculationStrategy.REPORT_MISSING_ITEMS);
                stage = 2;
                return false;
            }
            if (stage == 2) {
                if (!plan.isDone()) return false;
                try {
                    var result = plan.get();
                    helper.assertTrue(!result.simulation() && result.finalOutput().what().equals(OUTPUT)
                                    && result.finalOutput().amount() == QUANTITY,
                            "Native planner must calculate sixteen actual Processing outputs");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Native large-scene plan interrupted", exception);
                } catch (ExecutionException exception) {
                    throw new IllegalStateException("Native large-scene planner failed", exception);
                }
                stage = 3;
            }
            if (stage == 3) {
                if (!source.requester().handleCrafting(OUTPUT, QUANTITY, helper.getLevel(),
                        source.grid().getCraftingService())) return false;
                var link = source.requester().submittedLink();
                helper.assertTrue(link != null && !link.isCanceled(), "Native CPU must submit one real requester link");
                jobId = link.getCraftingID().toString();
                stage = 4;
                return false;
            }
            if (!source.requester().observedDone() || source.requester().activeLink() != null
                    || source.requester().acceptedAmount(OUTPUT) != QUANTITY) return false;
            var transitions = MixedFactoryObservation.machineTransitions();
            var owner = Integer.toUnsignedString(System.identityHashCode(machine));
            var returnOwner = Integer.toUnsignedString(System.identityHashCode(lane.getReturnInv()));
            helper.assertTrue(transitions.size() == QUANTITY && transitions.stream().allMatch(receipt ->
                            receipt.machineOwner().equals(owner) && receipt.returnOwner().equals(returnOwner)
                                    && receipt.input().equals(INPUT.getId().toString())
                                    && receipt.output().equals(OUTPUT.getId().toString())
                                    && receipt.accepted() == 1 && receipt.consumed() == 1
                                    && receipt.produced() == 1),
                    "Sixteen one-item capability machine transitions must belong to host 0's native return owner");
            helper.assertTrue(source.requester().uniqueNativeJobCount() == 1
                            && source.requester().nativeJobIds().equals(jobId)
                            && source.requester().acceptedAmount() == QUANTITY
                            && !source.requester().observedCanceled()
                            && amount(source, INPUT) == 0 && amount(source, OUTPUT) == QUANTITY
                            && machine.inputCount(Items.COBBLESTONE) == 0 && lane.getReturnInv().isEmpty()
                            && source.grid().getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                    "Native job, callback, machine, source cell and CPU must conserve sixteen physical units");
            source.retention().retain(List.of(new ScaleNativeProcessingProbe.CatalogSelection(0, INPUT, OUTPUT)), 0);
            helper.assertValueEqual(amount(source, OUTPUT), 0L, "Source chest must drain into physical Drive");
            LOGGER.info("AE2F_SCALE_LARGE_DIRECT_ONE_TARGET grid={} networkId={} host={} machine={} "
                            + "lane={} jobId={} machineTransitions={} machineAccepted=16 machineConsumed=16 "
                            + "machineOutput=16 callback={} sourceInput=0 chestOutput=0 driveOutput=16 cpuIdle=true "
                            + "returnEmpty=true otherPublished=0",
                    System.identityHashCode(source.grid()), source.id().value(), HOST, MACHINE,
                    System.identityHashCode(lane), jobId, transitions.size(), source.requester().acceptedAmount(OUTPUT));
            MixedFactoryObservation.close();
            helper.setBlock(MACHINE, Blocks.AIR);
            return true;
        }

        private void checkMachinePosition(ScaleSourceHosts.Scene source) {
            var world = helper.absolutePos(MACHINE);
            helper.assertTrue(helper.getBounds().contains(world.getX() + 0.5, world.getY() + 0.5,
                            world.getZ() + 0.5) && helper.getLevel().isLoaded(world)
                            && helper.getLevel().getBlockEntity(world) == null
                            && (helper.getBlockState(MACHINE).isAir()
                                    || helper.getBlockState(MACHINE).is(Blocks.BARRIER)),
                    "Host 0 EAST machine position must be bounded, loaded and BE-free before placement");
            helper.assertTrue(source.hosts().get(0).hostBlockEntity()
                            == helper.getLevel().getBlockEntity(helper.absolutePos(HOST)),
                    "Machine WEST neighbor must be the exact host 0 physical Provider BE");
            for (var direction : Direction.values()) {
                if (direction == Direction.WEST) continue;
                var neighbor = helper.absolutePos(MACHINE.relative(direction));
                helper.assertTrue(helper.getBounds().contains(neighbor.getX() + 0.5, neighbor.getY() + 0.5,
                                neighbor.getZ() + 0.5) && helper.getLevel().isLoaded(neighbor)
                                && helper.getLevel().getBlockEntity(neighbor) == null,
                        "Machine must touch only the selected Provider BE: " + direction);
            }
        }

        private static long amount(ScaleSourceHosts.Scene source, AEItemKey key) {
            return source.crafting().storage().extract(key, Long.MAX_VALUE, Actionable.SIMULATE,
                    IActionSource.empty());
        }
    }
}
