package space.controlnet.ae2federation.script;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Graph edits: each one gives a canvas that is still a whole program, and it is checked through the printed script. */
final class EditsTest {
    private Ids ids;

    private Canvas canvas(String source) {
        ids = new Ids("n", ScriptParser.parse(source).positions().keySet());
        return CanvasTest.canvas(source);
    }

    private static String text(Canvas canvas) {
        return ScriptPrinter.print(new Program(List.of(canvas.toFunction()), List.of()));
    }

    private static Canvas.Node find(Canvas canvas, Canvas.Kind kind) {
        return canvas.nodes().stream().filter(n -> n.kind() == kind).findFirst().orElseThrow();
    }

    private static Canvas.Node find(Canvas canvas, Canvas.Kind kind, String name) {
        return canvas.nodes().stream().filter(n -> n.kind() == kind && name.equals(n.name())).findFirst()
                .orElseThrow();
    }

    private Expr.IntLit number(long value) {
        return new Expr.IntLit(ids.next(), value);
    }

    private Expr.Name name(String name) {
        return new Expr.Name(ids.next(), name);
    }

    @Test
    void aPortTakesALiteralAVariableOrANewValue() {
        var canvas = canvas("""
                def f(n: int) -> int:
                    return n + 1
                """);
        var sum = find(canvas, Canvas.Kind.BINARY).id();
        canvas = Edits.put(canvas, sum, "right", number(2));
        canvas = Edits.put(canvas, sum, "left", new Expr.Call(ids.next(), null, "max",
                List.of(name("n"), number(3)), List.of()));
        assertEquals("""
                def f(n: int) -> int:
                    return max(n, 3) + 2
                """, text(canvas));
        assertEquals(sum, find(canvas, Canvas.Kind.BINARY).id(), "the edited node keeps its ID");
    }

    @Test
    void setItemsComeAndGoAndDictEntriesAreOnlyReplaced() {
        var canvas = canvas("""
                def f(n: int) -> None:
                    s = {n, 2}
                    d = {"a": n}
                """);
        canvas = Edits.put(canvas, find(canvas, Canvas.Kind.SET).id(), "item2", number(3));
        canvas = Edits.put(canvas, find(canvas, Canvas.Kind.SET).id(), "item0", null);
        canvas = Edits.put(canvas, find(canvas, Canvas.Kind.DICT).id(), "value0", number(4));
        assertEquals("""
                def f(n: int) -> None:
                    s = {2, 3}
                    d = {"a": 4}
                """, text(canvas));
        var dict = find(canvas, Canvas.Kind.DICT).id();
        var shown = canvas;
        // A key without its value, or a value without its key, is no dict entry.
        assertThrows(IllegalArgumentException.class, () -> Edits.put(shown, dict, "key0", null));
        assertThrows(IllegalArgumentException.class, () -> Edits.put(shown, dict, "key1", number(1)));
    }

    @Test
    void argumentsKeywordsItemsAndOptionalPartsComeAndGo() {
        var canvas = canvas("""
                def f(n: int, x: float, xs: list[int]) -> None:
                    m = max(n, 1)
                    y = round(x)
                    ys = [n, 2]
                    zs = xs[1:n]
                """);
        canvas = Edits.put(canvas, find(canvas, Canvas.Kind.CALL, "max").id(), "arg2", number(5));
        canvas = Edits.put(canvas, find(canvas, Canvas.Kind.CALL, "max").id(), "arg0", null);
        canvas = Edits.put(canvas, find(canvas, Canvas.Kind.CALL, "round").id(), "kw:ndigits", number(2));
        canvas = Edits.put(canvas, find(canvas, Canvas.Kind.LIST).id(), "item2", number(3));
        canvas = Edits.put(canvas, find(canvas, Canvas.Kind.LIST).id(), "item0", null);
        canvas = Edits.put(canvas, find(canvas, Canvas.Kind.SLICE).id(), "upper", null);
        assertEquals("""
                def f(n: int, x: float, xs: list[int]) -> None:
                    m = max(1, 5)
                    y = round(x, ndigits=2)
                    ys = [2, 3]
                    zs = xs[1:]
                """, text(canvas));
        var before = canvas;
        assertThrows(IllegalArgumentException.class,
                () -> Edits.put(before, find(before, Canvas.Kind.CALL, "max").id(), "arg5", number(1)));
    }

