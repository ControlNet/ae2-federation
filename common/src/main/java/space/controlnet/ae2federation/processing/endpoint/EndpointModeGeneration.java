package space.controlnet.ae2federation.processing.endpoint;

import java.util.Objects;
import space.controlnet.ae2federation.ae2.processing.endpoint.NativeLocalProvider;
import space.controlnet.ae2federation.processing.claim.ClaimEpoch;
import space.controlnet.ae2federation.processing.claim.EndpointIdentity;
import space.controlnet.ae2federation.processing.claim.EndpointOwnerIdentity;

public sealed interface EndpointModeGeneration {
    long generation();

    record Local(long generation, NativeLocalProvider provider) implements EndpointModeGeneration {
        public Local {
            Objects.requireNonNull(provider);
        }
    }

    record Federated(long generation, EndpointIdentity endpoint, ClaimEpoch claimEpoch,
            EndpointOwnerIdentity owner) implements EndpointModeGeneration {
        public Federated {
            Objects.requireNonNull(endpoint);
            Objects.requireNonNull(claimEpoch);
            Objects.requireNonNull(owner);
        }
    }
}
