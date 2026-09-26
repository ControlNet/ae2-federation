package space.controlnet.ae2federation.domain;

import java.util.Objects;
import space.controlnet.ae2federation.identity.NetworkId;

public sealed interface FederationDomainPortEvidence {
    enum Disconnected implements FederationDomainPortEvidence {
        INSTANCE
    }

    record Federation(FederationDomainPortId peer) implements FederationDomainPortEvidence {
        public Federation {
            Objects.requireNonNull(peer);
        }
    }

    record Native(FederationDomainSourceId source, NetworkId networkId) implements FederationDomainPortEvidence {
        public Native {
            Objects.requireNonNull(source);
            Objects.requireNonNull(networkId);
        }
    }

    record Unsettled(FederationDomainSourceId source, String identityStatus) implements FederationDomainPortEvidence {
        public Unsettled {
            Objects.requireNonNull(source);
            Objects.requireNonNull(identityStatus);
        }
    }
}
