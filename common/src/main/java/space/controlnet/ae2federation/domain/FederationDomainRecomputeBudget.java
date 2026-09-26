package space.controlnet.ae2federation.domain;

public record FederationDomainRecomputeBudget(int maxNodeVisits, int maxPortVisits) {
    public FederationDomainRecomputeBudget {
        if (maxNodeVisits < 1 || maxPortVisits < 1) {
            throw new IllegalArgumentException("Federation Domain recomputation budgets must be positive");
        }
    }

    public static FederationDomainRecomputeBudget standard() {
        return new FederationDomainRecomputeBudget(4096, 24576);
    }
}
