package space.controlnet.ae2federation.client.menu;

import java.util.Objects;
import java.util.UUID;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.observability.ObservationLimits;
import space.controlnet.ae2federation.policy.PolicyRevision;

public record FabricPolicyActionRequest(FabricPolicyAction action, int containerId, UUID menuNonce,
        long menuSequence, FabricReference context, PolicyRevision expectedRevision) {
    public static final int MAX_CONTAINER_ID = 100;
    public static final int MAX_PAYLOAD_BYTES = 512;

    public FabricPolicyActionRequest {
        Objects.requireNonNull(action);
        Objects.requireNonNull(menuNonce);
        Objects.requireNonNull(context);
        Objects.requireNonNull(expectedRevision);
        if (containerId < 0 || containerId > MAX_CONTAINER_ID) {
            throw new IllegalArgumentException("Fabric policy container identity is out of bounds");
        }
        if (menuSequence < 0) {
            throw new IllegalArgumentException("Fabric policy menu sequence cannot be negative");
        }
        if (context.fabricId().value().length() > ObservationLimits.MAX_ID_LENGTH) {
            throw new IllegalArgumentException("Fabric policy context identity is oversized");
        }
    }
}
