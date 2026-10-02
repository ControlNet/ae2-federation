package space.controlnet.ae2federation.test;

import static space.controlnet.ae2federation.test.crafting.PatternProjectionFixture.cobblestone;
import static space.controlnet.ae2federation.test.crafting.PatternProjectionFixture.planks;
import static space.controlnet.ae2federation.test.crafting.PatternProjectionFixture.sticks;
import static space.controlnet.ae2federation.test.crafting.PatternProjectionFixture.stone;

import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.policy.BindingDiagnostic;
import space.controlnet.ae2federation.policy.RuleMode;
import space.controlnet.ae2federation.test.crafting.PatternProjectionFixture;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;

/**
 * Cross-network crafting by pattern projection: the consumer's own CPU runs the job with what its storage shows, and
 * pushes to the provider network's pattern providers; the provider network has no CPU.
 */
@PrefixGameTestTemplate(false)
public final class PatternProjectionGameTests {
    private PatternProjectionGameTests() {
    }

    /** Sticks from planks on the consumer, crafted by the provider network's Molecular Assembler. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 600)
    public static void projectionRequest(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper);
        var stage = new int[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for both networks and their providers");
            if (stage[0] == 0) {
                helper.assertFalse(fixture.consumerService().isCraftable(sticks()),
                        "Without a crafting rule the consumer must not see the provider's patterns");
                fixture.enableRules();
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertTrue(fixture.consumerService().isCraftable(sticks()),
                        "Waiting for the provider's patterns on the consumer");
                helper.assertValueEqual(fixture.projections(), 2, "Both of the provider's pattern providers are projected");
                helper.assertTrue(fixture.status().map(status -> status.active() && status.patterns() == 2).orElse(false),
                        "The rule must report two projected patterns");
                helper.assertValueEqual(fixture.providerGrid().getCraftingService().getCpus().size(), 0,
                        "The provider network has no CPU");
                fixture.putInConsumer(planks(), 2);
                fixture.begin(sticks(), 4);
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                helper.assertTrue(fixture.planReady(), "Waiting for the consumer's plan");
                helper.assertFalse(fixture.plan().simulation(), "The consumer's own planks must be enough");
                helper.assertTrue(fixture.submit(), "The consumer's own CPU must take the job");
                stage[0] = 3;
            }
            helper.assertValueEqual(fixture.busyConsumerCpus(), 0L, "Waiting for the consumer's job to finish");
            long sticksMade = fixture.held(fixture.consumerChest(), sticks()) + fixture.held(fixture.providerChest(), sticks());
            helper.assertValueEqual(sticksMade, 4L, "The job must store exactly four sticks");
            helper.assertValueEqual(fixture.held(fixture.consumerChest(), planks()), 0L, "The consumer's planks were used");
            helper.assertValueEqual(fixture.held(fixture.providerChest(), planks()), 0L,
                    "The provider's storage was not touched for materials");
            helper.assertValueEqual(fixture.owed(sticks()), 0L, "Every returned stick was handed back");
            PolicyEvidence.write("projectionrequest", 9, Map.of("consumerCpuRan", "true", "providerCpus", "0",
                    "projectedProviders", "2", "sticks", "4", "consumerMaterialsUsed", "true"));
            fixture.close();
        });
    }

    /** The materials are only on the provider network; the consumer's CPU takes them through the storage rule. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 600)
    public static void projectionRemoteMaterials(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper);
        var stage = new int[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for both networks and their providers");
            if (stage[0] == 0) {
                fixture.putInProvider(planks(), 2);
                fixture.enableRules();
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertTrue(fixture.consumerService().isCraftable(sticks()), "Waiting for the projection");
                fixture.begin(sticks(), 4);
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                helper.assertTrue(fixture.planReady(), "Waiting for the consumer's plan");
                helper.assertFalse(fixture.plan().simulation(), "The provider's planks are visible to the consumer");
                helper.assertTrue(fixture.submit(), "The consumer's own CPU must take the job");
                stage[0] = 3;
            }
            helper.assertValueEqual(fixture.busyConsumerCpus(), 0L, "Waiting for the consumer's job to finish");
            helper.assertValueEqual(fixture.held(fixture.providerChest(), planks()), 0L,
                    "The provider's planks were used");
            long sticksMade = fixture.held(fixture.consumerChest(), sticks()) + fixture.held(fixture.providerChest(), sticks());
            helper.assertValueEqual(sticksMade, 4L, "The job must store exactly four sticks");
            PolicyEvidence.write("projectionremotematerials", 4, Map.of("providerMaterialsUsed", "true",
                    "sticks", "4"));
            fixture.close();
        });
    }

    /**
     * A machine the test runs by hand returns its outputs late and with one too many: the consumer's CPU gets what
     * it waits for, the surplus stays on the provider network, and nothing is owed afterwards.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 600)
    public static void projectionManualReturn(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper);
        var stage = new int[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for both networks and their providers");
            if (stage[0] == 0) {
                fixture.enableRules();
                fixture.putInConsumer(cobblestone(), 2);
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertTrue(fixture.consumerService().isCraftable(stone()), "Waiting for the projection");
                fixture.begin(stone(), 2);
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                helper.assertTrue(fixture.planReady(), "Waiting for the consumer's plan");
                helper.assertTrue(fixture.submit(), "The consumer's own CPU must take the job");
                stage[0] = 3;
            }
            if (stage[0] == 3) {
                helper.assertValueEqual(fixture.inMachine(Items.COBBLESTONE), 2L, "Waiting for both pushes");
                helper.assertValueEqual(fixture.owed(stone()), 2L, "The provider network owes the consumer two stone");
                helper.assertTrue(fixture.providerRouted(), "The provider network routes returns");
                helper.assertValueEqual(fixture.runMachine(3), 3L, "The provider network takes all three stone");
                stage[0] = 4;
            }
            helper.assertValueEqual(fixture.busyConsumerCpus(), 0L, "Waiting for the consumer's job to finish");
            helper.assertValueEqual(fixture.owed(stone()), 0L, "Nothing is owed once the job has its stone");
            long stored = fixture.held(fixture.consumerChest(), stone()) + fixture.held(fixture.providerChest(), stone());
            helper.assertValueEqual(stored, 3L, "The job's two stone and the surplus one are all stored");
            helper.assertTrue(fixture.held(fixture.providerChest(), stone()) >= 1,
                    "The surplus stone stays on the provider network");
            PolicyEvidence.write("projectionmanualreturn", 8, Map.of("pushes", "2", "owedBeforeReturn", "2",
                    "returned", "3", "surplusStaysOnProvider", "true", "owedAfter", "0"));
            fixture.close();
        });
    }

    /**
     * Brings both networks up, turns the rules on and runs a two-stone job on the consumer until both pushes sit in
     * the hand-run machine; true from then on.
     */
    private static boolean pushedTwoStone(GameTestHelper helper, PatternProjectionFixture fixture, int[] stage) {
        if (stage[0] >= 4) return true;
        helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for both networks and their providers");
        if (stage[0] == 0) {
            fixture.enableRules();
            fixture.putInConsumer(cobblestone(), 2);
            stage[0] = 1;
        }
        if (stage[0] == 1) {
            helper.assertTrue(fixture.consumerService().isCraftable(stone()), "Waiting for the projection");
            fixture.begin(stone(), 2);
            stage[0] = 2;
        }
        if (stage[0] == 2) {
            helper.assertTrue(fixture.planReady(), "Waiting for the consumer's plan");
            helper.assertTrue(fixture.submit(), "The consumer's own CPU must take the job");
            stage[0] = 3;
        }
        helper.assertValueEqual(fixture.inMachine(Items.COBBLESTONE), 2L, "Waiting for both pushes");
        helper.assertValueEqual(fixture.owed(stone()), 2L, "The provider network owes the consumer two stone");
        stage[0] = 4;
        return true;
    }

