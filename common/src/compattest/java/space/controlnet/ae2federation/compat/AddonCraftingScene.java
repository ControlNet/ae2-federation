package space.controlnet.ae2federation.compat;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import space.controlnet.ae2federation.crafting.projection.CraftingReturnLedger;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.test.policy.PolicyBridgeFixtures;

/**
 * Remote crafting with blocks named by registry id: a pattern provider (and its assembler) on the provider network,
 * and the crafting CPU blocks of the consumer network. Blocks are placed as a player places them, joining the network
 * through the cable beside them. The consumer's CPU crafts four sticks with the provider's pattern.
 */
final class AddonCraftingScene {
    private static final BlockPos BASE = new BlockPos(5, 3, 5);
    /** Where the provider stands: east of the provider network's last cable, which is west of it. */
    static final BlockPos PROVIDER = BASE.east(2).north();
    /** The consumer's first CPU block, west of its cable; the others run on west from it. */
    static final BlockPos FIRST_CPU = BASE.west();
    /** The provider network's last cable. */
    static final BlockPos PROVIDER_CABLE = PROVIDER.west();
    private static final AEItemKey PLANKS = AEItemKey.of(Items.OAK_PLANKS);
    private static final AEItemKey STICKS = AEItemKey.of(Items.STICK);
    private static final AEItemKey COBBLESTONE = AEItemKey.of(Items.COBBLESTONE);
    private static final AEItemKey STONE = AEItemKey.of(Items.STONE);

    private final GameTestHelper helper;
    private final PolicyBridgeFixtures bridge;
    private final String providerId;
    private final String assemblerId;
    private final List<String> cpuIds;
    private final boolean processing;
    private final Machine machine;
    private java.util.function.Consumer<net.minecraft.world.level.block.entity.BlockEntity> prepareProvider = entity -> {
    };
    // Through the inventory a Pattern Access Terminal fills, which every provider keeps to its own rules.
    private java.util.function.BiPredicate<net.minecraft.world.level.block.entity.BlockEntity, ItemStack> installPattern =
            (entity, pattern) -> ((PatternContainer) entity).getTerminalPatternInventory().addItems(pattern).isEmpty();
    private java.util.function.BiConsumer<ICraftingPlan, ICraftingPlan> checkPlan = (plan, local) -> {
    };
    private boolean cpuOnCable;
    private boolean cancelAfterPush;
    private boolean disconnectAfterPush;
    private long jobs = 2;
    private java.util.function.Consumer<GameTestHelper> placeStructure;
    private java.util.function.Consumer<GameTestHelper> placeConsumerCpu;
    private java.util.function.BooleanSupplier consumerCpuReady = () -> true;
    private java.util.function.Consumer<GameTestHelper> placeConsumerStorage;
    /** The consumer's own storage when it is not its ME Chest; null until that storage is ready. */
    private java.util.function.Supplier<MEStorage> consumerStorage;
    /** When a structure scene's provider is ready: by default, once its multiblock has formed. */
    private java.util.function.Predicate<net.minecraft.world.level.block.entity.BlockEntity> structureReady =
            entity -> entity instanceof appeng.me.cluster.IAEMultiBlock<?> part && part.getCluster() != null;
    private BlockPos providerPos = PROVIDER;
    private BlockPos dismantlePart;
    private net.minecraft.world.level.block.state.BlockState dismantledState;
    private java.util.function.Consumer<GameTestHelper> reorderChange;
    private java.util.function.Consumer<GameTestHelper> reorderRestore;
    private CpuCheck cpuCheck;
    /** The consumer CPU that took each order, found right after it took it. */
    private final List<appeng.api.networking.crafting.ICraftingCPU> tookOrders = new java.util.ArrayList<>();
    private final BlockPos assemblerPos = PROVIDER.east();
    private final BlockPos outputChestPos = PROVIDER.below();
    private int stage;
    private boolean energyShared;
    private Future<ICraftingPlan> planFuture;
    private Future<ICraftingPlan> localPlanFuture;

    AddonCraftingScene(GameTestHelper helper, String providerId, String assemblerId, List<String> cpuIds) {
        this(helper, providerId, assemblerId, cpuIds, false);
    }

