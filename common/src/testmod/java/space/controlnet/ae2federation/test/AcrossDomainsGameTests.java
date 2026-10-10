package space.controlnet.ae2federation.test;

import static space.controlnet.ae2federation.test.crafting.BridgeChainFixture.A;
import static space.controlnet.ae2federation.test.crafting.BridgeChainFixture.B;
import static space.controlnet.ae2federation.test.crafting.BridgeChainFixture.C;

import appeng.api.stacks.AEItemKey;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.policy.RuleMode;
import space.controlnet.ae2federation.test.crafting.BridgeChainFixture;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;

/**
 * The guide's "Across Domains" page on its own build: networks A, B and C joined by two Bridges, so A and C share no
 * domain and no rule. "A uses B's" is set in the A-B domain, "B uses C's" in the B-C domain, and re-export on the B-C
 * rule decides whether A reaches C.
 */
@PrefixGameTestTemplate(false)
public final class AcrossDomainsGameTests {
    private static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);
    private static final AEItemKey GOLD = AEItemKey.of(Items.GOLD_INGOT);

    private AcrossDomainsGameTests() {
    }

    /**
     * A sees, takes from and can store into C's storage only while "B uses C's" Storage re-exports; stepping it back to
     * Enabled, the page's "Try it", takes C's items off A again while B keeps them.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", required = true,
            timeoutTicks = 600)
    public static void acrossDomainsStorage(GameTestHelper helper) {
        var fixture = new BridgeChainFixture(helper);
        var stage = new int[1];
        var roomWithoutC = new long[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for the three networks and two Bridges: "
                    + fixture.readiness());
            if (stage[0] == 0) {
                fixture.storage(A, B, RuleMode.ENABLED);
                fixture.storage(B, C, RuleMode.ENABLED);
                fixture.putIn(C, IRON, 5);
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertValueEqual(fixture.visible(B, IRON), 5L, "Waiting for C's iron on B");
                helper.assertValueEqual(fixture.visible(A, IRON), 0L, "Without re-export A must not see C's iron");
                roomWithoutC[0] = fixture.room(A, GOLD);
                fixture.storage(B, C, RuleMode.REEXPORT);
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                helper.assertValueEqual(fixture.visible(A, IRON), 5L, "Waiting for C's iron on A through re-export");
                helper.assertValueEqual(fixture.room(A, GOLD), roomWithoutC[0] + fixture.ownRoom(C, GOLD),
                        "A can store into C's storage too");
                helper.assertValueEqual(fixture.extract(A, IRON, 2), 2L, "A takes C's iron out");
                helper.assertValueEqual(fixture.amount(C, IRON), 3L, "The iron A took came out of C's chest");
                fixture.storage(B, C, RuleMode.ENABLED);
                stage[0] = 3;
            }
            helper.assertValueEqual(fixture.visible(A, IRON), 0L, "Waiting for C's iron to leave A without re-export");
            helper.assertValueEqual(fixture.visible(B, IRON), 3L, "B still sees C's iron");
            PolicyEvidence.write("acrossdomainsstorage", 8, Map.of("domains", "2", "visibleWithoutReexport", "0",
                    "visibleWithReexport", "5", "extractedThroughB", "2", "visibleAfterReexportOff", "0",
                    "middleKeepsAccess", "true", "onePowerSource", "true"));
            fixture.close();
        });
    }

    /**
     * A orders planks from C's AE2 pattern provider only while "B uses C's" Crafting re-exports. A's own CPU runs the
     * job; B has no CPU and routes nothing. C's Storage is not re-exported, so A still cannot see C's items.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", required = true,
            timeoutTicks = 1000)
    public static void acrossDomainsCrafting(GameTestHelper helper) {
        var fixture = new BridgeChainFixture(helper);
        var stage = new int[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for the three networks and two Bridges: "
                    + fixture.readiness());
            if (stage[0] == 0) {
                fixture.crafting(A, B, RuleMode.ENABLED);
                fixture.crafting(B, C, RuleMode.ENABLED);
                fixture.putIn(C, IRON, 5);
                fixture.putIn(A, BridgeChainFixture.log(), 1);
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertValueEqual(fixture.projections(B, C), 1, "Waiting for C's provider on B");
                helper.assertTrue(fixture.canCraft(B, BridgeChainFixture.planks()), "B can plan C's planks");
                helper.assertValueEqual(fixture.projections(A, C), 0, "Without re-export C is not projected onto A");
                helper.assertFalse(fixture.canCraft(A, BridgeChainFixture.planks()), "A cannot plan C's planks");
                fixture.crafting(B, C, RuleMode.REEXPORT);
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                helper.assertTrue(fixture.canCraft(A, BridgeChainFixture.planks()),
                        "Waiting for C's planks on A through re-export");
                helper.assertValueEqual(fixture.projections(A, C), 1, "C's provider is projected onto A");
                helper.assertValueEqual(fixture.visible(A, IRON), 0L,
                        "Crafting re-export alone must not show C's storage on A");
                helper.assertValueEqual(fixture.cpuCount(B), 0L, "B has no CPU");
                fixture.beginOnA(BridgeChainFixture.planks(), 4);
                stage[0] = 3;
            }
            if (stage[0] == 3) {
                helper.assertTrue(fixture.planReady(), "Waiting for A's plan");
                helper.assertFalse(fixture.plan().simulation(), "A's own log must be enough");
                helper.assertTrue(fixture.submitOnA(), "A's own CPU must take the job");
                stage[0] = 4;
            }
            helper.assertValueEqual(fixture.busyCpus(A), 0L, "Waiting for A's job to finish");
            helper.assertValueEqual(fixture.total(BridgeChainFixture.planks()), 4L,
                    "The job must store exactly four planks");
            helper.assertValueEqual(fixture.amount(A, BridgeChainFixture.log()), 0L, "A's log was used");
            helper.assertFalse(fixture.routes(B), "Nothing is routed through B");
            PolicyEvidence.write("acrossdomainscrafting", 14, Map.of("domains", "2", "reachedWithoutReexport", "false",
                    "reachedWithReexport", "true", "storageSeenWithoutStorageReexport", "false", "middleCpus", "0",
                    "middleRoutes", "false", "planks", "4"));
            fixture.close();
        });
    }

    /**
     * The guide's "A Factory That Takes Orders": A is the customer, B the counter and C the factory. "B uses C's"
     * Crafting re-exports while its Storage is plainly Enabled, and "A uses B's" Crafting is on, its Storage with it. A
     * sees the counter's goods but not the factory's private stock, and its own CPU orders the factory's planks with A's
     * own log. The page's "Try it" steps "B uses C's" Storage to re-export, which shows C's stock on A, and back, which
     * hides it again while A keeps ordering.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", required = true,
            timeoutTicks = 1600)
    public static void orderDesk(GameTestHelper helper) {
        var fixture = new BridgeChainFixture(helper);
        var stage = new int[1];
        var planks = BridgeChainFixture.planks();
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for the three networks and two Bridges: "
                    + fixture.readiness());
            if (stage[0] == 0) {
                fixture.crafting(A, B, RuleMode.ENABLED);
                fixture.crafting(B, C, RuleMode.REEXPORT);
                fixture.putIn(B, GOLD, 3);
                fixture.putIn(C, IRON, 5);
                fixture.putIn(A, BridgeChainFixture.log(), 2);
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertTrue(fixture.canCraft(A, planks), "Waiting for the factory's planks on the customer");
                helper.assertValueEqual(fixture.visible(A, GOLD), 3L, "The customer sees the counter's goods");
                helper.assertValueEqual(fixture.visible(B, IRON), 5L, "The counter sees the factory's stock");
                helper.assertValueEqual(fixture.visible(A, IRON), 0L,
                        "The customer must not see the factory's private stock");
                helper.assertValueEqual(fixture.projections(A, C), 1, "The factory's provider is projected onto A");
                fixture.beginOnA(planks, 4);
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                helper.assertTrue(fixture.planReady(), "Waiting for the customer's plan");
                helper.assertFalse(fixture.plan().simulation(), "The customer's own log must be enough");
                helper.assertTrue(fixture.submitOnA(), "The customer's own CPU must take the job");
                stage[0] = 3;
            }
            if (stage[0] == 3) {
                helper.assertValueEqual(fixture.busyCpus(A), 0L, "Waiting for the customer's job to finish");
                helper.assertValueEqual(fixture.visible(A, planks), 4L, "The four planks reach the customer");
                helper.assertValueEqual(fixture.amount(C, planks), 0L, "No planks stay in the factory's drive");
                helper.assertValueEqual(fixture.amount(A, BridgeChainFixture.log()), 1L,
                        "The customer paid with one of its own logs");
                helper.assertValueEqual(fixture.amount(C, IRON), 5L, "The factory's stock is untouched");
                // Try it: the counter passes the factory's storage on as well.
                fixture.storage(B, C, RuleMode.REEXPORT);
                stage[0] = 4;
            }
            if (stage[0] == 4) {
                helper.assertValueEqual(fixture.visible(A, IRON), 5L,
                        "Waiting for the factory's stock on the customer through Storage re-export");
                fixture.storage(B, C, RuleMode.ENABLED);
                stage[0] = 5;
            }
            if (stage[0] == 5) {
                helper.assertValueEqual(fixture.visible(A, IRON), 0L,
                        "Waiting for the factory's stock to leave the customer again");
                helper.assertTrue(fixture.canCraft(A, planks), "Stepping Storage back keeps the factory's recipe");
                fixture.beginOnA(planks, 4);
                stage[0] = 6;
            }
            if (stage[0] == 6) {
                helper.assertTrue(fixture.planReady(), "Waiting for the customer's second plan");
                helper.assertFalse(fixture.plan().simulation(), "The customer's last log must be enough");
                helper.assertTrue(fixture.submitOnA(), "The customer's CPU must take the second job");
                stage[0] = 7;
            }
            helper.assertValueEqual(fixture.busyCpus(A), 0L, "Waiting for the second job to finish");
            helper.assertValueEqual(fixture.visible(A, planks), 8L, "All eight planks reach the customer");
            helper.assertValueEqual(fixture.total(planks), 8L, "The two jobs stored exactly eight planks");
            helper.assertValueEqual(fixture.amount(C, planks), 0L, "Still no planks in the factory's drive");
            helper.assertValueEqual(fixture.visible(A, IRON), 0L, "The factory's stock stays hidden");
            PolicyEvidence.write("orderdesk", 24, Map.of("domains", "2", "counterGoodsSeen", "3",
                    "factoryStockSeen", "0", "factoryStockSeenWithStorageReexport", "5",
                    "factoryStockSeenAfterStepBack", "0", "orders", "2", "planks", "8",
                    "planksInCustomerChest", Long.toString(fixture.amount(A, planks)),
                    "planksInCounterChest", Long.toString(fixture.amount(B, planks))));
            fixture.close();
        });
    }
}
