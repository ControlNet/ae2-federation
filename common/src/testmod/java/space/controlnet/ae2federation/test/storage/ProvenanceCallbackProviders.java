package space.controlnet.ae2federation.test.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.config.AccessRestriction;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IAEPowerStorage;
import net.minecraft.network.chat.Component;
import space.controlnet.ae2federation.storage.provenance.FederationManagedStorage;

public final class ProvenanceCallbackProviders {
    private ProvenanceCallbackProviders() {
    }

    public static MEStorage wrapper(MEStorage source) {
        return new DelegatingStorage(source);
    }

    public static final class MutableAliases implements IStorageProvider {
        private MEStorage source;
        private MEStorage second;
        private boolean opaque;

        /**
         * Mounts the chest's native handle (also mounted by the chest itself) plus an AE2 {@code DelegatingMEInventory}
         * over it. AE2 refuses to mount one inventory twice per provider, so the provable aliases are: the same handle
         * across providers, and AE2's own documented forwarding wrapper.
         */
        public void configureDuplicates(MEStorage nativeSource) {
            source = nativeSource;
            second = new appeng.me.storage.DelegatingMEInventory(nativeSource);
            opaque = false;
        }

        public void configureOpaque(MEStorage nativeSource) {
            source = nativeSource;
            second = new DelegatingStorage(nativeSource);
            opaque = true;
        }

        @Override
        public void mountInventories(appeng.api.storage.IStorageMounts mounts) {
            if (source == null) {
                return;
            }
            mounts.mount(source, opaque ? 20 : 10);
            mounts.mount(second, opaque ? 30 : 40);
        }
    }

    public static final class ManagedSlotRebound implements IStorageProvider, IAEPowerStorage {
        private MEStorage nativeSource;
        private MEStorage managedSource;
        private boolean managedPrefix;

        public void configureManagedPrefix(MEStorage delegate) {
            nativeSource = new DelegatingStorage(delegate);
            managedSource = new ManagedDelegatingStorage(delegate);
            managedPrefix = true;
        }

        public void configureReplacement(MEStorage delegate) {
            nativeSource = new DelegatingStorage(delegate);
            managedSource = null;
            managedPrefix = false;
        }

        @Override
        public void mountInventories(appeng.api.storage.IStorageMounts mounts) {
            if (managedPrefix) {
                mounts.mount(managedSource, 5);
            }
            if (nativeSource != null) {
                mounts.mount(nativeSource, 10);
            }
        }

        @Override
        public double injectAEPower(double amount, Actionable mode) {
            return 0;
        }

        @Override
        public double getAEMaxPower() {
            return 0;
        }

        @Override
        public double getAECurrentPower() {
            return Double.MAX_VALUE;
        }

        @Override
        public boolean isAEPublicPowerStorage() {
            return true;
        }

        @Override
        public AccessRestriction getPowerFlow() {
            return AccessRestriction.READ;
        }

        @Override
        public double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) {
            return amount;
        }
    }

    private record DelegatingStorage(MEStorage delegate) implements MEStorage {
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
            return Component.literal("opaque external alias");
        }
    }

    private record ManagedDelegatingStorage(MEStorage delegate) implements FederationManagedStorage {
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
            return Component.literal("managed callback entry");
        }
    }
}
