package space.controlnet.ae2federation.function;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import space.controlnet.ae2federation.function.Lexer.Kind;
import space.controlnet.ae2federation.function.Lexer.Token;

/**
 * Parses the Python subset into a {@link Program}. Anything outside the subset is an error that names the construct.
 * A failing statement or function is recorded and skipped, and parsing goes on with the next one, so one result holds
 * every error and everything that did parse. One error per line: later ones on the same line are follow-ups.
 *
 * <p>Comments: a whole-line comment belongs to the next statement, {@code elif}, {@code else} or {@code def} (whatever
 * its indentation); comments after the last function belong to the file.
 */
public final class ScriptParser {
    private static final Set<String> KEYWORDS = Set.of("False", "None", "True", "and", "as", "assert", "async",
            "await", "break", "class", "continue", "def", "del", "elif", "else", "except", "finally", "for", "from",
            "global", "if", "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try",
            "while", "with", "yield");
    private static final Set<String> UNSUPPORTED_KEYWORDS = Set.of("as", "assert", "async", "await", "del",
            "except", "finally", "from", "global", "import", "lambda", "nonlocal", "raise", "try", "with", "yield");
    private static final Set<String> UNSUPPORTED_OPERATORS = Set.of("**", "&", "|", "^", "<<", ">>", "@");

    private final List<Token> tokens;
    private final List<ScriptError> errors;
    private final Map<String, Position> positions = new HashMap<>();
    private final Ids ids = new Ids("n");
    private final List<String> pending = new ArrayList<>();
    private int pos;

    private ScriptParser(List<Token> tokens, List<ScriptError> errors) {
        this.tokens = tokens;
        this.errors = errors;
    }

    public static ParseResult parse(String source) {
        var errors = new ArrayList<ScriptError>();
        var parser = new ScriptParser(Lexer.lex(source, errors), errors);
        var program = parser.file();
        errors.sort(Comparator.comparingInt(ScriptError::line).thenComparingInt(ScriptError::column));
        var firstPerLine = new ArrayList<ScriptError>();
        for (var error : errors) {
            if (firstPerLine.isEmpty() || firstPerLine.get(firstPerLine.size() - 1).line() != error.line()) {
                firstPerLine.add(error);
            }
        }
        return new ParseResult(program, firstPerLine, parser.positions);
    }

    /** Thrown to abandon the current statement; carries no stack trace. */
    private static final class Failure extends RuntimeException {
        final ScriptError error;

        Failure(ScriptError error) {
            super(error.message(), null, false, false);
            this.error = error;
        }
    }

    // ---- file and functions ----

    private Program file() {
        var definitions = new ArrayList<Definition>();
        while (true) {
            takeComments();
            if (check(Kind.EOF)) {
                break;
            }
            int start = pos;
            try {
                definitions.add(definition());
            } catch (Failure failure) {
                recover(failure, start);
            }
        }
        return new Program(definitions, takePending());
    }

    private Definition definition() {
        var first = peek();
        if (first.is(Kind.OP, "@")) {
            throw fail(first, "decorators are not supported");
        }
        if (first.kind() == Kind.INDENT) {
            throw fail(first, "unexpected indentation");
        }
        if (isKeyword(first, "class")) {
            return classDef();
        }
        if (!isKeyword(first, "def")) {
            if (first.kind() == Kind.NAME && UNSUPPORTED_KEYWORDS.contains(first.text())) {
                throw unsupportedKeyword(first);
            }
            throw fail(first, "only function and class definitions may appear at the top level of a file");
        }
        var above = takePending();
        advance();
        var nameToken = name("a function name");
        var params = new ArrayList<Param>();
        boolean defaults = false;
        expect("(");
        while (!checkOp(")")) {
            var token = peek();
            if (token.is(Kind.OP, "*") || token.is(Kind.OP, "**")) {
                throw fail(token, "variable arguments (*args, **kwargs) are not supported");
            }
            if (token.is(Kind.OP, "/")) {
                throw fail(token, "'/' in parameters is not supported");
            }
            var paramName = name("a parameter name");
            if (!checkOp(":")) {
                throw fail(paramName, "parameter '" + paramName.text() + "' needs a type hint, as in "
                        + paramName.text() + ": int");
            }
            advance();
            var type = type();
            Expr defaultValue = null;
            if (matchOp("=")) {
                defaultValue = expr();
                defaults = true;
            } else if (defaults) {
                throw fail(paramName, "parameter without a default follows parameter with a default");
            }
            params.add(new Param(id(paramName), paramName.text(), type, defaultValue));
            if (!checkOp(")")) {
                expect(",");
            }
        }
        advance();
        if (!checkOp("->")) {
            throw fail(nameToken, "function '" + nameToken.text()
                    + "' needs a return type hint (-> None if it returns nothing)");
        }
        advance();
        var result = type();
        expect(":");
        var end = endComment();
        openBlock("def");
        var docstring = docstring();
        var body = statements();
        var id = ids.next();
        positions.put(id, new Position(first.line(), first.column()));
        return new FunctionDef(id, new Notes(above, end), nameToken.text(), params, result, docstring, body);
    }

