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

    @Test
    void anEmptyTagIsNoStatus() {
        var read = ViewerSyncStatus.decode(new net.minecraft.nbt.CompoundTag());
        assertEquals(0, read.revision());
        assertTrue(read.categories().isEmpty());
        assertTrue(read.viewers().isEmpty());
    }
}
