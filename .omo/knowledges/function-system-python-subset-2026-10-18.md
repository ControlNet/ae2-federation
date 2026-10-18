# Function system: Python-subset parser (B1)

Date: 2026-10-18. Branch `feature/function-system`. Code: `common/src/main/java/space/controlnet/ae2federation/function/`
(`Lexer`, `ScriptParser`, `ScriptPrinter`, IR records). Tests: `ScriptTest`. The supported subset and the list of
rejected constructs are in `docs/ideas/function-system.md`, "The subset as built".

## How it works, and why

- **Comments ride the token stream.** Whole-line comments are buffered by the lexer and emitted after the next real
  line's INDENT/DEDENT tokens, so in the parser they always sit right before the statement (or `elif`/`else`/`def`)
  they belong to. The parser keeps one `pending` list; whatever takes it first owns the comments. No special cases
  for comments at block ends: they move to the next statement.
- **Brackets suppress NEWLINE and indentation** (Python's implicit line joining); comments inside brackets are an
  error rather than silently dropped.
- **Error recovery** (`ScriptParser.recover`): a statement or function that fails is skipped. The rule: if the failure
  came mid-line, skip to the end of the line; if it came at the start of a later line (for example "expected an
  indented block"), skip nothing there; then skip an INDENT block that follows (the failed header's body). A failure
  at an unexpected INDENT skips that whole block. Errors are sorted and only the first per line is kept, so lexer
  errors (which also leave an ERROR token) do not get a parser follow-up.
- **Lexer errors never stop lexing;** a broken token becomes ERROR, which the parser turns into a failure with the
  lexer's message.
- **IDs and positions:** every node gets an ID from `Ids("n")`; `ParseResult.positions` maps IDs to line/column, so
  the IR stays free of source positions (graph-made programs have none).
- **Printing is canonical**; the script text, not the printed form, is what is saved.

## Testing tips

- `ScriptTest.pythonAcceptsTheSamplesAndTheRejectedConstructs` pipes each sample and each rejected construct to
  `python3 -I -c "import ast, sys; ast.parse(sys.stdin.read())"` (stdlib only, skipped when `python3` is missing). It
  keeps "valid Python syntax" honest and makes sure every "not supported" example is real Python.
- Java text blocks: write a docstring's quotes as `\"""`, and double every backslash meant for the script.
- Run: `timeout 1200 ./gradlew test --tests '*function.*' -q` in the worktree, then read
  `neoforge-1.21.1/build/test-results/test/TEST-*function*.xml` (quiet mode prints nothing on success).

## Completion (B2)

- `Completion.complete(source, line, column, workspace)`. The cursor's logical line is read from
  `Lexer.lexPartial(text before the cursor)`: no end-of-input tokens, so the open line, an open bracket and an
  unclosed string (an ERROR token with `Lexer.UNCLOSED_STRING`) are seen as the cursor sees them.
- Scope comes from parsing the whole file with the cursor's logical line replaced by `pass` at its indentation: the
  half-typed line can never break the parse of what surrounds it. Variables are those assigned in statements that
  start before that line (params first); types come from annotations or a small inference over literals, calls and
  operators. B3's checker replaces that inference.
- The expected type comes from the innermost open `(` (callee signature, argument index by commas at depth 0), an
  annotated assignment, assignment to a typed variable, or `return`. Inside a string with an expected type, the
  catalogue supplies values (items, interface aliases).
- Mutation check done: with the lexer's bracket rule disabled, only the parser test fails; completion stays right
  because of the `pass` substitution.
- Never-closed brackets: `Lexer.lex` first lexes exactly as Python; only if a bracket is still open at the end does
  it lex again with recovery from that bracket's offset (`recoverFrom`), ending it at the next line indented no
  further. Valid Python is never affected (user: syntax must follow Python). Message as CPython's: "'(' was never
  closed".

## B1 follow-up: Python additions

- **One call shape.** `ae2.craft(x)`, `iron_tools.refill(x)` and `xs.append(x)` all look like `NAME . NAME (`, so the
  parser cannot tell a namespace from a variable. `Expr.Call` carries a receiver expression; completion (and later the
  checker) resolves it, a local variable shadowing a namespace or file of the same name, as in Python.
- **Postfix loop.** `postfix()` takes an atom and then any mix of `.name`, `.name(...)`, `[i]` and `[a:b:c]`; every
  node in a chain takes the position of the chain's first token, as CPython's `col_offset` does. Calling a call's
  result or an item (`f(x)(1)`, `xs[0]()`) stays an error: functions are not values.
- **Printer:** postfix nodes have precedence 8, so an operator operand gets parentheses (`(-x).real`); an int literal
  receiver is always parenthesised, since `1.real` lexes as a float.
- **`not in`** is two tokens; `comparisonOperator()` peeks both and `comparison()` advances twice.
- **Classes** are top-level `Definition`s beside functions (`Program.definitions()`, with `functions()`/`classes()`
  views), so files with both round-trip in order. A class body allows a docstring, `name: type [= default]` and
  `pass`; comments above a `pass` move on to the next field. A required field after one with a default gets the
  dataclass message ("non-default argument 'y' follows default argument"). Type hints may be dotted
  (`iron_tools.Order`); `TypeRef.name` then holds the dotted text.
- **Completion and classes:** `enclosing()` returns no function when the last definition before the cursor is a
  class; classes are offered as types and constructors (`Signature.of(ClassDef)`), and a field read takes the
  field's type.
- **CPython check uses `compile()`, not `ast.parse()`:** some errors (a repeated keyword argument) are raised only by
  the compiler. `pythonSyntaxErrors()` lists mistakes CPython rejects too, and the test asserts CPython's message
  matches ours.
- **Java text blocks inside a Python generator script:** writing `\"""` from a Python `'''...'''` string drops the
  backslash; write `\\"""` there.

## B3: checker

- **Shape.** `Checker.check(program, workspace)` returns problems keyed by node ID (map to lines through
  `ParseResult.positions()` only in the script view), a type per expression ID, and the effect sets. `null` as a type
  means "already reported": it fits everywhere, so one mistake gives one problem.
- **Locals are Python's:** a name assigned anywhere in a function is local to all of it (`collectAssigned`). A read
  before its first assignment in source order gets no type in the type pass; `Flow` reports it with CPython's
  UnboundLocalError text. Keep the two passes separate: types in source order, paths in `Flow`.
- **Other files' types are qualified** when a signature or field is read from them (`qualify`): `Order` in
  `iron_tools` becomes `iron_tools.Order` here, so it never clashes with a local `Order`.
- **Constants** live in a map by node ID filled as `expr()` returns, so folding is linear and each zero division is
  reported once. Typed strings are validated where the expected type is known, which covers arguments, annotated
  assignments, returns, list items, defaults and constructor fields with one check in `expr()`.
- **CPython's messages** were taken from `python3 -I -c` (runtime TypeErrors, ZeroDivisionError, UnboundLocalError);
  `max` says `'>'`, `min` says `'<'`; only the string-index message quotes the type.
- **Mutation checks done:** dropping the Item-to-Resource rule fails 2 tests, an `if` without `else` treated as
  never falling through fails 4, skipping typed-string validation fails 8.
- **Cost:** 1,020 lines parse in about 3.6 ms and check in about 2 ms (warm JVM, test machine), so run the core off
  the render thread.
