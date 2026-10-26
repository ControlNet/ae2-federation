package space.controlnet.ae2federation.test;

import static space.controlnet.ae2federation.test.crafting.PatternProjectionFixture.cobblestone;
import static space.controlnet.ae2federation.test.crafting.PatternProjectionFixture.stone;
import static space.controlnet.ae2federation.test.crafting.ProjectionChainFixture.CONSUMER;
import static space.controlnet.ae2federation.test.crafting.ProjectionChainFixture.MIDDLE;
import static space.controlnet.ae2federation.test.crafting.ProjectionChainFixture.SOURCE;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import java.util.concurrent.Future;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.policy.RuleMode;
import space.controlnet.ae2federation.test.crafting.PatternProjectionFixture;
import space.controlnet.ae2federation.test.crafting.ProjectionChainFixture;

/**
 * What a provider network's machine gives back through its pattern provider beyond what a consumer's CPU waits for:
 * a byproduct no pattern names, or the output of a job the consumer cancelled. It goes where it would go if the
 * provider were on the network that pushed to it, the consumer's storage, so a provider network with no storage of its
 * own does not clog. A provider network with a CPU of its own keeps what its machines give back beyond any debt.
 */
@PrefixGameTestTemplate(false)
public final class StorelessProviderReturnGameTests {
    private StorelessProviderReturnGameTests() {
    }

