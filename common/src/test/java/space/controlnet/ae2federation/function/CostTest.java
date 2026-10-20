package space.controlnet.ae2federation.function;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * What an installed function costs its computing device (user decisions 2026-10-20): its graph nodes and those of every
 * function it calls, once each, and the most each of its moving calls can move.
 */
final class CostTest {
    private static final Workspace WORKSPACE = new Workspace(Map.of("tools", ScriptParser.parse("""
            def give(target: Network, item: Item, n: int) -> None:
                ae2.move("main_base", target, item, n)

            def twice(n: int) -> int:
                return n * 2
            """).program()), CompletionTest.CATALOG);

    private static Program program(String source) {
        var parsed = ScriptParser.parse(source);
        assertEquals(List.of(), parsed.errors());
        return parsed.program();
    }

    private static FunctionDef function(Program program, String name) {
        return program.functions().stream().filter(f -> f.name().equals(name)).findFirst().orElseThrow();
    }

    private static int nodes(FunctionDef function) {
        return Canvas.of(function, Map.of(), Set.of()).nodes().size();
    }

    private static List<OptionalLong> most(String source, String function, Map<String, Long> args) {
        return Cost.of(program(source), function, args, WORKSPACE).moves().stream().map(Cost.Move::most).toList();
    }

    @Test
    void nodesCountTheFunctionAndEachFunctionItCallsOnce() {
        var program = program("""
                def _step(x: int) -> int:
                    return x + 1

                def main(n: int) -> int:
                    a = _step(n)
                    b = _step(a)
                    return b
                """);
        assertEquals(nodes(function(program, "main")) + nodes(function(program, "_step")),
                Cost.of(program, "main", Map.of(), WORKSPACE).nodes());
    }

    @Test
    void recursionCountsEachFunctionOnce() {
        var program = program("""
                def countdown(n: int) -> int:
                    if n <= 0:
                        return 0
                    return countdown(n - 1)
                """);
        assertEquals(nodes(function(program, "countdown")), Cost.of(program, "countdown", Map.of(), WORKSPACE).nodes());
    }

    @Test
    void callsIntoAnotherFileCountThatFunction() {
        var program = program("""
                def main(n: int) -> int:
                    return tools.twice(n)
                """);
        assertEquals(nodes(function(program, "main")) + nodes(function(WORKSPACE.files().get("tools"), "twice")),
                Cost.of(program, "main", Map.of(), WORKSPACE).nodes());
    }

    @Test
    void functionsTheInstalledOneNeverCallsCostNothing() {
        var program = program("""
                def unused(n: int) -> int:
                    return n

                def main() -> int:
                    return 1
                """);
        assertEquals(nodes(function(program, "main")), Cost.of(program, "main", Map.of(), WORKSPACE).nodes());
    }

    /** Cost counts nodes without checking the program, so the count must not depend on the checker's results. */
    @ParameterizedTest
    @MethodSource("space.controlnet.ae2federation.function.CanvasTest#sources")
    void theNodeCountDoesNotNeedTheChecker(String source) {
        var parsed = ScriptParser.parse(source);
        var result = Checker.check(parsed.program(), WORKSPACE);
        for (var function : parsed.program().functions()) {
            assertEquals(Canvas.of(function, result).nodes().size(), nodes(function), function.name());
        }
    }

    @Test
    void anInstallationAndItsConditionCountTheFunctionsTheyShareOnce() {
        var program = program("""
                def _level(n: int) -> int:
                    return n + 1

                def low(n: int) -> bool:
                    return _level(n) < 10

                def main(n: int) -> int:
                    return _level(n)
                """);
        assertEquals(nodes(function(program, "main")) + nodes(function(program, "low"))
                + nodes(function(program, "_level")), Cost.nodes(program, List.of("main", "low"), WORKSPACE));
        assertEquals(Cost.of(program, "main", Map.of(), WORKSPACE).nodes(),
                Cost.nodes(program, List.of("main"), WORKSPACE));
    }

    @Test
    void aConstantAmountIsItsBound() {
        assertEquals(List.of(OptionalLong.of(16)), most("""
                def feed_machine(port: Interface, item: Item) -> None:
                    machine = ae2f.select(port)
                    if mc.amount(machine, item) < 16:
                        ae2.export_to(machine, item, 16)
                """, "feed_machine", Map.of()));
    }

    @Test
    void anInstallationArgumentIsAConstant() {
        assertEquals(List.of(OptionalLong.of(64)), most("""
                def stock(base: Network, farm: Network, item: Item, n: int) -> None:
                    ae2.move(base, farm, item, n)
                """, "stock", Map.of("n", 64L)));
    }

