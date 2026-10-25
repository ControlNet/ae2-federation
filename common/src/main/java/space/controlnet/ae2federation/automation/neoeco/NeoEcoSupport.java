package space.controlnet.ae2federation.automation.neoeco;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

/**
 * The one entry into this package that refers to no Neo ECO class: registers the Federation computation system when
 * Neo ECO AE Extension 21.2.1 or later is loaded, and its N1 controller on Neo ECO Prototype's C1 structure when Neo
 * ECO Prototype 1.4.0 or later is loaded too. Earlier versions lack the calculator factory or the interface-face hook
 * they build on, and a pack with one still loads, without them.
 */
public final class NeoEcoSupport {
    public static final String MOD_ID = "neoecoae";
    public static final String PROTOTYPE_MOD_ID = "neoecoprototype";
    private static final DefaultArtifactVersion MINIMUM = new DefaultArtifactVersion("21.2.1");
    private static final DefaultArtifactVersion PROTOTYPE_MINIMUM = new DefaultArtifactVersion("1.4.0");

    private NeoEcoSupport() {
    }

    public static boolean available() {
        return loaded(MOD_ID, MINIMUM);
    }

    /** Whether N1 registers: Neo ECO Prototype's C1 has the interface-face hook N1 builds on since 1.4.0. */
    public static boolean prototypeAvailable() {
        return available() && loaded(PROTOTYPE_MOD_ID, PROTOTYPE_MINIMUM);
    }

    private static boolean loaded(String modId, DefaultArtifactVersion minimum) {
        return ModList.get().getModContainerById(modId)
                .map(container -> new DefaultArtifactVersion(container.getModInfo().getVersion().toString())
                        .compareTo(minimum) >= 0)
                .orElse(false);
    }

    public static void register(IEventBus modBus) {
        if (available()) {
            NeoEcoComputing.register(modBus);
        }
        if (prototypeAvailable()) {
            PrototypeComputing.register(modBus);
        }
    }
}
