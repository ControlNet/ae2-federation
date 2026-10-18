# Function system architecture

Discussion date: 2026-10-18. [Idea index](README.md).
Status: the first batch's scope, the script language, generic functions, server-wide storage and policies changed
by automation are the user's decisions (2026-10-18); the rest is assistant proposal. Phase 0 prototype on branch
`feature/function-system`, see the [plan](../../.omo/plans/function-system.md).

This page holds the architecture shared by [custom policies](custom-policies.md) and
[rule-based automation](rule-based-automation.md): how programs, functions and automation are defined, and which
layer each part of the work belongs to. The two idea pages keep their own product requirements.

## User decision: start from the bottom (2026-10-18)

The first batch leaves world and content interactions aside (policy hookup, the computing device, actions on a real
network) and starts at the lowest layer: editing a program as a script, as a graph, and converting between the two.
Everything above it waits until that layer is solid.

Earlier user decisions this page builds on: one general function system edited in the automation GUI (Policy as a
specialised use of it was later replaced by policies changed by automation, below), interchangeable graph and script
editors, redstone output as an automation action, and the
computing device's role (see the two idea pages).

## Script language (user decisions 2026-10-18)

- **A subset of Python.** The interpreter is not expected to cover all of Python; scripts in the subset should stay
  valid Python syntax, so outside editors can highlight and format them. Resources are plain strings (below), not
  the Phase 0 prototype's `@minecraft:iron_ingot`.
- **Numbers as in Python:** `int` (64-bit, matching AE2 amounts) and `float`; `/` gives a float, `//` an int, and an
  int widens to a float where a float is expected.
- **Loops are in:** `for x in xs` and `while`. Both are easy to draw as graph nodes (a loop node with a body chain and
  a next chain; for-each also gives the current element as a value output).

Consequences of loops (assistant notes, not decisions): a run's length is no longer bounded by the program's size,
so the Phase 0 prototype's static step bound goes; every run needs a step budget at run time. A budget per tick that lets a long run
continue over several ticks, so "wait until stock reaches N" is an ordinary loop, is one option to discuss with the
computing device's CU.

### The subset as built (B1, assistant choices 2026-10-18)

Choices made while writing the parser, where the user decisions left them open:

- **In:** `def` with typed parameters and result; docstrings; `if`/`elif`/`else`, `while`, `for x in xs`, `break`,
  `continue`, `return`, `pass`; assignment, annotated assignment and augmented assignment (`+=`, `-=`, `*=`, `/=`,
  `//=`, `%=`); call statements; `int`, `float`, `str` (escapes `\\ \' \" \n \t \r`), `True`, `False`, `None`, lists;
  calls as `f(...)` or `ns.f(...)`; `not`, unary `-`, `+ - * / // %`, comparisons, `and`, `or`; a line break inside
  brackets, so a long call can wrap.
- **Comments are kept:** a whole-line comment belongs to the next statement, `elif`, `else` or `def`; a comment at the
  end of a line belongs to that line. Both survive a trip through the graph. A comment above a docstring moves
  below it; a comment after a docstring on the same line is an error.
- **Out, each with an error naming it at its line:** imports, classes, decorators, `async`, nested functions,
  default values, `*args`, union and string type hints, `try`, `with`, `global`, `assert`, `raise`, `del`, `yield`,
  `lambda`, `else` after a loop, one-line bodies (`if x: f()`), semicolons, tuples, chained assignment and
  comparisons, conditional expressions, `in` outside `for`, `is`, `**` and bitwise operators, unary `+`,
  comprehensions, dicts and sets, indexing, keyword arguments, attribute access other than `ns.f(...)`, `:=`, `...`,
  adjacent strings, string prefixes (`f"..."`), hexadecimal, `1_000`, complex numbers, other escapes, `\`
  continuation, tabs. Every rejected example is still valid Python (a test checks with CPython's parser), so the
  message says "not supported", never "syntax error". Indexing and keyword arguments are the likeliest to be added.
- **Paradigms left out** (user, 2026-10-18): no exception handling (`try`, `except`, `raise`), no object-oriented
  programming (`class`), no functional programming (functions as values, higher-order functions, `lambda`,
  `map`/`filter`, comprehensions, generators, closures: no nested functions, no `nonlocal`/`global`). Functions are
  only defined and called; a name of a function is not a
  value that can be stored or passed. The language is plain procedural Python.
- **Language additions decided since B1** (user, 2026-10-18), to build next in the parser: keyword arguments,
  natively, in every call (built-ins and the player's functions alike); list and string methods with what they bring
  (indexing and slices, `in`/`not in`, method calls on any value, chained); read-only fields on built-in values
  (proposal); default parameter values in the player's own `def`s. Rule (user, 2026-10-18): standard Python syntax
  and behaviour are adopted as Python has them, without a separate decision each time; defaults, for instance, are
  evaluated once when the file loads, as Python does at `def` time.
- **A missing type hint is a parse error** naming the parameter or function.
- **Syntax follows Python exactly** (user, 2026-10-18: the subset is Python, so its syntax rules are Python's):
  inside brackets indentation does not matter. Only error recovery goes further than Python: for a bracket Python
  reports as never closed, parsing resumes at the next line indented no further than the bracket's line, so the
  rest of the file still parses and completes.
- **Errors do not stop parsing:** a failed statement or function is skipped, the rest parses, and every error is
  listed (one per line). The completion engine can work on the part that parsed.
- **The script text is what is saved.** Printing happens only when the graph changes a program; the printed form is
  canonical: four spaces, one blank line between functions (shorter than PEP 8's two, for the small editor), double
  quotes, parentheses only where needed, an empty body as `pass`. A comment that ended an indented block moves to the
  next line when printed.

## Static types everywhere (user decision 2026-10-18)

Functions are statically typed: every parameter has a type hint and every function a return type hint (`-> None`
when it returns nothing). A missing hint is an error, not an inferred `Any`, so the checker can make sure everything
fits before a program runs. Locals take the type of the value assigned to them; an annotation such as `x: Item = ...`
gives them another one (see below).

## Resources as typed strings (user direction 2026-10-18)

No `item("...")` wrapper and no special symbol: a resource is written as a plain string, and the static type says
what it is. A parameter typed `Item` (a type hint, valid Python) takes `"minecraft:iron_ingot"`; while the player
types that string, completion searches items; the checker turns it into an item. The player only needs to know
that an item goes there.

```python
def keep_iron(min: int) -> bool:
    now = ae2.stock("minecraft:iron_ingot")   # stock's parameter is a resource, so this is one
    return now >= min
