package space.controlnet.ae2federation.function;

import java.util.List;
import java.util.stream.Collectors;

/** A type hint as written: a name and its arguments ({@code list[Item]}). Which names exist is the checker's concern. */
public record TypeRef(String name, List<TypeRef> args) {
    public TypeRef {
        args = List.copyOf(args);
    }

    @Override
    public String toString() {
        return args.isEmpty() ? name
                : name + args.stream().map(TypeRef::toString).collect(Collectors.joining(", ", "[", "]"));
    }
}