    /**
     * With {@code processing}, the pattern turns cobblestone into stone, the "assembler" is a chest the provider pushes
     * into, and the test runs that machine: it takes the cobblestone and puts the stone into the provider network.
     */
    AddonCraftingScene(GameTestHelper helper, String providerId, String assemblerId, List<String> cpuIds,
            boolean processing) {
        this(helper, providerId, cpuIds, processing ? new ChestMachine(assemblerId) : null, assemblerId);
    }

    /** A processing scene whose pattern goes through {@code machine}, a real machine beside the provider. */
    AddonCraftingScene(GameTestHelper helper, String providerId, List<String> cpuIds, Machine machine) {
        this(helper, providerId, cpuIds, machine, machine.blockId());
    }

    private AddonCraftingScene(GameTestHelper helper, String providerId, List<String> cpuIds, Machine machine,
            String assemblerId) {
        this.helper = helper;
        this.providerId = providerId;
        this.assemblerId = assemblerId;
        this.cpuIds = cpuIds;
        this.processing = machine != null;
        this.machine = machine;
        bridge = new PolicyBridgeFixtures(helper, BASE);
        // As the guide's examples build it, the provider network has no power of its own: the ME power rule shares
        // the consumer's.
        bridge.installStorageCells(false);
    }

    /**
     * The provider is a multiblock with no assembler beside it: {@code place} builds it from {@link #PROVIDER}, which
     * touches the provider network's cable, and its block at {@code patternContainer} takes the pattern and crafts.
     */
    static AddonCraftingScene structure(GameTestHelper helper, String name, List<String> cpuIds,
            java.util.function.Consumer<GameTestHelper> place, BlockPos patternContainer) {
        var scene = new AddonCraftingScene(helper, name, cpuIds, null, null);
        scene.placeStructure = place;
        scene.providerPos = patternContainer;
        return scene;
    }

    /**
     * As {@link #structure(GameTestHelper, String, List, java.util.function.Consumer, BlockPos)}, for a provider that
     * crafts by itself without being an AE2 multiblock: the consumer orders once {@code ready} agrees.
     */
    static AddonCraftingScene structure(GameTestHelper helper, String name, List<String> cpuIds,
            java.util.function.Consumer<GameTestHelper> place, BlockPos patternContainer,
            java.util.function.Predicate<net.minecraft.world.level.block.entity.BlockEntity> ready) {
        var scene = structure(helper, name, cpuIds, place, patternContainer);
        scene.structureReady = ready;
        return scene;
    }

    /**
     * A structure whose block at {@code patternContainer} takes a processing pattern for {@code machine}, the
     * structure itself, and crafts once {@code ready} says so; {@code place} may take several ticks, so {@code ready}
     * is asked every tick until it agrees.
     */
    static AddonCraftingScene processingStructure(GameTestHelper helper, String name, List<String> cpuIds,
            Machine machine, java.util.function.Consumer<GameTestHelper> place, BlockPos patternContainer,
            java.util.function.Predicate<net.minecraft.world.level.block.entity.BlockEntity> ready) {
        var scene = new AddonCraftingScene(helper, name, cpuIds, machine, null);
        scene.placeStructure = place;
        scene.providerPos = patternContainer;
        scene.structureReady = ready;
        return scene;
    }

    /**
     * The consumer's CPU is a multiblock, which {@code place} builds instead of the CPU blocks. The consumer orders once
     * {@code ready} agrees; it is asked every tick and may finish the CPU as its player does.
     */
    AddonCraftingScene consumerCpuStructure(java.util.function.Consumer<GameTestHelper> place,
            java.util.function.BooleanSupplier ready) {
        placeConsumerCpu = place;
        consumerCpuReady = ready;
        return this;
    }

    /**
     * After the job, breaks the multiblock's block at {@code part}: its recipes must leave the consumer, and come back
     * once the block is put back and the structure forms again, as the guide's exercise has its player do.
     */
    AddonCraftingScene dismantlingAfterwards(BlockPos part) {
        dismantlePart = part;
        return this;
    }

