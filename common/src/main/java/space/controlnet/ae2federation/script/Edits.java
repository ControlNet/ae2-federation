package space.controlnet.ae2federation.script;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Edits of a function's canvas, addressed by node ID and port or exec output. Each gives the canvas of the edited
 * function, which is still a whole program: an edit that would leave a required port empty, assign to something that
 * is no place, or give a script that does not parse is refused with an {@link IllegalArgumentException}. Nodes keep
 * their IDs; the parts an edit adds bring their own, from an {@link Ids} that continues the file's. Types and action
 * marks stay the old canvas's until the program is checked again.
 */
public final class Edits {
    private Edits() {
    }

    // ---- values on ports ----

    /**
     * Puts {@code value} on a port: a literal, a variable, or a new value whose nodes come with it; {@code null}
     * empties an optional part. A call takes a new argument on {@code argN} or {@code kw:name} and a list a new item on
     * {@code itemN}, one past the last; {@code null} removes them, and the later ones move up.
     */
    public static Canvas put(Canvas canvas, String node, String port, Expr value) {
        var draft = new Draft(canvas);
        draft.place(node, port, value);
        return draft.done();
    }

    /**
     * Connects the value node {@code value} to another port; what that port held is dropped, and {@code filler} (as in
     * {@link #put}) takes the place the value leaves.
     */
    public static Canvas move(Canvas canvas, String value, String node, String port, Expr filler) {
        var draft = new Draft(canvas);
        var moved = draft.value(value);
        var target = draft.node(node);
        if (!target.inputs().containsKey(port)) {
            throw refuse(node + " has no port " + port);
        }
        if (draft.within(moved.id()).contains(node)) {
            throw refuse("a value cannot feed itself");
        }
        var holder = draft.holder(value);
        draft.place(holder.node(), holder.port(), filler);
        draft.wire(node, port, new Canvas.Input.Wire(value));
        return draft.done();
    }

    /**
     * Renames what a node shows: the function, a call, a field or a loop variable takes a new name; an operator node
     * takes another operator ({@code +}, {@code not}, ...).
     */
    public static Canvas rename(Canvas canvas, String node, String name) {
        var draft = new Draft(canvas);
        var n = draft.node(node);
        boolean valid = switch (n.kind()) {
            case ENTRY, CALL, ATTRIBUTE, FOR -> isName(name);
            // An augmented assignment with a comparison, such as "x <= 1", is no statement; the script check refuses it.
            case BINARY, AUG_ASSIGN -> BinaryOp.ofSymbol(name).isPresent();
            case UNARY -> name.equals("not") || name.equals("-");
            default -> throw refuse(n.kind() + " has no name");
        };
        if (!valid) {
            throw refuse("'" + name + "' cannot name " + n.kind());
        }
        draft.set(copy(n, name, n.type(), n.inputs(), n.params(), n.docstring(), n.notes(), n.armNotes()));
        return draft.done();
    }

    /** Renames a variable of the function wherever it is read or written, and where it is a parameter. */
    public static Canvas renameVariable(Canvas canvas, String from, String to) {
        var locals = Canvas.locals(canvas.toFunction());
        if (!locals.contains(from)) {
            throw refuse(from + " is no variable here");
        }
        if (!isName(to)) {
            throw refuse("'" + to + "' cannot name a variable");
        }
        if (locals.contains(to) || canvas.nodes().stream().anyMatch(n -> to.equals(n.qualifier()))) {
            throw refuse(to + " is already a name here");
        }
        var draft = new Draft(canvas);
        for (var n : canvas.nodes()) {
            var inputs = new LinkedHashMap<String, Canvas.Input>();
            n.inputs().forEach((port, input) -> inputs.put(port,
                    input instanceof Canvas.Input.Chip c && c.name().name().equals(from)
                            ? new Canvas.Input.Chip(new Expr.Name(c.name().id(), to))
                            : input));
            var params = n.params().stream().map(p -> p.name().equals(from)
                    ? new Canvas.Param(p.id(), to, p.type(), p.defaultValue()) : p).toList();
            var name = n.kind() == Canvas.Kind.FOR && from.equals(n.name()) ? to : n.name();
            draft.set(copy(n, name, n.type(), inputs, params, n.docstring(), n.notes(), n.armNotes()));
        }
        return draft.done();
    }

