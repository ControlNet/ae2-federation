# Lightweight Jade cable capacity display — archived discussion

Archived: 2026-10-14, after the owner agreed to archive implemented ideas.

See [implemented cable capacity](../../features/cable-capacity.md) for current behavior. The discussion below is
historical. The details it left open were settled as follows: the line reads "Capacity count: N/C (subdomain)" or
"(Federation domain)", with N red when over an enforced limit; bundled cables add their cross-section and capacity
and whether each end is connected.

[Idea index](../../ideas/README.md).

Discussion date: 2026-10-10.
Status: the count line is implemented on `dev` (2026-10-13): `Capacity count: N (subdomain)` or `(Federation domain)`, from
the [counting rules](../../architecture/cable-capacity-counting.md); the limit part waits for capacity limits.

## Direction

Jade information must remain very lightweight and should not dominate the HUD. The initial candidate is
one concise line on Federation cables showing the current network count and capacity limit, similar in
purpose to AE2 cable channel information. Counts and limits must come from the implemented cable capacity
rules; this display does not introduce its own counting algorithm.

This is a future display for the [cable capacity idea](../../ideas/multiblock-routing-and-cable-capacity.md), not a
claim that capacity limits currently exist. The earlier assistant suggestions for router diagnostics,
provider status, endpoint bindings, and expanded detail panels are not accepted scope for this idea.

## Details left open

- Final wording, including how to describe counted endpoints as well as ME networks.
- Presentation when capacity restrictions are disabled or a cable has unlimited capacity.
- Whether exceeding the limit warrants a simple color change, without additional diagnostic clutter.

No integration code, dependencies, or display assets are introduced. Technical feasibility research is
available in the [Jade reference](../../../.omo/knowledges/jade-extension-reference-2026-10-10.md).
