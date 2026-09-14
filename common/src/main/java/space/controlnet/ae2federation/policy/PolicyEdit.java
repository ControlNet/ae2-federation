package space.controlnet.ae2federation.policy;

import java.util.Objects;

public record PolicyEdit(PolicyKey key, PolicyRevision expectedRevision, PolicyRule rule) {
    public PolicyEdit {
        Objects.requireNonNull(key);
        Objects.requireNonNull(expectedRevision);
        Objects.requireNonNull(rule);
    }
}
