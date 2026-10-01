package space.controlnet.ae2federation.client.menu;

import com.lowdragmc.lowdraglib2.editor.resource.BuiltinResourceProvider;
import com.lowdragmc.lowdraglib2.editor.resource.ResourceInstance;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * AE2 surfaces in the NeoECO AE Extension palette: a light bevelled frame, lipped buttons, dark inner panels and
 * cards. Switches and toolbar buttons reuse AE2's own sprites.
 */
public final class FederationTheme {
    public static final int OUTLINE = 0xff413f54;
    public static final int FACE = 0xffcbccd4;
    public static final int HIGHLIGHT = 0xfff2f2f2;
    public static final int BAND = 0xff878fa5;
    public static final int TEXT = 0xff3f3d52;
    public static final int TEXT_MUTED = 0xff6d6a82;
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
    public static final IGuiTexture FIELD = inset(HIGHLIGHT, 0xff9a9fb4);
    public static final IGuiTexture BUTTON = button(0xff9a9fb4, 0xffadb0c4, 0xff696d88, 2);
    public static final IGuiTexture HOVER = button(SELECT, 0xffdaffff, 0xff708cba, 2);
    public static final IGuiTexture PRESSED = button(SELECT, 0xffdaffff, 0xff708cba, 1);
    public static final IGuiTexture DANGER = button(0xffd5b6bd, 0xffffe6e9, 0xff916271, 2);
    public static final IGuiTexture DISABLED = button(0xff696d88, 0xff767a93, 0xff5b5e76, 1);
    public static final IGuiTexture DARK_PANEL = darkPanel();
    public static final IGuiTexture CARD = card(0xffd8d3e4);
    public static final IGuiTexture CARD_SELECTED = card(SELECT);
    public static final IGuiTexture WELL_RECT = well();
    public static final IGuiTexture RAIL = rail();

    private static final ResourceLocation AE2_STATES = ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/states.png");
    public static final IGuiTexture SWITCH_OFF = slider(false, false, false);
    public static final IGuiTexture SWITCH_OFF_HOVER = slider(false, true, false);
    public static final IGuiTexture SWITCH_OFF_LOCKED = slider(false, false, true);
    public static final IGuiTexture SWITCH_ON = slider(true, false, false);
    public static final IGuiTexture SWITCH_ON_HOVER = slider(true, true, false);
    public static final IGuiTexture SWITCH_ON_LOCKED = slider(true, false, true);
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

    private static IGuiTexture sprite(ResourceLocation image, int x, int y, int width, int height) {
        return SpriteTexture.of(image).setSprite(x, y, width, height);
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

    /**
     * The design's slider switch: a raised knob beside a coloured track. On, the knob sits right of a blue track with
     * a light bar; off, it sits left of a grey track with a hollow square. A locked switch is drawn faded.
     */
    private static IGuiTexture slider(boolean on, boolean hover, boolean locked) {
        return painted((pen, x, y, width, height) -> {
            float knob = Math.round(width * 0.46f);
            float trackX = on ? x + 1 : x + 1 + knob;
            float trackWidth = width - 2 - knob;
            float knobX = on ? x + width - 1 - knob : x + 1;
            pen.rect(x, y, width, height, fade(OUTLINE, locked));
            pen.rect(trackX, y + 1, trackWidth, height - 2, fade(on ? SELECT : 0xff696d88, locked));
            float middleX = Math.round(trackX + trackWidth / 2f);
            float middleY = Math.round(y + height / 2f);
            if (on) {
                pen.rect(middleX - 1, middleY - 3, 1, 5, fade(0xffdaffff, locked));
            } else {
                int square = fade(0xff878fa5, locked);
                pen.rect(middleX - 2, middleY - 2, 4, 1, square);
                pen.rect(middleX - 2, middleY + 1, 4, 1, square);
                pen.rect(middleX - 2, middleY - 2, 1, 4, square);
                pen.rect(middleX + 1, middleY - 2, 1, 4, square);
            }
            pen.rect(knobX, y + 1, knob, height - 2, fade(hover ? 0xffadb0c4 : 0xff9a9fb4, locked));
            pen.rect(knobX, y + 1, knob, 1, fade(hover ? 0xffdaffff : 0xffadb0c4, locked));
            pen.rect(knobX, y + 1, 1, height - 4, fade(hover ? 0xffdaffff : 0xffadb0c4, locked));
            pen.rect(knobX, y + height - 3, knob, 2, fade(0xff696d88, locked));
        });
    }

    /** A locked control reads at about half strength against the dark panel, as the design's 55% opacity. */
    private static int fade(int color, boolean locked) {
        if (!locked) return color;
        int panel = 0x2f2a34;
        int r = Math.round((color >> 16 & 0xff) * 0.55f + (panel >> 16 & 0xff) * 0.45f);
        int g = Math.round((color >> 8 & 0xff) * 0.55f + (panel >> 8 & 0xff) * 0.45f);
        int b = Math.round((color & 0xff) * 0.55f + (panel & 0xff) * 0.45f);
        return 0xff000000 | r << 16 | g << 8 | b;
    }

    /** A face with a top/left highlight and a darker bottom lip that reads as a raised AE2 button. */
    private static IGuiTexture button(int face, int light, int lip, int lipHeight) {
        return painted((pen, x, y, width, height) -> {
            pen.rect(x, y, width, height, OUTLINE);
            pen.rect(x + 1, y + 1, width - 2, height - 2, face);
            pen.rect(x + 1, y + 1, width - 2, 1, light);
            pen.rect(x + 1, y + 1, 1, height - 2 - lipHeight, light);
            pen.rect(x + 1, y + height - 1 - lipHeight, width - 2, lipHeight, lip);
        });
    }

    private static IGuiTexture inset(int fill, int shadow) {
        return painted((pen, x, y, width, height) -> {
            pen.rect(x, y, width, height, OUTLINE);
            pen.rect(x + 1, y + 1, width - 2, height - 2, fill);
            pen.rect(x + 1, y + 1, width - 2, 1, shadow);
            pen.rect(x + 1, y + 1, 1, height - 2, shadow);
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

    /** A one-pixel state line along a card's bottom edge. */
    /** A one-pixel dashed outline: something shown here that belongs elsewhere, such as a related domain's link. */
    public static IGuiTexture dashedBorder(int color) {
        return painted((pen, x, y, width, height) -> {
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
