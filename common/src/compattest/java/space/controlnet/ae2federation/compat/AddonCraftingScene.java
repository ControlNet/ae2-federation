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
import space.controlnet.ae2federation.crafting.projection.CraftingProjectionService;
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
    /** South of the consumer's ME Chest, touching only the consumer network. */
    private static final BlockPos CONSUMER_OUTPUT_CHEST = BASE.south(2);
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
    private boolean providerWithoutStorage;
    private boolean cancelAfterPush;
    private boolean outputOnConsumer;
    private boolean lateByproduct;
    private boolean craftingOnly;
    private boolean disconnectAfterPush;
    private boolean relinkAfterDisconnect;
    private boolean mergeAfterPush;
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
    private long sweeps;
    private String whileMerged;
    /** Both networks' ids, read once confirmed: in a joined Grid neither is confirmed, yet they are the same. */
    private space.controlnet.ae2federation.identity.NetworkId consumerNetwork;
    private space.controlnet.ae2federation.identity.NetworkId providerNetwork;
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

    /**
     * A chest standing in for a furnace: the test turns whatever cobblestone the provider pushed into stone, or into
     * {@code outputPerInput} of another output kept in an {@code outputCell} on the provider network.
     */
    private record ChestMachine(String blockId, AEKey output, long outputPerInput, String outputCell)
            implements Machine {
        ChestMachine(String blockId) {
            this(blockId, STONE, 1, null);
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
            network.insert(output, pushed * outputPerInput, Actionable.MODULATE, IActionSource.empty());
        }
    }

    /** The hand-run chest machine making a bucket of lava from each cobblestone, kept in a 1k fluid cell. */
    static Machine lavaMachine() {
        return new ChestMachine("minecraft:chest", appeng.api.stacks.AEFluidKey.of(
                net.minecraft.world.level.material.Fluids.LAVA), appeng.api.stacks.AEFluidKey.AMOUNT_BUCKET,
                "ae2:fluid_storage_cell_1k");
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

    /**
     * The provider network's ME Chest holds no cell, so that network has no storage at all: whatever its provider makes
     * must reach the consumer's waiting CPU.
     */
    AddonCraftingScene providerWithoutStorage() {
        providerWithoutStorage = true;
        return this;
    }

    /** How many inputs the consumer stores and uses up, two unless set: two planks make four sticks. */
    AddonCraftingScene requesting(long jobs) {
        this.jobs = jobs;
        return this;
    }

    /** Never enables the Storage rule, including during fixture preparation. */
    AddonCraftingScene craftingOnly() {
        craftingOnly = true;
        return this;
    }

    /** A test-operated machine delays the declared bottle byproduct until all primary output reaches the real CPU. */
    AddonCraftingScene lateByproduct() {
        lateByproduct = true;
        return this;
    }

    /**
     * Cancels the consumer's job once the provider has pushed every input into the machine, then runs the machine: the
     * late output must reach the consumer and only actual delivery settles the debt. A real machine must take longer
     * than a tick, so the job is still waiting when every input has gone out.
     */
    AddonCraftingScene cancellingAfterPush() {
        if (machine == null) throw new IllegalStateException("Only a processing machine can wait");
        cancelAfterPush = true;
        return this;
    }

    /**
     * The consumer gets an ME Chest of its own with the machine's output cell, so what it makes is stored on the
     * consumer even when an item cell cannot hold it. The end then checks the return ledger as well: nothing owed, held
     * or in transit, the job retired, and none of the output left on the provider network.
     */
    AddonCraftingScene outputStoredOnConsumer() {
        if (machine == null || machine.outputCell() == null) {
            throw new IllegalStateException("Only a machine with an output cell needs one on the consumer");
        }
        outputOnConsumer = true;
        return this;
    }

    /**
     * Removes the Bridge once the provider has pushed every input into the chest machine, then runs the machine: the
     * projected pattern leaves the consumer, the output stays on the provider network, the consumer's job keeps waiting
     * for it and its debt stays owed, as a job waits for an output that went elsewhere.
     */
    AddonCraftingScene disconnectingAfterPush() {
        if (machine == null) throw new IllegalStateException("Only a processing machine can wait");
        disconnectAfterPush = true;
        // Apart from the consumer, the provider network still runs, as one with its own power does.
        bridge.powerOuter();
        return this;
    }

    /**
     * After {@link #disconnectingAfterPush()}, places the Bridge again instead of cancelling the job: the output held
     * on the provider network while the networks were apart must reach the waiting job, and the return must settle.
     * The held output's debt is also read back from the ledger's saved form while the networks are apart.
     */
    AddonCraftingScene relinkingAfterDisconnect() {
        if (!disconnectAfterPush || !outputOnConsumer) {
            throw new IllegalStateException("Relinking follows disconnectingAfterPush() with outputStoredOnConsumer()");
        }
        relinkAfterDisconnect = true;
        return this;
    }

    /**
     * Once the provider has pushed every input, removes only the Bridge part: the consumer's cable under it is fluix,
     * so it joins the provider's cable and both networks become one AE2 Grid. The machine's output then reaches the
     * consumer's CPU natively; the part is placed again, the Grids split, and the debt must have been settled by that
     * delivery: output the provider network stores later stays there.
     */
    AddonCraftingScene mergingAfterPush() {
        if (!outputOnConsumer) {
            throw new IllegalStateException("Merging checks the return with outputStoredOnConsumer()");
        }
        mergeAfterPush = true;
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

    boolean submitted() { return stage >= 6; }

    PolicyBridgeFixtures bridge() { return bridge; }

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
                if (outputOnConsumer) {
                    helper.setBlock(CONSUMER_OUTPUT_CHEST, appeng.core.definitions.AEBlocks.ME_CHEST.block());
                    var chest = helper.<appeng.blockentity.storage.MEChestBlockEntity>getBlockEntity(
                            CONSUMER_OUTPUT_CHEST);
                    chest.setCell(new ItemStack(item(machine.outputCell())));
                    // In the joined Grid the finished job stores its output on the consumer's cell, not the provider's.
                    if (mergeAfterPush) chest.setPriority(1);
                }
                if (providerWithoutStorage) bridge.providerChest().setCell(ItemStack.EMPTY);
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
                consumerNetwork = bridge.mainNetwork();
                providerNetwork = bridge.outerNetwork();
                enableRules();
                if (mergeAfterPush) fluixCableUnderBridge();
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
                if (!cancelAfterPush && !disconnectAfterPush && !mergeAfterPush) {
                    stage = 9;
                    helper.fail("Running the job");
                }
                if (machine instanceof ChestMachine) {
                    helper.assertValueEqual(inChest(assemblerPos, Items.COBBLESTONE), jobs,
                            "Waiting for the provider to push every input");
                } else {
                    // A real machine leaves no count to read; every accepted push is owed in the ledger.
                    helper.assertValueEqual(owed(), requested(), "Waiting for the provider to push every input");
                }
                var busy = consumerGrid().getCraftingService().getCpus().stream().filter(cpu -> cpu.isBusy()).toList();
                helper.assertValueEqual(busy.size(), 1, "The consumer's CPU must be running the job");
                if (disconnectAfterPush) {
                    bridge.removeFirstBridge();
                    stage = 10;
                    helper.fail("Removed the Bridge");
                }
                if (mergeAfterPush) {
                    bridge.removeFirstBridge();
                    stage = 19;
                    helper.fail("Removed only the Bridge part");
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
                helper.assertValueEqual(held(providerOutput(), output()), requested(),
                        "The output stays on the provider network");
                helper.assertValueEqual(held(consumerOutput(), output()), 0L, "Nothing crosses the removed Bridge");
                var busy = consumerGrid().getCraftingService().getCpus().stream().filter(cpu -> cpu.isBusy()).toList();
                helper.assertValueEqual(busy.size(), 1, "The consumer's job keeps waiting");
                helper.assertValueEqual(CraftingReturnLedger.get(helper.getLevel()).owed(bridge.outerNetwork(),
                        bridge.mainNetwork(), output()), requested(), "The debt stays while the consumer still waits");
                if (relinkAfterDisconnect) {
                    var ledger = CraftingReturnLedger.get(helper.getLevel());
                    helper.assertValueEqual(ledger.held(bridge.outerNetwork(), bridge.mainNetwork(), output()),
                            requested(), "The output is held for the consumer");
                    var registries = helper.getLevel().registryAccess();
                    var restored = CraftingReturnLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag(),
                            registries), registries);
                    helper.assertValueEqual(restored.owed(bridge.outerNetwork(), bridge.mainNetwork(), output()),
                            requested(), "The saved ledger keeps the debt of " + output());
                    helper.assertValueEqual(restored.held(bridge.outerNetwork(), bridge.mainNetwork(), output()),
                            requested(), "The saved ledger keeps the held " + output());
                    bridge.placeFirstBridge();
                    stage = 18;
                    helper.fail("Placed the Bridge again");
                }
                cancel(busy.getFirst());
            }
            case 18 -> {
                if (!bridge.firstBridgeReady()) bridge.refreshFirstBridge();
                helper.assertTrue(bridge.firstBridgeReady(), "Waiting for the Bridge to link the networks again");
                stage = 9;
                helper.fail("Linked the networks again");
            }
            case 19 -> {
                helper.assertTrue(consumerGrid() == bridge.outerGrid(), "Waiting for the networks to become one Grid");
                helper.assertTrue(busyCpu() != null, "Waiting for the joined Grid to list the busy CPU");
                helper.assertFalse(CraftingProjectionService.get(helper.getLevel()).routes(bridge.outerGrid()),
                        "Waiting for Federation to stop routing the joined Grid");
                machine.collect(helper, assemblerPos, bridge.outerGrid().getStorageService().getInventory());
                stage = 20;
                helper.fail("Ran the machine in the joined Grid");
            }
            case 20 -> {
                helper.assertTrue(consumerGrid().getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                        "Waiting for the CPU to take its output natively");
                whileMerged = returns();
                bridge.placeFirstBridge();
                stage = 21;
                helper.fail("Placed the Bridge part again");
            }
            case 21 -> {
                helper.assertFalse(consumerGrid() == bridge.outerGrid(), "Waiting for the Grids to split");
                if (!bridge.firstBridgeReady()) bridge.refreshFirstBridge();
                helper.assertTrue(bridge.firstBridgeReady(), "Waiting for the Bridge to link the networks again");
                helper.assertTrue(consumerGrid().getCraftingService().isCraftable(output()),
                        "Waiting for the provider's pattern to come back");
                sweeps = CraftingProjectionService.get(helper.getLevel()).ledgerSweeps();
                stage = 22;
                helper.fail("Split the Grids");
            }
            case 22 -> {
                helper.assertTrue(CraftingProjectionService.get(helper.getLevel()).ledgerSweeps() - sweeps >= 3,
                        "Letting three ledger sweeps pass");
                var context = "; while merged: " + whileMerged + "; after the split: " + returns();
                helper.assertValueEqual(held(consumerOutput(), output()), requested(),
                        "The job's output reaches the consumer" + context);
                helper.assertValueEqual(held(bridge.outerGrid().getStorageService().getInventory(), output()), 0L,
                        "The provider network keeps none of it" + context);
                // Unrelated output that a player's import bus stores on the provider network after the job.
                helper.assertValueEqual(bridge.outerGrid().getStorageService().getInventory().insert(output(),
                        machine.outputPerInput(), Actionable.MODULATE, IActionSource.empty()), machine.outputPerInput(),
                        "The provider network stores a player's output");
                sweeps = CraftingProjectionService.get(helper.getLevel()).ledgerSweeps();
                stage = 23;
                helper.fail("Stored a player's output on the provider network");
            }
            case 23 -> {
                helper.assertTrue(CraftingProjectionService.get(helper.getLevel()).ledgerSweeps() - sweeps >= 3,
                        "Letting three more ledger sweeps pass");
                var context = "; while merged: " + whileMerged + "; now: " + returns();
                var ledger = CraftingReturnLedger.get(helper.getLevel());
                helper.assertValueEqual(owed(), 0L, "The native delivery settled the debt" + context);
                helper.assertValueEqual(ledger.held(providerNetwork, consumerNetwork, output()), 0L,
                        "Nothing is held for the consumer" + context);
                helper.assertValueEqual(ledger.transit(providerNetwork, output()), 0L,
                        "Nothing is in transit" + context);
                helper.assertTrue(ledger.jobs(consumerNetwork).isEmpty(), "The finished job retires" + context);
                helper.assertValueEqual(held(providerOutput(), output()), machine.outputPerInput(),
                        "The player's output stays on the provider network" + context);
                helper.assertValueEqual(held(consumerOutput(), output()), requested(),
                        "The consumer keeps only its job's output" + context);
                helper.assertTrue(consumerGrid().getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                        "The consumer's CPU is idle" + context);
            }
            case 7 -> {
                helper.assertTrue(consumerGrid().getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                        "The consumer's job must be cancelled");
                helper.assertValueEqual(owed(), requested(), "Cancellation preserves the dispatched output responsibility");
                machine.collect(helper, assemblerPos, bridge.outerGrid().getStorageService().getInventory());
                stage = 8;
                helper.fail("Ran the machine after the cancel");
            }
            case 8 -> {
                helper.assertValueEqual(CraftingReturnLedger.get(helper.getLevel()).owed(bridge.outerNetwork(),
                        bridge.mainNetwork(), output()), 0L, "Actual late delivery settles cancellation responsibility");
                helper.assertValueEqual(held(providerChest(), output()), 0L,
                        "The provider retains none of the cancelled consumer's output");
                helper.assertValueEqual(held(consumerOutput(), output()), requested(),
                        "Late output reaches the cancelled consumer");
                helper.assertValueEqual(held(consumerChest(), input()), 0L, "The pushed inputs are not given back twice");
                if (outputOnConsumer) assertReturnsSettled();
            }
            case 17 -> {
                helper.assertTrue(consumerGrid().getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                        "Both declared outputs complete the real independent CPU");
                helper.assertValueEqual(held(consumerChest(), output()), requested(), "All primary output reaches consumer storage");
                var bottle = AEItemKey.of(Items.GLASS_BOTTLE);
                helper.assertValueEqual(held(consumerChest(), bottle), jobs,
                        "Delayed declared byproducts must reach the independent CPU or its consumer storage");
                helper.assertValueEqual(CraftingReturnLedger.get(helper.getLevel()).owed(bridge.outerNetwork(),
                        bridge.mainNetwork(), bottle), 0L, "Actual byproduct delivery settles the remaining debt");
                helper.assertValueEqual(held(providerChest(), bottle), 0L, "No completed consumer byproducts stay stranded");
            }
            default -> {
                if (processing) machine.collect(helper, assemblerPos, bridge.outerGrid().getStorageService().getInventory());
                if (lateByproduct) {
                    helper.assertValueEqual(inChest(assemblerPos, Items.COBBLESTONE), 0L, "Test machine consumed every input");
                    helper.assertValueEqual(CraftingReturnLedger.get(helper.getLevel()).owed(bridge.outerNetwork(),
                            bridge.mainNetwork(), output()), 0L, "Primary output has been accepted by the independent CPU");
                    var bottle = AEItemKey.of(Items.GLASS_BOTTLE);
                    helper.assertValueEqual(CraftingReturnLedger.get(helper.getLevel()).owed(bridge.outerNetwork(),
                            bridge.mainNetwork(), bottle), jobs, "Primary output delivery retains byproduct debt");
                    helper.assertValueEqual(bridge.outerGrid().getStorageService().getInventory().insert(bottle, jobs,
                            Actionable.MODULATE, IActionSource.empty()), jobs, "Test machine emits the delayed declared byproduct");
                    stage = 17;
                    helper.fail("Waiting for the independent CPU's delayed byproduct return");
                }
                helper.assertTrue(consumerGrid().getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                        "Waiting for the consumer's job to finish");
                if (consumerStorage != null) {
                    helper.assertValueEqual(held(consumerChest(), output()), requested() * tookOrders.size(),
                            "The consumer's own storage must hold everything made");
                }
                var made = held(consumerChest(), output()) + held(providerChest(), output())
                        + (processing && machine.outputCell() != null ? held(outputChest(), output()) : 0)
                        + (outputOnConsumer ? held(consumerOutput(), output()) : 0);
                helper.assertValueEqual(made, requested() * tookOrders.size(),
                        "The job must store exactly what was requested");
                if (outputOnConsumer) {
                    helper.assertValueEqual(held(consumerOutput(), output()), requested() * tookOrders.size(),
                            "Everything made is stored on the consumer");
                    assertReturnsSettled();
                }
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

    IGrid consumerGrid() {
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

    /** Where the consumer stores the output: its own output chest, else its ME Chest. */
    private MEStorage consumerOutput() {
        if (!outputOnConsumer) return consumerChest();
        return Objects.requireNonNull(helper.<appeng.blockentity.storage.MEChestBlockEntity>getBlockEntity(
                CONSUMER_OUTPUT_CHEST).getOriginalCellInventory(0));
    }

    /** Where the provider network keeps the output: the machine's output cell, else its ME Chest. */
    private MEStorage providerOutput() {
        return machine != null && machine.outputCell() != null ? outputChest() : providerChest();
    }

    /** What the ledger says of the output, for failure messages. */
    private String returns() {
        var ledger = CraftingReturnLedger.get(helper.getLevel());
        return "owed " + ledger.owed(providerNetwork, consumerNetwork, output()) + ", held "
                + ledger.held(providerNetwork, consumerNetwork, output()) + ", transit "
                + ledger.transit(providerNetwork, output()) + ", jobs " + ledger.jobs(consumerNetwork)
                + ", on the provider network "
                + held(bridge.outerGrid().getStorageService().getInventory(), output()) + ", on the consumer "
                + held(consumerOutput(), output());
    }

    /**
     * Dyes the consumer's cable under the Bridge fluix, as a player's cable often is. The Bridge part still covers that
     * side, so the networks stay apart; removing only the part then joins them into one AE2 Grid.
     */
    private void fluixCableUnderBridge() {
        var host = (appeng.api.parts.IPartHost) helper.getLevel().getBlockEntity(helper.absolutePos(BASE));
        var cable = (appeng.parts.networking.CablePart) host.getPart(null);
        helper.assertTrue(cable.changeColor(appeng.api.util.AEColor.TRANSPARENT, null),
                "The cable under the Bridge takes fluix");
    }

    /** What the provider network still owes the consumer of the output. */
    private long owed() {
        return CraftingReturnLedger.get(helper.getLevel()).owed(bridge.outerNetwork(), bridge.mainNetwork(), output());
    }

    /**
     * The return is complete: nothing owed, held or in transit, the consumer's watched jobs retired, its CPUs idle and
     * empty, and none of the output anywhere on the provider network.
     */
    private void assertReturnsSettled() {
        var ledger = CraftingReturnLedger.get(helper.getLevel());
        var factory = bridge.outerNetwork();
        var consumer = bridge.mainNetwork();
        helper.assertValueEqual(owed(), 0L, "Delivery settles the output's debt");
        helper.assertValueEqual(ledger.held(factory, consumer, output()), 0L, "Nothing is held for the consumer");
        helper.assertValueEqual(ledger.transit(factory, output()), 0L, "Nothing is in transit");
        helper.assertValueEqual(held(bridge.outerGrid().getStorageService().getInventory(), output()), 0L,
                "The provider network keeps none of the output");
        helper.assertTrue(ledger.jobs(consumer).isEmpty(), "Waiting for the consumer's job to retire: " + ledger.jobs(consumer));
        for (var cpu : consumerGrid().getCraftingService().getCpus()) {
            helper.assertTrue(!cpu.isBusy() && (!(cpu instanceof appeng.me.cluster.implementations.CraftingCPUCluster
                    cluster) || cluster.craftingLogic.getInventory().list.isEmpty()), "The consumer's CPU is idle and empty");
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
        var edits = new java.util.ArrayList<PolicyEdit>();
        if (!craftingOnly) edits.add(new PolicyEdit(storage, policies.revision(storage), PolicyRule.storageDefaults()));
        edits.add(new PolicyEdit(crafting, policies.revision(crafting), PolicyRule.enabled(Set.of(PolicyOperation.REQUEST))));
        var result = policies.editAll(edits);
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
                lateByproduct ? List.of(new appeng.api.stacks.GenericStack(machine.output(), machine.outputPerInput()),
                        new appeng.api.stacks.GenericStack(AEItemKey.of(Items.GLASS_BOTTLE), 1))
                        : List.of(new appeng.api.stacks.GenericStack(machine.output(), machine.outputPerInput())));
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
