# Rule-based automation

Discussion date: 2026-10-10.
Status: preliminary user-proposed direction; the computing device's role was settled on 2026-10-17. Detailed
behavior remains open.

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

## Computing device (user decisions 2026-10-17)

Deferred (user, 2026-10-17): the device is designed after the automation program features themselves. The
decisions below stay as its starting point.

Automation runs on a Federation computing multiblock, a supercomputer-like structure. It belongs to this mod and
continues the Nexus Processor / Nexus Core material line; its component names follow AE2's style.

- **Required.** Players must build one to use automation at all.
- **Optional limit.** The configuration can turn the computing limit off, giving unlimited automation
  (user, 2026-10-17); like the other gameplay rules it is on by default. Whether a device is still needed then is open.
- **Upgradable.** The structure holds installable "CPU" and "memory" components; more components host more automation.
- **Two resources.** Each program has a known cost in two measures: one from its logic (for example its node count)
  and one from its I/O (moving items, transferring energy, emitting redstone). The first is **算力** (compute),
  measured in **CU** (Compute Unit); the second is **带宽** (bandwidth), measured in bytes per second, written like a
  network speed: B/s, KB/s, MB/s (user, 2026-10-17). A device reads, for example, "80/100 CU · 1.5/4 KB/s". Both
  costs are inferred from the program itself when it is compiled; how is later design.
- **Bytes from AE2.** AE2 already prices resources in bytes: every resource type (`AEKeyType`) has an amount per byte
  (8 items, 8 buckets, and addon types their own), so item, fluid and addon-resource transfers convert to bytes
  without per-type lists. Energy and redstone are not AE2 resource types and need their own conversion, later.
  Steps of 1024, as AE2's 1k = 1024 bytes, are an assistant proposal.
- **Reserved while enabled.** Enabling a program reserves its whole cost on the device, whatever work it actually
  does; disabling it releases the cost.
- **Placement.** A domain may have several devices. Each program is installed on exactly one, chosen in a GUI, and
  one program never combines resources from two devices: with A = 100 and B = 50, programs needing 80 and 60 cannot both
  run. A distributed-computing block was mentioned as a possible later idea, with no current plan.
- **Not crafting.** It has nothing to do with AE2 crafting CPUs. A program may call AE2 autocrafting, but the device
  is not part of AE2's crafting system.

The user worries that "CPU" and "memory" would carry over AE2's meaning: there a crafting CPU is how many jobs run
at once and its storage is how many items a job holds. CU and bandwidth avoid that reading; "nodes" was rejected as
the measure's name. AE2's crafting casing is the "Crafting Unit", so component names may want to avoid "... Unit".

Open: the component names (tiering them 1k/4k like AE2 storage would invite the storage reading); how node count, nested calls and declared I/O turn into costs;
how a device joins its domain; and what enabled programs do when the device loses power or its domain stops sharing.

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

Apart from the computing device above, no particular interface entry point, new block/item, language, runtime,
DAG executor, or backend architecture is selected. Detailed lifecycle, action targets, and permissions belong to this automation idea rather than
expanding the initial custom-policy output list. No implementation or runtime tests were performed.
