package space.controlnet.ae2federation.automation.neoeco;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

/**
 * The one entry into this package that refers to no Neo ECO class: registers the Federation computation system when
 * Neo ECO AE Extension 21.2.1 or later is loaded. Earlier versions lack the calculator factory it builds on, and a pack
 * with one still loads, without the system.
 */
public final class NeoEcoSupport {
    public static final String MOD_ID = "neoecoae";
    private static final DefaultArtifactVersion MINIMUM = new DefaultArtifactVersion("21.2.1");

    private NeoEcoSupport() {
    }

    public static boolean available() {
        return ModList.get().getModContainerById(MOD_ID)
                .map(container -> new DefaultArtifactVersion(container.getModInfo().getVersion().toString())
                        .compareTo(MINIMUM) >= 0)
                .orElse(false);
    }

    public static void register(IEventBus modBus) {
        if (available()) {
            NeoEcoComputing.register(modBus);
        }
    }
}
