package space.controlnet.ae2federation.script;

import java.util.List;

/**
 * The type rules the checker uses, over {@link TypeRef}s. {@code null} stands for a type already reported as wrong,
 * which fits everywhere so one mistake gives one problem.
 */
final class Types {
    static final TypeRef INT = simple("int");
    static final TypeRef FLOAT = simple("float");
    static final TypeRef STR = simple("str");
    static final TypeRef BOOL = simple("bool");
    static final TypeRef NONE = simple("None");
    /** The type of {@code []} with no element type known yet. */
    static final TypeRef EMPTY_LIST = simple("list");
    /** What {@code range(...)} gives: iterable, indexable and sliceable, but not a list. */
    static final TypeRef RANGE = simple("range");

    private Types() {
    }

    static TypeRef simple(String name) {
        return new TypeRef(name, List.of());
    }

    static TypeRef list(TypeRef element) {
        return new TypeRef("list", List.of(element));
    }

    /** {@code Optional[t]}: {@code t} or {@code None}. */
    static TypeRef optional(TypeRef t) {
        return t == null || t.equals(NONE) || isOptional(t) ? t : new TypeRef("Optional", List.of(t));
    }

    static boolean isOptional(TypeRef t) {
        return t != null && t.name().equals("Optional") && t.args().size() == 1;
    }

    /** The type without its {@code Optional}. */
    static TypeRef unwrap(TypeRef t) {
        return isOptional(t) ? t.args().get(0) : t;
    }

    static boolean isList(TypeRef t) {
        return t != null && t.name().equals("list");
    }

    /** A list's element type; {@code null} for {@code []} whose element type is not known. */
    static TypeRef element(TypeRef list) {
        return list.args().isEmpty() ? null : list.args().get(0);
    }

    static boolean isRange(TypeRef t) {
        return RANGE.equals(t);
    }

    /** A list, a string or a range: what {@code for} walks and {@code len} measures. */
    static boolean isIterable(TypeRef t) {
        return isList(t) || isStr(t) || isRange(t);
    }

    /** What iterating yields; {@code null} for {@code []} whose element type is not known. */
    static TypeRef iterated(TypeRef iterable) {
        return isList(iterable) ? element(iterable) : isRange(iterable) ? INT : STR;
    }

    static boolean isTypedString(TypeRef t) {
        return t != null && t.args().isEmpty() && StandardLibrary.TYPED_STRINGS.contains(t.name());
    }

    /** A {@code str} or a typed string: typed strings are strings when the program runs. */
    static boolean isStr(TypeRef t) {
        return t != null && (t.equals(STR) || isTypedString(t));
    }

    /** {@code bool} counts as a number, as it is an {@code int} in Python. */
    static boolean isNumber(TypeRef t) {
        return t != null && (t.equals(INT) || t.equals(FLOAT) || t.equals(BOOL));
    }

    /** The type of a number result: {@code float} if either side is one, else {@code int}. */
    static TypeRef number(TypeRef a, TypeRef b) {
        return a.equals(FLOAT) || b.equals(FLOAT) ? FLOAT : INT;
    }

    /** Whether a value of type {@code value} may be stored where {@code target} is expected. */
    static boolean assignable(TypeRef target, TypeRef value) {
        if (target == null || value == null || target.equals(value)) {
            return true;
        }
        if (isOptional(target)) {
            return value.equals(NONE) || assignable(target.args().get(0), unwrap(value));
        }
        // An optional value where a plain one is expected is checked when the program runs, as Python would.
        value = unwrap(value);
        if (target.equals(value)) {
            return true;
        }
        if (target.equals(FLOAT) && (value.equals(INT) || value.equals(BOOL)) || target.equals(INT) && value.equals(BOOL)) {
            return true;
        }
        if (target.name().equals("Resource") && (value.name().equals("Item") || value.name().equals("Fluid"))) {
            return true;
        }
        // An interface is a block: the interface's own block, as in mc.redstone(port) (user, 2026-10-18).
        if (target.name().equals("Block") && value.name().equals("Interface")) {
            return true;
        }
        if (target.equals(STR) && isTypedString(value)) {
            return true;
        }
        if (isList(target) && isList(value)) {
            return value.args().isEmpty() || assignable(element(target), element(value));
        }
        // A parameter taking any iterable, as list.extend and str.join do.
        if (target.name().equals("Iterable") && target.args().size() == 1 && isIterable(value)) {
            var item = iterated(value);
            return item == null || assignable(target.args().get(0), item);
        }
        return false;
    }

    /** The one type both fit into, or {@code null} if there is none. */
    static TypeRef common(TypeRef a, TypeRef b) {
        if (a.equals(b)) {
            return a;
        }
        if (a.equals(NONE)) {
            return optional(b);
        }
        if (b.equals(NONE)) {
            return optional(a);
        }
        if (assignable(a, b) && !isOptional(b)) {
            return a;
        }
        if (assignable(b, a) && !isOptional(a)) {
            return b;
        }
        if (isTypedString(a) && isTypedString(b)) {
            boolean resources = List.of("Item", "Fluid", "Resource").containsAll(List.of(a.name(), b.name()));
            return resources ? simple("Resource") : STR;
        }
        return null;
    }

    /** What CPython calls the type in its messages: {@code list}, {@code int}, {@code Order}; typed strings are str. */
    static String runtimeName(TypeRef t) {
        t = unwrap(t);
        if (isTypedString(t)) {
            return "str";
        }
        var name = t.name();
        return name.substring(name.lastIndexOf('.') + 1);
    }
}
