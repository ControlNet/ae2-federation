package space.controlnet.ae2federation.policy;

import java.util.Collections;
import space.controlnet.ae2federation.identity.IdentitySettlement;

final class PolicyActivation {
    private PolicyActivation() {
    }

    static PolicyActivationState classify(PolicyActivationRequest request) {
        var configured = request.configured().orElse(null);
        if (configured == null) {
            return PolicyActivationState.UNCONFIGURED;
        }
        if (!configured.rule().enabled()) {
            return PolicyActivationState.OFF;
        }
        if (!matches(request.consumerIdentity(), request.key().consumerNetworkId())
                || !matches(request.providerIdentity(), request.key().providerNetworkId())) {
            return PolicyActivationState.DISCONNECTED;
        }
        var consumerFederationDomains = request.federationDomainRegistry().federationdomainsFor(request.key().consumerNetworkId());
        var providerFederationDomains = request.federationDomainRegistry().federationdomainsFor(request.key().providerNetworkId());
        if (Collections.disjoint(consumerFederationDomains, providerFederationDomains)) {
            return PolicyActivationState.DISCONNECTED;
        }
        return switch (request.backendStatus()) {
            case UNREADY -> PolicyActivationState.BACKEND_UNREADY;
            case READY -> PolicyActivationState.ACTIVE;
        };
    }

    private static boolean matches(IdentitySettlement settlement,
            space.controlnet.ae2federation.identity.NetworkId expected) {
        return settlement.canInheritPolicy() && settlement.networkId().filter(expected::equals).isPresent();
    }
}
