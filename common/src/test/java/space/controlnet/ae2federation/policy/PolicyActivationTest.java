package space.controlnet.ae2federation.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.domain.FederationDomainRecomputeBudget;
import space.controlnet.ae2federation.domain.FederationDomainRegistry;
import space.controlnet.ae2federation.domain.FederationDomainSourceId;
import space.controlnet.ae2federation.identity.IdentitySettlement;
import space.controlnet.ae2federation.identity.IdentityStatus;
import space.controlnet.ae2federation.identity.NetworkId;

final class PolicyActivationTest {
    private static final NetworkId CONSUMER = network(1);
    private static final NetworkId PROVIDER = network(2);
    private static final PolicyKey KEY = new PolicyKey(CONSUMER, PROVIDER, PolicyCapability.STORAGE);

    @Test
    void classifiesStatesWithDeterministicFailClosedPrecedence() {
        // Given
        var store = new PolicyStore();
        var registry = new FederationDomainRegistry(FederationDomainRecomputeBudget.standard());
        var disconnected = request(store, registry, settled(CONSUMER), settled(PROVIDER), BackendStatus.READY);

        // When / Then: absent configuration wins over every runtime condition.
        assertEquals(PolicyActivationState.UNCONFIGURED, PolicyActivation.classify(disconnected));

        var enabled = accepted(store.edit(new PolicyEdit(KEY, PolicyRevision.NONE, PolicyRule.storageDefaults())));
        var disabled = accepted(store.edit(new PolicyEdit(KEY, enabled.revision(),
                PolicyRule.storageDefaults().withEnabled(false))));

        // When / Then: explicit off wins over disconnection and backend readiness.
        assertEquals(PolicyActivationState.OFF, PolicyActivation.classify(
                request(store, registry, settled(CONSUMER), settled(PROVIDER), BackendStatus.READY)));

        var reenabled = accepted(store.edit(new PolicyEdit(KEY, disabled.revision(),
                PolicyRule.storageDefaults().withEnabled(true))));
        assertEquals(PolicyActivationState.DISCONNECTED, PolicyActivation.classify(
                request(store, registry, settled(CONSUMER), settled(PROVIDER), BackendStatus.READY)));

        registry.upsertDirectBridge(new FederationDomainSourceId("confirmed-bridge"), CONSUMER, PROVIDER);
        var unsettled = request(store, registry,
                new IdentitySettlement(IdentityStatus.AMBIGUOUS_SPLIT, Optional.of(CONSUMER)),
                settled(PROVIDER), BackendStatus.READY);
        assertEquals(PolicyActivationState.DISCONNECTED, PolicyActivation.classify(unsettled));

        var backendUnready = request(store, registry, settled(CONSUMER), settled(PROVIDER), BackendStatus.UNREADY);
        assertEquals(PolicyActivationState.BACKEND_UNREADY, PolicyActivation.classify(backendUnready));

        var active = request(store, registry, settled(CONSUMER), settled(PROVIDER), BackendStatus.READY);
        assertEquals(PolicyActivationState.ACTIVE, PolicyActivation.classify(active));
        assertEquals(reenabled.revision(), store.revision(KEY));
    }

    @Test
    void rejectsSettledIdentityThatDoesNotMatchDirectionalKey() {
        // Given
        var store = new PolicyStore();
        store.edit(new PolicyEdit(KEY, PolicyRevision.NONE, PolicyRule.storageDefaults()));
        var registry = new FederationDomainRegistry(FederationDomainRecomputeBudget.standard());
        registry.upsertDirectBridge(new FederationDomainSourceId("confirmed-bridge"), CONSUMER, PROVIDER);

        // When
        var state = PolicyActivation.classify(request(store, registry, settled(PROVIDER), settled(CONSUMER),
                BackendStatus.READY));

        // Then
        assertEquals(PolicyActivationState.DISCONNECTED, state);
    }

    private static PolicyActivationRequest request(PolicyStore store, FederationDomainRegistry registry,
            IdentitySettlement consumer, IdentitySettlement provider, BackendStatus backend) {
        return new PolicyActivationRequest(store.configured(KEY), KEY, consumer, provider, registry, backend);
    }

    private static PolicyRecord.Configured accepted(PolicyMutationResult result) {
        return (PolicyRecord.Configured) ((PolicyMutationResult.Accepted) result).record();
    }

    private static IdentitySettlement settled(NetworkId networkId) {
        return new IdentitySettlement(IdentityStatus.SETTLED, Optional.of(networkId));
    }

    private static NetworkId network(long value) {
        return new NetworkId(new UUID(0, value));
    }
}
