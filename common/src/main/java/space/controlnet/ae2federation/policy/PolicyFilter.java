package space.controlnet.ae2federation.policy;

import java.util.Objects;
import java.util.Set;

public record PolicyFilter(PolicyFilterMode mode, Set<PolicyResource> entries) {
    public PolicyFilter {
        Objects.requireNonNull(mode);
        entries = Set.copyOf(entries);
        if (mode == PolicyFilterMode.ALL && !entries.isEmpty()) {
            throw new IllegalArgumentException("An all-resources filter cannot contain entries");
        }
    }

    public static PolicyFilter allowAll() {
        return new PolicyFilter(PolicyFilterMode.ALL, Set.of());
    }
}
