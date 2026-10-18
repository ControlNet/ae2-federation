package space.controlnet.ae2federation.function;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class GraphTest {
    private static final Program IRON = ScriptParser.parse(Samples.IRON, new Ids("n"));

    @Test
    void theGraphReadsBackAsTheSameProgram() {
        assertEquals(IRON, GraphView.toProgram(GraphView.of(IRON)));
    }

    @Test
    void statementsFollowExecutionWiresAndLiteralsSitOnTheirPorts() {
        var graph = GraphView.of(IRON);
        assertTrue(graph.nodes().stream().noneMatch(node -> node.type().equals("literal")),
                "a literal is a value on its port, as a Videnoa parameter is");
        var call = graph.nodes().stream().filter(node -> "craft".equals(node.data().get("function"))).findFirst()
                .orElseThrow();
        assertEquals("do", call.type(), "an action call made for its effect is one node with execution ports");
        assertEquals(2, call.inline().size());
        // The action's two statements: the if, then the craft call after it.
        var act = IRON.function("signal_low_iron").orElseThrow();
        var branch = act.body().get(0).id();
        assertTrue(graph.wires().contains(new GraphView.Wire(act.id(), "exec", branch, "exec")));
        assertTrue(graph.wires().contains(new GraphView.Wire(branch, "next", call.id(), "exec")));
    }

    @Test
    void aValueWiredToTwoPortsIsRejectedForALocal() {
        var graph = GraphView.of(ScriptParser.parse("""
                query f(a: number): bool
                  return a + 1 > 2
                end
                """, new Ids("n")));
        var plus = graph.nodes().stream().filter(node -> "+".equals(node.data().get("op"))).findFirst().orElseThrow();
        var compare = graph.nodes().stream().filter(node -> ">".equals(node.data().get("op"))).findFirst().orElseThrow();
        var wires = new ArrayList<>(graph.wires());
        var inline = new HashMap<>(compare.inline());
        inline.remove("b");
        var nodes = new ArrayList<>(graph.nodes());
        nodes.set(nodes.indexOf(compare), new GraphView.Node(compare.id(), compare.type(), compare.data(), inline));
        wires.add(new GraphView.Wire(plus.id(), "value", compare.id(), "b"));
        var error = assertThrows(GraphView.GraphError.class,
                () -> GraphView.toProgram(new GraphView.Graph(nodes, wires)));
        assertTrue(error.getMessage().contains("local"), error.getMessage());
    }

    @Test
    void positionsStayWithTheirNodesThroughAScriptEdit() {
        var before = GraphView.of(IRON);
        // The player arranges the graph.
        var layout = new HashMap<String, Layout.Pos>();
        int i = 0;
        for (var node : before.nodes()) layout.put(node.id(), new Layout.Pos(1000 + 37 * i, -500 + 11 * i++));
        // Then edits the script: a new statement at the top of keep_iron.
        var edited = Reconciler.reconcile(IRON, ScriptParser.parse(Samples.IRON.replace(
                "  local now = stock(@minecraft:iron_ingot)\n",
                "  local spare = stock(@minecraft:iron_block) * 9\n  local now = stock(@minecraft:iron_ingot)\n"),
                new Ids("x")));
        var after = GraphView.of(edited);
        var placed = Layout.update(layout, after);
        for (var node : before.nodes()) assertEquals(layout.get(node.id()), placed.get(node.id()), node.id());
        assertEquals(after.nodes().size(), placed.size(), "the new nodes are placed too");
        assertEquals(before.nodes().size() + 3, after.nodes().size(), "local, *, stock: the literals are inline");
    }

    @Test
    void aCommentWrittenInTheGraphShowsInTheScriptAndComesBack() {
        var graph = GraphView.of(IRON);
        var target = IRON.function("signal_low_iron").orElseThrow().body().get(1).id();
        var nodes = new ArrayList<GraphView.Node>();
        for (var node : graph.nodes()) {
            if (!node.id().equals(target)) {
                nodes.add(node);
                continue;
            }
            var data = new HashMap<>(node.data());
            data.put("comment", "Two blocks a cycle.");
            nodes.add(new GraphView.Node(node.id(), node.type(), Map.copyOf(data), node.inline()));
        }
        var program = GraphView.toProgram(new GraphView.Graph(nodes, graph.wires()));
        var script = ScriptPrinter.print(program);
        assertTrue(script.contains("  -- Two blocks a cycle.\n  craft(@minecraft:iron_block, 2)\n"), script);
        var back = GraphView.of(Reconciler.reconcile(program, ScriptParser.parse(script, new Ids("x"))));
        assertEquals("Two blocks a cycle.", back.nodes().stream().filter(node -> node.id().equals(target))
                .findFirst().orElseThrow().data().get("comment"));
        assertEquals(List.copyOf(nodes), back.nodes());
    }
}
