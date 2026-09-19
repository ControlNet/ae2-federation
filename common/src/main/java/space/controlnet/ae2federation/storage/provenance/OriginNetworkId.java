package space.controlnet.ae2federation.storage.provenance;

import java.util.Objects;
import space.controlnet.ae2federation.identity.NetworkId;

public record OriginNetworkId(NetworkId value) {
    public OriginNetworkId {
        Objects.requireNonNull(value, "value");
    }
}
