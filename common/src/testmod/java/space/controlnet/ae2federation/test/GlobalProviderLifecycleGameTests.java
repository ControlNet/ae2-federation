package space.controlnet.ae2federation.test;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.me.service.CraftingService;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.crafting.projection.CraftingProjectionService;
import space.controlnet.ae2federation.policy.RuleMode;
import space.controlnet.ae2federation.test.crafting.ProjectionChainFixture;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;

/** An independent accepting provider double exercises the real native global-registration lifecycle. */
@PrefixGameTestTemplate(false)
public final class GlobalProviderLifecycleGameTests {
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 600)
    public static void projectionGlobalWithdrawalImmediate(GameTestHelper helper) {
        var fixture = new ProjectionChainFixture(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(fixture.ready(), "Waiting for native Grids"))
                .thenExecute(() -> {
            var output = AEItemKey.of(Items.DIAMOND);
            var stack = PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)),
                    List.of(new GenericStack(output, 1)));
            var pattern = PatternDetailsHelper.decodePattern(stack, helper.getLevel());
            var calls = new int[1];
            var provider = new ICraftingProvider() {
                @Override public List<IPatternDetails> getAvailablePatterns() { return List.of(pattern); }
                @Override public boolean isBusy() { return false; }
                @Override public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs) {
                    calls[0]++;
                    return true;
                }
            };
            var source = fixture.grid(2).getCraftingService();
            source.addGlobalCraftingProvider(provider);
            fixture.enableChain(RuleMode.REEXPORT);
            CraftingProjectionService.get(helper.getLevel()).reconcileAll();
            var consumers = List.of((CraftingService) fixture.grid(0).getCraftingService(),
                    (CraftingService) fixture.grid(1).getCraftingService());
            var retained = consumers.stream().map(consumer -> consumer.getProviders(pattern).iterator().next()).toList();
            source.removeGlobalCraftingProvider(provider);
            helper.assertFalse(((CraftingService) source).getProviders(pattern).iterator().hasNext(),
                    "The native source removes the provider synchronously");
            // Retained mediums must fail closed in every consumer, before the next Federation refresh tick.
            for (int index = 0; index < consumers.size(); index++) {
                helper.assertFalse(retained.get(index).pushPattern(pattern, new KeyCounter[] {new KeyCounter()}),
                        "Withdrawal must prevent same-tick execution through a retained projection");
                helper.assertValueEqual(calls[0], 0, "Removed provider must never receive the push");
                helper.assertFalse(consumers.get(index).isCraftable(output),
                        "Every consumer must withdraw the native recipe immediately");
            }
            source.addGlobalCraftingProvider(provider);
            CraftingProjectionService.get(helper.getLevel()).reconcileAll();
            for (int index = 0; index < consumers.size(); index++) {
                helper.assertTrue(consumers.get(index).isCraftable(output),
                        "A real re-registration must publish at the next source discovery");
                helper.assertTrue(retained.get(index).isBusy(), "Re-registration must not reactivate a withdrawn object");
            }
            var afterRegistration = consumers.stream()
                    .map(consumer -> consumer.getProviders(pattern).iterator().next()).toList();
            var sourceGrid = fixture.grid(2);
            var nodes = new java.util.ArrayList<appeng.api.networking.IGridNode>();
            sourceGrid.getNodes().forEach(nodes::add);
            // Native Grid destruction is explicit lifecycle fault injection; consumer Grids remain loaded.
            for (var node : nodes) {
                if (node.getOwner() instanceof appeng.me.helpers.IGridConnectedBlockEntity owner) {
                    owner.getMainNode().destroy();
                } else if (node.getOwner() instanceof space.controlnet.ae2federation.domain.port.RouterFacePort owner) {
                    owner.destroy();
                } else {
                    throw new IllegalStateException("Unexpected lifecycle-test node owner: " + node.getOwner().getClass());
                }
            }
            helper.assertTrue(sourceGrid.isEmpty(), "The old source Grid has no remaining pivot");
            source.removeGlobalCraftingProvider(provider);
            for (int index = 0; index < consumers.size(); index++) {
                helper.assertFalse(afterRegistration.get(index).pushPattern(pattern, new KeyCounter[] {new KeyCounter()}),
                        "An empty source Grid must not bypass synchronous withdrawal");
                helper.assertFalse(consumers.get(index).isCraftable(output), "Empty source withdrawal updates consumers");
            }
            PolicyEvidence.write("projectionglobalwithdrawalimmediate", 16,
                    Map.of("provider", "independent accepting test double", "withdrawal", "same tick",
                            "callbacks", "0", "consumers", "2"));
            fixture.close();
        }).thenSucceed();
    }
}
