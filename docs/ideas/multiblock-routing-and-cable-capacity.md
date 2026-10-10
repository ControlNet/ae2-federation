# Multiblock routers, switches, and cable capacity

Discussion dates: 2026-10-09 through 2026-10-10.
Status: gameplay idea with accepted layout tradeoffs and a candidate region-counting model. Cable multipliers,
connection details and failure behavior still need evaluation. Router and switch multiblocks must be solid cuboids. Router capacity limits are out of scope for now;
do not treat the original charger-rooted model as a requirement.

## Player experience

Reward infrastructure construction with large, visually connected routers and thick beam-like cable trunks.
Building a massive conduit should feel like an achievement: its appearance communicates scale and substantial
energy flow, while larger infrastructure can support greater connectivity.

## User-proposed forms

- Adjacent router blocks visually connect and act as a larger multiblock router. The desired construction is
  a complete solid cuboid, following the shape principle of AE2 crafting CPU multiblocks. This supersedes
  the earlier arbitrary-shape adjacency proposal.
- Cable sections can become visibly larger structures with different textures and a thicker rendered beam.
  The current candidate sizes are 1x1, 1x2, 2x2, 2x3, and 3x3. A possible 9x9 visual scale appeared
  earlier in the discussion; it is not a selected capacity tier.
- Large cables must be able to turn corners; a straight-only trunk does not fulfill the desired construction.
- Different cable sizes should not directly convert into each other. Splitting a 2x2 trunk into four 1x1 lines
  should require a router rather than a direct cable junction.
- Cable capacity should grow substantially with size; the current proposed multipliers are recorded below.
- Both routers and switches should support solid-cuboid multiblock expansion to accommodate bundled cable
  connections. This expansion proposal belongs to this idea, not the separate preliminary switch design.
  Switch traversal and counting boundaries remain open.
- Connected textures and visible energy flow are central to the reward, not merely incidental implementation polish.

The user mentioned a Create construction analogy without identifying the block. No specific Create mechanism
has been verified or selected as the implementation reference.

## Confirmed shape direction: solid cuboids

The user revised the earlier free-form proposal: router and switch multiblocks must form complete,
axis-aligned solid rectangular prisms. Length, width, and height need not be equal; the rule does not
prescribe a single fixed-size blueprint. Hollow shells, missing internal blocks, and L-shaped or branched
structures do not form one valid multiblock. Exact supported dimensions and limits remain undecided.

