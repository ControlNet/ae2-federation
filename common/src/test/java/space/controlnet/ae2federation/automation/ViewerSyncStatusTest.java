package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.automation.ViewerSyncStatus.Bucket;
import space.controlnet.ae2federation.automation.ViewerSyncStatus.CategoryRow;
import space.controlnet.ae2federation.automation.ViewerSyncStatus.Lease;
import space.controlnet.ae2federation.automation.ViewerSyncStatus.ViewerRow;
import space.controlnet.ae2federation.automation.ViewerSyncStatus.ViewerState;

/** What the Recipe sync page shows, sent to the client as one tag and read back the same. */
final class ViewerSyncStatusTest {
    @Test
    void theStatusReadsBackAsItWasSent() {
        var status = new ViewerSyncStatus(42, true, 1_762_000_000_000L, List.of(
                new CategoryRow("create:milling", 51, 3, 1, List.of("create:milling/broken"), new byte[] {
                        Bucket.UPDATED.code(), Bucket.READING.code(), Bucket.FAILED.code() }, 12, 30, 2, 9, 51,
                        List.of(new Lease("Alex", 1, 1))),
                new CategoryRow("gonemod:pressing", 7, 0, 0, List.of(), new byte[] { Bucket.UNCHECKED.code() }, 0, 0, 0,
                        0, -1, List.of())),
                List.of(new ViewerRow("Alex", ViewerState.READING, "create:milling", 1, 1, 12, 30, 68.5f, 12),
                        new ViewerRow("Kai", ViewerState.WAITING, "", 0, 0, 0, 0, 0, 0)));
        var read = ViewerSyncStatus.decode(status.encode());
        assertEquals(status.revision(), read.revision());
        assertEquals(status.firstSync(), read.firstSync());
        assertEquals(status.checkedAt(), read.checkedAt());
        assertEquals(status.viewers(), read.viewers());
        assertEquals(2, read.categories().size());
        var milling = read.categories().get(0);
        assertEquals("create:milling", milling.id());
        assertArrayEquals(status.categories().get(0).buckets(), milling.buckets());
        assertEquals(List.of("create:milling/broken"), milling.failedIds());
        assertEquals(List.of(new Lease("Alex", 1, 1)), milling.leases());
        assertEquals(List.of(51, 3, 1, 12, 30, 2, 9, 51), List.of(milling.recipes(), milling.skipped(), milling.failed(),
                milling.read(), milling.toRead(), milling.dropped(), milling.changed(), milling.listed()));
        assertEquals(-1, read.categories().get(1).listed(), "A category no viewer lists");
        assertEquals(Bucket.FAILED, Bucket.of(milling.buckets()[2]));
    }

    /**
     * The whole status at ATM10's scale (543 categories, 202,381 recipes: measured on the pack), with every field at
     * its largest: each category with {@link ViewerSyncStatus#FAILED_IDS} long failed IDs and the most buckets
     * {@link RecipeBuckets#bitsFor} gives, twenty viewers each holding a lease. LDLib2 sends the tag whole
     * (ByteBufCodecs.TRUSTED_TAG, no NBT size limit) inside one ModularUI sync packet at most once a second while the
     * page is open; it stays under half the 1 MiB a custom payload holds before NeoForge has to split it (measured
     * 2026-11-24: 435,757 bytes before compression, 347,520 of them the failed IDs).
     */
    @Test
    void theStatusAtAtm10ScaleStaysWellUnderOnePacket() throws java.io.IOException {
        int categories = 543;
        int recipes = 202_381;
        var rows = new java.util.ArrayList<CategoryRow>();
        int buckets = 0;
        for (int i = 0; i < categories; i++) {
            // One large category, as minecraft:crafting is, and the rest spread evenly.
            int count = i == 0 ? recipes / 3 : (recipes - recipes / 3) / (categories - 1);
            var states = new byte[1 << RecipeBuckets.bitsFor(count)];
            buckets += states.length;
            java.util.Arrays.fill(states, Bucket.UPDATED.code());
            var failed = new java.util.ArrayList<String>();
            for (int f = 0; f < ViewerSyncStatus.FAILED_IDS; f++) {
                failed.add(id("somemod", "category_" + i + "/failed_recipe_" + f, 80));
            }
            var leases = i < 20 ? List.of(new Lease(name(i), 0, states.length)) : List.<Lease>of();
            rows.add(new CategoryRow(id("somemod", "category_" + i, 48), count, count, count, List.copyOf(failed),
                    states, count, count, count, count, count, leases));
        }
        var viewers = new java.util.ArrayList<ViewerRow>();
        for (int v = 0; v < 20; v++) {
            viewers.add(new ViewerRow(name(v), ViewerState.READING, rows.get(v).id(), 0, 1 << 16, 1 << 16, 1 << 16,
                    999.5f, recipes));
        }
        var tag = new ViewerSyncStatus(Integer.MAX_VALUE, true, Long.MAX_VALUE, rows, viewers).encode();
        var bytes = new java.io.ByteArrayOutputStream();
        // What FriendlyByteBuf.writeNbt writes.
        net.minecraft.nbt.NbtIo.writeAnyTag(tag, new java.io.DataOutputStream(bytes));
        System.out.printf("Recipe sync status at ATM10 scale: %d categories, %d buckets, %d bytes%n", categories,
                buckets, bytes.size());
        assertTrue(bytes.size() < 512 * 1024, "the status is " + bytes.size() + " bytes");
        assertEquals(categories, ViewerSyncStatus.decode(tag).categories().size());
    }

    /** {@code namespace:path}, the path padded to {@code length} characters in all. */
    private static String id(String namespace, String path, int length) {
        var id = new StringBuilder(namespace).append(':').append(path);
        while (id.length() < length) {
            id.append('_');
        }
        return id.toString();
    }

    /** A player name at its longest, 16 characters. */
    private static String name(int index) {
        return String.format("Player%010d", index);
    }

    @Test
    void anEmptyTagIsNoStatus() {
        var read = ViewerSyncStatus.decode(new net.minecraft.nbt.CompoundTag());
        assertEquals(0, read.revision());
        assertTrue(read.categories().isEmpty());
        assertTrue(read.viewers().isEmpty());
    }
}
