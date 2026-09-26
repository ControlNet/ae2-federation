package space.controlnet.ae2federation.processing.claim;

import java.util.Objects;

public record EndpointIdentity(EndpointId id, EndpointInstanceEpoch instanceEpoch) {
    public EndpointIdentity {
        Objects.requireNonNull(id);
        Objects.requireNonNull(instanceEpoch);
    }

    public static EndpointIdentity create() {
        return new EndpointIdentity(EndpointId.create(), new EndpointInstanceEpoch(1));
    }
}
