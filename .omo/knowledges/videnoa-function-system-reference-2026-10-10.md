# Videnoa reference for shared functions and automation

Date: 2026-10-10. Read-only inspection of the user-provided local Videnoa checkout at HEAD
`44a10178958d97e2e9e3f075458e135a979ec38c`. No Videnoa code was changed or executed; observations concern
the files inspected, not full application validation. References use repository-relative paths and public
source URLs, without local account paths.

## Verified reference concepts

Source base: https://github.com/ControlNet/videnoa/tree/44a10178958d97e2e9e3f075458e135a979ec38c

| Source | Observed behavior | Federation design relevance (proposal) |
|---|---|---|
| `crates/core/src/graph.rs` | `WorkflowInterface` declares named typed inputs/outputs and defaults; `PipelineGraph` validates topology, ports, types, and required inputs | A reusable function has an explicit interface independent of its graph layout |
| `crates/core/src/nodes/workflow_io.rs` | `WorkflowInput`, `WorkflowOutput`, and nested `Workflow` implement parameterized workflow reuse; nested calls carry cancellation and detect repeated paths / depth limits | Expose user functions as callable nodes with arguments and return ports |
| `crates/core/src/node.rs` | Common node trait exposes ports and execution; execution context tracks cancellation, nesting, and shared execution resources | Separate saved function definitions from per-call runtime state |
| `crates/core/src/registry.rs` | Node factories registered by type, reused for nested workflows | Shared catalog of primitives rather than separate Policy and automation implementations |
| `crates/core/src/descriptor.rs`, `web/src/stores/node-definitions-store.ts` | Node metadata includes names, categories, icons, typed ports, defaults, UI hints, and enum choices; frontend loads descriptors from `/api/nodes` | One operation description can inform graph widgets and future script completion; script support is a Federation proposal |
| `web/src/stores/workflow-store.ts` | Extracts workflow interface from I/O nodes and exports semantic graph data; layout supplied separately on load | Keep editable presentation information distinct from executable meaning |
| `web/src/pages/editor/RunFromEditorDialog.tsx`, `web/src/pages/jobs/RunWorkflowDialog.tsx` | UI supports running workflows with interface-driven parameters | Manual function calls should expose the declared inputs rather than require editing the graph |
| `crates/controller/src/domain/task.rs`, `docs/controller.md` | Tasks and attempts have separate identity, status, progress, failure and retry metadata; persistent operational state supports recovery | Long-running automation needs invocation/attempt lifecycle distinct from the function definition |

## Limits of the comparison

- The inspected graph uses topological sorting; nested workflows call a sequential executor. Video-frame
  processing has a specialized streaming path. These do not establish a ready-made Minecraft tick scheduler,
  persistent wait-until conditions, or unrestricted looping semantics.
- `docs/controller.md` explicitly excludes built-in cron discovery, watchers, and a rules engine. Controller
  task scheduling is not evidence that the desired Federation conditional/timed trigger system already exists.
- No lossless graph-to-Python/Lua-to-graph editor was established by the inspected sources. JSON graph export
  and typed I/O are useful foundations, but do not satisfy Federation's bidirectional script-editing requirement.
- No read-only/effectful function distinction was established. Federation Policy predicates need an additional
  behavioral contract preventing actions, including nested actions, during permission evaluation.
- File-backed workflow lookup and nested result handling should not be copied as a contract. Federation needs
  explicit function identity, declared return ownership, and behavior when referenced functions are edited.

## Suggested direction, not selected architecture

Use Videnoa's workflow-as-function model as a concrete reference: typed interfaces, callable user functions,
shared node metadata, validation, and parameter-driven invocation. Add domain-specific resource/network types,
query/action classification, and long-running action lifecycle as separate Federation concerns. Keep a shared
semantic function representation as a candidate foundation for graph/script parity, without selecting a
language, AST format, runtime, storage mechanism, or reuse of Videnoa code.

Related ideas: [automation](../../docs/ideas/rule-based-automation.md),
[custom policies](../../docs/ideas/custom-policies.md).
