package space.controlnet.ae2federation.client.policy;

/**
 * A Federation screen's size in GUI units: as much of the screen as it can use, less a margin, up to the screen's
 * own maximum. The workspace grows to {@link #MAX_WIDTH} x {@link #MAX_HEIGHT}, so the graph, wires and aside get
 * the room the player's screen offers; beyond that wider rows only spread the same content thin. Small dialogs pass
 * their own, smaller maximum.
 */
public record WorkspaceSize(int width, int height) {
    public static final int MAX_WIDTH = 1024;
    public static final int MAX_HEIGHT = 640;
    /** Margin on each side when the screen has room for the full 640x380 layout; 4 below that. */
    private static final int MARGIN = 8;
    private static final int TIGHT_MARGIN = 4;

    public static WorkspaceSize fit(int screenWidth, int screenHeight) {
        return fit(screenWidth, screenHeight, MAX_WIDTH, MAX_HEIGHT);
    }

    public static WorkspaceSize fit(int screenWidth, int screenHeight, int maxWidth, int maxHeight) {
        int margin = screenWidth > FixedGuiScale.MIN_WIDTH && screenHeight > FixedGuiScale.MIN_HEIGHT ? MARGIN : TIGHT_MARGIN;
        return new WorkspaceSize(Math.min(maxWidth, screenWidth - 2 * margin), Math.min(maxHeight, screenHeight - 2 * margin));
    }
}
