package space.controlnet.ae2federation.client.menu;

import com.lowdragmc.lowdraglib2.editor.resource.BuiltinResourceProvider;
import com.lowdragmc.lowdraglib2.editor.resource.ResourceInstance;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * AE2 surfaces in the NeoECO AE Extension palette: a light bevelled frame, dark inner panels and cards. Buttons, text
 * fields, switches, the scrollbar and toolbar buttons are AE2's own sprites, as NeoECO's are.
 */
public final class FederationTheme {
    public static final int OUTLINE = 0xff413f54;
    public static final int FACE = 0xffcbccd4;
    public static final int HIGHLIGHT = 0xfff2f2f2;
    public static final int BAND = 0xff878fa5;
    public static final int TEXT = 0xff413f54;
    public static final int TEXT_MUTED = 0xff6d6a82;
    /** AE2's text field placeholder, light on the field's grey-blue fill. */
    public static final int PLACEHOLDER = 0xffdedfe3;
    public static final int DARK_TEXT = 0xffd6d0e0;
    public static final int DARK_TITLE = 0xffefeaf8;
    public static final int DARK_MUTED = 0xffaaa4b2;
    public static final int CANVAS = 0xff201e27;
    public static final int WELL = 0xff17141e;
    public static final int SELECT = 0xff9cd3ff;
    public static final int OK = 0xff55ff8a;
    public static final int WARN = 0xffffd65a;
    public static final int ERROR = 0xffff6a75;
    public static final int VALUE = 0xff8377ff;
    public static final int TEAL = 0xff26a6bd;
    public static final int INFO = 0xff55a7ff;
    public static final int EDGE = 0xff8b83a0;
    /** AE2's energy green, for stored energy that is not running low. */
    public static final int ENERGY = 0xff00fc00;
    /** A shared-energy link, drawn like AE2's Quartz Fiber: a pale rail, its core, and the quartz beads on it. */
    public static final int QUARTZ = 0xffcfd9e8;
    public static final int QUARTZ_CORE = 0xff7f93ab;
    public static final int QUARTZ_BEAD = 0xffe8f4ff;
    public static final int QUARTZ_BEAD_EDGE = 0xff3b4a5c;
    /** Distinct network accents; a network keeps its colour for the lifetime of the open workspace. */
    public static final int[] NETWORK_ACCENTS = {0xff61afef, 0xffd19a66, 0xffc678dd, 0xff98c379, 0xffe06c75,
            0xff56b6c2, 0xffe5c07b, 0xffbe5046};

    public static final int PANEL = FACE;
    public static final int INSET = 0xffb9bbc6;
    public static final int PAPER = HIGHLIGHT;
    public static final int SELECTED = SELECT;

    public static final IGuiTexture FRAME = frame();
    private static final ResourceLocation AE2_BUTTON = ae2("textures/gui/sprites/button.png");
    private static final ResourceLocation AE2_BUTTON_HIGHLIGHTED = ae2("textures/gui/sprites/button_highlighted.png");
    private static final ResourceLocation AE2_BUTTON_DISABLED = ae2("textures/gui/sprites/button_disabled.png");
    private static final ResourceLocation AE2_TEXT_FIELD = ae2("textures/guis/text_field.png");
    private static final ResourceLocation AE2_CHECKBOX = ae2("textures/guis/checkbox.png");
    private static final ResourceLocation AE2_SCROLLER = ae2("textures/gui/sprites/small_scroller.png");
    private static final ResourceLocation AE2_STATES = ae2("textures/guis/states.png");

