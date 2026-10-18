package space.controlnet.ae2federation.automation;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.domain.FederationDomainId;
import space.controlnet.ae2federation.domain.FederationDomainNodeId;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;

/**
 * The overworld's {@link AutomationInterfaceBook}: every interface's ID, place and alias, kept while it is unloaded,
 * so IDs stay unique on the server and programs can name an interface in an unloaded area. Which domain an interface
 * is in is not stored; it is read from the domain registry, where the interface is a node.
 */
public final class AutomationInterfaces extends SavedData {
    private static final String DATA_NAME = "ae2federation_automation_interfaces";
    private static final Factory<AutomationInterfaces> FACTORY =
            new Factory<>(AutomationInterfaces::new, AutomationInterfaces::load);

    private final AutomationInterfaceBook book = new AutomationInterfaceBook();

    public static AutomationInterfaces get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public String claim(@Nullable String stored, ServerLevel level, BlockPos position) {
        var before = book.entries().size();
        var id = book.claim(stored, FederationDomainRegistryAccess.dimension(level), position,
                ThreadLocalRandom.current());
        if (book.entries().size() != before || !id.equals(stored)) {
            setDirty();
        }
        return id;
    }

    public void release(String id) {
        if (book.entry(id).isPresent()) {
            book.release(id);
            setDirty();
        }
    }

    public Optional<AutomationInterfaceBook.Entry> entry(String id) {
        return book.entry(id);
    }

    /** Every interface on the server, loaded or not. */
    public java.util.Map<String, AutomationInterfaceBook.Entry> entries() {
        return book.entries();
    }

    public String alias(String id) {
        return book.entry(id).map(AutomationInterfaceBook.Entry::alias).orElse("");
    }

    public AutomationInterfaceBook.AliasResult setAlias(ServerLevel level, String id, String alias) {
        var domain = domainOf(level, id).map(found -> idsIn(level, found)).orElse(Set.of(id));
        var result = book.setAlias(id, alias, domain);
        if (result == AutomationInterfaceBook.AliasResult.SET) {
            setDirty();
        }
        return result;
    }

    /** The domain the interface is in now: one of its own while it links nothing; empty until it has published. */
    public Optional<FederationDomainId> domainOf(ServerLevel level, String id) {
        return book.entry(id).flatMap(entry -> FederationDomainRegistryAccess.get(level)
                .federationDomainOf(node(entry)).map(snapshot -> snapshot.federationDomainId()));
    }

    /** The IDs of the interfaces in a domain. */
    public Set<String> idsIn(ServerLevel level, FederationDomainId domain) {
        var registry = FederationDomainRegistryAccess.get(level);
        var ids = new LinkedHashSet<String>();
        book.entries().forEach((id, entry) -> registry.federationDomainOf(node(entry))
                .filter(snapshot -> snapshot.federationDomainId().equals(domain)).ifPresent(snapshot -> ids.add(id)));
        return ids;
    }

    /** The interface a program names, by ID or alias, within a domain. */
    public Optional<String> resolve(ServerLevel level, FederationDomainId domain, String name) {
        return book.resolve(name, idsIn(level, domain));
    }

    private static FederationDomainNodeId node(AutomationInterfaceBook.Entry entry) {
        return new FederationDomainNodeId(entry.dimension(), entry.position().asLong());
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var entries = new CompoundTag();
        book.entries().forEach((id, entry) -> {
            var saved = new CompoundTag();
            saved.putString("dimension", entry.dimension());
            saved.putLong("position", entry.position().asLong());
            saved.putString("alias", entry.alias());
            entries.put(id, saved);
        });
        tag.put("interfaces", entries);
        return tag;
    }

    private static AutomationInterfaces load(CompoundTag tag, HolderLookup.Provider registries) {
        var loaded = new AutomationInterfaces();
        var entries = tag.getCompound("interfaces");
        for (var id : entries.getAllKeys()) {
            var saved = entries.getCompound(id);
            // A malformed record is dropped rather than failing the whole save.
            if (ShortId.valid(AutomationInterfaceBook.PREFIX, id) && !saved.getString("dimension").isBlank()) {
                loaded.book.put(id, new AutomationInterfaceBook.Entry(saved.getString("dimension"),
                        BlockPos.of(saved.getLong("position")), saved.getString("alias")));
            }
        }
        return loaded;
    }
}
