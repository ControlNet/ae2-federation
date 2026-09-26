package space.controlnet.ae2federation.storage.subscription;

import appeng.api.networking.storage.IStorageService;
import appeng.api.storage.MEStorage;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public record SourceSubscriptionPlan(SourceSubscriptionKey key, MEStorage source, IStorageService nativeStorageService,
        BooleanSupplier sourceCurrent, List<SubscriptionTarget> targets) {
    public SourceSubscriptionPlan {
        Objects.requireNonNull(key);
        Objects.requireNonNull(source);
        Objects.requireNonNull(nativeStorageService);
        Objects.requireNonNull(sourceCurrent);
        targets = List.copyOf(targets);
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("Source subscription requires an effective consumer");
        }
    }
}
