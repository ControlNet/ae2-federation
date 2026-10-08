package space.controlnet.ae2federation.client.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

class DevicePlaceTest {
    @Test
    void aDeviceInThePlayersDimensionShowsItsCoordinatesAlone() {
        assertEquals("203, 70, 205",
                DevicePlace.of("203, 70, 205", "minecraft:overworld", "minecraft:overworld").getString());
    }

    @Test
    void aDeviceInAnotherDimensionNamesItAfterItsCoordinates() {
        var place = DevicePlace.of("203, 70, 205", "minecraft:the_nether", "minecraft:overworld");
        assertEquals("203, 70, 205 · ", place.getContents() instanceof net.minecraft.network.chat.contents.PlainTextContents
                plain ? plain.text() : "");
        var name = (TranslatableContents) place.getSiblings().getFirst().getContents();
        assertEquals("ae2federation.ui.topology.dimension.the_nether", name.getKey());
        assertEquals("the_nether", name.getFallback());
    }

    @Test
    void anUnknownDimensionIsTakenAsThePlayers() {
        assertEquals("1, 2, 3", DevicePlace.of("1, 2, 3", "", "minecraft:overworld").getString());
    }
}
