package space.controlnet.ae2federation.function;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Completion at a cursor in half-typed scripts: what is replaced, the expected type and the candidates. */
final class CompletionTest {
    private static TypeRef t(String name) {
        return new TypeRef(name, List.of());
    }

    private static Signature.Arg arg(String name, String type) {
        return new Signature.Arg(name, t(type));
    }

    /**
     * Test-only stand-in for the game's catalogue: a few made-up built-ins (the real standard library is still being
     * designed) and fixed values for typed strings in place of the item registry and the domain's interfaces.
     */
    static final Catalog CATALOG = new Catalog() {
        @Override
        public Map<String, List<Signature>> namespaces() {
            return Map.of(
                    "ae2", List.of(
                            new Signature("stock", List.of(arg("resource", "Resource")), t("int"), "How much is stored."),
                            new Signature("craft", List.of(arg("resource", "Resource"), arg("amount", "int")), t("Job"),
                                    "Requests crafting."),
                            new Signature("craftable", List.of(arg("resource", "Resource")), t("bool"), "Can it craft?")),
                    "mc", List.of(
                            new Signature("redstone", List.of(arg("at", "Interface"), arg("level", "int")), t("None"),
                                    "Emits redstone.")));
        }

        @Override
        public List<Candidate> values(TypeRef type, String prefix) {
            List<Candidate> all = switch (type.name()) {
                case "Item", "Resource" -> List.of(
                        new Candidate("minecraft:iron_ingot", Candidate.Kind.VALUE, "Iron Ingot"),
                        new Candidate("minecraft:iron_block", Candidate.Kind.VALUE, "Block of Iron"),
                        new Candidate("minecraft:gold_ingot", Candidate.Kind.VALUE, "Gold Ingot"));
                case "Interface" -> List.of(
                        new Candidate("north_gate", Candidate.Kind.VALUE, "interface-k7m2qx"),
                        new Candidate("mine_entry", Candidate.Kind.VALUE, "interface-r4tw9e"));
                default -> List.of();
            };
            return all.stream().filter(c -> c.label().contains(prefix)).toList();
        }
    };

    /** Another file of the server's programs. */
    static final Map<String, Program> FILES = Map.of("iron_tools", ScriptParser.parse("""
            def _low(name: Item, min: int) -> bool:
                return ae2.stock(name) < min

            def refill(name: Item, min: int) -> None:
                pass
            """).program());

    static final Workspace WORKSPACE = new Workspace(FILES, CATALOG);

    /** Completes at the {@code |} in {@code source} (which is removed first). */
    private static Completions at(String source) {
        int offset = source.indexOf('|');
        assertTrue(offset >= 0, "no cursor");
        var text = source.substring(0, offset) + source.substring(offset + 1);
        int line = 1 + (int) source.substring(0, offset).chars().filter(c -> c == '\n').count();
        int column = offset - source.lastIndexOf('\n', offset - 1);
        return Completion.complete(text, line, column, WORKSPACE);
    }

    private static List<String> labels(Completions completions) {
        return completions.candidates().stream().map(Candidate::label).toList();
    }

    @Test
    void membersOfANamespace() {
        var c = at("""
                def f() -> None:
                    ae2.cr|
                """);
        assertEquals(List.of("craft", "craftable"), labels(c));
        assertEquals("cr", c.prefix());
        assertEquals(new Position(2, 9), new Position(c.line(), c.column()));
        assertEquals("(resource: Resource, amount: int) -> Job", c.candidates().get(0).detail());
    }

    @Test
    void membersOfAnotherFileLeaveOutItsPrivateFunctions() {
        var c = at("""
                def f() -> None:
                    iron_tools.|
                """);
        assertEquals(List.of("refill"), labels(c));
    }

    @Test
    void aHalfTypedCallKeepsTheRestOfTheFileUsable() {
        var c = at("""
                def refill(name: Item, min: int) -> None:
                    now = ae2.stock(name)
                    ratio = 0.5
                    ae2.craft(|
                    later = 1
                    mc.redstone("north_gate", 15)

                def after() -> None:
                    pass
                """);
        assertEquals(t("Resource"), c.expected());
        var labels = labels(c);
        assertEquals("name", labels.get(0), "a variable of the expected type comes first: " + labels);
        assertTrue(labels.containsAll(List.of("min", "now", "ratio", "refill", "after", "ae2", "mc", "iron_tools")),
                labels.toString());
        assertFalse(labels.contains("later"), "assigned after the cursor");
        var now = c.candidates().stream().filter(x -> x.label().equals("now")).findFirst().orElseThrow();
        assertEquals("int", now.detail(), "the type comes from ae2.stock's result");
        var ratio = c.candidates().stream().filter(x -> x.label().equals("ratio")).findFirst().orElseThrow();
        assertEquals("float", ratio.detail());
    }

