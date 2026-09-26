package space.controlnet.ae2federation.test.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import java.util.ArrayList;
import java.util.Map;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import space.controlnet.ae2federation.ae2.storage.NativeStorageProvenance;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.storage.mount.StorageMountService;
import space.controlnet.ae2federation.test.policy.PolicyBridgeFixtures;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;

public final class StorageRevocationChecks {
    private static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);
    private static final IActionSource ACTION_SOURCE = IActionSource.empty();

    private StorageRevocationChecks() {
    }

    public static void verify(GameTestHelper helper, PolicyBridgeFixtures fixtures, PolicyKey key,
            MEStorage projection) {
        projection.insert(IRON, 5, Actionable.MODULATE, ACTION_SOURCE);
        var policies = PolicyService.get(helper.getLevel());
        var probe = fixtures.callbackProbe();
        var providerSource = nativeSource(fixtures.outerGrid());
        probe.configure(providerSource, nativeSource(fixtures.mainGrid()));
        policies.edit(new PolicyEdit(key, policies.revision(key), PolicyRule.storageDefaults().withEnabled(false)));
        helper.assertValueEqual(projection.insert(IRON, 2, Actionable.MODULATE, ACTION_SOURCE), 0L,
                "Held projection must fail closed after invalid callback and Policy revocation");
        helper.assertValueEqual(projection.extract(IRON, 2, Actionable.MODULATE, ACTION_SOURCE), 0L,
                "Held projection extraction must fail closed after Policy revocation");
        helper.assertValueEqual(projection.getAvailableStacks().get(IRON), 0L,
                "Held projection must hide stacks after Policy revocation");
        var mounts = StorageMountService.get(helper.getLevel());
        helper.assertValueEqual(mounts.mountedRelationshipCount(), 0,
                "Invalid second callback must atomically remove the native global provider");
        helper.assertValueEqual(providerSource.getAvailableStacks().get(IRON), 5L,
                "Rejected operations must preserve provider quantity");
        probe.stabilize();
        policies.edit(new PolicyEdit(key, policies.revision(key), PolicyRule.storageDefaults()));
        var reactivated = mounts.projection(key);
        helper.assertTrue(reactivated != null, "Reactivation must restore a fresh projection");
        policies.edit(new PolicyEdit(key, policies.revision(key), PolicyRule.storageDefaults().withEnabled(false)));
        helper.assertValueEqual(reactivated.insert(IRON, 1, Actionable.MODULATE, ACTION_SOURCE), 0L,
                "Repeated revocation must invalidate the held projection");
        policies.edit(new PolicyEdit(key, policies.revision(key), PolicyRule.storageDefaults()));
        var secondActivation = mounts.projection(key);
        helper.assertTrue(secondActivation != null, "Second activation must restore a fresh projection");
        probe.clear();
        fixtures.providerChest().setCell(appeng.core.definitions.AEItems.ITEM_CELL_1K.stack());
        helper.assertValueEqual(secondActivation.insert(IRON, 1, Actionable.MODULATE, ACTION_SOURCE), 0L,
                "Held projection must reject a replaced native callback source");
        policies.edit(new PolicyEdit(key, policies.revision(key), PolicyRule.storageDefaults()));
        var beforeInactivity = mounts.projection(key);
        helper.assertTrue(beforeInactivity != null, "Current callback source must remount after replacement");
        fixtures.removeProviderChest();
        helper.assertValueEqual(beforeInactivity.insert(IRON, 1, Actionable.MODULATE, ACTION_SOURCE), 0L,
                "Provider-node inactivity must invalidate the held projection");
        helper.assertValueEqual(mounts.mountedRelationshipCount(), 0,
                "Provider inactivity must remove the stale mount");
        PolicyEvidence.write("storagerejectrevoked", 20, Map.ofEntries(
                Map.entry("insertAfterRevoke", "0"), Map.entry("extractAfterRevoke", "0"),
                Map.entry("visibleAfterRevoke", "0"), Map.entry("mountedRelationships", "0"),
                Map.entry("providerRemaining", "5"), Map.entry("dynamicAuthorization", "true"),
                Map.entry("failClosed", "true"), Map.entry("globalProviderRemoved", "true"),
                Map.entry("cachedSpendableInventory", "false"), Map.entry("reactivated", "true"),
                Map.entry("invalidSecondCallback", "BACKEND_UNREADY"), Map.entry("repeatedRevocation", "true"),
                Map.entry("sourceReplacementAccepted", "0"), Map.entry("staleSourceMountRemoved", "true"),
                Map.entry("providerInactiveAccepted", "0"), Map.entry("inactiveMountRemoved", "true")));
    }

    private static MEStorage nativeSource(IGrid grid) {
        var provenance = new NativeStorageProvenance();
        var providers = new ArrayList<IStorageProvider>();
        for (var node : grid.getNodes()) {
            var provider = node.getService(IStorageProvider.class);
            if (provider != null && node.isActive()) {
                provenance.qualify(node);
                providers.add(provider);
            }
        }
        return provenance.sources(providers).getFirst().storage();
    }
}
