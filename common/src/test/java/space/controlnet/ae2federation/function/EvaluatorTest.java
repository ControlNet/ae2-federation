package space.controlnet.ae2federation.function;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Expressions and assignments as they run. Results are compared by repr, or by error type and message. */
final class EvaluatorTest {
    private static final String MAIN = """
            class Order:
                item: Item
                amount: int = 1

            def f() -> None:
                pass
            """;

    /** Test-only stand-in for the game: answers library reads from fixed values and records each call. */
    private static final class FakeHost implements Evaluator.Host {
        final List<String> calls = new ArrayList<>();

        @Override
        public Object call(String namespace, String name, List<Object> args) {
            calls.add(namespace + "." + name + Ops.repr(new ListValue(args)));
            return switch (namespace + "." + name) {
                case "ae2.amount" -> 640L;
                case "ae2.energy" -> 1.5e6;
                case "ae2.storage" -> new ListValue(List.of(RunnerTest.stack("minecraft:iron_ingot", 640)));
                default -> throw new RunError("RuntimeError", "the fake host has no " + namespace + "." + name);
            };
        }
    }

    private final FakeHost host = new FakeHost();
    private final Evaluator evaluator = new Evaluator(new Workspace(
            Map.of("main", ScriptParser.parse(MAIN).program(), "iron_tools", CheckerTest.FILES.get("iron_tools")),
            CompletionTest.CATALOG), host);
    private final Map<String, Object> locals = new HashMap<>();

    private static Expr parse(String expression) {
        var parsed = ScriptParser.parse("def f() -> None:\n    x = " + expression + "\n");
        assertEquals(List.of(), parsed.errors(), expression);
        return ((Stmt.Assign) parsed.program().functions().get(0).body().get(0)).value();
    }

    private static Stmt statement(String source) {
        var parsed = ScriptParser.parse("def f() -> None:\n    " + source + "\n");
        assertEquals(List.of(), parsed.errors(), source);
        return parsed.program().functions().get(0).body().get(0);
    }

    /** The repr of the value, or {@code Type: message}. */
    private String run(String expression) {
        try {
            return Ops.repr(evaluator.eval("main", parse(expression), locals));
        } catch (RunError e) {
            return e.toString();
        }
    }

    /** Runs assignments, then gives the repr of {@code result}, or the first error. */
    private String exec(String result, String... statements) {
        try {
            for (var source : statements) {
                switch (statement(source)) {
                    case Stmt.Assign a -> evaluator.store("main", a.target(), evaluator.eval("main", a.value(), locals),
                            locals);
                    case Stmt.AugAssign a -> evaluator.augment("main", a.target(), a.op(), a.value(), locals);
                    case Stmt.CallStmt c -> evaluator.eval("main", c.call(), locals);
                    default -> fail(source);
                }
            }
        } catch (RunError e) {
            return e.toString();
        }
        return run(result);
    }

