package space.controlnet.ae2federation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.domain.CableCapacityCounter.Demand;
import space.controlnet.ae2federation.domain.CableCapacityCounter.Scope;
import space.controlnet.ae2federation.identity.NetworkId;

final class CableCapacityCounterTest {
    private static final NetworkId A = network(1);
    private static final NetworkId B = network(2);
    private static final NetworkId C = network(3);

    @Test
    void aRegionOnOneRouterCountsItsOwnSwitchNetworksAndEndpoints() {
        var graph = new Graph();
        var router = graph.node(FederationDomainNodeKind.ROUTER);
        var first = graph.node(FederationDomainNodeKind.CABLE);
        var second = graph.node(FederationDomainNodeKind.CABLE);
        var sw = graph.node(FederationDomainNodeKind.SWITCH, A, B);
        var endpoint = graph.node(FederationDomainNodeKind.ENDPOINT);
        graph.link(router, first).link(first, second).link(second, sw).link(first, endpoint);
        // Elsewhere in the domain, behind the Router: not this region's demand.
        var away = graph.node(FederationDomainNodeKind.CABLE);
        graph.link(router, away).link(away, graph.node(FederationDomainNodeKind.SWITCH, C));

        var demand = graph.count();
        assertEquals(new Demand(Scope.SUBDOMAIN, 3), demand.get(first));
        assertEquals(new Demand(Scope.SUBDOMAIN, 3), demand.get(second));
        assertEquals(new Demand(Scope.SUBDOMAIN, 1), demand.get(away));
        assertFalse(demand.containsKey(sw), "only cables and P2P tunnels have a demand");
    }

    @Test
    void aRegionBetweenTwoRoutersCountsTheWholeDomain() {
        var graph = new Graph();
        var left = graph.node(FederationDomainNodeKind.ROUTER);
        var right = graph.node(FederationDomainNodeKind.ROUTER);
        var trunk = graph.node(FederationDomainNodeKind.CABLE);
        var spur = graph.node(FederationDomainNodeKind.CABLE);
        graph.link(left, trunk).link(trunk, right).link(trunk, spur);
        var leftCable = graph.node(FederationDomainNodeKind.CABLE);
        graph.link(left, leftCable).link(leftCable, graph.node(FederationDomainNodeKind.SWITCH, A));
        var rightCable = graph.node(FederationDomainNodeKind.CABLE);
        graph.link(right, rightCable).link(rightCable, graph.node(FederationDomainNodeKind.ENDPOINT));
        // A network attached straight onto a Router face still belongs to the domain.
        graph.link(right, graph.node(FederationDomainNodeKind.SWITCH, B));

        var demand = graph.count();
        assertEquals(new Demand(Scope.DOMAIN, 3), demand.get(trunk));
        assertEquals(new Demand(Scope.DOMAIN, 3), demand.get(spur), "a dead-end branch inherits the domain demand");
        assertEquals(new Demand(Scope.SUBDOMAIN, 1), demand.get(leftCable));
        assertEquals(new Demand(Scope.SUBDOMAIN, 1), demand.get(rightCable));
    }

    @Test
    void blocksOfOneRouterBodyAreOneRouter() {
        var graph = new Graph();
        var block = graph.node(FederationDomainNodeKind.ROUTER);
        var sameBody = graph.node(FederationDomainNodeKind.ROUTER);
        var cable = graph.node(FederationDomainNodeKind.CABLE);
        graph.link(block, sameBody).link(block, cable).link(cable, sameBody)
                .link(cable, graph.node(FederationDomainNodeKind.ENDPOINT));
        graph.link(sameBody, graph.node(FederationDomainNodeKind.SWITCH, A));

        assertEquals(new Demand(Scope.SUBDOMAIN, 1), graph.count().get(cable));
    }

