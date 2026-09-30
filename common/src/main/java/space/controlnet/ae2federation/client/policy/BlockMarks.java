package space.controlnet.ae2federation.client.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Block positions exchanged as text or flat integer lists, free of Minecraft types so they stay unit-testable. */
public final class BlockMarks {
    private BlockMarks() {
    }

    public record Mark(int x, int y, int z) {
    }

    /** Parses {@code BlockPos.toShortString()} ("x, y, z"). */
    public static Optional<Mark> parseShort(String value) {
        var fields = value.split(",");
        if (fields.length != 3) return Optional.empty();
        try {
            return Optional.of(new Mark(Integer.parseInt(fields[0].strip()), Integer.parseInt(fields[1].strip()),
                    Integer.parseInt(fields[2].strip())));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    /** Reads {@code [x, y, z, x, y, z, ...]}; a trailing partial triple is ignored. */
    public static List<Mark> fromFlat(List<Integer> values) {
        var marks = new ArrayList<Mark>();
        for (int i = 0; i + 2 < values.size(); i += 3) marks.add(new Mark(values.get(i), values.get(i + 1), values.get(i + 2)));
        return marks;
    }

    /** The middle of the marks' horizontal bounding box, or empty when there are none. */
    public static Optional<Mark> center(List<Mark> marks) {
        if (marks.isEmpty()) return Optional.empty();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (var mark : marks) {
            minX = Math.min(minX, mark.x());
            maxX = Math.max(maxX, mark.x());
            minY = Math.min(minY, mark.y());
            maxY = Math.max(maxY, mark.y());
            minZ = Math.min(minZ, mark.z());
            maxZ = Math.max(maxZ, mark.z());
        }
        return Optional.of(new Mark(Math.floorDiv(minX + maxX, 2), Math.floorDiv(minY + maxY, 2), Math.floorDiv(minZ + maxZ, 2)));
    }

    /** The map radius, in blocks, that shows every mark around the centre, clamped to {@code [min, max]}. */
    public static int radius(Mark center, List<Mark> marks, int min, int max) {
        int radius = min;
        for (var mark : marks) {
            radius = Math.max(radius, Math.max(Math.abs(mark.x() - center.x()), Math.abs(mark.z() - center.z())) + 2);
        }
        return Math.min(radius, max);
    }

    /** Heights a map tile looks through: from the highest mark down to {@code depth} blocks below the lowest. */
    public record Slice(int top, int bottom) {
        public boolean contains(int y) {
            return y <= top && y >= bottom;
        }
    }

    public static Optional<Slice> slice(List<Mark> marks, int depth) {
        if (marks.isEmpty()) return Optional.empty();
        int top = Integer.MIN_VALUE;
        int bottom = Integer.MAX_VALUE;
        for (var mark : marks) {
            top = Math.max(top, mark.y());
            bottom = Math.min(bottom, mark.y());
        }
        return Optional.of(new Slice(top, bottom - depth));
    }
}
