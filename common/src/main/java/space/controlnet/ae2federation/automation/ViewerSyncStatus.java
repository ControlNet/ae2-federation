package space.controlnet.ae2federation.automation;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * What the Recipe sync page shows of the server's viewer recipes ({@link ViewerScans}), sent to the client as one tag
 * while the page is open: each category with its buckets' states in the current round, what was read and dropped, the
 * failed recipes, and each online player's viewer with what it is doing.
 *
 * @param revision bumped by the server on every change, so an unchanged status is not sent again
 * @param firstSync whether the round began with no recipes saved
 * @param checkedAt when the round last finished, in milliseconds since the epoch; 0 before
 */
public record ViewerSyncStatus(int revision, boolean firstSync, long checkedAt, List<CategoryRow> categories,
        List<ViewerRow> viewers) {
    /** Failed recipe IDs sent per category: enough to name what went wrong. */
    public static final int FAILED_IDS = 8;

    /** A bucket in the current round. */
    public enum Bucket {
        /** Not checked yet this round. */
        QUEUED,
        /** Leased to a viewer. */
        READING,
        /** Checked; the viewer had what the server saved. */
        MATCHED,
        /** Checked; recipes were read or dropped. */
        UPDATED,
        /** Checked; it holds recipes the viewer could not lay out. */
        FAILED,
        /** Not part of the round: no viewer lists its category. */
        UNCHECKED;

        public byte code() {
            return (byte) ordinal();
        }

        public static Bucket of(byte code) {
            return code >= 0 && code < values().length ? values()[code] : UNCHECKED;
        }
    }

    /** A viewer's lease on a category's buckets. */
    public record Lease(String viewer, int first, int count) {
    }

    /**
     * A category: its saved recipes, skipped and failed entries, bucket states, what this round read (of how many to
     * read) and dropped, how many recipes changed (new or gone, as a forced rescan reads unchanged ones too), how many
     * recipes its viewers list (-1 when none lists it), and the leases on it.
     */
    public record CategoryRow(String id, int recipes, int skipped, int failed, List<String> failedIds, byte[] buckets,
            int read, int toRead, int dropped, int changed, int listed, List<Lease> leases) {
    }

    /** What an online player's viewer is doing. */
    public enum ViewerState {
        /** It has not said hello: no recipe viewer, or one still loading. */
        WAITING,
        /** It said hello and has no task: done, or nothing left it can answer. */
        IDLE,
        /** It is fingerprinting a task's buckets. */
        CHECKING,
        /** It is reading the recipes the server asked for. */
        READING,
        /** Its last task ended badly (out of order, silent); it gets none until it says hello again. */
        STOPPED
    }

    /** A viewer: its player's name, state, task, the reads received of those asked, its rate, and what it read. */
    public record ViewerRow(String name, ViewerState state, String category, int first, int count, int received,
            int awaited, float rate, int read) {
    }

    public static ViewerSyncStatus empty() {
        return new ViewerSyncStatus(0, false, 0, List.of(), List.of());
    }

    public CompoundTag encode() {
        var tag = new CompoundTag();
        tag.putInt("revision", revision);
        tag.putBoolean("first", firstSync);
        tag.putLong("checked", checkedAt);
        var categories = new ListTag();
        for (var row : this.categories) {
            var each = new CompoundTag();
            each.putString("id", row.id());
            each.putIntArray("n", new int[] { row.recipes(), row.skipped(), row.failed(), row.read(), row.toRead(),
                    row.dropped(), row.listed(), row.changed() });
            var failed = new ListTag();
            row.failedIds().forEach(id -> failed.add(StringTag.valueOf(id)));
            each.put("failed", failed);
            each.putByteArray("buckets", row.buckets());
            var leases = new ListTag();
            for (var lease : row.leases()) {
                var one = new CompoundTag();
                one.putString("viewer", lease.viewer());
                one.putInt("first", lease.first());
                one.putInt("count", lease.count());
                leases.add(one);
            }
            each.put("leases", leases);
            categories.add(each);
        }
        tag.put("categories", categories);
        var viewers = new ListTag();
        for (var row : this.viewers) {
            var each = new CompoundTag();
            each.putString("name", row.name());
            each.putByte("state", (byte) row.state().ordinal());
            each.putString("category", row.category());
            each.putIntArray("n", new int[] { row.first(), row.count(), row.received(), row.awaited(), row.read() });
            each.putFloat("rate", row.rate());
            viewers.add(each);
        }
        tag.put("viewers", viewers);
        return tag;
    }

    public static ViewerSyncStatus decode(CompoundTag tag) {
        var categories = new ArrayList<CategoryRow>();
        for (var element : tag.getList("categories", Tag.TAG_COMPOUND)) {
            var each = (CompoundTag) element;
            var n = numbers(each.getIntArray("n"), 8);
            var failed = new ArrayList<String>();
            each.getList("failed", Tag.TAG_STRING).forEach(id -> failed.add(id.getAsString()));
            var leases = new ArrayList<Lease>();
            for (var lease : each.getList("leases", Tag.TAG_COMPOUND)) {
                var one = (CompoundTag) lease;
                leases.add(new Lease(one.getString("viewer"), one.getInt("first"), one.getInt("count")));
            }
            categories.add(new CategoryRow(each.getString("id"), n[0], n[1], n[2], List.copyOf(failed),
                    each.getByteArray("buckets"), n[3], n[4], n[5], n[7], n[6], List.copyOf(leases)));
        }
        var viewers = new ArrayList<ViewerRow>();
        var states = ViewerState.values();
        for (var element : tag.getList("viewers", Tag.TAG_COMPOUND)) {
            var each = (CompoundTag) element;
            var n = numbers(each.getIntArray("n"), 5);
            var state = each.getByte("state");
            viewers.add(new ViewerRow(each.getString("name"),
                    state >= 0 && state < states.length ? states[state] : ViewerState.WAITING,
                    each.getString("category"), n[0], n[1], n[2], n[3], each.getFloat("rate"), n[4]));
        }
        return new ViewerSyncStatus(tag.getInt("revision"), tag.getBoolean("first"), tag.getLong("checked"),
                List.copyOf(categories), List.copyOf(viewers));
    }

    private static int[] numbers(int[] read, int size) {
        return read.length == size ? read : java.util.Arrays.copyOf(read, size);
    }
}
