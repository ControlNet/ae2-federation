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
import appeng.api.util.AEColor;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import java.util.ArrayList;
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
import net.minecraft.world.level.block.Block;
import space.controlnet.ae2federation.bridge.MultipartBridgePart;
import space.controlnet.ae2federation.crafting.projection.CraftingProjectionService;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
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
import space.controlnet.ae2federation.test.bridge.BridgeFixtures;

/**
 * The guide's "Across Domains" build: three networks A, B and C in a row, A and B joined by one Bridge and B and C by
 * another, so A and C share no Federation domain. Only A has an energy cell; B and C run on it through ME power, A to B
 * and B to C. A has an ME chest and a crafting CPU; B has only an ME chest; C has an ME chest and an AE2 pattern
 * provider with a Molecular Assembler that crafts planks from logs.
 */
public final class BridgeChainFixture implements AutoCloseable {
    public static final int A = 0;
    public static final int B = 1;
    public static final int C = 2;

    // Each network's cables run along x at z = 3, in a colour of its own so neighbouring networks never join.
    private static final BlockPos A_CABLE = new BlockPos(6, 3, 3);
    private static final BlockPos B_EAST_CABLE = new BlockPos(5, 3, 3);
    private static final BlockPos B_WEST_CABLE = new BlockPos(4, 3, 3);
    private static final BlockPos C_CABLE = new BlockPos(3, 3, 3);
    private static final List<BlockPos> CHESTS = List.of(new BlockPos(7, 3, 4), new BlockPos(4, 3, 4),
            new BlockPos(2, 3, 4));
    private static final BlockPos A_CPU = new BlockPos(6, 3, 4);
    private static final BlockPos C_PROVIDER = new BlockPos(2, 3, 2);

    private final GameTestHelper helper;
    private final BridgeFixtures fixtures;
    private final List<MultipartBridgePart> bridges = new ArrayList<>();
    private final Set<PolicyKey> configured = new LinkedHashSet<>();
    private final List<NetworkId> networks = new ArrayList<>();
    private int stage;
    private Future<ICraftingPlan> planFuture;
    private ICraftingPlan plan;

