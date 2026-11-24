package space.controlnet.ae2federation.compat.neoecoae.computation;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

/**
 * The one entry into this package that refers to no Neo ECO class: registers the Federation computation system when
 * Neo ECO AE Extension 21.2.15 or later is loaded, and its N1 controller on Neo ECO Prototype's C1 structure when Neo
 * ECO Prototype 1.4.6 or later is loaded too. These are the versions the package is compiled against: the controller
 * screen calls Neo ECO's public GUI API directly, and 21.2.15 changed it (MultiblockBuilderUI.Config gained the
 * multiblock definition), so an older Neo ECO would crash the server when the controller is opened (owner, 2026-11-24:
 * drop older versions rather than call both APIs). A pack with an older version still loads, without these blocks.
 */
public final class NeoEcoSupport {
    public static final String MOD_ID = "neoecoae";
    public static final String PROTOTYPE_MOD_ID = "neoecoprototype";
    /** The versions {@code addonApi} compiles against; the guide's example packs require the same. */
    public static final String MINIMUM = "21.2.15";
    public static final String PROTOTYPE_MINIMUM = "1.4.6";

    private NeoEcoSupport() {
    }

    public static boolean available() {
        return loaded(MOD_ID, MINIMUM);
    }

    /** Whether N1 registers: on Neo ECO Prototype's C1, beside a Neo ECO this package supports. */
    public static boolean prototypeAvailable() {
        return available() && loaded(PROTOTYPE_MOD_ID, PROTOTYPE_MINIMUM);
    }

    /** Whether {@code version} is {@code minimum} or later, as NeoForge compares mod versions. */
    static boolean atLeast(String version, String minimum) {
        return new DefaultArtifactVersion(version).compareTo(new DefaultArtifactVersion(minimum)) >= 0;
    }

    private static boolean loaded(String modId, String minimum) {
        return ModList.get().getModContainerById(modId)
                .map(container -> atLeast(container.getModInfo().getVersion().toString(), minimum))
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
