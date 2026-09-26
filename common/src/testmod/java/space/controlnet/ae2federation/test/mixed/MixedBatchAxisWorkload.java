package space.controlnet.ae2federation.test.mixed;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import net.minecraft.world.item.Items;
import space.controlnet.ae2federation.test.automation.AutomationAuthorityObservation;
import space.controlnet.ae2federation.test.automation.NativeAutomationFixture;

final class MixedBatchAxisWorkload {
    private MixedBatchAxisWorkload() {
    }

    static void execute(NativeAutomationFixture fixture, MixedFactoryProfile profile) {
        var largeKey = AEItemKey.of(Items.LAPIS_LAZULI);
        var smallKey = AEItemKey.of(Items.QUARTZ);
        authorize(largeKey);
        authorize(smallKey);
        var storage = fixture.binding().consumerGrid().getStorageService().getInventory();
        for (var call = 0; call < profile.largeBatchCalls(); call++) {
            transfer(storage, largeKey, profile.largeBatchUnits());
        }
        for (var call = 0; call < profile.smallBatchCalls(); call++) {
            transfer(storage, smallKey, profile.smallBatchUnits());
        }
    }

    private static void authorize(AEItemKey key) {
        AutomationAuthorityObservation.authorizeProjectionOperation("insert", key);
        AutomationAuthorityObservation.authorizeProjectionOperation("extract", key);
    }

    private static void transfer(appeng.api.storage.MEStorage storage, AEItemKey key, long amount) {
        var source = IActionSource.empty();
        var inserted = storage.insert(key, amount, Actionable.MODULATE, source);
        if (inserted != amount) throw new IllegalStateException("Mixed batch axis insertion did not reconcile");
        var extracted = storage.extract(key, amount, Actionable.MODULATE, source);
        if (extracted != amount) throw new IllegalStateException("Mixed batch axis extraction did not reconcile");
    }
}
