package space.controlnet.ae2federation.function;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Carries node IDs from the program before a script edit to the program the edited script reads as, so the graph
 * keeps each unchanged node where the player put it. The script holds no IDs: matching is by structure.
 *
 * <ul>
 * <li>Functions match by name, then, among the rest, by kind and order (a renamed function); parameters likewise.</li>
 * <li>A block's statements match by the longest common run of identical text, then a statement found unchanged
 * elsewhere in the same block (moved), then, between matched ones, statements of the same kind in order (edited).</li>
 * <li>An expression matches whole where its text is unchanged, including inside a new wrapper or out of a removed
 * one; otherwise a node of the same shape keeps its ID and its children are matched in turn.</li>
 * </ul>
 *
 * Unmatched nodes get fresh IDs that the earlier program never used. Known limit: a statement moved into another
 * block is new there.
 */
public final class Reconciler {
    /** Edited ID to earlier ID. */
    private final Map<String, String> matched = new HashMap<>();

    private Reconciler() {
    }

    public static Program reconcile(Program before, Program after) {
        var reconciler = new Reconciler();
        reconciler.functions(before.functions(), after.functions());
        var used = new HashSet<String>();
        Walk.nodes(before, (id, label) -> used.add(id));
        var fresh = new HashMap<String, String>();
        var ids = new Ids("n");
        return Walk.rewrite(after, id -> {
            var earlier = reconciler.matched.get(id);
            if (earlier != null) return earlier;
            return fresh.computeIfAbsent(id, unused -> {
                String next;
                do next = ids.next(); while (used.contains(next));
                return next;
            });
        });
    }

    private void functions(List<FunctionDef> before, List<FunctionDef> after) {
        var left = new ArrayList<>(before);
        var unmatched = new ArrayList<FunctionDef>();
        for (var function : after) {
            var same = left.stream().filter(f -> f.name().equals(function.name())).findFirst();
            if (same.isPresent()) {
                left.remove(same.get());
                function(same.get(), function);
            } else {
                unmatched.add(function);
            }
        }
        for (var function : unmatched) {
            var same = left.stream().filter(f -> f.kind() == function.kind()).findFirst();
            if (same.isPresent()) {
                left.remove(same.get());
                function(same.get(), function);
            }
        }
    }

    private void function(FunctionDef before, FunctionDef after) {
        matched.put(after.id(), before.id());
        var left = new ArrayList<>(before.params());
        var unmatched = new ArrayList<FunctionDef.Param>();
        for (var param : after.params()) {
            var same = left.stream().filter(p -> p.name().equals(param.name())).findFirst();
            if (same.isPresent()) {
                left.remove(same.get());
                matched.put(param.id(), same.get().id());
            } else {
                unmatched.add(param);
            }
        }
        for (int i = 0; i < Math.min(unmatched.size(), left.size()); i++) matched.put(unmatched.get(i).id(), left.get(i).id());
        block(before.body(), after.body());
    }