    // ---- statements ----

    /** Inserts {@code statement}, with its own nodes, on the exec output {@code output} of {@code from}. */
    public static Canvas insert(Canvas canvas, String from, String output, Stmt statement) {
        var draft = new Draft(canvas);
        var added = draft.statement(statement);
        draft.attach(added, from, output);
        return draft.done();
    }

    /** Removes a statement and everything inside it; the statements after it close up. */
    public static Canvas remove(Canvas canvas, String statement) {
        var draft = new Draft(canvas);
        draft.detach(draft.statementNode(statement).id());
        return draft.done();
    }

    /** Moves a statement, with everything inside it, to the exec output {@code output} of {@code from}. */
    public static Canvas relocate(Canvas canvas, String statement, String from, String output) {
        var draft = new Draft(canvas);
        draft.statementNode(statement);
        if (draft.inside(statement).contains(from)) {
            throw refuse("a statement cannot move inside itself");
        }
        draft.detach(statement);
        draft.attach(statement, from, output);
        return draft.done();
    }

    /**
     * Stores a value in a new variable just before the statement that uses it, and reads the variable where the value
     * was. Refused where that would change how often the value is computed: in a {@code while} or {@code elif}
     * condition, or on the right of {@code and} and {@code or}.
     */
    public static Canvas extract(Canvas canvas, String value, String name, Ids ids) {
        if (!isName(name)) {
            throw refuse("'" + name + "' cannot name a variable");
        }
        if (Canvas.locals(canvas.toFunction()).contains(name)) {
            throw refuse(name + " is already a variable here");
        }
        var draft = new Draft(canvas);
        draft.value(value);
        var place = draft.holder(value);
        var at = place;
        while (true) {
            var holder = draft.node(at.node());
            boolean repeated = holder.kind() == Canvas.Kind.WHILE
                    || holder.kind() == Canvas.Kind.IF && !at.port().equals("condition")
                    || holder.kind() == Canvas.Kind.BINARY && at.port().equals("right")
                            && (holder.name().equals("and") || holder.name().equals("or"));
            if (repeated) {
                throw refuse("the value would no longer run as often as it does here");
            }
            if (STATEMENTS.contains(holder.kind())) {
                break;
            }
            at = draft.holder(holder.id());
        }
        var statement = at.node();
        draft.wire(place.node(), place.port(), new Canvas.Input.Chip(new Expr.Name(ids.next(), name)));
        var inputs = new LinkedHashMap<String, Canvas.Input>();
        inputs.put("target", new Canvas.Input.Chip(new Expr.Name(ids.next(), name)));
        inputs.put("value", new Canvas.Input.Wire(value));
        var assign = new Canvas.Node(ids.next(), Canvas.Kind.ASSIGN, null, null, null, inputs, List.of(), null,
                Notes.NONE, List.of(), false);
        draft.set(assign);
        var incoming = draft.incoming(statement);
        draft.links.remove(incoming);
        draft.links.add(new Canvas.Link(incoming.from(), incoming.output(), assign.id()));
        draft.links.add(new Canvas.Link(assign.id(), "next", statement));
        return draft.done();
    }

    // ---- if arms ----

    /** Adds an {@code elif} with {@code condition} after the last one, its body empty. */
    public static Canvas addArm(Canvas canvas, String node, Expr condition) {
        var draft = new Draft(canvas);
        var n = draft.kind(node, Canvas.Kind.IF);
        int arms = conditions(n);
        var inputs = new LinkedHashMap<>(n.inputs());
        inputs.put("condition" + arms, draft.input(condition));
        var armNotes = new ArrayList<>(n.armNotes());
        armNotes.add(arms - 1, Notes.NONE);
        draft.set(copy(n, n.name(), n.type(), inputs, n.params(), n.docstring(), n.notes(), armNotes));
        return draft.done();
    }

