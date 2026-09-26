package space.controlnet.ae2federation.processing.provider;

import java.util.Objects;

public record ProviderIdentity(ProviderId id, ProviderInstanceEpoch instanceEpoch) {
    public ProviderIdentity {
        Objects.requireNonNull(id);
        Objects.requireNonNull(instanceEpoch);
    }

    public static ProviderIdentity create() {
        return new ProviderIdentity(ProviderId.create(), new ProviderInstanceEpoch(1));
    }
}
