# Function system architecture

Discussion date: 2026-10-18. [Idea index](README.md).
Status: layering and vocabulary proposed by the assistant, not yet accepted; the first batch's scope and the script
language and generic functions are the user's decisions (2026-10-18). Phase 0 spike on branch `feature/function-system`, see the
[plan](../../.omo/plans/function-system.md).

This page holds the architecture shared by [custom policies](custom-policies.md) and
[rule-based automation](rule-based-automation.md): how programs, functions and automation are defined, and which
layer each part of the work belongs to. The two idea pages keep their own product requirements.

## User decision: start from the bottom (2026-10-18)

The first batch leaves world and content interactions aside (policy hookup, the computing device, actions on a real
network) and starts at the lowest layer: editing a program as a script, as a graph, and converting between the two.
Everything above it waits until that layer is solid.

Earlier user decisions this page builds on: one general function system edited in the automation GUI, Policy as a
specialised use of it, interchangeable graph and script editors, redstone output as an automation action, and the
computing device's role (see the two idea pages).

## Script language (user decisions 2026-10-18)

- **A subset of Python.** The interpreter is not expected to cover all of Python; scripts in the subset should stay
  valid Python syntax, so outside editors can highlight and format them. Resources are plain strings (below), not
  the spike's `@minecraft:iron_ingot`.
- **Numbers as in Python:** `int` (64-bit, matching AE2 amounts) and `float`; `/` gives a float, `//` an int, and an
  int widens to a float where a float is expected.
- **Loops are in:** `for x in xs` and `while`. Both are easy to draw as graph nodes (a loop node with a body chain and
  a next chain; for-each also gives the current element as a value output).

Consequences of loops (assistant notes, not decisions): a run's length is no longer bounded by the program's size,
so the spike's static step bound goes; every run needs a step budget at run time. A policy evaluation that runs out of
budget needs a defined answer (unavailable is the obvious candidate). For automation, a budget per tick that lets a long run
continue over several ticks, so "wait until stock reaches N" is an ordinary loop, is one option to discuss with the
computing device's CU.

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
works out what a function can do, so the system knows where it may be used. This replaces the spike's declared
query/action split.

How it could work (assistant proposal):

- **Capabilities on the built-ins.** Each built-in's descriptor states what calling it does, for example: reads the
  network's storage, reads redstone, emits redstone, requests crafting, moves resources, waits (spans ticks). Plain
  arithmetic and the player's own logic add nothing.
- **Inferred per function.** A function's capabilities are the union of those of every built-in it can reach,
  through any call chain, solved over the call graph so recursion is no obstacle. The analysis is conservative: a
  call that sits on a branch that never runs still counts, which keeps it simple and easy to explain.
- **Hosts state requirements.** A policy slot accepts a function that only reads, never waits and returns `bool` (a
  predicate) or a filter; automation accepts anything. A refusal says why and where, e.g. "emits redstone (line 12,
  through `signal`)".
- **More than a yes/no.** The same analysis can report which observations a function reads (a cached policy answer
  then only needs recomputing when those change), whether it may loop without end (`while`, recursion), and its I/O
  for the computing device's bandwidth cost, which the device decisions already expect to be inferred from the
  program.
- **Guarantee at run time too.** A host passes in only the capabilities it allows, so a policy evaluation physically
  cannot act even if the analysis had a bug.
- **Both views show it.** The graph can badge nodes and functions with their capabilities; the script editor can show
  them beside each `def`.

## Standard library (user direction 2026-10-18)

The system ships a set of ready functions players call directly: AE2 ones (stock, craftability, crafting status,
storage, energy and the like) and Minecraft ones (reading and emitting redstone and the like). Lists are not fixed.

Grouped by namespace, already available without imports, as `ae2.stock(...)`, `mc.redstone(...)`,
`federation.online()` (user, 2026-10-18). Namespaces become the graph palette's categories, and addon mods can add
their own (`mekanism....`) through the same descriptors, as the mod already handles addon resource types without
AE2-only lists.

## A smart script editor (user requirement 2026-10-18)

The script editor must be genuinely smart, completion above all; without it players forget names and signatures.
This repeats the [custom policies](custom-policies.md) requirement of comprehensive completion.

What completion should cover (assistant proposal):

- After `ae2.`, `mc.`, `federation.` (and addon namespaces): that namespace's functions, each with its signature,
  a one-line description and its capabilities.
- Inside a call: the current parameter's name and type highlighted, as IDE parameter hints.
- Ranked by the expected type: where an `int` is expected, `int` locals, parameters and functions returning `int`
  come first.
