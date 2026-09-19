package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.me.helpers.PlayerSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.ae2.storage.NativeStorageProvenance;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyFilter;
import space.controlnet.ae2federation.policy.PolicyFilterMode;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyResource;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.storage.mount.StorageMountService;
import space.controlnet.ae2federation.storage.mount.StorageLevelLifecycle;
import space.controlnet.ae2federation.test.policy.PolicyBridgeFixtures;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;
import space.controlnet.ae2federation.test.storage.HubStorageMountFixture;
import space.controlnet.ae2federation.test.storage.StorageRevocationChecks;

@PrefixGameTestTemplate(false)
public final class StorageMountGameTests {
    private static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);
    private static final IActionSource ACTION_SOURCE = IActionSource.empty();
    private StorageMountGameTests() {
    }
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void storageNativeAccess(GameTestHelper helper) {
        var fixtures = new HubStorageMountFixture(helper);
        var connected = new boolean[1];
        helper.succeedWhen(() -> {
            if (!connected[0] && fixtures.networksSettled()) {
                fixtures.connectHubs();
                connected[0] = true;
                helper.assertTrue(false, "Waiting for two-Hub Federation component");
            }
            helper.assertTrue(connected[0] && fixtures.connected(), "Cable-connected Hubs must confirm one Fabric");
            var key = fixtures.key();
            var policies = PolicyService.get(helper.getLevel());
            if (policies.revision(key).equals(PolicyRevision.NONE)) {
                policies.edit(new PolicyEdit(key, PolicyRevision.NONE, PolicyRule.storageDefaults()));
            }
            var projection = StorageMountService.get(helper.getLevel()).projection(key);
            helper.assertTrue(projection != null, "Cross-Hub relationship must mount one projection");
            var provider = nativeSource(fixtures.providerGrid());
            var terminalSource = new PlayerSource(helper.makeMockPlayer(GameType.CREATIVE));
            helper.assertValueEqual(projection.insert(IRON, 9, Actionable.MODULATE, terminalSource), 9L,
                    "Authorized insertion must preserve the native accepted amount");
            helper.assertValueEqual(provider.getAvailableStacks().get(IRON), 9L,
                    "Authorized projection must mutate the provider native source");
            var consumerAggregate = fixtures.consumerGrid().getStorageService().getInventory();
            helper.assertValueEqual(consumerAggregate.extract(IRON, 4, Actionable.MODULATE, terminalSource), 4L,
                    "Consumer native Grid aggregate must access the mounted projection");
            helper.assertValueEqual(provider.getAvailableStacks().get(IRON), 5L,
                    "Consumer aggregate extraction must reach provider native storage");
            var cleanupConsumerBefore = consumerAggregate.getAvailableStacks().get(IRON);
            var cleanup = StorageLevelLifecycle.close(helper.getLevel());
            var cleanupConsumerAfter = consumerAggregate.getAvailableStacks().get(IRON);
            helper.assertValueEqual(cleanupConsumerBefore, 5L,
                    "Mounted provider must be visible before level teardown");
            helper.assertValueEqual(cleanupConsumerAfter, 0L,
                    "Level teardown must remove the provider from the consumer native aggregate");
            helper.assertValueEqual(provider.getAvailableStacks().get(IRON), 5L,
                    "Level teardown must not mutate the provider native source");
            helper.assertTrue(cleanup.servicePresentBefore() && cleanup.mountedProvidersBefore() == 1
                            && cleanup.mountedProvidersRemoved() == 1 && cleanup.serviceRemoved(),
                    "Level teardown must remove the exact mounted service entry and provider");
            helper.assertTrue(cleanup.registryPresentBefore() && cleanup.registryRemoved()
                            && cleanup.registryAbsentAfter(),
                    "Level teardown must remove the exact Fabric registry entry");
            PolicyEvidence.write("storagenativeaccess", 20, Map.ofEntries(
                    Map.entry("mountedRelationships", "1"), Map.entry("insertAccepted", "9"),
                    Map.entry("consumerExtracted", "4"), Map.entry("providerRemaining", "5"),
                    Map.entry("nativeConsumerAggregate", "true"), Map.entry("nativeProviderSource", "true"),
                    Map.entry("direction", "consumer-to-provider"), Map.entry("customInventory", "false"),
                    Map.entry("twoHubFabric", "true"), Map.entry("nativeCaller", "player-source"),
                    Map.entry("cleanupConsumerBefore", Long.toString(cleanupConsumerBefore)),
                    Map.entry("cleanupConsumerAfter", Long.toString(cleanupConsumerAfter)),
                    Map.entry("cleanupServicePresentBefore", Boolean.toString(cleanup.servicePresentBefore())),
                    Map.entry("cleanupMountedProvidersBefore", Integer.toString(cleanup.mountedProvidersBefore())),
                    Map.entry("cleanupMountedProvidersRemoved", Integer.toString(cleanup.mountedProvidersRemoved())),
                    Map.entry("cleanupServiceRemoved", Boolean.toString(cleanup.serviceRemoved())),
                    Map.entry("cleanupRegistryPresentBefore", Boolean.toString(cleanup.registryPresentBefore())),
                    Map.entry("cleanupRegistryRemoved", Boolean.toString(cleanup.registryRemoved())),
                    Map.entry("cleanupRegistryAbsentAfter", Boolean.toString(cleanup.registryAbsentAfter())),
                    Map.entry("cleanupNativeProviderRemoved", Boolean.toString(cleanupConsumerAfter == 0
                            && provider.getAvailableStacks().get(IRON) == 5))));
            fixtures.close();
        });
    }
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void storagePriority(GameTestHelper helper) {
        runWithBridge(helper, "storagepriority", PolicyRule.storageDefaults(), 37, true,
                (fixtures, key, projection) -> {
            var expected = nativePriority(fixtures.outerGrid());
            var service = StorageMountService.get(helper.getLevel());
            helper.assertTrue(expected > 0, "Native callback priority must be nonzero");
            helper.assertValueEqual(service.mountedPriority(key), expected,
                    "Relationship provider must preserve the highest qualified native priority");
            helper.assertValueEqual(service.mountedRelationshipCount(), 1,
                    "One directional Policy key must have exactly one logical mount");
            var mountedPriority = service.mountedPriority(key);
            var provider = nativeSource(fixtures.outerGrid());
            var consumerLocal = nativeSource(fixtures.mainGrid());
            var consumerAggregate = fixtures.mainGrid().getStorageService().getInventory();
            var providerInsertedQuantity = provider.insert(IRON, 13, Actionable.MODULATE, ACTION_SOURCE);
            var providerQuantity = provider.getAvailableStacks().get(IRON);
            var consumerVisibleQuantity = consumerAggregate.getAvailableStacks().get(IRON);
            var providerRemainingCapacity = provider.insert(IRON, Long.MAX_VALUE, Actionable.SIMULATE, ACTION_SOURCE);
            var consumerLocalCapacity = consumerLocal.insert(IRON, Long.MAX_VALUE, Actionable.SIMULATE, ACTION_SOURCE);
            var consumerDeduplicatedCapacity = consumerAggregate.insert(
                    IRON, Long.MAX_VALUE, Actionable.SIMULATE, ACTION_SOURCE);
            helper.assertValueEqual(providerInsertedQuantity, 13L,
                    "Provider must accept the known redundant-route quantity");
            helper.assertValueEqual(providerQuantity, 13L,
                    "Provider source must contain the known quantity");
            helper.assertValueEqual(consumerVisibleQuantity, providerQuantity,
                    "Consumer aggregate must expose the provider quantity exactly once");
            helper.assertTrue(providerRemainingCapacity > 0 && consumerLocalCapacity > 0,
                    "Native provider and consumer cells must expose operation-derived remaining capacity");
            helper.assertValueEqual(consumerDeduplicatedCapacity,
                    providerRemainingCapacity + consumerLocalCapacity,
                    "Consumer aggregate capacity must include the provider exactly once");
            fixtures.removeFirstBridge();
            helper.assertValueEqual(projection.insert(IRON, 1, Actionable.MODULATE, ACTION_SOURCE), 1L,
                    "Removing one redundant route must preserve access");
            fixtures.removeSecondBridge();
            helper.assertValueEqual(projection.insert(IRON, 1, Actionable.MODULATE, ACTION_SOURCE), 0L,
                    "Removing the final route must invalidate the held projection");
            PolicyEvidence.write("storagepriority", 16, Map.ofEntries(
                    Map.entry("nativePriority", Integer.toString(expected)),
                    Map.entry("mountedPriority", Integer.toString(mountedPriority)),
                    Map.entry("priorityPreserved", "true"), Map.entry("mountedRelationships", "1"),
                    Map.entry("logicalDeduplication", "true"), Map.entry("aggregateRemounted", "false"),
                    Map.entry("redundantRoutes", "2"), Map.entry("firstRemovalAccepted", "1"),
                    Map.entry("finalRemovalAccepted", "0"), Map.entry("nonzeroPriority", "true"),
                    Map.entry("providerInsertedQuantity", Long.toString(providerInsertedQuantity)),
                    Map.entry("providerQuantity", Long.toString(providerQuantity)),
                    Map.entry("consumerVisibleQuantity", Long.toString(consumerVisibleQuantity)),
                    Map.entry("providerRemainingCapacity", Long.toString(providerRemainingCapacity)),
                    Map.entry("consumerLocalCapacity", Long.toString(consumerLocalCapacity)),
                    Map.entry("consumerDeduplicatedCapacity", Long.toString(consumerDeduplicatedCapacity))));
        });
    }
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void storageViewOnly(GameTestHelper helper) {
        var iron = new PolicyResource(IRON.getType().getId(), IRON.getId());
        var rule = new PolicyRule(true, Set.of(PolicyOperation.VIEW),
                new PolicyFilter(PolicyFilterMode.ALLOW_LIST, Set.of(iron)), false);
        runWithBridge(helper, "storageviewonly", rule, (fixtures, key, projection) -> {
            var provider = nativeSource(fixtures.outerGrid());
            provider.insert(IRON, 6, Actionable.MODULATE, ACTION_SOURCE);
            provider.insert(AEItemKey.of(Items.GOLD_INGOT), 3, Actionable.MODULATE, ACTION_SOURCE);
            helper.assertValueEqual(projection.getAvailableStacks().get(IRON), 6L,
                    "VIEW permission must expose provider stacks");
            helper.assertValueEqual(projection.getAvailableStacks().get(AEItemKey.of(Items.GOLD_INGOT)), 0L,
                    "Allow-list filter must hide resources not listed by Policy");
            helper.assertValueEqual(projection.insert(IRON, 2, Actionable.MODULATE, ACTION_SOURCE), 0L,
                    "VIEW-only projection must reject insertion");
            helper.assertValueEqual(projection.extract(IRON, 2, Actionable.MODULATE, ACTION_SOURCE), 0L,
                    "VIEW-only projection must reject extraction");
            helper.assertValueEqual(provider.getAvailableStacks().get(IRON), 6L,
                    "Rejected operations must not mutate provider storage");
            PolicyEvidence.write("storageviewonly", 10, Map.of("visibleAmount", "6", "insertAccepted", "0",
                    "extractAccepted", "0", "providerRemaining", "6", "viewAllowed", "true",
                    "insertAllowed", "false", "extractAllowed", "false", "nativeMutation", "false",
                    "deniedVisible", "0", "allowListApplied", "true"));
        });
    }
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void storageSimulate(GameTestHelper helper) {
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var denied = new PolicyResource(gold.getType().getId(), gold.getId());
        var rule = new PolicyRule(true, PolicyRule.storageDefaults().operations(),
                new PolicyFilter(PolicyFilterMode.DENY_LIST, Set.of(denied)), false);
        runWithBridge(helper, "storagesimulate", rule, (fixtures, key, projection) -> {
            helper.assertValueEqual(projection.insert(gold, 1, Actionable.MODULATE, ACTION_SOURCE), 0L,
                    "Deny-list filter must reject the configured resource");
            helper.assertValueEqual(projection.insert(IRON, 7, Actionable.SIMULATE, ACTION_SOURCE), 7L,
                    "SIMULATE insertion must preserve native accepted amount");
            helper.assertValueEqual(projection.getAvailableStacks().get(IRON), 0L,
                    "SIMULATE insertion must not mutate storage");
            projection.insert(IRON, 7, Actionable.MODULATE, ACTION_SOURCE);
            helper.assertValueEqual(projection.extract(IRON, 3, Actionable.SIMULATE, ACTION_SOURCE), 3L,
                    "SIMULATE extraction must preserve native available amount");
            helper.assertValueEqual(projection.getAvailableStacks().get(IRON), 7L,
                    "SIMULATE extraction must not mutate storage");
            PolicyEvidence.write("storagesimulate", 9, Map.of("simulateInsertAccepted", "7",
                    "afterSimulateInsert", "0", "modulatedAmount", "7", "simulateExtractAccepted", "3",
                    "afterSimulateExtract", "7", "actionablePreserved", "true", "sourcePreserved", "true",
                    "nativeMutationDuringSimulation", "false", "deniedResourceAccepted", "0"));
        });
    }
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void storageRejectRevoked(GameTestHelper helper) {
        runWithBridge(helper, "storagerejectrevoked", PolicyRule.storageDefaults(),
                (fixtures, key, projection) -> StorageRevocationChecks.verify(helper, fixtures, key, projection));
    }
    private static void runWithBridge(GameTestHelper helper, String testId, PolicyRule rule,
            StorageAssertion assertion) {
        runWithBridge(helper, testId, rule, 0, false, assertion);
    }
    private static void runWithBridge(GameTestHelper helper, String testId, PolicyRule rule, int providerPriority,
            boolean redundant, StorageAssertion assertion) {
        var fixtures = new PolicyBridgeFixtures(helper, new BlockPos(5, 3, 5));
        fixtures.installStorageCells();
        fixtures.providerChest().setPriority(providerPriority);
        var bridgePlaced = new boolean[1];
        helper.succeedWhen(() -> {
            if (!bridgePlaced[0] && fixtures.networksSettled()) {
                fixtures.placeFirstBridge();
                if (redundant) {
                    fixtures.placeSecondBridge();
                }
                bridgePlaced[0] = true;
                helper.assertTrue(false, "Waiting for " + testId + " confirmed Fabric");
            }
            if (!fixtures.firstBridgeReady() || (redundant && !fixtures.secondBridgeReady())) {
                helper.assertTrue(false, "Waiting for " + testId + " identities and Fabric");
            }
            var key = PolicyLifecycleGameTests.storageKey(fixtures);
            PolicyService.get(helper.getLevel()).edit(new PolicyEdit(key, PolicyRevision.NONE, rule));
            var mountService = StorageMountService.get(helper.getLevel());
            var projection = mountService.projection(key);
            helper.assertTrue(projection != null, "Active Storage Policy must mount one authorized projection");
            assertion.verify(fixtures, key, projection);
            fixtures.close();
        });
    }

    private static MEStorage nativeSource(IGrid grid) {
        var provenance = new NativeStorageProvenance();
        var providers = new ArrayList<IStorageProvider>();
        for (var node : grid.getNodes()) {
            var provider = node.getService(IStorageProvider.class);
            if (provider != null) {
                provenance.qualify(node);
                providers.add(provider);
            }
        }
        return provenance.sources(providers).getFirst().storage();
    }
    private static int nativePriority(IGrid grid) {
        var provenance = new NativeStorageProvenance();
        var providers = new ArrayList<IStorageProvider>();
        for (var node : grid.getNodes()) {
            var provider = node.getService(IStorageProvider.class);
            if (provider != null) {
                provenance.qualify(node);
                providers.add(provider);
            }
        }
        return provenance.sources(providers).stream().mapToInt(source -> source.priority()).max().orElseThrow();
    }
    @FunctionalInterface
    private interface StorageAssertion {
        void verify(PolicyBridgeFixtures fixtures, space.controlnet.ae2federation.policy.PolicyKey key,
                MEStorage projection);
    }
}
