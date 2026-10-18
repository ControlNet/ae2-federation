package space.controlnet.ae2federation.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Counts the capacity each Federation Cable, Federation P2P tunnel and Beam Former block must carry, from one domain's
 * evidence (see {@code docs/ideas/multiblock-routing-and-cable-capacity.md}, "Confirmed counting rules"); the registry
 * compares it with each node's capacity. A tunnel carries one cable's capacity, so a pair limits the region it joins as
 * a single cable would. A Beam Former and its beam are cable of the former's square.
 *
 * <p>A unit is a distinct ME network attached through a Switch, a Processing Endpoint or a Federation Pattern Provider;
 * a Provider is one device, whatever network stands behind it. Cables, Switches, P2P tunnels and Beam Formers joined
 * by links form a region; only Router bodies bound it. A region touching at most one Router body is a subdomain and
 * carries its own units; one touching two or more carries the whole Federation domain's.
 * Only links both sides name count, as in the domain itself.
 */
public final class CableCapacityCounter {
    public enum Scope {
        /**
         * The region's own units: it touches no Router or one Router body, so it and what attaches to it form a
         * subdomain.
         */
        SUBDOMAIN,
        /** The Federation domain's units: the region touches two or more Router bodies. */
        DOMAIN
    }

    public record Demand(Scope scope, int units) {
    }

    private CableCapacityCounter() {
    }

    /** The demand of every cable, P2P tunnel and Beam Former block among {@code domain}, one domain's evidence. */
    public static Map<FederationDomainNodeId, Demand> count(Collection<FederationDomainNodeEvidence> domain) {
        var nodes = new HashMap<FederationDomainNodeId, FederationDomainNodeEvidence>();
        domain.forEach(evidence -> nodes.put(evidence.nodeId(), evidence));
        var neighbours = new HashMap<FederationDomainNodeId, List<FederationDomainNodeId>>();
        nodes.values().forEach(evidence -> neighbours.put(evidence.nodeId(), linked(evidence, nodes)));

        var bodies = new HashMap<FederationDomainNodeId, FederationDomainNodeId>();
        for (var evidence : nodes.values()) {
            if (evidence.kind() == FederationDomainNodeKind.ROUTER && !bodies.containsKey(evidence.nodeId())) {
                var body = evidence.nodeId();
                component(body, neighbours, nodes, kind -> kind == FederationDomainNodeKind.ROUTER)
                        .forEach(member -> bodies.put(member, body));
            }
        }

        var domainUnits = new HashSet<Object>();
        nodes.values().forEach(evidence -> addUnits(evidence, domainUnits));

        var demands = new HashMap<FederationDomainNodeId, Demand>();
        var visited = new HashSet<FederationDomainNodeId>();
        for (var evidence : nodes.values()) {
            if (!passable(evidence.kind()) || visited.contains(evidence.nodeId())) continue;
            var region = component(evidence.nodeId(), neighbours, nodes, CableCapacityCounter::passable);
            visited.addAll(region);
            var routers = new HashSet<FederationDomainNodeId>();
            var units = new HashSet<Object>();
            for (var member : region) {
                addUnits(nodes.get(member), units);
                for (var neighbour : neighbours.get(member)) {
                    var kind = nodes.get(neighbour).kind();
                    if (kind == FederationDomainNodeKind.ROUTER) routers.add(bodies.get(neighbour));
                    if (device(kind)) units.add(neighbour);
                }
            }
            var demand = routers.size() >= 2 ? new Demand(Scope.DOMAIN, domainUnits.size())
                    : new Demand(Scope.SUBDOMAIN, units.size());
            for (var member : region) {
                var kind = nodes.get(member).kind();
                if (kind == FederationDomainNodeKind.CABLE || kind == FederationDomainNodeKind.P2P_TUNNEL
                        || kind == FederationDomainNodeKind.BEAM_FORMER
                        || kind == FederationDomainNodeKind.QUANTUM_CUBE) {
                    demands.put(member, demand);
                }
            }
        }
        return demands;
    }

    private static boolean passable(FederationDomainNodeKind kind) {
        return kind == FederationDomainNodeKind.CABLE || kind == FederationDomainNodeKind.SWITCH
                || kind == FederationDomainNodeKind.P2P_TUNNEL || kind == FederationDomainNodeKind.BEAM_FORMER
                || kind == FederationDomainNodeKind.QUANTUM_CUBE;
    }

    /** Endpoints, Pattern Providers and Automation Interfaces: one unit each, linked to a region from outside it. */
    private static boolean device(FederationDomainNodeKind kind) {
        return kind == FederationDomainNodeKind.ENDPOINT || kind == FederationDomainNodeKind.PATTERN_PROVIDER
                || kind == FederationDomainNodeKind.AUTOMATION_INTERFACE;
    }

    /** Switch networks by id (settled native evidence only), and the other devices by node. */
    private static void addUnits(FederationDomainNodeEvidence evidence, Set<Object> units) {
        switch (evidence.kind()) {
            case SWITCH -> evidence.ports().values().forEach(port -> {
                if (port instanceof FederationDomainPortEvidence.Native attached) units.add(attached.networkId());
            });
            case ENDPOINT, PATTERN_PROVIDER, AUTOMATION_INTERFACE -> units.add(evidence.nodeId());
            default -> {
            }
        }
    }

    private static List<FederationDomainNodeId> linked(FederationDomainNodeEvidence evidence,
            Map<FederationDomainNodeId, FederationDomainNodeEvidence> nodes) {
        var result = new ArrayList<FederationDomainNodeId>();
        evidence.ports().forEach((name, port) -> {
            if (!(port instanceof FederationDomainPortEvidence.Federation link)) return;
            var peer = nodes.get(link.peer().node());
            if (peer != null && peer.port(link.peer().port()) instanceof FederationDomainPortEvidence.Federation back
                    && back.peer().equals(new FederationDomainPortId(evidence.nodeId(), name))) {
                result.add(peer.nodeId());
            }
        });
        return result;
    }

    private static Set<FederationDomainNodeId> component(FederationDomainNodeId start,
            Map<FederationDomainNodeId, List<FederationDomainNodeId>> neighbours,
            Map<FederationDomainNodeId, FederationDomainNodeEvidence> nodes,
            java.util.function.Predicate<FederationDomainNodeKind> member) {
        var found = new HashSet<FederationDomainNodeId>();
        var queue = new ArrayDeque<FederationDomainNodeId>();
        found.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            for (var next : neighbours.get(queue.poll())) {
                if (member.test(nodes.get(next).kind()) && found.add(next)) queue.add(next);
            }
        }
        return found;
    }
}
