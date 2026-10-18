package space.controlnet.ae2federation.function;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class EvaluatorTest {
    private static final Program IRON = ScriptParser.parse(Samples.IRON, new Ids("n"));
    private static final ResourceRef IRON_INGOT = new ResourceRef(ResourceRef.ITEM, "minecraft:iron_ingot");

    /**
     * TEST-ONLY STAND-IN for a network's observations: fixed answers, no world. The game will implement
     * {@link Observations} over a real network; nothing here is shipped.
     */
    private record FixedObservations(Map<ResourceRef, Value> stock, Value online, Value redstone)
            implements Observations {
        @Override
        public Value stock(ResourceRef resource) {
            return stock.getOrDefault(resource, new Value.Num(0));
        }

        @Override
        public Value online() {
            return online;
        }

        @Override
        public Value redstoneIn() {
            return redstone;
        }
    }

    /** TEST-ONLY STAND-IN for an action's effects: it records the calls instead of acting on a world. */
    private static final class RecordedEffects implements Effects {
        final List<String> calls = new ArrayList<>();

        @Override
        public void redstone(int level) {
            calls.add("redstone " + level);
        }

        @Override
        public void craft(ResourceRef resource, long amount) {
            calls.add("craft " + resource.id() + " " + amount);
        }
    }

    private static FixedObservations world(Value iron, Value online) {
        return new FixedObservations(Map.of(IRON_INGOT, iron), online, new Value.Num(0));
    }

    private static Value keepIron(Value iron, Value online, double min) {
        return Evaluator.query(IRON, "keep_iron", List.of(new Value.Num(min)), world(iron, online), 1000);
    }

    @Test
    void aQueryAnswersFromWhatItObserves() {
        var yes = new Value.Bool(true);
        assertEquals(yes, keepIron(new Value.Num(100), yes, 64));
        assertEquals(new Value.Bool(false), keepIron(new Value.Num(10), yes, 64));
        assertEquals(yes, keepIron(new Value.Num(500), new Value.Bool(false), 64), "a large surplus shares anyway");
    }

    @Test
    void anUnavailableObservationIsNeitherFalseNorZero() {
        var yes = new Value.Bool(true);
        // The sample checks for it and answers false.
        assertEquals(new Value.Bool(false), keepIron(Value.UNAVAILABLE, yes, 64));
        // Where it is not checked it flows on: "and" with false is false, with true it stays unknown.
        assertEquals(new Value.Bool(false), keepIron(new Value.Num(10), Value.UNAVAILABLE, 64));
        assertEquals(Value.UNAVAILABLE, keepIron(new Value.Num(100), Value.UNAVAILABLE, 64));
    }

    @Test
    void anActionActsAndCallsQueries() {
        var effects = new RecordedEffects();
        Evaluator.action(IRON, "signal_low_iron", List.of(), world(new Value.Num(10), new Value.Bool(true)), effects,
                1000);
        assertEquals(List.of("redstone 15", "craft minecraft:iron_block 2"), effects.calls);
    }

    @Test
    void aQueryCanAnswerAFilter() {
        var filter = Evaluator.query(IRON, "workshop_filter", List.of(), world(new Value.Num(0), Value.UNAVAILABLE), 100);
        assertEquals(new Value.Filter(Value.Filter.Mode.ALLOW_LIST,
                Set.of(IRON_INGOT, new ResourceRef("fluid", "minecraft:water"))), filter);
    }

    @Test
    void aRunStopsAtItsBudget() {
        var error = assertThrows(Evaluator.BudgetExceeded.class, () -> Evaluator.query(IRON, "keep_iron",
                List.of(new Value.Num(64)), world(new Value.Num(100), new Value.Bool(true)), 5));
        assertTrue(error.getMessage().contains("5"));
        // With 10 iron the whole action takes 30 steps; craft's arguments are steps 29 and 30.
        var effects = new RecordedEffects();
        assertThrows(Evaluator.BudgetExceeded.class, () -> Evaluator.action(IRON, "signal_low_iron", List.of(),
                world(new Value.Num(10), new Value.Bool(true)), effects, 28));
        assertEquals(List.of("redstone 15"), effects.calls, "effects made before the budget ran out stay made");
    }

    @Test
    void computeIsCountedFromTheProgramIncludingWhatItCalls() {
        int keep = Cost.compute(IRON, "keep_iron");
        int signal = Cost.compute(IRON, "signal_low_iron");
        int[] nodes = {0};
        Walk.nodes(new Program(List.of(IRON.function("keep_iron").orElseThrow())), (id, label) -> nodes[0]++);
        assertEquals(nodes[0], keep);
        assertTrue(signal > keep, "an action that calls keep_iron pays for it: " + signal);
    }

    @Test
    void theComputeMeasureIsEnoughForAnyWorld() {
        var values = List.of(new Value.Num(0), new Value.Num(70), new Value.Num(1000), Value.UNAVAILABLE);
        var online = List.of(new Value.Bool(true), new Value.Bool(false), Value.UNAVAILABLE);
        for (var iron : values) {
            for (var on : online) {
                Evaluator.query(IRON, "keep_iron", List.of(new Value.Num(64)), world(iron, on),
                        Cost.compute(IRON, "keep_iron"));
                Evaluator.action(IRON, "signal_low_iron", List.of(), world(iron, on), new RecordedEffects(),
                        Cost.compute(IRON, "signal_low_iron"));
            }
        }
    }
}