    /** Adds an empty {@code else}. */
    public static Canvas addElse(Canvas canvas, String node) {
        var draft = new Draft(canvas);
        var n = draft.kind(node, Canvas.Kind.IF);
        if (hasElse(n)) {
            throw refuse("this if already has an else");
        }
        var armNotes = new ArrayList<>(n.armNotes());
        armNotes.add(Notes.NONE);
        draft.set(copy(n, n.name(), n.type(), n.inputs(), n.params(), n.docstring(), n.notes(), armNotes));
        return draft.done();
    }

    /** Removes an {@code elif} ({@code arm1}, {@code arm2}, ...) or the {@code else}, with its statements. */
    public static Canvas removeArm(Canvas canvas, String node, String output) {
        var draft = new Draft(canvas);
        var n = draft.kind(node, Canvas.Kind.IF);
        int arms = conditions(n);
        var armNotes = new ArrayList<>(n.armNotes());
        var inputs = new LinkedHashMap<>(n.inputs());
        if (output.equals("else") && hasElse(n)) {
            armNotes.remove(armNotes.size() - 1);
            draft.links.removeIf(l -> l.from().equals(node) && l.output().equals("else"));
        } else {
            int arm = armIndex(output);
            if (arm == 0) {
                throw refuse("the first arm is the if itself; remove the if instead");
            }
            if (arm < 0 || arm >= arms) {
                throw refuse("this if has no " + output);
            }
            armNotes.remove(arm - 1);
            inputs.remove("condition" + arm);
            for (int i = arm + 1; i < arms; i++) {
                inputs.put("condition" + (i - 1), inputs.remove("condition" + i));
            }
            draft.links.removeIf(l -> l.from().equals(node) && l.output().equals(output));
            draft.links.replaceAll(l -> l.from().equals(node) && armIndex(l.output()) > arm
                    ? new Canvas.Link(node, "arm" + (armIndex(l.output()) - 1), l.to()) : l);
        }
        draft.set(copy(n, n.name(), n.type(), inputs, n.params(), n.docstring(), n.notes(), armNotes));
        return draft.done();
    }

    // ---- signature, types and comments ----

    /** Sets the function's parameters and its result type. */
    public static Canvas signature(Canvas canvas, List<Param> params, TypeRef result) {
        var draft = new Draft(canvas);
        var entry = canvas.nodes().get(0);
        var projected = new ArrayList<Canvas.Param>();
        for (var param : params) {
            if (!isName(param.name())) {
                throw refuse("'" + param.name() + "' cannot name a parameter");
            }
            projected.add(new Canvas.Param(param.id(), param.name(), param.type(),
                    param.defaultValue() == null ? Canvas.Input.Empty.INSTANCE : draft.input(param.defaultValue())));
        }
        draft.set(copy(entry, entry.name(), result, entry.inputs(), projected, entry.docstring(), entry.notes(),
                entry.armNotes()));
        return draft.done();
    }

    /** Sets the function's docstring; {@code null} removes it. */
    public static Canvas describe(Canvas canvas, String docstring) {
        var draft = new Draft(canvas);
        var entry = canvas.nodes().get(0);
        draft.set(copy(entry, entry.name(), entry.type(), entry.inputs(), entry.params(), docstring, entry.notes(),
                entry.armNotes()));
        return draft.done();
    }

    /** Sets an assignment's type annotation, as in {@code x: int = 0}; {@code null} removes it. */
    public static Canvas retype(Canvas canvas, String node, TypeRef type) {
        var draft = new Draft(canvas);
        var n = draft.kind(node, Canvas.Kind.ASSIGN);
        draft.set(copy(n, n.name(), type, n.inputs(), n.params(), n.docstring(), n.notes(), n.armNotes()));
        return draft.done();
    }

