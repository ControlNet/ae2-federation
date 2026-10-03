# Custom policies

Status: future direction established; detailed behavior and scope under discussion.

Discussion date: 2026-10-02. Comparative research expanded: 2026-10-03. [Idea index](README.md).

## Intent and baseline

Give players more control over which capabilities and resources their networks share, and when sharing becomes
effective. Conditions should use information available from AE2 as well as redstone inputs.

The agreed baseline is the simplified policy model: storage and crafting have disabled / enabled /
enabled-with-re-export states; crafting requires storage for the same pair and direction. That baseline does not
offer resource filtering. ME power uses a shared energy pool with an on/off control.

This page describes future extensions to that baseline. Retained filtering fields in older code do not establish
the intended product scope. Concrete architecture, implementation and release scheduling remain deferred.

## Confirmed directions

### Storage resource filtering

Players should be able to choose which stored resources are exposed through Federation sharing. The user endorsed
this as a future feature direction. Exact filter modes and matching options remain open.

### Conditions based on AE2 information

Conditions should consider current network state, the state of items in a network, and other useful information
available from AE2. Redstone is one possible input; the feature is not limited to redstone-controlled switches.

Individual observables still need selection and feasibility verification. Possible examples include selected item
or fluid quantities, network availability, craftability, crafting activity, storage pressure and energy status.
These examples are not an approved exhaustive list or a guarantee that every metric has a reliable API.

### Two interchangeable editing modes

The user specified both of these authoring modes:

