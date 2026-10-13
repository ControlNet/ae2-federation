package space.controlnet.ae2federation.compat.jade;

import java.util.Optional;
import net.minecraft.network.chat.Component;
import space.controlnet.ae2federation.router.DeviceStructure;

/** The Jade lines on Federation blocks, from the plain values the server sends; nothing here refers to Jade. */
final class FederationJadeText {
    private static final String PREFIX = "ae2federation.jade.";

    private FederationJadeText() {
    }

    /**
     * A Router or Switch body: its size when it is more than one block, else why it does not form. {@code state} is
     * {@code valid} or the lower-case name of an {@link DeviceStructure.Reason}, or {@code unloaded}.
     */
    static Optional<Component> body(String state, int sizeX, int sizeY, int sizeZ) {
        return switch (state) {
            case "valid" -> sizeX * sizeY * sizeZ == 1 ? Optional.empty()
                    : Optional.of(Component.translatable(PREFIX + "structure.size", sizeX, sizeY, sizeZ));
            case "too_large" -> Optional.of(Component.translatable(PREFIX + "structure.too_large", DeviceStructure.MAX_EDGE));
            default -> Optional.of(Component.translatable(PREFIX + "structure." + state));
        };
    }

    static Component networks(int count) {
        return Component.translatable(PREFIX + "switch_networks", count);
    }

    static Component endpoint(boolean local, boolean claimed) {
        return Component.translatable(PREFIX + "endpoint." + (local ? "local" : claimed ? "claimed" : "unclaimed"));
    }

    static Component provider(int used, int total, int mappings) {
        return Component.translatable(PREFIX + "pattern_provider", used, total, mappings);
    }
}
