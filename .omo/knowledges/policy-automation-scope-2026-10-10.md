# Policy and automation scope clarification

The user wants to explore active resource movement, automatic crafting requests, and redstone output.
The assistant's earlier preference to defer active automation is not a user rejection of these features.
The proposed boundary is a shared rule-authoring system with separate Policy and automation interfaces.
See [automation](../../docs/ideas/rule-based-automation.md) and
[custom policies](../../docs/ideas/custom-policies.md). Trigger modes, runtime architecture, permission
semantics, and action details remain open; no code changed. Do not treat all earlier policy-output
suggestions (reserves, quotas, crafting filters, etc.) as confirmed by this clarification.

The user subsequently proposed meta-crafting: parameterized functions coordinating a production DAG,
probabilistic-output accumulation checked against actual inventory, optional warehouse staging, and batching
for limited crafting storage. Calls may be manual, triggered, or scheduled. The automation idea records
this direction and separates it from unselected execution semantics. A dependency DAG can contain stages
whose internal operation retries; observing stock is not reserving it. Probabilistic-pattern completion and
per-batch CPU storage feasibility require validation, not assumptions of guaranteed support.

Further scope clarification: meta-crafting is only one possible application of a general automation system.
Do not redefine all automation as production functions or impose a production DAG/stage/batch model on
every rule. Other applications remain in scope for discussion; no additional action list is confirmed.

The user explicitly confirmed redstone output as an automation action direction, including standalone
uses outside production. Strength/pulse semantics and a physical output carrier are still undecided.

Latest refinement supersedes separate function editors: use one general function system, edited in the
automation GUI. Policy customization is a specialized use, e.g. boolean predicates or filter-returning
functions. Policy function selection/argument binding is a UI recommendation. Read-only queries versus
effectful actions is an assistant-proposed boundary to prevent permission checks from submitting jobs or
moving resources, including through nested calls. Query outputs can depend on live state; no language,
type system, runtime, failure default, or sampling algorithm is selected.