    @Test
    void anEditThatIsNoProgramIsRefused() {
        var canvas = canvas("""
                def f(n: int) -> int:
                    m = n
                    return n + 1
                """);
        var sum = find(canvas, Canvas.Kind.BINARY).id();
        var assign = find(canvas, Canvas.Kind.ASSIGN).id();
        assertThrows(IllegalArgumentException.class, () -> Edits.put(canvas, sum, "left", null));
        assertThrows(IllegalArgumentException.class, () -> Edits.put(canvas, sum, "nowhere", number(1)));
        assertThrows(IllegalArgumentException.class, () -> Edits.put(canvas, assign, "target", number(1)));
        assertThrows(IllegalArgumentException.class, () -> Edits.rename(canvas, sum, "+="));
        assertThrows(IllegalArgumentException.class, () -> Edits.rename(canvas, canvas.nodes().get(0).id(), "1f"));
        assertThrows(IllegalArgumentException.class, () -> Edits.put(canvas, sum, "left", name("two words")),
                "the printed script must parse");
    }

    @Test
    void aValueMovesToAnotherPortAndLeavesAFillerBehind() {
        var canvas = canvas("""
                def f(n: int) -> int:
                    a = n * 2
                    return 0
                """);
        var product = find(canvas, Canvas.Kind.BINARY).id();
        var ret = find(canvas, Canvas.Kind.RETURN).id();
        canvas = Edits.move(canvas, product, ret, "value", number(0));
        assertEquals("""
                def f(n: int) -> int:
                    a = 0
                    return n * 2
                """, text(canvas));
        assertEquals(product, find(canvas, Canvas.Kind.BINARY).id());
        var moved = canvas;
        assertThrows(IllegalArgumentException.class, () -> Edits.move(moved, product, product, "left", number(0)),
                "a value cannot feed itself");
    }

    @Test
    void operatorsCallsFieldsLoopsAndTheFunctionAreRenamed() {
        var canvas = canvas("""
                def f(xs: list[int], st: Stack) -> int:
                    total = 0
                    for x in xs:
                        total += max(x, st.amount)
                    return -total
                """);
        canvas = Edits.rename(canvas, canvas.nodes().get(0).id(), "g");
        canvas = Edits.rename(canvas, find(canvas, Canvas.Kind.AUG_ASSIGN).id(), "-");
        canvas = Edits.rename(canvas, find(canvas, Canvas.Kind.CALL).id(), "min");
        canvas = Edits.rename(canvas, find(canvas, Canvas.Kind.ATTRIBUTE).id(), "resource");
        canvas = Edits.rename(canvas, find(canvas, Canvas.Kind.UNARY).id(), "not");
        var renamed = canvas;
        assertThrows(IllegalArgumentException.class,
                () -> Edits.rename(renamed, find(renamed, Canvas.Kind.AUG_ASSIGN).id(), "<"), "there is no <=");
        assertEquals("""
                def g(xs: list[int], st: Stack) -> int:
                    total = 0
                    for x in xs:
                        total -= min(x, st.resource)
                    return not total
                """, text(canvas));
    }

    @Test
    void aVariableIsRenamedEverywhereItIsReadOrWritten() {
        var canvas = canvas("""
                def f(xs: list[int], n: int = 1) -> int:
                    for x in xs:
                        n += x
                    return n
                """);
        canvas = Edits.renameVariable(canvas, "n", "total");
        canvas = Edits.renameVariable(canvas, "x", "item");
        assertEquals("""
                def f(xs: list[int], total: int = 1) -> int:
                    for item in xs:
                        total += item
                    return total
                """, text(canvas));
        var renamed = canvas;
        assertThrows(IllegalArgumentException.class, () -> Edits.renameVariable(renamed, "item", "xs"),
                "two variables would become one");
        var reading = canvas("""
                def f(n: int) -> int:
                    return ae2.amount("minecraft:iron_ingot") + n
                """);
        assertThrows(IllegalArgumentException.class, () -> Edits.renameVariable(reading, "n", "ae2"),
                "the variable would hide a namespace");
    }

