package space.controlnet.ae2federation.fabric;

public record FabricRecomputeBudget(int maxNodeVisits, int maxPortVisits) {
    public FabricRecomputeBudget {
        if (maxNodeVisits < 1 || maxPortVisits < 1) {
            throw new IllegalArgumentException("Fabric recomputation budgets must be positive");
        }
    }

    public static FabricRecomputeBudget standard() {
        return new FabricRecomputeBudget(4096, 24576);
    }
}
