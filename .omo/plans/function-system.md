# Function system: Phase 0 design spike

Date: 2026-10-18. Branch `feature/function-system` (own worktree; owner's request: a large new feature starts apart
from `dev`). Status: Phase 0 done as a spike. Nothing here is wired into the game; every choice below is an assistant
proposal until the owner accepts it, except where marked (owner).

Design sources: [custom policies](../../docs/ideas/custom-policies.md),
[rule-based automation](../../docs/ideas/rule-based-automation.md),
[scope clarification](../knowledges/policy-automation-scope-2026-10-10.md),
[Videnoa reference](../knowledges/videnoa-function-system-reference-2026-10-10.md).

Settled before this plan (owner): one general function system, edited in the automation GUI; Policy customisation is
a specialised use (predicates, filters); redstone output is an automation action; the computing device's role
(2026-10-17); the computing limit is enforced by default and can be turned off.

## Phase 0 scope

A design spike with no game code: the program representation (IR) that both a graph and a script show, the
query/action split, when policies evaluate, and proof by unit tests that graph and script round-trip. Code lives in
`common/.../function/`, plain Java (a test fails on any Minecraft, NeoForge or AE2 import). No GUI, no device, no
network wiring.

## What the spike proved

| Claim | Test |
|---|---|
| Script -> IR -> script is the identity on printed text; precedence, negatives, literals, syntax errors with lines | `ScriptTest` |
| IR -> graph -> IR is the identity, IDs included; statements chain by exec wires, literals sit on ports | `GraphTest` |
| A script edit keeps the IDs of unchanged nodes (rename, insert, literal or operator change, wrap, delete, reorder, function or parameter rename), so graph positions and comments survive editing in the other view | `ReconcilerTest`, `GraphTest.layoutSurvivesAScriptEdit` |
| Static checks: a query cannot call an action (built-in or the player's, at any depth); no recursion, also through other functions; types, arity, unknown names, block scope; an action returns nothing; a query returns on every path; each problem names its node | `CheckerTest` |
| A query runs with observations only and cannot act; an action acts and may call queries; unavailable is neither false nor 0; a run stops at its budget and keeps effects already made | `EvaluatorTest` |
| A compute measure from node counts bounds every run's steps, whatever the world | `EvaluatorTest.theComputeMeasureIsEnoughForAnyWorld` |

`./gradlew test --tests '*function.*'` from the repo root: 32 tests. Test-only stand-ins: `EvaluatorTest`'s
`FixedObservations` and `RecordedEffects` (fixed answers and recorded calls in place of a real network).

## IR

- `Program` = functions. `FunctionDef(id, comment, kind, name, params, result, body)`.
- Statements: `local`, `if/elseif/else`, `return`, call statement (`Do`). Expressions: literal, variable, unary,
  binary, call. No loops and no recursion, so every run ends and its length is bounded by the program's size.
- Every node has a stable ID; the script holds none (see identity below).
- Types: `bool`, `number`, `text`, `resource` (key type + ID, `@ns:path` for items, `@[fluid]ns:path` otherwise, so
  addon key types work without a whitelist), `filter`, `nothing` (an action's result).
- `Value.Filter` modes ALL / ALLOW_LIST / DENY_LIST match `PolicyFilterMode`; resources map to `PolicyResource`
  through the key type registry. The adapter belongs to Phase 1.

## Query/action contract

Superseded (user, 2026-10-18): functions are generic and static analysis infers their capabilities; see the
[idea page](../../docs/ideas/function-system.md). The spike's split below stays as a record of what was tested.

- A **query** reads only. Policies call queries, as often as they need. It returns a value.
- An **action** may also act (redstone, crafting requests, later moving resources) and returns nothing.
- Enforced twice: the Checker rejects a query that calls an action, and at run time a query never receives `Effects`
  (an action calling a query does not pass its effects on), so purity holds by construction, not by trust.
- `unavailable` is a value: an observation that could not be made (unloaded network, no answer). Kleene logic for
  `and`/`or`; arithmetic and comparisons give unavailable; `available(x)` tests for it. Where the program must decide
  on it (a condition, an effect's argument) it cannot go on: a query answers unavailable, an action stops there.
- What a policy does with an unavailable answer is open (proposal: keep the last known answer, as an unloaded beam
  position keeps its last result).

## Evaluation timing (proposal)

- Policies never run functions inside AE2's hot paths (`isAllowed` on every storage access). A predicate's or
  filter's answer is cached per (function, arguments, network) and recomputed when an input it read may have changed:
  sampled on a fixed server-tick period at first, event-driven later (AE2 grid events, as the storage authority
  already does).
- When a cached answer changes, `AuthorityEpoch.advance()` runs, so the existing epoch fast path invalidates exactly
  as a GUI policy change does today.
- Actions run on triggers (redstone edge, schedule, manual), on the server tick, within the computing device's
  budget. Trigger semantics stay with the automation phase.

## Compute

`Cost.compute` = the function's node count plus, at each call site of another function, that function's measure. It
bounds the steps of any run, so a device can admit a function once, statically, instead of metering every tick. How
the measure becomes computing units, and the device's capacity, are not designed here.

## Two views, one program

- **Script view**: a small Lua-like language, proposal (not chosen: Q2). Comments only on their own line above a
  statement or function, so they belong to a node and show in the graph too. `not` binds looser than comparisons.
- **Graph view** (Videnoa-inspired): nodes plus wires named by ports; positions apart, by node ID (`Layout`); literals
  inline on ports. Unlike Videnoa, statements need order: exec wires (`exec` in, `next` out; `if` has `then0..`,
  `else`, `next`). A value output feeds one port; a value used twice goes through a local (the graph says so).
- **Identity**: the graph keeps IDs; a script edit reparses to fresh IDs and `Reconciler` carries old IDs over by
  structure (functions by name then kind/order; statements by LCS of identical text, then moved within the block, then
  same kind in gaps; expressions by text, wrap/unwrap, then same shape). Unmatched nodes get new IDs and default
  positions next to their neighbours.
  - Trade-off: no IDs in the script keeps it clean to type and paste. The cost is heuristics.
  - Known limit (tested): a statement moved into another block gets new IDs, so its graph position resets.
  - Rejected alternative: IDs as hidden comments in the script. Exact, but copy/paste duplicates them and editing
    them by hand breaks the graph.

## Videnoa lessons

Adopted: port-named wires (`from_node/from_port -> to_node/to_port`); positions kept beside the workflow by node ID
with a default; parameters inline on the node instead of separate constant nodes; validation before run (types,
cycles). Planned for Phase 1: node descriptors (display name, category, icon, accent colour, typed ports with
defaults, UI hints, enum options) generated from `Library` and the player's functions, feeding both the graph palette
and script completion. Not applicable: Videnoa is pure data flow with no control flow, no script view and no
query/action split; those parts are this design's own.

## Decisions since Phase 0

The design moved on in discussion on 2026-10-18; the [idea page](../../docs/ideas/function-system.md) is the record.
In short (user decisions): a Python subset (valid Python syntax, `int`/`float`, `for`/`while`), full type hints on
every function, resources as typed strings, generic functions with capabilities inferred, effects called in the body
with one effectful call per tick, standard namespaces `ae2`/`ae2f`/`mc` without imports, files of several functions
calling each other by `file.function`, programs stored server-wide as plain-text `.ae2f` files in the world save,
policies changed by automation through `ae2f.set_policy`, no entry decorators, a script with syntax errors does not
switch to the graph, variables in the graph as chips on ports (option A), silent automatic saving from the client.
The Phase 0 sections above describe the superseded prototype.

## Bottom layer plan (next)

Pure Java in `common/.../function/`, no game types (the boundary test stays), test first. The Phase 0 prototype code
is replaced step by step; what it proved (round-trip, identity reconciliation, budgeted interpreter) is reused.

| Step | Content | Tests prove |
|---|---|---|
| B1 (done) | Python-subset lexer (indentation, comments), parser and printer to a new IR: `def` with typed parameters and result, docstrings, own-line comments, assignment with optional annotation, `if/elif/else`, `while`, `for x in xs`, `break`, `continue`, `return`, call statements; literals (`int`, `float`, `str`, `True`/`False`, `None`, lists), names, `ns.fn` and `file.fn` calls, unary `not`/`-`, `+ - * / // %`, comparisons, `and`/`or`; anything else is a clear "not supported" error | Script -> IR -> script is the identity; every unsupported construct names itself and its line |
| B2 (done) | Error-tolerant parsing and completion context: the cursor's scope, expected type and candidates | Half-typed lines still give the right candidates |
| B3 | Types and checks: `int`/`float` widening, `str`, `bool`, `None`, `Item`/`Fluid`/`Resource` from string literals in typed places (registry catalogue as an interface), `list[T]`, definite assignment, calls across files; capability inference over the call graph | One test per rule, problems point at nodes |
| B4 | Compile each function to an execution graph (exec nodes joined by `next`; every effect and player-function call its own node, in Python's order; operators and built-in reads as data inputs) and run it with a frame stack: one node per step, pause = keep the current node, budget = nodes run, recursion with a depth limit (user, 2026-10-18) | At most one effect per tick; budget carries over; `while True` cannot freeze; arithmetic matches CPython; node values map to program nodes for the dry run |
| B5 | Graph view and full graph editing on the program tree (every language feature editable, for players who do not code): exec chain, variable chips, `x = call()` merged nodes, automatic layout (no stored positions, no reconciler; user 2026-10-18) | IR -> graph -> IR identity; every edit operation round-trips to script; layout is deterministic |
| B6 | Built-in descriptors (`ae2`, `ae2f`, `mc`; capabilities, docs, examples) and `.ae2f` storage (text only; references by name) | Files round-trip; a broken cross-file name is a check error |

B1 done 2026-10-18: `Lexer`, `ScriptParser`, `ScriptPrinter` and the IR (`Program`, `FunctionDef`, `Stmt`, `Expr`,
...) in `common/.../function/`; `ScriptTest` (71 cases with the parameterised ones). The Phase 0 prototype classes
were removed first (7496a7d0); git history keeps them for B3-B5 to reuse.

B2 done 2026-10-18: `Completion` with `Catalog` (the game's registries and namespaces, an interface), `Workspace`
(the other files), `Signature`, `Candidate`, `Completions`; for a bracket that is never closed (an error in Python
too), the lexer resumes at the next line not indented further; valid Python is lexed exactly as Python does. `CompletionTest` (12) uses a test-only catalogue with made-up built-ins, since the standard
library is still being designed.

B1 follow-up (decided 2026-10-18, before B3): keyword arguments in every call; list and string methods with
indexing, slices, `in`/`not in` and chained method calls; read-only fields on built-in values; default
values in `def`s as in Python; player records (plain fields-only `class`, no decorator, no methods or inheritance). Test against CPython as B1 does.
Done 2026-10-18 (parser, printer and completion; no checking yet, that is B3): keyword arguments and defaults;
`Expr.Call` takes a receiver expression instead of a qualifier name, plus `Expr.Attribute`, `Index`, `Slice`;
assignment targets are expressions; `BinaryOp.IN`/`NOT_IN`/`IS`/`IS_NOT`; `Program.definitions()` holds `FunctionDef` and
`ClassDef` (with `Field`s) in file order. Method tables and field checks wait for B3's catalogue.

B3 done 2026-10-18: `StandardLibrary` (the settled `ae2`/`ae2f`/`mc` signatures with effect flags and integer
ranges, typed strings, closed sets, `Stack`, `Block`); `Catalog` reduced to host facts (`values`, `kinds`, `exists`);
`Checker` (names, types, Python call binding, fields, items, slices, operators, built-ins, list/str methods, returns,
definitions; problems by node ID; a type per expression), `Flow` (unbound locals, missing returns), constant folding
and typed-string validation, `Effects` (effect inference across files). Rules are listed in the idea page, "Checking
rules as built". Next: B4, compile to the execution graph and run it with a frame stack.

B4 done 2026-10-18: runtime values and `Ops` (CPython 3.12 semantics and messages; 64-bit ints; lists and strings
capped at 100,000 items), `Evaluator` (expressions without suspension points; library reads through a `Host`),
`Compiler` (one `Graph` of `Node`s per function; player calls and library actions lifted in Python's order),
`Runner` (own frames, recursion limit 1000, one budget step per node, one action per tick, `mc.wait`, errors at the
innermost expression, a trace by program node). Checker additions: `range` type, `list()`, `Iterable` parameters,
list ordering, constant-only defaults. Decisions in the idea page, "Execution as built". Next: B5.

B5 done 2026-10-18: `Canvas` (a function projected to nodes, ports and exec links, and back unchanged), `Edits`
(graph edits checked through the printed script; IDs kept, new IDs after the file's highest; only the edited
definition changes), `Layout` (layout A picked by the user on the design board "图的自动布局": exec line left to right,
values below-left, bodies below-right; sizes from the GUI's `Metrics`). Renaming a function's callers waits for B6
storage. Next: B6.

B6 storage done 2026-10-18: `Library` (`.ae2f` files read and written as text; atomic writes; byte-for-byte round
trip; edited definitions spliced in place; checks against the other files; text-preserving renames of functions and
files; delete refused while called). A broken cross-file name was already a check error (B3), now tested for a
missing file too. Descriptors done 2026-10-18 after the standard library rounds closed: a checked example per
library function; kinds of access dropped by the user (acting or not stays the only distinction).

Next (user, 2026-10-18: the host and the editor screens both): assistant phasing, a vertical slice first.

| Step | Content |
|---|---|
| H1 (done) | ME Federation Automation Interface block: acts in front, cable on the other faces, random `interface-xxxxxx` ID, alias GUI (ID + copy), wrench turns / sneak-wrench picks up; joins the domain; temporary textures |
| H2 (done) | Programs in the world save (`<world>/ae2federation/programs/*.ae2f` through `Library`), reload command; server `Catalog` (registries, domain interfaces and networks, `network-xxxxxx` IDs) |
| H3 (done) | `Evaluator.Host` for the 30 library functions through AE2's strategies and NeoForge capabilities, outside `function/`; GameTests through a dev-only runner |
| H4 (done) | Creative Computing Device (placeholder, unlimited); installations in `SavedData`; scheduler with the step budget; triggers, events, rate limit and overlap modes; Push |
| H5 (done) | Packets: library index, chunked upload, last write wins, install/uninstall/push |
| G0 (done) | Render the design row "自动化页签" and check it covers the Edit view controls; add boards for what is missing and get the user's nod |
| H6 (done) | Index follow-ups for the tab (user, 2026-10-19): interfaces in the index, device aliases (screen like the interface's), devices filtered by domain, an `args` request that keeps the installation |
| G1a (done) | Automation tab shell (assistant phasing, 2026-10-19): fourth rail tab, program list with functions and problem counts from the index, file tabs, the script shown read-only in LDLib2's `CodeEditor` with Python colouring from our lexer and a line-number gutter; subscribe on open, unsubscribe on close; UI scenario screenshots against `AutoScript`. Controls that would do nothing yet come with their steps: New and Push with G1b, Library with G1d, the Edit/Install switch with G1e, the Script/Graph switch with G1f |
| G1b | Editing: local copy, automatic chunked upload after a pause (no save button, no sync indicator), New program, problems checked on a client worker (gutter mark, underline, problem list, count), Push with the answer on the status line (`AutoScriptErrors`); Tab inserts 4 spaces, `#` comment toggle, indent after `:` |
| G1c | Completion from the B2 engine with item icons (`AutoScript`, `AutoScriptItem`) |
| G1d | Library panel: namespaces, docs with the checked example, search (`AutoLibraryDoc`) |
| G1e | Install view and the Edit/Install switch: programs and this domain's devices, wires to install, aside with arguments, trigger, interval, overlap, log, Run now, Uninstall, Open in editor; installed functions marked in the program list (`AutoInstall*`) |
| G1f | Graph view and the Script/Graph switch (Graph disabled while the script does not parse) from `Canvas`/`Layout`, library drag (`AutoGraph`, `AutoLibraryDrag`) |
| G1g | Outside editors: files of the open window synced both ways |
| G1+ | Automation tab: Edit view (program list, file tabs, script editor with completion, errors, item icons), Graph view from `Canvas`/`Layout`, library panel with the examples, Install view |

Decided (user, 2026-10-18): programs are global with no permissions for now; installations run the version last
pushed with a Push button at the top right of the edit view; Edit and Install are two switched pages; a computing
device has one port in the install view and lists its installed functions below.
