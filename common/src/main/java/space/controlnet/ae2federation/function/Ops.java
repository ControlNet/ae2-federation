package space.controlnet.ae2federation.function;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Python's meaning of operators, conversions, built-in functions and list and string methods, over the runtime values:
 * {@code null} (None), {@link Boolean}, {@link Long} (int, 64-bit), {@link Double}, {@link String}, {@link ListValue},
 * {@link RecordValue}, {@link RangeValue}, and the host's own values (a block). Results and messages are CPython's,
 * except that integers are 64-bit: a result that does not fit is an error.
 */
public final class Ops {
    static final String TOO_LARGE = "integers are 64-bit; this result is too large";
    /**
     * The most items a list, or characters a string, may grow to. It bounds memory, and how long one step can take:
     * measured 2026-10-18 in the test JVM, sorting 10,000 strings takes about 5 ms and 100,000 about 60 ms.
     */
    static final long MAX_SEQUENCE = 100_000;
    private static final Pattern INT = Pattern.compile("[+-]?\\d(?:_?\\d)*");
    private static final Pattern FLOAT = Pattern.compile(
            "[+-]?(?:(?:\\d(?:_?\\d)*(?:\\.(?:\\d(?:_?\\d)*)?)?|\\.\\d(?:_?\\d)*)(?:[eE][+-]?\\d(?:_?\\d)*)?"
                    + "|(?i:inf|infinity|nan))");

    private Ops() {
    }

    // ---- kinds of value ----

    static String typeName(Object v) {
        return switch (v) {
            case null -> "NoneType";
            case Boolean b -> "bool";
            case Long l -> "int";
            case Double d -> "float";
            case String s -> "str";
            case ListValue l -> "list";
            case RangeValue r -> "range";
            case RecordValue r -> r.type.substring(r.type.lastIndexOf('.') + 1);
            default -> v.getClass().getSimpleName();
        };
    }

    private static boolean isInt(Object v) {
        return v instanceof Long || v instanceof Boolean;
    }

    private static boolean isNumber(Object v) {
        return isInt(v) || v instanceof Double;
    }

    private static long asLong(Object v) {
        return v instanceof Boolean b ? (b ? 1 : 0) : (Long) v;
    }

    private static double asDouble(Object v) {
        return v instanceof Double d ? d : asLong(v);
    }

    static RunError typeError(String message) {
        return new RunError("TypeError", message);
    }

    static RunError valueError(String message) {
        return new RunError("ValueError", message);
    }

    private static RunError overflow() {
        return new RunError("OverflowError", TOO_LARGE);
    }

    // ---- truth, equality, order ----

    public static boolean truthy(Object v) {
        return switch (v) {
            case null -> false;
            case Boolean b -> b;
            case Long l -> l != 0;
            case Double d -> d != 0;
            case String s -> !s.isEmpty();
            case ListValue l -> !l.items.isEmpty();
            case RangeValue r -> r.length() != 0;
            default -> true;
        };
    }

