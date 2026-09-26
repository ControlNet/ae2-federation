package space.controlnet.ae2federation.storage.dependency;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import space.controlnet.ae2federation.policy.PolicyFilter;
import space.controlnet.ae2federation.policy.PolicyFilterMode;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyResource;
import space.controlnet.ae2federation.policy.PolicyRule;

public final class EffectiveStorageAuthority {
    private static final PolicyFilter NONE = new PolicyFilter(PolicyFilterMode.ALLOW_LIST, Set.of());
    private static final EffectiveStorageAuthority EMPTY = new EffectiveStorageAuthority(Map.of());
    private static final EffectiveStorageAuthority UNBOUNDED = new EffectiveStorageAuthority(
            java.util.Arrays.stream(PolicyOperation.values())
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(operation -> operation,
                            operation -> PolicyFilter.allowAll())));

    private final Map<PolicyOperation, PolicyFilter> filters;

    public EffectiveStorageAuthority(Set<PolicyOperation> operations, PolicyFilter filter) {
        var entries = new EnumMap<PolicyOperation, PolicyFilter>(PolicyOperation.class);
        operations.forEach(operation -> entries.put(operation, Objects.requireNonNull(filter)));
        filters = canonical(entries);
    }

    private EffectiveStorageAuthority(Map<PolicyOperation, PolicyFilter> filters) {
        this.filters = canonical(filters);
    }

    public static EffectiveStorageAuthority empty() {
        return EMPTY;
    }

    public static EffectiveStorageAuthority unbounded() {
        return UNBOUNDED;
    }

    public static EffectiveStorageAuthority from(PolicyRule rule) {
        return new EffectiveStorageAuthority(rule.operations(), rule.filter());
    }

    public Set<PolicyOperation> operations() {
        return filters.keySet();
    }

    public PolicyFilter filter() {
        return filters.values().stream().reduce(NONE, EffectiveStorageAuthority::union);
    }

    public boolean isEmpty() {
        return filters.isEmpty();
    }

    public boolean permits(PolicyOperation operation, PolicyResource resource) {
        var filter = filters.get(operation);
        return filter != null && permits(filter, resource);
    }

    public EffectiveStorageAuthority intersect(EffectiveStorageAuthority other) {
        var result = new EnumMap<PolicyOperation, PolicyFilter>(PolicyOperation.class);
        filters.forEach((operation, filter) -> {
            var otherFilter = other.filters.get(operation);
            if (otherFilter != null) {
                result.put(operation, intersect(filter, otherFilter));
            }
        });
        return new EffectiveStorageAuthority(result);
    }

    public EffectiveStorageAuthority union(EffectiveStorageAuthority other) {
        var result = new EnumMap<PolicyOperation, PolicyFilter>(PolicyOperation.class);
        result.putAll(filters);
        other.filters.forEach((operation, filter) -> result.merge(operation, filter, EffectiveStorageAuthority::union));
        return new EffectiveStorageAuthority(result);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EffectiveStorageAuthority authority && filters.equals(authority.filters);
    }

    @Override
    public int hashCode() {
        return filters.hashCode();
    }

    private static Map<PolicyOperation, PolicyFilter> canonical(Map<PolicyOperation, PolicyFilter> filters) {
        var result = new EnumMap<PolicyOperation, PolicyFilter>(PolicyOperation.class);
        filters.forEach((operation, filter) -> {
            Objects.requireNonNull(operation);
            Objects.requireNonNull(filter);
            if (filter.mode() != PolicyFilterMode.ALLOW_LIST || !filter.entries().isEmpty()) {
                result.put(operation, filter);
            }
        });
        return Map.copyOf(result);
    }

    private static boolean permits(PolicyFilter filter, PolicyResource resource) {
        return switch (filter.mode()) {
            case ALL -> true;
            case ALLOW_LIST -> filter.entries().contains(resource);
            case DENY_LIST -> !filter.entries().contains(resource);
        };
    }

    private static PolicyFilter intersect(PolicyFilter left, PolicyFilter right) {
        if (left.mode() == PolicyFilterMode.ALL) return right;
        if (right.mode() == PolicyFilterMode.ALL) return left;
        if (left.mode() == PolicyFilterMode.ALLOW_LIST && right.mode() == PolicyFilterMode.ALLOW_LIST) {
            return filter(PolicyFilterMode.ALLOW_LIST, intersection(left.entries(), right.entries()));
        }
        if (left.mode() == PolicyFilterMode.DENY_LIST && right.mode() == PolicyFilterMode.DENY_LIST) {
            return filter(PolicyFilterMode.DENY_LIST, union(left.entries(), right.entries()));
        }
        var allow = left.mode() == PolicyFilterMode.ALLOW_LIST ? left : right;
        var deny = left.mode() == PolicyFilterMode.DENY_LIST ? left : right;
        return filter(PolicyFilterMode.ALLOW_LIST, difference(allow.entries(), deny.entries()));
    }

    private static PolicyFilter union(PolicyFilter left, PolicyFilter right) {
        if (left.mode() == PolicyFilterMode.ALL || right.mode() == PolicyFilterMode.ALL) return PolicyFilter.allowAll();
        if (left.mode() == PolicyFilterMode.ALLOW_LIST && right.mode() == PolicyFilterMode.ALLOW_LIST) {
            return filter(PolicyFilterMode.ALLOW_LIST, union(left.entries(), right.entries()));
        }
        if (left.mode() == PolicyFilterMode.DENY_LIST && right.mode() == PolicyFilterMode.DENY_LIST) {
            return filter(PolicyFilterMode.DENY_LIST, intersection(left.entries(), right.entries()));
        }
        var allow = left.mode() == PolicyFilterMode.ALLOW_LIST ? left : right;
        var deny = left.mode() == PolicyFilterMode.DENY_LIST ? left : right;
        return filter(PolicyFilterMode.DENY_LIST, difference(deny.entries(), allow.entries()));
    }

    private static PolicyFilter filter(PolicyFilterMode mode, Set<PolicyResource> entries) {
        return new PolicyFilter(mode, entries);
    }

    private static Set<PolicyResource> intersection(Set<PolicyResource> left, Set<PolicyResource> right) {
        var result = new HashSet<>(left);
        result.retainAll(right);
        return Set.copyOf(result);
    }

    private static Set<PolicyResource> union(Set<PolicyResource> left, Set<PolicyResource> right) {
        var result = new HashSet<>(left);
        result.addAll(right);
        return Set.copyOf(result);
    }

    private static Set<PolicyResource> difference(Set<PolicyResource> left, Set<PolicyResource> right) {
        var result = new HashSet<>(left);
        result.removeAll(right);
        return Set.copyOf(result);
    }
}
