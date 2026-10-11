package space.controlnet.ae2federation.test;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNodeListener;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.crafting.projection.CraftingProjectionService;
import space.controlnet.ae2federation.domain.*;
import space.controlnet.ae2federation.identity.*;
import space.controlnet.ae2federation.persistence.PolicySavedData;
import space.controlnet.ae2federation.policy.*;
import space.controlnet.ae2federation.test.crafting.ProjectionChainFixture;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;

/** Native managed nodes and automatic binding refresh; direct domain evidence is an explicit test injection. */
@PrefixGameTestTemplate(false)
public final class IdentityBindingCoverageGameTests {
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 600)
    public static void identityFreshPolicyBinding(GameTestHelper helper) {
        var fixture = new ProjectionChainFixture(helper);
        var stage = new int[1];
        var initialFailure = new RuntimeException[1];
        var boundary = new BoundaryProbe[1];
        var verification = new Runnable[1];
        helper.succeedWhen(() -> {
            if (initialFailure[0] != null) throw initialFailure[0];
            if (stage[0] == 1) {
                helper.assertTrue(boundary[0].sampled, "Waiting for first maturity-notified server tick end");
                try {
                    verification[0].run();
                } catch (RuntimeException failure) {
                    initialFailure[0] = failure;
                    throw failure;
                }
                return;
            }
            helper.assertTrue(fixture.ready(), "Waiting for native fixture");
            stage[0] = 1;
            try {
                var notices = new int[1];
                IGridNodeListener<Object> listener = (owner, node) -> notices[0]++;
                var first = GridHelper.createManagedNode(new Object(), listener).setTagName("main").setInWorldNode(false).setIdlePowerUsage(0);
                var second = GridHelper.createManagedNode(new Object(), listener).setTagName("main").setInWorldNode(false).setIdlePowerUsage(0);
                first.create(helper.getLevel(), helper.absolutePos(new BlockPos(1, 2, 1)));
                second.create(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 1)));
                var originalFirst = first.getGrid().getService(NetworkIdentityService.class).lineage(first.getNode()).networkId();
                var originalSecond = second.getGrid().getService(NetworkIdentityService.class).lineage(second.getNode()).networkId();
                helper.assertFalse(originalFirst.equals(originalSecond), "Independent fresh Grids begin with distinct identities");
                GridHelper.createConnection(second.getNode(), first.getNode());
                var grid = first.getGrid();
                var identity = grid.getService(NetworkIdentityService.class);
                var finalId = identity.lineage(first.getNode()).networkId();
                helper.assertTrue(finalId.equals(originalFirst) || finalId.equals(originalSecond), "Fresh merge adopts one existing identity");
                var discarded = finalId.equals(originalFirst) ? originalSecond : originalFirst;
                helper.assertFalse(discarded.equals(finalId), "One fresh native identity is actually discarded during same-tick adoption");
                helper.assertValueEqual(identity.lineage(second.getNode()).networkId(), finalId, "Both fresh nodes unify identity");
                helper.assertValueEqual(identity.settlement().status(), IdentityStatus.PARTIAL_LOAD, "Fresh-only identity stays provisional");
                helper.assertFalse(identity.settlement().canInheritPolicy(), "Provisional identity cannot authorize persisted bindings");
                helper.assertTrue(FederationDomainRegistryAccess.confirmedNetworkId(grid).isEmpty(), "No provisional confirmed endpoint is exposed");
                var port = new FederationDomainPortId(FederationDomainRegistryAccess.nodeId(helper.getLevel(),
                        helper.absolutePos(new BlockPos(1, 2, 1))), "test-observer");
                helper.assertTrue(FederationDomainRegistryAccess.nativeEvidence(grid, port) instanceof FederationDomainPortEvidence.Unsettled,
                        "Native topology publication cannot publish the provisional identity as settled");
                var output = AEItemKey.of(Items.DIAMOND);
                var encoded = PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)),
                        List.of(new GenericStack(output, 1)));
                var pattern = PatternDetailsHelper.decodePattern(encoded, helper.getLevel());
                ICraftingProvider provider = new ICraftingProvider() {
                    public List<appeng.api.crafting.IPatternDetails> getAvailablePatterns() { return List.of(pattern); }
                    public boolean pushPattern(appeng.api.crafting.IPatternDetails details, appeng.api.stacks.KeyCounter[] inputs) { return false; }
                    public boolean isBusy() { return false; }
                };
                fixture.grid(2).getCraftingService().addGlobalCraftingProvider(provider);
                var source = new FederationDomainSourceId("fresh-binding-test:" + finalId);
                var registry = FederationDomainRegistryAccess.get(helper.getLevel());
                // Inject even optimistic topology evidence: runtime identity authorization must still fail closed.
                registry.upsertDirectBridge(source, helper.getLevel().dimension().location().toString(), finalId, fixture.network(2));
                var key = new PolicyKey(finalId, fixture.network(2), PolicyCapability.CRAFTING);
                var oldKey = new PolicyKey(discarded, fixture.network(2), PolicyCapability.CRAFTING);
                var policies = PolicyService.get(helper.getLevel());
                helper.assertTrue(policies.edit(new PolicyEdit(key, policies.revision(key), PolicyRule.enabled(Set.of(PolicyOperation.REQUEST))))
                        instanceof PolicyMutationResult.Accepted, "Explicit API configuration is allowed while activation is unavailable");
                var configured = policies.configured(key).orElseThrow();
                var endpoints = new PolicyRuntimeEndpoints(grid, fixture.grid(2), BackendStatus.READY);
                helper.assertFalse(policies.activation(key, endpoints) == PolicyActivationState.ACTIVE, "Saved rule cannot activate provisional identity");
                CraftingProjectionService.get(helper.getLevel()).observeFederationDomainMembers(List.of(grid, fixture.grid(2)));
                helper.assertFalse(grid.getCraftingService().isCraftable(output), "No crafting binding is registered during adoption tick");
                int before = notices[0];
                boundary[0] = new BoundaryProbe(helper.getLevel().getServer(),
                        () -> notices[0] > before && identity.settlement().status() == IdentityStatus.SETTLED,
                        () -> grid.getCraftingService().isCraftable(output));
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                        net.neoforged.bus.api.EventPriority.LOWEST, boundary[0]);
                verification[0] = () -> {
                    helper.assertValueEqual(identity.settlement().status(), IdentityStatus.SETTLED, "Native tick boundary settles the unified identity");
                    helper.assertTrue(notices[0] > before, "Tick settlement emits native save notifications");
                    helper.assertValueEqual(FederationDomainRegistryAccess.confirmedNetworkId(grid).orElseThrow(), finalId, "Confirmed identity is the final adopted one");
                    helper.assertValueEqual(policies.activation(key, endpoints), PolicyActivationState.ACTIVE, "Saved rule activates only after settlement");
                    helper.assertTrue(boundary[0].craftable,
                            "The first maturity-notified tick ends with an automatic provider binding");
                    helper.assertValueEqual(policies.configured(key).orElseThrow(), configured, "Activation does not rewrite the saved rule or its revision");
                    var data = PolicySavedData.get(helper.getLevel());
                    var restored = PolicySavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
                    helper.assertValueEqual(restored.configured(key).orElseThrow(), configured, "SavedData round-trip preserves the final identity binding");
                    helper.assertTrue(restored.configured(oldKey).isEmpty(), "The discarded provisional identity has no orphaned persisted rule");
                    var savedFirst = new CompoundTag();
                    first.saveToNBT(savedFirst);
                    helper.assertValueEqual(savedFirst.getCompound("main").getCompound("ae2federation_network_identity").getUUID("network"),
                            finalId.value(), "Native save carries the final adopted lineage");
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(boundary[0]);
                    policies.delete(new PolicyDelete(key, policies.revision(key)));
                    registry.invalidateDirectBridge(source);
                    fixture.grid(2).getCraftingService().removeGlobalCraftingProvider(provider);
                    first.destroy();
                    second.destroy();
                    fixture.close();
                    PolicyEvidence.write("identityfreshpolicybinding", 18, Map.of("sameTickAdoption", "true", "bindingRefresh", "first maturity-notified ServerTick.Post",
                            "topologyEvidence", "explicit direct-bridge injection", "persistence", "SavedData round-trip", "guiClick", "not exercised"));
                };
            } catch (RuntimeException failure) {
                initialFailure[0] = failure;
                throw failure;
            }
            helper.fail("Waiting for native tick-boundary settlement and automatic binding publication");
        });
    }

    /** Captures exactly once after production HIGH-priority binding refresh; no callbacks or reconciliation are replayed. */
    private static final class BoundaryProbe implements java.util.function.Consumer<net.neoforged.neoforge.event.tick.ServerTickEvent.Post> {
        private final net.minecraft.server.MinecraftServer server;
        private final java.util.function.BooleanSupplier mature;
        private final java.util.function.BooleanSupplier available;
        private boolean sampled;
        private boolean craftable;

        private BoundaryProbe(net.minecraft.server.MinecraftServer server, java.util.function.BooleanSupplier mature,
                java.util.function.BooleanSupplier available) {
            this.server = server;
            this.mature = mature;
            this.available = available;
        }

        @Override
        public void accept(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
            if (sampled || event.getServer() != server || !mature.getAsBoolean()) return;
            craftable = available.getAsBoolean();
            sampled = true;
        }
    }
}
