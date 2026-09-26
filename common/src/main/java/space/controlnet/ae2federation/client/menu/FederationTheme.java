package space.controlnet.ae2federation.client.menu;

import com.lowdragmc.lowdraglib2.editor.resource.BuiltinResourceProvider;
import com.lowdragmc.lowdraglib2.editor.resource.ResourceInstance;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;

/** Shared pixel bevels and neutral surfaces for the Federation workspace. */
public final class FederationTheme {
    public static final int PANEL = 0xffc8c8d2;
    public static final int INSET = 0xffb7bac9;
    public static final int PAPER = 0xffe0e0e6;
    public static final int TEXT = 0xff343548;
    public static final int SELECTED = 0xffacd3e5;
    public static final IGuiTexture FRAME = bevel(PANEL, 0xfff5f5f8, 0xff73758a);
    public static final IGuiTexture FIELD = bevel(PAPER, 0xff77798d, 0xfff4f4f7);
    public static final IGuiTexture BUTTON = bevel(0xffafb2c5, 0xffe9eaf1, 0xff686c83);
    public static final IGuiTexture HOVER = bevel(0xffc5deed, 0xfff4faff, 0xff638aa3);
    public static final IGuiTexture PRESSED = bevel(SELECTED, 0xff638aa3, 0xffedf8ff);
    public static final IGuiTexture DANGER = bevel(0xffd5b6bd, 0xffffe6e9, 0xff916271);

    private FederationTheme() {}

    public static void register(ResourceInstance<IGuiTexture> instance) {
        var provider = new BuiltinResourceProvider<IGuiTexture>("federation", instance);
        provider.addResource("FRAME", FRAME);
        provider.addResource("FIELD", FIELD);
        provider.addResource("BUTTON", BUTTON);
        provider.addResource("HOVER", HOVER);
        provider.addResource("PRESSED", PRESSED);
        provider.addResource("DANGER", DANGER);
        instance.addBuiltinProvider(provider);
    }

    private static IGuiTexture bevel(int fill, int light, int shadow) {
        return (graphics, mouseX, mouseY, x, y, width, height, partialTicks) -> {
            DrawerHelper.drawSolidRect(graphics, x, y, width, height, 0xff535669);
            DrawerHelper.drawSolidRect(graphics, x + 1, y + 1, width - 2, height - 2, fill);
            DrawerHelper.drawSolidRect(graphics, x + 1, y + 1, width - 2, 1, light);
            DrawerHelper.drawSolidRect(graphics, x + 1, y + 1, 1, height - 2, light);
            DrawerHelper.drawSolidRect(graphics, x + 1, y + height - 2, width - 2, 1, shadow);
            DrawerHelper.drawSolidRect(graphics, x + width - 2, y + 1, 1, height - 2, shadow);
        };
    }
}
