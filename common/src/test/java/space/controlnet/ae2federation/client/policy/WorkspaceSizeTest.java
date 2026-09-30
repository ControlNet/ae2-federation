package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WorkspaceSizeTest {
    @Test
    void theWorkspaceFillsTheScreenLeavingAMargin() {
        assertEquals(new WorkspaceSize(784, 464), WorkspaceSize.fit(800, 480));
        assertEquals(new WorkspaceSize(944, 524), WorkspaceSize.fit(960, 540));
        assertEquals(new WorkspaceSize(837, 464), WorkspaceSize.fit(853, 480));
    }

    @Test
    void aVeryLargeScreenStopsAtTheMaximumSoTheLayoutDoesNotSpreadThin() {
        assertEquals(new WorkspaceSize(WorkspaceSize.MAX_WIDTH, WorkspaceSize.MAX_HEIGHT), WorkspaceSize.fit(1920, 1080));
    }

    @Test
    void aSmallScreenKeepsOnlyAThinMargin() {
        assertEquals(new WorkspaceSize(312, 232), WorkspaceSize.fit(320, 240));
        assertEquals(new WorkspaceSize(632, 372), WorkspaceSize.fit(640, 380));
    }

    @Test
    void aSmallDialogKeepsItsOwnSize() {
        assertEquals(new WorkspaceSize(440, 280), WorkspaceSize.fit(800, 480, 440, 280));
        assertEquals(new WorkspaceSize(360, 160), WorkspaceSize.fit(800, 480, 360, 160));
        assertEquals(new WorkspaceSize(312, 160), WorkspaceSize.fit(320, 240, 360, 160));
    }
}
