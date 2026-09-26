package space.controlnet.ae2federation.observability;

public final class ObservationLimits {
    public static final int MAX_ID_LENGTH = 256;
    public static final int MAX_STRING_LENGTH = 256;
    public static final int MAX_MEMBERS = 256;
    public static final int MAX_PROVIDERS = 256;
    public static final int MAX_ENDPOINTS = 256;
    public static final int MAX_POLICIES = 1_024;
    public static final int MAX_LOCKS = 512;
    public static final int MAX_TASKS = 512;
    public static final int MAX_FLOWS = 128;
    public static final int MAX_DELTA_EVENTS = 64;
    public static final int MAX_PAYLOAD_BYTES = 1_048_576;
    public static final long MAX_RESOURCE_AMOUNT = 9_000_000_000_000_000L;
    public static final long MAX_NANO_AE_AMOUNT = 9_000_000_000_000_000_000L;
    public static final int MAX_SUBSCRIPTIONS_PER_PLAYER = 4;

    private ObservationLimits() {
    }

    public static String boundedString(String value, String field) {
        if (value == null || value.isBlank() || value.length() > MAX_STRING_LENGTH) {
            throw new IllegalArgumentException(field + " is blank or exceeds the bound");
        }
        return value;
    }

    public static void boundedCount(int size, int maximum, String field) {
        if (size < 0 || size > maximum) {
            throw new IllegalArgumentException(field + " exceeds the bound");
        }
    }
}