    /**
     * After the job, {@code change} alters the world as the guide's exercise has its player do, and the consumer orders
     * again; the second job must finish exactly too. {@code check} sees each finished order's CPU.
     */
    AddonCraftingScene reorderingAfterwards(java.util.function.Consumer<GameTestHelper> change, CpuCheck check) {
        reorderChange = change;
        cpuCheck = check;
        return this;
    }

    /**
     * As {@link #reorderingAfterwards(java.util.function.Consumer, CpuCheck)}, where {@code change} takes the recipe
     * away from the consumer: once it has left, {@code restore} brings it back before the consumer orders again.
     */
    AddonCraftingScene reorderingAfterwards(java.util.function.Consumer<GameTestHelper> change,
            java.util.function.Consumer<GameTestHelper> restore, CpuCheck check) {
        reorderRestore = restore;
        return reorderingAfterwards(change, check);
    }

    /** Checks the consumer CPU that ran an order, the first being 1, against the provider network. */
    interface CpuCheck {
        void check(int order, appeng.api.networking.crafting.ICraftingCPU cpu, IGrid provider);
    }

    /** The machine a processing pattern names: its block, what it makes from what, and how it runs. */
    interface Machine {
        String blockId();

        default AEItemKey input() {
            return COBBLESTONE;
        }

        /** What one craft takes: by default one {@link #input()}. */
        default java.util.Map<AEItemKey, Long> inputs() {
            return java.util.Map.of(input(), 1L);
        }

        AEKey output();

        /** How much of {@link #output()} one craft makes. */
        default long outputPerInput() {
            return 1;
        }

        /** A storage cell on the provider network for an output an item cell cannot hold; null when none is needed. */
        default String outputCell() {
            return null;
        }

        /** Places what the machine needs besides its own block, such as a motor; the block is already placed. */
        default void placeAround(GameTestHelper helper, BlockPos position) {
        }

        /** Moves whatever the machine has made so far into the provider network. */
        void collect(GameTestHelper helper, BlockPos position, MEStorage network);
    }

    /** A chest standing in for a furnace: the test turns whatever cobblestone the provider pushed into stone. */
    private record ChestMachine(String blockId) implements Machine {
        @Override
        public AEItemKey output() {
            return STONE;
        }