    /** Sets the comments of the function or of a statement. */
    public static Canvas annotate(Canvas canvas, String node, Notes notes) {
        checkNotes(notes);
        var draft = new Draft(canvas);
        var n = draft.statementNode(node, true);
        draft.set(copy(n, n.name(), n.type(), n.inputs(), n.params(), n.docstring(), notes, n.armNotes()));
        return draft.done();
    }

    /** Sets the comments of an {@code elif} ({@code arm1}, ...) or of the {@code else}. */
    public static Canvas annotateArm(Canvas canvas, String node, String output, Notes notes) {
        checkNotes(notes);
        var draft = new Draft(canvas);
        var n = draft.kind(node, Canvas.Kind.IF);
        int arm = output.equals("else") && hasElse(n) ? conditions(n) : armIndex(output);
        if (arm < 1 || arm > n.armNotes().size()) {
            throw refuse("this if has no comments for " + output);
        }
        var armNotes = new ArrayList<>(n.armNotes());
        armNotes.set(arm - 1, notes);
        draft.set(copy(n, n.name(), n.type(), n.inputs(), n.params(), n.docstring(), n.notes(), armNotes));
        return draft.done();
    }

    // ---- the program ----

    /** The program with {@code definition} in place of the one with its ID; every other definition stays as it is. */
    public static Program replace(Program program, Definition definition) {
        var definitions = new ArrayList<Definition>();
        boolean found = false;
        for (var existing : program.definitions()) {
            if (existing.id().equals(definition.id())) {
                definitions.add(definition);
                found = true;
            } else {
                definitions.add(existing);
            }
        }
        if (!found) {
            throw refuse("the program has no definition " + definition.id());
        }
        return new Program(definitions, program.endComments());
    }

    // ---- helpers ----

    private static final Set<Canvas.Kind> STATEMENTS = Set.of(Canvas.Kind.ASSIGN, Canvas.Kind.AUG_ASSIGN,
            Canvas.Kind.CALL_STATEMENT, Canvas.Kind.IF, Canvas.Kind.WHILE, Canvas.Kind.FOR, Canvas.Kind.RETURN,
            Canvas.Kind.BREAK, Canvas.Kind.CONTINUE, Canvas.Kind.PASS);

    private static IllegalArgumentException refuse(String message) {
        return new IllegalArgumentException(message);
    }

    /** Whether {@code name} is a variable name the script accepts. */
    static boolean isName(String name) {
        var parsed = ScriptParser.parse("def f() -> None:\n    " + name + " = 0\n");
        return parsed.ok() && parsed.program().functions().get(0).body().get(0) instanceof Stmt.Assign a
                && a.target() instanceof Expr.Name n && n.name().equals(name);
    }

    private static void checkNotes(Notes notes) {
        var texts = new ArrayList<>(notes.above());
        if (notes.end() != null) {
            texts.add(notes.end());
        }
        if (texts.stream().anyMatch(t -> t.contains("\n") || t.contains("\r"))) {
            throw refuse("a comment is one line");
        }
    }

    private static int conditions(Canvas.Node n) {
        return (int) n.inputs().keySet().stream().filter(k -> k.startsWith("condition")).count();
    }

    private static boolean hasElse(Canvas.Node n) {
        return n.armNotes().size() == conditions(n);
    }

    /** {@code k} for {@code armK}, otherwise -1. */
    private static int armIndex(String output) {
        return output.matches("arm\\d+") ? Integer.parseInt(output.substring(3)) : -1;
    }

    private static Canvas.Node copy(Canvas.Node n, String name, TypeRef type, Map<String, Canvas.Input> inputs,
            List<Canvas.Param> params, String docstring, Notes notes, List<Notes> armNotes) {
        return new Canvas.Node(n.id(), n.kind(), name, n.namespace(), type, inputs, params, docstring, notes, armNotes,
                n.effect());
    }

    /** Where a value node is wired in: a node and its port. */
    private record Place(String node, String port) {
    }

