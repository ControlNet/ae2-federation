package space.controlnet.ae2federation.function;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the script view: a Lua-like subset (an assistant proposal, 2026-10-18; the language is not yet chosen).
 * Functions are {@code query name(p: type): type ... end} or {@code action name(p: type) ... end}; statements are
 * {@code local}, {@code if/elseif/else/end}, {@code return} and calls; {@code not} binds looser than a comparison, as
 * in Python. A comment ({@code --}) must stand on its own lines above a statement or a function, which it then
 * belongs to. Every node gets a fresh ID from {@code ids}; {@link Reconciler} carries earlier IDs over.
 */
public final class ScriptParser {
    /** A mistake in the text, at a 1-based line. */
    public static final class SyntaxError extends RuntimeException {
        private final int line;

        SyntaxError(int line, String message) {
            super("Line " + line + ": " + message);
            this.line = line;
        }

        public int line() {
            return line;
        }
    }

    private enum Kind { WORD, NUMBER, TEXT, RESOURCE, SYMBOL, COMMENT, END }

    private record Token(Kind kind, String text, Object value, int line, boolean ownLine) {
    }

    private final List<Token> tokens;
    private final Ids ids;
    private int at;

    private ScriptParser(List<Token> tokens, Ids ids) {
        this.tokens = tokens;
        this.ids = ids;
    }

    public static Program parse(String source, Ids ids) {
        return new ScriptParser(lex(source), ids).program();
    }

    // ---- lexing ----

    private static List<Token> lex(String source) {
        var out = new ArrayList<Token>();
        int line = 1, i = 0, n = source.length();
        boolean lineStart = true;
        while (i < n) {
            char c = source.charAt(i);
            if (c == '\n') {
                line++;
                lineStart = true;
                i++;
                continue;
            }
            if (c == ' ' || c == '\t' || c == '\r') {
                i++;
                continue;
            }
            if (source.startsWith("--", i)) {
                int end = source.indexOf('\n', i);
                if (end < 0) end = n;
                var text = source.substring(i + 2, end);
                out.add(new Token(Kind.COMMENT, text.startsWith(" ") ? text.substring(1) : text, null, line, lineStart));
                i = end;
                continue;
            }
            int start = i;
            if (Character.isLetter(c) || c == '_') {
                while (i < n && (Character.isLetterOrDigit(source.charAt(i)) || source.charAt(i) == '_')) i++;
                out.add(new Token(Kind.WORD, source.substring(start, i), null, line, lineStart));
            } else if (Character.isDigit(c)) {
                while (i < n && (Character.isDigit(source.charAt(i)) || source.charAt(i) == '.')) i++;
                var text = source.substring(start, i);
                try {
                    out.add(new Token(Kind.NUMBER, text, Double.parseDouble(text), line, lineStart));
                } catch (NumberFormatException e) {
                    throw new SyntaxError(line, "not a number: " + text);
                }
            } else if (c == '"') {
                var text = new StringBuilder();
                i++;
                while (true) {
                    if (i >= n || source.charAt(i) == '\n') throw new SyntaxError(line, "unfinished text");
                    char d = source.charAt(i++);
                    if (d == '"') break;
                    if (d == '\\' && i < n) {
                        char e = source.charAt(i++);
                        text.append(switch (e) {
                            case 'n' -> '\n';
                            case '"', '\\' -> e;
                            default -> throw new SyntaxError(line, "unknown escape \\" + e);
                        });
                    } else {
                        text.append(d);
                    }
                }
                out.add(new Token(Kind.TEXT, source.substring(start, i), text.toString(), line, lineStart));
            } else if (c == '@') {
                i++;
                String type = ResourceRef.ITEM;
                if (i < n && source.charAt(i) == '[') {
                    int close = source.indexOf(']', i);
                    if (close < 0) throw new SyntaxError(line, "unfinished resource type");
                    type = source.substring(i + 1, close);
                    i = close + 1;
                }
                int idStart = i;
                while (i < n && "abcdefghijklmnopqrstuvwxyz0123456789_.-/:".indexOf(source.charAt(i)) >= 0) i++;
                try {
                    out.add(new Token(Kind.RESOURCE, source.substring(start, i),
                            new ResourceRef(type, source.substring(idStart, i)), line, lineStart));
                } catch (IllegalArgumentException e) {
                    throw new SyntaxError(line, e.getMessage());
                }
            } else {
                var two = i + 1 < n ? source.substring(i, i + 2) : "";
                var symbol = List.of("==", "~=", "<=", ">=").contains(two) ? two : String.valueOf(c);
                if (!"()=,:<>+-*/".contains(symbol) && symbol.length() == 1) {
                    throw new SyntaxError(line, "unexpected '" + c + "'");
                }
                i += symbol.length();
                out.add(new Token(Kind.SYMBOL, symbol, null, line, lineStart));
            }
            lineStart = false;
        }
        out.add(new Token(Kind.END, "end of text", null, line, true));
        return out;
    }

