package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.script.Candidate;
import space.controlnet.ae2federation.script.Catalog;
import space.controlnet.ae2federation.script.Palette;
import space.controlnet.ae2federation.script.Program;
import space.controlnet.ae2federation.script.ScriptParser;
import space.controlnet.ae2federation.script.Signature;
import space.controlnet.ae2federation.script.StandardLibrary;
import space.controlnet.ae2federation.script.TypeRef;
import space.controlnet.ae2federation.script.Workspace;

/** The Library panel's rows: groups with their counts, and the entries of the open ones or of a search. */
final class LibraryRowsTest {
    private static Signature fn(String name, boolean acts) {
        return new Signature(name, List.of(), new TypeRef("int", List.of()), "", acts, "");
    }

    private static Palette.Group namespace(String name, Signature... functions) {
        return new Palette.Group(name, true, Stream.of(functions).map(f -> Palette.call(name, f)).toList());
    }

    private static final List<Palette.Group> LIBRARY = List.of(
            namespace("ae2", fn("amount", false), fn("craftable", false), fn("craft", true)),
            namespace("mc", fn("amount", false), fn("set_redstone", true)));

    /** Test-only catalogue: the standard library's namespaces and no item, interface or network values. */
    private static final Workspace WORKSPACE = new Workspace(Map.of(), new Catalog() {
        @Override
        public List<Candidate> values(TypeRef type, String prefix) {
            return List.of();
        }

        @Override
        public Set<String> kinds(String id) {
            return Set.of();
        }

        @Override
        public boolean exists(TypeRef type, String value) {
            return false;
        }
    });

    private static List<String> labels(List<LibraryRows.Row> rows) {
        return rows.stream().map(row -> row.function().isEmpty()
                ? row.group() + (row.open() ? " v " : " > ") + row.count()
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
    void longActionsAreMarkedApart() {
        var rows = LibraryRows.rows(Palette.groups(new Program(List.of(), List.of()), WORKSPACE), "net.", Set.of());
        var marks = rows.stream().filter(row -> !row.function().isEmpty())
                .map(row -> row.function() + (row.lasts() ? " long" : row.acts() ? " action" : "")).toList();
        assertEquals(List.of("http long", "connect long", "send long", "receive", "connected", "disconnect action"),
                marks);
    }

    @Test
    void anEntryIsFoundByItsGroupAndName() {
        var groups = Palette.groups(new Program(List.of(), List.of()), WORKSPACE);
        assertNotNull(LibraryRows.find(groups, "ae2", "craft"));
        assertNotNull(LibraryRows.find(groups, Palette.STATEMENTS, "while"));
        assertNull(LibraryRows.find(groups, "ae2", "nothing"));
        assertNull(LibraryRows.find(groups, "nothing", "craft"));
    }

    @Test
    void theStatementsOperatorsBuiltInsAndValuesComeBeforeTheNamespaces() {
        var self = ScriptParser.parse("""
                def refill(chest: Interface) -> None:
                    ae2.craft("minecraft:iron_ingot", 1)

                def _low() -> bool:
                    return False
                """).program();
        var labels = labels(LibraryRows.rows(Palette.groups(self, WORKSPACE), "", Set.of(Palette.MINE)));
        assertEquals(List.of("statements > 7", "operators > 17", "builtins > 17", "values > 3", "mine v 2",
                "  refill *", "  _low"), labels.subList(0, 7));
        assertEquals(List.of("ae2", "ae2f", "fs", "mc", "net"),
                labels.subList(7, labels.size()).stream().map(label -> label.split(" ")[0]).toList());
    }

    @Test
    void aSearchFindsStatementsOperatorsAndBuiltInsByName() {
        var labels = labels(LibraryRows.rows(Palette.groups(new Program(List.of(), List.of()), WORKSPACE), "in",
                Set.of()));
        assertEquals(List.of("statements v 1", "  continue", "operators v 2", "  in", "  not in", "builtins v 3",
                "  int", "  min", "  print", "ae2 v 1", "  crafting"), labels.subList(0, 11));
    }

    @Test
    void everyStandardFunctionHasAnExample() {
        StandardLibrary.NAMESPACES.forEach((namespace, functions) -> functions.forEach(function ->
                assertEquals(false, function.example().isBlank(), namespace + "." + function.name())));
    }
}
