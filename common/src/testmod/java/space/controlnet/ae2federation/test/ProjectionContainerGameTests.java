package space.controlnet.ae2federation.test;

import static space.controlnet.ae2federation.test.crafting.PatternProjectionFixture.bucket;
import static space.controlnet.ae2federation.test.crafting.PatternProjectionFixture.fluixGlassCable;
import static space.controlnet.ae2federation.test.crafting.PatternProjectionFixture.glassBottle;
import static space.controlnet.ae2federation.test.crafting.PatternProjectionFixture.honeyBottle;
import static space.controlnet.ae2federation.test.crafting.PatternProjectionFixture.sugar;
import static space.controlnet.ae2federation.test.crafting.PatternProjectionFixture.whiteGlassCable;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.test.crafting.PatternProjectionFixture;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;

/**
 * Container items through a projection: what a recipe's inputs leave behind (a honey bottle's glass bottle, a water
 * bucket's bucket) is waited for by the consumer's CPU like an output, so the provider network must hand it back. The
 * provider's Molecular Assembler empties the inputs it is pushed, so what is owed must be known before the push.
 */
@PrefixGameTestTemplate(false)
public final class ProjectionContainerGameTests {
    private ProjectionContainerGameTests() {
    }

    /**
     * Nine sugar from three honey bottles: three pushes to the provider network's Molecular Assembler, each leaving a
     * glass bottle that the consumer's CPU waits for. The job finishes only if all three bottles come back.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 1200)
    public static void projectionContainerReturn(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper);
        var stage = new int[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for both networks and their providers");
            if (stage[0] == 0) {
                fixture.installContainerPatterns();
                fixture.enableRules();
                fixture.putInConsumer(honeyBottle(), 3);
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertTrue(fixture.consumerService().isCraftable(sugar()), "Waiting for the sugar pattern");
                fixture.begin(sugar(), 9);
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                helper.assertTrue(fixture.planReady(), "Waiting for the consumer's plan");
                helper.assertFalse(fixture.plan().simulation(), "Three honey bottles make nine sugar");
                helper.assertTrue(fixture.submit(), "The consumer's own CPU must take the job");
                stage[0] = 3;
            }
            helper.assertValueEqual(fixture.busyConsumerCpus(), 0L,
                    "Waiting for the job, which waits for its three glass bottles: owed "
                            + fixture.owed(glassBottle()) + ", on the provider network "
                            + fixture.onProviderNetwork(glassBottle()));
            helper.assertTrue(fixture.consumerCpuEmpty(), "The finished CPU holds nothing");
            helper.assertValueEqual(fixture.held(fixture.consumerChest(), sugar()), 9L, "Nine sugar reach the consumer");
            helper.assertValueEqual(fixture.held(fixture.consumerChest(), glassBottle()), 3L,
                    "The three glass bottles reach the consumer; " + fixture.returns(glassBottle()));
            helper.assertValueEqual(fixture.held(fixture.consumerChest(), honeyBottle()), 0L, "The honey was used");
            for (var key : new AEKey[] {sugar(), glassBottle(), honeyBottle()}) {
                helper.assertValueEqual(fixture.held(fixture.providerChest(), key), 0L,
                        "The provider network keeps no " + key);
                helper.assertValueEqual(fixture.owed(key), 0L, "Nothing more is owed of " + key);
            }
            PolicyEvidence.write("projectioncontainerreturn", 13, Map.of("pushes", "3", "sugar", "9",
                    "glassBottlesReturned", "3", "cpuEmpty", "true", "owedAfter", "0"));
            fixture.close();
        });
    }

    /**
     * Six sugar from one glass bottle: the bottle is filled on the provider network's bottling machine, made into sugar
     * by its Molecular Assembler, and must come back to the consumer's CPU to be filled again for the second batch.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 1600)
    public static void projectionContainerReuse(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper);
        var stage = new int[1];
        var filled = new long[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for both networks and their providers");
            if (stage[0] == 0) {
                fixture.installContainerPatterns();
                fixture.enableRules();
                fixture.putInConsumer(glassBottle(), 1);
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertTrue(fixture.consumerService().isCraftable(sugar())
                        && fixture.consumerService().isCraftable(honeyBottle()), "Waiting for both patterns");
                fixture.begin(sugar(), 6);
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                helper.assertTrue(fixture.planReady(), "Waiting for the consumer's plan");
                helper.assertFalse(fixture.plan().simulation(), "One glass bottle, filled twice, makes six sugar");
                helper.assertTrue(fixture.submit(), "The consumer's own CPU must take the job");
                stage[0] = 3;
            }
            filled[0] += fixture.runBottleMachine();
            helper.assertValueEqual(fixture.busyConsumerCpus(), 0L,
                    "Waiting for the job; bottles filled " + filled[0] + ", glass bottles owed "
                            + fixture.owed(glassBottle()) + ", on the provider network "
                            + fixture.onProviderNetwork(glassBottle()));
            helper.assertValueEqual(filled[0], 2L, "The one bottle was filled twice");
            helper.assertTrue(fixture.consumerCpuEmpty(), "The finished CPU holds nothing");
            helper.assertValueEqual(fixture.held(fixture.consumerChest(), sugar()), 6L, "Six sugar reach the consumer");
            helper.assertValueEqual(fixture.held(fixture.consumerChest(), glassBottle()), 1L,
                    "The one glass bottle is back on the consumer; " + fixture.returns(glassBottle()));
            for (var key : new AEKey[] {sugar(), glassBottle(), honeyBottle()}) {
                helper.assertValueEqual(fixture.held(fixture.providerChest(), key), 0L,
                        "The provider network keeps no " + key);
                helper.assertValueEqual(fixture.owed(key), 0L, "Nothing more is owed of " + key);
            }
            PolicyEvidence.write("projectioncontainerreuse", 12, Map.of("bottlesFilled", "2", "sugar", "6",
                    "glassBottleBack", "1", "cpuEmpty", "true", "owedAfter", "0"));
            fixture.close();
        });
    }

    /**
     * Pushes through the projection by hand, with inputs AE2's own CPU helper extracts, and compares what the ledger
     * then owes with the container items AE2 itself expects from the same inputs: water as a fluid leaves no bucket,
     * a water bucket leaves one, a snowball in its place none; a push the busy assembler refuses owes nothing.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 1200)
    public static void projectionContainerPush(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper);
        var stage = new int[1];
        var context = new PushContext();
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for both networks and their providers");
            if (stage[0] == 0) {
                fixture.installContainerPatterns();
                fixture.enableRules();
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertTrue(fixture.consumerService().isCraftable(fluixGlassCable()),
                        "Waiting for the cable pattern on the consumer");
                // Each push happens once: what it shows is recorded and asserted at the end, so a failed check cannot
                // make a later tick push again.
                stage[0] = 2;
                context.details = PatternDetailsHelper.decodePattern(fixture.cleanCablePattern(), helper.getLevel());
                var providers = fixture.consumerProviders(context.details);
                context.check(providers.size() == 1, "One projected provider offers the cable pattern: " + providers.size());
                context.projection = providers.get(0);
                // Water as a fluid: AE2 expects no bucket back, and none may be owed.
                context.push(helper, fixture, AEFluidKey.of(Fluids.WATER), 1000, true, 0);
                // The assembler is busy now: the next push is refused and owes nothing.
                context.push(helper, fixture, PatternProjectionFixture.waterBucket(), 1, false, 1);
            }
            if (stage[0] == 2) {
                helper.assertValueEqual(fixture.onProviderNetwork(fluixGlassCable()), 1L,
                        "Waiting for the first cable to come out of the assembler; " + context.problems);
                stage[0] = 3;
                context.check(fixture.onProviderNetwork(bucket()) == 0, "Water as a fluid leaves no bucket");
                context.push(helper, fixture, PatternProjectionFixture.waterBucket(), 1, true, 1);
            }
            if (stage[0] == 3) {
                helper.assertValueEqual(fixture.onProviderNetwork(fluixGlassCable()), 2L,
                        "Waiting for the second cable to come out of the assembler; " + context.problems);
                helper.assertValueEqual(fixture.onProviderNetwork(bucket()), 1L,
                        "Waiting for the water bucket's bucket to come out of the assembler; " + context.problems);
                stage[0] = 4;
                context.push(helper, fixture, PatternProjectionFixture.snowball(), 1, true, 0);
            }
            helper.assertValueEqual(fixture.onProviderNetwork(fluixGlassCable()), 3L,
                    "Waiting for the third cable to come out of the assembler; " + context.problems);
            helper.assertTrue(context.problems.isEmpty(), "Every push must match AE2: " + context.problems);
            helper.assertValueEqual(fixture.onProviderNetwork(bucket()), 1L, "Only the water bucket left a bucket");
            helper.assertValueEqual(context.owedBuckets, 1L, "One bucket was owed in all");
            helper.assertValueEqual(context.owedCables, 3L, "Three cables were owed in all, one per accepted push");
            PolicyEvidence.write("projectioncontainerpush", 25, Map.of("fluidBuckets", "0", "bucketBuckets", "1",
                    "snowballBuckets", "0", "refusedPushOwed", "0", "nativeParity", "true"));
            fixture.close();
        });
    }

    /** One projected provider, what the ledger was seen to owe from the pushes so far, and what did not match. */
    private static final class PushContext {
        private final java.util.List<String> problems = new java.util.ArrayList<>();
        private IPatternDetails details;
        private ICraftingProvider projection;
        private long owedBuckets;
        private long owedCables;

