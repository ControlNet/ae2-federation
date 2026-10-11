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
    public static void projectionAcceptedPushDisconnect(GameTestHelper helper) {
        var fixture = new ProjectionChainFixture(helper);
        var input = AEItemKey.of(Items.IRON_INGOT);
        var output = AEItemKey.of(Items.DIAMOND);
        var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(input, 1)), List.of(new GenericStack(output, 1))), helper.getLevel());
        var calls = new int[1];
        var machineInput = new long[1];
        var source = new appeng.api.networking.IGrid[1];
        var consumer = new space.controlnet.ae2federation.identity.NetworkId[1];
        var executing = new space.controlnet.ae2federation.identity.NetworkId[1];
        var provider = new ICraftingProvider() {
            @Override public List<IPatternDetails> getAvailablePatterns() { return List.of(pattern); }
            @Override public boolean isBusy() { return false; }
            @Override public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs) {
                calls[0]++;
                for (var counter : inputs) {
                    machineInput[0] += counter.get(input);
                    counter.reset();
                }
                // Explicit callback fault injection: accepted inputs can synchronously change the source topology.
                fixture.removeSwitch();
                CraftingProjectionService.get(helper.getLevel()).reconcileAll();
                return true;
            }
        };
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(fixture.ready(), "Waiting for native Grids"))
                .thenExecute(() -> {
                    source[0] = fixture.grid(2);
                    consumer[0] = fixture.network(0);
                    executing[0] = fixture.network(2);
                    source[0].getCraftingService().addGlobalCraftingProvider(provider);
                    fixture.craftingOnly(0, 2, RuleMode.ENABLED);
                    CraftingProjectionService.get(helper.getLevel()).observeFederationDomainMembers(fixture.grids());
                    fixture.putIn(0, input, 1);
                    fixture.begin(0, output, 1);
                })
                .thenWaitUntil(() -> helper.assertTrue(fixture.planReady(), "Waiting for the native calculation"))
                .thenExecute(() -> helper.assertTrue(fixture.submit(), "The native CPU accepts the plan"))
                .thenWaitUntil(() -> helper.assertValueEqual(calls[0], 1, "Waiting for the real CPU dispatch"))
                .thenExecute(() -> {
                    var ledger = space.controlnet.ae2federation.crafting.projection.CraftingReturnLedger.get(helper.getLevel());
                    helper.assertValueEqual(machineInput[0], 1L, "The machine owns the one dispatched input");
                    helper.assertValueEqual(ledger.owed(executing[0], consumer[0], output), 1L,
                            "The accepted push records its output debt despite withdrawal");
                    helper.assertValueEqual(ledger.jobs(consumer[0]).size(), 1,
                            "The accepted push must retain its native CPU lifecycle after withdrawal");
                    helper.assertTrue(CraftingProjectionService.get(helper.getLevel()).routes(source[0]),
                            "The executing Grid must retain a return router for the new debt");
                    helper.assertValueEqual(source[0].getStorageService().getInventory().insert(output, 1,
                            appeng.api.config.Actionable.MODULATE,
                            appeng.api.networking.security.IActionSource.empty()), 1L,
                            "The disconnected machine output enters native local storage once");
                    machineInput[0] = 0;
                    helper.assertValueEqual(ledger.held(executing[0], consumer[0], output), 1L,
                            "The accepted local arrival remains eligible for return without storage sharing");
                    helper.setBlock(new net.minecraft.core.BlockPos(6, 5, 6),
                            space.controlnet.ae2federation.router.RouterRegistration.SWITCH.get());
                })
                .thenWaitUntil(() -> helper.assertValueEqual(fixture.amount(0, output), 1L,
                        "Restoring the physical connection must finish the original job"))
                .thenExecute(() -> {
                    helper.assertValueEqual(fixture.total(output), 1L, "Exactly one physical output exists");
                    helper.assertValueEqual(fixture.owed(2, 0, output), 0L, "Actual return settles the debt");
                    helper.assertValueEqual(fixture.busyCpus(0), 0L, "The original native CPU completes");
                    var storageKey = new space.controlnet.ae2federation.policy.PolicyKey(consumer[0], executing[0],
                            space.controlnet.ae2federation.policy.PolicyCapability.STORAGE);
                    helper.assertTrue(space.controlnet.ae2federation.policy.PolicyService.get(helper.getLevel())
                            .configured(storageKey).isEmpty(), "Storage sharing was never configured");
                    PolicyEvidence.write("projectionacceptedpushdisconnect", 15,
                            Map.of("dispatch", "native CPU", "provider", "callback fault injection",
                                    "storageConfigured", "false", "returned", "1"));
                    source[0].getCraftingService().removeGlobalCraftingProvider(provider);
                    fixture.close();
                }).thenSucceed();
    }

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
            // Native blocks must finish CPU cluster teardown before their managed nodes are destroyed.
            // Keep the Switch face alive for their neighbor updates; retire that face last.
            for (var node : nodes) {
                if (node.getOwner() instanceof net.minecraft.world.level.block.entity.BlockEntity owner) {
                    helper.getLevel().removeBlock(owner.getBlockPos(), false);
                } else if (!(node.getOwner() instanceof space.controlnet.ae2federation.domain.port.RouterFacePort)) {
                    throw new IllegalStateException("Unexpected lifecycle-test node owner: " + node.getOwner().getClass());
                }
            }
            for (var node : nodes) {
                if (node.getOwner() instanceof space.controlnet.ae2federation.domain.port.RouterFacePort owner) {
                    owner.destroy();
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
            // The destroyed face must not remain in a live Switch for later structure-clear neighbor updates.
            fixture.removeSwitch();
            fixture.close();
        }).thenSucceed();
    }
}
