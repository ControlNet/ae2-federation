package space.controlnet.ae2federation.mixin.compat;

import appeng.api.storage.MEStorage;
import appeng.me.storage.DelegatingMEInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import space.controlnet.ae2federation.ae2.storage.NativeMountLedger;

/**
 * Compatibility layer: reads the forwarding target of AE2's own {@link DelegatingMEInventory} (protected
 * {@code getDelegate()}). AE2 documents that class as forwarding all methods to the delegate, so a mounted wrapper
 * whose chain reaches another mounted handle is a provable alias of that handle. Used only while rebuilding a
 * source domain and for cheap chain-stamp validation; never to perform inventory operations.
 */
@Mixin(DelegatingMEInventory.class)
public abstract class DelegatingMEInventoryAccessMixin implements NativeMountLedger.DelegatingInventoryAccess {
    @Shadow
    protected abstract MEStorage getDelegate();

    @Override
    public MEStorage ae2federation$delegate() {
        return getDelegate();
    }
}
