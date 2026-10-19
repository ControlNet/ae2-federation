package space.controlnet.ae2federation.client.automation;

import com.lowdragmc.lowdraglib2.gui.LDLibFonts;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.client.menu.FederationTheme;
import space.controlnet.ae2federation.function.Candidate;
import space.controlnet.ae2federation.function.Completions;

/**
 * The script editor's completion list, under the caret: the candidates, and the chosen one's summary below them.
 * Functions show their signature, with a square when they act; items and fluids their icon, name and ID. The editor
 * keeps the keyboard and moves the choice; a click accepts a row. Drawn in the editor's font, as the
 * {@code AutoScript} and {@code AutoScriptItem} boards show it. The domain GUI's tree is built on the server too,
 * where the editor's code names this class: only the methods that measure and draw are client-only.
 */
public final class CompletionPopup extends UIElement {
    /** Rows shown before the list scrolls. */
    static final int ROWS = 8;
    private static final float PAD = 3;
    /** From the popup's left edge to its text, so a row's text starts under what it completes. */
    static final float TEXT_INSET = PAD + 2;
    private static final float GAP = 6;
    private static final float ICON = 8;
    private static final float MIN_WIDTH = 120;
    private static final float MAX_WIDTH = 480;
    /** Room at a row's right end for the square of a function that acts. */
    private static final float MARKER = 9;
    private static final int BACKGROUND = FederationTheme.WELL;
    private static final int CHOSEN = 0xff2c2735;
    private static final int RULE = 0xff3a3545;
    /** A function's name, and the square of a function that acts; the Library panel shows them alike. */
    public static final int NAME = 0xff7fb6ff;
    public static final int ACTS = 0xffb26cff;

    private final Font font;
    private final ResourceLocation textFont;
    private final float scale;
    private final float row;
    private final Consumer<Candidate> accept;
    private @Nullable Completions completions;
    private java.util.function.Function<String, String> resourceNote = id -> "";
    private int chosen;
    private int top;

