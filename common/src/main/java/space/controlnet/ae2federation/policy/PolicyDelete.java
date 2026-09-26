package space.controlnet.ae2federation.policy;

import java.util.Objects;

public record PolicyDelete(PolicyKey key, PolicyRevision expectedRevision) {
    public PolicyDelete {
        Objects.requireNonNull(key);
        Objects.requireNonNull(expectedRevision);
    }
}
