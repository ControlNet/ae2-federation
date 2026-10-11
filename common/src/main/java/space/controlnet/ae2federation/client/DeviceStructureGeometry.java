package space.controlnet.ae2federation.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The geometry of one member of a formed Router or Switch, composed from the artist's own model as the accepted
 * offline prototype composes it ({@code tools/multiblock/geometry.py} with {@code router_style='raised'} and
 * {@code switch_style='white-backed'}, and {@code switch_connectors.py}). The whole body shares one outer frame:
 * corners and edges stay only on the body's outside, panels run on across members, the Router's raised core tabs
 * become solid links one unit above the backing, and the Switch's backing is the original white with the original
 * recessed contacts set in real sockets between modules.
 *
 * <p>A member's geometry depends only on which of its six neighbours belong to the same body, so it is built per
 * connection set. It reads the model's elements and Blockbench groups and touches no client class, so a unit test
 * compares it with the prototype's output. Coordinates are model units (0..16) and texture pixels (0..16) until
 * {@link Quad} divides them by 16.
 */
public final class DeviceStructureGeometry {
    /** Python's side order; the order of output quads does not matter. */
    public enum Side {
        WEST(0, -1), EAST(0, 1), DOWN(1, -1), UP(1, 1), NORTH(2, -1), SOUTH(2, 1);

        final int axis;
        final int sign;

        Side(int axis, int sign) {
            this.axis = axis;
            this.sign = sign;
        }

        static Side of(int axis, int sign) {
            for (var side : values()) {
                if (side.axis == axis && side.sign == sign) return side;
            }
            throw new IllegalArgumentException(axis + "/" + sign);
        }

        static Side named(String name) {
            return valueOf(name.toUpperCase(java.util.Locale.ROOT));
        }
    }

    public enum Device { ROUTER, SWITCH }

    /** One quad: four corners in block units, counter-clockwise seen from its front, and normalized texture UVs. */
    public record Quad(double[][] positions, double[][] uv, String kind) {
    }

    private record Face(double[] uv, int rotation) {
    }

    private record Element(double[] from, double[] to, Map<Side, Face> faces) {
        Element copy() {
            return new Element(from.clone(), to.clone(), new EnumMap<>(faces));
        }
    }

    private final Device device;
    private final List<Element> elements = new ArrayList<>();
    private final Map<String, JsonObject> groups = new LinkedHashMap<>();

    /** {@code model} is the device's block model JSON, including its Blockbench {@code groups}. */
    public DeviceStructureGeometry(Device device, JsonObject model) {
        this.device = device;
        for (var element : model.getAsJsonArray("elements")) {
            var object = element.getAsJsonObject();
            var faces = new EnumMap<Side, Face>(Side.class);
            for (var face : object.getAsJsonObject("faces").entrySet()) {
                var data = face.getValue().getAsJsonObject();
                faces.put(Side.named(face.getKey()), new Face(doubles(data.getAsJsonArray("uv")),
                        data.has("rotation") ? data.get("rotation").getAsInt() : 0));
            }
            elements.add(new Element(doubles(object.getAsJsonArray("from")), doubles(object.getAsJsonArray("to")),
                    faces));
        }
        for (var group : model.getAsJsonArray("groups")) {
            if (group.isJsonObject()) {
                groups.put(group.getAsJsonObject().get("name").getAsString(), group.getAsJsonObject());
            }
        }
        for (var name : device == Device.ROUTER ? List.of("corner", "edge", "face", "face_detail")
                : List.of("corner", "edge")) {
            if (!groups.containsKey(name)) {
                throw new IllegalArgumentException("The " + device + " model has no '" + name + "' group");
            }
        }
    }

    private static double[] doubles(JsonArray array) {
        var values = new double[array.size()];
        for (int i = 0; i < values.length; i++) values[i] = array.get(i).getAsDouble();
        return values;
    }