    @Test
    void aSwitchJoinsTheCablesOnItsSides() {
        var graph = new Graph();
        var left = graph.node(FederationDomainNodeKind.CABLE);
        var right = graph.node(FederationDomainNodeKind.CABLE);
        var sw = graph.node(FederationDomainNodeKind.SWITCH, A);
        graph.link(left, sw).link(sw, right);
        graph.link(graph.node(FederationDomainNodeKind.ROUTER), left);
        graph.link(right, graph.node(FederationDomainNodeKind.ROUTER));
        graph.link(right, graph.node(FederationDomainNodeKind.ENDPOINT));

        var demand = graph.count();
        assertEquals(new Demand(Scope.DOMAIN, 2), demand.get(left));
        assertEquals(new Demand(Scope.DOMAIN, 2), demand.get(right));
    }

    /** A pair joins the cable on both sides into one region and carries that region's demand itself. */
    @Test
    void aP2pTunnelPairCountsAsCable() {
        var graph = new Graph();
        var near = graph.node(FederationDomainNodeKind.CABLE);
        var far = graph.node(FederationDomainNodeKind.CABLE);
        var input = graph.node(FederationDomainNodeKind.P2P_TUNNEL);
        var output = graph.node(FederationDomainNodeKind.P2P_TUNNEL);
        graph.link(near, input).link(input, output).link(output, far);
        graph.link(graph.node(FederationDomainNodeKind.ROUTER), near);
        graph.link(far, graph.node(FederationDomainNodeKind.SWITCH, A));

        var demand = graph.count();
        assertEquals(new Demand(Scope.SUBDOMAIN, 1), demand.get(near));
        assertEquals(new Demand(Scope.SUBDOMAIN, 1), demand.get(far));
        assertEquals(new Demand(Scope.SUBDOMAIN, 1), demand.get(input), "the tunnels carry the region's demand");
        assertEquals(new Demand(Scope.SUBDOMAIN, 1), demand.get(output));
    }

    /** A Beam Former pair and its beam join the regions at both ends as cable joined directly. */
    @Test
    void aBeamFormerPairCountsAsCable() {
        var graph = new Graph();
        var near = graph.node(FederationDomainNodeKind.CABLE);
        var far = graph.node(FederationDomainNodeKind.CABLE);
        var west = graph.node(FederationDomainNodeKind.BEAM_FORMER);
        var east = graph.node(FederationDomainNodeKind.BEAM_FORMER);
        graph.link(near, west).link(west, east).link(east, far);
        graph.link(near, graph.node(FederationDomainNodeKind.SWITCH, A));
        graph.link(far, graph.node(FederationDomainNodeKind.SWITCH, B));

        var demand = graph.count();
        assertEquals(new Demand(Scope.SUBDOMAIN, 2), demand.get(near), "both ends' networks count in one region");
        assertEquals(new Demand(Scope.SUBDOMAIN, 2), demand.get(west), "the formers carry the region's demand");
        assertEquals(new Demand(Scope.SUBDOMAIN, 2), demand.get(east));
    }

    @Test
    void aPatternProviderCountsOneLikeAnEndpointWhateverItsNetwork() {
        var graph = new Graph();
        var cable = graph.node(FederationDomainNodeKind.CABLE);
        var trunk = graph.node(FederationDomainNodeKind.CABLE);
        var left = graph.node(FederationDomainNodeKind.ROUTER);
        var right = graph.node(FederationDomainNodeKind.ROUTER);
        graph.link(cable, graph.node(FederationDomainNodeKind.PATTERN_PROVIDER, C));
        graph.link(cable, graph.node(FederationDomainNodeKind.SWITCH, C));
        graph.link(left, cable).link(left, trunk).link(trunk, right);

        var demand = graph.count();
        // The Provider is a device of its own: it counts beside the network C a Switch attaches.
        assertEquals(new Demand(Scope.SUBDOMAIN, 2), demand.get(cable));
        assertEquals(new Demand(Scope.DOMAIN, 2), demand.get(trunk));
    }

