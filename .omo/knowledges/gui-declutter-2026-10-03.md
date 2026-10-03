# GUI declutter and AE2 toolbar tabs, 2026-10-03

## Rail tabs compared with AE2 and NeoECO

- AE2 19.2.17 `IconButton.renderWidget`: an 18x20 `states.png` sprite (176,128 normal, 212,128 hover, 194,128 keyboard
  focus) drawn at its own size, the 16x16 icon one pixel inside its top-left corner. Hovered, it swaps to the hover
  sprite and draws sprite and icon one pixel lower (`yOffset = isHovered() ? 1 : 0`). The hover sprite's lip is one
  pixel and its last row is transparent, so the button reads as pressed in.
- NeoECO c33f736 `UploadButton` copies that exactly. Its LDLib2 `toolbarButton` (crafting host) uses `RECT_RD*`
  sprites, a different control.
- Federation's rail tabs were 22x24, stretching the sprite, with the icon centred over the lip and no drop on hover.
  Now: `.rail-tab` 18x20, rail 22 wide; `FederationTheme.toolbar/toolbarHover/toolbarSelected` draw sprite plus icon
  with the one-pixel drop, set per tab in `FederationWorkspace.show`. The open page uses the focus sprite.
- Checked in `ui-rail-tab-hover` (graph-controls) at 6x: crisp pixels, hovered tab one pixel lower with a one-pixel lip.

## What the player no longer sees

- Revisions anywhere: rule states ("Off", "Active", ...), the footer's rule/topology revision label (removed with
  `revisionsText`), accepted/stale/conflict statuses. Revisions still travel hidden for stale-edit authority.
- Server chatter: footer ready text is empty; "Server confirmed:" / "Rejected by server:" prefixes are gone.
- Tooltips on topology cards and Endpoint nodes. An Endpoint's recent deliveries/returns moved into its panel text.
  Cards carry an undrawn `network-<uuid>` class so tests still find them after a rename.
- Rule switch tooltip (mode list, mouse hints, crafting-holds-storage and re-export notes) and the `topology.mode.*`
  keys. The state label's tooltip is only `attention(...)`: the reasons of an error/waiting rule without the "Last
  backend check:" headings, or the skipped-storage line of a working storage rule; none otherwise.
- The reason is the tooltip alone (user's choice): the state line under a rule is only its state and recent flow.

## Test notes

- graph-controls' storage rule is Active or waiting for storage depending on which fixture network ends up the
  provider; the tooltip check accepts both (Active: none; waiting: only "The other network has no storage it can
  share."), and waits because the runtime arrives after the switch.
- The mapping fixture has no rule pill; to return to the pair editor select a card, then its `.network-link`.
- `federationUiVerifierSelfTest` already failed before this change for `forged-task15-ack` (its regex `policy-N$`
  never matched the visible status) and `claim-conflict-success` (Task 33); Task 34's probes all reject.

## Trimmed afterwards (all done)

Every candidate above was removed later the same day (the user took the recommendations as listed):

- the Endpoint identity panel and the Return binding and Last claim result facts (90eb7cd);
- the energy note (0df3463);
- the rest in one commit after it:
  - claim epochs on the wires view and in the release dialog;
  - 尚未生效 in the legend;
  - the "Runtime:" prefixes;
  - the own-pair note (`#pair_note` is now only the read-only banner);
  - the many-to-many legend line and the drag hint;
  - the header's sync word and entrance line;
  - the Provider ready footer;
  - "Saving…" for pending requests.

The claim-conflict evidence comes from the Endpoint explanation now, and the two broken self-test probes were fixed
in de8c8d8. The crafting-holds-storage hint stays out, by the user's choice.

## Second trim round (21 items)

- Network card and panel: the normal state (online, confirmed) writes nothing, only abnormal states show; idle flow is
  empty; no UUID fragment in network or Endpoint subtitles (the UUID stays in the tooltip); no map/3D captions; no
  "Surroundings" legend entry; no "no rules" notes; related pills lose "Other domain · read only"; the read-only
  banner reads "Read-only: belongs to %s".
- Endpoint panel: no "Mapped by a Provider of ..." line (`#network_explain` hides when it has no notes);
  Configured and Runtime mode merge into one `#endpoint_fact_mode` row when they agree.
- Wires view and Provider screen: no help text with nothing selected (only `empty_help` when there are no slots or
  Endpoints); success mapping feedback is silent (`MappingFeedback.silent()`), so tests read the raw code from the
  `#processing_status` tooltip (`TaskThirtyThreeScenarioSupport.mappingCode`); an IN_USE Endpoint has no State fact;
  the column reads "Pattern Provider"; no Federation-face phrase in the summary, no footer count, no return-buffer
  note; the Provider footer auto-hides like the domain footer.
- Tooltips: no `rename_help`, `pattern_help`, `pattern_selected_help`; `mode_help` is "Drag to rotate, scroll to zoom".
- Then: the Endpoint subtitle is only the dimension (the title has the position); the wires views' corner legend
  (one-owner rule, dashed-card line) is gone, the guide explains both; `provider_editing_help` reads "Drag a pattern
  onto an Endpoint to map it."
