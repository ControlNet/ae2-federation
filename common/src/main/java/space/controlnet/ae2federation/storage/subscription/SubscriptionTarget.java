package space.controlnet.ae2federation.storage.subscription;

import appeng.api.networking.storage.IStorageService;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import space.controlnet.ae2federation.storage.dependency.EffectiveSourceRelationshipKey;

public record SubscriptionTarget(EffectiveSourceRelationshipKey relationship, IStorageService consumer,
        BooleanSupplier current) {
    public SubscriptionTarget {
        Objects.requireNonNull(relationship);
        Objects.requireNonNull(consumer);
        Objects.requireNonNull(current);
    }
}
