package space.controlnet.ae2federation.client.automation;

import com.lowdragmc.lowdraglib2.gui.LDLibFonts;
import com.lowdragmc.lowdraglib2.gui.ui.elements.codeeditor.CodeEditor;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The Automation tab's script editor: LDLib2's code editor with the scripts' colours ({@link PythonLanguage}) and a
 * line-number gutter in the left padding of its content view, widened to fit the numbers. It only shows a program
 * until editing is turned on.
 */
public final class ScriptEditor extends CodeEditor {
    private static final int GUTTER = 0xff17141e;
    private static final int NUMBER = 0xff6f6a7a;
    /** The narrowest gutter, the room on either side of its numbers, and between the gutter and the text. */
    private static final float MIN_GUTTER = 16;
    private static final float NUMBER_GAP = 3;
    private static final float TEXT_GAP = 4;

    private boolean editing;
    /** The content view's left padding as last set: the gutter and the gap after it. */
    private float padding = -1;

    public ScriptEditor() {
        setLanguage(PythonLanguage.INSTANCE);
        setStyleManager(PythonLanguage.INSTANCE.styles());
        addClass("script-editor");
    }

    /** Whether keys change the text; the text can be selected and scrolled either way. */
    public void setEditing(boolean editing) {
        this.editing = editing;
    }

    @Override
    public boolean isEditable() {
        return editing && super.isEditable();
    }

    private MutableComponent inFont(String text) {
        return Component.literal(text).withStyle(style -> style.withFont(getTextAreaStyle().font()));
    }

    /**
     * The gutter is drawn over the content view's padding after the view, which clips its own drawing to its text. A
     * line count with another digit widens it from the next frame.
     */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawBackgroundOverlay(GUIContext context) {
        super.drawBackgroundOverlay(context);
        var font = getFont();
        float scale = scale();
        int lines = getLines().size();
        float width = Math.max(MIN_GUTTER,
                font.getSplitter().stringWidth(inFont("9".repeat(String.valueOf(lines).length()))) * scale + 2 * NUMBER_GAP);
        if (width + TEXT_GAP != padding) {
            padding = width + TEXT_GAP;
            contentView.layout(layout -> layout.paddingLeft(padding));
        }
        float left = contentView.getPositionX();
        DrawerHelper.drawSolidRect(context.graphics, left, contentView.getPositionY(), width, contentView.getSizeHeight(), GUTTER);
        float lineHeight = lineHeight();
        int first = Math.max(0, (int) Math.floor(getScrollY() / lineHeight));
        int last = Math.min(lines - 1, first + (int) Math.ceil(contentView.getContentHeight() / lineHeight) + 1);
        context.graphics.flush();
        context.enableScissor(left, contentView.getContentY(), width, contentView.getContentHeight());
        for (int line = first; line <= last; line++) {
            var number = inFont(String.valueOf(line + 1));
            context.pose.pushPose();
            context.pose.translate(left + width - NUMBER_GAP - font.getSplitter().stringWidth(number) * scale,
                    contentView.getContentY() + line * lineHeight - getScrollY(), 0);
            context.pose.scale(scale, scale, 1);
            LDLibFonts.drawText(context.graphics, font, number, 0, 0, NUMBER, false);
            context.pose.popPose();
        }
        context.graphics.flush();
        context.disableScissor();
    }
}
