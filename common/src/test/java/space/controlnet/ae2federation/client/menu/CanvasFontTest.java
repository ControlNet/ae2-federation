package space.controlnet.ae2federation.client.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class CanvasFontTest {
    @Test
    void chineseInABoldNameIsNotBolded() {
        // Bold redraws a glyph one unit to the right: a Latin glyph gets thicker, a hanzi's strokes one texel apart merge.
        assertEquals(List.of("网络:plain", " 0A1F:bold"), runs(CanvasFont.boldExceptChinese(Component.literal("网络 0A1F"))));
    }

    @Test
    void aLatinNameStaysBoldAsOneRun() {
        assertEquals(List.of("Network 0A1F:bold"), runs(CanvasFont.boldExceptChinese(Component.literal("Network 0A1F"))));
    }

    @Test
    void aMixedPlayerNameSwitchesAtEveryScriptChange() {
        assertEquals(List.of("ME:bold", "主网:plain", "-2:bold", "，北:plain"),
                runs(CanvasFont.boldExceptChinese(Component.literal("ME主网-2，北"))));
    }

    @Test
    void theNamesTextIsKept() {
        var name = Component.translatable("ae2federation.ui.topology.network_tag", "0A1F");
        assertEquals(name.getString(), CanvasFont.boldExceptChinese(name).getString());
    }

    private static List<String> runs(Component text) {
        var runs = new ArrayList<String>();
        text.visit((style, part) -> {
            if (!part.isEmpty()) runs.add(part + (style.isBold() ? ":bold" : ":plain"));
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return runs;
    }
}