    @Test
    void theSecondArgumentExpectsItsOwnType() {
        var c = at("""
                def f(iron: Item, n: int) -> None:
                    ae2.craft(iron, |)
                """);
        assertEquals(t("int"), c.expected());
        assertEquals("n", labels(c).get(0));
    }

    @Test
    void anItemOfAListHasTheListsElementType() {
        var c = at("""
                def f(items: list[Item]) -> None:
                    first = items[0]
                    some = items[1:]
                    ae2.craft(|)
                """);
        var details = c.candidates().stream().filter(x -> x.kind() == Candidate.Kind.VARIABLE)
                .collect(java.util.stream.Collectors.toMap(Candidate::label, Candidate::detail));
        assertEquals("Item", details.get("first"));
        assertEquals("list[Item]", details.get("some"));
    }

    @Test
    void aVariableIsNotTakenForANamespace() {
        var c = at("""
                def f(ae2: list[int]) -> None:
                    ae2.craft(|)
                """);
        assertNull(c.expected(), "ae2 is a list here, not the namespace");
    }

    @Test
    void aKeywordArgumentExpectsItsParametersType() {
        var c = at("""
                def refill(name: Item, min: int = 64) -> None:
                    pass

                def f() -> None:
                    refill(min=1, name="minecraft:g|
                """);
        assertEquals(t("Item"), c.expected());
        assertEquals(List.of("minecraft:gold_ingot"), labels(c));
    }

    @Test
    void aSignatureShowsDefaults() {
        var refill = ScriptParser.parse("def refill(name: Item, min: int = 64) -> None:\n    pass\n").program()
                .functions().get(0);
        assertEquals("(name: Item, min: int = 64) -> None", Signature.of(refill).display());
    }

    @Test
    void aResourceStringOffersRegistryValues() {
        var c = at("""
                def f() -> None:
                    ae2.craft("iron|
                """);
        assertEquals(t("Resource"), c.expected());
        assertEquals(List.of("minecraft:iron_ingot", "minecraft:iron_block"), labels(c));
        assertEquals("iron", c.prefix());
        assertEquals(new Position(2, 16), new Position(c.line(), c.column()), "the text after the quote is replaced");
    }

    @Test
    void anAnnotatedAssignmentGivesTheStringItsType() {
        var c = at("""
                def f() -> None:
                    iron: Item = "minecraft:g|"
                """);
        assertEquals(t("Item"), c.expected());
        assertEquals(List.of("minecraft:gold_ingot"), labels(c));
    }

    @Test
    void anInterfaceStringOffersAliases() {
        var c = at("""
                def f() -> None:
                    mc.redstone("|
                """);
        assertEquals(List.of("north_gate", "mine_entry"), labels(c));
        assertEquals("interface-k7m2qx", c.candidates().get(0).detail());
    }

    @Test
    void aStringWithoutAKnownTypeOffersNothing() {
        assertEquals(List.of(), labels(at("""
                def f() -> None:
                    x = "minecraft:|
                """)));
    }

    @Test
    void typeHintsOfferTypes() {
        var param = labels(at("def f(x: I|"));
        assertTrue(param.containsAll(List.of("Item", "Interface", "int")), param.toString());
        assertFalse(param.contains("str"));
        assertTrue(labels(at("def f(x: int) -> |")).containsAll(List.of("None", "bool", "list")));
        assertEquals(List.of("list"), labels(at("""
                def f() -> None:
                    xs: li|
                """)));
    }

    @Test
    void aStatementStartOffersStatementKeywordsAndNames() {
        var labels = labels(at("""
                def f(n: int) -> None:
                    x = 1
                    |
                """));
        assertTrue(labels.containsAll(List.of("if", "for", "while", "return", "n", "x", "f", "ae2")), labels.toString());
        var inExpression = labels(at("""
                def f(n: int) -> None:
                    x = |
                """));
        assertFalse(inExpression.contains("while"), inExpression.toString());
        assertTrue(inExpression.containsAll(List.of("n", "True", "not")), inExpression.toString());
    }

    @Test
    void aLoopVariableIsInScopeInItsBody() {
        var c = at("""
                def f(xs: list[Item]) -> None:
                    for item in xs:
                        ae2.stock(it|)
                """);
        assertEquals(List.of("item"), labels(c));
        assertEquals("Item", c.candidates().get(0).detail());
    }

    @Test
    void nothingInCommentsOrNames() {
        assertEquals(List.of(), labels(at("""
                def f() -> None:
                    # ae2.|
                """)));
        assertEquals(List.of(), labels(at("def fo|")));
        assertEquals(List.of(), labels(at("""
                def f() -> None:
                    for it|
                """)));
    }
}
