package space.controlnet.ae2federation.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.identity.NetworkId;

class PolicyKeyTest {
    @Test
    void equalKeysHashAlikeAndEachCapabilityIsItsOwnKey() {
        var consumer = new NetworkId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        var provider = new NetworkId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        var keys = new HashMap<PolicyKey, PolicyCapability>();
        for (var capability : PolicyCapability.values()) {
            var key = new PolicyKey(consumer, provider, capability);
            var copy = new PolicyKey(new NetworkId(consumer.value()), new NetworkId(provider.value()), capability);
            assertEquals(key, copy);
            assertEquals(key.hashCode(), copy.hashCode());
            keys.put(key, capability);
        }
        for (var capability : PolicyCapability.values()) {
            assertEquals(capability, keys.get(new PolicyKey(consumer, provider, capability)));
        }
        assertNotEquals(new PolicyKey(consumer, provider, PolicyCapability.STORAGE),
                new PolicyKey(provider, consumer, PolicyCapability.STORAGE));
    }
}
