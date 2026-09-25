package space.controlnet.ae2federation.mixin.compat;

import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.me.service.StorageService;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.controlnet.ae2federation.ae2.storage.NativeMountLedger;

/**
 * Compatibility layer over AE2's private {@code StorageService$ProviderState}, the real mount table for both node
 * providers and global providers. It records, in AE2's own mount order, every inventory that AE2 actually mounted
 * into {@code NetworkStorage} and forgets them when AE2 unmounts the provider. This is the only place where AE2's
 * mount lifecycle (node join/leave, {@code refreshNodeStorageProvider} for priority/cell changes, global provider
 * add/remove/refresh) is observable; see {@link NativeMountLedger} for why no public API suffices.
 *
 * <p>Injections run at TAIL so only successful native mounts are recorded (AE2 throws on duplicate mounts before
 * reaching TAIL), and nothing is cancelled or altered.
 */
@Mixin(targets = "appeng.me.service.StorageService$ProviderState")
public abstract class StorageServiceProviderStateMixin implements NativeMountLedger.ProviderMountTable {
    @Shadow
    @Final
    private IStorageProvider provider;

    @Shadow
    @Final
    StorageService this$0;

    @Unique
    private final List<NativeMountLedger.Mount> ae2federation$mounts = new ArrayList<>();

    @Inject(method = "mount(Lappeng/api/storage/MEStorage;I)V", at = @At("TAIL"))
    private void ae2federation$recordMount(MEStorage inventory, int priority, CallbackInfo callback) {
        ae2federation$mounts.add(new NativeMountLedger.Mount(inventory, priority));
        NativeMountLedger.recordChange(this$0, provider);
    }

    @Inject(method = "unmount()V", at = @At("TAIL"))
    private void ae2federation$recordUnmount(CallbackInfo callback) {
        ae2federation$mounts.clear();
        NativeMountLedger.recordChange(this$0, provider);
    }

    @Override
    public IStorageProvider ae2federation$provider() {
        return provider;
    }

    @Override
    public List<NativeMountLedger.Mount> ae2federation$mounts() {
        return ae2federation$mounts;
    }
}
