package space.controlnet.ae2federation.processing.claim;

import java.util.Map;
import java.util.Objects;
import space.controlnet.ae2federation.processing.provider.ProviderTargetState;

public record NativeTargetDomainSnapshot(Map<EndpointIdentity, ProviderTargetState> states) {
    public NativeTargetDomainSnapshot {
        states = Map.copyOf(states);
    }

    public ProviderTargetState state(EndpointIdentity endpoint) {
        return Objects.requireNonNull(states.get(endpoint), "Endpoint has no observed native target domain");
    }
}
