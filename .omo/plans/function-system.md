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

## Open decisions (owner)

| # | Question | Proposal |
|---|---|---|
| Q1 | First use: a storage policy's filter list? | Yes: a filter-returning query plugs into the existing `PolicyFilter` and shows the whole path (edit, check, cache, epoch) with the least new gameplay. |
| Q2 | Script language | Decided (user, 2026-10-18): a Python subset with `int`/`float` and loops (`for x in xs`, `while`); see the [idea page](../../docs/ideas/function-system.md). The spike's Lua-like syntax and its static step bound are superseded. |
| Q3 | First action: redstone output? | Yes: owner-confirmed as a direction, needs no item movement, easy to see in game. |

Also open: what a policy does with an unavailable answer; sampling period; trigger kinds; where functions are stored
(per network, per device) and who may edit them; the measure-to-CU scale.

## Next phases

The user decided on 2026-10-18 to build bottom-up, world interactions last; layers and vocabulary are on the
[function system architecture](../../docs/ideas/function-system.md) idea page.

1. Bottom layer (next): drafts (unconnected graph nodes, scripts with syntax errors), graph edit operations, script
   editing support (error positions, completion data), serialisation with a version, built-in descriptors. Pure Java,
   test first.
2. Editor screens over that layer (script, then graph), GUI design canvases first.
3. Hosts: the Policy adapter with cached evaluation and `AuthorityEpoch.advance()` (Q1), then actions on the computing
   device with triggers and the compute limit (Q3).
