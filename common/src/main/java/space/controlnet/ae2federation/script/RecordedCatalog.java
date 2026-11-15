package space.controlnet.ae2federation.script;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * A catalog that notes each question asked of it and the answer, so a check made through it can be reused while
 * another catalog gives the same answers: a program checks the same against any catalog that answers alike. Asking a
 * literal's few questions again is much cheaper than checking a long program again.
 */
public final class RecordedCatalog implements Catalog {
    private final Catalog catalog;
    /** Each question once, by what was asked (a part may be null), with how to ask it again and its answer. */
    private final Map<List<Object>, Asked> asked = new LinkedHashMap<>();

    private record Asked(Function<Catalog, Object> question, Object answer) {
    }

    public RecordedCatalog(Catalog catalog) {
        this.catalog = catalog;
    }

    @SuppressWarnings("unchecked")
    private <T> T note(List<Object> key, Function<Catalog, Object> question) {
        var known = asked.get(key);
        if (known != null) {
            return (T) known.answer();
        }
        var answer = question.apply(catalog);
        asked.put(key, new Asked(question, answer));
        return (T) answer;
    }

    @Override
    public Map<String, List<Signature>> namespaces() {
        return note(Arrays.asList("namespaces"), Catalog::namespaces);
    }

    @Override
    public List<Candidate> values(TypeRef type, String prefix) {
        return note(Arrays.asList("values", type, prefix), c -> c.values(type, prefix));
    }

    @Override
    public List<Candidate> values(TypeRef type, String of, String prefix) {
        return note(Arrays.asList("values of", type, of, prefix), c -> c.values(type, of, prefix));
    }

    @Override
    public Set<String> kinds(String id) {
        return note(Arrays.asList("kinds", id), c -> c.kinds(id));
    }

    @Override
    public boolean exists(TypeRef type, String value) {
        return note(Arrays.asList("exists", type, value), c -> c.exists(type, value));
    }

    /** Whether {@code other} answers every question asked here as it was answered. */
    public boolean answersAlike(Catalog other) {
        for (var known : asked.values()) {
            if (!Objects.equals(known.question().apply(other), known.answer())) {
                return false;
            }
        }
        return true;
    }
}