    /** A canvas being edited: nodes by ID (the entry first) and the exec links. */
    private static final class Draft {
        final Canvas canvas;
        final Map<String, Canvas.Node> nodes = new LinkedHashMap<>();
        final List<Canvas.Link> links;

        Draft(Canvas canvas) {
            this.canvas = canvas;
            canvas.nodes().forEach(n -> nodes.put(n.id(), n));
            links = new ArrayList<>(canvas.links());
        }

        Canvas.Node node(String id) {
            var n = nodes.get(id);
            if (n == null) {
                throw refuse("no node " + id);
            }
            return n;
        }

        Canvas.Node kind(String id, Canvas.Kind kind) {
            var n = node(id);
            if (n.kind() != kind) {
                throw refuse(id + " is no " + kind);
            }
            return n;
        }

        Canvas.Node value(String id) {
            var n = node(id);
            if (STATEMENTS.contains(n.kind()) || n.kind() == Canvas.Kind.ENTRY) {
                throw refuse(id + " is no value");
            }
            return n;
        }

        Canvas.Node statementNode(String id) {
            return statementNode(id, false);
        }

        Canvas.Node statementNode(String id, boolean orEntry) {
            var n = node(id);
            if (!STATEMENTS.contains(n.kind()) && !(orEntry && n.kind() == Canvas.Kind.ENTRY)) {
                throw refuse(id + " is no statement");
            }
            return n;
        }

        void set(Canvas.Node n) {
            nodes.put(n.id(), n);
        }

        /** Projects a new value, adding its nodes; refused if one of them has an ID the canvas uses. */
        Canvas.Input input(Expr value) {
            var builder = new Canvas.Builder(Set.of(), Set.of());
            var input = builder.input(value);
            addAll(builder);
            return input;
        }

        /** Projects a new statement, adding its nodes and inner links; its ID. */
        String statement(Stmt statement) {
            var builder = new Canvas.Builder(Set.of(), Set.of());
            var id = builder.statement(statement);
            addAll(builder);
            return id;
        }

        private void addAll(Canvas.Builder builder) {
            for (var n : builder.nodes) {
                if (nodes.containsKey(n.id())) {
                    throw refuse("the ID " + n.id() + " is taken");
                }
                set(n);
            }
            links.addAll(builder.links);
        }

        void wire(String node, String port, Canvas.Input input) {
            var n = node(node);
            var inputs = new LinkedHashMap<>(n.inputs());
            inputs.put(port, input);
            set(copy(n, n.name(), n.type(), inputs, n.params(), n.docstring(), n.notes(), n.armNotes()));
        }

        /** {@link Edits#put}. */
        void place(String node, String port, Expr value) {
            var n = node(node);
            var prefix = n.kind() == Canvas.Kind.CALL ? "arg" : n.kind() == Canvas.Kind.LIST ? "item" : null;
            boolean numbered = prefix != null && port.matches(prefix + "\\d+");
            boolean keyword = n.kind() == Canvas.Kind.CALL && port.startsWith("kw:");
            var inputs = new LinkedHashMap<>(n.inputs());
            if (!inputs.containsKey(port)) {
                long count = inputs.keySet().stream().filter(k -> prefix != null && k.matches(prefix + "\\d+")).count();
                boolean next = numbered && Integer.parseInt(port.substring(prefix.length())) == count;
                if (value == null || !(next || keyword && isName(port.substring(3)))) {
                    throw refuse(node + " has no port " + port);
                }
            }
            if (value != null) {
                inputs.put(port, input(value));
            } else if (numbered || keyword) {
                inputs.remove(port);
            } else if (optional(n.kind(), port)) {
                inputs.put(port, Canvas.Input.Empty.INSTANCE);
            } else {
                throw refuse(port + " of " + node + " needs a value");
            }
            if (value != null && n.kind() == Canvas.Kind.CALL_STATEMENT && !(value instanceof Expr.Call)) {
                throw refuse("a statement of its own is a call");
            }
            // The canvas made from the result numbers the ports again from 0.
            set(copy(n, n.name(), n.type(), inputs, n.params(), n.docstring(), n.notes(), n.armNotes()));
        }