    /** AE2's inset: a light rim and two shadow rows over the fill. Text fields and the scrollbar track use it. */
    public static final IGuiTexture FIELD = sprite(AE2_TEXT_FIELD, 0, 0, 128, 12, 1, 3, 1, 1);
    // AE2's button sprites. The highlighted one sits a pixel lower and the disabled one two, as AE2 draws them.
    public static final IGuiTexture BUTTON = sprite(AE2_BUTTON, 0, 0, 200, 20, 2, 2, 2, 5);
    public static final IGuiTexture HOVER = sprite(AE2_BUTTON_HIGHLIGHTED, 0, 0, 200, 20, 2, 3, 2, 4);
    public static final IGuiTexture PRESSED = HOVER;
    public static final IGuiTexture DISABLED = sprite(AE2_BUTTON_DISABLED, 0, 0, 200, 20, 2, 4, 2, 3);
    /** AE2 has no warning button: its button's shape in red, for an action that releases or removes something. */
    public static final IGuiTexture DANGER = danger();
    public static final IGuiTexture SCROLL_BAR = sprite(AE2_SCROLLER, 0, 0, 7, 15, 2, 2, 2, 4);
    public static final IGuiTexture DARK_PANEL = darkPanel();
    public static final IGuiTexture CARD = card(0xffd8d3e4);
    public static final IGuiTexture CARD_SELECTED = card(SELECT);
    /** A related domain's network: the card's frame without its solid edge, which the caller draws dashed. */
    public static final IGuiTexture CARD_RELATED = relatedCard();
    public static final IGuiTexture WELL_RECT = well();
    public static final IGuiTexture RAIL = rail();

    // AE2's 22x12 switch: off, the knob left of a grey track; on, a blue track left of the knob. Hover is lighter.
    public static final IGuiTexture SWITCH_OFF = sprite(AE2_CHECKBOX, 0, 28, 22, 12);
    public static final IGuiTexture SWITCH_OFF_HOVER = sprite(AE2_CHECKBOX, 22, 28, 22, 12);
    public static final IGuiTexture SWITCH_ON = sprite(AE2_CHECKBOX, 0, 40, 22, 12);
    public static final IGuiTexture SWITCH_ON_HOVER = sprite(AE2_CHECKBOX, 22, 40, 22, 12);
    /** A locked switch reads at about half strength against the dark panel. */
    public static final IGuiTexture SWITCH_OFF_LOCKED = tinted(AE2_CHECKBOX, 0, 28, 22, 12, 0x8cffffff);
    public static final IGuiTexture SWITCH_ON_LOCKED = tinted(AE2_CHECKBOX, 0, 40, 22, 12, 0x8cffffff);
    /** A re-exporting rule: AE2's on switch with only its track green; the knob and outline stay AE2's. */
    public static final IGuiTexture SWITCH_REEXPORT = reexport(0, SwitchTrackOverlay.ON, 0xff);
    public static final IGuiTexture SWITCH_REEXPORT_HOVER = reexport(22, SwitchTrackOverlay.ON_HOVER, 0xff);
    public static final IGuiTexture SWITCH_REEXPORT_LOCKED = reexport(0, SwitchTrackOverlay.ON, 0x8c);
    /** A flat outlined button on a dark panel, such as a rule's "Map ›" link. */
    public static final IGuiTexture LINK = painted((pen, x, y, width, height) -> {
        pen.rect(x, y, width, 1, EDGE);
        pen.rect(x, y + height - 1, width, 1, EDGE);
        pen.rect(x, y, 1, height, EDGE);
        pen.rect(x + width - 1, y, 1, height, EDGE);
    });
    public static final IGuiTexture LINK_HOVER = painted((pen, x, y, width, height) -> {
        pen.rect(x, y, width, height, 0xff47434f);
        pen.rect(x, y, width, 1, SELECT);
        pen.rect(x, y + height - 1, width, 1, SELECT);
        pen.rect(x, y, 1, height, SELECT);
        pen.rect(x + width - 1, y, 1, height, SELECT);
    });
    /** The faint rule between a pair editor's rule rows. */
    public static final IGuiTexture ROW_RULE = painted((pen, x, y, width, height) -> pen.rect(x, y, width, 1, 0x1fd8d3e4));
    public static final IGuiTexture TOOLBAR = sprite(AE2_STATES, 176, 128, 18, 20);
    public static final IGuiTexture TOOLBAR_HOVER = sprite(AE2_STATES, 212, 128, 18, 20);
    public static final IGuiTexture TOOLBAR_ACTIVE = sprite(AE2_STATES, 194, 128, 18, 20);

