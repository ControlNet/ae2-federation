package space.controlnet.ae2federation.fabric;

import java.util.Objects;
import space.controlnet.ae2federation.identity.NetworkId;

public sealed interface FabricPortEvidence {
    enum Disconnected implements FabricPortEvidence {
        INSTANCE
    }

    record Federation(FabricPortId peer) implements FabricPortEvidence {
        public Federation {
            Objects.requireNonNull(peer);
        }
    }

    record Native(FabricSourceId source, NetworkId networkId) implements FabricPortEvidence {
        public Native {
            Objects.requireNonNull(source);
            Objects.requireNonNull(networkId);
        }
    }

    record Unsettled(FabricSourceId source, String identityStatus) implements FabricPortEvidence {
        public Unsettled {
            Objects.requireNonNull(source);
            Objects.requireNonNull(identityStatus);
        }
    }
}