    /**
     * The provider network has no storage. The machine gives back the stone the consumer ordered and two gravel no
     * pattern names: the stone reaches the consumer's job, the gravel the consumer's chest, and the provider keeps
     * nothing. Gravel inserted into the provider network by anything else than the provider still has nowhere to go.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 800)
    public static void storelessProviderByproduct(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper);
        var stage = new int[1];
        helper.succeedWhen(() -> {
            pushedOneStone(helper, fixture, stage);
            if (stage[0] == 4) {
                helper.assertValueEqual(fixture.providerGrid().getStorageService().getInventory().insert(gravel(), 1,
                        Actionable.SIMULATE, IActionSource.empty()), 0L,
                        "Gravel from no provider has nowhere to go on a network without storage");
                fixture.ejectIntoProvider(stone(), 1);
                fixture.ejectIntoProvider(gravel(), 2);
                stage[0] = 5;
            }
            helper.assertValueEqual(fixture.owed(stone()), 0L, "Waiting for the stone to reach the job");
            helper.assertValueEqual(fixture.busyConsumerCpus(), 0L, "Waiting for the consumer's job to finish");
            helper.assertValueEqual(fixture.held(fixture.consumerChest(), stone()), 1L,
                    "The consumer stores the stone it ordered");
            helper.assertValueEqual(fixture.inProviderReturns(gravel()), 0L,
                    "Waiting for the provider to give the gravel back");
            helper.assertValueEqual(fixture.held(fixture.consumerChest(), gravel()), 2L,
                    "The gravel goes to the consumer's chest");
            helper.assertValueEqual(fixture.inProviderReturns(stone()), 0L, "The provider keeps no stone");
        });
    }

    /**
     * The provider network has no storage, and the consumer cancels its job after the push. The stone the machine gives
     * back later goes to the consumer's chest, and the debt is settled.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 800)
    public static void storelessProviderLateOutput(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper);
        var stage = new int[1];
        helper.succeedWhen(() -> {
            pushedOneStone(helper, fixture, stage);
            if (stage[0] == 4) {
                fixture.cancelConsumerJob();
                stage[0] = 5;
            }
            if (stage[0] == 5) {
                helper.assertValueEqual(fixture.busyConsumerCpus(), 0L, "Waiting for the cancel");
                helper.assertValueEqual(fixture.owed(stone()), 1L, "Cancelling keeps the stone owed");
                fixture.ejectIntoProvider(stone(), 1);
                stage[0] = 6;
            }
            helper.assertValueEqual(fixture.inProviderReturns(stone()), 0L,
                    "Waiting for the provider to give the stone back");
            helper.assertValueEqual(fixture.held(fixture.consumerChest(), stone()), 1L,
                    "The late stone goes to the consumer's chest");
            helper.assertValueEqual(fixture.owed(stone()), 0L, "Delivering the late stone settles the debt");
        });
    }

    /**
     * Neither network can store anything when the machine gives back the stone and a gravel. AE2 counts the stone as
     * the job's output and ends the job, but the stone has nowhere to go, so it waits in the provider with the gravel,
     * still owed. Once the consumer's chest has its cell back, both go into it and the debt is settled.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 1000)
    public static void storelessByproductWaitsForStorage(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper);
        var stage = new int[1];
        var cell = new ItemStack[1];
        var sweeps = new long[1];
        helper.succeedWhen(() -> {
            pushedOneStone(helper, fixture, stage);
            if (stage[0] == 4) {
                cell[0] = fixture.takeConsumerCell();
                fixture.ejectIntoProvider(stone(), 1);
                fixture.ejectIntoProvider(gravel(), 1);
                sweeps[0] = fixture.ledgerSweeps();
                stage[0] = 5;
            }
            if (stage[0] == 5) {
                helper.assertTrue(fixture.ledgerSweeps() > sweeps[0] + 1, "Waiting for two ledger sweeps");
                helper.assertValueEqual(fixture.busyConsumerCpus(), 0L, "AE2 ends the job as its stone arrives");
                helper.assertValueEqual(fixture.inProviderReturns(stone()), 1L,
                        "The stone waits in the provider while no network can store it");
                helper.assertValueEqual(fixture.inProviderReturns(gravel()), 1L, "So does the gravel");
                helper.assertValueEqual(fixture.owed(stone()), 1L, "The stone stays owed");
                fixture.restoreConsumerCell(cell[0]);
                stage[0] = 6;
            }
            helper.assertValueEqual(fixture.inProviderReturns(stone()) + fixture.inProviderReturns(gravel()), 0L,
                    "Waiting for the provider to give both back");
            helper.assertValueEqual(fixture.held(fixture.consumerChest(), stone()), 1L,
                    "The stone goes to the consumer's chest once it has a cell");
            helper.assertValueEqual(fixture.held(fixture.consumerChest(), gravel()), 1L, "So does the gravel");
            helper.assertValueEqual(fixture.owed(stone()), 0L, "Delivering the stone settles the debt");
        });
    }

    /**
     * The provider network has storage and a CPU of its own, and both networks order one stone through the same
     * provider. What the machine gives back beyond the consumer's stone stays with the provider network: its job gets
     * its stone, and the gravel goes into its own chest. AE2 ends the provider network's job as its stone arrives and
     * lets the stone on into storage, so the router cannot tell it from a byproduct; having a CPU is what keeps both.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 1000)
    public static void providerOwnJobKeepsByproduct(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper);
        var stage = new int[1];
        @SuppressWarnings("unchecked")
        Future<ICraftingPlan>[] providerPlan = new Future[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for both networks and their providers");
            if (stage[0] == 0) {
                fixture.placeProviderCpu();
                fixture.setRule(fixture.crafting(), RuleMode.ENABLED);
                fixture.putInConsumer(cobblestone(), 1);
                fixture.putInProvider(cobblestone(), 1);
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertValueEqual(fixture.providerGrid().getCraftingService().getCpus().size(), 1,
                        "Waiting for the provider network's CPU");
                helper.assertTrue(fixture.consumerService().isCraftable(stone()), "Waiting for the projection");
                fixture.begin(stone(), 1);
                providerPlan[0] = fixture.planOnProvider(stone(), 1);
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                helper.assertTrue(fixture.planReady() && providerPlan[0].isDone(), "Waiting for both plans");
                helper.assertTrue(fixture.submit(), "The consumer's CPU must take its job");
                helper.assertTrue(fixture.submitOnProvider(providerPlan[0].resultNow()),
                        "The provider network's CPU must take its job");
                stage[0] = 3;
            }
            if (stage[0] == 3) {
                helper.assertValueEqual(fixture.inMachine(Items.COBBLESTONE), 2L, "Waiting for both pushes");
                helper.assertValueEqual(fixture.owed(stone()), 1L, "The provider network owes the consumer one stone");
                helper.assertValueEqual(fixture.busyProviderCpus(), 1L, "The provider network's job runs");
                fixture.ejectIntoProvider(stone(), 2);
                fixture.ejectIntoProvider(gravel(), 1);
                stage[0] = 4;
            }
            helper.assertValueEqual(fixture.inProviderReturns(gravel()), 0L, "Waiting for the provider to give back");
            helper.assertValueEqual(fixture.owed(stone()), 0L, "The consumer's job gets its stone");
            helper.assertValueEqual(fixture.busyConsumerCpus() + fixture.busyProviderCpus(), 0L,
                    "Waiting for both jobs to finish");
            helper.assertValueEqual(fixture.held(fixture.consumerChest(), stone()), 1L, "The consumer stores its stone");
            helper.assertValueEqual(fixture.held(fixture.providerChest(), stone()), 1L,
                    "The provider network stores its stone");
            helper.assertValueEqual(fixture.held(fixture.providerChest(), gravel()), 1L,
                    "The gravel stays with the provider network, whose own job was running");
            helper.assertValueEqual(fixture.held(fixture.consumerChest(), gravel()), 0L, "The consumer gets no gravel");
        });
    }

    /**
     * Two networks with CPUs (the chain fixture's consumer and source) order stone through the same provider of a third
     * network that has no CPU and no storage (the middle). The gravel the machine gives back beyond both stones goes to
     * the network that pushed to the provider last: the source, then, after the consumer orders again, the consumer.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 1200)
    public static void sharedProviderByproductToLastPusher(GameTestHelper helper) {
        var fixture = new ProjectionChainFixture(helper);
        var stage = new int[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for the three networks and their providers");
            if (stage[0] == 0) {
                fixture.placeMiddleMachine();
                fixture.takeMiddleCell();
                fixture.shareMiddle();
                fixture.putIn(CONSUMER, cobblestone(), 2);
                fixture.putIn(SOURCE, cobblestone(), 1);
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertTrue(fixture.middleMachineReady(), "Waiting for the middle's provider");
                helper.assertTrue(fixture.canCraft(CONSUMER, stone()) && fixture.canCraft(SOURCE, stone()),
                        "Waiting for the middle's provider on the consumer and the source");
                helper.assertValueEqual(fixture.cpuCount(MIDDLE), 0L, "The middle has no CPU");
                fixture.begin(CONSUMER, stone(), 1);
                stage[0] = 2;
            }
            if (stage[0] == 2 || stage[0] == 4 || stage[0] == 7) {
                helper.assertTrue(fixture.planReady(), "Waiting for the plan");
                helper.assertTrue(fixture.submit(), "The ordering network's own CPU must take the job");
                stage[0]++;
            }
            if (stage[0] == 3) {
                helper.assertValueEqual(fixture.inMiddleMachine(Items.COBBLESTONE), 1L, "Waiting for the consumer's push");
                fixture.begin(SOURCE, stone(), 1);
                stage[0] = 4;
            }
            if (stage[0] == 5) {
                helper.assertValueEqual(fixture.inMiddleMachine(Items.COBBLESTONE), 2L, "Waiting for the source's push");
                helper.assertValueEqual(fixture.owed(MIDDLE, CONSUMER, stone()), 1L, "The middle owes the consumer");
                helper.assertValueEqual(fixture.owed(MIDDLE, SOURCE, stone()), 1L, "The middle owes the source");
                fixture.ejectIntoMiddle(gravel(), 1);
                fixture.ejectIntoMiddle(stone(), 2);
                stage[0] = 6;
            }
            if (stage[0] == 6) {
                assertGivenBack(helper, fixture);
                helper.assertValueEqual(fixture.amount(SOURCE, gravel()), 1L,
                        "The gravel goes to the source, which pushed last");
                helper.assertValueEqual(fixture.amount(CONSUMER, gravel()), 0L, "The consumer gets no gravel yet");
                helper.assertValueEqual(fixture.amount(CONSUMER, stone()), 1L, "The consumer stores its stone");
                helper.assertValueEqual(fixture.amount(SOURCE, stone()), 1L, "The source stores its stone");
                fixture.begin(CONSUMER, stone(), 1);
                stage[0] = 7;
            }
            if (stage[0] == 8) {
                helper.assertValueEqual(fixture.inMiddleMachine(Items.COBBLESTONE), 1L,
                        "Waiting for the consumer's second push");
                fixture.ejectIntoMiddle(gravel(), 1);
                fixture.ejectIntoMiddle(stone(), 1);
                stage[0] = 9;
            }
            helper.assertValueEqual(stage[0], 9, "Waiting for the consumer's second order");
            assertGivenBack(helper, fixture);
            helper.assertValueEqual(fixture.amount(CONSUMER, gravel()), 1L,
                    "The second gravel goes to the consumer, which pushed last now");
            helper.assertValueEqual(fixture.amount(SOURCE, gravel()), 1L, "The source keeps only the first gravel");
            helper.assertValueEqual(fixture.amount(CONSUMER, stone()), 2L, "The consumer stores its second stone");
            fixture.close();
        });
    }

    /** Waits until the middle's provider gave everything back and both ordering networks' jobs finished. */
    private static void assertGivenBack(GameTestHelper helper, ProjectionChainFixture fixture) {
        helper.assertValueEqual(fixture.inMiddleReturns(gravel()) + fixture.inMiddleReturns(stone()), 0L,
                "Waiting for the middle's provider to give back");
        helper.assertValueEqual(fixture.busyCpus(CONSUMER) + fixture.busyCpus(SOURCE), 0L,
                "Waiting for the jobs to finish");
        helper.assertValueEqual(fixture.owed(MIDDLE, CONSUMER, stone()) + fixture.owed(MIDDLE, SOURCE, stone()), 0L,
                "Every stone was handed back");
    }

