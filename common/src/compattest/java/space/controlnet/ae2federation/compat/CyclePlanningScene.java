package space.controlnet.ae2federation.compat;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
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
 * An addon's cycle planner with a pattern only the provider network has: one wheat seed makes two, as a seed farm does.
 * The consumer stores one seed and orders four. AE2's own planner will not use a pattern for its own input, so it
 * reports a missing seed; the addon's planner must chain the pattern instead, and its plan must run through the
 * projected provider, every seed the machine makes coming back to the consumer's CPU and storage.
 */
final class CyclePlanningScene {
    private static final BlockPos BASE = new BlockPos(5, 3, 5);
    private static final BlockPos PROVIDER = AddonCraftingScene.PROVIDER;
    private static final BlockPos MACHINE = PROVIDER.east();
    private static final AEItemKey SEEDS = AEItemKey.of(Items.WHEAT_SEEDS);
    private static final long STORED = 1;
    private static final long ORDERED = 4;

    private final GameTestHelper helper;
    private final PolicyBridgeFixtures bridge;
    private final String planner;
    private final UnaryOperator<ICraftingSimulationRequester> plannerRequest;
    private final Predicate<ICraftingPlan> plannedByAddon;
    private java.util.function.Consumer<GameTestHelper> placeConsumerCpu =
            test -> test.setBlock(AddonCraftingScene.FIRST_CPU, AddonCraftingScene.block("ae2:1k_crafting_storage"));
    private java.util.function.BooleanSupplier consumerCpuReady = () -> true;
    private Runnable finish = () -> {
    };
    private boolean planningOnly;
    private java.util.function.Function<java.util.function.Supplier<Future<ICraftingPlan>>, Future<ICraftingPlan>>
            plannerScope = java.util.function.Supplier::get;
    private int stage;
    private Future<ICraftingPlan> nativeFuture;
    private Future<ICraftingPlan> plannerFuture;
    private long crafts;
    /** What the submitted plan runs, for the message of a job that does not finish. */
    private String submittedPlan;

    /**
     * {@code plannerRequest} turns a plain request into the one the addon's own screen makes, and
     * {@code plannedByAddon} tells whether a plan came from the addon's planner rather than AE2's.
     */
    CyclePlanningScene(GameTestHelper helper, String planner, UnaryOperator<ICraftingSimulationRequester> plannerRequest,
            Predicate<ICraftingPlan> plannedByAddon) {
        this.helper = helper;
        this.planner = planner;
        this.plannerRequest = plannerRequest;
        this.plannedByAddon = plannedByAddon;
        bridge = new PolicyBridgeFixtures(helper, BASE);
        bridge.installStorageCells(false);
    }

    /**
     * The consumer runs the job on a CPU multiblock, which {@code place} builds instead of AE2's crafting storage; the
     * consumer orders once {@code ready} agrees. Some planners give their plans only to their own CPUs.
     */
    CyclePlanningScene consumerCpuStructure(java.util.function.Consumer<GameTestHelper> place,
            java.util.function.BooleanSupplier ready) {
        placeConsumerCpu = place;
        consumerCpuReady = ready;
        return this;
    }

    /**
     * Starts the addon's calculation inside {@code scope}, for a planner that the screen switches on around the call
     * rather than through the request: {@code scope} must run the calculation it is given and return its result.
     */
    CyclePlanningScene planningWithin(
            java.util.function.Function<java.util.function.Supplier<Future<ICraftingPlan>>, Future<ICraftingPlan>> scope) {
        plannerScope = scope;
        return this;
    }

    /**
     * Checks the addon's plan without running it, for a planner whose cycle plans AE2's own CPU cannot run on any
     * network, Federation or not.
     */
    CyclePlanningScene planningOnly() {
        planningOnly = true;
        return this;
    }

    /** Runs {@code finish} once the scene has passed, such as to put back a setting the planner needed. */
    CyclePlanningScene finishing(Runnable finish) {
        this.finish = finish;
        return this;
    }

