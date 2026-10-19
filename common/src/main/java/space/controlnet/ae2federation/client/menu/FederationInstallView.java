package space.controlnet.ae2federation.client.menu;

import com.lowdragmc.lowdraglib2.client.shader.LDLibRenderTypes;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
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
import org.joml.Vector2f;
import space.controlnet.ae2federation.automation.ClientPrograms;
import space.controlnet.ae2federation.automation.ProgramSync;
import space.controlnet.ae2federation.client.policy.WireCurve;

/**
 * The Automation tab's Install view: every program with its public functions on the left, the computing devices of
 * this domain with what is installed on them on the right, and a wire from each installed function to its device in
 * the program's colour ({@code AutoInstall}). A square before an installation says whether its device runs the
 * program's current version (green) or an older one, waiting for Push (yellow).
 *
 * <p>Selecting an installation shows it beside the canvas: its signature, arguments, trigger, rate limit, what a
 * trigger does while it runs and whether it runs, with Uninstall, Open in editor and Run now. Changing its settings
 * and installing by drawing a wire come in a later step.
 */
final class FederationInstallView {
    /** Programs take these colours in the index's order: their squares, ports and wires. */
    private static final int[] PROGRAM_COLORS = {0xff5aa8ff, 0xfff0a530, 0xffb26cff, 0xff26a6bd, 0xff55ff8a,
            0xffff6a75};
    private static final int CARD_FACE = 0xff2a2633;
    private static final int CARD_RING = 0xff4a4655;
    private static final int CARD_SHADOW = 0xff121016;
    private static final int PORT_FILL = 0xff17141e;
    private static final int PORT = 4;

    private final ScrollerView canvasScroll;
    private final ScrollerView aside;
    private final Canvas canvas = new Canvas();
    private final Consumer<ProgramSync.Answer> answer;
    private final BiConsumer<String, String> openInEditor;
    /** The ports wires end at: a function's by {@code file.function}, a device's by its ID. */
    private final Map<String, UIElement> functionPorts = new HashMap<>();
    private final Map<String, UIElement> devicePorts = new HashMap<>();
    private List<Wire> wires = List.of();
    private @Nullable ProgramSync.Index index;
    private String domain = "";
    private @Nullable String selected;

    private record Wire(String function, String device, String installation, int color) {
    }

    /** {@code openInEditor} takes a program and one of its functions. */
    FederationInstallView(UI ui, Consumer<ProgramSync.Answer> answer, BiConsumer<String, String> openInEditor) {
        this.answer = answer;
        this.openInEditor = openInEditor;
        canvasScroll = element(ui, "auto_install_canvas", ScrollerView.class);
        aside = element(ui, "auto_install_aside", ScrollerView.class);
        canvasScroll.addScrollViewChild(canvas);
    }

    /** Shows {@code index} as seen from {@code domain}: its devices and their installations. */
    void show(@Nullable ProgramSync.Index index, String domain) {
        this.index = index;
        this.domain = domain;
        rebuild();
    }

    private List<ProgramSync.DeviceInfo> devices() {
        return index == null ? List.of()
                : index.devices().stream().filter(device -> device.domain().equals(domain)).toList();
    }

    /** The installations on this domain's devices. */
    private List<ProgramSync.InstallationInfo> installations() {
        if (index == null) return List.of();
        var devices = devices().stream().map(ProgramSync.DeviceInfo::id).toList();
        return index.installations().stream().filter(installation -> devices.contains(installation.device()))
                .toList();
    }

    private int color(String file) {
        if (index == null) return PROGRAM_COLORS[0];
        var files = index.files();
        for (int i = 0; i < files.size(); i++) {
            if (files.get(i).name().equals(file)) return PROGRAM_COLORS[i % PROGRAM_COLORS.length];
        }
        return FederationTheme.DARK_MUTED;
    }

    private @Nullable ProgramSync.FileInfo file(String name) {
        return index == null ? null
                : index.files().stream().filter(file -> file.name().equals(name)).findFirst().orElse(null);
    }

