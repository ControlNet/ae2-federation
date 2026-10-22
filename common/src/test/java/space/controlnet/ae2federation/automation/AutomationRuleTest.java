package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The server's switches for programs reaching outside the game. */
final class AutomationRuleTest {
    @AfterEach
    void reset() {
        AutomationRule.reset();
    }

    @Test
    void everythingIsOnByDefault() {
        assertTrue(AutomationRule.fileAccess());
        assertTrue(AutomationRule.http());
        assertTrue(AutomationRule.websockets());
        assertEquals(Duration.ofSeconds(10), AutomationRule.timeout());
        assertEquals(16L * 1024 * 1024, AutomationRule.workspaceCapBytes());
    }

    @Test
    void theServerSetsThem() {
        AutomationRule.set(false, true, false, 3, 2);
        assertFalse(AutomationRule.fileAccess());
        assertTrue(AutomationRule.http());
        assertFalse(AutomationRule.websockets());
        assertEquals(Duration.ofSeconds(3), AutomationRule.timeout());
        assertEquals(2L * 1024 * 1024, AutomationRule.workspaceCapBytes());
    }

    @Test
    void aSwitchedOffGroupStopsTheRun() {
        AutomationRule.set(false, false, false, 10, 16);
        assertEquals("PermissionError: file access is turned off on this server (allowFileAccess)",
                assertThrows(space.controlnet.ae2federation.script.RunError.class,
                        () -> AutomationRule.require("fs.read")).toString());
        assertEquals("PermissionError: HTTP requests are turned off on this server (allowHttp)",
                assertThrows(space.controlnet.ae2federation.script.RunError.class,
                        () -> AutomationRule.require("net.http")).toString());
        assertEquals("PermissionError: websockets are turned off on this server (allowWebsockets)",
                assertThrows(space.controlnet.ae2federation.script.RunError.class,
                        () -> AutomationRule.require("net.receive")).toString());
        AutomationRule.require("ae2.amount");
    }

    @Test
    void outOfRangeValuesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> AutomationRule.set(true, true, true, 0, 16));
        assertThrows(IllegalArgumentException.class,
                () -> AutomationRule.set(true, true, true, AutomationRule.MAX_TIMEOUT_SECONDS + 1, 16));
        assertThrows(IllegalArgumentException.class, () -> AutomationRule.set(true, true, true, 10, 0));
        assertThrows(IllegalArgumentException.class,
                () -> AutomationRule.set(true, true, true, 10, AutomationRule.MAX_WORKSPACE_MB + 1));
    }
}
