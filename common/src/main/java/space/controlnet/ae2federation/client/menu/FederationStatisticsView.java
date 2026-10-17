package space.controlnet.ae2federation.client.menu;

import appeng.api.stacks.AEKey;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import space.controlnet.ae2federation.client.policy.SearchText;
import space.controlnet.ae2federation.observability.statistics.StatisticsNames;
import space.controlnet.ae2federation.observability.statistics.StatisticsPayload;
import space.controlnet.ae2federation.observability.statistics.StatisticsRange;
import space.controlnet.ae2federation.power.OperatingPowerCosts;

/**
 * The domain's statistics page: a card per metric on the left with its sparkline, the chosen metric large on the right,
 * and below it what that metric has besides: stored power under power use, the items to draw under stock (searched,
 * picked and pinned), the keys that moved most, each network's jobs and the CPUs crafting now, every CPU, or each
 * network's channels. Networks keep the colour the topology gives them; items get colours of their own. The server
 * sends the figures only while this page is open ({@link #setOpen}), for the range, metric and resource kind chosen
 * here; which network is shown is chosen on the client.
 */
public final class FederationStatisticsView {
    private static final String ITEMS = "ae2:i";
    private static final String FLUIDS = "ae2:f";
    private static final int CARD = FederationTheme.CANVAS;
    private static final int CARD_SELECTED = 0xff2c2735;
    private static final int CARD_EDGE = 0xff121016;
    private static final int GRID = 0xff2c2935;
    private static final int GRID_ZERO = 0xff5a5568;
    /** Colours of items on the charts, in order of amount; what is not shown by item is grey. */
    private static final int[] ITEM_COLORS = {0xffd8b25f, 0xff8fc46a, 0xffe06464, 0xff4fb3c4, 0xffe48fb0, 0xffa48ff0,
            0xffd99a4e, 0xff6a9fe0, 0xffc9c26a, 0xff7fd6b0};
    private static final int OTHER = 0xff6a6478;
    /** Items the stock chart draws when the player has not picked any. */
    private static final int TOP_PICK = 6;
    /** Federation power's groups on the charts, in {@link OperatingPowerCosts.Group} order, and its converters' input. */
    private static final int[] GROUP_COLORS = {0xffe5c07b, 0xff98c379, 0xff56b6c2, 0xffb5a7ff, 0xfff4a6c6,
            0xff61afef};
    private static final int INPUT = FederationTheme.DARK_TITLE;

    /** Which items the stock chart draws: the most held, the pinned ones, none, or the ones the player ticked. */
    enum Pick {
        TOP, PINNED, NONE, CUSTOM
    }

    enum Metric {
        POWER, FEDPOWER, STORED, MOVED, CRAFTING, CPUS, CHANNELS;

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The metric's name in a request to the server. */
        String payload() {
            return this == STORED ? StatisticsPayload.STOCK : key();
        }
    }

    /** A member network as the topology shows it: its colour, its name and the short name tooltips use. */
    record Net(String id, int color, Component name, String tag) {
    }

    /** TEST-ONLY evidence: how many networks the last figures from the server covered, and for which request. */
    private static volatile int shownNetworks;
    private static volatile String shownRequest = "";
    private final UI ui;
    private final Consumer<String> request;
    private final Consumer<Tag> pins;
    private final List<Net> networks = new ArrayList<>();
    private final List<Card> cards = new ArrayList<>();
    private final List<String> kinds = new ArrayList<>(List.of(ITEMS, FLUIDS));
    private final Chart chart = new Chart(() -> figure(this.metric, true));
    private final Chart storedPower = new Chart(this::storedPowerFigure);
    /** Federation power's buffer, in the lower box's left half; its converters and use are in the right half. */
    private final Chart fedStored = new Chart(this::fedStoredFigure, true);
    private final Lower lower = new Lower();
    private @Nullable StatisticsPayload.Snapshot<AEKey> snapshot;
    private StatisticsRange range = StatisticsRange.FIVE_SECONDS;
    private Metric metric = Metric.MOVED;
    private @Nullable String network;
    private String kind = ITEMS;
    private boolean open;
    private List<String> shownChips = List.of();
    private boolean stockByItem = true;
    private boolean movedByItem;
    private Pick pick = Pick.TOP;
    /** The items the player ticked, and whether "everything else" is ticked, once {@link #pick} is CUSTOM. */
    private final Set<AEKey> ticked = new LinkedHashSet<>();
    private boolean otherTicked = true;
    private String search = "";
    /** The pinned keys as last sent or received: the server's answer replaces them. */
    private Set<AEKey> pinned = Set.of();
    /** Each tracked item's stock by network, from the last figures; the items in order of what the domain holds. */
    private final Map<AEKey, Map<String, float[]>> stock = new HashMap<>();
    private final List<AEKey> stockOrder = new ArrayList<>();
    /** The request last sent, so the same one is not sent again. */
    private String asked = "";

