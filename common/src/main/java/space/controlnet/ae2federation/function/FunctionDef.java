package space.controlnet.ae2federation.function;

import java.util.List;

/**
 * One function of a program. A {@link Kind#QUERY} only reads: a policy can ask it whether to share, or what to share,
 * as often as it needs. An {@link Kind#ACTION} may also act on the world (move resources, request crafting, emit
 * redstone) and returns nothing.
 */
public record FunctionDef(String id, String comment, Kind kind, String name, List<Param> params, ValueType result,
        List<Stmt> body) {
    public enum Kind {
        QUERY("query"), ACTION("action");

        private final String word;

        Kind(String word) {
            this.word = word;
        }

        public String word() {
            return word;
        }
    }

    public record Param(String id, String name, ValueType type) {
    }

    public FunctionDef {
        params = List.copyOf(params);
        body = List.copyOf(body);
        if (kind == Kind.ACTION && result != ValueType.NOTHING) throw new IllegalArgumentException("An action returns nothing");
        if (kind == Kind.QUERY && result == ValueType.NOTHING) throw new IllegalArgumentException("A query returns a value");
    }
}
