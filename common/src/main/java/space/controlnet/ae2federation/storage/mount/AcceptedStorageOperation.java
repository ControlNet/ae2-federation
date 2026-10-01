package space.controlnet.ae2federation.storage.mount;

import appeng.api.stacks.AEKey;
import java.util.Objects;

/** One accepted storage operation; the meter gives it an event id of its own if it keeps its flow. */
public record AcceptedStorageOperation(AEKey resource, long amount) {
    public AcceptedStorageOperation {
        Objects.requireNonNull(resource);
        if (amount < 1) {
            throw new IllegalArgumentException("Accepted storage amount must be positive");
        }
    }
}
