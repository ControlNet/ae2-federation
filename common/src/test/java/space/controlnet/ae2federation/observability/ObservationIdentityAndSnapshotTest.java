package space.controlnet.ae2federation.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.domain.FederationDomainId;
import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.observability.id.MemberId;
import space.controlnet.ae2federation.observability.id.FlowId;
import space.controlnet.ae2federation.observability.id.PolicyId;
import space.controlnet.ae2federation.observability.state.PolicyState;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.observability.meter.OperationEventId;
import space.controlnet.ae2federation.observability.state.FederationDomainStateSnapshot;
import space.controlnet.ae2federation.observability.state.FlowState;
import space.controlnet.ae2federation.observability.state.MemberState;
import space.controlnet.ae2federation.observability.state.ResourceUnit;

class ObservationIdentityAndSnapshotTest {
    private static final FederationDomainReference FIRST = new FederationDomainReference(new FederationDomainId("physical:first"), 3);
    private static final FederationDomainReference SECOND = new FederationDomainReference(new FederationDomainId("physical:second"), 3);

    @Test
    void stableIdsSurviveOrderingAndRejectCrossFederationDomainReuse() {
        var network = new NetworkId(UUID.fromString("10000000-0000-0000-0000-000000000001"));

        var first = MemberId.forNetwork(FIRST.federationDomainId(), network);
        var repeated = MemberId.forNetwork(FIRST.federationDomainId(), network);
        var otherFederationDomain = MemberId.forNetwork(SECOND.federationDomainId(), network);

        assertEquals(first, repeated);
        assertNotEquals(first, otherFederationDomain);
        assertThrows(IllegalArgumentException.class,
                () -> new MemberState(FIRST, otherFederationDomain, network, "online"));
    }

    @Test
    void snapshotCanonicalizesMembersAndRejectsBounds() {
        var firstNetwork = new NetworkId(UUID.fromString("20000000-0000-0000-0000-000000000002"));
        var secondNetwork = new NetworkId(UUID.fromString("10000000-0000-0000-0000-000000000001"));
        var firstMember = new MemberState(FIRST, MemberId.forNetwork(FIRST.federationDomainId(), firstNetwork), firstNetwork,
                "online");
        var secondMember = new MemberState(FIRST, MemberId.forNetwork(FIRST.federationDomainId(), secondNetwork), secondNetwork,
                "online");

        var snapshot = new FederationDomainStateSnapshot(FIRST, 9, 4, 7, List.of(firstMember, secondMember), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of());

        assertEquals(List.of(secondMember, firstMember), snapshot.members());
        var oversized = java.util.stream.IntStream.range(0, ObservationLimits.MAX_MEMBERS + 1)
                .mapToObj(index -> {
                    var network = new NetworkId(new UUID(0, index + 1L));
                    return new MemberState(FIRST, MemberId.forNetwork(FIRST.federationDomainId(), network), network, "online");
                }).toList();
        assertThrows(IllegalArgumentException.class, () -> new FederationDomainStateSnapshot(FIRST, 9, 4, 7, oversized,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
    }

    @Test
    void boundedSnapshotKeepsTheCanonicalPrefixAndCountsTheRest() {
        var members = java.util.stream.IntStream.range(0, ObservationLimits.MAX_MEMBERS + 44).mapToObj(index -> {
            var network = new NetworkId(new UUID(1, index));
            return new MemberState(FIRST, MemberId.forNetwork(FIRST.federationDomainId(), network), network, "online");
        }).toList();
        var policies = java.util.stream.IntStream.range(0, ObservationLimits.MAX_POLICIES + 76).mapToObj(index ->
                new PolicyState(FIRST, PolicyId.forKey(FIRST.federationDomainId(), new PolicyKey(
                        new NetworkId(new UUID(2, index)), new NetworkId(new UUID(3, index)), PolicyCapability.STORAGE)),
                        "enabled")).toList();

        var snapshot = FederationDomainStateSnapshot.bounded(FIRST, 9, 4, 7, members, List.of(), List.of(), policies,
                List.of(), List.of(), List.of());

        assertEquals(members.stream().sorted(java.util.Comparator.comparing(member -> member.id().value()))
                .limit(ObservationLimits.MAX_MEMBERS).toList(), snapshot.members());
        assertEquals(policies.stream().sorted(java.util.Comparator.comparing(policy -> policy.id().value()))
                .limit(ObservationLimits.MAX_POLICIES).toList(), snapshot.policies());
        assertEquals(new FederationDomainStateSnapshot.Omitted(44, 0, 0, 76, 0, 0, 0), snapshot.omitted());
        assertEquals(FederationDomainStateSnapshot.Omitted.NONE, FederationDomainStateSnapshot.bounded(FIRST, 9, 4, 7,
                members.subList(0, 3), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()).omitted());
    }

    @Test
    void flowRejectsQuantityAboveTheWireBound() {
        var event = UUID.fromString("30000000-0000-0000-0000-000000000003");

        assertThrows(IllegalArgumentException.class, () -> new FlowState(FIRST,
                FlowId.forEvent(FIRST.federationDomainId(), event), new OperationEventId(event), "ae2:item",
                ObservationLimits.MAX_RESOURCE_AMOUNT + 1, ResourceUnit.ITEM,
                FlowState.Attribution.EXACT_OPERATION, false));
    }
}
