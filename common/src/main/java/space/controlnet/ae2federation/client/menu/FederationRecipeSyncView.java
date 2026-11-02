package space.controlnet.ae2federation.client.menu;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.automation.ViewerSyncStatus;
import space.controlnet.ae2federation.automation.ViewerSyncStatus.Bucket;
import space.controlnet.ae2federation.automation.ViewerSyncStatus.CategoryRow;
import space.controlnet.ae2federation.automation.ViewerSyncStatus.ViewerRow;
import space.controlnet.ae2federation.automation.ViewerSyncStatus.ViewerState;

/**
 * The Recipe sync page: how the server's processing recipes come in from the players' recipe viewers. At the top every
 * bucket of every category as a square, coloured by what this round did with it; on the left each online player's
 * viewer and its task; on the right the categories, filtered, each with its buckets' progress, and a rescan for the one
 * opened. The server sends the status only while this page is open ({@link #setOpen}).
 */
public final class FederationRecipeSyncView {
    private static final int PANEL_EDGE = 0xff121016;
    private static final int CARD = 0xff2c2735;
    private static final int CARD_EDGE = 0xff4a4655;
    private static final int TRACK = 0xff3a3646;
    private static final int HEADING = 0xff6f6a7a;
    private static final int MATCHED = 0xff2f4a3a;
    private static final int UPDATED = 0xff3fae6a;
    private static final int QUEUED = 0xffc9a93a;
    private static final int UNCHECKED = 0xff2a2733;
    private static final int FAILED = 0xffff6b6b;
    private static final int YELLOW = 0xfff7d046;
    /** Each viewer's colour, in the order the server lists them; their tasks' buckets take it too. */
    private static final int[] VIEWER_COLORS = {0xff5aa9ff, 0xffb37bea, 0xfff7b13a, 0xff4fd8e8, 0xffe48fb0,
            0xff98c379};
    private static final int ROW = 13;
    private static final int TOP = 68;

    enum Filter {
        ALL, CHANGED, QUEUED, FAILED
    }

    private final UI ui;
    private final Consumer<String> ask;
    private final Board board = new Board();
    private ViewerSyncStatus status = ViewerSyncStatus.empty();
    private boolean open;
    /** Rescans asked for from this screen, numbering each so the server does each once. */
    private int rescans;
    private Filter filter = Filter.ALL;
    private @Nullable String expanded;
    /** The categories list's scroll, in pixels. */
    private float scroll;

    FederationRecipeSyncView(UI ui, Consumer<String> ask) {
        this.ui = ui;
        this.ask = ask;
        board.addClass("sync-board");
        var holder = element("sync_board", UIElement.class);
        board.layout(style -> style.widthPercent(100).heightPercent(100));
        holder.addChild(board);
        element("sync_rescan_all", Button.class).setOnClick(event -> rescan("*"));
        refreshSummary();
    }

    /** Asks for the status while the page is open, and stops it when another page is shown. */
    void setOpen(boolean open) {
        if (this.open == open) return;
        this.open = open;
        ask.accept(open ? "open" : "");
    }

    /** Has the viewers check every category again ("*"), or read one category again in full. */
    private void rescan(String category) {
        if (!open) return;
        ask.accept("open/" + ++rescans + "/" + category);
    }

    void accept(Tag tag) {
        status = tag instanceof CompoundTag compound && !compound.isEmpty() ? ViewerSyncStatus.decode(compound)
                : ViewerSyncStatus.empty();
        refreshSummary();
    }

    // ---- what the round is doing ----------------------------------------------------------------------------

    private boolean active() {
        for (var row : status.categories()) {
            for (var code : row.buckets()) {
                var state = Bucket.of(code);
                if (state == Bucket.QUEUED || state == Bucket.READING) return true;
            }
        }
        return false;
    }

    private int count(Bucket wanted) {
        int count = 0;
        for (var row : status.categories()) {
            for (var code : row.buckets()) {
                if (Bucket.of(code) == wanted) count++;
            }
        }
        return count;
    }

    private int busyViewers() {
        return (int) status.viewers().stream()
                .filter(row -> row.state() == ViewerState.READING || row.state() == ViewerState.CHECKING).count();
    }

