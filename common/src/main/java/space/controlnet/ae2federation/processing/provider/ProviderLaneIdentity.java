package space.controlnet.ae2federation.processing.provider;

import java.util.Objects;

public record ProviderLaneIdentity(ProviderIdentity provider, int laneIndex, long revision) {
    public ProviderLaneIdentity {
        Objects.requireNonNull(provider);
        if (laneIndex < 0 || revision < 1) {
            throw new IllegalArgumentException("Provider Lane identity requires a non-negative index and positive revision");
        }
    }
}