    // ---- parsing ----

    private Token peek() {
        return tokens.get(at);
    }

    private Token take() {
        return tokens.get(at++);
    }

    private boolean is(String text) {
        var token = peek();
        return (token.kind == Kind.WORD || token.kind == Kind.SYMBOL) && token.text.equals(text);
    }

    private Token expect(String text) {
        if (!is(text)) throw new SyntaxError(peek().line, "expected '" + text + "' but found '" + peek().text + "'");
        return take();
    }

    private String name() {
        var token = take();
        if (token.kind != Kind.WORD || KEYWORDS.contains(token.text)) {
            throw new SyntaxError(token.line, "expected a name but found '" + token.text + "'");
        }
        return token.text;
    }

    private static final List<String> KEYWORDS = List.of("query", "action", "local", "if", "then", "elseif", "else",
            "end", "return", "and", "or", "not", "true", "false");

    /** The comment lines directly above the next token, joined; "" for none. */
    private String comment() {
        var lines = new ArrayList<String>();
        while (peek().kind == Kind.COMMENT) {
            var token = take();
            if (!token.ownLine) throw new SyntaxError(token.line, "a comment must stand on its own line above a statement");
            lines.add(token.text);
        }
        return String.join("\n", lines);
    }

    private Program program() {
        var functions = new ArrayList<FunctionDef>();
        while (true) {
            var comment = comment();
            if (peek().kind == Kind.END) {
                if (!comment.isEmpty()) throw new SyntaxError(peek().line, "a comment must stand above a function");
                return new Program(functions);
            }
            functions.add(function(comment));
        }
    }

    private FunctionDef function(String comment) {
        var head = take();
        FunctionDef.Kind kind;
        if (head.text.equals("query")) kind = FunctionDef.Kind.QUERY;
        else if (head.text.equals("action")) kind = FunctionDef.Kind.ACTION;
        else throw new SyntaxError(head.line, "expected 'query' or 'action' but found '" + head.text + "'");
        var id = ids.next();
        var name = name();
        expect("(");
        var params = new ArrayList<FunctionDef.Param>();
        while (!is(")")) {
            if (!params.isEmpty()) expect(",");
            var paramId = ids.next();
            var paramName = name();
            expect(":");
            params.add(new FunctionDef.Param(paramId, paramName, type(false)));
        }
        expect(")");
        var result = ValueType.NOTHING;
        if (kind == FunctionDef.Kind.QUERY) {
            expect(":");
            result = type(false);
        } else if (is(":")) {
            throw new SyntaxError(peek().line, "an action returns nothing, so it has no result type");
        }
        var body = block();
        expect("end");
        return new FunctionDef(id, comment, kind, name, params, result, body);
    }

    private ValueType type(boolean nothing) {
        var token = take();
        try {
            var type = ValueType.of(token.text);
            if (type == ValueType.NOTHING && !nothing) throw new IllegalArgumentException();
            return type;
        } catch (IllegalArgumentException e) {
            throw new SyntaxError(token.line, "unknown type '" + token.text + "'");
        }
    }

    /** Statements up to the {@code end}, {@code else} or {@code elseif} that closes the block, which is not taken. */
    private List<Stmt> block() {
        var body = new ArrayList<Stmt>();
        while (true) {
            var comment = comment();
            if (is("end") || is("else") || is("elseif") || peek().kind == Kind.END) {
                if (!comment.isEmpty()) throw new SyntaxError(peek().line, "a comment must stand above a statement");
                if (peek().kind == Kind.END) throw new SyntaxError(peek().line, "expected 'end'");
                return body;
            }
            body.add(statement(comment));
        }
    }

