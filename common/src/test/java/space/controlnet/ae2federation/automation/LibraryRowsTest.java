package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.script.Signature;
import space.controlnet.ae2federation.script.StandardLibrary;
import space.controlnet.ae2federation.script.TypeRef;

/** The Library panel's rows: namespaces with their counts, and the functions of the open ones or of a search. */
final class LibraryRowsTest {
    private static Signature fn(String name, boolean acts) {
        return new Signature(name, List.of(), new TypeRef("int", List.of()), "", acts, "");
    }

    private static final Map<String, List<Signature>> LIBRARY = Map.of(
            "mc", List.of(fn("amount", false), fn("set_redstone", true)),
            "ae2", List.of(fn("amount", false), fn("craftable", false), fn("craft", true)));

    private static List<String> labels(List<LibraryRows.Row> rows) {
        return rows.stream().map(row -> row.function().isEmpty()
                ? row.namespace() + (row.open() ? " v " : " > ") + row.count()
                : "  " + row.function() + (row.acts() ? " *" : "")).toList();
    }

    @Test
    void namespacesComeSortedAndClosedWithTheirCounts() {
        assertEquals(List.of("ae2 > 3", "mc > 2"), labels(LibraryRows.rows(LIBRARY, "", Set.of())));
    }

    @Test
    void anOpenNamespaceListsItsFunctionsInTheLibrarysOrder() {
        assertEquals(List.of("ae2 v 3", "  amount", "  craftable", "  craft *", "mc > 2"),
                labels(LibraryRows.rows(LIBRARY, "", Set.of("ae2"))));
    }

    @Test
    void aSearchOpensTheNamespacesWithMatchesAndCountsThem() {
        assertEquals(List.of("ae2 v 2", "  craftable", "  craft *"),
                labels(LibraryRows.rows(LIBRARY, "CRAFT", Set.of())));
        assertEquals(List.of("ae2 v 1", "  amount", "mc v 1", "  amount"),
                labels(LibraryRows.rows(LIBRARY, " amount ", Set.of())));
    }

    @Test
    void aSearchMatchesTheQualifiedName() {
        assertEquals(List.of("mc v 1", "  amount"), labels(LibraryRows.rows(LIBRARY, "mc.am", Set.of())));
        assertEquals(List.of(), LibraryRows.rows(LIBRARY, "nothing", Set.of()));
    }

    @Test
    void aFunctionIsFoundByItsNamespaceAndName() {
        assertNotNull(LibraryRows.find(StandardLibrary.NAMESPACES, "ae2", "craft"));
        assertNull(LibraryRows.find(StandardLibrary.NAMESPACES, "ae2", "nothing"));
        assertNull(LibraryRows.find(StandardLibrary.NAMESPACES, "nothing", "craft"));
    }

    @Test
    void everyStandardFunctionHasAnExample() {
        StandardLibrary.NAMESPACES.forEach((namespace, functions) -> functions.forEach(function ->
                assertEquals(false, function.example().isBlank(), namespace + "." + function.name())));
    }
}