    FederationStatisticsView(UI ui, Consumer<String> request, Consumer<Tag> pins) {
        this.ui = ui;
        this.request = request;
        this.pins = pins;
        for (var candidate : StatisticsRange.values()) {
            var chip = element("stats_range_" + candidate.name(), Button.class);
            chip.setOnClick(event -> {
                range = candidate;
                ask();
                refreshRanges();
            });
        }
        refreshRanges();
        var metrics = element("stats_metrics", UIElement.class);
        for (var candidate : Metric.values()) {
            var card = new Card(candidate);
            card.addClass("stats-card");
            card.addEventListener(UIEvents.MOUSE_DOWN, event -> {
                if (metricDisabled(candidate)) return;
                metric = candidate;
                ask();
                refreshKinds();
                refreshLower();
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
        // The lower box draws under its controls: the stock filter's search and quick picks.
        var lowerBox = element("stats_lower", UIElement.class);
        var filter = element("stats_filter", UIElement.class);
        lowerBox.removeChild(filter);
        lower.addClass("stats-layer");
        lowerBox.addChild(lower);
        storedPower.addClass("stats-layer");
        lowerBox.addChild(storedPower);
        fedStored.addClass("stats-layer");
        lowerBox.addChild(fedStored);
        lowerBox.addChild(filter);
        var searchField = element("stats_search", TextField.class);
        searchField.textFieldStyle(style -> style.placeholder(tr("stock.search")
                .withStyle(Style.EMPTY.withColor(FederationTheme.DARK_MUTED & 0xffffff))));
        searchField.setTextResponder(value -> {
            search = value.strip().toLowerCase(Locale.ROOT);
            ask();
        });
        for (var candidate : List.of(Pick.TOP, Pick.PINNED, Pick.NONE)) {
            element("stats_pick_" + candidate.name().toLowerCase(Locale.ROOT), Button.class).setOnClick(event -> {
                pick = candidate;
                refreshLower();
            });
        }
        refreshKinds();
        refreshLower();
    }

    /** Asks the server for this page's figures while it is open, and stops them when another page is shown. */
    void setOpen(boolean open) {
        if (this.open == open) return;
        this.open = open;
        asked = "";
        if (open) ask();
        else request.accept("");
    }

    /** Asks for the figures the page shows now, while it is open: the server sends what the chosen metric needs. */
    private void ask() {
        if (!open) return;
        var next = new StatisticsPayload.Request(range, metric.payload(), kind, metric == Metric.MOVED && movedByItem,
                metric == Metric.STORED && !search.isEmpty()).encode();
        if (next.equals(asked)) return;
        asked = next;
        request.accept(next);
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
        shownRequest = snapshot == null ? "" : snapshot.metric() + ";" + snapshot.kind() + ";" + snapshot.catalog().size();
        if (snapshot == null) return;
        if (metricDisabled(metric)) {
            metric = Metric.POWER;
            ask();
            refreshKinds();
        }
        var present = new LinkedHashSet<>(List.of(ITEMS, FLUIDS));
        snapshot.networks().values().forEach(series -> series.keySet().stream()
                .filter(name -> name.startsWith(StatisticsNames.STORED + "/"))
                .forEach(name -> present.add(name.substring(StatisticsNames.STORED.length() + 1))));
        snapshot.moved().forEach(flow -> present.add(flow.type()));
        present.removeIf(type -> keyType(type) == null);
        if (!present.equals(new LinkedHashSet<>(kinds))) {
            kinds.clear();
            kinds.addAll(present);
            if (!kinds.contains(kind)) {
                kind = ITEMS;
                ask();
            }
            refreshKinds();
        }
        pinned = snapshot.pinned();
        stock.clear();
        for (var row : snapshot.stock()) {
            if (!row.key().getType().getId().toString().equals(kind)) continue;
            stock.computeIfAbsent(row.key(), key -> new HashMap<>()).put(row.network(), row.points());
        }
        refreshStockOrder();
        refreshLower();
    }

    /** The tracked items and the pinned ones of the kind shown, the most held first. */
    private void refreshStockOrder() {
        var amounts = new HashMap<AEKey, Double>();
        stock.forEach((key, byNetwork) -> amounts.put(key, (double) heldNow(byNetwork)));
        for (var key : pinned) {
            if (key.getType().getId().toString().equals(kind)) amounts.putIfAbsent(key, -1d);
        }
        stockOrder.clear();
        stockOrder.addAll(amounts.keySet());
        stockOrder.sort((left, right) -> Double.compare(amounts.get(right), amounts.get(left)));
    }

    /** What the shown networks hold of an item at its newest point. */
    private float heldNow(Map<String, float[]> byNetwork) {
        float total = 0;
        for (var entry : byNetwork.entrySet()) {
            if (network != null && !entry.getKey().equals(network)) continue;
            float value = last(entry.getValue());
            if (!Float.isNaN(value)) total += value;
        }
        return total;
    }

    /** The items the stock chart draws, in order of amount. */
    private List<AEKey> pickedKeys() {
        var held = stockOrder.stream().filter(stock::containsKey).toList();
        return switch (pick) {
            case TOP -> held.stream().limit(TOP_PICK).toList();
            case PINNED -> held.stream().filter(pinned::contains).toList();
            case NONE -> List.of();
            case CUSTOM -> held.stream().filter(ticked::contains).toList();
        };
    }

    /** Whether the stock chart draws everything the picked items leave out, as one grey band. */
    private boolean otherPicked() {
        return pick == Pick.TOP || pick == Pick.CUSTOM && otherTicked;
    }

    /** Ticks or unticks an item: the quick pick in force becomes the player's own. */
    private void toggle(@Nullable AEKey key) {
        if (pick != Pick.CUSTOM) {
            boolean other = otherPicked();
            var current = pickedKeys();
            ticked.clear();
            ticked.addAll(current);
            otherTicked = other;
            pick = Pick.CUSTOM;
        }
        if (key == null) otherTicked = !otherTicked;
        else if (!ticked.remove(key)) ticked.add(key);
        refreshLower();
    }

    /** Pins or unpins an item for the whole domain: the server keeps its stock whether or not it is among the most held. */
    private void togglePin(AEKey key) {
        var next = new LinkedHashSet<>(pinned);
        if (!next.remove(key)) {
            if (next.size() >= space.controlnet.ae2federation.observability.statistics.StatisticsBook.PIN_CAP) return;
            next.add(key);
        }
        pinned = Set.copyOf(next);
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return;
        var list = new ListTag();
        for (var pin : next) list.add(pin.toTagGeneric(level.registryAccess()));
        pins.accept(list);
        refreshStockOrder();
    }

    private int itemColor(AEKey key, List<AEKey> order) {
        int index = order.indexOf(key);
        return index < 0 ? OTHER : ITEM_COLORS[index % ITEM_COLORS.length];
    }

    /** Shows the lower box's parts for the chosen metric, and marks the quick pick in force. */
    private void refreshLower() {
        storedPower.setDisplay(metric == Metric.POWER);
        fedStored.setDisplay(metric == Metric.FEDPOWER);
        lower.setDisplay(metric != Metric.POWER);
        refreshChipStates();
        element("stats_filter", UIElement.class).setDisplay(metric == Metric.STORED && stockByItem);
        for (var candidate : List.of(Pick.TOP, Pick.PINNED, Pick.NONE)) {
            var button = element("stats_pick_" + candidate.name().toLowerCase(Locale.ROOT), Button.class);
            button.removeClass("selected");
            if (candidate == pick) button.addClass("selected");
        }
    }

    /** Federation power's card is shown but cannot be opened while the server does not require power. */
    private boolean metricDisabled(Metric candidate) {
        return candidate == Metric.FEDPOWER && snapshot != null && !snapshot.powerRequired();
    }

    /** TEST-ONLY: how many networks the last figures from the server covered; 0 before any arrived. */
    public static int shownNetworks() {
        return shownNetworks;
    }

    /** TEST-ONLY: the metric and kind the last figures were for, and how many keys they had to search. */
    public static String shownRequest() {
        return shownRequest;
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
        refreshChipStates();
    }

    /** Marks the chosen chip; Federation power belongs to the whole domain, so its network filter is greyed out. */
    private void refreshChipStates() {
        boolean domainWide = metric == Metric.FEDPOWER;
        for (var child : element("stats_networks", UIElement.class).getChildren()) {
            child.removeClass("selected");
            if (java.util.Objects.equals(child.getId(), chipId(network))) child.addClass("selected");
            child.setActive(!domainWide);
            if (domainWide) child.addClass("dim");
            else child.removeClass("dim");
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
            refreshStockOrder();
        });
        return button;
    }

    /**
     * The stock and transfer charts' toggles: one chip per resource kind with figures, then whether the chart is
     * coloured by item or by network.
     */
    private void refreshKinds() {
        var row = element("stats_kinds", UIElement.class);
        row.clearAllChildren();
        boolean shown = metric == Metric.STORED || metric == Metric.MOVED;
        row.setDisplay(shown);
        if (!shown) return;
        for (var candidate : kinds) {
            var type = keyType(candidate);
            if (type == null) continue;
            row.addChild(toggle(type.getDescription(), candidate.equals(kind), () -> {
                kind = candidate;
                stock.clear();
                stockOrder.clear();
                ticked.clear();
                if (pick == Pick.CUSTOM) pick = Pick.TOP;
                ask();
                refreshKinds();
            }));
        }
        var gap = new UIElement();
        gap.layout(layout -> layout.width(6));
        row.addChild(gap);
        boolean byItem = metric == Metric.STORED ? stockByItem : movedByItem;
        for (boolean item : new boolean[] {true, false}) {
            var button = toggle(tr(item ? "by_item" : "by_network"), byItem == item, () -> {
                if (metric == Metric.STORED) stockByItem = item;
                else movedByItem = item;
                ask();
                refreshKinds();
                refreshLower();
            });
            button.setId(item ? "stats_by_item" : "stats_by_network");
            button.addClass("colour-chip");
            row.addChild(button);
        }
    }

    private static Button toggle(Component text, boolean selected, Runnable action) {
        var button = new Button();
        button.addClass("kind-chip");
        if (selected) button.addClass("selected");
        button.setText(text);
        button.setOnClick(event -> action.run());
        return button;
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

    /** A metric's figure: for its card, or {@code large} for the chart, which may draw it by item. */
    private Figure figure(Metric shown, boolean large) {
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
            if (shown == Metric.FEDPOWER || !all && !net.id().equals(network)) continue;
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
            case POWER -> usage(figure, shownNets, n);
            case FEDPOWER -> fedpower(figure, data, n);
            case STORED -> {
                if (large && stockByItem) stockByItem(figure, shownNets, n);
                else stored(figure, shownNets, n, large);
            }
            case MOVED -> {
                if (large && movedByItem) movedByItem(figure, data, shownNets, n);
                else moved(figure, data, shownNets, n);
            }
            case CRAFTING -> started(figure, data, shownNets, n);
            case CPUS, CHANNELS -> gauges(figure, shown, shownNets, n);
        }
        return figure;
    }

    /** Stored ME power, under the power use chart. */
    private Figure storedPowerFigure() {
        var figure = new Figure();
        figure.title = tr("title.stored_power");
        var data = snapshot;
        if (data == null) {
            figure.empty = tr("waiting");
            return figure;
        }
        power(figure, networks.stream().filter(net -> data.networks().containsKey(net.id())).toList(),
                data.range().buckets());
        return figure;
    }

    /** Federation power's buffer, beside its converters under the use chart; its tooltip gives their input too. */
    private Figure fedStoredFigure() {
        var figure = new Figure();
        figure.title = tr("title.fedpower_stored");
        figure.unit = tr("unit.power");
        var data = snapshot;
        if (data == null) {
            figure.empty = tr("waiting");
            return figure;
        }
        int n = data.range().buckets();
        figure.max = 100;
        figure.top = "100%";
        figure.bottom = "0";
        var stored = scaled(data.domain().get(StatisticsNames.FEDPOWER_STORED), 1, n);
        var capacity = data.domain().get(StatisticsNames.FEDPOWER_CAPACITY);
        var input = scaled(data.domain().get(StatisticsNames.FEDPOWER_INPUT), 1, n);
        var stopped = data.domain().get(StatisticsNames.FEDPOWER_STOPPED);
        stoppedSpans(figure, stopped, n);
        figure.layers.add(new Line(stored, FederationTheme.QUARTZ, 1.5f, 1, 1));
        figure.tip = index -> {
            boolean halted = stopped != null && stopped[index] > 0;
            var in = new TipRow(INPUT, tr("tip.converters_in", fmt(input[index]) + " FE/t"), FederationTheme.DARK_TITLE);
            if (Float.isNaN(stored[index])) {
                return List.of(new TipRow(FederationTheme.QUARTZ, tr("tip.no_buffer"), FederationTheme.DARK_MUTED), in);
            }
            float size = capacity == null ? Float.NaN : capacity[index];
            return List.of(new TipRow(FederationTheme.QUARTZ, tr("tip.fedpower_stored", Math.round(stored[index]) + "%",
                    fmt(size) + " FE"), halted ? FederationTheme.ERROR : FederationTheme.DARK_TEXT), in);
        };
        return figure;
    }

    /** Where the domain was stopped for want of power, a red hatch behind the figure. */
    private static void stoppedSpans(Figure figure, float @Nullable [] stopped, int n) {
        if (stopped == null) return;
        for (int index = 0; index < n; index++) {
            if (!(stopped[index] > 0)) continue;
            int end = index;
            while (end + 1 < n && stopped[end + 1] > 0) end++;
            figure.missing.add(new int[] {index, end, FederationTheme.ERROR});
            index = end;
        }
    }

    /**
     * The domain's Federation power use, stacked by group; red where the domain was stopped. Groups that used nothing
     * over the range are left out. The converters' input is with the buffer below, not here.
     */
    private void fedpower(Figure figure, StatisticsPayload.Snapshot<AEKey> data, int n) {
        figure.title = tr("title.fedpower");
        figure.unit = tr("unit.fedpower");
        if (!data.powerRequired() || data.domain().isEmpty()) {
            figure.empty = tr("waiting");
            return;
        }
        var groups = new ArrayList<Integer>();
        var lo = new float[n];
        var counted = new boolean[n];
        var groupValues = new ArrayList<float[]>();
        for (var group : OperatingPowerCosts.Group.values()) {
            var values = scaled(data.domain().get(StatisticsNames.FEDPOWER_USE + "/" + group.key()), 1, n);
            if (!(peak(values) > 0)) continue;
            var hi = new float[n];
            var base = lo.clone();
            for (int index = 0; index < n; index++) {
                hi[index] = Float.isNaN(values[index]) ? lo[index] : lo[index] + values[index];
                if (Float.isNaN(values[index])) base[index] = Float.NaN;
                else counted[index] = true;
            }
            figure.layers.add(new Area(base, mask(hi, base), GROUP_COLORS[group.ordinal()], 0.45f, 1));
            groups.add(group.ordinal());
            groupValues.add(values);
            lo = hi;
        }
        var use = lo.clone();
        for (int index = 0; index < n; index++) if (!counted[index]) use[index] = Float.NaN;
        var stopped = data.domain().get(StatisticsNames.FEDPOWER_STOPPED);
        stoppedSpans(figure, stopped, n);
        figure.max = niceMax(Math.max(1, peak(use)) * 1.15);
        figure.top = fmt(figure.max);
        figure.bottom = "0";
        figure.value = Component.literal(fmt(last(use)) + " FE/t");
        figure.valueColor = last(stopped) > 0 ? FederationTheme.ERROR : FederationTheme.DARK_TEXT;
        figure.tip = index -> {
            boolean halted = stopped != null && stopped[index] > 0;
            var rows = new ArrayList<TipRow>();
            for (int at = groups.size() - 1; at >= 0; at--) {
                var group = OperatingPowerCosts.Group.values()[groups.get(at)];
                rows.add(new TipRow(GROUP_COLORS[group.ordinal()], tr("fedpower.group." + group.key())
                        .append(" " + fmt(groupValues.get(at)[index]) + " FE/t"), FederationTheme.DARK_TEXT));
            }
            rows.add(new TipRow(0, tr(halted ? "tip.use_stopped" : "tip.use", fmt(use[index]) + " FE/t"),
                    halted ? FederationTheme.ERROR : FederationTheme.DARK_TITLE));
            return rows;
        };
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

    /**
     * ME power use, stacked by network so the top edge is the domain's. A network in a shared pool shows its share of
     * what the pool drew, which the server splits by idle draw.
     */
    private void usage(Figure figure, List<Net> nets, int n) {
        figure.unit = tr("unit.usage");
        var shown = nets.stream().filter(net -> !dimmed(net)).toList();
        var lo = new float[n];
        var counted = new boolean[n];
        for (var net : shown) {
            var values = scaled(series(net, StatisticsNames.USAGE), 1, n);
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
        var newest = lo.clone();
        for (int index = 0; index < n; index++) if (!counted[index]) newest[index] = Float.NaN;
        figure.value = Component.literal(fmt(last(newest)) + " AE/t");
        final var totals = lo;
        figure.tip = index -> {
            var rows = new ArrayList<TipRow>();
            for (var net : shown) {
                var values = series(net, StatisticsNames.USAGE);
                rows.add(values == null || Float.isNaN(values[index]) ? noData(net)
                        : new TipRow(net.color(), Component.literal(net.tag() + " " + fmt(values[index]) + " AE/t"), FederationTheme.DARK_TEXT));
            }
            if (shown.size() > 1) rows.add(new TipRow(0, tr("tip.total", fmt(totals[index]) + " AE/t"), FederationTheme.DARK_TITLE));
            return rows;
        };
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

    /**
     * Each network's own stock, stacked so the top edge is the domain's. On the chart, without "everything else"
     * picked, a network's band is only the items picked below it.
     */
    private void stored(Figure figure, List<Net> nets, int n, boolean large) {
        var type = keyType(kind);
        double perUnit = type == null ? 1 : Math.max(1, type.getAmountPerUnit());
        String symbol = unitSymbol(kind);
        figure.title = tr("title.stored");
        figure.unit = symbol.isEmpty() ? tr("unit.items") : Component.literal(symbol.trim());
        figure.sampling = tr("tip.stock");
        var name = StatisticsNames.STORED + "/" + kind;
        var shown = nets.stream().filter(net -> !dimmed(net)).toList();
        boolean picked = large && !otherPicked();
        var keys = picked ? pickedKeys() : List.<AEKey>of();
        if (picked && keys.isEmpty()) figure.empty = tr("stock.none_picked");
        IntFunction<float[]> valuesOf = at -> {
            var net = shown.get(at);
            var total = scaled(series(net, name), perUnit, n);
            if (!picked) return total;
            var out = new float[n];
            for (int index = 0; index < n; index++) out[index] = Float.isNaN(total[index]) ? Float.NaN : 0;
            for (var key : keys) {
                var points = stock.getOrDefault(key, Map.of()).get(net.id());
                if (points == null) continue;
                for (int index = 0; index < n; index++) {
                    if (!Float.isNaN(out[index]) && !Float.isNaN(points[index])) out[index] += (float) (points[index] / perUnit);
                }
            }
            return out;
        };
        var lo = new float[n];
        var counted = new boolean[n];
        for (int at = 0; at < shown.size(); at++) {
            var net = shown.get(at);
            var values = valuesOf.apply(at);
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
            for (int at = 0; at < shown.size(); at++) {
                var net = shown.get(at);
                var values = valuesOf.apply(at);
                rows.add(Float.isNaN(values[index]) ? noData(net)
                        : new TipRow(net.color(), Component.literal(net.tag() + " " + fmt(values[index]) + symbol), FederationTheme.DARK_TEXT));
            }
            if (shown.size() > 1) rows.add(new TipRow(0, tr("tip.domain", fmt(totals[index]) + symbol), FederationTheme.DARK_TITLE));
            return rows;
        };
    }

    /**
     * The domain's stock by item, or the chosen network's: the picked items stacked, the most held lowest, and
     * everything else as one grey band on top when it is picked.
     */
    private void stockByItem(Figure figure, List<Net> nets, int n) {
        var type = keyType(kind);
        double perUnit = type == null ? 1 : Math.max(1, type.getAmountPerUnit());
        String symbol = unitSymbol(kind);
        figure.title = tr("title.stored");
        figure.unit = symbol.isEmpty() ? tr("unit.items") : Component.literal(symbol.trim());
        figure.sampling = tr("tip.stock");
        var shown = nets.stream().filter(net -> !dimmed(net)).toList();
        var keys = pickedKeys();
        boolean other = otherPicked();
        if (keys.isEmpty() && !other) figure.empty = tr("stock.none_picked");
        var name = StatisticsNames.STORED + "/" + kind;
        var total = new float[n];
        Arrays.fill(total, Float.NaN);
        for (var net : shown) {
            var values = scaled(series(net, name), perUnit, n);
            for (int index = 0; index < n; index++) {
                if (!Float.isNaN(values[index])) total[index] = (Float.isNaN(total[index]) ? 0 : total[index]) + values[index];
            }
        }
        var bands = new ArrayList<float[]>();
        for (var key : keys) {
            var values = new float[n];
            Arrays.fill(values, Float.NaN);
            for (var net : shown) {
                var points = stock.getOrDefault(key, Map.of()).get(net.id());
                if (points == null) continue;
                for (int index = 0; index < n; index++) {
                    if (!Float.isNaN(points[index])) {
                        values[index] = (Float.isNaN(values[index]) ? 0 : values[index]) + (float) (points[index] / perUnit);
                    }
                }
            }
            bands.add(values);
        }
        var rest = new float[n];
        for (int index = 0; index < n; index++) {
            float sum = 0;
            for (var band : bands) if (!Float.isNaN(band[index])) sum += band[index];
            rest[index] = Float.isNaN(total[index]) ? Float.NaN : Math.max(0, total[index] - sum);
        }
        var lo = new float[n];
        for (int index = 0; index < n; index++) lo[index] = Float.isNaN(total[index]) ? Float.NaN : 0;
        for (int at = 0; at <= bands.size(); at++) {
            if (at == bands.size() && !other) break;
            var values = at < bands.size() ? bands.get(at) : rest;
            var hi = new float[n];
            for (int index = 0; index < n; index++) {
                hi[index] = Float.isNaN(lo[index]) ? Float.NaN : lo[index] + (Float.isNaN(values[index]) ? 0 : values[index]);
            }
            figure.layers.add(new Area(lo, hi, at < bands.size() ? itemColor(keys.get(at), stockOrder) : OTHER, 0.5f, 1));
            lo = hi;
        }
        double top = 1;
        for (float value : lo) if (!Float.isNaN(value)) top = Math.max(top, value);
        figure.max = niceMax(top * 1.1);
        figure.top = fmt(figure.max);
        figure.bottom = "0";
        figure.value = Component.literal(fmt(last(total)) + symbol);
        final var shownTotal = lo;
        figure.tip = index -> {
            var rows = new ArrayList<TipRow>();
            for (int at = 0; at < bands.size(); at++) {
                float value = bands.get(at)[index];
                rows.add(new TipRow(itemColor(keys.get(at), stockOrder), Component.literal(keys.get(at).getDisplayName().getString()
                        + " " + (Float.isNaN(value) ? "–" : fmt(value) + symbol)), FederationTheme.DARK_TEXT));
            }
            if (other) rows.add(new TipRow(OTHER, tr("tip.other", fmt(rest[index]) + symbol), FederationTheme.DARK_TEXT));
            rows.add(new TipRow(0, tr("tip.shown", fmt(shownTotal[index]) + symbol), FederationTheme.DARK_TITLE));
            return rows;
        };
    }

    /**
     * What moved by item: across the domain, the items that moved most stacked with the rest in grey; for one network,
     * what came in above the axis and what went out below.
     */
    private void movedByItem(Figure figure, StatisticsPayload.Snapshot<AEKey> data, List<Net> nets, int n) {
        var type = keyType(kind);
        double perUnit = type == null ? 1 : Math.max(1, type.getAmountPerUnit());
        String symbol = unitSymbol(kind);
        String rateUnit = symbol + "/s";
        figure.title = tr("title.moved");
        figure.unit = symbol.isEmpty() ? tr("unit.items_per_second") : Component.literal(symbol.trim() + "/s");
        figure.sampling = tr("tip.counted");
        var flows = data.moved().stream().filter(flow -> flow.type().equals(kind)).toList();
        if (flows.isEmpty() && !data.storageRules() && !data.craftingRules()) figure.empty = tr("empty.moved");
        var observed = data.observed();
        var rows = data.movedKeys().stream().filter(row -> row.key().getType().getId().toString().equals(kind)).toList();
        var order = new ArrayList<AEKey>();
        rows.stream().filter(row -> row.network().isEmpty()).forEach(row -> order.add(row.key()));
        rows.forEach(row -> {
            if (!order.contains(row.key())) order.add(row.key());
        });
        record Side(int sign, List<StatisticsPayload.KeySeries<AEKey>> keys, float[] total) {
        }
        var sides = new ArrayList<Side>();
        if (network == null) {
            var total = new float[n];
            for (var flow : flows) for (int index = 0; index < n; index++) total[index] += flow.sums()[index];
            sides.add(new Side(1, rows.stream().filter(row -> row.network().isEmpty()).toList(), total));
        } else {
            figure.mirror = true;
            var in = new float[n];
            var out = new float[n];
            for (var flow : flows) {
                for (int index = 0; index < n; index++) {
                    if (flow.to().equals(network)) in[index] += flow.sums()[index];
                    if (flow.from().equals(network)) out[index] += flow.sums()[index];
                }
            }
            sides.add(new Side(1, rows.stream().filter(row -> row.network().equals(network) && row.direction().equals("in")).toList(), in));
            sides.add(new Side(-1, rows.stream().filter(row -> row.network().equals(network) && row.direction().equals("out")).toList(), out));
        }
        double top = 1;
        for (var side : sides) {
            var lo = new float[n];
            for (int index = 0; index < n; index++) lo[index] = observed[index] > 0 ? 0 : Float.NaN;
            var keyed = new float[n];
            for (int at = 0; at <= side.keys().size(); at++) {
                var hi = new float[n];
                for (int index = 0; index < n; index++) {
                    float sum;
                    if (at < side.keys().size()) {
                        sum = side.keys().get(at).points()[index];
                        keyed[index] += sum;
                    } else {
                        sum = Math.max(0, side.total()[index] - keyed[index]);
                    }
                    hi[index] = Float.isNaN(lo[index]) ? Float.NaN : lo[index] + rate(sum, observed[index], perUnit);
                }
                int color = at < side.keys().size() ? itemColor(side.keys().get(at).key(), order) : OTHER;
                figure.layers.add(new Area(lo, hi, color, 0.5f, side.sign()));
                lo = hi;
            }
            top = Math.max(top, peak(lo));
        }
        figure.max = niceMax(top);
        figure.top = figure.mirror ? tr("axis.in", fmt(figure.max)).getString() : fmt(figure.max);
        figure.bottom = figure.mirror ? tr("axis.out", fmt(figure.max)).getString() : "0";
        double moved = sides.get(0).total().length == 0 ? 0 : sum(sides.get(0).total()) / perUnit;
        figure.value = tr(network == null ? "value.moved" : "value.in", fmt(moved / Math.max(1, seconds(observed))) + rateUnit);
        figure.tip = index -> {
            var tip = new ArrayList<TipRow>();
            for (var side : sides) {
                float keyed = 0;
                for (var row : side.keys()) {
                    float sum = row.points()[index];
                    keyed += sum;
                    if (sum <= 0) continue;
                    var text = row.key().getDisplayName().getString() + " " + fmt(rate(sum, observed[index], perUnit)) + rateUnit;
                    tip.add(new TipRow(itemColor(row.key(), order), side.sign() > 0 || !figure.mirror ? Component.literal(text)
                            : tr("tip.out", text), FederationTheme.DARK_TEXT));
                }
                float rest = Math.max(0, side.total()[index] - keyed);
                if (rest > 0) tip.add(new TipRow(OTHER, tr("tip.other", fmt(rate(rest, observed[index], perUnit)) + rateUnit),
                        FederationTheme.DARK_TEXT));
            }
            return tip;
        };
    }

    /**
     * Crafting jobs the networks' CPUs started per minute, stacked by network; jobs that used another network's
     * patterns and finished or were cancelled are marked along the top.
     */
    private void started(Figure figure, StatisticsPayload.Snapshot<AEKey> data, List<Net> nets, int n) {
        figure.unit = tr("unit.jobs");
        figure.sampling = tr("tip.counted");
        var observed = data.observed();
        var shown = nets.stream().filter(net -> !dimmed(net)).toList();
        var stack = new float[n];
        for (var net : shown) {
            var started = series(net, StatisticsNames.STARTED);
            if (started == null) continue;
            for (int index = 0; index < n; index++) {
                float value = rate(started[index], observed[index], 1) * 60;
                if (Float.isNaN(value) || value <= 0) continue;
                figure.layers.add(new Bar(index, stack[index], stack[index] + value, net.color(), 1));
                stack[index] += value;
            }
        }
        for (var net : shown) {
            var done = series(net, StatisticsNames.DONE);
            var cancelled = series(net, StatisticsNames.CANCELLED);
            for (int index = 0; index < n; index++) {
                if (cancelled != null && cancelled[index] > 0) figure.layers.add(new Mark(index, FederationTheme.ERROR));
                else if (done != null && done[index] > 0) figure.layers.add(new Mark(index, FederationTheme.OK));
            }
        }
        figure.max = niceMax(Math.max(4, peak(stack) * 1.15));
        figure.top = fmt(figure.max);
        figure.bottom = "0";
        double jobs = shown.stream().mapToDouble(net -> {
            var started = series(net, StatisticsNames.STARTED);
            return started == null ? 0 : sum(started);
        }).sum();
        figure.value = tr("value.per_minute", fmt(jobs * 60 / Math.max(1, seconds(observed))));
        figure.tip = index -> {
            var rows = new ArrayList<TipRow>();
            for (var net : shown) {
                var started = series(net, StatisticsNames.STARTED);
                float value = started == null ? 0 : rate(started[index], observed[index], 1) * 60;
                if (value > 0) rows.add(new TipRow(net.color(), tr("tip.started", net.tag(), fmt(value)), FederationTheme.DARK_TEXT));
            }
            endRows(rows, shown, index);
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
        if (flows.isEmpty() && !data.storageRules() && !data.craftingRules()) figure.empty = tr("empty.moved");
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
            if (metricDisabled(shown)) {
                text(context, tr("metric." + shown.key()), x + 5, y + 4, alpha(FederationTheme.DARK_MUTED, 0.6f));
                return;
            }
            var figure = figure(shown, false);
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

    /** A figure large: axes, the time under it, and a tooltip at the hovered point. */
    private final class Chart extends UIElement {
        private final Supplier<Figure> source;
        /** Where the pointer last moved over the chart, from the move events the screen sends; NaN once it left. */
        private float pointerX = Float.NaN;
        private float pointerY = Float.NaN;

        /** Whether the chart takes only the left half of its box, beside a list. */
        private final boolean half;

        Chart(Supplier<Figure> source) {
            this(source, false);
        }

        Chart(Supplier<Figure> source, boolean half) {
            this.source = source;
            this.half = half;
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
            float width = half ? getSizeWidth() / 2 - 2 : getSizeWidth();
            float height = getSizeHeight();
            frame(context, x, y, width, height, FederationTheme.CANVAS, CARD_EDGE);
            var figure = source.get();
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

    /** A clickable rectangle of the lower box, as last drawn. */
    private record Hit(float x, float y, float width, float height, Runnable action) {
    }

    /**
     * What the chosen metric has besides its chart: the items to draw (stock), the keys that moved most, each
     * network's jobs and the CPUs crafting now, every CPU, or each network's channels. Stored power has a chart of its
     * own instead.
     */
    private final class Lower extends UIElement {
        private final List<Hit> hits = new ArrayList<>();

        Lower() {
            addEventListener(UIEvents.MOUSE_DOWN, event -> {
                for (var hit : List.copyOf(hits)) {
                    if (event.x >= hit.x() && event.x < hit.x() + hit.width() && event.y >= hit.y()
                            && event.y < hit.y() + hit.height()) {
                        hit.action().run();
                        return;
                    }
                }
            });
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            super.drawBackgroundAdditional(context);
            hits.clear();
            float x = getPositionX();
            float y = getPositionY();
            float width = getSizeWidth();
            float height = getSizeHeight();
            if (metric == Metric.FEDPOWER) {
                // The right half: the buffer's chart takes the left.
                x += width / 2 + 2;
                width = width / 2 - 2;
            }
            frame(context, x, y, width, height, FederationTheme.CANVAS, CARD_EDGE);
            var data = snapshot;
            switch (metric) {
                case STORED -> {
                    if (stockByItem) stock(context, data, x, y, width, height);
                    else networkTable(context, data, x, y, width, height, StatisticsNames.STORED + "/" + kind, "stock");
                }
                case MOVED -> ranking(context, data, x, y, width, height);
                case CRAFTING -> crafting(context, data, x, y, width, height);
                case CPUS -> cpus(context, data, x, y, width, height);
                case CHANNELS -> networkTable(context, data, x, y, width, height, StatisticsNames.CHANNELS, "channels");
                case FEDPOWER -> fedpower(context, data, x, y, width, height);
                case POWER -> {
                }
            }
        }

        // ---- Federation power ----

        /** The domain's converter bodies and what each takes in now, then its use by group now. */
        private void fedpower(GUIContext context, StatisticsPayload.@Nullable Snapshot<AEKey> data, float x, float y,
                float width, float height) {
            heading(context, tr("fedpower.title"), Component.empty(), x, y, width);
            if (data == null) return;
            float left = x + 5;
            float rowWidth = width - 10;
            float bottom = y + height - 3;
            float rowY = y + 16;
            text(context, tr("fedpower.converters"), left, rowY, FederationTheme.DARK_MUTED);
            rowY += 11;
            if (data.converters().isEmpty()) {
                text(context, tr("fedpower.no_converters"), left, rowY, FederationTheme.DARK_MUTED);
                rowY += 12;
            }
            for (var converter : data.converters()) {
                if (rowY + 22 > bottom) break;
                rect(context, left, rowY, rowWidth, 22, FederationTheme.WELL);
                var input = Component.literal(fmt(converter.input()) + " FE/t");
                int inputWidth = font().width(input);
                text(context, input, left + rowWidth - 4 - inputWidth, rowY + 7,
                        converter.matches() && converter.input() > 0 ? FederationTheme.DARK_TITLE : FederationTheme.ERROR);
                var name = tr("fedpower.converter", converter.size().replace('x', '×')).getString();
                text(context, Component.literal(font().plainSubstrByWidth(name, (int) Math.max(0, rowWidth - 12 - inputWidth))),
                        left + 4, rowY + 2, FederationTheme.DARK_TEXT);
                var where = DevicePlace.dimensionName(converter.dimension())
                        .append(" " + converter.x() + ", " + converter.y() + ", " + converter.z());
                text(context, where, left + 4, rowY + 12, FederationTheme.DARK_MUTED);
                rowY += 24;
            }
            rowY += 2;
            if (rowY + 11 > bottom) return;
            text(context, tr("fedpower.use_by_block"), left, rowY, FederationTheme.DARK_MUTED);
            rowY += 11;
            for (var group : OperatingPowerCosts.Group.values()) {
                float now = last(data.domain().get(StatisticsNames.FEDPOWER_USE + "/" + group.key()));
                if (!(now > 0)) continue;
                if (rowY + 10 > bottom) break;
                rect(context, left, rowY + 3, 4, 4, GROUP_COLORS[group.ordinal()]);
                text(context, tr("fedpower.group." + group.key()), left + 8, rowY, FederationTheme.DARK_TEXT);
                var value = fmt(now) + " FE/t";
                text(context, Component.literal(value), left + rowWidth - 4 - font().width(value), rowY, FederationTheme.DARK_TITLE);
                rowY += 12;
            }
        }

        private void heading(GUIContext context, Component title, Component note, float x, float y, float width) {
            text(context, title, x + 5, y + 4, FederationTheme.DARK_TITLE);
            text(context, note, x + width - 5 - font().width(note), y + 4, FederationTheme.DARK_MUTED);
        }

        // ---- stock ----

        /** The items the stock chart can draw, ticked or not, with search hits that are not recorded yet below them. */
        private void stock(GUIContext context, StatisticsPayload.@Nullable Snapshot<AEKey> data, float x, float y,
                float width, float height) {
            long pins = pinned.stream().filter(key -> key.getType().getId().toString().equals(kind)).count();
            heading(context, tr("stock.title"), tr("stock.note", pins), x, y, width);
            if (data == null) return;
            var type = keyType(kind);
            double perUnit = type == null ? 1 : Math.max(1, type.getAmountPerUnit());
            String symbol = unitSymbol(kind);
            var picked = pickedKeys();
            float columnWidth = (width - 16) / 2;
            float top = y + 33;
            int perColumn = Math.max(1, (int) ((y + height - 3 - top) / 18));
            var positions = new ArrayList<float[]>();
            for (int at = 0; at < perColumn * 2; at++) {
                positions.add(new float[] {x + 5 + (at / perColumn) * (columnWidth + 6), top + (at % perColumn) * 18});
            }
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            var shown = stockOrder.stream().filter(key -> matches(key)).toList();
            // Without a search, the last place is kept for everything else.
            int itemSlots = search.isEmpty() ? positions.size() - 1 : positions.size();
            int slot = 0;
            for (var key : shown) {
                if (slot >= itemSlots) break;
                var at = positions.get(slot++);
                boolean recorded = stock.containsKey(key);
                boolean on = picked.contains(key);
                float amount = recorded ? heldNow(stock.get(key)) : Float.NaN;
                row(context, minecraft, key, null, at[0], at[1], columnWidth, on, itemColor(key, stockOrder),
                        recorded ? fmt(amount / perUnit) + symbol : "", recorded);
            }
            if (search.isEmpty() && slot < positions.size()) {
                var at = positions.get(slot++);
                float rest = otherAmount(picked);
                row(context, minecraft, null, tr("stock.other"), at[0], at[1], columnWidth, otherPicked(), OTHER,
                        Float.isNaN(rest) ? "" : fmt(rest / perUnit) + symbol, true);
            }
            if (!search.isEmpty()) {
                for (var amount : data.catalog()) {
                    if (slot >= positions.size()) break;
                    if (stockOrder.contains(amount.key()) || !matches(amount.key())) continue;
                    var at = positions.get(slot++);
                    row(context, minecraft, amount.key(), null, at[0], at[1], columnWidth, false, OTHER,
                            fmt(amount.amount() / perUnit) + symbol, false);
                }
            }
            if (slot == 0) text(context, tr("stock.no_match"), x + 5, top + 4, FederationTheme.DARK_MUTED);
        }

        private boolean matches(AEKey key) {
            return SearchText.contains(key.getDisplayName().getString(), search);
        }

        /** What the shown networks hold now besides the picked items. */
        private float otherAmount(List<AEKey> picked) {
            float total = 0;
            boolean any = false;
            for (var net : networks) {
                if (dimmed(net)) continue;
                float value = last(series(net, StatisticsNames.STORED + "/" + kind));
                if (Float.isNaN(value)) continue;
                total += value;
                any = true;
            }
            if (!any) return Float.NaN;
            for (var key : picked) total -= heldNow(stock.getOrDefault(key, Map.of()));
            return Math.max(0, total);
        }

        /**
         * One item's row: its tick box in its chart colour, icon, name and amount, and its pin. An item not recorded yet
         * can only be pinned.
         */
        private void row(GUIContext context, net.minecraft.client.Minecraft minecraft, @Nullable AEKey key,
                @Nullable Component label, float x, float y, float width, boolean on, int color, String amount,
                boolean recorded) {
            rect(context, x, y, width, 16, recorded ? FederationTheme.WELL : alpha(FederationTheme.WELL, 0.6f));
            float textX = x + 4;
            if (recorded) {
                if (on) rect(context, x + 4, y + 5, 6, 6, color);
                else {
                    rect(context, x + 4, y + 5, 6, 6, color);
                    rect(context, x + 5, y + 6, 4, 4, FederationTheme.WELL);
                }
                textX = x + 14;
            }
            if (key != null) {
                appeng.api.client.AEKeyRendering.drawInGui(minecraft, context.graphics, Math.round(textX), Math.round(y), key);
                textX += 18;
            }
            boolean isPinned = key != null && pinned.contains(key);
            boolean canPin = key != null && snapshot != null && snapshot.canPin();
            float pinWidth = key == null ? 0 : 10;
            var note = recorded ? amount : isPinned ? tr("stock.pinned_waiting").getString() : tr("stock.not_recorded").getString();
            int noteWidth = font().width(note);
            float right = x + width - 4 - pinWidth;
            if (!recorded && canPin && !isPinned) {
                var pin = tr("stock.pin");
                float buttonWidth = font().width(pin) + 8;
                float buttonX = right - noteWidth - 6 - buttonWidth;
                frame(context, buttonX, y + 2, buttonWidth, 12, FederationTheme.WELL, FederationTheme.SELECT);
                text(context, pin, buttonX + 4, y + 4, FederationTheme.DARK_TITLE);
                hits.add(new Hit(buttonX, y + 2, buttonWidth, 12, () -> togglePin(key)));
                right = buttonX + noteWidth + buttonWidth + 6;
            }
            text(context, Component.literal(note), x + width - 4 - pinWidth - noteWidth, y + 4,
                    recorded ? FederationTheme.DARK_TITLE : FederationTheme.DARK_MUTED);
            var name = key != null ? key.getDisplayName().getString() : label == null ? "" : label.getString();
            float nameRoom = Math.max(0, (recorded || !canPin ? x + width - 8 - pinWidth - noteWidth : right - noteWidth - 40) - textX);
            text(context, Component.literal(font().plainSubstrByWidth(name, (int) nameRoom)), textX, y + 4,
                    recorded ? FederationTheme.DARK_TEXT : FederationTheme.DARK_MUTED);
            if (key != null && (isPinned || canPin && recorded)) {
                pinGlyph(context, x + width - 10, y + 4, isPinned ? FederationTheme.SELECT : alpha(FederationTheme.DARK_MUTED, 0.5f));
                if (canPin) hits.add(new Hit(x + width - 12, y, 12, 16, () -> togglePin(key)));
            }
            if (recorded) hits.add(new Hit(x, y, width - pinWidth - 2, 16, () -> toggle(key)));
        }

        /** A pin: a round head on a short stem. */
        private void pinGlyph(GUIContext context, float x, float y, int color) {
            rect(context, x + 1, y, 3, 1, color);
            rect(context, x, y + 1, 5, 3, color);
            rect(context, x + 1, y + 4, 3, 1, color);
            rect(context, x + 2, y + 5, 1, 3, color);
        }

        // ---- moved ----

        /** "Most moved": the keys that moved most over the range, items on the left and the chosen other kind right. */
        private void ranking(GUIContext context, StatisticsPayload.@Nullable Snapshot<AEKey> data, float x, float y,
                float width, float height) {
            heading(context, tr("rank.title"), tr("span." + range.name().toLowerCase(Locale.ROOT)), x, y, width);
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

        // ---- crafting and CPUs ----

        /** Each network's jobs over the range on the left; the CPUs crafting now on the right. */
        private void crafting(GUIContext context, StatisticsPayload.@Nullable Snapshot<AEKey> data, float x, float y,
                float width, float height) {
            heading(context, tr("crafting.title"), tr("span." + range.name().toLowerCase(Locale.ROOT)), x, y, width);
            if (data == null) return;
            float columnWidth = (width - 16) * 0.58f;
            float nowWidth = width - 16 - columnWidth;
            float left = x + 5;
            float top = y + 16;
            text(context, tr("crafting.per_network"), left, top, FederationTheme.DARK_MUTED);
            var labels = List.of(tr("crafting.started"), tr("crafting.sent"), tr("crafting.done"), tr("crafting.cancelled"));
            float cell = Math.min(56, (columnWidth - 64) / labels.size());
            for (int at = 0; at < labels.size(); at++) {
                var label = labels.get(at);
                text(context, label, left + columnWidth - 4 - (labels.size() - 1 - at) * cell - font().width(label), top,
                        FederationTheme.DARK_MUTED);
            }
            float rowY = top + 11;
            for (var net : networks) {
                if (!data.networks().containsKey(net.id()) || dimmed(net)) continue;
                if (rowY + 16 > y + height - 3) break;
                double sent = data.pushed().stream().filter(flow -> flow.from().equals(net.id())).mapToDouble(flow -> sum(flow.sums())).sum();
                var values = List.of(total(net, StatisticsNames.STARTED), sent, total(net, StatisticsNames.DONE),
                        total(net, StatisticsNames.CANCELLED));
                rect(context, left, rowY, columnWidth, 16, FederationTheme.WELL);
                rect(context, left, rowY, 2, 16, net.color());
                text(context, Component.literal(font().plainSubstrByWidth(net.name().getString(), (int) (columnWidth - 8 - labels.size() * cell))),
                        left + 6, rowY + 4, FederationTheme.DARK_TEXT);
                for (int at = 0; at < values.size(); at++) {
                    var value = fmt(values.get(at));
                    text(context, Component.literal(value), left + columnWidth - 4 - (values.size() - 1 - at) * cell - font().width(value),
                            rowY + 4, at == 3 && values.get(at) > 0 ? FederationTheme.ERROR : FederationTheme.DARK_TITLE);
                }
                rowY += 18;
            }
            float right = x + 11 + columnWidth;
            text(context, tr("crafting.now"), right, top, FederationTheme.DARK_MUTED);
            var busy = data.cpus().stream().filter(cpu -> cpu.output() != null && (network == null || cpu.network().equals(network))).toList();
            if (busy.isEmpty()) text(context, tr("crafting.none"), right, top + 12, FederationTheme.DARK_MUTED);
            rowY = top + 11;
            for (var cpu : busy) {
                if (rowY + 20 > y + height - 3) break;
                cpuRow(context, cpu, right, rowY, nowWidth);
                rowY += 22;
            }
        }

        private double total(Net net, String name) {
            var values = series(net, name);
            return values == null ? 0 : sum(values);
        }

        /** Every CPU of the shown networks: what it crafts and how far it is, or that it is idle. */
        private void cpus(GUIContext context, StatisticsPayload.@Nullable Snapshot<AEKey> data, float x, float y,
                float width, float height) {
            if (data == null) {
                heading(context, tr("cpus.title"), Component.empty(), x, y, width);
                return;
            }
            var shown = data.cpus().stream().filter(cpu -> network == null || cpu.network().equals(network)).toList();
            long busy = shown.stream().filter(cpu -> cpu.output() != null).count();
            heading(context, tr("cpus.title"), tr("cpus.busy", busy, shown.size()), x, y, width);
            if (shown.isEmpty()) {
                text(context, tr("cpus.none"), x + 5, y + 18, FederationTheme.DARK_MUTED);
                return;
            }
            float columnWidth = (width - 16) / 2;
            int perColumn = Math.max(1, (int) ((height - 22) / 22));
            for (int at = 0; at < shown.size() && at < perColumn * 2; at++) {
                cpuRow(context, shown.get(at), x + 5 + (at / perColumn) * (columnWidth + 6), y + 16 + (at % perColumn) * 22,
                        columnWidth);
            }
        }

        /** A CPU: its name, network and size; then what it crafts, a progress bar and the time it has run. */
        private void cpuRow(GUIContext context, StatisticsPayload.Cpu<AEKey> cpu, float x, float y, float width) {
            var owner = net(cpu.network());
            rect(context, x, y, width, 20, FederationTheme.WELL);
            rect(context, x, y, 2, 20, owner == null ? FederationTheme.EDGE : owner.color());
            var name = cpu.name().isEmpty() ? tr("cpus.unnamed").getString() : cpu.name();
            float nameWidth = Math.min(width * 0.38f, 90);
            text(context, Component.literal(font().plainSubstrByWidth(name, (int) nameWidth)), x + 6, y + 2, FederationTheme.DARK_TEXT);
            var detail = (owner == null ? "?" : owner.tag()) + " · " + bytes(cpu.storage());
            text(context, Component.literal(detail), x + 6, y + 11, owner == null ? FederationTheme.DARK_MUTED : owner.color());
            var output = cpu.output();
            if (output == null) {
                var idle = tr("cpus.idle");
                text(context, idle, x + width - 5 - font().width(idle), y + 6, FederationTheme.DARK_MUTED);
                return;
            }
            float jobX = x + 8 + nameWidth;
            appeng.api.client.AEKeyRendering.drawInGui(net.minecraft.client.Minecraft.getInstance(), context.graphics,
                    Math.round(jobX), Math.round(y + 2), output);
            var amount = "×" + fmt(cpu.amount());
            float amountWidth = font().width(amount);
            text(context, Component.literal(amount), x + width - 5 - amountWidth, y + 2, FederationTheme.DARK_TITLE);
            float textX = jobX + 19;
            text(context, Component.literal(font().plainSubstrByWidth(output.getDisplayName().getString(),
                    (int) Math.max(0, x + width - 9 - amountWidth - textX))), textX, y + 2, FederationTheme.DARK_TEXT);
            var elapsed = elapsed(cpu.elapsedNanos());
            float elapsedWidth = font().width(elapsed);
            float barWidth = Math.max(8, x + width - 9 - elapsedWidth - textX);
            rect(context, textX, y + 13, barWidth, 3, GRID);
            float done = cpu.total() > 0 ? Math.min(1, (float) cpu.progress() / cpu.total()) : 0;
            rect(context, textX, y + 13, barWidth * done, 3, FederationTheme.OK);
            text(context, Component.literal(elapsed), x + width - 5 - elapsedWidth, y + 11, FederationTheme.DARK_MUTED);
        }

        // ---- per-network tables ----

        /** Each network's figure now, its peak and its lowest over the range. */
        private void networkTable(GUIContext context, StatisticsPayload.@Nullable Snapshot<AEKey> data, float x, float y,
                float width, float height, String name, String title) {
            heading(context, tr(title + ".by_network"), tr("span." + range.name().toLowerCase(Locale.ROOT)), x, y, width);
            if (data == null) return;
            double perUnit = 1;
            String symbol = "";
            if (name.startsWith(StatisticsNames.STORED + "/")) {
                var type = keyType(kind);
                perUnit = type == null ? 1 : Math.max(1, type.getAmountPerUnit());
                symbol = unitSymbol(kind);
            }
            float left = x + 5;
            float tableWidth = width - 10;
            var labels = List.of(tr("table.now"), tr("table.peak"), tr("table.lowest"));
            float cell = Math.min(70, (tableWidth - 120) / labels.size());
            float top = y + 16;
            for (int at = 0; at < labels.size(); at++) {
                var label = labels.get(at);
                text(context, label, left + tableWidth - 6 - (labels.size() - 1 - at) * cell - font().width(label), top,
                        FederationTheme.DARK_MUTED);
            }
            float rowY = top + 11;
            for (var net : networks) {
                if (!data.networks().containsKey(net.id()) || dimmed(net)) continue;
                if (rowY + 16 > y + height - 3) break;
                var values = series(net, name);
                float now = last(values);
                double peak = Double.NaN;
                double lowest = Double.NaN;
                if (values != null) {
                    for (float value : values) {
                        if (Float.isNaN(value)) continue;
                        peak = Double.isNaN(peak) ? value : Math.max(peak, value);
                        lowest = Double.isNaN(lowest) ? value : Math.min(lowest, value);
                    }
                }
                rect(context, left, rowY, tableWidth, 16, FederationTheme.WELL);
                rect(context, left + 4, rowY + 5, 6, 6, net.color());
                text(context, net.name(), left + 14, rowY + 4, FederationTheme.DARK_TEXT);
                var cells = new double[] {now, peak, lowest};
                for (int at = 0; at < cells.length; at++) {
                    var value = fmt(cells[at] / perUnit) + (Double.isNaN(cells[at]) ? "" : symbol);
                    text(context, Component.literal(value), left + tableWidth - 6 - (cells.length - 1 - at) * cell - font().width(value),
                            rowY + 4, FederationTheme.DARK_TITLE);
                }
                rowY += 18;
            }
        }
    }

    /** A CPU's storage as AE2 names its sizes: 1k, 64k, 1M. */
    static String bytes(long storage) {
        if (storage >= 1L << 20 && storage % (1L << 20) == 0) return (storage >> 20) + "M";
        if (storage >= 1024) return fmt(storage / 1024d) + "k";
        return Long.toString(storage);
    }

    /** "1m 44s", "38s": how long a job has run. */
    static String elapsed(long nanos) {
        long seconds = nanos / 1_000_000_000L;
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + seconds % 60 + "s";
        return seconds % 60 + "s";
    }

    static MutableComponent tr(String key, Object... arguments) {
        return Component.translatable("ae2federation.ui.statistics." + key, arguments);
    }

    private <T> T element(String id, Class<T> type) {
        return ui.selectId(id, type).findFirst().orElseThrow(() -> new IllegalStateException("Missing UI element #" + id));
    }
}
