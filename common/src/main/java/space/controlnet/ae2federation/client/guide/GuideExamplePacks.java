package space.controlnet.ae2federation.client.guide;

import java.util.List;
import java.util.function.Predicate;

/**
 * Guide examples that need other mods, each in a built-in resource pack under {@code resourcepacks/<id>}. A pack is
 * active only while every mod it requires is loaded, so its pages, their links and their scenes are absent from the
 * guide otherwise: GuideME has no filter of its own for that.
 */
public final class GuideExamplePacks {
    public static final List<Pack> ALL = List.of(new Pack("guide_mekanism", List.of("mekanism")),
            new Pack("guide_mekanism_appmek", List.of("mekanism", "appmek")),
            new Pack("guide_create", List.of("create")),
            new Pack("guide_extendedae", List.of("extendedae")),
            new Pack("guide_neoecoae", List.of("neoecoae")));

    private GuideExamplePacks() {
    }

    /** The packs whose required mods are all loaded. */
    public static List<Pack> active(Predicate<String> loaded) {
        return ALL.stream().filter(pack -> pack.requiredMods().stream().allMatch(loaded)).toList();
    }

    public record Pack(String id, List<String> requiredMods) {
        public String path() {
            return "resourcepacks/" + id;
        }

        public String nameKey() {
            return "ae2federation.pack." + id;
        }
    }
}