    private Stmt statement(String comment) {
        var token = peek();
        var id = ids.next();
        if (is("local")) {
            take();
            var name = name();
            expect("=");
            return new Stmt.Let(id, comment, name, expression());
        }
        if (is("return")) {
            take();
            // A bare return ends an action; anything on the same line is its value.
            boolean bare = is("end") || is("else") || is("elseif") || peek().kind == Kind.END || peek().ownLine;
            return new Stmt.Return(id, comment, bare ? null : expression());
        }
        if (is("if")) {
            take();
            var branches = new ArrayList<Stmt.Branch>();
            branches.add(branch(ids.next()));
            List<Stmt> otherwise = List.of();
            while (true) {
                if (is("elseif")) {
                    take();
                    branches.add(branch(ids.next()));
                } else if (is("else")) {
                    take();
                    otherwise = block();
                    expect("end");
                    break;
                } else {
                    expect("end");
                    break;
                }
            }
            return new Stmt.If(id, comment, branches, otherwise);
        }
        var expr = expression();
        if (!(expr instanceof Expr.Call call)) throw new SyntaxError(token.line, "only a call can stand as a statement");
        return new Stmt.Do(id, comment, call);
    }

    private Stmt.Branch branch(String id) {
        var condition = expression();
        expect("then");
        return new Stmt.Branch(id, condition, block());
    }

    private Expr expression() {
        return binary(1);
    }

    private Expr binary(int precedence) {
        if (precedence == Expr.Op.NOT.precedence() && is("not")) {
            var id = ids.next();
            take();
            return new Expr.Unary(id, Expr.Op.NOT, binary(precedence));
        }
        if (precedence > Expr.Op.DIV.precedence()) return unary();
        var left = binary(precedence + 1);
        while (true) {
            var op = binaryOp(precedence);
            if (op == null) return left;
            var id = ids.next();
            take();
            left = new Expr.Binary(id, op, left, binary(precedence + 1));
        }
    }

    private Expr.Op binaryOp(int precedence) {
        var token = peek();
        if (token.kind != Kind.WORD && token.kind != Kind.SYMBOL) return null;
        for (var op : Expr.Op.values()) {
            if (!op.unary() && op.precedence() == precedence && op.symbol().equals(token.text)) return op;
        }
        return null;
    }

    private Expr unary() {
        if (is("-")) {
            var id = ids.next();
            take();
            var number = peek().kind == Kind.NUMBER;
            var operand = unary();
            // A minus written straight before a number is part of it: -5 is the literal -5.
            if (number && operand instanceof Expr.Literal literal) {
                return new Expr.Literal(literal.id(), ValueType.NUMBER, -(Double) literal.value());
            }
            return new Expr.Unary(id, Expr.Op.NEG, operand);
        }
        return primary();
    }

    private Expr primary() {
        var token = take();
        switch (token.kind) {
            case NUMBER -> {
                return new Expr.Literal(ids.next(), ValueType.NUMBER, token.value);
            }
            case TEXT -> {
                return new Expr.Literal(ids.next(), ValueType.TEXT, token.value);
            }
            case RESOURCE -> {
                return new Expr.Literal(ids.next(), ValueType.RESOURCE, token.value);
            }
            case WORD -> {
                if (token.text.equals("true") || token.text.equals("false")) {
                    return new Expr.Literal(ids.next(), ValueType.BOOL, token.text.equals("true"));
                }
                if (KEYWORDS.contains(token.text)) throw new SyntaxError(token.line, "unexpected '" + token.text + "'");
                var id = ids.next();
                if (!is("(")) return new Expr.Var(id, token.text);
                take();
                var args = new ArrayList<Expr>();
                while (!is(")")) {
                    if (!args.isEmpty()) expect(",");
                    args.add(expression());
                }
                expect(")");
                return new Expr.Call(id, token.text, args);
            }
            case SYMBOL -> {
                if (token.text.equals("(")) {
                    var inner = expression();
                    expect(")");
                    return inner;
                }
                throw new SyntaxError(token.line, "unexpected '" + token.text + "'");
            }
            default -> throw new SyntaxError(token.line, "unexpected " + token.text);
        }
    }
}
