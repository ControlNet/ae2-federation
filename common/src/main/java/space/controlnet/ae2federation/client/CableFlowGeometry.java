package space.controlnet.ae2federation.client;

import java.util.ArrayList;
import java.util.List;

/** Cached interior ribbons, in block coordinates. No world or renderer state. */
final class CableFlowGeometry {
    enum Layer { FLOW, BASE, BEND, HUB }
    record Vertex(float x, float y, float z, float along, float across, float alpha) {}
    record Ribbon(int axis, Layer layer, List<Vertex> vertices) {}

    private static final List<List<Ribbon>> MASKS = buildMasks();

    private CableFlowGeometry() {}

    static List<Ribbon> ribbons(int mask) {
        return MASKS.get(mask);
    }

    /** Four-block repeat with bounded coordinates, including at negative world positions. */
    static float phase(int coordinate, long gameTime, float partialTick) {
        return Math.floorMod(coordinate, 4) * 0.25f - ((gameTime % 4096) + partialTick) / 64f;
    }

    private static List<List<Ribbon>> buildMasks() {
        var masks = new ArrayList<List<Ribbon>>(64);
        for (int mask = 0; mask < 64; mask++) {
            var ribbons = new ArrayList<Ribbon>(6);
            int axes = 0;
            for (int axis = 0; axis < 3; axis++) {
                if (((mask >> (axis * 2)) & 3) != 0) axes++;
            }
            if (axes > 1) {
                if (Integer.bitCount(mask) == 2) buildElbow(ribbons, mask);
                else buildJunction(ribbons, mask);
                masks.add(List.copyOf(ribbons));
                continue;
            }
            for (int axis = 0; axis < 3; axis++) {
                int pair = (mask >> (axis * 2)) & 3;
                if (pair == 0 && mask != 0) continue;
                float from = (pair & 2) != 0 ? 0 : 0.375f;
                float to = (pair & 1) != 0 ? 1 : 0.625f;
                for (int cross = 1; cross <= 2; cross++) {
                    int widthAxis = (axis + cross) % 3;
                    ribbons.add(new Ribbon(axis, Layer.FLOW, List.of(
                            vertex(axis, widthAxis, from, 0.375f, 0, 1),
                            vertex(axis, widthAxis, to, 0.375f, 0, 1),
                            vertex(axis, widthAxis, to, 0.625f, 1, 1),
                            vertex(axis, widthAxis, from, 0.625f, 1, 1))));
                }
            }
            masks.add(List.copyOf(ribbons));
        }
        return List.copyOf(masks);
    }

    private static void buildJunction(List<Ribbon> ribbons, int mask) {
        for (int bit = 0; bit < 6; bit++) {
            if ((mask & (1 << bit)) != 0) addArm(ribbons, bit, 0.125f, 0.6f);
        }
        // One shared patch per plane replaces overlapping strips from each axis.
        for (int normal = 0; normal < 3; normal++) {
            int axis = (normal + 1) % 3;
            int widthAxis = (normal + 2) % 3;
            var corners = new ArrayList<Vertex>(4);
            for (int corner = 0; corner < 4; corner++) {
                float u = corner == 1 || corner == 2 ? 1 : 0;
                float v = corner >= 2 ? 1 : 0;
                var point = vertex(axis, widthAxis, 0.375f + u * 0.25f, 0.375f + v * 0.25f, v, 1);
                corners.add(new Vertex(point.x(), point.y(), point.z(), u, v, 0.6f));
            }
            ribbons.add(new Ribbon(normal, Layer.HUB, List.copyOf(corners)));
        }
    }

    private static void buildElbow(List<Ribbon> ribbons, int mask) {
        int first = Integer.numberOfTrailingZeros(mask);
        int second = Integer.numberOfTrailingZeros(mask & ~(1 << first));
        float radius = 0.1875f;
        addArm(ribbons, first, radius, 1);
        addArm(ribbons, second, radius, 1);
        int firstAxis = first / 2, secondAxis = second / 2;
        int thirdAxis = 3 - firstAxis - secondAxis;
        int firstSign = (first & 1) == 0 ? 1 : -1;
        int secondSign = (second & 1) == 0 ? 1 : -1;
        // Four facets turn the interior while staying inside the square enclosure.
        for (int step = 0; step < 4; step++) {
            for (int plane = 0; plane < 2; plane++) {
                var corners = new ArrayList<Vertex>(4);
                for (int corner = 0; corner < 4; corner++) {
                    int sample = step + (corner == 1 || corner == 2 ? 1 : 0);
                    double angle = sample * Math.PI / 8;
                    float sin = (float) Math.sin(angle), cos = (float) Math.cos(angle);
                    float across = corner >= 2 ? 1 : 0;
                    float width = (across - 0.5f) * 0.25f;
                    float[] point = {0.5f, 0.5f, 0.5f};
                    point[firstAxis] += firstSign * (radius * (1 - sin) + (plane == 0 ? width * sin : 0));
                    point[secondAxis] += secondSign * (radius * (1 - cos) + (plane == 0 ? width * cos : 0));
                    if (plane == 1) point[thirdAxis] += width;
                    corners.add(new Vertex(point[0], point[1], point[2], 0, across, 1));
                }
                ribbons.add(new Ribbon(firstAxis, Layer.BEND, List.copyOf(corners)));
            }
        }
    }

    private static void addArm(List<Ribbon> ribbons, int bit, float radius, float centerAlpha) {
        int axis = bit / 2;
        float inner = 0.5f + ((bit & 1) == 0 ? radius : -radius);
        float outer = (bit & 1) == 0 ? 1 : 0;
        for (int cross = 1; cross <= 2; cross++) {
            int widthAxis = (axis + cross) % 3;
            for (var layer : List.of(Layer.BASE, Layer.FLOW)) {
                float innerAlpha = layer == Layer.BASE ? centerAlpha : 0;
                float outerAlpha = layer == Layer.BASE ? 0 : 1;
                ribbons.add(new Ribbon(axis, layer, List.of(
                        vertex(axis, widthAxis, inner, 0.375f, 0, innerAlpha),
                        vertex(axis, widthAxis, outer, 0.375f, 0, outerAlpha),
                        vertex(axis, widthAxis, outer, 0.625f, 1, outerAlpha),
                        vertex(axis, widthAxis, inner, 0.625f, 1, innerAlpha))));
            }
        }
    }

    private static Vertex vertex(int axis, int widthAxis, float along, float width, float across, float alpha) {
        float x = axis == 0 ? along : widthAxis == 0 ? width : 0.5f;
        float y = axis == 1 ? along : widthAxis == 1 ? width : 0.5f;
        float z = axis == 2 ? along : widthAxis == 2 ? width : 0.5f;
        return new Vertex(x, y, z, along * 0.25f, across, alpha);
    }
}
