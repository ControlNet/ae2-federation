package space.controlnet.ae2federation.compat;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AEColor;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.me.helpers.IGridConnectedBlockEntity;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetBinding;
import space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity;

/**
 * Processing through the Federation Pattern Provider into a real machine, built as a player builds it. The Provider's
 * network (energy, ME Chest, crafting CPU) maps one processing pattern to an Endpoint whose Federation face touches the
 * Provider's front. The Endpoint's subnet stores the delivered input through a Storage Bus on the machine's input
 * side, so the input goes straight into the machine; a hopper under the machine pushes its product into the Endpoint,
 * which returns it to the Provider. Nothing in the test moves items: only the Provider's network asks for the output.
 */
final class EndpointMachineScene {
    private static final BlockPos ENERGY = new BlockPos(1, 1, 3);
    private static final BlockPos CHEST = new BlockPos(2, 1, 3);
    private static final BlockPos OUTPUT_CHEST = new BlockPos(2, 1, 2);
    private static final BlockPos CPU = new BlockPos(3, 1, 3);
    private static final BlockPos PROVIDER = new BlockPos(4, 1, 3);
    private static final BlockPos ENDPOINT = new BlockPos(5, 1, 3);
    private static final BlockPos SUBNET_ENERGY = new BlockPos(5, 1, 4);
    private static final BlockPos HOPPER = new BlockPos(5, 2, 3);
    private static final long JOBS = 2;

    /** The machine a processing pattern names and how a player sets it up. */
    interface Machine {
        String blockId();

        AEKey input();

        AEKey output();

        /** A storage cell for an output an item cell cannot hold, such as a chemical; null when none is needed. */
        default String outputCell() {
            return null;
        }

        /** How much of {@link #output()} one input makes. */
        default long outputPerInput() {
            return 1;
        }

        /**
         * True when the machine sits on the Endpoint and pushes its product into it by itself (auto-eject), so no
         * hopper is needed.
         */
        default boolean ejectsIntoEndpoint() {
            return false;
        }

        /** The machine's face the subnet's Storage Bus inserts into: {@link Direction#UP} or {@link Direction#EAST}. */
        default Direction inputFace() {
            return Direction.UP;
        }

        /** Places the machine; most machines are one block. */
        default void place(GameTestHelper helper, BlockPos position) {
            helper.setBlock(position, AddonCraftingScene.block(blockId()));
        }

        /** Places or loads what the machine needs besides itself, such as power or fuel. */
        default void prepare(GameTestHelper helper, BlockPos position) {
        }

        /** What the machine reports, for a scene that never gets its output. */
        default String state(GameTestHelper helper, BlockPos position) {
            return "";
        }
    }

    private final GameTestHelper helper;
    private final Machine machine;
    private final BlockPos machinePosition;
    private int stage;
    private Future<ICraftingPlan> planFuture;

    EndpointMachineScene(GameTestHelper helper, Machine machine) {
        this.helper = helper;
        this.machine = machine;
        machinePosition = machine.ejectsIntoEndpoint() ? HOPPER : HOPPER.above();
    }

    /** Runs the whole job; call from {@code succeedWhen}. */
    void tick() {
        switch (stage) {
            case 0 -> {
                helper.setBlock(ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());
                helper.setBlock(CHEST, AEBlocks.ME_CHEST.block());
                helper.<MEChestBlockEntity>getBlockEntity(CHEST).setCell(AEItems.ITEM_CELL_1K.stack());
                if (machine.outputCell() != null) {
                    helper.setBlock(OUTPUT_CHEST, AEBlocks.ME_CHEST.block());
                    var cell = AddonCraftingScene.item(machine.outputCell());
                    helper.<MEChestBlockEntity>getBlockEntity(OUTPUT_CHEST).setCell(new net.minecraft.world.item.ItemStack(cell));
                }
                helper.setBlock(CPU, AEBlocks.CRAFTING_STORAGE_1K.block());
                helper.setBlock(PROVIDER, ProcessingRegistration.PROVIDER.get().defaultBlockState()
                        .setValue(BlockStateProperties.FACING, Direction.EAST));
                helper.setBlock(ENDPOINT, ProcessingRegistration.ENDPOINT.get().defaultBlockState()
                        .setValue(BlockStateProperties.FACING, Direction.WEST));
                helper.setBlock(SUBNET_ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());
                machine.place(helper, machinePosition);
                if (!machine.ejectsIntoEndpoint()) {
                    helper.setBlock(HOPPER, Blocks.HOPPER.defaultBlockState()
                            .setValue(HopperBlock.FACING, Direction.DOWN));
                }
                placeStorageBus();
                machine.prepare(helper, machinePosition);
                stage = 1;
                helper.fail("Placed the Provider, the Endpoint, its subnet and the machine");
            }
            case 1 -> {
                helper.assertTrue(node(CHEST) != null && node(CHEST).isActive() && node(PROVIDER) != null
                        && node(CHEST).getGrid() == node(PROVIDER).getGrid(), "Waiting for the Provider's network");
                helper.assertTrue(FederationDomainRegistryAccess.confirmedNetworkId(grid()).isPresent(),
                        "Waiting for the Provider network's identity");
                helper.assertTrue(provider().runtime().isPresent(), "Waiting for the Provider to start");
                helper.assertTrue(binding() != null, "Waiting for the Endpoint to join the Provider's domain");
                helper.assertFalse(grid().getCraftingService().getCpus().isEmpty(), "Waiting for the CPU");
                var pattern = PatternDetailsHelper.encodeProcessingPattern(
                        List.of(new GenericStack(machine.input(), 1)),
                        List.of(new GenericStack(machine.output(), machine.outputPerInput())));
                // succeedWhen retries this stage until the mapping is accepted, so the pattern goes in once.
                if (provider().getTerminalPatternInventory().getStackInSlot(0).isEmpty()) {
                    helper.assertTrue(provider().getTerminalPatternInventory().insertItem(0, pattern, false).isEmpty(),
                            "The Provider must take the processing pattern");
                }
                var status = provider().toggleEndpoint(provider().mappedProvider().mappingHandle(0), binding());
                helper.assertTrue(status.startsWith("accepted-"), "Mapping the pattern to the Endpoint: " + status);
                stage = 2;
                helper.fail("Mapped the pattern to the Endpoint");
            }
            case 2 -> {
                helper.assertTrue(grid().getCraftingService().isCraftable(machine.output()),
                        "Waiting for the Provider's pattern to be craftable");
                helper.assertValueEqual(grid().getStorageService().getInventory().insert(machine.input(), JOBS,
                        Actionable.MODULATE, IActionSource.empty()), JOBS, "The ME Chest must take the inputs");
                begin();
                stage = 3;
                helper.fail("Planning the request");
            }
            case 3 -> {
                helper.assertTrue(planFuture.isDone(), "Waiting for the plan");
                var plan = plan();
                helper.assertFalse(plan.simulation(), "The inputs must be enough: " + plan.missingItems());
                helper.assertTrue(grid().getCraftingService().submitJob(plan, null, null, false, IActionSource.empty())
                        .successful(), "The CPU must take the job");
                stage = 4;
                helper.fail("Submitted the job");
            }
            default -> {
                var stored = grid().getStorageService().getInventory().getAvailableStacks();
                helper.assertValueEqual(stored.get(machine.output()), requested(),
                        "Waiting for the machine's output to return through the Endpoint: " + diagnostics());
                helper.assertTrue(grid().getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                        "Waiting for the job to finish");
                helper.assertValueEqual(stored.get(machine.input()), 0L, "The inputs were used");
            }
        }
    }

