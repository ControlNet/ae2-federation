package space.controlnet.ae2federation.test.crafting;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import space.controlnet.ae2federation.crafting.projection.CraftingProjectionService;
import space.controlnet.ae2federation.crafting.projection.CraftingReturnLedger;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.port.RouterPortBinding;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.policy.RuleMode;
import space.controlnet.ae2federation.test.router.RouterFixtures;

/**
 * Three native Grids on three faces of one Router. The consumer (north) has a CPU and crafts sticks from planks; the
 * middle (south) has only a chest; the source (east) has a CPU and crafts planks from logs. Each crafter is a pattern
 * provider with a Molecular Assembler. The chain runs consumer to middle and middle to source, each with its storage
 * rule, so the consumer reaches the source's provider only when "middle uses source" re-exports; the mutual rules run
 * consumer to source and back.
 */
public final class ProjectionChainFixture implements AutoCloseable {
    private static final BlockPos CENTER = new BlockPos(6, 5, 6);
    private static final List<Direction> FACES = List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST);
    public static final int CONSUMER = 0;
    public static final int MIDDLE = 1;
    public static final int SOURCE = 2;

    private final GameTestHelper helper;
    private final RouterFixtures routers;
    private final Set<PolicyKey> configured = new LinkedHashSet<>();
    private int stage;
    private int planning;
    private Future<ICraftingPlan> planFuture;
    private ICraftingPlan plan;

    public ProjectionChainFixture(GameTestHelper helper) {
        this.helper = helper;
        routers = new RouterFixtures(helper);
        for (var face : FACES) {
            routers.placeNativeDevice(CENTER, face);
            helper.setBlock(CENTER.relative(face).below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
            chest(face).setCell(AEItems.ITEM_CELL_1K.stack());
        }
    }

    /** Places the Router, then the CPUs, providers and assemblers of the consumer and the source. */
    public boolean ready() {
        if (stage == 0) {
            if (grids().stream().anyMatch(grid -> FederationDomainRegistryAccess.confirmedNetworkId(grid).isEmpty())) {
                return false;
            }
            routers.placeRouter(CENTER);
            stage = 1;
            return false;
        }
        if (stage == 1) {
            var router = routers.router(CENTER);
            if (FACES.stream().anyMatch(face -> !(router.binding(face) instanceof RouterPortBinding.Native))
                    || Set.copyOf(grids()).size() != 3) {
                return false;
            }
            for (var face : crafters()) {
                place(CENTER.relative(face, 2), AEBlocks.CRAFTING_STORAGE_1K.block(), face);
                place(provider(face), AEBlocks.PATTERN_PROVIDER.block(), face);
                place(provider(face).above(), AEBlocks.MOLECULAR_ASSEMBLER.block(), face);
            }
            stage = 2;
            return false;
        }
        if (stage == 2) {
            if (crafters().stream().anyMatch(face -> grid(FACES.indexOf(face)).getCraftingService().getCpus().isEmpty()
                    || providerEntity(face).getMainNode().getGrid() != grid(FACES.indexOf(face)))) {
                return false;
            }
            install(FACES.get(CONSUMER), sticksPattern());
            install(FACES.get(SOURCE), planksPattern());
            stage = 3;
            return false;
        }
        var registry = FederationDomainRegistryAccess.get(helper.getLevel());
        var common = registry.federationdomainsFor(network(CONSUMER));
        return grid(CONSUMER).getCraftingService().isCraftable(sticks())
                && grid(SOURCE).getCraftingService().isCraftable(planks())
                && FACES.stream().skip(1).allMatch(face -> registry.federationdomainsFor(network(FACES.indexOf(face)))
                        .stream().anyMatch(common::contains));
    }

    /** Consumer uses middle, middle uses source with {@code sourceMode}; each with its storage rule. */
    public void enableChain(RuleMode sourceMode) {
        crafting(CONSUMER, MIDDLE, RuleMode.ENABLED);
        crafting(MIDDLE, SOURCE, sourceMode);
        CraftingProjectionService.get(helper.getLevel()).observeFederationDomainMembers(grids());
    }

    /** Consumer uses source and source uses consumer, each with its storage rule. */
    public void enableMutual() {
        crafting(CONSUMER, SOURCE, RuleMode.ENABLED);
        crafting(SOURCE, CONSUMER, RuleMode.ENABLED);
        CraftingProjectionService.get(helper.getLevel()).observeFederationDomainMembers(grids());
    }

    /** {@code executing}'s providers projected onto {@code consumer}'s Grid. */
    public int projections(int consumer, int executing) {
        return CraftingProjectionService.get(helper.getLevel()).projectionCount(network(consumer), network(executing));
    }

    /** Whether {@code executing}'s Grid routes crafting returns, as it does only while its providers are projected. */
    public boolean routes(int executing) {
        return CraftingProjectionService.get(helper.getLevel()).routes(grid(executing));
    }

    public long owed(int executing, int consumer, AEItemKey key) {
        return CraftingReturnLedger.get(helper.getLevel()).owed(network(executing), network(consumer), key);
    }

    public boolean canCraft(int index, AEItemKey key) {
        return grid(index).getCraftingService().isCraftable(key);
    }

    public void putIn(int index, AEItemKey key, long amount) {
        helper.assertValueEqual(storage(index).insert(key, amount, Actionable.MODULATE, IActionSource.empty()),
                amount, "The chest must take the materials");
    }

    /** Starts a calculation on {@code index}'s own crafting service; only one plan is tracked at a time. */
    public void begin(int index, AEItemKey what, long amount) {
        plan = null;
        planning = index;
        var node = chest(FACES.get(index)).getMainNode().getNode();
        ICraftingSimulationRequester requester = new ICraftingSimulationRequester() {
            @Override
            public IActionSource getActionSource() {
                return IActionSource.empty();
            }

            @Override
            public appeng.api.networking.IGridNode getGridNode() {
                return node;
            }
        };
        planFuture = grid(index).getCraftingService().beginCraftingCalculation(helper.getLevel(), requester, what,
                amount, CalculationStrategy.REPORT_MISSING_ITEMS);
    }

    public boolean planReady() {
        if (plan != null) return true;
        if (planFuture == null || !planFuture.isDone()) return false;
        try {
            plan = planFuture.get();
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Calculation interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Calculation failed", exception);
        }
    }

    public ICraftingPlan plan() {
        return plan;
    }

    /** Submits the tracked plan on the Grid that calculated it. */
    public boolean submit() {
        return grid(planning).getCraftingService().submitJob(plan, null, null, true, IActionSource.empty())
                .successful();
    }

    /** What {@code index}'s own chest cell holds, so nothing mounted from another network is counted. */
    public long amount(int index, AEItemKey key) {
        return storage(index).extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    /** Every chest of the three networks, so an output stored through a storage mount is counted once. */
    public long total(AEItemKey key) {
        long total = 0;
        for (int index = 0; index < FACES.size(); index++) total += amount(index, key);
        return total;
    }

    public long busyCpus(int index) {
        return grid(index).getCraftingService().getCpus().stream().filter(cpu -> cpu.isBusy()).count();
    }

    public long cpuCount(int index) {
        return grid(index).getCraftingService().getCpus().size();
    }

    public List<IGrid> grids() {
        return FACES.stream().map(face -> routers.nativeDeviceNode(CENTER, face).getGrid()).toList();
    }

    public static AEItemKey sticks() {
        return AEItemKey.of(Items.STICK);
    }

    public static AEItemKey planks() {
        return AEItemKey.of(Items.OAK_PLANKS);
    }

    public static AEItemKey log() {
        return AEItemKey.of(Items.OAK_LOG);
    }

    /** Sets {@code consumer} uses {@code provider}'s crafting to {@code mode}, with the pair's storage rule on. */
    public void crafting(int consumer, int provider, RuleMode mode) {
        rule(key(consumer, provider, PolicyCapability.STORAGE), PolicyRule.storageDefaults());
        rule(key(consumer, provider, PolicyCapability.CRAFTING),
                PolicyRule.enabled(Set.of(PolicyOperation.REQUEST)).withMode(mode));
    }

    private void rule(PolicyKey key, PolicyRule rule) {
        var policies = PolicyService.get(helper.getLevel());
        var result = policies.edit(new PolicyEdit(key, policies.revision(key), rule));
        helper.assertTrue(result instanceof PolicyMutationResult.Accepted, "Each chain rule must be accepted");
        configured.add(key);
    }

    /** The Grid's own chest cell, not the Grid's aggregate, so nothing mounted from another network is counted. */
    private MEStorage storage(int index) {
        return Objects.requireNonNull(chest(FACES.get(index)).getOriginalCellInventory(0));
    }

    private IGrid grid(int index) {
        return grids().get(index);
    }

    private NetworkId network(int index) {
        return FederationDomainRegistryAccess.confirmedNetworkId(grid(index)).orElseThrow();
    }

    private PolicyKey key(int consumer, int provider, PolicyCapability capability) {
        return new PolicyKey(network(consumer), network(provider), capability);
    }

    private void place(BlockPos position, net.minecraft.world.level.block.Block block, Direction face) {
        helper.setBlock(position, block);
        var entity = helper.getLevel().getBlockEntity(helper.absolutePos(position));
        var networkId = network(FACES.indexOf(face));
        if (entity instanceof appeng.blockentity.grid.AENetworkedBlockEntity networked) {
            networked.getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", networkId));
        }
    }

    private MEChestBlockEntity chest(Direction face) {
        return helper.getBlockEntity(CENTER.relative(face));
    }

    private static BlockPos provider(Direction face) {
        return CENTER.relative(face).above();
    }

    private static List<Direction> crafters() {
        return List.of(FACES.get(CONSUMER), FACES.get(SOURCE));
    }

    private PatternProviderBlockEntity providerEntity(Direction face) {
        return helper.getBlockEntity(provider(face));
    }

    private void install(Direction face, ItemStack pattern) {
        var logic = providerEntity(face).getLogic();
        logic.getPatternInv().addItems(pattern);
        logic.updatePatterns();
    }

    private ItemStack sticksPattern() {
        var items = NonNullList.withSize(9, ItemStack.EMPTY);
        items.set(0, new ItemStack(Items.OAK_PLANKS));
        items.set(3, new ItemStack(Items.OAK_PLANKS));
        return encode(items);
    }

    private ItemStack planksPattern() {
        var items = NonNullList.withSize(9, ItemStack.EMPTY);
        items.set(0, new ItemStack(Items.OAK_LOG));
        return encode(items);
    }

    private ItemStack encode(NonNullList<ItemStack> items) {
        var input = CraftingInput.of(3, 3, items);
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .orElseThrow();
        return PatternDetailsHelper.encodeCraftingPattern(recipe, items.toArray(ItemStack[]::new),
                recipe.value().assemble(input, helper.getLevel().registryAccess()), false, false);
    }

    /**
     * Switches the chain's rules off before the Router is released: the Grids outlive the test, and live rules would
     * keep their projections reconciled on every domain refresh of later tests in the same level.
     */
    @Override
    public void close() {
        var policies = PolicyService.get(helper.getLevel());
        for (var key : configured) {
            policies.configured(key).ifPresent(record -> policies.edit(new PolicyEdit(key, policies.revision(key),
                    record.rule().withEnabled(false))));
        }
        routers.close();
    }
}
