package space.controlnet.ae2federation.observability.state;

import java.util.Objects;
import java.util.UUID;
import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.observability.id.SubscriptionId;

public record ObservationSession(UUID playerId, UUID menuSessionId, SubscriptionId subscriptionId,
        long subscriptionGeneration, FederationDomainReference scope, UUID nonce) {
    public ObservationSession {
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(menuSessionId);
        Objects.requireNonNull(subscriptionId);
        Objects.requireNonNull(scope);
        Objects.requireNonNull(nonce);
        if (subscriptionGeneration < 1 || !subscriptionId.federationDomainId().equals(scope.federationDomainId())) {
            throw new IllegalArgumentException("Observation session binding is invalid");
        }
    }

    public static ObservationSession create(UUID playerId, UUID menuSessionId, FederationDomainReference scope,
            long subscriptionGeneration) {
        var nonce = UUID.randomUUID();
        return new ObservationSession(playerId, menuSessionId,
                SubscriptionId.create(scope.federationDomainId(), playerId, menuSessionId, subscriptionGeneration, nonce),
                subscriptionGeneration, scope, nonce);
    }
}