    private FederationTheme() {}

    /** Axis-aligned fills; painters only see this, so textures can be built where client classes are absent. */
    public interface Pen {
        void rect(float x, float y, float width, float height, int color);
    }

    public interface Painter {
        void paint(Pen pen, float x, float y, float width, float height);
    }

    /**
     * A texture drawn by a {@link Painter}. Menus are also built on the dedicated server, where a lambda implementing
     * {@link IGuiTexture} directly would fail to link against client-only {@code GuiGraphics}.
     */
    public static IGuiTexture painted(Painter painter) {
        return new PaintedTexture(painter);
    }

    private static final class PaintedTexture implements IGuiTexture {
        private final Painter painter;

        private PaintedTexture(Painter painter) {
            this.painter = painter;
        }

        @Override
        public void draw(net.minecraft.client.gui.GuiGraphics graphics, float mouseX, float mouseY, float x, float y,
                float width, float height, float partialTicks) {
            painter.paint((left, top, w, h, color) -> DrawerHelper.drawSolidRect(graphics, left, top, w, h, color),
                    x, y, width, height);
        }
    }

    public static void register(ResourceInstance<IGuiTexture> instance) {
        var provider = new BuiltinResourceProvider<IGuiTexture>("federation", instance);
        provider.addResource("FRAME", FRAME);
        provider.addResource("FIELD", FIELD);
        provider.addResource("BUTTON", BUTTON);
        provider.addResource("HOVER", HOVER);
        provider.addResource("PRESSED", PRESSED);
        provider.addResource("DANGER", DANGER);
        provider.addResource("DISABLED", DISABLED);
        provider.addResource("DARK_PANEL", DARK_PANEL);
        provider.addResource("CARD", CARD);
        provider.addResource("WELL", WELL_RECT);
        provider.addResource("RAIL", RAIL);
        provider.addResource("SWITCH_OFF", SWITCH_OFF);
        provider.addResource("SWITCH_ON", SWITCH_ON);
        provider.addResource("SWITCH_OFF_LOCKED", SWITCH_OFF_LOCKED);
        provider.addResource("SWITCH_ON_LOCKED", SWITCH_ON_LOCKED);
        provider.addResource("SWITCH_REEXPORT", SWITCH_REEXPORT);
        provider.addResource("SWITCH_REEXPORT_LOCKED", SWITCH_REEXPORT_LOCKED);
        provider.addResource("SCROLL_BAR", SCROLL_BAR);
        provider.addResource("ROW_RULE", ROW_RULE);
        provider.addResource("LINK", LINK);
        provider.addResource("LINK_HOVER", LINK_HOVER);
        provider.addResource("TOOLBAR", TOOLBAR);
        provider.addResource("TOOLBAR_HOVER", TOOLBAR_HOVER);
        provider.addResource("TOOLBAR_ACTIVE", TOOLBAR_ACTIVE);
        instance.addBuiltinProvider(provider);
    }

    public static int networkAccent(int index) {
        return NETWORK_ACCENTS[Math.floorMod(index, NETWORK_ACCENTS.length)];
    }

    private static ResourceLocation ae2(String path) {
        return ResourceLocation.fromNamespaceAndPath("ae2", path);
    }

    private static IGuiTexture sprite(ResourceLocation image, int x, int y, int width, int height) {
        return new SnappedSprite(SpriteTexture.of(image).setSprite(x, y, width, height));
    }

    private static IGuiTexture sprite(ResourceLocation image, int x, int y, int width, int height, int left, int top,
            int right, int bottom) {
        return new SnappedSprite(SpriteTexture.of(image).setSprite(x, y, width, height)
                .setBorder(left, top, right, bottom));
    }

