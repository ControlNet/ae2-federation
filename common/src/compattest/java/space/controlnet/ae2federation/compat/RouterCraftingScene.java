package space.controlnet.ae2federation.compat;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import space.controlnet.ae2federation.crafting.projection.CraftingReturnLedger;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.port.RouterPortBinding;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.test.router.RouterFixtures;

/**
 * Networks on the faces of one Router, as the guide's hub examples build them. Each network starts as an ME Chest with
 * an item cell beside its face; only the first network has an energy cell, and the others run on it through the ME
 * power rule. Beside each chest, outwards, a consumer gets its crafting CPU and a provider its crafter, both placed as a
 * player places them, so they join the chest's network. Each consumer uses the providers it names, and every consumer
 * orders at once, each on its own CPU; every order must finish exactly and leave nothing owed.
 */
final class RouterCraftingScene implements AutoCloseable {
    static final AEItemKey LOG = AEItemKey.of(Items.OAK_LOG);
    static final AEItemKey PLANKS = AEItemKey.of(Items.OAK_PLANKS);
    static final AEItemKey STICKS = AEItemKey.of(Items.STICK);

    private final GameTestHelper helper;
    private final BlockPos center;
    private final RouterFixtures routers;
    private final List<Member> members = new ArrayList<>();
    private final Set<PolicyKey> configured = new LinkedHashSet<>();
    private int stage;

    /** Places the networks' chests; {@code center} is where the Router goes once every network stands. */
    RouterCraftingScene(GameTestHelper helper, BlockPos center) {
        this.helper = helper;
        this.center = center;
        routers = new RouterFixtures(helper);
    }

    /** Places what a network needs beside its chest: {@code start} touches the chest, {@code outward} leads away. */
    interface Placement {
        void place(GameTestHelper helper, BlockPos start, Direction outward);
    }

    private static final class Member {
        final String name;
        final Direction face;
        final Placement placement;
        final Predicate<BlockEntity> ready;
        BlockPos patternContainer;
        List<ItemStack> patterns = List.of();
        // Through the inventory a Pattern Access Terminal fills, by default.
        java.util.function.BiPredicate<BlockEntity, ItemStack> install = (entity, pattern) -> entity instanceof
                PatternContainer container && container.getTerminalPatternInventory().addItems(pattern).isEmpty();
        List<Member> sources = List.of();
        AEItemKey order;
        long amount;
        AEItemKey input;
        long inputAmount;
        Future<ICraftingPlan> planFuture;
        ICraftingPlan plan;

        Member(String name, Direction face, Placement placement, Predicate<BlockEntity> ready) {
            this.name = name;
            this.face = face;
            this.placement = placement;
            this.ready = ready;
        }

        boolean consumer() {
            return order != null;
        }
    }

    /**
     * A network that crafts with its crafter: {@code placement} builds it, and its block at {@code patternContainer}
     * (relative to the test, as {@code placement} worked it out) takes {@code patterns} once {@code ready} agrees.
     */
    RouterCraftingScene provider(String name, Direction face, Placement placement, BlockPos patternContainer,
            Predicate<BlockEntity> ready, ItemStack... patterns) {
        var member = new Member(name, face, placement, ready);
        member.patternContainer = patternContainer;
        member.patterns = List.of(patterns);
        members.add(member);
        return this;
    }

    /** Puts the last provider's patterns in with {@code install}, true when it took the pattern. */
    RouterCraftingScene installingPatternsWith(java.util.function.BiPredicate<BlockEntity, ItemStack> install) {
        members.getLast().install = install;
        return this;
    }

