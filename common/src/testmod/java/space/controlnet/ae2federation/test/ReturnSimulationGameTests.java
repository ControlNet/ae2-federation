package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.IStorageProvider;
import java.util.function.Function;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.crafting.projection.CraftingReturnLedger;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.test.crafting.PatternProjectionFixture;
import space.controlnet.ae2federation.test.storage.SourceIndexFixtures.CountingStorage;

/** Explicit capacity-limited storage test double mounted through actual native Grid storage. */
@PrefixGameTestTemplate(false)
public final class ReturnSimulationGameTests {
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 600)
    public static void projectionReturnSimulationCapacity(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(fixture.ready(), "Waiting for native Grids"))
                .thenExecute(() -> {
                    fixture.takeProviderCell();
                    var stock = new CountingStorage("return-simulation-capacity", 10);
                    var unmount = fixture.mountOnProvider(stock, 0);
                    var ledger = new CraftingReturnLedger();
                    var key = PatternProjectionFixture.stone();
                    var executing = fixture.providerNetwork();
                    var consumer = fixture.consumerNetwork();
                    IStorageProvider router;
                    try {
                        var add = CraftingReturnLedger.class.getDeclaredMethod("add", NetworkId.class,
                                NetworkId.class, AEKey.class, long.class);
                        add.setAccessible(true);
                        add.invoke(ledger, executing, consumer, key, 8L);
                        var type = Class.forName("space.controlnet.ae2federation.crafting.projection.CraftingReturnRouter");
                        var constructor = type.getDeclaredConstructor(NetworkId.class, IGrid.class,
                                CraftingReturnLedger.class, Function.class);
                        constructor.setAccessible(true);
                        router = (IStorageProvider) constructor.newInstance(executing, fixture.providerGrid(), ledger,
                                (Function<NetworkId, IGrid>) ignored -> null);
                    } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
                    var service = fixture.providerGrid().getStorageService();
                    service.addGlobalStorageProvider(router);
                    var inventory = service.getInventory();
                    helper.assertValueEqual(inventory.insert(key, 20, Actionable.SIMULATE, IActionSource.empty()), 10L,
                            "One native mount's capacity must be counted once, even when only eight of twenty are owed");
                    helper.assertValueEqual(stock.total(), 0L, "Simulation must not consume native capacity");
                    helper.assertValueEqual(ledger.held(executing, consumer, key), 0L, "Simulation must not record arrivals");
                    helper.assertValueEqual(inventory.insert(key, 20, Actionable.MODULATE, IActionSource.empty()), 10L,
                            "Actual acceptance must match the unchanged simulated native capacity");
                    helper.assertValueEqual(stock.total(), 10L, "Ten actual items leave the machine");
                    helper.assertValueEqual(ledger.held(executing, consumer, key), 8L, "Only the eight owed arrivals are held");
                    helper.assertValueEqual(inventory.insert(key, 10, Actionable.SIMULATE, IActionSource.empty()), 0L,
                            "A full native mount cannot promise more stock");
                    service.removeGlobalStorageProvider(router);
                    unmount.run();
                    space.controlnet.ae2federation.test.policy.PolicyEvidence.write("projectionreturnsimulationcapacity", 7,
                            java.util.Map.of("capacity", "10", "offered", "20", "owed", "8", "conserved", "10"));
                    fixture.close();
                }).thenSucceed();
    }
}
