package space.controlnet.ae2federation.client.guide;

import com.mojang.math.Axis;
import guideme.document.LytRect;
import guideme.document.block.LytBlock;
import guideme.document.interaction.GuideTooltip;
import guideme.document.interaction.InteractiveElement;
import guideme.document.interaction.TextTooltip;
import guideme.layout.LayoutContext;
import guideme.render.RenderContext;
import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import space.controlnet.ae2federation.client.guide.TopologyDiagram.Capability;
import space.controlnet.ae2federation.client.guide.TopologyDiagram.State;
import space.controlnet.ae2federation.client.guide.TopologyDiagramLayout.Rect;
import space.controlnet.ae2federation.client.menu.FederationTheme;
import space.controlnet.ae2federation.client.policy.TopologyLink;

/**
 * Draws a {@link TopologyDiagram} in the topology screen's style: network cards on the dark canvas, links between
 * them, the quartz rail and its beads where a pair shares energy, a label per direction pointing at the network that
 * uses it, and teal dots moving from the providing network to the user along every link with a rule. GuideME draws
 * the page every frame, so the dots and beads move. Hovering a card, chip or energy chip names it.
 */
public final class LytFederationTopology extends LytBlock implements InteractiveElement {
    private static final long PULSE_PERIOD = 1500;
    private static final long BEAD_PERIOD = 3200;
    private static final int PULSE_DOTS = 3;
    private static final int BEADS = 4;
    private static final int GRID = 0xff2a2733;
    private static final int CARD = 0xff2b2836;
    private static final int RAIL_OUTLINE = 0xff121016;
    private static final int DOT_OUTLINE = 0xff0b0a12;

    private final TopologyDiagram diagram;
    private TopologyDiagramLayout layout;

    public LytFederationTopology(TopologyDiagram diagram) {
        this.diagram = diagram;
    }

    @Override
    protected LytRect computeLayout(LayoutContext context, int x, int y, int availableWidth) {
        var font = Minecraft.getInstance().font;
        layout = TopologyDiagramLayout.of(diagram, availableWidth, font::width, capability(Capability.STORAGE).getString(),
                capability(Capability.CRAFTING).getString(), energyName().getString());
        return new LytRect(x, y, layout.width(), layout.height());
    }

    @Override
    protected void onLayoutMoved(int deltaX, int deltaY) {
    }

    @Override
    public void renderBatch(RenderContext context, MultiBufferSource buffers) {
    }

    @Override
    public void render(RenderContext context) {
        if (layout == null) return;
        var graphics = context.guiGraphics();
        var font = Minecraft.getInstance().font;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(bounds.x(), bounds.y(), 0);
        canvas(graphics);
        long now = System.currentTimeMillis();
        for (var link : layout.links()) {
            if (link.sharesEnergy()) {
                curve(graphics, link.curve(), 5, RAIL_OUTLINE);
                curve(graphics, link.curve(), 3, FederationTheme.QUARTZ);
                curve(graphics, link.curve(), 1, FederationTheme.QUARTZ_CORE);
            } else {
                curve(graphics, link.curve(), 2, FederationTheme.EDGE);
            }
        }
        for (var link : layout.links()) {
            if (link.sharesEnergy()) beads(graphics, link, now);
            pulses(graphics, link, now);
        }
        for (var link : layout.links()) {
            if (link.sharesEnergy()) chip(graphics, font, link.energyChip(), energyName().getString(), FederationTheme.QUARTZ);
            for (var label : link.labels()) label(graphics, font, label);
        }
        for (var card : layout.cards()) card(graphics, font, card);
        legend(graphics, font);
        pose.popPose();
        // GuideME 21.1.1 does not flush before it changes the scissor; keep this page's drawing inside its clip.
        graphics.flush();
    }

    private void canvas(GuiGraphics graphics) {
        int width = layout.width();
        int height = layout.height();
        graphics.fill(0, 0, width, height, FederationTheme.CANVAS);
        for (int x = 16; x < width; x += 32) graphics.fill(x, 1, x + 1, height - 1, GRID);
        for (int y = 16; y < height; y += 32) graphics.fill(1, y, width - 1, y + 1, GRID);
        graphics.renderOutline(0, 0, width, height, FederationTheme.OUTLINE);
    }

