package space.controlnet.ae2federation.compat.data_energistics;

import net.neoforged.fml.ModList;

/** Data Energistics nests GuideME's sidebar the same way the guide does; with it loaded, the guide leaves it the rows. */
public final class DataEnergisticsGuide {
    private static final String MOD_ID = "data_energistics";

    private DataEnergisticsGuide() {
    }

    /** Whether Data Energistics is loaded and nests the sidebar. */
    public static boolean nestsSidebar() {
        var mods = ModList.get();
        return mods != null && mods.isLoaded(MOD_ID);
    }
}
