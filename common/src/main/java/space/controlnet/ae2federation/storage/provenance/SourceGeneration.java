package space.controlnet.ae2federation.storage.provenance;

public record SourceGeneration(long value) {
    public SourceGeneration {
        if (value < 1) {
            throw new IllegalArgumentException("Source generation must be positive");
        }
    }
}
