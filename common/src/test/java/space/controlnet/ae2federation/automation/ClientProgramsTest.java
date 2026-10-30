package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.script.Library;

/** The tab's client side: what it refuses itself, and what it forgets when the connection ends. */
final class ClientProgramsTest {
    @Test
    void aTextOverTheCapIsRefusedAtOnceAsAnAnswer() {
        var answers = new ArrayList<ProgramSync.Answer>();
        ClientPrograms.get().upload("big", "", "x".repeat(Library.MAX_TEXT + 1), answers::add);
        assertEquals(1, answers.size(), "the caller hears back, so it does not wait for ever");
        assertFalse(answers.get(0).ok());
        assertEquals("a program may hold at most 64000 characters", answers.get(0).message());
    }

    @Test
    void resetForgetsTheLastServersPrograms() {
        var programs = ClientPrograms.get();
        programs.receive(new ProgramSync.Index(List.of(), List.of(), List.of(), List.of(), List.of()));
        programs.receive(new ProgramSync.Text("old", "v", 0, 1, "# old server"));
        var answers = new ArrayList<ProgramSync.Answer>();
        programs.request("log", List.of("installation-a"), answers::add);
        programs.reset();
        assertTrue(programs.index().isEmpty());
        assertTrue(programs.text("old").isEmpty());
        // Request numbers are not shown; this test JVM has used far fewer than these.
        for (int request = 1; request <= 1000; request++) {
            programs.receive(new ProgramSync.Answer(request, true, "late"));
        }
        assertEquals(List.of(), answers, "an answer awaited from the old server is dropped");
    }
}