    /** The quads of a member whose neighbours on {@code connections} belong to the same body. */
    public List<Quad> build(Set<Side> connections) {
        var raw = new ArrayList<RawQuad>();
        if (device == Device.ROUTER) {
            frame(connections, 3, raw);
            for (var side : Side.values()) {
                if (connections.contains(side)) continue;
                raw.addAll(panelQuads(side, connections, 3));
                routerDetails(side, connections, raw);
            }
        } else {
            frame(connections, 2, raw);
            var surfaces = new ArrayList<RawQuad>();
            for (var side : Side.values()) {
                if (connections.contains(side)) continue;
                var panels = panelQuads(side, connections, 2);
                var fill = switchInfill(side, connections);
                if (!fill.isEmpty()) {
                    // The original panel sinks a quarter unit so the backing layers stay inside the block.
                    for (var panel : panels) {
                        for (var p : panel.positions) p[side.axis] -= side.sign * .25;
                    }
                }
                surfaces.addAll(fill);
                surfaces.addAll(panels);
            }
            matchEdges(surfaces, connections, raw);
        }
        var quads = new ArrayList<Quad>(raw.size());
        for (var quad : raw) quads.add(quad.normalized());
        return List.copyOf(quads);
    }

    // --- shared frame ------------------------------------------------------------------------------------------

    /** A quad in model units, with texture pixels, and the face it lies on for socket cutting. */
    private record RawQuad(double[][] positions, double[][] uv, Side side, String kind) {
        Quad normalized() {
            var p = new double[4][3];
            var t = new double[4][2];
            for (int i = 0; i < 4; i++) {
                for (int a = 0; a < 3; a++) p[i][a] = positions[i][a] / 16;
                for (int a = 0; a < 2; a++) t[i][a] = uv[i][a] / 16;
            }
            return new Quad(p, t, kind);
        }
    }