```

Details (assistant proposal):

- **The expected type decides.** A string literal in a place that expects `Item`, `Fluid` or `Resource` (any AE2
  key type) is parsed there and checked against the game's registries; an unknown ID is an edit-time error with
  suggestions. The core asks the host for the catalogue of known IDs, so it stays free of game code.
- **Other key types.** In a `Resource` place a bare ID means an item; another type is named with a prefix:
  `"fluid:minecraft:water"`, and for addon types their key type ID or a short name the addon registers. Completion
  inserts the right form, so players rarely type a prefix.
- **Literals only.** A computed `str` (joined at run time) is not turned into an item silently; an explicit
  conversion is needed, so every resource error shows up while editing.
- **Locals** (user, 2026-10-18). `iron = "minecraft:iron_ingot"` alone is a `str`; `iron: Item =
  "minecraft:iron_ingot"` is an item. When a `str` local is passed where an item is expected, the error offers adding
  the annotation; no inference from later use.
- **Lists.** The expected type flows into list elements: a `list[Item]` parameter takes
  `["minecraft:iron_ingot", "minecraft:gold_ingot"]`.
- **Graph.** The port shows the resource's icon and opens a searchable picker instead of a text field.

## Generic functions, capabilities by static analysis (user decision 2026-10-18)

A function is just a piece of program, fully generic: there is no declared query or action kind. Static analysis
works out what a function can do, so the system knows where it may be used. This replaces the Phase 0 prototype's
declared query/action split.

How it could work (assistant proposal):

- **Capabilities on the built-ins.** Each built-in's descriptor states what calling it does, for example: reads the
  network's storage, reads redstone, emits redstone, requests crafting, moves resources, waits (spans ticks). Plain
  arithmetic and the player's own logic add nothing.
- **Inferred per function.** A function's capabilities are the union of those of every built-in it can reach,
  through any call chain, solved over the call graph so recursion is no obstacle. The analysis is conservative: a
  call that sits on a branch that never runs still counts, which keeps it simple and easy to explain.
- **What it is used for.** With policies changed by automation there is one host, so inference no longer gates a
  policy slot. It separates pure from effectful calls in the graph (below), reports whether a function may loop
  without end (`while`, recursion), and gives its I/O for the computing device's bandwidth cost, which the device
  decisions already expect to be inferred from the program. Messages say why and where, e.g. "emits redstone
  (line 12, through `signal`)".
- **Guarantee at run time too.** A run gets only the capabilities its host allows; a preview gets none, so it
  physically cannot act even if the analysis had a bug.
- **Both views show it.** The graph can badge nodes and functions with their capabilities; the script editor can show
  them beside each `def`.

## Standard library (user direction 2026-10-18)

The system ships a set of ready functions players call directly: AE2 ones (stock, craftability, crafting status,
storage, energy and the like) and Minecraft ones (reading and emitting redstone and the like). Lists are not fixed.

Grouped by namespace, already available without imports (user, 2026-10-18): `ae2` for AE2 (`ae2.stock(...)`),
`ae2f` for AE2 Federation (`ae2f.online()`, `ae2f.set_policy(...)`) and `mc` for Minecraft (`mc.redstone(...)`).
Namespaces become the graph palette's categories, and addon mods can add
their own (`mekanism....`) through the same descriptors, as the mod already handles addon resource types without
AE2-only lists.

**Python's built-ins at the top level** (user, 2026-10-18): functions without a namespace are Python's own, with
Python's names and meaning (`str()`, `int()`, `range()` ...); everything about the game stays in `ae2`, `ae2f` and
`mc`. Assistant proposals, accepted by the user (2026-10-18):

- A first set: `int`, `float`, `str`, `bool` (conversions), `range` (1 to 3 arguments), `len`, `abs`, `min`, `max`,
  `sum`, `round`, `any`, `all`. `print` is left out for now (user, 2026-10-18): the graph's dry run shows values at
  each node instead (below). Left out while the subset has no tuples: `enumerate`, `zip`, `dict`;
  `isinstance` and `type` are not needed with static types.
- Results behave exactly as in Python: `int(3.7)` is 3, `round(2.5)` is 2, `-7 // 2` is -4, `-7 % 2` is 1, and a
  failing conversion (`int("abc")`) stops the run with Python's message. B4 can test this against CPython, as B1 tests
  syntax against CPython's parser.
- Typed for the checker: `range(...)` is iterable as `list[int]` but produced lazily, so `range(10**9)` costs nothing
  until used; `min`/`max`/`sum` take a list or two numbers.
- **Dry run in the graph** (user, 2026-10-18): in graph mode the player sees the current dry run of the function,
  with each node showing its result. Assistant proposals: reads see the real world (stock, block contents); effects
  are not performed but simulated where the game can (AE2 storage and NeoForge inventories both have a simulate
  mode, so "would move 64" is a real answer, and AE2 can plan a craft without starting it), so values downstream
  of an effect stay meaningful; the run is not paced by ticks but stops at a step budget; parameters come from an
  installation's arguments or values typed for the dry run. Reads need the server, so the server runs it and sends
  back the value per node, refreshed while the graph is open. Open: whether the script view shows the same values
  inline.