    /** The curve stamped with {@code size}-pixel squares a pixel apart. */
    private static void curve(GuiGraphics graphics, TopologyLink link, int size, int color) {
        var points = link.curve().points(32);
        float half = size / 2f;
        for (int index = 2; index < points.length; index += 2) {
            float ax = points[index - 2];
            float ay = points[index - 1];
            float dx = points[index] - ax;
            float dy = points[index + 1] - ay;
            int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy))));
            for (int step = 0; step <= steps; step++) {
                int x = Math.round(ax + dx * step / steps - half);
                int y = Math.round(ay + dy * step / steps - half);
                graphics.fill(x, y, x + size, y + size, color);
            }
        }
    }

    /** Teal dots from the providing network to the user, one stream per direction that has a rule. */
    private static void pulses(GuiGraphics graphics, TopologyDiagramLayout.Link link, long now) {
        float phase = (now % PULSE_PERIOD) / (float) PULSE_PERIOD;
        float halfWidth = link.sharesEnergy() ? link.energyChip().width() / 2f + 3 : 0;
        float halfHeight = link.sharesEnergy() ? link.energyChip().height() / 2f + 3 : 0;
        for (var label : link.labels()) {
            // The label belongs to the user; the dots run from the other end towards it.
            boolean towardsStart = label.chips().getFirst().rule().user().equals(link.from().key());
            var dots = link.curve().dots(phase, PULSE_DOTS, towardsStart, halfWidth, halfHeight);
            for (int index = 0; index < dots.length; index += 2) {
                int x = Math.round(dots[index]);
                int y = Math.round(dots[index + 1]);
                graphics.fill(x - 3, y - 3, x + 3, y + 3, DOT_OUTLINE);
                graphics.fill(x - 2, y - 2, x + 2, y + 2, FederationTheme.TEAL);
            }
        }
    }

    /** Quartz beads both ways along a shared-energy rail, as on the topology screen. */
    private static void beads(GuiGraphics graphics, TopologyDiagramLayout.Link link, long now) {
        float phase = (now % BEAD_PERIOD) / (float) BEAD_PERIOD;
        var pose = graphics.pose();
        var chip = link.energyChip();
        for (int bead = 0; bead < BEADS; bead++) {
            float t = (phase + bead / (float) BEADS) % 1f;
            var point = link.curve().curve().at(bead % 2 == 0 ? t : 1 - t);
            if (chip.contains(point[0], point[1]) || new Rect(chip.x() - 3, chip.y() - 3, chip.width() + 6,
                    chip.height() + 6).contains(point[0], point[1])) continue;
            pose.pushPose();
            pose.translate(point[0], point[1], 0);
            pose.mulPose(Axis.ZP.rotationDegrees(45));
            graphics.fill(-3, -3, 3, 3, FederationTheme.QUARTZ_BEAD_EDGE);
            graphics.fill(-2, -2, 2, 2, FederationTheme.QUARTZ_BEAD);
            pose.popPose();
        }
    }

    private static void label(GuiGraphics graphics, Font font, TopologyDiagramLayout.Label label) {
        var rect = label.rect();
        int x = rect.x() + (label.capX() < 0 ? TopologyDiagramLayout.CAP : 0);
        int y = rect.y() + (label.capY() < 0 ? TopologyDiagramLayout.CAP : 0);
        int width = rect.width() - (label.capX() != 0 ? TopologyDiagramLayout.CAP : 0);
        int height = rect.height() - (label.capY() != 0 ? TopologyDiagramLayout.CAP : 0);
        int border = stateColor(label.chips().getFirst().rule().state());
        graphics.fill(x, y, x + width, y + height, FederationTheme.WELL);
        graphics.renderOutline(x, y, width, height, border);
        // The cap: a small arrow on the side of the network that uses these capabilities.
        int cap = TopologyDiagramLayout.CAP;
        if (label.capX() != 0) {
            int tipX = label.capX() < 0 ? rect.x() : rect.x() + rect.width() - 1;
            int middle = y + height / 2;
            for (int step = 0; step < cap; step++) {
                int columnX = tipX - label.capX() * step;
                graphics.fill(columnX, middle - step, columnX + 1, middle + step + 1, border);
            }
        } else if (label.capY() != 0) {
            int tipY = label.capY() < 0 ? rect.y() : rect.y() + rect.height() - 1;
            int middle = x + width / 2;
            for (int step = 0; step < cap; step++) {
                int rowY = tipY - label.capY() * step;
                graphics.fill(middle - step, rowY, middle + step + 1, rowY + 1, border);
            }
        }
        for (var chip : label.chips()) chip(graphics, font, chip.rect(), chip.text(), stateColor(chip.rule().state()));
    }

    private static void chip(GuiGraphics graphics, Font font, Rect rect, String text, int color) {
        graphics.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), FederationTheme.WELL);
        graphics.renderOutline(rect.x(), rect.y(), rect.width(), rect.height(), color);
        if (font.width(text) > rect.width() - 4) {
            graphics.fill(rect.x() + 3, rect.y() + 4, rect.x() + rect.width() - 3, rect.y() + rect.height() - 4, color);
            return;
        }
        int textX = rect.x() + (rect.width() - font.width(text)) / 2;
        graphics.drawString(font, text, textX, rect.y() + 2, color, false);
    }

    private static void card(GuiGraphics graphics, Font font, TopologyDiagramLayout.Card card) {
        var rect = card.rect();
        int accent = 0xff000000 | card.network().color();
        graphics.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), CARD);
        graphics.renderOutline(rect.x(), rect.y(), rect.width(), rect.height(), accent);
        graphics.fill(rect.x() + 1, rect.y() + rect.height() - 3, rect.x() + rect.width() - 1, rect.y() + rect.height() - 1,
                FederationTheme.OK);
        graphics.fill(rect.x() + 4, rect.y() + 4, rect.x() + 8, rect.y() + 8, accent);
        int textWidth = rect.width() - 14;
        graphics.drawString(font, fit(font, card.network().label(), textWidth), rect.x() + 10, rect.y() + 2,
                FederationTheme.DARK_TITLE, false);
        int y = rect.y() + 13;
        for (var detail : card.network().details()) {
            graphics.drawString(font, fit(font, detail, rect.width() - 8), rect.x() + 4, y, FederationTheme.DARK_MUTED, false);
            y += TopologyDiagramLayout.LINE_HEIGHT;
        }
    }

    /** "■ active ■ energy shared" for the states this diagram uses, over "▸ points to the network that uses it". */
    private void legend(GuiGraphics graphics, Font font) {
        var states = EnumSet.noneOf(State.class);
        diagram.rules().forEach(rule -> states.add(rule.state()));
        var line = Component.empty();
        for (var state : states) {
            if (!line.getSiblings().isEmpty()) line.append("  ");
            line.append(swatch(legendKey(state), stateColor(state)));
        }
        if (!diagram.energy().isEmpty()) {
            if (!line.getSiblings().isEmpty()) line.append("  ");
            line.append(swatch("energy", FederationTheme.QUARTZ));
        }
        int y = layout.legendY();
        graphics.drawString(font, line, TopologyDiagramLayout.PAD, y, FederationTheme.DARK_TEXT, false);
        if (!diagram.rules().isEmpty()) {
            graphics.drawString(font, topology("legend.reads"), TopologyDiagramLayout.PAD, y + TopologyDiagramLayout.LINE_HEIGHT + 2,
                    FederationTheme.DARK_MUTED, false);
        }
    }

    @Override
    public Optional<GuideTooltip> getTooltip(float x, float y) {
        if (layout == null) return Optional.empty();
        float px = x - bounds.x();
        float py = y - bounds.y();
        for (var link : layout.links()) {
            for (var label : link.labels()) {
                for (var chip : label.chips()) {
                    if (!chip.rect().contains(px, py)) continue;
                    var rule = chip.rule();
                    return Optional.of(new TextTooltip(topology("uses", name(rule.user()), name(rule.source()))
                            .append(" ").append(capability(rule.capability())), topology("legend." + legendKey(rule.state()))
                            .withStyle(Style.EMPTY.withColor(stateColor(rule.state()) & 0xffffff))));
                }
            }
            if (link.sharesEnergy() && link.energyChip().contains(px, py)) {
                return Optional.of(new TextTooltip(topology("energy_section", link.from().label(), link.to().label())));
            }
        }
        for (var card : layout.cards()) {
            if (!card.rect().contains(px, py)) continue;
            var lines = new java.util.ArrayList<Component>();
            lines.add(Component.literal(card.network().label()));
            card.network().details().forEach(detail -> lines.add(Component.literal(detail).withStyle(
                    Style.EMPTY.withColor(FederationTheme.DARK_MUTED & 0xffffff))));
            return Optional.of(new TextTooltip(lines));
        }
        return Optional.empty();
    }

    private String name(String key) {
        return diagram.network(key).map(TopologyDiagram.Network::label).orElse(key);
    }

    private static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }

    private static MutableComponent swatch(String key, int color) {
        return Component.literal("■ ").append(topology("legend." + key)).withStyle(Style.EMPTY.withColor(color & 0xffffff));
    }

    private static String legendKey(State state) {
        return switch (state) {
            case ACTIVE -> "active";
            case REEXPORT -> "reexport";
            case WAITING -> "waiting";
            case ERROR -> "error";
        };
    }

    private static int stateColor(State state) {
        return switch (state) {
            case ACTIVE -> FederationTheme.OK;
            case REEXPORT -> FederationTheme.REEXPORT;
            case WAITING -> FederationTheme.WARN;
            case ERROR -> FederationTheme.ERROR;
        };
    }

    private static MutableComponent capability(Capability capability) {
        return Component.translatable("ae2federation.ui.workspace.capability." + capability.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static MutableComponent energyName() {
        return topology("energy");
    }

    private static MutableComponent topology(String key, Object... arguments) {
        return Component.translatable("ae2federation.ui.topology." + key, arguments);
    }
}
