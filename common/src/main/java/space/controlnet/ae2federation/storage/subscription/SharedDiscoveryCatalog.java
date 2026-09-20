package space.controlnet.ae2federation.storage.subscription;

import java.util.List;

final class SharedDiscoveryCatalog<K> {
    private final BoundedKeyCursor<K> retained;

    SharedDiscoveryCatalog(int maximumRetainedKeys) {
        retained = new BoundedKeyCursor<>(maximumRetainedKeys);
    }

    List<K> discover(Iterable<? extends K> keys) {
        return retained.addAll(keys);
    }

    List<K> replay() {
        return retained.retained();
    }

    boolean contains(K key) {
        return retained.contains(key);
    }

    int size() {
        return retained.size();
    }
}