        @Override
        public void collect(GameTestHelper helper, BlockPos position, MEStorage network) {
            var chest = helper.<net.minecraft.world.level.block.entity.ChestBlockEntity>getBlockEntity(position);
            long pushed = 0;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                if (chest.getItem(slot).is(Items.COBBLESTONE)) pushed += chest.getItem(slot).getCount();
            }
            if (pushed == 0) return;
            chest.clearContent();
            network.insert(STONE, pushed, Actionable.MODULATE, IActionSource.empty());
        }
    }

    static Block block(String id) {
        var key = ResourceLocation.parse(id);
        if (!BuiltInRegistries.BLOCK.containsKey(key)) throw new IllegalStateException("Block " + id + " is not registered");
        return BuiltInRegistries.BLOCK.get(key);
    }

    static net.minecraft.world.item.Item item(String id) {
        var key = ResourceLocation.parse(id);
        if (!BuiltInRegistries.ITEM.containsKey(key)) throw new IllegalStateException("Item " + id + " is not registered");
        return BuiltInRegistries.ITEM.get(key);
    }

    /**
     * The consumer keeps its items in a multiblock that {@code place} builds on its cable instead of its ME Chest, which
     * is left without a cell: the inputs are stored there, and the made output and used inputs are counted there.
     * {@code storage} gives that multiblock's storage once it is ready, else null.
     */
    AddonCraftingScene consumerStorageStructure(java.util.function.Consumer<GameTestHelper> place,
            java.util.function.Supplier<MEStorage> storage) {
        placeConsumerStorage = place;
        consumerStorage = storage;
        return this;
    }

    /** Readies the provider as its player would before patterns go in, such as by fitting a part into it. */
    AddonCraftingScene preparingProvider(
            java.util.function.Consumer<net.minecraft.world.level.block.entity.BlockEntity> prepare) {
        prepareProvider = prepare;
        return this;
    }

    /** Puts the pattern into a provider that keeps its patterns elsewhere; true when it took the pattern. */
    AddonCraftingScene installingPatternsWith(
            java.util.function.BiPredicate<net.minecraft.world.level.block.entity.BlockEntity, ItemStack> install) {
        installPattern = install;
        return this;
    }

    /**
     * Stacks the CPU blocks on a cable west of the consumer's cable instead of in a row: some addon CPUs connect only
     * through their top and bottom.
     */
    AddonCraftingScene cpuOnCable() {
        cpuOnCable = true;
        return this;
    }

    /** How many inputs the consumer stores and uses up, two unless set: two planks make four sticks. */
    AddonCraftingScene requesting(long jobs) {
        this.jobs = jobs;
        return this;
    }

    /**
     * Cancels the consumer's job once the provider has pushed every input into the chest machine, then runs the
     * machine: the late output stays on the provider network, nothing reaches the consumer and nothing stays owed.
     */
    AddonCraftingScene cancellingAfterPush() {
        if (!(machine instanceof ChestMachine)) throw new IllegalStateException("Only the chest machine can wait");
        cancelAfterPush = true;
        return this;
    }

    /**
     * Removes the Bridge once the provider has pushed every input into the chest machine, then runs the machine: the
     * projected pattern leaves the consumer, the output stays on the provider network, the consumer's job keeps waiting
     * for it and its debt stays owed, as a job waits for an output that went elsewhere.
     */
    AddonCraftingScene disconnectingAfterPush() {
        if (!(machine instanceof ChestMachine)) throw new IllegalStateException("Only the chest machine can wait");
        disconnectAfterPush = true;
        // Apart from the consumer, the provider network still runs, as one with its own power does.
        bridge.powerOuter();
        return this;
    }

    /**
     * Checks the consumer's plan before it is submitted, such as how an addon rewrote it, against the plan the provider
     * network makes for the same request with its own pattern. That plan lacks the inputs, which are on the consumer.
     */
    AddonCraftingScene checkingPlan(java.util.function.BiConsumer<ICraftingPlan, ICraftingPlan> check) {
        checkPlan = check;
        return this;
    }

    /** Runs the whole craft; call from {@code succeedWhen}. */
    void tick() {
        switch (stage) {
            case 0 -> {
                helper.assertTrue(bridge.networksSettled(), "Waiting for both networks");
                if (placeStructure != null) {
                    placeStructure.accept(helper);
                } else {
                    helper.setBlock(providerPos, block(providerId));
                    helper.setBlock(assemblerPos, block(assemblerId));
                }
                if (machine != null) machine.placeAround(helper, assemblerPos);
                if (machine != null && machine.outputCell() != null) {
                    helper.setBlock(outputChestPos, appeng.core.definitions.AEBlocks.ME_CHEST.block());
                    helper.<appeng.blockentity.storage.MEChestBlockEntity>getBlockEntity(outputChestPos)
                            .setCell(new ItemStack(item(machine.outputCell())));
                }
                if (placeConsumerStorage != null) {
                    // As the guide's district has it, the provider network has no storage at all.
                    bridge.consumerChest().setCell(ItemStack.EMPTY);
                    bridge.providerChest().setCell(ItemStack.EMPTY);
                    placeConsumerStorage.accept(helper);
                }
                if (placeConsumerCpu != null) {
                    placeConsumerCpu.accept(helper);
                } else if (cpuOnCable) {
                    helper.assertTrue(appeng.api.parts.PartHelper.setPart(helper.getLevel(),
                            helper.absolutePos(BASE.west()), null, null,
                            appeng.core.definitions.AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT)) != null,
                            "A cable must go west of the consumer's cable");
                    for (int index = 0; index < cpuIds.size(); index++) {
                        helper.setBlock(BASE.west().above(index + 1), block(cpuIds.get(index)));
                    }
                } else {
                    // The CPU blocks run west from the consumer's cable, each touching the one before.
                    for (int index = 0; index < cpuIds.size(); index++) {
                        helper.setBlock(BASE.west(index + 1), block(cpuIds.get(index)));
                    }
                }
                stage = 1;
                helper.fail("Placed the provider, assembler and CPU");
            }
            case 1 -> {
                // What stage 0 placed can join a network, whose identity then settles again.
                helper.assertTrue(bridge.networksSettled(), "Waiting for both networks to settle again");
                bridge.placeFirstBridge();
                stage = 2;
                helper.fail("Placed the Bridge");
            }
            case 2 -> {
                helper.assertTrue(bridge.networksSettled(), "Waiting for both networks: " + bridge.settlementDiagnostics());
                if (!bridge.firstBridgeReady()) bridge.refreshFirstBridge();
                helper.assertTrue(bridge.firstBridgeReady(), "Waiting for the Bridge");
                if (!energyShared) {
                    enableEnergy();
                    energyShared = true;
                }
                helper.assertTrue(bridge.outerGrid().getEnergyService().isNetworkPowered(),
                        "Waiting for the ME power rule to power the provider network");
                if (placeStructure != null) {
                    helper.assertTrue(structureReady.test(helper.getLevel().getBlockEntity(helper.absolutePos(providerPos))),
                            "Waiting for " + providerId + " to form");
                }
                helper.assertTrue(grid(providerPos) == bridge.outerGrid(),
                        providerId + " must join the provider network");
                var entity = helper.getLevel().getBlockEntity(helper.absolutePos(providerPos));
                // Addons with their own provider logic still show it to AE2's Pattern Access Terminal.
                helper.assertTrue(entity instanceof PatternContainer,
                        providerId + " must be a pattern container, but is " + entity);
                prepareProvider.accept(entity);
                helper.assertTrue(installPattern.test(entity, processing ? processingPattern() : stickPattern()),
                        providerId + " refused the pattern");
                if (entity instanceof PatternProviderLogicHost host) host.getLogic().updatePatterns();
                stage = 3;
                helper.fail("Installed the stick pattern");
            }
            case 3 -> {
                helper.assertTrue(bridge.outerGrid().getCraftingService().isCraftable(output()),
                        "Waiting for the provider network to craft with " + providerId + ": " + providerState());
                helper.assertTrue(consumerCpuReady.getAsBoolean(), "Waiting for the consumer's CPU structure");
                helper.assertTrue(consumerStorage == null || consumerStorage.get() != null,
                        "Waiting for the consumer's storage structure");
                helper.assertFalse(consumerGrid().getCraftingService().getCpus().isEmpty(),
                        "Waiting for the consumer's CPU from " + cpuIds);
                helper.assertTrue(FederationDomainRegistryAccess.confirmedNetworkId(consumerGrid()).isPresent()
                        && FederationDomainRegistryAccess.confirmedNetworkId(bridge.outerGrid()).isPresent(),
                        "Waiting for both networks' identities");
                enableRules();
                stage = 4;
                helper.fail("Enabled the rules");
            }
            case 4 -> {
                helper.assertTrue(consumerGrid().getCraftingService().isCraftable(output()),
                        "Waiting for the provider's pattern on the consumer");
                storeInputs("The consumer's chest must take the inputs");
                begin();
                stage = 5;
                helper.fail("Planning the request");
            }
            case 5 -> {
                helper.assertTrue(planFuture.isDone() && localPlanFuture.isDone(), "Waiting for the consumer's plan");
                var plan = plan();
                helper.assertFalse(plan.simulation(), "The consumer's planks must be enough: " + plan.missingItems());
                checkPlan.accept(plan, get(localPlanFuture));
                helper.assertTrue(consumerGrid().getCraftingService()
                        .submitJob(plan, null, null, true, IActionSource.empty()).successful(),
                        "The consumer's CPU must take the job");
                tookOrders.add(busyCpu());
                stage = 6;
                helper.fail("Submitted the job");
            }
            case 6 -> {
                if (!cancelAfterPush && !disconnectAfterPush) {
                    stage = 9;
                    helper.fail("Running the job");
                }
                helper.assertValueEqual(inChest(assemblerPos, Items.COBBLESTONE), jobs,
                        "Waiting for the provider to push every input");
                var busy = consumerGrid().getCraftingService().getCpus().stream().filter(cpu -> cpu.isBusy()).toList();
                helper.assertValueEqual(busy.size(), 1, "The consumer's CPU must be running the job");
                if (disconnectAfterPush) {
                    bridge.removeFirstBridge();
                    stage = 10;
                    helper.fail("Removed the Bridge");
                }
                cancel(busy.getFirst());
                stage = 7;
                helper.fail("Cancelled the consumer's job");
            }
            case 10 -> {
                helper.assertFalse(consumerGrid().getCraftingService().isCraftable(output()),
                        "Waiting for the provider's pattern to leave the consumer");
                machine.collect(helper, assemblerPos, bridge.outerGrid().getStorageService().getInventory());
                stage = 11;
                helper.fail("Ran the machine with the networks apart");
            }
            case 11 -> {
                helper.assertValueEqual(held(providerChest(), output()), jobs,
                        "The output stays on the provider network");
                helper.assertValueEqual(held(consumerChest(), output()), 0L, "Nothing crosses the removed Bridge");
                var busy = consumerGrid().getCraftingService().getCpus().stream().filter(cpu -> cpu.isBusy()).toList();
                helper.assertValueEqual(busy.size(), 1, "The consumer's job keeps waiting");
                helper.assertValueEqual(CraftingReturnLedger.get(helper.getLevel()).owed(bridge.outerNetwork(),
                        bridge.mainNetwork(), output()), jobs, "The debt stays while the consumer still waits");
                cancel(busy.getFirst());
            }
            case 7 -> {
                helper.assertTrue(consumerGrid().getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                        "The consumer's job must be cancelled");
                helper.assertValueEqual(CraftingReturnLedger.get(helper.getLevel()).owed(bridge.outerNetwork(),
                        bridge.mainNetwork(), output()), 0L, "Waiting for the cancelled job's debt to be forgotten");
                machine.collect(helper, assemblerPos, bridge.outerGrid().getStorageService().getInventory());
                stage = 8;
                helper.fail("Ran the machine after the cancel");
            }
            case 8 -> {
                helper.assertValueEqual(held(providerChest(), output()), jobs,
                        "The late output stays on the provider network");
                helper.assertValueEqual(held(consumerChest(), output()), 0L, "Nothing reaches the cancelled consumer");
                helper.assertValueEqual(held(consumerChest(), input()), 0L, "The pushed inputs are not given back twice");
            }
            default -> {
                if (processing) machine.collect(helper, assemblerPos, bridge.outerGrid().getStorageService().getInventory());
                helper.assertTrue(consumerGrid().getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                        "Waiting for the consumer's job to finish");
                if (consumerStorage != null) {
                    helper.assertValueEqual(held(consumerChest(), output()), requested() * tookOrders.size(),
                            "The consumer's own storage must hold everything made");
                }
                var made = held(consumerChest(), output()) + held(providerChest(), output())
                        + (processing && machine.outputCell() != null ? held(outputChest(), output()) : 0);
                helper.assertValueEqual(made, requested() * tookOrders.size(),
                        "The job must store exactly what was requested");
                for (var input : inputs().keySet()) {
                    helper.assertValueEqual(held(consumerChest(), input), 0L, "The consumer's inputs were used");
                }
                if (cpuCheck != null) {
                    var cpu = tookOrders.getLast();
                    helper.assertTrue(cpu != null, "No consumer CPU was busy right after order " + tookOrders.size());
                    cpuCheck.check(tookOrders.size(), cpu, bridge.outerGrid());
                }
                if (reorderChange != null && tookOrders.size() == 1) {
                    reorderChange.accept(helper);
                    stage = reorderRestore != null ? 16 : 14;
                    helper.fail("Changed the world before ordering again");
                }
                if (dismantlePart != null) {
                    dismantledState = helper.getBlockState(dismantlePart);
                    helper.setBlock(dismantlePart, net.minecraft.world.level.block.Blocks.AIR);
                    stage = 12;
                    helper.fail("Broke the structure at " + dismantlePart);
                }
            }
            case 12 -> {
                helper.assertFalse(consumerGrid().getCraftingService().isCraftable(output()),
                        "Waiting for the broken structure's recipes to leave the consumer");
                helper.setBlock(dismantlePart, dismantledState);
                stage = 13;
                helper.fail("Put the structure's block back");
            }
            case 14 -> {
                var cpus = consumerGrid().getCraftingService().getCpus();
                helper.assertTrue(!cpus.isEmpty() && cpus.stream().noneMatch(cpu -> cpu.isBusy()),
                        "Waiting for the consumer's CPU");
                helper.assertTrue(consumerGrid().getCraftingService().isCraftable(output()),
                        "Waiting for the provider's pattern on the consumer");
                storeInputs("The consumer's chest must take the second order's inputs");
                begin();
                stage = 15;
                helper.fail("Planning the second order");
            }
            case 16 -> {
                helper.assertFalse(consumerGrid().getCraftingService().isCraftable(output()),
                        "Waiting for the changed provider's recipe to leave the consumer");
                reorderRestore.accept(helper);
                stage = 14;
                helper.fail("Brought the recipe back");
            }
            case 15 -> {
                helper.assertTrue(planFuture.isDone() && localPlanFuture.isDone(), "Waiting for the second plan");
                var plan = plan();
                helper.assertFalse(plan.simulation(), "The second order's inputs must be enough: " + plan.missingItems());
                helper.assertTrue(consumerGrid().getCraftingService()
                        .submitJob(plan, null, null, true, IActionSource.empty()).successful(),
                        "The consumer's CPU must take the second job");
                tookOrders.add(busyCpu());
                stage = 9;
                helper.fail("Submitted the second job");
            }
            case 13 -> helper.assertTrue(consumerGrid().getCraftingService().isCraftable(output()),
                    "Waiting for the re-formed structure's recipes to return to the consumer");
        }
    }

    /** The consumer's only busy CPU, or null when none or several are busy. */
    private appeng.api.networking.crafting.ICraftingCPU busyCpu() {
        var busy = consumerGrid().getCraftingService().getCpus().stream().filter(cpu -> cpu.isBusy()).toList();
        return busy.size() == 1 ? busy.getFirst() : null;
    }

    /** What the provider reports, for a scene that never gets its pattern. */
    private String providerState() {
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(providerPos)) instanceof PatternProviderLogicHost host)) {
            return "not an AE2 pattern provider logic host";
        }
        var logic = host.getLogic();
        return "patterns=" + logic.getAvailablePatterns().size() + " inventory=" + logic.getPatternInv().size()
                + " active=" + logic.getGrid() + " powered=" + (host.getBlockEntity() instanceof
                appeng.blockentity.grid.AENetworkedBlockEntity networked && networked.getMainNode().isActive());
    }

    private IGrid grid(BlockPos position) {
        var host = GridHelper.getNodeHost(helper.getLevel(), helper.absolutePos(position));
        if (host == null) return null;
        for (var side : Direction.values()) {
            IGridNode node = host.getGridNode(side);
            if (node != null) return node.getGrid();
        }
        return null;
    }

    private IGrid consumerGrid() {
        return bridge.mainGrid();
    }

    /** The consumer's own storage: its ME Chest's cell, or the multiblock that replaces it. */
    private MEStorage consumerChest() {
        if (consumerStorage != null) return Objects.requireNonNull(consumerStorage.get(), "The consumer's storage");
        return Objects.requireNonNull(bridge.consumerChest().getOriginalCellInventory(0));
    }

    /** The provider's ME Chest's cell; an empty storage when it has none. */
    private MEStorage providerChest() {
        var cell = bridge.providerChest().getOriginalCellInventory(0);
        return cell != null ? cell : appeng.me.storage.NullInventory.of();
    }

    private long inChest(BlockPos position, net.minecraft.world.item.Item item) {
        var chest = helper.<net.minecraft.world.level.block.entity.ChestBlockEntity>getBlockEntity(position);
        long count = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (chest.getItem(slot).is(item)) count += chest.getItem(slot).getCount();
        }
        return count;
    }

    /** Cancels a CPU's job as its screen's Cancel button does; addon CPUs have their own classes for it. */
    private static void cancel(appeng.api.networking.crafting.ICraftingCPU cpu) {
        try {
            cpu.getClass().getMethod("cancelJob").invoke(cpu);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(cpu.getClass().getName() + " cannot cancel its job", exception);
        }
    }

    private MEStorage outputChest() {
        return Objects.requireNonNull(helper.<appeng.blockentity.storage.MEChestBlockEntity>getBlockEntity(
                outputChestPos).getOriginalCellInventory(0));
    }

    private static long held(MEStorage storage, AEKey what) {
        return storage.extract(what, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    /** The provider network draws on the consumer's energy cell; either direction pools both. */
    private void enableEnergy() {
        var policies = PolicyService.get(helper.getLevel());
        var energy = new PolicyKey(bridge.outerNetwork(), bridge.mainNetwork(), PolicyCapability.ME_POWER);
        var result = policies.edit(new PolicyEdit(energy, policies.revision(energy),
                PolicyRule.enabled(Set.of(PolicyOperation.SUPPLY))));
        helper.assertTrue(result instanceof PolicyMutationResult.Accepted, "The ME power rule was refused: " + result);
    }

    private void enableRules() {
        var policies = PolicyService.get(helper.getLevel());
        var storage = new PolicyKey(bridge.mainNetwork(), bridge.outerNetwork(), PolicyCapability.STORAGE);
        var crafting = new PolicyKey(bridge.mainNetwork(), bridge.outerNetwork(), PolicyCapability.CRAFTING);
        var result = policies.editAll(List.of(
                new PolicyEdit(storage, policies.revision(storage), PolicyRule.storageDefaults()),
                new PolicyEdit(crafting, policies.revision(crafting), PolicyRule.enabled(Set.of(PolicyOperation.REQUEST)))));
        helper.assertTrue(result instanceof PolicyMutationResult.Accepted, "The rule edit was refused: " + result);
    }

    private void begin() {
        var node = bridge.consumerChest().getMainNode().getNode();
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
        planFuture = consumerGrid().getCraftingService().beginCraftingCalculation(helper.getLevel(), requester, output(),
                requested(), CalculationStrategy.REPORT_MISSING_ITEMS);
        var localNode = bridge.providerChest().getMainNode().getNode();
        localPlanFuture = bridge.outerGrid().getCraftingService().beginCraftingCalculation(helper.getLevel(),
                new ICraftingSimulationRequester() {
                    @Override
                    public IActionSource getActionSource() {
                        return IActionSource.empty();
                    }

                    @Override
                    public IGridNode getGridNode() {
                        return localNode;
                    }
                }, output(), requested(), CalculationStrategy.REPORT_MISSING_ITEMS);
    }

    private ICraftingPlan plan() {
        return get(planFuture);
    }

    private static ICraftingPlan get(Future<ICraftingPlan> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Crafting calculation interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Crafting calculation failed", exception);
        }
    }

    /** Stores every input of {@link #jobs} crafts in the consumer's chest. */
    private void storeInputs(String message) {
        for (var input : inputs().entrySet()) {
            var amount = input.getValue() * jobs;
            helper.assertValueEqual(consumerChest().insert(input.getKey(), amount, Actionable.MODULATE,
                    IActionSource.empty()), amount, message + ": " + input.getKey());
        }
    }

    /** What one craft takes. */
    private java.util.Map<AEItemKey, Long> inputs() {
        return processing ? machine.inputs() : java.util.Map.of(PLANKS, 1L);
    }

    /** Two planks make four sticks. */
    private long requested() {
        return processing ? jobs * machine.outputPerInput() : 2 * jobs;
    }

    private AEItemKey input() {
        return processing ? machine.input() : PLANKS;
    }

    private AEKey output() {
        return processing ? machine.output() : STICKS;
    }

    private ItemStack processingPattern() {
        return PatternDetailsHelper.encodeProcessingPattern(
                machine.inputs().entrySet().stream()
                        .map(input -> new appeng.api.stacks.GenericStack(input.getKey(), input.getValue())).toList(),
                List.of(new appeng.api.stacks.GenericStack(machine.output(), machine.outputPerInput())));
    }

    private ItemStack stickPattern() {
        var items = NonNullList.withSize(9, ItemStack.EMPTY);
        items.set(0, new ItemStack(Items.OAK_PLANKS));
        items.set(3, new ItemStack(Items.OAK_PLANKS));
        var input = CraftingInput.of(3, 3, items);
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .orElseThrow();
        return PatternDetailsHelper.encodeCraftingPattern(recipe, items.toArray(ItemStack[]::new),
                recipe.value().assemble(input, helper.getLevel().registryAccess()), false, false);
    }
}
