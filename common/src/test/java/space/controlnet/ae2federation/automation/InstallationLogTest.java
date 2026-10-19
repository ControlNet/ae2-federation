package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** An installation's state and log as the {@code log} request answers it, and the ages the aside shows. */
final class InstallationLogTest {
    @Test
    void theAnswerCarriesTheRunsAndTheEntriesNewestFirstWithTheirAges() {
        var entries = List.of(new ProgramScheduler.LogEntry(100, "started"),
                new ProgramScheduler.LogEntry(340, "done: None"), new ProgramScheduler.LogEntry(360, "started"));
        var text = InstallationLog.answer(1, 400, entries);
        assertEquals("running 1\n40\tstarted\n60\tdone: None\n300\tstarted", text);
        var log = InstallationLog.parse(text);
        assertEquals(1, log.running());
        assertEquals(List.of(new InstallationLog.Line(40, "started"), new InstallationLog.Line(60, "done: None"),
                new InstallationLog.Line(300, "started")), log.lines());
    }

    @Test
    void anEntryKeepsItsTabsAndLineBreaksOut() {
        var text = InstallationLog.answer(0, 10, List.of(new ProgramScheduler.LogEntry(10, "failed: a\tb\nc")));
        assertEquals(List.of(new InstallationLog.Line(0, "failed: a b c")), InstallationLog.parse(text).lines());
    }

    @Test
    void nothingLoggedIsIdle() {
        var log = InstallationLog.parse(InstallationLog.answer(0, 5, List.of()));
        assertEquals(0, log.running());
        assertEquals(List.of(), log.lines());
    }

    @Test
    void agesReadAsTheBoardShowsThem() {
        assertEquals("now", InstallationLog.age(0));
        assertEquals("now", InstallationLog.age(19));
        assertEquals("1 s", InstallationLog.age(20));
        assertEquals("59 s", InstallationLog.age(59 * 20 + 19));
        assertEquals("1 min", InstallationLog.age(60 * 20));
        assertEquals("59 min", InstallationLog.age(3600 * 20 - 1));
        assertEquals("2 h", InstallationLog.age(2 * 3600 * 20));
    }
}
