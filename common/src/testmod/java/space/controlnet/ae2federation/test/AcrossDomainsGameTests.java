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
}
