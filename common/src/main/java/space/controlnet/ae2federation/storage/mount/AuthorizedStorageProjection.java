package space.controlnet.ae2federation.storage.mount;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import space.controlnet.ae2federation.policy.PolicyOperation;

final class AuthorizedStorageProjection implements
        space.controlnet.ae2federation.storage.provenance.FederationManagedStorage {
    private final MEStorage delegate;
    private final StorageProjectionAuthorization authorization;

    AuthorizedStorageProjection(MEStorage delegate, StorageProjectionAuthorization authorization) {
        this.delegate = Objects.requireNonNull(delegate);
        this.authorization = Objects.requireNonNull(authorization);
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return authorization.permits(PolicyOperation.INSERT, what) && delegate.isPreferredStorageFor(what, source);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(what, amount, mode, source);
        return authorization.permits(PolicyOperation.INSERT, what)
                ? delegate.insert(what, amount, mode, source)
                : 0;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(what, amount, mode, source);
        return authorization.permits(PolicyOperation.EXTRACT, what)
                ? delegate.extract(what, amount, mode, source)
                : 0;
    }

    @Override
    public void getAvailableStacks(KeyCounter output) {
        if (!authorization.ready()) {
            return;
        }
        var available = delegate.getAvailableStacks();
        for (var entry : available) {
            if (authorization.permitsView(entry.getKey())) {
                output.add(entry.getKey(), entry.getLongValue());
            }
        }
    }

    @Override
    public Component getDescription() {
        return Component.literal("AE2 Federation authorized storage");
    }
}
