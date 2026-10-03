# Custom policy ideas (2026-10-02)

## Product discussion location

Following the user's approval to separate ideas from research notes, maintain future product decisions in
[the custom policies idea](../../docs/ideas/custom-policies.md), listed in the
[idea library](../../docs/ideas/README.md). This note retains code observations, research and discussion history;
its historical proposals do not override the current idea page. Product requirements should no longer be
maintained independently in both places.

## Status and discussion boundary

The user confirmed the Federation P2P idea and opened idea 2: richer, customizable policies.
The user requested suggestions, not implementation. Storage resource filtering was subsequently endorsed as a
future feature direction. The user also clarified that conditional policies should consider AE2 network and
resource information, with redstone as one possible input. The user further specified two interchangeable authoring
modes: node-graph editing and script editing with comprehensive completion and contextual icons.
Other extensions remain proposals awaiting discussion.
Concrete architecture and implementation decisions remain for the future coding agent. DESIGN.md is unchanged.

## Endorsed future direction: storage resource filtering

The user agreed that filtering the resources shared from a storage network is an idea that could be implemented
in the future. The player-facing intent is to choose which stored resources are exposed through Federation sharing.
This endorses the direction, not a current-release commitment or implementation request. The agreed simplified
policy remains the current product baseline.

Specific filter modes, matching by tags or components, per-operation filters, editing UI and delivery schedule
remain undecided. This endorsement does not also approve granular operation controls, reserve floors, quotas,
crafting filters or particular conditional-rule semantics. Existing code fields do not determine the future
feature specification. The conditional-rule direction is separately clarified below.

## Clarified direction: conditions based on AE2 information

The user wants conditions to consider information available from AE2, explicitly including current network state
and the state of items in a network. Redstone is an available input, not the defining or exclusive input mechanism.
Do not narrow the idea to a redstone-controlled switch or treat a redstone-first delivery plan as agreed.

Candidate observables for discussion include network availability/power/boot state, quantities of selected items
or fluids, whether a resource is craftable or being crafted, crafting CPU availability, and storage or energy
status. These examples are not a verified API availability list or an approved exhaustive feature list. In
particular, generic free storage capacity, network-local energy figures inside a shared pool, and addon-specific
state may not have a single reliable meaning or inexpensive query. A future coding agent should verify each
selected observable against the target AE2 version and integrations.

A suggested player-facing expression selects an observed network, a metric/resource, a comparison and a value,
optionally combining conditions with all/any/not. Exact syntax, block and API design remain undecided; the later
dual-editor direction below supersedes the earlier form/sentence-editor suggestion.
Examples include opening storage access when the provider's iron stock exceeds a threshold, or accepting new
crafting requests when the consumer's relevant native crafting resources are available. Crafting conditions must
reflect the agreed model: the consumer runs the CPU; the provider supplies patterns and machines.

Observation scope should eventually distinguish native/local stock from the network's full accessible storage
view. An unloaded or otherwise unreadable network is unknown, not automatically zero stock. Conditions should
not themselves grant access to an unrelated network's information. Stock change rates and sustained-duration
conditions are possible later extensions requiring history, not assumed direct AE2 fields. These are design
considerations, not settled implementation requirements.

## Current baseline inspected

### Correction after checking the latest agreed design

The initial ideation response missed `crafting-pattern-projection-design-2026-10-02.md`, committed in
`2d8a792` and subsequently expanded. That note explicitly records an agreed, not-yet-implemented simplification:
storage and crafting use disabled / enabled / enabled-with-re-export states; crafting requires storage for the
same pair and direction; this version has no filters. Storage exposes all storage and crafting all patterns.
`PolicyFilter` remains in the data model with ALL, the storage compiler still reads it, and the new crafting
implementation is instructed not to read it. The editor should remove the filter summary.

