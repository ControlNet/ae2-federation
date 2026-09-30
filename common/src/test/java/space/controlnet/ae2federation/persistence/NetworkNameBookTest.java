package space.controlnet.ae2federation.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.identity.NetworkId;

class NetworkNameBookTest {
    private static final NetworkId NETWORK = new NetworkId(UUID.fromString("3f9a0000-0000-4000-8000-000000000c21"));

    @Test
    void sanitizingTrimsAndCountsCodePoints() {
        assertEquals(Optional.of("主基地"), NetworkNameBook.sanitize("  主基地  "));
        assertEquals(Optional.of("😀".repeat(32)), NetworkNameBook.sanitize("😀".repeat(32)));
        assertTrue(NetworkNameBook.sanitize("😀".repeat(33)).isEmpty());
    }

    @Test
    void sanitizingRejectsControlCharactersAndFormattingCodes() {
        assertTrue(NetworkNameBook.sanitize("Main\nBase").isEmpty());
        assertTrue(NetworkNameBook.sanitize("§cRed").isEmpty());
    }

    @Test
    void renamingStoresClearsAndReportsChanges() {
        var names = new NetworkNameBook();
        assertTrue(names.rename(NETWORK, "Main Base"));
        assertEquals(Optional.of("Main Base"), names.name(NETWORK));
        assertTrue(!names.rename(NETWORK, "Main Base"));
        assertTrue(names.rename(NETWORK, ""));
        assertTrue(names.name(NETWORK).isEmpty());
    }
}
