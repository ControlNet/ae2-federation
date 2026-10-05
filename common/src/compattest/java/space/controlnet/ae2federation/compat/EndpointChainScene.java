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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetBinding;
import space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity;
import space.controlnet.ae2federation.router.RouterRegistration;

/**
 * A production line of machines, each behind its own Processing Endpoint, built as a player builds it. One Federation
 * Pattern Provider holds a processing pattern per machine and maps each to that machine's Endpoint; the Endpoints'
 * Federation faces sit on one Federation Cable from the Provider's front. Each Endpoint's subnet, powered through the
 * Endpoint, stores the delivered input through a Storage Bus on the machine's top, and a hopper under the machine
 * pushes the product into the Endpoint. Ordering the last machine's product makes the CPU run every step in turn: each
 * intermediate product returns to the Provider's network and goes out again to the next machine.
 */
final class EndpointChainScene {
    private static final BlockPos ENERGY = new BlockPos(1, 1, 3);
    private static final BlockPos CHEST = new BlockPos(2, 1, 3);
    private static final BlockPos CPU = new BlockPos(3, 1, 3);
    private static final BlockPos PROVIDER = new BlockPos(4, 1, 3);
    /** Endpoints stand this far apart, so one subnet's cable never touches the next Endpoint. */
    private static final int SPACING = 3;

    private final GameTestHelper helper;
    private final List<EndpointMachineScene.Machine> machines;
    private final long inputs;
    private int cutStage = -1;
    private int cutTicks;
    private BlockState cutCollector;
    private long resumeAt;
    private String leaked;
    private int stage;
    private Future<ICraftingPlan> planFuture;

    /** {@code machines} in the order the line runs them; each takes the previous one's product. */
    EndpointChainScene(GameTestHelper helper, long inputs, EndpointMachineScene.Machine... machines) {
        this.helper = helper;
        this.inputs = inputs;
        this.machines = List.of(machines);
    }

    /**
     * Takes away the hopper under machine {@code index} as the job is submitted; for {@code ticks}, longer than the
     * steps before it and one of its own, the job must wait with no final product returned, and then the hopper goes
     * back and the job finishes, as the guide's exercise has its player do.
     */
    EndpointChainScene cuttingOff(int index, int ticks) {
        cutStage = index;
        cutTicks = ticks;
        return this;
    }

    /** Runs the whole job; call from {@code succeedWhen}. */
    void tick() {
        switch (stage) {
            case 0 -> {
                helper.setBlock(ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());
                helper.setBlock(CHEST, AEBlocks.ME_CHEST.block());
                helper.<MEChestBlockEntity>getBlockEntity(CHEST).setCell(AEItems.ITEM_CELL_1K.stack());
                helper.setBlock(CPU, AEBlocks.CRAFTING_STORAGE_1K.block());
                helper.setBlock(PROVIDER, ProcessingRegistration.PROVIDER.get().defaultBlockState()
                        .setValue(BlockStateProperties.FACING, Direction.EAST));
                for (var x = PROVIDER.getX() + 1; x <= endpoint(machines.size() - 1).getX(); x++) {
                    helper.setBlock(new BlockPos(x, PROVIDER.getY(), PROVIDER.getZ()),
                            RouterRegistration.FEDERATION_CABLE.get());
                }
                for (var index = 0; index < machines.size(); index++) placeCell(index);
                stage = 1;
                helper.fail("Placed the Provider, the Federation Cable and each machine's Endpoint and subnet");
            }
            case 1 -> {
                helper.assertTrue(node(CHEST) != null && node(CHEST).isActive() && node(PROVIDER) != null
                        && node(CHEST).getGrid() == node(PROVIDER).getGrid(), "Waiting for the Provider's network");
                helper.assertTrue(FederationDomainRegistryAccess.confirmedNetworkId(grid()).isPresent(),
                        "Waiting for the Provider network's identity");
                helper.assertTrue(provider().runtime().isPresent(), "Waiting for the Provider to start");
                helper.assertFalse(grid().getCraftingService().getCpus().isEmpty(), "Waiting for the CPU");
                for (var index = 0; index < machines.size(); index++) {
                    helper.assertTrue(binding(index) != null,
                            "Waiting for Endpoint " + index + " to join the Provider's domain");
                }
                // succeedWhen retries this stage until every mapping is accepted, so each pattern goes in once.
                for (var index = 0; index < machines.size(); index++) {
                    if (provider().getTerminalPatternInventory().getStackInSlot(index).isEmpty()) {
                        helper.assertTrue(provider().getTerminalPatternInventory()
                                .insertItem(index, pattern(machines.get(index)), false).isEmpty(),
                                "The Provider must take pattern " + index);
                    }
                }
                for (var index = 0; index < machines.size(); index++) {
                    var status = provider().toggleEndpoint(provider().mappedProvider().mappingHandle(index),
                            binding(index));
                    helper.assertTrue(status.startsWith("accepted-"),
                            "Mapping pattern " + index + " to its Endpoint: " + status);
                }
                stage = 2;
                helper.fail("Mapped each pattern to its machine's Endpoint");
            }
            case 2 -> {
                helper.assertTrue(grid().getCraftingService().isCraftable(last().output()),
                        "Waiting for the line's product to be craftable");
                helper.assertValueEqual(grid().getStorageService().getInventory().insert(first().input(), inputs,
                        Actionable.MODULATE, IActionSource.empty()), inputs, "The ME Chest must take the inputs");
                begin();
                stage = 3;
                helper.fail("Planning the request");
            }
            case 3 -> {
                helper.assertTrue(planFuture.isDone(), "Waiting for the plan");
                var plan = plan();
                helper.assertFalse(plan.simulation(), "The inputs must be enough: " + plan.missingItems());
                if (cutStage >= 0) {
                    cutCollector = helper.getBlockState(hopper(cutStage));
                    helper.setBlock(hopper(cutStage), Blocks.AIR);
                }
                helper.assertTrue(grid().getCraftingService().submitJob(plan, null, null, false, IActionSource.empty())
                        .successful(), "The CPU must take the job");
                resumeAt = helper.getTick() + cutTicks;
                stage = cutStage >= 0 ? 4 : 5;
                helper.fail("Submitted the job");
            }
            case 4 -> {
                var returned = stored().get(last().output());
                var busy = grid().getCraftingService().getCpus().stream().anyMatch(cpu -> cpu.isBusy());
                if (leaked == null && (returned != 0 || !busy)) {
                    leaked = "returned=" + returned + " busy=" + busy + " at tick " + helper.getTick();
                }
                helper.assertTrue(helper.getTick() >= resumeAt, "Waiting while machine " + cutStage
                        + " has no hopper: " + diagnostics());
                helper.setBlock(hopper(cutStage), cutCollector);
                stage = 5;
                helper.fail("Put the hopper back");
            }
            default -> {
                helper.assertTrue(leaked == null, "The job must wait while a machine has no hopper: " + leaked);
                helper.assertValueEqual(stored().get(last().output()), requested(),
                        "Waiting for the line's product to return: " + diagnostics());
                helper.assertTrue(grid().getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                        "Waiting for the job to finish");
                for (var machine : machines) {
                    helper.assertValueEqual(stored().get(machine.input()), 0L,
                            "Every step's input was used: " + machine.input());
                }
            }
        }
    }

