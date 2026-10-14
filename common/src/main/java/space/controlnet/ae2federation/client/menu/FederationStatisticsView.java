package space.controlnet.ae2federation.client.menu;

import appeng.api.stacks.AEKey;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import space.controlnet.ae2federation.observability.statistics.StatisticsNames;
import space.controlnet.ae2federation.observability.statistics.StatisticsPayload;
import space.controlnet.ae2federation.observability.statistics.StatisticsRange;

/**
 * The domain's statistics page: a card per metric on the left with its sparkline, the chosen metric large on the right,
 * and the keys that moved most below it. Networks keep the colour the topology gives them. The server sends the
 * figures only while this page is open ({@link #setOpen}), for the range chosen here; which network and which resource
 * kind are shown is chosen on the client.
 */
public final class FederationStatisticsView {
    private static final String ITEMS = "ae2:i";
    private static final String FLUIDS = "ae2:f";
    private static final int CARD = FederationTheme.CANVAS;
    private static final int CARD_SELECTED = 0xff2c2735;
    private static final int CARD_EDGE = 0xff121016;
    private static final int GRID = 0xff2c2935;
    private static final int GRID_ZERO = 0xff5a5568;

    enum Metric {
        POWER, STORED, MOVED, CRAFTING, CPUS, CHANNELS;

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** A member network as the topology shows it: its colour, its name and the short name tooltips use. */
    record Net(String id, int color, Component name, String tag) {
    }

    /** TEST-ONLY evidence: how many networks the last figures from the server covered. */
    private static volatile int shownNetworks;
    private final UI ui;
    private final Consumer<String> request;
    private final List<Net> networks = new ArrayList<>();
    private final List<Card> cards = new ArrayList<>();
    private final List<String> kinds = new ArrayList<>(List.of(ITEMS, FLUIDS));
    private final Chart chart = new Chart();
    private final Ranking ranking = new Ranking();
    private @Nullable StatisticsPayload.Snapshot<AEKey> snapshot;
    private StatisticsRange range = StatisticsRange.FIVE_SECONDS;
    private Metric metric = Metric.MOVED;
    private @Nullable String network;
    private String kind = ITEMS;
    private boolean open;
    private List<String> shownChips = List.of();

    FederationStatisticsView(UI ui, Consumer<String> request) {
        this.ui = ui;
        this.request = request;
        for (var candidate : StatisticsRange.values()) {
            var chip = element("stats_range_" + candidate.name(), Button.class);
            chip.setOnClick(event -> {
                range = candidate;
                if (open) request.accept(range.name());
                refreshRanges();
            });
        }
        refreshRanges();
        var metrics = element("stats_metrics", UIElement.class);
        for (var candidate : Metric.values()) {
            var card = new Card(candidate);
            card.addClass("stats-card");
            card.addEventListener(UIEvents.MOUSE_DOWN, event -> {
                metric = candidate;
                refreshKinds();
            });
            cards.add(card);
            metrics.addChild(card);
        }
        var chartBox = element("stats_chart", UIElement.class);
        var kindRow = element("stats_kinds", UIElement.class);
        chartBox.removeChild(kindRow);
        chart.addClass("stats-plot");
        chartBox.addChild(chart);
        chart.addChild(kindRow);
        ranking.addClass("stats-plot");
        element("stats_rank", UIElement.class).addChild(ranking);
        refreshKinds();
    }

    /** Asks the server for this page's figures while it is open, and stops them when another page is shown. */
    void setOpen(boolean open) {
        if (this.open == open) return;
        this.open = open;
        request.accept(open ? range.name() : "");
    }

    /** The topology's member networks, in its order, so each keeps the colour it has there. */
    void acceptChoices(JsonObject root) {
        if (!root.has("networks")) return;
        networks.clear();
        int index = 0;
        for (var value : root.getAsJsonArray("networks")) {
            var json = value.getAsJsonObject();
            var id = json.get("id").getAsString();
            var name = json.has("name") ? json.get("name").getAsString() : "";
            networks.add(new Net(id, FederationTheme.networkAccent(index++), FederationTopologyView.displayName(id, name),
                    name.isEmpty() ? id.substring(0, 4).toUpperCase(Locale.ROOT) : name));
        }
        if (network != null && networks.stream().noneMatch(net -> net.id().equals(network))) network = null;
        refreshChips();
    }

    void accept(Tag tag) {
        snapshot = tag instanceof CompoundTag compound ? StatisticsPayload.decode(compound, FederationStatisticsView::readKey) : null;
        shownNetworks = snapshot == null ? 0 : snapshot.networks().size();
        if (snapshot == null) return;
        var present = new LinkedHashSet<>(List.of(ITEMS, FLUIDS));
        snapshot.networks().values().forEach(series -> series.keySet().stream()
                .filter(name -> name.startsWith(StatisticsNames.STORED + "/"))
                .forEach(name -> present.add(name.substring(StatisticsNames.STORED.length() + 1))));
        snapshot.moved().forEach(flow -> present.add(flow.type()));
        present.removeIf(type -> keyType(type) == null);
        if (!present.equals(new LinkedHashSet<>(kinds))) {
            kinds.clear();
            kinds.addAll(present);
            if (!kinds.contains(kind)) kind = ITEMS;
            refreshKinds();
        }
    }

    /** TEST-ONLY: how many networks the last figures from the server covered; 0 before any arrived. */
    public static int shownNetworks() {
        return shownNetworks;
    }

