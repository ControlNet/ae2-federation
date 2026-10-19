package space.controlnet.ae2federation.client.automation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import space.controlnet.ae2federation.automation.AutomationInterfaceScreens;
import space.controlnet.ae2federation.client.menu.FederationTheme;
import space.controlnet.ae2federation.persistence.NetworkNameBook;

/**
 * The ID and alias screen of an Automation Interface or a computing device, as the design boards "ME联邦自动化接口"
 * and the device's beside it show it: the ID with a copy button and the alias, nothing else. The alias is sent when the player presses Enter or closes the screen; a refused one turns red.
 */
public final class AutomationInterfaceScreen extends Screen {
    private static final int WIDTH = 200;
    private static final int HEIGHT = 72;
    private static final int LABEL_X = 6;
    private static final int FIELD_X = 40;
    private static final int ID_W = 112;
    private static final int ALIAS_W = 154;
    private static final int ROW_H = 15;
    private static final int ID_Y = 22;
    private static final int ALIAS_Y = 44;

    private AutomationInterfaceScreens.View view;
    /** How much wider than {@link #WIDTH} the screen is drawn so that a long title fits; the fields grow with it. */
    private int extra;
    private EditBox alias;
    private String sent;

    private AutomationInterfaceScreen(AutomationInterfaceScreens.View view) {
        super(Component.translatable(view.title()));
        this.view = view;
        this.sent = view.alias();
    }

    /** Opens the screen, or updates it when it is already open for the same interface. */
    public static void show(AutomationInterfaceScreens.View view) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof AutomationInterfaceScreen open && open.view.position().equals(view.position())) {
            open.accept(view);
        } else {
            minecraft.setScreen(new AutomationInterfaceScreen(view));
        }
    }

    private void accept(AutomationInterfaceScreens.View answer) {
        view = answer;
        if (!answer.refused()) {
            sent = answer.alias();
            alias.setValue(answer.alias());
        }
        alias.setTextColor(answer.refused() ? FederationTheme.ERROR : FederationTheme.HIGHLIGHT);
    }

    private int left() {
        return (width - WIDTH - extra) / 2;
    }

    private int top() {
        return (height - HEIGHT) / 2;
    }

    @Override
    protected void init() {
        var current = alias == null ? view.alias() : alias.getValue();
        extra = Math.max(0, font.width(title) + 2 * LABEL_X - WIDTH);
        alias = new EditBox(font, left() + FIELD_X + 4, top() + ALIAS_Y + 4, ALIAS_W + extra - 8, ROW_H - 6,
                Component.translatable("ae2federation.automation_interface.alias"));
        alias.setBordered(false);
        alias.setMaxLength(NetworkNameBook.MAX_CODE_POINTS);
        // Light and flat on AE2's dark field, as the Domain screen's text fields draw.
        alias.setTextShadow(false);
        alias.setTextColor(FederationTheme.HIGHLIGHT);
        alias.setValue(current);
        alias.setResponder(text -> alias.setTextColor(FederationTheme.HIGHLIGHT));
        addRenderableWidget(alias);
        setInitialFocus(alias);
        var copyWidth = ALIAS_W - ID_W - 4;
        addRenderableWidget(new Ae2Button(left() + FIELD_X + ID_W + extra + 4, top() + ID_Y, copyWidth, ROW_H,
                Component.translatable("ae2federation.automation_interface.copy"),
                () -> Minecraft.getInstance().keyboardHandler.setClipboard(view.id())));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        int x = left();
        int y = top();
        FederationTheme.FRAME.draw(graphics, mouseX, mouseY, x, y, WIDTH + extra, HEIGHT, partialTick);
        graphics.drawString(font, title, x + LABEL_X, y + 7, FederationTheme.TEXT, false);
        graphics.drawString(font, Component.translatable("ae2federation.automation_interface.id"),
                x + LABEL_X, y + ID_Y + 4, FederationTheme.TEXT, false);
        graphics.drawString(font, Component.translatable("ae2federation.automation_interface.alias"),
                x + LABEL_X, y + ALIAS_Y + 4, FederationTheme.TEXT, false);
        FederationTheme.WELL_RECT.draw(graphics, mouseX, mouseY, x + FIELD_X, y + ID_Y, ID_W + extra, ROW_H,
                partialTick);
        graphics.drawString(font, view.id(), x + FIELD_X + 4, y + ID_Y + 4, FederationTheme.DARK_TEXT, false);
        FederationTheme.FIELD.draw(graphics, mouseX, mouseY, x + FIELD_X, y + ALIAS_Y, ALIAS_W + extra, ROW_H,
                partialTick);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && alias.isFocused()) {
            send();
            return true;
        }
        if (alias.isFocused() && minecraft != null && minecraft.options.keyInventory.matches(key, scanCode)) {
            // Typing E into the alias must not close the screen.
            return alias.keyPressed(key, scanCode, modifiers);
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void removed() {
        send();
        super.removed();
    }

    private void send() {
        var text = alias.getValue();
        if (!text.equals(sent)) {
            sent = text;
            AutomationInterfaceScreens.sendAlias(view.position(), text);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** A button drawn with AE2's own button sprites, as the Federation screens use. */
    private static final class Ae2Button extends AbstractButton {
        private final Runnable action;

        Ae2Button(int x, int y, int width, int height, Component message, Runnable action) {
            super(x, y, width, height, message);
            this.action = action;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            (isHoveredOrFocused() ? FederationTheme.HOVER : FederationTheme.BUTTON).draw(graphics, mouseX, mouseY,
                    getX(), getY(), width, height, partialTick);
            var font = Minecraft.getInstance().font;
            graphics.drawString(font, getMessage(), getX() + (width - font.width(getMessage())) / 2,
                    getY() + (height - 8) / 2, FederationTheme.TEXT, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
