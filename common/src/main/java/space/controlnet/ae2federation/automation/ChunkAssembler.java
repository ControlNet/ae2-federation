package space.controlnet.ae2federation.automation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Puts a text sent in chunks back together. Serverbound payloads are limited to 32 KiB, so program files travel in
 * pieces of {@link #CHUNK} characters, at most {@link #MAX_TEXT} in all (assistant proposal). Each sender and file has
 * one upload at a time: a chunk of a newer upload drops the older one, and an upload left unfinished for
 * {@link #STALE_TICKS} is forgotten.
 */
public final class ChunkAssembler<K> {
    /** Characters per chunk: at most three UTF-8 bytes each, well under the payload limit. */
    public static final int CHUNK = 8_000;
    public static final int MAX_TEXT = 64_000;
    public static final int STALE_TICKS = 600;

    private record Upload(int id, int count, String[] parts, long started) {
    }

    private final Map<K, Upload> uploads = new HashMap<>();

    /** Splits a text into the chunks to send; at least one, so an empty file is sent too. */
    public static List<String> split(String text) {
        if (text.length() > MAX_TEXT) {
            throw new IllegalArgumentException("a program may hold at most " + MAX_TEXT + " characters");
        }
        var chunks = new ArrayList<String>();
        var start = 0;
        while (start < text.length()) {
            var end = Math.min(text.length(), start + CHUNK);
            // Never cut a surrogate pair: the receiver checks each chunk as text.
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) {
                end--;
            }
            chunks.add(text.substring(start, end));
            start = end;
        }
        if (chunks.isEmpty()) {
            chunks.add("");
        }
        return chunks;
    }

    /**
     * Takes one chunk of upload {@code id}; gives the whole text once every chunk has arrived. Throws when the upload
     * cannot be valid: a bad index or count, or more text than allowed.
     */
    public Optional<String> accept(K key, int id, int index, int count, String part, long now) {
        if (count < 1 || count > (MAX_TEXT + CHUNK - 1) / CHUNK + 1 || index < 0 || index >= count
                || part.length() > CHUNK) {
            uploads.remove(key);
            throw new IllegalArgumentException("malformed upload");
        }
        var upload = uploads.get(key);
        if (upload == null || upload.id() != id || upload.count() != count) {
            upload = new Upload(id, count, new String[count], now);
            uploads.put(key, upload);
        }
        upload.parts()[index] = part;
        for (var received : upload.parts()) {
            if (received == null) {
                return Optional.empty();
            }
        }
        uploads.remove(key);
        var text = String.join("", upload.parts());
        if (text.length() > MAX_TEXT) {
            throw new IllegalArgumentException("a program may hold at most " + MAX_TEXT + " characters");
        }
        return Optional.of(text);
    }

    /** Forgets uploads started more than {@link #STALE_TICKS} ago. */
    public void expire(long now) {
        uploads.values().removeIf(upload -> now - upload.started() > STALE_TICKS);
    }

    public int pending() {
        return uploads.size();
    }
}
