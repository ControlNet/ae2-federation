# Multiblock and capacity brainstorming boundary

Recorded the user's exploration in the [idea page](../../docs/ideas/multiblock-routing-and-cable-capacity.md).
Core motivations are construction scale, connected router textures, thick cable beams, and visible energy flow.
The user proposed free-form adjacent routers, cable bends, router-mediated size conversion, and larger capacity
for larger cable sections. Their numerical examples and charger-originated network/path accounting remain
explicitly unsettled. Do not turn the self-corrected counting description into a fixed architecture.

Follow-up clarification: one connected ME network counts as one capacity unit, and one attached Processing
Endpoint also counts as one. Policy does not affect counting; this is not per pair or per shared capability.
The user set aside charger-originated allocation/pathfinding for now. Segment/domain boundaries and repeated
attachments of one ME network remain undecided. The linked idea contains the current direction.

Separate energy consumption, connection capacity, and resource throughput during further discussion. Existing
Federation capabilities and optional power configuration should not silently gain new meanings. No Create block
was confidently identified, and no external mechanism comparison was asserted. Only idea documentation changed.

2026-10-10 follow-up: the user accepts Bridge daisy chains with re-export; do not frame them as an exploit
to prevent. Their new candidate partitions a domain into router-bounded subdomains with local global-count
capacity and full-capacity inter-router links. The idea page records an assistant formalization assuming
domain-wide backbone demand, its weakest-link consequence, unresolved segment classification, and possible
overload/counting feedback. This assumption and failure semantics are not approved requirements. The user
remains concerned about rooted allocation; do not reintroduce a reference router as the selected design.

Further 2026-10-10 clarification supersedes the unresolved backbone-role assumption: a cable region
bounded by routers uses local demand when adjacent to exactly one router, and full domain demand when
adjacent to multiple routers. This classifies regions from topology without a selected root or mandatory
physical hierarchy. The idea page records remaining questions: zero-router regions, distinct logical router
identity, attachments on routers, and capacity on direct router adjacency. Explicit consequences include
one-to-two-router demand jumps and domain-wide demand on dead-end branches of a multi-router region.
These are conceptual deductions; no implementation or runtime validation was performed.

Accepted layout tradeoff, 2026-10-10: the user asked to record allowing the 3+2 example and concentrated
star layouts without router capacity limits. Do not revive the assistant's proposed router capacity cap.
Routers and switches should support free-form multiblock expansion for bundled connections. The current
cable multiplier candidate is 1x1 C, 1x2 3C, 2x2 6C, 2x3 9C, 3x3 unlimited. Textures are later work.
Elongated router structures are not prohibited; evaluate build/space costs and optional power costs so
cables remain attractive for distance. No final recipes, costs, geometry, or switch semantics were chosen.
The idea page is authoritative for this evolving direction; this update changes documentation only.

Switch follow-up and scope correction: `docs/ideas/federation-switch.md` (archived on 2026-10-10 to `docs/archive/ideas/federation-switch.md` once implemented) records only the preliminary
role split: switches attach ordinary ME networks, and routers handle Federation interconnection without
direct ordinary ME attachment. The user explicitly rejected mixing capacity or multiblock expansion into
that idea. Keep expansion and capacity proposals in this document's linked multiblock idea; the switch
page must not prescribe their integration, power/configuration behavior, or other adjacent feature rules.

Switch registry planning: the idea now specifies `ae2federation:switch` for both block and block item,
following `router` and `cable` in `RouterRegistration.java`. No matching switch registration was found
in current production sources; this is documentation of a future ID, not an implemented entry.
