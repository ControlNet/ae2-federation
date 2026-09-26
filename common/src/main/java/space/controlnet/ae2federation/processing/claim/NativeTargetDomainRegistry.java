package space.controlnet.ae2federation.processing.claim;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import space.controlnet.ae2federation.processing.provider.ProviderTargetState;

public final class NativeTargetDomainRegistry {
    private final Map<EndpointIdentity, Object> domains = new HashMap<>();

    public synchronized NativeTargetDomainSnapshot observe(EndpointIdentity endpoint, Object nativeGrid) {
        domains.put(Objects.requireNonNull(endpoint), Objects.requireNonNull(nativeGrid));
        var members = new IdentityHashMap<Object, Integer>();
        domains.values().forEach(domain -> members.merge(domain, 1, Integer::sum));
        var states = new HashMap<EndpointIdentity, ProviderTargetState>();
        domains.forEach((identity, domain) -> states.put(identity, members.get(domain) > 1
                ? ProviderTargetState.OVERLAPPING_SUBNET
                : ProviderTargetState.ACTIVE));
        return new NativeTargetDomainSnapshot(states);
    }

    public synchronized void forget(EndpointIdentity endpoint) {
        domains.remove(endpoint);
    }
}
