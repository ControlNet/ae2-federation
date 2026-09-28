package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.identity.IdentitySettlement;
import space.controlnet.ae2federation.identity.IdentityStatus;
import space.controlnet.ae2federation.identity.NetworkId;

class NetworkIdentityStateTest {
    private static final NetworkId NETWORK = new NetworkId(UUID.fromString("3f9a0000-0000-4000-8000-000000000c21"));

    @Test
    void oneSettledGridIsSettled() {
        assertEquals(NetworkIdentityState.SETTLED, NetworkIdentityState.of(NETWORK,
                List.of(new IdentitySettlement(IdentityStatus.SETTLED, Optional.of(NETWORK)))));
    }

    @Test
    void noLiveGridMeansUnloaded() {
        assertEquals(NetworkIdentityState.UNLOADED, NetworkIdentityState.of(NETWORK, List.of()));
    }

    @Test
    void unsettledGridsReportTheirReason() {
        assertEquals(NetworkIdentityState.SPLIT, NetworkIdentityState.of(NETWORK, List.of(
                new IdentitySettlement(IdentityStatus.AMBIGUOUS_SPLIT, Optional.empty()),
                new IdentitySettlement(IdentityStatus.AMBIGUOUS_SPLIT, Optional.empty()))));
        assertEquals(NetworkIdentityState.MERGE, NetworkIdentityState.of(NETWORK, List.of(
                new IdentitySettlement(IdentityStatus.AMBIGUOUS_MERGE, Optional.empty()))));
        assertEquals(NetworkIdentityState.LOADING, NetworkIdentityState.of(NETWORK, List.of(
                new IdentitySettlement(IdentityStatus.PARTIAL_LOAD, Optional.empty()))));
        assertEquals(NetworkIdentityState.COPIED, NetworkIdentityState.of(NETWORK, List.of(
                new IdentitySettlement(IdentityStatus.CONFLICTING_NODE_DATA, Optional.empty()))));
        assertEquals(NetworkIdentityState.COPIED, NetworkIdentityState.of(NETWORK, List.of(
                new IdentitySettlement(IdentityStatus.COPIED_LIVE_IDENTITY, Optional.empty()))));
    }

    @Test
    void aSettledGridWinsOverAStaleUnsettledOne() {
        assertEquals(NetworkIdentityState.SETTLED, NetworkIdentityState.of(NETWORK, List.of(
                new IdentitySettlement(IdentityStatus.PARTIAL_LOAD, Optional.empty()),
                new IdentitySettlement(IdentityStatus.SETTLED, Optional.of(NETWORK)))));
    }

    @Test
    void onlySettledNetworksCanBeRenamed() {
        for (var state : NetworkIdentityState.values()) {
            assertEquals(state == NetworkIdentityState.SETTLED, state.renamable());
        }
    }

    @Test
    void eachStateHasTheToneAndActionsOfTheDesign() {
        assertEquals(NetworkIdentityState.Tone.OK, NetworkIdentityState.SETTLED.tone());
        assertEquals(NetworkIdentityState.Tone.WARN, NetworkIdentityState.MERGE.tone());
        assertEquals(NetworkIdentityState.Tone.WARN, NetworkIdentityState.SPLIT.tone());
        assertEquals(NetworkIdentityState.Tone.INFO, NetworkIdentityState.LOADING.tone());
        assertEquals(NetworkIdentityState.Tone.ERROR, NetworkIdentityState.COPIED.tone());
        assertEquals(NetworkIdentityState.Tone.MUTED, NetworkIdentityState.UNLOADED.tone());
        // Only merge and split have two parts a player can find in the world, and a fix to apply.
        assertEquals(true, NetworkIdentityState.MERGE.hasParts());
        assertEquals(true, NetworkIdentityState.SPLIT.hasParts());
        assertEquals(false, NetworkIdentityState.COPIED.hasParts());
        assertEquals(true, NetworkIdentityState.COPIED.hasFix());
        assertEquals(false, NetworkIdentityState.LOADING.hasFix());
        assertEquals(false, NetworkIdentityState.SETTLED.hasFix());
    }
}