- **List and string methods are needed** (user, 2026-10-18). Assistant proposals:
  - Lists: `append`, `extend`, `insert`, `pop`, `remove`, `index`, `count`, `reverse`, `sort` (no `key=`, no
    lambdas), `clear`, `copy`. Strings: `startswith`, `endswith`, `lower`, `upper`, `strip`, `split`, `join`,
    `replace`, `find`, `count`. Same behaviour as Python, typed (`list[T].append(T) -> None`, `str.split(str) ->
    list[str]`). Changing a list is the program's own state, not an effect: no tick.
  - What they bring with them, all plain Python syntax: indexing `xs[0]` (and slices `s[1:3]`), membership `x in xs`
    and `not in` outside `for`, and calls on any value, chained (`name.strip().lower()`). The parser would gain a
    method call on an expression, replacing today's `qualifier` string for those cases; namespaces and other files
    stay as they are.

### Standard library discussion (started 2026-10-18)

The user chose to settle the standard library before more of the bottom layer. Assistant draft, to discuss in
rounds; nothing here is decided.

**Foundations first** (they shape every function):

1. **Which ME network `ae2` functions act on.** A program runs on a computing device in a domain of several
   networks. Options: (a) the network the device is attached to; (b) a network value chosen like a block,
   `base = ae2f.network("main_base")`, then `ae2.stock(base, item)`; (c) the whole domain at once, as the federation
   already shares storage across it. Possibly (c) by default with (b) to narrow.
2. **Values with fields.** Several results are records: a stack (resource, amount), a crafting job (state,
   progress). Python reads them as `stack.amount`; the subset rejects attribute access today. Proposal: read-only
   fields on built-in values (still Python syntax), no fields on anything the player defines.
3. **Failure.** The subset has no `try`. Proposal: an outcome the world decides (nothing to move, target full, chest
   gone) is a return value (`moved == 0`, `False`); a mistake in the program (an unknown interface, reach 5) stops
   the run with a message in the run log, as a Python exception would.
   Decided (user, 2026-10-18): this split, and every action returns a `bool`, whether it succeeded. No exception
   handling: `try`/`except`/`raise` stay out of the subset (too much for this design). Assistant
   An action with an amount does as much as it can (user, 2026-10-18): `True` if it did anything, `False` if it did
   nothing; partial work is kept. Assistant proposals: the amount actually done is read afterwards (`mc.count`) when
   it matters; `ae2.craft`
   returns `True` once the job has started, and progress is read with reads such as `ae2.crafting(resource) -> int`
   (the amount on its way) instead of a job value.
4. **Where each function lives.** Proposal: `ae2` is what AE2 itself has, on one network or the domain (storage,
   crafting, energy, CPUs); `ae2f` is the Federation's own concepts (selecting blocks through interfaces, networks
   of the domain, policies, statistics); `mc` is the vanilla world at a selected block (inventories, redstone,
   breaking and placing, time, waiting).

**Decided: networks** (user, 2026-10-18). The computing device sits directly on Federation cable and belongs to no
ME network, so it sees the whole domain. Every `ae2` function takes an optional network as its last parameter,
`None` by default, meaning the whole domain; passing a network's ID narrows the call to that network. A network is
named by an ID, as interfaces are.

Assistant proposals on top:

- **Network IDs** (accepted by the user 2026-10-18): a typed string `Network`, completed in the editor, holding
  either the network's ID or the alias (name) given to it in the Federation GUI. The ID can be looked up in the
  Federation GUI. Proposal: the ID is short, derived from the network's stable identity (`NetworkId`, a UUID), as
  `network-p3x9ka` with the interface IDs' alphabet, and the topology view's network details show it with a copy
  button (one row, minimal GUI). A
  network whose identity is paused (merged, split or copied) is not usable until it settles; a call naming it
  returns nothing and the run log says why.
- **Optional arguments in calls.** `ae2.stock("minecraft:iron_ingot")` for the domain and
  `ae2.stock("minecraft:iron_ingot", "main_base")` or `ae2.stock("minecraft:iron_ingot", network="main_base")` for
  one network; keyword arguments are supported natively (user, 2026-10-18).
- **Domain-wide meaning per function.** `stock`: the sum over the domain's networks. `craft`: wherever the
  federation can craft it (as pattern projection already decides). `insert`/`extract`: through the domain's storage
  as the federation routes it today.
- **Policies and automation** (user, 2026-10-18): a program reaches every network of its domain directly; Storage
  and Crafting rules only govern what networks share with each other. A program can therefore move resources
  between networks that share nothing, as an administrator would. With the domain-wide default, `stock`, `craft`,
  `insert` and `extract` cover all networks, not only what the rules share. Revisit if per-player rights arrive.

**First draft of the lists** (types: `Resource` and `Item` are typed strings, `Block` from `ae2f.select`, `Stack` and
`Job` records):

| Namespace | Function | Effect? |
|---|---|---|
| `ae2` | `stock(resource) -> int`, `craftable(resource) -> bool`, `stored() -> list[Stack]` | no |
| `ae2` | `craft(resource, amount) -> Job`, `cancel(job)` | yes |
| `ae2` | `job_state(job) -> str` or `job.state`, `busy_cpus() -> int`, `power() -> float` | no |
| `ae2` | `insert(block, resource, amount) -> int` (block to network), `extract(block, resource, amount) -> int` (network to block) | yes |
| `ae2f` | `select(interface, reach) -> Block` | no |
| `ae2f` | `set_policy(...)` (shape follows the existing policy model) | yes |
| `ae2f` | `networks()`, `online(network) -> bool` | no |
| `mc` | `move(resource, amount, source, target) -> int` (any resource type: items, fluids, chemicals) | yes |
| `mc` | `contents(block) -> list[Stack]`, `count(block, resource) -> int`, `block_id(block) -> str` | no |
| `mc` | `redstone_in(block) -> int`, `redstone(block, level)` | read no, set yes |
| `mc` | `break_block(block) -> bool` (drops into the network), `place_block(block, item) -> bool` | yes |
| `mc` | `time() -> int`, `wait(ticks)` | `wait` yes (several ticks) |

Open within the draft: `mc.move` for every resource type versus `move_item`/`move_fluid`; where a redstone signal
is emitted (the interface itself, facing the selected block, is the natural emitter); messages to players.

Status (user, 2026-10-18): the standard library's design, including which functions take an interface as their
location, needs a further dedicated look; the bottom layer goes on meanwhile with a catalogue interface in place of
the real list.

## A smart script editor (user requirement 2026-10-18)

