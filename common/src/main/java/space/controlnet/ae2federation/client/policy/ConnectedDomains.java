package space.controlnet.ae2federation.client.policy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

/**
 * The domains connected to one domain through shared networks, however far: a domain that shares a network with a
 * connected domain is connected too. A breadth-first search from the domain's own networks, so nearer domains and
 * their networks come first, which is the order to keep when the result is cut short.
 */
public final class ConnectedDomains {
    private ConnectedDomains() {
    }

    /**
     * @param start the current domain's member networks
     * @param current the current domain, which is not part of the result
     * @param domainsOf the domains a network is a member of, in a stable order
     * @param membersOf the member networks of a domain, in a stable order
     */
    public static <D, N> Reach<D, N> reach(Collection<N> start, D current, Function<N, ? extends Collection<D>> domainsOf,
            Function<D, ? extends Collection<N>> membersOf) {
        var domains = new LinkedHashSet<D>();
        var networks = new ArrayList<N>();
        var seen = new HashSet<N>(start);
        var queue = new ArrayDeque<N>(start);
        while (!queue.isEmpty()) {
            for (var domain : domainsOf.apply(queue.poll())) {
                if (domain.equals(current) || !domains.add(domain)) continue;
                for (var network : membersOf.apply(domain)) {
                    if (seen.add(network)) {
                        networks.add(network);
                        queue.add(network);
                    }
                }
            }
        }
        return new Reach<>(List.copyOf(domains), List.copyOf(networks));
    }

    /** Connected domains and their networks outside the current domain, both nearest first. */
    public record Reach<D, N>(List<D> domains, List<N> networks) {
    }
}
