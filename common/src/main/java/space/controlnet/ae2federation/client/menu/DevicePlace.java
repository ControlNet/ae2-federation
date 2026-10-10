package space.controlnet.ae2federation.client.menu;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Where a device is, as its cards show it: its coordinates, and the name of its dimension when that is not the
 * player's, so two devices at the same coordinates in different dimensions read differently.
 */
public final class DevicePlace {
    private DevicePlace() {
    }

    /** "203, 70, 205", or "203, 70, 205 · Nether" seen from another dimension; an unknown dimension is the player's. */
    public static MutableComponent of(String position, String dimension, String playerDimension) {
        var place = Component.literal(position);
        if (dimension.isEmpty() || dimension.equals(playerDimension)) return place;
        return Component.literal(position + " · ").append(dimensionName(dimension));
    }

    /** The dimension's name, "Nether" for {@code minecraft:the_nether}; its path when it has no name. */
    public static MutableComponent dimensionName(String dimension) {
        var path = dimension.substring(dimension.indexOf(':') + 1);
        return Component.translatableWithFallback("ae2federation.ui.topology.dimension." + path, path);
    }
}
