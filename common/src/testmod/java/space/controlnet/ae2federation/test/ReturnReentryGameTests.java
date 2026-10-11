package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import java.util.function.Function;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.crafting.projection.CraftingReturnLedger;
import space.controlnet.ae2federation.crafting.projection.HeldReturnTransfer;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.test.crafting.PatternProjectionFixture;

/** Reentrant storage test doubles exercise actual native Grid routing, with exactly three injected transit items. */
@PrefixGameTestTemplate(false)
public final class ReturnReentryGameTests {
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 600)
    public static void projectionTransitReentry(GameTestHelper helper) {
        runTransitReentry(helper, 3);
    }

    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 600)
    public static void projectionTransitPartialReentry(GameTestHelper helper) {
        runTransitReentry(helper, 1);
    }

    private static void runTransitReentry(GameTestHelper helper, int limit) {
        var fixture = new PatternProjectionFixture(helper);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(fixture.ready(), "Waiting for native Grid"))
                .thenExecute(() -> {
                    fixture.takeProviderCell();
                    var ledger = new CraftingReturnLedger();
                    var key = PatternProjectionFixture.stone();
                    call(CraftingReturnLedger.class, ledger, "addTransit",
                            new Class<?>[]{NetworkId.class, AEKey.class, long.class}, fixture.providerNetwork(), key, 3L);
                    var router = router(fixture, ledger);
                    fixture.providerGrid().getStorageService().addGlobalStorageProvider(router);
                    long[] accepted = {0};
                    long[] reentrant = {0};
                    var unmount = fixture.mountOnProvider(new MEStorage() {
                        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
                            if (mode == Actionable.MODULATE) {
                                // The real NetworkStorage is not in use: transit fallback uses a separate filtered view.
                                reentrant[0] += fixture.providerGrid().getStorageService().getInventory()
                                        .extract(what, 3, Actionable.MODULATE, source);
                                accepted[0] += Math.min(amount, limit);
                            }
                            return Math.min(amount, limit);
                        }
                        public Component getDescription() { return Component.literal("Reentrant transit test storage"); }
                    }, 0);
                    for (int pass = 0; pass < 3 / limit; pass++) {
                        call(HeldReturnTransfer.class, null, "storeTransit",
                                new Class<?>[]{CraftingReturnLedger.class, NetworkId.class, IGrid.class},
                                ledger, fixture.providerNetwork(), fixture.providerGrid());
                        helper.assertValueEqual(accepted[0] + reentrant[0] + ledger.transit(fixture.providerNetwork(), key),
                                3L, "Reentrant extraction plus stored stock and transit must conserve three actual items");
                        helper.assertValueEqual(ledger.transit(fixture.providerNetwork(), key), 3L - (pass + 1L) * limit,
                                "Only accepted transit is removed; rejected items remain available for the next sweep");
                    }
                    helper.assertValueEqual(reentrant[0], 0L, "In-flight transit must not remain visible for extraction");
                    helper.assertValueEqual(accepted[0], 3L, "Native fallback accepts all three actual items");
                    helper.assertValueEqual(ledger.transit(fixture.providerNetwork(), key), 0L, "Delivered transit is cleared");
                    unmount.run();
                    fixture.providerGrid().getStorageService().removeGlobalStorageProvider(router);
                    space.controlnet.ae2federation.test.policy.PolicyEvidence.write(
                            limit == 3 ? "projectiontransitreentry" : "projectiontransitpartialreentry", 2 * (3 / limit) + 3,
                            java.util.Map.of("conserved", "3", "nativeGrid", "true", "inventory", "reentrant test double"));
                    fixture.close();
                }).thenSucceed();
    }

    private static IStorageProvider router(PatternProjectionFixture fixture, CraftingReturnLedger ledger) {
        try {
            var type = Class.forName("space.controlnet.ae2federation.crafting.projection.CraftingReturnRouter");
            var constructor = type.getDeclaredConstructor(NetworkId.class, IGrid.class, CraftingReturnLedger.class, Function.class);
            constructor.setAccessible(true);
            return (IStorageProvider) constructor.newInstance(fixture.providerNetwork(), fixture.providerGrid(), ledger,
                    (Function<NetworkId, IGrid>) ignored -> null);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    private static Object call(Class<?> owner, Object receiver, String name, Class<?>[] types, Object... arguments) {
        try {
            var method = owner.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(receiver, arguments);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
}
