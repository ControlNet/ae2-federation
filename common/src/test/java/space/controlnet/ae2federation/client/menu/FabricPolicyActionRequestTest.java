package space.controlnet.ae2federation.client.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.fabric.FabricId;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.policy.PolicyRevision;

class FabricPolicyActionRequestTest {
    @Test
    void preservesServerIssuedMenuAuthority() {
        var nonce = UUID.randomUUID();
        var scope = new FabricReference(new FabricId("physical:menu-authority"), 7);

        var request = new FabricPolicyActionRequest(FabricPolicyAction.TOGGLE_POLICY, 12, nonce, 3, scope,
                new PolicyRevision(4));

        assertEquals(FabricPolicyAction.TOGGLE_POLICY, request.action());
        assertEquals(12, request.containerId());
        assertEquals(nonce, request.menuNonce());
        assertEquals(3, request.menuSequence());
        assertEquals(scope, request.context());
        assertEquals(new PolicyRevision(4), request.expectedRevision());
    }

    @Test
    void rejectsUnboundedContainerIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new FabricPolicyActionRequest(
                FabricPolicyAction.NEXT_CONSUMER, -1, UUID.randomUUID(), 0,
                new FabricReference(new FabricId("physical:menu-authority"), 7), PolicyRevision.NONE));
    }

    @Test
    void rejectsMissingAuthorityFields() {
        var scope = new FabricReference(new FabricId("physical:menu-authority"), 7);
        assertThrows(NullPointerException.class, () -> new FabricPolicyActionRequest(
                FabricPolicyAction.NEXT_CONSUMER, 1, null, 0, scope, PolicyRevision.NONE));
    }
}
