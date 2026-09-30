package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyRevision;

class PolicySwitchTargetTest {
    private static final NetworkId CONSUMER = new NetworkId(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
    private static final NetworkId PROVIDER = new NetworkId(UUID.fromString("00000000-0000-0000-0000-00000000000b"));

    @Test
    void roundTripsTheCompleteDirectionalKeyStateAndObservedRevision() {
        var target = new PolicySwitchTarget(new PolicyKey(CONSUMER, PROVIDER, PolicyCapability.ME_POWER), true,
                new PolicyRevision(42));

        var encoded = target.encode();

        assertEquals(target, PolicySwitchTarget.parse(encoded).orElseThrow());
        assertTrue(encoded.length() <= 160, "The target must fit the bounded action payload");
    }

    @Test
    void keepsDirectionDistinct() {
        var forward = new PolicySwitchTarget(new PolicyKey(CONSUMER, PROVIDER, PolicyCapability.STORAGE), false,
                PolicyRevision.NONE);
        var reverse = new PolicySwitchTarget(new PolicyKey(PROVIDER, CONSUMER, PolicyCapability.STORAGE), false,
                PolicyRevision.NONE);

        assertFalse(forward.encode().equals(reverse.encode()));
        assertEquals(PROVIDER, PolicySwitchTarget.parse(reverse.encode()).orElseThrow().key().consumerNetworkId());
    }

    @Test
    void rejectsMalformedSelfReferentialOrUnknownTargets() {
        var consumer = CONSUMER.value();
        var provider = PROVIDER.value();
        for (var value : new String[] { "", "a/b/STORAGE/1/0", consumer + "/" + provider + "/STORAGE/1",
                consumer + "/" + provider + "/TELEPORT/1/0", consumer + "/" + provider + "/STORAGE/yes/0",
                consumer + "/" + provider + "/STORAGE/1/-1", consumer + "/" + consumer + "/STORAGE/1/0",
                consumer + "/" + provider + "/STORAGE/1/0/extra" }) {
            assertTrue(PolicySwitchTarget.parse(value).isEmpty(), "Accepted malformed target: " + value);
        }
    }
}