At inspected HEAD `b493abf`, the existing production code still checks operations and resource filters in
`EffectiveStorageAuthority`, `StorageRelationshipAuthority`, `AuthorizedStorageProjection` and the old crafting
implementation. `StorageDependencyCompiler` still consumes allowReexport. These are real code paths, not just
unused fields, but they do not establish the latest intended player-facing feature scope. The current editor
still edits enabled rather than the proposed three states.

Use the simplified agreed design as the product baseline for idea 2. Adding custom filters is a proposed scope
extension to that baseline, not merely completion of an accidentally missing UI. Beyond the storage filtering
direction endorsed above, proposals remain unconfirmed; their composition must also account for the agreed
coupling between crafting and storage.
No production changes or runtime tests were made for this correction.

### Existing source and older DESIGN.md baseline

- DESIGN.md sections 4.4-4.5 bind policies to persistent, directional network pairs, with no sharing by default.
  Re-export must preserve origin restrictions; topology changes do not delete policies.
- `policy/PolicyRule.java` stores enabled, operations, filter and allowReexport.
- `policy/PolicyFilter.java` supports all resources, allow lists and deny lists.
- `policy/PolicyResource.java` identifies resource type and registry ID, without component-specific matching.
- `crafting/binding/CraftingPolicyFilter.java` checks resource type and registry ID.
- `client/policy/FederationDomainPolicySession.java` supplies broad defaults and exposes terms for display;
  the earlier implementation audit identifies missing editing controls for operations, filters and re-export.
- `policy/PolicyCapability.java` currently contains STORAGE, CRAFTING and ME_POWER. Processing uses mappings
  and claims rather than a separate policy capability.
- DESIGN.md section 8.4 explicitly makes ME power sharing symmetric and transitive through a native energy pool.
  Directional power budgets would be a separate gameplay change, not an ordinary policy field addition.
- DESIGN.md section 7 leaves stock replenishment execution to native automation. Conditional access should not
  silently introduce an autonomous stock balancer or a new global crafting scheduler.

Java paths above are relative to `common/src/main/java/space/controlnet/ae2federation/`.

## Candidate player-facing extensions

1. Rich resource and operation selection: separate visibility, insertion, extraction and crafting access;
   choose resource IDs, tags, resource types, mod namespaces and optional component-sensitive matches.
   Reusable named resource sets could express categories such as construction supplies or restricted materials.
   Resource-set names are player configuration, not fixed project categories.
2. Provider reserve floors: expose only the exportable surplus of a resource, e.g. keep 4,096 iron ingots locally.
   The reserve should constrain total Federation extraction across consumers and paths, rather than granting
   each relationship its own independently spendable copy. Local native consumption is a distinct concern;
   an export floor is not a guarantee that local machines cannot consume the reserve.
3. Consumer budgets: limit extraction rate or quantity over a configured period. Examples include a mining
   outpost's iron allowance or an experimental network's expensive-material allowance. Rate and cumulative
   quota are different controls. Resource types need meaningful, distinct units. Duplicate paths must not
   multiply a configured allowance.
4. Conditional access: enable a named rule set through a designated redstone control, local stock thresholds,
   or availability of a named network. Separate thresholds for entering and leaving a state can prevent
   oscillation. Example: open surplus export above 8,192 ingots and close it below 4,096. Conditions control
   access; they do not independently transport resources. Energy-driven conditions need separate analysis
   because current energy state belongs to a shared pool.
5. Crafting access profiles: expose selected output categories; optionally constrain request size or remote
   concurrency to keep public requests from occupying all production capacity. Source preference and
   fallback are additional candidates, subject to native planner and job lifecycle constraints. Changing
   policy must distinguish new requests from already running work and legitimate output returns.
6. Managed re-export: refine the current yes/no switch with allowed downstream networks or groups. Example:
   a warehouse lets a logistics network re-expose building supplies only to construction networks. Original
   resource restrictions, reserve limits and budgets must survive intermediate paths.
7. Reusable templates and explicit network groups: configure a public warehouse, production service or
   isolated laboratory once, then apply its settings to chosen relationships. Automatic grants to future
   group members and whether template edits update existing instances are separate product choices.
