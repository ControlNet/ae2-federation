package space.controlnet.ae2federation.test;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.policy.RuleMode;
import space.controlnet.ae2federation.test.crafting.ProjectionChainFixture;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;

/** Independent official global provider doubles; nine actual calculations on three native Grids. */
@PrefixGameTestTemplate(false)
public final class ProviderPriorityCoverageGameTests {
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 900)
    public static void projectionPriorityTwoConsumerPlans(GameTestHelper helper) {
        var fixture = new ProjectionChainFixture(helper);
        var providers = new Provider[2];
        var output = AEItemKey.of(Items.DIAMOND);
        var stage = new int[1];
        var phase = new int[1];
        var consumer = new int[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for three native Grids");
            if (stage[0] == 0) {
                for (int index = 0; index < 2; index++) {
                    var encoded = PatternDetailsHelper.encodeProcessingPattern(
                            List.of(new GenericStack(AEItemKey.of(index == 0 ? Items.IRON_INGOT : Items.GOLD_INGOT), 1)),
                            List.of(new GenericStack(output, 1)));
                    providers[index] = new Provider(PatternDetailsHelper.decodePattern(encoded, helper.getLevel()));
                    fixture.grid(2).getCraftingService().addGlobalCraftingProvider(providers[index]);
                }
                providers[0].priority = 10;
                fixture.grid(2).getCraftingService().refreshGlobalCraftingProvider(providers[0]);
                fixture.craftingOnly(0, 2, RuleMode.ENABLED);
                fixture.craftingOnly(1, 2, RuleMode.ENABLED);
                for (int index = 0; index < 3; index++) {
                    fixture.putIn(index, AEItemKey.of(Items.IRON_INGOT), 1);
                    fixture.putIn(index, AEItemKey.of(Items.GOLD_INGOT), 1);
                }
                stage[0] = 1;
            }
            int preferred = phase[0] == 1 ? 1 : 0;
            if (stage[0] == 1) {
                for (int index = 0; index < 3; index++) {
                    var recipes = fixture.grid(index).getCraftingService().getCraftingFor(output);
                    helper.assertValueEqual(recipes.size(), 2, "Both recipes registered on each native Grid");
                    helper.assertTrue(recipes.iterator().next() == providers[preferred].pattern,
                            "Waiting for both consumer registrations and native source to reflect priority");
                }
                consumer[0] = 0;
                fixture.begin(consumer[0], output, 1);
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                helper.assertTrue(fixture.planReady(), "Waiting for actual native plan");
                helper.assertFalse(fixture.plan().simulation(), "Each Grid has both competing recipe inputs locally");
                helper.assertTrue(fixture.plan().patternTimes().containsKey(providers[preferred].pattern),
                        "Native plan selects the current preferred recipe for Grid " + consumer[0]);
                helper.assertFalse(fixture.plan().patternTimes().containsKey(providers[1 - preferred].pattern),
                        "Native plan does not keep the previous preferred recipe");
                if (++consumer[0] < 3) {
                    fixture.begin(consumer[0], output, 1);
                    helper.fail("Calculate the next consumer or native reference plan");
                }
                if (++phase[0] < 3) {
                    int changed = phase[0] == 1 ? 1 : 0;
                    providers[changed].priority = phase[0] == 1 ? 20 : 30;
                    fixture.grid(2).getCraftingService().refreshGlobalCraftingProvider(providers[changed]);
                    stage[0] = 1;
                    helper.fail("Priority-only edit; pattern objects and lists are unchanged");
                }
                for (var provider : providers) fixture.grid(2).getCraftingService().removeGlobalCraftingProvider(provider);
                stage[0] = 3;
            }
            for (int index = 0; index < 3; index++) {
                helper.assertFalse(fixture.canCraft(index, output), "Official withdrawal removes all three registrations");
            }
            for (int index = 0; index < 2; index++) {
                helper.assertTrue(PolicyService.get(helper.getLevel()).configured(new PolicyKey(fixture.network(index),
                        fixture.network(2), PolicyCapability.STORAGE)).isEmpty(), "Neither consumer ever enables Storage");
            }
            PolicyEvidence.write("projectionprioritytwoconsumerplans", 32,
                    Map.of("consumers", "2", "nativeReferenceGrids", "1", "actualNativePlans", "9", "priorityEdits", "2"));
            fixture.close();
        });
    }

    private static final class Provider implements ICraftingProvider {
        private final IPatternDetails pattern;
        private int priority;
        private Provider(IPatternDetails pattern) { this.pattern = pattern; }
        public List<IPatternDetails> getAvailablePatterns() { return List.of(pattern); }
        public int getPatternPriority() { return priority; }
        public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) { return false; }
        public boolean isBusy() { return false; }
    }
}
