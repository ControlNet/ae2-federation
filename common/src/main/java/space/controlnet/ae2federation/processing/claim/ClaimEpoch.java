package space.controlnet.ae2federation.processing.claim;

public record ClaimEpoch(long value) implements Comparable<ClaimEpoch> {
    public static final ClaimEpoch NONE = new ClaimEpoch(0);

    public ClaimEpoch {
        if (value < 0) {
            throw new IllegalArgumentException("Claim epoch cannot be negative");
        }
    }

    public ClaimEpoch next() {
        return new ClaimEpoch(Math.addExact(value, 1));
    }

    @Override
    public int compareTo(ClaimEpoch other) {
        return Long.compare(value, other.value);
    }
}
