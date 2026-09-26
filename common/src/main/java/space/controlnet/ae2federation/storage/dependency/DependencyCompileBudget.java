package space.controlnet.ae2federation.storage.dependency;

public record DependencyCompileBudget(int maxRelationships, int maxFrontierRelaxations) {
    public DependencyCompileBudget {
        if (maxRelationships < 1 || maxFrontierRelaxations < 1) {
            throw new IllegalArgumentException("Dependency compilation budgets must be positive");
        }
    }

    public static DependencyCompileBudget standard() {
        return new DependencyCompileBudget(16_384, 262_144);
    }
}
