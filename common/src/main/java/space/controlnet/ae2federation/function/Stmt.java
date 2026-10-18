package space.controlnet.ae2federation.function;

import java.util.List;

/**
 * A statement. Statements run in order; in the graph they are joined by execution wires, while expressions are pure
 * data flow. {@code comment} is the player's note above the statement, shown in both views; empty for none.
 */
public sealed interface Stmt {
    String id();

    String comment();

    /** {@code local name = value}. */
    record Let(String id, String comment, String name, Expr value) implements Stmt {
    }

    /** {@code if ... elseif ... else ... end}; {@code otherwise} is empty when there is no else. */
    record If(String id, String comment, List<Branch> branches, List<Stmt> otherwise) implements Stmt {
        public If {
            branches = List.copyOf(branches);
            otherwise = List.copyOf(otherwise);
            if (branches.isEmpty()) throw new IllegalArgumentException("An if needs a condition");
        }
    }

    /** One condition of an {@link If} and what runs when it is the first that holds. */
    record Branch(String id, Expr condition, List<Stmt> body) {
        public Branch {
            body = List.copyOf(body);
        }
    }

    /** {@code return value}; an action returns without a value. */
    record Return(String id, String comment, Expr value) implements Stmt {
    }

    /** A call made for its effect, as an action's {@code redstone(15)}. */
    record Do(String id, String comment, Expr.Call call) implements Stmt {
    }
}
