package space.controlnet.ae2federation.storage.provenance;

public record MountGeneration(long value) {
    public MountGeneration {
        if (value < 1) {
            throw new IllegalArgumentException("Mount generation must be positive");
        }
    }

    public MountGeneration next() {
        return new MountGeneration(Math.incrementExact(value));
    }
}
