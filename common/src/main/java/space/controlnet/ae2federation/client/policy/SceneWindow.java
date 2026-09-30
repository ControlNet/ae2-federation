package space.controlnet.ae2federation.client.policy;

import java.util.List;

/**
 * The box of world blocks a 3D location preview draws: a square of the given radius around the focus, from a few
 * blocks below the lowest nearby network block (the ground it stands on) to a couple above the highest, never more
 * than {@code maxHalfHeight} blocks from the focus so a tall or distant network cannot make the preview unbounded.
 */
public record SceneWindow(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    private static final int GROUND = 4;
    private static final int HEADROOM = 2;

    public static SceneWindow around(BlockMarks.Mark focus, List<BlockMarks.Mark> marks, int radius, int maxHalfHeight) {
        int low = focus.y();
        int high = focus.y();
        for (var mark : marks) {
            if (Math.abs(mark.x() - focus.x()) > radius || Math.abs(mark.z() - focus.z()) > radius) continue;
            low = Math.min(low, mark.y());
            high = Math.max(high, mark.y());
        }
        int minY = Math.max(focus.y() - maxHalfHeight, low - GROUND);
        int maxY = Math.min(focus.y() + maxHalfHeight, high + HEADROOM);
        return new SceneWindow(focus.x() - radius, minY, focus.z() - radius, focus.x() + radius, maxY, focus.z() + radius);
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public int volume() {
        return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }
}