    CompletionPopup(Font font, ResourceLocation textFont, float scale, float row, Consumer<Candidate> accept) {
        this.font = font;
        this.textFont = textFont;
        this.scale = scale;
        this.row = row;
        this.accept = accept;
        addClass("completion-popup");
        layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE));
        style(style -> style.zIndex(10));
        setDisplay(false);
        addEventListener(UIEvents.LAYOUT_CHANGED, event -> adaptPositionToScreen());
        addEventListener(UIEvents.MOUSE_DOWN, event -> {
            int index = top + (int) Math.floor((event.y - getPositionY() - PAD) / row);
            if (completions != null && index >= top && index < Math.min(completions.candidates().size(), top + ROWS)) {
                accept.accept(completions.candidates().get(index));
            }
            event.stopPropagation();
        });
        addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (completions == null) return;
            int last = Math.max(0, completions.candidates().size() - ROWS);
            top = Math.max(0, Math.min(last, top - (int) Math.signum(event.deltaY)));
            event.stopPropagation();
        });
    }

    /** Shows {@code completions} with its first candidate chosen; the caller places the popup. */
    @OnlyIn(Dist.CLIENT)
    void show(Completions completions) {
        this.completions = completions;
        chosen = 0;
        top = 0;
        float width = MIN_WIDTH;
        for (var candidate : completions.candidates()) width = Math.max(width, rowWidth(candidate));
        float height = Math.min(ROWS, completions.candidates().size()) * row + 2 * PAD
                + (footer().isEmpty() ? 0 : row + PAD);
        float clamped = Math.min(MAX_WIDTH, width + 2 * TEXT_INSET);
        layout(layout -> {
            layout.width(clamped);
            layout.height(height);
        });
        setDisplay(true);
    }

    void setResourceNote(java.util.function.Function<String, String> note) {
        resourceNote = note;
    }

    void hide() {
        completions = null;
        setDisplay(false);
    }

    boolean isOpen() {
        return completions != null && isDisplayed();
    }

    @Nullable Completions completions() {
        return completions;
    }

    @Nullable Candidate chosen() {
        return completions == null ? null : completions.candidates().get(chosen);
    }

    /** Moves the choice by {@code rows}, keeping it on the list and in view. */
    void move(int rows) {
        if (completions == null) return;
        chosen = Math.max(0, Math.min(completions.candidates().size() - 1, chosen + rows));
        if (chosen < top) top = chosen;
        if (chosen >= top + ROWS) top = chosen - ROWS + 1;
    }

    private MutableComponent text(String text) {
        return Component.literal(text).withStyle(style -> style.withFont(textFont));
    }

    @OnlyIn(Dist.CLIENT)
    private float width(String text) {
        return font.getSplitter().stringWidth(text(text)) * scale;
    }

    private static boolean isResource(Candidate candidate) {
        return candidate.kind() == Candidate.Kind.VALUE && candidate.label().contains(":");
    }

    @OnlyIn(Dist.CLIENT)
    private float rowWidth(Candidate candidate) {
        if (isResource(candidate)) return ICON + 3 + width(candidate.detail()) + GAP + width(candidate.label());
        return width(candidate.label()) + width(candidate.detail()) + (candidate.acts() ? MARKER : 0);
    }

    /**
     * What sits under the rows: the chosen function's summary, or the type a value must have, with what the editor
     * notes for a chosen resource ("Item · in stock: 1,284" on {@code AutoScriptItem}).
     */
    private String footer() {
        var candidate = chosen();
        if (candidate == null) return "";
        if (!candidate.doc().isEmpty()) return candidate.doc().lines().findFirst().orElse("");
        if (candidate.kind() == Candidate.Kind.VALUE && completions.expected() != null) {
            var note = isResource(candidate) ? resourceNote.apply(candidate.label()) : "";
            return completions.expected() + (note.isEmpty() ? "" : " \u00b7 " + note);
        }
        return "";
    }

    /** An item's icon, or a fluid's bucket; empty for an ID that is neither. */
    static ItemStack icon(String id) {
        var location = ResourceLocation.tryParse(id);
        if (location == null) return ItemStack.EMPTY;
        var item = BuiltInRegistries.ITEM.get(location);
        if (item != Items.AIR) return new ItemStack(item);
        var fluid = BuiltInRegistries.FLUID.get(location);
        return fluid == Fluids.EMPTY ? ItemStack.EMPTY : new ItemStack(fluid.getBucket());
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawBackgroundAdditional(GUIContext context) {
        super.drawBackgroundAdditional(context);
        if (completions == null) return;
        var graphics = context.graphics;
        float x = getPositionX();
        float y = getPositionY();
        float width = getSizeWidth();
        float height = getSizeHeight();
        DrawerHelper.drawSolidRect(graphics, x, y, width, height, FederationTheme.SELECT);
        DrawerHelper.drawSolidRect(graphics, x + 1, y + 1, width - 2, height - 2, BACKGROUND);
        var candidates = completions.candidates();
        int last = Math.min(candidates.size(), top + ROWS);
        context.graphics.flush();
        context.enableScissor(x + 1, y + 1, width - 2, height - 2);
        for (int index = top; index < last; index++) {
            var candidate = candidates.get(index);
            float rowY = y + PAD + (index - top) * row;
            if (index == chosen) DrawerHelper.drawSolidRect(graphics, x + 1, rowY, width - 2, row, CHOSEN);
            float textY = rowY + (row - 9 * scale) / 2;
            float left = x + TEXT_INSET;
            if (isResource(candidate)) {
                var stack = icon(candidate.label());
                if (!stack.isEmpty()) {
                    context.pose.pushPose();
                    context.pose.translate(left, rowY + (row - ICON) / 2, 0);
                    context.pose.scale(ICON / 16, ICON / 16, 1);
                    graphics.renderItem(stack, 0, 0);
                    context.pose.popPose();
                }
                draw(context, candidate.detail(), left + ICON + 3, textY, FederationTheme.DARK_TITLE);
                draw(context, candidate.label(), x + width - TEXT_INSET - width(candidate.label()), textY,
                        FederationTheme.TEXT_MUTED);
                continue;
            }
            draw(context, candidate.label(), left, textY, NAME);
            float room = x + width - TEXT_INSET - (candidate.acts() ? MARKER : 0) - left - width(candidate.label());
            draw(context, fit(candidate.detail(), room), left + width(candidate.label()), textY,
                    FederationTheme.DARK_MUTED);
            if (candidate.acts()) {
                DrawerHelper.drawSolidRect(graphics, x + width - TEXT_INSET - 3, rowY + (row - 3) / 2, 3, 3, ACTS);
            }
        }
        var footer = footer();
        if (!footer.isEmpty()) {
            float ruleY = y + PAD + (last - top) * row + PAD / 2;
            DrawerHelper.drawSolidRect(graphics, x + 1, ruleY, width - 2, 1, RULE);
            draw(context, footer, x + TEXT_INSET, ruleY + 1 + (row - 9 * scale) / 2, FederationTheme.DARK_TEXT);
        }
        context.graphics.flush();
        context.disableScissor();
    }

    /** The text, or as much of it as fits in {@code room} followed by an ellipsis. */
    @OnlyIn(Dist.CLIENT)
    private String fit(String text, float room) {
        if (width(text) <= room) return text;
        int end = text.length();
        while (end > 0 && width(text.substring(0, end) + "...") > room) end--;
        return text.substring(0, end) + "...";
    }

    @OnlyIn(Dist.CLIENT)
    private void draw(GUIContext context, String text, float x, float y, int color) {
        context.pose.pushPose();
        context.pose.translate(x, y, 0);
        context.pose.scale(scale, scale, 1);
        LDLibFonts.drawText(context.graphics, font, text(text), 0, 0, color, false);
        context.pose.popPose();
    }

    /** The text under the rows; for tests. */
    public String footerText() {
        return footer();
    }

    /** The candidates' labels, for tests. */
    public List<String> labels() {
        return completions == null ? List.of() : completions.candidates().stream().map(Candidate::label).toList();
    }
}
