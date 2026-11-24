package space.controlnet.ae2federation.client;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Guards the Blender export boundary: studio props must never enter the shipped device mesh. */
final class RefinedDeviceResourcesTest {
    @Test void coreContainsOnlyBoundedFiniteDeviceGeometryAndAtlasUvs() throws Exception {
        for (var name : new String[]{"core-outer","core-inner","core-centre"}) {
            try (var input = getClass().getResourceAsStream("/assets/ae2federation/device_meshes/"+name+".json")) {
                assertNotNull(input);
                var quads = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonArray("quads");
                assertTrue(quads.size() > 100);
                for (var quad : quads) {
                    assertEquals(4, quad.getAsJsonArray().size());
                    for (var vertex : quad.getAsJsonArray()) {
                        var v = vertex.getAsJsonArray();
                        assertEquals(5, v.size());
                        for (int a=0; a<5; a++) {
                            double value = v.get(a).getAsDouble();
                            assertTrue(Double.isFinite(value));
                            assertTrue(a<3 ? Math.abs(value)<=1.01 : value>=0 && value<=1,
                                    name+" component "+a+" = "+value);
                        }
                    }
                }
            }
        }
    }
    @Test void standaloneBlocksHaveNativeCubeFacesAndOpaqueDedicatedTextures() throws Exception {
        for (var name : new String[]{"quantum_cube"}) {
            try (var input = getClass().getResourceAsStream("/assets/ae2federation/models/block/"+name+".json")) {
                assertNotNull(input);
                var model = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
                assertEquals("minecraft:block/cube", model.get("parent").getAsString(), name+" must render without a custom item wrapper");
                var textures = model.getAsJsonObject("textures");
                for (var face : new String[]{"north", "south", "east", "west", "up", "down", "particle"}) {
                    var id = textures.get(face).getAsString();
                    assertTrue(id.startsWith("ae2federation:block/"+name+"/"), name+" / "+face);
                    try (var png = getClass().getResourceAsStream("/assets/ae2federation/textures/"+id.split(":")[1]+".png")) {
                        assertNotNull(png, id);
                        var image = javax.imageio.ImageIO.read(png);
                        assertEquals(16, image.getWidth());
                        assertEquals(16, image.getHeight());
                        for (int y=0;y<16;y++) for (int x=0;x<16;x++) assertEquals(255,image.getRGB(x,y)>>>24);
                    }
                }
            }
        }
    }

    /**
     * The converter's blockstate picks exactly one model for every state: the artist's converter when unformed and as a
     * 1x1x2 rear member, the artist's head as the front member, a slice of the artist's 2x2 model for a member with one
     * connection on each axis across its facing and at least one along it, and the converter (only its particle) for
     * any other member, whose look is the 3x3x6 Blender mesh.
     */
    @Test void converterStatesPickTheArtistsModels() throws Exception {
        com.google.gson.JsonArray parts;
        try (var input = getClass().getResourceAsStream("/assets/ae2federation/blockstates/beam_converter.json")) {
            assertNotNull(input);
            parts = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonArray("multipart");
        }
        var sides = new String[]{"down", "up", "north", "south", "west", "east"};
        var opposite = java.util.Map.of("down", "up", "up", "down", "north", "south", "south", "north", "west", "east",
                "east", "west");
        int heads = 0;
        var slices = new java.util.TreeMap<String, Integer>();
        for (var facing : sides) for (var formed : new String[]{"false", "true"}) for (int bits = 0; bits < 64; bits++) {
            var state = new java.util.HashMap<String, String>();
            state.put("facing", facing);
            state.put("formed", formed);
            for (int i = 0; i < 6; i++) state.put(sides[i], (bits >> i & 1) == 1 ? "true" : "false");
            var selected = new java.util.ArrayList<String>();
            for (var part : parts) {
                if (matches(part.getAsJsonObject().get("when"), state)) {
                    selected.add(part.getAsJsonObject().getAsJsonObject("apply").get("model").getAsString());
                }
            }
            assertEquals(1, selected.size(), state.toString());
            boolean head = formed.equals("true") && bits == 1 << java.util.List.of(sides).indexOf(opposite.get(facing));
            if (head) heads++;
            var model = selected.get(0);
            if (model.startsWith("ae2federation:block/beam_converter_large_")) {
                slices.merge(model.substring(model.lastIndexOf('_') + 1), 1, Integer::sum);
                continue;
            }
            assertEquals(head ? "ae2federation:block/beam_converter_head" : "ae2federation:block/beam_converter",
                    model, state.toString());
        }
        assertEquals(6, heads);
        // Twelve slices (two by two across, three layers), each once per facing.
        assertEquals(12, slices.size(), slices.toString());
        slices.values().forEach(count -> assertEquals(6, count, slices.toString()));
        // Facing south, the model's +x is east, +y up and its front south: the rear lower-west member is slice 000, the
        // front upper-east one 112.
        assertEquals(java.util.List.of("ae2federation:block/beam_converter_large_000"), models(parts, "south",
                "east", "up", "south"));
        assertEquals(java.util.List.of("ae2federation:block/beam_converter_large_112"), models(parts, "south",
                "west", "down", "north"));
        assertEquals(java.util.List.of("ae2federation:block/beam_converter_large_011"), models(parts, "south",
                "east", "down", "north", "south"));
    }

    /** The models a formed converter facing {@code facing} gets with exactly the {@code connected} sides joined. */
    private static java.util.List<String> models(com.google.gson.JsonArray parts, String facing, String... connected) {
        var state = new java.util.HashMap<String, String>();
        state.put("facing", facing);
        state.put("formed", "true");
        for (var side : new String[]{"down", "up", "north", "south", "west", "east"}) state.put(side, "false");
        for (var side : connected) state.put(side, "true");
        var selected = new java.util.ArrayList<String>();
        for (var part : parts) {
            if (matches(part.getAsJsonObject().get("when"), state)) {
                selected.add(part.getAsJsonObject().getAsJsonObject("apply").get("model").getAsString());
            }
        }
        return selected;
    }

    private static boolean matches(com.google.gson.JsonElement condition, java.util.Map<String, String> state) {
        var object = condition.getAsJsonObject();
        if (object.has("AND")) {
            for (var child : object.getAsJsonArray("AND")) if (!matches(child, state)) return false;
            return true;
        }
        if (object.has("OR")) {
            for (var child : object.getAsJsonArray("OR")) if (matches(child, state)) return true;
            return false;
        }
        for (var entry : object.entrySet()) {
            if (!state.get(entry.getKey()).equals(entry.getValue().getAsString())) return false;
        }
        return true;
    }
}
