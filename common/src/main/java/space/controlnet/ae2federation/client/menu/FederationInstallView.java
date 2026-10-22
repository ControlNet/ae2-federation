package space.controlnet.ae2federation.client.menu;

import com.lowdragmc.lowdraglib2.client.shader.LDLibRenderTypes;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
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
import org.lwjgl.glfw.GLFW;
import space.controlnet.ae2federation.automation.AutomationRegistration;
import space.controlnet.ae2federation.automation.ClientPrograms;
import space.controlnet.ae2federation.automation.ComputingHost;
import space.controlnet.ae2federation.automation.DeviceBudget;
import space.controlnet.ae2federation.automation.InstallationCost;
import space.controlnet.ae2federation.automation.InstallationLog;
import space.controlnet.ae2federation.automation.ProgramEvents;
import space.controlnet.ae2federation.automation.ProgramSync;
import space.controlnet.ae2federation.automation.TriggerText;
import space.controlnet.ae2federation.client.policy.WireCurve;

/**
 * The Automation tab's Install view: every program with its public functions on the left, the computing devices of
 * this domain with what is installed on them on the right, and a wire from each installed function to its device in
 * the program's colour ({@code AutoInstall}). A square before an installation says whether its device runs the
 * program's current version (green) or an older one, waiting for Push (yellow).
 *
 * <p>Selecting an installation shows it beside the canvas: its signature, arguments, trigger, rate limit, what a
 * trigger does while it runs and whether it runs, with Uninstall, Open in editor and Run now. A click on an argument
 * or the rate limit edits it in place, sent on Enter or when the field loses focus; chips choose the trigger and what
 * a trigger does while it runs. The server's answer comes back as a new index, which rebuilds the aside.
 *
 * <p>A device with a capacity shows it in one row under its kind ({@code AutoCapD}): compute and bandwidth reserved
 * of all, each underlined by a bar as wide as its figures, and its installations of its threads. Bandwidth left to
 * elastic installations is drawn dim green. While a function is dragged, each device's ring says whether it can take
 * it, by the server's own rule, and the device under the pointer previews its cost on the bars.
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
    /** How far the pressed pointer moves on a function's row before it drags a wire, as on the Processing graph. */
    private static final float DRAG_THRESHOLD = 3;
    /** How long a dropped wire is drawn dashed while the server's index does not show its installation yet. */
    private static final long PENDING_MILLIS = 5000;
    /** Ticks between log requests while an installation is shown. */
    private static final int LOG_PERIOD = 20;
    /** Secondary text in a value field, darker than the theme's muted text to read on the field. */
    private static final int FIELD_MUTED = 0xff575468;
    /** A meter's bar: bandwidth elastic installations share, and a dragged function's cost while it fits. */
    private static final int METER_SHARED = 0xff2f6b46;
    private static final int METER_PREVIEW = 0xffb8ffd0;

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
    /** The aside's field being typed in: a new index leaves the aside alone until it loses focus. */
    private @Nullable TextField editing;
    /** A trigger kind chosen with its chip that cannot be sent yet: Every waits for its ticks. */
    private @Nullable String draftTrigger;
    /** A drafted condition's function or event, and its arguments as typed so far. */
    private @Nullable String draftName;
    private List<String> draftArgs = new ArrayList<>();
    /** A field to open once the aside holds it: focus needs the field in the UI. */
    private @Nullable Runnable opening;

    private record Wire(String function, String device, String installation, int color) {
    }

    /** A function's wire being dragged, by {@code file.function}. */
    private record FunctionDrag(String file, String function) {
    }

    /** An installation dropped on a device whose required arguments are still being typed. */
    private record Draft(String device, String file, String function, List<String> args) {
    }

    /** The function row pressed, which drags once the pointer moves. */
    private @Nullable FunctionDrag pressed;
    private float pressX;
    private float pressY;
    private String hoverDevice = "";
    private @Nullable Draft draftInstall;
    /** Wires dropped and sent, by {@code file.function} and device, with when they were sent. */
    private final Map<Wire, Long> pendingWires = new HashMap<>();
    /** The State row's value and the log box of the shown installation, updated in place by each log answer. */
    private @Nullable Label stateValue;
    private @Nullable UIElement stateRow;
    /** At most a few lines high (the style sheet's max-height), scrolled within, so the actions stay in view. */
    private @Nullable ScrollerView logBox;
    /** The last log answer and whose it is; ticks until the next request, and whether one is on its way. */
    private @Nullable InstallationLog.Log log;
    private @Nullable String logId;
    private int pollTicks;
    private boolean asking;
    private int waited;

    /** {@code openInEditor} takes a program and one of its functions. */
    FederationInstallView(UI ui, Consumer<ProgramSync.Answer> answer, BiConsumer<String, String> openInEditor) {
        this.answer = answer;
        this.openInEditor = openInEditor;
        canvasScroll = element(ui, "auto_install_canvas", ScrollerView.class);
        aside = element(ui, "auto_install_aside", ScrollerView.class);
        canvasScroll.addScrollViewChild(canvas);
        // A hidden element does not tick: the log is asked for only while the Install view is shown.
        aside.addEventListener(UIEvents.TICK, event -> poll());
        // Any press or release forgets the pressed row, so only its own press drags.
        ui.rootElement.addEventListener(UIEvents.MOUSE_DOWN, event -> pressed = null, true);
        ui.rootElement.addEventListener(UIEvents.MOUSE_UP, event -> pressed = null, true);
        ui.rootElement.addEventListener(UIEvents.MOUSE_MOVE, event -> {
            var drag = pressed;
            if (drag == null || dragged() != null) return;
            if (Math.abs(event.x - pressX) + Math.abs(event.y - pressY) < DRAG_THRESHOLD) return;
            var port = functionPorts.get(drag.file() + "." + drag.function());
            pressed = null;
            if (port != null) port.startDrag(drag, null);
        }, true);
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
        pendingWires.keySet().removeIf(pending -> wires.stream().anyMatch(wire ->
                wire.function().equals(pending.function()) && wire.device().equals(pending.device())));
        // A refresh would drop what is being typed; the aside is rebuilt once the field is left.
        if (editing == null || !editing.isFocused()) rebuildAside();
    }

    private UIElement column(String type, String heading) {
        var column = new UIElement();
        column.addClass("install-column");
        column.addClass(type);
        column.addChild(label("install-heading", Component.translatable(heading)));
        return column;
    }

    private static UIElement card() {
        return card(() -> CARD_RING);
    }

    /** A card whose ring colour is read when drawn: a device's lights up while a wire is dragged. */
    private static UIElement card(java.util.function.IntSupplier ring) {
        var card = new UIElement();
        card.addClass("install-card");
        card.style(style -> style.backgroundTexture(FederationTheme.painted((pen, x, y, width, height) -> {
            pen.rect(x - 1, y - 1, width + 2, height + 2, CARD_SHADOW);
            pen.rect(x, y, width, height, ring.getAsInt());
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
            var drag = new FunctionDrag(file.name(), function.name());
            port.style(style -> style.backgroundTexture(FederationTheme.painted((pen, x, y, width, height) ->
                    FederationProcessingGraph.round(pen, x + width + 1, y + height / 2, PORT, color,
                            drag.equals(dragged()) ? color : PORT_FILL))));
            row.addChild(port);
            row.setId("auto_install_fn_" + file.name() + "_" + function.name());
            // The row drags its function's wire to a device once the pressed pointer moves.
            row.addEventListener(UIEvents.MOUSE_DOWN, event -> {
                if (event.button != 0) return;
                pressed = drag;
                pressX = event.x;
                pressY = event.y;
            });
            functionPorts.put(file.name() + "." + function.name(), port);
            card.addChild(row);
        }
        return card;
    }

    private UIElement deviceCard(ProgramSync.DeviceInfo device, List<ProgramSync.InstallationInfo> installations) {
        // Every device of the domain takes a dragged wire: lit while one is dragged (AutoInstallDrag).
        var card = card(() -> {
            var drag = dragged();
            return drag == null ? CARD_RING : fits(device, drag) ? FederationTheme.OK : FederationTheme.ERROR;
        });
        card.setId("auto_install_device_" + device.id());
        card.addClass("install-device");
        card.addEventListener(UIEvents.DRAG_ENTER, event -> {
            if (dragged() != null) hoverDevice = device.id();
        }, true);
        card.addEventListener(UIEvents.DRAG_LEAVE, event -> {
            if (hoverDevice.equals(device.id())) hoverDevice = "";
        }, true);
        card.addEventListener(UIEvents.DRAG_PERFORM, event -> {
            var drag = dragged();
            hoverDevice = "";
            if (drag != null) drop(drag, device.id());
        });
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
        lines.addChild(label("install-muted", deviceLine(device)));
        var budget = device.budget();
        if (budget != null && !budget.capacity().unlimited()) {
            lines.addChild(meters(device.id(), budget));
            head.addClass("metered");
        }
        head.addChildren(port, lines);
        card.addChild(head);
        for (var installation : installations) card.addChild(installationRow(installation));
        return card;
    }

    /**
     * A device's capacity row: compute and fixed bandwidth reserved of all, each underlined by its bar, and its
     * installations of its threads; a figure over its capacity is red. A structure that is not formed has none: dashes.
     */
    private UIElement meters(String device, ProgramSync.Budget budget) {
        var capacity = budget.capacity();
        var reserved = budget.reserved();
        var row = new UIElement();
        row.addClass("install-meters");
        var formed = !capacity.equals(ComputingHost.Capacity.NONE);
        row.addChild(label("install-meter-name", tr("meter.compute")));
        row.addChild(formed ? meter(reserved.compute(), capacity.compute(), false,
                () -> preview(device, InstallationCost::compute)) : dash());
        row.addChild(label("install-meter-name gap", tr("meter.bandwidth")));
        row.addChild(formed ? meter(reserved.bandwidth(), capacity.bandwidth(), reserved.elastic(),
                () -> preview(device, cost -> cost.elastic() ? 0 : cost.bandwidth())) : dash());
        row.addChild(label("install-meter-name gap", tr("meter.threads")));
        row.addChild(formed ? figure(reserved.installations() + "/" + capacity.installations(),
                reserved.installations() > capacity.installations()) : dash());
        return row;
    }

    /** Used of all, underlined by a bar of the same width: green, red when over, dim green where elastic ones share. */
    private static UIElement meter(long used, long total, boolean shared, java.util.function.LongSupplier preview) {
        var meter = new UIElement();
        meter.addClass("install-meter");
        meter.addChild(figure(FederationTopologyView.compact(used) + "/" + FederationTopologyView.compact(total),
                used > total));
        meter.style(style -> style.backgroundTexture(FederationTheme.painted((pen, x, y, width, height) -> {
            int bar = (int) y + (int) height - 2;
            pen.rect(x, bar, width, 2, FederationTheme.WELL);
            // A capacity of nothing draws an empty bar, or a full red one while something is reserved on it.
            float fill = total <= 0 ? (used > 0 ? 1 : 0) : Math.min(1f, (float) used / total);
            pen.rect(x, bar, width * fill, 2, used > total ? FederationTheme.ERROR : FederationTheme.OK);
            long extra = preview.getAsLong();
            if (extra > 0) {
                float more = total <= 0 ? 1 : Math.min(1 - fill, (float) extra / total);
                pen.rect(x + width * fill, bar, width * more, 2,
                        extra > total - used ? FederationTheme.ERROR : METER_PREVIEW);
            } else if (shared) {
                pen.rect(x + width * fill, bar, width * (1 - fill), 2, METER_SHARED);
            }
        })));
        return meter;
    }

    private static Label figure(String text, boolean over) {
        return label("install-meter-value", Component.literal(text)
                .withColor((over ? FederationTheme.ERROR : FederationTheme.DARK_TEXT) & 0xffffff));
    }

    private static Label dash() {
        return label("install-meter-value",
                Component.literal("\u2014").withColor(FederationTheme.DARK_MUTED & 0xffffff));
    }

    /** What the dragged function would add to a meter of the device under the pointer; 0 elsewhere. */
    private long preview(String device, java.util.function.ToLongFunction<InstallationCost> part) {
        var drag = dragged();
        if (drag == null || !hoverDevice.equals(device)) return 0;
        var function = function(drag.file(), drag.function());
        return function == null || function.cost() == null ? 0 : part.applyAsLong(function.cost());
    }

    /**
     * Whether the device can take the dragged function, by the rule the server installs by. One it cannot judge, not
     * loaded or with a function it could not price, is let through: the server answers on the drop.
     */
    private boolean fits(ProgramSync.DeviceInfo device, FunctionDrag drag) {
        var budget = device.budget();
        var function = function(drag.file(), drag.function());
        if (budget == null || function == null || function.cost() == null) return true;
        return DeviceBudget.refusal(budget.capacity(), budget.reserved(), function.cost()).isEmpty();
    }

    /**
     * The line under a device's name: its kind, then its position while it has no alias. The Creative Computing
     * Device says it is unlimited instead; another mod's device goes by its block's name, and one whose chunk is not
     * loaded by its position alone.
     */
    private static Component deviceLine(ProgramSync.DeviceInfo device) {
        var parts = new ArrayList<Component>();
        var block = device.block().isEmpty() ? null : ResourceLocation.tryParse(device.block());
        if (block != null) {
            parts.add(block.getNamespace().equals("ae2federation")
                    ? Component.translatable("ae2federation.ui.automation.kind." + block.getPath())
                    : BuiltInRegistries.BLOCK.get(block).getName());
        }
        var position = device.position();
        if (device.alias().isEmpty() || block == null) {
            parts.add(Component.literal(position.getX() + ", " + position.getY() + ", " + position.getZ()));
        } else if (block.equals(AutomationRegistration.DEVICE.getId())) {
            parts.add(Component.translatable("ae2federation.ui.automation.unlimited"));
        }
        var line = Component.empty();
        for (var part : parts) {
            if (!line.getSiblings().isEmpty()) line.append(" · ");
            line.append(part);
        }
        return line;
    }

    @Nullable
    private FunctionDrag dragged() {
        var ui = canvas.getModularUI();
        if (ui == null || !ui.getDragHandler().isDragging()) return null;
        return ui.getDragHandler().draggingObject instanceof FunctionDrag drag ? drag : null;
    }

    /**
     * A function dropped on a device: installed at once when it needs no arguments, else a draft in the aside whose
     * fields open one by one; the installation is sent once every required argument is typed (assistant proposal).
     */
    private void drop(FunctionDrag drag, String device) {
        var function = function(drag.file(), drag.function());
        if (function == null) return;
        var args = new ArrayList<String>();
        for (var param : function.params()) {
            args.add(param.contains("=") ? param.substring(param.indexOf('=') + 1).strip() : "");
        }
        draftInstall = new Draft(device, drag.file(), drag.function(), args);
        selected = null;
        draftTrigger = null;
        sendInstallWhenComplete();
    }

    private @Nullable ProgramSync.FunctionInfo function(String file, String name) {
        var info = file(file);
        return info == null ? null
                : info.functions().stream().filter(found -> found.name().equals(name)).findFirst().orElse(null);
    }

    private void sendInstallWhenComplete() {
        var draft = draftInstall;
        if (draft == null) return;
        if (draft.args().stream().anyMatch(String::isEmpty)) {
            rebuildAside();
            return;
        }
        draftInstall = null;
        var request = new ArrayList<>(List.of(draft.device(), draft.file(), draft.function()));
        request.addAll(draft.args());
        pendingWires.put(new Wire(draft.file() + "." + draft.function(), draft.device(), "",
                color(draft.file())), System.currentTimeMillis());
        rebuildAside();
        // The answer names the new installation, and comes after the index that lists it: select it and show it.
        ClientPrograms.get().request("install", request, result -> {
            if (result.ok() && result.message().startsWith("installed ")) {
                selected = result.message().substring("installed ".length());
                rebuild();
            }
            if (!result.ok()) pendingWires.clear();
            answer.accept(result);
        });
    }

    /** The aside for an installation being dropped: its arguments, typed before it is sent. */
    private void draftAside(Draft draft) {
        var device = devices().stream().filter(found -> found.id().equals(draft.device())).findFirst().orElse(null);
        aside.addScrollViewChild(label("install-aside-title", Component.literal(draft.function())
                .withStyle(ChatFormatting.BOLD).append(Component.literal(" → " + (device == null
                        ? draft.device() : deviceName(device))).withStyle(style -> style.withBold(false)))));
        var function = function(draft.file(), draft.function());
        var params = function == null ? List.<String>of() : function.params();
        aside.addScrollViewChild(label("install-aside-caption", Component.literal(draft.file() + "."
                + draft.function() + "(" + String.join(", ", params) + ")")));
        for (int i = 0; i < params.size() && i < draft.args().size(); i++) {
            var arg = draft.args().get(i);
            int index = i;
            aside.addScrollViewChild(fact(Component.literal(params.get(i).split(":")[0].strip()),
                    editable("auto_install_arg_" + i, argument(arg), arg, numeric(params.get(i)),
                            opening == null && arg.isEmpty(), text -> {
                                draft.args().set(index, text);
                                sendInstallWhenComplete();
                            })));
        }
        aside.addScrollViewChild(label("install-aside-caption", tr("install_when_typed")));
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
            if (!installation.id().equals(selected)) draftTrigger = null;
            draftInstall = null;
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
        editing = null;
        stateValue = null;
        stateRow = null;
        logBox = null;
        if (draftInstall != null) {
            draftAside(draftInstall);
            if (opening != null) opening.run();
            opening = null;
            return;
        }
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
            int index = i;
            aside.addScrollViewChild(fact(Component.literal(params.get(i).split(":")[0].strip()),
                    editable("auto_install_arg_" + i, argument(arg), arg, numeric(params.get(i)), false,
                            text -> sendArgs(installation, params, index, text))));
        }
        triggerRows(installation);
        aside.addScrollViewChild(fact(tr("min_gap"), editable("auto_install_gap", value(tr("ticks", installation.interval())),
                String.valueOf(installation.interval()), true, false, text -> ClientPrograms.get()
                        .request("interval", List.of(installation.id(), text), answer))));
        var overlap = new UIElement();
        overlap.addClass("install-chips");
        for (var mode : new String[] {"ignore", "restart", "parallel"}) {
            overlap.addChild(chip("auto_install_overlap_" + mode, tr("overlap." + mode),
                    mode.equals(installation.overlap()), () -> ClientPrograms.get()
                            .request("overlap", List.of(installation.id(), mode), answer)));
        }
        aside.addScrollViewChild(fact(tr("if_running"), overlap));
        aside.addScrollViewChild(fact(tr("cost"), cost(installation)));
        stateValue = label("install-value-text", Component.empty());
        stateValue.setId("auto_install_state");
        stateRow = fact(tr("state"), stateValue);
        aside.addScrollViewChild(stateRow);
        aside.addScrollViewChild(label("install-log-heading", tr("log")));
        logBox = new ScrollerView();
        logBox.setId("auto_install_log");
        logBox.addClass("install-log");
        aside.addScrollViewChild(logBox);
        // The index's count until the first answer; the last answer at once when the same installation is shown again.
        showLog(installation.id().equals(logId) && log != null ? log
                : new InstallationLog.Log(installation.running(), List.of()), !installation.id().equals(logId));
        pollTicks = 0;
        var actions = new UIElement();
        actions.addClass("install-actions");
        actions.addChild(action("auto_install_uninstall", "uninstall", "danger-action", () -> ClientPrograms.get()
                .request("uninstall", List.of(installation.id()), answer)));
        actions.addChild(action("auto_install_open", "open_in_editor", null,
                () -> openInEditor.accept(installation.file(), installation.function())));
        actions.addChild(action("auto_install_run", "run_now", null, () -> ClientPrograms.get()
                .request("run", List.of(installation.id()), answer)));
        aside.addScrollViewChild(actions);
        if (opening != null) opening.run();
        opening = null;
    }

    /**
     * The trigger chips and, below them, what the chosen kind needs: Every its ticks, Condition a function of the
     * program that gives a {@code bool} without acting and its arguments, Event an event and its arguments. A kind
     * chosen with its chip is a draft until all it needs is given; then the trigger is sent.
     */
    private void triggerRows(ProgramSync.InstallationInfo installation) {
        var words = TriggerText.words(installation.trigger());
        var kind = words.isEmpty() ? "manual" : words.get(0);
        if (kind.equals(draftTrigger) && kind.equals("every")) draftTrigger = null;
        var shown = draftTrigger != null ? draftTrigger : kind;
        var chips = new UIElement();
        chips.addClass("install-chips");
        // Five chips do not fit a narrow aside on one line.
        chips.layout(layout -> layout.flexWrap(FlexWrap.WRAP));
        for (var choice : new String[] {"manual", "redstone", "every", "when", "on"}) {
            chips.addChild(chip("auto_install_trigger_" + choice, tr("trigger." + switch (choice) {
                case "when" -> "condition";
                case "on" -> "event";
                default -> choice;
            }),
                    choice.equals(shown), () -> {
                        if (choice.equals(kind) || choice.equals("manual") || choice.equals("redstone")) {
                            draftTrigger = null;
                            if (choice.equals(kind)) {
                                rebuildAside();
                            } else {
                                ClientPrograms.get().request("trigger", List.of(installation.id(), choice), answer);
                            }
                        } else {
                            // The other kinds need more first: the chip shows their fields.
                            draftTrigger = choice;
                            draftName = null;
                            draftArgs = new ArrayList<>();
                            rebuildAside();
                        }
                    }));
        }
        aside.addScrollViewChild(fact(tr("trigger"), chips));
        if (shown.equals("every")) {
            var ticks = kind.equals("every") && words.size() > 1 ? words.get(1) : "";
            aside.addScrollViewChild(fact(Component.empty(), editable("auto_install_every", value(ticks.isEmpty()
                    ? Component.empty() : tr("ticks", ticks)), ticks, true, draftTrigger != null, text ->
                            ClientPrograms.get().request("trigger", List.of(installation.id(), "every " + text),
                                    answer))));
            return;
        }
        if (!shown.equals("when") && !shown.equals("on")) return;
        var draft = draftTrigger != null;
        var name = draft ? draftName : words.size() > 1 ? words.get(1) : null;
        var args = draft ? draftArgs : new ArrayList<>(words.subList(Math.min(2, words.size()), words.size()));
        List<Choice> choices = shown.equals("when") ? conditions(installation) : events();
        var chosen = choices.stream().filter(choice -> choice.name().equals(name)).findFirst().orElse(null);
        if (shown.equals("when")) {
            aside.addScrollViewChild(fact(Component.empty(), dropdown(name == null
                    ? tr("choose_condition").withColor(FIELD_MUTED) : chosen == null
                            ? Component.literal(name) : chosen.display(FIELD_MUTED), choices, name == null, tr("no_conditions"),
                    choice -> choose(installation, "when", choice))));
        } else {
            aside.addScrollViewChild(fact(Component.empty(), eventField(name == null ? "" : name, choices,
                    choice -> choose(installation, "on", choice))));
        }
        if (chosen == null) return;
        for (int i = 0; i < chosen.params().size(); i++) {
            var param = chosen.params().get(i);
            var arg = i < args.size() ? args.get(i) : "";
            int index = i;
            var row = fact(Component.literal(param.split("[:=]")[0].strip()), editable("auto_install_" + shown
                    + "_arg_" + i, argument(arg), arg, numeric(param), draft && opening == null && arg.isEmpty(),
                    text -> setTriggerArg(installation, shown, chosen, args, index, text)));
            row.addClass("sub");
            aside.addScrollViewChild(row);
        }
    }

    /**
     * Something a trigger starts on: a condition function, shown with its parameters as declared, or an event (no
     * result), shown with their names and defaults as on {@code AutoInstallEvent}.
     */
    private record Choice(String name, List<String> params, String result, String description) {
        Component display(int muted) {
            var shown = result.isEmpty() ? params.stream().map(param -> param.split(":")[0].strip()
                    + (param.contains("=") ? "=" + param.substring(param.indexOf('=') + 1).strip() : "")).toList()
                    : params;
            return Component.literal(name).append(Component.literal("(" + String.join(", ", shown) + ")"
                    + (result.isEmpty() ? "" : " -> " + result)).withColor(muted));
        }
    }

    /** The program's functions a condition may name: public, giving a {@code bool}, not acting. */
    private List<Choice> conditions(ProgramSync.InstallationInfo installation) {
        var file = file(installation.file());
        if (file == null) return List.of();
        return file.functions().stream().filter(function -> !function.name().startsWith("_")
                && function.result().equals("bool") && !function.acts())
                .map(function -> new Choice(function.name(), function.params(), "bool", "")).toList();
    }

    /** The curated events, with their arguments as the scheduler takes them. */
    private static List<Choice> events() {
        return ProgramEvents.EVENTS.values().stream().map(spec -> new Choice(spec.name(), switch (spec.kind()) {
            case NETWORK -> List.<String>of();
            case RESOURCE -> List.of("resource: Resource");
            case BLOCK -> List.of("interface: Interface", "reach: int = 1");
        }, "", spec.description())).toList();
    }

    /** A condition or an event chosen: sent at once when it needs no arguments, else a draft until they are given. */
    private void choose(ProgramSync.InstallationInfo installation, String kind, Choice choice) {
        draftTrigger = kind;
        draftName = choice.name();
        draftArgs = new ArrayList<>();
        for (var param : choice.params()) {
            draftArgs.add(param.contains("=") ? param.substring(param.indexOf('=') + 1).strip() : "");
        }
        sendTriggerWhenComplete(installation);
    }

    /** One argument of a condition or an event typed: a draft keeps it, a trigger in place is sent again. */
    private void setTriggerArg(ProgramSync.InstallationInfo installation, String kind, Choice choice,
            List<String> args, int index, String text) {
        if (draftTrigger == null) {
            draftTrigger = kind;
            draftName = choice.name();
            draftArgs = new ArrayList<>(args);
        }
        while (draftArgs.size() < choice.params().size()) draftArgs.add("");
        draftArgs.set(index, text);
        sendTriggerWhenComplete(installation);
    }

    private void sendTriggerWhenComplete(ProgramSync.InstallationInfo installation) {
        if (draftArgs.stream().anyMatch(String::isEmpty)) {
            rebuildAside();
            return;
        }
        var text = new StringBuilder(draftTrigger).append(' ').append(draftName);
        for (var arg : draftArgs) {
            text.append(' ').append(arg.contains(" ") ? '"' + arg + '"' : arg);
        }
        draftTrigger = null;
        ClientPrograms.get().request("trigger", List.of(installation.id(), text.toString()), answer);
    }

    /** A field showing the choice, opening the list of choices under it when clicked (the Condition's). */
    private UIElement dropdown(Component text, List<Choice> choices, boolean open, Component none,
            Consumer<Choice> pick) {
        var box = new UIElement();
        box.setId("auto_install_choose");
        box.addClass("install-dropdown");
        var shown = new UIElement();
        shown.addClass("install-value");
        shown.addChildren(label("install-value-text", text), label("install-arrow", Component.literal("\u25bc")));
        var list = choiceList(choices, none, pick);
        list.setDisplay(open);
        shown.addEventListener(UIEvents.CLICK, event -> list.setDisplay(!list.isDisplayed()));
        box.addChildren(shown, list);
        return box;
    }

    /** A field to type an event's name in, listing the events that contain the text (the Event's). */
    private UIElement eventField(String name, List<Choice> choices, Consumer<Choice> pick) {
        var box = new UIElement();
        box.setId("auto_install_choose");
        box.addClass("install-dropdown");
        var field = new TextField();
        field.addClass("install-field");
        field.setText(name, false);
        field.textFieldStyle(style -> style.placeholder(tr("choose_event")));
        var holder = new UIElement();
        holder.addClass("install-choices-holder");
        Runnable list = () -> {
            holder.clearAllChildren();
            var typed = field.getText().strip().toLowerCase(java.util.Locale.ROOT);
            // The event in place is not offered again until the text changes.
            if (!name.isEmpty() && typed.equals(name)) return;
            holder.addChild(choiceList(choices.stream().filter(choice -> choice.name().contains(typed)).toList(),
                    tr("no_events"), pick));
        };
        field.setTextResponder(text -> list.run());
        field.addEventListener(UIEvents.FOCUS, event -> editing = field);
        field.addEventListener(UIEvents.KEY_DOWN, event -> {
            if (event.keyCode != GLFW.GLFW_KEY_ENTER && event.keyCode != GLFW.GLFW_KEY_KP_ENTER) return;
            var typed = field.getText().strip().toLowerCase(java.util.Locale.ROOT);
            choices.stream().filter(choice -> choice.name().contains(typed)).findFirst().ifPresent(pick);
        });
        if (name.isEmpty()) {
            list.run();
            opening = () -> {
                editing = field;
                field.focus();
            };
        }
        box.addChildren(field, holder);
        return box;
    }

    /** The choices as rows, each its name and parameters, with the first one's description below when it has one. */
    private UIElement choiceList(List<Choice> choices, Component none, Consumer<Choice> pick) {
        var list = new UIElement();
        list.addClass("install-choices");
        if (choices.isEmpty()) list.addChild(label("install-choice-note", none.copy()));
        for (var choice : choices) {
            var row = new UIElement();
            row.addClass("install-choice");
            row.setId("auto_install_choice_" + choice.name());
            row.addChild(label("install-choice-text", choice.display(FederationTheme.DARK_MUTED)));
            // On the click, not the press: choosing rebuilds the aside.
            row.addEventListener(UIEvents.CLICK, event -> pick.accept(choice));
            list.addChild(row);
        }
        if (!choices.isEmpty() && !choices.get(0).description().isEmpty()) {
            list.addChild(label("install-choice-note", Component.literal(choices.get(0).description())));
        }
        return list;
    }

    /** Sends the installation's arguments with the one at {@code index} changed, defaults filling any before it. */
    private void sendArgs(ProgramSync.InstallationInfo installation, List<String> params, int index, String text) {
        var args = new ArrayList<String>();
        args.add(installation.id());
        for (int i = 0; i < Math.max(index + 1, installation.args().size()); i++) {
            if (i == index) {
                args.add(text);
            } else if (i < installation.args().size()) {
                args.add(installation.args().get(i));
            } else {
                var param = params.get(i);
                args.add(param.contains("=") ? param.substring(param.indexOf('=') + 1).strip() : "");
            }
        }
        ClientPrograms.get().request("args", args, answer);
    }

    /** Whether a parameter takes a number, for a narrow field. */
    private static boolean numeric(String param) {
        var type = param.contains(":") ? param.substring(param.indexOf(':') + 1) : "";
        if (type.contains("=")) type = type.substring(0, type.indexOf('='));
        return type.strip().equals("int") || type.strip().equals("float");
    }

    /**
     * A value shown in a field until clicked, then edited as its text: Enter or leaving the field sends a changed
     * text, and the new index shows the result. {@code open} starts it edited, for a trigger that waits for it.
     */
    private UIElement editable(String id, UIElement shown, String text, boolean narrow, boolean open,
            Consumer<String> send) {
        var box = new UIElement();
        box.setId(id);
        box.addClass("install-edit");
        if (narrow) box.addClass("narrow");
        var field = new TextField();
        field.addClass("install-field");
        field.setText(text, false);
        field.textFieldStyle(style -> style.placeholder(Component.empty()));
        field.setDisplay(false);
        Runnable leave = () -> {
            if (editing != field) return;
            editing = null;
            var typed = field.getText().strip();
            field.setDisplay(false);
            shown.setDisplay(true);
            if (!typed.equals(text) && !typed.isEmpty()) send.accept(typed);
        };
        Runnable edit = () -> {
            shown.setDisplay(false);
            field.setDisplay(true);
            editing = field;
            field.focus();
            // All of the text selected, so typing replaces it.
            field.setCursor(text.length());
            field.setSelection(0, text.length());
        };
        // On the click, not the press: the shown value hides under the pointer.
        shown.addEventListener(UIEvents.CLICK, event -> edit.run());
        field.addEventListener(UIEvents.KEY_DOWN, event -> {
            if (event.keyCode == GLFW.GLFW_KEY_ENTER || event.keyCode == GLFW.GLFW_KEY_KP_ENTER) leave.run();
        });
        field.addEventListener(UIEvents.BLUR, event -> leave.run());
        box.addChildren(shown, field);
        if (open) opening = edit;
        return box;
    }

    /** Text in a value field. */
    private static UIElement value(Component text) {
        var value = new UIElement();
        value.addClass("install-value");
        value.addChild(label("install-value-text", text.copy()));
        return value;
    }

    private static Button chip(String id, Component text, boolean chosen, Runnable run) {
        var button = new Button();
        button.setId(id);
        button.addClass("install-chip");
        if (chosen) button.addClass("selected");
        button.setText(text);
        int width = net.minecraft.client.Minecraft.getInstance().font.width(text) + 10;
        button.layout(layout -> layout.width(width));
        // On the click, not the press: the answer rebuilds the aside.
        button.addEventListener(UIEvents.CLICK, event -> run.run());
        return button;
    }

    /** Asks for the shown installation's state and log every second while the Install view is shown. */
    private void poll() {
        if (asking) {
            // Leaving the tab drops the awaited answers: ask again after a while.
            if (++waited < 5 * LOG_PERIOD) return;
            asking = false;
        }
        if (selected == null || draftInstall != null || --pollTicks > 0) return;
        pollTicks = LOG_PERIOD;
        asking = true;
        waited = 0;
        var id = selected;
        ClientPrograms.get().request("log", List.of(id), result -> {
            asking = false;
            if (!result.ok() || !id.equals(selected)) return;
            logId = id;
            log = InstallationLog.parse(result.message());
            showLog(log, false);
        });
    }

    /** Puts a log answer into the aside's State row and log box, without rebuilding the aside. */
    private void showLog(InstallationLog.Log shown, boolean waiting) {
        if (stateValue == null || stateRow == null || logBox == null) return;
        var running = shown.running() > 0;
        stateValue.setText(tr(running ? "running" : "idle"));
        stateRow.removeClass("running");
        if (running) stateRow.addClass("running");
        logBox.clearAllScrollViewChildren();
        if (shown.lines().isEmpty()) {
            logBox.addScrollViewChild(label("install-log-text note", waiting ? Component.empty() : tr("no_log")));
        }
        for (var line : shown.lines()) {
            var row = new UIElement();
            row.addClass("install-log-row");
            row.addChild(label("install-log-age", Component.literal(InstallationLog.age(line.age()))));
            var text = label("install-log-text", Component.literal(line.text()));
            if (failed(line.text())) text.addClass("failed");
            row.addChild(text);
            logBox.addScrollViewChild(row);
        }
    }

    /** Entries the scheduler writes when a run could not start or went wrong. */
    private static boolean failed(String text) {
        return text.startsWith("failed") || text.startsWith("not started") || text.startsWith("condition failed")
                || text.startsWith("event not read");
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
                : Component.literal(name + " ").append(Component.literal(arg).withColor(FIELD_MUTED))));
        return value;
    }

    /**
     * What an installation reserves: its compute, and its bandwidth, or for an elastic one the share it has now, then
     * "shared"; no figure while that is unknown (its device not loaded) or unlimited.
     */
    private static Component cost(ProgramSync.InstallationInfo installation) {
        var cost = installation.cost();
        var text = Component.literal(FederationTopologyView.compact(cost.compute()) + " ")
                .append(tr("meter.compute"));
        if (!cost.elastic()) {
            return text.append(" \u00b7 " + FederationTopologyView.compact(cost.bandwidth()) + " ")
                    .append(tr("meter.bandwidth"));
        }
        var share = installation.bandwidth();
        var known = share >= 0 && share != Long.MAX_VALUE;
        text.append(" \u00b7 ");
        if (known) text.append(FederationTopologyView.compact(share) + " ").append(tr("meter.bandwidth")).append(" ");
        return text.append(tr("shared").withColor(FederationTheme.TEXT_MUTED & 0xffffff));
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
                        chosen ? FederationTheme.WIRE_CHOSEN : FederationTheme.WIRE);
            }
            // A dropped wire is dashed until the index lists its installation; a draft's while it is typed.
            long now = System.currentTimeMillis();
            var dashed = new ArrayList<Wire>();
            pendingWires.forEach((wire, sent) -> {
                if (now - sent < PENDING_MILLIS) dashed.add(wire);
            });
            var draft = draftInstall;
            if (draft != null) {
                dashed.add(new Wire(draft.file() + "." + draft.function(), draft.device(), "", color(draft.file())));
            }
            for (var wire : dashed) {
                var from = functionPorts.get(wire.function());
                var to = devicePorts.get(wire.device());
                if (from == null || to == null) continue;
                FederationProcessingGraph.dashed(context.graphics, FederationProcessingGraph.polyline(new WireCurve(
                        from.getPositionX() + from.getSizeWidth() + 1, from.getPositionY() + from.getSizeHeight() / 2,
                        to.getPositionX() - 1, to.getPositionY() + to.getSizeHeight() / 2)), wire.color());
            }
            var drag = dragged();
            var ui = getModularUI();
            var from = drag == null ? null : functionPorts.get(drag.file() + "." + drag.function());
            if (from != null && ui != null) {
                float startX = from.getPositionX() + from.getSizeWidth() + 1;
                float startY = from.getPositionY() + from.getSizeHeight() / 2;
                var to = devicePorts.get(hoverDevice);
                float endX = to == null ? ui.getLastMouseX() : to.getPositionX() - 1;
                float endY = to == null ? ui.getLastMouseY() : to.getPositionY() + to.getSizeHeight() / 2;
                FederationProcessingGraph.dashed(context.graphics, FederationProcessingGraph.polyline(
                        new WireCurve(startX, startY, endX, endY)), 0xffefeaf8);
            }
        }
    }
}
