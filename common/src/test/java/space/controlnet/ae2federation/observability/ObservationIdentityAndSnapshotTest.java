package space.controlnet.ae2federation.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.fabric.FabricId;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.observability.id.MemberId;
import space.controlnet.ae2federation.observability.id.FlowId;
import space.controlnet.ae2federation.observability.meter.OperationEventId;
import space.controlnet.ae2federation.observability.state.FabricStateSnapshot;
import space.controlnet.ae2federation.observability.state.FlowState;
import space.controlnet.ae2federation.observability.state.MemberState;
import space.controlnet.ae2federation.observability.state.ResourceUnit;

class ObservationIdentityAndSnapshotTest {
    private static final FabricReference FIRST = new FabricReference(new FabricId("physical:first"), 3);
    private static final FabricReference SECOND = new FabricReference(new FabricId("physical:second"), 3);

    @Test
    void stableIdsSurviveOrderingAndRejectCrossFabricReuse() {
        var network = new NetworkId(UUID.fromString("10000000-0000-0000-0000-000000000001"));

        var first = MemberId.forNetwork(FIRST.fabricId(), network);
        var repeated = MemberId.forNetwork(FIRST.fabricId(), network);
        var otherFabric = MemberId.forNetwork(SECOND.fabricId(), network);

        assertEquals(first, repeated);
        assertNotEquals(first, otherFabric);
        assertThrows(IllegalArgumentException.class,
                () -> new MemberState(FIRST, otherFabric, network, "online"));
    }

    @Test
    void snapshotCanonicalizesMembersAndRejectsBounds() {
        var firstNetwork = new NetworkId(UUID.fromString("20000000-0000-0000-0000-000000000002"));
        var secondNetwork = new NetworkId(UUID.fromString("10000000-0000-0000-0000-000000000001"));
        var firstMember = new MemberState(FIRST, MemberId.forNetwork(FIRST.fabricId(), firstNetwork), firstNetwork,
                "online");
        var secondMember = new MemberState(FIRST, MemberId.forNetwork(FIRST.fabricId(), secondNetwork), secondNetwork,
                "online");

        var snapshot = new FabricStateSnapshot(FIRST, 9, 4, 7, List.of(firstMember, secondMember), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of());

        assertEquals(List.of(secondMember, firstMember), snapshot.members());
        var oversized = java.util.stream.IntStream.range(0, ObservationLimits.MAX_MEMBERS + 1)
                .mapToObj(index -> {
                    var network = new NetworkId(new UUID(0, index + 1L));
                    return new MemberState(FIRST, MemberId.forNetwork(FIRST.fabricId(), network), network, "online");
                }).toList();
        assertThrows(IllegalArgumentException.class, () -> new FabricStateSnapshot(FIRST, 9, 4, 7, oversized,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
    }

    @Test
    void flowRejectsQuantityAboveTheWireBound() {
        var event = UUID.fromString("30000000-0000-0000-0000-000000000003");

        assertThrows(IllegalArgumentException.class, () -> new FlowState(FIRST,
                FlowId.forEvent(FIRST.fabricId(), event), new OperationEventId(event), "ae2:item",
                ObservationLimits.MAX_RESOURCE_AMOUNT + 1, ResourceUnit.ITEM,
                FlowState.Attribution.EXACT_OPERATION, false));
    }
}
