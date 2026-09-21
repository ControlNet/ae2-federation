package space.controlnet.ae2federation.observability.state;

import java.util.Objects;
import space.controlnet.ae2federation.fabric.FabricReference;

public record FabricStateDelta(FabricStateSnapshot replacement, long baseDataRevision, boolean resnapshotRequired) {
    public FabricStateDelta {
        Objects.requireNonNull(replacement);
        if (baseDataRevision < 0 || replacement.dataRevision() <= baseDataRevision) {
            throw new IllegalArgumentException("Observation delta revisions are invalid");
        }
    }

    public FabricReference scope() {
        return replacement.scope();
    }

    public long topologyRevision() {
        return replacement.topologyRevision();
    }

    public long dataRevision() {
        return replacement.dataRevision();
    }
}