    /**
     * A network that orders {@code amount} of {@code order} on the CPU {@code placement} builds, from {@code
     * inputAmount} of {@code input} in its own chest, using the providers named in {@code sources}.
     */
    RouterCraftingScene consumer(String name, Direction face, Placement placement, Predicate<BlockEntity> cpuReady,
            AEItemKey order, long amount, AEItemKey input, long inputAmount, String... sources) {
        var member = new Member(name, face, placement, cpuReady);
        member.order = order;
        member.amount = amount;
        member.input = input;
        member.inputAmount = inputAmount;
        member.patternContainer = chestPosition(face).relative(face);
        member.sources = new ArrayList<>();
        members.add(member);
        for (var source : sources) {
            ((ArrayList<Member>) member.sources).add(members.stream().filter(other -> other.name.equals(source))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Name the provider " + source + " first")));
        }
        return this;
    }

    /** Where {@code face}'s network keeps its chest. */
    BlockPos chestPosition(Direction face) {
        return center.relative(face);
    }

    /** Where a network's crafter or CPU starts: touching its chest, on the side away from the Router. */
    BlockPos start(Direction face) {
        return center.relative(face, 2);
    }

    /** Runs the scene; call from {@code succeedWhen}. */
    void tick() {
        switch (stage) {
            case 0 -> {
                for (var member : members) {
                    routers.placeNativeDevice(center, member.face);
                    chest(member).setCell(AEItems.ITEM_CELL_1K.stack());
                }
                // As the guide's examples have it, one energy cell powers every network through the ME power rule.
                helper.setBlock(chestPosition(members.getFirst().face).below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
                stage = 1;
                helper.fail("Placed the networks' chests");
            }
            case 1 -> {
                helper.assertTrue(settled(), "Waiting for every network's identity");
                for (var member : members) member.placement.place(helper, start(member.face), member.face);
                stage = 2;
                helper.fail("Placed the CPUs and crafters");
            }
            case 2 -> {
                helper.assertTrue(settled(), "Waiting for every network to settle again");
                routers.placeRouter(center);
                stage = 3;
                helper.fail("Placed the Router");
            }
            case 3 -> {
                var router = routers.router(center);
                helper.assertTrue(members.stream().allMatch(member -> router.binding(member.face)
                        instanceof RouterPortBinding.Native), "Waiting for the Router's faces to join the networks");
                helper.assertValueEqual(Set.copyOf(grids()).size(), members.size(), "Each face must be its own network");
                helper.assertTrue(settled() && sharedDomain(), "Waiting for one Federation domain");
                var first = members.getFirst();
                for (var member : members.subList(1, members.size())) {
                    rule(new PolicyKey(network(member), network(first), PolicyCapability.ME_POWER),
                            PolicyRule.enabled(Set.of(PolicyOperation.SUPPLY)));
                }
                stage = 4;
                helper.fail("Switched on ME power");
            }
            case 4 -> {
                for (var member : members) {
                    helper.assertTrue(grid(member).getEnergyService().isNetworkPowered(),
                            "Waiting for the ME power rule to power " + member.name);
                    var entity = helper.getLevel().getBlockEntity(helper.absolutePos(member.patternContainer));
                    helper.assertTrue(member.ready.test(entity), "Waiting for " + member.name + "'s "
                            + (member.consumer() ? "CPU" : "crafter") + " to form");
                    helper.assertTrue(entity != null && grid(entity) == grid(member),
                            member.name + "'s " + entity + " must join its network, but is on " + describe(grid(entity))
                                    + "; its start block is on "
                                    + describe(grid(helper.getLevel().getBlockEntity(helper.absolutePos(start(member.face)))))
                                    + " and its chest on " + describe(grid(member)));
                }
                for (var member : members) {
                    if (member.consumer()) {
                        helper.assertTrue(!grid(member).getCraftingService().getCpus().isEmpty(),
                                member.name + " must have a crafting CPU");
                        continue;
                    }
                    var entity = helper.getLevel().getBlockEntity(helper.absolutePos(member.patternContainer));
                    for (var pattern : member.patterns) {
                        helper.assertTrue(member.install.test(entity, pattern), member.name + "'s " + entity
                                + " refused a pattern");
                    }
                    if (entity instanceof PatternProviderLogicHost host) host.getLogic().updatePatterns();
                }
                for (var member : members) {
                    for (var source : member.sources) {
                        rule(new PolicyKey(network(member), network(source), PolicyCapability.STORAGE),
                                PolicyRule.storageDefaults());
                        rule(new PolicyKey(network(member), network(source), PolicyCapability.CRAFTING),
                                PolicyRule.enabled(Set.of(PolicyOperation.REQUEST)));
                    }
                }
                stage = 5;
                helper.fail("Installed the patterns and switched on the crafting rules");
            }
            case 5 -> {
                for (var member : consumers()) {
                    helper.assertTrue(grid(member).getCraftingService().isCraftable(member.order),
                            "Waiting for the providers' patterns on " + member.name);
                }
                for (var member : consumers()) {
                    helper.assertValueEqual(storage(member).insert(member.input, member.inputAmount,
                            Actionable.MODULATE, IActionSource.empty()), member.inputAmount, "The chest must take the inputs");
                    member.planFuture = grid(member).getCraftingService().beginCraftingCalculation(helper.getLevel(),
                            requester(member), member.order, member.amount, CalculationStrategy.REPORT_MISSING_ITEMS);
                }
                stage = 6;
                helper.fail("Started every consumer's plan");
            }
            case 6 -> {
                for (var member : consumers()) {
                    helper.assertTrue(planReady(member), "Waiting for " + member.name + "'s plan");
                    helper.assertFalse(member.plan.simulation(), member.name + "'s own inputs must be enough");
                }
                for (var member : consumers()) {
                    helper.assertTrue(grid(member).getCraftingService().submitJob(member.plan, null, null, true,
                            IActionSource.empty()).successful(), member.name + "'s own CPU must take the job");
                }
                stage = 7;
                helper.fail("Submitted every order");
            }
            default -> {
                for (var member : consumers()) {
                    helper.assertTrue(grid(member).getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                            "Waiting for " + member.name + "'s job to finish");
                }
                var orders = new java.util.HashMap<AEItemKey, Long>();
                consumers().forEach(member -> orders.merge(member.order, member.amount, Long::sum));
                for (var order : orders.entrySet()) {
                    helper.assertValueEqual(total(order.getKey()), order.getValue(),
                            "The orders must store exactly what was ordered of " + order.getKey());
                }
                for (var member : consumers()) {
                    helper.assertValueEqual(total(member.input), 0L, member.name + "'s inputs must be used up");
                    for (var source : members) {
                        if (source == member) continue;
                        for (var key : List.of(member.order, PLANKS, STICKS)) {
                            helper.assertValueEqual(CraftingReturnLedger.get(helper.getLevel()).owed(network(source),
                                    network(member), key), 0L, source.name + " must owe " + member.name + " nothing");
                        }
                    }
                }
                close();
            }
        }
    }

    /** A crafting pattern for the recipe that {@code items}, laid out row by row in a 3x3 grid, make. */
    static ItemStack craftingPattern(GameTestHelper helper, ItemStack... items) {
        var grid = NonNullList.withSize(9, ItemStack.EMPTY);
        for (int index = 0; index < items.length; index++) grid.set(index, items[index]);
        var input = CraftingInput.of(3, 3, grid);
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .orElseThrow();
        return PatternDetailsHelper.encodeCraftingPattern(recipe, grid.toArray(ItemStack[]::new),
                recipe.value().assemble(input, helper.getLevel().registryAccess()), false, false);
    }

    /** Whether {@code entity} is part of an AE2-style multiblock that has formed. */
    static boolean formed(BlockEntity entity) {
        return entity instanceof appeng.me.cluster.IAEMultiBlock<?> part && part.getCluster() != null;
    }

    /** A one-block AE2 crafting CPU at {@code start}. */
    static void placeCpu(GameTestHelper helper, BlockPos start, Direction outward) {
        helper.setBlock(start, AEBlocks.CRAFTING_STORAGE_1K.block());
    }

    /** Four sticks from two planks. */
    static ItemStack sticksPattern(GameTestHelper helper) {
        return craftingPattern(helper, new ItemStack(Items.OAK_PLANKS), ItemStack.EMPTY, ItemStack.EMPTY,
                new ItemStack(Items.OAK_PLANKS));
    }

    /** Four planks from a log. */
    static ItemStack planksPattern(GameTestHelper helper) {
        return craftingPattern(helper, new ItemStack(Items.OAK_LOG));
    }

    private List<Member> consumers() {
        return members.stream().filter(Member::consumer).toList();
    }

    private boolean planReady(Member member) {
        if (member.plan != null) return true;
        if (!member.planFuture.isDone()) return false;
        try {
            member.plan = member.planFuture.get();
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Calculation interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Calculation failed", exception);
        }
    }

    private ICraftingSimulationRequester requester(Member member) {
        var node = chest(member).getMainNode().getNode();
        return new ICraftingSimulationRequester() {
            @Override
            public IActionSource getActionSource() {
                return IActionSource.empty();
            }

            @Override
            public appeng.api.networking.IGridNode getGridNode() {
                return node;
            }
        };
    }

    /** Every network's own chest cell, so an output stored through a storage mount is counted once. */
    private long total(AEItemKey key) {
        long total = 0;
        for (var member : members) {
            total += storage(member).extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
        }
        return total;
    }

    private MEStorage storage(Member member) {
        return Objects.requireNonNull(chest(member).getOriginalCellInventory(0));
    }

    private MEChestBlockEntity chest(Member member) {
        return helper.getBlockEntity(chestPosition(member.face));
    }

    private List<IGrid> grids() {
        return members.stream().map(this::grid).toList();
    }

    private IGrid grid(Member member) {
        return routers.nativeDeviceNode(center, member.face).getGrid();
    }

    /** The Grid of {@code entity}'s own node: inner blocks of a multiblock expose no node on any side. */
    private static IGrid grid(BlockEntity entity) {
        if (entity instanceof appeng.me.helpers.IGridConnectedBlockEntity connected
                && connected.getMainNode().getGrid() != null) {
            return connected.getMainNode().getGrid();
        }
        if (entity instanceof appeng.api.networking.IInWorldGridNodeHost host) {
            for (var side : Direction.values()) {
                var node = host.getGridNode(side);
                if (node != null) return node.getGrid();
            }
            var node = host.getGridNode(null);
            if (node != null) return node.getGrid();
        }
        return null;
    }

    private static String describe(IGrid grid) {
        return grid == null ? "no Grid" : "Grid " + System.identityHashCode(grid) + " of " + grid.size() + " nodes";
    }

    private boolean settled() {
        return grids().stream().allMatch(grid -> grid != null
                && FederationDomainRegistryAccess.confirmedNetworkId(grid).isPresent());
    }

    private boolean sharedDomain() {
        var registry = FederationDomainRegistryAccess.get(helper.getLevel());
        var common = new java.util.HashSet<>(registry.federationdomainsFor(network(members.getFirst())));
        for (var member : members) common.retainAll(registry.federationdomainsFor(network(member)));
        return !common.isEmpty();
    }

    private NetworkId network(Member member) {
        return FederationDomainRegistryAccess.confirmedNetworkId(grid(member)).orElseThrow();
    }

    private void rule(PolicyKey key, PolicyRule rule) {
        var policies = PolicyService.get(helper.getLevel());
        var result = policies.edit(new PolicyEdit(key, policies.revision(key), rule));
        helper.assertTrue(result instanceof PolicyMutationResult.Accepted, "The rule " + key + " must be accepted");
        configured.add(key);
    }

    /** Switches the scene's rules off and releases the Router's test nodes, as later tests share the level. */
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
