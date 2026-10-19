package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.identity.NetworkId;

/** The short ID programs name a network by, drawn once per network and kept. */
final class NetworkShortIdBookTest {
    private static final NetworkId MAIN = new NetworkId(new UUID(1, 2));
    private static final NetworkId FARM = new NetworkId(new UUID(3, 4));

    @Test
    void aNetworkDrawsItsIdOnceAndKeepsIt() {
        var book = new NetworkShortIdBook();
        var id = book.id(MAIN, new Random(3));
        assertTrue(ShortId.valid(NetworkShortIdBook.PREFIX, id), id);
        assertEquals(id, book.id(MAIN, new Random(4)));
        assertEquals(Optional.of(MAIN), book.network(id));
        assertEquals(Optional.empty(), book.network("network-zzzzzz"));
    }

    @Test
    void twoNetworksNeverShareAnId() {
        var book = new NetworkShortIdBook();
        var first = book.id(MAIN, new Random(3));
        assertNotEquals(first, book.id(FARM, new Random(3)), "the same draw is taken, so it draws again");
    }

    @Test
    void loadedRecordsAreKeptAndAClashIsRefused() {
        var book = new NetworkShortIdBook();
        assertTrue(book.put(MAIN, "network-abcdef"));
        assertFalse(book.put(FARM, "network-abcdef"), "a second network with a saved ID is dropped");
        assertFalse(book.put(FARM, "farm"), "not an ID");
        assertEquals("network-abcdef", book.id(MAIN, new Random(3)));
        assertEquals(1, book.entries().size());
    }

    @Test
    void aFoldedNetworksIdNamesTheSurvivor() {
        var book = new NetworkShortIdBook();
        var main = book.id(MAIN, new Random(3));
        var farm = book.id(FARM, new Random(4));
        book.fold(FARM, MAIN);
        assertEquals(Optional.of(MAIN), book.network(farm), "a program naming the folded network reaches the survivor");
        assertEquals(main, book.id(MAIN, new Random(5)), "the survivor keeps its own ID");
        assertEquals(Map.of(MAIN, main), book.entries());
        assertEquals(Map.of(farm, MAIN), book.aliases());
    }

    @Test
    void aSurvivorWithoutAnIdTakesTheFoldedOne() {
        var book = new NetworkShortIdBook();
        var farm = book.id(FARM, new Random(4));
        book.fold(FARM, MAIN);
        assertEquals(farm, book.id(MAIN, new Random(5)));
        assertEquals(Map.of(), book.aliases());
    }

    @Test
    void loadedAliasesNameTheirNetworkAndAClashIsRefused() {
        var book = new NetworkShortIdBook();
        assertTrue(book.put(MAIN, "network-abcdef"));
        assertTrue(book.alias("network-ghjkmn", MAIN));
        assertFalse(book.alias("network-abcdef", FARM), "an ID already taken");
        assertEquals(Optional.of(MAIN), book.network("network-ghjkmn"));
        assertEquals("network-abcdef", book.id(MAIN, new Random(3)));
    }
}
