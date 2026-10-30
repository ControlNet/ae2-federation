package space.controlnet.ae2federation.automation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.persistence.NetworkNameBook;

/**
 * Every Automation Interface on the server: its ID, unique on the server and drawn by it, where it stands, and the
 * alias a player gave it, unique within its domain. An unloaded interface keeps its entry; a broken one gives it up.
 * Computing devices keep their IDs in a book of their own, with their prefix.
 */
public final class AutomationInterfaceBook {
    public static final String PREFIX = "interface";

    /** Where an interface stands and its alias, empty when it has none. */
    public record Entry(String dimension, BlockPos position, String alias) {
        public Entry {
            position = position.immutable();
        }
    }

    public enum AliasResult {
        SET, UNCHANGED, TAKEN, INVALID
    }

    private final String prefix;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public AutomationInterfaceBook() {
        this(PREFIX);
    }

    public AutomationInterfaceBook(String prefix) {
        this.prefix = prefix;
    }

    /**
     * The ID of the interface at a place: the one it stored, while that is valid and not another interface's;
     * otherwise a new one. A copy of an interface (a structure carrying its ID elsewhere) therefore draws a new ID.
     */
    public String claim(@Nullable String stored, String dimension, BlockPos position, RandomGenerator random) {
        if (stored != null && ShortId.valid(prefix, stored)) {
            var entry = entries.get(stored);
            if (entry == null || entry.dimension().equals(dimension) && entry.position().equals(position)) {
                entries.put(stored, new Entry(dimension, position, entry == null ? "" : entry.alias()));
                return stored;
            }
        }
        var id = ShortId.draw(prefix, random, entries::containsKey);
        entries.put(id, new Entry(dimension, position, ""));
        return id;
    }

    /**
     * The other IDs recorded at the place of {@code id}. A block holds one ID, so these are stale: left by a block
     * whose saved ID was lost while the book kept its own, as when the server was killed between saving the two.
     */
    public java.util.List<String> othersAt(String id, String dimension, BlockPos position) {
        return entries.entrySet().stream().filter(entry -> !entry.getKey().equals(id)
                && entry.getValue().dimension().equals(dimension) && entry.getValue().position().equals(position))
                .map(Map.Entry::getKey).sorted().toList();
    }

    /** The interface was broken: its ID is free again. */
    public void release(String id) {
        entries.remove(id);
    }

    public Optional<Entry> entry(String id) {
        return Optional.ofNullable(entries.get(id));
    }

    public Map<String, Entry> entries() {
        return Collections.unmodifiableMap(entries);
    }

    /**
     * Sets or, with an empty text, clears an alias. {@code domain} holds the IDs of the interfaces in the same domain,
     * whose aliases it may not repeat. An alias is trimmed text as a network name is, and may not read as an ID.
     */
    public AliasResult setAlias(String id, String alias, Set<String> domain) {
        var entry = entries.get(id);
        var sanitized = NetworkNameBook.sanitize(alias).orElse(null);
        if (entry == null || sanitized == null || ShortId.valid(prefix, sanitized)) {
            return AliasResult.INVALID;
        }
        if (sanitized.equals(entry.alias())) {
            return AliasResult.UNCHANGED;
        }
        if (!sanitized.isEmpty() && domain.stream().anyMatch(other -> !other.equals(id)
                && entries.containsKey(other) && entries.get(other).alias().equals(sanitized))) {
            return AliasResult.TAKEN;
        }
        entries.put(id, new Entry(entry.dimension(), entry.position(), sanitized));
        return AliasResult.SET;
    }

    /** The interface a program names, by ID or alias, among those of one domain. */
    public Optional<String> resolve(String name, Set<String> domain) {
        if (domain.contains(name) && entries.containsKey(name)) {
            return Optional.of(name);
        }
        return domain.stream().filter(id -> entries.containsKey(id) && !name.isEmpty()
                && Objects.equals(entries.get(id).alias(), name)).findFirst();
    }

    /** Restores an entry as saved. */
    public void put(String id, Entry entry) {
        entries.put(id, entry);
    }
}
