package space.controlnet.ae2federation.client.guide;

import guideme.document.LytRect;
import guideme.document.block.LytBlock;
import guideme.document.interaction.InteractiveElement;
import guideme.layout.LayoutContext;
import guideme.render.RenderContext;
import guideme.ui.GuideUiHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.client.menu.ProgramGraph;

/**
 * A program on a guide page, as the board {@code GuideCodeA} draws it: the script editor's dark panel with its Script
 * and Graph pair at the top right, showing the script in the editor's colours or a function's graph as the Automation
 * tab draws it. A click on the pair switches the view. GuideME cannot lay a page out again after a click, so the
 * block is as tall as the taller view; a graph wider than the page is drawn smaller to fit.
 */
public final class LytFederationProgram extends LytBlock implements InteractiveElement {
    private static final int PANEL = 0xff201e27;
    private static final int PANEL_EDGE = 0xff121016;
    private static final int ON_FACE = 0xff2c2735;
    private static final int ON_EDGE = 0xff9cd3ff;
    private static final int ON_TEXT = 0xffefeaf8;
    private static final int OFF_FACE = 0xff17141e;
    private static final int OFF_EDGE = 0xff4a4655;
    private static final int OFF_TEXT = 0xffaaa4b2;
    /** The pair's buttons, their place from the block's top right, and where the views start below them. */
    private static final int BUTTON_WIDTH = 36;
    private static final int BUTTON_HEIGHT = 12;
    private static final int SCRIPT_RIGHT = 78;
    private static final int GRAPH_RIGHT = 40;
    private static final int BUTTON_TOP = 3;
    private static final int BODY = 18;
    /** The script's lines: their left, their pitch, and the room above the first and below the last. */
    private static final int LINE_LEFT = 6;
    private static final int LINE = 12;
    private static final int LINE_PAD = 4;
    /** The graph's room on each side, enough for the pins drawn outside a card's edge. */
    private static final int GRAPH_INSET = 4;

    private final GuideProgram program;
    private @Nullable ProgramGraph graph;
    private boolean graphShown;
    private float scriptScale = 1;
    private float graphScale = 1;

    public LytFederationProgram(GuideProgram program) {
        this.program = program;
    }

    @Override
    protected LytRect computeLayout(LayoutContext context, int x, int y, int availableWidth) {
        var font = Minecraft.getInstance().font;
        // Measured here, not when the page is compiled: the font may not be loaded then.
        if (graph == null) graph = ProgramGraph.of(program.graph());
        int widest = 0;
        for (var line : program.lines()) {
            int width = 0;
            for (var run : line) width += font.width(run.text());
            widest = Math.max(widest, width);
        }
        scriptScale = Math.min(1f, (availableWidth - 2f * LINE_LEFT) / Math.max(1, widest));
        int scriptHeight = (int) Math.ceil((2 * LINE_PAD + LINE * program.lines().size()) * scriptScale);
        graphScale = Math.min(1f, (availableWidth - 2f * GRAPH_INSET) / graph.width());
        int graphHeight = (int) Math.ceil(graph.height() * graphScale) + GRAPH_INSET;
        return new LytRect(x, y, availableWidth, BODY + Math.max(scriptHeight, graphHeight));
    }

    @Override
    protected void onLayoutMoved(int deltaX, int deltaY) {
    }

    @Override
    public void renderBatch(RenderContext context, MultiBufferSource buffers) {
    }

    @Override
    public void render(RenderContext context) {
        if (graph == null) return;
        var graphics = context.guiGraphics();
        var font = Minecraft.getInstance().font;
        int x = bounds.x();
        int y = bounds.y();
        graphics.fill(x, y, bounds.right(), bounds.bottom(), PANEL_EDGE);
        graphics.fill(x + 1, y + 1, bounds.right() - 1, bounds.bottom() - 1, PANEL);
        button(graphics, scriptButton(), Component.translatable("ae2federation.ui.automation.script"),
                !graphShown);
        button(graphics, graphButton(), Component.translatable("ae2federation.ui.automation.graph"), graphShown);
        var pose = graphics.pose();
        pose.pushPose();
        if (graphShown) {
            pose.translate(x + GRAPH_INSET, y + BODY, 0);
            pose.scale(graphScale, graphScale, 1);
            graph.paint(graphics, 0, 0);
        } else {
            pose.translate(x + LINE_LEFT, y + BODY, 0);
            pose.scale(scriptScale, scriptScale, 1);
            int lineY = LINE_PAD;
            for (var line : program.lines()) {
                int lineX = 0;
                for (var run : line) {
                    graphics.drawString(font, run.text(), lineX, lineY, 0xff000000 | run.color(), false);
                    lineX += font.width(run.text());
                }
                lineY += LINE;
            }
        }
        pose.popPose();
        // GuideME 21.1.1 does not flush before it changes the scissor; keep this page's drawing inside its clip.
        graphics.flush();
    }

    private LytRect scriptButton() {
        return new LytRect(bounds.right() - SCRIPT_RIGHT, bounds.y() + BUTTON_TOP, BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    private LytRect graphButton() {
        return new LytRect(bounds.right() - GRAPH_RIGHT, bounds.y() + BUTTON_TOP, BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    /** One of the pair, as the editor draws it: the shown view's with a light blue edge. */
    private static void button(GuiGraphics graphics, LytRect rect, Component label, boolean on) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), on ? ON_EDGE : OFF_EDGE);
        graphics.fill(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.bottom() - 1, on ? ON_FACE : OFF_FACE);
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, label, rect.x() + (rect.width() - font.width(label)) / 2, rect.y() + 2,
                on ? ON_TEXT : OFF_TEXT, false);
    }

    @Override
    public boolean mouseClicked(GuideUiHost screen, int x, int y, int button) {
        if (button != 0) return false;
        if (scriptButton().contains(x, y)) {
            graphShown = false;
            return true;
        }
        if (graphButton().contains(x, y)) {
            graphShown = true;
            return true;
        }
        return false;
    }
}
