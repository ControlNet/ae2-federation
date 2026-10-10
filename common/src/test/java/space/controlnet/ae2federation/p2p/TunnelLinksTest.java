package space.controlnet.ae2federation.p2p;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TunnelLinksTest {
    private static final List<String> GROUP = List.of("input", "first", "second");

    @Test
    void theInputLinksToEveryActiveOutput() {
        assertEquals(List.of("first", "second"),
                TunnelLinks.linked("input", "input", GROUP, Set.of("input", "first", "second")::contains));
    }

    @Test
    void anOutputLinksOnlyToTheInput() {
        assertEquals(List.of("input"),
                TunnelLinks.linked("first", "input", GROUP, Set.of("input", "first", "second")::contains));
    }

    @Test
    void theInputLeavesOutAnInactiveOutput() {
        assertEquals(List.of("second"), TunnelLinks.linked("input", "input", GROUP, Set.of("input", "second")::contains));
    }

    @Test
    void anInactiveInputLinksNothing() {
        assertEquals(List.of(), TunnelLinks.linked("first", "input", GROUP, Set.of("first", "second")::contains));
    }

    @Test
    void noInputLinksNothing() {
        assertEquals(List.of(), TunnelLinks.linked("first", null, List.of("first", "second"),
                Set.of("first", "second")::contains));
    }

    @Test
    void anInactiveTunnelLinksNothingAndIsLeftOut() {
        assertEquals(List.of(), TunnelLinks.linked("first", "input", GROUP, Set.of("input", "second")::contains));
        assertEquals(List.of("input"), TunnelLinks.linked("second", "input", GROUP, Set.of("input", "second")::contains));
    }
}
