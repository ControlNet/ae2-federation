package space.controlnet.ae2federation.observability.state;

import java.util.Objects;
import java.util.UUID;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.observability.id.SubscriptionId;

public record ObservationSession(UUID playerId, UUID menuSessionId, SubscriptionId subscriptionId,
        long subscriptionGeneration, FabricReference scope, UUID nonce) {
    public ObservationSession {
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(menuSessionId);
        Objects.requireNonNull(subscriptionId);
        Objects.requireNonNull(scope);
        Objects.requireNonNull(nonce);
        if (subscriptionGeneration < 1 || !subscriptionId.fabricId().equals(scope.fabricId())) {
            throw new IllegalArgumentException("Observation session binding is invalid");
        }
    }

    public static ObservationSession create(UUID playerId, UUID menuSessionId, FabricReference scope,
            long subscriptionGeneration) {
        var nonce = UUID.randomUUID();
        return new ObservationSession(playerId, menuSessionId,
                SubscriptionId.create(scope.fabricId(), playerId, menuSessionId, subscriptionGeneration, nonce),
                subscriptionGeneration, scope, nonce);
    }
}
