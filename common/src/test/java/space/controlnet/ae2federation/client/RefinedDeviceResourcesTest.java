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
        for (var name : new String[]{"beam_converter", "creative_beam_converter", "quantum_cube"}) {
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

    @Test void creativeAtlasIsPurpleAndKeepsTheOrdinaryAtlasLayout() throws Exception {
        var root = "/assets/ae2federation/textures/block/refined_devices/";
        try (var a=getClass().getResourceAsStream(root+"surface.png");
                var b=getClass().getResourceAsStream(root+"creative_surface.png")) {
            assertNotNull(b, "creative multiblocks need their own atlas");
            var ordinary=javax.imageio.ImageIO.read(a);
            var creative=javax.imageio.ImageIO.read(b);
            assertEquals(ordinary.getWidth(),creative.getWidth());
            assertEquals(ordinary.getHeight(),creative.getHeight());
            int changed=0;
            for (int y=0;y<ordinary.getHeight();y++) for (int x=0;x<ordinary.getWidth();x++) {
                int original=ordinary.getRGB(x,y), purple=creative.getRGB(x,y);
                assertEquals(original>>>24,purple>>>24,"atlas alpha/layout must be preserved");
                if (original!=purple && (purple>>>24)!=0) {
                    changed++;
                    assertTrue((purple&255)>((purple>>8)&255),"purple blue > green");
                    assertTrue(((purple>>16)&255)>((purple>>8)&255),"purple red > green");
                }
            }
            assertTrue(changed>1000,"the creative palette must be visibly distinct");
        }
    }
}