    /** Expected values were produced by CPython 3.12.2 evaluating each expression. */
    static Stream<Arguments> cpython() {
        return Stream.of(
                Arguments.of("1 + 2", "3"),
                Arguments.of("7 // -2", "-4"),
                Arguments.of("-7 // 2", "-4"),
                Arguments.of("-7 % 3", "2"),
                Arguments.of("7 % -3", "-2"),
                Arguments.of("7.5 % -2", "-0.5"),
                Arguments.of("-7.5 // 2", "-4.0"),
                Arguments.of("7 / 2", "3.5"),
                Arguments.of("6 / 3", "2.0"),
                Arguments.of("1 / 0", "ZeroDivisionError: division by zero"),
                Arguments.of("1 // 0", "ZeroDivisionError: integer division or modulo by zero"),
                Arguments.of("1 % 0", "ZeroDivisionError: integer modulo by zero"),
                Arguments.of("1.0 / 0", "ZeroDivisionError: float division by zero"),
                Arguments.of("1.0 // 0", "ZeroDivisionError: float floor division by zero"),
                Arguments.of("1.0 % 0", "ZeroDivisionError: float modulo"),
                Arguments.of("2 * 3.5", "7.0"),
                Arguments.of("True + 1", "2"),
                Arguments.of("True * True", "1"),
                Arguments.of("-True", "-1"),
                Arguments.of("-0.0", "-0.0"),
                Arguments.of("0.1 + 0.2", "0.30000000000000004"),
                Arguments.of("1e16", "1e+16"),
                Arguments.of("1e15", "1000000000000000.0"),
                Arguments.of("0.00001", "1e-05"),
                Arguments.of("0.0001", "0.0001"),
                Arguments.of("123456789.123", "123456789.123"),
                Arguments.of("1.5e300 * 1e10", "inf"),
                Arguments.of("-(1.5e300 * 1e10)", "-inf"),
                Arguments.of("1e308 * 10 - 1e308 * 10", "nan"),
                Arguments.of("1 == 1.0", "True"),
                Arguments.of("1 == True", "True"),
                Arguments.of("[1, 2.0] == [1.0, 2]", "True"),
                Arguments.of("[1, 2] == [1, 2, 3]", "False"),
                Arguments.of("'a' < 'b'", "True"),
                Arguments.of("'B' < 'a'", "True"),
                Arguments.of("'abc' < 'abd'", "True"),
                Arguments.of("'ab' < 'abc'", "True"),
                Arguments.of("1 < 2.5", "True"),
                Arguments.of("2 <= 2.0", "True"),
                Arguments.of("'a' < 1", "TypeError: '<' not supported between instances of 'str' and 'int'"),
                Arguments.of("[1] < [2]", "True"),
                Arguments.of("None == None", "True"),
                Arguments.of("None == 0", "False"),
                Arguments.of("1 is 1", "True"),
                Arguments.of("'a' is 'a'", "True"),
                Arguments.of("None is None", "True"),
                Arguments.of("[1] is [1]", "False"),
                Arguments.of("not []", "True"),
                Arguments.of("not [0]", "False"),
                Arguments.of("not ''", "True"),
                Arguments.of("not 'x'", "False"),
                Arguments.of("not 0.0", "True"),
                Arguments.of("not None", "True"),
                Arguments.of("1 and 2", "2"),
                Arguments.of("0 and 2", "0"),
                Arguments.of("0 or 'x'", "'x'"),
                Arguments.of("'' or ''", "''"),
                Arguments.of("[] or [1]", "[1]"),
                Arguments.of("1 in [1, 2]", "True"),
                Arguments.of("3 not in [1, 2]", "True"),
                Arguments.of("'b' in 'abc'", "True"),
                Arguments.of("'' in 'abc'", "True"),
                Arguments.of("1 in 'abc'", "TypeError: 'in <string>' requires string as left operand, not int"),
                Arguments.of("1 in 5", "TypeError: argument of type 'int' is not iterable"),
                Arguments.of("2 in range(0, 10, 2)", "True"),
                Arguments.of("3 in range(0, 10, 2)", "False"),
                Arguments.of("2.0 in range(5)", "True"),
                Arguments.of("2.5 in range(5)", "False"),
                Arguments.of("'ab' + 'cd'", "'abcd'"),
                Arguments.of("'ab' * 3", "'ababab'"),
                Arguments.of("3 * 'ab'", "'ababab'"),
                Arguments.of("'ab' * -1", "''"),
                Arguments.of("[1] * 3", "[1, 1, 1]"),
                Arguments.of("[1, 2] + [3]", "[1, 2, 3]"),
                Arguments.of("'a' + 1", "TypeError: can only concatenate str (not \"int\") to str"),
                Arguments.of("1 + 'a'", "TypeError: unsupported operand type(s) for +: 'int' and 'str'"),
                Arguments.of("[1] + 'a'", "TypeError: can only concatenate list (not \"str\") to list"),
                Arguments.of("None + 1", "TypeError: unsupported operand type(s) for +: 'NoneType' and 'int'"),
                Arguments.of("-'a'", "TypeError: bad operand type for unary -: 'str'"),
                Arguments.of("[1, 2, 3][0]", "1"),
                Arguments.of("[1, 2, 3][-1]", "3"),
                Arguments.of("[1, 2, 3][3]", "IndexError: list index out of range"),
                Arguments.of("[1, 2, 3][-4]", "IndexError: list index out of range"),
                Arguments.of("'abc'[1]", "'b'"),
                Arguments.of("'abc'[5]", "IndexError: string index out of range"),
                Arguments.of("'abc'['a']", "TypeError: string indices must be integers, not 'str'"),
                Arguments.of("[1, 2]['a']", "TypeError: list indices must be integers or slices, not str"),
                Arguments.of("[1, 2][1.0]", "TypeError: list indices must be integers or slices, not float"),
                Arguments.of("5[0]", "TypeError: 'int' object is not subscriptable"),
                Arguments.of("[1, 2, 3, 4, 5][1:3]", "[2, 3]"),
                Arguments.of("[1, 2, 3, 4, 5][::-1]", "[5, 4, 3, 2, 1]"),
                Arguments.of("[1, 2, 3, 4, 5][::2]", "[1, 3, 5]"),
                Arguments.of("[1, 2, 3, 4, 5][-2:]", "[4, 5]"),
                Arguments.of("[1, 2, 3, 4, 5][:-2]", "[1, 2, 3]"),
                Arguments.of("[1, 2, 3, 4, 5][4:1:-1]", "[5, 4, 3]"),
                Arguments.of("[1, 2, 3, 4, 5][10:]", "[]"),
                Arguments.of("[1, 2, 3, 4, 5][-10:2]", "[1, 2]"),
                Arguments.of("[1, 2, 3, 4, 5][::0]", "ValueError: slice step cannot be zero"),
                Arguments.of("'hello'[1:4]", "'ell'"),
                Arguments.of("'hello'[::-1]", "'olleh'"),
                Arguments.of("'hello'[::-2]", "'olh'"),
                Arguments.of("range(10)[2:8:3]", "range(2, 8, 3)"),
                Arguments.of("range(10)[::-1]", "range(9, -1, -1)"),
                Arguments.of("range(10)[3]", "3"),
                Arguments.of("range(10)[-1]", "9"),
                Arguments.of("range(10)[10]", "IndexError: range object index out of range"),
                Arguments.of("range(1, 10, 3)", "range(1, 10, 3)"),
                Arguments.of("range(5)", "range(0, 5)"),
                Arguments.of("range(0)", "range(0, 0)"),
                Arguments.of("range(1, 5, 0)", "ValueError: range() arg 3 must not be zero"),
                Arguments.of("range(1.5)", "TypeError: 'float' object cannot be interpreted as an integer"),
                Arguments.of("len(range(0, 10, 3))", "4"),
                Arguments.of("len(range(10, 0, -3))", "4"),
                Arguments.of("len(range(5, 0))", "0"),
                Arguments.of("len('héllo')", "5"),
                Arguments.of("len([1, 2])", "2"),
                Arguments.of("int('42')", "42"),
                Arguments.of("int(' 7 ')", "7"),
                Arguments.of("int('1_000')", "1000"),
                Arguments.of("int('-5')", "-5"),
                Arguments.of("int('+5')", "5"),
                Arguments.of("int('1.5')", "ValueError: invalid literal for int() with base 10: '1.5'"),
                Arguments.of("int('')", "ValueError: invalid literal for int() with base 10: ''"),
                Arguments.of("int('1__0')", "ValueError: invalid literal for int() with base 10: '1__0'"),
                Arguments.of("int(3.9)", "3"),
                Arguments.of("int(-3.9)", "-3"),
                Arguments.of("int(True)", "1"),
                Arguments.of("int(float('nan'))", "ValueError: cannot convert float NaN to integer"),
                Arguments.of("int(float('inf'))", "OverflowError: cannot convert float infinity to integer"),
                Arguments.of("float('1.5')", "1.5"),
                Arguments.of("float(' 2 ')", "2.0"),
                Arguments.of("float('1e3')", "1000.0"),
                Arguments.of("float('inf')", "inf"),
                Arguments.of("float('-inf')", "-inf"),
                Arguments.of("float('nan')", "nan"),
                Arguments.of("float('-Infinity')", "-inf"),
                Arguments.of("float('abc')", "ValueError: could not convert string to float: 'abc'"),
                Arguments.of("float('1_0.5')", "10.5"),
                Arguments.of("float(3)", "3.0"),
                Arguments.of("str(1)", "'1'"),
                Arguments.of("str(1.0)", "'1.0'"),
                Arguments.of("str(None)", "'None'"),
                Arguments.of("str(True)", "'True'"),
                Arguments.of("str([1, 'a', None])", "\"[1, 'a', None]\""),
                Arguments.of("str('x')", "'x'"),
                Arguments.of("str(1e100)", "'1e+100'"),
                Arguments.of("str(['it\\'s', \"a\\\"b\", 'a\\'b\"c'])", "'[\"it\\'s\", \\'a\"b\\', \\'a\\\\\\'b\"c\\']'"),
                Arguments.of("str(['tab\\there', 'new\\nline', 'back\\\\slash'])", "\"['tab\\\\there', 'new\\\\nline', 'back\\\\\\\\slash']\""),
                Arguments.of("bool(0)", "False"),
                Arguments.of("bool([])", "False"),
                Arguments.of("bool('0')", "True"),
                Arguments.of("bool()", "False"),
                Arguments.of("int()", "0"),
                Arguments.of("float()", "0.0"),
                Arguments.of("str()", "''"),
                Arguments.of("abs(-3)", "3"),
                Arguments.of("abs(-3.5)", "3.5"),
                Arguments.of("abs(True)", "1"),
                Arguments.of("abs('a')", "TypeError: bad operand type for abs(): 'str'"),
                Arguments.of("min([3, 1, 2])", "1"),
                Arguments.of("min(3, 1, 2)", "1"),
                Arguments.of("max([1, 2.5, 2])", "2.5"),
                Arguments.of("max([])", "ValueError: max() iterable argument is empty"),
                Arguments.of("min('bca')", "'a'"),
                Arguments.of("max(['b', 'a', 'c'])", "'c'"),
                Arguments.of("max(1, 'a')", "TypeError: '>' not supported between instances of 'str' and 'int'"),
                Arguments.of("max([1, 1.0])", "1"),
                Arguments.of("max([1.0, 1])", "1.0"),
                Arguments.of("sum([1, 2, 3])", "6"),
                Arguments.of("sum([])", "0"),
                Arguments.of("sum([0.1] * 10)", "1.0"),
                Arguments.of("sum([1, 0.1, 0.2])", "1.3"),
                Arguments.of("sum([1e100, 1.0, -1e100])", "1.0"),
                Arguments.of("sum(['a'])", "TypeError: unsupported operand type(s) for +: 'int' and 'str'"),
                Arguments.of("sum([1, 'a'])", "TypeError: unsupported operand type(s) for +: 'int' and 'str'"),
                Arguments.of("round(2.5)", "2"),
                Arguments.of("round(3.5)", "4"),
                Arguments.of("round(-2.5)", "-2"),
                Arguments.of("round(2.675, 2)", "2.67"),
                Arguments.of("round(1250, -2)", "1200"),
                Arguments.of("round(1350, -2)", "1400"),
                Arguments.of("round(1.25, 1)", "1.2"),
                Arguments.of("round(1.35, 1)", "1.4"),
                Arguments.of("round(7, 1)", "7"),
                Arguments.of("round(0.5)", "0"),
                Arguments.of("round(1.5)", "2"),
                Arguments.of("round(-0.5)", "0"),
                Arguments.of("round(123.456, -1)", "120.0"),
                Arguments.of("round(float('inf'))", "OverflowError: cannot convert float infinity to integer"),
                Arguments.of("round(float('nan'))", "ValueError: cannot convert float NaN to integer"),
                Arguments.of("round(1.0, 'a')", "TypeError: 'str' object cannot be interpreted as an integer"),
                Arguments.of("any([0, 0, 1])", "True"),
                Arguments.of("any([])", "False"),
                Arguments.of("all([])", "True"),
                Arguments.of("all([1, 0])", "False"),
                Arguments.of("any('')", "False"),
                Arguments.of("[3, 1, 2].sort()", "None"),
                Arguments.of("[1, 2, 3].pop()", "3"),
                Arguments.of("[1, 2, 3].pop(0)", "1"),
                Arguments.of("[1, 2, 3].pop(-1)", "3"),
                Arguments.of("[1, 2, 3].pop(5)", "IndexError: pop index out of range"),
                Arguments.of("[].pop()", "IndexError: pop from empty list"),
                Arguments.of("[1, 2, 3].index(2)", "1"),
                Arguments.of("[1, 2, 3].index(5)", "ValueError: 5 is not in list"),
                Arguments.of("['a'].index('b')", "ValueError: 'b' is not in list"),
                Arguments.of("[1, 2, 1].count(1)", "2"),
                Arguments.of("[1, 2].remove(3)", "ValueError: list.remove(x): x not in list"),
                Arguments.of("[1, 2].copy()", "[1, 2]"),
                Arguments.of("[1, 2].nope()", "AttributeError: 'list' object has no attribute 'nope'"),
                Arguments.of("'Hello'.lower()", "'hello'"),
                Arguments.of("'Hello'.upper()", "'HELLO'"),
                Arguments.of("'  hi  '.strip()", "'hi'"),
                Arguments.of("'a,b,,c'.split(',')", "['a', 'b', '', 'c']"),
                Arguments.of("' a  b  c '.split()", "['a', 'b', 'c']"),
                Arguments.of("''.split()", "[]"),
                Arguments.of("''.split(',')", "['']"),
                Arguments.of("'abc'.split('')", "ValueError: empty separator"),
                Arguments.of("'-'.join(['a', 'b'])", "'a-b'"),
                Arguments.of("'-'.join([])", "''"),
                Arguments.of("'-'.join(['a', 1])", "TypeError: sequence item 1: expected str instance, int found"),
                Arguments.of("'aaa'.replace('a', 'b')", "'bbb'"),
                Arguments.of("'abc'.replace('', '-')", "'-a-b-c-'"),
                Arguments.of("'hello'.find('l')", "2"),
                Arguments.of("'hello'.find('z')", "-1"),
                Arguments.of("'hello'.find('')", "0"),
                Arguments.of("'abc'.count('')", "4"),
                Arguments.of("'aaaa'.count('aa')", "2"),
                Arguments.of("'hello'.startswith('he')", "True"),
                Arguments.of("'hello'.endswith('lo')", "True"),
                Arguments.of("'hello'.startswith(1)", "TypeError: startswith first arg must be str or a tuple of str, not int"),
                Arguments.of("'é'.upper()", "'É'"),
                Arguments.of("'ǅ'.lower()", "'ǆ'"),
                Arguments.of("'ß'.upper()", "'SS'"),
                Arguments.of("'İ'.lower()", "'i̇'"),
                Arguments.of("1 < 'a'", "TypeError: '<' not supported between instances of 'int' and 'str'"),
                Arguments.of("[1, 2] < [1, 3]", "True"),
                Arguments.of("None < 1", "TypeError: '<' not supported between instances of 'NoneType' and 'int'"),
                Arguments.of("(1).nope", "AttributeError: 'int' object has no attribute 'nope'"),
                Arguments.of("'abc'.nope", "AttributeError: 'str' object has no attribute 'nope'"),
                Arguments.of("'a'.split(None)", "['a']"),
                Arguments.of("[1, 2, 3][1:2:0]", "ValueError: slice step cannot be zero"),
                Arguments.of("[1, 2][None:]", "[1, 2]"),
                Arguments.of("'x' * 0", "''"),
                Arguments.of("0.1 * 3", "0.30000000000000004"),
                Arguments.of("1 / 3", "0.3333333333333333"),
                Arguments.of("2 / 3", "0.6666666666666666"),
                Arguments.of("10 / 3", "3.3333333333333335"),
                Arguments.of("1e-7", "1e-07"),
                Arguments.of("1e22", "1e+22"),
                Arguments.of("1e21", "1e+21"),
                Arguments.of("12345678901234567.0", "1.2345678901234568e+16"),
                Arguments.of("0.1 + 0.7", "0.7999999999999999"),
                Arguments.of("-1e-5", "-1e-05"),
                Arguments.of("5e-324", "5e-324"),
                Arguments.of("1.7976931348623157e308", "1.7976931348623157e+308"),
                Arguments.of("3.0 * 1.1", "3.3000000000000003"),
                Arguments.of("100.0", "100.0"),
                Arguments.of("1e16 + 1", "1e+16"),
                Arguments.of("9007199254740993.0", "9007199254740992.0"),
                Arguments.of("float('1e400')", "inf"),
                Arguments.of("float('-0')", "-0.0"),
                Arguments.of("-0.0 == 0.0", "True"),
                Arguments.of("float('nan') == float('nan')", "False"),
                Arguments.of("float('nan') != float('nan')", "True"),
                Arguments.of("float('nan') < 1", "False"),
                Arguments.of("[float('nan')] == [float('nan')]", "False"),
                Arguments.of("'é' < 'f'", "False"),
                Arguments.of("'z' < 'é'", "True"),
                Arguments.of("min(['b', 'A'])", "'A'"),
                Arguments.of("'a'.join('xyz')", "'xayaz'"),
                Arguments.of("'a b'.split(' ')", "['a', 'b']"),
                Arguments.of("[1, [2, 3]]", "[1, [2, 3]]"),
                Arguments.of("[[]] * 2", "[[], []]"),
                Arguments.of("[1, 2, 3][True]", "2"),
                Arguments.of("[1, 2, 3][False:True]", "[1]"),
                Arguments.of("-9223372036854775807", "-9223372036854775807"),
                Arguments.of("9223372036854775807 + 0", "9223372036854775807"),
                Arguments.of("'abc'[-1]", "'c'"),
                Arguments.of("'abc'[1:]", "'bc'"),
                Arguments.of("[1, 2, 3, 4, 5, 6][-1:-5:-2]", "[6, 4]"),
                Arguments.of("[1, 2, 3, 4, 5, 6][5:0:-2]", "[6, 4, 2]"),
                Arguments.of("round(0.125, 2)", "0.12"),
                Arguments.of("round(0.375, 2)", "0.38"),
                Arguments.of("round(-0.0)", "0"),
                Arguments.of("round(2.5, 0)", "2.0"),
                Arguments.of("str(1e20)", "'1e+20'"),
                Arguments.of("str(1.0e-5)", "'1e-05'"),
                Arguments.of("str(123e-20)", "'1.23e-18'"),
                Arguments.of("'' < 'a'", "True"),
                Arguments.of("'a' * True", "'a'"),
                Arguments.of("'abc'.find('c')", "2"),
                Arguments.of("'aXbXc'.split('X')", "['a', 'b', 'c']"),
                Arguments.of("'  a\\tb\\n'.split()", "['a', 'b']"),
                Arguments.of("' x '.strip()", "'x'"),
                Arguments.of("'\\t x\\n'.strip()", "'x'"),
                Arguments.of("'a' in ['a', 'b']", "True"),
                Arguments.of("['a'] in [['a']]", "True"),
                Arguments.of("None in [1, None]", "True"),
                Arguments.of("1.0 in [1]", "True"),
                Arguments.of("True in [1]", "True"),
                Arguments.of("'1' in [1]", "False"),
                Arguments.of("range(3).index(5)", "ValueError: 5 is not in range"),
                Arguments.of("range(3).index(1)", "1"),
                Arguments.of("range(3).count(1)", "1"),
                Arguments.of("range(3).count(7)", "0"),
                Arguments.of("range(0, 10, 3).stop", "10"),
                Arguments.of("range(0, 10, 3).step", "3"),
                Arguments.of("range(5)[1:].start", "1"),
                Arguments.of("list(5)", "TypeError: 'int' object is not iterable"),
                Arguments.of("list(range(3))", "[0, 1, 2]"),
                Arguments.of("list('ab')", "['a', 'b']"),
                Arguments.of("list()", "[]"),
                Arguments.of("list([1, 2])", "[1, 2]"),
                Arguments.of("range(1, 4).index(True)", "0"),
                Arguments.of("[1] + range(2)", "TypeError: can only concatenate list (not \"range\") to list"),
                Arguments.of("range(3) == range(0, 3, 1)", "True"),
                Arguments.of("range(0) == range(5, 2)", "True"),
                Arguments.of("range(3) == [0, 1, 2]", "False"),
                Arguments.of("[1, 2] < [1, 2, 3]", "True"),
                Arguments.of("[1, 2] <= [1, 2]", "True"),
                Arguments.of("[1, 'a'] < [1, 2]", "TypeError: '<' not supported between instances of 'str' and 'int'"),
                Arguments.of("[2] > [1, 'x']", "True"),
                Arguments.of("[] < []", "False"),
                Arguments.of("[[1]] < [[2]]", "True"),
                Arguments.of("[1, None] < [2, None]", "True"),
                Arguments.of("[None] < [None]", "False"),
                Arguments.of("'a'.endswith(1)", "TypeError: endswith first arg must be str or a tuple of str, not int"),
                Arguments.of("'a'.replace(1, 'b')", "TypeError: replace() argument 1 must be str, not int"),
                Arguments.of("'a'.replace('a', 1)", "TypeError: replace() argument 2 must be str, not int"),
                Arguments.of("'a'.find(1)", "TypeError: must be str, not int"),
                Arguments.of("'a'.count(1)", "TypeError: must be str, not int"),
                Arguments.of("'a'.split(1)", "TypeError: must be str or None, not int"),
                Arguments.of("'a'.join(1)", "TypeError: can only join an iterable"),
                Arguments.of("'a'.join([[]])", "TypeError: sequence item 0: expected str instance, list found"),
                Arguments.of("[1, 2].extend(3)", "TypeError: 'int' object is not iterable"),
                Arguments.of("[1].insert('a', 1)", "TypeError: 'str' object cannot be interpreted as an integer"),
                Arguments.of("[1].pop('a')", "TypeError: 'str' object cannot be interpreted as an integer"),
                Arguments.of("[1] * 'a'", "TypeError: can't multiply sequence by non-int of type 'str'"),
                Arguments.of("'a' * 'b'", "TypeError: can't multiply sequence by non-int of type 'str'"),
                Arguments.of("'a' * 1.5", "TypeError: can't multiply sequence by non-int of type 'float'"),
                Arguments.of("3.5 * [1]", "TypeError: can't multiply sequence by non-int of type 'float'"),
                Arguments.of("len(5)", "TypeError: object of type 'int' has no len()"),
                Arguments.of("len(range(3))", "3"),
                Arguments.of("min(5)", "TypeError: 'int' object is not iterable"),
                Arguments.of("sum(5)", "TypeError: 'int' object is not iterable"),
                Arguments.of("sum(range(5))", "10"),
                Arguments.of("max(range(5))", "4"),
                Arguments.of("any(5)", "TypeError: 'int' object is not iterable"),
                Arguments.of("1 in None", "TypeError: argument of type 'NoneType' is not iterable"),
                Arguments.of("max([1, 'a'])", "TypeError: '>' not supported between instances of 'str' and 'int'"),
                Arguments.of("min(['a', 1])", "TypeError: '<' not supported between instances of 'int' and 'str'"),
                Arguments.of("[1, 'a'].sort()", "TypeError: '<' not supported between instances of 'str' and 'int'"),
                Arguments.of("[3, 1, 2].sort(reverse=True)", "None"),
                Arguments.of("-4.0 % 2", "0.0"),
                Arguments.of("4.0 % -2", "-0.0"),
                Arguments.of("0.0 % -1", "-0.0"),
                Arguments.of("str(range(2, 5))", "'range(2, 5)'"),
                Arguments.of("str(range(0, 5, 2))", "'range(0, 5, 2)'"),
                Arguments.of("bool(range(0))", "False"),
                Arguments.of("bool(range(1))", "True"),
                Arguments.of("'abc'[1:2:'a']", "TypeError: slice indices must be integers or None or have an __index__ method"),
                Arguments.of("[1, 2, 3]['a':]", "TypeError: slice indices must be integers or None or have an __index__ method"),
                Arguments.of("len(None)", "TypeError: object of type 'NoneType' has no len()"),
                Arguments.of("sum(None)", "TypeError: 'NoneType' object is not iterable"),
                Arguments.of("None[0]", "TypeError: 'NoneType' object is not subscriptable"),
                Arguments.of("None.x", "AttributeError: 'NoneType' object has no attribute 'x'"),
                Arguments.of("sum(range(10, 0, -3))", "22"),
                Arguments.of("sum(range(-5, 6))", "0"),
                Arguments.of("min(range(5, 0, -2))", "1"),
                Arguments.of("max(range(5, 0, -2))", "5"),
                Arguments.of("max(range(0, 10, 3))", "9"),
                Arguments.of("any(range(0, 1))", "False"),
                Arguments.of("any(range(0, 2))", "True"),
                Arguments.of("all(range(1, 5))", "True"),
                Arguments.of("all(range(-3, 3, 2))", "True"),
                Arguments.of("all(range(-4, 4, 2))", "False"),
                Arguments.of("all(range(0))", "True"),
                Arguments.of("min(range(0))", "ValueError: min() iterable argument is empty"),
                Arguments.of("sum(range(0))", "0"),
                // Python orders by code point; Java's UTF-16 order would put the emoji first.
                Arguments.of("'\uD83D\uDE00' > '\uFFFD'", "True"),
                Arguments.of("min(['\uD83D\uDE00', '\uFFFD'])", "'\uFFFD'"),
                Arguments.of("round(number=2.5)", "2"),
                Arguments.of("round(2.675, ndigits=2)", "2.67"),
                Arguments.of("round(2.5, ndigits=None)", "2"),
                Arguments.of("round(ndigits=1, number=2.25)", "2.2"),
                Arguments.of("str(object=5)", "'5'"),
                Arguments.of("sum([1, 2], 10)", "13"),
                Arguments.of("sum([1, 2], start=10)", "13"),
                Arguments.of("sum([0.5], start=1)", "1.5"),
                Arguments.of("sum([1, 2], 0.5)", "3.5"),
                Arguments.of("sum(range(5), 10)", "20"),
                Arguments.of("sum(range(5), start=-10)", "0"),
                Arguments.of("sum(['a'], '')", "TypeError: sum() can't sum strings [use ''.join(seq) instead]"),
                Arguments.of("min([], default=0)", "0"),
                Arguments.of("max([3, 1], default=0)", "3"),
                Arguments.of("max([], default=None)", "None"),
                Arguments.of("min(range(0), default=-1)", "-1"),
                Arguments.of("min('', default='z')", "'z'"),
                Arguments.of("1", "1"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void cpython(String expression, String expected) {
        assertEquals(expected, run(expression));
    }

    /** Where integers being 64-bit makes the result differ from CPython's, which has no limit. */
    static Stream<Arguments> sixtyFourBit() {
        var tooLarge = "OverflowError: " + Ops.TOO_LARGE;
        return Stream.of(
                Arguments.of("9223372036854775807 + 1", tooLarge),
                Arguments.of("-9223372036854775807 - 2", tooLarge),
                Arguments.of("9223372036854775807 * 2", tooLarge),
                Arguments.of("abs(-9223372036854775807 - 1)", tooLarge),
                Arguments.of("-(-9223372036854775807 - 1)", tooLarge),
                Arguments.of("(-9223372036854775807 - 1) // -1", tooLarge),
                Arguments.of("int('99999999999999999999')", tooLarge),
                Arguments.of("int(1e19)", tooLarge),
                Arguments.of("round(1e20)", tooLarge),
                Arguments.of("sum([9223372036854775807, 1])", tooLarge),
                Arguments.of("int(-9.2e18)", "-9200000000000000000"),
                Arguments.of("sum(range(0, 9223372036854775807))", tooLarge),
                Arguments.of("max(range(0, 9223372036854775807, 2))", "9223372036854775806"),
                Arguments.of("9223372036854775806 in range(0, 9223372036854775807, 2)", "True"),
                Arguments.of("len(range(-9000000000000000000, 9000000000000000000, 3))", "6000000000000000000"),
                Arguments.of("range(-9000000000000000000, 9000000000000000000, 3)[-1]", "8999999999999999997"),
                Arguments.of("len(range(-9223372036854775807, 9223372036854775807))", tooLarge));
    }

    /** Lists and strings stop growing at a cap, so one step stays short; CPython has no such limit. */
    static Stream<Arguments> sequenceCap() {
        var tooMany = "MemoryError: lists and strings hold at most 100000 items";
        return Stream.of(
                Arguments.of("'ab' * 100000000", tooMany),
                Arguments.of("'a' * 100001", tooMany),
                Arguments.of("len('a' * 100000)", "100000"),
                Arguments.of("[0] * 100001", tooMany),
                Arguments.of("list(range(100001))", tooMany),
                Arguments.of("len(list(range(100000)))", "100000"),
                Arguments.of("'a' * 60000 + 'b' * 60000", tooMany),
                Arguments.of("[0] * 60000 + [1] * 60000", tooMany),
                Arguments.of("('a' * 50000).replace('a', 'bbb')", tooMany),
                Arguments.of("('a' * 50000).replace('', '-')", tooMany),
                Arguments.of("'--'.join(['a'] * 40000)", tooMany),
                Arguments.of("sum(range(1000000000))", "499999999500000000"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void sequenceCap(String expression, String expected) {
        assertEquals(expected, run(expression));
    }

    @Test
    void growingInPlaceStopsAtTheCap() {
        var tooMany = "MemoryError: lists and strings hold at most 100000 items";
        assertEquals(tooMany, exec("xs", "xs = [0] * 100000", "xs.append(1)"));
        assertEquals(tooMany, exec("xs", "xs = [0] * 100000", "xs.insert(0, 1)"));
        assertEquals(tooMany, exec("xs", "xs = [0] * 60000", "xs.extend(xs)"));
        assertEquals(tooMany, exec("xs", "xs = [0] * 60000", "xs += xs"));
        assertEquals(tooMany, exec("xs", "xs = [0] * 60000", "xs[0:0] = xs"));
        assertEquals(tooMany, exec("xs", "xs = [0]", "xs.extend(range(100000))"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void sixtyFourBit(String expression, String expected) {
        assertEquals(expected, run(expression));
    }

    @Test
    void listsAreSharedAndChangedInPlace() {
        assertEquals("[1, 2, 3, 4]", exec("ys", "xs = [1, 2, 3]", "ys = xs", "xs.append(4)"));
        assertEquals("[1, 2, 3]", exec("ys", "xs = [1, 2]", "ys = xs", "xs += [3]"));
        assertEquals("[1, 2, 1, 2]", exec("ys", "xs = [1, 2]", "ys = xs", "xs *= 2"));
        assertEquals("[1, 2]", exec("ys", "xs = [1, 2]", "ys = xs", "xs = xs + [3]"));
        assertEquals("'ab'", exec("t", "s = 'a'", "t = s", "s += 'b'", "t = s"));
        assertEquals("[1, 7, 3]", exec("xs", "xs = [1, 2, 3]", "xs[1] += 5"));
        assertEquals("[1, 7, 8, 9, 3]", exec("xs", "xs = [1, 2, 3]", "xs[1:2] = [7, 8, 9]"));
        assertEquals("[0, 2, 0, 4]", exec("xs", "xs = [1, 2, 3, 4]", "xs[::2] = [0, 0]"));
        assertEquals("ValueError: attempt to assign sequence of size 1 to extended slice of size 2",
                exec("xs", "xs = [1, 2, 3, 4]", "xs[::2] = [0]"));
        assertEquals("IndexError: list assignment index out of range", exec("xs", "xs = [1]", "xs[1] = 0"));
        assertEquals("TypeError: 'str' object does not support item assignment", exec("s", "s = 'ab'", "s[0] = 'c'"));
        assertEquals("[[1], [1]]", exec("xs", "xs = [[]] * 2", "xs[0].append(1)"));
    }

    @Test
    void sortIsStableAndFailsWithCPythonsMessage() {
        assertEquals("[1, 1.0, 2]", exec("xs", "xs = [2, 1, 1.0]", "xs.sort()"));
        assertEquals("[2, 1, 1.0]", exec("xs", "xs = [1, 2, 1.0]", "xs.sort(reverse=True)"));
        assertEquals("['a', 'b', 'c']", exec("xs", "xs = ['c', 'a', 'b']", "xs.sort()"));
        assertEquals("[[1], [1, 0], [2]]", exec("xs", "xs = [[2], [1, 0], [1]]", "xs.sort()"));
        // CPython leaves [3, 1, 'a'] as it was too; for longer lists it may leave them partly sorted.
        assertEquals("TypeError: '<' not supported between instances of 'str' and 'int'",
                exec("xs", "xs = [3, 1, 'a']", "xs.sort()"));
    }

    @Test
    void unboundLocalsFailAsInPython() {
        assertEquals("UnboundLocalError: cannot access local variable 'n' where it is not associated with a value",
                run("n + 1"));
    }

    @Test
    void recordsAreDataclasses() {
        assertEquals("Order(item='minecraft:iron_ingot', amount=1)", run("Order('minecraft:iron_ingot')"));
        assertEquals("Order(item='minecraft:iron_ingot', amount=5)", run("Order(amount=5, item='minecraft:iron_ingot')"));
        assertEquals("True", run("Order('a') == Order('a', 1)"));
        assertEquals("False", run("Order('a') == Order('a', 2)"));
        assertEquals("False", run("Order('a') is Order('a')"));
        // Classes of different files are different types, even with the same name and fields.
        assertEquals("False", run("Order('a') == iron_tools.Order('a')"));
        assertEquals("True", run("iron_tools.Order('a') == iron_tools.Order('a')"));
        assertEquals("[Order(item='a', amount=3)]", exec("[o]", "o = Order('a')", "p = o", "p.amount = 3"));
        assertEquals("Order(item='a', amount=4)", exec("o", "o = Order('a', 2)", "o.amount *= 2"));
        assertEquals("AttributeError: 'Order' object has no attribute 'count'", run("Order('a').count"));
    }

    @Test
    void libraryReadsGoToTheHostWithDefaultsFilledIn() {
        assertEquals("641", run("ae2.amount('minecraft:iron_ingot') + 1"));
        assertEquals("1500000.0", run("ae2.energy(network='main_base')"));
        assertEquals("640", run("ae2.storage()[0].amount"));
        assertEquals(List.of("ae2.amount['minecraft:iron_ingot', None]", "ae2.energy['main_base']",
                "ae2.storage[None]"), host.calls);
        assertEquals("FrozenInstanceError: cannot assign to field 'amount'",
                exec("st", "st = ae2.storage()[0]", "st.amount = 1"));
    }

    @Test
    void libraryArgumentsAreCheckedWhenTheyRun() {
        assertEquals("TypeError: argument 'resource' of ae2.amount() must be Resource, not None",
                exec("ae2.amount(r)", "r = None"));
        assertEquals("ValueError: reach of ae2f.select() must be from 1 to 4, not 5",
                exec("ae2f.select('north_gate', n)", "n = 5"));
        assertEquals(List.of(), host.calls, "a refused call never reaches the host");
    }

    @Test
    void actionsAndPlayerFunctionsMustBeLiftedOutByTheCompiler() {
        assertThrows(IllegalStateException.class, () -> evaluator.eval("main", parse("ae2.craft('a', 1)"), locals));
        assertThrows(IllegalStateException.class, () -> evaluator.eval("main", parse("f()"), locals));
        assertThrows(IllegalStateException.class,
                () -> evaluator.eval("main", parse("iron_tools.make('a')"), locals));
    }
}
