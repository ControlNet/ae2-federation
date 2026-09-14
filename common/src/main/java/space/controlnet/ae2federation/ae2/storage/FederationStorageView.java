package space.controlnet.ae2federation.ae2.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import net.minecraft.network.chat.Component;

final class FederationStorageView implements MEStorage {
    private final String name;
    private final MEStorage delegate;

    FederationStorageView(String name, MEStorage delegate) {
        this.name = name;
        this.delegate = delegate;
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return delegate.isPreferredStorageFor(what, source);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return delegate.insert(what, amount, mode, source);
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        return delegate.extract(what, amount, mode, source);
    }

    @Override
    public void getAvailableStacks(KeyCounter output) {
        delegate.getAvailableStacks(output);
    }

    @Override
    public Component getDescription() {
        return Component.literal("AE2 Federation " + name);
    }
}
