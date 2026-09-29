# Processing Endpoint: Federation-face design (confirmed with the user, 2026-09-29)

The shipped 0.0.1 Endpoint got this wrong: its "Federation face" was a fixed, inert EAST face (no
`FederationPortCapability`, no cable arm), the Endpoint was listed only when its own ME network was a Domain member,
and Provider → Endpoint needed a `PROCESSING` pair rule. The user's intended design:

1. **Orientation** — same AE2 orientation as the Federation Pattern Provider: FRONT is the Federation face, placed
   toward the clicked block, wrench-rotatable.
2. **Federation face** connects to a Federation cable, a Router, or a Federation Pattern Provider's front face. When
   connected, the Endpoint is a node of that Federation Domain.
3. **Other five faces** connect one ME network (the Endpoint's subnet). That network does **not** join the Domain (the
   Endpoint publishes no native membership evidence); it is only a processing target. Inputs go straight into the
   subnet's ME storage (no ME Interface); machine output pushed back into these faces returns to the owning Provider.
   All five faces share one node, so two different networks on two faces merge (same as an ME Interface).
4. **Access** — every Federation Provider in the same Domain can see and map the Endpoint; no rule is needed.
   Kept: one Provider owns an Endpoint at a time (Claim); the Provider's network must differ from the subnet.
5. **Policy** — the `PROCESSING` capability is removed from rules. Rules keep Storage, Crafting, ME power.
   `CRAFTING` is the native AE2 cross-network crafting (A ▸ B: A's terminal requests B's craftables with B's patterns,
   Pattern Providers and CPUs) and is unrelated to Endpoints.
6. **Topology** — each Endpoint is drawn as a small node on the Domain graph, linked to the network of the Provider
   that maps it (this replaces the "Processing ×N" link chip).
7. **Mode by what touches the Federation face** — Federation cable / Router / Federation Provider front → Federated
   mode; a native AE2 Pattern Provider (another network) → Local mode. Mutually exclusive; switching drains and
   releases the previous Claim first.
8. **No save compatibility** with 0.0.1 is required.
9. **Topology links only between networks that discover each other** — two ME networks "discover" each other when
   they share at least one Federation Domain. Only such pairs get a link (solid for rules, dashed candidates on
   selection) or appear in a network's connection list. Rules are editable only for pairs inside the workspace's own
   domain (the server already enforces this in `FederationDomainPolicySession.setPolicy`); related-domain pairs stay
   read-only. Before this change the selection drew dashed candidates to every shown network, including related
   networks that share no domain with it.