    private void rebuild() {
        canvas.clearAllChildren();
        functionPorts.clear();
        devicePorts.clear();
        var installations = installations();
        if (installations.stream().noneMatch(installation -> installation.id().equals(selected))) selected = null;
        var programs = column("install-programs", "ae2federation.ui.automation.programs");
        if (index != null) {
            for (var file : index.files()) programs.addChild(programCard(file, installations));
        }
        var devices = column("install-devices", "ae2federation.ui.automation.devices");
        for (var device : devices()) {
            devices.addChild(deviceCard(device, installations.stream()
                    .filter(installation -> installation.device().equals(device.id())).toList()));
        }
        if (devices().isEmpty()) {
            devices.addChild(label("install-note", Component.translatable("ae2federation.ui.automation.no_devices")));
        }
        canvas.addChildren(programs, devices);
        wires = installations.stream().map(installation -> new Wire(installation.file() + "."
                + installation.function(), installation.device(), installation.id(), color(installation.file())))
                .toList();
        rebuildAside();
    }

    private UIElement column(String type, String heading) {
        var column = new UIElement();
        column.addClass("install-column");
        column.addClass(type);
        column.addChild(label("install-heading", Component.translatable(heading)));
        return column;
    }

    private static UIElement card() {
        var card = new UIElement();
        card.addClass("install-card");
        card.style(style -> style.backgroundTexture(FederationTheme.painted((pen, x, y, width, height) -> {
            pen.rect(x - 1, y - 1, width + 2, height + 2, CARD_SHADOW);
            pen.rect(x, y, width, height, CARD_RING);
            pen.rect(x + 1, y + 1, width - 2, height - 2, CARD_FACE);
        })));
        return card;
    }

    private static UIElement square(int color) {
        var square = new UIElement();
        square.addClass("install-square");
        square.style(style -> style.backgroundTexture(FederationTheme.solid(color)));
        return square;
    }

    private UIElement programCard(ProgramSync.FileInfo file, List<ProgramSync.InstallationInfo> installations) {
        int color = color(file.name());
        var card = card();
        card.setId("auto_install_program_" + file.name());
        var head = new UIElement();
        head.addClass("install-card-head");
        head.addChildren(square(color), label("install-name", Component.literal(file.name())));
        card.addChild(head);
        for (var function : file.functions()) {
            var row = new UIElement();
            row.addClass("install-row");
            var params = function.params().stream().map(param -> param.split(":")[0].strip()).toList();
            row.addChild(label("install-name", Component.literal(function.name()).append(Component
                    .literal("(" + String.join(", ", params) + ")").withColor(FederationTheme.DARK_MUTED))));
            long count = installations.stream().filter(installation -> installation.file().equals(file.name())
                    && installation.function().equals(function.name())).count();
            if (count > 0) {
                row.addChild(label("install-count", Component.literal("→ " + count)));
            }
            var port = new UIElement();
            port.addClass("install-port");
            port.style(style -> style.backgroundTexture(FederationTheme.painted((pen, x, y, width, height) ->
                    FederationProcessingGraph.round(pen, x + width + 1, y + height / 2, PORT, color, PORT_FILL))));
            row.addChild(port);
            functionPorts.put(file.name() + "." + function.name(), port);
            card.addChild(row);
        }
        return card;
    }

    private UIElement deviceCard(ProgramSync.DeviceInfo device, List<ProgramSync.InstallationInfo> installations) {
        var card = card();
        card.setId("auto_install_device_" + device.id());
        var head = new UIElement();
        head.addClass("install-device-head");
        var port = new UIElement();
        port.addClass("install-device-port");
        port.style(style -> style.backgroundTexture(FederationTheme.painted((pen, x, y, width, height) ->
                FederationProcessingGraph.round(pen, x - 1, y + height / 2, PORT, FederationTheme.DARK_TEXT,
                        PORT_FILL))));
        devicePorts.put(device.id(), port);
        var lines = new UIElement();
        lines.addClass("install-device-lines");
        lines.addChild(label("install-title", Component.literal(deviceName(device))));
        // The Creative Computing Device is the only kind so far: unlimited compute.
        var position = device.position();
        lines.addChild(label("install-muted", device.alias().isEmpty()
                ? Component.translatable("ae2federation.ui.automation.creative_at",
                        position.getX() + ", " + position.getY() + ", " + position.getZ())
                : Component.translatable("ae2federation.ui.automation.creative")));
        head.addChildren(port, lines);
        card.addChild(head);
        for (var installation : installations) card.addChild(installationRow(installation));
        return card;
    }

