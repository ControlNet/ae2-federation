package space.controlnet.ae2federation.compat.jade;

import java.util.Optional;
import net.minecraft.network.chat.Component;
import space.controlnet.ae2federation.router.DeviceStructure;

/** The Jade lines on Federation blocks, from the plain values the server sends; nothing here refers to Jade. */
final class FederationJadeText {
    private static final String PREFIX = "ae2federation.jade.";
    /** The topology's error colour, which also marks a domain over capacity there. */
    private static final int OVER = 0xff6a75;
    /** The topology's colour for a running network or domain. */
    private static final int RUNNING = 0x55ff8a;

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

    /**
     * A Federation Cable's count, "4/4" against the enforced limit and red once over it, or the bare count when capacity
     * is not enforced; and whether that is its subdomain's or the Federation domain's.
     */
    static Component capacity(int units, java.util.OptionalInt limit, boolean domain) {
        var count = Component.literal(limit.isPresent() ? units + "/" + limit.getAsInt() : String.valueOf(units));
        if (limit.isPresent() && units > limit.getAsInt()) count.withStyle(style -> style.withColor(OVER));
        return Component.translatable(PREFIX + "cable_capacity." + (domain ? "domain" : "subdomain"), count);
    }

    static Component bundleTitle() {
        return Component.translatable(PREFIX + "bundle.title");
    }

    /** A bundle's cross-section and the capacity it carries: a number, unlimited, or nothing while not enforced. */
    static Component bundleShape(int narrow, int wide, java.util.OptionalInt capacity, boolean unlimited) {
        if (unlimited) return Component.translatable(PREFIX + "bundle.shape.unlimited", narrow, wide);
        return capacity.isPresent() ? Component.translatable(PREFIX + "bundle.shape", narrow, wide, capacity.getAsInt())
                : Component.translatable(PREFIX + "bundle.shape.unenforced", narrow, wide);
    }

    /** "Connected: 2 ends, 1 side", counting whole contacts with devices. */
    static Component bundleConnected(int ends, int sides) {
        if (ends == 0 && sides == 0) return Component.translatable(PREFIX + "bundle.connected.none");
        var parts = new java.util.ArrayList<Component>();
        if (ends > 0) parts.add(Component.translatable(PREFIX + (ends == 1 ? "bundle.end" : "bundle.ends"), ends));
        if (sides > 0) parts.add(Component.translatable(PREFIX + (sides == 1 ? "bundle.side" : "bundle.sides"), sides));
        var list = Component.empty();
        for (int index = 0; index < parts.size(); index++) {
            if (index > 0) list.append(Component.translatable(PREFIX + "bundle.separator"));
            list.append(parts.get(index));
        }
        return Component.translatable(PREFIX + "bundle.connected", list);
    }

    /** A device beside the bundle that covers only part of its contact, in the error colour. */
    static Component bundlePartial(int x, int y, int z, int covered, int cells) {
        return Component.translatable(PREFIX + "bundle.partial", x, y, z, covered, cells)
                .withStyle(style -> style.withColor(OVER));
    }

    /**
     * A Beam Converter's buffer and the FE it took in on the last tick, green when its body matches its domain's largest
     * cable cross-section and red when it does not. Outside a domain that needs power nothing draws on it, so there is
     * no input figure and no colour.
     */
    static Component converter(int stored, int capacity, java.util.OptionalInt input, Optional<Boolean> matches) {
        var line = input.isPresent()
                ? Component.translatable(PREFIX + "converter", stored, capacity, input.getAsInt())
                : Component.translatable(PREFIX + "converter.idle", stored, capacity);
        matches.ifPresent(match -> line.withStyle(style -> style.withColor(match ? RUNNING : OVER)));
        return line;
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
