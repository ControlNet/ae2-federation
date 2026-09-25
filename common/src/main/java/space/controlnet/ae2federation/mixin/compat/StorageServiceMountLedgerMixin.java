package space.controlnet.ae2federation.mixin.compat;

import appeng.api.networking.IGridNode;
import appeng.me.service.StorageService;
import java.util.List;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import appeng.api.networking.storage.IStorageService;
import space.controlnet.ae2federation.ae2.storage.NativeMountLedger;
import space.controlnet.ae2federation.storage.mount.StorageMountService;

/**
 * Compatibility layer: exposes AE2's storage mount table (read only) plus a monotonically increasing mount
 * generation. {@code IStorageService} has no public query or listener for which inventories are mounted, so
 * Federation would otherwise have to replay every provider's {@code mountInventories} on every storage operation.
 * The generation is bumped only by {@link StorageServiceProviderStateMixin} when AE2 really mounts or unmounts.
 * Nothing here changes AE2 behaviour; {@code StorageServiceNotificationMixin} stays responsible for notifications.
 */
@Mixin(StorageService.class)
public abstract class StorageServiceMountLedgerMixin implements NativeMountLedger.ServiceMountLedger {
    @Shadow
    @Final
    private Map<IGridNode, ?> nodeProviders;

    @Shadow
    @Final
    private List<?> globalProviders;

    @Unique
    private long ae2federation$mountGeneration = 1;

    @Unique
    private boolean ae2federation$mountChanged;

    /**
     * Coalesces this tick's native mount changes into one relationship reconcile for the observing level. This is
     * event driven (only services whose mount table really changed), not a global poll.
     */
    @Inject(method = "onServerEndTick", at = @At("TAIL"))
    private void ae2federation$reconcileChangedMounts(CallbackInfo callback) {
        if (ae2federation$mountChanged) {
            ae2federation$mountChanged = false;
            StorageMountService.nativeMountsChanged((IStorageService) (Object) this);
        }
    }

    @Override
    public long ae2federation$mountGeneration() {
        return ae2federation$mountGeneration;
    }

    @Override
    public void ae2federation$markMountChanged() {
        ae2federation$mountGeneration = Math.incrementExact(ae2federation$mountGeneration);
        ae2federation$mountChanged = true;
    }

    @Override
    public Map<IGridNode, ?> ae2federation$nodeProviderStates() {
        return nodeProviders;
    }

    @Override
    public List<?> ae2federation$globalProviderStates() {
        return globalProviders;
    }
}
