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
import appeng.helpers.patternprovider.PatternContainer;
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

        /** What sits between the machine and the Endpoint and moves the product down into it: a hopper unless set. */
        default net.minecraft.world.level.block.state.BlockState collector() {
            return Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN);
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
    private final String cpuId;
    private final BlockPos machinePosition;
    private boolean checkTerminal;
    private String localProviderId;
    private boolean localProviderPart;
    private java.util.function.BiPredicate<Object, net.minecraft.world.item.ItemStack> installLocal =
            (provider, pattern) -> provider instanceof PatternContainer container
                    && container.getTerminalPatternInventory().addItems(pattern).isEmpty();
    private long jobs = 2;
    private java.util.function.Consumer<FederationPatternProviderBlockEntity> prepareProvider = provider -> {
    };
    private java.util.function.Consumer<ICraftingPlan> checkPlan = plan -> {
    };
    private boolean localPatternInstalled;
    private int stage;
    private Future<ICraftingPlan> planFuture;

    EndpointMachineScene(GameTestHelper helper, Machine machine) {
        this(helper, machine, null);
    }

    /**
     * With {@code cpuId}, the Provider network's only crafting CPU is that single block, standing on a cable in the
     * CPU's place: some addon CPUs connect only through their top and bottom.
     */
    EndpointMachineScene(GameTestHelper helper, Machine machine, String cpuId) {
        this.helper = helper;
        this.machine = machine;
        this.cpuId = cpuId;
        machinePosition = machine.ejectsIntoEndpoint() ? HOPPER : HOPPER.above();
    }

    /** How many inputs the Provider's network stores and uses up; two unless set. */
    EndpointMachineScene requesting(long jobs) {
        this.jobs = jobs;
        return this;
    }

    /** Sets up the Federation Pattern Provider before its pattern goes in, as its player would in its screen. */
    EndpointMachineScene preparingProvider(java.util.function.Consumer<FederationPatternProviderBlockEntity> prepare) {
        prepareProvider = prepare;
        return this;
    }

    /** Checks the plan before it is submitted, such as how an addon rewrote it. */
    EndpointMachineScene checkingPlan(java.util.function.Consumer<ICraftingPlan> check) {
        checkPlan = check;
        return this;
    }

    /**
     * Puts another mod's pattern provider ({@code providerId}) where the Federation Pattern Provider would be. Touching
     * the Endpoint's Federation face, it runs the Endpoint in Local mode: it pushes the pattern's input into the
     * Endpoint, and the Endpoint returns the product to it.
     */
    EndpointMachineScene throughLocalProvider(String providerId) {
        localProviderId = providerId;
        return this;
    }

    /** As {@link #throughLocalProvider}, with a cable-part provider ({@code partItemId}) on a cable, facing the Endpoint. */
    EndpointMachineScene throughLocalProviderPart(String partItemId) {
        localProviderId = partItemId;
        localProviderPart = true;
        return this;
    }

    /** How the Local provider takes the pattern, for providers a player fills another way. */
    EndpointMachineScene installingLocalPatternWith(
            java.util.function.BiPredicate<Object, net.minecraft.world.item.ItemStack> install) {
        installLocal = install;
        return this;
    }

    /**
     * After the job, checks what AE2's Pattern Access Terminal lists on the Provider's network: the Provider once,
     * holding its pattern, and none of its internal lanes.
     */
    EndpointMachineScene checkingPatternAccessTerminal() {
        checkTerminal = true;
        return this;
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
                if (cpuId == null) {
                    helper.setBlock(CPU, AEBlocks.CRAFTING_STORAGE_1K.block());
                } else {
                    helper.assertTrue(PartHelper.setPart(helper.getLevel(), helper.absolutePos(CPU), null, null,
                            AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT)) != null, "A cable must go at " + CPU);
                    helper.setBlock(CPU.above(), AddonCraftingScene.block(cpuId));
                }
                if (localProviderId == null) {
                    helper.setBlock(PROVIDER, ProcessingRegistration.PROVIDER.get().defaultBlockState()
                            .setValue(BlockStateProperties.FACING, Direction.EAST));
                } else if (localProviderPart) {
                    var level = helper.getLevel();
                    var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                            net.minecraft.resources.ResourceLocation.parse(localProviderId));
                    helper.assertTrue(item instanceof appeng.api.parts.IPartItem<?>, localProviderId + " is not a part");
                    helper.assertTrue(PartHelper.setPart(level, helper.absolutePos(PROVIDER), null, null,
                            AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT)) != null
                            && PartHelper.setPart(level, helper.absolutePos(PROVIDER), Direction.EAST, null,
                                    (appeng.api.parts.IPartItem<?>) item) != null,
                            localProviderId + " must go on a cable at " + PROVIDER);
                } else {
                    helper.setBlock(PROVIDER, AddonCraftingScene.block(localProviderId));
                }
                helper.setBlock(ENDPOINT, ProcessingRegistration.ENDPOINT.get().defaultBlockState()
                        .setValue(BlockStateProperties.FACING, Direction.WEST));
                helper.setBlock(SUBNET_ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());
                machine.place(helper, machinePosition);
                if (!machine.ejectsIntoEndpoint()) {
                    helper.setBlock(HOPPER, machine.collector());
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
                if (localProviderId != null) {
                    installLocalPattern();
                    stage = 2;
                    helper.fail("Put the pattern into " + localProviderId);
                }
                helper.assertTrue(provider().runtime().isPresent(), "Waiting for the Provider to start");
                helper.assertTrue(binding() != null, "Waiting for the Endpoint to join the Provider's domain");
                helper.assertFalse(grid().getCraftingService().getCpus().isEmpty(),
                        "Waiting for the CPU" + (cpuId == null ? "" : " from " + cpuId));
                var pattern = pattern();
                // succeedWhen retries this stage until the mapping is accepted, so the pattern goes in once.
                if (provider().getTerminalPatternInventory().getStackInSlot(0).isEmpty()) {
                    prepareProvider.accept(provider());
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
                if (localProviderId != null) {
                    var mode = binding() == null ? null : binding().runtime().mode().orElse(null);
                    helper.assertTrue(mode instanceof space.controlnet.ae2federation.processing.endpoint
                            .EndpointModeGeneration.Local, localProviderId + " must run the Endpoint in Local mode: "
                            + mode);
                }
                helper.assertValueEqual(grid().getStorageService().getInventory().insert(machine.input(), jobs,
                        Actionable.MODULATE, IActionSource.empty()), jobs, "The ME Chest must take the inputs");
                begin();
                stage = 3;
                helper.fail("Planning the request");
            }
            case 3 -> {
                helper.assertTrue(planFuture.isDone(), "Waiting for the plan");
                var plan = plan();
                helper.assertFalse(plan.simulation(), "The inputs must be enough: " + plan.missingItems());
                checkPlan.accept(plan);
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
                if (checkTerminal) checkPatternAccessTerminal();
            }
        }
    }

    private void installLocalPattern() {
        helper.assertFalse(grid().getCraftingService().getCpus().isEmpty(), "Waiting for the CPU");
        var entity = helper.getLevel().getBlockEntity(helper.absolutePos(PROVIDER));
        Object provider = entity instanceof appeng.api.parts.IPartHost parts ? parts.getPart(Direction.EAST) : entity;
        helper.assertTrue(provider instanceof PatternContainer, localProviderId + " is not a pattern container");
        // succeedWhen retries this stage until the pattern is craftable, so the pattern goes in once.
        if (!localPatternInstalled) {
            helper.assertTrue(installLocal.test(provider, pattern()), localProviderId + " refused the pattern");
            localPatternInstalled = true;
        }
    }

    private net.minecraft.world.item.ItemStack pattern() {
        return PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(machine.input(), 1)),
                List.of(new GenericStack(machine.output(), machine.outputPerInput())));
    }

    /** The containers AE2's Pattern Access Terminal lists, found the way its menu finds them. */
    private void checkPatternAccessTerminal() {
        var listed = new java.util.ArrayList<PatternContainer>();
        for (var type : grid().getMachineClasses()) {
            if (!PatternContainer.class.isAssignableFrom(type)) continue;
            for (var machine : grid().getActiveMachines(type)) {
                if (machine instanceof PatternContainer container && container.isVisibleInTerminal()) {
                    listed.add(container);
                }
            }
        }
        helper.assertTrue(listed.size() == 1 && listed.getFirst() == provider(),
                "AE2's Pattern Access Terminal must list the Provider once: " + listed);
        helper.assertFalse(provider().getTerminalPatternInventory().getStackInSlot(0).isEmpty(),
                "The terminal must show the Provider's pattern");
    }

    private long requested() {
        return jobs * machine.outputPerInput();
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
        var lane = localProviderId != null ? "local=" + localProviderId : provider().laneCount() == 0 ? "no lane" : "send=" + provider().lane(0).hasPendingSend()
                + " return=" + !provider().lane(0).getReturnInv().isEmpty();
        return "cpuBusy=" + grid().getCraftingService().getCpus().stream().anyMatch(cpu -> cpu.isBusy()) + " cpus="
                + grid().getCraftingService().getCpus().stream().map(cpu -> cpu.getClass().getSimpleName()).toList()
                + " " + lane
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
        var entity = helper.getLevel().getBlockEntity(helper.absolutePos(position));
        if (entity instanceof appeng.api.parts.IPartHost parts) {
            var cable = parts.getPart(null);
            return cable == null ? null : cable.getGridNode();
        }
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