    @Test
    void aDefaultIsTheValueWhenTheArgumentIsLeftOut() {
        assertEquals(List.of(OptionalLong.of(8)), most("""
                def stock(base: Network, farm: Network, item: Item, n: int = 8) -> None:
                    ae2.move(base, farm, item, n)
                """, "stock", Map.of()));
    }

    @Test
    void minWithAConstantBoundsAWorldValue() {
        assertEquals(List.of(OptionalLong.of(512)), most("""
                def empty_chest(port: Interface) -> None:
                    chest = ae2f.select(port)
                    for stack in mc.storage(chest):
                        ae2.import_from(chest, stack.resource, min(stack.amount, 512))
                """, "empty_chest", Map.of()));
    }

    /** Library amounts are never negative, so what is missing up to a target is at most the target. */
    @Test
    void whatIsMissingUpToATargetIsAtMostTheTarget() {
        assertEquals(List.of(OptionalLong.of(1000)), most("""
                def keep_stocked(base: Network, farm: Network, item: Item, target: int) -> None:
                    missing = target - ae2.amount(item, farm) - ae2.crafting(item, farm)
                    if missing > 0:
                        ae2.move(base, farm, item, missing)
                """, "keep_stocked", Map.of("target", 1000L)));
    }

    @Test
    void aValueReadFromTheWorldHasNoBound() {
        assertEquals(List.of(OptionalLong.empty()), most("""
                def empty_chest(port: Interface) -> None:
                    chest = ae2f.select(port)
                    for stack in mc.storage(chest):
                        ae2.import_from(chest, stack.resource, stack.amount)
                """, "empty_chest", Map.of()));
    }

    @Test
    void aTotalBuiltUpInALoopHasNoBound() {
        assertEquals(List.of(OptionalLong.empty()), most("""
                def grow(base: Network, farm: Network, item: Item) -> None:
                    n = 1
                    while True:
                        n += 1
                        ae2.move(base, farm, item, n)
                """, "grow", Map.of()));
    }

    @Test
    void aLoopOverAConstantRangeIsBounded() {
        assertEquals(List.of(OptionalLong.of(36)), most("""
                def steps(base: Network, farm: Network, item: Item) -> None:
                    for i in range(10):
                        ae2.move(base, farm, item, i * 4)
                """, "steps", Map.of()));
    }

    @Test
    void aCalledFunctionsParameterIsBoundedByEveryCallSite() {
        assertEquals(List.of(OptionalLong.of(100)), most("""
                def _send(base: Network, farm: Network, item: Item, n: int) -> None:
                    ae2.move(base, farm, item, n)

                def main(base: Network, farm: Network, item: Item) -> None:
                    _send(base, farm, item, 32)
                    _send(base, farm, item, 100)
                """, "main", Map.of()));
    }

    @Test
    void movesInAnotherFileAreCounted() {
        assertEquals(List.of(OptionalLong.of(12)), most("""
                def main(farm: Network) -> None:
                    tools.give(farm, "minecraft:iron_ingot", 12)
                """, "main", Map.of()));
    }

    @Test
    void craftingAndFeAreNotMoves() {
        assertEquals(List.of(), most("""
                def busy(port_from: Interface, port_to: Interface) -> None:
                    ae2.craft("minecraft:iron_ingot", 64)
                    mc.move_fe(ae2f.select(port_from), ae2f.select(port_to), 10000)
                """, "busy", Map.of()));
    }

    /** Each library example that moves resources, as installed with no arguments: its bounds, or none (elastic). */
    @Test
    void theLibraryExamplesMoveWhatTheySay() {
        var found = new java.util.TreeMap<String, List<OptionalLong>>();
        StandardLibrary.NAMESPACES.forEach((namespace, signatures) -> signatures.forEach(signature -> {
            if (signature.example().isEmpty()) {
                return;
            }
            var example = program(signature.example());
            var cost = Cost.of(example, example.functions().get(0).name(), Map.of(), WORKSPACE);
            if (!cost.moves().isEmpty()) {
                found.put(namespace + "." + signature.name(), cost.moves().stream().map(Cost.Move::most).toList());
            }
        }));
        assertEquals(Map.of("ae2.import_from", List.of(OptionalLong.empty()),
                "ae2.export_to", List.of(OptionalLong.of(16)),
                "ae2.move", List.of(OptionalLong.of(64)),
                "mc.move", List.of(OptionalLong.of(64))), found);
    }

    @Test
    void aMoveKeepsTheResourceAsWritten() {
        var moves = Cost.of(program("""
                def pass_along(port_in: Interface, port_out: Interface) -> None:
                    mc.move(ae2f.select(port_in), ae2f.select(port_out), "minecraft:iron_ingot", 64)
                """), "pass_along", Map.of(), WORKSPACE).moves();
        assertEquals(1, moves.size());
        assertEquals(new Expr.StrLit(moves.get(0).resource().id(), "minecraft:iron_ingot"), moves.get(0).resource());
    }
}
