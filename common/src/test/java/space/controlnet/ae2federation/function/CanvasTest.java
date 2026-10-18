package space.controlnet.ae2federation.function;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** The graph view of a function: a complete projection of the program, so turning it back gives the same function. */
final class CanvasTest {
    private static final Workspace WORKSPACE = new Workspace(
            Map.of("iron_tools", CheckerTest.FILES.get("iron_tools")), CompletionTest.CATALOG);

    /** Every sample the parser and runner tests use, including ones that do not check. */
    static Stream<String> sources() {
        var sources = new ArrayList<String>(List.of(ScriptTest.SAMPLE, ScriptTest.LOOPS, ScriptTest.DEFAULTS,
                ScriptTest.POSTFIX, ScriptTest.MEMBERSHIP, ScriptTest.RECORDS, ScriptTest.CONTINUATION));
        RunnerTest.programs().forEach(arguments -> sources.add((String) arguments.get()[1]));
        return sources.stream();
    }

    @ParameterizedTest
    @MethodSource("sources")
    void aFunctionComesBackFromItsCanvasUnchanged(String source) {
        var parsed = ScriptParser.parse(source);
        assertEquals(List.of(), parsed.errors());
        var result = Checker.check(parsed.program(), WORKSPACE);
        for (var function : parsed.program().functions()) {
            var canvas = Canvas.of(function, result);
            assertEquals(function, canvas.toFunction(), function.name());
        }
    }

    private static Canvas canvas(String source) {
        var parsed = ScriptParser.parse(source);
        assertEquals(List.of(), parsed.errors());
        var result = Checker.check(parsed.program(), WORKSPACE);
        assertEquals(List.of(), result.problems());
        return Canvas.of(parsed.program().functions().get(0), result);
    }

    /** {@code kind name} of each node, in the canvas's order. */
    private static List<String> nodes(Canvas canvas) {
        return canvas.nodes().stream().map(n -> n.kind() + (n.name() == null ? "" : " " + n.name())).toList();
    }

    @Test
    void literalsAndVariablesSitOnPortsAndOtherValuesAreNodes() {
        var canvas = canvas("""
                def f(n: int) -> int:
                    total = n * 2 + ae2.amount("minecraft:iron_ingot")
                    return total
                """);
        assertEquals(List.of("ENTRY f", "ASSIGN", "BINARY +", "BINARY *", "CALL amount", "RETURN"), nodes(canvas));
        var assign = canvas.node(canvas.nodes().get(1).id());
        assertInstanceOf(Canvas.Input.Chip.class, assign.inputs().get("target"));
        var sum = canvas.nodes().get(2);
        assertEquals(new Canvas.Input.Wire(sum.id()), assign.inputs().get("value"));
        var product = canvas.nodes().get(3);
        assertEquals("n", ((Canvas.Input.Chip) product.inputs().get("left")).name().name());
        assertEquals(2L, ((Expr.IntLit) ((Canvas.Input.Literal) product.inputs().get("right")).value()).value());
        var call = canvas.nodes().get(4);
        assertEquals("ae2", call.qualifier());
        assertEquals(List.of("arg0"), List.copyOf(call.inputs().keySet()));
        assertFalse(call.effect(), "a read takes no tick");
        assertEquals(List.of(
                new Canvas.Link(canvas.nodes().get(0).id(), "next", assign.id()),
                new Canvas.Link(assign.id(), "next", canvas.nodes().get(5).id())), canvas.links());
    }

    @Test
    void branchesAndLoopsHaveAnExecOutputPerArm() {
        var canvas = canvas("""
                def f(xs: list[int]) -> None:
                    for x in xs:
                        if x > 1:
                            mc.wait(x)
                        elif x == 0:
                            break
                        else:
                            continue
                    while True:
                        pass
                """);
        var entry = canvas.nodes().get(0);
        var loop = canvas.nodes().stream().filter(n -> n.kind() == Canvas.Kind.FOR).findFirst().orElseThrow();
        var branch = canvas.nodes().stream().filter(n -> n.kind() == Canvas.Kind.IF).findFirst().orElseThrow();
        var wait = canvas.nodes().stream().filter(n -> n.kind() == Canvas.Kind.CALL_STATEMENT).findFirst().orElseThrow();
        assertEquals("x", loop.name());
        assertEquals(List.of("condition", "condition1"), List.copyOf(branch.inputs().keySet()));
        var outputs = canvas.links().stream().filter(l -> l.from().equals(branch.id())).map(Canvas.Link::output).toList();
        assertEquals(List.of("arm0", "arm1", "else"), outputs);
        assertTrue(canvas.links().contains(new Canvas.Link(entry.id(), "next", loop.id())));
        assertTrue(canvas.links().contains(new Canvas.Link(loop.id(), "body", branch.id())));
        assertTrue(canvas.links().stream().anyMatch(l -> l.from().equals(loop.id()) && l.output().equals("next")));
        var waitCall = canvas.node(((Canvas.Input.Wire) wait.inputs().get("call")).node());
        assertTrue(waitCall.effect(), "an action is marked");
    }

    @Test
    void commentsOnEachArmAndNegativeNumbersComeBack() {
        var source = """
                def f(n: int) -> int:
                    # first
                    if n > 0:
                        return -1
                    # second
                    elif n == 0:  # zero
                        return -n
                    # last
                    else:
                        return 2
                """;
        var canvas = canvas(source);
        assertEquals(ScriptParser.parse(source).program().functions().get(0), canvas.toFunction());
        var branch = canvas.nodes().stream().filter(n -> n.kind() == Canvas.Kind.IF).findFirst().orElseThrow();
        assertEquals(List.of(new Notes(List.of(" second"), " zero"), new Notes(List.of(" last"), null)),
                branch.armNotes());
        var returns = canvas.nodes().stream().filter(n -> n.kind() == Canvas.Kind.RETURN).toList();
        assertInstanceOf(Canvas.Input.Literal.class, returns.get(0).inputs().get("value"), "-1 is a literal");
        assertInstanceOf(Canvas.Input.Wire.class, returns.get(1).inputs().get("value"), "-n is a node");
    }

    @Test
    void placesWrittenToAreNodesOfTheTarget() {
        var canvas = canvas("""
                def f(xs: list[int], st: Stack) -> None:
                    xs[0] += st.amount
                    xs[1:] = [xs[0], 2]
                """);
        assertEquals(List.of("ENTRY f", "AUG_ASSIGN +", "INDEX", "ATTRIBUTE amount", "ASSIGN", "SLICE", "LIST",
                "INDEX"), nodes(canvas));
        var slice = canvas.nodes().get(5);
        assertEquals(Canvas.Input.Empty.INSTANCE, slice.inputs().get("upper"));
    }
}
