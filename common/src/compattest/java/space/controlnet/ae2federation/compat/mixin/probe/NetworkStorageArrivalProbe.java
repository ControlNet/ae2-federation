package space.controlnet.ae2federation.compat.mixin.probe;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.me.storage.NetworkStorage;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import space.controlnet.ae2federation.compat.probe.DispatchProbe;

/**
 * Every insert into a grid's storage, for the demand fallback's question: when a crafting output comes back, does its
 * action source name the provider that was pushed?
 */
@Mixin(value = NetworkStorage.class, remap = false)
abstract class NetworkStorageArrivalProbe {
    @WrapMethod(method = "insert")
    private long ae2federation$arrival(AEKey what, long amount, Actionable mode, IActionSource source,
            Operation<Long> original) {
        // Only the outermost insert: Federation's return router re-inserts into another grid from inside one.
        boolean outermost = DispatchProbe.enterInsert();
        long inserted;
        try {
            inserted = original.call(what, amount, mode, source);
        } finally {
            DispatchProbe.exitInsert();
        }
        if (outermost && mode == Actionable.MODULATE) DispatchProbe.arrived(what, inserted, source);
        return inserted;
    }
}
