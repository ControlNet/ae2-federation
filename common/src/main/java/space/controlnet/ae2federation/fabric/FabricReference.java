package space.controlnet.ae2federation.fabric;

public record FabricReference(FabricId fabricId, long generation) {
    public FabricReference {
        java.util.Objects.requireNonNull(fabricId);
        if (generation < 0) {
            throw new IllegalArgumentException("Fabric generation cannot be negative");
        }
    }
}