    /** At the start of an opened block: its docstring, or {@code null}. Comments above it move below it. */
    private String docstring() {
        takeComments();
        String docstring = null;
        if (check(Kind.STRING) && peekAt(1).kind() == Kind.END_COMMENT) {
            // Recorded without abandoning the definition: the block is already open.
            errors.add(fail(peekAt(1), "a comment after a docstring is not supported; put it on its own line").error);
            docstring = cleanDocstring((String) advance().value());
            advance();
        }
        if (check(Kind.STRING) && peekAt(1).kind() == Kind.NEWLINE) {
            docstring = cleanDocstring((String) advance().value());
        }
        if (docstring != null) {
            advance();
        }
        return docstring;
    }

    /** A record: typed fields only, as a dataclass without the decorator. */
    private ClassDef classDef() {
        var first = advance();
        var above = takePending();
        var nameToken = name("a class name");
        if (matchOp("(")) {
            if (!checkOp(")")) {
                throw fail(peek(), "inheritance is not supported; a class holds only fields");
            }
            advance();
        }
        expect(":");
        var end = endComment();
        openBlock("class");
        var docstring = docstring();
        var fields = new ArrayList<Field>();
        boolean defaults = false;
        while (true) {
            takeComments();
            if (check(Kind.DEDENT)) {
                advance();
                break;
            }
            if (check(Kind.EOF)) {
                break;
            }
            int start = pos;
            try {
                var field = field();
                if (field == null) {
                    continue;
                }
                if (field.defaultValue() != null) {
                    defaults = true;
                } else if (defaults) {
                    throw fail(tokens.get(start), "non-default argument '" + field.name()
                            + "' follows default argument");
                }
                fields.add(field);
            } catch (Failure failure) {
                recover(failure, start);
            }
        }
        var id = ids.next();
        positions.put(id, new Position(first.line(), first.column()));
        return new ClassDef(id, new Notes(above, end), nameToken.text(), docstring, fields);
    }

    /** One line of a class body: a field, or {@code null} for {@code pass}, whose comments go to what follows. */
    private Field field() {
        var token = peek();
        if (token.is(Kind.OP, "@")) {
            throw fail(token, "decorators are not supported");
        }
        if (token.kind() == Kind.INDENT) {
            throw fail(token, "unexpected indentation");
        }
        if (isKeyword(token, "def")) {
            throw fail(token, "methods are not supported; a class holds only fields");
        }
        if (isKeyword(token, "pass")) {
            advance();
            endLine();
            return null;
        }
        if (token.kind() == Kind.NAME && !KEYWORDS.contains(token.text()) && peekAt(1).is(Kind.OP, "=")) {
            throw fail(token, "field '" + token.text() + "' needs a type hint, as in " + token.text() + ": int = ...");
        }
        if (token.kind() != Kind.NAME || KEYWORDS.contains(token.text()) || !peekAt(1).is(Kind.OP, ":")) {
            throw fail(token, "a class holds only fields, written as name: type");
        }
        var above = takePending();
        advance();
        advance();
        var type = type();
        Expr defaultValue = matchOp("=") ? expr() : null;
        return new Field(id(token), new Notes(above, endLine()), token.text(), type, defaultValue);
    }