    private static String deviceName(ProgramSync.DeviceInfo device) {
        return device.alias().isEmpty() ? device.id() : device.alias();
    }

    private UIElement installationRow(ProgramSync.InstallationInfo installation) {
        var row = new UIElement();
        row.addClass("install-row");
        row.addClass("installation");
        row.setId("auto_installation_" + installation.id());
        if (installation.id().equals(selected)) row.addClass("selected");
        var file = file(installation.file());
        row.addChild(square(file != null && file.current() ? FederationTheme.OK : FederationTheme.WARN));
        row.addChild(label("install-function", Component.literal(installation.function())));
        var summary = new ArrayList<String>();
        ItemStack icon = ItemStack.EMPTY;
        for (var arg : installation.args()) {
            var stack = icon(arg);
            if (!stack.isEmpty()) {
                if (icon.isEmpty()) icon = stack;
                summary.add(stack.getHoverName().getString());
            } else if (!arg.isEmpty()) {
                summary.add(interfaceName(arg));
            }
        }
        if (!icon.isEmpty()) {
            var image = new UIElement();
            image.addClass("install-icon");
            var stack = icon;
            image.style(style -> style.backgroundTexture(
                    new com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture(stack)));
            row.addChild(image);
        }
        row.addChild(label("install-muted install-summary", Component.literal(String.join(" · ", summary))));
        row.addEventListener(UIEvents.CLICK, event -> {
            selected = installation.id();
            rebuild();
        });
        return row;
    }

    /** An interface's alias in place of its ID, when it has one. */
    private String interfaceName(String id) {
        if (index == null) return id;
        return index.interfaces().stream().filter(found -> found.id().equals(id) && !found.alias().isEmpty())
                .map(ProgramSync.DeviceInfo::alias).findFirst().orElse(id);
    }

    /** An item's icon, or a fluid's bucket; empty for text that names neither. */
    private static ItemStack icon(String id) {
        if (id.indexOf(':') <= 0) return ItemStack.EMPTY;
        var location = ResourceLocation.tryParse(id);
        if (location == null) return ItemStack.EMPTY;
        var item = BuiltInRegistries.ITEM.get(location);
        if (item != Items.AIR) return new ItemStack(item);
        var fluid = BuiltInRegistries.FLUID.get(location);
        return fluid == Fluids.EMPTY ? ItemStack.EMPTY : new ItemStack(fluid.getBucket());
    }

    private void rebuildAside() {
        aside.clearAllScrollViewChildren();
        var installation = installations().stream().filter(found -> found.id().equals(selected)).findFirst()
                .orElse(null);
        if (installation == null) return;
        var device = devices().stream().filter(found -> found.id().equals(installation.device())).findFirst()
                .orElse(null);
        aside.addScrollViewChild(label("install-aside-title", Component.literal(installation.function())
                .withStyle(ChatFormatting.BOLD).append(Component.literal(" → " + (device == null
                        ? installation.device() : deviceName(device))).withStyle(style -> style.withBold(false)))));
        var file = file(installation.file());
        var function = file == null ? null : file.functions().stream()
                .filter(found -> found.name().equals(installation.function())).findFirst().orElse(null);
        var params = function == null ? List.<String>of() : function.params();
        aside.addScrollViewChild(label("install-aside-caption", Component.literal(installation.file() + "."
                + installation.function() + "(" + String.join(", ", params) + ")")));
        for (int i = 0; i < params.size(); i++) {
            var arg = i < installation.args().size() ? installation.args().get(i) : "";
            aside.addScrollViewChild(fact(Component.literal(params.get(i).split(":")[0].strip()), argument(arg)));
        }
        aside.addScrollViewChild(fact(tr("trigger"), trigger(installation.trigger())));
        aside.addScrollViewChild(fact(tr("min_gap"), tr("ticks", installation.interval())));
        aside.addScrollViewChild(fact(tr("if_running"), tr("overlap." + installation.overlap())));
        var running = installation.running() > 0;
        var state = fact(tr("state"), tr(running ? "running" : "idle"));
        state.addClass(running ? "running" : "idle");
        aside.addScrollViewChild(state);
        var actions = new UIElement();
        actions.addClass("install-actions");
        actions.addChild(action("auto_install_uninstall", "uninstall", "danger-action", () -> ClientPrograms.get()
                .request("uninstall", List.of(installation.id()), answer)));
        actions.addChild(action("auto_install_open", "open_in_editor", null,
                () -> openInEditor.accept(installation.file(), installation.function())));
        actions.addChild(action("auto_install_run", "run_now", null, () -> ClientPrograms.get()
                .request("run", List.of(installation.id()), answer)));
        aside.addScrollViewChild(actions);
    }