8. Explainable rules: preview whether a particular resource operation would be allowed, show the matching
   rule and why an enabled rule is currently inactive, and expose remaining exportable stock or allowance.
   Examples: blocked by reserve floor, denied resource, exhausted quota, missing connectivity, or re-export
   restricted by the origin network. These are proposed future UI behaviors, not current implementation claims.

## Conditional rules: qualitative feasibility discussion

The user subsequently proposed condition-based rules and asked about difficulty. This opens discussion of the
direction; no condition set, release scope or implementation has been approved.

Fresh inspection during this follow-up found `RuleMode`, `RuleLinks`, session `applyLinked` and service `editAll`
already present in the evolving working tree. The earlier b493abf snapshot above is historical: do not repeat its
not-yet-implemented three-state UI conclusion as a statement about the latest code. This follow-up did not run tests
or establish completion of the broader crafting redesign. Other source/test changes in the workspace were untouched.

`PolicyActivation` separates configured enabled state from runtime activation based on identity, connectivity and
backend readiness. `PolicyService` reconciles storage, crafting and energy after edits. `AuthorityEpoch` caches
authorization until relevant changes occur. This supplies useful integration points, but does not implement custom
conditions. Dynamic condition changes would need to invalidate affected effective access even without a saved edit.

Qualitative estimates, not measured delivery promises:

- A designated redstone signal or straightforward schedule gating a selected relationship: low to medium relative
  complexity, still requiring input selection, unloaded-input behavior and a usable configuration interface.
- Stock thresholds, AND/OR combinations and separate opening/closing thresholds: medium complexity. The measured
  network and inventory scope must be explicit; including imported stock can make a rule change its own input.
- Strict per-resource reserve guarantees, shared quotas, automatic source switching, and rules affecting in-flight
  crafting: high complexity. A periodically checked threshold alone cannot guarantee a reserve against a large
  extraction or several consumers. Existing jobs and valid returns need distinct treatment from new access.

Suggested initial product shape: a configured rule becomes effective only while its condition is satisfied;
manual disable remains authoritative. Show condition failure separately from manual disable or disconnection.
Start with a small condition vocabulary rather than committing to an arbitrary scripting engine. These are
recommendations, not settled requirements. Dynamic storage gating must respect crafting's storage dependency,
and cannot silently discard existing jobs or their outputs. Frequent toggles must avoid unnecessary broad
reconciliation. Cross-dimensional or unloaded signal sources require an explicit unavailable state.

## Suggested composition and discussion order

The user's later dual-editor direction below supersedes the assistant's sentence/form editor and shallow
condition-list recommendations. Those suggestions must not be treated as agreed UX scope or a reason to remove
the requested ability to alternate between graph and script editing.

A player-facing rule could read: selected consumer accesses selected provider's capability, for selected
resources, under chosen conditions, subject to specified limits. Basic presets can cover common cases, with
an advanced editor for combinations. A visual rule composer is a suggestion; scripting is not required.

Start discussion with resource/operation controls, reserves, templates and explanations. Consider rate limits,
conditional rules and crafting budgets after identifying desired gameplay. This is a recommendation, not
an approved release scope or implementation sequence.

Useful product boundaries to discuss later include conflict resolution for overlapping rules, whether limits
are per relationship or shared by a provider, and how policy edits affect existing crafting jobs. A suggested
default is that explicit protective restrictions constrain broad allows, but this is not user-confirmed.
Grouping or connecting networks must not silently override the existing default-deny contract.

## User-specified direction: interchangeable graph and script editors

The user rejected the proposed sentence/form-based condition editor and described these requirements:

1. Low-code graphical editing through connected nodes, referencing Low-Drag-MC/KilaGraph and KilaGraphDemo.
2. Code editing with a Python-like or Lua-like scripting form. No actual language, runtime or full-language
   compatibility commitment was selected.
3. Comprehensive contextual completion and visual icons. Preset parameter values should offer corresponding
   icons, and references to item IDs should automatically show the corresponding item icons.
