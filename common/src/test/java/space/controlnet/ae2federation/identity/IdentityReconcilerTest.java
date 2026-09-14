package space.controlnet.ae2federation.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class IdentityReconcilerTest {
    private static final NetworkId NETWORK_A = new NetworkId(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
    private static final NetworkId NETWORK_B = new NetworkId(UUID.fromString("00000000-0000-0000-0000-00000000000b"));

    @Test
    void preservesNetworkIdWhenCompleteLineageIsCoherent() {
        var evidence = List.of(
                lineage(NETWORK_A, "00000000-0000-0000-0000-000000000101"),
                lineage(NETWORK_A, "00000000-0000-0000-0000-000000000102"));

        var settlement = IdentityReconciler.reconcile(evidence, false, false, true);

        assertEquals(IdentityStatus.SETTLED, settlement.status());
        assertEquals(NETWORK_A, settlement.networkId().orElseThrow());
        assertTrue(settlement.canInheritPolicy());
    }

    @Test
    void rejectsMergeWhenOneGridContainsDifferentNetworkIds() {
        var evidence = List.of(
                lineage(NETWORK_A, "00000000-0000-0000-0000-000000000201"),
                lineage(NETWORK_B, "00000000-0000-0000-0000-000000000202"));

        var settlement = IdentityReconciler.reconcile(evidence, false, false, true);

        assertEquals(IdentityStatus.AMBIGUOUS_MERGE, settlement.status());
        assertFalse(settlement.canInheritPolicy());
    }

    @Test
    void rejectsCopiedLiveIdentity() {
        var evidence = List.of(lineage(NETWORK_A, "00000000-0000-0000-0000-000000000301"));

        var settlement = IdentityReconciler.reconcile(evidence, true, false, true);

        assertEquals(IdentityStatus.COPIED_LIVE_IDENTITY, settlement.status());
        assertFalse(settlement.canInheritPolicy());
    }

    @Test
    void rejectsAmbiguousSplitAndIncompleteObservation() {
        var evidence = List.of(lineage(NETWORK_A, "00000000-0000-0000-0000-000000000401"));

        var split = IdentityReconciler.reconcile(evidence, false, true, true);
        var partial = IdentityReconciler.reconcile(evidence, false, false, false);

        assertEquals(IdentityStatus.AMBIGUOUS_SPLIT, split.status());
        assertEquals(IdentityStatus.PARTIAL_LOAD, partial.status());
        assertFalse(split.canInheritPolicy());
        assertFalse(partial.canInheritPolicy());
    }

    private static NodeLineage lineage(NetworkId networkId, String nodeId) {
        return new NodeLineage(networkId, UUID.fromString(nodeId), 1);
    }
}
