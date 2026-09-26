package space.controlnet.ae2federation.test.mixin;

import java.util.Map;
import java.util.ArrayDeque;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.storage.subscription.SourceSnapshotLedger;
import space.controlnet.ae2federation.test.storage.SubscriptionTestHooks;

@Mixin(value = SourceSnapshotLedger.class, remap = false)
abstract class SourceSnapshotLedgerTestHookMixin {
    @Shadow
    private boolean snapshotting;

    @Shadow
    private long eventVersion;

    @Shadow
    private ArrayDeque<?> pending;

    @Inject(method = "completeSnapshot", at = @At("HEAD"))
    private void ae2federation$fireSnapshotBoundary(long generation, Map<?, Long> snapshot, boolean publishChanges,
            CallbackInfoReturnable<Boolean> callback) {
        SubscriptionTestHooks.fireSnapshotBoundary(this);
    }

    @Inject(method = "acceptAbsolute", at = @At("HEAD"))
    private void ae2federation$recordLedgerAccept(long generation, Object key, long amount,
            CallbackInfoReturnable<Boolean> callback) {
        SubscriptionTestHooks.recordLedgerAccept(this, snapshotting, eventVersion);
    }

    @Inject(method = "completeSnapshot", at = @At(value = "FIELD",
            target = "Lspace/controlnet/ae2federation/storage/subscription/SourceSnapshotLedger;snapshotting:Z",
            opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void ae2federation$recordReplayStart(long generation, Map<?, Long> snapshot, boolean publishChanges,
            CallbackInfoReturnable<Boolean> callback) {
        SubscriptionTestHooks.recordReplayStart(this, pending.size(), eventVersion);
    }

    @Inject(method = "completeSnapshot", at = @At("RETURN"))
    private void ae2federation$recordSnapshotComplete(long generation, Map<?, Long> snapshot, boolean publishChanges,
            CallbackInfoReturnable<Boolean> callback) {
        SubscriptionTestHooks.recordSnapshotComplete(this, eventVersion);
    }
}
