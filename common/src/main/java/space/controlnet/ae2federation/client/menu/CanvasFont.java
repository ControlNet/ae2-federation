package space.controlnet.ae2federation.client.menu;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * The topology canvas's font (font/canvas.json): Chinese from a vector font, which stays legible when the canvas is
 * zoomed out where Unifont's pixel glyphs blur, and everything else from the default font.
 */
final class CanvasFont {
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("ae2federation", "canvas");
    /** The vector font has no glyph below this; it is where CJK radicals, punctuation and ideographs begin. */
    private static final int CJK_START = 0x2E80;

    private CanvasFont() {
    }

    /**
     * {@code text} in bold except its Chinese. Bold redraws a glyph one unit to the right: a pixel glyph gets thicker,
     * but the vector font's strokes are thinner than that, so they show twice.
     */
    static MutableComponent boldExceptChinese(Component text) {
        var plain = text.getString();
        var result = Component.empty();
        int start = 0;
        while (start < plain.length()) {
            boolean chinese = plain.codePointAt(start) >= CJK_START;
            int end = start;
            while (end < plain.length() && plain.codePointAt(end) >= CJK_START == chinese) {
                end += Character.charCount(plain.codePointAt(end));
            }
            result.append(Component.literal(plain.substring(start, end)).withStyle(Style.EMPTY.withBold(!chinese)));
            start = end;
        }
        return result;
    }
}
