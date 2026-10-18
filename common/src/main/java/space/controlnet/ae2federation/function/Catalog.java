package space.controlnet.ae2federation.function;

import java.util.List;
import java.util.Map;

/**
 * What the game knows and the language core does not: the built-in namespaces with their functions, and the values a
 * typed string can hold (item IDs for {@code Item}, interface aliases for {@code Interface}). The client fills it from
 * its own registries and the cached domain, so completion never waits for the server.
 */
public interface Catalog {
    Map<String, List<Signature>> namespaces();

    /** Values of a typed string matching what is typed so far, best first; empty for types without values. */
    List<Candidate> values(TypeRef type, String prefix);
}
