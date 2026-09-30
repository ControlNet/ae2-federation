package space.controlnet.ae2federation.observability.meter;

import java.util.Objects;
import java.util.UUID;

public record OperationEventId(UUID value) {
    public OperationEventId {
        Objects.requireNonNull(value);
    }

    /**
     * A fresh random (version 4 layout) id. It only has to be unique within a meter window, so it draws from
     * {@link java.util.concurrent.ThreadLocalRandom} instead of the SecureRandom behind {@link UUID#randomUUID()}.
     */
    public static OperationEventId create() {
        var random = java.util.concurrent.ThreadLocalRandom.current();
        var most = (random.nextLong() & ~0xF000L) | 0x4000L;
        var least = (random.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new OperationEventId(new UUID(most, least));
    }
}
