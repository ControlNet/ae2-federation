package space.controlnet.ae2federation.router;

import net.minecraft.core.Direction;

/**
 * Which of the artist's connected core textures a core face shows, and turned how far. An edge of the face opens
 * where the side past that edge joins another Federation Cable at full width, so the frame's rails run on into the
 * cable's arm; a Router, Provider or Endpoint arm is narrower and leaves the edge closed.
 *
 * <p>Edges are in texture space as AE2's {@code CubeBuilder} lays a face's standard UVs: top, right, bottom, left.
 */
public final class CableCoreFaces {
    public static final int TOP = 1;
    public static final int RIGHT = 2;
    public static final int BOTTOM = 4;
    public static final int LEFT = 8;

    /** Each texture with the edges it opens unturned. */
    public enum Variant {
        CLOSED("core", 0),
        ONE("core_connected_1", TOP),
        ADJACENT("core_connected_2", TOP | RIGHT),
        OPPOSITE("core_connected_2_straight", TOP | BOTTOM),
        THREE("core_connected_3", TOP | RIGHT | LEFT),
        ALL("core_connected_4", TOP | RIGHT | BOTTOM | LEFT);

        private final String texture;
        private final int edges;

        Variant(String texture, int edges) {
            this.texture = texture;
            this.edges = edges;
        }

        /** The texture's name under {@code part/cable/dense/}. */
        public String texture() {
            return texture;
        }

        public int edges() {
            return edges;
        }
    }

    /** A texture turned clockwise by {@code quarterTurns} quarter turns. */
    public record Face(Variant variant, int quarterTurns) {
    }

    private CableCoreFaces() {
    }

    /** The texture for the core's {@code face} under {@code connections}, as {@link CableVisualConnections} encodes. */
    public static Face face(int connections, Direction face) {
        var sides = textureEdges(face);
        int edges = 0;
        for (int edge = 0; edge < 4; edge++) {
            if (CableVisualConnections.kind(connections, sides[edge]) == CableVisualConnections.CABLE) edges |= 1 << edge;
        }
        return forEdges(edges);
    }

    /** The texture and turn whose open edges are {@code edges}. */
    public static Face forEdges(int edges) {
        for (var variant : Variant.values()) {
            for (int turns = 0; turns < 4; turns++) {
                if (turned(variant.edges, turns) == edges) return new Face(variant, turns);
            }
        }
        throw new IllegalArgumentException("Edge set out of range: " + edges);
    }

    /** {@code edges} after {@code quarterTurns} clockwise quarter turns: top to right, right to bottom, and so on. */
    public static int turned(int edges, int quarterTurns) {
        for (int turn = 0; turn < quarterTurns; turn++) edges = (edges << 1 | edges >> 3) & 15;
        return edges;
    }

    /** The world sides past a face's top, right, bottom and left texture edges. */
    private static Direction[] textureEdges(Direction face) {
        return switch (face) {
            case SOUTH -> new Direction[] {Direction.UP, Direction.EAST, Direction.DOWN, Direction.WEST};
            case NORTH -> new Direction[] {Direction.UP, Direction.WEST, Direction.DOWN, Direction.EAST};
            case WEST -> new Direction[] {Direction.UP, Direction.SOUTH, Direction.DOWN, Direction.NORTH};
            case EAST -> new Direction[] {Direction.UP, Direction.NORTH, Direction.DOWN, Direction.SOUTH};
            case UP, DOWN -> new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        };
    }
}
