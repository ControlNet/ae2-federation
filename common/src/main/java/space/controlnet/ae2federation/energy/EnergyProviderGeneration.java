package space.controlnet.ae2federation.energy;

public record EnergyProviderGeneration(long value) {
    public EnergyProviderGeneration {
        if (value < 1) {
            throw new IllegalArgumentException("Energy provider generation must be positive");
        }
    }
}