    private static IGuiTexture tinted(ResourceLocation image, int x, int y, int width, int height, int color) {
        return new SnappedSprite(SpriteTexture.of(image).setSprite(x, y, width, height).setColor(color));
    }

    private static IGuiTexture reexport(int spriteX, int[][] track, int alpha) {
        return new RecolouredSwitch(SpriteTexture.of(AE2_CHECKBOX).setSprite(spriteX, 40, 22, 12)
                .setColor(alpha << 24 | 0xffffff), track, alpha);
    }

    /** An AE2 sprite drawn with its edges on whole screen pixels; a rotated or skewed pose draws it as laid out. */
    private static class SnappedSprite implements IGuiTexture {
        private final SpriteTexture sprite;

        private SnappedSprite(SpriteTexture sprite) {
            this.sprite = sprite;
        }

        @Override
        public void draw(net.minecraft.client.gui.GuiGraphics target, float mouseX, float mouseY, float x, float y,
                float width, float height, float partialTicks) {
            var pose = target.pose().last().pose();
            if (pose.m01() != 0 || pose.m10() != 0 || pose.m00() == 0 || pose.m11() == 0) {
                drawAt(target, mouseX, mouseY, x, y, width, height, partialTicks);
                return;
            }
            double guiScale = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
            float[] horizontal = PixelSnap.span(x, width, pose.m00(), pose.m30(), guiScale);
            float[] vertical = PixelSnap.span(y, height, pose.m11(), pose.m31(), guiScale);
            drawAt(target, mouseX, mouseY, horizontal[0], vertical[0], horizontal[1], vertical[1], partialTicks);
        }

        void drawAt(net.minecraft.client.gui.GuiGraphics target, float mouseX, float mouseY, float x, float y,
                float width, float height, float partialTicks) {
            sprite.draw(target, mouseX, mouseY, x, y, width, height, partialTicks);
        }
    }

    /**
     * AE2's on switch with its track pixels painted over in green, scaled into the same snapped rectangle as the
     * sprite so each painted pixel covers exactly one of the sprite's.
     */
    private static final class RecolouredSwitch extends SnappedSprite {
        private final int[][] track;
        private final int alpha;

        private RecolouredSwitch(SpriteTexture sprite, int[][] track, int alpha) {
            super(sprite);
            this.track = track;
            this.alpha = alpha;
        }

        @Override
        void drawAt(net.minecraft.client.gui.GuiGraphics target, float mouseX, float mouseY, float x, float y,
                float width, float height, float partialTicks) {
            super.drawAt(target, mouseX, mouseY, x, y, width, height, partialTicks);
            float unitX = width / 22;
            float unitY = height / 12;
            for (var rect : track) {
                int color = alpha << 24 | SwitchTrackOverlay.color(rect[4]) & 0xffffff;
                DrawerHelper.drawSolidRect(target, x + rect[0] * unitX, y + rect[1] * unitY, rect[2] * unitX,
                        rect[3] * unitY, color);
            }
        }
    }

    /** Outline, one-pixel highlight and a two-pixel bottom band, as in AE2's and NeoECO's panel backgrounds. */
    private static IGuiTexture frame() {
        return painted((pen, x, y, width, height) -> {
            pen.rect(x, y, width, height, OUTLINE);
            pen.rect(x + 1, y + 1, width - 2, height - 2, FACE);
            pen.rect(x + 1, y + 1, width - 2, 1, HIGHLIGHT);
            pen.rect(x + 1, y + 1, 1, height - 2, HIGHLIGHT);
            pen.rect(x + 1, y + height - 3, width - 2, 2, BAND);
        });
    }

    private static IGuiTexture rail() {
        return painted((pen, x, y, width, height) -> {
            pen.rect(x, y, width, height, OUTLINE);
            pen.rect(x + 1, y + 1, width - 1, height - 2, FACE);
            pen.rect(x + 1, y + 1, width - 1, 1, HIGHLIGHT);
            pen.rect(x + 1, y + 1, 1, height - 2, HIGHLIGHT);
        });
    }

