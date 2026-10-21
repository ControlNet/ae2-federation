package space.controlnet.ae2federation.script;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Program files: plain {@code .ae2f} text in one folder, the text being the source of truth. */
final class LibraryTest {
    @TempDir
    Path folder;

    private static final String IRON_TOOLS = """
            # Keep the workshop stocked.
            def low(name: Item, min: int) -> bool:
                return ae2.amount(name)<min   # spacing kept as written

            def refill(name: Item) -> None:
                if low(name, 64):
                    ae2.craft(name, 64)
            """;

    private static final String SMELTING = """
            def smelt(n: int) -> bool:
                return iron_tools.low("minecraft:iron_ingot", n)
            """;

    private void write(String file, String text) throws IOException {
        Files.writeString(folder.resolve(file), text, StandardCharsets.UTF_8);
    }

    private String read(String file) throws IOException {
        return Files.readString(folder.resolve(file), StandardCharsets.UTF_8);
    }

    private Library library() throws IOException {
        return Library.load(folder, CompletionTest.CATALOG);
    }

    private List<String> files() throws IOException {
        try (Stream<Path> listing = Files.list(folder)) {
            return listing.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void listsTheOtherFilesAProgramCalls() throws IOException {
        write("iron_tools.ae2f", IRON_TOOLS);
        write("smelting.ae2f", SMELTING);
        write("shadowed.ae2f", """
                def run() -> int:
                    iron_tools = [1]
                    return len(iron_tools) + smelting.smelt(3)
                """);
        var library = library();
        assertEquals(Set.of(), library.calledFiles("iron_tools"), "its own bare calls and ae2 are not files");
        assertEquals(Set.of("iron_tools"), library.calledFiles("smelting"));
        assertEquals(Set.of("smelting"), library.calledFiles("shadowed"), "a local of that name is not the file");
    }

    @Test
    void loadsEveryProgramFileAndReportsTheOnesItCannotUse() throws IOException {
        write("iron_tools.ae2f", IRON_TOOLS);
        write("smelting.ae2f", SMELTING);
        write("notes.txt", "not a program");
        write("bad name.ae2f", SMELTING);
        write("ae2.ae2f", SMELTING);
        var library = library();
        assertEquals(Set.of("iron_tools", "smelting"), library.names());
        assertEquals(Set.of("bad name.ae2f", "ae2.ae2f"), library.skipped().keySet());
        assertEquals("'ae2' is a standard namespace", library.skipped().get("ae2.ae2f"));
    }

    @Test
    void filesComeBackByteForByte() throws IOException {
        var crlf = SMELTING.replace("\n", "\r\n");
        write("iron_tools.ae2f", IRON_TOOLS);
        write("smelting.ae2f", crlf);
        var library = library();
        assertEquals(crlf, library.text("smelting"));
        assertEquals(List.of(), library.parse("smelting").errors(), "CRLF from outside editors parses");
        for (var name : library.names()) {
            library.save(name, library.text(name));
        }
        assertEquals(IRON_TOOLS, read("iron_tools.ae2f"));
        assertEquals(crlf, read("smelting.ae2f"));
        assertEquals(List.of("iron_tools.ae2f", "smelting.ae2f"), files(), "no temporary file is left");
    }

    @Test
    void aFileIsCheckedAgainstTheOthersButNotItself() throws IOException {
        write("iron_tools.ae2f", IRON_TOOLS + """

                def again() -> bool:
                    return iron_tools.low("minecraft:iron_ingot", 1)
                """);
        write("smelting.ae2f", SMELTING + """

                def broken() -> bool:
                    return iron_tools.nope()
                """);
        var library = library();
        assertEquals(List.of("iron_tools has no function 'nope'"),
                library.check("smelting").problems().stream().map(Checker.Problem::message).toList());
        assertEquals(List.of("name 'iron_tools' is not defined"),
                library.check("iron_tools").problems().stream().map(Checker.Problem::message).toList());
    }

    @Test
    void aFileWithErrorsIsStoredAsWritten() throws IOException {
        var library = library();
        var broken = "def f(n: int) -> int:\n    return n +\n";
        library.save("draft", broken);
        assertEquals(broken, read("draft.ae2f"));
        assertFalse(library.parse("draft").ok());
        assertEquals(broken, library().text("draft"), "a reload reads it back");
    }

    @Test
    void namesThatCannotBeFilesAreRefused() throws IOException {
        var library = library();
        for (var name : List.of("../escape", "two words", "class", "ae2", "mc", "max", "")) {
            assertThrows(IllegalArgumentException.class, () -> library.save(name, SMELTING), name);
        }
        assertEquals(List.of(), files());
    }

    @Test
    void aReloadPicksUpFilesChangedOutsideTheGame() throws IOException {
        write("smelting.ae2f", SMELTING);
        var library = library();
        write("smelting.ae2f", SMELTING.replace("n)", "n * 2)"));
        write("iron_tools.ae2f", IRON_TOOLS);
        assertTrue(library.text("smelting").contains("n)"), "a library does not watch the folder");
        var reloaded = library();
        assertTrue(reloaded.text("smelting").contains("n * 2)"));
        assertEquals(Set.of("iron_tools", "smelting"), reloaded.names());
    }

    @Test
    void aFileOthersCallIsNotDeleted() throws IOException {
        write("iron_tools.ae2f", IRON_TOOLS);
        write("smelting.ae2f", SMELTING);
        var library = library();
        var refused = assertThrows(IllegalArgumentException.class, () -> library.delete("iron_tools"));
        assertEquals("iron_tools is called from smelting", refused.getMessage());
        library.delete("smelting");
        library.delete("iron_tools");
        assertEquals(List.of(), files());
    }

    @Test
    void anEditedDefinitionReplacesOnlyItsOwnText() throws IOException {
        var text = """
                # Keep the workshop stocked.
                def low(name: Item, min: int) -> bool:
                    return ae2.amount(name)<min   # spacing kept as written


                # Refill when low.
                # Two lines of comments.
                def refill(name: Item) -> None:
                    if low(name,64):
                        ae2.craft(name, 64)

                class Order:
                    item: Item
                    amount:int=1
                # the end
                """;
        write("iron_tools.ae2f", text);
        var library = library();
        var refill = library.parse("iron_tools").program().functions().get(1);
        var canvas = Canvas.of(refill, library.check("iron_tools"));
        var literal = canvas.nodes().stream().filter(n -> n.kind() == Canvas.Kind.CALL_STATEMENT).findFirst()
                .orElseThrow();
        var call = ((Canvas.Input.Wire) literal.inputs().get("call")).node();
        var ids = new Ids("n", library.parse("iron_tools").positions().keySet());
        canvas = Edits.put(canvas, call, "arg1", new Expr.IntLit(ids.next(), 32));
        library.replace("iron_tools", canvas.toFunction());
        assertEquals(text.replace("""
                def refill(name: Item) -> None:
                    if low(name,64):
                        ae2.craft(name, 64)
                """, """
                def refill(name: Item) -> None:
                    if low(name, 64):
                        ae2.craft(name, 32)
                """), read("iron_tools.ae2f"));

        var spliced = read("iron_tools.ae2f");
        var order = (ClassDef) library.parse("iron_tools").program().definitions().get(2);
        var amount = order.fields().get(1);
        var fields = List.of(order.fields().get(0), new Field(amount.id(), amount.notes(), amount.name(),
                amount.type(), new Expr.IntLit(ids.next(), 2)));
        library.replace("iron_tools", new ClassDef(order.id(), order.notes(), order.name(), order.docstring(), fields));
        assertEquals(spliced.replace("    amount:int=1\n", "    amount: int = 2\n"), read("iron_tools.ae2f"),
                "the last definition ends before the file's closing comment");
    }

    @Test
    void renamingAFunctionRewritesItsCallersAndNothingElse() throws IOException {
        write("iron_tools.ae2f", IRON_TOOLS + """

                def other(low_text: str) -> str:
                    # low stays low in comments
                    return "low"
                """);
        write("smelting.ae2f", SMELTING);
        var library = library();
        assertEquals(Set.of("iron_tools", "smelting"), library.renameFunction("iron_tools", "low", "short"));
        assertEquals(IRON_TOOLS.replace("def low(", "def short(").replace("if low(", "if short(") + """

                def other(low_text: str) -> str:
                    # low stays low in comments
                    return "low"
                """, read("iron_tools.ae2f"));
        assertEquals(SMELTING.replace("iron_tools.low(", "iron_tools.short("), read("smelting.ae2f"));
        assertEquals(List.of(), library.check("smelting").problems());
    }

    @Test
    void aFunctionRenameThatWouldChangeMeaningIsRefused() throws IOException {
        var original = IRON_TOOLS + """

                def uses(n: int) -> bool:
                    level = n
                    return low("minecraft:iron_ingot", level)
                """;
        write("iron_tools.ae2f", original);
        write("smelting.ae2f", SMELTING);
        var library = library();
        assertThrows(IllegalArgumentException.class, () -> library.renameFunction("iron_tools", "low", "refill"),
                "the file has a refill");
        assertThrows(IllegalArgumentException.class, () -> library.renameFunction("iron_tools", "low", "level"),
                "a caller's variable would hide it");
        assertThrows(IllegalArgumentException.class, () -> library.renameFunction("iron_tools", "low", "_low"),
                "smelting could no longer call it");
        assertThrows(IllegalArgumentException.class, () -> library.renameFunction("iron_tools", "nope", "x"));
        assertEquals(original, read("iron_tools.ae2f"), "nothing was written");
    }

    @Test
    void renamingAFileMovesItAndRewritesItsCallers() throws IOException {
        write("iron_tools.ae2f", IRON_TOOLS);
        var smelting = SMELTING + """

                def keep(iron_tools: list[int]) -> None:
                    iron_tools.append(1)
                """;
        write("smelting.ae2f", smelting);
        var library = library();
        assertEquals(Set.of("smelting", "iron"), library.renameFile("iron_tools", "iron"));
        assertEquals(List.of("iron.ae2f", "smelting.ae2f"), files());
        assertEquals(IRON_TOOLS, read("iron.ae2f"));
        assertEquals(smelting.replace("iron_tools.low(", "iron.low("), read("smelting.ae2f"),
                "a variable named like the file is left alone");
        assertEquals(Set.of("iron", "smelting"), library.names());
        assertThrows(IllegalArgumentException.class, () -> library.renameFile("iron", "smelting"));
        assertThrows(IllegalArgumentException.class, () -> library.renameFile("iron", "mc"));
        write("tools.ae2f", """
                def use(stock: int) -> bool:
                    return iron.low("minecraft:iron_ingot", stock)
                """);
        var reloaded = library();
        assertThrows(IllegalArgumentException.class, () -> reloaded.renameFile("iron", "stock"),
                "a caller's variable would hide the file");
    }

    @Test
    void theWorkspaceOfAFileHoldsEveryOtherFile() throws IOException {
        write("iron_tools.ae2f", IRON_TOOLS);
        write("smelting.ae2f", SMELTING);
        Map<String, Program> files = library().workspace("smelting").files();
        assertEquals(Set.of("iron_tools"), files.keySet());
    }
}
