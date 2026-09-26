package space.controlnet.ae2federation.processing.provider;

public record ProviderInstanceEpoch(long value) {
    public ProviderInstanceEpoch {
        if (value < 1) {
            throw new IllegalArgumentException("Provider instance epoch must be positive");
        }
    }
}
