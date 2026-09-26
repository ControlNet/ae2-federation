package space.controlnet.ae2federation.storage.dependency;

import java.util.Objects;
import space.controlnet.ae2federation.storage.provenance.OriginNetworkId;
import space.controlnet.ae2federation.storage.provenance.SourceGeneration;

public record NativeSourceCandidate(OriginNetworkId origin, SourceGeneration generation) {
    public NativeSourceCandidate {
        Objects.requireNonNull(origin);
        Objects.requireNonNull(generation);
    }
}