The script editor must be genuinely smart, completion above all; without it players forget names and signatures.
This repeats the [custom policies](custom-policies.md) requirement of comprehensive completion.

What completion should cover (assistant proposal):

- After `ae2.`, `ae2f.`, `mc.` (and addon namespaces): that namespace's functions, each with its signature,
  a one-line description and its capabilities.
- Inside a call: the current parameter's name and type highlighted, as IDE parameter hints.
- Ranked by the expected type: where an `int` is expected, `int` locals, parameters and functions returning `int`
  come first.
- In a resource place: a search over the registry with icons, inserting the right ID form.
- Names in scope (parameters, locals, the program's own functions), keywords, type names in hints.
- Errors underlined while typing, and the inferred type and documentation on hover.

This puts a demand on the bottom layer: completion runs on code that is half typed, so the parser must recover from
errors and still tell the cursor's context and expected type.

## Automation tab (user direction 2026-10-18)

The Federation domain GUI gains a fourth tab on its left strip, Automation, where players edit programs and install
them on a computing device. Boards on the GUI design canvas, row "自动化页签" (sample data):

- **Two views, switched** (user, 2026-10-18): Edit and Install are two pages of the one tab, switched at the top.
- **Edit view:** the program list (files and their functions) on the left; the editor with file tabs and a Script /
  Graph switch on the right.
- **Push** (user, 2026-10-18): a button at the top right of the edit view sends the file's current version to the
  computing devices that run its functions. Saving never reaches a device by itself; installations keep running the
  version last pushed. Assistant proposal: only a file that checks clean can be pushed, the button is inactive while
  the devices already run the current version, and a new installation takes the current version.
- **Installing by drawing wires** (user, 2026-10-18), as the processing mapping links Pattern Provider rows to
  Endpoints, not through a dialog. Left: program files with their public functions, each with a port. Right: the
  computing devices with compute and bandwidth meters, each with one port (user, 2026-10-18); the functions installed
  on it are listed below the meters, not as ports. Dragging a function onto a device adds an installation; while dragging, devices colour by whether its cost still fits (assistant proposal). Selecting
  an installation shows its arguments, trigger and cost beside the canvas.
- **A function library beside the programs** (user, 2026-10-18): every built-in function, by namespace. Clicking one
  opens its documentation and an example; in the graph view it can also be dragged onto the canvas as a node. The
  script view has no drag, but clicking still shows what exists. Assistant proposal: the documentation opens in the
  library panel itself, with a back button, and effectful functions carry a small purple mark matching the graph's
  effect colour.
- **Item icons wherever an item is named** (user, 2026-10-18): before a resource string in the script, in the
  resource-string completion, on resource ports in the graph and in installation arguments.

## Editing on the client, saving asynchronously (user requirement 2026-10-18)

Writing code needs flow: the player edits on the client and changes reach the server asynchronously. Network delay
must never slow typing, completion, checking or graph editing.

How it could work (assistant proposal):

- **Everything interactive runs on the client.** The language core (parser, checker, completion, graph conversion,
  layout) is plain Java in `common`, so the client runs the same code as the server. Item completion uses the client's
  own registries.
- **What the client needs from the server is cached.** Opening the tab sends the library index (files, function
  signatures, capabilities, versions) and the devices; the server pushes changes. Live values (stock in completion,
  device usage) arrive when they arrive and are left out until then; nothing waits for them.
- **Local first, then the server** (user, 2026-10-18): every edit is written to a local copy on the client first,
  then uploaded to the server automatically, but only for files open in the editor. Assistant proposal: the local
  copy is a plain `.ae2f` file under the game directory, one folder per server and world; the upload happens after a
  pause in typing, on switching file and on closing the screen, carrying the version the edit was based on; opening a
  file whose local copy holds edits that never reached the server uploads them if the server's version is still the
  one they were based on, otherwise opens the server's version and keeps the local edits beside it as a copy.
- **Editing in an outside editor** (user, 2026-10-18): while the Automation window is open in game, the player can
  edit the same local files in their own editor (VS Code or any other), and the changes reach the game and the
  server as if typed in game. Assistant proposal: the client watches the local folder while the window is open; a
  change to a file open in a tab reloads that tab (the graph keeps its layout through the reconciler) and uploads
  under the same rules; the in-game editor and the outside editor write the same file, so the last write wins. A file
  created in the folder shows in the program list and uploads once opened. The subset stays valid Python, so outside
  editors highlight it (with the file type set to Python for `.ae2f`).
- **Saving is automatic and silent** (user, 2026-10-18): no save button and no sync indicator ("saving…", "saved",
  pending marks). A file with errors is still saved as a draft, so nothing is lost; devices only receive a version
  through Push, and only a file that checks clean can be pushed (proposal).
- **The server stays authoritative.** It parses and checks again and never trusts the client's result; the client's
  check is for instant feedback. Same code on both sides means they agree.
- **Conflicts**, without any sync prompt: the last save wins, and an open editor without unsent changes simply
  updates when another player saves. Programs are global (user, 2026-10-18), so this applies to every player.
- **Installing optimistically.** A drawn wire appears at once like any other; in the rare case the server refuses it
  (the device lacked compute, which the drag colours already showed) it is simply gone.
- **Unsent edits survive a disconnect or a crash**: they are in the local copy and upload when the file is next open.
- **Payload size.** Serverbound custom payloads are size-limited; long files are sent in chunks or as diffs. The
  exact NeoForge limit needs checking before the protocol is designed.

## Effects: called in the body, one per tick (user decision 2026-10-18)

A function performs effects by calling them in its body, as ordinary calls; it does not return a list of actions.
Every effectful call takes one game tick, so a run makes at most one effectful call per tick.

Earlier comparison, kept for the record: returning actions as values would make functions pure and easy to batch
and preview, but reacting to an effect's result and multi-step work would need state machines across runs.

Consequences (assistant notes and proposals):

- **A run is a coroutine.** Each installation's run goes on through pure computation and reads until its next
  effectful call, makes that call, and pauses until the next tick; the call's result (a job, how much moved) is there
  when it resumes. The interpreter never touches the world itself: it hands the request to the host.
- **Reads are not effects.** Any number of observations per tick; after an effect, reads made on the next tick see
  its outcome.
- **Pure computation per tick is bounded by the device's compute**; a run that has not reached its next effect when
  the tick's budget is spent continues on the next tick. So `while True:` loops are fine: an effect inside one runs
  at most once per tick, and a loop without effects cannot freeze the server.
- **Waiting is an effect that takes several ticks**, such as `mc.wait(20)`.
- **Throughput comes from amounts, not from call counts:** one `craft` or move call carries an amount, so one call
  per tick is not one item per tick.
- **Static analysis** can state the fewest ticks a path needs (its effectful calls), shown beside the function.
- Each installation runs on its own; two installations on one device may each make one effect in the same tick.

## Program storage and installation (user decision 2026-10-18)

Programs are stored server-wide, not in items and not per network. Installing a program on a computing device decides
where it runs; storage and execution are separate.

Follow-ups (assistant proposal, open):

- **Identity.** Each program has a stable ID; installations refer to that ID and a function name,
  so renaming a program breaks nothing.
- **Visibility and editing rights** (user, 2026-10-18): no permissions for now; every program is global, seen and
  edited by every player.
- **Editing an installed program** (user, 2026-10-18): installations run the version last pushed; the Push button in
  the edit view (see the Automation tab) sends the current version to the devices.
- **Deleting a program in use** is refused, or its installations stop with a visible reason.
- **Sharing between servers** needs no item: the script text is a complete export format; pasting it imports a copy.
  Graph layout can travel along as an optional block.
- **Plain-text files in the world save** (user, 2026-10-18): every program is saved as a readable code file with the
  extension `.ae2f`, for example `<world>/ae2federation/programs/iron_tools.ae2f` (the folder is an assistant
  proposal). Proposals that follow from it:
  - The text is the source of truth. The graph's layout lives beside it (a sidecar keyed by node ID, or the world's
    `SavedData`); IDs are recovered by parsing, and if the text was changed outside the game, the Reconciler maps the
    old IDs onto the new parse, so positions survive outside edits too.
  - References between files are by name in the text. Renaming in the editor rewrites the callers' text, as an IDE
    rename does; renaming a file by hand outside the game makes its callers report errors.
  - A file with errors is simply saved as text; installations keep using the last version that checked clean.
  - Installations, devices and other state stay in the world's `SavedData`, not in the code files.
  - Writes are atomic (a temporary file, then a rename), UTF-8 with LF. File names from clients are checked against
    the identifier rule, so no path can leave the folder.
  - Files edited outside the game (another editor, git) are picked up by a reload command (user agreed, 2026-10-18),
    not by watching the folder.

