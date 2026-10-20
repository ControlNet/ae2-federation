package space.controlnet.ae2federation.automation.neoeco;

import appeng.client.Point;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.Scrollbar;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.crafting.display.format.NEByteFormatter;
import cn.dancingsnow.neoecoae.gui.common.GuideButton;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.gui.multiblock.MultiblockBuilderUI;
import cn.dancingsnow.neoecoae.gui.theme.NEStyleSheets;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.SyncStrategy;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;
import space.controlnet.ae2federation.automation.AutomationInterfaceBook;
import space.controlnet.ae2federation.automation.ComputingDevices;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.automation.Installation;
import space.controlnet.ae2federation.automation.ProgramScheduler;
import space.controlnet.ae2federation.automation.ProgramSync;
import space.controlnet.ae2federation.persistence.NetworkNameBook;

/**
 * The controller screen of an ECO - N system: Neo ECO's computation controller screen, its atlas, side buttons, guide
 * and Structure Builder, with the crafting CPU readouts swapped for the device's (the board "ECO - N4 option A", user
 * decisions 2026-10-20). The left box shows the ID with a copy button, the alias, compute, bandwidth and installations,
 * used against what the device holds, and the selected installation below; the right list shows this device's
 * installations, read only. Neo ECO's CPU mode, frequency and planner buttons are left out.
 */
final class EcoComputationPanel extends BindableValue<CompoundTag> {
    static final int WIDTH = 261, HEIGHT = 214, ROWS = 8;
    private static final int LIST_X = 176, LIST_Y = 21, ROW_WIDTH = 67, ROW_HEIGHT = 22, ROW_STRIDE = 23;
    private static final int TRACK_X = 245, TRACK_Y = 21, TRACK_HEIGHT = 183, TRACK_WIDTH = 12;
    private static final int COPY_X = 141, COPY_Y = 25, COPY_WIDTH = 21, COPY_HEIGHT = 10;
    private static final int ALIAS_X = 96, ALIAS_Y = 37, ALIAS_WIDTH = 67, ALIAS_HEIGHT = 11;
    /** The left box's rows, 12 apart as Neo ECO's, and the line under them. */
    private static final int ID_Y = 27, ALIAS_ROW_Y = 39, COMPUTE_Y = 51, BANDWIDTH_Y = 63, INSTALLATIONS_Y = 75,
            DIVIDER_Y = 84;
    /** The system's page, from the guide pack that loads with Neo ECO, as this screen does. */
    private static final String GUIDE_PAGE = "ae2federation:items/eco_computation_system.md";
    private static final IGuiTexture BACKGROUND = sprite(0, 0, WIDTH, HEIGHT);
    private static final IGuiTexture NORMAL = sprite(0, 222, ROW_WIDTH, ROW_HEIGHT);
    private static final IGuiTexture SELECTED = sprite(69, 222, ROW_WIDTH, ROW_HEIGHT);

    private final EcoComputationSystemBlockEntity device;
    private final TextField alias = new TextField();
    private final Scroll scroll;
    private List<Row> rows = List.of();
    /** The selected installation's ID, kept on the client only: the list is read only. */
    private String selected = "";
    private int offset;
    private String sent = "";
    /** The alias the server last showed, and one waiting to be put in the field once the field has its width. */
    private String shownAlias;
    private String pendingAlias;

