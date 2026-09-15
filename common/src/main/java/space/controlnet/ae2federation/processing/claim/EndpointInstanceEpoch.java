package space.controlnet.ae2federation.processing.claim;

public record EndpointInstanceEpoch(long value) {
    public EndpointInstanceEpoch {
        if (value < 1) {
            throw new IllegalArgumentException("Endpoint instance epoch must be positive");
        }
    }
}