    public BridgeChainFixture(GameTestHelper helper) {
        this.helper = helper;
        fixtures = new BridgeFixtures(helper);
        var ports = fixtures.nativePorts();
        ports.placeCable(A_CABLE, AEColor.PURPLE);
        ports.placeCable(A_CABLE.east(), AEColor.PURPLE);
        ports.placeCable(B_EAST_CABLE, AEColor.LIGHT_BLUE);
        ports.placeCable(B_WEST_CABLE, AEColor.LIGHT_BLUE);
        ports.placeCable(C_CABLE, AEColor.GREEN);
        ports.placeCable(C_CABLE.west(), AEColor.GREEN);
        for (var chest : CHESTS) {
            ports.placeChest(chest);
            this.<MEChestBlockEntity>entity(chest).setCell(AEItems.ITEM_CELL_1K.stack());
        }
        helper.setBlock(CHESTS.get(A).below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
    }

    /**
     * Advances the build one step at a time: the crafting blocks, the A-B Bridge, the B-C Bridge, ME power, then the
     * planks pattern on C. True once C can craft planks and the two Bridges form two domains that meet only at B.
     */
    public boolean ready() {
        switch (stage) {
            case 0 -> {
                var grids = grids();
                if (grids.stream().anyMatch(grid -> grid == null
                        || FederationDomainRegistryAccess.confirmedNetworkId(grid).isEmpty())
                        || Set.copyOf(grids).size() != 3) {
                    return false;
                }
                grids.forEach(grid -> networks.add(FederationDomainRegistryAccess.confirmedNetworkId(grid).orElseThrow()));
                place(A_CPU, AEBlocks.CRAFTING_STORAGE_1K.block(), A);
                place(C_PROVIDER, AEBlocks.PATTERN_PROVIDER.block(), C);
                place(C_PROVIDER.above(), AEBlocks.MOLECULAR_ASSEMBLER.block(), C);
                // The A-B Bridge sits on A's cable, facing B's; the B-C Bridge on B's cable, facing C's.
                bridges.add(fixtures.placeBridge(A_CABLE, Direction.WEST));
                stage = 1;
            }
            case 1 -> {
                if (!bridgeReady(bridges.getFirst())) return false;
                bridges.add(fixtures.placeBridge(B_WEST_CABLE, Direction.WEST));
                stage = 2;
            }
            case 2 -> {
                if (!bridgeReady(bridges.getLast()) || !twoDomainsMeetAtB()) return false;
                rule(key(B, A, PolicyCapability.ME_POWER), PolicyRule.enabled(Set.of(PolicyOperation.SUPPLY)));
                rule(key(C, B, PolicyCapability.ME_POWER), PolicyRule.enabled(Set.of(PolicyOperation.SUPPLY)));
                stage = 3;
            }
            case 3 -> {
                if (grids().stream().anyMatch(grid -> !grid.getEnergyService().isNetworkPowered())
                        || grid(A).getCraftingService().getCpus().isEmpty()
                        || provider().getMainNode().getGrid() != grid(C)) {
                    return false;
                }
                provider().getLogic().getPatternInv().addItems(planksPattern());
                provider().getLogic().updatePatterns();
                stage = 4;
            }
            default -> {
                return grid(C).getCraftingService().isCraftable(planks()) && twoDomainsMeetAtB();
            }
        }
        return false;
    }

    /** {@code consumer} uses {@code provider}'s storage in {@code mode}. */
    public void storage(int consumer, int provider, RuleMode mode) {
        rule(key(consumer, provider, PolicyCapability.STORAGE), PolicyRule.storageDefaults().withMode(mode));
    }

    /** {@code consumer} uses {@code provider}'s crafting in {@code mode}, with the pair's storage rule plainly on. */
    public void crafting(int consumer, int provider, RuleMode mode) {
        storage(consumer, provider, RuleMode.ENABLED);
        rule(key(consumer, provider, PolicyCapability.CRAFTING),
                PolicyRule.enabled(Set.of(PolicyOperation.REQUEST)).withMode(mode));
    }

    /** What {@code index}'s terminal would list of {@code key}: its own storage and everything mounted on it. */
    public long visible(int index, AEItemKey key) {
        return grid(index).getStorageService().getInventory().getAvailableStacks().get(key);
    }

    /** How much of {@code key} {@code index}'s storage would take, its own and every mounted network's. */
    public long room(int index, AEItemKey key) {
        return grid(index).getStorageService().getInventory().insert(key, Long.MAX_VALUE, Actionable.SIMULATE,
                IActionSource.empty());
    }

    /** Takes {@code amount} of {@code key} out through {@code index}'s storage, as its terminal would. */
    public long extract(int index, AEItemKey key, long amount) {
        return grid(index).getStorageService().getInventory().extract(key, amount, Actionable.MODULATE,
                IActionSource.empty());
    }

    /** Puts {@code amount} of {@code key} straight into {@code index}'s own chest cell. */
    public void putIn(int index, AEItemKey key, long amount) {
        helper.assertValueEqual(own(index).insert(key, amount, Actionable.MODULATE, IActionSource.empty()), amount,
                "The chest must take the items");
    }

    /** What {@code index}'s own chest cell holds, so nothing mounted from another network is counted. */
    public long amount(int index, AEItemKey key) {
        return own(index).extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    /** How much of {@code key} {@code index}'s own chest cell alone would take. */
    public long ownRoom(int index, AEItemKey key) {
        return own(index).insert(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    /** Every chest of the three networks, so an output stored through a storage mount is counted once. */
    public long total(AEItemKey key) {
        long total = 0;
        for (int index = 0; index < CHESTS.size(); index++) total += amount(index, key);
        return total;
    }

    public boolean canCraft(int index, AEItemKey key) {
        return grid(index).getCraftingService().isCraftable(key);
    }

    /** {@code executing}'s providers projected onto {@code consumer}'s Grid. */
    public int projections(int consumer, int executing) {
        return CraftingProjectionService.get(helper.getLevel()).projectionCount(network(consumer), network(executing));
    }

    /** Whether {@code executing}'s Grid routes crafting returns, as it does only while its providers are projected. */
    public boolean routes(int executing) {
        return CraftingProjectionService.get(helper.getLevel()).routes(grid(executing));
    }

    public long cpuCount(int index) {
        return grid(index).getCraftingService().getCpus().size();
    }

    public long busyCpus(int index) {
        return grid(index).getCraftingService().getCpus().stream().filter(cpu -> cpu.isBusy()).count();
    }

    /** Starts a calculation on A's own crafting service, as A's terminal does. */
    public void beginOnA(AEItemKey what, long amount) {
        plan = null;
        var node = this.<MEChestBlockEntity>entity(CHESTS.get(A)).getMainNode().getNode();
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
        planFuture = grid(A).getCraftingService().beginCraftingCalculation(helper.getLevel(), requester, what, amount,
                CalculationStrategy.REPORT_MISSING_ITEMS);
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

    /** Submits the tracked plan on A, whose CPU then runs it. */
    public boolean submitOnA() {
        return grid(A).getCraftingService().submitJob(plan, null, null, true, IActionSource.empty()).successful();
    }

    public static AEItemKey planks() {
        return AEItemKey.of(Items.OAK_PLANKS);
    }

    public static AEItemKey log() {
        return AEItemKey.of(Items.OAK_LOG);
    }

    public String readiness() {
        return "stage=" + stage + ", bridges=" + bridges.stream().map(bridge -> bridge.operationalReason().name()).toList();
    }

    private boolean bridgeReady(MultipartBridgePart bridge) {
        bridge.onUpdateShape(bridge.getSide());
        return bridge.membershipCandidate().isPresent();
    }

    /** A and C each in one domain, B in both, and no domain holding A and C together. */
    private boolean twoDomainsMeetAtB() {
        var registry = FederationDomainRegistryAccess.get(helper.getLevel());
        var aDomains = registry.federationdomainsFor(network(A));
        var bDomains = registry.federationdomainsFor(network(B));
        var cDomains = registry.federationdomainsFor(network(C));
        return aDomains.size() == 1 && bDomains.size() == 2 && cDomains.size() == 1
                && bDomains.containsAll(aDomains) && bDomains.containsAll(cDomains)
                && aDomains.stream().noneMatch(cDomains::contains);
    }

    private List<IGrid> grids() {
        return CHESTS.stream().map(chest -> this.<MEChestBlockEntity>entity(chest).getMainNode().getGrid()).toList();
    }

    private IGrid grid(int index) {
        return grids().get(index);
    }

    /** The network ids read once confirmed and kept, as an unpowered network keeps its id. */
    private NetworkId network(int index) {
        return networks.get(index);
    }

    private PolicyKey key(int consumer, int provider, PolicyCapability capability) {
        return new PolicyKey(network(consumer), network(provider), capability);
    }

    private void rule(PolicyKey key, PolicyRule rule) {
        var policies = PolicyService.get(helper.getLevel());
        var result = policies.edit(new PolicyEdit(key, policies.revision(key), rule));
        helper.assertTrue(result instanceof PolicyMutationResult.Accepted, "Each rule must be accepted: " + key);
        configured.add(key);
    }

    /** Places an AE2 block already carrying its network's id, so it joins that network without an identity merge. */
    private void place(BlockPos position, Block block, int network) {
        helper.setBlock(position, block);
        if (helper.getLevel().getBlockEntity(helper.absolutePos(position))
                instanceof appeng.blockentity.grid.AENetworkedBlockEntity networked) {
            networked.getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", network(network)));
        }
    }

    private MEStorage own(int index) {
        return Objects.requireNonNull(this.<MEChestBlockEntity>entity(CHESTS.get(index)).getOriginalCellInventory(0));
    }

    private PatternProviderBlockEntity provider() {
        return entity(C_PROVIDER);
    }

    private <T extends net.minecraft.world.level.block.entity.BlockEntity> T entity(BlockPos position) {
        return helper.getBlockEntity(position);
    }

    private ItemStack planksPattern() {
        var items = NonNullList.withSize(9, ItemStack.EMPTY);
        items.set(0, new ItemStack(Items.OAK_LOG));
        var input = CraftingInput.of(3, 3, items);
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .orElseThrow();
        return PatternDetailsHelper.encodeCraftingPattern(recipe, items.toArray(ItemStack[]::new),
                recipe.value().assemble(input, helper.getLevel().registryAccess()), false, false);
    }

    /**
     * Switches every rule the test set off: the networks reach past the test structure and stay in the level after
     * it, and live rules would keep their mounts and projections for later tests.
     */
    @Override
    public void close() {
        try {
            var policies = PolicyService.get(helper.getLevel());
            for (var key : configured) {
                policies.configured(key).filter(record -> record.rule().enabled()).ifPresent(record -> policies.edit(
                        new PolicyEdit(key, record.revision(), record.rule().withEnabled(false))));
            }
        } finally {
            fixtures.close();
        }
    }
}
