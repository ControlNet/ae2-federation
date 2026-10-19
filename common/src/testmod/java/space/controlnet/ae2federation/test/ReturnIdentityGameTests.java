package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.IStorageProvider;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.ae2.storage.NativeMountLedger;
import space.controlnet.ae2federation.crafting.projection.CraftingProjectionService;
import space.controlnet.ae2federation.crafting.projection.CraftingReturnLedger;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;
import space.controlnet.ae2federation.identity.NetworkIdentityService;
import space.controlnet.ae2federation.test.storage.SourceIndexFixtures.CountingStorage;

/** Real native merges preserve one Grid while fresh nodes legally adopt two durable identities in one tick. */
@PrefixGameTestTemplate(false)
public final class ReturnIdentityGameTests {
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 100)
    public static void projectionRouterIdentityReplacement(GameTestHelper helper) {
        IGridNodeListener<Object> listener = new IGridNodeListener<>() {
            @Override public void onSaveChanges(Object owner, appeng.api.networking.IGridNode node) {
                // This native-node fixture intentionally has no persistent block owner.
            }
        };
        var freshOne = GridHelper.createManagedNode(new Object(), listener).setTagName("main");
        var freshTwo = GridHelper.createManagedNode(new Object(), listener).setTagName("main");
        var first = GridHelper.createManagedNode(new Object(), listener).setTagName("main");
        var second = GridHelper.createManagedNode(new Object(), listener).setTagName("main");
        var identityA = NetworkId.create();
        var identityB = NetworkId.create();
        var consumer = NetworkId.create();
        first.loadFromNBT(NetworkIdentityNodeSeed.managedNode("main", identityA));
        second.loadFromNBT(NetworkIdentityNodeSeed.managedNode("main", identityB));
        var ledger = CraftingReturnLedger.get(helper.getLevel());
        var service = CraftingProjectionService.get(helper.getLevel());
        var key = AEItemKey.of(Items.STONE);
        try {
            freshOne.create(helper.getLevel(), helper.absolutePos(new BlockPos(1, 3, 1)));
            freshTwo.create(helper.getLevel(), helper.absolutePos(new BlockPos(2, 3, 1)));
            first.create(helper.getLevel(), helper.absolutePos(new BlockPos(3, 3, 1)));
            second.create(helper.getLevel(), helper.absolutePos(new BlockPos(4, 3, 1)));
            GridHelper.createConnection(freshOne.getNode(), freshTwo.getNode());
            var grid = freshOne.getGrid();
            GridHelper.createConnection(freshOne.getNode(), first.getNode());
            helper.assertTrue(freshOne.getGrid() == grid, "The two-node native Grid survives merging with one durable node");
            helper.assertTrue(grid.getService(NetworkIdentityService.class).settlement().networkId().orElseThrow()
                    .equals(identityA), "Fresh nodes adopt the first durable identity");
            add(ledger, identityA, consumer, key);
            service.observeFederationDomainMembers(List.of(grid));
            var stock = new CountingStorage("same-grid-identity-return", 8);
            IStorageProvider mount = inventories -> inventories.mount(stock, 0);
            grid.getStorageService().addGlobalStorageProvider(mount);
            first.destroy();
            GridHelper.createConnection(freshOne.getNode(), second.getNode());
            helper.assertTrue(freshOne.getGrid() == grid, "The exact same Grid survives the second native merge in this tick");
            helper.assertTrue(grid.getService(NetworkIdentityService.class).settlement().networkId().orElseThrow()
                    .equals(identityB), "Fresh survivors adopt the second durable identity without inheriting policy");
            add(ledger, identityB, consumer, key);
            service.observeFederationDomainMembers(List.of(grid));
            helper.assertValueEqual(grid.getStorageService().getInventory().insert(key, 1, Actionable.MODULATE,
                    IActionSource.empty()), 1L, "Exactly one injected test arrival is accepted by the native Grid");
            helper.assertValueEqual(ledger.held(identityB, consumer, key), 1L,
                    "The currently confirmed executing identity must receive the arrival");
            helper.assertValueEqual(ledger.held(identityA, consumer, key), 0L,
                    "A stale router must not assign this arrival to the old identity");
            helper.assertValueEqual(stock.total(), 1L, "Physical inventory contains exactly one actual arrival");
            helper.assertValueEqual(ledger.owed(identityA, consumer, key), 1L,
                    "Old return responsibility is retained rather than inherited or deleted");
            var routers = NativeMountLedger.snapshot(grid.getStorageService()).globalProviders().stream()
                    .filter(provider -> provider.provider().getClass().getSimpleName().equals("CraftingReturnRouter")).toList();
            service.observeFederationDomainMembers(List.of(grid));
            var unchanged = NativeMountLedger.snapshot(grid.getStorageService()).globalProviders().stream()
                    .filter(provider -> provider.provider().getClass().getSimpleName().equals("CraftingReturnRouter")).toList();
            helper.assertTrue(routers.size() == 1 && unchanged.size() == 1
                    && routers.getFirst().provider() == unchanged.getFirst().provider(),
                    "Unchanged identity preserves exactly one router without repeated remounting");
            grid.getStorageService().removeGlobalStorageProvider(mount);
            space.controlnet.ae2federation.test.policy.PolicyEvidence.write("projectionrouteridentityreplacement", 10,
                    java.util.Map.of("sameNativeGrid", "true", "sameTick", "true", "heldA", "0", "heldB", "1"));
        } finally {
            drop(ledger, identityA, consumer, key);
            drop(ledger, identityB, consumer, key);
            first.destroy();
            second.destroy();
            freshOne.destroy();
            freshTwo.destroy();
            service.reconcileAll();
        }
        helper.succeed();
    }

    private static void add(CraftingReturnLedger ledger, NetworkId executing, NetworkId consumer, AEItemKey key) {
        invoke(ledger, "add", new Class<?>[]{NetworkId.class, NetworkId.class, appeng.api.stacks.AEKey.class, long.class},
                executing, consumer, key, 1L);
    }

    private static void drop(CraftingReturnLedger ledger, NetworkId executing, NetworkId consumer, AEItemKey key) {
        invoke(ledger, "drop", new Class<?>[]{NetworkId.class, NetworkId.class, appeng.api.stacks.AEKey.class},
                executing, consumer, key);
    }

    private static void invoke(CraftingReturnLedger ledger, String name, Class<?>[] types, Object... arguments) {
        try {
            var method = CraftingReturnLedger.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            method.invoke(ledger, arguments);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }
}
