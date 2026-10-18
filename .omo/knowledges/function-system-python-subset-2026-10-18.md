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
  UnboundLocalError text. Keep the two passes separate: types in source order, paths in `Flow`. A `break` hands its
  assigned set to its loop, so `while True: x = ...; if ...: break` then reading `x` is accepted.
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

## B4: execution

- **Values:** None = `null`, int = `Long` (64-bit; overflow is an error), float = `Double`, str = `String`, bool =
  `Boolean`, list = `ListValue` (shared, mutable), record = `RecordValue` (type named `file.Class`, so the same class
  reached from two files is one type; fields in declared order: build them with a `LinkedHashMap`, `Map.of` makes the
  repr order random), range = `RangeValue`, Block = the host's own object.
- **Semantics live in `Ops` only;** the checker's constant folding calls `Ops.binary`, so compile-time and run-time
  results cannot drift.
- **Float repr:** Java's `Double.toString` digits are shortest except that it never prints one digit (`4.9E-324`
  for Python's `5e-324`); `Ops.floatRepr` tries the one-digit candidates. Checked against 19,995 random doubles.
- **Sort:** CPython compares `later < earlier`; a mixed list must fail with CPython's operand order. Lists of one
  kind use Java's stable sort (strings with `compareTo` when no surrogate is present, since UTF-16 order differs from
  code point order only at surrogates).
- **Compiler (ANF):** `liftAll` materializes every operand left of the last suspending one, unless it is a literal or
  a local (only statements change locals). Assignment: value before the target's parts. Augmented assignment: parts
  and current value read before the right side. `and`/`or` with a suspending right side become a branch.
- **Differential tests:** expected values are generated by CPython once and stored in the tests (the build needs no
  Python). For programs, CPython runs the same source with stub `ae2`/`ae2f`/`mc` modules that log each call with all
  arguments; classes get `dataclasses.dataclass` applied after `exec`, and `Item` etc. are bound to `str`. Compare
  the log, not only the result: only the log shows evaluation order of library calls.
- **Mutation checks done:** no materialization fails 2 programs, no short-circuit branch 2, target before value 2,
  augmented read after the call 1. A mutation run also exposed the `Map.of` field-order flake above.
- **Timing (test JVM, slow):** sorting 10,000 strings about 5 ms, 100,000 about 60 ms; plain Java is close.

## Completion of members (after B4)

- After a `.`, `Completion.receiver` walks back over the tokens of a postfix chain (names, strings, `(...)`/`[...]`
  groups applied to what precedes them, joined by dots; Python keywords stop it), the text is reparsed as
  `completion_value = <text>` and typed by `infer`. A single non-variable name keeps the namespace/file path.
- `Checker.substitute` and the method tables are shared with completion so signatures shown match what is checked.


## B5: canvas and graph edits

- `Canvas.of(function, result)` projects one function; `toFunction()` inverts it exactly (tested on every parser
  and runner sample). Elif arms are numbered (`condition1`, `arm1`); whether an `if` has an `else` is read from
  `armNotes` (elif notes, then the else's), not from an exec link, because an emptied `else` has no link.
- A call's receiver is a namespace or file (node title, no port) when the name is no variable of the function
  (`Canvas.locals`: parameters, assigned names, loop variables). Using checker types for this broke on
  unchecked programs and on nodes added by edits.
- `Edits` work on a mutable `Draft`, then `done()` turns it into a function, prints it, parses it again and
  re-projects. That guard makes several explicit checks redundant (assignment targets, augmented comparison
  operators, argument renumbering); mutation runs showed them surviving, so they were removed. Keep checks only
  where the guard cannot see the problem: names (`Edits.isName` parses `name = 0`), one-line comments, cycles,
  exec outputs, ID collisions.
- New IDs: `new Ids("n", parseResult.positions().keySet())` continues after the highest; every parsed ID is
  in `positions`.
- Mutation script for edits: scratchpad `mut/edits.py` pattern (exact-string replacements, run one test class,
  restore the file).

## B5: layout board from real canvases

- The layout board (design canvas row "图的自动布局") was generated, not hand-placed: a scratch Java `DumpCanvas`
  (compiled against `neoforge-1.21.1/build/classes/java/main`; the function package needs no game classes) checks
  sample scripts with a board-only stand-in catalog and prints each `Canvas` as JSON; a stdlib Python script lays
  out both conventions and writes static `.dc.html` matching the `AutoGraph` board's node style (VT323 10 px ≈ 4.2
  GUI px per character, header 11 px, rows 12 px, scale 1.75).
- Headless Chrome `--headless=new` cuts about 90 px off the bottom of `--window-size`; add that much when rendering
  a board to check it.
- Problems the renders caught: exec wires of several arms stacked on one vertical line (A: start arms further right;
  B: one lane per output, routed right of every node in between), the variables strip wider than the graph.
- `Layout` (Java) follows the board generator's layout A step for step; tests use a stand-in `Metrics`
  (`LayoutTest.METRICS`) with sizes varying by rows and kind. Nothing is placed left of or above the entry card, so
  no normalisation is needed (a mutation run showed it was dead code).

## B6: program files

- `Library` keeps texts, never Programs: every parse is from the stored text, so IDs match a fresh parse of it.
  `splice` relies on that (an edited definition is found by its ID in a parse of the current text).
- Calls are located for rewriting by AST + lexer tokens: a qualified call's position is its receiver's token (the
  parser records `file.fn(` at `file`), so the function name is two tokens later; a bare call's position is its
  name; `def name` is one token after `def`. Replacements are applied last first, by offset.
- Line starts for offsets must break lines as the lexer does (CRLF as one break, lone CR as a break).
- A bug the mutation tests found: comparing a `FunctionDef` from one parse with one from another parse with `==`
  is always false; compare names (or the records with `equals`).

## Standard library round 4

- `Interface` is a subtype of `Block` in `Types.assignable` (one direction only): `mc.redstone(port)` reads what
  the interface receives, `mc.redstone(ae2f.select(port))` the block in front. The host gets the interface's
  ID/alias string where a `Block` is expected and does nothing (False/0) where the interface means nothing.
- `Optional[...]` in a signature is not a `Completion.TYPES` name; the checker and
  `StandardLibraryTest.everyTypeInASignatureIsKnown` accept it as a wrapper, like `list`.