## Files, multiple functions and calls between them (user ideas 2026-10-18)

The user wants functions to call other functions, including ones the player wrote elsewhere, and asked whether a
player should be able to edit several functions at once, or write several `def`s in one "file" or window.

Assistant proposal:

- **A file is the unit.** A program is a file holding any number of `def`s, edited as one script, saved in the
  server-wide library. The file name is a Python identifier and doubles as its namespace.
- **Calls across files without imports**, as the standard library: `iron_tools.keep_iron(64)` calls `keep_iron` from
  the file `iron_tools`; inside the same file the bare name is enough. Completion lists files and their functions as
  it lists `ae2.`. Names of the standard namespaces (`ae2`, `ae2f`, `mc`, addon ones) are reserved.
- **Private helpers** follow Python's convention: a name starting with `_` cannot be called from other files.
- **Checks span the library.** Types and capabilities are inferred across files. When a function's signature
  changes, the files calling it are re-checked and show the errors; whether running installations see the change
  belongs to the installation version question above.
- **No main function and no entry decorators** (user, 2026-10-18). A file may hold any number of functions; any
  public one can be installed on a computing device, `_name` never. Entry decorators (`@automation`, `@policy`) were
  proposed and dropped: with policies changed by automation there is one host, and an installation list grouped by
  file and searchable stays usable.
  - An installed function's parameters are filled in at installation (an item picker for an `Item`, a number field
    for an `int`), so `keep_stock(item: Item, min: int)` can be installed many times with different arguments.
  - Everything it calls comes along with it.
  - One `_helper` serves any number of entries in its file: privacy is per file, not per caller. Entries in
    different files that share helpers either use a public helper file, or, if wanted later, folders as packages: a
    file named `_common` is callable only from files in its own folder (Python's own convention for internal
    modules). Folders are not needed for the first batch.
- **Across files in detail** (asked by the user 2026-10-18):
  - References are stored by ID. The library keeps programs in the program representation, where a call to another
    file's function holds that file's and function's IDs; the script shows their current names. Renaming a file or
    a function therefore updates every caller, as an IDE rename does, with nothing to fix by hand.
  - File names are unique on the server (programs are global).
  - A dependency graph between files: saving a file re-checks the files that call it, and their errors appear at
    once. Files may call each other in both directions.
  - Deleting a file or a public function that others call is refused, listing the callers.
  - Exporting a file as text also exports the files it calls (or names them, to re-link by name on import).
  - The editor offers "go to definition" and "find callers" across files, in both views.
- **Several at once.** The editor opens several files in tabs, possibly side by side. In the graph view each function
  is its own canvas, listed beside it; a call node can be opened to jump to the called function's canvas, in either
  view.

## Policies changed by automation (user decision 2026-10-18)

Instead of policies calling functions, an automation calls a built-in such as `ae2f.set_policy(...)` and so
changes policies over time. Policies stay what they are today: plain settings, edited in the GUI or by a program.

Assistant assessment: a large simplification, recommended.

- **One host instead of two.** No function runs inside AE2's storage and crafting checks, so there is no policy-slot
  binding, no read-only requirement for it, no caching or sampling design and no "what does a policy do with an
  unavailable answer". The entry-decorator question disappears with it.
- **The existing path does the rest.** A policy change already advances `AuthorityEpoch`, so everything downstream
  invalidates as it does for a GUI edit today.