    /** Runs the whole scene; call from {@code succeedWhen}. */
    void tick() {
        switch (stage) {
            case 0 -> {
                helper.assertTrue(bridge.networksSettled(), "Waiting for both networks");
                helper.setBlock(PROVIDER, AddonCraftingScene.block("ae2:pattern_provider"));
                helper.setBlock(MACHINE, AddonCraftingScene.block("minecraft:chest"));
                placeConsumerCpu.accept(helper);
                stage = 1;
                helper.fail("Placed the provider, the seed machine and the CPU");
            }
            case 1 -> {
                helper.assertTrue(bridge.networksSettled(), "Waiting for both networks to settle again");
                bridge.placeFirstBridge();
                stage = 2;
                helper.fail("Placed the Bridge");
            }
            case 2 -> {
                helper.assertTrue(bridge.networksSettled(), "Waiting for both networks: " + bridge.settlementDiagnostics());
                if (!bridge.firstBridgeReady()) bridge.refreshFirstBridge();
                helper.assertTrue(bridge.firstBridgeReady(), "Waiting for the Bridge");
                edit(new PolicyKey(bridge.outerNetwork(), bridge.mainNetwork(), PolicyCapability.ME_POWER),
                        PolicyRule.enabled(Set.of(PolicyOperation.SUPPLY)));
                var entity = helper.getBlockEntity(PROVIDER);
                helper.assertTrue(((PatternContainer) entity).getTerminalPatternInventory().addItems(seedPattern()).isEmpty(),
                        "The provider refused the seed pattern");
                ((PatternProviderLogicHost) entity).getLogic().updatePatterns();
                stage = 3;
                helper.fail("Installed the seed pattern");
            }
            case 3 -> {
                helper.assertTrue(bridge.outerGrid().getEnergyService().isNetworkPowered(),
                        "Waiting for the ME power rule to power the provider network");
                helper.assertTrue(bridge.outerGrid().getCraftingService().isCraftable(SEEDS),
                        "Waiting for the provider network to know the seed pattern");
                helper.assertTrue(consumerCpuReady.getAsBoolean(), "Waiting for the consumer's CPU structure");
                helper.assertFalse(consumerGrid().getCraftingService().getCpus().isEmpty(), "Waiting for the CPU");
                helper.assertTrue(FederationDomainRegistryAccess.confirmedNetworkId(consumerGrid()).isPresent()
                        && FederationDomainRegistryAccess.confirmedNetworkId(bridge.outerGrid()).isPresent(),
                        "Waiting for both networks' identities");
                var policies = PolicyService.get(helper.getLevel());
                var storage = new PolicyKey(bridge.mainNetwork(), bridge.outerNetwork(), PolicyCapability.STORAGE);
                var crafting = new PolicyKey(bridge.mainNetwork(), bridge.outerNetwork(), PolicyCapability.CRAFTING);
                var result = policies.editAll(List.of(
                        new PolicyEdit(storage, policies.revision(storage), PolicyRule.storageDefaults()),
                        new PolicyEdit(crafting, policies.revision(crafting),
                                PolicyRule.enabled(Set.of(PolicyOperation.REQUEST)))));
                helper.assertTrue(result instanceof PolicyMutationResult.Accepted, "The rule edit was refused: " + result);
                stage = 4;
                helper.fail("Enabled the rules");
            }
            case 4 -> {
                helper.assertTrue(consumerGrid().getCraftingService().isCraftable(SEEDS),
                        "Waiting for the projected seed pattern on the consumer");
                helper.assertValueEqual(consumerChest().insert(SEEDS, STORED, Actionable.MODULATE, IActionSource.empty()),
                        STORED, "The consumer's chest must take its seed");
                nativeFuture = begin(plainRequest());
                stage = 5;
                helper.fail("Planning with AE2's planner");
            }
            case 5 -> {
                helper.assertTrue(nativeFuture.isDone(), "Waiting for AE2's plan");
                var plan = get(nativeFuture);
                helper.assertFalse(plannedByAddon.test(plan), "A plain request must stay with AE2's planner");
                helper.assertTrue(plan.simulation(), "AE2's planner must not chain the seed pattern: " + plan.patternTimes());
                beginPlanner();
            }
            case 6 -> {
                helper.assertTrue(plannerFuture.isDone(), "Waiting for " + planner + "'s plan");
                var plan = get(plannerFuture);
                helper.assertTrue(plannedByAddon.test(plan), planner + " must make the plan itself");
                helper.assertFalse(plan.simulation(), planner + " must chain the projected seed pattern: missing "
                        + plan.missingItems() + ", patterns " + plan.patternTimes());
                crafts = plan.patternTimes().values().stream().mapToLong(Long::longValue).sum();
                submittedPlan = plan.patternTimes() + " used " + plan.usedItems() + " emitted " + plan.emittedItems();
                helper.assertTrue(crafts >= ORDERED - STORED, "Too few crafts for the order: " + plan.patternTimes());
                var remote = ((PatternProviderLogicHost) helper.getBlockEntity(PROVIDER)).getLogic().getAvailablePatterns();
                helper.assertTrue(plan.patternTimes().keySet().stream().allMatch(
                        pattern -> remote.stream().anyMatch(own -> own == pattern)),
                        planner + " must plan with the provider's own pattern: " + plan.patternTimes());
                if (planningOnly) {
                    finish.run();
                    return;
                }
                var submitted = consumerGrid().getCraftingService().submitJob(plan, null, null, true,
                        IActionSource.empty());
                helper.assertTrue(submitted.successful(), "The consumer's CPU must take " + planner + "'s plan: "
                        + submitted.errorCode() + " " + submitted.errorDetail());
                stage = 7;
                helper.fail("Submitted the job");
            }
            default -> {
                runMachine();
                helper.assertTrue(consumerGrid().getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                        "Waiting for the consumer's job to finish: plan " + submittedPlan + ", consumer "
                                + held(consumerChest()) + ", provider " + held(providerChest()) + ", owed "
                                + CraftingReturnLedger.get(helper.getLevel()).owed(bridge.outerNetwork(),
                                        bridge.mainNetwork(), SEEDS) + ", jobs " + consumerGrid().getCraftingService()
                                        .getCpus().stream().filter(cpu -> cpu.isBusy()).map(cpu -> cpu.getJobStatus())
                                        .toList());
                helper.assertValueEqual(seedsIn(MACHINE), 0L, "The machine must have nothing left to make");
                // Each craft takes one seed and makes two.
                helper.assertValueEqual(held(consumerChest()), STORED + crafts,
                        "Every seed made must reach the consumer");
                helper.assertValueEqual(held(providerChest()), 0L, "No seed may stay on the provider network");
                helper.assertValueEqual(CraftingReturnLedger.get(helper.getLevel()).owed(bridge.outerNetwork(),
                        bridge.mainNetwork(), SEEDS), 0L, "Nothing may stay owed");
                finish.run();
            }
        }
    }

