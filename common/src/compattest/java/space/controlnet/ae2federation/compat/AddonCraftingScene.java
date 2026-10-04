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
    private final BlockPos providerPos = BASE.east(2).north();
    private final BlockPos assemblerPos = providerPos.east();
    private int stage;
    private Future<ICraftingPlan> planFuture;

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
        bridge.installStorageCells();
    }

    /** The machine a processing pattern names: its block, what it makes from cobblestone, and how it runs. */
    interface Machine {
        String blockId();

        AEItemKey output();

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

    /** Runs the whole craft; call from {@code succeedWhen}. */
    void tick() {
        switch (stage) {
            case 0 -> {
                helper.assertTrue(bridge.networksSettled(), "Waiting for both networks");
                helper.setBlock(providerPos, block(providerId));
                helper.setBlock(assemblerPos, block(assemblerId));
                if (machine != null) machine.placeAround(helper, assemblerPos);
                // The CPU blocks run west from the consumer's cable, each touching the one before.
                for (int index = 0; index < cpuIds.size(); index++) {
                    helper.setBlock(BASE.west(index + 1), block(cpuIds.get(index)));
                }
                stage = 1;
                helper.fail("Placed the provider, assembler and CPU");
            }
            case 1 -> {
                bridge.placeFirstBridge();
                stage = 2;
                helper.fail("Placed the Bridge");
            }
            case 2 -> {
                if (!bridge.firstBridgeReady()) bridge.refreshFirstBridge();
                helper.assertTrue(bridge.firstBridgeReady(), "Waiting for the Bridge");
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
                var input = processing ? COBBLESTONE : PLANKS;
                helper.assertValueEqual(consumerChest().insert(input, 2, Actionable.MODULATE, IActionSource.empty()),
                        2L, "The consumer's chest must take the inputs");
                begin();
                stage = 5;
                helper.fail("Planning the request");
            }
            case 5 -> {
                helper.assertTrue(planFuture.isDone(), "Waiting for the consumer's plan");
                var plan = plan();
                helper.assertFalse(plan.simulation(), "The consumer's planks must be enough: " + plan.missingItems());
                helper.assertTrue(consumerGrid().getCraftingService()
                        .submitJob(plan, null, null, true, IActionSource.empty()).successful(),
                        "The consumer's CPU must take the job");
                stage = 6;
                helper.fail("Submitted the job");
            }
            default -> {
                if (processing) machine.collect(helper, assemblerPos, bridge.outerGrid().getStorageService().getInventory());
                helper.assertTrue(consumerGrid().getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                        "Waiting for the consumer's job to finish");
                var made = held(consumerChest(), output()) + held(providerChest(), output());
                helper.assertValueEqual(made, processing ? 2L : 4L, "The job must store exactly what was requested");
                helper.assertValueEqual(held(consumerChest(), processing ? COBBLESTONE : PLANKS), 0L,
                        "The consumer's inputs were used");
            }
        }
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

    private MEStorage consumerChest() {
        return Objects.requireNonNull(bridge.consumerChest().getOriginalCellInventory(0));
    }

    private MEStorage providerChest() {
        return Objects.requireNonNull(bridge.providerChest().getOriginalCellInventory(0));
    }

    private static long held(MEStorage storage, AEItemKey what) {
        return storage.extract(what, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
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
                processing ? 2 : 4, CalculationStrategy.REPORT_MISSING_ITEMS);
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

    private AEItemKey output() {
        return processing ? machine.output() : STICKS;
    }

    private ItemStack processingPattern() {
        return PatternDetailsHelper.encodeProcessingPattern(List.of(new appeng.api.stacks.GenericStack(COBBLESTONE, 1)),
                List.of(new appeng.api.stacks.GenericStack(machine.output(), 1)));
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
