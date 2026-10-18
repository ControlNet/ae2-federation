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