    private void beginPlanner() {
        var request = plannerRequest.apply(plainRequest());
        plannerFuture = plannerScope.apply(() -> begin(request));
        stage = 6;
        helper.fail("Planning with " + planner);
    }

    /** The seed farm: every seed the provider pushed into its chest becomes two on the provider network. */
    private void runMachine() {
        long pushed = seedsIn(MACHINE);
        if (pushed == 0) return;
        helper.<ChestBlockEntity>getBlockEntity(MACHINE).clearContent();
        bridge.outerGrid().getStorageService().getInventory().insert(SEEDS, 2 * pushed, Actionable.MODULATE,
                IActionSource.empty());
    }

    private long seedsIn(BlockPos position) {
        var chest = helper.<ChestBlockEntity>getBlockEntity(position);
        long count = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (chest.getItem(slot).is(Items.WHEAT_SEEDS)) count += chest.getItem(slot).getCount();
        }
        return count;
    }

    private Future<ICraftingPlan> begin(ICraftingSimulationRequester requester) {
        return consumerGrid().getCraftingService().beginCraftingCalculation(helper.getLevel(), requester, SEEDS, ORDERED,
                CalculationStrategy.REPORT_MISSING_ITEMS);
    }

    private ICraftingSimulationRequester plainRequest() {
        var node = bridge.consumerChest().getMainNode().getNode();
        return new ICraftingSimulationRequester() {
            @Override
            public IActionSource getActionSource() {
                return IActionSource.empty();
            }

            @Override
            public IGridNode getGridNode() {
                return node;
            }
        };
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

    private void edit(PolicyKey key, PolicyRule rule) {
        var policies = PolicyService.get(helper.getLevel());
        var result = policies.edit(new PolicyEdit(key, policies.revision(key), rule));
        helper.assertTrue(result instanceof PolicyMutationResult.Accepted, "The rule was refused: " + result);
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

    private static long held(MEStorage storage) {
        return storage.extract(SEEDS, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    private static ItemStack seedPattern() {
        return PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(SEEDS, 1)),
                List.of(new GenericStack(SEEDS, 2)));
    }
}