    /** A cancelled job forgets what it was owed: outputs that arrive later stay on the provider network. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 800)
    public static void projectionCancel(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper);
        var stage = new int[1];
        helper.succeedWhen(() -> {
            pushedTwoStone(helper, fixture, stage);
            if (stage[0] == 4) {
                fixture.cancelConsumerJob();
                stage[0] = 5;
            }
            if (stage[0] == 5) {
                helper.assertValueEqual(fixture.busyConsumerCpus(), 0L, "The consumer's job must be cancelled");
                helper.assertValueEqual(fixture.owed(stone()), 0L, "Waiting for the cancelled job's debt to be forgotten");
                helper.assertValueEqual(fixture.runMachine(2), 2L, "The provider network takes the late stone");
                stage[0] = 6;
            }
            helper.assertValueEqual(fixture.held(fixture.providerChest(), stone()), 2L,
                    "The late stone stays on the provider network");
            helper.assertValueEqual(fixture.held(fixture.consumerChest(), stone()), 0L,
                    "Nothing reaches the cancelled consumer");
            PolicyEvidence.write("projectioncancel", 7, Map.of("cancelled", "true", "owedAfterCancel", "0",
                    "lateOutputOnProvider", "2"));
            fixture.close();
        });
    }

    /**
     * Switching the crafting rule off after the push withdraws the patterns but not the debt: the outputs still reach
     * the job that pushed them, and nothing new can be pushed.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 800)
    public static void projectionRevoked(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper);
        var stage = new int[1];
        helper.succeedWhen(() -> {
            pushedTwoStone(helper, fixture, stage);
            if (stage[0] == 4) {
                fixture.setRule(fixture.crafting(), RuleMode.DISABLED);
                stage[0] = 5;
            }
            if (stage[0] == 5) {
                helper.assertValueEqual(fixture.projections(), 0, "Waiting for the patterns to be withdrawn");
                helper.assertFalse(fixture.consumerService().isCraftable(stone()), "The consumer can no longer plan it");
                helper.assertTrue(fixture.providerRouted(), "The provider network still routes what it owes");
                helper.assertValueEqual(fixture.runMachine(2), 2L, "The provider network takes the stone");
                stage[0] = 6;
            }
            helper.assertValueEqual(fixture.busyConsumerCpus(), 0L, "Waiting for the consumer's job to finish");
            helper.assertValueEqual(fixture.owed(stone()), 0L, "Nothing is owed once the job has its stone");
            helper.assertValueEqual(fixture.held(fixture.providerChest(), stone())
                    + fixture.held(fixture.consumerChest(), stone()), 2L, "The job stores its two stone");
            PolicyEvidence.write("projectionrevoked", 8, Map.of("patternsWithdrawn", "true", "routedAfterRevoke", "true",
                    "jobFinished", "true"));
            fixture.close();
        });
    }

    /** A reload between the push and the return: the saved debt and the reloaded job meet again. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 1000)
    public static void projectionReload(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper);
        var stage = new int[1];
        helper.succeedWhen(() -> {
            pushedTwoStone(helper, fixture, stage);
            if (stage[0] == 4) {
                fixture.reloadAll();
                stage[0] = 5;
            }
            if (stage[0] == 5) {
                helper.assertTrue(fixture.reloaded(), "Waiting for both networks and the consumer's job to reload");
                helper.assertTrue(fixture.providerRouted(), "Waiting for the reloaded provider network's router");
                helper.assertValueEqual(fixture.owed(stone()), 2L, "The debt survives the reload");
                helper.assertValueEqual(fixture.runMachine(2), 2L, "The reloaded provider network takes the stone");
                stage[0] = 6;
            }
            helper.assertValueEqual(fixture.busyConsumerCpus(), 0L, "Waiting for the reloaded job to finish");
            helper.assertValueEqual(fixture.owed(stone()), 0L, "Nothing is owed once the job has its stone");
            helper.assertValueEqual(fixture.held(fixture.providerChest(), stone())
                    + fixture.held(fixture.consumerChest(), stone()), 2L, "The job stores its two stone");
            PolicyEvidence.write("projectionreload", 8, Map.of("debtSurvivesReload", "true", "routedAfterReload", "true",
                    "jobFinished", "true"));
            fixture.close();
        });
    }

    /** A crafting rule without its storage rule, set through the API: nothing is projected, and the rule says why. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 400)
    public static void projectionStorageRequired(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper);
        var stage = new int[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for both networks and their providers");
            if (stage[0] == 0) {
                fixture.setRule(fixture.crafting(), RuleMode.ENABLED);
                stage[0] = 1;
            }
            helper.assertTrue(fixture.status().flatMap(status -> status.reason())
                    .filter(BindingDiagnostic.Reason.CRAFTING_STORAGE_REQUIRED::equals).isPresent(),
                    "Waiting for the rule to report the missing storage rule");
            helper.assertValueEqual(fixture.projections(), 0, "Nothing is projected without the storage rule");
            helper.assertFalse(fixture.consumerService().isCraftable(sticks()),
                    "The consumer must not see the provider's patterns");
            PolicyEvidence.write("projectionstoragerequired", 3, Map.of("reason", "CRAFTING_STORAGE_REQUIRED",
                    "projections", "0"));
            fixture.close();
        });
    }
}
