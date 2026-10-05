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
 * an item cell beside its face; only the first network (or the one named by {@link #poweredBy}) has an energy cell,
 * and the others run on it through the ME power rule. Beside each chest, outwards, a consumer gets its crafting CPU and a provider its crafter, both placed as a
 * player places them, so they join the chest's network. Each consumer uses the providers it names, and every consumer
 * places its orders at once on its own CPU; every order must finish exactly and leave nothing owed.
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
    private String refused;
    private String powering;
    private String[] switchOff;
    private AEItemKey gone;
    private AEItemKey kept;
    private boolean switchedOff;
    private BlockPos removed;
    private net.minecraft.world.level.block.state.BlockState removedState;
    private AEItemKey lost;
    private String loser;
    private int removal;
    private boolean ownPower;
    private String unpowered;
    private AEItemKey dark;
    private String darkConsumer;
    private boolean unpoweredDone;
    private Runnable endCheck;

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
        final List<Order> orders = new ArrayList<>();
        boolean storage = true;

        Member(String name, Direction face, Placement placement, Predicate<BlockEntity> ready) {
            this.name = name;
            this.face = face;
            this.placement = placement;
            this.ready = ready;
        }

        boolean consumer() {
            return !orders.isEmpty();
        }
    }

    /** {@code amount} of {@code what}, made from {@code inputAmount} of {@code input} in the consumer's own chest. */
    private static final class Order {
        final AEItemKey what;
        final long amount;
        final AEItemKey input;
        final long inputAmount;
        Future<ICraftingPlan> planFuture;
        ICraftingPlan plan;
        boolean submitted;

        Order(AEItemKey what, long amount, AEItemKey input, long inputAmount) {
            this.what = what;
            this.amount = amount;
            this.input = input;
            this.inputAmount = inputAmount;
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
        member.orders.add(new Order(order, amount, input, inputAmount));
        member.patternContainer = chestPosition(face).relative(face);
        member.sources = new ArrayList<>();
        members.add(member);
        for (var source : sources) {
            ((ArrayList<Member>) member.sources).add(members.stream().filter(other -> other.name.equals(source))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Name the provider " + source + " first")));
        }
        return this;
    }

    /** The last network's chest stays empty, so it has no storage of its own. */
    RouterCraftingScene withoutStorage() {
        members.getLast().storage = false;
        return this;
    }

    /**
     * A network that neither crafts nor orders: {@code placement} builds it, and its block at {@code readyAt} (relative
     * to the test) must be on its network once {@code ready} agrees.
     */
    RouterCraftingScene member(String name, Direction face, Placement placement, BlockPos readyAt,
            Predicate<BlockEntity> ready) {
        var member = new Member(name, face, placement, ready);
        member.patternContainer = readyAt;
        members.add(member);
        return this;
    }

    /** The network {@link #poweredBy} names brings its own power source, so no energy cell is placed. */
    RouterCraftingScene withoutEnergyCell() {
        ownPower = true;
        return this;
    }

    /**
     * After every order, ME power between {@code name} and the powering network is switched off, as the guide's
     * exercise has its player do: {@code name} must lose power and {@code gone} leave {@code consumer}'s craftables,
     * while {@code consumer} stays powered.
     */
    RouterCraftingScene thenSwitchingOffPower(String name, AEItemKey gone, String consumer) {
        unpowered = name;
        dark = gone;
        darkConsumer = consumer;
        return this;
    }

    /** Runs {@code check}, which fails the test by assertion, after everything else. */
    RouterCraftingScene checkingAtEnd(Runnable check) {
        endCheck = check;
        return this;
    }

    /** The last consumer also orders {@code amount} of {@code order}, at the same time and on the same CPU. */
    RouterCraftingScene alsoOrdering(AEItemKey order, long amount, AEItemKey input, long inputAmount) {
        members.getLast().orders.add(new Order(order, amount, input, inputAmount));
        return this;
    }

    /** The network named {@code name} holds the energy cell instead of the first one. */
    RouterCraftingScene poweredBy(String name) {
        powering = name;
        return this;
    }

    /**
     * After every order, {@code consumer}'s Crafting rule on {@code source} is switched off, as the guide's exercise has
     * its player do: {@code gone} must stop being craftable there while {@code kept} still is.
     */
    RouterCraftingScene thenSwitchingOff(String consumer, String source, AEItemKey gone, AEItemKey kept) {
        switchOff = new String[] {consumer, source};
        this.gone = gone;
        this.kept = kept;
        return this;
    }

    /**
     * After every order, the block at {@code block} is broken and put back, as the guide's exercise has its player do:
     * {@code key} must leave {@code downstream}'s craftables while it is gone and come back once it is in place again.
     */
    RouterCraftingScene thenRemoving(BlockPos block, AEItemKey key, String downstream) {
        removed = block;
        lost = key;
        loser = downstream;
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
                    if (member.storage) chest(member).setCell(AEItems.ITEM_CELL_1K.stack());
                }
                // As the guide's examples have it, one energy cell powers every network through the ME power rule.
                if (!ownPower) helper.setBlock(chestPosition(power().face).below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
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
                var power = power();
                for (var member : members) {
                    if (member == power) continue;
                    rule(new PolicyKey(network(member), network(power), PolicyCapability.ME_POWER),
                            PolicyRule.enabled(Set.of(PolicyOperation.SUPPLY)));
                }
                stage = 4;
                helper.fail("Switched on ME power");
            }
            case 4 -> {
                for (var member : members) {
                    var entity = helper.getLevel().getBlockEntity(helper.absolutePos(member.patternContainer));
                    // Before power: a network that brings its own source may only start it once it has formed.
                    helper.assertTrue(member.ready.test(entity), "Waiting for " + member.name + "'s "
                            + (member.consumer() ? "CPU" : "crafter") + " to form");
                    helper.assertTrue(grid(member).getEnergyService().isNetworkPowered(),
                            "Waiting for the ME power rule to power " + member.name);
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
                    for (var order : member.orders) {
                        helper.assertTrue(grid(member).getCraftingService().isCraftable(order.what),
                                "Waiting for the providers' patterns of " + order.what + " on " + member.name);
                    }
                }
                for (var member : consumers()) {
                    for (var order : member.orders) {
                        helper.assertValueEqual(storage(member).insert(order.input, order.inputAmount,
                                Actionable.MODULATE, IActionSource.empty()), order.inputAmount,
                                "The chest must take the inputs");
                    }
                    for (var order : member.orders) {
                        order.planFuture = grid(member).getCraftingService().beginCraftingCalculation(
                                helper.getLevel(), requester(member), order.what, order.amount,
                                CalculationStrategy.REPORT_MISSING_ITEMS);
                    }
                }
                stage = 6;
                helper.fail("Started every consumer's plan");
            }
            case 6 -> {
                for (var member : consumers()) {
                    for (var order : member.orders) {
                        helper.assertTrue(planReady(order), "Waiting for " + member.name + "'s plan");
                        helper.assertFalse(order.plan.simulation(), member.name + "'s own inputs must be enough for "
                                + order.what + ": " + order.plan.missingItems());
                    }
                }
                for (var member : consumers()) {
                    for (var order : member.orders) {
                        // succeedWhen retries this stage until every job is taken, so each job goes in once.
                        if (order.submitted) continue;
                        var result = grid(member).getCraftingService().submitJob(order.plan, null, null, true,
                                IActionSource.empty());
                        if (!result.successful() && refused == null) {
                            refused = member.name + "'s CPU refused the job for " + order.what + ": "
                                    + result.errorCode() + " " + result.errorDetail();
                        }
                        helper.assertTrue(result.successful(), member.name + "'s own CPU must take the job for "
                                + order.what + ": " + result.errorCode() + " " + result.errorDetail());
                        order.submitted = true;
                    }
                }
                stage = 7;
                helper.fail("Submitted every order");
            }
            default -> {
                if (removal > 0) {
                    removing();
                    close();
                    return;
                }
                // Every job is ordered at once, so a CPU that can run only one job at a time must not pass by
                // taking the next one after the first is done.
                helper.assertTrue(refused == null, "Every job must be taken when it is ordered: " + refused);
                for (var member : consumers()) {
                    helper.assertTrue(grid(member).getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                            "Waiting for " + member.name + "'s job to finish");
                }
                var orders = new java.util.HashMap<AEItemKey, Long>();
                consumers().forEach(member -> member.orders.forEach(order -> orders.merge(order.what, order.amount,
                        Long::sum)));
                for (var order : orders.entrySet()) {
                    helper.assertValueEqual(total(order.getKey()), order.getValue(),
                            "The orders must store exactly what was ordered of " + order.getKey());
                }
                for (var member : consumers()) {
                    for (var order : member.orders) {
                        helper.assertValueEqual(total(order.input), 0L, member.name + "'s inputs must be used up");
                    }
                    for (var source : members) {
                        if (source == member) continue;
                        var keys = new LinkedHashSet<AEItemKey>(List.of(PLANKS, STICKS));
                        member.orders.forEach(order -> keys.add(order.what));
                        for (var key : keys) {
                            helper.assertValueEqual(CraftingReturnLedger.get(helper.getLevel()).owed(network(source),
                                    network(member), key), 0L, source.name + " must owe " + member.name + " nothing");
                        }
                    }
                }
                if (switchOff != null) {
                    var consumer = named(switchOff[0]);
                    var key = new PolicyKey(network(consumer), network(named(switchOff[1])), PolicyCapability.CRAFTING);
                    if (!switchedOff) {
                        var policies = PolicyService.get(helper.getLevel());
                        policies.configured(key).ifPresent(record -> rule(key, record.rule().withEnabled(false)));
                        switchedOff = true;
                        helper.fail("Switched off " + consumer.name + "'s Crafting rule on " + switchOff[1]);
                    }
                    helper.assertFalse(grid(consumer).getCraftingService().isCraftable(gone),
                            "Waiting for " + gone + " to leave " + consumer.name + "'s craftables");
                    helper.assertTrue(grid(consumer).getCraftingService().isCraftable(kept),
                            consumer.name + " must still craft " + kept);
                }
                if (removed != null) removing();
                if (unpowered != null) {
                    var member = named(unpowered);
                    var key = new PolicyKey(network(member), network(power()), PolicyCapability.ME_POWER);
                    if (!unpoweredDone) {
                        var policies = PolicyService.get(helper.getLevel());
                        policies.configured(key).ifPresent(record -> rule(key, record.rule().withEnabled(false)));
                        unpoweredDone = true;
                        helper.fail("Switched off ME power for " + member.name);
                    }
                    helper.assertFalse(grid(member).getEnergyService().isNetworkPowered(),
                            "Waiting for " + member.name + " to lose power");
                    helper.assertFalse(grid(named(darkConsumer)).getCraftingService().isCraftable(dark),
                            "Waiting for " + dark + " to leave " + darkConsumer + "'s craftables");
                    helper.assertTrue(grid(named(darkConsumer)).getEnergyService().isNetworkPowered(),
                            darkConsumer + " must stay powered");
                }
                if (endCheck != null) endCheck.run();
                close();
            }
        }
    }

    /**
     * Breaks the block {@link #thenRemoving} names and puts it back. Once it is broken, a network may split and lose
     * its identity until it is back, so this alone runs on the later ticks.
     */
    private void removing() {
        var downstream = grid(named(loser)).getCraftingService();
        if (removal == 0) {
            removedState = helper.getBlockState(removed);
            helper.setBlock(removed, net.minecraft.world.level.block.Blocks.AIR);
            removal = 1;
            helper.fail("Broke the block at " + removed);
        }
        if (removal == 1) {
            helper.assertFalse(downstream.isCraftable(lost),
                    "Waiting for " + lost + " to leave " + loser + "'s craftables");
            helper.setBlock(removed, removedState);
            removal = 2;
            helper.fail("Put the block at " + removed + " back");
        }
        helper.assertTrue(downstream.isCraftable(lost),
                "Waiting for " + lost + " to come back to " + loser + "'s craftables");
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

    /** An AE2 Pattern Provider at {@code start} with a Molecular Assembler beyond it, which crafts its patterns. */
    static void placeAssemblyProvider(GameTestHelper helper, BlockPos start, Direction outward) {
        helper.setBlock(start, AEBlocks.PATTERN_PROVIDER.block());
        helper.setBlock(start.relative(outward), AEBlocks.MOLECULAR_ASSEMBLER.block());
    }

    /** Whether {@code entity} is a pattern provider. */
    static boolean patternProvider(BlockEntity entity) {
        return entity instanceof PatternProviderLogicHost;
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

    private Member named(String name) {
        return members.stream().filter(member -> member.name.equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No network named " + name));
    }

    private Member power() {
        return powering == null ? members.getFirst() : named(powering);
    }

    private List<Member> consumers() {
        return members.stream().filter(Member::consumer).toList();
    }

    private boolean planReady(Order order) {
        if (order.plan != null) return true;
        if (!order.planFuture.isDone()) return false;
        try {
            order.plan = order.planFuture.get();
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
            if (!member.storage) continue;
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
        var id = FederationDomainRegistryAccess.confirmedNetworkId(grid(member));
        helper.assertTrue(id.isPresent(), "Waiting for " + member.name + "'s identity: " + grid(member).getService(
                space.controlnet.ae2federation.identity.NetworkIdentityService.class).settlement() + " on "
                + describe(grid(member)));
        return id.get();
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
