package space.controlnet.ae2federation.function;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The script carries no IDs, so after a script edit {@link Reconciler} must recognise the nodes that are still the
 * same; the graph keeps their positions by ID. These tests edit {@link Samples#IRON} the way a player would.
 */
final class ReconcilerTest {
    private static final Program BEFORE = ScriptParser.parse(Samples.IRON, new Ids("n"));

    private static Program edit(String from, String to) {
        assertTrue(Samples.IRON.contains(from), from);
        var text = Samples.IRON.replace(from, to);
        return Reconciler.reconcile(BEFORE, ScriptParser.parse(text, new Ids("x")));
    }

    /** Each node's ID with the script text it prints as (an expression) or its kind and name (others). */
    private static Map<String, String> nodes(Program program) {
        var out = new HashMap<String, String>();
        Walk.nodes(program, (id, label) -> assertNull(out.put(id, label), "IDs are unique: " + id));
        return out;
    }

    private static Set<String> kept(Program after) {
        var kept = new HashSet<>(nodes(BEFORE).keySet());
        kept.retainAll(nodes(after).keySet());
        return kept;
    }

    @Test
    void printingAndReadingBackChangesNothing() {
        var again = Reconciler.reconcile(BEFORE, ScriptParser.parse(ScriptPrinter.print(BEFORE), new Ids("x")));
        assertEquals(BEFORE, again);
    }

    @Test
    void renamingALocalKeepsEveryNode() {
        var after = edit("now", "level");
        assertEquals(nodes(BEFORE).keySet(), nodes(after).keySet());
    }

    @Test
    void insertingAStatementKeepsTheOthersAndGivesTheNewOneFreshIds() {
        var after = edit("  local now = stock(@minecraft:iron_ingot)\n",
                "  local spare = stock(@minecraft:iron_block) * 9\n  local now = stock(@minecraft:iron_ingot)\n");
        assertEquals(nodes(BEFORE).keySet(), kept(after));
        var fresh = new HashSet<>(nodes(after).keySet());
        fresh.removeAll(nodes(BEFORE).keySet());
        assertEquals(5, fresh.size(), "let, *, call and two literals: " + fresh);
        sameWhereKept(after);
    }

    @Test
    void changingALiteralOrAnOperatorKeepsTheirNodes() {
        var after = edit("now - min > 64 * 2", "now - min >= 32 * 2");
        assertEquals(nodes(BEFORE).keySet(), nodes(after).keySet());
    }

    @Test
    void wrappingAnExpressionKeepsTheWrappedNodes() {
        var after = edit("return now >= min and online()", "return (now >= min and online()) or redstone_in() > 0");
        assertEquals(nodes(BEFORE).keySet(), kept(after));
        assertEquals(nodes(BEFORE).size() + 4, nodes(after).size(), "or, >, call, literal");
        sameWhereKept(after);
    }

    @Test
    void deletingAndReorderingStatementsKeepTheRest() {
        var deleted = edit("  craft(@minecraft:iron_block, 2)\n", "");
        assertEquals(nodes(BEFORE).size() - 4, kept(deleted).size(), "the craft call, its two literals and its statement");
        var moved = edit("""
                  if not keep_iron(64) then
                    redstone(15)
                  else
                    redstone(0)
                  end
                  craft(@minecraft:iron_block, 2)
                """, """
                  craft(@minecraft:iron_block, 2)
                  if not keep_iron(64) then
                    redstone(15)
                  else
                    redstone(0)
                  end
                """);
        assertEquals(nodes(BEFORE).keySet(), nodes(moved).keySet());
        sameWhereKept(moved);
    }

    /**
     * Every kept expression ID still names an expression that prints as it did: no node took over another's ID.
     * Statements are left out, as their text includes the expressions an edit changed.
     */
    private static void sameWhereKept(Program after) {
        var before = nodes(BEFORE);
        var now = nodes(after);
        for (var id : kept(after)) {
            var label = before.get(id);
            if (label.endsWith("\n") || label.matches("(function|param|branch) .*")) continue;
            assertEquals(label, now.get(id), id);
        }
    }

    @Test
    void renamingAFunctionOrAParameterKeepsTheirNodes() {
        var after = edit("keep_iron", "has_iron");
        assertEquals(nodes(BEFORE).keySet(), nodes(after).keySet());
        assertEquals(nodes(BEFORE).keySet(), nodes(edit("min", "floor")).keySet());
    }

    @Test
    void aStatementMovedIntoAnotherBlockIsNewThere() {
        // A known limit of matching blocks one by one: the statement is recognised only within its own block.
        var moved = edit("""
                    redstone(0)
                  end
                  craft(@minecraft:iron_block, 2)
                """, """
                    redstone(0)
                    craft(@minecraft:iron_block, 2)
                  end
                """);
        var craftBefore = BEFORE.function("signal_low_iron").orElseThrow().body().get(1).id();
        assertFalse(nodes(moved).containsKey(craftBefore));
    }
}