- **Same expressiveness in practice.** The earlier design would also have sampled and cached answers; an automation
  on a trigger or a schedule computing a filter list and setting it is equivalent, and its timing is visible.
- **Natural failure mode.** When the device stops (no power, unloaded), the policy keeps its last setting, as other
  devices keep their last state.
- **Progression.** Dynamic policies need a computing device, like all automation.

Points to settle:

- **Setting an unchanged value is a no-op**, so a loop that sets the same filter every tick does not churn the
  epoch; a rate limit may still be needed.
- **Conflicts.** A player's GUI edit and an automation, or two automations, can set the same policy; the last write
  wins, and the policy GUI should show which automation controls it (within the minimal-GUI rule).
- **Rights.** An automation may only change policies its owner may edit.
- **What `set_policy` addresses**: which domain relationship and which rule (Storage, Crafting) are independent
  today, so the built-in follows the existing policy model.

## Graph conversion for the Python subset (assistant proposal 2026-10-18)

The Phase 0 prototype's rules carry over (one program representation, stable node IDs, exec wires for order, data wires for
values, literals on ports, positions by node ID). What the new decisions add:

- **One canvas per function.** A file is a list of function canvases. The function list beside the canvas shows each
  function's privacy and capabilities; file-level text (order of `def`s, comments between them) is kept on the function
  nodes.
- **Entry node** per canvas: the signature, with each parameter as a typed output port, and the docstring as its
  description. `return` nodes end a chain.
- **Statements on the exec chain:** assignment (`x = e`, `x: Item = e`; reassignment is allowed, as loops need it),
  `if/elif/else` (a branch node with one exec output per arm), `while` (condition port; outputs `body` and `next`),
  `for x in xs` (list port; outputs `body`, `next` and the element `x`), `break`, `continue`, `return`, and a call on
  its own line.
- **Pure and effectful calls, as Unreal Blueprints split them.** A call whose inferred capabilities include an effect
  (emits redstone, requests crafting, waits) must sit on the exec chain, because its order matters; a pure call is a
  data node with no exec ports. In the script the matching rule is that an effectful call may only be a statement or
  the whole right side of an assignment (`job = ae2.craft(...)`), never inside a larger expression. Because
  capabilities are inferred, editing a called function can turn a pure call effectful; the checker then reports where
  it must move.