    @Test
    void statementsAreInsertedOnAnExecLinkAndRemoved() {
        var canvas = canvas("""
                def f(xs: list[int]) -> None:
                    for x in xs:
                        if x > 1:
                            mc.wait(x)
                        else:
                            pass
                """);
        var entry = canvas.nodes().get(0).id();
        var loop = find(canvas, Canvas.Kind.FOR).id();
        var branch = find(canvas, Canvas.Kind.IF).id();
        canvas = Edits.insert(canvas, entry, "next", new Stmt.Assign(ids.next(), Notes.NONE, name("n"), null,
                number(0)));
        canvas = Edits.insert(canvas, loop, "body", new Stmt.Break(ids.next(), Notes.NONE));
        canvas = Edits.insert(canvas, loop, "next", new Stmt.Return(ids.next(), Notes.NONE, null));
        canvas = Edits.remove(canvas, find(canvas, Canvas.Kind.PASS).id());
        canvas = Edits.remove(canvas, find(canvas, Canvas.Kind.CALL_STATEMENT).id());
        assertEquals("""
                def f(xs: list[int]) -> None:
                    n = 0
                    for x in xs:
                        break
                        if x > 1:
                            pass
                        else:
                            pass
                    return
                """, text(canvas));
        var emptied = find(canvas, Canvas.Kind.IF);
        assertEquals(1, emptied.armNotes().size(), "an emptied else stays");
        assertFalse(canvas.links().stream().anyMatch(l -> l.from().equals(emptied.id()) && l.output().equals("else")));
        var before = canvas;
        assertThrows(IllegalArgumentException.class, () -> Edits.insert(before, branch, "arm1",
                new Stmt.Pass(ids.next(), Notes.NONE)), "the if has no second arm");
        assertThrows(IllegalArgumentException.class, () -> Edits.remove(before, entry));
    }

    @Test
    void aStatementMovesToAnotherExecLink() {
        var canvas = canvas("""
                def f(xs: list[int]) -> int:
                    n = 0
                    for x in xs:
                        n += x
                    return n
                """);
        var loop = find(canvas, Canvas.Kind.FOR).id();
        var assign = find(canvas, Canvas.Kind.ASSIGN).id();
        canvas = Edits.relocate(canvas, assign, loop, "body");
        assertEquals("""
                def f(xs: list[int]) -> int:
                    for x in xs:
                        n = 0
                        n += x
                    return n
                """, text(canvas));
        var moved = canvas;
        assertThrows(IllegalArgumentException.class, () -> Edits.relocate(moved, loop, assign, "next"),
                "a loop cannot move into its own body");
    }

    @Test
    void aValueIsExtractedToAVariableBeforeItsStatement() {
        var canvas = canvas("""
                def f(xs: list[int]) -> int:
                    for x in xs:
                        if x > len(xs) * 2:
                            return x
                    return 0
                """);
        canvas = Edits.extract(canvas, find(canvas, Canvas.Kind.BINARY, "*").id(), "limit", ids);
        assertEquals("""
                def f(xs: list[int]) -> int:
                    for x in xs:
                        limit = len(xs) * 2
                        if x > limit:
                            return x
                    return 0
                """, text(canvas));
    }

    @Test
    void aValueIsNotExtractedWhereItWouldRunADifferentNumberOfTimes() {
        var canvas = canvas("""
                def f(n: int, xs: list[int]) -> int:
                    while len(xs) > 0:
                        xs.pop()
                    if n > 0:
                        return 1
                    elif n + 1 > 0:
                        return 2
                    if n > 0 and n * 2 > 3:
                        return 3
                    return 0
                """);
        var whileLength = find(canvas, Canvas.Kind.CALL, "len").id();
        var elifSum = find(canvas, Canvas.Kind.BINARY, "+").id();
        var rightOfAnd = find(canvas, Canvas.Kind.BINARY, "*").id();
        for (var value : List.of(whileLength, elifSum, rightOfAnd)) {
            assertThrows(IllegalArgumentException.class, () -> Edits.extract(canvas, value, "v", ids), value);
        }
    }

