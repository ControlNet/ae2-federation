package space.controlnet.ae2federation.automation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * How the server and a recipe viewer find the recipes that differ without sending them all. A category's recipes are
 * split into a power of two buckets by the low bits of a partition hash (of the server's recipe ID, else of the items
 * and amounts the recipe takes and makes), so a recipe stays in its bucket however its contents change. Each recipe
 * also has a content digest; a bucket's digest covers its recipes' partitions and digests, and a bucket whose digest
 * differs is compared recipe by recipe, so only the recipes that changed are read again.
 *
 * <p>Assistant choices (measured on ATM10, 2026-11-02, notes in viewer-recipe-scan-2026-11-01.md): about 500 recipes
 * a bucket; a category splits its buckets once it holds four times what they were made for; SHA-256 cut to 8 bytes.
 */
public final class RecipeBuckets {
    static final int RECIPES_PER_BUCKET = 500;
    static final int MAX_BITS = 16;

    /** A recipe as the bucket comparison sees it: the partition hash and the content digest. */
    public record Fingerprint(long partition, long digest) {
    }

    /** What a viewer has that the server lacks, to read; what the server has that the viewer lacks, to drop. */
    public record Difference(List<Fingerprint> read, List<Fingerprint> drop) {
    }

    private RecipeBuckets() {
    }

    /** The bits of a category of that many recipes: the fewest buckets of at most 500 recipes each. */
    public static int bitsFor(int recipes) {
        var bits = 0;
        while (bits < MAX_BITS && (long) RECIPES_PER_BUCKET << bits < recipes) {
            bits++;
        }
        return bits;
    }

    /** Whether a category holding that many recipes in buckets of those bits splits them again. */
    public static boolean splits(int recipes, int bits) {
        return bits < MAX_BITS && recipes > (4L * RECIPES_PER_BUCKET << bits);
    }

    public static int bucket(long partition, int bits) {
        return (int) (partition & ((1L << bits) - 1));
    }

    public static long hash(String text) {
        return hash(text.getBytes(StandardCharsets.UTF_8));
    }

    public static long hash(byte[] bytes) {
        return head(sha256().digest(bytes));
    }

    /** The digest of a bucket's recipes, in any order; 0 for an empty bucket, as for one the server never had. */
    public static long digest(Collection<Fingerprint> recipes) {
        if (recipes.isEmpty()) {
            return 0;
        }
        var sorted = new ArrayList<>(recipes);
        sorted.sort(Comparator.comparingLong(Fingerprint::partition).thenComparingLong(Fingerprint::digest));
        var sha = sha256();
        var buffer = java.nio.ByteBuffer.allocate(16);
        for (var recipe : sorted) {
            buffer.clear();
            buffer.putLong(recipe.partition()).putLong(recipe.digest());
            sha.update(buffer.array());
        }
        var digest = head(sha.digest());
        return digest == 0 ? 1 : digest;
    }

    /** The server's recipes of a bucket against a viewer's, each counted as often as it occurs. */
    public static Difference compare(List<Fingerprint> server, List<Fingerprint> viewer) {
        var serverCounts = counts(server);
        var read = new ArrayList<Fingerprint>();
        for (var recipe : viewer) {
            if (serverCounts.merge(recipe, -1, Integer::sum) < 0) {
                read.add(recipe);
            }
        }
        var viewerCounts = counts(viewer);
        var drop = new ArrayList<Fingerprint>();
        for (var recipe : server) {
            if (viewerCounts.merge(recipe, -1, Integer::sum) < 0) {
                drop.add(recipe);
            }
        }
        return new Difference(read, drop);
    }

    private static HashMap<Fingerprint, Integer> counts(List<Fingerprint> recipes) {
        var out = new HashMap<Fingerprint, Integer>();
        recipes.forEach(recipe -> out.merge(recipe, 1, Integer::sum));
        return out;
    }

    private static long head(byte[] digest) {
        var out = 0L;
        for (var i = 0; i < 8; i++) {
            out = out << 8 | (digest[i] & 0xff);
        }
        return out;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
    }
}