    @Test
    void aNetworkOnTwoSwitchesCountsOncePerRegionAndOnceInTheDomain() {
        var graph = new Graph();
        var left = graph.node(FederationDomainNodeKind.ROUTER);
        var right = graph.node(FederationDomainNodeKind.ROUTER);
        var leftCable = graph.node(FederationDomainNodeKind.CABLE);
        var rightCable = graph.node(FederationDomainNodeKind.CABLE);
        var trunk = graph.node(FederationDomainNodeKind.CABLE);
        graph.link(left, leftCable).link(right, rightCable).link(left, trunk).link(trunk, right);
        graph.link(leftCable, graph.node(FederationDomainNodeKind.SWITCH, A));
        graph.link(leftCable, graph.node(FederationDomainNodeKind.SWITCH, A));
        graph.link(rightCable, graph.node(FederationDomainNodeKind.SWITCH, A, B));

        var demand = graph.count();
        assertEquals(new Demand(Scope.SUBDOMAIN, 1), demand.get(leftCable));
        assertEquals(new Demand(Scope.SUBDOMAIN, 2), demand.get(rightCable));
        assertEquals(new Demand(Scope.DOMAIN, 2), demand.get(trunk));
    }

    @Test
    void aRegionWithoutRoutersIsASubdomainAndOneSidedLinksAreIgnored() {
        var graph = new Graph();
        var cable = graph.node(FederationDomainNodeKind.CABLE);
        graph.link(cable, graph.node(FederationDomainNodeKind.SWITCH, A))
                .link(cable, graph.node(FederationDomainNodeKind.ENDPOINT));
        var pending = graph.node(FederationDomainNodeKind.ENDPOINT);
        graph.declare(cable, pending);
        var unsettled = graph.node(FederationDomainNodeKind.SWITCH);
        graph.link(cable, unsettled);
        graph.unsettled(unsettled);

        assertEquals(new Demand(Scope.SUBDOMAIN, 2), graph.count().get(cable));
    }

    private static NetworkId network(long value) {
        return new NetworkId(new UUID(0, value));
    }

    /** A domain's evidence: numbered nodes whose links publish matching ports on both sides. */
    private static final class Graph {
        private final List<FederationDomainNodeKind> kinds = new ArrayList<>();
        private final List<Map<String, FederationDomainPortEvidence>> ports = new ArrayList<>();

        FederationDomainNodeId node(FederationDomainNodeKind kind, NetworkId... networks) {
            var id = id(kinds.size());
            kinds.add(kind);
            var map = new LinkedHashMap<String, FederationDomainPortEvidence>();
            for (int i = 0; i < networks.length; i++) {
                var face = "native" + i;
                map.put(face, new FederationDomainPortEvidence.Native(
                        FederationDomainSourceId.nativePort(new FederationDomainPortId(id, face)), networks[i]));
            }
            ports.add(map);
            return id;
        }

        Graph link(FederationDomainNodeId first, FederationDomainNodeId second) {
            declare(first, second);
            declare(second, first);
            return this;
        }

        /** Only {@code from} names the link, as a neighbour that has not answered yet. */
        void declare(FederationDomainNodeId from, FederationDomainNodeId to) {
            ports.get((int) from.blockPosition()).put("to" + to.blockPosition(), new FederationDomainPortEvidence.Federation(
                    new FederationDomainPortId(to, "to" + from.blockPosition())));
        }

        void unsettled(FederationDomainNodeId node) {
            ports.get((int) node.blockPosition()).put("face", new FederationDomainPortEvidence.Unsettled(
                    new FederationDomainSourceId("unsettled"), "PENDING"));
        }

        Map<FederationDomainNodeId, Demand> count() {
            var evidence = new ArrayList<FederationDomainNodeEvidence>();
            for (int i = 0; i < kinds.size(); i++) {
                evidence.add(new FederationDomainNodeEvidence(id(i), kinds.get(i), ports.get(i)));
            }
            return CableCapacityCounter.count(evidence);
        }

        private static FederationDomainNodeId id(long index) {
            return new FederationDomainNodeId("test:dimension", index);
        }
    }
}
