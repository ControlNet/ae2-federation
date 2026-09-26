package space.controlnet.ae2federation.client.policy;

/** Explains why a device entrance cannot select a unique editable domain. */
enum DeviceDomainAvailability {
    AVAILABLE,
    UNCONFIRMED,
    NO_DOMAIN,
    AMBIGUOUS;

    static DeviceDomainAvailability classify(boolean networkConfirmed, int candidateCount) {
        if (!networkConfirmed) return UNCONFIRMED;
        if (candidateCount == 0) return NO_DOMAIN;
        return candidateCount == 1 ? AVAILABLE : AMBIGUOUS;
    }

    String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