    /** An argument as its field shows it: an item or fluid by its icon and name, an interface by its alias. */
    private UIElement argument(String arg) {
        var value = new UIElement();
        value.addClass("install-value");
        var stack = icon(arg);
        if (!stack.isEmpty()) {
            var image = new UIElement();
            image.addClass("install-icon");
            image.style(style -> style.backgroundTexture(
                    new com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture(stack)));
            value.addChild(image);
            value.addChild(label("install-value-text", stack.getHoverName().copy()));
            return value;
        }
        var name = interfaceName(arg);
        value.addChild(label("install-value-text", name.equals(arg) ? Component.literal(arg)
                : Component.literal(name + " ").append(Component.literal(arg).withColor(FederationTheme.TEXT_MUTED))));
        return value;
    }

    /** A trigger line ({@code every 40}, {@code when is_low ...}) as the aside words it. */
    private static MutableComponent trigger(String text) {
        var space = text.indexOf(' ');
        var kind = space < 0 ? text : text.substring(0, space);
        var rest = space < 0 ? "" : text.substring(space + 1);
        return switch (kind) {
            case "every" -> tr("trigger.every", rest);
            case "when" -> tr("trigger.condition", rest);
            case "on" -> tr("trigger.event", rest);
            case "redstone" -> tr("trigger.redstone");
            default -> tr("trigger.manual");
        };
    }

    private static UIElement fact(Component name, Component value) {
        return fact(name, label("install-value-text", value.copy()));
    }

    private static UIElement fact(Component name, UIElement value) {
        var row = new UIElement();
        row.addClass("install-fact");
        row.addChildren(label("install-fact-name", name.copy()), value);
        return row;
    }

    private static Button action(String id, String key, @Nullable String type, Runnable run) {
        var button = new Button();
        button.setId(id);
        button.setText(tr(key));
        if (type != null) button.addClass(type);
        // On the click, not the press: the aside may be rebuilt by the answer.
        button.addEventListener(UIEvents.CLICK, event -> run.run());
        return button;
    }

    private static MutableComponent tr(String key, Object... arguments) {
        return Component.translatable("ae2federation.ui.automation." + key, arguments);
    }

    private static Label label(String classes, Component text) {
        var label = new Label();
        for (var type : classes.split(" ")) label.addClass(type);
        label.setText(text);
        return label;
    }

    private static <T> T element(UI ui, String id, Class<T> type) {
        return ui.selectId(id, type).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing UI element #" + id));
    }

    /** The programs' and the devices' columns; the wires are drawn behind their cards. */
    private final class Canvas extends UIElement {
        Canvas() {
            setId("auto_install_wires");
            addClass("install-canvas");
            // The style sheet's justify-content does not take; the Processing graph sets its columns in code too.
            layout(style -> style.widthPercent(100).flexDirection(FlexDirection.ROW)
                    .justifyContent(AlignContent.SPACE_BETWEEN).alignItems(AlignItems.FLEX_START));
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            for (var wire : wires) {
                var from = functionPorts.get(wire.function());
                var to = devicePorts.get(wire.device());
                if (from == null || to == null) continue;
                var start = new Vector2f(from.getPositionX() + from.getSizeWidth() + 1,
                        from.getPositionY() + from.getSizeHeight() / 2);
                var end = new Vector2f(to.getPositionX() - 1, to.getPositionY() + to.getSizeHeight() / 2);
                boolean chosen = wire.installation().equals(selected);
                DrawerHelper.drawTexLines(context.graphics, LDLibRenderTypes.graphWire(), FederationProcessingGraph
                        .polyline(new WireCurve(start.x, start.y, end.x, end.y)), wire.color(), wire.color(),
                        chosen ? 3f : 1.5f);
            }
        }
    }
}
