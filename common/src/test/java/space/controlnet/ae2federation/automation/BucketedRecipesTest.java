package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.automation.BucketedRecipes.Entry;
import space.controlnet.ae2federation.automation.BucketedRecipes.Status;
import space.controlnet.ae2federation.automation.RecipeBuckets.Fingerprint;

/**
 * A category's saved viewer recipes, kept in the buckets the server and a viewer compare: each bucket's digest follows
 * what it holds, a bucket changes alone, and a category that grew past its buckets splits them.
 */
final class BucketedRecipesTest {
    private static Entry<String> recipe(long partition, long digest) {
        return new Entry<>(new Fingerprint(partition, digest), Status.RECIPE, "r" + partition + "/" + digest);
    }

    private static Entry<String> skipped(long partition, long digest) {
        return new Entry<>(new Fingerprint(partition, digest), Status.SKIPPED, null);
    }

    private static List<Fingerprint> fingerprints(List<Entry<String>> entries) {
        return entries.stream().map(Entry::fingerprint).toList();
    }

    @Test
    void eachRecipeSitsInTheBucketOfItsPartitionAndEachBucketHasTheDigestOfWhatItHolds() {
        var entries = List.of(recipe(0b00, 1), recipe(0b01, 2), recipe(0b101, 3), skipped(0b11, 4));
        var shelf = BucketedRecipes.of(2, entries);
        assertEquals(2, shelf.bits());
        assertEquals(4, shelf.size());
        assertEquals(List.of(entries.get(1), entries.get(2)), shelf.bucket(1));
        assertEquals(List.of(), shelf.bucket(2));
        assertEquals(0, shelf.digest(2), "An empty bucket reads as one the server never had");
        for (var bucket = 0; bucket < 4; bucket++) {
            assertEquals(RecipeBuckets.digest(fingerprints(shelf.bucket(bucket))), shelf.digest(bucket));
        }
    }

    @Test
    void applyingABucketDropsAndAddsThereAloneAndLeavesTheOldShelfAsItWas() {
        var kept = recipe(0b00, 1);
        var changed = recipe(0b10, 2);
        var other = recipe(0b01, 3);
        var before = BucketedRecipes.of(1, List.of(kept, changed, other));
        var changedAfter = recipe(0b10, 5);
        var after = before.apply(0, List.of(changed.fingerprint()), List.of(changedAfter));
        assertEquals(List.of(kept, changedAfter), after.bucket(0));
        assertSame(before.bucket(1), after.bucket(1), "The other bucket is the same list");
        assertEquals(before.digest(1), after.digest(1));
        assertEquals(RecipeBuckets.digest(List.of(kept.fingerprint(), changedAfter.fingerprint())), after.digest(0));
        assertEquals(List.of(kept, changed), before.bucket(0), "The shelf a reader holds does not change");
    }

    @Test
    void droppingATwinDropsOneOfThem() {
        var twin = recipe(4, 4);
        var shelf = BucketedRecipes.of(0, List.of(twin, twin));
        assertEquals(List.of(twin), shelf.apply(0, List.of(twin.fingerprint()), List.of()).bucket(0));
    }

    @Test
    void anEntryOfAnotherBucketIsRefused() {
        var shelf = BucketedRecipes.<String>of(1, List.of());
        assertThrows(IllegalArgumentException.class, () -> shelf.apply(0, List.of(), List.of(recipe(1, 1))));
        assertThrows(IllegalArgumentException.class, () -> shelf.apply(2, List.of(), List.of()));
    }

    @Test
    void dropAFingerprintTheBucketLacksIsRefused() {
        var shelf = BucketedRecipes.of(0, List.of(recipe(1, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> shelf.apply(0, List.of(new Fingerprint(1, 2)), List.of()));
    }

    @Test
    void aCategoryPastFourTimesItsBucketsSplitsThemKeepingEveryEntry() {
        var random = new Random(5);
        var entries = new ArrayList<Entry<String>>();
        for (var i = 0; i < 2_001; i++) {
            entries.add(recipe(random.nextLong(), i));
        }
        var small = BucketedRecipes.of(0, entries.subList(0, 2_000));
        assertSame(small, small.split(), "Four times what one bucket was made for stays");
        var grown = BucketedRecipes.of(0, entries);
        var split = grown.split();
        assertEquals(RecipeBuckets.bitsFor(2_001), split.bits());
        assertEquals(2_001, split.size());
        var all = new ArrayList<Entry<String>>();
        for (var bucket = 0; bucket < 1 << split.bits(); bucket++) {
            for (var entry : split.bucket(bucket)) {
                assertEquals(bucket, RecipeBuckets.bucket(entry.fingerprint().partition(), split.bits()));
                all.add(entry);
            }
        }
        assertEquals(new java.util.HashSet<>(entries), new java.util.HashSet<>(all));
    }

    @Test
    void theEntriesComeBucketByBucketAndTheRecordsLeaveOutSkippedOnes() {
        var shelf = BucketedRecipes.of(1, List.of(recipe(1, 1), skipped(2, 2), recipe(4, 4)));
        assertEquals(List.of(skipped(2, 2), recipe(4, 4), recipe(1, 1)), shelf.entries());
        assertEquals(List.of("r4/4", "r1/1"), shelf.records());
    }
}
