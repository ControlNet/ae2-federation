package space.controlnet.ae2federation.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.client.DeviceStructureGeometry.Device;
import space.controlnet.ae2federation.client.DeviceStructureGeometry.Quad;
import space.controlnet.ae2federation.client.DeviceStructureGeometry.Side;

/**
 * The Minecraft geometry must equal the accepted offline prototype for every connection set of both artist devices,
 * each composed from its connected-face tiles. The fixture comes from {@code tools/multiblock/export_runtime_parity.py}; on a mismatch, dump the prototype's lines
 * with its {@code --dump} option and compare them with the lines this test prints.
 */
final class DeviceStructureGeometryTest {
    private static JsonObject json(String path) throws Exception {
        try (var stream = DeviceStructureGeometryTest.class.getClassLoader().getResourceAsStream(path)) {
            assertTrue(stream != null, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static DeviceStructureGeometry geometry(Device device) throws Exception {
        var tiles = new HashMap<String, int[]>();
        for (var name : DeviceStructureGeometry.TILES) {
            var path = "assets/ae2federation/textures/block/" + device.id() + "/" + device.id() + "_" + name + ".png";
            try (var stream = DeviceStructureGeometryTest.class.getClassLoader().getResourceAsStream(path)) {
                assertTrue(stream != null, path);
                var image = ImageIO.read(stream);
                tiles.put(name, image.getRGB(0, 0, 16, 16, null, 0, 16));
            }
        }
        return new DeviceStructureGeometry(device, tiles);
    }

    static Set<Side> sides(int mask) {
        var sides = EnumSet.noneOf(Side.class);
        for (var side : Side.values()) {
            if ((mask >> side.ordinal() & 1) != 0) sides.add(side);
        }
        return sides;
    }

    private static String fixed(double value) {
        return Long.toString((long) Math.floor(value * 16 * 1000 + 0.5));
    }

    private static List<String> canonical(List<Quad> quads) {
        var lines = new ArrayList<String>();
        for (var quad : quads) {
            var line = new StringBuilder(quad.kind()).append(' ').append(quad.texture());
            for (var p : quad.positions()) for (var c : p) line.append(' ').append(fixed(c));
            for (var t : quad.uv()) for (var c : t) line.append(' ').append(fixed(c));
            lines.add(line.toString());
        }
        Collections.sort(lines);
        return lines;
    }

    @Test
    void everyConnectionSetMatchesTheAcceptedPrototype() throws Exception {
        var fixture = json("multiblock/device-structure-parity.json");
        assertEquals("tiles", fixture.getAsJsonObject("styles").get("router_style").getAsString());
        assertEquals("tiles", fixture.getAsJsonObject("styles").get("switch_style").getAsString());
        var order = fixture.getAsJsonArray("sideOrder");
        for (var side : Side.values()) {
            assertEquals(side.name().toLowerCase(java.util.Locale.ROOT), order.get(side.ordinal()).getAsString());
        }
        for (var device : List.of(Device.ROUTER, Device.SWITCH)) {
            var geometry = geometry(device);
            var expected = fixture.getAsJsonObject("devices").getAsJsonArray(device.name().toLowerCase(java.util.Locale.ROOT));
            assertEquals(64, expected.size());
            for (var entry : expected) {
                var mask = entry.getAsJsonObject().get("mask").getAsInt();
                var lines = canonical(geometry.build(sides(mask)));
                var digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(String.join("\n", lines).getBytes(StandardCharsets.UTF_8)));
                var message = device + " mask " + mask + " (" + sides(mask) + ")";
                if (!digest.equals(entry.getAsJsonObject().get("sha256").getAsString())) {
                    System.out.println("PARITY_MISMATCH " + message + "\n" + String.join("\n", lines));
                }
                assertEquals(entry.getAsJsonObject().get("quads").getAsInt(), lines.size(), message);
                assertEquals(entry.getAsJsonObject().get("sha256").getAsString(), digest, message);
            }
        }
    }

    /** Minecraft draws only a quad's front, so every quad must be a flat, non-degenerate rectangle. */
    @Test
    void everyQuadIsAnAxisAlignedRectangleWithAnArea() throws Exception {
        for (var device : List.of(Device.ROUTER, Device.SWITCH)) {
            var geometry = geometry(device);
            for (int mask = 0; mask < 64; mask++) {
                for (var quad : geometry.build(sides(mask))) {
                    var p = quad.positions();
                    var normal = DeviceStructureGeometry.normal(p);
                    var length = Math.sqrt(normal[0] * normal[0] + normal[1] * normal[1] + normal[2] * normal[2]);
                    assertTrue(length > 1e-9, device + " mask " + mask + " " + quad.kind() + " is degenerate");
                    int axes = 0;
                    for (var c : normal) if (Math.abs(c) > 1e-9) axes++;
                    assertEquals(1, axes, device + " mask " + mask + " " + quad.kind() + " is not axis-aligned");
                    for (var corner : p) {
                        for (var c : corner) {
                            assertTrue(c >= -1e-9 && c <= 1 + 1e-9, device + " mask " + mask + " leaves its block");
                        }
                    }
                }
            }
        }
    }

    /** A member surrounded by its body on all six sides is hidden inside it and draws nothing. */
    @Test
    void anInteriorMemberHasNoQuads() throws Exception {
        for (var device : List.of(Device.ROUTER, Device.SWITCH)) {
            assertEquals(List.of(), geometry(device).build(sides(63)));
        }
    }
}
