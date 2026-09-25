package space.controlnet.ae2federation.client.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.domain.FederationDomainId;
import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.policy.PolicyRevision;

class FederationDomainPolicyActionRequestTest {
    @Test
    void preservesServerIssuedMenuAuthority() {
        var nonce = UUID.randomUUID();
        var scope = new FederationDomainReference(new FederationDomainId("physical:menu-authority"), 7);

        var request = new FederationDomainPolicyActionRequest(FederationDomainPolicyAction.TOGGLE_POLICY, 12, nonce, 3, scope,
                new PolicyRevision(4));

        assertEquals(FederationDomainPolicyAction.TOGGLE_POLICY, request.action());
        assertEquals(12, request.containerId());
        assertEquals(nonce, request.menuNonce());
        assertEquals(3, request.menuSequence());
        assertEquals(scope, request.context());
        assertEquals(new PolicyRevision(4), request.expectedRevision());
    }

    @Test
    void rejectsUnboundedContainerIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new FederationDomainPolicyActionRequest(
                FederationDomainPolicyAction.NEXT_CONSUMER, -1, UUID.randomUUID(), 0,
                new FederationDomainReference(new FederationDomainId("physical:menu-authority"), 7), PolicyRevision.NONE));
    }

    @Test
    void rejectsMissingAuthorityFields() {
        var scope = new FederationDomainReference(new FederationDomainId("physical:menu-authority"), 7);
        assertThrows(NullPointerException.class, () -> new FederationDomainPolicyActionRequest(
                FederationDomainPolicyAction.NEXT_CONSUMER, 1, null, 0, scope, PolicyRevision.NONE));
    }
}
