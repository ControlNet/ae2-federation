# Lightweight Jade cable capacity display

Discussion date: 2026-10-10.
Status: preliminary display idea, dependent on future cable capacity implementation.

## Direction

Jade information must remain very lightweight and should not dominate the HUD. The initial candidate is
one concise line on Federation cables showing the current network count and capacity limit, similar in
purpose to AE2 cable channel information. Counts and limits must come from the implemented cable capacity
rules; this display does not introduce its own counting algorithm.

This is a future display for the [cable capacity idea](multiblock-routing-and-cable-capacity.md), not a
claim that capacity limits currently exist. The earlier assistant suggestions for router diagnostics,
provider status, endpoint bindings, and expanded detail panels are not accepted scope for this idea.

## Details left open

- Final wording, including how to describe counted endpoints as well as ME networks.
- Presentation when capacity restrictions are disabled or a cable has unlimited capacity.
- Whether exceeding the limit warrants a simple color change, without additional diagnostic clutter.

No integration code, dependencies, or display assets are introduced. Technical feasibility research is
available in the [Jade reference](../../.omo/knowledges/jade-extension-reference-2026-10-10.md).