    private Map<String, Integer> viewerColors() {
        var colors = new HashMap<String, Integer>();
        for (var viewer : status.viewers()) {
            colors.putIfAbsent(viewer.name(), VIEWER_COLORS[colors.size() % VIEWER_COLORS.length]);
        }
        return colors;
    }

    /** Recipes a first sync has yet to read: what the viewers list beyond what is saved. */
    private static int unread(CategoryRow row) {
        return Math.max(0, row.listed() - row.recipes() - row.skipped() - row.failed());
    }

    private static boolean queued(CategoryRow row) {
        for (var code : row.buckets()) {
            if (Bucket.of(code) == Bucket.QUEUED) return true;
        }
        return false;
    }

    private static boolean changed(CategoryRow row) {
        return row.changed() > 0;
    }

    private boolean matches(CategoryRow row, Filter filter) {
        return switch (filter) {
            case ALL -> true;
            case CHANGED -> firstSyncActive() ? !row.leases().isEmpty() : changed(row);
            case QUEUED -> queued(row);
            case FAILED -> row.failed() > 0;
        };
    }

    private boolean firstSyncActive() {
        return status.firstSync() && active();
    }

    private void refreshSummary() {
        element("sync_summary", Label.class).setText(summary());
    }

    private Component summary() {
        var rows = status.categories();
        if (rows.isEmpty()) return tr("summary.none");
        int recipes = rows.stream().mapToInt(CategoryRow::recipes).sum();
        int categories = rows.size();
        if (active()) {
            if (status.firstSync()) {
                int listed = rows.stream().mapToInt(row -> Math.max(row.listed(), row.recipes())).sum();
                return tr("summary.first", number(recipes), number(listed), busyViewers());
            }
            return tr("summary.checking", number(rows.stream().mapToInt(CategoryRow::changed).sum()), busyViewers());
        }
        int failed = rows.stream().mapToInt(CategoryRow::failed).sum();
        if (failed > 0) return tr("summary.failed", number(recipes), number(categories), number(failed));
        if (status.checkedAt() == 0) return tr("summary.saved", number(recipes), number(categories));
        var time = java.time.Instant.ofEpochMilli(status.checkedAt()).atZone(java.time.ZoneId.systemDefault())
                .toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        boolean changed = rows.stream().anyMatch(FederationRecipeSyncView::changed);
        return tr(changed ? "summary.changed" : "summary.no_changes", number(recipes), number(categories), time);
    }

    // ---- drawing ----------------------------------------------------------------------------------------------

    private record Hit(float x, float y, float width, float height, Runnable action) {
    }

    /** The whole page below the header: the buckets, the viewers and the categories. */
    private final class Board extends UIElement {
        private final List<Hit> hits = new ArrayList<>();
        /** The categories list as last drawn: its top, bottom and content height, for the wheel. */
        private float listTop;
        private float listBottom;
        private float listLeft;
        private float listRight;
        private float listHeight;