    @Test
    void armsAreAddedAndRemovedAndKeepTheirComments() {
        var canvas = canvas("""
                def f(n: int) -> int:
                    if n > 0:
                        return 1
                    # zero
                    elif n == 0:
                        return 0
                    # last
                    elif n == -1:
                        return -1
                    return 2
                """);
        var branch = find(canvas, Canvas.Kind.IF).id();
        canvas = Edits.removeArm(canvas, branch, "arm1");
        canvas = Edits.addArm(canvas, branch, new Expr.Binary(ids.next(), BinaryOp.LT, name("n"), number(-5)));
        canvas = Edits.addElse(canvas, branch);
        canvas = Edits.insert(canvas, branch, "arm2", new Stmt.Return(ids.next(), Notes.NONE, number(5)));
        canvas = Edits.annotateArm(canvas, branch, "arm2", new Notes(List.of(), " far"));
        canvas = Edits.annotateArm(canvas, branch, "else", new Notes(List.of(" rest"), null));
        assertEquals("""
                def f(n: int) -> int:
                    if n > 0:
                        return 1
                    # last
                    elif n == -1:
                        return -1
                    elif n < -5:  # far
                        return 5
                    # rest
                    else:
                        pass
                    return 2
                """, text(canvas));
        var edited = canvas;
        assertThrows(IllegalArgumentException.class, () -> Edits.addElse(edited, branch), "one else only");
        assertThrows(IllegalArgumentException.class, () -> Edits.removeArm(edited, branch, "arm0"));
        canvas = Edits.removeArm(canvas, branch, "else");
        assertEquals(List.of(new Notes(List.of(" last"), null), new Notes(List.of(), " far")),
                find(canvas, Canvas.Kind.IF).armNotes(),
                "two elifs and no else");
    }

    @Test
    void theSignatureTypesAndCommentsAreEdited() {
        var canvas = canvas("""
                def f(n: int) -> int:
                    m = n
                    return m
                """);
        var entry = canvas.nodes().get(0).id();
        var assign = find(canvas, Canvas.Kind.ASSIGN).id();
        var integer = new TypeRef("int", List.of());
        canvas = Edits.signature(canvas, List.of(
                new Param(ids.next(), "n", integer, null),
                new Param(ids.next(), "k", integer, number(3))), new TypeRef("float", List.of()));
        canvas = Edits.describe(canvas, "Scales n.");
        canvas = Edits.retype(canvas, assign, integer);
        canvas = Edits.annotate(canvas, assign, new Notes(List.of(" keep"), " end"));
        assertEquals("""
                def f(n: int, k: int = 3) -> float:
                    \"""Scales n.\"""
                    # keep
                    m: int = n  # end
                    return m
                """, text(canvas));
        assertEquals(entry, canvas.nodes().get(0).id());
    }

    @Test
    void anEditKeepsEveryOtherIdAndNewOnesAreFresh() {
        var canvas = canvas("""
                def f(n: int) -> int:
                    total = n * 2 + ae2.amount("minecraft:iron_ingot")
                    return total
                """);
        var before = ids(canvas);
        canvas = Edits.extract(canvas, find(canvas, Canvas.Kind.BINARY, "*").id(), "twice", ids);
        var after = ids(canvas);
        assertTrue(after.containsAll(before), "no ID changed");
        assertEquals(before.size() + 3, after.size(), "an assignment, its target and the chip are new");
    }

    /** Every node ID the function holds, through its printed form's structure. */
    private static Set<String> ids(Canvas canvas) {
        var ids = new HashSet<String>();
        canvas.nodes().forEach(n -> {
            ids.add(n.id());
            n.params().forEach(p -> ids.add(p.id()));
            n.inputs().values().forEach(input -> {
                if (input instanceof Canvas.Input.Chip c) {
                    ids.add(c.name().id());
                } else if (input instanceof Canvas.Input.Literal l) {
                    ids.add(l.value().id());
                }
            });
        });
        return ids;
    }

    @Test
    void anEditedFunctionReplacesOnlyItselfInTheProgram() {
        var source = """
                # a file
                def f(n: int) -> int:
                    return n

                class Order:
                    amount: int = 1

                def g(n: int) -> int:
                    return n + 1
                """;
        var program = ScriptParser.parse(source).program();
        var printed = ScriptPrinter.print(program);
        ids = new Ids("n", ScriptParser.parse(source).positions().keySet());
        var g = program.functions().get(1);
        var canvas = Canvas.of(g, Checker.check(program, new Workspace(java.util.Map.of(), CompletionTest.CATALOG)));
        canvas = Edits.put(canvas, find(canvas, Canvas.Kind.BINARY).id(), "right", number(2));
        var edited = Edits.replace(program, canvas.toFunction());
        assertSame(program.definitions().get(0), edited.definitions().get(0));
        assertSame(program.definitions().get(1), edited.definitions().get(1));
        assertEquals(printed.replace("n + 1", "n + 2"), ScriptPrinter.print(edited));
    }
}
