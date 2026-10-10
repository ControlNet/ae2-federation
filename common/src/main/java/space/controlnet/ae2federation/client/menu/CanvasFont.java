package space.controlnet.ae2federation.client.menu;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * The topology canvas's font (font/canvas.json): Chinese from a 10-pixel sheet at one texel per unit, which stays
 * legible as far out as the Latin pixel font does where Unifont's 16-pixel glyphs blur, and everything else from the
 * default font.
 */
final class CanvasFont {
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("ae2federation", "canvas");
    /** The sheet has no glyph below this; it is where CJK radicals, punctuation and ideographs begin. */
    private static final int CJK_START = 0x2E80;

    private CanvasFont() {
    }

    /**
     * {@code text} in bold except its Chinese. Bold redraws a glyph one unit to the right: a Latin glyph gets thicker,
     * but a 10-pixel hanzi's strokes are one texel apart, so they merge into a block.
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
