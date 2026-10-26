package space.controlnet.ae2federation.client;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Immutable mesh snapshot replaced on each model/resource reload. */
public final class RefinedDeviceAssets {
    public static final ResourceLocation SURFACE=id("block/refined_devices/surface");
    public static final ResourceLocation EMISSION=id("block/refined_devices/emission");
    public static final ResourceLocation CREATIVE_SURFACE=id("block/refined_devices/creative_surface");
    public static final ResourceLocation CREATIVE_EMISSION=id("block/refined_devices/creative_emission");
    public static final ResourceLocation SURFACE_FILE=id("textures/block/refined_devices/surface.png");
    public static final ResourceLocation EMISSION_FILE=id("textures/block/refined_devices/emission.png");
    private static volatile Map<String,List<RefinedDeviceMesh.Quad>> meshes=Map.of();
    private RefinedDeviceAssets() { }
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("ae2federation",path); }
    public static List<RefinedDeviceMesh.Quad> mesh(String name) { return meshes.getOrDefault(name,List.of()); }
    public static void reload() {
        var next=new HashMap<String,List<RefinedDeviceMesh.Quad>>();
        var names=new ArrayList<>(List.of("core-outer","core-inner","core-centre"));
        for (int n=1;n<=3;n++) { names.add("laser-"+n); names.add("quantum-"+n); }
        for (var name:names) {
            try (var reader=Minecraft.getInstance().getResourceManager().openAsReader(id("device_meshes/"+name+".json"))) {
                var result=new ArrayList<RefinedDeviceMesh.Quad>();
                for (var face:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("quads")) {
                    var vertices=new ArrayList<RefinedDeviceMesh.Vertex>();
                    for (var element:face.getAsJsonArray()) {
                        var v=element.getAsJsonArray();
                        vertices.add(new RefinedDeviceMesh.Vertex(v.get(0).getAsDouble(),v.get(1).getAsDouble(),
                                v.get(2).getAsDouble(),v.get(3).getAsDouble(),v.get(4).getAsDouble()));
                    }
                    if (vertices.size()!=4) throw new IOException("Expected a quad in "+name);
                    result.add(new RefinedDeviceMesh.Quad(List.copyOf(vertices)));
                }
                next.put(name,List.copyOf(result));
            } catch (IOException exception) { throw new UncheckedIOException("Cannot load device mesh "+name,exception); }
        }
        meshes=Map.copyOf(next);
    }
}