    private EcoComputationPanel(EcoComputationSystemBlockEntity device, Player player) {
        super(new CompoundTag());
        this.device = device;
        layout(layout -> layout.width(WIDTH).height(HEIGHT).paddingAll(0));
        style(style -> style.backgroundTexture(BACKGROUND));
        setOverflowVisible(true);
        var server = new ServerState(device, player);
        bind(DataBindingBuilder.create(server::snapshot, ignored -> {
                })
                .syncType(CompoundTag.class).c2sStrategy(SyncStrategy.NONE).build());
        addChild(aliasRequest(server));
        for (int row = 0; row < ROWS; row++) addChild(rowHitbox(row));
        var copy = hitbox(COPY_X, COPY_Y, COPY_WIDTH, COPY_HEIGHT);
        copy.addEventListener(UIEvents.MOUSE_UP, event -> {
            if (event.button != 0 || getValue().getString("id").isEmpty()) return;
            Minecraft.getInstance().keyboardHandler.setClipboard(getValue().getString("id"));
            event.stopImmediatePropagation();
        });
        addChild(copy);
        HostElements.absolute(alias, ALIAS_X + 1, ALIAS_Y + 1, ALIAS_WIDTH - 2, ALIAS_HEIGHT - 2);
        alias.layout(layout -> layout.paddingAll(0).paddingLeft(2));
        alias.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        alias.textFieldStyle(style -> style.textShadow(false).textColor(HostText.VALUE).fontSize(8)
                .placeholder(Component.empty()).focusOverlay(IGuiTexture.EMPTY));
        alias.setAnyString();
        alias.setTextValidator(text -> text.codePointCount(0, text.length()) <= NetworkNameBook.MAX_CODE_POINTS);
        alias.addEventListener(UIEvents.KEY_DOWN, event -> {
            if (event.keyCode == GLFW.GLFW_KEY_ENTER || event.keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                send();
                alias.blur();
            }
        });
        alias.addEventListener(UIEvents.BLUR, event -> send());
        alias.setTextResponder(text -> paintAlias());
        addChild(alias);
        scroll = new Scroll(this, TRACK_X, TRACK_Y, TRACK_HEIGHT, this::scrollTo);
        addChild(scroll);
        addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (event.x < getPositionX() + LIST_X || event.x >= getPositionX() + TRACK_X + TRACK_WIDTH
                    || event.y < getPositionY() + LIST_Y || event.y >= getPositionY() + TRACK_Y + TRACK_HEIGHT) return;
            scrollTo(offset + (event.deltaY < 0 ? 2 : -2));
            event.stopImmediatePropagation();
        }, true);
    }

    /** The whole screen, built on both sides as Neo ECO's controller builds its own. */
    static ModularUI create(EcoComputationSystemBlockEntity device, BlockUIMenuType.BlockUIHolder holder,
            UIElement buildWindow) {
        var player = holder.player;
        var root = new UIElement().layout(layout -> layout.width(WIDTH).height(HEIGHT).paddingAll(0));
        root.setOverflowVisible(true);
        var main = new EcoComputationPanel(device, player);
        var inventory = new InventorySlots();
        inventory.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(7).top(129).width(162).height(77));
        inventory.hotbar.layout(layout -> layout.marginTop(4));
        inventory.apply(slot -> slot.style(style -> style.backgroundTexture(IGuiTexture.EMPTY)));
        inventory.getChildren().forEach(child -> child.style(style -> style.backgroundTexture(IGuiTexture.EMPTY)));
        main.addChild(inventory);
        main.addChildren(HostSideButtonBar.left(GuideButton.create(player, GUIDE_PAGE),
                MultiblockBuilderUI.createInlineOpenButton(buildWindow)), buildWindow);
        root.addChild(main);
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))),
                player);
    }

    @Override
    public EcoComputationPanel setValue(CompoundTag value, boolean notify) {
        super.setValue(value, notify);
        if (value == null || !value.contains("installations")) return this;
        rows = value.getList("rows", Tag.TAG_COMPOUND).stream().map(tag -> Row.read((CompoundTag) tag)).toList();
        if (rows.stream().noneMatch(row -> row.id().equals(selected))) {
            selected = rows.isEmpty() ? "" : rows.get(0).id();
        }
        scrollTo(offset);
        // Only a change on the server replaces what the player typed, so a refused alias stays to be corrected.
        var serverAlias = value.getString("alias");
        if (!serverAlias.equals(shownAlias)) {
            shownAlias = serverAlias;
            if (!alias.isFocused()) pendingAlias = serverAlias;
        }
        paintAlias();
        return this;
    }

    /** A refused alias stays in the field, in red, until the player changes it. */
    private void paintAlias() {
        var refused = getValue().getString("refused");
        var red = !refused.isEmpty() && refused.equals(alias.getText());
        alias.textFieldStyle(style -> style.textColor(red ? HostText.ERROR : HostText.VALUE));
    }

    private String[] asked = {""};
    private int count;

    private void send() {
        var text = alias.getText();
        if (!text.equals(sent)) {
            sent = text;
            asked[0] = ++count + ":" + text;
        }
    }

    /**
     * The alias the player asks for, as {@code "<number>:<alias>"}, numbered per request so the server applies each
     * once, as the Provider screen's requests travel: a C2S-only binding would abort reading the menu's initial data.
     */
    private BindableValue<String> aliasRequest(ServerState server) {
        var value = new BindableValue<String>("");
        value.bind(DataBindingBuilder.string(server::applied, server::request)
                .initialValue("")
                .remoteGetter(() -> asked[0])
                .remoteSetter(ignored -> {
                })
                .build());
        value.setDisplay(false);
        return value;
    }

    private UIElement rowHitbox(int row) {
        var hitbox = hitbox(LIST_X, LIST_Y + row * ROW_STRIDE, ROW_WIDTH, ROW_HEIGHT);
        hitbox.setId("eco-computation-row-" + row);
        hitbox.addEventListener(UIEvents.MOUSE_DOWN, UIEvent::stopImmediatePropagation);
        hitbox.addEventListener(UIEvents.MOUSE_UP, event -> {
            if (event.button != 0 || offset + row >= rows.size()) return;
            selected = rows.get(offset + row).id();
            event.stopImmediatePropagation();
        });
        return hitbox;
    }

    private void scrollTo(int requested) {
        offset = Math.clamp(requested, 0, Math.max(0, rows.size() - ROWS));
        scroll.update(offset, rows.size() - ROWS, ROWS / 3);
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        // LDLib2 draws the contents itself, so the inventory, side buttons and builder window are drawn too.
        var font = Minecraft.getInstance().font;
        float x = getPositionX(), y = getPositionY();
        var value = getValue();
        if (pendingAlias != null) {
            // Set while drawing: before its first layout the field has no width and would scroll the text out of view.
            alias.setText(pendingAlias, false);
            alias.setCursor(0);
            alias.setCursor(pendingAlias.length());
            sent = pendingAlias;
            pendingAlias = null;
            paintAlias();
        }
        context.graphics.flush();
        text(context, font, device.getBlockState().getBlock().getName().getString(), x + 8, y + 5, 157, 0.85F,
                0x3F3D52);
        var connected = value.getBoolean("connected");
        text(context, font, Component.translatable(connected ? "gui.neoecoae.host.network.connected"
                : "gui.neoecoae.host.network.disconnected").getString(), x + 8, y + 13, 157, 0.6F,
                connected ? HostText.USED : HostText.ERROR);
        text(context, font, Component.translatable("ae2federation.eco_computation.installed", rows.size()).getString(),
                x + LIST_X, y + 7, ROW_WIDTH, 0.75F, 0x3F3D52);

        var id = value.getString("id");
        int idWidth = (int) Math.ceil(font.width(id) * 0.8F);
        text(context, font, Component.translatable("ae2federation.automation_interface.id").getString(), x + 13,
                y + ID_Y, 40, 0.8F, HostText.MUTED);
        text(context, font, id, x + COPY_X - 2 - idWidth, y + ID_Y, idWidth, 0.8F, HostText.VALUE);
        copyButton(context, font, x + COPY_X, y + COPY_Y);
        text(context, font, Component.translatable("ae2federation.automation_interface.alias").getString(), x + 13,
                y + ALIAS_ROW_Y, ALIAS_X - 15, 0.8F, HostText.MUTED);
        context.graphics.fill(Math.round(x + ALIAS_X), Math.round(y + ALIAS_Y), Math.round(x + ALIAS_X + ALIAS_WIDTH),
                Math.round(y + ALIAS_Y + ALIAS_HEIGHT), 0xFF4A4655);
        context.graphics.fill(Math.round(x + ALIAS_X + 1), Math.round(y + ALIAS_Y + 1),
                Math.round(x + ALIAS_X + ALIAS_WIDTH - 1), Math.round(y + ALIAS_Y + ALIAS_HEIGHT - 1), 0xFF17141E);
        metric(context, font, "compute", HostText.ae2Amount(value.getLong("computeUsed")) + " / "
                + HostText.ae2Amount(value.getLong("compute")) + " CU", x, y + COMPUTE_Y);
        metric(context, font, "bandwidth", NEByteFormatter.format(value.getLong("bandwidthUsed")) + "/s / "
                + NEByteFormatter.format(value.getLong("bandwidth")) + "/s", x, y + BANDWIDTH_Y);
        metric(context, font, "installations", HostText.ae2Amount(rows.size()) + " / "
                + HostText.ae2Amount(value.getLong("installations")), x, y + INSTALLATIONS_Y);
        context.graphics.fill((int) x + 13, (int) y + DIVIDER_Y, (int) x + 162, (int) y + DIVIDER_Y + 1, 0xFF484252);
        var chosen = rows.stream().filter(row -> row.id().equals(selected)).findFirst().orElse(null);
        if (chosen == null) {
            text(context, font, Component.translatable("ae2federation.eco_computation.none").getString(), x + 13,
                    y + DIVIDER_Y + 8, 149, 0.85F, HostText.MUTED);
        } else {
            var status = Component.translatable(chosen.running() ? "ae2federation.ui.automation.running"
                    : "ae2federation.ui.automation.idle").getString();
            var name = chosen.function() + " · ";
            text(context, font, name, x + 13, y + DIVIDER_Y + 4, 149, 0.85F, HostText.PRIMARY);
            text(context, font, status, x + 13 + font.width(name) * 0.85F, y + DIVIDER_Y + 4, 149, 0.85F,
                    chosen.running() ? HostText.USED : HostText.MUTED);
            var trigger = chosen.trigger().getString();
            int triggerWidth = (int) Math.ceil(font.width(trigger) * 0.8F);
            text(context, font, Component.translatable("ae2federation.ui.automation.trigger").getString(), x + 13,
                    y + DIVIDER_Y + 15, Math.max(0, 145 - triggerWidth), 0.8F, HostText.MUTED);
            text(context, font, trigger, x + 162 - triggerWidth, y + DIVIDER_Y + 15, triggerWidth, 0.8F,
                    HostText.PRIMARY);
            text(context, font, chosen.compute() + " CU · " + NEByteFormatter.format(chosen.bandwidth()) + "/s",
                    x + 13, y + DIVIDER_Y + 27, 149, 0.8F, HostText.VALUE);
        }
        for (int row = 0; row < ROWS && offset + row < rows.size(); row++) {
            drawRow(context, font, rows.get(offset + row), x + LIST_X, y + LIST_Y + row * ROW_STRIDE);
        }
    }

    /** As Neo ECO draws a CPU at rest: the name, then compute by the processor icon and bandwidth by the storage icon. */
    private void drawRow(GUIContext context, Font font, Row row, float x, float y) {
        context.drawTexture(row.id().equals(selected) ? SELECTED : NORMAL, x, y, ROW_WIDTH, ROW_HEIGHT);
        context.graphics.flush();
        int color = 0x413F54;
        text(context, font, row.function(), x + 3, y + 2, 52, 0.666F, color);
        icon(context, Icon.S_PROCESSOR, x + 2, y + 9);
        fittedText(context, font, HostText.ae2Amount(row.compute()), x + 14, y + 13, 12, 0.666F, color);
        icon(context, Icon.S_STORAGE, x + 27, y + 9);
        fittedText(context, font, NEByteFormatter.format(row.bandwidth()) + "/s", x + 39, y + 13, 26, 0.666F, color);
    }

    /** A small button in AE2's colours, as the board draws it. */
    private static void copyButton(GUIContext context, Font font, float x, float y) {
        var graphics = context.graphics;
        int left = Math.round(x), top = Math.round(y), right = left + COPY_WIDTH, bottom = top + COPY_HEIGHT;
        graphics.fill(left, top, right, bottom, 0xFF413F54);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, 0xFFADB0C4);
        graphics.fill(left + 2, top + 2, right - 2, bottom - 1, 0xFF9A9FB4);
        graphics.fill(left + 1, bottom - 3, right - 1, bottom - 1, 0xFF696D88);
        var label = Component.translatable("ae2federation.automation_interface.copy").getString();
        float scale = 0.6F;
        text(context, font, label, x + (COPY_WIDTH - font.width(label) * scale) / 2, y + 2, COPY_WIDTH, scale,
                0x413F54);
    }

    private static void metric(GUIContext context, Font font, String metric, String value, float x, float y) {
        var label = Component.translatable("ae2federation.eco_computation." + metric).getString();
        float scale = 0.8F;
        int valueWidth = (int) Math.ceil(font.width(value) * scale);
        text(context, font, label, x + 13, y, Math.max(0, 145 - valueWidth), scale, HostText.MUTED);
        text(context, font, value, x + 162 - valueWidth, y, valueWidth, scale, HostText.VALUE);
    }

    private static void text(GUIContext context, Font font, String value, float x, float y, int width, float scale,
            int color) {
        context.graphics.pose().pushPose();
        context.graphics.pose().translate(x, y, 0);
        context.graphics.pose().scale(scale, scale, 1);
        context.graphics.drawString(font, font.plainSubstrByWidth(value, Math.max(0, (int) (width / scale))), 0, 0,
                0xFF000000 | color, false);
        context.graphics.pose().popPose();
    }

    private static void fittedText(GUIContext context, Font font, String value, float x, float y, int width,
            float scale, int color) {
        float fitted = Math.min(scale, width / (float) Math.max(1, font.width(value)));
        context.graphics.pose().pushPose();
        context.graphics.pose().translate(x, y + (scale - fitted) * font.lineHeight / 2, 0);
        context.graphics.pose().scale(fitted, fitted, 1);
        context.graphics.drawString(font, value, 0, 0, 0xFF000000 | color, false);
        context.graphics.pose().popPose();
    }

    private static void icon(GUIContext context, Icon icon, float x, float y) {
        context.graphics.pose().pushPose();
        context.graphics.pose().translate(x, y, 0);
        icon.getBlitter().dest(0, 0).blit(context.graphics);
        context.graphics.pose().popPose();
    }

    private static IGuiTexture sprite(int x, int y, int width, int height) {
        return SpriteTexture.of(NeoECOAE.id("textures/gui/eco_cpu_controller.png")).setSprite(x, y, width, height);
    }

    private static UIElement hitbox(int x, int y, int width, int height) {
        return new UIElement().layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(x).top(y)
                .width(width).height(height));
    }

    /** An installation as the list and the box below the readouts show it. */
    private record Row(String id, String function, Component trigger, boolean running, long compute, long bandwidth) {
        private CompoundTag write() {
            var tag = new CompoundTag();
            tag.putString("id", id);
            tag.putString("function", function);
            tag.putString("trigger", Component.Serializer.toJson(trigger,
                    net.minecraft.core.RegistryAccess.EMPTY));
            tag.putBoolean("running", running);
            tag.putLong("compute", compute);
            tag.putLong("bandwidth", bandwidth);
            return tag;
        }

        private static Row read(CompoundTag tag) {
            var trigger = Component.Serializer.fromJson(tag.getString("trigger"), net.minecraft.core.RegistryAccess.EMPTY);
            return new Row(tag.getString("id"), tag.getString("function"),
                    trigger == null ? Component.empty() : trigger, tag.getBoolean("running"), tag.getLong("compute"),
                    tag.getLong("bandwidth"));
        }
    }

    /** A trigger as the Automation tab names it. */
    private static Component trigger(Installation.Trigger trigger) {
        return switch (trigger) {
            case Installation.Trigger.Manual manual -> Component.translatable("ae2federation.ui.automation.trigger.manual");
            case Installation.Trigger.Redstone redstone ->
                    Component.translatable("ae2federation.ui.automation.trigger.redstone");
            case Installation.Trigger.Schedule schedule ->
                    Component.translatable("ae2federation.ui.automation.trigger.every").append(" ")
                            .append(Component.translatable("ae2federation.ui.automation.ticks", schedule.ticks()));
            case Installation.Trigger.Condition condition ->
                    Component.translatable("ae2federation.ui.automation.trigger.condition").append(" "
                            + condition.function());
            case Installation.Trigger.Event event ->
                    Component.translatable("ae2federation.ui.automation.trigger.event").append(" " + event.name());
        };
    }

    /** The server's side: what the screen shows, sampled at most every five ticks, and the alias the player sends. */
    private static final class ServerState {
        private final EcoComputationSystemBlockEntity device;
        private final Player player;
        private CompoundTag cached;
        private long sampledTick = Long.MIN_VALUE;
        private String applied = "";
        private long appliedNumber;
        private String refused = "";

        private ServerState(EcoComputationSystemBlockEntity device, Player player) {
            this.device = device;
            this.player = player;
        }

        private String applied() {
            return applied;
        }

        private void request(String text) {
            var separator = text == null ? -1 : text.indexOf(':');
            if (separator <= 0 || !(device.getLevel() instanceof ServerLevel level)
                    || !device.canPlayerInteract(player)) return;
            long number;
            try {
                number = Long.parseLong(text.substring(0, separator));
            } catch (NumberFormatException e) {
                return;
            }
            if (number <= appliedNumber) return;
            appliedNumber = number;
            applied = text;
            var alias = text.substring(separator + 1);
            var result = device.setAlias(level, alias);
            refused = result == AutomationInterfaceBook.AliasResult.TAKEN
                    || result == AutomationInterfaceBook.AliasResult.INVALID ? alias : "";
            if (result == AutomationInterfaceBook.AliasResult.SET) {
                ProgramSync.get(level.getServer()).changed();
            }
            cached = null;
        }

        private CompoundTag snapshot() {
            if (!(device.getLevel() instanceof ServerLevel level)) return new CompoundTag();
            long tick = level.getGameTime();
            if (cached != null && tick >= sampledTick && tick - sampledTick < 5) return cached;
            sampledTick = tick;
            var tag = new CompoundTag();
            var id = device.id();
            var capacity = device.capacity();
            tag.putString("id", id == null ? "" : id);
            tag.putString("alias", device.alias(level));
            tag.putString("refused", refused);
            // A device alone is a domain of its own, with its interfaces: online once the domain reaches further.
            tag.putBoolean("connected", FederationDomainRegistryAccess.get(level)
                    .federationDomainOf(FederationDomainRegistryAccess.nodeId(level, device.getBlockPos()))
                    .filter(domain -> domain.nodes().size() > 1 + device.remotePorts().size()).isPresent());
            tag.putLong("compute", capacity.compute());
            tag.putLong("bandwidth", capacity.bandwidth());
            tag.putLong("installations", capacity.installations());
            var rows = new ListTag();
            long compute = 0, bandwidth = 0;
            if (id != null) {
                var scheduler = ProgramScheduler.get(level.getServer());
                for (var installation : ComputingDevices.get(level).installations()) {
                    if (!installation.device().equals(id)) continue;
                    var cost = scheduler.cost(installation.id());
                    compute = plus(compute, cost.compute());
                    bandwidth = plus(bandwidth, cost.bandwidth());
                    rows.add(new Row(installation.id(), installation.function(), trigger(installation.trigger()),
                            scheduler.running(installation.id()) > 0, cost.compute(), cost.bandwidth()).write());
                }
            }
            tag.putLong("computeUsed", compute);
            tag.putLong("bandwidthUsed", bandwidth);
            tag.put("rows", rows);
            return cached = tag;
        }

        /** A sum that stops at the largest long rather than wrapping round. */
        private static long plus(long a, long b) {
            var sum = a + b;
            return sum < 0 ? Long.MAX_VALUE : sum;
        }
    }

    /** AE2's own scrollbar, as Neo ECO's controller list uses it, driven by LDLib2's mouse events. */
    private static final class Scroll extends UIElement {
        private final Scrollbar scrollbar = new Scrollbar(Scrollbar.BIG);
        private final java.util.function.IntConsumer onScroll;
        private int published;
        private boolean pressed;

        private Scroll(UIElement owner, int x, int y, int height, java.util.function.IntConsumer onScroll) {
            this.onScroll = onScroll;
            HostElements.absolute(this, x, y, TRACK_WIDTH, height);
            scrollbar.setHeight(height).setCaptureMouseWheel(false);
            addEventListener(UIEvents.MOUSE_DOWN, event -> {
                if (event.button != 0) return;
                position();
                pressed = scrollbar.onMouseDown(mouse(event), event.button);
                publish();
                event.stopImmediatePropagation();
            });
            owner.addEventListener(UIEvents.MOUSE_MOVE, event -> {
                if (pressed && scrollbar.onMouseDrag(mouse(event), 0)) {
                    publish();
                    event.stopImmediatePropagation();
                }
            }, true);
            owner.addEventListener(UIEvents.MOUSE_UP, event -> {
                if (event.button == 0 && pressed) {
                    scrollbar.onMouseUp(mouse(event), event.button);
                    pressed = false;
                    event.stopImmediatePropagation();
                }
            }, true);
            addEventListener(UIEvents.TICK, event -> {
                // LDLib2 sends a mouse release only to the hovered element; one outside the panel ends the drag too.
                if (pressed && getModularUI() != null && getModularUI().getLastMouseDownButton() != 0) release();
                scrollbar.tick();
                publish();
            });
        }

        private void update(int value, int max, int pageSize) {
            scrollbar.setRange(0, Math.max(0, max), pageSize);
            scrollbar.setCurrentScroll(value);
            published = scrollbar.getCurrentScroll();
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            position();
            context.graphics.flush();
            scrollbar.drawForegroundLayer(context.graphics, new Rect2i(0, 0, 0, 0),
                    new Point(Math.round(context.mouseX), Math.round(context.mouseY)));
        }

        private void position() {
            scrollbar.setPosition(new Point(Math.round(getPositionX()), Math.round(getPositionY())));
        }

        private void release() {
            scrollbar.onMouseUp(new Point(0, 0), 0);
            pressed = false;
        }

        private void publish() {
            int value = scrollbar.getCurrentScroll();
            if (value != published) {
                published = value;
                onScroll.accept(value);
            }
        }

        private static Point mouse(UIEvent event) {
            return new Point(Math.round(event.x), Math.round(event.y));
        }
    }
}