    /** AE2's button geometry: outline, a one-pixel highlight around the face and a three-pixel lip. */
    private static IGuiTexture danger() {
        return painted((pen, x, y, width, height) -> {
            pen.rect(x, y, width, height, OUTLINE);
            pen.rect(x + 1, y + 1, width - 2, height - 5, 0xffffe6e9);
            pen.rect(x + 2, y + 2, width - 4, height - 7, 0xffd5b6bd);
            pen.rect(x + 1, y + height - 4, width - 2, 3, 0xff916271);
        });
    }

    private static IGuiTexture darkPanel() {
        return painted((pen, x, y, width, height) -> {
            pen.rect(x, y, width, height, 0xff0d0d11);
            pen.rect(x + 1, y + 1, width - 2, height - 2, 0xff2f2a34);
            pen.rect(x + 1, y + 1, width - 2, 1, 0xff85818d);
            pen.rect(x + 1, y + 1, 1, height - 2, 0xff85818d);
            pen.rect(x + 1, y + height - 2, width - 2, 1, 0xff47434f);
            pen.rect(x + width - 2, y + 1, 1, height - 2, 0xff47434f);
        });
    }

    private static IGuiTexture relatedCard() {
        return painted((pen, x, y, width, height) -> {
            pen.rect(x, y, width, height, 0xff121016);
            pen.rect(x + 2, y + 2, width - 4, height - 4, 0xff2c2735);
        });
    }

    private static IGuiTexture card(int edge) {
        return painted((pen, x, y, width, height) -> {
            pen.rect(x, y, width, height, 0xff121016);
            pen.rect(x + 1, y + 1, width - 2, height - 2, edge);
            pen.rect(x + 2, y + 2, width - 4, height - 4, 0xff2c2735);
        });
    }

    private static IGuiTexture well() {
        return painted((pen, x, y, width, height) -> {
            pen.rect(x, y, width, height, 0xff0d0d11);
            pen.rect(x + 1, y + 1, width - 2, height - 2, WELL);
        });
    }

    /** A one-pixel dashed outline: something shown here that belongs elsewhere, such as a related domain's link. */
    public static IGuiTexture dashedBorder(int color) {
        return dashedBorder(color, 0);
    }

    /** {@link #dashedBorder(int)} drawn {@code inset} pixels inside the bounds, where a card draws its edge. */
    public static IGuiTexture dashedBorder(int color, float inset) {
        return painted((pen, outerX, outerY, outerWidth, outerHeight) -> {
            float x = outerX + inset;
            float y = outerY + inset;
            float width = outerWidth - 2 * inset;
            float height = outerHeight - 2 * inset;
            for (float at = 0; at < width; at += 4) {
                float length = Math.min(2, width - at);
                pen.rect(x + at, y, length, 1, color);
                pen.rect(x + at, y + height - 1, length, 1, color);
            }
            for (float at = 0; at < height; at += 4) {
                float length = Math.min(2, height - at);
                pen.rect(x, y + at, 1, length, color);
                pen.rect(x + width - 1, y + at, 1, length, color);
            }
        });
    }

    /** A small padlock, 5x6 pixels at the top-right corner: this can be read here but not changed. */
    public static IGuiTexture lockMark(int color) {
        return painted((pen, x, y, width, height) -> {
            float left = x + width - 8;
            float top = y + 2;
            pen.rect(left + 1, top, 3, 1, color);
            pen.rect(left, top + 1, 1, 2, color);
            pen.rect(left + 4, top + 1, 1, 2, color);
            pen.rect(left, top + 3, 5, 3, color);
        });
    }

    public static IGuiTexture accentLine(int color) {
        return painted((pen, x, y, width, height) ->
                pen.rect(x + 2, y + height - 3, width - 4, 1, color));
    }

    public static IGuiTexture solid(int color) {
        return painted((pen, x, y, width, height) ->
                pen.rect(x, y, width, height, color));
    }
}
