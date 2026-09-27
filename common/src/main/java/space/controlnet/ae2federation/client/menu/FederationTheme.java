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
    public static final int EDGE = 0xff8b83a0;
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

    private static final ResourceLocation AE2_CHECKBOX = ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/checkbox.png");
    private static final ResourceLocation AE2_STATES = ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/states.png");
    public static final IGuiTexture SWITCH_OFF = sprite(AE2_CHECKBOX, 0, 28, 22, 12);
    public static final IGuiTexture SWITCH_OFF_HOVER = sprite(AE2_CHECKBOX, 22, 28, 22, 12);
    public static final IGuiTexture SWITCH_ON = sprite(AE2_CHECKBOX, 0, 40, 22, 12);
    public static final IGuiTexture SWITCH_ON_HOVER = sprite(AE2_CHECKBOX, 22, 40, 22, 12);
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
    public static IGuiTexture accentLine(int color) {
        return painted((pen, x, y, width, height) ->
                pen.rect(x + 2, y + height - 3, width - 4, 1, color));
    }

    public static IGuiTexture solid(int color) {
        return painted((pen, x, y, width, height) ->
                pen.rect(x, y, width, height, color));
    }
}