    /** Endpoint {@code index}, with its Federation face north on the cable. */
    private BlockPos endpoint(int index) {
        return new BlockPos(PROVIDER.getX() + 1 + SPACING * index, PROVIDER.getY(), PROVIDER.getZ() + 1);
    }

    private BlockPos hopper(int index) {
        return endpoint(index).above();
    }

    private BlockPos machinePosition(int index) {
        return hopper(index).above();
    }

    /** The Endpoint, the hopper and machine on top of it, and a cable from its east face to a Storage Bus on the top. */
    private void placeCell(int index) {
        var machine = machines.get(index);
        var endpoint = endpoint(index);
        helper.setBlock(endpoint, ProcessingRegistration.ENDPOINT.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.NORTH));
        machine.place(helper, machinePosition(index));
        helper.setBlock(hopper(index), machine.collector());
        var level = helper.getLevel();
        var cable = AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT);
        var bus = machinePosition(index).above();
        var route = new java.util.ArrayList<BlockPos>();
        for (var y = endpoint.getY(); y <= bus.getY(); y++) route.add(endpoint.east().atY(y));
        route.add(bus);
        for (var position : route) {
            helper.assertTrue(PartHelper.setPart(level, helper.absolutePos(position), null, null, cable) != null,
                    "A cable must go at " + position);
        }
        helper.assertTrue(PartHelper.setPart(level, helper.absolutePos(bus), Direction.DOWN, null,
                AEParts.STORAGE_BUS.asItem()) != null, "A Storage Bus must go on the cable at " + bus);
        machine.prepare(helper, machinePosition(index));
    }

    private static net.minecraft.world.item.ItemStack pattern(EndpointMachineScene.Machine machine) {
        return PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(machine.input(), 1)),
                List.of(new GenericStack(machine.output(), machine.outputPerInput())));
    }

    private EndpointMachineScene.Machine first() {
        return machines.getFirst();
    }

    private EndpointMachineScene.Machine last() {
        return machines.getLast();
    }

    /** What the inputs make once every step has run. */
    private long requested() {
        var amount = inputs;
        for (var machine : machines) amount *= machine.outputPerInput();
        return amount;
    }

    private appeng.api.stacks.KeyCounter stored() {
        return grid().getStorageService().getInventory().getAvailableStacks();
    }

    private String diagnostics() {
        var text = new StringBuilder("cpuBusy=")
                .append(grid().getCraftingService().getCpus().stream().anyMatch(cpu -> cpu.isBusy()));
        for (var index = 0; index < machines.size(); index++) {
            var machine = machines.get(index);
            text.append(" [").append(machine.blockId()).append(" input=").append(stored().get(machine.input()))
                    .append(" output=").append(stored().get(machine.output())).append(' ')
                    .append(machine.state(helper, machinePosition(index))).append(']');
        }
        return text.toString();
    }

    private FederationPatternProviderBlockEntity provider() {
        return helper.getBlockEntity(PROVIDER);
    }

    private EndpointTargetBinding binding(int index) {
        return EndpointTargetBinding.findEndpoint(helper.getLevel(), helper.absolutePos(endpoint(index)));
    }

    private IGrid grid() {
        return node(CHEST).getGrid();
    }

    private IGridNode node(BlockPos position) {
        var entity = helper.getLevel().getBlockEntity(helper.absolutePos(position));
        return entity instanceof IGridConnectedBlockEntity connected ? connected.getMainNode().getNode() : null;
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
                last().output(), requested(), CalculationStrategy.REPORT_MISSING_ITEMS);
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