    private void frame(Set<Side> connections, int rim, List<RawQuad> out) {
        for (int index : flat(groups.get("corner"))) {
            var element = elements.get(index);
            if (disjoint(boundarySides(element.from, element.to, rim), connections)) {
                out.addAll(elementQuads(element, "corner", Set.of(), null));
            }
        }
        for (var group : groups.get("edge").getAsJsonArray("children")) {
            var indices = flat(group);
            var lo = new double[] {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
            var hi = new double[] {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            for (int index : indices) {
                for (int a = 0; a < 3; a++) {
                    lo[a] = Math.min(lo[a], elements.get(index).from[a]);
                    hi[a] = Math.max(hi[a], elements.get(index).to[a]);
                }
            }
            var boundaries = boundarySides(lo, hi, rim);
            if (!disjoint(boundaries, connections)) continue;
            int axis = 0;
            while (axis < 3) {
                final int candidate = axis;
                if (boundaries.stream().noneMatch(side -> side.axis == candidate)) break;
                axis++;
            }
            for (int index : indices) {
                var element = elements.get(index);
                double start = element.from[axis];
                double end = element.to[axis];
                for (var side : connections) {
                    if (side.axis == axis && side.sign < 0 && start == rim) start = 0;
                    if (side.axis == axis && side.sign > 0 && end == 16 - rim) end = 16;
                }
                out.addAll(elementQuads(element, "edge", connections, new double[] {axis, start, end}));
            }
            if (device == Device.SWITCH && !connections.isEmpty()) {
                double start = lo[axis];
                double end = hi[axis];
                for (var side : connections) {
                    if (side.axis == axis && side.sign < 0 && start == rim) start = 0;
                    if (side.axis == axis && side.sign > 0 && end == 16 - rim) end = 16;
                }
                channelWalls(boundaries, axis, lo, hi, start, end, out);
            }
        }
    }

    /**
     * The two inner walls of the channel an edge assembly sits in. In the original Switch the body and face slabs
     * close the channel; a connected member drops them, so without these walls the notches beside the recessed
     * contact pieces would show through the block.
     */
    private void channelWalls(Set<Side> boundaries, int axis, double[] lo, double[] hi, double start, double end,
            List<RawQuad> out) {
        for (var side : boundaries) {
            int a = side.axis;
            int b = boundaries.stream().filter(other -> other != side).findFirst().orElseThrow().axis;
            double plane = side.sign < 0 ? hi[a] : lo[a];
            var wallLo = new double[3];
            var wallHi = new double[3];
            wallLo[a] = wallHi[a] = plane;
            wallLo[b] = lo[b];
            wallHi[b] = hi[b];
            wallLo[axis] = start;
            wallHi[axis] = end;
            var source = elements.stream().filter(element -> element.faces.containsKey(side)
                    && (side.sign < 0 ? element.from[a] : element.to[a]) == plane
                    && element.from[b] <= lo[b] && element.to[b] >= hi[b]
                    && element.from[axis] <= lo[axis] && element.to[axis] >= hi[axis]).findFirst().orElseThrow();
            out.add(facePatch(source, side, wallLo, wallHi, "channel-wall"));
        }
    }

    /**
     * A rectangle on one of the element's face planes, textured like that face; points beyond the element along a
     * tangent axis repeat its end pixel, as edge extensions do.
     */
    private static RawQuad facePatch(Element element, Side side, double[] lo, double[] hi, String kind) {
        var face = element.faces.get(side);
        double u = face.uv[0], v = face.uv[1], uMax = face.uv[2], vMax = face.uv[3];
        var sourceUv = new double[][] {{u, v}, {u, vMax}, {uMax, vMax}, {uMax, v}};
        int turns = face.rotation / 90;
        var rotated = new double[4][];
        for (int i = 0; i < 4; i++) rotated[i] = sourceUv[(i + turns) % 4];
        var sourcePoints = facePoints(element.from, element.to, side);
        var points = facePoints(lo, hi, side);
        var uv = new double[4][];
        for (int i = 0; i < 4; i++) {
            var coords = points[i].clone();
            for (int a = 0; a < 3; a++) {
                if (a == side.axis) continue;
                if (coords[a] < element.from[a]) {
                    coords[a] = element.from[a] + .5;
                } else if (coords[a] > element.to[a]) {
                    coords[a] = element.to[a] - .5;
                }
            }
            double s = projection(coords, sourcePoints[0], sourcePoints[3]);
            double t = projection(coords, sourcePoints[0], sourcePoints[1]);
            uv[i] = new double[2];
            for (int a = 0; a < 2; a++) {
                uv[i][a] = rotated[0][a] + s * (rotated[3][a] - rotated[0][a]) + t * (rotated[1][a] - rotated[0][a]);
            }
        }
        return new RawQuad(points, uv, side, kind);
    }

    /**
     * The quads of one source element, keeping its UVs and rotations. {@code extension} = (axis, start, end)
     * stretches it along one axis by repeating its end pixels, so its centre details keep their size; faces lying
     * on a connected side's block boundary are left out.
     */
    private List<RawQuad> elementQuads(Element element, String kind, Set<Side> connections, double[] extension) {
        var result = new ArrayList<RawQuad>();
        var originalLo = element.from;
        var originalHi = element.to;
        var lo = originalLo.clone();
        var hi = originalHi.clone();
        int axis = extension == null ? -1 : (int) extension[0];
        if (extension != null) {
            lo[axis] = extension[1];
            hi[axis] = extension[2];
        }
        for (var entry : element.faces.entrySet()) {
            var side = entry.getKey();
            var face = entry.getValue();
            double plane = side.sign < 0 ? lo[side.axis] : hi[side.axis];
            if (connections.contains(side) && plane == (side.sign < 0 ? 0 : 16)) continue;
            double u = face.uv[0], v = face.uv[1], uMax = face.uv[2], vMax = face.uv[3];
            var sourceUv = new double[][] {{u, v}, {u, vMax}, {uMax, vMax}, {uMax, v}};
            int turns = face.rotation / 90;
            var rotated = new double[4][];
            for (int i = 0; i < 4; i++) rotated[i] = sourceUv[(i + turns) % 4];
            var sourcePoints = facePoints(originalLo, originalHi, side);
            List<double[]> intervals = new ArrayList<>();
            if (extension != null && axis != side.axis) {
                var cuts = new java.util.TreeSet<Double>(List.of(lo[axis], hi[axis], originalLo[axis], originalHi[axis]));
                Double previous = null;
                for (var cut : cuts) {
                    if (previous != null) intervals.add(new double[] {previous, cut});
                    previous = cut;
                }
            } else {
                intervals.add(null);
            }
            for (var interval : intervals) {
                var patchLo = lo.clone();
                var patchHi = hi.clone();
                if (interval != null) {
                    patchLo[axis] = interval[0];
                    patchHi[axis] = interval[1];
                }
                var points = facePoints(patchLo, patchHi, side);
                var uv = new double[4][];
                for (int i = 0; i < 4; i++) {
                    var coords = points[i].clone();
                    if (interval != null) {
                        if (interval[1] <= originalLo[axis]) {
                            coords[axis] = originalLo[axis] + .5;
                        } else if (interval[0] >= originalHi[axis]) {
                            coords[axis] = originalHi[axis] - .5;
                        }
                    }
                    var origin = sourcePoints[0];
                    var vertical = sourcePoints[1];
                    var horizontal = sourcePoints[3];
                    double s = projection(coords, origin, horizontal);
                    double t = projection(coords, origin, vertical);
                    uv[i] = new double[2];
                    for (int a = 0; a < 2; a++) {
                        uv[i][a] = rotated[0][a] + s * (rotated[3][a] - rotated[0][a]) + t * (rotated[1][a] - rotated[0][a]);
                    }
                }
                result.add(new RawQuad(points, uv, side, kind));
            }
        }
        return result;
    }

    /** A quad's front normal (unnormalized), from its counter-clockwise corners. */
    public static double[] normal(double[][] p) {
        double ax = p[1][0] - p[0][0], ay = p[1][1] - p[0][1], az = p[1][2] - p[0][2];
        double bx = p[2][0] - p[0][0], by = p[2][1] - p[0][1], bz = p[2][2] - p[0][2];
        return new double[] {ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx};
    }

    private static double projection(double[] point, double[] origin, double[] toward) {
        double dot = 0;
        double length = 0;
        for (int a = 0; a < 3; a++) {
            double d = toward[a] - origin[a];
            dot += (point[a] - origin[a]) * d;
            length += d * d;
        }
        return dot / length;
    }

    /** The corners of a box's face, in Minecraft's vertex order: counter-clockwise from the top left, seen outside. */
    static double[][] facePoints(double[] lo, double[] hi, Side side) {
        double x = lo[0], y = lo[1], z = lo[2], X = hi[0], Y = hi[1], Z = hi[2];
        return switch (side) {
            case NORTH -> new double[][] {{X, Y, z}, {X, y, z}, {x, y, z}, {x, Y, z}};
            case SOUTH -> new double[][] {{x, Y, Z}, {x, y, Z}, {X, y, Z}, {X, Y, Z}};
            case WEST -> new double[][] {{x, Y, z}, {x, y, z}, {x, y, Z}, {x, Y, Z}};
            case EAST -> new double[][] {{X, Y, Z}, {X, y, Z}, {X, y, z}, {X, Y, z}};
            case UP -> new double[][] {{x, Y, z}, {x, Y, Z}, {X, Y, Z}, {X, Y, z}};
            case DOWN -> new double[][] {{x, y, Z}, {x, y, z}, {X, y, z}, {X, y, Z}};
        };
    }

    private static double[] uvRect(Side side, double[] lo, double[] hi) {
        double x = lo[0], y = lo[1], z = lo[2], X = hi[0], Y = hi[1], Z = hi[2];
        return switch (side) {
            case DOWN -> new double[] {x, 16 - Z, X, 16 - z};
            case UP -> new double[] {x, z, X, Z};
            case NORTH -> new double[] {16 - X, 16 - Y, 16 - x, 16 - y};
            case SOUTH -> new double[] {x, 16 - Y, X, 16 - y};
            case WEST -> new double[] {z, 16 - Y, Z, 16 - y};
            case EAST -> new double[] {16 - Z, 16 - Y, 16 - z, 16 - y};
        };
    }

    private static List<Integer> flat(JsonElement group) {
        if (group.isJsonPrimitive()) return List.of(group.getAsInt());
        var result = new ArrayList<Integer>();
        for (var child : group.getAsJsonObject().getAsJsonArray("children")) result.addAll(flat(child));
        return result;
    }

    private static Set<Side> boundarySides(double[] lo, double[] hi, int rim) {
        var result = EnumSet.noneOf(Side.class);
        for (var side : Side.values()) {
            if (side.sign < 0 ? hi[side.axis] <= rim : lo[side.axis] >= 16 - rim) result.add(side);
        }
        return result;
    }

    private static boolean disjoint(Set<Side> first, Set<Side> second) {
        return first.stream().noneMatch(second::contains);
    }

    /** The outward panel: its centre keeps its pixels, its borderless ends run on to connected sides. */
    private List<RawQuad> panelQuads(Side side, Set<Side> connections, int rim) {
        int[] tangent = tangents(side.axis);
        var spans = new ArrayList<List<double[]>>();
        for (int a : tangent) {
            var cuts = new ArrayList<Double>(List.of((double) rim, 16.0 - rim));
            if (connections.contains(Side.of(a, -1))) cuts.add(0, 0.0);
            if (connections.contains(Side.of(a, 1))) cuts.add(16.0);
            var pairs = new ArrayList<double[]>();
            for (int i = 0; i + 1 < cuts.size(); i++) pairs.add(new double[] {cuts.get(i), cuts.get(i + 1)});
            spans.add(pairs);
        }
        double inset = device == Device.ROUTER ? 1 : 0;
        var result = new ArrayList<RawQuad>();
        for (var first : spans.get(0)) {
            for (var second : spans.get(1)) {
                var lo = new double[] {0, 0, 0};
                var hi = new double[] {16, 16, 16};
                lo[side.axis] = hi[side.axis] = side.sign < 0 ? inset : 16 - inset;
                var pair = new double[][] {first, second};
                for (int i = 0; i < 2; i++) {
                    lo[tangent[i]] = pair[i][0];
                    hi[tangent[i]] = pair[i][1];
                }
                var points = facePoints(lo, hi, side);
                var uvLo = lo.clone();
                var uvHi = hi.clone();
                for (int i = 0; i < 2; i++) {
                    int a = tangent[i];
                    if (pair[i][1] <= rim) {
                        uvLo[a] = uvHi[a] = rim + .5;
                    } else if (pair[i][0] >= 16 - rim) {
                        uvLo[a] = uvHi[a] = 16 - rim - .5;
                    }
                }
                var r = uvRect(side, uvLo, uvHi);
                result.add(new RawQuad(points, new double[][] {{r[0], r[1]}, {r[0], r[3]}, {r[2], r[3]}, {r[2], r[1]}},
                        side, "panel"));
            }
        }
        return result;
    }

    private static int[] tangents(int axis) {
        return switch (axis) {
            case 0 -> new int[] {1, 2};
            case 1 -> new int[] {0, 2};
            default -> new int[] {0, 1};
        };
    }

    // --- Router -----------------------------------------------------------------------------------------------

    /** The artist's raised core and face details; core tabs toward a connected member become raised links. */
    private void routerDetails(Side side, Set<Side> connections, List<RawQuad> out) {
        var indices = new ArrayList<>(flat(groups.get("face")));
        indices.addAll(flat(groups.get("face_detail")));
        for (int index : indices) {
            var element = elements.get(index);
            if (!boundarySides(element.from, element.to, 1).contains(side)) continue;
            double[] extension = null;
            for (var direction : connections) {
                if (direction.axis == side.axis) continue;
                double start = element.from[direction.axis];
                double end = element.to[direction.axis];
                // The 4-wide, 1-high tab continues to the neighbour at core height, one unit above the backing.
                if (start == 3 && end == 4 && direction.sign < 0) {
                    extension = new double[] {direction.axis, 0, end};
                } else if (start == 12 && end == 13 && direction.sign > 0) {
                    extension = new double[] {direction.axis, start, 16};
                }
            }
            out.addAll(elementQuads(element, extension != null ? "raised-link" : "detail", connections, extension));
        }
    }

    // --- Switch -----------------------------------------------------------------------------------------------

    /** The face's local frame: the top-left corner and unit vectors toward its right and its bottom. */
    private static double[][] basis(Side side) {
        var points = facePoints(new double[] {0, 0, 0}, new double[] {16, 16, 16}, side);
        var origin = points[0];
        var u = new double[3];
        var v = new double[3];
        for (int a = 0; a < 3; a++) {
            u[a] = (points[3][a] - origin[a]) / 16;
            v[a] = (points[1][a] - origin[a]) / 16;
        }
        return new double[][] {origin, u, v};
    }

    private static double[] toWorld(Side side, double u, double v, double depth) {
        var basis = basis(side);
        var p = new double[3];
        for (int a = 0; a < 3; a++) p[a] = basis[0][a] + basis[1][a] * u + basis[2][a] * v;
        p[side.axis] -= side.sign * depth;
        return p;
    }

    private static Side direction(double[] vector, int sign) {
        int axis = 0;
        while (vector[axis] == 0) axis++;
        return Side.of(axis, (int) (vector[axis] * sign));
    }

    private enum Edge { LEFT, RIGHT, TOP, BOTTOM }

    /** The face's edges whose neighbour across them belongs to the body, in the face's own frame. */
    private static List<Edge> connectionsOnFace(Side side, Set<Side> connections) {
        var basis = basis(side);
        var result = new ArrayList<Edge>();
        if (connections.contains(direction(basis[1], -1))) result.add(Edge.LEFT);
        if (connections.contains(direction(basis[1], 1))) result.add(Edge.RIGHT);
        if (connections.contains(direction(basis[2], -1))) result.add(Edge.TOP);
        if (connections.contains(direction(basis[2], 1))) result.add(Edge.BOTTOM);
        return result;
    }

    /** The original white body fills the gaps around the purple module, with small grooves at four-way joins. */
    private static List<RawQuad> switchInfill(Side side, Set<Side> connections) {
        var edges = connectionsOnFace(side, connections);
        boolean left = edges.contains(Edge.LEFT), right = edges.contains(Edge.RIGHT);
        boolean top = edges.contains(Edge.TOP), bottom = edges.contains(Edge.BOTTOM);
        var result = new ArrayList<RawQuad>();
        if (!(left || right || top || bottom)) return result;
        double loU = left ? 0 : 2, hiU = right ? 16 : 14, loV = top ? 0 : 2, hiV = bottom ? 16 : 14;
        for (var rect : new double[][] {{loU, loV, hiU, 3}, {loU, 13, hiU, hiV}, {loU, 3, 3, 13}, {13, 3, hiU, 13}}) {
            // Pixel (2, 2) is the original body white, RGB 242/242/242.
            rectangle(side, rect, 2, 2, .18, "infill", result);
        }
        double[][] corners = {{0, 0}, {13, 0}, {0, 13}, {13, 13}};
        boolean[] enabled = {left && top, right && top, left && bottom, right && bottom};
        for (int i = 0; i < 4; i++) {
            if (!enabled[i]) continue;
            for (double offset : new double[] {.65, 1.65}) {
                double u = corners[i][0], v = corners[i][1];
                rectangle(side, new double[] {u + .5, v + offset, u + 2.5, v + offset + .4}, 1, 1, .12,
                        "infill-groove", result);
            }
        }
        return result;
    }

    private static void rectangle(Side side, double[] rect, int pixelU, int pixelV, double depth, String kind,
            List<RawQuad> out) {
        if (rect[2] <= rect[0] || rect[3] <= rect[1]) return;
        var points = new double[4][];
        double[][] corners = {{rect[0], rect[1]}, {rect[0], rect[3]}, {rect[2], rect[3]}, {rect[2], rect[1]}};
        for (int i = 0; i < 4; i++) points[i] = toWorld(side, corners[i][0], corners[i][1], depth);
        var uv = new double[4][];
        for (int i = 0; i < 4; i++) uv[i] = new double[] {pixelU + .5, pixelV + .5};
        out.add(new RawQuad(points, uv, side, kind));
    }

    private static double[] footprint(Edge edge) {
        return switch (edge) {
            case LEFT -> new double[] {0, 5, 3, 11};
            case RIGHT -> new double[] {13, 5, 16, 11};
            case TOP -> new double[] {5, 0, 11, 3};
            case BOTTOM -> new double[] {5, 13, 11, 16};
        };
    }

    /** Cuts the sockets out of the panels and backing, then sets the original contacts into them. */
    private void matchEdges(List<RawQuad> surfaces, Set<Side> connections, List<RawQuad> out) {
        var faces = new EnumMap<Side, List<Edge>>(Side.class);
        for (var quad : surfaces) {
            if (!quad.kind.equals("panel") && !quad.kind.equals("infill")) {
                out.add(quad);
                continue;
            }
            var edges = faces.computeIfAbsent(quad.side, side -> connectionsOnFace(side, connections));
            var cuts = new ArrayList<double[]>();
            for (var edge : edges) cuts.add(footprint(edge));
            out.addAll(cutSurface(quad, cuts));
        }
        for (var entry : faces.entrySet()) {
            for (var edge : entry.getValue()) out.addAll(socket(entry.getKey(), edge));
        }
    }

    private static double[] localUv(Side side, double[] point) {
        var basis = basis(side);
        double u = 0, v = 0;
        for (int a = 0; a < 3; a++) {
            double relative = point[a] - basis[0][a];
            u += relative * basis[1][a];
            v += relative * basis[2][a];
        }
        return new double[] {u, v};
    }

    private static List<double[]> subtract(double[] rect, double[] cut) {
        double u = rect[0], v = rect[1], uMax = rect[2], vMax = rect[3];
        double x = Math.max(u, cut[0]), y = Math.max(v, cut[1]);
        double xMax = Math.min(uMax, cut[2]), yMax = Math.min(vMax, cut[3]);
        if (x >= xMax || y >= yMax) return List.of(rect);
        var result = new ArrayList<double[]>();
        for (var piece : new double[][] {{u, v, uMax, y}, {u, yMax, uMax, vMax}, {u, y, x, yMax}, {xMax, y, uMax, yMax}}) {
            if (piece[0] < piece[2] && piece[1] < piece[3]) result.add(piece);
        }
        return result;
    }

    /** Removes the socket footprints from a surface quad, interpolating its positions and UVs; the white backing
     * samples the original body pixel. */
    private static List<RawQuad> cutSurface(RawQuad quad, List<double[]> cuts) {
        var first = localUv(quad.side, quad.positions[0]);
        var last = localUv(quad.side, quad.positions[2]);
        double u = first[0], v = first[1], uMax = last[0], vMax = last[1];
        List<double[]> pieces = List.of(new double[] {u, v, uMax, vMax});
        for (var cut : cuts) {
            var next = new ArrayList<double[]>();
            for (var piece : pieces) next.addAll(subtract(piece, cut));
            pieces = next;
        }
        var result = new ArrayList<RawQuad>();
        for (var piece : pieces) {
            double[][] corners = {{piece[0], piece[1]}, {piece[0], piece[3]}, {piece[2], piece[3]}, {piece[2], piece[1]}};
            var positions = new double[4][];
            var uv = new double[4][];
            for (int i = 0; i < 4; i++) {
                double s = (corners[i][0] - u) / (uMax - u);
                double t = (corners[i][1] - v) / (vMax - v);
                positions[i] = interpolate(quad.positions, s, t);
                uv[i] = quad.kind.equals("infill") ? new double[] {2.5, 2.5} : interpolate(quad.uv, s, t);
            }
            result.add(new RawQuad(positions, uv, quad.side, quad.kind));
        }
        return result;
    }

    private static double[] interpolate(double[][] corners, double s, double t) {
        var result = new double[corners[0].length];
        for (int i = 0; i < result.length; i++) {
            result[i] = corners[0][i] + s * (corners[3][i] - corners[0][i]) + t * (corners[1][i] - corners[0][i]);
        }
        return result;
    }

    private static double[] mappedPoint(Side side, Edge edge, double[] point) {
        double x = point[0], y = point[1], depth = point[2];
        return switch (edge) {
            case LEFT -> toWorld(side, y, x, depth);
            case RIGHT -> toWorld(side, 16 - y, x, depth);
            case TOP -> toWorld(side, x, y, depth);
            case BOTTOM -> toWorld(side, x, 16 - y, depth);
        };
    }

    private static double determinant(Side side, Edge edge) {
        var origin = mappedPoint(side, edge, new double[] {0, 0, 0});
        var axes = new double[3][3];
        for (int b = 0; b < 3; b++) {
            var unit = new double[3];
            unit[b] = 1;
            var mapped = mappedPoint(side, edge, unit);
            for (int i = 0; i < 3; i++) axes[b][i] = mapped[i] - origin[i];
        }
        var a = axes[0];
        var b = axes[1];
        var c = axes[2];
        return a[0] * (b[1] * c[2] - b[2] * c[1]) - a[1] * (b[0] * c[2] - b[2] * c[0])
                + a[2] * (b[0] * c[1] - b[1] * c[0]);
    }

    /** One recessed socket: a two-unit-deep floor and walls, the original contact pieces, and a neck to the module. */
    private List<RawQuad> socket(Side side, Edge edge) {
        var result = new ArrayList<RawQuad>();
        var rect = footprint(edge);
        double[][] corners = {{rect[0], rect[1]}, {rect[0], rect[3]}, {rect[2], rect[3]}, {rect[2], rect[1]}};
        var floor = new double[4][];
        for (int i = 0; i < 4; i++) floor[i] = toWorld(side, corners[i][0], corners[i][1], 2);
        result.add(new RawQuad(floor, constantUv(3.5, 1.5), side, "socket-floor"));
        for (int i = 0; i < 4; i++) {
            var a = floor[i];
            var b = floor[(i + 1) % 4];
            // The wall on the neighbour's block boundary stays open: the partner socket continues there.
            var onBoundary = false;
            for (int axis = 0; axis < 3; axis++) {
                if (axis != side.axis && a[axis] == b[axis] && (a[axis] == 0 || a[axis] == 16)) onBoundary = true;
            }
            if (onBoundary) continue;
            var raisedA = a.clone();
            var raisedB = b.clone();
            raisedA[side.axis] += side.sign * 1.82;
            raisedB[side.axis] += side.sign * 1.82;
            result.add(new RawQuad(new double[][] {a, raisedA, raisedB, b}, constantUv(1.5, 1.5), side, "socket-wall"));
        }
        // Elements 15/16/17 are the original cyan contact and its recessed side pieces, kept at their 0/1/2-unit
        // depths; faces buried in the original body but visible in the socket borrow an existing face's pixels.
        for (int index : new int[] {15, 16, 17}) {
            var element = elements.get(index).copy();
            var fallback = element.faces.getOrDefault(Side.DOWN, element.faces.get(Side.NORTH));
            for (var face : List.of(Side.EAST, Side.WEST, Side.UP, Side.DOWN)) element.faces.putIfAbsent(face, fallback);
            for (var quad : elementQuads(element, "edge-contact", Set.of(), null)) {
                // The contact's outer end meets its partner across the seam.
                if (quad.side == Side.DOWN && element.from[1] == 0) continue;
                result.add(mapped(side, edge, quad));
            }
        }
        // A one-unit recessed neck joins the unchanged purple module to the contact.
        var pixel = new Face(new double[] {10.5, 14.5, 10.5, 14.5}, 0);
        var neckFaces = new EnumMap<Side, Face>(Side.class);
        for (var face : List.of(Side.NORTH, Side.EAST, Side.WEST, Side.UP, Side.DOWN)) neckFaces.put(face, pixel);
        var neck = new Element(new double[] {5, 2, 1}, new double[] {11, 3, 2}, neckFaces);
        for (var quad : elementQuads(neck, "contact-neck", Set.of(), null)) result.add(mapped(side, edge, quad));
        return result;
    }

    private static RawQuad mapped(Side side, Edge edge, RawQuad quad) {
        var positions = new double[4][];
        var uv = new double[4][];
        for (int i = 0; i < 4; i++) {
            positions[i] = mappedPoint(side, edge, quad.positions[i]);
            uv[i] = quad.uv[i].clone();
        }
        if (determinant(side, edge) < 0) {
            // A mirroring map reverses the winding; reverse it back so the quad still faces outward.
            java.util.Collections.reverse(java.util.Arrays.asList(positions));
            java.util.Collections.reverse(java.util.Arrays.asList(uv));
        }
        return new RawQuad(positions, uv, side, quad.kind);
    }

    private static double[][] constantUv(double u, double v) {
        return new double[][] {{u, v}, {u, v}, {u, v}, {u, v}};
    }
}
