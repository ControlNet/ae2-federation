package space.controlnet.ae2federation.storage.subscription;

import appeng.api.stacks.AEKey;

public interface NativeStorageListener {
    void onAmountChanged(NativeStorageAmount amount);

    long sourceAmount(AEKey key);

    void discoverKeys(Iterable<? extends AEKey> keys);

    void onDiscoveryOverflow();

    int reconcileSource(int keyBudget);
}