    /**
     * Readies both networks, takes the provider chest's cell out and turns on only the crafting rule, then has the
     * consumer order one stone and waits until the provider pushed its cobblestone into the machine.
     */
    private static void pushedOneStone(GameTestHelper helper, PatternProjectionFixture fixture, int[] stage) {
        if (stage[0] >= 4) return;
        helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for both networks and their providers");
        if (stage[0] == 0) {
            fixture.takeProviderCell();
            fixture.setRule(fixture.crafting(), RuleMode.ENABLED);
            fixture.putInConsumer(cobblestone(), 1);
            stage[0] = 1;
        }
        if (stage[0] == 1) {
            helper.assertTrue(fixture.consumerService().isCraftable(stone()), "Waiting for the projection");
            fixture.begin(stone(), 1);
            stage[0] = 2;
        }
        if (stage[0] == 2) {
            helper.assertTrue(fixture.planReady(), "Waiting for the consumer's plan");
            helper.assertTrue(fixture.submit(), "The consumer's own CPU must take the job");
            stage[0] = 3;
        }
        helper.assertValueEqual(fixture.inMachine(Items.COBBLESTONE), 1L, "Waiting for the push");
        helper.assertValueEqual(fixture.owed(stone()), 1L, "The provider network owes the consumer one stone");
        stage[0] = 4;
    }

    private static AEItemKey gravel() {
        return AEItemKey.of(Items.GRAVEL);
    }
}
