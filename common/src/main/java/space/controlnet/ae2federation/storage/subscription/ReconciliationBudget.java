package space.controlnet.ae2federation.storage.subscription;

public record ReconciliationBudget(int providersPerService, int keysPerProvider) {
    public ReconciliationBudget {
        if (providersPerService < 1 || keysPerProvider < 1) {
            throw new IllegalArgumentException("Reconciliation budgets must be positive");
        }
    }

    public int maximumKeyProbes() {
        return Math.multiplyExact(providersPerService, keysPerProvider);
    }
}