        private static boolean optional(Canvas.Kind kind, String port) {
            return kind == Canvas.Kind.SLICE && !port.equals("object") || kind == Canvas.Kind.RETURN;
        }

        /** The port a value node is wired to. */
        Place holder(String value) {
            for (var n : nodes.values()) {
                for (var entry : n.inputs().entrySet()) {
                    if (entry.getValue() instanceof Canvas.Input.Wire w && w.node().equals(value)) {
                        return new Place(n.id(), entry.getKey());
                    }
                }
            }
            throw refuse(value + " is wired nowhere");
        }

        /** A value node and every node that feeds it. */
        Set<String> within(String value) {
            var found = new HashSet<String>();
            var pending = new ArrayList<>(List.of(value));
            while (!pending.isEmpty()) {
                var id = pending.remove(pending.size() - 1);
                if (found.add(id)) {
                    node(id).inputs().values().forEach(input -> {
                        if (input instanceof Canvas.Input.Wire w) {
                            pending.add(w.node());
                        }
                    });
                }
            }
            return found;
        }

        /** A statement and every statement in its bodies. */
        Set<String> inside(String statement) {
            var found = new HashSet<String>();
            var pending = new ArrayList<>(List.of(statement));
            while (!pending.isEmpty()) {
                var id = pending.remove(pending.size() - 1);
                if (found.add(id)) {
                    for (var l : links) {
                        if (l.from().equals(id) && (!l.output().equals("next") || !id.equals(statement))) {
                            pending.add(l.to());
                        }
                    }
                }
            }
            return found;
        }

        Canvas.Link incoming(String statement) {
            return links.stream().filter(l -> l.to().equals(statement)).findFirst()
                    .orElseThrow(() -> refuse(statement + " is on no exec link"));
        }

        /** Takes a statement out of its chain; the statements after it close up. */
        void detach(String statement) {
            var incoming = incoming(statement);
            var next = links.stream().filter(l -> l.from().equals(statement) && l.output().equals("next")).findFirst();
            links.remove(incoming);
            next.ifPresent(links::remove);
            next.ifPresent(l -> links.add(new Canvas.Link(incoming.from(), incoming.output(), l.to())));
        }

        /** Puts a detached statement on the exec output {@code output} of {@code from}. */
        void attach(String statement, String from, String output) {
            var n = node(from);
            boolean exists = switch (n.kind()) {
                case ENTRY -> output.equals("next");
                case WHILE, FOR -> output.equals("next") || output.equals("body");
                case IF -> output.equals("next") || output.equals("else") && hasElse(n)
                        || armIndex(output) >= 0 && armIndex(output) < conditions(n);
                default -> STATEMENTS.contains(n.kind()) && output.equals("next");
            };
            if (!exists) {
                throw refuse(from + " has no exec output " + output);
            }
            var old = links.stream().filter(l -> l.from().equals(from) && l.output().equals(output)).findFirst();
            old.ifPresent(links::remove);
            links.add(new Canvas.Link(from, output, statement));
            old.ifPresent(l -> links.add(new Canvas.Link(statement, "next", l.to())));
        }

        /** The edited canvas, projected again from the function it gives. */
        Canvas done() {
            FunctionDef function;
            try {
                function = new Canvas(List.copyOf(nodes.values()), links, canvas.types()).toFunction();
            } catch (RuntimeException e) {
                throw refuse("this gives no program: " + e.getMessage());
            }
            var parsed = ScriptParser.parse(ScriptPrinter.print(new Program(List.of(function), List.of())));
            if (!parsed.ok()) {
                throw refuse(parsed.errors().get(0).message());
            }
            return Canvas.of(function, canvas.types(), canvas.effectfulCalls());
        }
    }
}