        private void check(boolean condition, String problem) {
            if (!condition) problems.add(problem);
        }

        /**
         * Extracts the cable pattern's inputs with AE2's own CPU helper from an inventory of one white glass cable and
         * {@code remover}, pushes them through the projection, and compares the ledger with AE2's expectation.
         */
        private void push(GameTestHelper helper, PatternProjectionFixture fixture, AEKey remover, long removers,
                boolean accepted, long nativeBuckets) {
            var inventory = new ListCraftingInventory(key -> {
            });
            inventory.insert(whiteGlassCable(), 1, Actionable.MODULATE);
            inventory.insert(remover, removers, Actionable.MODULATE);
            var outputs = new KeyCounter();
            var containers = new KeyCounter();
            var inputs = CraftingCpuHelper.extractPatternInputs(details, inventory, helper.getLevel(), outputs,
                    containers);
            if (inputs == null) {
                problems.add("AE2 could not extract the cable pattern's inputs from " + remover);
                return;
            }
            check(containers.get(bucket()) == nativeBuckets, "AE2 itself expects " + containers.get(bucket())
                    + " buckets back from " + remover + ", not " + nativeBuckets);
            long bucketsBefore = fixture.owed(bucket());
            long cablesBefore = fixture.owed(fluixGlassCable());
            boolean pushed = projection.pushPattern(details, inputs);
            check(pushed == accepted, "The push of " + remover + " must be " + (accepted ? "accepted" : "refused"));
            long buckets = fixture.owed(bucket()) - bucketsBefore;
            long cables = fixture.owed(fluixGlassCable()) - cablesBefore;
            check(buckets == (pushed ? containers.get(bucket()) : 0), "The ledger owes " + buckets
                    + " buckets for " + remover + "; AE2 expects " + containers.get(bucket()) + ", pushed " + pushed);
            check(cables == (pushed ? 1 : 0), "The ledger owes " + cables + " cables for " + remover + ", pushed "
                    + pushed);
            owedBuckets += buckets;
            owedCables += cables;
        }
    }
}
