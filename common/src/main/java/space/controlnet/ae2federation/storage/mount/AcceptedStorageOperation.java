package space.controlnet.ae2federation.storage.mount;

import appeng.api.stacks.AEKey;
import java.util.Objects;
import space.controlnet.ae2federation.observability.meter.OperationEventId;

public record AcceptedStorageOperation(OperationEventId eventId, AEKey resource, long amount) {
    public AcceptedStorageOperation {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(resource);
        if (amount < 1) {
            throw new IllegalArgumentException("Accepted storage amount must be positive");
        }
    }
}