    private void block(List<Stmt> before, List<Stmt> after) {
        var oldText = before.stream().map(ScriptPrinter::statement).toList();
        var newText = after.stream().map(ScriptPrinter::statement).toList();
        // Longest common subsequence of identical statements: the anchors.
        int n = before.size(), m = after.size();
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = oldText.get(i).equals(newText.get(j)) ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        var oldTaken = new boolean[n];
        var newTaken = new boolean[m];
        var anchors = new ArrayList<int[]>();
        for (int i = 0, j = 0; i < n && j < m; ) {
            if (oldText.get(i).equals(newText.get(j))) {
                anchors.add(new int[] {i, j});
                oldTaken[i] = newTaken[j] = true;
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                i++;
            } else {
                j++;
            }
        }
        for (var pair : anchors) statement(before.get(pair[0]), after.get(pair[1]));
        // A statement found unchanged elsewhere in the block was moved.
        for (int j = 0; j < m; j++) {
            if (newTaken[j]) continue;
            for (int i = 0; i < n; i++) {
                if (!oldTaken[i] && oldText.get(i).equals(newText.get(j))) {
                    oldTaken[i] = newTaken[j] = true;
                    statement(before.get(i), after.get(j));
                    break;
                }
            }
        }
        // Between anchors, the remaining statements of the same kind were edited, in order.
        anchors.add(new int[] {n, m});
        int fromOld = 0, fromNew = 0;
        for (var anchor : anchors) {
            var olds = new ArrayList<Integer>();
            for (int i = fromOld; i < anchor[0]; i++) if (!oldTaken[i]) olds.add(i);
            for (int j = fromNew; j < anchor[1]; j++) {
                if (newTaken[j]) continue;
                for (var iterator = olds.iterator(); iterator.hasNext(); ) {
                    int i = iterator.next();
                    if (before.get(i).getClass() == after.get(j).getClass()) {
                        iterator.remove();
                        oldTaken[i] = newTaken[j] = true;
                        statement(before.get(i), after.get(j));
                        break;
                    }
                }
            }
            fromOld = anchor[0] + 1;
            fromNew = anchor[1] + 1;
        }
    }

    private void statement(Stmt before, Stmt after) {
        matched.put(after.id(), before.id());
        switch (after) {
            case Stmt.Let let -> expr(((Stmt.Let) before).value(), let.value());
            case Stmt.Do call -> expr(((Stmt.Do) before).call(), call.call());
            case Stmt.Return ret -> {
                var old = ((Stmt.Return) before).value();
                if (old != null && ret.value() != null) expr(old, ret.value());
            }
            case Stmt.If branch -> {
                var old = (Stmt.If) before;
                for (int i = 0; i < Math.min(old.branches().size(), branch.branches().size()); i++) {
                    var b = branch.branches().get(i);
                    var o = old.branches().get(i);
                    matched.put(b.id(), o.id());
                    expr(o.condition(), b.condition());
                    block(o.body(), b.body());
                }
                block(old.otherwise(), branch.otherwise());
            }
        }
    }

    private void expr(Expr before, Expr after) {
        var oldText = ScriptPrinter.expr(before);
        if (oldText.equals(ScriptPrinter.expr(after))) {
            same(before, after);
            return;
        }
        // Wrapped: the earlier expression is now inside a new one.
        var inside = find(after, oldText);
        if (inside != null) {
            same(before, inside);
            return;
        }
        // Unwrapped: the expression is what was inside an earlier one.
        var outer = find(before, ScriptPrinter.expr(after));
        if (outer != null) {
            same(outer, after);
            return;
        }
        if (before.getClass() != after.getClass()) return;
        if (before instanceof Expr.Binary b && after instanceof Expr.Binary a && b.op() != a.op()
                && b.op().comparison() != a.op().comparison()) {
            // A different kind of operator is a different node, though its operands may be the same.
            matchChildren(before, after);
            return;
        }
        matched.put(after.id(), before.id());
        matchChildren(before, after);
    }

    private void matchChildren(Expr before, Expr after) {
        var old = Walk.children(before);
        var now = Walk.children(after);
        for (int i = 0; i < Math.min(old.size(), now.size()); i++) expr(old.get(i), now.get(i));
    }

    /** The first expression strictly inside {@code expr} that prints as {@code text}. */
    private static Expr find(Expr expr, String text) {
        for (var child : Walk.children(expr)) {
            if (ScriptPrinter.expr(child).equals(text)) return child;
            var deeper = find(child, text);
            if (deeper != null) return deeper;
        }
        return null;
    }

    /** Two expressions that print alike: every node keeps its ID. */
    private void same(Expr before, Expr after) {
        matched.put(after.id(), before.id());
        var old = Walk.children(before);
        var now = Walk.children(after);
        for (int i = 0; i < now.size(); i++) same(old.get(i), now.get(i));
    }
}
