package space.controlnet.ae2federation.client.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

class ProviderReturnsTextTest {
    @Test
    void waitingReturnsAreJoinedWithTheLanguagesSeparator() {
        var joined = FederationProviderMenuHolder.joined(List.of(Component.literal("Iron Ingot × 3"),
                Component.literal("Gold Ingot × 1")));
        var parts = joined.getSiblings();
        assertEquals(3, parts.size());
        assertEquals("Iron Ingot × 3", parts.get(0).getString());
        assertEquals("ae2federation.ui.provider.returns_separator",
                ((TranslatableContents) parts.get(1).getContents()).getKey());
        assertEquals("Gold Ingot × 1", parts.get(2).getString());
    }

    @Test
    void oneReturnHasNoSeparator() {
        assertEquals("Iron Ingot × 3", FederationProviderMenuHolder.joined(List.of(Component.literal("Iron Ingot × 3")))
                .getString());
    }
}
