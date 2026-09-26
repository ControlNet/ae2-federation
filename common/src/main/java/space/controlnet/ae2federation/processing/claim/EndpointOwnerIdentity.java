package space.controlnet.ae2federation.processing.claim;

import java.util.Objects;
import space.controlnet.ae2federation.processing.provider.ProviderIdentity;

public record EndpointOwnerIdentity(ProviderIdentity provider) {
    public EndpointOwnerIdentity {
        Objects.requireNonNull(provider);
    }
}
