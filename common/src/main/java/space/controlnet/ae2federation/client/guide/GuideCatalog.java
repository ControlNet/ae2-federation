package space.controlnet.ae2federation.client.guide;

import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import space.controlnet.ae2federation.automation.RegistryResources;
import space.controlnet.ae2federation.script.Candidate;
import space.controlnet.ae2federation.script.Catalog;
import space.controlnet.ae2federation.script.TypeRef;

/**
 * What a guide page's program is checked against: resources from the game's registries, and every interface and
 * network taken to exist, since a page names ones the reader does not have. The guide may open outside a world, so
 * the built-in registries stand in for a level's.
 */
final class GuideCatalog implements Catalog {
    private final RegistryResources resources = new RegistryResources(registries());

    private static HolderLookup.Provider registries() {
        var level = Minecraft.getInstance().level;
        return level != null ? level.registryAccess() : RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    @Override
    public List<Candidate> values(TypeRef type, String prefix) {
        return List.of();
    }

    @Override
    public Set<String> kinds(String id) {
        return resources.kinds(id);
    }

    @Override
    public boolean exists(TypeRef type, String value) {
        return true;
    }
}