    private long requested() {
        return JOBS * machine.outputPerInput();
    }

    /** A cable up from the Endpoint's east face to a Storage Bus on the machine's input face. */
    private void placeStorageBus() {
        var level = helper.getLevel();
        var cable = AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT);
        var route = new java.util.ArrayList<BlockPos>();
        BlockPos bus;
        Direction busFacing;
        if (machine.inputFace() == Direction.UP) {
            bus = machinePosition.above();
            busFacing = Direction.DOWN;
        } else if (machine.inputFace() == Direction.EAST) {
            bus = machinePosition.east();
            busFacing = Direction.WEST;
        } else {
            throw new IllegalArgumentException("Unsupported input face " + machine.inputFace());
        }
        for (var y = ENDPOINT.getY(); y <= bus.getY(); y++) {
            route.add(new BlockPos(ENDPOINT.getX() + 1, y, ENDPOINT.getZ()));
        }
        if (!route.contains(bus)) route.add(bus);
        for (var position : route) {
            helper.assertTrue(PartHelper.setPart(level, helper.absolutePos(position), null, null, cable) != null,
                    "A cable must go at " + position);
        }
        helper.assertTrue(PartHelper.setPart(level, helper.absolutePos(bus), busFacing, null,
                AEParts.STORAGE_BUS.asItem()) != null, "A Storage Bus must go on the cable at " + bus);
    }

    private String diagnostics() {
        var lane = provider().laneCount() == 0 ? "no lane" : "send=" + provider().lane(0).hasPendingSend()
                + " return=" + !provider().lane(0).getReturnInv().isEmpty();
        return "cpuBusy=" + grid().getCraftingService().getCpus().stream().anyMatch(cpu -> cpu.isBusy()) + " " + lane
                + " input=" + grid().getStorageService().getInventory().getAvailableStacks().get(machine.input())
                + " machine=" + machine.state(helper, machinePosition);
    }

    private FederationPatternProviderBlockEntity provider() {
        return helper.getBlockEntity(PROVIDER);
    }

    private EndpointTargetBinding binding() {
        return EndpointTargetBinding.findEndpoint(helper.getLevel(), helper.absolutePos(ENDPOINT));
    }

    private IGrid grid() {
        return node(CHEST).getGrid();
    }

    private IGridNode node(BlockPos position) {
        return helper.getLevel().getBlockEntity(helper.absolutePos(position)) instanceof IGridConnectedBlockEntity entity
                ? entity.getMainNode().getNode() : null;
    }

    private void begin() {
        var node = node(CHEST);
        ICraftingSimulationRequester requester = new ICraftingSimulationRequester() {
            @Override
            public IActionSource getActionSource() {
                return IActionSource.empty();
            }

            @Override
            public IGridNode getGridNode() {
                return node;
            }
        };
        planFuture = grid().getCraftingService().beginCraftingCalculation(helper.getLevel(), requester,
                machine.output(), requested(), CalculationStrategy.REPORT_MISSING_ITEMS);
    }

    private ICraftingPlan plan() {
        try {
            return planFuture.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Crafting calculation interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Crafting calculation failed", exception);
        }
    }
}
