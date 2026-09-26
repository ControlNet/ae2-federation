package space.controlnet.ae2federation.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class IdentityClaimIndexTest {
    private static final NetworkId NETWORK_A = new NetworkId(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
    private static final NetworkId NETWORK_B = new NetworkId(UUID.fromString("00000000-0000-0000-0000-00000000000b"));

    @Test
    void stableReadsReuseSettlementWithoutLineageScansOrDirtyMarks() {
        var registry = new IdentityClaimIndex<Object>();
        var grid = grid();
        for (int index = 0; index < 1_000; index++) {
            registry.add(grid, lineage(NETWORK_A, index));
        }
        for (int index = 0; index < 50; index++) {
            registry.add(grid(), lineage(NETWORK_B, 10_000 + index));
        }
        var persisted = new int[1];
        var first = registry.settle(grid, result -> persisted[0]++);
        assertEquals(IdentityStatus.SETTLED, first.status());
        assertEquals(1, persisted[0], "The first settlement is offered for persistence");
        var computations = registry.settlementComputations();
        var lookups = registry.lineageLookups();

        for (int read = 0; read < 10_000; read++) {
            assertSame(first, registry.settle(grid, result -> persisted[0]++));
        }

        assertEquals(computations, registry.settlementComputations(), "Stable reads must not recompute");
        assertEquals(lookups, registry.lineageLookups(), "Stable reads must not scan lineages");
        assertEquals(10_000, registry.cachedReads());
        assertEquals(1, persisted[0], "Cached reads must not be offered for persistence (no SavedData dirty)");
    }

    @Test
    void settlementCostScalesWithOwnLineagesNotWithOtherGrids() {
        var registry = new IdentityClaimIndex<Object>();
        var grid = grid();
        registry.add(grid, lineage(NETWORK_A, 1));
        for (int other = 0; other < 500; other++) {
            var otherGrid = grid();
            var network = new NetworkId(UUID.randomUUID());
            for (int node = 0; node < 20; node++) {
                registry.add(otherGrid, new NodeLineage(network, UUID.randomUUID(), 1));
            }
        }
        var before = registry.lineageLookups();
        assertEquals(IdentityStatus.SETTLED, registry.settle(grid, ignored -> { }).status());
        assertEquals(1, registry.lineageLookups() - before, "Settlement uses index lookups for its own lineages only");
    }

    @Test
    void crossGridCopiedNodeInvalidatesAndRestoresUnchangedGrid() {
        var registry = new IdentityClaimIndex<Object>();
        var original = grid();
        var copied = lineage(NETWORK_A, 7);
        registry.add(original, copied);
        registry.add(original, lineage(NETWORK_A, 8));
        assertEquals(IdentityStatus.SETTLED, registry.settle(original, ignored -> { }).status());

        var intruder = grid();
        registry.add(intruder, copied);

        assertEquals(IdentityStatus.COPIED_LIVE_IDENTITY, registry.settle(original, ignored -> { }).status(),
                "A copied node on another Grid must invalidate this Grid's cached settlement");
        assertEquals(IdentityStatus.COPIED_LIVE_IDENTITY, registry.settle(intruder, ignored -> { }).status());

        registry.release(intruder);
        assertEquals(IdentityStatus.SETTLED, registry.settle(original, ignored -> { }).status(),
                "Removing the copy on the other Grid must re-settle the unchanged Grid");
    }

    @Test
    void crossGridSplitAndMergeTransitions() {
        var registry = new IdentityClaimIndex<Object>();
        var left = grid();
        var right = grid();
        registry.add(left, lineage(NETWORK_A, 1));
        registry.add(left, lineage(NETWORK_A, 2));
        assertEquals(IdentityStatus.SETTLED, registry.settle(left, ignored -> { }).status());

        registry.remove(left, lineage(NETWORK_A, 2));
        registry.add(right, lineage(NETWORK_A, 2));
        assertEquals(IdentityStatus.AMBIGUOUS_SPLIT, registry.settle(left, ignored -> { }).status(),
                "A split observed only through the other Grid must invalidate the unchanged half");
        assertEquals(IdentityStatus.AMBIGUOUS_SPLIT, registry.settle(right, ignored -> { }).status());

        registry.release(right);
        registry.add(left, lineage(NETWORK_A, 2));
        assertEquals(IdentityStatus.SETTLED, registry.settle(left, ignored -> { }).status());

        registry.add(left, lineage(NETWORK_B, 3));
        assertEquals(IdentityStatus.AMBIGUOUS_MERGE, registry.settle(left, ignored -> { }).status());
    }

    @Test
    void unrelatedGridChangesKeepCachedSettlement() {
        var registry = new IdentityClaimIndex<Object>();
        var grid = grid();
        registry.add(grid, lineage(NETWORK_A, 1));
        var settled = registry.settle(grid, ignored -> { });
        var computations = registry.settlementComputations();

        var unrelated = grid();
        registry.add(unrelated, lineage(NETWORK_B, 2));
        registry.release(unrelated);

        assertSame(settled, registry.settle(grid, ignored -> { }));
        assertEquals(computations, registry.settlementComputations(),
                "Changes on Grids sharing no node or network id must not invalidate this Grid");
    }

    @Test
    void releaseCleansEveryIndexEntry() {
        var registry = new IdentityClaimIndex<Object>();
        var grid = grid();
        registry.add(grid, lineage(NETWORK_A, 1));
        registry.add(grid, lineage(NETWORK_B, 2));
        registry.release(grid);
        assertTrue(registry.liveClaims().isEmpty());
        var reused = grid();
        registry.add(reused, lineage(NETWORK_A, 1));
        assertEquals(IdentityStatus.SETTLED, registry.settle(reused, ignored -> { }).status(),
                "Released claims must not leave stale copy evidence behind");
    }

    private static NodeLineage lineage(NetworkId network, int node) {
        return new NodeLineage(network, new UUID(0, node), 1);
    }

    private static Object grid() {
        return new Object();
    }
}
