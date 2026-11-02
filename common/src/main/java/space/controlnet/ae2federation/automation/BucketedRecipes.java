package space.controlnet.ae2federation.automation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.automation.RecipeBuckets.Fingerprint;

/**
 * One category's saved viewer recipes in the buckets {@link RecipeBuckets} compares, each bucket with its digest. It
 * never changes: {@link #apply} and {@link #split} return a new one sharing the buckets they leave alone, so a program
 * reading the recipes keeps a whole view while a viewer's answer is being saved.
 *
 * <p>An entry is a recipe the viewer read, or one it fingerprinted but could not or would not read (its layout failed,
 * or it makes what it takes); those keep their fingerprint so the bucket still matches and the viewer is not asked for
 * them again.
 */
public final class BucketedRecipes<R> {
    /** What reading a fingerprinted recipe gave. */
    public enum Status {
        RECIPE, SKIPPED, FAILED
    }

    /** A fingerprinted recipe and, when it was read, its record. */
    public record Entry<R>(Fingerprint fingerprint, Status status, @Nullable R record) {
        public Entry {
            Objects.requireNonNull(fingerprint);
            if ((status == Status.RECIPE) != (record != null)) {
                throw new IllegalArgumentException("A read recipe has a record, and only it");
            }
        }
    }

    private final int bits;
    private final List<List<Entry<R>>> buckets;
    private final long[] digests;
    private final int size;

    private BucketedRecipes(int bits, List<List<Entry<R>>> buckets, long[] digests) {
        this.bits = bits;
        this.buckets = buckets;
        this.digests = digests;
        this.size = buckets.stream().mapToInt(List::size).sum();
    }

    /** The entries in {@code 2^bits} buckets, each by its partition's low bits. */
    public static <R> BucketedRecipes<R> of(int bits, List<Entry<R>> entries) {
        if (bits < 0 || bits > RecipeBuckets.MAX_BITS) {
            throw new IllegalArgumentException("bits " + bits);
        }
        var sorted = new ArrayList<List<Entry<R>>>(1 << bits);
        for (var bucket = 0; bucket < 1 << bits; bucket++) {
            sorted.add(new ArrayList<>());
        }
        for (var entry : entries) {
            sorted.get(RecipeBuckets.bucket(entry.fingerprint().partition(), bits)).add(entry);
        }
        var buckets = new ArrayList<List<Entry<R>>>(sorted.size());
        var digests = new long[sorted.size()];
        for (var bucket = 0; bucket < sorted.size(); bucket++) {
            buckets.add(List.copyOf(sorted.get(bucket)));
            digests[bucket] = digest(buckets.get(bucket));
        }
        return new BucketedRecipes<>(bits, Collections.unmodifiableList(buckets), digests);
    }

    public int bits() {
        return bits;
    }

    /** All entries, read or not. */
    public int size() {
        return size;
    }

    public List<Entry<R>> bucket(int bucket) {
        return buckets.get(bucket);
    }

    public long digest(int bucket) {
        return digests[bucket];
    }

    public List<Fingerprint> fingerprints(int bucket) {
        return buckets.get(bucket).stream().map(Entry::fingerprint).toList();
    }

    /** Every entry, bucket by bucket. */
    public List<Entry<R>> entries() {
        var out = new ArrayList<Entry<R>>(size);
        buckets.forEach(out::addAll);
        return out;
    }

    /** The records of the read recipes, bucket by bucket. */
    public List<R> records() {
        var out = new ArrayList<R>();
        for (var bucket : buckets) {
            for (var entry : bucket) {
                if (entry.record() != null) {
                    out.add(entry.record());
                }
            }
        }
        return out;
    }

    /**
     * The bucket with one entry dropped for each fingerprint in {@code drop} and the {@code add} entries added; the
     * other buckets as they are. Refuses a drop the bucket lacks and an entry of another bucket.
     */
    public BucketedRecipes<R> apply(int bucket, List<Fingerprint> drop, List<Entry<R>> add) {
        if (bucket < 0 || bucket >= buckets.size()) {
            throw new IllegalArgumentException("bucket " + bucket + " of " + buckets.size());
        }
        var changed = new ArrayList<>(buckets.get(bucket));
        for (var fingerprint : drop) {
            var index = indexOf(changed, fingerprint);
            if (index < 0) {
                throw new IllegalArgumentException("bucket " + bucket + " has no " + fingerprint);
            }
            changed.remove(index);
        }
        for (var entry : add) {
            if (RecipeBuckets.bucket(entry.fingerprint().partition(), bits) != bucket) {
                throw new IllegalArgumentException(entry.fingerprint() + " is not of bucket " + bucket);
            }
            changed.add(entry);
        }
        var buckets = new ArrayList<>(this.buckets);
        buckets.set(bucket, List.copyOf(changed));
        var digests = Arrays.copyOf(this.digests, this.digests.length);
        digests[bucket] = digest(buckets.get(bucket));
        return new BucketedRecipes<>(bits, Collections.unmodifiableList(buckets), digests);
    }

    /** Itself, or once it holds more than four times what its buckets were made for, its entries in more buckets. */
    public BucketedRecipes<R> split() {
        return RecipeBuckets.splits(size, bits) ? of(RecipeBuckets.bitsFor(size), entries()) : this;
    }

    private static <R> int indexOf(List<Entry<R>> entries, Fingerprint fingerprint) {
        for (var index = 0; index < entries.size(); index++) {
            if (entries.get(index).fingerprint().equals(fingerprint)) {
                return index;
            }
        }
        return -1;
    }

    private static <R> long digest(List<Entry<R>> bucket) {
        return RecipeBuckets.digest(bucket.stream().map(Entry::fingerprint).toList());
    }
}