        Board() {
            addEventListener(UIEvents.MOUSE_DOWN, event -> {
                for (var hit : List.copyOf(hits)) {
                    if (event.x >= hit.x() && event.x < hit.x() + hit.width() && event.y >= hit.y()
                            && event.y < hit.y() + hit.height()) {
                        hit.action().run();
                        return;
                    }
                }
            });
            addEventListener(UIEvents.MOUSE_WHEEL, event -> {
                if (event.x < listLeft || event.x >= listRight || event.y < listTop || event.y >= listBottom) return;
                scroll = Math.max(0, Math.min(scroll - event.deltaY * ROW * 3,
                        Math.max(0, listHeight - (listBottom - listTop))));
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
            var colors = viewerColors();
            buckets(context, colors, x, y, width, TOP);
            float below = y + TOP + 6;
            float viewersWidth = Math.max(110, Math.min(264, Math.round(width * 0.35f)));
            viewers(context, colors, x, below, viewersWidth, y + height - below);
            categories(context, colors, x + viewersWidth + 6, below, width - viewersWidth - 6, y + height - below);
        }

        // ---- all buckets ----

        private void buckets(GUIContext context, Map<String, Integer> colors, float x, float y, float width,
                float height) {
            frame(context, x, y, width, height, FederationTheme.CANVAS, PANEL_EDGE);
            text(context, tr("buckets.title"), x + 10, y + 6, FederationTheme.DARK_TITLE);
            var squares = new ArrayList<Integer>();
            var present = EnumSet.noneOf(Bucket.class);
            int total = 0;
            int done = 0;
            int matched = 0;
            int updated = 0;
            int failed = 0;
            int reading = 0;
            boolean firstSync = firstSyncActive();
            for (var row : status.categories()) {
                var codes = row.buckets();
                for (int bucket = 0; bucket < codes.length; bucket++) {
                    var state = Bucket.of(codes[bucket]);
                    present.add(state);
                    squares.add(color(state, row, bucket, colors, firstSync));
                    if (state == Bucket.UNCHECKED) continue;
                    total++;
                    switch (state) {
                        case MATCHED -> matched++;
                        case UPDATED -> updated++;
                        case FAILED -> failed++;
                        case READING -> reading++;
                        default -> { }
                    }
                }
            }
            done = matched + updated + failed;
            // The figures, right-aligned on the title's line.
            var parts = new ArrayList<Component>();
            var partColors = new ArrayList<Integer>();
            if (total > 0) {
                parts.add(Component.literal(Math.round(done * 100f / total) + "%  "));
                partColors.add(FederationTheme.DARK_TITLE);
                boolean busy = done < total;
                int read = status.categories().stream().mapToInt(CategoryRow::read).sum();
                int dropped = status.categories().stream().mapToInt(CategoryRow::dropped).sum();
                if (busy) {
                    var line = tr("buckets.progress", number(done), number(total));
                    if (read > 0) line.append("  ·  ").append(tr(firstSync ? "buckets.read" : "buckets.reread",
                            number(read)));
                    if (dropped > 0) line.append("  ·  ").append(tr("buckets.removed", number(dropped)));
                    var eta = eta();
                    if (eta != null) line.append("  ·  ").append(eta);
                    parts.add(line);
                    partColors.add(FederationTheme.DARK_MUTED);
                } else if (failed > 0) {
                    parts.add(tr("buckets.progress", number(done - failed), number(total)).append("  ·  "));
                    partColors.add(FederationTheme.DARK_MUTED);
                    parts.add(tr("buckets.failed", number(failed)));
                    partColors.add(FAILED);
                } else {
                    parts.add(tr("buckets.match", number(done), number(total)));
                    partColors.add(FederationTheme.DARK_MUTED);
                }
            }
            float right = x + width - 10;
            for (int index = parts.size() - 1; index >= 0; index--) {
                right -= font().width(parts.get(index));
                text(context, parts.get(index), right, y + 6, partColors.get(index));
            }
            // The round's progress: matched, updated, failed and leased buckets.
            float barX = x + 10;
            float barWidth = width - 20;
            rect(context, barX, y + 20, barWidth, 4, TRACK);
            if (total > 0) {
                float at = barX;
                for (var segment : new int[][] { { matched, MATCHED }, { updated, UPDATED }, { failed, FAILED },
                        { reading, FederationTheme.INFO } }) {
                    float span = barWidth * segment[0] / total;
                    rect(context, at, y + 20, span, 4, segment[1]);
                    at += span;
                }
            }
            grid(context, squares, x + 10, y + 28, width - 20, height - 28 - 15);
            legend(context, present, firstSync, x + width - 10, y + height - 13);
        }

        private int color(Bucket state, CategoryRow row, int bucket, Map<String, Integer> colors, boolean firstSync) {
            return switch (state) {
                case QUEUED -> firstSync ? TRACK : QUEUED;
                case READING -> row.leases().stream()
                        .filter(lease -> bucket >= lease.first() && bucket < lease.first() + lease.count())
                        .map(lease -> colors.getOrDefault(lease.viewer(), FederationTheme.INFO)).findFirst()
                        .orElse(FederationTheme.INFO);
                case MATCHED -> MATCHED;
                case UPDATED -> UPDATED;
                case FAILED -> FAILED;
                case UNCHECKED -> UNCHECKED;
            };
        }

        /**
         * A square per bucket, as large as fits (4, 3 or 2 pixels); when even the smallest do not fit, each square
         * stands for several buckets and takes the colour of the one that matters most.
         */
        private void grid(GUIContext context, List<Integer> squares, float x, float y, float width, float height) {
            int n = squares.size();
            if (n == 0) return;
            int pitch = 3;
            for (int candidate : new int[] { 5, 4, 3 }) {
                if ((int) ((width + 1) / candidate) * (int) ((height + 1) / candidate) >= n) {
                    pitch = candidate;
                    break;
                }
            }
            int columns = Math.max(1, (int) ((width + 1) / pitch));
            int rows = Math.max(1, (int) ((height + 1) / pitch));
            int per = (n + columns * rows - 1) / (columns * rows);
            for (int square = 0; square * per < n; square++) {
                int color = squares.get(square * per);
                for (int index = square * per + 1; index < Math.min(n, (square + 1) * per); index++) {
                    if (rank(squares.get(index)) > rank(color)) color = squares.get(index);
                }
                rect(context, x + (square % columns) * pitch, y + (square / columns) * pitch, pitch - 1, pitch - 1,
                        color);
            }
        }

        /** Which colour a shared square shows: a viewer at work, then a failure, then what is left to do. */
        private static int rank(int color) {
            if (color == FAILED) return 5;
            if (color == QUEUED || color == TRACK) return 3;
            if (color == UPDATED) return 2;
            if (color == MATCHED) return 1;
            if (color == UNCHECKED) return 0;
            return 6;
        }

        private void legend(GUIContext context, EnumSet<Bucket> present, boolean firstSync, float right, float y) {
            var entries = new ArrayList<Object[]>();
            if (present.contains(Bucket.MATCHED)) entries.add(new Object[] { MATCHED, "legend.matched" });
            if (present.contains(Bucket.UPDATED)) {
                entries.add(new Object[] { UPDATED, firstSync ? "legend.read" : "legend.updated" });
            }
            if (present.contains(Bucket.QUEUED)) {
                entries.add(new Object[] { firstSync ? TRACK : QUEUED, firstSync ? "legend.unread" : "legend.queued" });
            }
            if (present.contains(Bucket.READING)) entries.add(new Object[] { FederationTheme.INFO, "legend.reading" });
            if (present.contains(Bucket.FAILED)) entries.add(new Object[] { FAILED, "legend.failed" });
            if (present.contains(Bucket.UNCHECKED)) entries.add(new Object[] { UNCHECKED, "legend.unchecked" });
            float at = right;
            for (int index = entries.size() - 1; index >= 0; index--) {
                var label = tr((String) entries.get(index)[1]);
                at -= font().width(label);
                text(context, label, at, y, FederationTheme.DARK_MUTED);
                at -= 8;
                rect(context, at, y + 2, 4, 4, (int) entries.get(index)[0]);
                at -= 10;
            }
        }

        /** How long the viewers at work need for what is left, at their rate now; null when unknown. */
        private @Nullable Component eta() {
            float rate = 0;
            for (var viewer : status.viewers()) {
                if (viewer.state() == ViewerState.READING) rate += viewer.rate();
            }
            int left = 0;
            for (var row : status.categories()) {
                left += status.firstSync() ? unread(row) : Math.max(0, row.toRead() - row.read());
            }
            if (rate <= 0 || left <= 0) return null;
            int seconds = Math.max(1, Math.round(left / rate));
            return seconds < 60 ? tr("eta.seconds", seconds) : tr("eta.minutes", (seconds + 59) / 60);
        }

        // ---- viewers ----

        private void viewers(GUIContext context, Map<String, Integer> colors, float x, float y, float width,
                float height) {
            frame(context, x, y, width, height, FederationTheme.CANVAS, PANEL_EDGE);
            text(context, tr("viewers.title"), x + 10, y + 6, FederationTheme.DARK_TITLE);
            float cardX = x + 10;
            float cardWidth = width - 20;
            float cardY = y + 20;
            boolean active = active();
            for (var viewer : status.viewers()) {
                boolean working = viewer.state() == ViewerState.READING || viewer.state() == ViewerState.CHECKING;
                float cardHeight = working ? 44 : viewer.state() == ViewerState.WAITING ? 18 : 31;
                if (cardY + cardHeight > y + height - 4) break;
                frame(context, cardX, cardY, cardWidth, cardHeight, CARD, CARD_EDGE);
                int color = colors.getOrDefault(viewer.name(), HEADING);
                rect(context, cardX + 5, cardY + 6, 4, 4, viewer.state() == ViewerState.WAITING ? HEADING : color);
                var name = Component.literal(viewer.name());
                text(context, name, cardX + 12, cardY + 4, FederationTheme.DARK_TITLE);
                float stateX = cardX + 12 + font().width(name) + 6;
                switch (viewer.state()) {
                    case WAITING -> text(context, tr("viewer.waiting"), stateX, cardY + 4, FederationTheme.DARK_MUTED);
                    case IDLE -> {
                        text(context, tr(active ? "viewer.idle" : "viewer.done"), stateX, cardY + 4,
                                active ? YELLOW : FederationTheme.OK);
                        var note = viewer.read() == 0 ? tr("viewer.nothing")
                                : tr(status.firstSync() ? "viewer.read" : "viewer.reread", number(viewer.read()));
                        text(context, clip(note, cardWidth - 16), cardX + 8, cardY + 18, FederationTheme.DARK_MUTED);
                    }
                    case STOPPED -> {
                        text(context, tr("viewer.stopped"), stateX, cardY + 4, FAILED);
                        text(context, clip(tr("viewer.stopped_note"), cardWidth - 16), cardX + 8, cardY + 18,
                                FederationTheme.DARK_MUTED);
                    }
                    case CHECKING, READING -> working(context, viewer, color, cardX, cardY, cardWidth, stateX);
                }
                cardY += cardHeight + 8;
            }
        }

        private void working(GUIContext context, ViewerRow viewer, int color, float x, float y, float width,
                float stateX) {
            boolean reading = viewer.state() == ViewerState.READING;
            text(context, tr(!reading ? "viewer.checking" : status.firstSync() ? "viewer.reading" : "viewer.rereading"),
                    stateX, y + 4, FederationTheme.OK);
            if (reading && viewer.rate() > 0) {
                var rate = tr("viewer.rate", number(Math.round(viewer.rate())));
                text(context, rate, x + width - 12 - font().width(rate), y + 4, FederationTheme.DARK_MUTED);
            }
            int total = status.categories().stream().filter(row -> row.id().equals(viewer.category()))
                    .mapToInt(row -> row.buckets().length).findFirst().orElse(0);
            var bucket = tr("viewer.bucket", viewer.first() + viewer.count(), total);
            float idWidth = width - 16 - font().width(bucket) - 6;
            float after = category(context, viewer.category(), x + 8, y + 18, idWidth);
            text(context, bucket, after + 6, y + 18, FederationTheme.DARK_MUTED);
            if (!reading) {
                text(context, tr("viewer.comparing", viewer.count()), x + 8, y + 31, FederationTheme.DARK_MUTED);
                return;
            }
            var counts = Component.literal(number(viewer.received()) + "/" + number(viewer.awaited()));
            float countsX = x + width - 12 - font().width(counts);
            text(context, counts, countsX, y + 31, FederationTheme.DARK_TEXT);
            float barWidth = countsX - 8 - (x + 8);
            rect(context, x + 8, y + 34, barWidth, 2, TRACK);
            if (viewer.awaited() > 0) {
                rect(context, x + 8, y + 34, barWidth * Math.min(1, viewer.received() / (float) viewer.awaited()), 2,
                        color);
            }
        }

        // ---- categories ----

        private void categories(GUIContext context, Map<String, Integer> colors, float x, float y, float width,
                float height) {
            frame(context, x, y, width, height, FederationTheme.CANVAS, PANEL_EDGE);
            text(context, tr("categories.title"), x + 10, y + 6, FederationTheme.DARK_TITLE);
            boolean firstSync = firstSyncActive();
            // The filters, right-aligned on the title's line.
            float chipRight = x + width - 10;
            var filters = Filter.values();
            for (int index = filters.length - 1; index >= 0; index--) {
                var candidate = filters[index];
                int matching = (int) status.categories().stream().filter(row -> matches(row, candidate)).count();
                var label = tr("filter." + (candidate == Filter.CHANGED && firstSync ? "reading"
                        : candidate.name().toLowerCase(Locale.ROOT)));
                var count = Component.literal(String.valueOf(matching));
                float chipWidth = font().width(label) + 4 + font().width(count) + 12;
                float chipX = chipRight - chipWidth;
                boolean selected = filter == candidate;
                frame(context, chipX, y + 3, chipWidth, 12, selected ? TRACK : CARD,
                        selected ? FederationTheme.SELECT : CARD_EDGE);
                text(context, label, chipX + 6, y + 5, FederationTheme.DARK_TEXT);
                text(context, count, chipX + 6 + font().width(label) + 4, y + 5, FederationTheme.DARK_MUTED);
                hits.add(new Hit(chipX, y + 3, chipWidth, 12, () -> {
                    filter = candidate;
                    scroll = 0;
                }));
                chipRight = chipX - 3;
            }
            float left = x + 10;
            float right = x + width - 10;
            float recipesRight = right - 212;
            float changedRight = right - 150;
            float barX = right - 142;
            float bucketsX = right - 98;
            text(context, tr("column.category"), left, y + 23, HEADING);
            var recipesHead = tr("column.recipes");
            text(context, recipesHead, recipesRight - font().width(recipesHead), y + 23, HEADING);
            var changedHead = tr(firstSync ? "column.to_read" : "column.changed");
            text(context, changedHead, changedRight - font().width(changedHead), y + 23, HEADING);
            text(context, tr("column.buckets"), barX, y + 23, HEADING);
            rect(context, left, y + 35, right - left, 1, CARD_EDGE);
            var rows = status.categories().stream().filter(row -> matches(row, filter))
                    .sorted(Comparator.comparing((CategoryRow row) -> row.leases().isEmpty())
                            .thenComparing(row -> row.failed() == 0)
                            .thenComparing(Comparator.comparingInt((CategoryRow row) ->
                                    Math.max(row.listed(), row.recipes())).reversed()))
                    .toList();
            listTop = y + 39;
            listBottom = y + height - 4;
            listLeft = x;
            listRight = x + width;
            float content = 0;
            for (var row : rows) content += rowHeight(row);
            listHeight = content;
            scroll = Math.max(0, Math.min(scroll, Math.max(0, content - (listBottom - listTop))));
            float rowY = listTop + 2 - scroll;
            for (var row : rows) {
                float rowHeight = rowHeight(row);
                if (rowY >= listTop && rowY + rowHeight <= listBottom + 1) {
                    row(context, row, colors, firstSync, left, rowY, right, recipesRight, changedRight, barX, bucketsX);
                }
                rowY += rowHeight;
            }
            if (content > listBottom - listTop) {
                float track = listBottom - listTop;
                rect(context, right + 5, listTop, 2, track, CARD);
                float thumb = Math.max(8, track * track / content);
                rect(context, right + 5, listTop + (track - thumb) * scroll / (content - track), 2, thumb, HEADING);
            }
        }

        private float rowHeight(CategoryRow row) {
            if (!row.id().equals(expanded)) return ROW;
            return ROW + 4 + ROW * (row.failedIds().size() + 1) + 18;
        }

        private void row(GUIContext context, CategoryRow row, Map<String, Integer> colors, boolean firstSync,
                float left, float y, float right, float recipesRight, float changedRight, float barX, float bucketsX) {
            boolean open = row.id().equals(expanded);
            if (open) frame(context, left - 4, y - 3, right - left + 8, ROW + 2, TRACK, FederationTheme.SELECT);
            hits.add(new Hit(left - 4, y - 3, right - left + 8, ROW, () -> expanded = open ? null : row.id()));
            category(context, row.id(), left, y, recipesRight - 40 - left);
            var recipes = Component.literal(number(Math.max(row.listed(), row.recipes())));
            text(context, recipes, recipesRight - font().width(recipes), y, FederationTheme.DARK_TEXT);
            int changed = firstSync ? (queued(row) || !row.leases().isEmpty() ? unread(row) : 0)
                    : row.changed();
            if (changed > 0) {
                var value = Component.literal(number(changed));
                text(context, value, changedRight - font().width(value), y, YELLOW);
            }
            var codes = row.buckets();
            int done = 0;
            int failedBuckets = 0;
            int reading = 0;
            for (var code : codes) {
                switch (Bucket.of(code)) {
                    case MATCHED, UPDATED -> done++;
                    case FAILED -> failedBuckets++;
                    case READING -> reading++;
                    default -> { }
                }
            }
            rect(context, barX, y + 3, 40, 3, TRACK);
            if (codes.length > 0) {
                float okWidth = 40f * done / codes.length;
                rect(context, barX, y + 3, okWidth, 3, FederationTheme.OK);
                int leaseColor = row.leases().isEmpty() ? FederationTheme.INFO
                        : colors.getOrDefault(row.leases().get(0).viewer(), FederationTheme.INFO);
                rect(context, barX + okWidth, y + 3, 40f * reading / codes.length, 3, leaseColor);
                rect(context, barX + 40 - 40f * failedBuckets / codes.length, y + 3, 40f * failedBuckets / codes.length,
                        3, FAILED);
            }
            text(context, Component.literal((done + failedBuckets) + "/" + codes.length), bucketsX, y,
                    FederationTheme.DARK_MUTED);
            Component state;
            int stateColor;
            if (!row.leases().isEmpty()) {
                var viewer = row.leases().get(0).viewer();
                state = Component.literal(row.leases().size() > 1 ? viewer + " +" + (row.leases().size() - 1) : viewer);
                stateColor = colors.getOrDefault(viewer, FederationTheme.INFO);
            } else if (row.listed() < 0 && codes.length > 0 && Bucket.of(codes[0]) == Bucket.UNCHECKED) {
                state = tr("row.unlisted");
                stateColor = HEADING;
            } else if (queued(row)) {
                state = tr("row.queued");
                stateColor = YELLOW;
            } else if (row.failed() > 0) {
                state = tr("row.failed", number(row.failed()));
                stateColor = FAILED;
            } else {
                state = tr(firstSync ? "row.read" : "row.synced");
                stateColor = FederationTheme.OK;
            }
            text(context, state, right - font().width(state), y, stateColor);
            if (!open) return;
            float lineY = y + ROW + 4;
            for (var id : row.failedIds()) {
                category(context, id, left + 20, lineY, right - left - 30);
                lineY += ROW;
            }
            text(context, clip(tr(row.failed() > 0 ? "row.failed_note" : "row.rescan_note"), right - left - 30),
                    left + 20, lineY, FederationTheme.DARK_MUTED);
            lineY += ROW;
            var label = tr("row.rescan");
            float buttonWidth = font().width(label) + 12;
            frame(context, right - buttonWidth, lineY, buttonWidth, 13, CARD, CARD_EDGE);
            text(context, label, right - buttonWidth + 6, lineY + 3, FederationTheme.DARK_TEXT);
            var id = row.id();
            hits.add(0, new Hit(right - buttonWidth, lineY, buttonWidth, 13, () -> rescan(id)));
        }

        /** A category or recipe ID, its namespace muted, cut to {@code width}; returns where it ends. */
        private float category(GUIContext context, String id, float x, float y, float width) {
            int colon = id.indexOf(':');
            var namespace = colon < 0 ? "" : id.substring(0, colon + 1);
            var path = colon < 0 ? id : id.substring(colon + 1);
            var shownNamespace = font().plainSubstrByWidth(namespace, (int) Math.max(0, width));
            text(context, Component.literal(shownNamespace), x, y, FederationTheme.DARK_MUTED);
            float at = x + font().width(shownNamespace);
            var room = (int) Math.max(0, width - (at - x));
            var shownPath = path;
            if (font().width(path) > room) {
                shownPath = font().plainSubstrByWidth(path, Math.max(0, room - font().width("…"))) + "…";
            }
            if (shownNamespace.length() == namespace.length()) {
                text(context, Component.literal(shownPath), at, y, FederationTheme.DARK_TEXT);
                at += font().width(shownPath);
            }
            return at;
        }
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private static Component clip(Component text, float width) {
        var plain = text.getString();
        if (font().width(plain) <= width) return text;
        return Component.literal(font().plainSubstrByWidth(plain, (int) Math.max(0, width - font().width("…"))) + "…");
    }

    private static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

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

    private static MutableComponent tr(String key, Object... arguments) {
        return Component.translatable("ae2federation.ui.sync." + key, arguments);
    }

    private <T> T element(String id, Class<T> type) {
        return ui.selectId(id, type).findFirst().orElseThrow(() -> new IllegalStateException("Missing UI element #" + id));
    }
}
