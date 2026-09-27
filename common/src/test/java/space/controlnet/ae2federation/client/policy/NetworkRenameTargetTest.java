package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.identity.NetworkId;

class NetworkRenameTargetTest {
    private static final NetworkId NETWORK = new NetworkId(UUID.fromString("3f9a0000-0000-4000-8000-000000000c21"));

    @Test
    void roundTripsANameContainingTheSeparator() {
        var target = new NetworkRenameTarget(NETWORK, "Main/North base");
        assertEquals(target, NetworkRenameTarget.parse(target.encode()).orElseThrow());
    }

    @Test
    void anEmptyNameClearsTheName() {
        var parsed = NetworkRenameTarget.parse(NETWORK.value() + "/").orElseThrow();
        assertEquals("", parsed.name());
    }

    @Test
    void rejectsMalformedTargets() {
        assertTrue(NetworkRenameTarget.parse("").isEmpty());
        assertTrue(NetworkRenameTarget.parse("not-a-uuid/Main").isEmpty());
        assertTrue(NetworkRenameTarget.parse(NETWORK.value().toString()).isEmpty());
    }

    @Test
    void rejectsNamesThatDoNotSurviveSanitizing() {
        assertTrue(NetworkRenameTarget.parse(NETWORK.value() + "/" + "x".repeat(33)).isEmpty());
        assertTrue(NetworkRenameTarget.parse(NETWORK.value() + "/Main\u0000").isEmpty());
    }
}
