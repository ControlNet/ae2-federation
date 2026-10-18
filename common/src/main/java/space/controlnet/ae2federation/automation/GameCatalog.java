package space.controlnet.ae2federation.automation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import space.controlnet.ae2federation.function.Candidate;
import space.controlnet.ae2federation.function.Catalog;
import space.controlnet.ae2federation.function.TypeRef;

/**
 * The game's {@link Catalog}: interfaces and networks named by ID or by alias, and resources from the registries. The
 * lists are read on every query, since a library checks its files on demand and aliases change while it is loaded.
 * A string shaped like an ID is only ever that ID, even when something else carries it as a name.
 */
public final class GameCatalog implements Catalog {
    /** An interface or network: its ID and its alias or name, empty when it has none. */
    public record Named(String id, String name) {
    }

    /** What the registries know: the kinds of resource an ID names, and IDs to complete. */
    public interface Resources {
        Set<String> kinds(String id);

        List<Candidate> values(String type, String prefix);
    }

    private final Supplier<List<Named>> interfaces;
    private final Supplier<List<Named>> networks;
    private final Resources resources;

    public GameCatalog(Supplier<List<Named>> interfaces, Supplier<List<Named>> networks, Resources resources) {
        this.interfaces = interfaces;
        this.networks = networks;
        this.resources = resources;
    }

    @Override
    public List<Candidate> values(TypeRef type, String prefix) {
        return switch (type.name()) {
            case "Item", "Fluid", "Resource" -> resources.values(type.name(), prefix);
            case "Interface" -> named(interfaces.get(), prefix);
            case "Network" -> named(networks.get(), prefix);
            default -> List.of();
        };
    }

    @Override
    public Set<String> kinds(String id) {
        return resources.kinds(id);
    }

    @Override
    public boolean exists(TypeRef type, String value) {
        return !matches(type, value).isEmpty();
    }

    /** The IDs of the interfaces or networks the string names; more than one when a name is shared. */
    public List<String> matches(TypeRef type, String value) {
        var prefix = switch (type.name()) {
            case "Interface" -> AutomationInterfaceBook.PREFIX;
            case "Network" -> NetworkShortIdBook.PREFIX;
            default -> null;
        };
        if (prefix == null) {
            return List.of();
        }
        var all = prefix.equals(AutomationInterfaceBook.PREFIX) ? interfaces.get() : networks.get();
        var byId = ShortId.valid(prefix, value);
        return all.stream().filter(named -> byId ? named.id().equals(value) : named.name().equals(value))
                .map(Named::id).toList();
    }

    /** Aliases first, each with its ID beside it, then every ID with its alias. */
    private static List<Candidate> named(List<Named> all, String prefix) {
        var typed = prefix.toLowerCase(Locale.ROOT);
        var sorted = all.stream().sorted(Comparator.comparing(Named::id)).toList();
        var candidates = new ArrayList<Candidate>();
        sorted.stream().filter(named -> !named.name().isEmpty() && contains(named.name(), typed))
                .sorted(Comparator.comparing(Named::name))
                .forEach(named -> candidates.add(new Candidate(named.name(), Candidate.Kind.VALUE, named.id())));
        sorted.stream().filter(named -> contains(named.id(), typed))
                .forEach(named -> candidates.add(new Candidate(named.id(), Candidate.Kind.VALUE, named.name())));
        return candidates;
    }

    private static boolean contains(String text, String typed) {
        return text.toLowerCase(Locale.ROOT).contains(typed);
    }
}
