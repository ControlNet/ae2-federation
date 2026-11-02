package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.automation.RecipeBuckets.Fingerprint;

/**
 * Viewer recipes in buckets: a category's recipes split by a hash of what tells them apart, each bucket compared by a
 * digest of its recipes' fingerprints, and a differing bucket compared recipe by recipe.
 */
final class RecipeBucketsTest {
    @Test
    void aCategoryHasAPowerOfTwoBucketsOfAboutFiveHundredRecipes() {
        assertEquals(0, RecipeBuckets.bitsFor(0));
        assertEquals(0, RecipeBuckets.bitsFor(500));
        assertEquals(1, RecipeBuckets.bitsFor(501));
        assertEquals(3, RecipeBuckets.bitsFor(3_919));
        // ATM10's create:automatic_shaped: 37,936 real recipes in 128 buckets.
        assertEquals(7, RecipeBuckets.bitsFor(37_936));
    }

    @Test
    void aCategoryWithFourTimesTheRecipesItsBucketsWereMadeForSplits() {
        assertFalse(RecipeBuckets.splits(2_000, 0));
        assertTrue(RecipeBuckets.splits(2_001, 0));
        assertFalse(RecipeBuckets.splits(8_000, 2));
        assertTrue(RecipeBuckets.splits(8_001, 2));
    }

    @Test
    void doublingTheBucketsSendsARecipeOfBucketBOnlyToBOrBPlusTheOldCount() {
        var random = new Random(3);
        for (var i = 0; i < 10_000; i++) {
            var partition = random.nextLong();
            for (var bits = 0; bits < 10; bits++) {
                var before = RecipeBuckets.bucket(partition, bits);
                var after = RecipeBuckets.bucket(partition, bits + 1);
                assertTrue(after == before || after == before + (1 << bits), partition + " at " + bits);
            }
        }
    }

    @Test
    void theSameTextHashesTheSameAndAnotherTextOtherwise() {
        assertEquals(RecipeBuckets.hash("id:create:milling/calcite"), RecipeBuckets.hash("id:create:milling/calcite"));
        assertNotEquals(RecipeBuckets.hash("id:create:milling/calcite"), RecipeBuckets.hash("id:create:milling/cobble"));
        assertNotEquals(RecipeBuckets.hash(new byte[] {1, 2}), RecipeBuckets.hash(new byte[] {2, 1}));
    }

    @Test
    void aBucketDigestIgnoresOrderButNotContent() {
        var a = new Fingerprint(1, 10);
        var b = new Fingerprint(2, 20);
        var c = new Fingerprint(3, 30);
        assertEquals(RecipeBuckets.digest(List.of(a, b, c)), RecipeBuckets.digest(List.of(c, a, b)));
        assertNotEquals(RecipeBuckets.digest(List.of(a, b, c)), RecipeBuckets.digest(List.of(a, b)));
        assertNotEquals(RecipeBuckets.digest(List.of(a, b)), RecipeBuckets.digest(List.of(a, new Fingerprint(2, 21))));
        // Two identical recipes count twice.
        assertNotEquals(RecipeBuckets.digest(List.of(a, b)), RecipeBuckets.digest(List.of(a, b, b)));
    }

    @Test
    void anEmptyBucketIsZeroAsABucketTheServerNeverHad() {
        assertEquals(0, RecipeBuckets.digest(List.of()));
        assertNotEquals(0, RecipeBuckets.digest(List.of(new Fingerprint(0, 0))));
    }

    @Test
    void comparingABucketReadsWhatTheServerLacksAndDropsWhatTheViewerLacks() {
        var kept = new Fingerprint(1, 10);
        var changedBefore = new Fingerprint(2, 20);
        var changedAfter = new Fingerprint(2, 21);
        var removed = new Fingerprint(3, 30);
        var added = new Fingerprint(4, 40);
        var diff = RecipeBuckets.compare(List.of(kept, changedBefore, removed), List.of(added, changedAfter, kept));
        // In the viewer's order, and dropped in the server's order.
        assertEquals(List.of(added, changedAfter), diff.read());
        assertEquals(List.of(changedBefore, removed), diff.drop());
    }

    @Test
    void comparingCountsDuplicates() {
        var twin = new Fingerprint(5, 50);
        var diff = RecipeBuckets.compare(List.of(twin), List.of(twin, twin));
        assertEquals(List.of(twin), diff.read());
        assertEquals(List.of(), diff.drop());
        var back = RecipeBuckets.compare(List.of(twin, twin), List.of(twin));
        assertEquals(List.of(), back.read());
        assertEquals(List.of(twin), back.drop());
    }

    @Test
    void comparingEqualBucketsFindsNothing() {
        var random = new Random(11);
        var recipes = new ArrayList<Fingerprint>();
        for (var i = 0; i < 500; i++) {
            recipes.add(new Fingerprint(random.nextLong(), random.nextLong()));
        }
        var shuffled = new ArrayList<>(recipes);
        java.util.Collections.shuffle(shuffled, random);
        var diff = RecipeBuckets.compare(recipes, shuffled);
        assertTrue(diff.read().isEmpty());
        assertTrue(diff.drop().isEmpty());
        assertEquals(RecipeBuckets.digest(recipes), RecipeBuckets.digest(shuffled));
    }
}