    private static @Nullable AEKey readKey(Tag tag) {
        if (!(tag instanceof CompoundTag compound)) return null;
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return null;
        try {
            return AEKey.fromTagGeneric(level.registryAccess(), compound);
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    private void refreshRanges() {
        for (var candidate : StatisticsRange.values()) {
            var chip = element("stats_range_" + candidate.name(), Button.class);
            chip.removeClass("selected");
            if (candidate == range) chip.addClass("selected");
        }
    }

    private void refreshChips() {
        var ids = new ArrayList<String>();
        ids.add("");
        networks.forEach(net -> ids.add(net.id() + net.tag()));
        var row = element("stats_networks", UIElement.class);
        if (!ids.equals(shownChips)) {
            shownChips = ids;
            row.clearAllChildren();
            row.addChild(chip(null, tr("all_networks")));
            for (var net : networks) {
                row.addChild(chip(net.id(), Component.empty()
                        .append(Component.literal("■ ").withStyle(Style.EMPTY.withColor(net.color() & 0xffffff))).append(net.name())));
            }
        }
        for (var child : row.getChildren()) {
            child.removeClass("selected");
            if (java.util.Objects.equals(child.getId(), chipId(network))) child.addClass("selected");
        }
    }

    private static String chipId(@Nullable String network) {
        return "stats_network_" + (network == null ? "all" : network);
    }

    private Button chip(@Nullable String id, Component text) {
        var button = new Button();
        button.setId(chipId(id));
        button.addClass("network-chip");
        button.setText(text);
        int width = net.minecraft.client.Minecraft.getInstance().font.width(text) + 8;
        button.layout(layout -> layout.width(width));
        button.setOnClick(event -> {
            network = id;
            refreshChips();
        });
        return button;
    }

    /** The kind toggle shows on the stock and transfer charts only, one chip per resource kind with figures. */
    private void refreshKinds() {
        var row = element("stats_kinds", UIElement.class);
        row.clearAllChildren();
        boolean shown = metric == Metric.STORED || metric == Metric.MOVED;
        row.setDisplay(shown);
        if (shown) {
            for (var candidate : kinds) {
                var type = keyType(candidate);
                if (type == null) continue;
                var button = new Button();
                button.addClass("kind-chip");
                if (candidate.equals(kind)) button.addClass("selected");
                button.setText(type.getDescription());
                button.setOnClick(event -> {
                    kind = candidate;
                    refreshKinds();
                });
                row.addChild(button);
            }
        }
    }

    // ---- figures ----------------------------------------------------------------------------------------------

    /** One row of a chart's tooltip: a swatch (0 for none), its text and the text's colour. */
    private record TipRow(int swatch, Component text, int color) {
    }

    /** A drawable layer of a figure. */
    private sealed interface Layer permits Line, Area, Bar, Mark {
    }

    private record Line(float[] values, int color, float width, float alpha, int sign) implements Layer {
    }

    private record Area(float[] lo, float[] hi, int color, float fillAlpha, int sign) implements Layer {
    }

    private record Bar(int index, float from, float to, int color, int sign) implements Layer {
    }

    private record Mark(int index, int color) implements Layer {
    }

    /** What a chart draws for a metric: its scale, layers, labels, the gaps to hatch and its tooltip. */
    private static final class Figure {
        Component title = Component.empty();
        Component unit = Component.empty();
        Component value = Component.empty();
        /** A card's headline when {@link #value}, one figure per network, is too wide for the card. */
        @Nullable Component compact;
        int valueColor = FederationTheme.DARK_TEXT;
        boolean mirror;
        double max = 1;
        String top = "";
        String bottom = "";
        @Nullable Component empty;
        Component sampling = tr("tip.sampled");
        final List<Layer> layers = new ArrayList<>();
        /** Spans {@code [first, last, colour]} where a network's figures are missing although the server ran. */
        final List<int[]> missing = new ArrayList<>();
        IntFunction<List<TipRow>> tip = index -> List.of();
    }

    private Figure figure(Metric shown) {
        var figure = new Figure();
        var data = snapshot;
        figure.title = tr("title." + shown.key());
        if (data == null) {
            figure.empty = tr("waiting");
            return figure;
        }
        int n = data.range().buckets();
        var observed = data.observed();
        var shownNets = networks.stream().filter(net -> data.networks().containsKey(net.id())).toList();
        boolean all = network == null;
        for (var net : shownNets) {
            if (!all && !net.id().equals(network)) continue;
            var cpus = series(net, StatisticsNames.CPUS);
            for (int index = 0; index < n; index++) {
                if (observed[index] == 0 || cpus != null && !Float.isNaN(cpus[index])) continue;
                int end = index;
                while (end + 1 < n && observed[end + 1] > 0 && (cpus == null || Float.isNaN(cpus[end + 1]))) end++;
                figure.missing.add(new int[] {index, end, net.color()});
                index = end;
            }
        }
        switch (shown) {
            case POWER -> power(figure, shownNets, n);
            case STORED -> stored(figure, shownNets, n);
            case MOVED -> moved(figure, data, shownNets, n);
            case CRAFTING -> crafting(figure, data, shownNets, n);
            case CPUS, CHANNELS -> gauges(figure, shown, shownNets, n);
        }
        return figure;
    }

    private float @Nullable [] series(Net net, String name) {
        var data = snapshot;
        if (data == null) return null;
        var series = data.networks().get(net.id());
        return series == null ? null : series.get(name);
    }

    private boolean dimmed(Net net) {
        return network != null && !net.id().equals(network);
    }

    private void power(Figure figure, List<Net> nets, int n) {
        figure.unit = tr("unit.power");
        figure.max = 100;
        figure.top = "100%";
        figure.bottom = "0";
        var pools = new ArrayList<float[]>();
        for (var net : nets) {
            var values = series(net, StatisticsNames.POWER);
            if (values != null) figure.layers.add(new Line(values, net.color(), 1, dimmed(net) ? 0.25f : 0.95f, 1));
            var pool = series(net, StatisticsNames.POOL);
            if (pool != null && pools.stream().noneMatch(known -> Arrays.equals(known, pool))) pools.add(pool);
        }
        pools.forEach(pool -> figure.layers.add(new Line(pool, FederationTheme.QUARTZ, 1.5f, 1, 1)));
        // The headline: the shared pool when every network draws on it, otherwise each network's own.
        var latest = new ArrayList<Float>();
        for (var net : nets) {
            if (dimmed(net)) continue;
            latest.add(last(series(net, StatisticsNames.POWER)));
        }
        Float poolValue = pools.size() == 1 && network == null && nets.stream().allMatch(net -> series(net, StatisticsNames.POOL) != null)
                ? last(pools.get(0)) : null;
        if (poolValue != null && !poolValue.isNaN()) {
            figure.value = Component.literal(Math.round(poolValue) + "%");
            figure.valueColor = poolValue < 25 ? FederationTheme.WARN : FederationTheme.OK;
        } else {
            figure.value = Component.literal(joined(latest) + (latest.stream().anyMatch(value -> !value.isNaN()) ? "%" : ""));
            float lowest = (float) latest.stream().filter(value -> !value.isNaN()).mapToDouble(Float::doubleValue).min().orElse(Double.NaN);
            figure.compact = Float.isNaN(lowest) ? null : tr("value.lowest", Math.round(lowest) + "%");
            figure.valueColor = Float.isNaN(lowest) ? FederationTheme.DARK_MUTED : lowest < 25 ? FederationTheme.WARN : FederationTheme.OK;
        }
        figure.tip = index -> {
            var rows = new ArrayList<TipRow>();
            for (var pool : pools) {
                if (!Float.isNaN(pool[index])) rows.add(new TipRow(FederationTheme.QUARTZ, tr("tip.pool", Math.round(pool[index]) + "%"), FederationTheme.DARK_TEXT));
            }
            for (var net : nets) {
                if (dimmed(net)) continue;
                var values = series(net, StatisticsNames.POWER);
                rows.add(values == null || Float.isNaN(values[index]) ? noData(net)
                        : new TipRow(net.color(), Component.literal(net.tag() + " " + Math.round(values[index]) + "%"), FederationTheme.DARK_TEXT));
            }
            return rows;
        };
    }

    private void stored(Figure figure, List<Net> nets, int n) {
        var type = keyType(kind);
        double perUnit = type == null ? 1 : Math.max(1, type.getAmountPerUnit());
        String symbol = unitSymbol(kind);
        figure.title = tr("title.stored");
        figure.unit = symbol.isEmpty() ? tr("unit.items") : Component.literal(symbol.trim());
        figure.sampling = tr("tip.stock");
        var name = StatisticsNames.STORED + "/" + kind;
        var shown = nets.stream().filter(net -> !dimmed(net)).toList();
        var lo = new float[n];
        var counted = new boolean[n];
        for (var net : shown) {
            var values = scaled(series(net, name), perUnit, n);
            var hi = new float[n];
            var base = lo.clone();
            for (int index = 0; index < n; index++) {
                hi[index] = Float.isNaN(values[index]) ? lo[index] : lo[index] + values[index];
                if (Float.isNaN(values[index])) base[index] = Float.NaN;
                else counted[index] = true;
            }
            figure.layers.add(new Area(base, mask(hi, base), net.color(), 0.5f, 1));
            lo = hi;
        }
        double top = 1;
        for (float value : lo) if (!Float.isNaN(value)) top = Math.max(top, value);
        figure.max = niceMax(top * 1.1);
        figure.top = fmt(figure.max);
        figure.bottom = "0";
        // The headline is the newest point some network counted, not a total of nothing.
        var newest = lo.clone();
        for (int index = 0; index < n; index++) if (!counted[index]) newest[index] = Float.NaN;
        figure.value = Component.literal(fmt(last(newest)) + symbol);
        final var totals = lo;
        figure.tip = index -> {
            var rows = new ArrayList<TipRow>();
            for (var net : shown) {
                var values = scaled(series(net, name), perUnit, n);
                rows.add(Float.isNaN(values[index]) ? noData(net)
                        : new TipRow(net.color(), Component.literal(net.tag() + " " + fmt(values[index]) + symbol), FederationTheme.DARK_TEXT));
            }
            if (shown.size() > 1) rows.add(new TipRow(0, tr("tip.domain", fmt(totals[index]) + symbol), FederationTheme.DARK_TITLE));
            return rows;
        };
    }

    private void moved(Figure figure, StatisticsPayload.Snapshot<AEKey> data, List<Net> nets, int n) {
        var type = keyType(kind);
        double perUnit = type == null ? 1 : Math.max(1, type.getAmountPerUnit());
        String symbol = unitSymbol(kind);
        String rateUnit = symbol + "/s";
        figure.title = tr("title.moved");
        figure.unit = symbol.isEmpty() ? tr("unit.items_per_second") : Component.literal(symbol.trim() + "/s");
        figure.sampling = tr("tip.counted");
        figure.mirror = true;
        var flows = data.moved().stream().filter(flow -> flow.type().equals(kind)).toList();
        if (flows.isEmpty() && !data.storageRules()) figure.empty = tr("empty.storage");
        var observed = data.observed();
        double top = 1;
        if (network == null) {
            var ins = new ArrayList<float[]>();
            var outs = new ArrayList<float[]>();
            for (var net : nets) {
                var in = new float[n];
                var out = new float[n];
                for (var flow : flows) {
                    for (int index = 0; index < n; index++) {
                        float rate = rate(flow.sums()[index], observed[index], perUnit);
                        if (flow.to().equals(net.id())) in[index] += rate;
                        if (flow.from().equals(net.id())) out[index] += rate;
                    }
                }
                gap(in, observed, series(net, StatisticsNames.CPUS));
                gap(out, observed, series(net, StatisticsNames.CPUS));
                ins.add(in);
                outs.add(out);
                top = Math.max(top, Math.max(peak(in), peak(out)));
                var zero = zeros(in);
                figure.layers.add(new Area(zero, in, net.color(), 0.12f, 1));
                figure.layers.add(new Area(zeros(out), out, net.color(), 0.12f, -1));
            }
            figure.tip = index -> {
                var rows = new ArrayList<TipRow>();
                for (int at = 0; at < nets.size(); at++) {
                    var net = nets.get(at);
                    rows.add(Float.isNaN(ins.get(at)[index]) ? noData(net) : new TipRow(net.color(), tr("tip.in_out", net.tag(),
                            fmt(ins.get(at)[index]) + rateUnit, fmt(outs.get(at)[index]) + rateUnit), FederationTheme.DARK_TEXT));
                }
                return rows;
            };
            double total = flows.stream().mapToDouble(flow -> sum(flow.sums())).sum() / perUnit;
            figure.value = tr("value.moved", fmt(total / Math.max(1, seconds(observed))) + rateUnit);
        } else {
            var net = nets.stream().filter(candidate -> candidate.id().equals(network)).findFirst().orElse(null);
            var cpus = net == null ? null : series(net, StatisticsNames.CPUS);
            var ins = flows.stream().filter(flow -> flow.to().equals(network)).toList();
            var outs = flows.stream().filter(flow -> flow.from().equals(network)).toList();
            for (var side : List.of(ins, outs)) {
                int sign = side == ins ? 1 : -1;
                var lo = new float[n];
                for (var flow : side) {
                    var hi = new float[n];
                    for (int index = 0; index < n; index++) hi[index] = lo[index] + rate(flow.sums()[index], observed[index], perUnit);
                    var base = lo.clone();
                    gap(base, observed, cpus);
                    var peer = net(sign > 0 ? flow.from() : flow.to());
                    figure.layers.add(new Area(base, mask(hi, base), peer == null ? FederationTheme.EDGE : peer.color(), 0.55f, sign));
                    lo = hi;
                }
                top = Math.max(top, peak(lo));
            }
            figure.tip = index -> {
                var rows = new ArrayList<TipRow>();
                if (observed[index] > 0 && cpus != null && Float.isNaN(cpus[index])) {
                    rows.add(net == null ? new TipRow(0, Component.empty(), 0) : noData(net));
                    return rows;
                }
                for (var flow : ins) {
                    var peer = net(flow.from());
                    rows.add(new TipRow(peer == null ? FederationTheme.EDGE : peer.color(), tr("tip.from", peer == null ? "?" : peer.tag(),
                            fmt(rate(flow.sums()[index], observed[index], perUnit)) + rateUnit), FederationTheme.DARK_TEXT));
                }
                for (var flow : outs) {
                    var peer = net(flow.to());
                    rows.add(new TipRow(peer == null ? FederationTheme.EDGE : peer.color(), tr("tip.to", peer == null ? "?" : peer.tag(),
                            fmt(rate(flow.sums()[index], observed[index], perUnit)) + rateUnit), FederationTheme.DARK_TEXT));
                }
                return rows;
            };
            double total = ins.stream().mapToDouble(flow -> sum(flow.sums())).sum() / perUnit;
            figure.value = tr("value.in", fmt(total / Math.max(1, seconds(observed))) + rateUnit);
        }
        figure.max = niceMax(top);
        figure.top = tr("axis.in", fmt(figure.max)).getString();
        figure.bottom = tr("axis.out", fmt(figure.max)).getString();
    }

    private void crafting(Figure figure, StatisticsPayload.Snapshot<AEKey> data, List<Net> nets, int n) {
        figure.unit = tr("unit.patterns");
        figure.sampling = tr("tip.counted");
        if (data.pushed().isEmpty() && !data.craftingRules()) figure.empty = tr("empty.crafting");
        var observed = data.observed();
        IntFunction<float[]> perMinute = at -> {
            var flow = data.pushed().get(at);
            var out = new float[n];
            for (int index = 0; index < n; index++) out[index] = rate(flow.sums()[index], observed[index], 1) * 60;
            return out;
        };
        double top = 4;
        var pushed = data.pushed();
        if (network == null) {
            var stack = new float[n];
            for (int at = 0; at < pushed.size(); at++) {
                var values = perMinute.apply(at);
                var requester = net(pushed.get(at).from());
                for (int index = 0; index < n; index++) {
                    if (Float.isNaN(values[index]) || values[index] <= 0) continue;
                    figure.layers.add(new Bar(index, stack[index], stack[index] + values[index],
                            requester == null ? FederationTheme.EDGE : requester.color(), 1));
                    stack[index] += values[index];
                }
            }
            top = Math.max(top, peak(stack) * 1.15);
            figure.tip = index -> {
                var rows = new ArrayList<TipRow>();
                for (int at = 0; at < pushed.size(); at++) {
                    var requester = net(pushed.get(at).from());
                    var executor = net(pushed.get(at).to());
                    var value = perMinute.apply(at)[index];
                    if (Float.isNaN(value) || value <= 0) continue;
                    rows.add(new TipRow(requester == null ? FederationTheme.EDGE : requester.color(), tr("tip.ran_on",
                            requester == null ? "?" : requester.tag(), fmt(value), executor == null ? "?" : executor.tag()), FederationTheme.DARK_TEXT));
                }
                endRows(rows, nets, index);
                return rows;
            };
        } else {
            figure.mirror = true;
            var sent = new float[n];
            var ran = new float[n];
            for (int at = 0; at < pushed.size(); at++) {
                var flow = pushed.get(at);
                var values = perMinute.apply(at);
                boolean mine = flow.from().equals(network);
                if (!mine && !flow.to().equals(network)) continue;
                var peer = net(mine ? flow.to() : flow.from());
                var stack = mine ? sent : ran;
                for (int index = 0; index < n; index++) {
                    if (Float.isNaN(values[index]) || values[index] <= 0) continue;
                    figure.layers.add(new Bar(index, stack[index], stack[index] + values[index],
                            peer == null ? FederationTheme.EDGE : peer.color(), mine ? 1 : -1));
                    stack[index] += values[index];
                }
            }
            top = Math.max(top, Math.max(peak(sent), peak(ran)) * 1.15);
            figure.tip = index -> {
                var rows = new ArrayList<TipRow>();
                for (int at = 0; at < pushed.size(); at++) {
                    var flow = pushed.get(at);
                    boolean mine = flow.from().equals(network);
                    if (!mine && !flow.to().equals(network)) continue;
                    var value = perMinute.apply(at)[index];
                    if (Float.isNaN(value) || value <= 0) continue;
                    var peer = net(mine ? flow.to() : flow.from());
                    rows.add(new TipRow(peer == null ? FederationTheme.EDGE : peer.color(), tr(mine ? "tip.sent_to" : "tip.ran_for",
                            fmt(value), peer == null ? "?" : peer.tag()), FederationTheme.DARK_TEXT));
                }
                endRows(rows, nets, index);
                return rows;
            };
        }
        // A job that ended is marked along the top: green finished, red cancelled.
        for (var net : nets) {
            if (dimmed(net)) continue;
            var done = series(net, StatisticsNames.DONE);
            var cancelled = series(net, StatisticsNames.CANCELLED);
            for (int index = 0; index < n; index++) {
                if (cancelled != null && cancelled[index] > 0) figure.layers.add(new Mark(index, FederationTheme.ERROR));
                else if (done != null && done[index] > 0) figure.layers.add(new Mark(index, FederationTheme.OK));
            }
        }
        figure.max = niceMax(top);
        figure.top = figure.mirror ? tr("axis.sent", fmt(figure.max)).getString() : fmt(figure.max);
        figure.bottom = figure.mirror ? tr("axis.ran", fmt(figure.max)).getString() : "0";
        double pushes = pushed.stream().filter(flow -> network == null || flow.from().equals(network) || flow.to().equals(network))
                .mapToDouble(flow -> sum(flow.sums())).sum();
        figure.value = tr("value.per_minute", fmt(pushes * 60 / Math.max(1, seconds(observed))));
    }

    private void endRows(List<TipRow> rows, List<Net> nets, int index) {
        for (var net : nets) {
            if (dimmed(net)) continue;
            var done = series(net, StatisticsNames.DONE);
            var cancelled = series(net, StatisticsNames.CANCELLED);
            if (done != null && done[index] > 0) rows.add(new TipRow(FederationTheme.OK, tr("tip.done", net.tag(), fmt(done[index])), FederationTheme.DARK_TEXT));
            if (cancelled != null && cancelled[index] > 0) rows.add(new TipRow(FederationTheme.ERROR, tr("tip.cancelled", net.tag(), fmt(cancelled[index])), FederationTheme.DARK_TEXT));
        }
    }

    private void gauges(Figure figure, Metric shown, List<Net> nets, int n) {
        var name = shown == Metric.CPUS ? StatisticsNames.BUSY_CPUS : StatisticsNames.CHANNELS;
        figure.unit = tr("unit." + shown.key());
        double top = 1;
        var latest = new ArrayList<Float>();
        for (var net : nets) {
            var values = series(net, name);
            if (values == null) continue;
            top = Math.max(top, peak(values));
            figure.layers.add(new Line(values, net.color(), 1, dimmed(net) ? 0.25f : 0.95f, 1));
            if (!dimmed(net)) latest.add(last(values));
        }
        figure.max = niceMax(top * 1.1);
        figure.top = fmt(figure.max);
        figure.bottom = "0";
        figure.value = Component.literal(joined(latest));
        double total = latest.stream().filter(value -> !value.isNaN()).mapToDouble(Float::doubleValue).sum();
        figure.compact = latest.stream().allMatch(value -> value.isNaN()) ? null : tr("value.total", fmt(total));
        figure.tip = index -> {
            var rows = new ArrayList<TipRow>();
            for (var net : nets) {
                if (dimmed(net)) continue;
                var values = series(net, name);
                rows.add(values == null || Float.isNaN(values[index]) ? noData(net)
                        : new TipRow(net.color(), Component.literal(net.tag() + " " + fmt(values[index])), FederationTheme.DARK_TEXT));
            }
            return rows;
        };
    }

    private @Nullable Net net(String id) {
        return networks.stream().filter(net -> net.id().equals(id)).findFirst().orElse(null);
    }

    private static TipRow noData(Net net) {
        return new TipRow(net.color(), tr("tip.no_data", net.tag()), FederationTheme.DARK_MUTED);
    }

    // ---- numbers ----------------------------------------------------------------------------------------------

    /** A counter's rate per observed second in one point, divided into display units; NaN where nothing ran. */
    static float rate(float sum, int seconds, double perUnit) {
        return seconds > 0 ? (float) (sum / seconds / perUnit) : Float.NaN;
    }

    private static float[] scaled(float @Nullable [] values, double perUnit, int n) {
        var out = new float[n];
        if (values == null) {
            Arrays.fill(out, Float.NaN);
            return out;
        }
        for (int index = 0; index < n; index++) out[index] = (float) (values[index] / perUnit);
        return out;
    }

    /** Points of {@code values} where the server did not run, or the network was not loaded, become NaN. */
    private static void gap(float[] values, int[] observed, float @Nullable [] presence) {
        for (int index = 0; index < values.length; index++) {
            if (observed[index] == 0 || presence == null || Float.isNaN(presence[index])) values[index] = Float.NaN;
        }
    }

    private static float[] mask(float[] values, float[] base) {
        var out = values.clone();
        for (int index = 0; index < out.length; index++) if (Float.isNaN(base[index])) out[index] = Float.NaN;
        return out;
    }

    private static float[] zeros(float[] like) {
        var out = new float[like.length];
        for (int index = 0; index < out.length; index++) out[index] = Float.isNaN(like[index]) ? Float.NaN : 0;
        return out;
    }

    private static double peak(float[] values) {
        double top = 0;
        for (float value : values) if (!Float.isNaN(value)) top = Math.max(top, value);
        return top;
    }

    private static double sum(float[] values) {
        double total = 0;
        for (float value : values) if (!Float.isNaN(value)) total += value;
        return total;
    }

    private static long seconds(int[] observed) {
        long total = 0;
        for (int value : observed) total += value;
        return total;
    }

    private static float last(float @Nullable [] values) {
        if (values == null) return Float.NaN;
        for (int index = values.length - 1; index >= 0; index--) if (!Float.isNaN(values[index])) return values[index];
        return Float.NaN;
    }

    private static String joined(List<Float> values) {
        if (values.isEmpty()) return "–";
        var parts = new ArrayList<String>();
        values.forEach(value -> parts.add(value.isNaN() ? "–" : fmt(value)));
        return String.join(" · ", parts);
    }

    /** The scale's top: 1, 2, 2.5 or 5 times a power of ten, at least {@code value}. */
    static double niceMax(double value) {
        if (!(value > 0)) return 1;
        double power = Math.pow(10, Math.floor(Math.log10(value)));
        double mantissa = value / power;
        return (mantissa <= 1 ? 1 : mantissa <= 2 ? 2 : mantissa <= 2.5 ? 2.5 : mantissa <= 5 ? 5 : 10) * power;
    }

    /** 1.2M, 69k, 1.5k, 2.4, 12. */
    static String fmt(double value) {
        if (Double.isNaN(value)) return "–";
        double magnitude = Math.abs(value);
        if (magnitude >= 1e9) return String.format(Locale.ROOT, "%.1fG", value / 1e9);
        if (magnitude >= 1e6) return String.format(Locale.ROOT, "%.1fM", value / 1e6);
        if (magnitude >= 1e4) return Math.round(value / 1e3) + "k";
        if (magnitude >= 1e3) return String.format(Locale.ROOT, "%.1fk", value / 1e3);
        if (magnitude > 0 && magnitude < 10 && Math.round(magnitude) != magnitude) return String.format(Locale.ROOT, "%.1f", value);
        return Long.toString(Math.round(value));
    }

    /** How long before the newest point {@code index} is: "-9m 55s", "-23h 50m", "now". */
    static String ago(int index, StatisticsRange range) {
        long seconds = (long) (range.buckets() - 1 - index) * range.seconds();
        if (seconds == 0) return tr("ago.now").getString();
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        long rest = seconds % 60;
        if (hours > 0) return "-" + hours + "h" + (minutes > 0 ? " " + minutes + "m" : "");
        if (minutes > 0) return "-" + minutes + "m" + (rest > 0 ? " " + rest + "s" : "");
        return "-" + rest + "s";
    }

    private static appeng.api.stacks.@Nullable AEKeyType keyType(String id) {
        var location = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (location == null) return null;
        try {
            return appeng.api.stacks.AEKeyTypes.get(location);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /** " B" for fluids, empty for items: what follows a count of {@code kind}. */
    private static String unitSymbol(String kind) {
        var type = keyType(kind);
        var symbol = type == null ? null : type.getUnitSymbol();
        return symbol == null ? "" : " " + symbol;
    }

    // ---- drawing ----------------------------------------------------------------------------------------------

    private static net.minecraft.client.gui.Font font() {
        return com.lowdragmc.lowdraglib2.gui.LDLibFonts.font();
    }

    private static void text(GUIContext context, Component text, float x, float y, int color) {
        com.lowdragmc.lowdraglib2.gui.LDLibFonts.drawText(context.graphics, font(), text, x, y, color, false);
    }

    private static void rect(GUIContext context, float x, float y, float width, float height, int color) {
        if (width > 0 && height > 0) DrawerHelper.drawSolidRect(context.graphics, x, y, width, height, color);
    }

    private static void frame(GUIContext context, float x, float y, float width, float height, int fill, int edge) {
        rect(context, x, y, width, height, edge);
        rect(context, x + 1, y + 1, width - 2, height - 2, fill);
    }

    private static int alpha(int color, float alpha) {
        return Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24 | color & 0xffffff;
    }

    /** Draws {@code figure} into the plot rectangle: for a full chart its grid, then hatched gaps and the layers. */
    private void plot(GUIContext context, Figure figure, int n, float px, float py, float pw, float ph, boolean full) {
        var data = snapshot;
        if (full) {
            rect(context, px, py, pw, ph, FederationTheme.WELL);
            for (int row = 0; row <= 4; row++) {
                float y = py + Math.round(row * (ph - 1) / 4f);
                rect(context, px, y, pw, 1, figure.mirror && row == 2 ? GRID_ZERO : GRID);
            }
            for (int column = 1; column < 6; column++) rect(context, px + Math.round(column * pw / 6), py, 1, ph, GRID);
        }
        if (data == null || figure.empty != null) return;
        float step = pw / n;
        // Where the server did not run, and where a network was not loaded, the chart is hatched, not drawn as zero.
        var observed = data.observed();
        for (int index = 0; index < n; index++) {
            if (observed[index] > 0) continue;
            int end = index;
            while (end + 1 < n && observed[end + 1] == 0) end++;
            hatch(context, px + index * step, py, (end - index + 1) * step, ph, alpha(FederationTheme.DARK_MUTED, 0.45f));
            index = end;
        }
        for (var missing : figure.missing) {
            hatch(context, px + missing[0] * step, py, (missing[1] - missing[0] + 1) * step, ph, alpha(missing[2], 0.5f));
        }
        float max = (float) figure.max;
        boolean mirror = figure.mirror;
        java.util.function.BiFunction<Float, Integer, Float> y = (value, sign) -> mirror
                ? py + ph / 2 - sign * value / max * (ph / 2 - 1)
                : py + ph - 1 - value / max * (ph - 2);
        for (var layer : figure.layers) {
            switch (layer) {
                case Area area -> {
                    int fill = alpha(area.color(), area.fillAlpha());
                    for (int column = 0; column < Math.ceil(pw); column++) {
                        float lo = interpolate(area.lo(), (column + 0.5f) / pw * n - 0.5f);
                        float hi = interpolate(area.hi(), (column + 0.5f) / pw * n - 0.5f);
                        if (Float.isNaN(lo) || Float.isNaN(hi) || hi <= lo) continue;
                        float y0 = y.apply(Math.min(hi, max), area.sign());
                        float y1 = y.apply(Math.min(lo, max), area.sign());
                        rect(context, px + column, Math.min(y0, y1), Math.min(1, pw - column), Math.abs(y1 - y0), fill);
                    }
                    polyline(context, area.hi(), n, px, step, value -> y.apply(Math.min(value, max), area.sign()),
                            area.color(), full ? 1 : 0.8f, 1);
                }
                case Line line -> polyline(context, line.values(), n, px, step,
                        value -> y.apply(Math.min(value, max), line.sign()), line.color(), full ? line.width() : 1, line.alpha());
                case Bar bar -> {
                    float y0 = y.apply(Math.min(bar.from(), max), bar.sign());
                    float y1 = y.apply(Math.min(bar.to(), max), bar.sign());
                    float width = Math.max(1, step - (n > 80 ? 0.4f : 1));
                    rect(context, px + bar.index() * step, Math.min(y0, y1), width, Math.max(1, Math.abs(y1 - y0)), bar.color());
                }
                case Mark mark -> rect(context, px + (mark.index() + 0.5f) * step - 1.5f, py + 1, 3, 3, mark.color());
            }
        }
    }

    /** {@code values} at a fractional point; the nearer point where only one side has a value. */
    private static float interpolate(float[] values, float at) {
        int left = (int) Math.floor(at);
        int right = left + 1;
        float leftValue = left >= 0 && left < values.length ? values[left] : Float.NaN;
        float rightValue = right >= 0 && right < values.length ? values[right] : Float.NaN;
        if (!Float.isNaN(leftValue) && !Float.isNaN(rightValue)) return leftValue + (rightValue - leftValue) * (at - left);
        if (!Float.isNaN(leftValue) && at - left <= 0.5f) return leftValue;
        if (!Float.isNaN(rightValue) && right - at <= 0.5f) return rightValue;
        return Float.NaN;
    }

    private static void polyline(GUIContext context, float[] values, int n, float px, float step,
            java.util.function.Function<Float, Float> y, int color, float width, float alpha) {
        int argb = alpha(color, alpha);
        var points = new ArrayList<Vector2f>();
        for (int index = 0; index <= n; index++) {
            if (index == n || Float.isNaN(values[index])) {
                if (points.size() == 1) {
                    var point = points.get(0);
                    rect(context, point.x - 0.5f, point.y - 0.5f, 1, 1, argb);
                } else if (points.size() > 1) {
                    DrawerHelper.drawTexLines(context.graphics, com.lowdragmc.lowdraglib2.client.shader.LDLibRenderTypes.graphWire(),
                            points, argb, argb, width);
                }
                points = new ArrayList<>();
                continue;
            }
            points.add(new Vector2f(px + (index + 0.5f) * step, y.apply(values[index])));
        }
    }

    /** Diagonal one-pixel stripes four pixels apart, clipped to the rectangle. */
    private static void hatch(GUIContext context, float x, float y, float width, float height, int color) {
        for (float offset = 0; offset < width + height; offset += 4) {
            float startX = Math.min(offset, width);
            float endX = Math.max(0, offset - height);
            var line = List.of(new Vector2f(x + startX, y + offset - startX), new Vector2f(x + endX, y + offset - endX));
            DrawerHelper.drawTexLines(context.graphics, com.lowdragmc.lowdraglib2.client.shader.LDLibRenderTypes.graphWire(),
                    line, color, color, 1);
        }
    }

    /** One metric's card: its name and headline figure over its sparkline; a click shows it large. */
    private final class Card extends UIElement {
        private final Metric shown;

        Card(Metric shown) {
            this.shown = shown;
            setId("stats_card_" + shown.key());
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            float x = getPositionX();
            float y = getPositionY();
            float width = getSizeWidth();
            float height = getSizeHeight();
            boolean selected = shown == metric;
            frame(context, x, y, width, height, selected ? CARD_SELECTED : CARD, selected ? FederationTheme.SELECT : CARD_EDGE);
            var figure = figure(shown);
            Component name = shown == Metric.STORED ? storedName() : tr("metric." + shown.key());
            var value = snapshot == null ? Component.literal("–") : figure.value;
            if (figure.compact != null && font().width(value) > (width - 14) / 2) value = figure.compact;
            int valueWidth = font().width(value);
            text(context, value, x + width - 5 - valueWidth, y + 4, figure.valueColor);
            var clipped = font().plainSubstrByWidth(name.getString(), (int) Math.max(0, width - 14 - valueWidth));
            text(context, Component.literal(clipped), x + 5, y + 4, selected ? FederationTheme.DARK_TITLE : FederationTheme.DARK_TEXT);
            float top = y + 15;
            float plotHeight = height - 19;
            if (plotHeight >= 4 && snapshot != null) {
                plot(context, figure, snapshot.range().buckets(), x + 5, top, width - 10, plotHeight, false);
            }
        }

        private Component storedName() {
            var type = keyType(kind);
            return tr("metric.stored", type == null ? Component.empty() : type.getDescription());
        }
    }

    /** The chosen metric large: axes, the time under it, and a tooltip at the hovered point. */
    private final class Chart extends UIElement {
        /** Where the pointer last moved over the chart, from the move events the screen sends; NaN once it left. */
        private float pointerX = Float.NaN;
        private float pointerY = Float.NaN;

        Chart() {
            addEventListener(UIEvents.MOUSE_MOVE, event -> {
                pointerX = event.x;
                pointerY = event.y;
            });
            addEventListener(UIEvents.MOUSE_LEAVE, event -> pointerX = Float.NaN);
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            float x = getPositionX();
            float y = getPositionY();
            float width = getSizeWidth();
            float height = getSizeHeight();
            frame(context, x, y, width, height, FederationTheme.CANVAS, CARD_EDGE);
            var figure = figure(metric);
            text(context, figure.title, x + 5, y + 4, FederationTheme.DARK_TITLE);
            text(context, figure.unit, x + width - 5 - font().width(figure.unit), y + 4, FederationTheme.DARK_MUTED);
            var data = snapshot;
            float labelWidth = Math.max(font().width(figure.top), font().width(figure.bottom));
            float px = x + Math.max(14, labelWidth + 6);
            float py = y + 17;
            float pw = x + width - 6 - px;
            float ph = y + height - 13 - py;
            if (pw < 8 || ph < 8) return;
            int n = data == null ? StatisticsRange.FIVE_SECONDS.buckets() : data.range().buckets();
            plot(context, figure, n, px, py, pw, ph, true);
            if (figure.empty != null) {
                int emptyWidth = font().width(figure.empty);
                text(context, figure.empty, px + (pw - emptyWidth) / 2, py + ph * 0.45f, FederationTheme.DARK_MUTED);
                return;
            }
            if (data == null) return;
            var range = data.range();
            text(context, Component.literal(figure.top), px - 3 - font().width(figure.top), py - 1, FederationTheme.DARK_MUTED);
            if (figure.mirror) text(context, Component.literal("0"), px - 3 - font().width("0"), py + ph / 2 - 4, FederationTheme.DARK_MUTED);
            text(context, Component.literal(figure.bottom), px - 3 - font().width(figure.bottom), py + ph - 8, FederationTheme.DARK_MUTED);
            var first = ago(0, range);
            var middle = ago(n / 2, range);
            var now = ago(n - 1, range);
            text(context, Component.literal(first), px, py + ph + 3, FederationTheme.DARK_MUTED);
            text(context, Component.literal(middle), px + pw / 2 - font().width(middle) / 2f, py + ph + 3, FederationTheme.DARK_MUTED);
            text(context, Component.literal(now), px + pw - font().width(now), py + ph + 3, FederationTheme.DARK_MUTED);
            if (data.observed()[0] == 0) text(context, tr("tip.no_record"), px + 3, py + 3, FederationTheme.DARK_TEXT);
            float mouseX = pointerX;
            float mouseY = pointerY;
            if (Float.isNaN(mouseX) || !isMouseOver(mouseX, mouseY) || mouseX < px || mouseX >= px + pw || mouseY < py || mouseY >= py + ph) return;
            int index = Math.max(0, Math.min(n - 1, (int) ((mouseX - px) / pw * n)));
            float crossX = px + (index + 0.5f) * pw / n;
            rect(context, crossX - 0.5f, py, 1, ph, alpha(FederationTheme.DARK_TITLE, 0.55f));
            tooltip(context, figure, data, index, crossX, py, x, width);
        }

        private void tooltip(GUIContext context, Figure figure, StatisticsPayload.Snapshot<AEKey> data, int index, float crossX,
                float py, float x, float width) {
            boolean observed = data.observed()[index] > 0;
            var head = Component.literal(ago(index, data.range()) + " · ").append(observed ? figure.sampling : tr("tip.no_record"));
            var rows = observed ? figure.tip.apply(index) : List.<TipRow>of();
            int boxWidth = font().width(head);
            for (var row : rows) boxWidth = Math.max(boxWidth, font().width(row.text()) + (row.swatch() != 0 ? 7 : 0));
            boxWidth += 8;
            int boxHeight = 5 + 10 * (rows.size() + 1);
            float left = crossX > x + width / 2 ? crossX - 5 - boxWidth : crossX + 5;
            float top = py + 4;
            context.graphics.pose().pushPose();
            context.graphics.pose().translate(0, 0, 200);
            frame(context, left, top, boxWidth, boxHeight, 0xf217141e, FederationTheme.SELECT);
            text(context, head, left + 4, top + 3, FederationTheme.DARK_MUTED);
            float rowY = top + 13;
            for (var row : rows) {
                float textX = left + 4;
                if (row.swatch() != 0) {
                    rect(context, textX, rowY + 2, 4, 4, row.swatch());
                    textX += 7;
                }
                text(context, row.text(), textX, rowY, row.color());
                rowY += 10;
            }
            context.graphics.pose().popPose();
        }
    }

    /** "Most moved": the keys that moved most over the range, items on the left and the chosen other kind right. */
    private final class Ranking extends UIElement {
        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            float x = getPositionX();
            float y = getPositionY();
            float width = getSizeWidth();
            float height = getSizeHeight();
            frame(context, x, y, width, height, FederationTheme.CANVAS, CARD_EDGE);
            text(context, tr("rank.title"), x + 5, y + 4, FederationTheme.DARK_TITLE);
            var span = tr("span." + range.name().toLowerCase(Locale.ROOT));
            text(context, span, x + width - 5 - font().width(span), y + 4, FederationTheme.DARK_MUTED);
            var data = snapshot;
            var second = kind.equals(ITEMS) ? FLUIDS : kind;
            float columnWidth = (width - 16) / 2;
            column(context, data, ITEMS, x + 5, y + 16, columnWidth, height - 20);
            column(context, data, second, x + 11 + columnWidth, y + 16, columnWidth, height - 20);
        }

        private void column(GUIContext context, StatisticsPayload.@Nullable Snapshot<AEKey> data, String type, float x, float y,
                float width, float height) {
            var keyType = keyType(type);
            var heading = keyType == null ? Component.literal(type) : keyType.getDescription();
            text(context, heading, x, y, FederationTheme.DARK_MUTED);
            if (data == null) return;
            double perUnit = keyType == null ? 1 : Math.max(1, keyType.getAmountPerUnit());
            var rows = data.ranking().stream()
                    .filter(ranked -> ranked.key().getType().getId().toString().equals(type))
                    .filter(ranked -> network == null || ranked.from().equals(network) || ranked.to().equals(network))
                    .toList();
            if (rows.isEmpty()) {
                text(context, tr("rank.nothing"), x, y + 12, FederationTheme.DARK_MUTED);
                return;
            }
            float rowY = y + 11;
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            for (var ranked : rows) {
                if (rowY + 22 > y + height) break;
                rect(context, x, rowY, width, 22, FederationTheme.WELL);
                appeng.api.client.AEKeyRendering.drawInGui(minecraft, context.graphics, Math.round(x + 3), Math.round(rowY + 3), ranked.key());
                var amount = fmt(ranked.amount() / perUnit) + unitSymbol(type);
                int amountWidth = font().width(amount);
                text(context, Component.literal(amount), x + width - 4 - amountWidth, rowY + 7, FederationTheme.DARK_TITLE);
                var name = font().plainSubstrByWidth(ranked.key().getDisplayName().getString(), (int) Math.max(0, width - 32 - amountWidth));
                text(context, Component.literal(name), x + 23, rowY + 2, FederationTheme.DARK_TEXT);
                var from = net(ranked.from());
                var to = net(ranked.to());
                MutableComponent route = Component.literal(from == null ? "?" : from.tag())
                        .withStyle(Style.EMPTY.withColor(from == null ? 0xaaa4b2 : from.color() & 0xffffff))
                        .append(Component.literal(" → ").withStyle(Style.EMPTY.withColor(0xaaa4b2)))
                        .append(Component.literal(to == null ? "?" : to.tag()).withStyle(Style.EMPTY.withColor(to == null ? 0xaaa4b2 : to.color() & 0xffffff)));
                text(context, route, x + 23, rowY + 12, -1);
                rowY += 24;
            }
        }
    }

    static MutableComponent tr(String key, Object... arguments) {
        return Component.translatable("ae2federation.ui.statistics." + key, arguments);
    }

    private <T> T element(String id, Class<T> type) {
        return ui.selectId(id, type).findFirst().orElseThrow(() -> new IllegalStateException("Missing UI element #" + id));
    }
}