    public static boolean eq(Object a, Object b) {
        if (isNumber(a) && isNumber(b)) {
            return compareNumbers(a, b) == 0;
        }
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a instanceof ListValue x && b instanceof ListValue y) {
            if (x.items.size() != y.items.size()) {
                return false;
            }
            for (int i = 0; i < x.items.size(); i++) {
                if (!eq(x.items.get(i), y.items.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof RecordValue x && b instanceof RecordValue y) {
            if (!x.type.equals(y.type)) {
                return false;
            }
            for (var field : x.fields.keySet()) {
                if (!eq(x.fields.get(field), y.fields.get(field))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof RangeValue x && b instanceof RangeValue y) {
            long n = x.length();
            return n == y.length() && (n == 0 || x.start() == y.start() && (n == 1 || x.step() == y.step()));
        }
        return a.equals(b);
    }

    /** {@code is}: identity for lists, records and the host's values; the value for None, numbers and strings. */
    static boolean same(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a instanceof Boolean || a instanceof Long || a instanceof Double || a instanceof String) {
            return a.getClass() == b.getClass() && a.equals(b);
        }
        return a == b;
    }

    /** Exact comparison of two numbers, as Python compares an int with a float. */
    private static int compareNumbers(Object a, Object b) {
        if (isInt(a) && isInt(b)) {
            return Long.compare(asLong(a), asLong(b));
        }
        double x = asDouble(a);
        double y = asDouble(b);
        if (Double.isNaN(x) || Double.isNaN(y)) {
            return 2;
        }
        if (Double.isInfinite(x) || Double.isInfinite(y) || a instanceof Double && b instanceof Double) {
            return x == y ? 0 : x < y ? -1 : 1;
        }
        var left = a instanceof Double ? new BigDecimal(x) : BigDecimal.valueOf(asLong(a));
        var right = b instanceof Double ? new BigDecimal(y) : BigDecimal.valueOf(asLong(b));
        return left.compareTo(right);
    }

    /** {@code a op b} for {@code < <= > >=}. */
    static boolean order(BinaryOp op, Object a, Object b) {
        int c;
        if (isNumber(a) && isNumber(b)) {
            c = compareNumbers(a, b);
            if (c == 2) {
                return false;
            }
        } else if (a instanceof String x && b instanceof String y) {
            c = compareText(x, y);
        } else if (a instanceof ListValue x && b instanceof ListValue y) {
            // The first items that differ decide; if there are none, the shorter list is the smaller.
            int n = Math.min(x.items.size(), y.items.size());
            for (int i = 0; i < n; i++) {
                if (!eq(x.items.get(i), y.items.get(i))) {
                    return order(op, x.items.get(i), y.items.get(i));
                }
            }
            c = Integer.compare(x.items.size(), y.items.size());
        } else {
            throw typeError("'" + op.symbol + "' not supported between instances of '" + typeName(a) + "' and '"
                    + typeName(b) + "'");
        }
        return switch (op) {
            case LT -> c < 0;
            case LE -> c <= 0;
            case GT -> c > 0;
            default -> c >= 0;
        };
    }

    /** By code point, as Python orders strings. */
    private static int compareText(String a, String b) {
        int n = Math.min(a.length(), b.length());
        for (int i = 0; i < n; i++) {
            char x = a.charAt(i);
            char y = b.charAt(i);
            if (x != y) {
                // UTF-16 order is code point order except where a surrogate meets a character above it.
                if (Character.isSurrogate(x) || Character.isSurrogate(y)) {
                    return Integer.compare(a.codePointAt(i), b.codePointAt(i));
                }
                return Character.compare(x, y);
            }
        }
        return Integer.compare(a.length(), b.length());
    }

    // ---- operators ----

    public static Object unary(UnaryOp op, Object v) {
        if (op == UnaryOp.NOT) {
            return !truthy(v);
        }
        if (isInt(v)) {
            long l = asLong(v);
            if (l == Long.MIN_VALUE) {
                throw overflow();
            }
            return -l;
        }
        if (v instanceof Double d) {
            return -d;
        }
        throw typeError("bad operand type for unary -: '" + typeName(v) + "'");
    }

    /** Every binary operator but {@code and}/{@code or}, which the evaluator short-circuits. */
    public static Object binary(BinaryOp op, Object a, Object b) {
        return switch (op) {
            case EQ -> eq(a, b);
            case NE -> !eq(a, b);
            case IS -> same(a, b);
            case IS_NOT -> !same(a, b);
            case LT, LE, GT, GE -> order(op, a, b);
            case IN -> contains(b, a);
            case NOT_IN -> !contains(b, a);
            case AND, OR -> throw new IllegalStateException("short-circuit operators are evaluated by the caller");
            default -> arithmetic(op, a, b);
        };
    }

    private static Object arithmetic(BinaryOp op, Object a, Object b) {
        if (isInt(a) && isInt(b)) {
            long x = asLong(a);
            long y = asLong(b);
            try {
                return switch (op) {
                    case ADD -> Math.addExact(x, y);
                    case SUB -> Math.subtractExact(x, y);
                    case MUL -> Math.multiplyExact(x, y);
                    case DIV -> {
                        if (y == 0) {
                            throw new RunError("ZeroDivisionError", "division by zero");
                        }
                        yield (double) x / y;
                    }
                    case FLOOR_DIV -> {
                        if (y == 0) {
                            throw new RunError("ZeroDivisionError", "integer division or modulo by zero");
                        }
                        if (x == Long.MIN_VALUE && y == -1) {
                            throw overflow();
                        }
                        yield Math.floorDiv(x, y);
                    }
                    case MOD -> {
                        if (y == 0) {
                            throw new RunError("ZeroDivisionError", "integer modulo by zero");
                        }
                        yield Math.floorMod(x, y);
                    }
                    default -> throw new IllegalStateException(op.name());
                };
            } catch (ArithmeticException e) {
                throw overflow();
            }
        }
        if (isNumber(a) && isNumber(b)) {
            double x = asDouble(a);
            double y = asDouble(b);
            return switch (op) {
                case ADD -> x + y;
                case SUB -> x - y;
                case MUL -> x * y;
                case DIV -> {
                    if (y == 0) {
                        throw new RunError("ZeroDivisionError", "float division by zero");
                    }
                    yield x / y;
                }
                case FLOOR_DIV -> {
                    if (y == 0) {
                        throw new RunError("ZeroDivisionError", "float floor division by zero");
                    }
                    yield Math.floor(x / y);
                }
                case MOD -> {
                    if (y == 0) {
                        throw new RunError("ZeroDivisionError", "float modulo");
                    }
                    double r = x % y;
                    // Python's remainder takes the divisor's sign.
                    yield r != 0 && r < 0 != y < 0 ? r + y : r == 0 ? Math.copySign(0.0, y) : r;
                }
                default -> throw new IllegalStateException(op.name());
            };
        }
        if (op == BinaryOp.ADD && a instanceof String x && b instanceof String y) {
            grow((long) x.length() + y.length());
            return x + y;
        }
        if (op == BinaryOp.ADD && a instanceof ListValue x && b instanceof ListValue y) {
            grow((long) x.items.size() + y.items.size());
            var items = new ArrayList<>(x.items);
            items.addAll(y.items);
            return new ListValue(items);
        }
        if (op == BinaryOp.ADD && (a instanceof String || a instanceof ListValue)) {
            throw typeError("can only concatenate " + typeName(a) + " (not \"" + typeName(b) + "\") to "
                    + typeName(a));
        }
        if (op == BinaryOp.MUL && (a instanceof String || a instanceof ListValue)) {
            if (!isInt(b)) {
                throw typeError("can't multiply sequence by non-int of type '" + typeName(b) + "'");
            }
            return repeat(a, asLong(b));
        }
        if (op == BinaryOp.MUL && (b instanceof String || b instanceof ListValue)) {
            if (!isInt(a)) {
                throw typeError("can't multiply sequence by non-int of type '" + typeName(a) + "'");
            }
            return repeat(b, asLong(a));
        }
        throw typeError("unsupported operand type(s) for " + op.symbol + ": '" + typeName(a) + "' and '"
                + typeName(b) + "'");
    }

    private static Object repeat(Object sequence, long times) {
        long n = Math.max(0, times);
        long size = sequence instanceof String s ? s.length() : ((ListValue) sequence).items.size();
        if (size != 0) {
            grow(n > MAX_SEQUENCE ? n : n * size);
        }
        if (sequence instanceof String s) {
            return s.repeat((int) n);
        }
        var items = new ArrayList<>();
        for (long i = 0; i < n; i++) {
            items.addAll(((ListValue) sequence).items);
        }
        return new ListValue(items);
    }

    /** {@code current op= value}: {@code +=} and {@code *=} change a list in place, as in Python. */
    static Object inPlace(BinaryOp op, Object current, Object value) {
        if (current instanceof ListValue list && op == BinaryOp.ADD) {
            var added = items(value);
            grow((long) list.items.size() + added.size());
            list.items.addAll(added);
            return list;
        }
        if (current instanceof ListValue list && op == BinaryOp.MUL && isInt(value)) {
            var repeated = (ListValue) repeat(list, asLong(value));
            list.items.clear();
            list.items.addAll(repeated.items);
            return list;
        }
        return binary(op, current, value);
    }

    static boolean contains(Object container, Object item) {
        switch (container) {
            case ListValue l -> {
                for (var element : l.items) {
                    if (eq(element, item)) {
                        return true;
                    }
                }
                return false;
            }
            case String s -> {
                if (!(item instanceof String text)) {
                    throw typeError("'in <string>' requires string as left operand, not " + typeName(item));
                }
                return s.contains(text);
            }
            case RangeValue r -> {
                if (!isNumber(item) || item instanceof Double d && (d != Math.rint(d) || Double.isInfinite(d))) {
                    return false;
                }
                long v = item instanceof Double d ? (long) (double) d : asLong(item);
                long n = r.length();
                if (n == 0 || (v - r.start()) % r.step() != 0) {
                    return false;
                }
                long index = (v - r.start()) / r.step();
                return index >= 0 && index < n;
            }
            case null, default -> throw typeError("argument of type '" + typeName(container) + "' is not iterable");
        }
    }

    // ---- sequences ----

    /** How many items a {@code for} loop over the value sees right now (a list may change while it runs). */
    public static long length(Object iterable) {
        return switch (iterable) {
            case ListValue l -> l.items.size();
            case String s -> s.codePointCount(0, s.length());
            case RangeValue r -> r.length();
            case null, default -> throw typeError("'" + typeName(iterable) + "' object is not iterable");
        };
    }

    /** Item {@code i} (0-based, in range) of a list, string or range. */
    public static Object item(Object iterable, long i) {
        return switch (iterable) {
            case ListValue l -> l.items.get((int) i);
            case String s -> {
                int at = s.offsetByCodePoints(0, (int) i);
                yield new String(Character.toChars(s.codePointAt(at)));
            }
            case RangeValue r -> r.get(i);
            case null, default -> throw typeError("'" + typeName(iterable) + "' object is not iterable");
        };
    }

    /** Stops a list or string from growing past {@link #MAX_SEQUENCE}. */
    static void grow(long size) {
        if (size > MAX_SEQUENCE) {
            throw new RunError("MemoryError", "lists and strings hold at most " + MAX_SEQUENCE + " items");
        }
    }

    private static List<Object> items(Object iterable) {
        long n = length(iterable);
        if (iterable instanceof RangeValue) {
            grow(n);
        }
        var out = new ArrayList<>();
        for (long i = 0; i < n; i++) {
            out.add(item(iterable, i));
        }
        return out;
    }

    public static Object index(Object target, Object index) {
        if (!(target instanceof ListValue || target instanceof String || target instanceof RangeValue)) {
            throw typeError("'" + typeName(target) + "' object is not subscriptable");
        }
        if (!isInt(index)) {
            throw typeError(switch (target) {
                case ListValue l -> "list indices must be integers or slices, not " + typeName(index);
                case String s -> "string indices must be integers, not '" + typeName(index) + "'";
                default -> "range indices must be integers or slices, not " + typeName(index);
            });
        }
        long n = length(target);
        long i = asLong(index);
        if (i < 0) {
            i += n;
        }
        if (i < 0 || i >= n) {
            throw new RunError("IndexError", switch (target) {
                case ListValue l -> "list index out of range";
                case String s -> "string index out of range";
                default -> "range object index out of range";
            });
        }
        return item(target, i);
    }

    public static void setIndex(Object target, Object index, Object value) {
        if (!(target instanceof ListValue list)) {
            throw typeError("'" + typeName(target) + "' object does not support item assignment");
        }
        if (!isInt(index)) {
            throw typeError("list indices must be integers or slices, not " + typeName(index));
        }
        long n = list.items.size();
        long i = asLong(index);
        if (i < 0) {
            i += n;
        }
        if (i < 0 || i >= n) {
            throw new RunError("IndexError", "list assignment index out of range");
        }
        list.items.set((int) i, value);
    }

    /** CPython's slice adjustment: start, stop, step and the number of items for a sequence of this length. */
    private record Bounds(long start, long stop, long step, long count) {
    }

    private static Bounds bounds(long length, Object lower, Object upper, Object stepValue) {
        for (var part : new Object[] {lower, upper, stepValue}) {
            if (part != null && !isInt(part)) {
                throw typeError("slice indices must be integers or None or have an __index__ method");
            }
        }
        long step = stepValue == null ? 1 : asLong(stepValue);
        if (step == 0) {
            throw valueError("slice step cannot be zero");
        }
        long start = lower == null ? (step < 0 ? length - 1 : 0) : clamp(asLong(lower), length, step);
        long stop = upper == null ? (step < 0 ? -1 : length) : clamp(asLong(upper), length, step);
        long count = step < 0 ? (stop < start ? (start - stop - 1) / -step + 1 : 0)
                : (start < stop ? (stop - start - 1) / step + 1 : 0);
        return new Bounds(start, stop, step, count);
    }

    private static long clamp(long index, long length, long step) {
        if (index < 0) {
            index += length;
            if (index < 0) {
                return step < 0 ? -1 : 0;
            }
        } else if (index >= length) {
            return step < 0 ? length - 1 : length;
        }
        return index;
    }

    public static Object slice(Object target, Object lower, Object upper, Object step) {
        if (!(target instanceof ListValue || target instanceof String || target instanceof RangeValue)) {
            throw typeError("'" + typeName(target) + "' object is not subscriptable");
        }
        var b = bounds(length(target), lower, upper, step);
        if (target instanceof RangeValue r) {
            return new RangeValue(r.get(b.start), r.get(b.start) + b.count * b.step * r.step(), b.step * r.step());
        }
        if (target instanceof String s) {
            var out = new StringBuilder();
            int[] points = s.codePoints().toArray();
            for (long i = 0, at = b.start; i < b.count; i++, at += b.step) {
                out.appendCodePoint(points[(int) at]);
            }
            return out.toString();
        }
        var list = ((ListValue) target).items;
        var out = new ArrayList<>();
        for (long i = 0, at = b.start; i < b.count; i++, at += b.step) {
            out.add(list.get((int) at));
        }
        return new ListValue(out);
    }

    public static void setSlice(Object target, Object lower, Object upper, Object step, Object value) {
        if (!(target instanceof ListValue list)) {
            throw typeError("'" + typeName(target) + "' object does not support item assignment");
        }
        var values = items(value);
        var b = bounds(list.items.size(), lower, upper, step);
        if (b.step == 1) {
            long stop = Math.max(b.start, b.stop);
            grow(list.items.size() - (stop - b.start) + values.size());
            list.items.subList((int) b.start, (int) stop).clear();
            list.items.addAll((int) b.start, values);
            return;
        }
        if (values.size() != b.count) {
            throw valueError("attempt to assign sequence of size " + values.size() + " to extended slice of size "
                    + b.count);
        }
        for (long i = 0, at = b.start; i < b.count; i++, at += b.step) {
            list.items.set((int) at, values.get((int) i));
        }
    }

    // ---- text ----

    public static String str(Object v) {
        return v instanceof String s ? s : repr(v);
    }

    public static String repr(Object v) {
        return switch (v) {
            case null -> "None";
            case Boolean b -> b ? "True" : "False";
            case Long l -> Long.toString(l);
            case Double d -> floatRepr(d);
            case String s -> stringRepr(s);
            case ListValue l -> {
                var parts = new ArrayList<String>();
                l.items.forEach(item -> parts.add(repr(item)));
                yield "[" + String.join(", ", parts) + "]";
            }
            case RangeValue r -> "range(" + r.start() + ", " + r.stop() + (r.step() == 1 ? "" : ", " + r.step()) + ")";
            case RecordValue r -> {
                var parts = new ArrayList<String>();
                r.fields.forEach((name, value) -> parts.add(name + "=" + repr(value)));
                yield typeName(r) + "(" + String.join(", ", parts) + ")";
            }
            default -> v.toString();
        };
    }

    /** Python's shortest repr: fixed notation for exponents from -4 to 15, else {@code 1e+16}. */
    static String floatRepr(double d) {
        if (Double.isNaN(d)) {
            return "nan";
        }
        if (Double.isInfinite(d)) {
            return d > 0 ? "inf" : "-inf";
        }
        if (d == 0) {
            return (1 / d < 0) ? "-0.0" : "0.0";
        }
        // Java's Double.toString gives the shortest digits that round-trip; only the layout differs from Python.
        var java = Double.toString(Math.abs(d));
        int e = java.indexOf('E');
        var mantissa = e < 0 ? java : java.substring(0, e);
        int exponent = e < 0 ? 0 : Integer.parseInt(java.substring(e + 1));
        int dot = mantissa.indexOf('.');
        var digits = mantissa.substring(0, dot) + mantissa.substring(dot + 1);
        int point = dot + exponent;
        int lead = 0;
        while (lead < digits.length() - 1 && digits.charAt(lead) == '0') {
            lead++;
            point--;
        }
        digits = digits.substring(lead).replaceAll("0+$", "");
        if (digits.isEmpty()) {
            digits = "0";
        }
        if (digits.length() == 2) {
            // Java never prints fewer than two digits (4.9E-324); Python prints one when one is enough (5e-324).
            var one = oneDigit(Math.abs(d), digits, point);
            if (one != null) {
                digits = one.substring(0, 1);
                point += one.length() - 1;
            }
        }
        var out = new StringBuilder(d < 0 ? "-" : "");
        if (point > -4 && point <= 16) {
            if (point <= 0) {
                out.append("0.").append("0".repeat(-point)).append(digits);
            } else if (point >= digits.length()) {
                out.append(digits).append("0".repeat(point - digits.length())).append(".0");
            } else {
                out.append(digits, 0, point).append('.').append(digits.substring(point));
            }
            return out.toString();
        }
        out.append(digits.charAt(0));
        if (digits.length() > 1) {
            out.append('.').append(digits.substring(1));
        }
        int shown = point - 1;
        out.append('e').append(shown < 0 ? '-' : '+');
        var magnitude = Integer.toString(Math.abs(shown));
        return out.append(magnitude.length() < 2 ? "0" + magnitude : magnitude).toString();
    }

    /**
     * The one digit, nearest the value, that reads back as it; {@code "10"} when rounding up carries; {@code null}
     * when no single digit does.
     */
    private static String oneDigit(double value, String digits, int point) {
        String best = null;
        var exact = new BigDecimal(value);
        BigDecimal bestError = null;
        int first = digits.charAt(0) - '0';
        for (int candidate = first; candidate <= first + 1; candidate++) {
            var decimal = new BigDecimal(java.math.BigInteger.valueOf(candidate), 1 - point);
            if (decimal.doubleValue() == value) {
                var error = decimal.subtract(exact).abs();
                if (bestError == null || error.compareTo(bestError) < 0) {
                    best = Integer.toString(candidate);
                    bestError = error;
                }
            }
        }
        return best;
    }

    private static String stringRepr(String s) {
        char quote = s.indexOf('\'') >= 0 && s.indexOf('"') < 0 ? '"' : '\'';
        var out = new StringBuilder().append(quote);
        s.codePoints().forEach(c -> {
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\t' -> out.append("\\t");
                case '\r' -> out.append("\\r");
                default -> {
                    if (c == quote) {
                        out.append('\\').append((char) c);
                    } else if (c < 0x20 || c == 0x7f) {
                        out.append(String.format("\\x%02x", c));
                    } else {
                        out.appendCodePoint(c);
                    }
                }
            }
        });
        return out.append(quote).toString();
    }

    // ---- built-in functions ----

    /** {@code name(args)} for Python's built-ins in the subset; arguments are positional. */
    /**
     * {@code name(args, keywords)}: keywords as CPython takes them ({@link Checker#BUILT_IN_KEYWORDS}, and
     * {@code default} of {@code min}/{@code max}), already matched by the checker.
     */
    public static Object builtIn(String name, List<Object> args, Map<String, Object> keywords) {
        if (keywords.isEmpty()) {
            return builtIn(name, args);
        }
        if (keywords.containsKey("default")) {
            // Only with one iterable; an empty one gives the default.
            return length(args.get(0)) == 0 ? keywords.get("default") : builtIn(name, args);
        }
        var params = Checker.BUILT_IN_KEYWORDS.get(name);
        var all = new ArrayList<>(args);
        for (int i = args.size(); i < params.size(); i++) {
            if (!keywords.containsKey(params.get(i))) {
                break;
            }
            all.add(keywords.get(params.get(i)));
        }
        return builtIn(name, all);
    }

    public static Object builtIn(String name, List<Object> args) {
        return switch (name) {
            case "int" -> args.isEmpty() ? 0L : toInt(args.get(0));
            case "float" -> args.isEmpty() ? 0.0 : toFloat(args.get(0));
            case "str" -> args.isEmpty() ? "" : str(args.get(0));
            case "bool" -> !args.isEmpty() && truthy(args.get(0));
            case "list" -> new ListValue(args.isEmpty() ? List.of() : items(args.get(0)));
            case "range" -> range(args);
            case "len" -> {
                var v = args.get(0);
                if (!(v instanceof ListValue || v instanceof String || v instanceof RangeValue)) {
                    throw typeError("object of type '" + typeName(v) + "' has no len()");
                }
                yield length(v);
            }
            case "abs" -> {
                var v = args.get(0);
                if (isInt(v)) {
                    long l = asLong(v);
                    if (l == Long.MIN_VALUE) {
                        throw overflow();
                    }
                    yield Math.abs(l);
                }
                if (v instanceof Double d) {
                    yield Math.abs(d);
                }
                throw typeError("bad operand type for abs(): '" + typeName(v) + "'");
            }
            case "min", "max" -> args.size() == 1 && args.get(0) instanceof RangeValue r ? rangeExtreme(name, r)
                    : extreme(name, args);
            case "sum" -> {
                var start = args.size() > 1 ? args.get(1) : 0L;
                if (start instanceof String) {
                    throw typeError("sum() can't sum strings [use ''.join(seq) instead]");
                }
                if (args.get(0) instanceof RangeValue r && isInt(start)) {
                    var total = rangeSum(r);
                    try {
                        yield Math.addExact((Long) total, asLong(start));
                    } catch (ArithmeticException e) {
                        throw overflow();
                    }
                }
                var values = new ArrayList<>();
                values.add(start);
                values.addAll(items(args.get(0)));
                yield sum(values);
            }
            case "round" -> round(args.get(0), args.size() > 1 ? args.get(1) : null);
            case "any" -> args.get(0) instanceof RangeValue r
                    ? r.length() > 1 || r.length() == 1 && r.start() != 0
                    : items(args.get(0)).stream().anyMatch(Ops::truthy);
            case "all" -> args.get(0) instanceof RangeValue r ? !contains(r, 0L)
                    : items(args.get(0)).stream().allMatch(Ops::truthy);
            default -> throw new IllegalArgumentException(name);
        };
    }

    static long toInt(Object v) {
        if (isInt(v)) {
            return asLong(v);
        }
        if (v instanceof Double d) {
            if (Double.isNaN(d)) {
                throw valueError("cannot convert float NaN to integer");
            }
            if (Double.isInfinite(d)) {
                throw new RunError("OverflowError", "cannot convert float infinity to integer");
            }
            if (d >= 0x1p63 || d < -0x1p63) {
                throw overflow();
            }
            return (long) (double) d;
        }
        if (v instanceof String s) {
            var text = s.strip();
            if (!INT.matcher(text).matches()) {
                throw valueError("invalid literal for int() with base 10: " + repr(s));
            }
            try {
                return Long.parseLong(text.replace("_", ""));
            } catch (NumberFormatException e) {
                throw overflow();
            }
        }
        throw typeError("int() argument must be a string, a bytes-like object or a real number, not '"
                + typeName(v) + "'");
    }

    static double toFloat(Object v) {
        if (isNumber(v)) {
            return asDouble(v);
        }
        if (v instanceof String s) {
            var text = s.strip();
            if (!FLOAT.matcher(text).matches()) {
                throw valueError("could not convert string to float: " + repr(s));
            }
            var lower = text.toLowerCase(Locale.ROOT);
            var unsigned = lower.replaceFirst("^[+-]", "");
            if (unsigned.startsWith("inf") || unsigned.equals("nan")) {
                double special = unsigned.equals("nan") ? Double.NaN : Double.POSITIVE_INFINITY;
                return lower.startsWith("-") ? -special : special;
            }
            return Double.parseDouble(text.replace("_", ""));
        }
        throw typeError("float() argument must be a string or a real number, not '" + typeName(v) + "'");
    }

    private static RangeValue range(List<Object> args) {
        for (var arg : args) {
            if (!isInt(arg)) {
                throw typeError("'" + typeName(arg) + "' object cannot be interpreted as an integer");
            }
        }
        long start = args.size() == 1 ? 0 : asLong(args.get(0));
        long stop = asLong(args.get(args.size() == 1 ? 0 : 1));
        long step = args.size() == 3 ? asLong(args.get(2)) : 1;
        if (step == 0) {
            throw valueError("range() arg 3 must not be zero");
        }
        return new RangeValue(start, stop, step);
    }

    private static Object extreme(String name, List<Object> args) {
        var values = args.size() == 1 ? items(args.get(0)) : args;
        if (values.isEmpty()) {
            throw valueError(name + "() iterable argument is empty");
        }
        var best = values.get(0);
        var op = name.equals("max") ? BinaryOp.GT : BinaryOp.LT;
        for (var value : values.subList(1, values.size())) {
            if (order(op, value, best)) {
                best = value;
            }
        }
        return best;
    }

    /** {@code min}/{@code max} of a range without walking it. */
    private static Object rangeExtreme(String name, RangeValue r) {
        long n = r.length();
        if (n == 0) {
            throw valueError(name + "() iterable argument is empty");
        }
        long first = r.start();
        long last = r.get(n - 1);
        return name.equals("min") == r.step() > 0 ? first : last;
    }

    /** The sum of a range without walking it: n * (first + last) / 2. */
    private static Object rangeSum(RangeValue r) {
        long n = r.length();
        if (n == 0) {
            return 0L;
        }
        var total = java.math.BigInteger.valueOf(n)
                .multiply(java.math.BigInteger.valueOf(r.start()).add(java.math.BigInteger.valueOf(r.get(n - 1))))
                .shiftRight(1);
        if (total.bitLength() > 63) {
            throw overflow();
        }
        return total.longValue();
    }

    /**
     * Python 3.12's {@code sum} over its start then the items: exact for ints, compensated (Neumaier) once a float
     * appears.
     */
    private static Object sum(List<Object> values) {
        long total = 0;
        int i = 0;
        for (; i < values.size(); i++) {
            var v = values.get(i);
            if (v instanceof Double) {
                break;
            }
            if (!isInt(v)) {
                throw typeError("unsupported operand type(s) for +: 'int' and '" + typeName(v) + "'");
            }
            try {
                total = Math.addExact(total, asLong(v));
            } catch (ArithmeticException e) {
                throw overflow();
            }
        }
        if (i == values.size()) {
            return total;
        }
        double result = total;
        double c = 0;
        for (; i < values.size(); i++) {
            var v = values.get(i);
            if (!isNumber(v)) {
                throw typeError("unsupported operand type(s) for +: 'float' and '" + typeName(v) + "'");
            }
            double x = asDouble(v);
            double t = result + x;
            c += Math.abs(result) >= Math.abs(x) ? (result - t) + x : (x - t) + result;
            result = t;
        }
        return c != 0 && Double.isFinite(c) ? result + c : result;
    }

    private static Object round(Object v, Object digits) {
        if (digits != null && !isInt(digits)) {
            throw typeError("'" + typeName(digits) + "' object cannot be interpreted as an integer");
        }
        if (isInt(v)) {
            long l = asLong(v);
            if (digits == null || asLong(digits) >= 0) {
                return l;
            }
            long n = asLong(digits);
            if (n < -19) {
                return 0L;
            }
            return BigDecimal.valueOf(l).setScale((int) n, RoundingMode.HALF_EVEN).longValue();
        }
        if (v instanceof Double d) {
            if (digits == null) {
                return toInt(Math.rint(d));
            }
            long n = asLong(digits);
            if (Double.isNaN(d) || Double.isInfinite(d) || n > 323) {
                return d;
            }
            if (n < -308) {
                return Math.copySign(0.0, d);
            }
            return new BigDecimal(d).setScale((int) n, RoundingMode.HALF_EVEN).doubleValue();
        }
        throw typeError("type " + typeName(v) + " doesn't define __round__ method");
    }

    // ---- methods ----

    /** {@code receiver.name(args)} for list and str methods; arguments bound as the method's signature says. */
    public static Object method(Object receiver, String name, List<Object> args, Map<String, Object> keywords) {
        List<Signature> table = receiver instanceof ListValue ? Checker.LIST_METHODS
                : receiver instanceof String ? Checker.STR_METHODS
                : receiver instanceof RangeValue ? Checker.RANGE_METHODS : List.of();
        var signature = table.stream().filter(s -> s.name().equals(name)).findFirst()
                .orElseThrow(() -> new RunError("AttributeError", "'" + typeName(receiver)
                        + "' object has no attribute '" + name + "'"));
        var a = bind(signature, args, keywords);
        if (receiver instanceof ListValue list) {
            return listMethod(list, name, a);
        }
        if (receiver instanceof RangeValue range) {
            boolean in = contains(range, a[0]);
            if (name.equals("count")) {
                return in ? 1L : 0L;
            }
            if (!in) {
                throw valueError(repr(a[0]) + " is not in range");
            }
            long value = a[0] instanceof Double d ? (long) (double) d : asLong(a[0]);
            return (value - range.start()) / range.step();
        }
        return strMethod((String) receiver, name, a);
    }

    /** Arguments in parameter order, defaults filled in. */
    static Object[] bind(Signature signature, List<Object> args, Map<String, Object> keywords) {
        var params = signature.args();
        var out = new Object[params.size()];
        for (int i = 0; i < params.size(); i++) {
            var param = params.get(i);
            if (i < args.size()) {
                out[i] = args.get(i);
            } else if (keywords.containsKey(param.name())) {
                out[i] = keywords.get(param.name());
            } else {
                out[i] = literal(param.defaultValue());
            }
        }
        return out;
    }

    /** A default written in a signature: {@code None}, {@code True}, {@code False}, or a number. */
    static Object literal(String text) {
        return switch (text) {
            case "None" -> null;
            case "True" -> true;
            case "False" -> false;
            default -> text.contains(".") ? (Object) Double.parseDouble(text) : (Object) Long.parseLong(text);
        };
    }

    private static Object listMethod(ListValue list, String name, Object[] a) {
        var items = list.items;
        switch (name) {
            case "append" -> {
                grow(items.size() + 1L);
                items.add(a[0]);
            }
            case "extend" -> {
                var added = items(a[0]);
                grow((long) items.size() + added.size());
                items.addAll(added);
            }
            case "insert" -> {
                grow(items.size() + 1L);
                long i = integer(a[0]);
                long n = items.size();
                if (i < 0) {
                    i = Math.max(0, i + n);
                }
                items.add((int) Math.min(i, n), a[1]);
            }
            case "pop" -> {
                if (items.isEmpty()) {
                    throw new RunError("IndexError", "pop from empty list");
                }
                long i = integer(a[0]);
                long n = items.size();
                if (i < 0) {
                    i += n;
                }
                if (i < 0 || i >= n) {
                    throw new RunError("IndexError", "pop index out of range");
                }
                return items.remove((int) i);
            }
            case "remove" -> {
                for (int i = 0; i < items.size(); i++) {
                    if (eq(items.get(i), a[0])) {
                        items.remove(i);
                        return null;
                    }
                }
                throw valueError("list.remove(x): x not in list");
            }
            case "index" -> {
                for (int i = 0; i < items.size(); i++) {
                    if (eq(items.get(i), a[0])) {
                        return (long) i;
                    }
                }
                throw valueError(repr(a[0]) + " is not in list");
            }
            case "count" -> {
                return items.stream().filter(item -> eq(item, a[0])).count();
            }
            case "reverse" -> Collections.reverse(items);
            case "sort" -> {
                boolean reverse = truthy(a[0]);
                // Reversing around a stable sort keeps equal items in their order, as Python's reverse=True does.
                var sorted = new ArrayList<>(items);
                if (reverse) {
                    Collections.reverse(sorted);
                }
                sorted = sort(sorted);
                if (reverse) {
                    Collections.reverse(sorted);
                }
                items.clear();
                items.addAll(sorted);
            }
            case "clear" -> items.clear();
            case "copy" -> {
                return new ListValue(items);
            }
            default -> throw new IllegalArgumentException(name);
        }
        return null;
    }

    /**
     * Python's stable sort. Numbers without NaN, or strings, use Java's stable sort with the same order; anything
     * else goes through {@link #mergeSort}, which fails as CPython does.
     */
    private static ArrayList<Object> sort(ArrayList<Object> items) {
        boolean numbers = items.stream().allMatch(v -> isNumber(v) && !(v instanceof Double d && d.isNaN()));
        if (numbers) {
            items.sort(Ops::compareNumbers);
            return items;
        }
        if (items.stream().allMatch(String.class::isInstance)) {
            // Without surrogates UTF-16 order is code point order, and Java's own comparison is much faster.
            boolean plain = items.stream().noneMatch(v -> ((String) v).chars().anyMatch(c -> Character.isSurrogate((char) c)));
            items.sort(plain ? (x, y) -> ((String) x).compareTo((String) y)
                    : (x, y) -> compareText((String) x, (String) y));
            return items;
        }
        return mergeSort(items);
    }

    /**
     * A stable sort that only asks whether a later item is less than an earlier one, as CPython's does, so a mixed
     * list fails with CPython's message. Unlike CPython, a failed sort leaves the list as it was.
     */
    private static ArrayList<Object> mergeSort(List<Object> items) {
        if (items.size() < 2) {
            return new ArrayList<>(items);
        }
        int middle = items.size() / 2;
        var left = mergeSort(items.subList(0, middle));
        var right = mergeSort(items.subList(middle, items.size()));
        var out = new ArrayList<>(items.size());
        int i = 0;
        int j = 0;
        while (i < left.size() && j < right.size()) {
            out.add(order(BinaryOp.LT, right.get(j), left.get(i)) ? right.get(j++) : left.get(i++));
        }
        out.addAll(left.subList(i, left.size()));
        out.addAll(right.subList(j, right.size()));
        return out;
    }

    /** An index argument of a method. */
    private static long integer(Object v) {
        if (!isInt(v)) {
            throw typeError("'" + typeName(v) + "' object cannot be interpreted as an integer");
        }
        return asLong(v);
    }

    private static Object strMethod(String s, String name, Object[] a) {
        return switch (name) {
            case "startswith", "endswith" -> {
                if (!(a[0] instanceof String affix)) {
                    throw typeError(name + " first arg must be str or a tuple of str, not " + typeName(a[0]));
                }
                yield name.equals("startswith") ? s.startsWith(affix) : s.endsWith(affix);
            }
            case "lower" -> s.toLowerCase(Locale.ROOT);
            case "upper" -> s.toUpperCase(Locale.ROOT);
            case "strip" -> s.strip();
            case "split" -> split(s, a[0]);
            case "join" -> {
                if (!(a[0] instanceof ListValue || a[0] instanceof String || a[0] instanceof RangeValue)) {
                    throw typeError("can only join an iterable");
                }
                var parts = items(a[0]);
                for (int i = 0; i < parts.size(); i++) {
                    if (!(parts.get(i) instanceof String)) {
                        throw typeError("sequence item " + i + ": expected str instance, " + typeName(parts.get(i))
                                + " found");
                    }
                }
                long size = (long) s.length() * Math.max(0, parts.size() - 1);
                for (var part : parts) {
                    size += ((String) part).length();
                }
                grow(size);
                yield String.join(s, parts.stream().map(String.class::cast).toList());
            }
            case "replace" -> {
                for (int i = 0; i < 2; i++) {
                    if (!(a[i] instanceof String)) {
                        throw typeError("replace() argument " + (i + 1) + " must be str, not " + typeName(a[i]));
                    }
                }
                var old = (String) a[0];
                var replacement = (String) a[1];
                long matches = old.isEmpty() ? s.length() + 1L : 0;
                for (int at = old.isEmpty() ? -1 : s.indexOf(old); at >= 0; at = s.indexOf(old, at + old.length())) {
                    matches++;
                }
                grow(s.length() + matches * (replacement.length() - old.length()));
                yield s.replace(old, replacement);
            }
            case "find" -> {
                int at = s.indexOf(text(a[0]));
                yield at < 0 ? -1L : (long) s.codePointCount(0, at);
            }
            case "count" -> {
                var sub = text(a[0]);
                if (sub.isEmpty()) {
                    yield (long) s.codePointCount(0, s.length()) + 1;
                }
                long count = 0;
                for (int at = s.indexOf(sub); at >= 0; at = s.indexOf(sub, at + sub.length())) {
                    count++;
                }
                yield count;
            }
            default -> throw new IllegalArgumentException(name);
        };
    }

    private static String text(Object v) {
        if (!(v instanceof String s)) {
            throw typeError("must be str, not " + typeName(v));
        }
        return s;
    }

    private static ListValue split(String s, Object separator) {
        var parts = new ArrayList<Object>();
        if (separator == null) {
            for (var part : s.strip().split("\\s+")) {
                if (!part.isEmpty()) {
                    parts.add(part);
                }
            }
            return new ListValue(parts);
        }
        if (!(separator instanceof String sep)) {
            throw typeError("must be str or None, not " + typeName(separator));
        }
        if (sep.isEmpty()) {
            throw valueError("empty separator");
        }
        int from = 0;
        for (int at = s.indexOf(sep); at >= 0; at = s.indexOf(sep, from)) {
            parts.add(s.substring(from, at));
            from = at + sep.length();
        }
        parts.add(s.substring(from));
        return new ListValue(parts);
    }
}
