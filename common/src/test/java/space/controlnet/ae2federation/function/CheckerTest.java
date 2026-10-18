package space.controlnet.ae2federation.function;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class CheckerTest {
    private static String problems(String source) {
        return String.join("\n", Checker.check(ScriptParser.parse(source, new Ids("n"))).stream()
                .map(Checker.Problem::message).toList());
    }

    private static void rejects(String source, String expected) {
        var found = problems(source);
        assertTrue(found.contains(expected), "expected \"" + expected + "\" in: " + found);
    }

    @Test
    void theSampleIsSound() {
        assertEquals("", problems(Samples.IRON));
    }

    @Test
    void aQueryCannotAct() {
        rejects("query f(): bool\n  return redstone(1)\nend\n", "a query cannot call the action redstone");
        rejects("action a()\n  redstone(1)\nend\n\nquery f(): bool\n  a()\n  return true\nend\n",
                "a query cannot call the action a");
        rejects("query f(): bool\n  online()\n  return true\nend\n", "the value of online is unused");
    }

    @Test
    void functionsCannotCallThemselvesEvenThroughOthers() {
        rejects("query f(n: number): number\n  return f(n - 1)\nend\n", "f calls itself");
        rejects("query f(): bool\n  return g()\nend\n\nquery g(): bool\n  return f()\nend\n", "calls itself");
    }

    @Test
    void aQueryAnswersOnEveryPath() {
        rejects("query f(a: number): bool\n  if a > 1 then\n    return true\n  end\nend\n", "f may end without returning");
        assertEquals("", problems("""
                query f(a: number): bool
                  if a > 1 then
                    return true
                  else
                    return false
                  end
                end
                """));
    }

    @Test
    void typesAndNamesMustAgree() {
        rejects("query f(): bool\n  return 1\nend\n", "f returns bool, not number");
        rejects("query f(): number\n  return stock(5)\nend\n", "stock takes a resource as argument 1, not number");
        rejects("query f(): bool\n  return not 3\nend\n", "not takes bool, not number");
        rejects("query f(): bool\n  return x\nend\n", "unknown name x");
        rejects("query f(): bool\n  return online(1)\nend\n", "online takes 0 arguments, not 1");
        rejects("query f(): bool\n  return 1 < \"a\"\nend\n", "< takes number, not text");
        rejects("query f(): number\n  if true then\n    local a = 1\n  end\n  return a\nend\n", "unknown name a");
        rejects("query online(): bool\n  return true\nend\n", "online is a library function");
        rejects("action a()\n  return 1\nend\n", "an action returns nothing");
    }

    @Test
    void problemsPointAtTheirNode() {
        var program = ScriptParser.parse("query f(): bool\n  return redstone(1)\nend\n", new Ids("n"));
        var problem = Checker.check(program).get(0);
        var ret = (Stmt.Return) program.functions().get(0).body().get(0);
        assertEquals(ret.value().id(), problem.node(), "both views can mark the call");
    }
}
