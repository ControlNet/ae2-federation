package space.controlnet.ae2federation.function;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

final class ScriptTest {
    @Test
    void aWrittenProgramPrintsBackAsWritten() {
        var program = ScriptParser.parse(Samples.IRON, new Ids("n"));
        assertEquals(Samples.IRON, ScriptPrinter.print(program));
        assertEquals(3, program.functions().size());
        var keep = program.function("keep_iron").orElseThrow();
        assertEquals(FunctionDef.Kind.QUERY, keep.kind());
        assertEquals(ValueType.BOOL, keep.result());
        assertEquals("Keep iron for the workshop.", keep.comment());
        assertEquals("An unloaded network shares nothing.", keep.body().get(1).comment());
        var act = program.function("signal_low_iron").orElseThrow();
        assertEquals(FunctionDef.Kind.ACTION, act.kind());
        assertEquals(ValueType.NOTHING, act.result());
    }

    @Test
    void operatorsBindAsWrittenAndParenthesesSurviveOnlyWhereNeeded() {
        var program = ScriptParser.parse("""
                query f(a: number, b: number): bool
                  return (a + b) * 2 > a - (b - 1) and not a == b or b < 0
                end
                """, new Ids("n"));
        var value = ((Stmt.Return) program.functions().get(0).body().get(0)).value();
        var or = assertInstanceOf(Expr.Binary.class, value);
        assertEquals(Expr.Op.OR, or.op());
        var and = assertInstanceOf(Expr.Binary.class, or.left());
        assertEquals(Expr.Op.AND, and.op());
        // not binds looser than a comparison, as in Python: not (a == b).
        var not = assertInstanceOf(Expr.Unary.class, and.right());
        assertEquals(Expr.Op.EQ, assertInstanceOf(Expr.Binary.class, not.operand()).op());
        assertEquals("""
                query f(a: number, b: number): bool
                  return (a + b) * 2 > a - (b - 1) and not a == b or b < 0
                end
                """, ScriptPrinter.print(program));
    }

    @Test
    void negativeNumbersAndNegationStayApart() {
        var program = ScriptParser.parse("""
                query f(a: number): number
                  return -5 + -a - -(5)
                end
                """, new Ids("n"));
        var sum = (Expr.Binary) ((Stmt.Return) program.functions().get(0).body().get(0)).value();
        var left = (Expr.Binary) sum.left();
        assertEquals(-5.0, ((Expr.Literal) left.left()).value());
        assertInstanceOf(Expr.Unary.class, left.right());
        assertInstanceOf(Expr.Unary.class, sum.right(), "a negated literal in parentheses stays a negation");
        assertEquals("""
                query f(a: number): number
                  return -5 + -a - -(5)
                end
                """, ScriptPrinter.print(program));
    }

    @Test
    void literalsKeepTheirValues() {
        var program = ScriptParser.parse("""
                query f(): text
                  local a = 0.25
                  local b = 1000000000000
                  local c = @[appmek:chemical]mekanism:hydrogen
                  local d = true
                  return "say \\"hi\\"\\n"
                end
                """, new Ids("n"));
        var body = program.functions().get(0).body();
        assertEquals(.25, ((Expr.Literal) ((Stmt.Let) body.get(0)).value()).value());
        assertEquals(1e12, ((Expr.Literal) ((Stmt.Let) body.get(1)).value()).value());
        assertEquals(new ResourceRef("appmek:chemical", "mekanism:hydrogen"),
                ((Expr.Literal) ((Stmt.Let) body.get(2)).value()).value());
        assertEquals("say \"hi\"\n", ((Expr.Literal) ((Stmt.Return) body.get(4)).value()).value());
        assertTrue(ScriptPrinter.print(program).contains("local b = 1000000000000\n"));
    }

    @Test
    void mistakesAreReportedWithTheirLine() {
        for (var bad : List.of(
                "query f(): bool\n  return true\n",
                "query f(): bool\n  return 1 +\nend\n",
                "query f(): bool\n  return true -- why\nend\n",
                "query f(): bool\n  return true\n  -- dangling\nend\n",
                "query f(x: widget): bool\n  return true\nend\n",
                "action f(): bool\nend\n",
                "query f(): bool\n  1 + 2\nend\n")) {
            var error = assertThrows(ScriptParser.SyntaxError.class, () -> ScriptParser.parse(bad, new Ids("n")), bad);
            assertTrue(error.line() >= 1, bad);
        }
    }
}
