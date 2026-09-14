package space.controlnet.ae2federation.policy;

public record PolicyRevision(long value) implements Comparable<PolicyRevision> {
    public static final PolicyRevision NONE = new PolicyRevision(0);

    public PolicyRevision {
        if (value < 0) {
            throw new IllegalArgumentException("Policy revision cannot be negative");
        }
    }

    @Override
    public int compareTo(PolicyRevision other) {
        return Long.compare(value, other.value);
    }
}