1. Low-code node-graph editing with connected nodes, referencing
   [KilaGraph](https://github.com/Low-Drag-MC/KilaGraph) and
   [KilaGraphDemo](https://github.com/Low-Drag-MC/KilaGraphDemo).
2. Script editing in a Python-like or Lua-like form, with comprehensive automatic completion and contextual
   visual icons. Preset parameter values should show corresponding icons; item-ID references should automatically
   show the referenced item's icon.

Players must be able to switch back and forth, converting the same rules and continuing to write in either mode.
One-way code generation does not fulfill this requirement. The earlier sentence/form-based editor suggestion was
rejected and must not be treated as the selected UX.

No language, runtime, full Python/Lua compatibility promise, editor component or dependency has been selected.

## UX proposals for further discussion

- Context-aware completion for network references, observable fields, functions and accepted parameter values.
  Resource candidates can show localized names, registry IDs and icons; previews can remain visible after insertion.
- Equivalent editable behavior in both representations. A single opaque script node would not provide the intended
  graphical editing experience.
- Preserve graph arrangement, meaningful names, comments and grouping when switching. Define how incomplete or
  invalid scripts are shown without discarding the draft or silently showing an unrelated graph.
- Show current values and explain unmet conditions, with corresponding locations in the graph and script.

These are proposed ways to meet the requested experience, not separately approved detailed acceptance criteria.

## Other candidate extensions

These earlier suggestions remain unconfirmed:

| Candidate | Intended use |
|---|---|
| Granular operation permissions | Configure visibility, insertion and extraction separately |
| Rich resource matching | Tags, mod namespaces, component matching and reusable resource sets |
| Provider reserve floors | Keep a chosen amount available locally while sharing surplus |
| Rates and quotas | Limit a consumer's usage or share one allowance across several consumers |
| Crafting access controls | Filter exposed products or constrain remote requests |
| Restricted re-export | Specify downstream networks that may receive a shared capability |
| Templates and network groups | Reuse configuration across selected relationships |
| Conditional profiles and fallback | Switch related rules or suppliers when selected conditions change |

## Ideas developed through the comparative survey

The [100-project policy survey](../../.omo/knowledges/custom-policy-mod-survey-2026-10-03.md) investigates
matching, quantity controls, observations, rule composition, scripting and editor feedback. It includes popular
technology projects and explicitly identified storage, automation and tooling comparisons. The following expands
the idea pool; it does not approve every feature or choose a language, runtime or architecture.

### Resource matching that players can inspect and reuse

Candidate filters could refer to an individual resource, a tag, a mod namespace or a saved resource set. Matching
options should explain whether resource components matter. Empty filters deserve an explicit explanation: an
empty allow-list and an unrestricted filter need not have the same result. Actually Additions and Modern
Industrialization provide useful examples of making this behavior visible.

FTB Filter System suggests sample-based feedback: select an actual item and see whether it matches. Explaining
the reason would be a further Federation proposal. Its JEI drag interaction is also relevant to choosing resources
without typing identifiers. These conveniences
would belong within the requested node and script editors; its hierarchical form editor is not a replacement
for the approved editing direction.

### Different stock intentions deserve different controls

LaserIO's counting filters expose three distinct intentions: retaining stock at a source, maintaining stock at
a destination and imposing a receiving ceiling. A player might want a workshop to receive materials only while
the provider has a surplus, or keep a consumer supplied to a target amount. Those are different from a transfer
batch size, a rate limit or a per-consumer allowance.

For Federation, stock observation should identify the network and storage view being counted. A condition such
as "stock is above a threshold" is not by itself a strict reservation against simultaneous requests. Reserve floors,
replenishment targets, quotas and guarantees remain separate proposals for future design.

### Conditions that describe factory state

AE2 level emitters, XNet logic channels and Immersive Engineering's Machine Interface provide references for
conditions involving inventory, energy, crafting or machine activity. Their examples support investigating
combinations such as available material AND an operational network, rather than restricting input to redstone.
The exact available observations still require AE2-specific verification.

Further proposals include named reusable conditions, profiles for different operating states, and separate
activation/deactivation thresholds to avoid repeated switching near one threshold. These are design suggestions,
not a claim that every surveyed mod implements them. Unavailable information should be distinguishable from a
known zero or a false condition; the resulting fallback behavior has not been selected.

### Explain the effective rule

Players could inspect the values that a condition observed, which condition failed, which rule took precedence,
and what permission or action resulted. In Control's match statistics and explicit rule ordering provide an
adjacent reference; Integrated Tunnels and programmable automation tools provide additional status feedback.
Rule ordering, conflict handling and defaults would need a coherent explanation shared by both editors.

A preview against selected resources or current observations could help players understand a rule before enabling
it. An execution trace could link the same condition in graph and script views. Neither a dry-run feature nor a
particular conflict-resolution rule is approved yet.

### Rich authoring remains a combined requirement

RFTools Control and PneumaticCraft provide visual-programming comparisons; CC: Tweaked, Integrated Scripting,
KubeJS and CraftTweaker provide scripting/API comparisons. ProbeJS specifically supplies generated typing,
snippets and item-literal completion through VSCode. These are useful pieces to study separately.

The inspected evidence does not establish a ready-made system that combines freely editable node graphs and
scripts, two-way conversion, comprehensive completion and inline resource icons. That complete experience remains
the project's requested direction. External IDE completion, a graphical filter form or an opaque script node alone
does not meet it. KilaGraph and KilaGraphDemo remain the user's explicit graph-editor references.

## Open product questions

- Which AE2 observations should be exposed, and which network does each observation describe?
- Does a stock condition count locally held resources or the entire accessible storage view? How is unavailable
  information shown when a network unloads or disconnects?
- What should a condition change do to new requests, existing crafting work and legitimate output returns?
- Which combinations and script constructs must both editors express? How should layout, comments and unfinished
  edits survive switching?
- Which resource filters and operation-specific distinctions are useful enough to expose?

These questions document future discussion, not a request to resolve implementation choices now. In particular,
periodic stock thresholds do not alone guarantee strict reserve floors, and shared-pool energy observations need
clear scope. Future design must account for the crafting/storage dependency.

## Research and context

- [100-project custom-policy comparison](../../.omo/knowledges/custom-policy-mod-survey-2026-10-03.md):
  independently evaluated policy findings, sources, version limits and the distinction between actual mechanisms
  and proposed Federation behavior. Overlap with the P2P sample is intentional.
- [Policy investigation and discussion history](../../.omo/knowledges/custom-policy-ideas-2026-10-02.md): code
  snapshots, corrections, qualitative feasibility findings and graph-editor source references.
- [Simplified policy and crafting design](../../.omo/knowledges/crafting-pattern-projection-design-2026-10-02.md):
  the agreed baseline preceding this idea. Consult current implementation evidence separately for delivery status.
- [LDLib2 and KilaGraph investigation](../../.omo/knowledges/gui-ldlib2-reference-review-2026-09-26.md): existing
  graph-toolkit findings and the distinction between topology display and an executable rule editor.
- [Expanded wireless-mod survey](../../.omo/knowledges/wireless-tech-mod-survey-2026-10-03.md), entry 41:
  [Create: Improve](https://modrinth.com/mod/create-improve) documents a visual wireless-frequency router with
  AND/OR/NOT, names and configuration suggestions. It is an additional interaction reference, not a chosen
  dependency or evidence of bidirectional graph/script conversion.

The inspected references provide graph and workspace foundations. They do not establish a ready-made script editor
or lossless, bidirectional graph/script conversion.
