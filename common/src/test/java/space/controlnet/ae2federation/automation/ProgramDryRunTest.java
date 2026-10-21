package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.script.DryRun;
import space.controlnet.ae2federation.script.RunError;
import space.controlnet.ae2federation.script.Runner;

/** The dry run's answer as it travels to the editor: text, put back together on the client. */
final class ProgramDryRunTest {
    @Test
    void aFinishedRunKeepsItsValuesAndTheNodesThatRan() {
        var values = new LinkedHashMap<String, String>();
        values.put("n3", "37");
        values.put("n7", "'minecraft:iron_ingot'");
        var answer = ProgramDryRun.decode(ProgramDryRun.encode("0123456789abcdef",
                new DryRun.Result(values, Set.of("n3", "n7", "n9"), null, true)));
        assertEquals("0123456789abcdef", answer.version());
        assertEquals(ProgramDryRun.Status.DONE, answer.status());
        assertEquals(Map.of("n3", "37", "n7", "'minecraft:iron_ingot'"), answer.values());
        assertEquals(Set.of("n3", "n7", "n9"), answer.ran());
        assertEquals("", answer.failureNode());
        assertEquals("", answer.failure());
    }

    @Test
    void aFailedRunNamesItsNodeAndError() {
        var error = new RunError("ValueError", "no interface 'lamp9'\tin this domain");
        var failure = new Runner.Failure("iron_tools", "n12", error);
        var answer = ProgramDryRun.decode(ProgramDryRun.encode("v", new DryRun.Result(Map.of(), Set.of("n12"),
                failure, true)));
        assertEquals(ProgramDryRun.Status.FAILED, answer.status());
        assertEquals("n12", answer.failureNode());
        assertEquals("ValueError: no interface 'lamp9' in this domain", answer.failure());
    }

    @Test
    void aRunStoppedByTheBudgetSaysSo() {
        var answer = ProgramDryRun.decode(ProgramDryRun.encode("v", new DryRun.Result(Map.of(), Set.of(), null,
                false)));
        assertEquals(ProgramDryRun.Status.STOPPED, answer.status());
    }

    @Test
    void valuesStopBeforeTheAnswerGrowsTooLong() {
        var values = new LinkedHashMap<String, String>();
        for (int i = 0; i < 2000; i++) {
            values.put("n" + i, "x".repeat(DryRun.MAX_TEXT));
        }
        var text = ProgramDryRun.encode("v", new DryRun.Result(values, Set.of("n1"), null, true));
        assertTrue(text.length() <= ProgramDryRun.MAX_ANSWER, "length " + text.length());
        var answer = ProgramDryRun.decode(text);
        assertTrue(answer.values().size() > 100);
        assertEquals(Set.of("n1"), answer.ran());
    }
}