    /** Python's {@code inspect.cleandoc}: drop the common indentation of the continuation lines and blank ends. */
    private static String cleanDocstring(String raw) {
        var lines = new ArrayList<>(List.of(raw.split("\n", -1)));
        int indent = Integer.MAX_VALUE;
        for (int i = 1; i < lines.size(); i++) {
            var line = lines.get(i);
            if (!line.isBlank()) {
                indent = Math.min(indent, line.length() - line.stripLeading().length());
            }
        }
        lines.set(0, lines.get(0).strip());
        for (int i = 1; i < lines.size(); i++) {
            var line = lines.get(i);
            lines.set(i, line.isBlank() ? "" : line.substring(indent).stripTrailing());
        }
        while (!lines.isEmpty() && lines.get(0).isEmpty()) {
            lines.remove(0);
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return String.join("\n", lines);
    }

    private TypeRef type() {
        var token = peek();
        if (token.kind() == Kind.STRING) {
            throw fail(token, "string annotations are not supported; write the type itself");
        }
        if (token.kind() != Kind.NAME || KEYWORDS.contains(token.text()) && !token.text().equals("None")) {
            throw fail(token, "expected a type");
        }
        advance();
        var name = token.text();
        if (matchOp(".")) {
            // A class of another file.
            name += "." + name("a class name after '.'").text();
        }
        var args = new ArrayList<TypeRef>();
        if (checkOp("[")) {
            advance();
            do {
                args.add(type());
            } while (matchOp(","));
            expect("]");
        }
        if (checkOp("|")) {
            throw fail(peek(), "union types (A | B) are not supported");
        }
        return new TypeRef(name, args);
    }

    // ---- statements ----

    /** After a header's {@code :} and end comment: the line break and the indentation of a block. */
    private void openBlock(String keyword) {
        if (!check(Kind.NEWLINE)) {
            throw fail(peek(), "put the body of '" + keyword + "' on its own indented lines");
        }
        advance();
        if (!check(Kind.INDENT)) {
            throw fail(peek(), "expected an indented block after '" + keyword + "'");
        }
        advance();
    }

    private List<Stmt> block(String keyword) {
        openBlock(keyword);
        return statements();
    }

    /** Statements up to the end of the current block, which is consumed. */
    private List<Stmt> statements() {
        var body = new ArrayList<Stmt>();
        while (true) {
            takeComments();
            if (check(Kind.DEDENT)) {
                advance();
                return body;
            }
            if (check(Kind.EOF)) {
                return body;
            }
            int start = pos;
            try {
                body.add(statement());
            } catch (Failure failure) {
                recover(failure, start);
            }
        }
    }

    private Stmt statement() {
        var token = peek();
        if (token.is(Kind.OP, "@")) {
            throw fail(token, "decorators are not supported");
        }
        if (token.kind() == Kind.INDENT) {
            throw fail(token, "unexpected indentation");
        }
        if (token.kind() == Kind.NAME) {
            switch (token.text()) {
                case "if":
                    return ifStatement();
                case "while":
                    return whileStatement();
                case "for":
                    return forStatement();
                case "return": {
                    var above = takePending();
                    advance();
                    Expr value = check(Kind.NEWLINE) || check(Kind.END_COMMENT) ? null : expr();
                    return new Stmt.Return(id(token), new Notes(above, endLine()), value);
                }
                case "break": {
                    var above = takePending();
                    advance();
                    return new Stmt.Break(id(token), new Notes(above, endLine()));
                }
                case "continue": {
                    var above = takePending();
                    advance();
                    return new Stmt.Continue(id(token), new Notes(above, endLine()));
                }
                case "pass": {
                    var above = takePending();
                    advance();
                    return new Stmt.Pass(id(token), new Notes(above, endLine()));
                }
                case "def":
                    throw fail(token, "nested functions are not supported; define it at the top level");
                case "class":
                    throw fail(token, "nested classes are not supported; define it at the top level");
                case "elif", "else":
                    throw fail(token, "'" + token.text() + "' without a matching 'if'");
                default:
                    if (UNSUPPORTED_KEYWORDS.contains(token.text())) {
                        throw unsupportedKeyword(token);
                    }
            }
        }
        return simpleStatement();
    }

    private Stmt simpleStatement() {
        var first = peek();
        var above = takePending();
        var target = expr();
        if (checkOp(",")) {
            throw fail(peek(), "tuples are not supported");
        }
        if (checkOp("=")) {
            var name = assignable(target, first, "assigned to");
            advance();
            var value = expr();
            if (checkOp("=")) {
                throw fail(peek(), "chained assignment (a = b = 1) is not supported");
            }
            return new Stmt.Assign(id(first), new Notes(above, endLine()), name, null, value);
        }
        if (checkOp(":")) {
            var name = assignable(target, first, "annotated");
            advance();
            var type = type();
            if (!checkOp("=")) {
                throw fail(peek(), "a variable annotated with a type needs a value, as in "
                        + ScriptPrinter.expr(name) + ": " + type + " = ...");
            }
            advance();
            var value = expr();
            return new Stmt.Assign(id(first), new Notes(above, endLine()), name, type, value);
        }
        var token = peek();
        if (token.kind() == Kind.OP && token.text().length() >= 2 && token.text().endsWith("=")
                && !token.text().equals("==") && !token.text().equals("!=") && !token.text().equals("<=")
                && !token.text().equals(">=")) {
            var op = BinaryOp.ofSymbol(token.text().substring(0, token.text().length() - 1))
                    .filter(BinaryOp::augmentable)
                    .orElseThrow(() -> fail(token, "'" + token.text() + "' is not supported"));
            var name = assignable(target, first, "assigned to");
            advance();
            var value = expr();
            return new Stmt.AugAssign(id(first), new Notes(above, endLine()), name, op, value);
        }
        if (!(target instanceof Expr.Call call)) {
            throw fail(first, "only a call can stand on its own line; this value is unused");
        }
        return new Stmt.CallStmt(id(first), new Notes(above, endLine()), call);
    }

    private Expr assignable(Expr target, Token first, String what) {
        if (!(target instanceof Expr.Name || target instanceof Expr.Attribute || target instanceof Expr.Index
                || target instanceof Expr.Slice)) {
            throw fail(first, "only a variable, a field or an item can be " + what);
        }
        return target;
    }

    private Stmt ifStatement() {
        var token = advance();
        var above = takePending();
        var condition = expr();
        expect(":");
        var end = endComment();
        var body = block("if");
        var elifs = new ArrayList<Stmt.Elif>();
        Stmt.Else orElse = null;
        while (true) {
            takeComments();
            if (isKeyword(peek(), "elif")) {
                var elifAbove = takePending();
                advance();
                var elifCondition = expr();
                expect(":");
                var elifEnd = endComment();
                elifs.add(new Stmt.Elif(new Notes(elifAbove, elifEnd), elifCondition, block("elif")));
            } else if (isKeyword(peek(), "else")) {
                var elseAbove = takePending();
                advance();
                expect(":");
                var elseEnd = endComment();
                orElse = new Stmt.Else(new Notes(elseAbove, elseEnd), block("else"));
                break;
            } else {
                break;
            }
        }
        return new Stmt.If(id(token), new Notes(above, end), condition, body, elifs, orElse);
    }

    private Stmt whileStatement() {
        var token = advance();
        var above = takePending();
        var condition = expr();
        expect(":");
        var end = endComment();
        var body = block("while");
        rejectLoopElse();
        return new Stmt.While(id(token), new Notes(above, end), condition, body);
    }

    private Stmt forStatement() {
        var token = advance();
        var above = takePending();
        var variable = name("a loop variable");
        if (checkOp(",")) {
            throw fail(peek(), "tuples are not supported; loop over one value");
        }
        if (!isKeyword(peek(), "in")) {
            throw fail(peek(), "expected 'in'");
        }
        advance();
        var iterable = expr();
        expect(":");
        var end = endComment();
        var body = block("for");
        rejectLoopElse();
        return new Stmt.For(id(token), new Notes(above, end), variable.text(), iterable, body);
    }

    private void rejectLoopElse() {
        int mark = pos;
        while (check(Kind.COMMENT)) {
            advance();
        }
        if (isKeyword(peek(), "else")) {
            throw fail(peek(), "'else' after a loop is not supported");
        }
        pos = mark;
    }

    // ---- expressions ----

    private Expr expr() {
        var e = or();
        if (isKeyword(peek(), "if")) {
            throw fail(peek(), "conditional expressions (a if c else b) are not supported");
        }
        if (checkOp(":=")) {
            throw fail(peek(), "':=' is not supported");
        }
        return e;
    }

    private Expr or() {
        var start = peek();
        var left = and();
        while (isKeyword(peek(), "or")) {
            advance();
            left = new Expr.Binary(id(start), BinaryOp.OR, left, and());
        }
        return left;
    }

    private Expr and() {
        var start = peek();
        var left = not();
        while (isKeyword(peek(), "and")) {
            advance();
            left = new Expr.Binary(id(start), BinaryOp.AND, left, not());
        }
        return left;
    }

    private Expr not() {
        var token = peek();
        if (isKeyword(token, "not")) {
            advance();
            return new Expr.Unary(id(token), UnaryOp.NOT, not());
        }
        return comparison();
    }

    private Expr comparison() {
        var start = peek();
        var left = sum();
        var op = comparisonOperator();
        if (op == null) {
            return left;
        }
        advance();
        if (op == BinaryOp.NOT_IN || op == BinaryOp.IS_NOT) {
            advance();
        }
        var result = new Expr.Binary(id(start), op, left, sum());
        if (comparisonOperator() != null) {
            throw fail(peek(), "chained comparisons (a < b < c) are not supported; join them with 'and'");
        }
        return result;
    }

    /** The comparison under the cursor, without consuming it; rejects the ones outside the subset. */
    private BinaryOp comparisonOperator() {
        var token = peek();
        if (isKeyword(token, "in")) {
            return BinaryOp.IN;
        }
        if (isKeyword(token, "not") && isKeyword(peekAt(1), "in")) {
            return BinaryOp.NOT_IN;
        }
        if (isKeyword(token, "is")) {
            return isKeyword(peekAt(1), "not") ? BinaryOp.IS_NOT : BinaryOp.IS;
        }
        if (token.kind() != Kind.OP) {
            return null;
        }
        return BinaryOp.ofSymbol(token.text()).filter(op -> op.precedence == BinaryOp.COMPARISON).orElse(null);
    }

    private Expr sum() {
        var start = peek();
        var left = product();
        while (checkOp("+") || checkOp("-")) {
            var op = advance().text().equals("+") ? BinaryOp.ADD : BinaryOp.SUB;
            left = new Expr.Binary(id(start), op, left, product());
        }
        return left;
    }

    private Expr product() {
        var start = peek();
        var left = unary();
        while (true) {
            var token = peek();
            if (token.kind() == Kind.OP && UNSUPPORTED_OPERATORS.contains(token.text())) {
                throw fail(token, "'" + token.text() + "' is not supported");
            }
            if (!(checkOp("*") || checkOp("/") || checkOp("//") || checkOp("%"))) {
                return left;
            }
            var op = BinaryOp.ofSymbol(advance().text()).orElseThrow();
            left = new Expr.Binary(id(start), op, left, unary());
        }
    }

    private Expr unary() {
        var token = peek();
        if (token.is(Kind.OP, "-")) {
            advance();
            return new Expr.Unary(id(token), UnaryOp.NEG, unary());
        }
        if (token.is(Kind.OP, "+")) {
            throw fail(token, "unary '+' is not supported");
        }
        if (token.is(Kind.OP, "~")) {
            throw fail(token, "'~' is not supported");
        }
        return postfix();
    }

    /** An atom followed by fields, items, slices and method calls, in any order. */
    private Expr postfix() {
        var start = peek();
        var e = atom();
        while (true) {
            var token = peek();
            if (token.is(Kind.OP, "(")) {
                // Functions are not values: only a name or a method is called.
                throw fail(token, e instanceof Expr.Call ? "calling the result of a call is not supported"
                        : "only a function name can be called");
            }
            if (matchOp(".")) {
                var name = name("a name after '.'").text();
                e = checkOp("(") ? call(start, e, name) : new Expr.Attribute(id(start), e, name);
            } else if (matchOp("[")) {
                e = subscript(start, e);
            } else {
                return e;
            }
        }
    }

    /** After {@code [}: an index or a slice, up to the closing {@code ]}. */
    private Expr subscript(Token start, Expr target) {
        Expr lower = null;
        if (!checkOp(":")) {
            lower = expr();
            if (checkOp(",")) {
                throw fail(peek(), "tuples are not supported");
            }
            if (matchOp("]")) {
                return new Expr.Index(id(start), target, lower);
            }
        }
        expect(":");
        Expr upper = checkOp(":") || checkOp("]") ? null : expr();
        Expr step = null;
        if (matchOp(":") && !checkOp("]")) {
            step = expr();
        }
        expect("]");
        return new Expr.Slice(id(start), target, lower, upper, step);
    }

    private Expr atom() {
        var token = peek();
        switch (token.kind()) {
            case INT:
                advance();
                return new Expr.IntLit(id(token), (Long) token.value());
            case FLOAT:
                advance();
                return new Expr.FloatLit(id(token), (Double) token.value());
            case STRING:
                advance();
                if (check(Kind.STRING)) {
                    throw fail(peek(), "adjacent strings are not supported; join them with +");
                }
                return new Expr.StrLit(id(token), (String) token.value());
            case ERROR:
                throw fail(token, (String) token.value());
            case NAME:
                return nameOrCall();
            case OP:
                return bracket(token);
            case NEWLINE:
            case END_COMMENT:
                throw fail(token, "the line ends where a value is expected");
            default:
                throw fail(token, "expected a value");
        }
    }

    private Expr nameOrCall() {
        var token = advance();
        switch (token.text()) {
            case "True":
                return new Expr.BoolLit(id(token), true);
            case "False":
                return new Expr.BoolLit(id(token), false);
            case "None":
                return new Expr.NoneLit(id(token));
            default:
                if (UNSUPPORTED_KEYWORDS.contains(token.text())) {
                    throw unsupportedKeyword(token);
                }
                if (KEYWORDS.contains(token.text())) {
                    throw fail(token, "unexpected '" + token.text() + "'");
                }
        }
        return checkOp("(") ? call(token, null, token.text()) : new Expr.Name(id(token), token.text());
    }

    /** {@code name(...)} or {@code receiver.name(...)}, from its {@code (}. */
    private Expr.Call call(Token start, Expr receiver, String name) {
        expect("(");
        var args = new ArrayList<Expr>();
        var keywords = new ArrayList<Expr.Keyword>();
        while (!checkOp(")")) {
            var arg = peek();
            if (arg.is(Kind.OP, "*") || arg.is(Kind.OP, "**")) {
                throw fail(arg, "unpacking arguments with '" + arg.text() + "' is not supported");
            }
            if (arg.kind() == Kind.NAME && peekAt(1).is(Kind.OP, "=")) {
                var keyword = name("an argument name").text();
                advance();
                if (keywords.stream().anyMatch(k -> k.name().equals(keyword))) {
                    throw fail(arg, "keyword argument repeated: " + keyword);
                }
                keywords.add(new Expr.Keyword(keyword, expr()));
            } else if (!keywords.isEmpty()) {
                throw fail(arg, "positional argument follows keyword argument");
            } else {
                args.add(expr());
            }
            if (isKeyword(peek(), "for")) {
                throw fail(peek(), "comprehensions are not supported");
            }
            if (!checkOp(")")) {
                expect(",");
            }
        }
        advance();
        return new Expr.Call(id(start), receiver, name, args, keywords);
    }

    private Expr bracket(Token token) {
        switch (token.text()) {
            case "(": {
                advance();
                if (checkOp(")")) {
                    throw fail(token, "tuples are not supported");
                }
                var inner = expr();
                if (checkOp(",")) {
                    throw fail(peek(), "tuples are not supported");
                }
                if (isKeyword(peek(), "for")) {
                    throw fail(peek(), "comprehensions are not supported");
                }
                expect(")");
                return inner;
            }
            case "[": {
                advance();
                var items = new ArrayList<Expr>();
                while (!checkOp("]")) {
                    items.add(expr());
                    if (isKeyword(peek(), "for")) {
                        throw fail(peek(), "comprehensions are not supported");
                    }
                    if (!checkOp("]")) {
                        expect(",");
                    }
                }
                advance();
                return new Expr.ListLit(id(token), items);
            }
            case "{":
                throw fail(token, "dicts and sets are not supported");
            case "...":
                throw fail(token, "'...' is not supported");
            default:
                throw fail(token, "expected a value, found '" + token.text() + "'");
        }
    }

    // ---- line ends, comments and recovery ----

    private String endComment() {
        return check(Kind.END_COMMENT) ? (String) advance().value() : null;
    }

    /** The end of a simple statement: an optional comment and the line break. */
    private String endLine() {
        var end = endComment();
        var token = peek();
        if (token.kind() == Kind.NEWLINE) {
            advance();
            return end;
        }
        if (token.is(Kind.OP, ";")) {
            throw fail(token, "semicolons are not supported; put each statement on its own line");
        }
        if (token.is(Kind.OP, ",")) {
            throw fail(token, "tuples are not supported");
        }
        if (token.kind() == Kind.OP && UNSUPPORTED_OPERATORS.contains(token.text())) {
            throw fail(token, "'" + token.text() + "' is not supported");
        }
        if (token.kind() == Kind.ERROR) {
            throw fail(token, (String) token.value());
        }
        throw fail(token, "unexpected '" + token.text() + "'");
    }

    private void takeComments() {
        while (check(Kind.COMMENT)) {
            pending.add((String) advance().value());
        }
    }

    private List<String> takePending() {
        var taken = List.copyOf(pending);
        pending.clear();
        return taken;
    }

    /**
     * Records the error and skips what the failed statement covers: the rest of its line unless the failure came at
     * a line start after the statement's first token, then an indented block that follows (its body).
     */
    private void recover(Failure failure, int start) {
        errors.add(failure.error);
        pending.clear();
        if (pos == start && check(Kind.INDENT)) {
            skipIndentedBlock();
            return;
        }
        var previous = pos > start ? tokens.get(pos - 1).kind() : null;
        boolean atLineStart = previous == Kind.NEWLINE || previous == Kind.INDENT || previous == Kind.DEDENT;
        if (!atLineStart) {
            while (!check(Kind.NEWLINE) && !check(Kind.EOF)) {
                advance();
            }
            if (check(Kind.NEWLINE)) {
                advance();
            }
        }
        if (check(Kind.INDENT)) {
            skipIndentedBlock();
        }
    }

    private void skipIndentedBlock() {
        int depth = 0;
        do {
            if (check(Kind.INDENT)) {
                depth++;
            } else if (check(Kind.DEDENT)) {
                depth--;
            }
            advance();
        } while (depth > 0 && !check(Kind.EOF));
    }

    // ---- token helpers ----

    private Token peek() {
        return tokens.get(pos);
    }

    private Token peekAt(int offset) {
        return tokens.get(Math.min(pos + offset, tokens.size() - 1));
    }

    private Token advance() {
        var token = tokens.get(pos);
        if (token.kind() != Kind.EOF) {
            pos++;
        }
        return token;
    }

    private boolean check(Kind kind) {
        return peek().kind() == kind;
    }

    private boolean checkOp(String text) {
        return peek().is(Kind.OP, text);
    }

    private boolean matchOp(String text) {
        if (checkOp(text)) {
            advance();
            return true;
        }
        return false;
    }

    private static boolean isKeyword(Token token, String keyword) {
        return token.is(Kind.NAME, keyword);
    }

    private void expect(String op) {
        if (!checkOp(op)) {
            var token = peek();
            throw fail(token, "expected '" + op + "'" + (token.kind() == Kind.NEWLINE ? " before the line ends"
                    : ", found '" + token.text() + "'"));
        }
        advance();
    }

    private Token name(String what) {
        var token = peek();
        if (token.kind() != Kind.NAME || KEYWORDS.contains(token.text())) {
            throw fail(token, "expected " + what);
        }
        return advance();
    }

    private String id(Token at) {
        var id = ids.next();
        positions.put(id, new Position(at.line(), at.column()));
        return id;
    }

    private Failure unsupportedKeyword(Token token) {
        return fail(token, "'" + token.text() + "' is not supported");
    }

    private static Failure fail(Token token, String message) {
        return new Failure(new ScriptError(token.line(), token.column(), message));
    }
}
