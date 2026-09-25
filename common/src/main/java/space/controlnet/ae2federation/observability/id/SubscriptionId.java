package space.controlnet.ae2federation.observability.id;

import java.util.UUID;
import space.controlnet.ae2federation.domain.FederationDomainId;

public record SubscriptionId(FederationDomainId federationDomainId, String value) implements ScopedObservationId {
    public SubscriptionId {
        StableObservationId.validate(federationDomainId, "subscription", value);
    }

    public static SubscriptionId create(FederationDomainId federationDomainId, UUID playerId, UUID sessionId) {
        return new SubscriptionId(federationDomainId,
                StableObservationId.create(federationDomainId, "subscription", playerId + ":" + sessionId));
    }

    public static SubscriptionId create(FederationDomainId federationDomainId, UUID playerId, UUID sessionId, long generation,
            UUID nonce) {
        return new SubscriptionId(federationDomainId, StableObservationId.create(federationDomainId, "subscription",
                playerId + ":" + sessionId + ":" + generation + ":" + nonce));
    }
}