Reference: [AE2 1.21.1 crafting CPU guide](https://guide.appliedenergistics.org/1.21.1/items-blocks-machines/crafting_cpu_multiblock).
Only its solid-cuboid shape principle is adopted. This does not import crafting storage requirements,
coprocessors, channel costs, component recipes, or AE2's implementation. How incomplete structures behave
and how adjacent structures merge or remain separate still need design. Cable bends remain a separate
requirement; the cuboid restriction applies to router/switch bodies, not an entire cable route.

## Clarified direction: attachment-based capacity

In the follow-up discussion, the user specified:

- One connected ME network represents one capacity unit. Under the later switch proposal, ordinary ME
  networks attach through switches rather than directly through routers.
- An attached Processing Endpoint also represents one capacity unit, although it is not itself an ME network.
- Policy does not participate in capacity accounting. Enabling/disabling sharing or changing storage, crafting,
  power, or re-export rules must not change the number of units represented by the same attachments.
- Set aside the idea that a Beam Charger originates allocations or pathfinding. For now, discuss it as the
  energy-input device; charger-rooted allocation is not part of the current design scope.

For example, four distinct attached ME networks and two attached endpoints represent six attachment units,
regardless of which of their sharing policies are enabled. This does not yet specify which cable segments
must carry all or some of those units. Capacity is not counted per network pair or per enabled capability.
Repeated attachment of the same ME network and topology changes remain open. The candidate accounting
boundary is described below.

## Current candidate: classify regions by adjacent router count

On 2026-10-10, the user refined the proposal to remove any need for manually designated trunk or branch
roles. Routers form local counting boundaries. A connected cable region that touches exactly one router
is a subdomain and carries the total demand of attachments within that region. A region touching multiple
routers carries the entire Federation domain's attachment demand. This supersedes the earlier unresolved
suggestion of assigning separate backbone and local port roles. It remains a gameplay candidate, not a
complete implementation specification.

For evaluation, consider cable regions formed by connectivity without traversing router interiors. Let
R(S) be the set of distinct logical routers adjacent to region S, A(S) its local attachment identities,
and A(D) the attachment identities of the containing structural Federation domain. The candidate is:

- If |R(S)| = 1, every cable in S requires |A(S)| capacity.
- If |R(S)| >= 2, every cable in S requires |A(D)| capacity.
- If |R(S)| = 0, local counting is an assistant recommendation, not yet user-confirmed.

Distinct logical routers, rather than ports or individual blocks of one multiblock router, are the
recommended identity for R(S); this detail is not yet confirmed. Repeated ME-network attachments and
attachment ownership still need explicit rules. Domain demand includes
attachments located on multi-router regions as well as local subdomains. Re-exported resource visibility
must not silently be counted as additional physical attachments.

No root or two-level physical topology is necessary. Routers may form arbitrary chains and cycles, but
there remain two accounting scopes: local and domain-wide. Adding a second distinct router to a region
can abruptly raise all its cable demands from local to domain-wide. Dead-end cable branches belonging
to the same multi-router region inherit domain-wide demand too. Separate parallel routes do not automatically
add capacity under this counting model; bundled cross-sections use the proposed multipliers below, and all multi-router regions share the same domain-wide demand. These consequences should
be evaluated as gameplay tradeoffs, not presented as proof of design quality.

A router-free cable bypass merges regions; their router sets and local attachment sets must then be
reconsidered. Direct router-to-router adjacency needs explicit multiblock merge/separation rules; do not
introduce router interface capacity as an assumed solution, because router capacity limits are set aside. Special links, chunk availability, and multiblock merge/split behavior
need explicit boundary rules. For overload handling, an assistant recommendation is to distinguish
structural membership from operational availability, avoiding repeated disconnection/reconnection as
capacity-driven disconnections change the count. No shutdown scope or allocation algorithm is selected.

The user accepts Bridge daisy chains with re-export as legitimate play. Preventing that construction
or penalizing it is not a design objective. Switch interactions with this counting model remain open
and are not specified by the separate preliminary switch idea.

## Bundled cable capacity and multiblock connections

The user proposed the following multipliers relative to a single cable capacity C. These supersede the
older illustrative sizes/counts as the current balancing candidate; C and final balance are not fixed.

| Cross-section | Proposed capacity | Example only, if C = 4 |
|---|---|---|
| 1x1 | C | 4 |
| 1x2 | 3C | 12 |
| 2x2 | 6C | 24 |
| 2x3 | 9C | 36 |
| 3x3 | Unlimited | Unlimited |

The working interpretation is adjacent, aligned cable blocks forming one continuous bundled cross-section.
Do not infer automatic capacity addition for spatially separate routes. The finite bundled tiers yield
1.5C per constituent block; 3x3 deliberately ends capacity progression if retained. Larger visual sizes
would need a purpose other than additional capacity. These are consequences to evaluate, not new requirements.

Routers and switches can expand as solid cuboid multiblocks to provide physical space for large cable connections.
A complete matching attachment face is an assistant suggestion, not a selected geometry requirement.
Turns, orientation, partial cross-sections, size transitions, and multiblock separation need later design.
Bundled cable textures and thicker, coherent beam rendering are future visual work; no assets or final
material design are requested by this idea update.

## Accepted tradeoff: concentrated layouts remain valid

Following the 2026-10-10 discussion, the user accepted recording this direction:

- Do not add router capacity limits for now. The suggestion to cap a router's total attachment count is
  not part of the current direction.
- Allow three networks on one capacity-four local region and two on another, joined by one router.
  The two regions demand three and two units respectively; five total attachments do not require a
  capacity-five cable in this layout. This is legitimate construction, not an exploit to prohibit.
- Allow large star layouts that concentrate local regions on one router. Cable capacity constrains
  connection regions; it is not intended to independently cap the whole domain's size.
- Do not prohibit elongated solid-cuboid router multiblocks merely because they could substitute for a cable trunk.
  Evaluate their construction cost, space, and optional operating cost against bundled cables.
- Make bundled cables an attractive way to connect distant facilities efficiently and visibly. They
  need not be a compulsory upgrade for every small, compact layout.

This does not eliminate alternative constructions. Under the current counting candidate, regions joining
multiple distinct routers still require full-domain capacity, motivating large cables for distributed
facilities. A very long single router structure may avoid that situation; accept the alternative while
checking that normal cables remain worthwhile. Router expansion blocks should retain meaningful build
costs, with per-block operating costs a possible additional distinction when power is enabled. Exact
recipes and cost formulas are undecided. Since operating power is configurable, energy cost alone cannot
sustain the distinction when power requirements are disabled.

## Relationships

The user wants the full intended gameplay enabled by default, with deliberate opt-outs for pack authors.
See [configurable gameplay rules](configurable-gameplay-rules.md). Capacity and operating-power requirements
have independent switches, both enabled by default. Base cable capacity applies only while enforcement is
enabled; its numerical default remains open. Keeping construction features available without restrictions
is an assistant recommendation.

The [operating-power idea](federation-operating-power.md) supplies the working Beam Charger concept. Its role as
an allocation/pathfinding origin is set aside. Its configuration-optional power
requirement does not automatically establish whether capacity restrictions are optional too.

The [remote connections](remote-federation-connections.md), [beam links](line-of-sight-beam-links.md), and
[implemented P2P mode](../features/federation-p2p.md) would need explicit capacity boundary decisions if this
proposal proceeds. No changes to their existing contracts are selected by this brainstorm.

## Questions to resolve before specification

1. **Attachment identity:** how are repeated connections to the same ME network counted, and how do network
   splits/merges affect identity? The basic unit is already a connected ME network or endpoint, not a policy,
   capability, or pairwise relationship. Distinguish connection capacity from resource throughput.
2. **Counting boundary:** validate the adjacent-router-count candidate, including zero-router regions,
   logical router identity, region merging, and local-to-domain-wide demand jumps.
3. **Power versus capacity:** specify outage behavior without making a charger the allocation/pathfinding origin.
4. **Router and switch behavior:** distinguish their roles and counting-boundary behavior. Keep router
   capacity limits out of the current scope and preserve the accepted concentrated layouts.
5. **Shape recognition:** solid cuboids are required for routers and switches. Size limits, separation of
   neighboring structures, incomplete-structure behavior, cable axis/cross-section, turns, and forks remain
   open. No detection algorithm is selected.
6. **Usability:** saturated spans, power loss, obstructed remote links, and inactive policy rules should have
   distinguishable feedback. Rebuilding one segment should not silently change unrelated connection priorities.
7. **Progression:** does an unlimited 3x3 trunk leave useful roles for smaller sizes and potential larger sizes?
   Cost, available building space, energy, and visual ambition are separate possible motivations, not chosen gates.
8. **Configurations:** specify capacity settings independently of optional power under the configurable-rules idea;
   numerical defaults and existing-world transition behavior remain open; enforcement is enabled by default.

## Assistant discussion suggestions (not selected)

### AE2 channel reference

The [source-based AE2 comparison](../../.omo/knowledges/ae2-channel-capacity-reference-2026-10-09.md)
documents AE2 19.2.17 controller-rooted path allocation versus controller-free network-wide counting.
Useful concepts include separate demand and transport capacity, bottleneck checks, logical multiblock identity,
and visible saturation. AE2's rooted allocation cannot directly settle rootless Federation segment accounting;
its alternative-path limitations should also be evaluated before adopting similar routing behavior.
This reference does not select a controller, a charger root, or a global-count rule for Federation.

### Other suggestions

- Separate geometry/visual design from capacity accounting, so uncertain channel semantics do not freeze the art direction.
- Use a simple example with several networks on two router clusters and one intervening trunk to compare counting
  rules. Explain what happens on that trunk before discussing global routing algorithms.
- Consider the terms "link capacity" or "connection channels". Avoid "Capability" unless clearly distinguished
  from the mod's existing shared capabilities such as storage, crafting, and power.

No final names, block/item IDs, recipes, capacity values, mandatory topology, energy formula, or implementation
architecture have been approved. This page records gameplay exploration rather than an implementation task.
