package space.controlnet.ae2federation.domain;

import java.util.OptionalInt;

/**
 * The server's cable capacity rule: how many units one Federation Cable carries, and whether that limit is enforced. A
 * Federation domain with any cable over the limit stops as a whole ({@link FederationDomainRegistry#overCapacity}). The
 * server configuration sets it when it loads; until then, and in tests, the defaults hold.
 */
public final class CableCapacityLimit {
    public static final int DEFAULT_CAPACITY = 8;
    public static final int MAX_CAPACITY = 1024;

    private static volatile boolean enforced = true;
    private static volatile int capacity = DEFAULT_CAPACITY;

    private CableCapacityLimit() {
    }

    /** The units one cable may carry, or empty when capacity is not enforced. */
    public static OptionalInt current() {
        return enforced ? OptionalInt.of(capacity) : OptionalInt.empty();
    }

    /**
     * The units a cable node of {@code capacityFactor} cables may carry, or empty when capacity is not enforced or the
     * factor is {@link FederationDomainNodeEvidence#UNLIMITED}.
     */
    public static OptionalInt current(int capacityFactor) {
        return enforced && capacityFactor != FederationDomainNodeEvidence.UNLIMITED
                ? OptionalInt.of(capacity * capacityFactor) : OptionalInt.empty();
    }

    /** The units one cable may carry as configured, enforced or not. */
    public static int capacity() {
        return capacity;
    }

    /** Sets the rule; every registry judges its domains again on its next read. */
    public static void set(boolean enforce, int units) {
        if (units < 1 || units > MAX_CAPACITY) throw new IllegalArgumentException("Cable capacity out of range: " + units);
        capacity = units;
        enforced = enforce;
    }
}
