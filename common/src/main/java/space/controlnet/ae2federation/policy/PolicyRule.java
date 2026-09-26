package space.controlnet.ae2federation.policy;

import java.util.Objects;
import java.util.Set;

public record PolicyRule(boolean enabled, Set<PolicyOperation> operations, PolicyFilter filter,
        boolean allowReexport) {
    public PolicyRule {
        operations = Set.copyOf(operations);
        Objects.requireNonNull(filter);
    }

    public static PolicyRule storageDefaults() {
        return new PolicyRule(true, Set.of(PolicyOperation.VIEW, PolicyOperation.INSERT, PolicyOperation.EXTRACT),
                PolicyFilter.allowAll(), false);
    }

    public static PolicyRule enabled(Set<PolicyOperation> operations) {
        return new PolicyRule(true, operations, PolicyFilter.allowAll(), false);
    }

    public PolicyRule withEnabled(boolean nextEnabled) {
        return new PolicyRule(nextEnabled, operations, filter, allowReexport);
    }
}
