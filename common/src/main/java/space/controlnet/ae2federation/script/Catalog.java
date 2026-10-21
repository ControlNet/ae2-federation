package space.controlnet.ae2federation.script;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the game knows and the language core does not: the values a typed string can hold (item IDs for {@code Item},
 * interface aliases for {@code Interface}), and namespaces beyond the standard library. The client fills it from its
 * own registries and the cached domain, so completion never waits for the server.
 */
public interface Catalog {
    /** The namespaces with their functions: the standard library, plus any an addon brings. */
    default Map<String, List<Signature>> namespaces() {
        return StandardLibrary.NAMESPACES;
    }

    /** Values of a typed string matching what is typed so far, best first; empty for types without values. */
    List<Candidate> values(TypeRef type, String prefix);

    /**
     * The kinds of resource an ID names: {@code Item}, {@code Fluid}, or an addon's key type. Empty when nothing has
     * that ID; more than one when it is, say, both an item and a fluid.
     */
    Set<String> kinds(String id);

    /** Whether an {@code Interface} or {@code Network} with this ID or alias is in the domain. */
    boolean exists(TypeRef type, String value);
}
