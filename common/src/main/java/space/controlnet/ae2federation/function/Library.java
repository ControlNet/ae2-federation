package space.controlnet.ae2federation.function;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The functions every program can call. Observations read the world through {@link Observations}; actions act through
 * {@link Effects} and may only be called from an action. The spike's set is small on purpose: one of each shape.
 */
public final class Library {
    /**
     * A built-in's signature. A {@code null} parameter type accepts any value; a variadic built-in repeats its last
     * parameter.
     */
    public record Builtin(String name, boolean action, List<ValueType> params, boolean variadic, ValueType result) {
        public boolean accepts(int count) {
            return variadic ? count >= params.size() - 1 : count == params.size();
        }

        /** The type the argument at {@code index} must have; {@code null} for any. */
        public ValueType param(int index) {
            return params.get(Math.min(index, params.size() - 1));
        }
    }

    private static final Map<String, Builtin> BUILTINS = Arrays.stream(new Builtin[] {
            // Observations: the stock of a resource in the network, whether the network is online, the strongest
            // redstone signal reaching the device.
            query("stock", ValueType.NUMBER, ValueType.RESOURCE),
            query("online", ValueType.BOOL),
            query("redstone_in", ValueType.NUMBER),
            // Whether an observation could be made: an unavailable value is neither false nor 0.
            query("available", ValueType.BOOL, (ValueType) null),
            // Filters a storage rule can take.
            new Builtin("allow", false, List.of(ValueType.RESOURCE), true, ValueType.FILTER),
            new Builtin("deny", false, List.of(ValueType.RESOURCE), true, ValueType.FILTER),
            query("all", ValueType.FILTER),
            // Actions.
            new Builtin("redstone", true, List.of(ValueType.NUMBER), false, ValueType.NOTHING),
            new Builtin("craft", true, List.of(ValueType.RESOURCE, ValueType.NUMBER), false, ValueType.NOTHING),
    }).collect(Collectors.toUnmodifiableMap(Builtin::name, Function.identity()));

    private Library() {
    }

    private static Builtin query(String name, ValueType result, ValueType... params) {
        return new Builtin(name, false, Arrays.asList(params), false, result);
    }

    public static Optional<Builtin> builtin(String name) {
        return Optional.ofNullable(BUILTINS.get(name));
    }
}
