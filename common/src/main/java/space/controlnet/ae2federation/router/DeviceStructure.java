package space.controlnet.ae2federation.router;

import net.minecraft.core.BlockPos;

/**
 * Finds the solid cuboid a Router or Switch belongs to, as AE2's {@code MBCalculator} finds a crafting CPU's: the
 * bounds grow from the block along each axis through blocks of the same type, every position inside them must be that
 * type, and no block of that type may touch the outside. Touching same-type blocks are therefore always judged
 * together: one extra block against a finished body leaves the whole candidate unformed. Unlike AE2 it never loads a
 * chunk: a position it needs in an unloaded chunk makes the answer {@link Unknown}.
 */
public final class DeviceStructure {
    /** The longest edge of a body, in blocks; a longer candidate stays unformed. A scan visits at most 8³ + shell. */
    public static final int MAX_EDGE = 8;

    public enum Cell { MEMBER, OTHER, UNLOADED }

    @FunctionalInterface
    public interface Lookup {
        Cell at(int x, int y, int z);
    }

    public sealed interface Result {
    }

    /** A formed body from {@code min} to {@code max}, both inclusive. */
    public record Valid(BlockPos min, BlockPos max) implements Result {
        public boolean contains(BlockPos position) {
            return position.getX() >= min.getX() && position.getX() <= max.getX()
                    && position.getY() >= min.getY() && position.getY() <= max.getY()
                    && position.getZ() >= min.getZ() && position.getZ() <= max.getZ();
        }
    }

    /** {@code WRONG_SHAPE}: a cuboid that a device's own rule rejects ({@link FormableDevice#judge}). */
    public enum Reason { TOO_LARGE, NOT_SOLID, TOUCHING, WRONG_SHAPE }

    public record Invalid(Reason reason) implements Result {
    }

    /** Part of what decides the answer lies in an unloaded chunk. */
    public enum Unknown implements Result {
        INSTANCE
    }

    private DeviceStructure() {
    }

    /** The body containing the member at {@code start}. */
    public static Result evaluate(BlockPos start, Lookup lookup, int maxEdge) {
        int[] min = {start.getX(), start.getY(), start.getZ()};
        int[] max = min.clone();
        // Grow like MBCalculator: the low corner first, then the high corner from the start.
        for (int axis = 0; axis < 3; axis++) {
            var grown = grow(lookup, min, axis, -1, start, maxEdge);
            if (grown != null) return grown;
        }
        for (int axis = 0; axis < 3; axis++) {
            var probe = new int[] {start.getX(), start.getY(), start.getZ()};
            for (int a = 0; a < axis; a++) probe[a] = max[a];
            var grown = grow(lookup, probe, axis, 1, start, maxEdge);
            if (grown != null) return grown;
            max[axis] = probe[axis];
        }
        for (int axis = 0; axis < 3; axis++) {
            if (max[axis] - min[axis] + 1 > maxEdge) return new Invalid(Reason.TOO_LARGE);
        }
        var unknown = false;
        for (int x = min[0]; x <= max[0]; x++) {
            for (int y = min[1]; y <= max[1]; y++) {
                for (int z = min[2]; z <= max[2]; z++) {
                    switch (lookup.at(x, y, z)) {
                        case OTHER -> {
                            return new Invalid(Reason.NOT_SOLID);
                        }
                        case UNLOADED -> unknown = true;
                        case MEMBER -> {
                        }
                    }
                }
            }
        }
        // The shell one block outside each face: a same-type block there joins the candidate and breaks the cuboid.
        for (int axis = 0; axis < 3; axis++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                int[] lo = min.clone();
                int[] hi = max.clone();
                lo[axis] = hi[axis] = sign < 0 ? min[axis] - 1 : max[axis] + 1;
                for (int x = lo[0]; x <= hi[0]; x++) {
                    for (int y = lo[1]; y <= hi[1]; y++) {
                        for (int z = lo[2]; z <= hi[2]; z++) {
                            switch (lookup.at(x, y, z)) {
                                case MEMBER -> {
                                    return new Invalid(Reason.TOUCHING);
                                }
                                case UNLOADED -> unknown = true;
                                case OTHER -> {
                                }
                            }
                        }
                    }
                }
            }
        }
        return unknown ? Unknown.INSTANCE : new Valid(new BlockPos(min[0], min[1], min[2]),
                new BlockPos(max[0], max[1], max[2]));
    }

    /**
     * Moves {@code corner[axis]} outward while the next position is a member; returns a final answer only when the
     * growth meets an unloaded position or passes the provisional edge bound.
     */
    private static Result grow(Lookup lookup, int[] corner, int axis, int sign, BlockPos start, int maxEdge) {
        int origin = axis == 0 ? start.getX() : axis == 1 ? start.getY() : start.getZ();
        while (true) {
            int[] next = corner.clone();
            next[axis] += sign;
            var cell = lookup.at(next[0], next[1], next[2]);
            if (cell == Cell.UNLOADED) return Unknown.INSTANCE;
            if (cell != Cell.MEMBER) return null;
            corner[axis] = next[axis];
            if (Math.abs(corner[axis] - origin) + 1 > maxEdge) return new Invalid(Reason.TOO_LARGE);
        }
    }
}
