package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;

final class PolicyEditorSelectionTest {
    private static final NetworkId FIRST = new NetworkId(new UUID(0, 1));
    private static final NetworkId SECOND = new NetworkId(new UUID(0, 2));
    private static final NetworkId THIRD = new NetworkId(new UUID(0, 3));

    @Test
    void initialSelectionUsesOrderedDistinctEndpointsAndStorageCapability() {
        var selection = PolicyEditorSelection.initial(List.of(THIRD, FIRST, SECOND));

        var key = selection.key();

        assertEquals(FIRST, key.consumerNetworkId());
        assertEquals(SECOND, key.providerNetworkId());
        assertEquals(PolicyCapability.STORAGE, key.capability());
    }

    @Test
    void endpointCyclingNeverSelectsTheSameNetworkOnBothSides() {
        var selection = PolicyEditorSelection.initial(List.of(FIRST, SECOND, THIRD));

        var movedConsumer = selection.nextConsumer();
        var movedProvider = movedConsumer.nextProvider();

        assertEquals(THIRD, movedConsumer.key().consumerNetworkId());
        assertEquals(FIRST, movedProvider.key().providerNetworkId());
    }

    @Test
    void capabilityCyclingIsBoundedByTheProductionEnum() {
        var selection = PolicyEditorSelection.initial(List.of(FIRST, SECOND));

        for (var ignored : PolicyCapability.values()) {
            selection = selection.nextCapability();
        }

        assertEquals(PolicyCapability.STORAGE, selection.key().capability());
    }

    @Test
    void malformedSelectionIndicesFailBeforePolicyMutation() {
        assertThrows(IllegalArgumentException.class,
                () -> new PolicyEditorSelection(List.of(FIRST, SECOND), 2, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new PolicyEditorSelection(List.of(FIRST, SECOND), 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new PolicyEditorSelection(List.of(FIRST, SECOND), 0, 1, PolicyCapability.values().length));
    }
}
