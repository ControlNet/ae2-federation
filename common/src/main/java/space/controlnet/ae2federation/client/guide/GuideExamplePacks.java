package space.controlnet.ae2federation.client.guide;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * Guide examples that need other mods, each in a built-in resource pack under {@code resourcepacks/<id>}. A pack is
 * active only while every mod it requires is loaded, so its pages, their links and their scenes are absent from the
 * guide otherwise: GuideME has no filter of its own for that.
 *
 * <p>Packs in one group are variants of the same example pages, each built with another mod's machine; they are listed
 * in order of preference, and only the first one whose mods are all loaded is active.
 */
public final class GuideExamplePacks {
    /** The Induction Card example: FE from the Provider's network powers a machine at its Endpoint. */
    private static final String INDUCTION = "induction";

    public static final List<Pack> ALL = List.of(new Pack("guide_mekanism", List.of("mekanism")),
            new Pack("guide_mekanism_appmek", List.of("mekanism", "appmek")),
            new Pack("guide_appflux_mekanism", List.of("appflux", "mekanism")),
            new Pack("guide_induction_mekanism", List.of("appflux", "mekanism"), INDUCTION),
            new Pack("guide_induction_createaddition", List.of("appflux", "createaddition", "create"), INDUCTION),
            new Pack("guide_induction_enderio", List.of("appflux", "enderio"), INDUCTION),
            new Pack("guide_advanced_ae", List.of("advanced_ae")),
            new Pack("guide_create", List.of("create")),
            new Pack("guide_extendedae", List.of("extendedae")),
            new Pack("guide_extendedae_plus", List.of("extendedae_plus", "extendedae")),
            new Pack("guide_ae2lt", List.of("ae2lt")),
            new Pack("guide_data_energistics", List.of("data_energistics")),
            new Pack("guide_neoecoae", List.of("neoecoae")),
            new Pack("guide_omnisequence", List.of("molecularmanipulator")));

    private GuideExamplePacks() {
    }

    /** The packs whose required mods are all loaded, keeping only the first such pack of each group. */
    public static List<Pack> active(Predicate<String> loaded) {
        return active(ALL, loaded);
    }

    static List<Pack> active(List<Pack> packs, Predicate<String> loaded) {
        var groups = new HashSet<String>();
        var active = new ArrayList<Pack>();
        for (var pack : packs) {
            if (pack.requiredMods().stream().allMatch(loaded) && (pack.group().isEmpty() || groups.add(pack.group()))) {
                active.add(pack);
            }
        }
        return List.copyOf(active);
    }

    /** {@code group} is empty for a pack that is not a variant of another. */
    public record Pack(String id, List<String> requiredMods, String group) {
        public Pack(String id, List<String> requiredMods) {
            this(id, requiredMods, "");
        }

        public String path() {
            return "resourcepacks/" + id;
        }

        public String nameKey() {
            return "ae2federation.pack." + id;
        }
    }
}
