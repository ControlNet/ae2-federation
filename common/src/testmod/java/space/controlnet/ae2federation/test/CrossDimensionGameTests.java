package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.crafting.projection.CraftingProjectionService;
import space.controlnet.ae2federation.domain.FederationBindingRefresh;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.energy.EnergySharingService;
import space.controlnet.ae2federation.observability.LevelObservabilityService;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.storage.mount.StorageLevelLifecycle;
import space.controlnet.ae2federation.test.crafting.QuantumBridgeProjectionScene;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;
import space.controlnet.ae2federation.test.storage.OtherDimensionRouterPair;
import space.controlnet.ae2federation.test.storage.RouterStorageMountFixture;
import space.controlnet.ae2federation.test.world.OtherDimensionSite;

/**
 * Federation's runtime serves the whole server: domains, rules, storage mounts and crafting projection of one dimension
 * work beside those of another, a network that spans dimensions is one network, and a dimension that unloads takes
 * only its own part away.
 */
@PrefixGameTestTemplate(false)
public final class CrossDimensionGameTests {
    private static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);

    private CrossDimensionGameTests() {
    }

    /** A domain in the overworld and one in the nether share storage under rules set through one policy service. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void domainsInTwoDimensions(GameTestHelper helper) {
        var scene = new TwoDomains(helper);
        helper.succeedWhen(() -> {
            scene.advance();
            scene.assertSharing("overworld", scene.overworld.providerGrid(), scene.overworld.consumerGrid());
            scene.assertSharing("nether", scene.nether.providerGrid(), scene.nether.consumerGrid());
            PolicyEvidence.write("domainsintwodimensions", 9, Map.of("overworldShared", "9", "netherShared", "9",
                    "onePolicyService", "true"));
            scene.close();
            helper.succeed();
        });
    }

    /**
     * The nether's unload takes its nodes out of the server-wide registry and ends its mounts; the overworld's domain
     * and mount stay. Reloaded, the nether's blocks form their domain again under the rule the server kept.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void otherDimensionUnloadKeepsOverworld(GameTestHelper helper) {
        var scene = new TwoDomains(helper);
        var unloaded = new boolean[1];
        helper.succeedWhen(() -> {
            scene.advance();
            if (!unloaded[0]) {
                scene.assertSharing("nether", scene.nether.providerGrid(), scene.nether.consumerGrid());
                var nether = scene.site.level();
                // What the entrypoint runs on the level's unload, while the server keeps running.
                FederationBindingRefresh.closeLevel(nether);
                var receipt = StorageLevelLifecycle.close(nether);
                CraftingProjectionService.levelClosed(nether);
                EnergySharingService.levelClosed(nether);
                LevelObservabilityService.levelClosed(nether);
                helper.assertTrue(receipt.dimensionNodesRemoved() > 0 && receipt.dimensionNodesLeft() == 0,
                        "The unload must take every nether node out of the registry: " + receipt);
                helper.assertTrue(receipt.servicePersists(), "The storage service serves the whole server");
                helper.assertTrue(receipt.mountedProvidersRemoved() > 0, "The nether's mount must end with it");
                helper.assertTrue(FederationDomainRegistryAccess.get(helper.getLevel())
                        .federationDomainOf(scene.nether.routerNode()).isEmpty(), "No domain may hold a nether node");
                helper.assertTrue(scene.overworld.connected(), "The overworld's domain must stay");
                helper.assertValueEqual(available(scene.overworld.consumerGrid()), 9L,
                        "The overworld's consumer must still see the provider's storage");
                scene.site.reloadBlockEntities();
                unloaded[0] = true;
                helper.fail("Reloaded the nether's block entities");
            }
            helper.assertTrue(scene.nether.connected(), "Waiting for the reloaded nether blocks to form their domain");
            helper.assertValueEqual(available(scene.nether.consumerGrid()), 9L,
                    "Waiting for the nether's consumer to see its provider's storage again");
            PolicyEvidence.write("otherdimensionunloadkeepsoverworld", 16, Map.of("netherNodesLeft", "0",
                    "overworldShared", "9", "netherRestored", "9"));
            scene.close();
            helper.succeed();
        });
    }

    /**
     * The provider network reaches into the nether over a real Quantum Network Bridge, where its only pattern provider
     * is, an AE2 one; the consumer in the overworld gets that provider's pattern projected.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 600, required = true, manualOnly = true)
    public static void projectionAcrossQuantumBridge(GameTestHelper helper) {
        var scene = new QuantumBridgeProjectionScene(helper);
        var ruled = new boolean[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(ruled[0] || scene.ready(),
                    "Waiting for the Quantum Bridge to bring the nether pattern provider onto the provider network");
            if (!ruled[0]) {
                helper.assertFalse(scene.consumerCraftsStone(), "Without a crafting rule nothing is projected");
                scene.enableRules();
                ruled[0] = true;
            }
            helper.assertTrue(scene.consumerCraftsStone(), "Waiting for the nether pattern on the consumer");
            helper.assertValueEqual(scene.projections(), 1, "The nether pattern provider must be projected");
            PolicyEvidence.write("projectionacrossquantumbridge", 4, Map.of("projectedProviders", "1"));
            scene.close();
            helper.succeed();
        });
    }

    private static long available(IGrid grid) {
        return grid.getStorageService().getInventory().getAvailableStacks().get(IRON);
    }

    /** The overworld's Router pair and the nether's, joined, ruled and stocked one step per tick. */
    private static final class TwoDomains {
        private final GameTestHelper helper;
        private final RouterStorageMountFixture overworld;
        private final OtherDimensionSite site;
        private final OtherDimensionRouterPair nether;
        private int step;
        private boolean placed;

        private TwoDomains(GameTestHelper helper) {
            this.helper = helper;
            overworld = new RouterStorageMountFixture(helper);
            site = OtherDimensionSite.nether(helper, OtherDimensionRouterPair.SIZE);
            nether = new OtherDimensionRouterPair(site);
        }

        /** Fails until both domains are formed, their rules set and nine iron put in each provider. */
        private void advance() {
            if (!placed) {
                helper.assertTrue(site.ready(), "Waiting for the nether site to tick: " + site.tickDiagnostics());
                nether.place();
                placed = true;
            }
            if (step == 0) {
                helper.assertTrue(overworld.networksSettled() && nether.networksSettled(),
                        "Waiting for the four networks to settle");
                overworld.connectRouters();
                nether.connectRouters();
                step = 1;
            }
            if (step == 1) {
                helper.assertTrue(overworld.connected() && nether.connected(), "Waiting for a domain in each dimension");
                var policies = PolicyService.get(helper.getLevel());
                helper.assertTrue(policies == PolicyService.get(site.level()), "One policy service serves every dimension");
                // Both rules through the overworld's handle: the nether's must reach the nether's storage all the same.
                var result = policies.editAll(List.of(
                        new PolicyEdit(overworld.key(), policies.revision(overworld.key()), PolicyRule.storageDefaults()),
                        new PolicyEdit(nether.key(), policies.revision(nether.key()), PolicyRule.storageDefaults())));
                helper.assertTrue(result instanceof PolicyMutationResult.Accepted, "Both rules must be accepted: " + result);
                for (var grid : List.of(overworld.providerGrid(), nether.providerGrid())) {
                    helper.assertValueEqual(grid.getStorageService().getInventory()
                            .insert(IRON, 9, Actionable.MODULATE, IActionSource.empty()), 9L, "The provider takes the iron");
                }
                step = 2;
            }
        }

        private void assertSharing(String dimension, IGrid provider, IGrid consumer) {
            helper.assertValueEqual(available(consumer), 9L,
                    "Waiting for the " + dimension + " consumer to see its provider's iron");
            helper.assertTrue(provider != consumer, "The " + dimension + " networks must stay two");
        }

        private void close() {
            overworld.close();
            site.close();
        }
    }
}
