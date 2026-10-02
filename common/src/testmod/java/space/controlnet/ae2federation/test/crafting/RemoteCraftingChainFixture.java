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
import space.controlnet.ae2federation.test.router.RouterFixtures;

/**
 * Three native Grids on three faces of one Router: the consumer (north) has only a chest and a CPU; the middle (south)
 * crafts sticks from planks; the source (east) crafts planks from logs and holds the logs. Crafting rules run
 * consumer to middle and middle to source only, so the consumer's sticks need the middle to request planks in turn.
 */
public final class RemoteCraftingChainFixture implements AutoCloseable {
    private static final BlockPos CENTER = new BlockPos(6, 5, 6);
    private static final List<Direction> FACES = List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST);
    private static final int CONSUMER = 0;
    private static final int MIDDLE = 1;
    private static final int SOURCE = 2;

    private final GameTestHelper helper;
    private final RouterFixtures routers;
    private int stage;
    private Future<ICraftingPlan> planFuture;
    private ICraftingPlan plan;

    public RemoteCraftingChainFixture(GameTestHelper helper) {
        this.helper = helper;
        routers = new RouterFixtures(helper);
        for (var face : FACES) {
            routers.placeNativeDevice(CENTER, face);
            helper.setBlock(CENTER.relative(face).below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
            chest(face).setCell(AEItems.ITEM_CELL_1K.stack());
        }
    }

    /** Places the Router, then each Grid's CPU, providers and assemblers, seeded with the Grid's settled id. */
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
            for (var face : FACES) place(cpu(face), AEBlocks.CRAFTING_STORAGE_1K.block(), face);
            for (var face : List.of(FACES.get(MIDDLE), FACES.get(SOURCE))) {
                place(provider(face), AEBlocks.PATTERN_PROVIDER.block(), face);
                place(provider(face).above(), AEBlocks.MOLECULAR_ASSEMBLER.block(), face);
            }
            stage = 2;
            return false;
        }
        if (stage == 2) {
            if (grids().stream().anyMatch(grid -> grid.getCraftingService().getCpus().isEmpty())
                    || providerEntity(FACES.get(MIDDLE)).getMainNode().getGrid() != grid(MIDDLE)
                    || providerEntity(FACES.get(SOURCE)).getMainNode().getGrid() != grid(SOURCE)) {
                return false;
            }
            install(FACES.get(MIDDLE), stickPattern());
            install(FACES.get(SOURCE), planksPattern());
            stage = 3;
            return false;
        }
        var registry = FederationDomainRegistryAccess.get(helper.getLevel());
        var common = registry.federationdomainsFor(network(grid(CONSUMER)));
        return grid(MIDDLE).getCraftingService().isCraftable(stick())
                && grid(SOURCE).getCraftingService().isCraftable(planks())
                && FACES.stream().skip(1).allMatch(face -> registry.federationdomainsFor(network(grid(face)))
                        .stream().anyMatch(common::contains));
    }

    public String readinessState() {
        return "stage=" + stage + ",grids=" + Set.copyOf(grids()).size();
    }

    /** Crafting rules consumer to middle and middle to source; none between consumer and source. */
    public void enableChain() {
        var policies = PolicyService.get(helper.getLevel());
        for (var key : List.of(consumerToMiddle(), middleToSource())) {
            var result = policies.edit(new PolicyEdit(key, policies.revision(key),
                    PolicyRule.enabled(Set.of(PolicyOperation.REQUEST))));
            helper.assertTrue(result instanceof PolicyMutationResult.Accepted, "Each chain rule must be accepted");
        }
        space.controlnet.ae2federation.crafting.binding.CraftingBindingService.get(helper.getLevel())
                .observeFederationDomainMembers(grids());
    }

    public PolicyKey consumerToMiddle() {
        return key(grid(CONSUMER), grid(MIDDLE));
    }

    public PolicyKey middleToSource() {
        return key(grid(MIDDLE), grid(SOURCE));
    }

    public PolicyKey consumerToSource() {
        return key(grid(CONSUMER), grid(SOURCE));
    }

    public void insertLogs(long amount) {
        helper.assertValueEqual(storage(SOURCE).insert(log(), amount, Actionable.MODULATE, IActionSource.empty()), amount,
                "The source chest must accept the logs");
    }

    public boolean consumerLists(AEItemKey key) {
        return grid(CONSUMER).getCraftingService().getCraftables(candidate -> true).contains(key);
    }

    public boolean consumerHasPattern(AEItemKey key) {
        return grid(CONSUMER).getCraftingService().isCraftable(key);
    }

    public void beginOnConsumer(long amount) {
        var node = chest(FACES.get(CONSUMER)).getMainNode().getNode();
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
        planFuture = grid(CONSUMER).getCraftingService().beginCraftingCalculation(helper.getLevel(), requester, stick(),
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
            throw new IllegalStateException("Consumer calculation interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Consumer calculation failed", exception);
        }
    }

    public ICraftingPlan plan() {
        return plan;
    }

    public boolean submitOnConsumer() {
        return grid(CONSUMER).getCraftingService().submitJob(plan, null, null, true, IActionSource.empty()).successful();
    }

    public long consumerAmount(AEItemKey key) {
        return amount(CONSUMER, key);
    }

    public long middleAmount(AEItemKey key) {
        return amount(MIDDLE, key);
    }

    public long sourceAmount(AEItemKey key) {
        return amount(SOURCE, key);
    }

    public long busyCpuCount() {
        return grids().stream().flatMap(grid -> grid.getCraftingService().getCpus().stream())
                .filter(cpu -> cpu.isBusy()).count();
    }

    public List<IGrid> grids() {
        return FACES.stream().map(face -> routers.nativeDeviceNode(CENTER, face).getGrid()).toList();
    }

    public static AEItemKey stick() {
        return AEItemKey.of(Items.STICK);
    }

    public static AEItemKey planks() {
        return AEItemKey.of(Items.OAK_PLANKS);
    }

    public static AEItemKey log() {
        return AEItemKey.of(Items.OAK_LOG);
    }

    private long amount(int index, AEItemKey key) {
        return storage(index).extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    /** The Grid's own chest cell, not the Grid's aggregate, so nothing mounted from another network is counted. */
    private MEStorage storage(int index) {
        return Objects.requireNonNull(chest(FACES.get(index)).getOriginalCellInventory(0));
    }

    private IGrid grid(int index) {
        return grids().get(index);
    }

    private IGrid grid(Direction face) {
        return grids().get(FACES.indexOf(face));
    }

    private void place(BlockPos position, net.minecraft.world.level.block.Block block, Direction face) {
        helper.setBlock(position, block);
        var entity = helper.getLevel().getBlockEntity(helper.absolutePos(position));
        var networkId = network(grid(face));
        if (entity instanceof appeng.blockentity.grid.AENetworkedBlockEntity networked) {
            networked.getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", networkId));
        }
    }

    private void install(Direction face, ItemStack pattern) {
        var logic = providerEntity(face).getLogic();
        logic.getPatternInv().addItems(pattern);
        logic.updatePatterns();
    }

    private MEChestBlockEntity chest(Direction face) {
        return helper.getBlockEntity(CENTER.relative(face));
    }

    private static BlockPos cpu(Direction face) {
        return CENTER.relative(face, 2);
    }

    private static BlockPos provider(Direction face) {
        return CENTER.relative(face).above();
    }

    private PatternProviderBlockEntity providerEntity(Direction face) {
        return helper.getBlockEntity(provider(face));
    }

    private ItemStack stickPattern() {
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

    private static PolicyKey key(IGrid consumer, IGrid provider) {
        return new PolicyKey(network(consumer), network(provider), PolicyCapability.CRAFTING);
    }

    private static NetworkId network(IGrid grid) {
        return FederationDomainRegistryAccess.confirmedNetworkId(grid).orElseThrow();
    }

    /**
     * Switches the chain's rules off before the Router is released: the Grids outlive the test, and live rules would
     * keep their bindings reconciled on every domain refresh of later tests in the same level.
     */
    @Override
    public void close() {
        var policies = PolicyService.get(helper.getLevel());
        for (var key : List.of(consumerToMiddle(), middleToSource())) {
            policies.configured(key).ifPresent(configured -> policies.edit(new PolicyEdit(key, policies.revision(key),
                    configured.rule().withEnabled(false))));
        }
        routers.close();
    }
}
