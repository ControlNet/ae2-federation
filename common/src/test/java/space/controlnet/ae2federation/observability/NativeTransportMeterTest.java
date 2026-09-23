package space.controlnet.ae2federation.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.fabric.FabricId;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.observability.id.FlowId;
import space.controlnet.ae2federation.observability.meter.NativeTransportMeter;
import space.controlnet.ae2federation.observability.meter.OperationEventId;
import space.controlnet.ae2federation.observability.state.FlowState;
import space.controlnet.ae2federation.observability.state.ResourceUnit;

class NativeTransportMeterTest {
    private static final FabricReference SCOPE = new FabricReference(new FabricId("physical:meter"), 4);

    @Test
    void metersEachAcceptedOperationExactlyOnceWithoutCollapsingEqualOperations() {
        var meter = new NativeTransportMeter(4);
        var firstEvent = new OperationEventId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        var secondEvent = new OperationEventId(UUID.fromString("00000000-0000-0000-0000-000000000002"));

        assertTrue(meter.recordAccepted(SCOPE, firstEvent, "minecraft:iron_ingot", 8, ResourceUnit.ITEM,
                FlowState.Attribution.EXACT_OPERATION));
        assertFalse(meter.recordAccepted(SCOPE, firstEvent, "minecraft:iron_ingot", 8, ResourceUnit.ITEM,
                FlowState.Attribution.EXACT_OPERATION));
        assertTrue(meter.recordAccepted(SCOPE, secondEvent, "minecraft:iron_ingot", 8, ResourceUnit.ITEM,
                FlowState.Attribution.EXACT_OPERATION));
        assertFalse(meter.recordAccepted(SCOPE, new OperationEventId(UUID.randomUUID()), "minecraft:iron_ingot", 0,
                ResourceUnit.ITEM, FlowState.Attribution.EXACT_OPERATION));

        var window = meter.window(SCOPE);
        assertEquals(2, window.events().size());
        assertEquals(16, window.events().stream().mapToLong(FlowState::amount).sum());
        assertEquals(2, window.dataRevision());
    }

    @Test
    void overflowCollapsesToExplicitResnapshot() {
        var meter = new NativeTransportMeter(2);
        for (var index = 0; index < 3; index++) {
            meter.recordAccepted(SCOPE, new OperationEventId(new UUID(0, index + 1)), "ae2:energy", 1,
                    ResourceUnit.NANO_AE, FlowState.Attribution.EXACT_OPERATION);
        }

        var window = meter.window(SCOPE);
        assertTrue(window.resnapshotRequired());
        assertTrue(window.events().isEmpty());
        assertEquals(3, window.dataRevision());
        assertThrows(IllegalArgumentException.class, () -> meter.recordAccepted(SCOPE,
                new OperationEventId(UUID.randomUUID()), "ae2:energy", -1, ResourceUnit.NANO_AE,
                FlowState.Attribution.EXACT_OPERATION));
    }

    @Test
    void aggregateReturnsCannotClaimExactBatchCompletion() {
        assertThrows(IllegalArgumentException.class, () -> new FlowState(SCOPE,
                FlowId.forEvent(SCOPE.fabricId(), UUID.randomUUID()), new OperationEventId(UUID.randomUUID()),
                "minecraft:diamond", 1, ResourceUnit.ITEM, FlowState.Attribution.AGGREGATE_LANE_RETURN, true));
    }

    @Test
    void successfulResnapshotClearsOverflowWithoutForgettingOperationDeduplication() {
        var meter = new NativeTransportMeter(1);
        var first = new OperationEventId(new UUID(0, 1));
        var second = new OperationEventId(new UUID(0, 2));
        meter.recordAccepted(SCOPE, first, "ae2:energy", 1, ResourceUnit.NANO_AE,
                FlowState.Attribution.EXACT_OPERATION);
        meter.recordAccepted(SCOPE, second, "ae2:energy", 1, ResourceUnit.NANO_AE,
                FlowState.Attribution.EXACT_OPERATION);

        meter.acknowledgeSnapshot(SCOPE);

        assertFalse(meter.window(SCOPE).resnapshotRequired());
        assertFalse(meter.recordAccepted(SCOPE, second, "ae2:energy", 1, ResourceUnit.NANO_AE,
                FlowState.Attribution.EXACT_OPERATION));
    }

    @Test
    void oversizedAmountLeavesMeterStateAndDeduplicationUnchanged() {
        var meter = new NativeTransportMeter(4);
        var event = new OperationEventId(UUID.randomUUID());
        var before = meter.window(SCOPE);

        assertThrows(IllegalArgumentException.class, () -> meter.recordAccepted(SCOPE, event, "minecraft:diamond",
                ObservationLimits.MAX_RESOURCE_AMOUNT + 1, ResourceUnit.ITEM,
                FlowState.Attribution.EXACT_OPERATION));

        assertEquals(before, meter.window(SCOPE));
        assertTrue(meter.recordAccepted(SCOPE, event, "minecraft:diamond", ObservationLimits.MAX_RESOURCE_AMOUNT,
                ResourceUnit.ITEM, FlowState.Attribution.EXACT_OPERATION));
        assertFalse(meter.recordAccepted(SCOPE, event, "minecraft:diamond", ObservationLimits.MAX_RESOURCE_AMOUNT,
                ResourceUnit.ITEM, FlowState.Attribution.EXACT_OPERATION));
        assertEquals(1, meter.window(SCOPE).dataRevision());
        assertEquals(ObservationLimits.MAX_RESOURCE_AMOUNT, meter.window(SCOPE).events().getFirst().amount());
    }

    @Test
    void largeNativeEnergyAcceptanceRemainsObservableInNanoAe() {
        var meter = new NativeTransportMeter(4);
        var acceptedNanoAe = 1_000_000_250_000_000_000L;

        assertTrue(meter.recordAccepted(SCOPE, OperationEventId.create(), "ae2:energy", acceptedNanoAe,
                ResourceUnit.NANO_AE, FlowState.Attribution.EXACT_OPERATION));

        assertEquals(acceptedNanoAe, meter.window(SCOPE).events().getFirst().amount());
    }

    @Test
    void nanoAeObservationsRejectAmountsBeyondTheirOwnBound() {
        var meter = new NativeTransportMeter(4);
        var event = OperationEventId.create();

        assertThrows(IllegalArgumentException.class, () -> meter.recordAccepted(SCOPE, event, "ae2:energy",
                ObservationLimits.MAX_NANO_AE_AMOUNT + 1, ResourceUnit.NANO_AE,
                FlowState.Attribution.EXACT_OPERATION));

        assertEquals(0, meter.window(SCOPE).dataRevision());
        assertTrue(meter.recordAccepted(SCOPE, event, "ae2:energy", ObservationLimits.MAX_NANO_AE_AMOUNT,
                ResourceUnit.NANO_AE, FlowState.Attribution.EXACT_OPERATION));
    }

    @Test
    void fluidObservationsRetainTheExternalResourceBound() {
        var meter = new NativeTransportMeter(4);

        assertThrows(IllegalArgumentException.class, () -> meter.recordAccepted(SCOPE, OperationEventId.create(),
                "minecraft:water", ObservationLimits.MAX_RESOURCE_AMOUNT + 1, ResourceUnit.FLUID_DROPLET,
                FlowState.Attribution.EXACT_OPERATION));
    }
}
