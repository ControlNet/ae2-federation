# Function system architecture

Discussion date: 2026-10-18. [Idea index](README.md).
Status: layering and vocabulary proposed by the assistant, not yet accepted; the first batch's scope and the script
language are the user's decisions (2026-10-18); the overall design is being revisited. Phase 0 spike on branch `feature/function-system`, see the
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
  valid Python syntax, so outside editors can highlight and format them. Consequence: resources are written as calls,
  for example `item("minecraft:iron_ingot")`, not with the spike's `@minecraft:iron_ingot`.
- **Numbers as in Python:** `int` (64-bit, matching AE2 amounts) and `float`; `/` gives a float, `//` an int, and an
  int widens to a float where a float is expected.
- **Loops are in:** `for x in xs` and `while`. Both are easy to draw as graph nodes (a loop node with a body chain and
  a next chain; for-each also gives the current element as a value output).

Consequences of loops (assistant notes, not decisions): a run's length is no longer bounded by the program's size,
so the spike's static step bound goes; every run needs a step budget at run time. A query that runs out of budget
needs a defined answer (unavailable is the obvious candidate). For actions, a budget per tick that lets a long run
continue over several ticks, so "wait until stock reaches N" is an ordinary loop, is one option to discuss with the
computing device's CU.

Still open, to be revisited with the overall design (user, 2026-10-18): how queries and actions are told apart, or
whether that split stays at all.

## Layers (assistant proposal)

| Layer | Contents | Game code |
|---|---|---|
| 1. Language core | Types, the program representation, script syntax, graph model, conversion both ways, keeping node identity across edits, static checks, interpreter | None |
| 2. Function descriptors | Each built-in function declared by name, parameters, types, query or action, category and icon. The core knows only descriptors; hosts register implementations, and addon mods can add functions of their own | None (definitions only) |
| 3. Program storage | Serialising a program with its graph layout and comments, with a format version | Only the save format |
| 4. Hosts | Who calls functions: the Policy host asks queries; the automation host (computing device) runs actions on triggers. A host supplies observations, effects and a budget | Yes |
| 5. Editors | Script editor and graph editor screens, working only on layers 1 and 3 | Yes |

The first batch covers layers 1 to 3.

## Vocabulary (assistant proposal)

- **Function**: the smallest callable unit. A **query** only reads and returns a value; an **action** may act on the
  world and returns nothing. A query never calls an action, also not through other functions.
- **Program**: a set of functions plus their graph layout; the unit that is edited, saved and copied.
- **Binding**: one use of a function at one place, with its arguments. A Policy binding is a policy slot, a query and
  its arguments.
- **Automation**: a binding with a trigger. It says when to run (trigger), what to run (an action call) and where
  (the computing device, which supplies the budget and the effects).
- **Trigger**: a redstone change, a schedule, a manual press, or "a query's answer turned true".

"Automation" is a host-level idea, not a language feature: the language has only functions, and policies and
automation differ in where and when they call one. That is what lets layer 1 be built and tested alone.

## What the bottom layer still needs

The Phase 0 spike handles complete, valid programs only. Real editing passes through invalid states.

- **Drafts.** A script being typed often has syntax errors; a graph being built has unconnected nodes. The custom
  policy requirements already ask that an incomplete or invalid script is shown without discarding the draft or
  showing an unrelated graph. Proposal: the program representation stays the one source of truth; unconnected graph
  nodes live in the graph document as a draft beside it; a script with a syntax error keeps its text and cannot switch
  to the graph until fixed, with the error's line shown.
- **Graph edit operations.** Add a node, connect and disconnect, delete, change a value on a port: each a pure
  operation on the program representation, each with tests.
- **Script editing support.** Error positions, completion data generated from the descriptors, and formatting (the
  printer already formats).
- **Serialisation** with a format version.
- **Descriptor table** for the built-ins, feeding the graph's node palette and script completion.

## Open questions

| # | Question | Assistant proposal |
|---|---|---|
| 1 | ~~Script language~~ | Decided: a Python subset (above). |
| 2 | Draft policy: may a script with a syntax error be shown as a graph? | No; keep the text and show the error line until it parses (see Drafts). |
| 3 | Where programs are stored: an item that can be copied and handed over (like a pattern or a disk), or a library on the network? | Not urgent; it shapes layer 3's format. |

Deferred with the world interactions: the first host use (a storage policy's filter list proposed), the first
action (redstone output proposed), what a policy does with an unavailable answer, sampling period, trigger kinds,
who may edit programs.

## Related

- [Phase 0 plan and spike results](../../.omo/plans/function-system.md)
- [Videnoa reference](../../.omo/knowledges/videnoa-function-system-reference-2026-10-10.md): port-named wires,
  positions by node ID, inline port values and node descriptors are adopted; its pure data flow is not.
- [Custom policies](custom-policies.md), [Rule-based automation](rule-based-automation.md)
