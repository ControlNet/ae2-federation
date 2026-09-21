package space.controlnet.ae2federation.observability.id;

import java.util.UUID;
import space.controlnet.ae2federation.fabric.FabricId;

public record SubscriptionId(FabricId fabricId, String value) implements ScopedObservationId {
    public SubscriptionId {
        StableObservationId.validate(fabricId, "subscription", value);
    }

    public static SubscriptionId create(FabricId fabricId, UUID playerId, UUID sessionId) {
        return new SubscriptionId(fabricId,
                StableObservationId.create(fabricId, "subscription", playerId + ":" + sessionId));
    }

    public static SubscriptionId create(FabricId fabricId, UUID playerId, UUID sessionId, long generation,
            UUID nonce) {
        return new SubscriptionId(fabricId, StableObservationId.create(fabricId, "subscription",
                playerId + ":" + sessionId + ":" + generation + ":" + nonce));
    }
}
