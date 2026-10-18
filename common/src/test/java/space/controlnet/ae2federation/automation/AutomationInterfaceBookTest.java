package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** Every interface's ID, unique on the server, where it is, and its alias, unique within its domain. */
final class AutomationInterfaceBookTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final BlockPos HERE = new BlockPos(1, 64, 2);
    private static final BlockPos THERE = new BlockPos(9, 70, -3);

    @Test
    void aNewInterfaceDrawsAnIdAndKeepsItWhereItIs() {
        var book = new AutomationInterfaceBook();
        var id = book.claim(null, OVERWORLD, HERE, new Random(3));
        assertTrue(ShortId.valid(AutomationInterfaceBook.PREFIX, id));
        assertEquals(id, book.claim(id, OVERWORLD, HERE, new Random(4)), "reloading keeps the ID");
        assertEquals(Optional.of(new AutomationInterfaceBook.Entry(OVERWORLD, HERE, "")), book.entry(id));
    }

    @Test
    void aCopyElsewhereDrawsAnotherId() {
        var book = new AutomationInterfaceBook();
        var id = book.claim(null, OVERWORLD, HERE, new Random(3));
        var copy = book.claim(id, OVERWORLD, THERE, new Random(3));
        assertNotEquals(id, copy, "a structure copy carries the ID to another position");
        assertEquals(HERE, book.entry(id).orElseThrow().position());
        assertEquals(THERE, book.entry(copy).orElseThrow().position());
        assertNotEquals(book.claim(null, "minecraft:the_nether", HERE, new Random(3)), id, "never a taken one");
    }

    @Test
    void aBrokenInterfaceGivesItsIdBackAndAMovedOneKeepsIt() {
        var book = new AutomationInterfaceBook();
        var id = book.claim(null, OVERWORLD, HERE, new Random(3));
        book.release(id);
        assertEquals(Optional.empty(), book.entry(id));
        assertEquals(id, book.claim(id, OVERWORLD, THERE, new Random(5)),
                "picked up with a wrench and placed again: its ID is free, so it keeps it");
        assertEquals(THERE, book.entry(id).orElseThrow().position());
    }

    @Test
    void anInvalidStoredIdIsReplaced() {
        var book = new AutomationInterfaceBook();
        var id = book.claim("interface-OOPS!!", OVERWORLD, HERE, new Random(3));
        assertTrue(ShortId.valid(AutomationInterfaceBook.PREFIX, id));
    }

    @Test
    void anAliasIsUniqueWithinTheDomainOnly() {
        var book = new AutomationInterfaceBook();
        var a = book.claim(null, OVERWORLD, HERE, new Random(1));
        var b = book.claim(null, OVERWORLD, THERE, new Random(2));
        assertEquals(AutomationInterfaceBook.AliasResult.SET, book.setAlias(a, "  north_gate ", Set.of(a, b)));
        assertEquals("north_gate", book.entry(a).orElseThrow().alias(), "trimmed");
        assertEquals(AutomationInterfaceBook.AliasResult.TAKEN, book.setAlias(b, "north_gate", Set.of(a, b)));
        assertEquals(AutomationInterfaceBook.AliasResult.SET, book.setAlias(b, "north_gate", Set.of(b)),
                "another domain may use it");
        assertEquals(AutomationInterfaceBook.AliasResult.UNCHANGED, book.setAlias(a, "north_gate", Set.of(a)));
        assertEquals(AutomationInterfaceBook.AliasResult.SET, book.setAlias(a, "", Set.of(a, b)), "cleared");
        assertEquals("", book.entry(a).orElseThrow().alias());
    }

    @Test
    void anAliasCannotPassForAnIdOrHoldControlCharacters() {
        var book = new AutomationInterfaceBook();
        var a = book.claim(null, OVERWORLD, HERE, new Random(1));
        for (var bad : java.util.List.of("interface-k7m2qx", "line\nbreak", "§cred", "x".repeat(33))) {
            assertEquals(AutomationInterfaceBook.AliasResult.INVALID, book.setAlias(a, bad, Set.of(a)), bad);
        }
        assertEquals(AutomationInterfaceBook.AliasResult.SET, book.setAlias(a, "北门", Set.of(a)));
    }

    @Test
    void anInterfaceIsFoundByIdOrAlias() {
        var book = new AutomationInterfaceBook();
        var a = book.claim(null, OVERWORLD, HERE, new Random(1));
        var b = book.claim(null, OVERWORLD, THERE, new Random(2));
        book.setAlias(a, "north_gate", Set.of(a, b));
        assertEquals(Optional.of(a), book.resolve("north_gate", Set.of(a, b)));
        assertEquals(Optional.of(b), book.resolve(b, Set.of(a, b)));
        assertEquals(Optional.empty(), book.resolve("north_gate", Set.of(b)), "only within the given domain");
        assertEquals(Optional.empty(), book.resolve("nowhere", Set.of(a, b)));
    }
}