4. The player can switch back and forth and continue authoring in either mode, converting between them freely.
   This is not merely one-way code generation, text export or two unrelated rule systems.

No implementation or new dependency was requested. Exact syntax and editor architecture remain for future work.

Suggested acceptance implications (assistant interpretation, not separately approved details):

- All supported rule constructs should remain meaningfully editable in both representations with equivalent
  behavior. Hiding arbitrary script in a single opaque node is not sufficient evidence of visual conversion.
- Completion should understand context: observable fields for the selected network, accepted parameter types,
  resources and preset values. Item candidates should show icons, localized names and registry IDs; already
  entered references should retain contextual previews. Icons decorate the text without destroying copy/paste.
- Preserve user-authored organization across repeated switches, including graph placement/groups and code
  comments/names where applicable. Formatting preservation and invalid intermediate edits need explicit UX
  treatment; do not silently delete drafts or pretend invalid text has a valid equivalent graph.
- Diagnostics and current values should be traceable to corresponding nodes and code locations.

The hard part is bidirectional editable conversion and preservation of author intent, not displaying two tabs.
Recommended feasibility direction: define one supported rule vocabulary with graphical and textual expressions.
A Python/Lua-like surface can provide familiar writing without promising arbitrary Python/Lua programs can always
be transformed into a readable node graph. The user has not chosen language restrictions; investigate them rather
than silently reducing the requested conversion guarantee. A shared structured representation is one possible
implementation strategy, not an approved architecture.

### Reference inspection

- KilaGraph README describes programmable Blueprint graphs, node libraries, resource management and Minecraft-aware
  nodes, based on LDLib2's Node Graph Toolkit: https://github.com/Low-Drag-MC/KilaGraph
- KilaGraph default branch inspected: `26.1`, `b84b64b203441ddccf73171992fa4d3bcd66abcd`.
  Its default README targets 26.1; this alone does not imply incompatibility of every branch with this project.
- Existing local research documents a 1.21 branch at `0f1b272cc2a61ddee01a8c23ffc76324cef6fabe`. Fresh inspection of
  its `KGGraphView` confirms it extends LDLib2 Node Graph Toolkit GraphView:
  https://github.com/Low-Drag-MC/KilaGraph/blob/0f1b272cc2a61ddee01a8c23ffc76324cef6fabe/src/main/java/com/lowdragmc/kilagraph/graph/util/KGGraphView.java
- KilaGraphDemo inspected: `master`, `90ff912ab24846c37ad0917e883c481c31901f06`.
  `HologramEditorWindow` composes a graph editor, resources, model settings and a world preview using SplittableWindow:
  https://github.com/Low-Drag-MC/KilaGraphDemo/blob/90ff912ab24846c37ad0917e883c481c31901f06/src/main/java/com/lowdragmc/kilagraphdemo/client/editor/HologramEditorWindow.java
- These inspected references support graph/workspace research. They do not establish a ready-made Python/Lua
  editor, contextual script completion with item icons, or lossless graph/script round-tripping.
- See also `gui-ldlib2-reference-review-2026-09-26.md`: the project's existing generic topology GraphView and the
  Node Graph Toolkit GraphView are different components. Existing topology rendering is not already a rule editor.

## Subsequent comparative research (2026-10-03)

The user requested approximately 100 mod references for policy separately from approximately 100 for P2P,
explicitly allowing overlap. The resulting [100-project policy survey](custom-policy-mod-survey-2026-10-03.md)
records resource matching, stock semantics, condition inputs, programming tools and editor feedback against
primary sources. It does not establish that all 100 implement an equivalent policy engine or that any inspected
project provides the entire requested graph/script experience.

Current product proposals distilled from that research belong in
[the custom-policy idea](../../docs/ideas/custom-policies.md). The historical code findings above remain tied to
their inspected revisions and are not a new audit of the current working tree.

## Verification

Read-only inspection of the design, production policy records and crafting filter; no runtime feasibility
claim for the new proposals. Only knowledge notes changed. No production code, tests or synthetic fixtures added.
