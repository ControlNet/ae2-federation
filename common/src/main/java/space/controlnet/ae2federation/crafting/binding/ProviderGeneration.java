package space.controlnet.ae2federation.crafting.binding;

public record ProviderGeneration(long value) {
    public ProviderGeneration {
        if (value < 1) {
            throw new IllegalArgumentException("Provider generation must be positive");
        }
    }
}
