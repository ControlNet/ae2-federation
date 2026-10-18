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
- **A missing type hint is a parse error** naming the parameter or function.
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

## Interaction blocks (user direction 2026-10-18)

Programs need a way to act on the world: an interaction interface that moves items, sends and reads redstone, breaks
and places blocks, and more (user, 2026-10-18). Designed after B2 and before B3, since the types and effects it adds
feed the checker and the interpreter.

Assistant proposals, to discuss:

- **Where a program reaches the world.** A block placed in the world and joined to the Federation (as Endpoints
  are) acts on the block it faces. Because the Federation spans networks and dimensions, one program on one
  computing device can drive interfaces anywhere in the domain.
- **One block or several.**
  - Option 1: one interface block whose abilities come from cards in its upgrade slots (redstone, transfer, break,
    place), as AE2 cards add abilities. One thing to name, place and address; the slots show what it can do.
  - Option 2: one block per ability, after AE2's own parts (import/export buses for transfer, the Annihilation and
    Formation Planes for breaking and placing, the Level Emitter and Toggle Bus for redstone). Familiar to AE2 players,
    more blocks to make and draw.
- **Addressing.** The player names an interface (in its GUI, as Endpoints are named); in a program it is a typed
  string, `Port`, completed from the names in the domain, as `Item` strings are completed from the registry; or a
  parameter filled in at installation from a picker. The name is a stable ID underneath, so renaming breaks nothing.
- **First functions it would give** (names and namespaces open):
  `mc.redstone(port, level)`, `mc.redstone_in(port) -> int`, `ae2.export(port, item, amount) -> int` and
  `ae2.import_from(port, item, amount) -> int` (move between the network and the faced inventory; amounts moved come
  back), `mc.break_block(port) -> bool` (drops go into the network), `mc.place_block(port, item) -> bool`,
  `mc.block_at(port) -> Block`. Each effectful call is one tick, as decided.
- **Generic resources.** Transfer works on any AE2 key type (items, fluids, addon types such as Mekanism chemicals),
  not an item-only list; see generic addon support.
- **Range and cost.** Each call costs compute on the device that runs it; whether distance or another dimension
  costs more is open.

Open: one block with cards or one block per ability; full block or cable part; the full first function list; reading
inventories and block states beyond redstone; entity interactions (later).

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
- **Script editing support.** An error-tolerant parser, a completion engine (cursor context, expected type, candidates
  from descriptors, scope and the registry catalogue), error positions, and formatting (the printer already formats).
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
