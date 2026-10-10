# Rule-based automation

Discussion date: 2026-10-10.
Status: preliminary user-proposed direction; detailed behavior remains open.

## Intent

The user is interested in active automation alongside custom sharing policies. Action directions include
moving resources, requesting crafting, and emitting redstone signals. The user explicitly confirmed that
redstone output belongs in this system, including uses independent of crafting or production workflows.
This is not an exhaustive action list or a claim of existing functionality; detailed semantics remain open.

The user clarified that this is a general automation system with multiple applications. Meta-crafting is
one candidate application, not the system's definition. Do not require every automation to be a production
DAG, contain crafting stages, or finish by delivering an item. Shared observations, rule authoring, actions,
and invocation mechanisms should be evaluated across different applications.

## Redstone output

Rules should be able to produce redstone signals as an automation action, independently of resource
movement or crafting. Setting signal strength, holding an output, and emitting a timed pulse are possible
forms to discuss, not separately confirmed requirements. The physical output location/device, output faces,
and behavior when automation stops or unloads remain undecided. Do not assume every Federation block gains
redstone output or introduce a new output block as an already selected requirement.

## Shared function system and unified authoring

The user refined the direction: build a general, powerful, flexible base system for both automation and
[custom policies](custom-policies.md). Policy customization can be a specialized application of that system:
for example, a predicate returns a boolean, or a function returns resource-filter criteria. Players edit
these functions in the automation GUI, with the proposed interchangeable node-graph and script editors.
This supersedes the earlier interpretation of separate editors for Policy and automation logic.

Recommended interaction: a shared function library/editor authors reusable logic, while Policy settings
bind suitable functions and parameters to sharing relationships. Manual, conditional, and scheduled
automation calls use suitable functions from the same system. Exact bindings and calling conventions
remain open; no additional screen or block is prescribed.

An assistant recommendation is to distinguish read-only query functions from effectful action functions
within this shared system. Queries may observe changing network/world state, return booleans or filter
values, and call other suitable queries. They must not indirectly execute actions or wait on long-running
production jobs during permission evaluation. Actions can use queries and perform permitted operations
such as transfers, crafting requests, and redstone output. Graph and script modes should express the same
distinction. This is a proposed behavioral contract, not a selected type system or scripting runtime.

Output types, execution budgets, errors, unavailable observations, function-reference lifecycle, and
update/sampling semantics need later design. A filter result is not by itself an atomic stock reservation.

## Reference: Videnoa

The user's [Videnoa project](https://github.com/ControlNet/videnoa) is an explicit reference for graph-based
workflow functions, typed inputs/outputs, nested calls, and invocation UX. See the
[source investigation](../../.omo/knowledges/videnoa-function-system-reference-2026-10-10.md) for verified
features and limits. This is a conceptual reference, not a decision to copy its runtime or restrict every
automation to its DAG execution model.

## User-proposed application: meta-crafting

The following describes one use case; its stage and batching concepts do not prescribe the architecture
or execution model of all automation.

The user proposed parameterized automation functions that coordinate multi-stage production as a DAG,
instead of representing the whole process as one AE2 crafting request. A function can be called manually
with arguments, by a trigger, or on a schedule. Exact calling and trigger semantics remain open.

Two motivating uses are:

- Probabilistic production: repeatedly observe actual output until a target quantity is reached, optionally
  collect that output in a chosen warehouse, and then request the next crafting stage. The warehouse step
  and its ownership/reservation semantics are still tentative.
- Limited early-game crafting storage: split a large goal into smaller crafting batches or explicit stages
  so the player can attempt it with smaller crafting CPUs. This is a goal, not a guarantee that every recipe
  graph becomes executable with any CPU size.

## Meta-crafting considerations, not system-wide execution rules

- An acyclic graph can express stage dependencies while an individual stage performs repeated attempts.
  Distinguish the dependency DAG from the condition/action graph and from the graph editor's visual layout.
- Treat AE2 job completion, observed output quantity, and resources secured for a later stage as separate
  states. If a submitted pattern promises a probabilistic output that never arrives, merely waiting for that
  job and then resubmitting may stall. Define how one production attempt is executed and recognized before
  promising support for probabilistic recipes; do not assume ordinary crafting submission solves it.
- Inventory observations need an explicit storage scope and protection against overlapping shared views.
  Observing sufficient stock is not a reservation. Concurrent function calls require resource/task ownership
  rules, particularly if outputs accumulate in a shared warehouse.
- Track outstanding jobs to avoid repeated submissions while production is in flight. Serial batches may
  help limit concurrent work, but a single batch's dependency tree can still exceed available crafting storage.
  Explicit intermediate-stock stages may be necessary; do not claim the smallest CPU can craft everything.
- Retries, timeouts, manual cancellation, restart recovery, and overlapping scheduled invocations need later
  design. Whether a function finishes with resources in storage or delivers them to a target remains open.

No particular interface entry point, new block/item, language, runtime, DAG executor, or backend architecture
is selected. Detailed lifecycle, action targets, and permissions belong to this automation idea rather than
expanding the initial custom-policy output list. No implementation or runtime tests were performed.