- In a resource place: a search over the registry with icons, inserting the right ID form.
- Names in scope (parameters, locals, the program's own functions), keywords, type names in hints.
- Errors underlined while typing, and the inferred type and documentation on hover.

This puts a demand on the bottom layer: completion runs on code that is half typed, so the parser must recover from
errors and still tell the cursor's context and expected type.

## Open: actions as calls or as return values (raised by the user 2026-10-18)

Should a function perform actions by calling them in its body, or return the actions as its value and leave
running them to the system?

| | Calls in the body | Actions as the return value |
|---|---|---|
| Purity | The function has effects | The function is pure; the system runs what it returns |
| Reacting to a result (job refused, how much actually moved) | Directly, on the next line | Only in a later run, with state kept between runs |
| Multi-step and waiting (`while stock < 64: wait()`) | An ordinary loop | A state machine over several runs |
| Scheduling, batching, bandwidth limits | Harder: effects happen mid-run | Easy: the system sees all actions first |
| Preview / dry run, testing | Needs stand-in results | Natural: show the returned list |
| All-or-nothing over several actions | No | Possible |

A middle way (assistant proposal): players write calls, the system runs them as requests. Calling `craft(...)` does
not touch the world from inside the interpreter; the run pauses, hands the request to its host, and resumes with
the result when the host has carried it out (a coroutine, as Python generators work, but without `yield` in the
script). The function itself stays free of side effects; the host decides when and whether each request runs, can
throttle by bandwidth, answer with simulated results for a preview, and refuse every request in a policy. What it
gives up against pure return values: the host cannot see all of a run's actions before the first runs, so
all-or-nothing batches need a separate construct if wanted.

## Program storage and installation (user decision 2026-10-18)

Programs are stored server-wide, not in items and not per network. Installing a program on a computing device decides
where it runs; storage and execution are separate.

Follow-ups (assistant proposal, open):

- **Identity.** Each program has a stable ID; installations and Policy bindings refer to that ID and a function name,
  so renaming a program breaks nothing.
- **Visibility and editing rights** on a multiplayer server: per player, per team, or everyone. Open.
- **Editing an installed program.** Either installations follow the latest saved version (simple, but a mistake
  reaches every device at once), or each installation pins a version and is updated on request. Open.
- **Deleting a program in use** is refused, or its installations stop with a visible reason.
- **Sharing between servers** needs no item: the script text is a complete export format; pasting it imports a copy.
  Graph layout can travel along as an optional block.
- Kept as a world `SavedData`, so it saves with the world and works the same in single player.

## Layers (assistant proposal)

| Layer | Contents | Game code |
|---|---|---|
| 1. Language core | Types, the program representation, script syntax, graph model, conversion both ways, keeping node identity across edits, static checks, interpreter | None |
| 2. Function descriptors | Each built-in function declared by name, parameters, types, capabilities, category and icon. The core knows only descriptors; hosts register implementations, and addon mods can add functions of their own | None (definitions only) |
| 3. Program storage | Serialising a program with its graph layout and comments, with a format version; the server-wide library | Only the save format |
| 4. Hosts | Who calls functions: the Policy host asks read-only functions; the automation host (computing device) runs functions on triggers. A host states which capabilities it allows, supplies them and a budget | Yes |
| 5. Editors | Script editor and graph editor screens, working only on layers 1 and 3 | Yes |

The first batch covers layers 1 to 3.

## Vocabulary (assistant proposal)

- **Function**: a generic piece of program with parameters and an optional result; what it can do is inferred
  (above), not declared.
- **Program**: a set of functions plus their graph layout; the unit that is edited, saved server-wide and installed
  on a computing device.
- **Binding**: one use of a function at one place, with its arguments. A Policy binding is a policy slot, a read-only
  function and its arguments.
- **Automation**: a binding with a trigger. It says when to run (trigger), what to run (a function call) and where
  (the computing device, which supplies the budget and the effects).
- **Trigger**: a redstone change, a schedule, a manual press, or "a function's answer turned true".

"Automation" is a host-level idea, not a language feature: the language has only functions, and policies and
automation differ in where and when they call one. That is what lets layer 1 be built and tested alone.

## What the bottom layer still needs

The Phase 0 spike handles complete, valid programs only. Real editing passes through invalid states.

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

Deferred with the world interactions: the first host use (a storage policy's filter list proposed), the first
action (redstone output proposed), what a policy does with an unavailable answer, sampling period, trigger kinds,
who may edit programs.

## Related

- [Phase 0 plan and spike results](../../.omo/plans/function-system.md)
- [Videnoa reference](../../.omo/knowledges/videnoa-function-system-reference-2026-10-10.md): port-named wires,
  positions by node ID, inline port values and node descriptors are adopted; its pure data flow is not.
- [Custom policies](custom-policies.md), [Rule-based automation](rule-based-automation.md)
