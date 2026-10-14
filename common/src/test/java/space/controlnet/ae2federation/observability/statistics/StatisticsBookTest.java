package space.controlnet.ae2federation.observability.statistics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class StatisticsBookTest {
    @Test
    void aCounterIsARatePerObservedSecond() {
        var book = new StatisticsBook<String>();
        for (long second = 600; second < 605; second++) book.observe(second);
        book.count("flow", 602, 10);
        var rates = book.rate("flow", StatisticsRange.FIVE_SECONDS, 604);
        assertEquals(2, rates[119], "10 items in one second of a five-second point is 2 a second");
        var seconds = book.rate("flow", StatisticsRange.SECONDS, 604);
        assertEquals(0, seconds[119], "an observed quiet second is zero");
        assertEquals(10, seconds[117]);
    }

    @Test
    void secondsTheServerDidNotObserveAreMissingForCountersAndGauges() {
        var book = new StatisticsBook<String>();
        book.observe(100);
        book.gauge("energy", 100, 50);
        book.observe(103);
        var rates = book.rate("flow", StatisticsRange.SECONDS, 103);
        assertEquals(0, rates[119]);
        assertTrue(Double.isNaN(rates[118]), "the server was not running");
        var observed = book.observed(StatisticsRange.SECONDS, 103);
        assertTrue(observed[119]);
        assertFalse(observed[118]);
        var energy = book.gauge("energy", StatisticsRange.SECONDS, 103);
        assertEquals(50, energy[116]);
        assertTrue(Double.isNaN(energy[119]), "a gauge not sampled in an observed second is missing too");
    }

    @Test
    void totalsAndLatestReadAcrossTheRange() {
        var book = new StatisticsBook<String>();
        for (long second = 50; second < 55; second++) book.observe(second);
        book.count("flow", 50, 3);
        book.count("flow", 53, 4);
        book.gauge("energy", 51, 20);
        book.gauge("energy", 52, 30);
        assertEquals(7, book.total("flow", StatisticsRange.SECONDS, 54));
        assertEquals(0, book.total("none", StatisticsRange.SECONDS, 54));
        assertEquals(30, book.latest("energy", StatisticsRange.SECONDS, 54));
        assertTrue(Double.isNaN(book.latest("none", StatisticsRange.SECONDS, 54)));
    }

    @Test
    void rankingsAreKeptPerName() {
        var book = new StatisticsBook<String>();
        book.observe(5);
        book.moved("to-a", 5, "iron", 3);
        book.moved("to-b", 5, "gold", 9);
        assertEquals(List.of("iron"), book.ranking("to-a", StatisticsRange.SECONDS, 5, 4).stream()
                .map(TopKeys.Ranked::key).toList());
        assertTrue(book.ranking("to-c", StatisticsRange.SECONDS, 5, 4).isEmpty());
    }

    @Test
    void pruneForgetsSeriesOlderThanADay() {
        var book = new StatisticsBook<String>();
        book.observe(10);
        book.gauge("old", 10, 1);
        book.count("old-flow", 10, 1);
        book.moved("old-rank", 10, "iron", 1);
        book.observe(90_000);
        book.gauge("new", 90_000, 1);
        book.prune(90_000);
        assertEquals(List.of("new"), List.copyOf(book.gaugeNames()));
        assertTrue(book.counterNames().isEmpty());
        assertTrue(book.rankingNames().isEmpty());
    }

    @Test
    void aSavedBookRestoresEverySeries() {
        var book = new StatisticsBook<String>();
        for (long second = 1000; second < 1010; second++) {
            book.observe(second);
            book.gauge("energy", second, second % 7);
        }
        book.count("flow", 1004, 12);
        book.moved("to-a", 1004, "iron", 12);
        var restored = StatisticsBook.restore(book.save(1009));
        for (var range : StatisticsRange.values()) {
            assertArrayEquals(book.gauge("energy", range, 1009), restored.gauge("energy", range, 1009));
            assertArrayEquals(book.rate("flow", range, 1009), restored.rate("flow", range, 1009));
            assertArrayEquals(book.observed(range, 1009), restored.observed(range, 1009));
            assertEquals(book.ranking("to-a", range, 1009, 4), restored.ranking("to-a", range, 1009, 4));
        }
    }
}
