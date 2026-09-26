package space.controlnet.ae2federation.storage.mount;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import space.controlnet.ae2federation.policy.PolicyOperation;
import java.util.function.Consumer;
import space.controlnet.ae2federation.observability.meter.OperationEventId;

final class AuthorizedStorageProjection implements
        space.controlnet.ae2federation.storage.provenance.FederationManagedStorage {
    private final MEStorage delegate;
    private final StorageProjectionAuthorization authorization;
    private final Consumer<AcceptedStorageOperation> acceptedObserver;

    AuthorizedStorageProjection(MEStorage delegate, StorageProjectionAuthorization authorization,
            Consumer<AcceptedStorageOperation> acceptedObserver) {
        this.delegate = Objects.requireNonNull(delegate);
        this.authorization = Objects.requireNonNull(authorization);
        this.acceptedObserver = Objects.requireNonNull(acceptedObserver);
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return authorization.permits(PolicyOperation.INSERT, what) && delegate.isPreferredStorageFor(what, source);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(what, amount, mode, source);
        var accepted = authorization.permits(PolicyOperation.INSERT, what)
                ? delegate.insert(what, amount, mode, source)
                : 0;
        observe(what, accepted, mode);
        return accepted;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(what, amount, mode, source);
        var accepted = authorization.permits(PolicyOperation.EXTRACT, what)
                ? delegate.extract(what, amount, mode, source)
                : 0;
        observe(what, accepted, mode);
        return accepted;
    }

    @Override
    public void getAvailableStacks(KeyCounter output) {
        // Source validity and relationship currency are evaluated once per enumeration; only the Policy resource
        // filter runs per key. Quantities come straight from the native delegate.
        var ready = authorization.readyAuthorization();
        if (ready == null) {
            return;
        }
        var available = delegate.getAvailableStacks();
        for (var entry : available) {
            if (ready.permits(PolicyOperation.VIEW, entry.getKey())) {
                output.add(entry.getKey(), entry.getLongValue());
            }
        }
    }

    @Override
    public Component getDescription() {
        return Component.literal("AE2 Federation authorized storage");
    }

    private void observe(AEKey key, long accepted, Actionable mode) {
        if (mode == Actionable.MODULATE && accepted > 0) {
            acceptedObserver.accept(new AcceptedStorageOperation(OperationEventId.create(), key, accepted));
        }
    }
}
