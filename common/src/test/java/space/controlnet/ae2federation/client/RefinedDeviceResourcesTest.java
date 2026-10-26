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
}
