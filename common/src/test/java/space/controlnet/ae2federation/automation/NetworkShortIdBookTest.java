package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.identity.NetworkId;

/** The short ID programs name a network by: the start of its NetworkId, as the topology shows it, kept once given. */
final class NetworkShortIdBookTest {
    private static final NetworkId MAIN = network("082a5c1e-0000-4000-8000-000000000001");
    private static final NetworkId FARM = network("4f1c9d2b-0000-4000-8000-000000000002");
    // Starts as MAIN does: the topology shows both as "Network 082A5C".
    private static final NetworkId TWIN = network("082a5c00-0000-4000-8000-000000000003");

    private static NetworkId network(String uuid) {
        return new NetworkId(UUID.fromString(uuid));
    }

    @Test
    void aNetworksIdIsTheDigitsOfItsTopologyTag() {
        var book = new NetworkShortIdBook();
        assertEquals("network-082a5c", book.id(MAIN));
        assertEquals("network-082a5c", book.id(MAIN));
        assertEquals(Optional.of(MAIN), book.network("network-082a5c"));
        assertEquals(Optional.of(MAIN), book.network("network-082A5C"), "the topology writes the digits in capitals");
        assertEquals(Optional.empty(), book.network("network-ffffff"));
    }

    @Test
    void aNetworkStartingAsAnotherTakesMoreDigits() {
        var book = new NetworkShortIdBook();
        assertEquals("network-082a5c", book.id(MAIN));
        assertEquals("network-082a5c0", book.id(TWIN), "082a5c is taken");
        assertEquals("network-082a5c", book.id(MAIN), "the first one keeps its ID");
    }

    @Test
    void idsAreShapedAsTheStartOfANetworkId() {
        assertTrue(NetworkShortIdBook.valid("network-082a5c"));
        assertTrue(NetworkShortIdBook.valid("network-082A5C0D"));
        assertTrue(NetworkShortIdBook.valid("network-082a5c1e000040008000000000000001"));
        assertFalse(NetworkShortIdBook.valid("network-082a"), "fewer digits than a tag");
        assertFalse(NetworkShortIdBook.valid("network-p3x9ka"), "not hexadecimal");
        assertFalse(NetworkShortIdBook.valid("082a5c"));
    }

    @Test
    void loadedRecordsAreKeptWhenTheyStartTheirNetworkId() {
        var book = new NetworkShortIdBook();
        assertTrue(book.put(TWIN, "network-082a5c00"));
        assertFalse(book.put(MAIN, "network-082a5c00"), "taken");
        assertFalse(book.put(FARM, "network-082a5c"), "not the start of its own NetworkId");
        assertFalse(book.put(FARM, "network-p3x9ka"), "a randomly drawn ID of an earlier build");
        assertEquals("network-082a5c00", book.id(TWIN));
        assertEquals("network-4f1c9d", book.id(FARM));
    }

    @Test
    void aFoldedNetworksIdNamesTheSurvivor() {
        var book = new NetworkShortIdBook();
        var main = book.id(MAIN);
        var farm = book.id(FARM);
        book.fold(FARM, MAIN);
        assertEquals(Optional.of(MAIN), book.network(farm), "a program naming the folded network reaches the survivor");
        assertEquals(main, book.id(MAIN), "the survivor keeps its own ID");
        assertEquals(Map.of(MAIN, main), book.entries());
        assertEquals(Map.of(farm, MAIN), book.aliases());
    }

    @Test
    void aSurvivorWithoutAnIdIsGivenItsOwn() {
        var book = new NetworkShortIdBook();
        var farm = book.id(FARM);
        book.fold(FARM, MAIN);
        assertEquals("network-082a5c", book.id(MAIN), "its ID is still the start of its own NetworkId");
        assertEquals(Optional.of(MAIN), book.network(farm));
        assertEquals(Map.of(farm, MAIN), book.aliases());
    }

    @Test
    void loadedAliasesNameTheirNetworkAndAClashIsRefused() {
        var book = new NetworkShortIdBook();
        assertTrue(book.put(MAIN, "network-082a5c"));
        assertTrue(book.alias("network-4f1c9d", MAIN));
        assertFalse(book.alias("network-082a5c", FARM), "an ID already taken");
        assertFalse(book.alias("network-p3x9ka", FARM), "not shaped as an ID");
        assertEquals(Optional.of(MAIN), book.network("network-4f1c9d"));
        assertEquals("network-4f1c9d2", book.id(FARM), "a network whose start is an alias takes more digits");
    }
}
