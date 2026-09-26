package space.controlnet.ae2federation.test.storage;

import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import org.jetbrains.annotations.Nullable;

public final class InvalidSecondCallbackProvider implements IStorageProvider {
    private @Nullable MEStorage qualifiedSource;
    private @Nullable MEStorage replacementSource;
    private int callbacks;
    private boolean armed;

    public void configure(MEStorage qualifiedSource, MEStorage replacementSource) {
        this.qualifiedSource = qualifiedSource;
        this.replacementSource = replacementSource;
        callbacks = 0;
        armed = true;
    }

    public void stabilize() {
        callbacks = 0;
        armed = false;
    }

    public void clear() {
        qualifiedSource = null;
        replacementSource = null;
        stabilize();
    }

    @Override
    public void mountInventories(IStorageMounts storageMounts) {
        if (qualifiedSource == null) {
            return;
        }
        callbacks++;
        storageMounts.mount(armed && callbacks > 1 ? replacementSource : qualifiedSource, 23);
    }
}
