package space.controlnet.ae2federation.function;

import java.util.List;
import java.util.Map;

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
}