- **Variables** (user picked option A, 2026-10-18, after calling the first board's getter nodes a mess):
  - A reading of a variable is a chip on the input port, coloured by type, like a literal written there: no wire and
    no node. A Variables strip at the top of the canvas lists the function's variables; dragging one onto a port
    reads it there.
  - An assignment whose value is one call is one node, `now = ae2.stock`; other assignments are a node on the exec
    chain with the value as input.
  - Not chosen: option B, short expressions written on ports as code (`now < 64`); operators stay nodes.
- **Values used twice.** Parameters and loop elements may feed any number of ports; variables are read as chips. A computed
  output feeds one port; dragging a second wire from it offers "extract to variable", which inserts an assignment.
  This keeps the script and the graph one to one.
- **Typed strings on ports.** A resource port shows the icon and opens the searchable picker; a list port takes a
  list node or an inline list of values.
- **Calls into other files** are call nodes titled `iron_tools.low`, with ports from the callee's signature; opening
  one jumps to that function's canvas. The node keeps the callee's ID, so renames follow.
- **Formatting.** The script view is regenerated by the printer, so switching graph to script normalises spacing and
  parentheses. A function whose graph was not edited keeps its original text.
- **Drafts.** A script with a syntax error does not switch (decided above); unconnected nodes stay as a graph draft.

## Interaction interface (user decisions 2026-10-18)

Programs act on the world through the standard library: moving items, sending and reading redstone, breaking and
placing blocks, and more. Those functions need a place in the world to act at; the interaction interface is that
place. Designed after B2 and before B3, since the location type it adds feeds the checker and the interpreter.

Decided (user, 2026-10-18):

- **The interface is a locator, not a machine.** It has no abilities of its own and no cards: it gives a program's
  functions a position to act at. Everything it does comes from the program running on a computing device, through
  standard library functions that take an interface as their location. The question is therefore never "what can
  the interface do" but "which library functions take a location".
- **One full block, acting in front of it.** Its other five faces connect ME Glass Cable or ME Federation Cable; once
  it is in a Federation domain, every computing device in that domain can use it. The domain spans networks and
  dimensions, so one program can act anywhere the domain reaches.
- **Reach beyond the adjacent block**, straight ahead: blocks in between do not matter (it acts through them), and a
  longer reach costs nothing extra. At most 4 blocks for now (user, 2026-10-18).
- **Reach is an argument of the call, not a block setting** (user, 2026-10-18): the program says how far ahead each
  call acts, as in `mc.break_block(gate, 2)`. The interface's GUI has only the ID and the alias.
- **Name** (user, 2026-10-18): ME Federation Automation Interface (「ME联邦自动化接口」). "Automation" keeps it apart
  from AE2's ME Interface, which it does not resemble: it stores and moves nothing by itself. Registry ID
  `automation_interface`; in programs the type is `Interface` (unambiguous inside a script) (assistant proposals,
  accepted by the user 2026-10-18). The automatic ID is random, not a counter (user, 2026-10-18: a running number
  looks off): six characters from a lowercase alphabet without look-alikes (no 0/o, 1/l/i), as in
  `interface-k7m2qx`, drawn again on a clash with any ID on the server (proposal accepted 2026-10-18). Below, "interface" means this block.
- **Identity and alias.** Every interface gets an ID automatically, so a program can always name it. Right-clicking it
  with a wrench turns it; any other right-click opens its GUI, where the player sets an alias.

Assistant proposals, to discuss:

- **Reach argument details.** Left out, it is the adjacent block (1); the checker allows that optional trailing
  argument (also as `reach=2`, keyword arguments being native); a constant outside 1..4 is a check
  error, a computed one outside it stops that call at run time.
- **Addressing** (proposal on top of the decision above). In a program an interface is a typed string (type
  `Interface`) holding either its alias or its ID; completion lists both, with the alias first. The
  automatic ID (random, see Name) never changes; an alias is
  unique within its domain (a duplicate is refused in the GUI). The editor shows the other half beside the string,
  as item icons sit beside item strings: the ID beside an alias, the alias beside an ID. The GUI shows the ID (copy
  button) and the alias field, nothing else. Renaming an alias: programs that use the old alias report it at once
  (the server re-checks files that refer to it), and the rename offers to update them, as renaming a file does.
- **Wrench.** Any item in the common wrench tag works, as for AE2 blocks; following AE2, sneak + wrench picks the
  interface up.
- **Library functions that take a location** (names and namespaces open; part of the standard library design):
  `mc.redstone(port, level)`, `mc.redstone_in(port) -> int`, `ae2.export(port, item, amount) -> int` and
  `ae2.import_from(port, item, amount) -> int` (between the network and the inventory at the target; the amount
  moved comes back), `mc.break_block(port) -> bool` (drops go into the network), `mc.place_block(port, item) -> bool`,
  `mc.block_at(port) -> str`. Each effectful call is one tick, as decided. Transfer works on any AE2 key type (items,
  fluids, addon types such as Mekanism chemicals), not an item-only list.

### Example: from one chest to another (asked by the user 2026-10-18)

Two chests, each faced by an automation interface (`chest_a`, `chest_b`). Assistant proposals, not decided:

- **A. One call that moves between two places**, straight from inventory to inventory, one tick:
  `moved = mc.move("chest_a", "chest_b", "minecraft:iron_ingot", 64)`, returning how many moved. Nothing passes
  through network storage, so it works with a full network and nothing else can take the items halfway; the two
  places may be in different networks or dimensions of the domain.
- **B. Through the network in two steps**: `ae2.import_from("chest_a", item, 64)`, then `ae2.export("chest_b", item,
  64)`; two ticks, needs free storage, and another automation may use the items in between. Worth having anyway for
  "put into the network" and "take out of the network".
- **Reach without keyword arguments.** With two places and a reach for each, positional reach arguments get hard to
  read (`mc.move(a, b, item, 64, 1, 2)`). Option: a place value, `mc.at("chest_b", 2)` (an interface and a reach),
  accepted wherever a place is, while a bare interface string means reach 1:
  `mc.move("chest_a", mc.at("chest_b", 2), item, 64)`. The other option is supporting keyword arguments in the
  subset (`reach=2`), which Python has anyway.
- **Choosing what to move**: reading the source first, `for stack in mc.items("chest_a"):` with each stack's
  resource and amount, then moving per stack.
- **Which side of the target is used**: the side facing the interface, as a hopper or an AE2 bus uses the side it
  touches. Machines with sided inventories (a furnace's top, side and bottom) may need the side as an option.

### Select the block first (user idea 2026-10-18)

The user's shape, preferred over passing interfaces and reaches to every call:

```python
chest_a = ae2f.select("interface_a6dz", 1)  # the block just in front of the interface
chest_b = ae2f.select("interface_7azh", 2)  # two blocks ahead
mc.move_item("minecraft:diamond", 64, chest_a, chest_b)  # 64 diamonds from chest_a to chest_b
```

Selecting is a step of its own, and the selected block is a value the other functions take. The user's aim: when the
selection is known while editing, the editor can check it and show what block it is, for vanilla inventories and
other mods' machines alike.

Assistant proposals on how:

- **A `Block` value.** `ae2f.select(interface, reach)` returns a `Block`: the position, and the side facing the
  interface (the side a hopper or AE2 bus would use). Selecting only reads, so it is not an effect and takes no
  tick; `mc.move_item` is the effect.
- **Knowing the block while editing.** Every interface reports what is at reach 1 to 4 in front of it: the block's ID
  and what it offers (items, fluids, energy, other resource types). The server keeps this with the interface list it
  already sends for completion, updated when those blocks change, so the client knows it even for interfaces in
  unloaded or other-dimension areas of the domain. With constant arguments, the checker then knows the block:
  - the editor shows it beside the variable, as item icons are shown (`chest_a` with a chest icon, "Chest, 27
    slots");
  - a call that needs something the block does not offer gets a warning (`mc.move_item` on a block without items).
- **Warnings, not errors.** The world changes after the check: the chest may be broken or replaced. At run time each
  call checks again and fails softly (moves 0 and the run goes on, or reports the reason; open). With a selection
  that is not constant (an installation argument, a parameter), the check happens when the installation's arguments
  are known: at installation and at Push.
- **Compatibility through AE2's own mechanism.** AE2's buses already reach any block through per-resource-type
  strategies in its public API (`appeng.api.behaviors.ExternalStorageStrategy`, `StackImportStrategy`,
  `StackExportStrategy`), built on NeoForge's block capabilities. Vanilla chests, furnaces and hoppers, other mods'
  machines, and addon resource types (Mekanism chemicals through their AE2 addon) all work wherever an AE2 Storage
  or Import/Export Bus works, with no list of supported blocks.
- **Sided machines.** The default side is the one facing the interface; choosing another side (a furnace's top for
  input, bottom for output) could be an optional argument of `select`. Open.
- ID format (user, 2026-10-18): the proposed one, `interface-k7m2qx` (six characters); the sample's
  `interface_a6dz` was only an illustration.

Open: the full list of library
functions that take a location (with the standard library design); entity interactions (later).

## Simplification review (asked by the user 2026-10-18)

The user wants a very simplified Python and asked what in the design makes the interpreter, maintenance or debugging
harder than needed. Assistant review, ranked by what drives the difficulty; all proposals, for the user to pick.

1. **Suspension anywhere** is what makes the interpreter hard: one effect per tick plus a carried-over step budget
   means a run must pause in the middle of an expression (inside a nested call that loops).
   - Rules that bound the pause points: an effectful call only as a statement or the whole right side of an
     assignment (already the graph's rule), and no recursion (Phase 0's rule, not restated since). Cost: with actions
     returning `bool`, `if mc.move(...):` is not allowed; it is `ok = mc.move(...)` then `if ok:`.
   - Build a small bytecode VM rather than a tree-walking interpreter: compile the tree to instructions and step
     them; pausing keeps the instruction pointer and the stack; the budget is an instruction count. More code at
     first, each part trivial, state inspectable and testable step by step; the dry run's per-node values come from
     the same machine.
   - Runs in progress are not saved: after a server restart every installation starts again from the beginning. No
     VM state to serialise.
2. **Graph identity.** Node IDs that survive script edits need the heuristic reconciler, a layout sidecar and stored
   positions. Simpler: the graph is always laid out automatically, nothing stored. Lost: arranging nodes by hand.
   With the dry run showing values on nodes, a live, auto-laid-out graph may be enough.
3. **Graph editing multiplies every language feature.** Each addition (keyword arguments, methods, indexing,
   defaults) needs a graph form and graph edit operations. Simpler: the graph is mainly a view (with dry-run values)
   plus a few edits, and the script is where code is written. Slices are subset scope worth dropping.
4. **Record values.** With `Job` gone, only `Stack` would remain (from `mc.contents`, `ae2.stored`). Simpler:
   `list[Resource]` plus `count` functions; no records, no attribute access on values at all.
5. **Edit-time block knowledge** (interfaces reporting blocks at reach 1 to 4, synced to clients) duplicates what the
   server-side dry run reads anyway. Simpler: block names and capability warnings come from the dry run.
6. **Dry run depth.** Simpler: run on demand (a button) instead of refreshing live; reads are real, effects are not
   simulated but shown as "would do X" and assumed to succeed. Lost: exact "would move 64" answers.
7. **References by ID across files**, rename propagation, re-checking dependants, refusing deletes, and alias renames
   rewriting programs. Simpler, as Python: references by name; a broken one is a check error.
8. **Sync.** Within local-first editing and outside editors: last write wins everywhere, no version bases, no
   conflict copies.
9. **Python fidelity, stated not chased.** Integers are 64-bit and an overflow stops the run (Python's are unbounded:
   a deliberate difference); `//` and `%` tested against CPython; `str()` of a float need not format exactly as
   Python does; built-in and method lists stay small.
10. **Checker scope.** No definite-assignment analysis (Python has none; an unbound name stops the run). No
    `unavailable` value (Phase 0): reads on an unloaded or paused network return plain values and the run log says
    why. Capability inference stays (cheap; graph colours and the tick rule need it).

Items 1 (its cost), 2, 3 and 6 touch user decisions or requests; 4, 5, 7, 8 and 10 were only ever assistant
proposals.

## Layers (assistant proposal)

| Layer | Contents | Game code |
|---|---|---|
| 1. Language core | Types, the program representation, script syntax, graph model, conversion both ways, keeping node identity across edits, static checks, interpreter | None |
| 2. Function descriptors | Each built-in function declared by name, parameters, types, capabilities, category and icon. The core knows only descriptors; hosts register implementations, and addon mods can add functions of their own | None (definitions only) |
| 3. Program storage | Serialising a program with its graph layout and comments, with a format version; the server-wide library; asynchronous client-server sync | Save format and packets |
| 4. Host | The computing device runs installed functions on triggers, supplying effects and a budget. Policies are plain settings an automation may change through `ae2f.set_policy` | Yes |
| 5. Editors | Script editor and graph editor screens, working only on layers 1 and 3 | Yes |

The first batch covers layers 1 to 3.

## Vocabulary (assistant proposal)

- **Function**: a generic piece of program with parameters and an optional result; what it can do is inferred
  (above), not declared.
- **Program** (file): a set of functions plus their graph layout; the unit that is edited and saved server-wide.
- **Automation** (an installation): a public function with its arguments, a trigger and a computing device. It says
  when to run (trigger), what to run (a function call) and where (the device, which supplies the budget and the
  effects).
- **Trigger**: a redstone change, a schedule, a manual press, or "a function's answer turned true".

"Automation" is a host-level idea, not a language feature: the language has only functions. Policies are not part
of the language either; an automation changes them through a built-in. That is what lets layer 1 be built and tested alone.

## What the bottom layer still needs

The Phase 0 prototype handles complete, valid programs only. Real editing passes through invalid states.

- **Drafts.** A script being typed often has syntax errors; a graph being built has unconnected nodes. The custom
  policy requirements already ask that an incomplete or invalid script is shown without discarding the draft or
  showing an unrelated graph. A script with a syntax error keeps its text and cannot switch to the graph until fixed,
  with the error's line shown (user, 2026-10-18). Proposal for the graph side: the program representation stays the
  one source of truth, and unconnected graph nodes live in the graph document as a draft beside it.
- **Graph edit operations.** Add a node, connect and disconnect, delete, change a value on a port: each a pure
  operation on the program representation, each with tests.
- **Script editing support.** Done in B1-B2 (2026-10-18): an error-tolerant parser with error positions, a
  completion engine (cursor context, expected type, candidates from scope and a catalogue interface), and formatting
  by the printer.
- **Serialisation** with a format version.
- **Descriptor table** for the built-ins, feeding the graph's node palette and script completion.

## Open questions

| # | Question | Assistant proposal |
|---|---|---|
| 1 | ~~Script language~~ | Decided: a Python subset (above). |
| 2 | ~~Draft policy~~ | Decided (user, 2026-10-18): a script with a syntax error cannot switch to the graph; its text is kept and the error shown until it parses. |
| 3 | ~~Where programs are stored~~ | Decided (user, 2026-10-18): server-wide; see Program storage and installation. |

Deferred with the world interactions: the first built-ins to implement (`ae2f.set_policy` for a storage filter
list and redstone output proposed) and trigger kinds. Decided (user, 2026-10-18): programs are global with no
permissions for now; installations run the pushed version; Edit and Install are two switched pages.

## Related

- [Phase 0 plan and prototype results](../../.omo/plans/function-system.md)
- [Videnoa reference](../../.omo/knowledges/videnoa-function-system-reference-2026-10-10.md): port-named wires,
  positions by node ID, inline port values and node descriptors are adopted; its pure data flow is not.
- [Custom policies](custom-policies.md), [Rule-based automation](rule-based-automation.md)
